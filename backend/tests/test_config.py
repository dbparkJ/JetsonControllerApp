import json
import tempfile
import unittest
from pathlib import Path

from jetson_control.config import DeviceConfig


class DeviceConfigTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory()
        self.path = Path(self.temporary.name) / "device.json"
        self.value = {
            "device_id": "00000000-0000-0000-0000-000000000001",
            "device_name": "MMS-TEST",
            "bootstrap_secret_hex": bytes(range(32)).hex(),
            "controlled_services": [],
            "service_flags": {},
            "allow_power_commands": False,
            "wifi_interface": "wlan0",
        }

    def tearDown(self) -> None:
        self.temporary.cleanup()

    def write(self) -> None:
        self.path.write_text(json.dumps(self.value), encoding="utf-8")

    def test_loads_valid_configuration(self) -> None:
        self.write()
        config = DeviceConfig.load(self.path)
        self.assertEqual(config.device_name, "MMS-TEST")
        self.assertFalse(config.allow_power_commands)
        self.assertTrue(config.wifi_direct_enabled)
        self.assertEqual(config.wifi_direct_frequency, 2412)
        self.assertEqual(config.wifi_direct_address, "192.168.49.1/24")

    def test_rejects_string_boolean_and_service_list(self) -> None:
        self.value["allow_power_commands"] = "false"
        self.write()
        with self.assertRaises(ValueError):
            DeviceConfig.load(self.path)

        self.value["allow_power_commands"] = False
        self.value["controlled_services"] = "camera.service"
        self.write()
        with self.assertRaises(ValueError):
            DeviceConfig.load(self.path)

    def test_rejects_invalid_wifi_direct_configuration(self) -> None:
        self.value["wifi_direct_enabled"] = "true"
        self.write()
        with self.assertRaisesRegex(ValueError, "wifi_direct_enabled"):
            DeviceConfig.load(self.path)

        self.value["wifi_direct_enabled"] = True
        self.value["wifi_direct_address"] = "not-an-address"
        self.write()
        with self.assertRaisesRegex(ValueError, "wifi_direct_address"):
            DeviceConfig.load(self.path)

        self.value["wifi_direct_address"] = "192.168.49.1/24"
        self.value["wifi_direct_frequency"] = 2420
        self.write()
        with self.assertRaisesRegex(ValueError, "wifi_direct_frequency"):
            DeviceConfig.load(self.path)


if __name__ == "__main__":
    unittest.main()
