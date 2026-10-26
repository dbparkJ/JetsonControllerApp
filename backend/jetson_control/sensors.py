from __future__ import annotations

import json
import os
import stat
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Dict, Optional, Tuple


MAX_STATUS_BYTES = 128 * 1024
MAX_PREVIEW_BYTES = 12 * 1024 * 1024


def _read_regular_file_with_metadata(
    path: Path,
    maximum_bytes: int,
) -> Tuple[bytes, os.stat_result]:
    flags = os.O_RDONLY | getattr(os, "O_CLOEXEC", 0) | getattr(os, "O_NOFOLLOW", 0)
    descriptor = os.open(str(path), flags)
    try:
        metadata = os.fstat(descriptor)
        if not stat.S_ISREG(metadata.st_mode):
            raise OSError("Sensor bridge path is not a regular file")
        if metadata.st_size <= 0 or metadata.st_size > maximum_bytes:
            raise OSError("Sensor bridge file size is invalid")
        chunks = []
        remaining = metadata.st_size
        while remaining:
            chunk = os.read(descriptor, min(remaining, 64 * 1024))
            if not chunk:
                break
            chunks.append(chunk)
            remaining -= len(chunk)
        content = b"".join(chunks)
        if len(content) != metadata.st_size:
            raise OSError("Sensor bridge file changed while reading")
        return content, metadata
    finally:
        os.close(descriptor)


def _read_regular_file(path: Path, maximum_bytes: int) -> bytes:
    content, _ = _read_regular_file_with_metadata(path, maximum_bytes)
    return content


def _regular_file_revision(path: Path, maximum_bytes: int) -> int:
    flags = os.O_RDONLY | getattr(os, "O_CLOEXEC", 0) | getattr(os, "O_NOFOLLOW", 0)
    descriptor = os.open(str(path), flags)
    try:
        metadata = os.fstat(descriptor)
        if not stat.S_ISREG(metadata.st_mode):
            raise OSError("Sensor bridge path is not a regular file")
        if metadata.st_size <= 0 or metadata.st_size > maximum_bytes:
            raise OSError("Sensor bridge file size is invalid")
        return metadata.st_mtime_ns
    finally:
        os.close(descriptor)


def _mapping(value: object) -> Dict[str, Any]:
    return value if isinstance(value, dict) else {}


def _boolean(value: object) -> bool:
    return value is True


def _integer(value: object) -> Optional[int]:
    if isinstance(value, bool):
        return None
    try:
        return int(value) if value is not None else None
    except (TypeError, ValueError, OverflowError):
        return None


def _number(value: object) -> Optional[float]:
    if isinstance(value, bool):
        return None
    try:
        result = float(value) if value is not None else None
    except (TypeError, ValueError, OverflowError):
        return None
    if result is None or result != result or result in (float("inf"), float("-inf")):
        return None
    return result


def _text(value: object, maximum: int = 256) -> Optional[str]:
    if not isinstance(value, str):
        return None
    cleaned = "".join(character for character in value if ord(character) >= 32).strip()
    return cleaned[:maximum] or None


def _vector(value: object) -> Optional[Dict[str, float]]:
    raw = _mapping(value)
    coordinates = {}
    for axis in ("x", "y", "z"):
        coordinate = _number(raw.get(axis))
        if coordinate is None:
            return None
        coordinates[axis] = coordinate
    return coordinates


def _output_size(width: object, height: object) -> Optional[Tuple[int, int]]:
    # Reject partial/invalid pairs instead of mixing sensor and stored-frame dimensions.
    if (isinstance(width, int) and not isinstance(width, bool)
            and isinstance(height, int) and not isinstance(height, bool)
            and 0 < width <= 16384 and 0 < height <= 16384):
        return width, height
    return None


