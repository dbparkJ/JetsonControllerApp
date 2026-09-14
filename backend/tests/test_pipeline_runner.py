import importlib.util
import io
import json
import os
import tempfile
import unittest
from types import SimpleNamespace
from datetime import datetime, timezone
from pathlib import Path
from unittest.mock import Mock, patch


RUNNER_PATH = Path(__file__).parents[1] / "scripts" / "run-pipeline.py"
SPEC = importlib.util.spec_from_file_location("pipeline_runner", RUNNER_PATH)
assert SPEC is not None and SPEC.loader is not None
pipeline_runner = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(pipeline_runner)


class PipelineRunnerLogTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory()
        self.directory = Path(self.temporary.name)
        self.stdout = type("BinaryStdout", (), {"buffer": io.BytesIO()})()
        self.stdout_patch = patch.object(pipeline_runner.sys, "stdout", self.stdout)
        self.stdout_patch.start()

    def tearDown(self) -> None:
        self.stdout_patch.stop()
        self.temporary.cleanup()

    def test_pipeline_id_allows_systemd_safe_underscores(self) -> None:
        self.assertIsNotNone(
            pipeline_runner.PIPELINE_ID.fullmatch("26_camera_record")
        )
        self.assertIsNone(pipeline_runner.PIPELINE_ID.fullmatch("Camera_Record"))

    def test_each_writer_creates_a_separate_immediately_readable_file(self) -> None:
        timestamps = [
            datetime(2026, 8, 14, 0, 0, 0, 1, tzinfo=timezone.utc),
            datetime(2026, 8, 14, 0, 0, 1, 1, tzinfo=timezone.utc),
        ]
        with patch.object(pipeline_runner, "utc_now", side_effect=timestamps):
            first = pipeline_runner.RunLogWriter(self.directory)
            first.emit(b"first run\n")
            self.assertEqual(first.path.read_bytes(), b"first run\n")
            first.close()
            second = pipeline_runner.RunLogWriter(self.directory)
            second.emit(b"second run\n")
            second.close()

        self.assertNotEqual(first.path, second.path)
        self.assertEqual(first.path.read_bytes(), b"first run\n")
        self.assertEqual(second.path.read_bytes(), b"second run\n")

    def test_run_log_is_bounded_and_marks_truncation(self) -> None:
        with patch.object(pipeline_runner, "MAX_RUN_LOG_BYTES", 5):
            writer = pipeline_runner.RunLogWriter(self.directory)
            writer.emit(b"123456789")
            writer.close()

        self.assertEqual(
            writer.path.read_bytes(),
            b"12345" + pipeline_runner.LOG_TRUNCATED,
        )

    def test_finish_metadata_is_kept_after_child_output_is_truncated(self) -> None:
        with patch.object(pipeline_runner, "MAX_RUN_LOG_BYTES", 5):
            writer = pipeline_runner.RunLogWriter(self.directory)
            writer.emit(b"123456789")
            writer.emit_footer(b"\nfinished_at=now\nexit_code=0\n")
            writer.close()

        self.assertTrue(writer.path.read_bytes().endswith(b"finished_at=now\nexit_code=0\n"))

    def test_storage_preflight_requires_capacity_and_cleans_probe(self) -> None:
        with patch.object(
            pipeline_runner.os,
            "statvfs",
            return_value=SimpleNamespace(f_bavail=10, f_frsize=1024),
        ):
            available = pipeline_runner.preflight_writable_storage(
                [self.directory],
                required_bytes=1024,
            )
        self.assertEqual(available, 10 * 1024)
        self.assertEqual(list(self.directory.glob(".jetson-pipeline-preflight-*")), [])

        with patch.object(
            pipeline_runner.os,
            "statvfs",
            return_value=SimpleNamespace(f_bavail=1, f_frsize=1024),
        ):
            with self.assertRaisesRegex(pipeline_runner.StoragePreflightError, "required"):
                pipeline_runner.preflight_writable_storage(
                    [self.directory],
                    required_bytes=2048,
                )

    def test_minimum_free_storage_default_and_override_are_bounded(self) -> None:
        self.assertEqual(pipeline_runner.minimum_free_bytes({}), 0)
        self.assertEqual(
            pipeline_runner.minimum_free_bytes({"JETSON_PIPELINE_MIN_FREE_BYTES": "0"}),
            0,
        )
        with self.assertRaises(pipeline_runner.StoragePreflightError):
            pipeline_runner.minimum_free_bytes({"JETSON_PIPELINE_MIN_FREE_BYTES": "1GiB"})

    def test_application_exit_78_remains_recoverable_by_systemd(self) -> None:
        self.assertEqual(
            pipeline_runner.service_exit_code(
                pipeline_runner.STORAGE_PREFLIGHT_EXIT_CODE
            ),
            1,
        )
        self.assertEqual(pipeline_runner.service_exit_code(7), 7)

    def test_retention_removes_oldest_run_files_only(self) -> None:
        for index in range(4):
            path = self.directory / f"run-20260814T00000{index}.000001Z-{index}.log"
            path.write_bytes(b"1234")
            path.touch()
        unrelated = self.directory / "keep.txt"
        unrelated.write_text("keep", encoding="utf-8")

        with patch.object(pipeline_runner, "MAX_LOG_FILES", 3), \
                patch.object(pipeline_runner, "MAX_LOG_TOTAL_BYTES", 100), \
                patch.object(pipeline_runner, "MAX_RUN_LOG_BYTES", 10):
            pipeline_runner.prune_logs(self.directory)

        self.assertEqual(
            len(list(self.directory.glob("run-*.log"))),
            2,
        )
        self.assertTrue(unrelated.exists())

    def test_time_sync_marker_must_be_owned_and_not_writable_by_others(self) -> None:
        marker = self.directory / "time-synchronized.json"
        marker.write_text(
            json.dumps(
                {
                    "schemaVersion": 1,
                    "synchronized": True,
                    "source": "MOBILE",
                    "sourceTimeEpochMillis": 1_777_000_000_000,
                    "synchronizedAtEpochMillis": 1_777_000_000_000,
                    "offsetBeforeMillis": 0,
                }
            ),
            encoding="utf-8",
        )
        marker.chmod(0o644)
        self.assertTrue(
            pipeline_runner.time_sync_ready(
                marker,
                expected_owner_uid=os.getuid(),
            )
        )

        marker.chmod(0o666)
        self.assertFalse(
            pipeline_runner.time_sync_ready(
                marker,
                expected_owner_uid=os.getuid(),
            )
        )

    def test_runner_waits_until_mobile_time_marker_is_available(self) -> None:
        marker = self.directory / "time-synchronized.json"
        sleep_calls = []

        def release(_seconds):
            sleep_calls.append(1)
            marker.write_text(
                json.dumps(
                    {
                        "schemaVersion": 1,
                        "synchronized": True,
                        "source": "MOBILE",
                        "sourceTimeEpochMillis": 1_777_000_000_000,
                        "synchronizedAtEpochMillis": 1_777_000_000_000,
                        "offsetBeforeMillis": 0,
                    }
                ),
                encoding="utf-8",
            )
            marker.chmod(0o644)

        with patch.object(pipeline_runner.sys, "stdout", io.StringIO()):
            ready = pipeline_runner.wait_for_time_sync(
                marker,
                expected_owner_uid=os.getuid(),
                sleep=release,
            )

        self.assertTrue(ready)
        self.assertEqual(sleep_calls, [1])

    def test_process_group_cleanup_escalates_before_device_handoff_release(self) -> None:
        child = Mock(pid=4242)
        child.wait.return_value = 0
        with patch.object(
            pipeline_runner,
            "process_group_exists",
            side_effect=[True, True, True, True, False],
        ), patch.object(pipeline_runner, "signal_process_group") as send:
            pipeline_runner.stop_process_group(child, timeout_seconds=0)

        self.assertEqual(
            send.call_args_list,
            [
                unittest.mock.call(4242, pipeline_runner.signal.SIGTERM),
                unittest.mock.call(4242, pipeline_runner.signal.SIGKILL),
            ],
        )

    def test_active_mobile_rtk_relay_overrides_only_ntrip_route(self) -> None:
        marker = self.directory / "mobile-rtk-relay.json"
        marker.write_text(
            json.dumps(
                {
                    "schemaVersion": 1,
                    "pipelineId": "capture",
                    "relayHost": "192.168.49.71",
                    "relayPort": 32101,
                    "expiresAtEpochMillis": 2_000,
                }
            ),
            encoding="utf-8",
        )
        marker.chmod(0o644)

        environment = pipeline_runner.mobile_rtk_relay_environment(
            "capture",
            marker,
            expected_owner_uid=os.getuid(),
            clock_millis=lambda: 1_000,
        )

        self.assertEqual(environment["NTRIP_HOST"], "192.168.49.71")
        self.assertEqual(environment["NTRIP_PORT"], "32101")
        self.assertEqual(environment["JETSON_PIPELINE_MOBILE_RTK_RELAY"], "1")

    def test_expired_or_other_pipeline_mobile_relay_is_ignored(self) -> None:
        marker = self.directory / "mobile-rtk-relay.json"
        marker.write_text(
            json.dumps(
                {
                    "schemaVersion": 1,
                    "pipelineId": "capture",
                    "relayHost": "192.168.49.71",
                    "relayPort": 32101,
                    "expiresAtEpochMillis": 2_000,
                }
            ),
            encoding="utf-8",
        )
        marker.chmod(0o644)

        self.assertEqual(
            pipeline_runner.mobile_rtk_relay_environment(
                "other",
                marker,
                expected_owner_uid=os.getuid(),
                clock_millis=lambda: 1_000,
            ),
            {},
        )
        self.assertEqual(
            pipeline_runner.mobile_rtk_relay_environment(
                "capture",
                marker,
                expected_owner_uid=os.getuid(),
                clock_millis=lambda: 2_001,
            ),
            {},
        )

    def test_contextual_launch_requires_fresh_same_boot_root_owned_reservation(self) -> None:
        pipeline_root = self.directory / "capture"
        pipeline_root.mkdir()
        policy = {
            "schemaVersion": 1,
            "pipelineId": "capture",
            "configured": True,
            "revision": "a" * 64,
        }
        (pipeline_root / "run-policy.json").write_text(json.dumps(policy))
        (pipeline_root / "run-policy.json").chmod(0o644)
        launch = {
            "schemaVersion": 1,
            "pipelineId": "capture",
            "runId": "capture/run-20260914T000000.000001Z-1.log",
            "logId": "run-20260914T000000.000001Z-1.log",
            "startedAtEpochMillis": 10_000,
            "bootId": "fixture-boot",
            "policySnapshot": {"revision": "a" * 64},
        }
        (pipeline_root / "next-run.json").write_text(json.dumps(launch))
        (pipeline_root / "next-run.json").chmod(0o644)

        loaded = pipeline_runner.load_contextual_launch(
            pipeline_root,
            "capture",
            expected_owner_uid=os.geteuid(),
            clock_millis=lambda: 11_000,
            boot_id=lambda: "fixture-boot",
        )
        self.assertEqual(loaded["runId"], launch["runId"])

        with self.assertRaisesRegex(pipeline_runner.StoragePreflightError, "identity"):
            pipeline_runner.load_contextual_launch(
                pipeline_root,
                "capture",
                expected_owner_uid=os.geteuid(),
                clock_millis=lambda: 200_001,
                boot_id=lambda: "fixture-boot",
            )

    def test_contextual_launch_rejects_changed_release_or_config(self) -> None:
        release = self.directory / "release"
        release.mkdir()
        manifest = {"source_revision": "a" * 40, "source_dirty": False}
        context = {
            "surveyProjectId": "p",
            "surveySectionId": "s",
        }
        policy = {"revision": "c" * 64}
        launch = {
            "runId": "capture/run-20260914T000000.000001Z-1.log",
            "deviceId": "device",
            "sourceRevision": "a" * 40,
            "sourceDirty": False,
            "release": str(release),
            "configRevision": "b" * 64,
            "contextSnapshot": context,
            "policySnapshot": policy,
            "preflightSnapshot": {
                "ready": True,
                "contextSnapshot": context,
                "policy": policy,
            },
            "output": {"outputId": "output"},
            "outputContext": {
                "surveyProjectId": "p",
                "surveySectionId": "s",
                "runId": "capture/run-20260914T000000.000001Z-1.log",
                "deviceId": "device",
                "pipelineId": "capture",
                "sourceRevision": "a" * 40,
                "configSha256": "b" * 64,
                "outputId": "output",
            },
        }
        pipeline_runner.validate_contextual_launch(
            launch, "capture", manifest, release, "b" * 64
        )
        with self.assertRaises(pipeline_runner.StoragePreflightError):
            pipeline_runner.validate_contextual_launch(
                launch, "capture", manifest, self.directory / "other", "b" * 64
            )

    def test_contextual_required_sensor_failure_is_explicit(self) -> None:
        bridge = self.directory / "bridge"
        bridge.mkdir()
        now = 1_777_000_000_000
        (bridge / "status.json").write_text(json.dumps({
            "schemaVersion": 1,
            "updatedAtEpochMillis": now,
            "camera": {"configured": True, "connected": True, "active": False},
            "gnss": {},
            "imu": {},
        }))
        launch = {
            "policySnapshot": {
                "requiredSensors": ["camera"],
                "optionalSensors": ["gnss"],
            }
        }
        with patch("jetson_control.sensors.time.time", return_value=now / 1000):
            with self.assertRaisesRegex(
                pipeline_runner.StoragePreflightError, "camera"
            ):
                pipeline_runner.contextual_sensor_preflight(launch, bridge, now)


if __name__ == "__main__":
    unittest.main()
