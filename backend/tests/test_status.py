import json
import subprocess
import tempfile
import time
import unittest
from pathlib import Path
from unittest.mock import patch

from jetson_control.config import DeviceConfig
from jetson_control.sensors import SensorBridgeStore
from jetson_control.status import StatusCollector


class StatusCollectorSensorTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)
        self.config = DeviceConfig(
            device_id="00000000-0000-0000-0000-000000000001",
            device_name="MMS-TEST",
            bootstrap_secret=bytes(range(32)),
            controlled_services=(),
            service_flags={
                "camera": "",
                "lidar": "",
                "gnss": "",
                "imu": "",
                "mms": "",
            },
            allow_power_commands=False,
            wifi_interface="wlan0",
        )

    def tearDown(self) -> None:
        self.temporary.cleanup()

    def write_status(self, updated_at_millis: int) -> None:
        (self.root / "status.json").write_text(
            json.dumps(
                {
                    "schemaVersion": 1,
                    "updatedAtEpochMillis": updated_at_millis,
                    "pipeline": {"active": True},
                    "camera": {"configured": True, "connected": True, "active": True},
                    "gnss": {
                        "configured": True,
                        "connected": True,
                        "active": True,
                        "fixQuality": 5,
                        "fixType": "rtk_float",
                    },
                    "imu": {"configured": True, "connected": True, "active": True},
                }
            ),
            encoding="utf-8",
        )

    def collect(self, now: float):
        collector = StatusCollector(
            self.config,
            storage_path=self.root,
            sensor_bridge=SensorBridgeStore(self.root, clock=lambda: now),
        )
        with patch.object(collector, "cpu_percent", return_value=0), patch.object(
            collector, "gpu_percent", return_value=0
        ), patch.object(collector, "ram_megabytes", return_value=(0, 0)), patch.object(
            collector, "temperature_c", return_value=0.0
        ), patch.object(collector, "wifi_status", return_value=(False, "")):
            return collector.collect()

    def test_fresh_bridge_marks_configured_sensors_active(self) -> None:
        now = time.time()
        self.write_status(int(now * 1000))

        status = self.collect(now)

        self.assertTrue(status["cameraConfigured"])
        self.assertTrue(status["cameraRunning"])
        self.assertTrue(status["gnssRunning"])
        self.assertTrue(status["imuRunning"])
        self.assertEqual(status["gnssSensor"]["fixType"], "rtk_float")
        self.assertTrue(status["sensorTelemetryFresh"])

    def test_stale_bridge_marks_sensors_inactive(self) -> None:
        now = time.time()
        self.write_status(int((now - 10) * 1000))

        status = self.collect(now)

        self.assertFalse(status["cameraRunning"])
        self.assertFalse(status["gnssRunning"])
        self.assertFalse(status["imuRunning"])
        self.assertFalse(status["sensorTelemetryFresh"])


class StatusCollectorWifiTest(unittest.TestCase):
    def setUp(self) -> None:
        self.config = DeviceConfig(
            device_id="00000000-0000-0000-0000-000000000001",
            device_name="MMS-TEST",
            bootstrap_secret=bytes(range(32)),
            controlled_services=(),
            service_flags={},
            allow_power_commands=False,
            wifi_interface="wlan0",
        )
        self.collector = StatusCollector(self.config)

    @staticmethod
    def result(stdout: str, returncode: int = 0) -> subprocess.CompletedProcess:
        return subprocess.CompletedProcess([], returncode, stdout, "")

    @patch("jetson_control.status.subprocess.run")
    def test_connected_wifi_uses_profile_ssid_instead_of_profile_name(self, run) -> None:
        run.side_effect = [
            self.result("100 (connected)\nprofile-uuid\nField profile\n"),
            self.result("Actual SSID\n"),
        ]

        connected, ssid = self.collector.wifi_status()

        self.assertTrue(connected)
        self.assertEqual(ssid, "Actual SSID")
        self.assertTrue(
            any(
                "GENERAL.STATE" in argument
                for argument in run.call_args_list[0].args[0]
            )
        )
        self.assertIn("802-11-wireless.ssid", run.call_args_list[1].args[0])
        self.assertEqual(run.call_args_list[0].kwargs["env"]["LC_ALL"], "C")

    @patch("jetson_control.status.subprocess.run")
    def test_disconnected_wifi_does_not_query_connection_profile(self, run) -> None:
        run.return_value = self.result("30 (disconnected)\n\n\n")

        self.assertEqual(self.collector.wifi_status(), (False, ""))
        run.assert_called_once()

    @patch("jetson_control.status.subprocess.run")
    def test_connected_wifi_falls_back_to_profile_name(self, run) -> None:
        run.side_effect = [
            self.result("100 (connected)\nprofile-uuid\nFallback profile\n"),
            self.result("", returncode=10),
        ]

        self.assertEqual(
            self.collector.wifi_status(),
            (True, "Fallback profile"),
        )


if __name__ == "__main__":
    unittest.main()
