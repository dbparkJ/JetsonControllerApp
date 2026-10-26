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

    def imu_status(self, imu, *, camera_imu=None, heartbeat_age=0, stale_after=5):
        now = 1_700_000_000_000
        self.write_status(now - heartbeat_age)
        path = self.root / "status.json"
        raw = json.loads(path.read_text(encoding="utf-8"))
        raw["imu"] = imu
        if camera_imu is not None:
            raw["camera"]["imu"] = camera_imu
        path.write_text(json.dumps(raw), encoding="utf-8")
        return SensorBridgeStore(
            self.root, clock=lambda: now / 1000, stale_after_seconds=stale_after,
        ).status().imu

    def oak_sample(self, **overrides):
        return {
            "lastSampleAtEpochMillis": 1_700_000_000_000,
            "accelerationMps2": {"x": 0, "y": -0.1, "z": 9.81},
            "angularVelocityRadS": {"x": 0.01, "y": 0, "z": -0.02},
            **overrides,
        }

    def test_camera_imu_overrides_missing_external_imu(self) -> None:
        imu = self.imu_status(
            {"configured": True, "source": "external_imu", "error": "not found"},
            camera_imu=self.oak_sample(),
        )
        self.assertTrue(imu["configured"])
        self.assertTrue(imu["connected"])
        self.assertTrue(imu["active"])
        self.assertEqual(imu["source"], "oak")
        self.assertEqual(imu["lastSampleAtEpochMillis"], 1_700_000_000_000)
        self.assertEqual(imu["accelerationMps2"], {"x": 0.0, "y": -0.1, "z": 9.81})
        self.assertEqual(imu["angularVelocityRadS"], {"x": 0.01, "y": 0.0, "z": -0.02})
        self.assertIsNone(imu["error"])

    def test_top_level_oak_imu_maps_actual_samples(self) -> None:
        imu = self.imu_status(self.oak_sample(source="oak"))
        self.assertTrue(imu["active"])
        self.assertEqual(imu["source"], "oak")
        self.assertEqual(imu["accelerationMps2"]["z"], 9.81)

    def test_camera_connection_and_flags_do_not_establish_imu_reception(self) -> None:
        for sample_at in (None, 0, True, "invalid", 1_699_999_994_999, 1_700_000_000_001):
            with self.subTest(sample_at=sample_at):
                imu = self.imu_status({}, camera_imu=self.oak_sample(
                    configured=True, connected=True, active=True,
                    lastSampleAtEpochMillis=sample_at,
                ))
                self.assertFalse(imu["connected"])
                self.assertFalse(imu["active"])
        imu = self.imu_status({"connected": True, "active": True})
        self.assertFalse(imu["active"])

    def test_stale_heartbeat_deactivates_recent_oak_sample(self) -> None:
        imu = self.imu_status({}, camera_imu=self.oak_sample(), heartbeat_age=5001)
        self.assertFalse(imu["connected"])
        self.assertFalse(imu["active"])
        self.assertEqual(imu["accelerationMps2"]["z"], 9.81)

    def test_sample_freshness_uses_store_threshold(self) -> None:
        sample = self.oak_sample(lastSampleAtEpochMillis=1_699_999_998_000)
        self.assertTrue(self.imu_status({}, camera_imu=sample, stale_after=2)["active"])
        self.assertFalse(self.imu_status({}, camera_imu=sample, stale_after=1)["active"])

    def test_missing_or_invalid_vectors_do_not_claim_oak_reception(self) -> None:
        for vector in (None, {}, [0, 0, 9.81], {"x": 0, "y": 0},
                       {"x": True, "y": 0, "z": 0},
                       {"x": float("nan"), "y": 0, "z": 0},
                       {"x": 0, "y": float("inf"), "z": 0}):
            with self.subTest(vector=vector):
                imu = self.imu_status({}, camera_imu=self.oak_sample(
                    accelerationMps2=vector, angularVelocityRadS=None,
                ))
                self.assertFalse(imu["active"])
                self.assertFalse(imu["connected"])
                self.assertIsNone(imu["accelerationMps2"])

    def test_single_valid_vector_is_an_actual_sample(self) -> None:
        for missing in ("accelerationMps2", "angularVelocityRadS"):
            with self.subTest(missing=missing):
                imu = self.imu_status({}, camera_imu=self.oak_sample(**{missing: None}))
                self.assertTrue(imu["active"])
                self.assertIsNone(imu[missing])

    def test_oak_failure_is_not_hidden_by_fresh_external_sample(self) -> None:
        external = self.oak_sample(source="external_imu", configured=True,
                                   connected=True, active=True)
        imu = self.imu_status(external, camera_imu={
            "configured": True, "error": "no onboard samples",
        })
        self.assertEqual(imu["source"], "oak")
        self.assertFalse(imu["active"])
        self.assertIsNone(imu["accelerationMps2"])
        self.assertEqual(imu["error"], "no onboard samples")

    def test_legacy_external_imu_remains_supported_with_fresh_sample_time(self) -> None:
        external = {
            "source": "external_imu", "configured": True, "connected": True,
            "active": True, "lastSampleAtEpochMillis": 1_700_000_000_000,
        }
        imu = self.imu_status(external)
        self.assertTrue(imu["active"])
        self.assertTrue(imu["connected"])
        self.assertEqual(imu["source"], "external_imu")
        external["lastSampleAtEpochMillis"] -= 6000
        self.assertFalse(self.imu_status(external)["active"])

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
