import os
from pathlib import Path
import pwd
import shlex
import subprocess
import tempfile
from typing import List
import unittest


SCRIPT = (
    Path(__file__).resolve().parents[1]
    / "scripts"
    / "install-depthai-pipeline.sh"
)


class InstallDepthaiPipelineTest(unittest.TestCase):
    def setUp(self) -> None:
        self.account = pwd.getpwuid(os.getuid())

    def run_dry(self, *arguments: str, check: bool = True) -> subprocess.CompletedProcess:
        environment = os.environ.copy()
        environment["SUDO_USER"] = self.account.pw_name
        return subprocess.run(
            [str(SCRIPT), "--dry-run", *arguments],
            check=check,
            capture_output=True,
            text=True,
            env=environment,
        )

    @staticmethod
    def option_value(command: List[str], option: str) -> str:
        index = command.index(option)
        return command[index + 1]

    def test_default_command_uses_root_pipeline_contract_and_bounded_outputs(self) -> None:
        result = self.run_dry()
        command = shlex.split(result.stdout)
        repository = Path(self.account.pw_dir) / "26_camera_record"

        self.assertEqual(Path(command[0]).name, "register-pipeline.sh")
        self.assertEqual(self.option_value(command, "--repo"), str(repository))
        self.assertEqual(
            self.option_value(command, "--venv"),
            str(repository / ".venv"),
        )
        self.assertEqual(self.option_value(command, "--entry"), "main.py")
        self.assertEqual(self.option_value(command, "--config"), "config.yaml")
        self.assertEqual(
            self.option_value(command, "--working-dir"),
            str(repository),
        )
        self.assertEqual(
            command[command.index("--argument=--output-dir") :][:6],
            [
                "--argument=--output-dir",
                "--argument",
                "/data/collections",
                "--argument=--controller-bridge-dir",
                "--argument",
                "/var/lib/jetson-sensors",
            ],
        )
        self.assertIn("--no-autostart", command)
        self.assertNotIn("--autostart", command)
        self.assertNotIn("--start-now", command)

        installer = SCRIPT.read_text(encoding="utf-8")
        self.assertIn('/etc/jetson-sensor-monitor.json', installer)
        self.assertIn("jetson-sensor-monitor.service", installer)
        self.assertNotIn("REPOSITORY_ID", installer)

    def test_custom_paths_remain_arguments_and_start_now_is_forwarded(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            repository = root / "26_camera_record"
            virtualenv = root / "pipeline venv"
            output = root / "collected data"

            result = self.run_dry(
                "--repo",
                str(repository),
                "--venv",
                str(virtualenv),
                "--output-root",
                str(output),
                "--start-now",
            )
            command = shlex.split(result.stdout)

        self.assertEqual(self.option_value(command, "--repo"), str(repository))
        self.assertEqual(self.option_value(command, "--venv"), str(virtualenv))
        self.assertEqual(self.option_value(command, "--working-dir"), str(repository))
        output_argument = command.index("--argument=--output-dir")
        self.assertEqual(command[output_argument + 2], str(output))
        self.assertIn("--start-now", command)

    def test_relative_output_root_is_rejected_without_side_effects(self) -> None:
        result = self.run_dry("--output-root", "relative/output", check=False)

        self.assertEqual(result.returncode, 2)
        self.assertIn("must be an absolute path", result.stderr)


if __name__ == "__main__":
    unittest.main()
