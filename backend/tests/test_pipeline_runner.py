import importlib.util
import io
import json
import os
import tempfile
import unittest
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


if __name__ == "__main__":
    unittest.main()
