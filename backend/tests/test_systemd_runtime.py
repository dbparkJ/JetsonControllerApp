import unittest
from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parents[2]
SYSTEMD_ROOT = REPOSITORY_ROOT / "backend" / "systemd"
SCRIPT_ROOT = REPOSITORY_ROOT / "backend" / "scripts"
CONTROLLER_UNITS = (
    "jetson-control.service",
    "jetson-control-api.service",
    "jetson-wifi-direct.service",
)


class SharedRuntimeDirectoryTest(unittest.TestCase):
    def test_all_controller_units_preserve_the_shared_runtime_directory(self) -> None:
        for unit_name in CONTROLLER_UNITS:
            with self.subTest(unit=unit_name):
                unit = (SYSTEMD_ROOT / unit_name).read_text(encoding="utf-8")
                self.assertIn("RuntimeDirectory=jetson-control\n", unit)
                self.assertIn("RuntimeDirectoryPreserve=yes\n", unit)
                self.assertIn("/run/jetson-control", unit)
                self.assertNotIn("RuntimeDirectoryPreserve=restart", unit)

    def test_installers_stage_and_backup_every_controller_unit(self) -> None:
        for script_name in ("install.sh", "deploy-wifi-hotfix.sh"):
            with self.subTest(script=script_name):
                script = (SCRIPT_ROOT / script_name).read_text(encoding="utf-8")
                self.assertIn('systemd_unit_root="/etc/systemd/system"', script)
                self.assertIn('cp -a -- "${unit_target}"', script)
                for unit_name in CONTROLLER_UNITS:
                    self.assertIn(f'"{unit_name}"', script)


if __name__ == "__main__":
    unittest.main()
