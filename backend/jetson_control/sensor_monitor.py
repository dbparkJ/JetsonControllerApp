from __future__ import annotations

import json
import os
import pwd
import re
import shlex
import signal
import stat
import subprocess
import sys
import time
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Callable, Mapping, Optional, Sequence

from .mobile_rtk import DEFAULT_MOBILE_RTK_RELAY, mobile_rtk_relay_environment
from .sensors import SensorBridgeSnapshot, SensorBridgeStore
from .sensor_handoff import (
    DEFAULT_SENSOR_MONITOR_CONFIG,
    SensorMonitorSettings,
    _open_lock,
    _try_exclusive_lock,
    capture_request_active,
)


DEFAULT_PIPELINE_ENV_ROOT = Path("/etc/jetson-control/pipelines")
ENVIRONMENT_KEY_PATTERN = re.compile(r"^[A-Za-z_][A-Za-z0-9_]*$")
MAX_PIPELINE_ENV_BYTES = 64 * 1024


@dataclass(frozen=True)
class MonitorRuntime:
    command: tuple[str, ...]
    environment: Mapping[str, str]
    pipeline_environment: Mapping[str, str]
    working_directory: Path
    user: str
    release: Path
    relay_environment: Mapping[str, str] = field(default_factory=dict)
    camera_required: bool = False


def _relative_path(value: object, name: str) -> Path:
    if not isinstance(value, str):
        raise ValueError(f"Sensor monitor pipeline {name} is invalid")
    path = Path(value)
    if not value or path.is_absolute() or ".." in path.parts or "\x00" in value:
        raise ValueError(f"Sensor monitor pipeline {name} is invalid")
    return path


def _required_string(value: Mapping[str, Any], key: str) -> str:
    result = value.get(key)
    if not isinstance(result, str) or not result or "\x00" in result:
        raise ValueError(f"Sensor monitor pipeline field is invalid: {key}")
    return result


def _trusted_directory(path: Path, name: str, expected_owner_uid: int) -> Path:
    try:
        metadata = os.lstat(path)
    except OSError as error:
        raise ValueError(f"Sensor monitor {name} is unavailable: {error}") from error
    if (
        not stat.S_ISDIR(metadata.st_mode)
        or stat.S_ISLNK(metadata.st_mode)
        or metadata.st_uid != expected_owner_uid
        or metadata.st_mode & (stat.S_IWGRP | stat.S_IWOTH)
    ):
        raise ValueError(f"Sensor monitor {name} ownership or permissions are unsafe")
    return path.resolve(strict=True)


def _load_trusted_manifest(path: Path, expected_owner_uid: int) -> Mapping[str, Any]:
    descriptor: Optional[int] = None
    try:
        flags = os.O_RDONLY | getattr(os, "O_CLOEXEC", 0)
        if hasattr(os, "O_NOFOLLOW"):
            flags |= os.O_NOFOLLOW
        descriptor = os.open(path, flags)
        metadata = os.fstat(descriptor)
        if (
            not stat.S_ISREG(metadata.st_mode)
            or metadata.st_uid != expected_owner_uid
            or metadata.st_mode & (stat.S_IWGRP | stat.S_IWOTH)
        ):
            raise ValueError("Sensor monitor pipeline manifest permissions are unsafe")
        with os.fdopen(descriptor, "r", encoding="utf-8") as source:
            descriptor = None
            value = json.load(source)
    except json.JSONDecodeError as error:
        raise ValueError(f"Could not load sensor monitor pipeline: {error}") from error
    except OSError as error:
        raise ValueError(f"Could not load sensor monitor pipeline: {error}") from error
    finally:
        if descriptor is not None:
            os.close(descriptor)
    if not isinstance(value, dict):
        raise ValueError("Sensor monitor pipeline manifest schema is invalid")
    return value


