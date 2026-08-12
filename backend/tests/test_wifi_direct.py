import json
import subprocess
import tempfile
import unittest
from pathlib import Path

from jetson_control.wifi_direct import (
    WifiDirectController,
    WifiDirectError,
    WifiDirectSettings,
    frequency_channel,
    normalize_mac_address,
    parse_ipv4_address,
    parse_p2p_group_interfaces,
    p2p_device_name,
    peer_address_from_path,
    read_wifi_direct_status,
)


class FakeRunner:
    def __init__(self, p2p_state="disconnected"):
        self.calls = []
        self.group_created = False
        self.p2p_state = p2p_state

    def __call__(self, command, **_kwargs):
        self.calls.append(command)
        stdout = ""
        if command[:2] == ["/usr/sbin/iw", "dev"]:
            if self.group_created:
                stdout = """phy#0
\tInterface p2p-wlan0-0
\t\ttype P2P-GO
\tInterface wlan0
\t\ttype managed
"""
        elif "DEVICE,TYPE,STATE" in command:
            stdout = "wlan0:wifi:connected\np2p-dev-wlan0:wifi-p2p:{}\n".format(
                self.p2p_state
            )
        elif "NAME,TYPE" in command:
            stdout = ""
        elif command[:4] == ["/usr/sbin/ip", "-j", "-4", "address"]:
            stdout = json.dumps(
                [{"addr_info": [{"family": "inet", "local": "192.168.49.1"}]}]
            )
        elif command[:3] == ["/usr/bin/nmcli", "--wait", "50"]:
            self.group_created = True
            stdout = "Connection successfully activated\n"
        elif command[0] == "/usr/sbin/wpa_cli":
            stdout = "PONG\n" if command[-1] == "ping" else "OK\n"
        return subprocess.CompletedProcess(command, 0, stdout=stdout, stderr="")


class WifiDirectTest(unittest.TestCase):
    def test_parses_only_group_owner_interfaces(self):
        output = """phy#0
\tInterface p2p-wlan0-3
\t\ttype P2P-GO
\tUnnamed/non-netdev interface
\t\ttype P2P-device
\tInterface wlan0
\t\ttype managed
"""
        self.assertEqual(parse_p2p_group_interfaces(output), ["p2p-wlan0-3"])

    def test_parses_ipv4_json(self):
        output = '[{"addr_info":[{"family":"inet6","local":"::1"},' \
            '{"family":"inet","local":"192.168.49.1"}]}]'
        self.assertEqual(parse_ipv4_address(output), "192.168.49.1")
        self.assertIsNone(parse_ipv4_address("not-json"))

    def test_frequency_channel_validation(self):
        self.assertEqual(frequency_channel(2412), (81, 1))
        self.assertEqual(frequency_channel(5180), (115, 36))
        with self.assertRaisesRegex(ValueError, "supported"):
            frequency_channel(2420)

    def test_p2p_device_name_respects_utf8_byte_limit(self):
        name = p2p_device_name("젯슨-현장-카메라-장비-01")
        self.assertLessEqual(len(name.encode("utf-8")), 32)
        self.assertTrue(name.startswith("젯슨"))

    def test_normalizes_peer_addresses_and_paths(self):
        self.assertEqual(normalize_mac_address("aabb.ccdd.eeff"), "AA:BB:CC:DD:EE:FF")
        self.assertEqual(
            peer_address_from_path("/fi/w1/wpa_supplicant1/Interfaces/1/Peers/aabbccddeeff"),
            "AA:BB:CC:DD:EE:FF",
        )
        self.assertIsNone(peer_address_from_path("/not-a-peer"))

    def test_controller_waits_then_activates_requested_peer_through_networkmanager(self):
        runner = FakeRunner()
        with tempfile.TemporaryDirectory() as temporary:
            status_path = Path(temporary) / "wifi-direct.json"
            controller = WifiDirectController(
                WifiDirectSettings(interface="wlan0", device_name="MMS-JETSON"),
                run=runner,
                status_path=status_path,
                sleep=lambda _seconds: None,
            )

            self.assertEqual(controller.prepare(), "DISCOVERABLE")
            waiting = json.loads(status_path.read_text(encoding="utf-8"))
            self.assertEqual(waiting["state"], "DISCOVERABLE")
            self.assertEqual(waiting["managementInterface"], "p2p-dev-wlan0")

            self.assertEqual(
                controller.activate_peer_for_test("AA:BB:CC:DD:EE:FF"),
                "p2p-wlan0-0",
            )
            status = json.loads(status_path.read_text(encoding="utf-8"))
            self.assertEqual(status["state"], "READY")
            self.assertEqual(status["address"], "192.168.49.1")
            add_call = next(call for call in runner.calls if "add" in call)
            self.assertIn("wifi-p2p.peer", add_call)
            self.assertIn("AA:BB:CC:DD:EE:FF", add_call)
            self.assertIn("shared", add_call)
            self.assertTrue(
                all(
                    ["-s", str(status_path.parent / "wpa-cli")] == call[3:5]
                    for call in runner.calls
                    if call[0] == "/usr/sbin/wpa_cli"
                )
            )

            controller.stop()
            self.assertEqual(read_wifi_direct_status(status_path)["state"], "STOPPED")

    def test_controller_rejects_stale_networkmanager_placeholder(self):
        runner = FakeRunner(p2p_state="unavailable")
        with tempfile.TemporaryDirectory() as temporary:
            controller = WifiDirectController(
                WifiDirectSettings(interface="wlan0", device_name="MMS-JETSON"),
                run=runner,
                status_path=Path(temporary) / "wifi-direct.json",
                sleep=lambda _seconds: None,
            )

            with self.assertRaisesRegex(WifiDirectError, "stale placeholder"):
                controller.prepare()

    def test_missing_status_is_reported_as_unavailable(self):
        with tempfile.TemporaryDirectory() as temporary:
            status = read_wifi_direct_status(Path(temporary) / "missing.json")
        self.assertFalse(status["enabled"])
        self.assertEqual(status["state"], "UNAVAILABLE")


if __name__ == "__main__":
    unittest.main()
