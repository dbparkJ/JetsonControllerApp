from __future__ import annotations

import fcntl
import json
import os
import stat
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Callable, Optional

from .config import validate_config_id


DEFAULT_SENSOR_MONITOR_CONFIG = Path("/etc/jetson-sensor-monitor.json")
DEFAULT_PIPELINE_REGISTRY = Path("/opt/jetson-pipelines")
REQUEST_LOCK_NAME = ".capture-request.lock"
REQUEST_MARKER_NAME = ".capture-request.json"
DEVICE_LOCK_NAME = ".devices.lock"


@dataclass(frozen=True)
class SensorMonitorSettings:
    pipeline_id: str
    bridge_dir: Path
    registry_root: Path
    monitor_arguments: tuple[str, ...]
    capture_pipeline_ids: tuple[str, ...] = ()

    @classmethod
    def load(
        cls,
        path: Path = DEFAULT_SENSOR_MONITOR_CONFIG,
        *,
        expected_owner_uid: Optional[int] = None,
    ) -> "SensorMonitorSettings":
        descriptor: Optional[int] = None
        try:
            flags = os.O_RDONLY | getattr(os, "O_CLOEXEC", 0)
            if hasattr(os, "O_NOFOLLOW"):
                flags |= os.O_NOFOLLOW
            descriptor = os.open(path, flags)
            metadata = os.fstat(descriptor)
            if (
                not stat.S_ISREG(metadata.st_mode)
                or metadata.st_mode & (stat.S_IWGRP | stat.S_IWOTH)
                or (
                    expected_owner_uid is not None
                    and metadata.st_uid != expected_owner_uid
                )
            ):
                raise ValueError(
                    f"Sensor monitor configuration permissions are unsafe: {path}"
                )
            with os.fdopen(descriptor, "r", encoding="utf-8") as source:
                descriptor = None
                value = json.load(source)
        except json.JSONDecodeError as error:
            raise ValueError(f"Sensor monitor configuration is invalid: {path}") from error
        finally:
            if descriptor is not None:
                os.close(descriptor)
        if not isinstance(value, dict) or value.get("schema_version") != 1:
            raise ValueError("Sensor monitor configuration schema is invalid")

        pipeline_value = value.get("pipeline_id")
        if not isinstance(pipeline_value, str):
            raise ValueError("Sensor monitor pipeline_id is invalid")
        pipeline_id = validate_config_id(pipeline_value, "sensor monitor pipeline")

        bridge_dir = cls._absolute_safe_path(value.get("bridge_dir"), "bridge_dir")
        registry_value = value.get("registry_root", str(DEFAULT_PIPELINE_REGISTRY))
        registry_root = cls._absolute_safe_path(registry_value, "registry_root")

        arguments_value = value.get("monitor_arguments", [])
        if (
            not isinstance(arguments_value, list)
            or len(arguments_value) > 64
            or any(
                not isinstance(argument, str)
                or "\x00" in argument
                or len(argument.encode("utf-8")) > 4096
                for argument in arguments_value
            )
        ):
            raise ValueError("Sensor monitor arguments are invalid")
        capture_ids_value = value.get("capture_pipeline_ids", [pipeline_id])
        if (
            not isinstance(capture_ids_value, list)
            or not capture_ids_value
            or len(capture_ids_value) > 16
            or any(not isinstance(item, str) for item in capture_ids_value)
        ):
            raise ValueError("Sensor monitor capture pipeline ids are invalid")
        capture_pipeline_ids = tuple(
            dict.fromkeys(
                validate_config_id(item, "sensor capture pipeline")
                for item in capture_ids_value
            )
        )
        return cls(
            pipeline_id=pipeline_id,
            bridge_dir=bridge_dir,
            registry_root=registry_root,
            monitor_arguments=tuple(arguments_value),
            capture_pipeline_ids=capture_pipeline_ids,
        )

    @staticmethod
    def _absolute_safe_path(value: object, name: str) -> Path:
        if not isinstance(value, str) or not value or "\x00" in value:
            raise ValueError(f"Sensor monitor {name} is invalid")
        path = Path(value).expanduser()
        if not path.is_absolute() or path == Path("/"):
            raise ValueError(f"Sensor monitor {name} must be an absolute safe path")
        return path.resolve(strict=False)

    @property
    def request_lock_path(self) -> Path:
        return self.bridge_dir / REQUEST_LOCK_NAME

    @property
    def request_marker_path(self) -> Path:
        return self.bridge_dir / REQUEST_MARKER_NAME

    @property
    def device_lock_path(self) -> Path:
        return self.bridge_dir / DEVICE_LOCK_NAME


