#!/usr/bin/env python3
from __future__ import annotations

import hashlib
import json
import os
import re
import signal
import stat
import subprocess
import sys
import time
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Callable, Mapping, Optional

from jetson_control.sensor_handoff import (
    DEFAULT_SENSOR_MONITOR_CONFIG,
    CaptureDeviceLease,
    settings_for_pipeline,
)
from jetson_control.mobile_rtk import MobileRtkRelayRegistry
from jetson_control.field_quality import FieldQualitySampler
from jetson_control.sensors import SensorBridgeStore


PIPELINE_ID = re.compile(r"^[a-z0-9][a-z0-9_.-]{0,63}$")
LOG_FILE = re.compile(r"^run-\d{8}T\d{6}\.\d{6}Z-\d+\.log$")
REGISTRY_ROOT = Path(os.environ.get("JETSON_PIPELINE_REGISTRY", "/opt/jetson-pipelines"))
TIME_SYNC_MARKER = Path(
    os.environ.get(
        "JETSON_PIPELINE_TIME_SYNC_MARKER",
        "/run/jetson-control/time-synchronized.json",
    )
)
SENSOR_MONITOR_CONFIG = Path(
    os.environ.get(
        "JETSON_SENSOR_MONITOR_CONFIG",
        str(DEFAULT_SENSOR_MONITOR_CONFIG),
    )
)
MOBILE_RTK_RELAY = Path(
    os.environ.get(
        "JETSON_CONTROL_MOBILE_RTK_RELAY",
        "/run/jetson-control/mobile-rtk-relay.json",
    )
)
MAX_LOG_FILES = 20
MAX_LOG_TOTAL_BYTES = 1024 * 1024 * 1024
MAX_RUN_LOG_BYTES = 128 * 1024 * 1024
LOG_TRUNCATED = b"\n=== file log limit reached; output continues in journald ===\n"
LOG_WRITE_FAILED = b"\n=== file log write failed; output continues in journald ===\n"
DEFAULT_MIN_FREE_BYTES = 0
STORAGE_PREFLIGHT_EXIT_CODE = 78
RUN_CONTEXT_SCHEMA_VERSION = 1
CONTEXTUAL_LAUNCH_MAX_AGE_MILLIS = 120_000


class StoragePreflightError(RuntimeError):
    pass