def _load_pipeline_environment(
    root: Path,
    pipeline_id: str,
    expected_owner_uid: int,
) -> Mapping[str, str]:
    """Load the root-owned EnvironmentFile used by the managed pipeline unit."""

    if not root.exists():
        return {}
    trusted_root = _trusted_directory(
        root,
        "pipeline secrets directory",
        expected_owner_uid,
    )
    path = trusted_root / f"{pipeline_id}.env"
    descriptor: Optional[int] = None
    try:
        flags = os.O_RDONLY | getattr(os, "O_CLOEXEC", 0)
        if hasattr(os, "O_NOFOLLOW"):
            flags |= os.O_NOFOLLOW
        descriptor = os.open(path, flags)
        metadata = os.fstat(descriptor)
        if (
            not stat.S_ISREG(metadata.st_mode)
            or metadata.st_uid != expected_owner_uid
            or metadata.st_mode & (stat.S_IRWXG | stat.S_IRWXO)
        ):
            raise ValueError("Sensor monitor pipeline secrets permissions are unsafe")
        with os.fdopen(descriptor, "r", encoding="utf-8") as source:
            descriptor = None
            content = source.read(MAX_PIPELINE_ENV_BYTES + 1)
    except FileNotFoundError:
        return {}
    except (OSError, UnicodeDecodeError) as error:
        raise ValueError(f"Could not load sensor monitor pipeline secrets: {error}") from error
    finally:
        if descriptor is not None:
            os.close(descriptor)
    if len(content.encode("utf-8")) > MAX_PIPELINE_ENV_BYTES:
        raise ValueError("Sensor monitor pipeline secrets file is too large")

    environment: dict[str, str] = {}
    for line_number, raw_line in enumerate(content.splitlines(), start=1):
        stripped = raw_line.strip()
        if not stripped or stripped.startswith("#") or stripped.startswith(";"):
            continue
        key, separator, raw_value = raw_line.partition("=")
        key = key.strip()
        if not separator or not ENVIRONMENT_KEY_PATTERN.fullmatch(key):
            raise ValueError(
                f"Sensor monitor pipeline secrets line {line_number} is invalid"
            )
        value = raw_value.strip()
        if value.startswith(("'", '"')):
            try:
                parsed = shlex.split(value, comments=False, posix=True)
            except ValueError as error:
                raise ValueError(
                    f"Sensor monitor pipeline secrets line {line_number} is invalid"
                ) from error
            if len(parsed) != 1:
                raise ValueError(
                    f"Sensor monitor pipeline secrets line {line_number} is invalid"
                )
            value = parsed[0]
        if "\x00" in value:
            raise ValueError(
                f"Sensor monitor pipeline secrets line {line_number} is invalid"
            )
        environment[key] = value
    return environment


def _trusted_release_file(
    path: Path,
    release: Path,
    name: str,
    expected_owner_uid: int,
) -> Path:
    try:
        relative = path.relative_to(release)
        parent = release
        for part in relative.parts[:-1]:
            parent = _trusted_directory(
                parent / part,
                f"{name} parent directory",
                expected_owner_uid,
            )
        metadata = os.lstat(path)
        resolved = path.resolve(strict=True)
    except (OSError, ValueError) as error:
        raise ValueError(f"Sensor monitor {name} is unavailable: {error}") from error
    try:
        resolved.relative_to(release)
    except ValueError as error:
        raise ValueError(f"Sensor monitor {name} leaves the release") from error
    if (
        stat.S_ISLNK(metadata.st_mode)
        or not stat.S_ISREG(metadata.st_mode)
        or metadata.st_uid != expected_owner_uid
        or metadata.st_mode & (stat.S_IWGRP | stat.S_IWOTH)
    ):
        raise ValueError(f"Sensor monitor {name} ownership or permissions are unsafe")
    return resolved


