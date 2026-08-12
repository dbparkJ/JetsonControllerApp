import json
import subprocess
import tempfile
import unittest
from pathlib import Path

from jetson_control.pipelines import PipelineManager


def manifest(pipeline_id: str = "capture"):
    return {
        "schema_version": 1,
        "id": pipeline_id,
        "label": "DepthAI Capture",
        "description": "Recorder",
        "entrypoint": "synced_image_recorder.py",
        "config": "configs/capture.yaml",
        "virtualenv": "/data/.venv",
        "python_version": "Python 3.8.10",
        "source_revision": "1234567890abcdef",
        "source_branch": "feature/capture",
        "source_dirty": True,
        "snapshot_created_at": "2026-08-12T00:00:00Z",
        "writable_paths": ["/data/records"],
    }


class FakeCommands:
    def __init__(self) -> None:
        self.commands = []
        self.active_state = "inactive"
        self.sub_state = "dead"
        self.enabled = "disabled"
        self.exit_status = 0
        self.restart_count = 0

    def __call__(self, command, **_kwargs):
        self.commands.append(command)
        if command[:2] == ["systemctl", "show"]:
            output = (
                "LoadState=loaded\n"
                f"ActiveState={self.active_state}\n"
                f"SubState={self.sub_state if self.active_state != 'active' else 'running'}\n"
                f"UnitFileState={self.enabled}\n"
                f"ExecMainStatus={self.exit_status}\n"
                f"Result={'exit-code' if self.exit_status else 'success'}\n"
                f"NRestarts={self.restart_count}\n"
            )
            return subprocess.CompletedProcess(command, 0, output, "")
        if command[:2] == ["systemctl", "start"]:
            self.active_state = "active"
        elif command[:2] == ["systemctl", "stop"]:
            self.active_state = "inactive"
            self.sub_state = "dead"
        elif command[:2] == ["systemctl", "enable"]:
            self.enabled = "enabled"
        elif command[:2] == ["systemctl", "disable"]:
            self.enabled = "disabled"
        return subprocess.CompletedProcess(command, 0, "", "")


class PipelineManagerTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)
        pipeline_root = self.root / "capture"
        pipeline_root.mkdir()
        release = pipeline_root / "releases" / "release-1"
        (release / "configs").mkdir(parents=True)
        (release / "configs" / "capture.yaml").write_text(
            "camera:\n  fps: 30\n",
            encoding="utf-8",
        )
        (pipeline_root / "current").symlink_to(Path("releases") / "release-1")
        (pipeline_root / "pipeline.json").write_text(
            json.dumps(manifest()),
            encoding="utf-8",
        )
        self.commands = FakeCommands()
        self.manager = PipelineManager(
            registry_root=self.root,
            registrar=Path("/opt/jetson-control/register-pipeline.py"),
            pipeline_user="jm",
            command_runner=self.commands,
        )

    def tearDown(self) -> None:
        self.temporary.cleanup()

    def test_lists_manifest_and_systemd_state(self) -> None:
        pipelines = self.manager.list_pipelines()
        self.assertEqual(len(pipelines), 1)
        self.assertEqual(pipelines[0]["id"], "capture")
        self.assertEqual(pipelines[0]["state"], "STOPPED")
        self.assertFalse(pipelines[0]["enabled"])
        self.assertTrue(pipelines[0]["sourceDirty"])

    def test_control_uses_exact_allowlisted_unit(self) -> None:
        response = self.manager.control("capture", "start")
        self.assertEqual(response["state"], "RUNNING")
        self.assertIn(
            ["systemctl", "start", "jetson-pipeline@capture.service"],
            self.commands.commands,
        )
        with self.assertRaises(ValueError):
            self.manager.control("capture", "status; reboot")

    def test_auto_restart_is_reported_as_retrying(self) -> None:
        self.commands.active_state = "activating"
        self.commands.sub_state = "auto-restart"
        self.commands.exit_status = 1
        self.commands.restart_count = 12
        pipeline = self.manager.list_pipelines()[0]
        self.assertEqual(pipeline["state"], "RETRYING")
        self.assertEqual(pipeline["lastExitCode"], 1)
        self.assertEqual(pipeline["restartCount"], 12)

    def test_malformed_registry_entry_does_not_hide_valid_pipeline(self) -> None:
        invalid = self.root / "invalid"
        invalid.mkdir()
        (invalid / "pipeline.json").write_text("{}", encoding="utf-8")
        self.assertEqual([item["id"] for item in self.manager.list_pipelines()], ["capture"])

    def test_reads_and_atomically_updates_runtime_yaml(self) -> None:
        document = self.manager.config_document("capture")
        self.assertEqual(document["path"], "configs/capture.yaml")
        self.assertIn("fps: 30", document["content"])

        updated = self.manager.update_config("capture", "camera:\n  fps: 15\n")
        self.assertEqual(updated["content"], "camera:\n  fps: 15\n")
        self.assertEqual(
            (self.root / "capture" / "current" / "configs" / "capture.yaml").read_text(),
            "camera:\n  fps: 15\n",
        )

    def test_logs_use_exact_unit_and_bounded_line_count(self) -> None:
        response = self.manager.logs("capture", 5000)
        self.assertEqual(response["pipelineId"], "capture")
        self.assertEqual(response["lines"], [])
        self.assertIn(
            [
                "journalctl",
                "--unit",
                "jetson-pipeline@capture.service",
                "--lines",
                "1000",
                "--output",
                "short-iso",
                "--no-pager",
            ],
            self.commands.commands,
        )


if __name__ == "__main__":
    unittest.main()
