from pathlib import Path
import stat
import subprocess
import tempfile
import unittest


SCRIPT = (
    Path(__file__).resolve().parents[1]
    / "scripts"
    / "configure-bluez-advertising.sh"
)


class ConfigureBluezAdvertisingTest(unittest.TestCase):
    def run_script(self, config: Path) -> subprocess.CompletedProcess:
        return subprocess.run(
            [str(SCRIPT), "--config", str(config), "--no-restart"],
            check=True,
            capture_output=True,
            text=True,
        )

    def test_adds_controller_section_and_preserves_original_backup(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            config = Path(temporary) / "main.conf"
            original = "[General]\nAutoEnable=true\n"
            config.write_text(original, encoding="utf-8")
            config.chmod(0o640)

            result = self.run_script(config)

            self.assertIn("Configured", result.stdout)
            self.assertEqual(
                config.read_text(encoding="utf-8"),
                original
                + "\n[Controller]\n"
                + "LEMinAdvertisementInterval=160\n"
                + "LEMaxAdvertisementInterval=240\n",
            )
            self.assertEqual(stat.S_IMODE(config.stat().st_mode), 0o640)
            backup = Path(str(config) + ".jetson-control.bak")
            self.assertEqual(backup.read_text(encoding="utf-8"), original)

    def test_replaces_active_values_once_and_is_idempotent(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            config = Path(temporary) / "main.conf"
            config.write_text(
                "[Controller]\n"
                "# Keep this comment\n"
                "LEMinAdvertisementInterval=2048\n"
                "LEMinAdvertisementInterval = 4096\n"
                "LEMaxAdvertisementInterval=2048\n"
                "\n[Policy]\nAutoEnable=true\n",
                encoding="utf-8",
            )

            self.run_script(config)
            first = config.read_bytes()
            result = self.run_script(config)

            self.assertIn("already configured", result.stdout)
            self.assertEqual(config.read_bytes(), first)
            text = first.decode("utf-8")
            self.assertEqual(text.count("LEMinAdvertisementInterval=160"), 1)
            self.assertEqual(text.count("LEMaxAdvertisementInterval=240"), 1)
            self.assertIn("# Keep this comment", text)
            self.assertIn("[Policy]", text)


if __name__ == "__main__":
    unittest.main()
