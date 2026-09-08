from __future__ import annotations

import os
import copy
import logging
import shutil
import subprocess
import threading
import time
from pathlib import Path
from typing import Dict, Iterable, Optional, Tuple

import psutil

from .config import DeviceConfig
from .sensors import SensorBridgeStore


class StatusCollector:
    GPU_LOAD_PATHS = (
        Path("/sys/devices/gpu.0/load"),
        Path("/sys/devices/17000000.gpu/load"),
    )

    def __init__(
        self,
        config: DeviceConfig,
        storage_path: Path = Path("/"),
        sensor_bridge: Optional[SensorBridgeStore] = None,
    ) -> None:
        self.config = config
        self.storage_path = storage_path
        self.sensor_bridge = sensor_bridge or SensorBridgeStore()
        self._metric_validity: Dict[str, object] = {}

    def _record_validity(self, keys, validity="valid", reason=None, observed_at=None):
        if observed_at is None and validity == "valid":
            observed_at = int(time.time() * 1000)
        for key in keys:
            self._metric_validity[key] = {
                "validity": validity,
                "observedAtEpochMillis": observed_at,
                "reason": reason,
            }

    def _measure(self, keys, reader, fallback):
        self._record_validity(keys)
        try:
            value = reader()
            for key in keys:
                if self._metric_validity[key]["validity"] == "valid":
                    self._record_validity((key,))
            return value
        except Exception as error:
            self._record_validity(keys, "unavailable", str(error) or type(error).__name__)
            return fallback

    @staticmethod
    def _clamp_percent(value: float) -> int:
        return max(0, min(100, int(round(value))))

    def cpu_percent(self) -> int:
        return self._clamp_percent(psutil.cpu_percent(interval=0.05))

    def gpu_percent(self) -> int:
        for path in self.GPU_LOAD_PATHS:
            try:
                value = int(path.read_text(encoding="utf-8").strip())
                if value > 100:
                    value = round(value / 10)
                return self._clamp_percent(value)
            except (FileNotFoundError, OSError, ValueError):
                continue
        raise OSError("GPU load sensor is unavailable")

    @staticmethod
    def ram_megabytes() -> Tuple[int, int]:
        memory = psutil.virtual_memory()
        return (
            int((memory.total - memory.available) / 1024 / 1024),
            int(memory.total / 1024 / 1024),
        )

    @staticmethod
    def temperature_c() -> float:
        values = []
        for path in Path("/sys/class/thermal").glob("thermal_zone*/temp"):
            try:
                value = float(path.read_text(encoding="utf-8").strip())
                if abs(value) > 1000:
                    value /= 1000.0
                if -40 <= value <= 150:
                    values.append(value)
            except (OSError, ValueError):
                continue
        if not values:
            raise OSError("Temperature sensor is unavailable")
        return round(max(values), 1)

    def storage_usage(self) -> Tuple[int, int, int, int]:
        usage = shutil.disk_usage(self.storage_path)
        return (
            self._clamp_percent(usage.used * 100 / usage.total),
            usage.used,
            usage.total,
            usage.free,
        )

    @staticmethod
    def service_active(unit: str) -> bool:
        if not unit:
            raise ValueError("No service is configured")
        result = subprocess.run(
            ["/usr/bin/systemctl", "is-active", "--quiet", unit],
            check=False,
            timeout=3,
        )
        if result.returncode not in (0, 3):
            raise OSError("Service state is unavailable")
        return result.returncode == 0

    def wifi_status(self) -> Tuple[bool, str]:
        environment = dict(os.environ)
        environment.update({"LC_ALL": "C", "LANG": "C"})
        try:
            device = subprocess.run(
                [
                    "/usr/bin/nmcli",
                    "--terse",
                    "--escape",
                    "no",
                    "--get-values",
                    "GENERAL.STATE,GENERAL.CON-UUID,GENERAL.CONNECTION",
                    "device",
                    "show",
                    self.config.wifi_interface,
                ],
                check=False,
                text=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.DEVNULL,
                timeout=3,
                env=environment,
            )
        except (OSError, subprocess.SubprocessError) as error:
            raise OSError("Wi-Fi state is unavailable") from error
        if device.returncode != 0:
            raise OSError("Wi-Fi state is unavailable")

        values = device.stdout.splitlines()
        if not values or not values[0].partition(" ")[0].isdigit():
            raise OSError("Wi-Fi state response is invalid")
        if values[0].partition(" ")[0] != "100":
            return False, ""

        connection_uuid = values[1].strip() if len(values) > 1 else ""
        connection_name = values[2].strip() if len(values) > 2 else ""
        if not connection_uuid:
            self._record_validity(("wifiSsid",), "unavailable", "Wi-Fi SSID is unavailable; showing profile name")
            return True, connection_name

        try:
            connection = subprocess.run(
                [
                    "/usr/bin/nmcli",
                    "--terse",
                    "--escape",
                    "no",
                    "--get-values",
                    "802-11-wireless.ssid",
                    "connection",
                    "show",
                    "uuid",
                    connection_uuid,
                ],
                check=False,
                text=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.DEVNULL,
                timeout=3,
                env=environment,
            )
        except (OSError, subprocess.SubprocessError):
            self._record_validity(("wifiSsid",), "unavailable", "Wi-Fi SSID query failed; showing profile name")
            return True, connection_name

        ssid = connection.stdout.rstrip("\r\n") if connection.returncode == 0 else ""
        if not ssid:
            self._record_validity(("wifiSsid",), "unavailable", "Wi-Fi SSID is unavailable; showing profile name")
        return True, ssid or connection_name

    def collect(self) -> Dict[str, object]:
        self._metric_validity = {}
        cpu_percent = self._measure(("cpuPercent",), self.cpu_percent, 0)
        gpu_percent = self._measure(("gpuPercent",), self.gpu_percent, 0)
        temperature = self._measure(("temperatureC",), self.temperature_c, 0.0)
        ram_used, ram_total = self._measure(("ramUsedMb", "ramTotalMb"), self.ram_megabytes, (0, 0))
        storage_percent, storage_used, storage_total, storage_available = self._measure(
            ("storagePercent", "storageUsedBytes", "storageTotalBytes", "storageAvailableBytes"),
            self.storage_usage, (0, 0, 0, 0),
        )
        flags = {
            name: self._measure(
                (name + "Running",),
                lambda name=name: self.service_active(self.config.service_flags.get(name, "")),
                False,
            )
            for name in ("camera", "lidar", "gnss", "imu", "mms")
        }
        configured = {
            name: bool(self.config.service_flags.get(name, ""))
            for name in ("camera", "gnss", "imu")
        }
        sensors = self.sensor_bridge.status()
        sensor_values = {
            "camera": sensors.camera,
            "gnss": sensors.gnss,
            "imu": sensors.imu,
        }
        if sensors.available:
            for name, values in sensor_values.items():
                configured[name] = configured[name] or bool(values.get("configured"))
                flags[name] = sensors.fresh and bool(values.get("active"))
                self._record_validity(
                    (name + "Running",), "valid" if sensors.fresh else "stale",
                    None if sensors.fresh else "Sensor heartbeat is stale",
                    sensors.updated_at_epoch_millis,
                )
        wifi_connected, wifi_ssid = self._measure(("wifiConnected", "wifiSsid"), self.wifi_status, (False, ""))
        return {
            "collectedAtEpochMillis": int(time.time() * 1000),
            "metricValidity": dict(self._metric_validity),
            "cpuPercent": cpu_percent,
            "gpuPercent": gpu_percent,
            "ramUsedMb": ram_used,
            "ramTotalMb": ram_total,
            "temperatureC": temperature,
            "storagePercent": storage_percent,
            "storageUsedBytes": storage_used,
            "storageTotalBytes": storage_total,
            "storageAvailableBytes": storage_available,
            "cameraRunning": flags["camera"],
            "lidarRunning": flags["lidar"],
            "gnssRunning": flags["gnss"],
            "imuRunning": flags["imu"],
            "mmsRunning": flags["mms"],
            "cameraConfigured": configured["camera"],
            "gnssConfigured": configured["gnss"],
            "imuConfigured": configured["imu"],
            "wifiConnected": wifi_connected,
            "wifiSsid": wifi_ssid or None,
            "sensorTelemetryAvailable": sensors.available,
            "sensorTelemetryFresh": sensors.fresh,
            "sensorTelemetryUpdatedAtEpochMillis": sensors.updated_at_epoch_millis,
            "sensorTelemetryAgeSeconds": sensors.age_seconds,
            "pipelineSensorBridgeActive": bool(sensors.pipeline.get("active")),
            "pipelineSensorBridgeError": sensors.pipeline.get("error"),
            "cameraSensor": sensors.camera,
            "gnssSensor": sensors.gnss,
            "imuSensor": sensors.imu,
        }

    def ble_packet_values(
        self,
    ) -> Tuple[int, int, int, int, int, int, int, int, bool, str]:
        status = self.collect()
        service_bits = sum(
            (1 << bit) if bool(status[key]) else 0
            for bit, key in enumerate(
                ("cameraRunning", "lidarRunning", "gnssRunning", "mmsRunning")
            )
        )
        return (
            1,
            int(status["cpuPercent"]),
            int(status["gpuPercent"]),
            max(-128, min(127, int(round(float(status["temperatureC"]))))),
            int(status["storagePercent"]),
            service_bits,
            int(status["ramUsedMb"]),
            int(status["ramTotalMb"]),
            bool(status["wifiConnected"]),
            str(status["wifiSsid"] or ""),
        )


