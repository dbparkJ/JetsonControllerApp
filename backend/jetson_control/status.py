from __future__ import annotations

import shutil
import subprocess
from pathlib import Path
from typing import Dict, Iterable, Tuple

import psutil

from .config import DeviceConfig


class StatusCollector:
    GPU_LOAD_PATHS = (
        Path("/sys/devices/gpu.0/load"),
        Path("/sys/devices/17000000.gpu/load"),
    )

    def __init__(self, config: DeviceConfig, storage_path: Path = Path("/")) -> None:
        self.config = config
        self.storage_path = storage_path

    @staticmethod
    def _clamp_percent(value: float) -> int:
        return max(0, min(100, int(round(value))))

    def cpu_percent(self) -> int:
        try:
            return self._clamp_percent(psutil.cpu_percent(interval=0.05))
        except Exception:
            return 0

    def gpu_percent(self) -> int:
        for path in self.GPU_LOAD_PATHS:
            try:
                value = int(path.read_text(encoding="utf-8").strip())
                if value > 100:
                    value = round(value / 10)
                return self._clamp_percent(value)
            except (FileNotFoundError, OSError, ValueError):
                continue
        return 0

    @staticmethod
    def ram_megabytes() -> Tuple[int, int]:
        try:
            memory = psutil.virtual_memory()
            return (
                int((memory.total - memory.available) / 1024 / 1024),
                int(memory.total / 1024 / 1024),
            )
        except Exception:
            return 0, 0

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
        return round(max(values), 1) if values else 0.0

    def storage_usage(self) -> Tuple[int, int, int, int]:
        try:
            usage = shutil.disk_usage(self.storage_path)
            return (
                self._clamp_percent(usage.used * 100 / usage.total),
                usage.used,
                usage.total,
                usage.free,
            )
        except (OSError, ZeroDivisionError):
            return 0, 0, 0, 0

    @staticmethod
    def service_active(unit: str) -> bool:
        if not unit:
            return False
        try:
            return (
                subprocess.run(
                    ["/usr/bin/systemctl", "is-active", "--quiet", unit],
                    check=False,
                    timeout=3,
                ).returncode
                == 0
            )
        except (OSError, subprocess.SubprocessError):
            return False

    def wifi_status(self) -> Tuple[bool, str]:
        try:
            result = subprocess.run(
                [
                    "/usr/bin/nmcli",
                    "-t",
                    "-f",
                    "DEVICE,TYPE,STATE,CONNECTION",
                    "device",
                    "status",
                ],
                check=False,
                text=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.DEVNULL,
                timeout=3,
            )
        except (OSError, subprocess.SubprocessError):
            return False, ""
        if result.returncode != 0:
            return False, ""
        for line in result.stdout.splitlines():
            parts = line.split(":", 3)
            if len(parts) != 4:
                continue
            device, connection_type, state, connection = parts
            if (
                device == self.config.wifi_interface
                and connection_type == "wifi"
                and state == "connected"
                and connection
                and connection != "--"
            ):
                return True, connection
        return False, ""

    def collect(self) -> Dict[str, object]:
        ram_used, ram_total = self.ram_megabytes()
        storage_percent, storage_used, storage_total, storage_available = (
            self.storage_usage()
        )
        flags = {
            name: self.service_active(self.config.service_flags.get(name, ""))
            for name in ("camera", "lidar", "gnss", "mms")
        }
        wifi_connected, wifi_ssid = self.wifi_status()
        return {
            "cpuPercent": self.cpu_percent(),
            "gpuPercent": self.gpu_percent(),
            "ramUsedMb": ram_used,
            "ramTotalMb": ram_total,
            "temperatureC": self.temperature_c(),
            "storagePercent": storage_percent,
            "storageUsedBytes": storage_used,
            "storageTotalBytes": storage_total,
            "storageAvailableBytes": storage_available,
            "cameraRunning": flags["camera"],
            "lidarRunning": flags["lidar"],
            "gnssRunning": flags["gnss"],
            "mmsRunning": flags["mms"],
            "wifiConnected": wifi_connected,
            "wifiSsid": wifi_ssid or None,
        }

    def ble_packet_values(self) -> Tuple[int, int, int, int, int, int, int, int]:
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
        )