def read_root_owned_json(path: Path, expected_owner_uid: int) -> Mapping[str, Any]:
    flags = os.O_RDONLY | getattr(os, "O_NOFOLLOW", 0)
    try:
        descriptor = os.open(path, flags)
    except OSError as error:
        raise StoragePreflightError(f"Could not read contextual run input: {error}") from error
    try:
        metadata = os.fstat(descriptor)
        if (
            not stat.S_ISREG(metadata.st_mode)
            or metadata.st_uid != expected_owner_uid
            or metadata.st_mode & (stat.S_IWGRP | stat.S_IWOTH)
            or metadata.st_size > 1024 * 1024
        ):
            raise StoragePreflightError("Contextual run input permissions are unsafe")
        encoded = os.pread(descriptor, metadata.st_size, 0)
    finally:
        os.close(descriptor)
    try:
        value = json.loads(encoded.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as error:
        raise StoragePreflightError("Contextual run input is invalid") from error
    if not isinstance(value, dict):
        raise StoragePreflightError("Contextual run input is invalid")
    return value


def current_boot_id(path: Path = Path("/proc/sys/kernel/random/boot_id")) -> str:
    try:
        value = path.read_text(encoding="ascii").strip().lower()
    except OSError as error:
        raise StoragePreflightError("Could not read device boot identity") from error
    if re.fullmatch(r"[0-9a-f-]{32,36}", value) is None:
        raise StoragePreflightError("Device boot identity is invalid")
    return value


def load_contextual_launch(
    pipeline_root: Path,
    pipeline_id: str,
    *,
    expected_owner_uid: int,
    clock_millis: Callable[[], int] = lambda: int(time.time() * 1000),
    boot_id: Callable[[], str] = current_boot_id,
) -> Optional[Mapping[str, Any]]:
    """Load an API-prepared launch; a configured policy forbids legacy starts."""

    policy_path = pipeline_root / "run-policy.json"
    if not policy_path.exists():
        return None
    policy = read_root_owned_json(policy_path, expected_owner_uid)
    if (
        policy.get("schemaVersion") != 1
        or policy.get("pipelineId") != pipeline_id
        or policy.get("configured") is not True
    ):
        raise StoragePreflightError("Configured run policy is invalid")
    launch = read_root_owned_json(pipeline_root / "next-run.json", expected_owner_uid)
    run_id = launch.get("runId")
    log_id = launch.get("logId")
    started_at = launch.get("startedAtEpochMillis")
    age = clock_millis() - started_at if isinstance(started_at, int) and not isinstance(started_at, bool) else None
    if (
        launch.get("schemaVersion") != RUN_CONTEXT_SCHEMA_VERSION
        or launch.get("pipelineId") != pipeline_id
        or not isinstance(run_id, str)
        or run_id != pipeline_id + "/" + str(log_id)
        or not isinstance(log_id, str)
        or LOG_FILE.fullmatch(log_id) is None
        or not isinstance(launch.get("policySnapshot"), dict)
        or launch["policySnapshot"].get("revision") != policy.get("revision")
        or launch.get("bootId") != boot_id()
        or age is None
        or age < 0
        or age > CONTEXTUAL_LAUNCH_MAX_AGE_MILLIS
    ):
        raise StoragePreflightError("Contextual launch identity is invalid")
    return launch


def contextual_storage(
    launch: Mapping[str, Any], manifest: Mapping[str, Any]
) -> Tuple[List[Path], Path, int]:
    output_value = launch.get("outputDirectory")
    policy = launch.get("policySnapshot")
    if not isinstance(output_value, str) or not output_value or not isinstance(policy, dict):
        raise StoragePreflightError("Contextual output is invalid")
    output = Path(output_value)
    if not output.is_absolute():
        raise StoragePreflightError("Contextual output must be absolute")
    try:
        metadata = os.lstat(output)
        output = output.resolve(strict=True)
    except OSError as error:
        raise StoragePreflightError(f"Contextual output is unavailable: {error}") from error
    if stat.S_ISLNK(metadata.st_mode) or not stat.S_ISDIR(metadata.st_mode):
        raise StoragePreflightError("Contextual output is unsafe")
    writable, _ = resolve_writable_storage(manifest)
    if not any(_is_relative_to(output, root) for root in writable):
        raise StoragePreflightError("Contextual output is outside registered writable storage")
    required = policy.get("minFreeBytes")
    if isinstance(required, bool) or not isinstance(required, int) or required < 0:
        raise StoragePreflightError("Contextual storage policy is invalid")
    return [output], output, required


def _is_relative_to(path: Path, parent: Path) -> bool:
    try:
        path.relative_to(parent)
        return True
    except ValueError:
        return False


def contextual_sensor_preflight(
    launch: Mapping[str, Any], bridge_dir: Path, observed_at_millis: int
) -> Mapping[str, object]:
    policy = launch.get("policySnapshot")
    if not isinstance(policy, dict):
        raise StoragePreflightError("Contextual sensor policy is invalid")
    required = policy.get("requiredSensors")
    optional = policy.get("optionalSensors")
    if not isinstance(required, list) or not isinstance(optional, list):
        raise StoragePreflightError("Contextual sensor policy is invalid")
    supported = {"camera", "gnss", "imu"}
    if any(sensor not in supported for sensor in required + optional):
        raise StoragePreflightError("Contextual sensor policy contains an unsupported sensor")
    requirements = {sensor: "REQUIRED" for sensor in required}
    requirements.update({sensor: "OPTIONAL" for sensor in optional})
    observation = FieldQualitySampler(requirements).observe(
        SensorBridgeStore(bridge_dir).status(), observed_at_millis
    )
    missing = [sensor for sensor in required if observation["sensors"][sensor]["state"] != "ACTIVE"]
    if missing:
        raise StoragePreflightError("Required sensors are not ready: " + ",".join(missing))
    return observation


def validate_contextual_launch(
    launch: Mapping[str, Any],
    pipeline_id: str,
    manifest: Mapping[str, Any],
    release: Path,
    config_sha256: str,
) -> None:
    context = launch.get("contextSnapshot")
    preflight = launch.get("preflightSnapshot")
    output_context = launch.get("outputContext")
    policy = launch.get("policySnapshot")
    run_id = launch.get("runId")
    if not all(isinstance(value, dict) for value in (context, preflight, output_context, policy)):
        raise StoragePreflightError("Contextual launch snapshots are invalid")
    assert isinstance(context, dict) and isinstance(preflight, dict)
    assert isinstance(output_context, dict) and isinstance(policy, dict)
    expected = {
        "surveyProjectId": context.get("surveyProjectId"),
        "surveySectionId": context.get("surveySectionId"),
        "runId": run_id,
        "deviceId": launch.get("deviceId"),
        "pipelineId": pipeline_id,
        "sourceRevision": manifest.get("source_revision"),
        "configSha256": config_sha256,
        "outputId": launch.get("output", {}).get("outputId") if isinstance(launch.get("output"), dict) else None,
    }
    if (
        launch.get("sourceRevision") != manifest.get("source_revision")
        or launch.get("sourceDirty") != manifest.get("source_dirty")
        or launch.get("release") != str(release)
        or launch.get("configRevision") != config_sha256
        or preflight.get("ready") is not True
        or preflight.get("contextSnapshot") != context
        or not isinstance(preflight.get("policy"), dict)
        or preflight["policy"].get("revision") != policy.get("revision")
        or any(output_context.get(key) != value for key, value in expected.items())
    ):
        raise StoragePreflightError("Pipeline source, config, context, or preflight changed")


def service_exit_code(application_exit_code: int) -> int:
    """Keep the runner-only preflight status distinct from child exit codes."""

    return (
        1
        if application_exit_code == STORAGE_PREFLIGHT_EXIT_CODE
        else application_exit_code
    )


def fail(message: str) -> "NoReturn":
    print(message, file=sys.stderr)
    raise SystemExit(1)


def relative_path(value: object, kind: str) -> Path:
    if not isinstance(value, str):
        fail(f"Manifest {kind} must be a string")
    path = Path(value)
    if not value or path.is_absolute() or ".." in path.parts or "\x00" in value:
        fail(f"Manifest {kind} is invalid")
    return path


def required_string(manifest: Mapping[str, Any], key: str) -> str:
    value = manifest.get(key)
    if not isinstance(value, str) or not value:
        fail(f"Manifest field is invalid: {key}")
    return value


def utc_now() -> datetime:
    return datetime.now(timezone.utc)


def time_sync_ready(path: Path, *, expected_owner_uid: int = 0) -> bool:
    flags = os.O_RDONLY
    if hasattr(os, "O_NOFOLLOW"):
        flags |= os.O_NOFOLLOW
    try:
        descriptor = os.open(path, flags)
    except OSError:
        return False
    try:
        metadata = os.fstat(descriptor)
        if (
            not stat.S_ISREG(metadata.st_mode)
            or metadata.st_uid != expected_owner_uid
            or metadata.st_mode & (stat.S_IWGRP | stat.S_IWOTH)
        ):
            return False
        with os.fdopen(os.dup(descriptor), "r", encoding="utf-8") as source:
            value = json.load(source)
    except (json.JSONDecodeError, OSError, UnicodeDecodeError):
        return False
    finally:
        os.close(descriptor)
    if not (
        isinstance(value, dict)
        and value.get("schemaVersion") == 1
        and value.get("synchronized") is True
        and value.get("source") == "MOBILE"
    ):
        return False
    return all(
        isinstance(value.get(key), int) and not isinstance(value.get(key), bool)
        for key in (
            "sourceTimeEpochMillis",
            "synchronizedAtEpochMillis",
            "offsetBeforeMillis",
        )
    )


def wait_for_time_sync(
    path: Path = TIME_SYNC_MARKER,
    *,
    expected_owner_uid: int = 0,
    cancelled: Callable[[], bool] = lambda: False,
    sleep: Callable[[float], None] = time.sleep,
) -> bool:
    announced = False
    while not time_sync_ready(path, expected_owner_uid=expected_owner_uid):
        if cancelled():
            return False
        if not announced:
            print("Waiting for authenticated mobile system-time synchronization", flush=True)
            announced = True
        sleep(1.0)
    if announced:
        print("Mobile system-time synchronization confirmed", flush=True)
    return True


def mobile_rtk_relay_environment(
    pipeline_id: str,
    path: Path = MOBILE_RTK_RELAY,
    *,
    expected_owner_uid: int = 0,
    clock_millis: Callable[[], int] = lambda: int(time.time() * 1000),
) -> Mapping[str, str]:
    relay = MobileRtkRelayRegistry(
        path,
        clock_millis=clock_millis,
        owner_uid=expected_owner_uid,
    ).read()
    if relay is None or relay.get("pipelineId") != pipeline_id:
        return {}
    return {
        "NTRIP_HOST": str(relay["relayHost"]),
        "NTRIP_PORT": str(relay["relayPort"]),
        "JETSON_PIPELINE_MOBILE_RTK_RELAY": "1",
    }


def process_group_exists(process_group_id: int) -> bool:
    try:
        os.killpg(process_group_id, 0)
        return True
    except ProcessLookupError:
        return False
    except PermissionError:
        return True


def signal_process_group(process_group_id: int, signum: int) -> None:
    try:
        os.killpg(process_group_id, signum)
    except ProcessLookupError:
        pass


def stop_process_group(
    child: subprocess.Popen,
    *,
    signum: int = signal.SIGTERM,
    timeout_seconds: float = 5.0,
) -> None:
    process_group_id = child.pid
    if not process_group_exists(process_group_id):
        child.poll()
        return
    signal_process_group(process_group_id, signum)
    deadline = time.monotonic() + timeout_seconds
    while process_group_exists(process_group_id) and time.monotonic() < deadline:
        child.poll()
        time.sleep(0.1)
    if process_group_exists(process_group_id):
        signal_process_group(process_group_id, signal.SIGKILL)
        deadline = time.monotonic() + timeout_seconds
        while process_group_exists(process_group_id) and time.monotonic() < deadline:
            child.poll()
            time.sleep(0.1)
    if process_group_exists(process_group_id):
        raise RuntimeError("Pipeline process group did not stop")
    try:
        child.wait(timeout=max(0.1, timeout_seconds))
    except subprocess.TimeoutExpired as error:
        raise RuntimeError("Pipeline process did not stop") from error


def minimum_free_bytes(environment: Mapping[str, str]) -> int:
    raw = environment.get("JETSON_PIPELINE_MIN_FREE_BYTES")
    if raw is None:
        return DEFAULT_MIN_FREE_BYTES
    if not raw.isascii() or not raw.isdecimal():
        raise StoragePreflightError(
            "JETSON_PIPELINE_MIN_FREE_BYTES must be a non-negative integer"
        )
    value = int(raw)
    if value > (1 << 63) - 1:
        raise StoragePreflightError("JETSON_PIPELINE_MIN_FREE_BYTES is too large")
    return value


def preflight_writable_storage(
    paths: list[Path],
    required_bytes: int,
) -> Optional[int]:
    """Verify capacity and a create/fsync/unlink cycle as the service user."""

    available_values = []
    for path in paths:
        try:
            usage = os.statvfs(path)
        except OSError as error:
            raise StoragePreflightError(
                f"Could not inspect pipeline storage {path}: {error}"
            ) from error
        available = usage.f_bavail * usage.f_frsize
        available_values.append(available)
        if available < required_bytes:
            raise StoragePreflightError(
                f"Pipeline storage {path} has {available} bytes available; "
                f"{required_bytes} bytes are required"
            )

        probe = path / f".jetson-pipeline-preflight-{os.getpid()}-{time.time_ns()}"
        flags = os.O_WRONLY | os.O_CREAT | os.O_EXCL
        if hasattr(os, "O_NOFOLLOW"):
            flags |= os.O_NOFOLLOW
        descriptor: Optional[int] = None
        try:
            descriptor = os.open(probe, flags, 0o600)
            os.write(descriptor, b"1")
            os.fsync(descriptor)
        except OSError as error:
            raise StoragePreflightError(
                f"Pipeline storage {path} is not writable: {error}"
            ) from error
        finally:
            if descriptor is not None:
                os.close(descriptor)
            try:
                probe.unlink()
            except FileNotFoundError:
                pass
            except OSError as error:
                raise StoragePreflightError(
                    f"Could not remove pipeline storage probe {probe}: {error}"
                ) from error
    return min(available_values) if available_values else None


def resolve_writable_storage(
    manifest: Mapping[str, Any],
) -> tuple[list[Path], Optional[Path]]:
    writable_values = manifest.get("writable_paths", [])
    if not isinstance(writable_values, list) or any(
        not isinstance(value, str) for value in writable_values
    ):
        raise StoragePreflightError(
            "Manifest writable_paths must be an array of strings"
        )
    resolved_writable_paths = []
    for value in writable_values:
        requested = Path(value)
        if not requested.is_absolute():
            raise StoragePreflightError("Manifest writable path must be absolute")
        try:
            metadata = os.lstat(requested)
            resolved = requested.resolve(strict=True)
        except OSError as error:
            raise StoragePreflightError(
                f"Pipeline writable path is unavailable: {error}"
            ) from error
        if stat.S_ISLNK(metadata.st_mode) or not stat.S_ISDIR(metadata.st_mode):
            raise StoragePreflightError("Pipeline writable path is unsafe")
        if resolved not in resolved_writable_paths:
            resolved_writable_paths.append(resolved)

    results_directory: Optional[Path] = None
    results_value = manifest.get("results_directory")
    if results_value:
        if not isinstance(results_value, str):
            raise StoragePreflightError(
                "Manifest results_directory must be a string"
            )
        requested_results = Path(results_value)
        if not requested_results.is_absolute():
            raise StoragePreflightError(
                "Manifest results_directory must be absolute"
            )
        try:
            results_metadata = os.lstat(requested_results)
            results_directory = requested_results.resolve(strict=True)
        except OSError as error:
            raise StoragePreflightError(
                f"Pipeline results directory is unavailable: {error}"
            ) from error
        if stat.S_ISLNK(results_metadata.st_mode) or not stat.S_ISDIR(
            results_metadata.st_mode
        ):
            raise StoragePreflightError("Pipeline results path is unsafe")
        if results_directory not in resolved_writable_paths:
            raise StoragePreflightError(
                "Pipeline results directory is not an approved writable path"
            )
    return resolved_writable_paths, results_directory


def prepare_log_directory() -> Path:
    value = os.environ.get("LOGS_DIRECTORY", "")
    directory = Path(value)
    if not value or not directory.is_absolute():
        fail("Pipeline log directory is unavailable")
    try:
        metadata = os.lstat(directory)
    except OSError as error:
        fail(f"Could not access pipeline log directory: {error}")
    if stat.S_ISLNK(metadata.st_mode) or not stat.S_ISDIR(metadata.st_mode):
        fail("Pipeline log directory is unsafe")
    return directory


def prune_logs(directory: Path) -> None:
    candidates = []
    try:
        entries = list(directory.iterdir())
    except OSError as error:
        fail(f"Could not inspect pipeline logs: {error}")
    for path in entries:
        if not LOG_FILE.fullmatch(path.name):
            continue
        try:
            metadata = os.lstat(path)
        except FileNotFoundError:
            continue
        if not stat.S_ISREG(metadata.st_mode):
            continue
        candidates.append((metadata.st_mtime_ns, metadata.st_size, path))

    retained_bytes = 0
    byte_budget = max(0, MAX_LOG_TOTAL_BYTES - MAX_RUN_LOG_BYTES)
    for index, (_, size, path) in enumerate(sorted(candidates, reverse=True)):
        if index < MAX_LOG_FILES - 1 and retained_bytes + size <= byte_budget:
            retained_bytes += size
            continue
        try:
            path.unlink()
            Path(str(path) + ".route.jsonl").unlink(missing_ok=True)
        except FileNotFoundError:
            pass


class RunLogWriter:
    def __init__(
        self,
        directory: Path,
        log_id: Optional[str] = None,
        started_at: Optional[str] = None,
    ) -> None:
        started = utc_now()
        self.started_at = started_at or started.isoformat().replace("+00:00", "Z")
        name = log_id or f"run-{started.strftime('%Y%m%dT%H%M%S.%fZ')}-{os.getpid()}.log"
        if LOG_FILE.fullmatch(name) is None:
            raise ValueError("Run log identity is invalid")
        self.path = directory / name
        flags = os.O_WRONLY | os.O_CREAT | os.O_EXCL
        if hasattr(os, "O_NOFOLLOW"):
            flags |= os.O_NOFOLLOW
        descriptor = os.open(self.path, flags, 0o640)
        self.output = os.fdopen(descriptor, "wb", buffering=0)
        self.written = 0
        self.truncated = False

    @staticmethod
    def _journal(data: bytes) -> None:
        try:
            sys.stdout.buffer.write(data)
            sys.stdout.buffer.flush()
        except (AttributeError, BrokenPipeError, OSError):
            pass

    def emit(self, data: bytes) -> None:
        if not data:
            return
        self._journal(data)
        if self.truncated:
            return
        remaining = MAX_RUN_LOG_BYTES - self.written
        try:
            if remaining > 0:
                chunk = data[:remaining]
                if self.output.write(chunk) != len(chunk):
                    raise OSError("short pipeline log write")
                self.written += len(chunk)
            if len(data) > remaining:
                if self.output.write(LOG_TRUNCATED) != len(LOG_TRUNCATED):
                    raise OSError("short pipeline log marker write")
                self.written += len(LOG_TRUNCATED)
                self.truncated = True
        except OSError:
            self.truncated = True
            self._journal(LOG_WRITE_FAILED)

    def emit_footer(self, data: bytes) -> None:
        """Persist bounded launcher metadata after child output was truncated."""

        self._journal(data)
        try:
            if self.output.write(data) != len(data):
                raise OSError("short pipeline log footer write")
        except OSError:
            self._journal(LOG_WRITE_FAILED)

    def close(self) -> None:
        try:
            try:
                os.fsync(self.output.fileno())
            except OSError:
                self._journal(LOG_WRITE_FAILED)
        finally:
            try:
                self.output.close()
            except OSError:
                self._journal(LOG_WRITE_FAILED)


def main() -> int:
    if len(sys.argv) != 2 or not PIPELINE_ID.fullmatch(sys.argv[1]):
        fail("Usage: run-pipeline.py <pipeline-id>")
    pipeline_id = sys.argv[1]
    pipeline_root = REGISTRY_ROOT / pipeline_id
    manifest_path = pipeline_root / "pipeline.json"
    try:
        manifest_stat = manifest_path.stat()
        if manifest_stat.st_uid != 0 or manifest_stat.st_mode & (stat.S_IWGRP | stat.S_IWOTH):
            fail("Pipeline manifest ownership or permissions are unsafe")
        with manifest_path.open("r", encoding="utf-8") as source:
            manifest = json.load(source)
    except (FileNotFoundError, json.JSONDecodeError, OSError) as error:
        fail(f"Could not load pipeline manifest: {error}")
    if not isinstance(manifest, dict) or manifest.get("schema_version") != 1:
        fail("Pipeline manifest schema is invalid")
    if manifest.get("id") != pipeline_id:
        fail("Pipeline manifest identity mismatch")

    releases_root = (pipeline_root / "releases").resolve(strict=True)
    release = (pipeline_root / "current").resolve(strict=True)
    try:
        release.relative_to(releases_root)
    except ValueError:
        fail("Current pipeline release leaves the release directory")
    for path in (pipeline_root, releases_root, release):
        path_stat = path.stat()
        if path_stat.st_uid != 0 or path_stat.st_mode & (stat.S_IWGRP | stat.S_IWOTH):
            fail(f"Pipeline release ownership or permissions are unsafe: {path}")

    virtualenv = Path(required_string(manifest, "virtualenv")).resolve(strict=True)
    python = Path(required_string(manifest, "python"))
    expected_python = virtualenv / "bin" / "python"
    if python != expected_python:
        fail("Selected Python does not belong to the configured virtualenv")
    if not python.is_file() or not os.access(python, os.X_OK):
        fail("Selected virtualenv Python is not executable")
    working_directory = Path(required_string(manifest, "working_directory")).resolve(strict=True)
    if not working_directory.is_dir():
        fail("Pipeline working directory is unavailable")

    entrypoint_relative = relative_path(manifest.get("entrypoint"), "entrypoint")
    config_relative = relative_path(manifest.get("config"), "config")
    entrypoint = (release / entrypoint_relative).resolve(strict=True)
    config = (release / config_relative).resolve(strict=True)
    for path, kind in ((entrypoint, "entrypoint"), (config, "config")):
        try:
            path.relative_to(release)
        except ValueError:
            fail(f"Pipeline {kind} leaves the current release")
        if not path.is_file():
            fail(f"Pipeline {kind} is not a file")

    arguments = manifest.get("arguments", [])
    if not isinstance(arguments, list) or any(not isinstance(value, str) for value in arguments):
        fail("Pipeline arguments must be an array of strings")
    config_argument = required_string(manifest, "config_argument")

    environment = os.environ.copy()
    environment.update(
        {
            "JETSON_PIPELINE_ID": pipeline_id,
            "JETSON_PIPELINE_RELEASE": str(release),
            "JETSON_PIPELINE_CONFIG": str(config),
            "PATH": f"{python.parent}:{environment.get('PATH', '')}",
            "PYTHONPATH": (
                f"{release}:{environment['PYTHONPATH']}"
                if environment.get("PYTHONPATH")
                else str(release)
            ),
            "PYTHONUNBUFFERED": "1",
            "PYTHONDONTWRITEBYTECODE": "1",
        }
    )
    invocation_id = environment.get("INVOCATION_ID", "")
    if invocation_id and not re.fullmatch(r"[0-9a-f]{32}", invocation_id):
        fail("Systemd invocation identity is invalid")
    relay_environment = mobile_rtk_relay_environment(pipeline_id)
    environment.update(relay_environment)
    if relay_environment:
        print(
            "Using authenticated mobile-data RTK relay at "
            f"{relay_environment['NTRIP_HOST']}:{relay_environment['NTRIP_PORT']}",
            flush=True,
        )
    os.chdir(working_directory)
    command = [
        str(python),
        "-u",
        str(entrypoint),
        config_argument,
        str(config),
        *arguments,
    ]
    child: Optional[subprocess.Popen] = None
    pending_signal: Optional[int] = None

    def forward_signal(signum: int, _frame: object) -> None:
        nonlocal pending_signal
        pending_signal = signum
        if child is not None and process_group_exists(child.pid):
            signal_process_group(child.pid, signum)

    signal.signal(signal.SIGINT, forward_signal)
    signal.signal(signal.SIGTERM, forward_signal)

    try:
        config_sha256 = hashlib.sha256(config.read_bytes()).hexdigest()
    except OSError as error:
        fail(f"Could not hash pipeline config: {error}")
    try:
        contextual_launch = load_contextual_launch(
            pipeline_root,
            pipeline_id,
            expected_owner_uid=manifest_stat.st_uid,
        )
        if contextual_launch is not None:
            validate_contextual_launch(contextual_launch, pipeline_id, manifest, release, config_sha256)
    except StoragePreflightError as error:
        print(f"Contextual start rejected: {error}", file=sys.stderr, flush=True)
        return STORAGE_PREFLIGHT_EXIT_CODE
    if contextual_launch is None and not wait_for_time_sync(
        cancelled=lambda: pending_signal is not None
    ):
        return 128 + int(pending_signal or signal.SIGTERM)

    log_directory = prepare_log_directory()
    environment["JETSON_PIPELINE_LOGS_DIR"] = str(log_directory)
    prune_logs(log_directory)
    try:
        writer = RunLogWriter(
            log_directory,
            str(contextual_launch["logId"]) if contextual_launch is not None else None,
            str(contextual_launch["startedAt"]) if contextual_launch is not None else None,
        )
    except FileExistsError:
        print("Contextual run was already consumed", file=sys.stderr, flush=True)
        return STORAGE_PREFLIGHT_EXIT_CODE
    required_storage_bytes: Optional[int] = None
    try:
        if contextual_launch is None:
            required_storage_bytes = minimum_free_bytes(environment)
            resolved_writable_paths, results_directory = resolve_writable_storage(manifest)
        else:
            resolved_writable_paths, results_directory, required_storage_bytes = contextual_storage(
                contextual_launch, manifest
            )
            if not time_sync_ready(TIME_SYNC_MARKER):
                raise StoragePreflightError("Authenticated device time is not ready")
        available_storage_bytes = preflight_writable_storage(
            resolved_writable_paths,
            required_storage_bytes,
        )
        storage_preflight = "passed" if resolved_writable_paths else "not_configured"
    except StoragePreflightError as error:
        available_storage_bytes = None
        storage_preflight = "failed"
        preflight_error = str(error).replace("\n", " ")
        header = (
            "=== Jetson pipeline run ===\n"
            f"started_at={writer.started_at}\n"
            f"pipeline_id={pipeline_id}\n"
            f"invocation_id={invocation_id}\n"
            f"release={release}\n"
            f"source_revision={manifest.get('source_revision', '')}\n"
            f"source_dirty={'true' if manifest.get('source_dirty') is True else 'false'}\n"
            f"run_id={contextual_launch.get('runId', '') if contextual_launch is not None else ''}\n"
            "storage_preflight=failed\n"
            f"storage_preflight_error={preflight_error}\n\n"
        ).encode("utf-8")
        writer.emit(header)
        finished_at = utc_now().isoformat().replace("+00:00", "Z")
        writer.emit_footer(
            (
                "\n=== Jetson pipeline run finished ===\n"
                f"finished_at={finished_at}\n"
                f"exit_code={STORAGE_PREFLIGHT_EXIT_CODE}\n"
                "terminal_state=FAILED\n"
                "stop_signal=\n"
            ).encode("ascii")
        )
        writer.close()
        print(f"Pipeline storage preflight failed: {error}", file=sys.stderr, flush=True)
        return STORAGE_PREFLIGHT_EXIT_CODE
    if results_directory is not None:
        environment["JETSON_PIPELINE_RESULTS_DIR"] = str(results_directory)
    if contextual_launch is not None:
        environment["JETSON_PIPELINE_RUN_ID"] = str(contextual_launch["runId"])
        environment["JETSON_PIPELINE_OUTPUT_ID"] = str(contextual_launch["output"]["outputId"])
    header = (
        "=== Jetson pipeline run ===\n"
        f"started_at={writer.started_at}\n"
        f"pipeline_id={pipeline_id}\n"
        f"invocation_id={invocation_id}\n"
        f"release={release}\n"
        f"source_revision={manifest.get('source_revision', '')}\n"
        f"source_dirty={'true' if manifest.get('source_dirty') is True else 'false'}\n"
        f"config_sha256={config_sha256}\n"
        f"run_id={contextual_launch.get('runId', '') if contextual_launch is not None else ''}\n"
        f"survey_project_id={contextual_launch.get('contextSnapshot', {}).get('surveyProjectId', '') if contextual_launch is not None else ''}\n"
        f"survey_section_id={contextual_launch.get('contextSnapshot', {}).get('surveySectionId', '') if contextual_launch is not None else ''}\n"
        f"policy_revision={contextual_launch.get('policySnapshot', {}).get('revision', '') if contextual_launch is not None else ''}\n"
        f"output_id={contextual_launch.get('output', {}).get('outputId', '') if contextual_launch is not None else ''}\n"
        f"results_directory={results_directory or ''}\n"
        f"storage_preflight={storage_preflight}\n"
        f"storage_available_bytes={available_storage_bytes if available_storage_bytes is not None else ''}\n"
        f"storage_required_bytes={required_storage_bytes}\n\n"
    ).encode("utf-8")
    writer.emit(header)
    from jetson_control.route_recorder import RouteRecorder
    route_recorder = None
    sensor_lease: Optional[CaptureDeviceLease] = None
    run_finished = False

    def finish_run(exit_code: int) -> int:
        nonlocal run_finished
        if not run_finished:
            finished_at = utc_now().isoformat().replace("+00:00", "Z")
            terminal_state = (
                "STOPPED" if pending_signal is not None
                else "COMPLETED" if exit_code == 0
                else "FAILED"
            )
            writer.emit_footer(
                (
                    "\n=== Jetson pipeline run finished ===\n"
                    f"finished_at={finished_at}\n"
                    f"exit_code={exit_code}\n"
                    f"terminal_state={terminal_state}\n"
                    f"stop_signal={pending_signal or ''}\n"
                ).encode("ascii")
            )
            run_finished = True
        # Exit 78 is reserved for the runner's preflight result. Preserve an
        # application's real 78 in the log while allowing systemd recovery.
        return service_exit_code(exit_code)

    try:
        if contextual_launch is not None:
            contextual_sensor_preflight(
                contextual_launch,
                Path(environment.get(
                    "JETSON_PIPELINE_SENSOR_BRIDGE_DIR",
                    environment.get("JETSON_CONTROL_SENSOR_BRIDGE_DIR", "/var/lib/jetson-sensors"),
                )),
                int(time.time() * 1000),
            )
        monitor_settings = settings_for_pipeline(pipeline_id, SENSOR_MONITOR_CONFIG)
        if monitor_settings is not None:
            print("Requesting sensor devices from the boot monitor", flush=True)
            sensor_lease = CaptureDeviceLease(monitor_settings, pipeline_id)
            if not sensor_lease.acquire(cancelled=lambda: pending_signal is not None):
                return finish_run(128 + int(pending_signal or signal.SIGTERM))
            environment["JETSON_PIPELINE_SENSOR_BRIDGE_DIR"] = str(
                monitor_settings.bridge_dir
            )
            print("Sensor devices handed off to the capture pipeline", flush=True)
        sensor_requirements = None
        if contextual_launch is not None:
            policy_snapshot = contextual_launch["policySnapshot"]
            sensor_requirements = {
                sensor: "REQUIRED" for sensor in policy_snapshot["requiredSensors"]
            }
            sensor_requirements.update({
                sensor: "OPTIONAL" for sensor in policy_snapshot["optionalSensors"]
            })
        route_recorder = RouteRecorder(writer.path, Path(environment.get(
            "JETSON_PIPELINE_SENSOR_BRIDGE_DIR", environment.get("JETSON_CONTROL_SENSOR_BRIDGE_DIR", "/var/lib/jetson-sensors"))),
            sensor_requirements=sensor_requirements)
        route_recorder.start()
        try:
            child = subprocess.Popen(
                command,
                executable=str(python),
                env=environment,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                bufsize=0,
                start_new_session=True,
            )
        except OSError as error:
            writer.emit(f"launcher_error={error}\n".encode("utf-8", errors="replace"))
            return finish_run(1)
        writer.emit(f"process_id={child.pid}\n\n".encode("ascii"))
        if pending_signal is not None and process_group_exists(child.pid):
            signal_process_group(child.pid, pending_signal)
        if child.stdout is None:
            writer.emit(b"launcher_error=child output pipe is unavailable\n")
            child.terminate()
            return finish_run(1)
        while True:
            try:
                chunk = os.read(child.stdout.fileno(), 64 * 1024)
            except InterruptedError:
                continue
            if not chunk:
                break
            writer.emit(chunk)
        return_code = child.wait()
        if process_group_exists(child.pid):
            stop_process_group(child)
        exit_code = 128 - return_code if return_code < 0 else return_code
        return finish_run(exit_code)
    except Exception as error:
        writer.emit(f"launcher_error={error}\n".encode("utf-8", errors="replace"))
        finish_run(1)
        raise
    finally:
        try:
            if child is not None and process_group_exists(child.pid):
                stop_process_group(
                    child,
                    signum=int(pending_signal or signal.SIGTERM),
                )
        finally:
            try:
                if route_recorder is not None:
                    route_recorder.close()
                writer.close()
            finally:
                if sensor_lease is not None:
                    sensor_lease.release()


if __name__ == "__main__":
    raise SystemExit(main())