def load_monitor_runtime(
    settings: SensorMonitorSettings,
    *,
    expected_owner_uid: int = 0,
    pipeline_env_root: Path = DEFAULT_PIPELINE_ENV_ROOT,
    pipeline_environment: Optional[Mapping[str, str]] = None,
    relay_path: Path = DEFAULT_MOBILE_RTK_RELAY,
) -> MonitorRuntime:
    registry_root = _trusted_directory(
        settings.registry_root,
        "pipeline registry",
        expected_owner_uid,
    )
    pipeline_root = _trusted_directory(
        registry_root / settings.pipeline_id,
        "pipeline root",
        expected_owner_uid,
    )
    releases_root = _trusted_directory(
        pipeline_root / "releases",
        "releases directory",
        expected_owner_uid,
    )
    manifest_path = pipeline_root / "pipeline.json"
    manifest = _load_trusted_manifest(manifest_path, expected_owner_uid)
    if (
        manifest.get("schema_version") != 1
        or manifest.get("id") != settings.pipeline_id
    ):
        raise ValueError("Sensor monitor pipeline manifest schema is invalid")

    # Reuse the operator's existing run policy, independently of child status.
    # Missing/optional camera policies must still permit camera-less devices.
    policy_path = pipeline_root / "run-policy.json"
    camera_required = False
    if policy_path.exists():
        policy = _load_trusted_manifest(policy_path, expected_owner_uid)
        if (
            policy.get("schemaVersion") != 1
            or policy.get("pipelineId") != settings.pipeline_id
            or policy.get("configured") is not True
            or not isinstance(policy.get("requiredSensors"), list)
        ):
            raise ValueError("Sensor monitor run policy is invalid")
        camera_required = "camera" in policy["requiredSensors"]

    release = (pipeline_root / "current").resolve(strict=True)
    try:
        release.relative_to(releases_root)
    except ValueError as error:
        raise ValueError("Sensor monitor release leaves the release directory") from error
    release = _trusted_directory(
        release,
        "current release",
        expected_owner_uid,
    )
    if manifest.get("release") != release.name:
        raise ValueError("Sensor monitor manifest and current release do not match")

    virtualenv = Path(_required_string(manifest, "virtualenv")).resolve(strict=True)
    python = Path(_required_string(manifest, "python"))
    if python != virtualenv / "bin" / "python" or not os.access(python, os.X_OK):
        raise ValueError("Sensor monitor virtualenv Python is invalid")

    entrypoint = _trusted_release_file(
        release / _relative_path(manifest.get("entrypoint"), "entrypoint"),
        release,
        "entrypoint",
        expected_owner_uid,
    )
    config = _trusted_release_file(
        release / _relative_path(manifest.get("config"), "config"),
        release,
        "config",
        expected_owner_uid,
    )

    working_directory = Path(
        _required_string(manifest, "working_directory")
    ).resolve(strict=True)
    if not working_directory.is_dir():
        raise ValueError("Sensor monitor working directory is invalid")
    user = _required_string(manifest, "user")
    account = pwd.getpwnam(user)
    if account.pw_uid == 0:
        raise ValueError("Sensor monitor pipeline must not run as root")

    arguments = manifest.get("arguments", [])
    if not isinstance(arguments, list) or any(not isinstance(item, str) for item in arguments):
        raise ValueError("Sensor monitor pipeline arguments are invalid")
    config_argument = _required_string(manifest, "config_argument")
    command = (
        str(python),
        "-u",
        str(entrypoint),
        config_argument,
        str(config),
        *arguments,
        *settings.monitor_arguments,
    )
    if pipeline_environment is None:
        pipeline_environment = _load_pipeline_environment(
            pipeline_env_root,
            settings.pipeline_id,
            expected_owner_uid,
        )
    environment = os.environ.copy()
    environment.pop("JETSON_PIPELINE_RESULTS_DIR", None)
    environment.pop("JETSON_PIPELINE_LOGS_DIR", None)
    environment.update(pipeline_environment)
    environment.pop("JETSON_PIPELINE_MOBILE_RTK_RELAY", None)
    relay_environment = mobile_rtk_relay_environment(
        settings.pipeline_id, relay_path, expected_owner_uid=expected_owner_uid,
    )
    environment.update(relay_environment)
    environment.update(
        {
            "JETSON_PIPELINE_ID": settings.pipeline_id,
            "JETSON_PIPELINE_RELEASE": str(release),
            "JETSON_PIPELINE_CONFIG": str(config),
            "JETSON_PIPELINE_SENSOR_BRIDGE_DIR": str(settings.bridge_dir),
            "PATH": f"{python.parent}:{environment.get('PATH', '')}",
            "PYTHONPATH": (
                f"{release}:{environment['PYTHONPATH']}"
                if environment.get("PYTHONPATH")
                else str(release)
            ),
            "PYTHONUNBUFFERED": "1",
            "PYTHONDONTWRITEBYTECODE": "1",
            "HOME": account.pw_dir,
            "USER": account.pw_name,
            "LOGNAME": account.pw_name,
        }
    )
    return MonitorRuntime(
        command,
        environment,
        dict(pipeline_environment),
        working_directory,
        user,
        release,
        relay_environment,
        camera_required,
    )


