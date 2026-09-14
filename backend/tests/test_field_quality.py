import unittest
from types import SimpleNamespace

from jetson_control.field_quality import (
    FieldQualitySampler,
    attach_route_indexes,
    summarize_quality,
)
from jetson_control.mobile_rtk import classify_rtk_fix


def observation(at, *, window=2000, fix="FIXED", gnss="ACTIVE", requirement="UNSPECIFIED"):
    return {
        "schemaVersion": 1,
        "observedAtEpochMillis": at,
        "observationWindowMillis": window,
        "rtkFixState": fix,
        "sensors": {
            "camera": {"state": "ACTIVE", "requirement": "UNSPECIFIED"},
            "gnss": {"state": gnss, "requirement": requirement},
            "imu": {"state": "ACTIVE", "requirement": "UNSPECIFIED"},
        },
    }


class MobileRtkFixClassificationTest(unittest.TestCase):
    def test_normalizes_reported_categories_without_a_pass_threshold(self):
        self.assertEqual(classify_rtk_fix("rtk_fixed", "", None, has_sample=True), "FIXED")
        self.assertEqual(classify_rtk_fix("", "", 5, has_sample=True), "FLOAT")
        self.assertEqual(classify_rtk_fix("", "", 1, has_sample=True), "STANDALONE")
        self.assertEqual(classify_rtk_fix("", "", 4.9, has_sample=True), "UNKNOWN")
        self.assertEqual(classify_rtk_fix("rtk_fixed", "", 4, has_sample=False), "NO_SAMPLE")


class FieldQualitySamplerTest(unittest.TestCase):
    def snapshot(self, sample_at, *, active=True, configured=True):
        return SimpleNamespace(
            available=True,
            fresh=True,
            updated_at_epoch_millis=10_000,
            camera={},
            gnss={
                "configured": configured,
                "connected": active,
                "active": active,
                "lastSampleAtEpochMillis": sample_at,
                "fixType": "rtk_fixed",
            },
            imu={},
        )

    def test_requirements_only_come_from_explicit_policy(self):
        default = FieldQualitySampler().observe(self.snapshot(10_000), 10_000)
        policy = FieldQualitySampler({"gnss": "required", "imu": "optional"}).observe(
            self.snapshot(10_000), 10_000
        )

        self.assertEqual(default["sensors"]["gnss"]["requirement"], "UNSPECIFIED")
        self.assertEqual(policy["sensors"]["gnss"]["requirement"], "REQUIRED")
        self.assertEqual(policy["sensors"]["imu"]["requirement"], "OPTIONAL")

    def test_active_flag_needs_a_current_nonfuture_sample(self):
        sampler = FieldQualitySampler(observation_window_millis=2_000)

        missing = sampler.observe(self.snapshot(None), 10_000)
        old = FieldQualitySampler().observe(self.snapshot(7_999), 10_000)
        future = FieldQualitySampler().observe(self.snapshot(10_001), 10_000)

        self.assertEqual(missing["sensors"]["gnss"]["state"], "LOST")
        self.assertEqual(old["sensors"]["gnss"]["state"], "STALE")
        self.assertEqual(future["sensors"]["gnss"]["state"], "STALE")
        self.assertTrue(future["sensors"]["gnss"]["clockIssue"])
        self.assertEqual(missing["rtkFixState"], "NO_SAMPLE")

    def test_sample_high_watermark_survives_backward_values_until_recovery(self):
        sampler = FieldQualitySampler(observation_window_millis=10)

        states = [
            sampler.observe(self.snapshot(sample), observed)["sensors"]["gnss"]["state"]
            for sample, observed in ((10, 10), (5, 11), (8, 12), (12, 12))
        ]

        self.assertEqual(states, ["ACTIVE", "STALE", "STALE", "ACTIVE"])

    def test_future_sample_does_not_poison_next_valid_sample(self):
        sampler = FieldQualitySampler(observation_window_millis=10)

        future = sampler.observe(self.snapshot(11), 10)
        valid = sampler.observe(self.snapshot(10), 10)

        self.assertEqual(future["sensors"]["gnss"]["state"], "STALE")
        self.assertEqual(valid["sensors"]["gnss"]["state"], "ACTIVE")