def settings_for_pipeline(
    pipeline_id: str,
    config_path: Path = DEFAULT_SENSOR_MONITOR_CONFIG,
) -> Optional[SensorMonitorSettings]:
    try:
        settings = SensorMonitorSettings.load(
            config_path,
            expected_owner_uid=(
                0 if config_path == DEFAULT_SENSOR_MONITOR_CONFIG else None
            ),
        )
    except FileNotFoundError:
        return None
    capture_ids = settings.capture_pipeline_ids or (settings.pipeline_id,)
    return settings if pipeline_id in capture_ids else None


def _open_lock(path: Path) -> int:
    flags = os.O_RDWR | os.O_CREAT | getattr(os, "O_CLOEXEC", 0)
    if hasattr(os, "O_NOFOLLOW"):
        flags |= os.O_NOFOLLOW
    return os.open(path, flags, 0o600)


def _try_exclusive_lock(descriptor: int) -> bool:
    try:
        fcntl.flock(descriptor, fcntl.LOCK_EX | fcntl.LOCK_NB)
        return True
    except BlockingIOError:
        return False


def capture_request_active(settings: SensorMonitorSettings) -> bool:
    """Return true while a live capture runner owns the request lock.

    A marker can remain after SIGKILL or a power loss.  The advisory request lock
    distinguishes that stale file from a live hand-off without trusting a PID.
    """

    if not settings.request_marker_path.exists():
        return False
    descriptor = _open_lock(settings.request_lock_path)
    try:
        if not _try_exclusive_lock(descriptor):
            return True
        try:
            settings.request_marker_path.unlink()
        except FileNotFoundError:
            pass
        return False
    finally:
        os.close(descriptor)


class CaptureDeviceLease:
    """Ask the boot monitor to release sensors and hold them for one capture."""

    def __init__(self, settings: SensorMonitorSettings, pipeline_id: str) -> None:
        self.settings = settings
        self.pipeline_id = pipeline_id
        self.request_descriptor: Optional[int] = None
        self.device_descriptor: Optional[int] = None
        self.marker_created = False

    def acquire(
        self,
        *,
        cancelled: Callable[[], bool] = lambda: False,
        sleep: Callable[[float], None] = time.sleep,
    ) -> bool:
        try:
            self.settings.bridge_dir.mkdir(parents=True, exist_ok=True)
            self.request_descriptor = _open_lock(self.settings.request_lock_path)
            while not _try_exclusive_lock(self.request_descriptor):
                if cancelled():
                    self.release()
                    return False
                sleep(0.1)
            if cancelled():
                self.release()
                return False

            marker = {
                "schemaVersion": 1,
                "pipelineId": self.pipeline_id,
                "runnerPid": os.getpid(),
            }
            temporary = self.settings.request_marker_path.with_name(
                f".{self.settings.request_marker_path.name}.{os.getpid()}.{time.monotonic_ns()}.tmp"
            )
            try:
                flags = os.O_WRONLY | os.O_CREAT | os.O_EXCL | getattr(os, "O_CLOEXEC", 0)
                if hasattr(os, "O_NOFOLLOW"):
                    flags |= os.O_NOFOLLOW
                descriptor = os.open(temporary, flags, 0o600)
                try:
                    content = (json.dumps(marker, separators=(",", ":")) + "\n").encode(
                        "utf-8"
                    )
                    with os.fdopen(descriptor, "wb") as output:
                        descriptor = -1
                        output.write(content)
                        output.flush()
                        os.fsync(output.fileno())
                finally:
                    if descriptor >= 0:
                        os.close(descriptor)
                os.replace(temporary, self.settings.request_marker_path)
                self.marker_created = True
            finally:
                try:
                    temporary.unlink()
                except FileNotFoundError:
                    pass

            self.device_descriptor = _open_lock(self.settings.device_lock_path)
            while not _try_exclusive_lock(self.device_descriptor):
                if cancelled():
                    self.release()
                    return False
                sleep(0.1)
            if cancelled():
                self.release()
                return False
            return True
        except BaseException:
            self.release()
            raise

    def release(self) -> None:
        try:
            if self.marker_created:
                try:
                    self.settings.request_marker_path.unlink()
                except FileNotFoundError:
                    pass
                self.marker_created = False
        finally:
            if self.device_descriptor is not None:
                try:
                    fcntl.flock(self.device_descriptor, fcntl.LOCK_UN)
                finally:
                    os.close(self.device_descriptor)
                    self.device_descriptor = None
            if self.request_descriptor is not None:
                try:
                    fcntl.flock(self.request_descriptor, fcntl.LOCK_UN)
                finally:
                    os.close(self.request_descriptor)
                    self.request_descriptor = None

    def __enter__(self) -> "CaptureDeviceLease":
        if not self.acquire():
            raise RuntimeError("Sensor device hand-off was cancelled")
        return self

    def __exit__(self, _type: object, _value: object, _traceback: object) -> None:
        self.release()