class StatusSnapshotService:
    """One collector loop; HTTP reads never invoke system commands or sensor I/O."""

    def __init__(self, collector: StatusCollector, interval: float = 2.0,
                 stale_after: float = 10.0, monotonic=time.monotonic) -> None:
        self.collector = collector
        self.interval = interval
        self.stale_after = stale_after
        self._monotonic = monotonic
        self._lock = threading.Lock()
        self._stop = threading.Event()
        self._thread: Optional[threading.Thread] = None
        self._snapshot: Optional[Dict[str, object]] = None
        self._collected_at: Optional[float] = None
        self._error: Optional[str] = None

    def refresh(self) -> None:
        try:
            snapshot = dict(self.collector.collect())
            snapshot.setdefault("collectedAtEpochMillis", int(time.time() * 1000))
        except Exception as error:
            logging.getLogger(__name__).exception("Status collection failed")
            with self._lock:
                self._error = str(error) or type(error).__name__
            return
        with self._lock:
            self._snapshot = snapshot
            self._collected_at = self._monotonic()
            self._error = None

    def snapshot(self) -> Dict[str, object]:
        with self._lock:
            if self._snapshot is None:
                raise LookupError("The first device status sample is not available yet")
            result = copy.deepcopy(self._snapshot)
            age = max(0.0, self._monotonic() - self._collected_at)
            error = self._error
        fresh = age <= self.stale_after and error is None
        result.update(statusAgeSeconds=round(age, 3), statusFresh=fresh,
                      statusCollectionError=error)
        if not fresh:
            for metric in result.get("metricValidity", {}).values():
                if metric["validity"] == "valid":
                    metric.update(validity="stale", reason=error or "Status sample is stale")
        return result

    def start(self) -> None:
        self._stop.clear()
        self._thread = threading.Thread(target=self._run, name="device-status", daemon=True)
        self._thread.start()

    def _run(self) -> None:
        while not self._stop.wait(self.interval):
            self.refresh()

    def stop(self) -> None:
        self._stop.set()
        if self._thread is not None:
            self._thread.join(timeout=1)