class CameraProgressWatchdog:
    """Watch sample progress outside the child, including a hung C++ close.

    Required cameras arm at launch, even if initialization never publishes a
    status. Optional cameras arm on new samples or attached hardware status.
    GNSS/status heartbeats do not establish sample progress. Use monotonic time
    so mobile clock synchronization cannot look like a stall.
    """

    def __init__(
        self, initial: SensorBridgeSnapshot, started_at: float,
        *, startup_seconds: float = 45.0, stale_seconds: float = 15.0,
        camera_required: bool = False,
    ) -> None:
        self.started_at = started_at
        self.startup_seconds = startup_seconds
        self.stale_seconds = stale_seconds
        self.samples = self._samples(initial)
        self.initial_status_update = initial.updated_at_epoch_millis
        self.camera_required = camera_required
        self.progress: dict[str, float] = {"camera": started_at} if camera_required else {}

    @staticmethod
    def _samples(snapshot: SensorBridgeSnapshot) -> dict[str, Optional[int]]:
        return {
            "camera": snapshot.camera.get("lastFrameAtEpochMillis"),
            "imu": snapshot.imu.get("lastSampleAtEpochMillis"),
        }

    def stalled(self, snapshot: SensorBridgeSnapshot, now: float) -> bool:
        for name, sample in self._samples(snapshot).items():
            sensor = getattr(snapshot, name)
            if (sensor.get("configured") is False and not (
                name == "camera" and self.camera_required
            )) or (
                name == "imu" and sensor.get("source") not in (None, "oak")
            ):
                self.progress.pop(name, None)
                continue
            if (
                sensor.get("connected") is True
                and snapshot.updated_at_epoch_millis is not None
                and snapshot.updated_at_epoch_millis != self.initial_status_update
                and (name == "camera" or sensor.get("source") == "oak")
            ):
                # A new child status can confirm an attached camera even if
                # its very first frame never arrives. Do not refresh this timer
                # for GNSS/status-only heartbeats.
                self.progress.setdefault(name, now)
            if isinstance(sample, int) and not isinstance(sample, bool) and sample > 0:
                if sample != self.samples[name]:
                    self.progress[name] = now
                    self.samples[name] = sample
        if now - self.started_at < self.startup_seconds:
            return False
        if "camera" not in self.progress:
            return False
        return any(now - updated >= self.stale_seconds for updated in self.progress.values())


def drop_privileges(user: str) -> None:
    account = pwd.getpwnam(user)
    if os.geteuid() == 0:
        os.initgroups(account.pw_name, account.pw_gid)
        os.setgid(account.pw_gid)
        os.setuid(account.pw_uid)
    elif os.geteuid() != account.pw_uid:
        raise PermissionError(f"Sensor monitor must run as {user}")


