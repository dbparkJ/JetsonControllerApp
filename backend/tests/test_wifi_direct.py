import json
import subprocess
import tempfile
import unittest
from pathlib import Path

from jetson_control.wifi_direct import (
    WifiDirectController,
    WifiDirectError,
    WifiDirectSettings,
    configured_ipv4_address,
    dhcp_lease_range,
    frequency_channel,
    managed_p2p_concurrency_capability,
    normalize_mac_address,
    parse_default_route_interfaces,
    parse_ipv4_address,
    parse_p2p_group_interfaces,
    parse_wiphy_name,
    p2p_device_name,
    peer_address_from_path,
    read_wifi_direct_status,
)


class FakeProcess:
    def __init__(self, command):
        self.command = command
        self.returncode = None
        self.terminated = False
        self.killed = False

    def poll(self):
        return self.returncode

    def terminate(self):
        self.terminated = True
        self.returncode = 0

    def kill(self):
        self.killed = True
        self.returncode = -9

    def wait(self, timeout=None):
        del timeout
        return self.returncode


class FakeRunner:
    def __init__(
        self,
        p2p_state="disconnected",
        concurrency_supported=None,
        managed_wifi_active=False,
        alternate_default=True,
        single_interface_group=False,
        group_address="192.168.49.1",
        fail_p2p_activation=False,
        fail_first_p2p_find=False,
    ):
        self.calls = []
        self.group_created = False
        self.p2p_state = p2p_state
        self.concurrency_supported = concurrency_supported
        self.managed_wifi_active = managed_wifi_active
        self.alternate_default = alternate_default
        self.single_interface_group = single_interface_group
        self.group_address = group_address
        self.fail_p2p_activation = fail_p2p_activation
        self.fail_first_p2p_find = fail_first_p2p_find
        self.p2p_find_attempts = 0
        self.dnsmasq_processes = []

    def start_process(self, command, **_kwargs):
        process = FakeProcess(command)
        self.dnsmasq_processes.append(process)
        return process

    def __call__(self, command, **_kwargs):
        self.calls.append(command)
        stdout = ""
        stderr = ""
        returncode = 0
        if command == ["/usr/sbin/iw", "dev"]:
            if self.group_created:
                group_interface = "wlan0" if self.single_interface_group else "p2p-wlan0-0"
                stdout = """phy#0
\tInterface {}
\t\ttype P2P-GO
""".format(group_interface)
                if not self.single_interface_group:
                    stdout += "\tInterface wlan0\n\t\ttype managed\n"
        elif command == ["/usr/sbin/iw", "dev", "wlan0", "info"]:
            if self.concurrency_supported is not None:
                stdout = "Interface wlan0\n\twiphy 0\n\ttype managed\n"
        elif command == ["/usr/sbin/iw", "phy", "phy0", "info"]:
            if self.concurrency_supported:
                stdout = """valid interface combinations:
 * #{ managed } <= 1, #{ P2P-client, P2P-GO } <= 1,
   total <= 2, #channels <= 1
"""
            elif self.concurrency_supported is False:
                stdout = "interface combinations are not supported\n"
        elif "DEVICE,TYPE,STATE" in command:
            stdout = "wlan0:wifi:connected\np2p-dev-wlan0:wifi-p2p:{}\n".format(
                self.p2p_state
            )
        elif "UUID,TYPE,DEVICE" in command:
            if self.managed_wifi_active:
                stdout = "11111111-2222-3333-4444-555555555555:802-11-wireless:wlan0\n"
        elif "NAME,TYPE" in command:
            stdout = ""
        elif command[:7] == [
            "/usr/sbin/ip", "-j", "-4", "route", "show", "default"
        ]:
            routes = [{"dst": "default", "dev": "wlan0"}]
            if self.alternate_default:
                routes.insert(0, {"dst": "default", "dev": "eth0"})
            stdout = json.dumps(routes)
        elif command[:4] == ["/usr/sbin/ip", "-j", "-4", "address"]:
            stdout = json.dumps(
                [{"addr_info": [{
                    "family": "inet",
                    "local": self.group_address,
                    "prefixlen": 24,
                }]}]
            )
        elif command[:3] == ["/usr/bin/nmcli", "--wait", "50"]:
            if self.fail_p2p_activation:
                returncode = 4
                stderr = "Activation failed\n"
            else:
                self.group_created = True
                stdout = "Connection successfully activated\n"
        elif command[:3] == ["/usr/bin/nmcli", "--wait", "20"]:
            self.managed_wifi_active = False
            stdout = "Connection successfully deactivated\n"
        elif command[:3] == ["/usr/bin/nmcli", "--wait", "45"]:
            self.managed_wifi_active = True
            stdout = "Connection successfully activated\n"
        elif command[0] == "/usr/sbin/wpa_cli":
            if "p2p_find" in command:
                self.p2p_find_attempts += 1
                if self.fail_first_p2p_find and self.p2p_find_attempts == 1:
                    stdout = "FAIL-BUSY\n"
                else:
                    stdout = "OK\n"
            elif "p2p_connect" in command:
                if self.fail_p2p_activation:
                    stdout = "FAIL\n"
                else:
                    self.group_created = True
                    stdout = "OK\n"
            elif "p2p_group_remove" in command:
                self.group_created = False
                stdout = "OK\n"
            else:
                stdout = "PONG\n" if command[-1] == "ping" else "OK\n"
        return subprocess.CompletedProcess(command, returncode, stdout=stdout, stderr=stderr)


