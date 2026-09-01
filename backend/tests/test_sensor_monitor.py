import json
import os
import pwd
import signal
import tempfile
import unittest
from pathlib import Path
from unittest.mock import Mock, patch

from jetson_control.sensor_handoff import SensorMonitorSettings
from jetson_control.sensor_monitor import load_monitor_runtime, stop_child, supervise


class SensorMonitorRuntimeTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)
        self.registry = self.root / "registry"
        self.pipeline_root = self.registry / "depthai-capture"
        self.release = self.pipeline_root / "releases" / "release-1"
        self.release.mkdir(parents=True)
        (self.release / "main.py").write_text("pass\n", encoding="utf-8")
        (self.release / "config.yaml").write_text("fps: 30\n", encoding="utf-8")
        for directory in (
            self.registry,
            self.pipeline_root,
            self.pipeline_root / "releases",
            self.release,
        ):
            directory.chmod(0o750)
        (self.release / "main.py").chmod(0o640)
        (self.release / "config.yaml").chmod(0o640)
        (self.pipeline_root / "current").symlink_to(self.release)

        self.virtualenv = self.root / "venv"
        python = self.virtualenv / "bin" / "python"
        python.parent.mkdir(parents=True)
        python.write_text("#!/bin/sh\n", encoding="utf-8")
        python.chmod(0o755)
        self.working = self.root / "working"
        self.working.mkdir()
        self.secrets_root = self.root / "secrets"
        self.secrets_root.mkdir(mode=0o700)
        manifest = {
            "schema_version": 1,
            "id": "depthai-capture",
            "release": self.release.name,
            "virtualenv": str(self.virtualenv),
            "python": str(python),
            "entrypoint": "main.py",
            "config": "config.yaml",
            "config_argument": "--config",
            "working_directory": str(self.working),
            "arguments": ["--output-dir", "/data/collections"],
            "user": pwd.getpwuid(os.getuid()).pw_name,
        }
        manifest_path = self.pipeline_root / "pipeline.json"
        manifest_path.write_text(
            json.dumps(manifest), encoding="utf-8"
        )
        manifest_path.chmod(0o644)
        self.settings = SensorMonitorSettings(
            pipeline_id="depthai-capture",
            bridge_dir=self.root / "bridge",
            registry_root=self.registry,
            monitor_arguments=("--monitor-only", "--allow-usb2", "--fps", "4"),
        )

    def tearDown(self) -> None:
        self.temporary.cleanup()

    def test_runtime_appends_non_recording_low_bandwidth_arguments(self) -> None:
        runtime = load_monitor_runtime(
            self.settings,
            expected_owner_uid=os.getuid(),
            pipeline_env_root=self.secrets_root,
        )

        self.assertEqual(runtime.command[0], str(self.virtualenv / "bin" / "python"))
        self.assertEqual(
            runtime.command[-4:],
            ("--monitor-only", "--allow-usb2", "--fps", "4"),
        )
        self.assertEqual(
            runtime.environment["JETSON_PIPELINE_SENSOR_BRIDGE_DIR"],
            str(self.settings.bridge_dir),
        )
        self.assertNotIn("JETSON_PIPELINE_RESULTS_DIR", runtime.environment)
        self.assertEqual(runtime.release, self.release)

    def test_runtime_loads_protected_pipeline_environment(self) -> None:
        secrets = self.secrets_root / "depthai-capture.env"
        secrets.write_text(
            "# NTRIP credentials\n"
            "NTRIP_USERNAME='field user'\n"
            'NTRIP_PASSWORD="field-password"\n',
            encoding="utf-8",
        )
        secrets.chmod(0o600)

        runtime = load_monitor_runtime(
            self.settings,
            expected_owner_uid=os.getuid(),
            pipeline_env_root=self.secrets_root,
        )

        self.assertEqual(runtime.environment["NTRIP_USERNAME"], "field user")
        self.assertEqual(runtime.environment["NTRIP_PASSWORD"], "field-password")
        self.assertEqual(runtime.pipeline_environment["NTRIP_USERNAME"], "field user")

    def test_runtime_reuses_preloaded_pipeline_environment(self) -> None:
        unavailable_secrets_root = self.root / "unavailable-secrets"

        runtime = load_monitor_runtime(
            self.settings,
            expected_owner_uid=os.getuid(),
            pipeline_env_root=unavailable_secrets_root,
            pipeline_environment={
                "NTRIP_USERNAME": "field-user",
                "NTRIP_PASSWORD": "field-password",
            },
        )

        self.assertEqual(runtime.environment["NTRIP_USERNAME"], "field-user")
        self.assertEqual(runtime.environment["NTRIP_PASSWORD"], "field-password")

    def test_runtime_rejects_public_pipeline_environment(self) -> None:
        secrets = self.secrets_root / "depthai-capture.env"
        secrets.write_text("NTRIP_PASSWORD=secret\n", encoding="utf-8")
        secrets.chmod(0o640)

        with self.assertRaisesRegex(ValueError, "secrets permissions are unsafe"):
            load_monitor_runtime(
                self.settings,
                expected_owner_uid=os.getuid(),
                pipeline_env_root=self.secrets_root,
            )

    def test_runtime_rejects_release_symlink_outside_registry(self) -> None:
        outside = self.root / "outside"
        outside.mkdir()
        (self.pipeline_root / "current").unlink()
        (self.pipeline_root / "current").symlink_to(outside)

        with self.assertRaisesRegex(ValueError, "leaves the release"):
            load_monitor_runtime(
                self.settings,
                expected_owner_uid=os.getuid(),
                pipeline_env_root=self.secrets_root,
            )

    def test_runtime_rejects_writable_or_wrong_owner_pipeline_metadata(self) -> None:
        manifest = self.pipeline_root / "pipeline.json"
        manifest.chmod(0o660)
        with self.assertRaisesRegex(ValueError, "permissions are unsafe"):
            load_monitor_runtime(
                self.settings,
                expected_owner_uid=os.getuid(),
            )

        manifest.chmod(0o640)
        with self.assertRaisesRegex(ValueError, "ownership or permissions are unsafe"):
            load_monitor_runtime(
                self.settings,
                expected_owner_uid=os.getuid() + 1,
                pipeline_env_root=self.secrets_root,
            )

    def test_runtime_rejects_writable_release_and_root_execution(self) -> None:
        self.release.chmod(0o770)
        with self.assertRaisesRegex(ValueError, "current release.*unsafe"):
            load_monitor_runtime(
                self.settings,
                expected_owner_uid=os.getuid(),
                pipeline_env_root=self.secrets_root,
            )

        self.release.chmod(0o750)
        manifest_path = self.pipeline_root / "pipeline.json"
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
        manifest["user"] = "root"
        manifest_path.write_text(json.dumps(manifest), encoding="utf-8")
        manifest_path.chmod(0o640)
        with self.assertRaisesRegex(ValueError, "must not run as root"):
            load_monitor_runtime(
                self.settings,
                expected_owner_uid=os.getuid(),
                pipeline_env_root=self.secrets_root,
            )

    def test_runtime_reloads_after_current_release_changes(self) -> None:
        second = self.pipeline_root / "releases" / "release-2"
        second.mkdir(mode=0o750)
        (second / "main.py").write_text("print('second')\n", encoding="utf-8")
        (second / "config.yaml").write_text("fps: 4\n", encoding="utf-8")
        (second / "main.py").chmod(0o640)
        (second / "config.yaml").chmod(0o640)
        manifest_path = self.pipeline_root / "pipeline.json"
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
        manifest["release"] = second.name
        manifest_path.write_text(json.dumps(manifest), encoding="utf-8")
        manifest_path.chmod(0o640)
        (self.pipeline_root / "current").unlink()
        (self.pipeline_root / "current").symlink_to(second)

        runtime = load_monitor_runtime(
            self.settings,
            expected_owner_uid=os.getuid(),
            pipeline_env_root=self.secrets_root,
        )

        self.assertEqual(runtime.release, second)
        self.assertIn(str(second / "main.py"), runtime.command)

    def test_stop_child_escalates_against_the_whole_process_group(self) -> None:
        child = Mock(pid=4242)
        child.wait.return_value = 0
        with patch(
            "jetson_control.sensor_monitor._process_group_exists",
            return_value=True,
        ), patch(
            "jetson_control.sensor_monitor._wait_for_process_group",
            side_effect=[False, False, True],
        ), patch("jetson_control.sensor_monitor.os.killpg") as killpg:
            stop_child(
                child,
                timeout_seconds=0,
                terminate_timeout_seconds=0,
                kill_timeout_seconds=0,
            )

        self.assertEqual(
            killpg.call_args_list,
            [
                unittest.mock.call(4242, signal.SIGINT),
                unittest.mock.call(4242, signal.SIGTERM),
                unittest.mock.call(4242, signal.SIGKILL),
            ],
        )

    def test_supervisor_retries_missing_pipeline_without_exiting(self) -> None:
        handlers = {}
        attempts = []

        def install_handler(signum, handler):
            handlers[signum] = handler

        def unavailable(_settings):
            attempts.append(True)
            raise FileNotFoundError("snapshot is being replaced")

        def stop_during_retry(_seconds):
            handlers[signal.SIGTERM](signal.SIGTERM, None)

        with patch(
            "jetson_control.sensor_monitor.signal.signal",
            side_effect=install_handler,
        ), patch(
            "jetson_control.sensor_monitor.time.sleep",
            side_effect=stop_during_retry,
        ):
            result = supervise(
                self.settings,
                pwd.getpwuid(os.getuid()).pw_name,
                retry_seconds=0.1,
                runtime_loader=unavailable,
            )

        self.assertEqual(result, 0)
        self.assertEqual(attempts, [True])


if __name__ == "__main__":
    unittest.main()
