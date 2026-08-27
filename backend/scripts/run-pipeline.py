#!/usr/bin/env python3
from __future__ import annotations

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
        except FileNotFoundError:
            pass


class RunLogWriter:
    def __init__(self, directory: Path) -> None:
        started = utc_now()
        self.started_at = started.isoformat().replace("+00:00", "Z")
        self.path = directory / (
            f"run-{started.strftime('%Y%m%dT%H%M%S.%fZ')}-{os.getpid()}.log"
        )
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

    results_directory: Optional[Path] = None
    results_value = manifest.get("results_directory")
    if results_value:
        if not isinstance(results_value, str):
            fail("Manifest results_directory must be a string")
        requested_results = Path(results_value)
        if not requested_results.is_absolute():
            fail("Manifest results_directory must be absolute")
        try:
            results_metadata = os.lstat(requested_results)
            results_directory = requested_results.resolve(strict=True)
        except OSError as error:
            fail(f"Pipeline results directory is unavailable: {error}")
        if stat.S_ISLNK(results_metadata.st_mode) or not stat.S_ISDIR(results_metadata.st_mode):
            fail("Pipeline results path is unsafe")
        writable_paths = manifest.get("writable_paths", [])
        if not isinstance(writable_paths, list) or any(
            not isinstance(value, str) for value in writable_paths
        ):
            fail("Manifest writable_paths must be an array of strings")
        resolved_writable_paths = []
        for value in writable_paths:
            try:
                resolved_writable_paths.append(Path(value).resolve(strict=True))
            except OSError:
                continue
        if results_directory not in resolved_writable_paths:
            fail("Pipeline results directory is not an approved writable path")

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
    relay_environment = mobile_rtk_relay_environment(pipeline_id)
    environment.update(relay_environment)
    if relay_environment:
        print(
            "Using authenticated mobile-data RTK relay at "
            f"{relay_environment['NTRIP_HOST']}:{relay_environment['NTRIP_PORT']}",
            flush=True,
        )
    if results_directory is not None:
        environment["JETSON_PIPELINE_RESULTS_DIR"] = str(results_directory)
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
    if not wait_for_time_sync(cancelled=lambda: pending_signal is not None):
        return 128 + int(pending_signal or signal.SIGTERM)

    log_directory = prepare_log_directory()
    environment["JETSON_PIPELINE_LOGS_DIR"] = str(log_directory)
    prune_logs(log_directory)
    writer = RunLogWriter(log_directory)
    header = (
        "=== Jetson pipeline run ===\n"
        f"started_at={writer.started_at}\n"
        f"pipeline_id={pipeline_id}\n"
        f"release={release}\n"
    ).encode("utf-8")
    writer.emit(header)
    sensor_lease: Optional[CaptureDeviceLease] = None
    try:
        monitor_settings = settings_for_pipeline(pipeline_id, SENSOR_MONITOR_CONFIG)
        if monitor_settings is not None:
            print("Requesting sensor devices from the boot monitor", flush=True)
            sensor_lease = CaptureDeviceLease(monitor_settings, pipeline_id)
            if not sensor_lease.acquire(cancelled=lambda: pending_signal is not None):
                return 128 + int(pending_signal or signal.SIGTERM)
            environment["JETSON_PIPELINE_SENSOR_BRIDGE_DIR"] = str(
                monitor_settings.bridge_dir
            )
            print("Sensor devices handed off to the capture pipeline", flush=True)
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
            return 1
        writer.emit(f"process_id={child.pid}\n\n".encode("ascii"))
        if pending_signal is not None and process_group_exists(child.pid):
            signal_process_group(child.pid, pending_signal)
        if child.stdout is None:
            writer.emit(b"launcher_error=child output pipe is unavailable\n")
            child.terminate()
            return 1
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
        finished_at = utc_now().isoformat().replace("+00:00", "Z")
        writer.emit(
            (
                "\n=== Jetson pipeline run finished ===\n"
                f"finished_at={finished_at}\n"
                f"exit_code={exit_code}\n"
            ).encode("ascii")
        )
        return exit_code
    finally:
        try:
            if child is not None and process_group_exists(child.pid):
                stop_process_group(
                    child,
                    signum=int(pending_signal or signal.SIGTERM),
                )
        finally:
            try:
                writer.close()
            finally:
                if sensor_lease is not None:
                    sensor_lease.release()


if __name__ == "__main__":
    raise SystemExit(main())
