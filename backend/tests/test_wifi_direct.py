import fcntl
import json
import subprocess
import tempfile
import threading
import time
import unittest
from pathlib import Path

from jetson_control.network import (
    WIFI_MODE_DIRECT,
    WIFI_MODE_LAN,
    WifiModeCoordinator,
    WifiProvisioner,
)
from jetson_control.wifi_direct import (
    WifiDirectController,
    WifiDirectError,
    WifiDirectRuntime,
    WifiDirectSettings,
    configured_ipv4_address,
    dhcp_lease_range,
    frequency_channel,
    managed_p2p_concurrency_capability,
    normalize_mac_address,
    parse_default_route_interfaces,
    parse_ipv4_address,
    parse_p2p_group_interfaces,
    parse_station_addresses,
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
        concurrency_supported=True,
        wiphy_available=True,
        managed_wifi_active=False,
        alternate_default=True,
        single_interface_group=False,
        group_address="192.168.49.1",
        fail_p2p_activation=False,
        fail_managed_disconnect=False,
        station_connected=False,
        p2p_find_failures=0,
        p2p_stop_find_failures=0,
        wifi_radio_enabled=True,
        device_autoconnect=False,
    ):
        self.calls = []
        self.group_created = False
        self.p2p_state = p2p_state
        self.concurrency_supported = concurrency_supported
        self.wiphy_available = wiphy_available
        self.managed_wifi_active = managed_wifi_active
        self.alternate_default = alternate_default
        self.single_interface_group = single_interface_group
        self.group_address = group_address
        self.fail_p2p_activation = fail_p2p_activation
        self.fail_managed_disconnect = fail_managed_disconnect
        self.station_connected = station_connected
        self.p2p_find_failures = p2p_find_failures
        self.p2p_stop_find_failures = p2p_stop_find_failures
        self.wifi_radio_enabled = wifi_radio_enabled
        self.device_autoconnect = device_autoconnect
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
            if self.wiphy_available:
                stdout = "Interface wlan0\n\twiphy 0\n\ttype managed\n"
        elif command[-2:] == ["station", "dump"]:
            if self.station_connected:
                stdout = "Station d2:04:b0:49:6e:b1 (on wlan0)\n"
        elif command == ["/usr/sbin/iw", "phy", "phy0", "info"]:
            if self.concurrency_supported:
                stdout = """valid interface combinations:
 * #{ managed } <= 1, #{ P2P-client, P2P-GO } <= 1,
   total <= 2, #channels <= 1
"""
            elif self.concurrency_supported is False:
                stdout = "interface combinations are not supported\n"
        elif "DEVICE,TYPE,STATE" in command:
            if self.wifi_radio_enabled:
                stdout = "wlan0:wifi:connected\np2p-dev-wlan0:wifi-p2p:{}\n".format(
                    self.p2p_state
                )
            else:
                stdout = (
                    "wlan0:wifi:unavailable\n"
                    "p2p-dev-wlan0:wifi-p2p:unavailable\n"
                )
        elif "GENERAL.AUTOCONNECT" in command:
            stdout = "yes\n" if self.device_autoconnect else "no\n"
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
            if self.fail_managed_disconnect:
                returncode = 4
                stderr = "Disconnect failed\n"
            else:
                self.managed_wifi_active = False
                stdout = "Connection successfully deactivated\n"
        elif command[:3] == ["/usr/bin/nmcli", "--wait", "45"]:
            self.managed_wifi_active = True
            stdout = "Connection successfully activated\n"
        elif command == [
            "/usr/bin/nmcli", "device", "set", "wlan0", "autoconnect", "no"
        ]:
            self.device_autoconnect = False
        elif command == [
            "/usr/bin/nmcli", "--wait", "15", "radio", "wifi", "off"
        ]:
            self.wifi_radio_enabled = False
        elif command == [
            "/usr/bin/nmcli", "--wait", "15", "radio", "wifi", "on"
        ]:
            self.wifi_radio_enabled = True
        elif "--ask" in command:
            self.managed_wifi_active = True
            stdout = "Connection successfully activated\n"
        elif command[0] == "/usr/sbin/wpa_cli":
            if not self.wifi_radio_enabled:
                returncode = 1
                stderr = "wpa_supplicant control interface unavailable\n"
            elif "p2p_stop_find" in command and self.p2p_stop_find_failures > 0:
                self.p2p_stop_find_failures -= 1
                stdout = "FAIL\n"
            elif "p2p_find" in command and self.p2p_find_failures > 0:
                self.p2p_find_failures -= 1
                stdout = "FAIL\n"
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

    def test_parses_associated_station_addresses(self):
        output = """Station d2:04:b0:49:6e:b1 (on wlan0)
\tinactive time:\t20 ms
Station AA:BB:CC:DD:EE:FF (on wlan0)
"""
        self.assertEqual(
            parse_station_addresses(output),
            ["D2:04:B0:49:6E:B1", "AA:BB:CC:DD:EE:FF"],
        )

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
        self.assertFalse(managed_p2p_concurrency_capability("Supported interface modes:"))

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

    def test_discovery_uses_unbounded_find_without_periodic_restart(self):
        runner = FakeRunner()
        now = [100.0]
        with tempfile.TemporaryDirectory() as temporary:
            controller = WifiDirectController(
                WifiDirectSettings(interface="wlan0", device_name="MMS-JETSON"),
                run=runner,
                status_path=Path(temporary) / "wifi-direct.json",
                sleep=lambda _seconds: None,
                monotonic=lambda: now[0],
            )

            self.assertEqual(controller.prepare(), "DISCOVERABLE")
            now[0] += 86_400.0
            self.assertTrue(controller.monitor())

            discovery_actions = []
            find_calls = []
            for call in runner.calls:
                if call[0] != "/usr/sbin/wpa_cli":
                    continue
                if "p2p_stop_find" in call:
                    discovery_actions.append("stop")
                elif "p2p_find" in call:
                    discovery_actions.append("find")
                    find_calls.append(call)
            self.assertEqual(discovery_actions, ["stop", "find"])
            self.assertEqual(len(find_calls), 1)
            self.assertEqual(find_calls[0][-1], "p2p_find")

    def test_discovery_recovers_a_stuck_driver_scan_once(self):
        runner = FakeRunner(p2p_find_failures=1)
        with tempfile.TemporaryDirectory() as temporary:
            controller = WifiDirectController(
                WifiDirectSettings(interface="wlan0", device_name="MMS-JETSON"),
                run=runner,
                status_path=Path(temporary) / "wifi-direct.json",
                sleep=lambda _seconds: None,
            )

            self.assertEqual(controller.prepare(), "DISCOVERABLE")
            recovery_actions = []
            for call in runner.calls:
                if call[0] != "/usr/sbin/wpa_cli":
                    continue
                for action in ("p2p_stop_find", "p2p_find", "abort_scan"):
                    if action in call:
                        recovery_actions.append(action)
                        break
            self.assertEqual(
                recovery_actions,
                ["p2p_stop_find", "p2p_find", "abort_scan", "p2p_stop_find", "p2p_find"],
            )

    def test_repeated_discovery_failures_pause_and_back_off_without_crashing(self):
        runner = FakeRunner(p2p_find_failures=6)
        now = [100.0]
        with tempfile.TemporaryDirectory() as temporary:
            status_path = Path(temporary) / "wifi-direct.json"
            controller = WifiDirectController(
                WifiDirectSettings(interface="wlan0", device_name="MMS-JETSON"),
                run=runner,
                status_path=status_path,
                sleep=lambda _seconds: None,
                monotonic=lambda: now[0],
            )
            controller.wifi_direct_resume_path.touch()

            self.assertEqual(controller.prepare(), "PAUSED")
            status = read_wifi_direct_status(status_path)
            self.assertEqual(status["state"], "PAUSED")
            self.assertIn("retrying automatically", status["message"])
            self.assertFalse(controller.wifi_direct_resume_path.exists())

            def count_find_calls():
                return sum(
                    call[0] == "/usr/sbin/wpa_cli" and "p2p_find" in call
                    for call in runner.calls
                )
            self.assertEqual(count_find_calls(), 2)

            now[0] += 4.9
            self.assertTrue(controller.monitor())
            self.assertEqual(count_find_calls(), 2)
            now[0] += 0.1
            self.assertTrue(controller.monitor())
            self.assertEqual(count_find_calls(), 4)

            now[0] += 9.9
            self.assertTrue(controller.monitor())
            self.assertEqual(count_find_calls(), 4)
            now[0] += 0.1
            self.assertTrue(controller.monitor())
            self.assertEqual(count_find_calls(), 6)
            self.assertEqual(read_wifi_direct_status(status_path)["state"], "PAUSED")

    def test_explicit_direct_recovers_repeated_driver_scan_failure_with_one_radio_cycle(self):
        runner = FakeRunner(
            p2p_find_failures=6,
            device_autoconnect=True,
        )
        now = [100.0]
        rebound = []
        with tempfile.TemporaryDirectory() as temporary:
            status_path = Path(temporary) / "wifi-direct.json"
            coordinator = WifiModeCoordinator(
                status_path.parent,
                sleep=lambda _seconds: None,
                monotonic=lambda: now[0],
            )
            request = coordinator.request_direct()
            controller = WifiDirectController(
                WifiDirectSettings(interface="wlan0", device_name="MMS-JETSON"),
                run=runner,
                status_path=status_path,
                sleep=lambda _seconds: None,
                monotonic=lambda: now[0],
                mode_coordinator=coordinator,
                radio_recovery_callback=lambda: rebound.append("rebound"),
            )

            self.assertEqual(controller.prepare(), "PAUSED")
            now[0] += 5.0
            self.assertTrue(controller.monitor())
            now[0] += 10.0
            self.assertTrue(controller.monitor())

            self.assertEqual(read_wifi_direct_status(status_path)["state"], "DISCOVERABLE")
            self.assertEqual(rebound, ["rebound"])
            self.assertFalse(runner.device_autoconnect)
            self.assertEqual(
                sum(call[-3:] == ["radio", "wifi", "off"] for call in runner.calls),
                1,
            )
            self.assertEqual(
                sum(call[-3:] == ["radio", "wifi", "on"] for call in runner.calls),
                1,
            )
            marker = json.loads(controller.radio_recovery_path.read_text(encoding="utf-8"))
            self.assertEqual(marker["requestId"], request.request_id)
            self.assertEqual(marker["version"], 1)

            off_index = next(
                index for index, call in enumerate(runner.calls)
                if call[-3:] == ["radio", "wifi", "off"]
            )
            on_index = next(
                index for index, call in enumerate(runner.calls)
                if call[-3:] == ["radio", "wifi", "on"]
            )
            final_find_index = max(
                index for index, call in enumerate(runner.calls)
                if call[0] == "/usr/sbin/wpa_cli" and "p2p_find" in call
            )
            self.assertLess(off_index, on_index)
            self.assertLess(on_index, final_find_index)

    def test_radio_recovery_is_attempted_only_once_for_same_direct_generation(self):
        runner = FakeRunner(p2p_find_failures=100)
        now = [100.0]
        with tempfile.TemporaryDirectory() as temporary:
            status_path = Path(temporary) / "wifi-direct.json"
            coordinator = WifiModeCoordinator(
                status_path.parent,
                sleep=lambda _seconds: None,
                monotonic=lambda: now[0],
            )
            coordinator.request_direct()
            controller = WifiDirectController(
                WifiDirectSettings(interface="wlan0", device_name="MMS-JETSON"),
                run=runner,
                status_path=status_path,
                sleep=lambda _seconds: None,
                monotonic=lambda: now[0],
                mode_coordinator=coordinator,
                radio_recovery_callback=lambda: None,
            )

            self.assertEqual(controller.prepare(), "PAUSED")
            now[0] += 5.0
            self.assertTrue(controller.monitor())
            now[0] += 10.0
            self.assertTrue(controller.monitor())
            now[0] += 40.0
            self.assertTrue(controller.monitor())
            now[0] += 60.0
            self.assertTrue(controller.monitor())

            self.assertEqual(read_wifi_direct_status(status_path)["state"], "PAUSED")
            self.assertEqual(
                sum(call[-3:] == ["radio", "wifi", "off"] for call in runner.calls),
                1,
            )
            self.assertEqual(
                sum(call[-3:] == ["radio", "wifi", "on"] for call in runner.calls),
                1,
            )

    def test_runtime_rebinds_go_negotiation_receiver_after_radio_cycle(self):
        class FakeBus:
            def __init__(self):
                self.added = []
                self.removed = []

            def add_signal_receiver(self, callback, **kwargs):
                self.added.append((callback, kwargs))

            def remove_signal_receiver(self, callback, **kwargs):
                self.removed.append((callback, kwargs))

        runner = FakeRunner()
        with tempfile.TemporaryDirectory() as temporary:
            controller = WifiDirectController(
                WifiDirectSettings(interface="wlan0", device_name="MMS-JETSON"),
                run=runner,
                status_path=Path(temporary) / "wifi-direct.json",
                sleep=lambda _seconds: None,
            )
            runtime = WifiDirectRuntime(controller)
            bus = FakeBus()
            runtime.bus = bus
            runtime.wpa_interface_path = "/fi/w1/wpa_supplicant1/Interfaces/1"
            runtime._find_wpa_interface_path = (
                lambda: "/fi/w1/wpa_supplicant1/Interfaces/2"
            )

            controller._radio_recovery_callback()

            self.assertEqual(len(bus.removed), 1)
            self.assertEqual(
                bus.removed[0][1]["path"],
                "/fi/w1/wpa_supplicant1/Interfaces/1",
            )
            self.assertEqual(len(bus.added), 1)
            self.assertEqual(
                bus.added[0][1]["path"],
                "/fi/w1/wpa_supplicant1/Interfaces/2",
            )
            self.assertEqual(
                runtime.wpa_interface_path,
                "/fi/w1/wpa_supplicant1/Interfaces/2",
            )

    def test_discovery_recovers_after_multiple_backed_off_failures(self):
        runner = FakeRunner(p2p_find_failures=4)
        now = [100.0]
        with tempfile.TemporaryDirectory() as temporary:
            status_path = Path(temporary) / "wifi-direct.json"
            controller = WifiDirectController(
                WifiDirectSettings(interface="wlan0", device_name="MMS-JETSON"),
                run=runner,
                status_path=status_path,
                sleep=lambda _seconds: None,
                monotonic=lambda: now[0],
            )

            self.assertEqual(controller.prepare(), "PAUSED")
            now[0] += 5.0
            self.assertTrue(controller.monitor())
            self.assertEqual(read_wifi_direct_status(status_path)["state"], "PAUSED")

            now[0] += 10.0
            self.assertTrue(controller.monitor())
            self.assertEqual(read_wifi_direct_status(status_path)["state"], "DISCOVERABLE")
            find_calls = sum(
                call[0] == "/usr/sbin/wpa_cli" and "p2p_find" in call
                for call in runner.calls
            )
            self.assertEqual(find_calls, 5)

            now[0] += 1.0
            self.assertTrue(controller.monitor())
            self.assertEqual(
                sum(
                    call[0] == "/usr/sbin/wpa_cli" and "p2p_find" in call
                    for call in runner.calls
                ),
                5,
            )

    def test_requested_discovery_resume_failure_is_published_as_paused(self):
        runner = FakeRunner()
        now = [100.0]
        with tempfile.TemporaryDirectory() as temporary:
            status_path = Path(temporary) / "wifi-direct.json"
            controller = WifiDirectController(
                WifiDirectSettings(interface="wlan0", device_name="MMS-JETSON"),
                run=runner,
                status_path=status_path,
                sleep=lambda _seconds: None,
                monotonic=lambda: now[0],
            )

            self.assertEqual(controller.prepare(), "DISCOVERABLE")
            runner.p2p_find_failures = 2
            controller.wifi_direct_resume_path.touch()
            self.assertTrue(controller.monitor())

            status = read_wifi_direct_status(status_path)
            self.assertEqual(status["state"], "PAUSED")
            self.assertIn("retrying automatically", status["message"])
            self.assertEqual(
                sum(
                    call[0] == "/usr/sbin/wpa_cli" and "p2p_find" in call
                    for call in runner.calls
                ),
                3,
            )

    def test_discovery_waits_for_wifi_provisioning_lock_then_resumes(self):
        runner = FakeRunner()
        with tempfile.TemporaryDirectory() as temporary:
            status_path = Path(temporary) / "wifi-direct.json"
            controller = WifiDirectController(
                WifiDirectSettings(interface="wlan0", device_name="MMS-JETSON"),
                run=runner,
                status_path=status_path,
                sleep=lambda _seconds: None,
            )
            radio_lock = controller.wifi_radio_lock_path.open("a+", encoding="utf-8")
            fcntl.flock(radio_lock.fileno(), fcntl.LOCK_EX)
            try:
                self.assertEqual(controller.prepare(), "PAUSED")
                self.assertFalse(
                    any("p2p_find" in call for call in runner.calls)
                )
                controller.wifi_direct_resume_path.touch()
                self.assertTrue(controller.monitor())
                self.assertEqual(read_wifi_direct_status(status_path)["state"], "PAUSED")
            finally:
                fcntl.flock(radio_lock.fileno(), fcntl.LOCK_UN)
                radio_lock.close()

            self.assertTrue(controller.monitor())
            self.assertEqual(read_wifi_direct_status(status_path)["state"], "DISCOVERABLE")
            self.assertTrue(any("p2p_find" in call for call in runner.calls))
            self.assertFalse(controller.wifi_direct_resume_path.exists())

    def test_prepare_pauses_discovery_while_managed_wifi_is_active(self):
        runner = FakeRunner(managed_wifi_active=True)
        with tempfile.TemporaryDirectory() as temporary:
            status_path = Path(temporary) / "wifi-direct.json"
            controller = WifiDirectController(
                WifiDirectSettings(interface="wlan0", device_name="MMS-JETSON"),
                run=runner,
                status_path=status_path,
                sleep=lambda _seconds: None,
            )

            self.assertEqual(controller.prepare(), "PAUSED")
            status = read_wifi_direct_status(status_path)
            self.assertEqual(status["state"], "PAUSED")
            self.assertIn("Managed Wi-Fi is active", status["message"])
            self.assertFalse(any("p2p_find" in call for call in runner.calls))
            stop_count = sum("p2p_stop_find" in call for call in runner.calls)
            self.assertEqual(stop_count, 1)

            self.assertTrue(controller.monitor())
            self.assertEqual(
                sum("p2p_stop_find" in call for call in runner.calls),
                stop_count,
            )

    def test_direct_mode_request_suspends_managed_wifi_and_starts_discovery(self):
        runner = FakeRunner(managed_wifi_active=True)
        managed_uuid = "11111111-2222-3333-4444-555555555555"
        with tempfile.TemporaryDirectory() as temporary:
            status_path = Path(temporary) / "wifi-direct.json"
            coordinator = WifiModeCoordinator(status_path.parent)
            request = coordinator.request(WIFI_MODE_DIRECT)
            controller = WifiDirectController(
                WifiDirectSettings(interface="wlan0", device_name="MMS-JETSON"),
                run=runner,
                status_path=status_path,
                sleep=lambda _seconds: None,
                mode_coordinator=coordinator,
            )

            self.assertEqual(controller.prepare(), "DISCOVERABLE")

            self.assertIn(
                [
                    "/usr/bin/nmcli", "--wait", "20", "device", "disconnect", "wlan0",
                ],
                runner.calls,
            )
            self.assertFalse(runner.managed_wifi_active)
            self.assertEqual(coordinator.suspended_profile(), managed_uuid)
            self.assertTrue(any("p2p_find" in call for call in runner.calls))
            self.assertEqual(coordinator.wait_for_ready(request, timeout=0.01)[0], True)

    def test_failed_managed_disconnect_never_starts_direct_discovery(self):
        runner = FakeRunner(
            managed_wifi_active=True,
            fail_managed_disconnect=True,
        )
        with tempfile.TemporaryDirectory() as temporary:
            status_path = Path(temporary) / "wifi-direct.json"
            coordinator = WifiModeCoordinator(status_path.parent)
            request = coordinator.request(WIFI_MODE_DIRECT)
            controller = WifiDirectController(
                WifiDirectSettings(interface="wlan0", device_name="MMS-JETSON"),
                run=runner,
                status_path=status_path,
                sleep=lambda _seconds: None,
                mode_coordinator=coordinator,
            )

            self.assertEqual(controller.prepare(), "ERROR")
            self.assertFalse(any("p2p_find" in call for call in runner.calls))
            self.assertFalse(coordinator.wait_for_ready(request, timeout=0.01)[0])

            self.assertTrue(controller.monitor())
            self.assertTrue(runner.managed_wifi_active)
            self.assertFalse(any("p2p_find" in call for call in runner.calls))

    def test_explicit_direct_mode_keeps_wifi_suspended_after_peer_leaves(self):
        runner = FakeRunner(managed_wifi_active=True, concurrency_supported=True)
        with tempfile.TemporaryDirectory() as temporary:
            status_path = Path(temporary) / "wifi-direct.json"
            coordinator = WifiModeCoordinator(status_path.parent)
            coordinator.request(WIFI_MODE_DIRECT)
            controller = WifiDirectController(
                WifiDirectSettings(interface="wlan0", device_name="MMS-JETSON"),
                run=runner,
                status_path=status_path,
                sleep=lambda _seconds: None,
                mode_coordinator=coordinator,
            )

            self.assertEqual(controller.prepare(), "DISCOVERABLE")
            self.assertEqual(
                controller.activate_peer_for_test("AA:BB:CC:DD:EE:FF"),
                "p2p-wlan0-0",
            )
            runner.group_created = False

            self.assertTrue(controller.monitor())

            self.assertFalse(runner.managed_wifi_active)
            self.assertFalse(
                any(
                    call[:6] == [
                        "/usr/bin/nmcli", "--wait", "45", "connection", "up", "uuid",
                    ]
                    for call in runner.calls
                )
            )
            self.assertEqual(read_wifi_direct_status(status_path)["state"], "DISCOVERABLE")

    def test_lan_handoff_acknowledges_after_daemon_owned_group_cleanup(self):
        runner = FakeRunner(
            managed_wifi_active=True,
            concurrency_supported=False,
            single_interface_group=True,
        )
        with tempfile.TemporaryDirectory() as temporary:
            status_path = Path(temporary) / "wifi-direct.json"
            coordinator = WifiModeCoordinator(status_path.parent)
            coordinator.request(WIFI_MODE_DIRECT)
            controller = WifiDirectController(
                WifiDirectSettings(interface="wlan0", device_name="MMS-JETSON"),
                run=runner,
                start_process=runner.start_process,
                status_path=status_path,
                sleep=lambda _seconds: None,
                mode_coordinator=coordinator,
            )
            self.assertEqual(controller.prepare(), "DISCOVERABLE")
            self.assertEqual(
                controller.activate_peer_for_test("AA:BB:CC:DD:EE:FF"),
                "wlan0",
            )
            dnsmasq = runner.dnsmasq_processes[0]

            lan_request = coordinator.request(WIFI_MODE_LAN)
            self.assertTrue(controller.monitor())

            self.assertTrue(coordinator.wait_for_ready(lan_request, timeout=0.01)[0])
            self.assertTrue(dnsmasq.terminated)
            self.assertFalse(runner.group_created)
            self.assertEqual(read_wifi_direct_status(status_path)["state"], "PAUSED")
            self.assertEqual(
                coordinator.suspended_profile(),
                "11111111-2222-3333-4444-555555555555",
            )

    def test_lan_handoff_is_not_acknowledged_when_discovery_will_not_stop(self):
        runner = FakeRunner(
            managed_wifi_active=False,
            p2p_stop_find_failures=1,
        )
        with tempfile.TemporaryDirectory() as temporary:
            status_path = Path(temporary) / "wifi-direct.json"
            coordinator = WifiModeCoordinator(status_path.parent)
            controller = WifiDirectController(
                WifiDirectSettings(interface="wlan0", device_name="MMS-JETSON"),
                run=runner,
                status_path=status_path,
                sleep=lambda _seconds: None,
                mode_coordinator=coordinator,
            )
            controller.management_interface = "p2p-dev-wlan0"
            lan_request = coordinator.request(WIFI_MODE_LAN)

            self.assertTrue(controller.monitor())

            ready, message = coordinator.wait_for_ready(lan_request, timeout=0.01)
            self.assertFalse(ready)
            self.assertIn("transition failed", message)
            self.assertEqual(read_wifi_direct_status(status_path)["state"], "ERROR")

    def test_lan_handoff_waits_for_active_p2p_radio_owner(self):
        runner = FakeRunner(managed_wifi_active=False)
        with tempfile.TemporaryDirectory() as temporary:
            status_path = Path(temporary) / "wifi-direct.json"
            coordinator = WifiModeCoordinator(status_path.parent)
            controller = WifiDirectController(
                WifiDirectSettings(interface="wlan0", device_name="MMS-JETSON"),
                run=runner,
                status_path=status_path,
                sleep=lambda _seconds: None,
                mode_coordinator=coordinator,
            )
            self.assertEqual(controller.prepare(), "DISCOVERABLE")
            activation_lock = controller._try_wifi_radio_lock()
            self.assertIsNotNone(activation_lock)
            lan_request = coordinator.request(WIFI_MODE_LAN)
            try:
                self.assertTrue(controller.monitor())
                self.assertFalse(
                    coordinator.wait_for_ready(lan_request, timeout=0.01)[0]
                )
            finally:
                controller._release_wifi_radio_lock(activation_lock)

            self.assertTrue(controller.monitor())
            self.assertTrue(coordinator.wait_for_ready(lan_request, timeout=0.01)[0])

    def test_lan_handoff_cancels_mid_activation_before_acknowledging(self):
        activation_started = threading.Event()
        release_activation = threading.Event()

        class BlockingActivationRunner(FakeRunner):
            def __call__(self, command, **kwargs):
                if command[:3] == ["/usr/bin/nmcli", "--wait", "50"]:
                    self.calls.append(command)
                    activation_started.set()
                    if not release_activation.wait(timeout=5):
                        raise subprocess.TimeoutExpired(command, 5)
                    return subprocess.CompletedProcess(
                        command,
                        4,
                        stdout="",
                        stderr="Activation cancelled\n",
                    )
                if command[0] == "/usr/sbin/wpa_cli" and "p2p_cancel" in command:
                    release_activation.set()
                return super().__call__(command, **kwargs)

        runner = BlockingActivationRunner(managed_wifi_active=False)
        with tempfile.TemporaryDirectory() as temporary:
            status_path = Path(temporary) / "wifi-direct.json"
            coordinator = WifiModeCoordinator(status_path.parent)
            coordinator.request(WIFI_MODE_DIRECT)
            controller = WifiDirectController(
                WifiDirectSettings(interface="wlan0", device_name="MMS-JETSON"),
                run=runner,
                status_path=status_path,
                sleep=lambda _seconds: None,
                mode_coordinator=coordinator,
            )
            self.assertEqual(controller.prepare(), "DISCOVERABLE")
            self.assertTrue(controller.request_connection("AA:BB:CC:DD:EE:FF"))
            self.assertTrue(activation_started.wait(timeout=1))

            lan_request = coordinator.request(WIFI_MODE_LAN)
            started = time.monotonic()
            self.assertTrue(controller.monitor())
            elapsed = time.monotonic() - started

            ready, message = coordinator.wait_for_ready(lan_request, timeout=0.1)
            self.assertTrue(ready, message)
            self.assertLess(elapsed, 5.0)
            self.assertFalse(controller._activation_thread.is_alive())
            self.assertTrue(
                any(
                    call[0] == "/usr/sbin/wpa_cli" and "p2p_cancel" in call
                    for call in runner.calls
                )
            )
            self.assertTrue(
                any(
                    call[:6] == [
                        "/usr/bin/nmcli", "--wait", "5", "connection", "down",
                        "jetson-control-p2p-aabbccddeeff",
                    ]
                    for call in runner.calls
                )
            )
            self.assertFalse(
                any(
                    call[:6] == [
                        "/usr/bin/nmcli", "--wait", "45", "connection", "up", "uuid",
                    ]
                    for call in runner.calls
                )
            )

    def test_direct_group_to_station_provisioning_handshake_end_to_end(self):
        runner = FakeRunner(
            managed_wifi_active=True,
            concurrency_supported=False,
            single_interface_group=True,
        )
        with tempfile.TemporaryDirectory() as temporary:
            status_path = Path(temporary) / "wifi-direct.json"
            coordinator = WifiModeCoordinator(status_path.parent)
            coordinator.request(WIFI_MODE_DIRECT)
            controller = WifiDirectController(
                WifiDirectSettings(interface="wlan0", device_name="MMS-JETSON"),
                run=runner,
                start_process=runner.start_process,
                status_path=status_path,
                sleep=lambda _seconds: None,
                mode_coordinator=coordinator,
            )
            self.assertEqual(controller.prepare(), "DISCOVERABLE")
            self.assertEqual(
                controller.activate_peer_for_test("AA:BB:CC:DD:EE:FF"),
                "wlan0",
            )
            dnsmasq = runner.dnsmasq_processes[0]
            provisioner = WifiProvisioner(
                "wlan0",
                run=runner,
                coordinate_wifi_direct=True,
                wpa_client_path=status_path.parent / "wpa-provision",
                mode_coordinator=coordinator,
                sleep=lambda _seconds: None,
            )

            provisioner.submit("Office", "password123")
            deadline = time.monotonic() + 2.0
            while time.monotonic() < deadline:
                request = coordinator.current_request()
                if request is not None and request.mode == WIFI_MODE_LAN:
                    break
                time.sleep(0.01)
            else:
                self.fail("provisioner did not request the LAN handoff")

            controller.monitor()
            while (
                time.monotonic() < deadline
                and provisioner.status()["state"] == "CONNECTING"
            ):
                time.sleep(0.01)
            controller.monitor()

            self.assertEqual(provisioner.status()["state"], "CONNECTED")
            self.assertEqual(coordinator.current_request().mode, WIFI_MODE_LAN)
            self.assertFalse(coordinator.provisioning_active())
            self.assertIsNone(coordinator.suspended_profile())
            self.assertTrue(runner.managed_wifi_active)
            self.assertFalse(runner.group_created)
            self.assertTrue(dnsmasq.terminated)
            self.assertEqual(read_wifi_direct_status(status_path)["state"], "PAUSED")

    def test_discovery_pauses_on_managed_wifi_and_resumes_after_disconnect(self):
        runner = FakeRunner(managed_wifi_active=False)
        with tempfile.TemporaryDirectory() as temporary:
            status_path = Path(temporary) / "wifi-direct.json"
            controller = WifiDirectController(
                WifiDirectSettings(interface="wlan0", device_name="MMS-JETSON"),
                run=runner,
                status_path=status_path,
                sleep=lambda _seconds: None,
            )

            self.assertEqual(controller.prepare(), "DISCOVERABLE")
            self.assertEqual(
                sum("p2p_find" in call for call in runner.calls),
                1,
            )

            runner.managed_wifi_active = True
            self.assertTrue(controller.monitor())
            self.assertEqual(read_wifi_direct_status(status_path)["state"], "PAUSED")
            self.assertEqual(
                sum("p2p_find" in call for call in runner.calls),
                1,
            )

            runner.managed_wifi_active = False
            self.assertTrue(controller.monitor())
            self.assertEqual(
                read_wifi_direct_status(status_path)["state"],
                "DISCOVERABLE",
            )
            self.assertEqual(
                sum("p2p_find" in call for call in runner.calls),
                2,
            )

    def test_managed_wifi_does_not_interrupt_p2p_connection_in_progress(self):
        runner = FakeRunner(managed_wifi_active=True)
        with tempfile.TemporaryDirectory() as temporary:
            status_path = Path(temporary) / "wifi-direct.json"
            controller = WifiDirectController(
                WifiDirectSettings(interface="wlan0", device_name="MMS-JETSON"),
                run=runner,
                status_path=status_path,
                sleep=lambda _seconds: None,
            )
            controller._publish("CONNECTING", "test")

            self.assertTrue(controller.monitor())

            self.assertEqual(read_wifi_direct_status(status_path)["state"], "CONNECTING")
            self.assertFalse(any("p2p_stop_find" in call for call in runner.calls))

    def test_missing_interface_combinations_use_manual_owner_mode(self):
        runner = FakeRunner(
            concurrency_supported=None,
            managed_wifi_active=False,
            alternate_default=False,
            single_interface_group=True,
        )
        with tempfile.TemporaryDirectory() as temporary:
            controller = WifiDirectController(
                WifiDirectSettings(interface="wlan0", device_name="MMS-JETSON"),
                run=runner,
                start_process=runner.start_process,
                status_path=Path(temporary) / "wifi-direct.json",
                sleep=lambda _seconds: None,
            )

            self.assertEqual(controller.prepare(), "DISCOVERABLE")
            self.assertEqual(
                controller.activate_peer_for_test("AA:BB:CC:DD:EE:FF"),
                "wlan0",
            )
            self.assertTrue(any("p2p_connect" in call for call in runner.calls))
            self.assertFalse(any("wifi-p2p.peer" in call for call in runner.calls))

    def test_single_interface_fallback_works_without_any_default_route(self):
        runner = FakeRunner(
            concurrency_supported=False,
            managed_wifi_active=False,
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
            self.assertEqual(
                controller.activate_peer_for_test("AA:BB:CC:DD:EE:FF"),
                "wlan0",
            )
            self.assertEqual(read_wifi_direct_status(status_path)["state"], "READY")
            self.assertFalse(
                any(
                    call[:7] == [
                        "/usr/sbin/ip", "-j", "-4", "route", "show", "default",
                    ]
                    for call in runner.calls
                )
            )

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

            self.assertEqual(controller.prepare(), "PAUSED")
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
            self.assertEqual(read_wifi_direct_status(status_path)["state"], "PAUSED")

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

            self.assertEqual(controller.prepare(), "PAUSED")
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

            self.assertEqual(controller.prepare(), "PAUSED")
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

            self.assertEqual(controller.prepare(), "PAUSED")
            self.assertEqual(controller.activate_peer_for_test("AA:BB:CC:DD:EE:FF"), "")

            self.assertTrue(runner.managed_wifi_active)
            self.assertTrue(
                any(call[:6] == [
                    "/usr/bin/nmcli", "--wait", "45", "connection", "up", "uuid",
                ] for call in runner.calls)
            )
            self.assertEqual(read_wifi_direct_status(status_path)["state"], "PAUSED")

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

            self.assertEqual(controller.prepare(), "PAUSED")
            self.assertEqual(
                controller.activate_peer_for_test("AA:BB:CC:DD:EE:FF"),
                "wlan0",
            )
            runner.dnsmasq_processes[0].returncode = 1

            controller.monitor()

            self.assertFalse(runner.group_created)
            self.assertTrue(runner.managed_wifi_active)
            self.assertTrue(any("p2p_group_remove" in call for call in runner.calls))
            self.assertEqual(read_wifi_direct_status(status_path)["state"], "PAUSED")

    def test_manual_owner_cleans_group_that_never_gets_an_associated_station(self):
        runner = FakeRunner(
            concurrency_supported=False,
            managed_wifi_active=False,
            alternate_default=True,
            single_interface_group=True,
        )
        now = [100.0]
        with tempfile.TemporaryDirectory() as temporary:
            status_path = Path(temporary) / "wifi-direct.json"
            controller = WifiDirectController(
                WifiDirectSettings(interface="wlan0", device_name="MMS-JETSON"),
                run=runner,
                start_process=runner.start_process,
                status_path=status_path,
                sleep=lambda _seconds: None,
                monotonic=lambda: now[0],
            )

            self.assertEqual(controller.prepare(), "DISCOVERABLE")
            self.assertEqual(
                controller.activate_peer_for_test("AA:BB:CC:DD:EE:FF"),
                "wlan0",
            )
            controller.monitor()
            self.assertTrue(runner.group_created)

            now[0] += 21.0
            controller.monitor()

            self.assertFalse(runner.group_created)
            self.assertTrue(any("p2p_group_remove" in call for call in runner.calls))
            self.assertEqual(read_wifi_direct_status(status_path)["state"], "DISCOVERABLE")

    def test_manual_owner_cleans_group_quickly_after_associated_station_leaves(self):
        runner = FakeRunner(
            concurrency_supported=False,
            managed_wifi_active=False,
            alternate_default=True,
            single_interface_group=True,
            station_connected=True,
        )
        now = [100.0]
        with tempfile.TemporaryDirectory() as temporary:
            status_path = Path(temporary) / "wifi-direct.json"
            controller = WifiDirectController(
                WifiDirectSettings(interface="wlan0", device_name="MMS-JETSON"),
                run=runner,
                start_process=runner.start_process,
                status_path=status_path,
                sleep=lambda _seconds: None,
                monotonic=lambda: now[0],
            )

            self.assertEqual(controller.prepare(), "DISCOVERABLE")
            self.assertEqual(
                controller.activate_peer_for_test("AA:BB:CC:DD:EE:FF"),
                "wlan0",
            )
            controller.monitor()
            runner.station_connected = False
            controller.monitor()
            now[0] += 9.0
            controller.monitor()

            self.assertFalse(runner.group_created)
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
