import unittest
from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parents[2]
SYSTEMD_ROOT = REPOSITORY_ROOT / "backend" / "systemd"
SHARED_RUNTIME_UNITS = (
    "jetson-control-api.service",
    "jetson-wifi-direct.service",
)


class SharedRuntimeDirectoryTest(unittest.TestCase):
    def test_runtime_owners_preserve_the_shared_directory_on_explicit_stop(self) -> None:
        for unit_name in SHARED_RUNTIME_UNITS:
            with self.subTest(unit=unit_name):
                unit = (SYSTEMD_ROOT / unit_name).read_text(encoding="utf-8")
                self.assertIn("RuntimeDirectory=jetson-control\n", unit)
                self.assertIn("RuntimeDirectoryPreserve=yes\n", unit)
                self.assertIn("/run/jetson-control", unit)
                self.assertNotIn("RuntimeDirectoryPreserve=restart", unit)

    def test_pipeline_template_loads_optional_per_pipeline_secrets(self) -> None:
        unit = (SYSTEMD_ROOT / "jetson-pipeline@.service").read_text(encoding="utf-8")

        self.assertIn(
            "EnvironmentFile=-/etc/jetson-control/pipelines/%i.env\n",
            unit,
        )

    def test_pipeline_template_can_request_sensor_handoff(self) -> None:
        unit = (SYSTEMD_ROOT / "jetson-pipeline@.service").read_text(encoding="utf-8")

        self.assertIn("ReadWritePaths=-/var/lib/jetson-sensors\n", unit)


if __name__ == "__main__":
    unittest.main()
