import json
import tempfile
import time
import unittest
from pathlib import Path

from jetson_control.sensors import SensorBridgeStore


class SensorBridgeStoreTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)

    def tearDown(self) -> None:
        self.temporary.cleanup()

    def write_status(self, updated_at: int) -> None:
        (self.root / "status.json").write_text(
            json.dumps(
                {
                    "schemaVersion": 1,
                    "updatedAtEpochMillis": updated_at,
                    "pipeline": {"active": True},
                    "camera": {
                        "configured": True,
                        "connected": True,
                        "active": True,
                        "previewAvailable": True,
                    },
                    "gnss": {
                        "configured": True,
                        "connected": True,
                        "active": True,
                        "fixQuality": 4,
                        "fixType": "rtk_fixed",
                        "latitude": 37.5,
                        "longitude": 127.0,
                        "ntripConnected": True,
                    },
                    "imu": {"configured": True, "connected": True, "active": True},
                }
            ),
            encoding="utf-8",
        )

    def test_reads_fresh_sensor_state_and_preview(self) -> None:
        now = time.time()
        self.write_status(int(now * 1000))
        (self.root / "camera-preview.jpg").write_bytes(b"\xff\xd8preview\xff\xd9")
        store = SensorBridgeStore(self.root, clock=lambda: now)

        status = store.status()

        self.assertTrue(status.fresh)
        self.assertTrue(status.camera["active"])
        self.assertEqual(status.gnss["fixType"], "rtk_fixed")
        self.assertEqual(store.preview_frame(), b"\xff\xd8preview\xff\xd9")
        content, revision = store.preview_frame_with_revision()
        self.assertEqual(content, b"\xff\xd8preview\xff\xd9")
        self.assertEqual(
            revision,
            (self.root / "camera-preview.jpg").stat().st_mtime_ns,
        )
        self.assertEqual(store.preview_frame_revision(), revision)

    def test_stale_heartbeat_deactivates_connected_sensors(self) -> None:
        now = time.time()
        self.write_status(int((now - 10) * 1000))
        status = SensorBridgeStore(self.root, clock=lambda: now).status()

        self.assertTrue(status.available)
        self.assertFalse(status.fresh)
        self.assertFalse(status.camera["connected"])
        self.assertFalse(status.camera["active"])
        self.assertFalse(status.gnss["ntripConnected"])
        self.assertEqual(status.gnss["latitude"], 37.5)

    def test_rejects_preview_symlink(self) -> None:
        outside = self.root / "outside.jpg"
        outside.write_bytes(b"\xff\xd8secret\xff\xd9")
        (self.root / "camera-preview.jpg").symlink_to(outside)

        with self.assertRaises(OSError):
            SensorBridgeStore(self.root).preview_frame()


if __name__ == "__main__":
    unittest.main()
