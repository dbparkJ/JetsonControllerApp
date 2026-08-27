import unittest
import subprocess
import time

from jetson_control.network import (
    NMCLI_PATH,
    SYSTEMCTL_PATH,
    WIFI_DIRECT_HANDOFF_GRACE_SECONDS,
    WIFI_DIRECT_SERVICE,
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

    def test_nmcli_receives_password_through_passwd_file_stdin(self) -> None:
        calls = []

        def run(command, **kwargs):
            calls.append((command, kwargs.get("input")))
            return subprocess.CompletedProcess(command, 0, "", "")

        provisioner = WifiProvisioner("wlan0", run=run)
        provisioner.submit("Office", "password123")
        deadline = time.monotonic() + 2
        while time.monotonic() < deadline and provisioner.status()["state"] == "CONNECTING":
            time.sleep(0.01)

        self.assertEqual(provisioner.status()["state"], "CONNECTED")
        self.assertFalse(
            any("password123" in argument for command, _input in calls for argument in command)
        )
        command, password_input = next(
            call for call in calls if "passwd-file" in call[0]
        )
        self.assertEqual(command[-2:], ["passwd-file", "/dev/stdin"])
        self.assertEqual(
            password_input,
            "802-11-wireless-security.psk:password123\n",
        )

    def test_open_wifi_does_not_create_a_password_pipe(self) -> None:
        calls = []

        def run(command, **kwargs):
            calls.append((command, kwargs.get("input")))
            return subprocess.CompletedProcess(command, 0, "", "")

        provisioner = WifiProvisioner("wlan0", run=run)
        provisioner.submit("Guest", "")
        self._wait_for_completion(provisioner)

        connect_command, password_input = next(
            call for call in calls if "connect" in call[0]
        )
        self.assertNotIn("--ask", connect_command)
        self.assertNotIn("passwd-file", connect_command)
        self.assertIsNone(password_input)
        self.assertEqual(provisioner.status()["state"], "CONNECTED")

    def test_direct_radio_is_released_before_wifi_and_restarted_afterward(self) -> None:
        calls = []
        delays = []

        def run(command, **kwargs):
            calls.append((command, kwargs.get("input")))
            return subprocess.CompletedProcess(command, 0, "", "")

        provisioner = WifiProvisioner(
            "wlan0",
            run=run,
            coordinate_wifi_direct=True,
            sleep=delays.append,
        )
        provisioner.submit("Office", "password123")
        self._wait_for_completion(provisioner)

        commands = [command for command, _input in calls]
        connect_index = next(
            index for index, command in enumerate(commands) if "passwd-file" in command
        )
        self.assertEqual(
            commands[0],
            [SYSTEMCTL_PATH, "stop", WIFI_DIRECT_SERVICE],
        )
        self.assertLess(0, connect_index)
        self.assertEqual(
            commands[-1],
            [SYSTEMCTL_PATH, "start", WIFI_DIRECT_SERVICE],
        )
        self.assertIn("id", commands[connect_index])
        self.assertEqual(
            calls[connect_index][1],
            "802-11-wireless-security.psk:password123\n",
        )
        self.assertEqual(delays, [WIFI_DIRECT_HANDOFF_GRACE_SECONDS])
        self.assertEqual(provisioner.status()["state"], "CONNECTED")

    def test_failed_direct_handoff_does_not_attempt_managed_wifi(self) -> None:
        calls = []

        def run(command, **kwargs):
            calls.append(command)
            returncode = 1 if command == [
                SYSTEMCTL_PATH, "stop", WIFI_DIRECT_SERVICE,
            ] else 0
            return subprocess.CompletedProcess(command, returncode, "", "")

        provisioner = WifiProvisioner(
            "wlan0",
            run=run,
            coordinate_wifi_direct=True,
            sleep=lambda _seconds: None,
        )
        provisioner.submit("Office", "password123")
        self._wait_for_completion(provisioner)

        self.assertFalse(any("passwd-file" in command for command in calls))
        self.assertEqual(
            calls[-1],
            [SYSTEMCTL_PATH, "start", WIFI_DIRECT_SERVICE],
        )
        self.assertEqual(provisioner.status()["state"], "FAILED")

    def test_failed_wifi_attempt_restores_direct_advertising(self) -> None:
        calls = []

        def run(command, **kwargs):
            calls.append(command)
            returncode = 4 if "passwd-file" in command else 0
            return subprocess.CompletedProcess(command, returncode, "", "")

        provisioner = WifiProvisioner(
            "wlan0",
            run=run,
            coordinate_wifi_direct=True,
            sleep=lambda _seconds: None,
        )
        provisioner.submit("Office", "password123")
        self._wait_for_completion(provisioner)

        self.assertEqual(provisioner.status()["state"], "FAILED")
        self.assertEqual(
            calls[-1],
            [SYSTEMCTL_PATH, "start", WIFI_DIRECT_SERVICE],
        )

    def _wait_for_completion(self, provisioner: WifiProvisioner) -> None:
        deadline = time.monotonic() + 2
        while time.monotonic() < deadline and provisioner.status()["state"] == "CONNECTING":
            time.sleep(0.01)
        self.assertNotEqual(provisioner.status()["state"], "CONNECTING")


if __name__ == "__main__":
    unittest.main()