def _process_group_exists(process_group_id: int) -> bool:
    try:
        os.killpg(process_group_id, 0)
        return True
    except ProcessLookupError:
        return False
    except PermissionError:
        return True


def _signal_process_group(process_group_id: int, signum: int) -> None:
    try:
        os.killpg(process_group_id, signum)
    except ProcessLookupError:
        pass


def _wait_for_process_group(
    child: subprocess.Popen,
    process_group_id: int,
    timeout_seconds: float,
) -> bool:
    deadline = time.monotonic() + timeout_seconds
    while _process_group_exists(process_group_id) and time.monotonic() < deadline:
        child.poll()
        time.sleep(0.1)
    if _process_group_exists(process_group_id):
        return False
    try:
        child.wait(timeout=max(0.1, timeout_seconds))
    except subprocess.TimeoutExpired:
        return False
    return True


def stop_child(
    child: subprocess.Popen,
    timeout_seconds: float = 20.0,
    terminate_timeout_seconds: float = 5.0,
    kill_timeout_seconds: float = 5.0,
) -> None:
    process_group_id = child.pid
    if not _process_group_exists(process_group_id):
        child.poll()
        return
    _signal_process_group(process_group_id, signal.SIGINT)
    if _wait_for_process_group(child, process_group_id, timeout_seconds):
        return
    _signal_process_group(process_group_id, signal.SIGTERM)
    if _wait_for_process_group(child, process_group_id, terminate_timeout_seconds):
        return
    _signal_process_group(process_group_id, signal.SIGKILL)
    if not _wait_for_process_group(child, process_group_id, kill_timeout_seconds):
        raise RuntimeError("Boot sensor monitor process group did not stop")


