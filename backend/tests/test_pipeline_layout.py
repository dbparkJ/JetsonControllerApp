import subprocess
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from jetson_control.pipeline_layout import discover_pipeline_folder
from jetson_control.pipelines import PipelineManager


class PipelineFolderLayoutTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)
        self.folder = self.root / "camera-capture"
        (self.folder / ".venv" / "bin").mkdir(parents=True)
        python = self.folder / ".venv" / "bin" / "python"
        python.write_text("#!/bin/sh\n", encoding="utf-8")
        python.chmod(0o750)
        (self.folder / "main.py").write_text("print('capture')\n", encoding="utf-8")
        (self.folder / "config.yaml").write_text("fps: 30\n", encoding="utf-8")

    def tearDown(self) -> None:
        self.temporary.cleanup()

    def test_discovers_fixed_paths_without_creating_results(self) -> None:
        layout = discover_pipeline_folder(self.folder)

        self.assertEqual(layout.pipeline_id, "camera-capture")
        self.assertEqual(layout.virtualenv, self.folder / ".venv")
        self.assertEqual(layout.entrypoint, self.folder / "main.py")
        self.assertEqual(layout.config, self.folder / "config.yaml")
        self.assertEqual(layout.results, self.folder / "results")
        self.assertFalse(layout.results.exists())

    def test_rejects_ambiguous_yaml_and_symlink_entrypoint(self) -> None:
        (self.folder / "config.yml").write_text("fps: 15\n", encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "exactly one"):
            discover_pipeline_folder(self.folder)

        (self.folder / "config.yml").unlink()
        (self.folder / "main.py").unlink()
        (self.root / "outside.py").write_text("pass\n", encoding="utf-8")
        (self.folder / "main.py").symlink_to(self.root / "outside.py")
        with self.assertRaisesRegex(FileNotFoundError, "regular main.py"):
            discover_pipeline_folder(self.folder)

    def test_folder_name_is_the_safe_internal_pipeline_id(self) -> None:
        invalid = self.root / "Camera Capture"
        self.folder.rename(invalid)
        with self.assertRaisesRegex(ValueError, "folder name"):
            discover_pipeline_folder(invalid)

    def test_folder_name_allows_systemd_safe_underscores(self) -> None:
        underscored = self.root / "26_camera_record"
        self.folder.rename(underscored)

        layout = discover_pipeline_folder(underscored)

        self.assertEqual(layout.pipeline_id, "26_camera_record")

    def test_manager_folder_registration_calls_shortcut_with_autostart(self) -> None:
        results_root = self.root / "collected-data"
        manager = PipelineManager(
            registry_root=self.root / "registry",
            registrar=Path("/opt/jetson-control/register-pipeline.py"),
            pipeline_user="operator",
            logs_root=self.root / "logs",
            folder_results_root=results_root,
        )
        completed = subprocess.CompletedProcess([], 0, "", "")
        with patch.object(manager, "_run", return_value=completed) as run, patch.object(
            manager,
            "get",
            return_value={"id": "camera-capture"},
        ) as get:
            response = manager.register_folder(
                label="Camera capture",
                repository=self.folder,
            )

        self.assertEqual(response, {"id": "camera-capture"})
        command = run.call_args.args[0]
        self.assertEqual(
            command,
            [
                "/usr/bin/python3",
                "/opt/jetson-control/register-pipeline.py",
                "--folder",
                str(self.folder),
                "--name",
                "Camera capture",
                "--user",
                "operator",
                "--results-dir",
                str(results_root / "camera-capture"),
                "--use-template-defaults",
                "--autostart",
            ],
        )
        get.assert_called_once_with("camera-capture")

    def test_manager_discovers_managed_results_directory(self) -> None:
        results_root = self.root / "collected-data"
        manager = PipelineManager(
            registry_root=self.root / "registry",
            registrar=Path("/opt/jetson-control/register-pipeline.py"),
            pipeline_user="operator",
            folder_results_root=results_root,
        )

        response = manager.discover_folder(self.folder)

        self.assertEqual(
            response["resultsDirectory"],
            str(results_root / "camera-capture"),
        )
        self.assertFalse(response["resultsExists"])


if __name__ == "__main__":
    unittest.main()