class FieldQualitySummaryTest(unittest.TestCase):
    def test_fix_ratio_uses_known_gnss_time_and_does_not_carry_through_gap(self):
        values = [
            observation(0, fix="FIXED"),
            observation(2_000, fix="FIXED"),
            observation(8_000, fix="FLOAT"),
            observation(10_000, fix="FLOAT"),
        ]

        summary = summarize_quality(values)

        self.assertEqual(summary["elapsedDurationMillis"], 10_000)
        self.assertEqual(summary["observedDurationMillis"], 6_000)
        self.assertEqual(summary["unknownDurationMillis"], 4_000)
        self.assertEqual(summary["rtkObservedDurationMillis"], 6_000)
        self.assertEqual(summary["rtkUnknownDurationMillis"], 4_000)
        self.assertEqual(summary["rtkFixDurationMillis"], 4_000)
        self.assertAlmostEqual(summary["rtkFixRatio"], 2 / 3, places=6)
        self.assertNotIn("passed", summary)
        self.assertEqual(
            [(item["kind"], item["durationMillis"]) for item in summary["problemIntervals"]
             if item["sensor"] in (None, "gnss")],
            [("CLOCK_GAP", 4_000), ("RTK_NOT_FIXED", 2_000)],
        )

    def test_absent_gnss_is_no_samples_instead_of_measured_zero(self):
        summary = summarize_quality([
            observation(0, fix="NO_SAMPLE", gnss="LOST"),
            observation(2_000, fix="NO_SAMPLE", gnss="LOST"),
        ])

        self.assertEqual(summary["sampleState"], "NO_SAMPLES")
        self.assertIsNone(summary["rtkObservedDurationMillis"])
        self.assertIsNone(summary["rtkFixDurationMillis"])
        self.assertIsNone(summary["rtkFixRatio"])
        self.assertEqual(summary["rtkUnknownDurationMillis"], 2_000)
        self.assertIn("GNSS_LOST", [item["kind"] for item in summary["problemIntervals"]])

    def test_inconsistent_active_flag_with_no_sample_still_has_null_ratio(self):
        summary = summarize_quality([
            observation(0, fix="NO_SAMPLE", gnss="ACTIVE"),
            observation(2_000, fix="NO_SAMPLE", gnss="ACTIVE"),
        ])

        self.assertEqual(summary["sampleState"], "NO_SAMPLES")
        self.assertIsNone(summary["rtkObservedDurationMillis"])
        self.assertIsNone(summary["rtkFixRatio"])

    def test_unknown_fix_category_is_not_counted_as_known_nonfix_time(self):
        summary = summarize_quality([
            observation(0, fix="UNKNOWN", gnss="ACTIVE"),
            observation(2_000, fix="UNKNOWN", gnss="ACTIVE"),
        ])

        self.assertEqual(summary["sampleState"], "UNKNOWN")
        self.assertIsNone(summary["rtkObservedDurationMillis"])
        self.assertIsNone(summary["rtkFixRatio"])
        self.assertEqual(summary["rtkUnknownDurationMillis"], 2_000)
        self.assertIn("RTK_UNKNOWN", [item["kind"] for item in summary["problemIntervals"]])

    def test_clock_reversal_does_not_double_count_recovered_timeline(self):
        summary = summarize_quality([
            observation(0, window=10, fix="FIXED"),
            observation(10, window=10, fix="FIXED"),
            observation(5, window=10, fix="FIXED"),
            observation(8, window=10, fix="FIXED"),
            observation(12, window=10, fix="FIXED"),
        ])

        self.assertEqual(summary["elapsedDurationMillis"], 12)
        self.assertEqual(summary["observedDurationMillis"], 10)
        self.assertEqual(summary["rtkFixDurationMillis"], 10)
        self.assertEqual(summary["rtkUnknownDurationMillis"], 2)
        self.assertEqual(summary["rtkFixRatio"], 1.0)
        self.assertIn("CLOCK_GAP", [item["kind"] for item in summary["problemIntervals"]])

    def test_route_indexes_require_temporal_overlap(self):
        base = summarize_quality([
            observation(0, fix="FLOAT"),
            observation(2_000, fix="FLOAT"),
        ])
        outside = attach_route_indexes(base, [
            {"timestamp": 10_000, "latitude": 37.0, "longitude": 127.0},
            {"timestamp": 12_000, "latitude": 37.1, "longitude": 127.1},
        ])
        self.assertIsNone(outside["problemIntervals"][0]["startRoutePointIndex"])
        self.assertIsNone(outside["problemIntervals"][0]["endRoutePointIndex"])

        overlapping = summarize_quality([
            observation(10_500, fix="FLOAT"),
            observation(11_500, fix="FLOAT"),
        ])
        located = attach_route_indexes(overlapping, [
            {"timestamp": 10_000, "latitude": 37.0, "longitude": 127.0},
            {"timestamp": 12_000, "latitude": 37.1, "longitude": 127.1},
        ])
        self.assertEqual(located["problemIntervals"][0]["startRoutePointIndex"], 0)
        self.assertEqual(located["problemIntervals"][0]["endRoutePointIndex"], 1)


if __name__ == "__main__":
    unittest.main()