def supervise(
    settings: SensorMonitorSettings,
    user: str,
    *,
    retry_seconds: float = 3.0,
    pipeline_environment: Optional[Mapping[str, str]] = None,
    runtime_loader: Optional[Callable[[SensorMonitorSettings], MonitorRuntime]] = None,
    relay_path: Path = DEFAULT_MOBILE_RTK_RELAY,
) -> int:
    stopping = False
    child: Optional[subprocess.Popen] = None

    def request_stop(_signum: int, _frame: object) -> None:
        nonlocal stopping
        stopping = True

    signal.signal(signal.SIGINT, request_stop)
    signal.signal(signal.SIGTERM, request_stop)
    settings.bridge_dir.mkdir(parents=True, exist_ok=True)
    bridge = SensorBridgeStore(settings.bridge_dir)

    while not stopping:
        while capture_request_active(settings) and not stopping:
            time.sleep(0.1)
        if stopping:
            break

        device_descriptor = _open_lock(settings.device_lock_path)
        try:
            while not _try_exclusive_lock(device_descriptor):
                if stopping:
                    return 0
                time.sleep(0.1)
            if capture_request_active(settings):
                continue

            runtime: Optional[MonitorRuntime] = None
            try:
                if runtime_loader is None:
                    runtime = load_monitor_runtime(
                        settings,
                        pipeline_environment=pipeline_environment,
                        relay_path=relay_path,
                    )
                else:
                    runtime = runtime_loader(settings)
            except (KeyError, OSError, ValueError) as error:
                # Registration atomically replaces current and pipeline.json in
                # separate operations.  A monitor reload can observe that short
                # transition, and an administrator can temporarily unregister a
                # pipeline.  Stay alive and retry instead of entering a systemd
                # restart loop and leaving sensor status permanently stale.
                print(
                    f"Sensor monitor pipeline is temporarily unavailable; retrying: {error}",
                    file=sys.stderr,
                    flush=True,
                )

            if runtime is not None:
                if runtime.user != user:
                    raise ValueError(
                        "Sensor monitor pipeline user changed; restarting supervisor"
                    )
                print(f"Starting boot sensor monitor for {settings.pipeline_id}", flush=True)
                watchdog = CameraProgressWatchdog(
                    bridge.status(), time.monotonic(),
                    camera_required=runtime.camera_required,
                )
                next_health_check = 0.0
                try:
                    child = subprocess.Popen(
                        runtime.command,
                        executable=runtime.command[0],
                        cwd=runtime.working_directory,
                        env=dict(runtime.environment),
                        start_new_session=True,
                    )
                except OSError as error:
                    print(f"Could not start boot sensor monitor: {error}", file=sys.stderr, flush=True)
                    child = None

            while runtime is not None and child is not None and child.poll() is None:
                # Handoff takes priority over reloads and health recovery. Keep
                # the lock until all children are gone, even if C++ close hangs.
                if stopping:
                    stop_child(child)
                    break
                if capture_request_active(settings):
                    stop_child(child, timeout_seconds=2.0, terminate_timeout_seconds=2.0)
                    break
                try:
                    release_changed = (
                        (settings.registry_root / settings.pipeline_id / "current").resolve(
                            strict=True
                        )
                        != runtime.release
                    )
                except OSError:
                    release_changed = True
                if release_changed:
                    print(
                        "Sensor monitor release changed; reloading",
                        flush=True,
                    )
                if release_changed:
                    stop_child(child)
                    break
                now = time.monotonic()
                if now >= next_health_check:
                    next_health_check = now + 1.0
                    relay_changed = mobile_rtk_relay_environment(
                        settings.pipeline_id, relay_path,
                    ) != runtime.relay_environment
                    camera_stalled = watchdog.stalled(bridge.status(), now)
                    if relay_changed or camera_stalled:
                        print(
                            "Sensor monitor relay changed; reloading" if relay_changed else
                            "Sensor monitor camera/IMU progress stalled; restarting child",
                            flush=True,
                        )
                        # Recovery must not wait on the child's C++ close/GIL.
                        stop_child(child, timeout_seconds=2.0, terminate_timeout_seconds=2.0)
                        break
                time.sleep(0.1)
            if child is not None:
                # The direct Python process may have left helpers in its process
                # group.  Keep the device lock until the entire group is gone.
                stop_child(child)
                return_code = child.poll()
                if return_code not in (None, 0) and not stopping:
                    print(
                        f"Boot sensor monitor exited with status {return_code}; retrying",
                        file=sys.stderr,
                        flush=True,
                    )
            child = None
        finally:
            if child is not None:
                stop_child(child)
                child = None
            os.close(device_descriptor)

        if not stopping and not capture_request_active(settings):
            # The watchdog already gives each child a startup grace period.
            # Extra camera backoff here would also leave GNSS/RTK stopped.
            deadline = time.monotonic() + retry_seconds
            while time.monotonic() < deadline and not stopping:
                if capture_request_active(settings):
                    break
                time.sleep(min(0.1, max(0.0, deadline - time.monotonic())))
    return 0


def main(argv: Optional[Sequence[str]] = None) -> int:
    arguments = list(sys.argv[1:] if argv is None else argv)
    check_only = bool(arguments and arguments[0] == "--check-config")
    if check_only:
        arguments.pop(0)
    if len(arguments) > 1:
        print(
            "Usage: python -m jetson_control.sensor_monitor [--check-config] [config.json]",
            file=sys.stderr,
        )
        return 2
    config_path = Path(
        arguments[0]
        if arguments
        else os.environ.get(
            "JETSON_SENSOR_MONITOR_CONFIG",
            str(DEFAULT_SENSOR_MONITOR_CONFIG),
        )
    )
    try:
        settings = SensorMonitorSettings.load(config_path, expected_owner_uid=0)
        runtime = load_monitor_runtime(settings)
        if check_only:
            return 0
        drop_privileges(runtime.user)
        return supervise(
            settings,
            runtime.user,
            pipeline_environment=runtime.pipeline_environment,
        )
    except (KeyError, OSError, ValueError) as error:
        print(str(error), file=sys.stderr, flush=True)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