class WifiDirectTest(unittest.TestCase):
    def test_discovery_refresh_stops_active_find_and_recovers_busy_scan(self):
        runner = FakeRunner(fail_first_p2p_find=True)
        with tempfile.TemporaryDirectory() as temporary:
            controller = WifiDirectController(
                WifiDirectSettings(interface="wlan0", device_name="MMS-JETSON"),
                run=runner,
                start_process=runner.start_process,
                status_path=Path(temporary) / "wifi-direct.json",
                sleep=lambda _seconds: None,
            )

            self.assertTrue(controller.refresh_discovery())

        commands = [
            call[7:]
            for call in runner.calls
            if call and call[0] == "/usr/sbin/wpa_cli"
        ]
        self.assertEqual(
            commands,
            [
                ["p2p_stop_find"],
                ["p2p_find", "600"],
                ["abort_scan"],
                ["p2p_stop_find"],
                ["p2p_find", "600"],
            ],
        )

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

    def test_configured_owner_address_requires_exact_ip_and_prefix(self):
        expected = '[{"addr_info":[{"family":"inet","local":"192.168.49.1",' \
            '"prefixlen":24}]}]'
        stale_lan = '[{"addr_info":[{"family":"inet","local":"192.168.0.10",' \
            '"prefixlen":24}]}]'
        wrong_prefix = '[{"addr_info":[{"family":"inet","local":"192.168.49.1",' \
            '"prefixlen":16}]}]'
        self.assertEqual(configured_ipv4_address(expected, "192.168.49.1/24"), "192.168.49.1")
        self.assertIsNone(configured_ipv4_address(stale_lan, "192.168.49.1/24"))
        self.assertIsNone(configured_ipv4_address(wrong_prefix, "192.168.49.1/24"))

    def test_parses_default_route_interfaces(self):
        output = '[{"dst":"default","dev":"eth0"},{"dst":"default","dev":"wlan0"}]'
        self.assertEqual(parse_default_route_interfaces(output), ["eth0", "wlan0"])
        self.assertEqual(parse_default_route_interfaces("not-json"), [])

    def test_detects_managed_p2p_concurrency_capability(self):
        supported = """valid interface combinations:
 * #{ managed } <= 1, #{ P2P-client, P2P-GO } <= 1,
   total <= 2, #channels <= 1
"""
        mutually_exclusive = """valid interface combinations:
 * #{ managed, P2P-GO } <= 1, total <= 2, #channels <= 1
"""
        self.assertEqual(parse_wiphy_name("Interface wlan0\n\twiphy 3\n"), "phy3")
        self.assertTrue(managed_p2p_concurrency_capability(supported))
        self.assertFalse(managed_p2p_concurrency_capability(mutually_exclusive))
        self.assertFalse(
            managed_p2p_concurrency_capability("interface combinations are not supported")
        )
        self.assertIsNone(managed_p2p_concurrency_capability("Supported interface modes:"))

    def test_derives_dhcp_range_without_leasing_owner_address(self):
        self.assertEqual(
            dhcp_lease_range("192.168.49.1/24"),
            ("192.168.49.2", "192.168.49.254", "255.255.255.0"),
        )
        self.assertEqual(
            dhcp_lease_range("10.0.0.2/30"),
            ("10.0.0.1", "10.0.0.1", "255.255.255.252"),
        )
        with self.assertRaisesRegex(ValueError, "subnet"):
            dhcp_lease_range("192.168.49.1/31")

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
                start_process=runner.start_process,
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

    def test_single_interface_fallback_suspends_and_restores_managed_wifi(self):
        runner = FakeRunner(
            concurrency_supported=False,
            managed_wifi_active=True,
            alternate_default=True,
            single_interface_group=True,
        )
        managed_uuid = "11111111-2222-3333-4444-555555555555"
        with tempfile.TemporaryDirectory() as temporary:
            status_path = Path(temporary) / "wifi-direct.json"
            controller = WifiDirectController(
                WifiDirectSettings(interface="wlan0", device_name="MMS-JETSON"),
                run=runner,
                start_process=runner.start_process,
                status_path=status_path,
                sleep=lambda _seconds: None,
            )

            self.assertEqual(controller.prepare(), "DISCOVERABLE")
            self.assertEqual(
                controller.activate_peer_for_test("AA:BB:CC:DD:EE:FF"),
                "wlan0",
            )
            down_call = [
                "/usr/bin/nmcli", "--wait", "20", "device", "disconnect", "wlan0",
            ]
            self.assertIn(down_call, runner.calls)
            self.assertFalse(runner.managed_wifi_active)
            ready = read_wifi_direct_status(status_path)
            self.assertEqual(ready["state"], "READY")
            self.assertEqual(ready["ownerMode"], "manual")
            self.assertTrue(ready["dhcpActive"])
            p2p_connect = next(call for call in runner.calls if "p2p_connect" in call)
            self.assertIn("go_intent=15", p2p_connect)
            self.assertIn("freq=2412", p2p_connect)
            self.assertIn(
                [
                    "/usr/sbin/ip", "-4", "address", "flush", "dev", "wlan0",
                    "scope", "global",
                ],
                runner.calls,
            )
            self.assertIn(
                [
                    "/usr/sbin/ip", "-4", "address", "add", "192.168.49.1/24",
                    "dev", "wlan0",
                ],
                runner.calls,
            )
            self.assertTrue(runner.dnsmasq_processes)
            dnsmasq = runner.dnsmasq_processes[0]
            self.assertIn("--port=0", dnsmasq.command)
            self.assertIn("--conf-file=", dnsmasq.command)
            self.assertIn("--interface=wlan0", dnsmasq.command)
            self.assertIn("--bind-interfaces", dnsmasq.command)
            self.assertIn(
                "--dhcp-range=192.168.49.2,192.168.49.254,255.255.255.0,1h",
                dnsmasq.command,
            )

            runner.group_created = False
            controller.monitor()

            up_call = [
                "/usr/bin/nmcli", "--wait", "45", "connection", "up", "uuid", managed_uuid,
                "ifname", "wlan0",
            ]
            self.assertIn(up_call, runner.calls)
            self.assertTrue(runner.managed_wifi_active)
            self.assertTrue(dnsmasq.terminated)
            self.assertEqual(read_wifi_direct_status(status_path)["state"], "DISCOVERABLE")

    def test_supported_concurrency_preserves_managed_wifi(self):
        runner = FakeRunner(
            concurrency_supported=True,
            managed_wifi_active=True,
            single_interface_group=False,
        )
        with tempfile.TemporaryDirectory() as temporary:
            controller = WifiDirectController(
                WifiDirectSettings(interface="wlan0", device_name="MMS-JETSON"),
                run=runner,
                status_path=Path(temporary) / "wifi-direct.json",
                sleep=lambda _seconds: None,
            )

            self.assertEqual(controller.prepare(), "DISCOVERABLE")
            self.assertEqual(
                controller.activate_peer_for_test("AA:BB:CC:DD:EE:FF"),
                "p2p-wlan0-0",
            )

            self.assertTrue(runner.managed_wifi_active)
            self.assertFalse(
                any(call[3:5] == ["device", "disconnect"] for call in runner.calls)
            )

    def test_single_interface_fallback_never_drops_only_default_route(self):
        runner = FakeRunner(
            concurrency_supported=False,
            managed_wifi_active=True,
            alternate_default=False,
            single_interface_group=True,
        )
        with tempfile.TemporaryDirectory() as temporary:
            status_path = Path(temporary) / "wifi-direct.json"
            controller = WifiDirectController(
                WifiDirectSettings(interface="wlan0", device_name="MMS-JETSON"),
                run=runner,
                start_process=runner.start_process,
                status_path=status_path,
                sleep=lambda _seconds: None,
            )

            self.assertEqual(controller.prepare(), "DISCOVERABLE")
            self.assertEqual(controller.activate_peer_for_test("AA:BB:CC:DD:EE:FF"), "")
            self.assertTrue(runner.managed_wifi_active)
            self.assertFalse(
                any(call[:6] == [
                    "/usr/bin/nmcli", "--wait", "20", "device", "disconnect", "wlan0",
                ] for call in runner.calls)
            )
            self.assertFalse(any("wifi-p2p.peer" in call for call in runner.calls))

    def test_single_interface_fallback_restores_wifi_after_activation_failure(self):
        runner = FakeRunner(
            concurrency_supported=False,
            managed_wifi_active=True,
            alternate_default=True,
            single_interface_group=True,
            fail_p2p_activation=True,
        )
        with tempfile.TemporaryDirectory() as temporary:
            status_path = Path(temporary) / "wifi-direct.json"
            controller = WifiDirectController(
                WifiDirectSettings(interface="wlan0", device_name="MMS-JETSON"),
                run=runner,
                start_process=runner.start_process,
                status_path=status_path,
                sleep=lambda _seconds: None,
            )

            self.assertEqual(controller.prepare(), "DISCOVERABLE")
            self.assertEqual(controller.activate_peer_for_test("AA:BB:CC:DD:EE:FF"), "")

            self.assertTrue(runner.managed_wifi_active)
            self.assertTrue(
                any(call[:6] == [
                    "/usr/bin/nmcli", "--wait", "45", "connection", "up", "uuid",
                ] for call in runner.calls)
            )
            self.assertEqual(read_wifi_direct_status(status_path)["state"], "DISCOVERABLE")

    def test_manual_owner_cleans_group_when_dhcp_child_exits(self):
        runner = FakeRunner(
            concurrency_supported=False,
            managed_wifi_active=True,
            alternate_default=True,
            single_interface_group=True,
        )
        with tempfile.TemporaryDirectory() as temporary:
            status_path = Path(temporary) / "wifi-direct.json"
            controller = WifiDirectController(
                WifiDirectSettings(interface="wlan0", device_name="MMS-JETSON"),
                run=runner,
                start_process=runner.start_process,
                status_path=status_path,
                sleep=lambda _seconds: None,
            )

            self.assertEqual(controller.prepare(), "DISCOVERABLE")
            self.assertEqual(
                controller.activate_peer_for_test("AA:BB:CC:DD:EE:FF"),
                "wlan0",
            )
            runner.dnsmasq_processes[0].returncode = 1

            controller.monitor()

            self.assertFalse(runner.group_created)
            self.assertTrue(runner.managed_wifi_active)
            self.assertTrue(any("p2p_group_remove" in call for call in runner.calls))
            self.assertEqual(read_wifi_direct_status(status_path)["state"], "DISCOVERABLE")

    def test_stale_lan_address_is_not_published_as_direct_ready(self):
        runner = FakeRunner(
            single_interface_group=True,
            group_address="192.168.0.25",
        )
        runner.group_created = True
        with tempfile.TemporaryDirectory() as temporary:
            status_path = Path(temporary) / "wifi-direct.json"
            controller = WifiDirectController(
                WifiDirectSettings(interface="wlan0", device_name="MMS-JETSON"),
                run=runner,
                status_path=status_path,
                sleep=lambda _seconds: None,
            )
            controller._publish("DISCOVERABLE", "test")

            controller.monitor()

            self.assertEqual(read_wifi_direct_status(status_path)["state"], "DISCOVERABLE")

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
