import argparse
import importlib.util
import io
import json
import os
import pwd
import subprocess
import sys
import tempfile
import unittest
from contextlib import redirect_stdout
from pathlib import Path
from unittest.mock import patch


SCRIPT = Path(__file__).parents[1] / "scripts" / "register-pipeline.py"
SPEC = importlib.util.spec_from_file_location("pipeline_registrar", SCRIPT)
assert SPEC is not None and SPEC.loader is not None
registrar = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(registrar)


class PipelineRegistrarTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory()
        self.base = Path(self.temporary.name)
        self.git_root = self.base / "worktree"
        self.git_root.mkdir()
        self.repo = self.git_root / "project"
        self.repo.mkdir()
        self._git("init")
        self._git("symbolic-ref", "HEAD", "refs/heads/main")
        self._git("config", "user.name", "Test")
        self._git("config", "user.email", "test@example.invalid")
        (self.repo / ".gitignore").write_text("ignored.bin\n", encoding="utf-8")
        (self.repo / "main.py").write_text("print('capture')\n", encoding="utf-8")
        (self.repo / "config.yaml").write_text("output: records\n", encoding="utf-8")
        self._git(
            "add",
            "project/.gitignore",
            "project/main.py",
            "project/config.yaml",
        )
        self._git("commit", "-m", "initial")
        (self.git_root / "outside.py").write_text("OUTSIDE = True\n", encoding="utf-8")
        self._git("add", "outside.py")
        self._git("commit", "-m", "outside source")
        (self.repo / "new_module.py").write_text("VALUE = 1\n", encoding="utf-8")
        (self.repo / "ignored.bin").write_bytes(b"do-not-copy")

        self.venv = self.base / ".venv"
        (self.venv / "bin").mkdir(parents=True)
        (self.venv / "bin" / "python").symlink_to(Path(sys.executable).resolve())
        self.registry = self.base / "registry"
        self.systemd = self.base / "systemd"
        self.commands = []

    def tearDown(self) -> None:
        self.temporary.cleanup()

    def _git(self, *arguments: str) -> None:
        subprocess.run(
            ["git", "-C", str(self.git_root), *arguments],
            check=True,
            capture_output=True,
            text=True,
        )

    def test_pipeline_id_allows_systemd_safe_underscores(self) -> None:
        self.assertEqual(registrar.validate_id("26_camera_record"), "26_camera_record")
        with self.assertRaisesRegex(ValueError, "lowercase letters"):
            registrar.validate_id("Camera_Record")

    def test_registers_dirty_git_snapshot_and_omits_ignored_files(self) -> None:
        account = pwd.getpwuid(os.getuid())
        args = argparse.Namespace(
            id="capture",
            label="Capture",
            description="Recorder",
            repo=self.repo,
            venv=self.venv,
            entry="main.py",
            config="config.yaml",
            working_dir=self.repo,
            write_path=[self.repo / "records"],
            argument=[],
            user=account.pw_name,
            autostart=True,
            no_autostart=False,
            start_now=False,
            restart_running=False,
        )
        original_run = registrar.run

        def fake_run(command, *, timeout=60):
            if command[0] == "systemctl":
                self.commands.append(list(command))
                return subprocess.CompletedProcess(
                    command,
                    3 if command[1:3] == ["is-active", "--quiet"] else 0,
                    "",
                    "",
                )
            return original_run(command, timeout=timeout)

        with patch.object(registrar, "REGISTRY_ROOT", self.registry), \
             patch.object(registrar, "SYSTEMD_ROOT", self.systemd), \
             patch.object(registrar, "run", side_effect=fake_run), \
             patch.object(registrar.os, "chown"), \
             patch.object(registrar.os, "lchown"):
            with redirect_stdout(io.StringIO()):
                registrar.register(args)

        pipeline_root = self.registry / "capture"
        release = (pipeline_root / "current").resolve()
        self.assertTrue((release / "main.py").is_file())
        self.assertTrue((release / "config.yaml").is_file())
        self.assertTrue((release / "new_module.py").is_file())
        self.assertFalse((release / "ignored.bin").exists())
        self.assertFalse((release / ".git").exists())
        self.assertFalse((release / "outside.py").exists())

        manifest = json.loads((pipeline_root / "pipeline.json").read_text(encoding="utf-8"))
        self.assertTrue(manifest["source_dirty"])
        self.assertEqual(manifest["source_git_root"], str(self.git_root))
        self.assertEqual(manifest["python"], str(self.venv / "bin" / "python"))
        self.assertEqual(manifest["entrypoint"], "main.py")
        self.assertEqual(manifest["config"], "config.yaml")
        self.assertIn(
            ["systemctl", "enable", "jetson-pipeline@capture.service"],
            self.commands,
        )
        override = self.systemd / "jetson-pipeline@capture.service.d" / "override.conf"
        override_text = override.read_text(encoding="utf-8")
        self.assertIn(f"User={account.pw_name}", override_text)
        self.assertIn(f"WorkingDirectory={self.repo}", override_text)
        self.assertNotIn(f'WorkingDirectory="{self.repo}"', override_text)

    def test_systemd_path_escapes_spaces_percent_and_unicode(self) -> None:
        escaped = registrar.systemd_path(Path("/home/Test Path/100%/한글"))
        self.assertEqual(
            escaped,
            "/home/Test\\x20Path/100%%/\\xed\\x95\\x9c\\xea\\xb8\\x80",
        )

    def test_folder_shortcut_infers_convention_and_defaults_to_autostart(self) -> None:
        (self.repo / ".venv").mkdir()
        args = argparse.Namespace(
            folder=self.repo,
            id=None,
            label="카메라 수집",
            repo=None,
            venv=None,
            entry=None,
            config=None,
            working_dir=None,
            write_path=[],
            autostart=False,
            no_autostart=False,
        )

        registrar.apply_folder_convention(args)

        self.assertEqual(args.id, "project")
        self.assertEqual(args.repo, self.repo)
        self.assertEqual(args.venv, self.repo / ".venv")
        self.assertEqual(args.entry, "main.py")
        self.assertEqual(args.config, "config.yaml")
        self.assertEqual(args.working_dir, self.repo)
        self.assertEqual(args.write_path, [self.repo / "results"])
        self.assertTrue(args.autostart)

    def test_folder_registration_excludes_runtime_directories_from_snapshot(self) -> None:
        (self.repo / ".venv" / "bin").mkdir(parents=True)
        (self.repo / ".venv" / "bin" / "python").symlink_to(
            Path(sys.executable).resolve()
        )
        (self.repo / ".venv" / "local.bin").write_bytes(b"venv")
        (self.repo / "logs").mkdir()
        (self.repo / "logs" / "old.log").write_text("old", encoding="utf-8")
        (self.repo / "results").mkdir()
        (self.repo / "results" / "old.bin").write_bytes(b"result")
        account = pwd.getpwuid(os.getuid())
        args = argparse.Namespace(
            folder=self.repo,
            id=None,
            label="Camera capture",
            description="",
            repo=None,
            venv=None,
            entry=None,
            config=None,
            working_dir=None,
            write_path=[],
            argument=[],
            user=account.pw_name,
            autostart=True,
            no_autostart=False,
            start_now=False,
            restart_running=False,
        )
        original_run = registrar.run

        def fake_run(command, *, timeout=60):
            if command[0] == "systemctl":
                return subprocess.CompletedProcess(
                    command,
                    3 if command[1:3] == ["is-active", "--quiet"] else 0,
                    "",
                    "",
                )
            return original_run(command, timeout=timeout)

        with patch.object(registrar, "REGISTRY_ROOT", self.registry), \
             patch.object(registrar, "SYSTEMD_ROOT", self.systemd), \
             patch.object(registrar, "run", side_effect=fake_run), \
             patch.object(registrar.os, "chown"), \
             patch.object(registrar.os, "lchown"):
            with redirect_stdout(io.StringIO()):
                registrar.register(args)

        pipeline_root = self.registry / "project"
        release = (pipeline_root / "current").resolve()
        self.assertFalse((release / ".venv").exists())
        self.assertFalse((release / "logs").exists())
        self.assertFalse((release / "results").exists())
        manifest = json.loads((pipeline_root / "pipeline.json").read_text())
        self.assertTrue(manifest["folder_convention"])
        self.assertEqual(manifest["results_directory"], str(self.repo / "results"))

    def test_writable_directory_rejects_final_symlink(self) -> None:
        outside = self.base / "outside-results"
        outside.mkdir()
        link = self.repo / "results"
        link.symlink_to(outside, target_is_directory=True)

        with self.assertRaisesRegex(ValueError, "unsafe"):
            registrar.prepare_writable_directory(
                link,
                os.getuid(),
                os.getgid(),
                required_path=link,
            )

    def test_git_uses_temporary_scoped_safe_directories(self) -> None:
        self.assertEqual(
            registrar.git_safe_directories(Path("/home/operator/capture/project")),
            [
                Path("/home/operator/capture/project"),
                Path("/home/operator/capture"),
                Path("/home/operator"),
                Path("/home"),
            ],
        )

        safe_directories = registrar.git(
            self.repo,
            "config",
            "--global",
            "--get-all",
            "safe.directory",
        ).splitlines()
        self.assertEqual(
            safe_directories,
            [str(path) for path in registrar.git_safe_directories(self.repo)],
        )
        self.assertNotIn("*", safe_directories)


if __name__ == "__main__":
    unittest.main()
