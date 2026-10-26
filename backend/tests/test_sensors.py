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

    def gnss_status(self, values, *, heartbeat_age=0):
        now = 1_700_000_000_000
        self.write_status(now - heartbeat_age)
        path = self.root / "status.json"
        raw = json.loads(path.read_text(encoding="utf-8"))
        raw["gnss"].update(values)
        path.write_text(json.dumps(raw), encoding="utf-8")
        return SensorBridgeStore(self.root, clock=lambda: now / 1000).status().gnss

    def test_gnss_base_diagnostics_accept_current_contract(self) -> None:
        for selection in ("client", "caster"):
            with self.subTest(selection=selection):
                values = {
                    "ntripSelection": selection, "ntripError": "  retry\n  ",
                    "rtcmLastAtEpochMillis": 1_699_999_997_000,
                    "rtcmMessageTypes": [1077, 1005, 1005, 1004],
                    "rtcmBaseStationId": 123, "rtcmBaseDistanceM": 7400.5,
                    "ntripLastGgaSentAtEpochMillis": 1_699_999_998_000,
                }
                gnss = self.gnss_status(values)
                for key, value in values.items():
                    expected = {"ntripError": "retry", "rtcmMessageTypes": [1004, 1005, 1077]}.get(key, value)
                    self.assertEqual(gnss[key], expected)
                self.assertTrue(gnss["ntripConnected"])

    def test_gnss_base_diagnostics_default_for_legacy_and_null_values(self) -> None:
        keys = ("ntripSelection", "ntripError", "rtcmLastAtEpochMillis", "rtcmBaseStationId",
                "rtcmBaseDistanceM", "ntripLastGgaSentAtEpochMillis", "rtcmMessageTypes")
        for values in ({}, dict.fromkeys(keys)):
            with self.subTest(values=values):
                gnss = self.gnss_status(values)
                for key in keys:
                    self.assertEqual(gnss[key], [] if key == "rtcmMessageTypes" else None)
                self.assertEqual(gnss["fixType"], "rtk_fixed")

    def test_gnss_base_diagnostics_reject_wrong_types_and_ranges(self) -> None:
        cases = {
            "ntripSelection": ["auto", "CASTER", " client ", True, [], {}],
            "ntripError": [42, False, [], {}, "\n\t"],
            "rtcmLastAtEpochMillis": [True, "123", 123.5, [], {}],
            "ntripLastGgaSentAtEpochMillis": [True, "123", 123.5, [], {}],
            "rtcmBaseStationId": [-1, 4096, True, "123", 123.0, [], {}],
            "rtcmBaseDistanceM": [-0.1, float("nan"), float("inf"), float("-inf"), True, "7400", [], {}],
        }
        for key, values in cases.items():
            for value in values:
                with self.subTest(key=key, value=value):
                    self.assertIsNone(self.gnss_status({key: value})[key])
        for value in (True, "1005", 1005, {}, None):
            with self.subTest(message_types=value):
                self.assertEqual(self.gnss_status({"rtcmMessageTypes": value})["rtcmMessageTypes"], [])

    def test_gnss_rtcm_types_filter_sort_deduplicate_and_limit(self) -> None:
        values = [4095, 1, 1005, 1005, 0, 4096, -1, True, "1077", 1077.5, None, {}]
        self.assertEqual(self.gnss_status({"rtcmMessageTypes": values})["rtcmMessageTypes"], [1, 1005, 4095])
        values = list(range(4095, 0, -1)) * 2
        self.assertEqual(self.gnss_status({"rtcmMessageTypes": values})["rtcmMessageTypes"], list(range(1, 33)))

    def test_gnss_rtcm_signals_accept_all_constellations_and_unknown_ids(self) -> None:
        values = ["GPS 1C", "GPS 5Q", "GAL 7Q", "BDS 5P", "GLO 2P", "QZS 5X",
                  "SBS ?12", "IRN 9A", "SBS ?0", "SBS ?00", "SBS ?99"]
        gnss = self.gnss_status({"rtcmSignals": list(reversed(values)) + values[:2]})
        self.assertEqual(gnss["rtcmSignals"], sorted(values))
        stale = self.gnss_status({"rtcmSignals": values}, heartbeat_age=5001)
        self.assertEqual(stale["rtcmSignals"], sorted(values))
        self.assertFalse(stale["ntripConnected"])

    def test_gnss_rtcm_signals_reject_malformed_items_without_coercion(self) -> None:
        invalid = [None, True, 5, 5.0, {}, [], "", "gps 1C", "XYZ 1C", "GPS 0C", "GPS 1c",
                   "GPS 10C", "GPS 1CC", "GPS １C", "GPS  1C", "GPS\t1C", " GPS 1C",
                   "GPS 1C ", "GPS 1C\n", "GPS ?", "GPS ?100", "GPS ?-1", "GPS ?１２"]
        self.assertEqual(self.gnss_status({"rtcmSignals": invalid + ["GPS 1C", "SBS ?12"]})["rtcmSignals"],
                         ["GPS 1C", "SBS ?12"])

    def test_gnss_rtcm_signals_missing_null_and_non_lists_default_empty(self) -> None:
        for values in ({}, {"rtcmSignals": None}, {"rtcmSignals": "GPS 5Q"},
                       {"rtcmSignals": {"GPS 5Q": True}}, {"rtcmSignals": True},
                       {"rtcmSignals": 5}, {"rtcmSignals": []}):
            with self.subTest(values=values):
                self.assertEqual(self.gnss_status(values)["rtcmSignals"], [])

    def test_gnss_rtcm_signals_limit_after_deduplication_and_sorting(self) -> None:
        values = [f"GPS {band}{code}" for band in range(1, 10) for code in "ABCDEFGHIJKLMNOPQRSTUVWXYZ"]
        self.assertEqual(self.gnss_status({"rtcmSignals": list(reversed(values)) * 2})["rtcmSignals"],
                         sorted(values)[:48])

    def test_gnss_diagnostic_boundaries_and_error_limit(self) -> None:
        for station_id in (0, 4095):
            self.assertEqual(self.gnss_status({"rtcmBaseStationId": station_id})["rtcmBaseStationId"], station_id)
        self.assertEqual(self.gnss_status({"rtcmBaseDistanceM": 0})["rtcmBaseDistanceM"], 0.0)
        gnss = self.gnss_status({"ntripError": "x" * 300, "error": "x" * 300})
        self.assertEqual(gnss["ntripError"], gnss["error"])
        self.assertEqual(len(gnss["ntripError"]), 256)

    def test_stale_gnss_preserves_diagnostics_but_not_ntrip_connection(self) -> None:
        values = {
            "ntripSelection": "caster", "ntripError": "retry",
            "rtcmLastAtEpochMillis": 1_699_999_997_000, "rtcmMessageTypes": [1005],
            "rtcmBaseStationId": 0, "rtcmBaseDistanceM": 0,
            "ntripLastGgaSentAtEpochMillis": 1_699_999_998_000,
        }
        gnss = self.gnss_status(values, heartbeat_age=5001)
        self.assertFalse(gnss["ntripConnected"])
        for key, value in values.items():
            self.assertEqual(gnss[key], value)

    def test_rejects_preview_symlink(self) -> None:
        outside = self.root / "outside.jpg"
        outside.write_bytes(b"\xff\xd8secret\xff\xd9")
        (self.root / "camera-preview.jpg").symlink_to(outside)

        with self.assertRaises(OSError):
            SensorBridgeStore(self.root).preview_frame()


if __name__ == "__main__":
    unittest.main()
