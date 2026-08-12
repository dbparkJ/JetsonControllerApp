import unittest
import subprocess
import time

from jetson_control.network import (
    WifiProvisioner,
    decode_wifi_payload,
    validate_wifi_credentials,
)


class WifiPayloadTest(unittest.TestCase):
    def test_decodes_app_wire_format(self) -> None:
        ssid = "Office WiFi".encode("utf-8")
        password = "password123".encode("utf-8")
        payload = bytes((1, 1, len(ssid), len(password))) + ssid + password
        self.assertEqual(
            decode_wifi_payload(payload),
            ("Office WiFi", "password123", True),
        )

    def test_rejects_short_password_and_length_mismatch(self) -> None:
        with self.assertRaises(ValueError):
            validate_wifi_credentials("Office", "short")
        with self.assertRaises(ValueError):
            decode_wifi_payload(bytes((1, 0, 4, 0)) + b"abc")

    def test_preserves_significant_ssid_whitespace(self) -> None:
        self.assertEqual(
            validate_wifi_credentials(" Studio ", "password123"),
            (" Studio ", "password123"),
        )

    def test_nmcli_receives_password_on_stdin_not_process_arguments(self) -> None:
        captured = {}

        def run(command, **kwargs):
            captured["command"] = command
            captured["input"] = kwargs.get("input")
            return subprocess.CompletedProcess(command, 0, "", "")

        provisioner = WifiProvisioner("wlan0", run=run)
        provisioner.submit("Office", "password123")
        deadline = time.monotonic() + 2
        while time.monotonic() < deadline and provisioner.status()["state"] == "CONNECTING":
            time.sleep(0.01)

        self.assertEqual(provisioner.status()["state"], "CONNECTED")
        self.assertNotIn("password123", captured["command"])
        self.assertEqual(captured["input"], "password123\n")


if __name__ == "__main__":
    unittest.main()