def _camera_output_size(camera: Dict[str, Any], pipeline: Dict[str, Any]) -> Optional[Tuple[int, int]]:
    explicit = _output_size(camera.get("sensorOutputWidth"), camera.get("sensorOutputHeight"))
    if explicit is not None:
        return explicit
    # The producer may forward its measured output size with the current bridge heartbeat.
    # A configured recording size is deliberately not used as sensor evidence.
    size = _mapping(pipeline.get("metadata")).get("rgb_camera_output_size")
    if isinstance(size, (list, tuple)) and len(size) == 2:
        return _output_size(size[0], size[1])
    if isinstance(size, dict):
        return _output_size(size.get("width"), size.get("height"))
    return None


def _imu_status(
    raw: Dict[str, Any], camera: Dict[str, Any], *,
    fresh: bool, now_millis: int, stale_after_seconds: float,
) -> Dict[str, object]:
    # Camera-owned IMU telemetry takes precedence over the legacy external IMU.
    # The capture bridge must export camera.imu using the sample fields below.
    onboard = camera.get("imu")
    oak = isinstance(onboard, dict) or raw.get("source") == "oak"
    if isinstance(onboard, dict):
        raw = onboard
    sample_at = _integer(raw.get("lastSampleAtEpochMillis"))
    sample_fresh = (
        fresh and sample_at is not None and sample_at > 0
        and 0 <= now_millis - sample_at <= int(stale_after_seconds * 1000)
    )
    acceleration = _vector(raw.get("accelerationMps2"))
    angular_velocity = _vector(raw.get("angularVelocityRadS"))
    # A heartbeat or attached camera alone is not evidence of an IMU sample.
    if oak:
        receiving = sample_fresh and (acceleration is not None or angular_velocity is not None)
    else:
        receiving = sample_fresh and _boolean(raw.get("active"))
    return {
        "configured": _boolean(raw.get("configured")) or (oak and sample_at is not None),
        "connected": receiving if oak else sample_fresh and _boolean(raw.get("connected")),
        "active": receiving,
        "lastSampleAtEpochMillis": sample_at,
        "source": "oak" if oak else _text(raw.get("source"), 32),
        "accelerationMps2": acceleration,
        "angularVelocityRadS": angular_velocity,
        "error": _text(raw.get("error")),
    }


@dataclass(frozen=True)
class SensorBridgeSnapshot:
    available: bool
    fresh: bool
    updated_at_epoch_millis: Optional[int]
    age_seconds: Optional[float]
    pipeline: Dict[str, object]
    camera: Dict[str, object]
    gnss: Dict[str, object]
    imu: Dict[str, object]


