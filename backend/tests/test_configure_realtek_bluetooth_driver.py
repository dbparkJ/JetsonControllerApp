import os
from pathlib import Path
import stat
import subprocess
import tempfile
import unittest


SCRIPT = (
    Path(__file__).resolve().parents[1]
    / "scripts"
    / "configure-realtek-bluetooth-driver.sh"
)


class ConfigureRealtekBluetoothDriverTest(unittest.TestCase):
    def prepare_fixture(self, root: Path, vendor: str = "1358", product: str = "c123") -> None:
        usb_device = root / "sys" / "bus" / "usb" / "devices" / "1-3"
        interface = root / "sys" / "bus" / "usb" / "devices" / "1-3:1.0"
        usb_device.mkdir(parents=True)
        interface.mkdir()
        (usb_device / "idVendor").write_text(vendor + "\n", encoding="ascii")
        (usb_device / "idProduct").write_text(product + "\n", encoding="ascii")
        (interface / "bInterfaceClass").write_text("e0\n", encoding="ascii")
        (interface / "bInterfaceSubClass").write_text("01\n", encoding="ascii")
        (interface / "bInterfaceProtocol").write_text("01\n", encoding="ascii")
        (interface / "bInterfaceNumber").write_text("00\n", encoding="ascii")
        (interface / "modalias").write_text(
            "usb:v1358pC123d0000dcE0dsc01dp01icE0isc01ip01in00\n",
            encoding="ascii",
        )

        firmware = root / "firmware"
        firmware.mkdir()
        (firmware / "rtl8822cu_fw").write_bytes(b"firmware")
        (firmware / "rtl8822cu_config").write_bytes(b"config")

        fake_bin = root / "bin"
        fake_bin.mkdir()
        modinfo = fake_bin / "modinfo"
        modinfo.write_text(
            "#!/usr/bin/env bash\n"
            "if [[ \"${1:-}\" == \"-F\" ]]; then\n"
            "  echo '5.10.216-tegra SMP preempt mod_unload modversions aarch64'\n"
            "fi\n"
            "exit 0\n",
            encoding="utf-8",
        )
        modinfo.chmod(0o755)
        modprobe = fake_bin / "modprobe"
        modprobe.write_text(
            "#!/usr/bin/env bash\n"
            "if [[ \"${1:-}\" == \"--resolve-alias\" ]]; then echo rtk_btusb; fi\n"
            "exit 0\n",
            encoding="utf-8",
        )
        modprobe.chmod(0o755)

    def run_script(self, root: Path, config: Path) -> subprocess.CompletedProcess:
        environment = os.environ.copy()
        environment["PATH"] = str(root / "bin") + os.pathsep + environment["PATH"]
        return subprocess.run(
            [
                str(SCRIPT),
                "--config",
                str(config),
                "--sysfs-root",
                str(root / "sys"),
                "--firmware-root",
                str(root / "firmware"),
                "--kernel-release",
                "5.10.216-tegra",
                "--no-rebind",
            ],
            check=True,
            capture_output=True,
            text=True,
            env=environment,
        )

    def test_writes_soft_dependency_and_is_idempotent(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            self.prepare_fixture(root)
            config = root / "modprobe.d" / "jetson-control-realtek-bluetooth.conf"

            first = self.run_script(root, config)
            first_bytes = config.read_bytes()
            second = self.run_script(root, config)

            self.assertIn("Configured rtk_btusb", first.stdout)
            self.assertIn("already configured", second.stdout)
            self.assertEqual(config.read_bytes(), first_bytes)
            self.assertIn(b"softdep btusb pre: rtk_btusb\n", first_bytes)
            self.assertEqual(stat.S_IMODE(config.stat().st_mode), 0o644)

    def test_nonmatching_adapter_does_not_create_configuration(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            self.prepare_fixture(root, vendor="1234", product="5678")
            config = root / "modprobe.d" / "jetson-control-realtek-bluetooth.conf"

            result = self.run_script(root, config)

            self.assertIn("not present", result.stdout)
            self.assertFalse(config.exists())


if __name__ == "__main__":
    unittest.main()
