import importlib.util
import io
import tempfile
import unittest
from datetime import datetime, timezone
from pathlib import Path
from unittest.mock import patch


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


if __name__ == "__main__":
    unittest.main()
