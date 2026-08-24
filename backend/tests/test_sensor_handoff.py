import fcntl
import json
import os
import tempfile
import threading
import time
import unittest
from pathlib import Path

from jetson_control.sensor_handoff import (
    CaptureDeviceLease,
    SensorMonitorSettings,
    _open_lock,
    capture_request_active,
    settings_for_pipeline,
)


class SensorHandoffTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)
        self.bridge = self.root / "bridge"
        self.bridge.mkdir()
        self.config = self.root / "sensor-monitor.json"
        self.config.write_text(
            json.dumps(
                {
                    "schema_version": 1,
                    "pipeline_id": "depthai-capture",
                    "bridge_dir": str(self.bridge),
                    "registry_root": str(self.root / "registry"),
                    "monitor_arguments": ["--monitor-only"],
                    "capture_pipeline_ids": ["depthai-capture", "26_camera_record"],
                }
            ),
            encoding="utf-8",
        )
        self.config.chmod(0o644)
        self.settings = SensorMonitorSettings.load(self.config)

    def tearDown(self) -> None:
        self.temporary.cleanup()

    def test_settings_only_apply_to_the_configured_pipeline(self) -> None:
        self.assertIsNotNone(settings_for_pipeline("depthai-capture", self.config))
        self.assertIsNotNone(settings_for_pipeline("26_camera_record", self.config))
        self.assertIsNone(settings_for_pipeline("another-job", self.config))

    def test_live_capture_waits_for_monitor_then_owns_device_lock(self) -> None:
        monitor_descriptor = _open_lock(self.settings.device_lock_path)
        fcntl.flock(monitor_descriptor, fcntl.LOCK_EX)
        lease = CaptureDeviceLease(self.settings, "depthai-capture")
        acquired = []

        thread = threading.Thread(target=lambda: acquired.append(lease.acquire()))
        thread.start()
        deadline = time.monotonic() + 2.0
        while not self.settings.request_marker_path.exists() and time.monotonic() < deadline:
            time.sleep(0.01)

        self.assertTrue(self.settings.request_marker_path.exists())
        self.assertTrue(capture_request_active(self.settings))
        self.assertTrue(thread.is_alive())

        fcntl.flock(monitor_descriptor, fcntl.LOCK_UN)
        os.close(monitor_descriptor)
        thread.join(timeout=2.0)
        self.assertEqual(acquired, [True])

        lease.release()
        self.assertFalse(self.settings.request_marker_path.exists())
        self.assertFalse(capture_request_active(self.settings))

    def test_stale_request_marker_is_removed_after_runner_dies(self) -> None:
        self.settings.request_marker_path.write_text("{}", encoding="utf-8")

        self.assertFalse(capture_request_active(self.settings))
        self.assertFalse(self.settings.request_marker_path.exists())

    def test_configuration_rejects_root_bridge_and_unbounded_arguments(self) -> None:
        value = json.loads(self.config.read_text(encoding="utf-8"))
        value["bridge_dir"] = "/"
        self.config.write_text(json.dumps(value), encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "absolute safe path"):
            SensorMonitorSettings.load(self.config)

    def test_configuration_rejects_writable_and_symlink_files(self) -> None:
        self.config.chmod(0o664)
        with self.assertRaisesRegex(ValueError, "permissions are unsafe"):
            SensorMonitorSettings.load(self.config)

        self.config.chmod(0o644)
        link = self.root / "linked-monitor.json"
        link.symlink_to(self.config)
        with self.assertRaises(OSError):
            SensorMonitorSettings.load(link)

    def test_configuration_can_require_the_root_owned_production_contract(self) -> None:
        with self.assertRaisesRegex(ValueError, "permissions are unsafe"):
            SensorMonitorSettings.load(
                self.config,
                expected_owner_uid=os.getuid() + 1,
            )


if __name__ == "__main__":
    unittest.main()