class SensorBridgeStore:
    def __init__(
        self,
        root: Path = Path("/var/lib/jetson-sensors"),
        stale_after_seconds: float = 5.0,
        clock=time.time,
    ) -> None:
        self.root = root
        self.status_path = root / "status.json"
        self.preview_path = root / "camera-preview.jpg"
        self.stale_after_seconds = max(1.0, stale_after_seconds)
        self.clock = clock

    def _empty(self) -> SensorBridgeSnapshot:
        return SensorBridgeSnapshot(False, False, None, None, {}, {}, {}, {})

    def status(self) -> SensorBridgeSnapshot:
        try:
            raw = json.loads(
                _read_regular_file(self.status_path, MAX_STATUS_BYTES).decode("utf-8")
            )
        except (OSError, UnicodeDecodeError, json.JSONDecodeError):
            return self._empty()
        if not isinstance(raw, dict) or raw.get("schemaVersion") != 1:
            return self._empty()

        updated_at = _integer(raw.get("updatedAtEpochMillis"))
        now_millis = int(self.clock() * 1000)
        if updated_at is None:
            age_seconds = None
            fresh = False
        else:
            age_millis = now_millis - updated_at
            age_seconds = round(max(0, age_millis) / 1000.0, 1)
            fresh = -60_000 <= age_millis <= int(self.stale_after_seconds * 1000)

        pipeline_raw = _mapping(raw.get("pipeline"))
        camera_raw = _mapping(raw.get("camera"))
        gnss_raw = _mapping(raw.get("gnss"))
        imu_raw = _mapping(raw.get("imu"))
        output_size = _camera_output_size(camera_raw, pipeline_raw)
        pipeline = {
            "active": fresh and _boolean(pipeline_raw.get("active")),
            "error": _text(pipeline_raw.get("error")),
        }
        camera = {
            "configured": _boolean(camera_raw.get("configured")),
            "connected": fresh and _boolean(camera_raw.get("connected")),
            "active": fresh and _boolean(camera_raw.get("active")),
            "lastFrameAtEpochMillis": _integer(camera_raw.get("lastFrameAtEpochMillis")),
            "frameWidth": _integer(camera_raw.get("frameWidth")),
            "frameHeight": _integer(camera_raw.get("frameHeight")),
            "sensorOutputWidth": output_size[0] if output_size else None,
            "sensorOutputHeight": output_size[1] if output_size else None,
            "previewAvailable": fresh and _boolean(camera_raw.get("previewAvailable")),
            "previewUpdatedAtEpochMillis": _integer(camera_raw.get("previewUpdatedAtEpochMillis")),
            "previewError": _text(camera_raw.get("previewError")),
        }
        gnss = {
            "configured": _boolean(gnss_raw.get("configured")),
            "connected": fresh and _boolean(gnss_raw.get("connected")),
            "active": fresh and _boolean(gnss_raw.get("active")),
            "lastSampleAtEpochMillis": _integer(gnss_raw.get("lastSampleAtEpochMillis")),
            "fixQuality": _integer(gnss_raw.get("fixQuality")),
            "fixType": _text(gnss_raw.get("fixType"), 32) or "none",
            "fixName": _text(gnss_raw.get("fixName"), 64) or "unknown",
            "rtkStatus": _text(gnss_raw.get("rtkStatus"), 32) or "unknown",
            "latitude": _number(gnss_raw.get("latitude")),
            "longitude": _number(gnss_raw.get("longitude")),
            "altitudeM": _number(gnss_raw.get("altitudeM")),
            "satellites": _integer(gnss_raw.get("satellites")),
            "hdop": _number(gnss_raw.get("hdop")),
            "differentialAgeS": _number(gnss_raw.get("differentialAgeS")),
            "referenceStationId": _text(gnss_raw.get("referenceStationId"), 64),
            "ntripConnected": fresh and _boolean(gnss_raw.get("ntripConnected")),
            "ntripMountpoint": _text(gnss_raw.get("ntripMountpoint"), 128),
            "rtcmBytes": max(0, _integer(gnss_raw.get("rtcmBytes")) or 0),
            "error": _text(gnss_raw.get("error")),
        }
        imu = _imu_status(
            imu_raw, camera_raw, fresh=fresh, now_millis=now_millis,
            stale_after_seconds=self.stale_after_seconds,
        )
        return SensorBridgeSnapshot(
            available=True,
            fresh=fresh,
            updated_at_epoch_millis=updated_at,
            age_seconds=age_seconds,
            pipeline=pipeline,
            camera=camera,
            gnss=gnss,
            imu=imu,
        )

    def preview_frame(self) -> bytes:
        content, _ = self.preview_frame_with_revision()
        return content

    def preview_frame_with_revision(self) -> Tuple[bytes, int]:
        content, metadata = _read_regular_file_with_metadata(
            self.preview_path,
            MAX_PREVIEW_BYTES,
        )
        if len(content) < 4 or not content.startswith(b"\xff\xd8") or not content.endswith(b"\xff\xd9"):
            raise OSError("Camera preview is not a valid JPEG")
        return content, metadata.st_mtime_ns

    def preview_frame_revision(self) -> int:
        return _regular_file_revision(self.preview_path, MAX_PREVIEW_BYTES)
