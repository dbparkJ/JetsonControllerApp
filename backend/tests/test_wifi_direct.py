import json
import subprocess
import tempfile
import threading
import unittest
from pathlib import Path
from unittest.mock import Mock, patch

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
    parse_ipv4_address,
    parse_iw_frequency,
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


class FakeClock:
    def __init__(self):
        self.wall = 100.0  # RTC near 1970 before the phone supplies time.
        self.mono = 1000.0

    def advance(self, seconds, drift=0.0):
        self.mono += seconds
        self.wall += seconds + drift


class FakeRunner:
    def __init__(
        self,
        p2p_state="disconnected",
        concurrency_supported=None,
        managed_wifi_active=False,
        single_interface_group=False,
        group_address="192.168.49.1",
        fail_p2p_activation=False,
        fail_first_p2p_listen=False,
        fail_all_p2p_listen=False,
        station_connected=True,
        wifi_driver="rtw_8822ce",
    ):
        self.calls = []
        self.group_created = False
        self.active_p2p_profile = None
        self.external_groups = []
        self.p2p_state = p2p_state
        self.concurrency_supported = concurrency_supported
        self.managed_wifi_active = managed_wifi_active
        self.single_interface_group = single_interface_group
        self.group_address = group_address
        self.fail_p2p_activation = fail_p2p_activation
        self.fail_first_p2p_listen = fail_first_p2p_listen
        self.fail_all_p2p_listen = fail_all_p2p_listen
        self.station_connected = station_connected
        self.wifi_driver = wifi_driver
        self.p2p_listen_attempts = 0
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
            stdout = "".join("Interface {}\n type P2P-GO\n".format(group)
                             for group in self.external_groups) + stdout
        elif command == [
            "/usr/bin/readlink",
            "-f",
            "/sys/class/net/wlan0/device/driver",
        ]:
            stdout = "/sys/bus/pci/drivers/{}\n".format(self.wifi_driver)
        elif command == ["/usr/sbin/iw", "dev", "wlan0", "info"]:
            if self.concurrency_supported is not None:
                stdout = "Interface wlan0\n\twiphy 0\n\ttype managed\n"
        elif command[:2] == ["/usr/sbin/iw", "dev"] and command[-2:] == [
            "station", "dump"
        ]:
            if self.group_created and self.station_connected:
                stdout = "Station aa:bb:cc:dd:ee:ff (on {})\n".format(command[2])
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
        elif "NAME,TYPE,DEVICE" in command:
            if self.active_p2p_profile:
                stdout = "{}:wifi-p2p:p2p-wlan0-0\n".format(self.active_p2p_profile)
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
                self.active_p2p_profile = command[5]
                stdout = "Connection successfully activated\n"
        elif command[:3] in (
            ["/usr/bin/nmcli", "connection", "down"],
            ["/usr/bin/nmcli", "connection", "delete"],
        ):
            if command[3] == self.active_p2p_profile:
                self.group_created = False
                self.active_p2p_profile = None
        elif command[:3] == ["/usr/bin/nmcli", "--wait", "20"]:
            self.managed_wifi_active = False
            stdout = "Connection successfully deactivated\n"
        elif command[:3] == ["/usr/bin/nmcli", "--wait", "45"]:
            self.managed_wifi_active = True
            stdout = "Connection successfully activated\n"
        elif command[0] == "/usr/sbin/wpa_cli":
            if "p2p_listen" in command:
                self.p2p_listen_attempts += 1
                if self.fail_all_p2p_listen or (
                    self.fail_first_p2p_listen and self.p2p_listen_attempts == 1
                ):
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
    def test_observed_frequency_is_scoped_to_named_interface(self):
        output = """phy#0
 Interface p2p-wlan0-2
  type P2P-GO
  channel 48 (5240 MHz), width: 20 MHz, center1: 5240 MHz
 Unnamed/non-netdev interface
  type P2P-device
 Interface wlan0
  type managed
  channel 1 (2412 MHz), width: 20 MHz, center1: 2412 MHz
"""
        self.assertEqual(parse_iw_frequency(output, "p2p-wlan0-2"), 5240)
        self.assertEqual(parse_iw_frequency(output, "wlan0"), 2412)
        self.assertIsNone(parse_iw_frequency(output, "p2p-wlan0-1"))
        self.assertIsNone(parse_iw_frequency(
            "Interface p2p-wlan0-2\nUnnamed/non-netdev interface\n channel 1 (2412 MHz)",
            "p2p-wlan0-2",
        ))

    def test_two_channel_device_reports_observation_without_radio_changes_in_ready(self):
        runner = FakeRunner(concurrency_supported=True, managed_wifi_active=True)
        observation_fails = [False]

        def run(command, **kwargs):
            result = runner(command, **kwargs)
            if command == ["/usr/sbin/iw", "phy", "phy0", "info"]:
                result.stdout = """valid interface combinations:
 * #{ managed } <= 1, #{ AP, P2P-client, P2P-GO } <= 1,
   #{ P2P-device } <= 1, total <= 3, #channels <= 2
"""
            if command == ["/usr/sbin/iw", "dev"] and runner.group_created:
                if observation_fails[0]:
                    return subprocess.CompletedProcess(command, 1, "", "unavailable")
                result.stdout = """Interface p2p-wlan0-0
 type P2P-GO
 channel 48 (5240 MHz), width: 20 MHz
Interface wlan0
 type managed
 channel 48 (5240 MHz), width: 80 MHz
"""
            return result

        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "wifi-direct.json"
            controller = WifiDirectController(
                WifiDirectSettings(interface="wlan0", device_name="MMS-JETSON"),
                run=run, status_path=path, sleep=lambda _: None,
            )
            controller.prepare()
            self.assertIsNone(read_wifi_direct_status(path)["groupFrequencyMhz"])
            controller.activate_peer_for_test("AA:BB:CC:DD:EE:FF")
            status = read_wifi_direct_status(path)
            self.assertEqual(status["frequencyMhz"], 2412)
            self.assertEqual(status["groupFrequencyMhz"], 5240)
            self.assertTrue(runner.managed_wifi_active)
            before = len(runner.calls)
            for _ in range(10):
                controller.monitor()
            self.assertTrue(all(
                call == ["/usr/sbin/iw", "dev"] or
                call == ["/usr/sbin/ip", "-j", "-4", "address", "show", "dev", "p2p-wlan0-0"]
                for call in runner.calls[before:]
            ))
            observation_fails[0] = True
            controller.monitor()
            self.assertIsNone(read_wifi_direct_status(path)["groupFrequencyMhz"])
            self.assertTrue(runner.group_created)
            observation_fails[0] = False
            controller.monitor()
            self.assertEqual(read_wifi_direct_status(path)["groupFrequencyMhz"], 5240)
            controller.stop()
            self.assertIsNone(read_wifi_direct_status(path)["groupFrequencyMhz"])

    def test_failed_link_observation_preserves_group_and_recovers_after_valid_query(self):
        for command_type in ("group", "address", "timeout"):
            with self.subTest(command_type=command_type), tempfile.TemporaryDirectory() as temporary:
                runner = FakeRunner(concurrency_supported=False, managed_wifi_active=True,
                                    single_interface_group=True)
                fail_query = [False]

                def run(command, **kwargs):
                    targeted = command == ["/usr/sbin/iw", "dev"]
                    if command_type == "address":
                        targeted = command[:4] == ["/usr/sbin/ip", "-j", "-4", "address"]
                    if fail_query[0] and targeted:
                        runner.calls.append(command)
                        if command_type == "timeout":
                            raise subprocess.TimeoutExpired(command, kwargs["timeout"])
                        return subprocess.CompletedProcess(command, 1, "", "query unavailable")
                    return runner(command, **kwargs)

                path = Path(temporary) / "wifi-direct.json"
                controller = WifiDirectController(
                    WifiDirectSettings(interface="wlan0", device_name="MMS-JETSON"),
                    run=run, start_process=runner.start_process, status_path=path,
                    sleep=lambda _seconds: None,
                )
                controller.prepare()
                controller.activate_peer_for_test("AA:BB:CC:DD:EE:FF")
                before = len(runner.calls)
                fail_query[0] = True
                self.assertTrue(controller.monitor())
                self.assertTrue(runner.group_created, "unknown link state must not remove a group")
                self.assertFalse(runner.dnsmasq_processes[0].terminated)
                self.assertFalse(any("p2p_group_remove" in call for call in runner.calls[before:]))
                self.assertEqual(read_wifi_direct_status(path)["state"], "ERROR")
                self.assertFalse(controller.request_connection("AA:BB:CC:DD:EE:FF"))

                fail_query[0] = False
                controller.monitor()
                self.assertEqual(read_wifi_direct_status(path)["state"], "READY")
                self.assertTrue(runner.group_created)
                controller.stop()

    def test_repeated_unknown_group_then_confirmed_absence_uses_existing_recovery(self):
        runner = FakeRunner(concurrency_supported=False, managed_wifi_active=True,
                            single_interface_group=True)
        fail_query = [False]

        def run(command, **kwargs):
            if fail_query[0] and command == ["/usr/sbin/iw", "dev"]:
                runner.calls.append(command)
                return subprocess.CompletedProcess(command, 1, "", "query unavailable")
            return runner(command, **kwargs)

        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "wifi-direct.json"
            controller = WifiDirectController(
                WifiDirectSettings(interface="wlan0", device_name="MMS-JETSON"),
                run=run, start_process=runner.start_process, status_path=path,
                sleep=lambda _seconds: None,
            )
            controller.prepare()
            controller.activate_peer_for_test("AA:BB:CC:DD:EE:FF")
            fail_query[0] = True
            for _tick in range(10):
                before = len(runner.calls)
                controller.monitor()
                self.assertEqual(len(runner.calls) - before, 1)
                self.assertEqual(read_wifi_direct_status(path)["state"], "ERROR")
                self.assertTrue(runner.group_created)
                self.assertFalse(controller.request_connection("AA:BB:CC:DD:EE:FF"))
            fail_query[0] = False
            runner.group_created = False
            controller.monitor()
            self.assertEqual(read_wifi_direct_status(path)["state"], "DISCOVERABLE")
            self.assertTrue(runner.dnsmasq_processes[0].terminated)
            self.assertTrue(runner.managed_wifi_active)
            controller.stop()

    def test_cleanup_does_not_confirm_absence_when_group_query_fails(self):
        runner = FakeRunner(concurrency_supported=False, managed_wifi_active=True,
                            single_interface_group=True)
        fail_query = [False]

        def run(command, **kwargs):
            if fail_query[0] and command == ["/usr/sbin/iw", "dev"]:
                return subprocess.CompletedProcess(command, 1, "", "query unavailable")
            return runner(command, **kwargs)

        with tempfile.TemporaryDirectory() as temporary:
            controller = WifiDirectController(
                WifiDirectSettings(interface="wlan0", device_name="MMS-JETSON"),
                run=run, start_process=runner.start_process,
                status_path=Path(temporary) / "wifi-direct.json", sleep=lambda _seconds: None,
            )
            controller.prepare()
            controller.activate_peer_for_test("AA:BB:CC:DD:EE:FF")
            fail_query[0] = True
            with self.assertRaises(WifiDirectError):
                controller._cleanup_direct_connection()
            self.assertIsNotNone(controller.group_interface)
            fail_query[0] = False
            controller.stop()

    def test_activation_timeout_restores_wifi_and_accepts_next_request(self):
        runner = FakeRunner(concurrency_supported=False, managed_wifi_active=True,
                            single_interface_group=True)
        timed_out = [False]

        def run(command, **kwargs):
            if "p2p_connect" in command and not timed_out[0]:
                timed_out[0] = True
                raise subprocess.TimeoutExpired(command, kwargs["timeout"])
            return runner(command, **kwargs)

        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "wifi-direct.json"
            controller = WifiDirectController(
                WifiDirectSettings(interface="wlan0", device_name="MMS-JETSON"),
                run=run, start_process=runner.start_process, status_path=path,
                sleep=lambda _seconds: None,
            )
            controller.prepare()
            self.assertEqual(controller.activate_peer_for_test("AA:BB:CC:DD:EE:FF"), "")
            status = read_wifi_direct_status(path)
            self.assertEqual(status["state"], "DISCOVERABLE")
            self.assertIn("timed out", status["lastError"])
            self.assertTrue(runner.managed_wifi_active)
            self.assertTrue(controller.request_connection("AA:BB:CC:DD:EE:FF"))
            controller._activation_thread.join(timeout=2)
            self.assertEqual(read_wifi_direct_status(path)["state"], "READY")
            controller.stop()

    def test_attempt_deadline_rejects_late_success_and_cleans_created_group(self):
        runner = FakeRunner(concurrency_supported=False, managed_wifi_active=True,
                            single_interface_group=True)
        now = [1000.0]

        def run(command, **kwargs):
            result = runner(command, **kwargs)
            if "p2p_connect" in command:
                now[0] += 121
            return result

        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "wifi-direct.json"
            controller = WifiDirectController(
                WifiDirectSettings(interface="wlan0", device_name="MMS-JETSON"),
                run=run, start_process=runner.start_process, status_path=path,
                sleep=lambda _seconds: None, monotonic=lambda: now[0],
                wallclock=lambda: now[0],
            )
            controller.prepare()
            self.assertEqual(controller.activate_peer_for_test("AA:BB:CC:DD:EE:FF"), "")
            self.assertEqual(read_wifi_direct_status(path)["state"], "DISCOVERABLE")
            self.assertFalse(runner.group_created)
            self.assertTrue(runner.managed_wifi_active)

    def test_monitor_recovers_dead_worker_and_surfaces_cleanup_failure(self):
        runner = FakeRunner()
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "wifi-direct.json"
            controller = WifiDirectController(
                WifiDirectSettings(interface="wlan0", device_name="MMS-JETSON"),
                run=runner, start_process=runner.start_process, status_path=path,
                sleep=lambda _seconds: None,
            )
            controller.prepare()
            controller._begin_attempt()
            controller._publish("CONNECTING", "dead worker")
            controller.monitor()
            self.assertEqual(read_wifi_direct_status(path)["state"], "DISCOVERABLE")

            controller._begin_attempt()
            controller._publish("CONNECTING", "dead worker")
            with patch.object(controller, "_cleanup_direct_connection", side_effect=WifiDirectError("DHCP cleanup failed")), patch.object(controller, "_restore_suspended_wifi") as restore:
                controller.monitor()
                restore.assert_called_once()
            controller.monitor()
            status = read_wifi_direct_status(path)
            self.assertEqual(status["state"], "ERROR")
            self.assertIn("DHCP cleanup failed", status["lastError"])
            self.assertFalse(controller.request_connection("AA:BB:CC:DD:EE:FF"))

    def test_iwlwifi_uses_active_find_so_android_can_discover_it(self):
        runner = FakeRunner(wifi_driver="iwlwifi")
        with tempfile.TemporaryDirectory() as temporary:
            controller = WifiDirectController(
                WifiDirectSettings(interface="wlan0", device_name="MMS-JETSON"),
                run=runner,
                start_process=runner.start_process,
                status_path=Path(temporary) / "wifi-direct.json",
                sleep=lambda _seconds: None,
            )

            self.assertTrue(controller.refresh_discovery())

        discovery_calls = [
            call[7:]
            for call in runner.calls
            if call and call[0] == "/usr/sbin/wpa_cli" and (
                "p2p_find" in call or "p2p_listen" in call
            )
        ]
        self.assertEqual(discovery_calls, [["p2p_find", "600"]])

    def test_discovery_recovers_busy_scan_before_starting_listener(self):
        runner = FakeRunner(fail_first_p2p_listen=True)
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
                ["p2p_listen"],
                ["abort_scan"],
                ["p2p_stop_find"],
                ["p2p_listen"],
            ],
        )

    def test_monitor_does_not_replace_active_listener(self):
        runner = FakeRunner()
        now = [1_000.0]
        with tempfile.TemporaryDirectory() as temporary:
            controller = WifiDirectController(
                WifiDirectSettings(interface="wlan0", device_name="MMS-JETSON"),
                run=runner,
                start_process=runner.start_process,
                status_path=Path(temporary) / "wifi-direct.json",
                sleep=lambda _seconds: None,
                monotonic=lambda: now[0],
            )
            self.assertEqual(controller.prepare(), "DISCOVERABLE")

            now[0] += 3_600.0
            self.assertTrue(controller.monitor())

        discovery_calls = [
            call[7:]
            for call in runner.calls
            if call and call[0] == "/usr/sbin/wpa_cli" and (
                "p2p_listen" in call or "p2p_stop_find" in call
            )
        ]
        self.assertEqual(
            discovery_calls,
            [
                ["p2p_stop_find"],
                ["p2p_listen"],
            ],
        )

    def test_find_stopped_event_restarts_after_driver_settle(self):
        runner = FakeRunner()
        now = [1_000.0]
        with tempfile.TemporaryDirectory() as temporary:
            controller = WifiDirectController(
                WifiDirectSettings(interface="wlan0", device_name="MMS-JETSON"),
                run=runner,
                start_process=runner.start_process,
                status_path=Path(temporary) / "wifi-direct.json",
                sleep=lambda _seconds: None,
                monotonic=lambda: now[0],
            )
            self.assertEqual(controller.prepare(), "DISCOVERABLE")

            now[0] += 100.0
            controller.discovery_stopped()
            now[0] += 4.0
            self.assertTrue(controller.monitor())
            self.assertEqual(runner.p2p_listen_attempts, 1)
            now[0] += 1.0
            self.assertTrue(controller.monitor())
            self.assertEqual(runner.p2p_listen_attempts, 2)

        discovery_calls = [
            call[7:]
            for call in runner.calls
            if call and call[0] == "/usr/sbin/wpa_cli" and (
                "p2p_listen" in call or "p2p_stop_find" in call
            )
        ]
        self.assertEqual(
            discovery_calls,
            [["p2p_stop_find"], ["p2p_listen"], ["p2p_listen"]],
        )

    def test_failed_discovery_restart_backs_off_and_recovers(self):
        runner = FakeRunner()
        now = [1_000.0]
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

            runner.fail_all_p2p_listen = True
            controller.discovery_stopped()
            now[0] += 5.0
            self.assertTrue(controller.monitor())
            self.assertEqual(read_wifi_direct_status(status_path)["state"], "ERROR")
            self.assertEqual(runner.p2p_listen_attempts, 2)

            now[0] += 9.0
            self.assertTrue(controller.monitor())
            self.assertEqual(runner.p2p_listen_attempts, 2)

            runner.fail_all_p2p_listen = False
            now[0] += 1.0
            self.assertTrue(controller.monitor())
            self.assertEqual(runner.p2p_listen_attempts, 3)
            self.assertEqual(
                read_wifi_direct_status(status_path)["state"],
                "DISCOVERABLE",
            )

    def test_monitor_does_not_restart_discovery_during_peer_activation(self):
        class ActiveThread:
            @staticmethod
            def is_alive():
                return True

        runner = FakeRunner()
        now = [1_000.0]
        with tempfile.TemporaryDirectory() as temporary:
            controller = WifiDirectController(
                WifiDirectSettings(interface="wlan0", device_name="MMS-JETSON"),
                run=runner,
                start_process=runner.start_process,
                status_path=Path(temporary) / "wifi-direct.json",
                sleep=lambda _seconds: None,
                monotonic=lambda: now[0],
            )
            self.assertEqual(controller.prepare(), "DISCOVERABLE")
            controller._activation_thread = ActiveThread()
            controller.discovery_stopped()
            now[0] += 5.0

            self.assertTrue(controller.monitor())
            self.assertEqual(runner.p2p_listen_attempts, 1)

    def test_connection_request_publishes_connecting_before_thread_starts(self):
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
            activation_thread = Mock()
            with patch(
                "jetson_control.wifi_direct.threading.Thread",
                return_value=activation_thread,
            ):
                self.assertTrue(
                    controller.request_connection("AA:BB:CC:DD:EE:FF")
                )

            self.assertEqual(
                read_wifi_direct_status(status_path)["state"],
                "CONNECTING",
            )
            activation_thread.start.assert_called_once_with()

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

    def test_pbc_provision_discovery_request_starts_connection(self):
        controller = Mock()
        runtime = WifiDirectRuntime(controller)

        runtime._on_provision_discovery_pbc_request(
            "/fi/w1/wpa_supplicant1/Interfaces/0/Peers/aabbccddeeff"
        )

        controller.request_connection.assert_called_once_with("AA:BB:CC:DD:EE:FF")

    def test_go_negotiation_request_still_accepts_pbc(self):
        controller = Mock()
        runtime = WifiDirectRuntime(controller)
        peer_path = "/fi/w1/wpa_supplicant1/Interfaces/0/Peers/aabbccddeeff"

        runtime._on_go_negotiation_request(peer_path, 4, 0)

        controller.request_connection.assert_called_once_with("AA:BB:CC:DD:EE:FF")

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
            self.assertEqual(add_call[add_call.index("ipv4.method") + 1], "shared")
            self.assertEqual(add_call[add_call.index("ipv4.never-default") + 1], "yes")
            self.assertEqual(add_call[add_call.index("save") + 1], "no")
            self.assertEqual(add_call[add_call.index("autoconnect") + 1], "no")
            self.assertTrue(
                all(
                    ["-s", str(status_path.parent / "wpa-cli")] == call[3:5]
                    for call in runner.calls
                    if call[0] == "/usr/sbin/wpa_cli"
                )
            )

            controller.stop()
            self.assertEqual(read_wifi_direct_status(status_path)["state"], "STOPPED")

    def test_single_interface_fallback_prioritizes_direct_and_restores_wifi(self):
        runner = FakeRunner(
            concurrency_supported=False,
            managed_wifi_active=True,
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

    def test_single_interface_fallback_works_offline_without_alternate_route(self):
        runner = FakeRunner(
            concurrency_supported=False,
            managed_wifi_active=False,
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
                any(call[:6] == [
                    "/usr/bin/nmcli", "--wait", "20", "device", "disconnect", "wlan0",
                ] for call in runner.calls)
            )

    def test_single_interface_fallback_restores_wifi_after_activation_failure(self):
        runner = FakeRunner(
            concurrency_supported=False,
            managed_wifi_active=True,
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

    def test_manual_owner_cleans_empty_group_after_peer_disconnects(self):
        runner = FakeRunner(
            concurrency_supported=False,
            managed_wifi_active=True,
            single_interface_group=True,
        )
        now = [1_000.0]
        with tempfile.TemporaryDirectory() as temporary:
            status_path = Path(temporary) / "wifi-direct.json"
            controller = WifiDirectController(
                WifiDirectSettings(interface="wlan0", device_name="MMS-JETSON"),
                run=runner,
                start_process=runner.start_process,
                status_path=status_path,
                sleep=lambda _seconds: None,
                monotonic=lambda: now[0],
                wallclock=lambda: now[0],
            )

            self.assertEqual(controller.prepare(), "DISCOVERABLE")
            self.assertEqual(
                controller.activate_peer_for_test("AA:BB:CC:DD:EE:FF"),
                "wlan0",
            )
            runner.station_connected = False

            controller.monitor()
            now[0] += 9.0
            controller.monitor()
            self.assertTrue(runner.group_created)
            self.assertEqual(read_wifi_direct_status(status_path)["state"], "READY")

            now[0] += 1.0
            controller.monitor()

            self.assertFalse(runner.group_created)
            self.assertTrue(runner.managed_wifi_active)
            self.assertTrue(runner.dnsmasq_processes[0].terminated)
            self.assertEqual(
                read_wifi_direct_status(status_path)["state"],
                "DISCOVERABLE",
            )

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


class WifiDirectClockTest(unittest.TestCase):
    def setUp(self):
        self.new_controller()

    def new_controller(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.clock = FakeClock()
        self.runner = FakeRunner()
        self.before_command = lambda _command: None
        self.after_command = lambda _command: None
        self.shared_lease_epochs = []
        self.path = Path(temporary.name) / "wifi-direct.json"
        self.controller = WifiDirectController(
            WifiDirectSettings(interface="wlan0", device_name="MMS-JETSON"),
            run=self.run_command, start_process=self.runner.start_process,
            status_path=self.path, sleep=lambda _: None,
            monotonic=lambda: self.clock.mono, wallclock=lambda: self.clock.wall,
        )

    def run_command(self, command, **kwargs):
        override = self.before_command(command)
        if override is not None:
            return override
        result = self.runner(command, **kwargs)
        if command[:5] == ["/usr/bin/nmcli", "--wait", "50", "connection", "up"]:
            # Model the shared DHCP ACK epoch at successful NM activation.
            self.shared_lease_epochs.append(self.clock.wall)
        self.after_command(command)
        return result

    def activate(self, manual=False):
        if manual:
            self.runner.concurrency_supported = False
            self.runner.single_interface_group = True
            self.runner.managed_wifi_active = True
        self.assertEqual(self.controller.prepare(), "DISCOVERABLE")
        expected = "wlan0" if manual else "p2p-wlan0-0"
        self.assertEqual(self.controller.activate_peer_for_test("AA:BB:CC:DD:EE:FF"), expected)

    def assert_state(self, state):
        self.assertEqual(read_wifi_direct_status(self.path)["state"], state)

    def test_old_shared_lease_clock_step_cleans_once_and_next_lease_uses_current_time(self):
        self.activate()
        self.assertEqual(self.shared_lease_epochs, [100.0])
        before = len(self.runner.calls)
        self.clock.wall = 1_790_000_000.0
        self.clock.advance(1)
        self.assertGreater(self.clock.wall, self.shared_lease_epochs[0] + 3600)
        self.controller.monitor()
        self.assert_state("DISCOVERABLE")
        self.assertFalse(self.runner.group_created)
        for _ in range(5):
            self.clock.advance(1)
            self.controller.refresh_discovery()
            self.controller.monitor()
        cleanup_calls = self.runner.calls[before:]
        self.assertEqual(sum(call[:3] == ["/usr/bin/nmcli", "connection", "delete"]
                             for call in cleanup_calls), 1)
        self.assertFalse(any("p2p_group_remove" in call for call in cleanup_calls))
        self.assertEqual(self.runner.dnsmasq_processes, [])
        epoch = self.clock.wall
        self.assertEqual(self.controller.activate_peer_for_test("AA:BB:CC:DD:EE:FF"),
                         "p2p-wlan0-0")
        self.assertEqual(self.shared_lease_epochs, [100.0, epoch])
        self.clock.advance(1800.004722)
        self.controller.monitor()
        self.assertLess(self.clock.wall, self.shared_lease_epochs[-1] + 3600)
        self.assert_state("READY")

    def test_backward_step_also_recovers_owned_group(self):
        self.clock.wall = 1_790_000_000.0
        self.activate()
        self.clock.wall -= 1_000_000
        self.controller.monitor()
        self.assert_state("DISCOVERABLE")
        self.assertFalse(self.runner.group_created)
        self.controller.monitor()
        self.assert_state("DISCOVERABLE")

    def test_jump_immediately_before_formation_preserves_new_group(self):
        for manual in (False, True):
            with self.subTest(manual=manual):
                self.new_controller()
                self.runner.concurrency_supported = not manual
                self.runner.single_interface_group = manual
                self.controller.prepare()

                def jump_before_formation(command):
                    if (command[:3] == ["/usr/bin/nmcli", "connection", "add"]
                            or "p2p_stop_find" in command):
                        self.clock.wall = 1_790_000_000.0

                self.after_command = jump_before_formation
                self.assertTrue(self.controller.activate_peer_for_test("AA:BB:CC:DD:EE:FF"))
                before = len(self.runner.calls)
                for _ in range(3):
                    self.clock.advance(1)
                    self.controller.monitor()
                self.assert_state("READY")
                self.assertTrue(self.runner.group_created)
                self.assertFalse(any("p2p_group_remove" in call or "down" in call
                                     for call in self.runner.calls[before:]))

    def test_step_during_formation_recovers_both_owner_paths(self):
        for manual in (False, True):
            for step in (1_790_000_000, -1_790_000_000):
                with self.subTest(manual=manual, step=step):
                    self.new_controller()
                    self.runner.concurrency_supported = not manual
                    self.runner.single_interface_group = manual
                    self.controller.prepare()

                    def jump_during_formation(command):
                        if "p2p_connect" in command or command[:3] == [
                            "/usr/bin/nmcli", "--wait", "50"
                        ]:
                            self.clock.wall += step

                    self.after_command = jump_during_formation
                    self.assertEqual(self.controller.activate_peer_for_test("AA:BB:CC:DD:EE:FF"), "")
                    self.assert_state("DISCOVERABLE")
                    self.assertFalse(self.runner.group_created)
                    self.assertIn("System clock changed", read_wifi_direct_status(self.path)["lastError"])

    def test_monitor_defers_clock_cleanup_to_live_activation_worker(self):
        entered = threading.Event()
        release = threading.Event()
        self.controller.prepare()

        def block_activation(command):
            if command[:3] == ["/usr/bin/nmcli", "--wait", "50"]:
                entered.set()
                release.wait(timeout=2)

        self.after_command = block_activation
        self.assertTrue(self.controller.request_connection("AA:BB:CC:DD:EE:FF"))
        try:
            self.assertTrue(entered.wait(timeout=2))
            before = len(self.runner.calls)
            self.clock.wall += 1_790_000_000
            self.controller.monitor()
            self.assertTrue(self.runner.group_created)
            self.assertEqual(len(self.runner.calls), before)
        finally:
            release.set()
            self.controller._activation_thread.join(timeout=2)
        self.assertFalse(self.controller._activation_thread.is_alive())
        self.assert_state("DISCOVERABLE")
        self.assertFalse(self.runner.group_created)

    def test_unchanged_clock_and_accumulated_gradual_drift_do_not_reset(self):
        self.activate()
        before = len(self.runner.calls)
        for _ in range(10):
            self.controller.monitor()
        for _ in range(100):
            self.clock.advance(600, drift=0.3)
            self.controller.monitor()
        self.assert_state("READY")
        self.assertTrue(self.runner.group_created)
        self.assertFalse(any("down" in call or "p2p_group_remove" in call
                             for call in self.runner.calls[before:]))

    def test_sampling_delay_is_not_evidence_of_a_clock_step(self):
        self.activate()

        def delayed_wall_read():
            self.clock.advance(10)
            return self.clock.wall

        self.controller._wallclock = delayed_wall_read
        for _ in range(3):
            self.controller.monitor()
        self.assert_state("READY")
        self.assertTrue(self.runner.group_created)

    def test_cleanup_failure_blocks_connections_then_retries_using_monotonic(self):
        self.activate()
        failures = []

        def fail_cleanup(command):
            if command[:3] in (["/usr/bin/nmcli", "connection", "down"],
                               ["/usr/bin/nmcli", "connection", "delete"]):
                failures.append(command)
                return subprocess.CompletedProcess(command, 1, "", "cleanup unavailable")

        self.before_command = fail_cleanup
        self.clock.wall += 1_790_000_000
        self.controller.monitor()
        self.assert_state("ERROR")
        self.assertTrue(self.runner.group_created)
        self.assertFalse(self.controller.request_connection("AA:BB:CC:DD:EE:FF"))
        self.clock.wall += 100_000
        self.controller.monitor()
        self.assertEqual(len(failures), 2)
        self.before_command = lambda _: None
        self.clock.advance(10)
        self.controller.monitor()
        self.assert_state("DISCOVERABLE")
        self.assertFalse(self.runner.group_created)
        self.assertFalse(self.controller._recovery_failed)
        self.assertTrue(self.controller.activate_peer_for_test("AA:BB:CC:DD:EE:FF"))

    def test_discovery_failure_after_cleanup_retries_without_another_group_reset(self):
        self.activate()
        self.runner.fail_all_p2p_listen = True
        self.clock.wall += 1_790_000_000
        self.controller.monitor()
        self.assert_state("ERROR")
        before = len(self.runner.calls)
        self.runner.fail_all_p2p_listen = False
        self.clock.advance(10)
        self.controller.monitor()
        self.assert_state("DISCOVERABLE")
        self.assertFalse(any("delete" in call or "p2p_group_remove" in call
                             for call in self.runner.calls[before:]))

    def test_manual_cleanup_failure_retains_target_until_recovery(self):
        self.activate(manual=True)

        def fail_remove(command):
            if "p2p_group_remove" in command:
                return subprocess.CompletedProcess(command, 0, "FAIL\n", "")

        self.before_command = fail_remove
        self.clock.wall += 1_790_000_000
        self.controller.monitor()
        self.assert_state("ERROR")
        self.assertEqual(self.controller.group_interface, "wlan0")
        self.assertTrue(self.runner.group_created)
        self.before_command = lambda _: None
        self.clock.advance(10)
        self.controller.monitor()
        self.assert_state("DISCOVERABLE")
        self.assertFalse(self.runner.group_created)
        self.assertTrue(self.runner.dnsmasq_processes[0].terminated)

    def test_clock_cleanup_preserves_external_group_and_stays_discoverable(self):
        for manual in (False, True):
            with self.subTest(manual=manual):
                self.new_controller()
                self.runner.concurrency_supported = not manual
                self.controller.prepare()
                self.runner.external_groups = ["p2p-wlan0-9", "p2p-wlan1-0"]
                self.assertEqual(self.controller.activate_peer_for_test("AA:BB:CC:DD:EE:FF"),
                                 "p2p-wlan0-0")
                self.clock.wall += 1_790_000_000
                self.controller.monitor()
                for _ in range(3):
                    self.clock.advance(1)
                    self.controller.monitor()
                self.assert_state("DISCOVERABLE")
                removals = [call[-1] for call in self.runner.calls if "p2p_group_remove" in call]
                self.assertEqual(removals, ["p2p-wlan0-0"] if manual else [])
                self.assertEqual(self.runner.external_groups, ["p2p-wlan0-9", "p2p-wlan1-0"])
                self.assertFalse(self.runner.group_created)

    def test_clock_step_without_owned_group_does_not_remove_external_group(self):
        self.controller.prepare()
        self.runner.external_groups = ["p2p-wlan0-9"]
        before = len(self.runner.calls)
        self.clock.wall += 1_790_000_000
        self.controller.monitor()
        self.assert_state("DISCOVERABLE")
        self.assertFalse(any("delete" in call or "p2p_group_remove" in call
                             for call in self.runner.calls[before:]))

    def test_prepare_adopts_managed_group_and_observes_later_step(self):
        self.runner.group_created = True
        self.runner.active_p2p_profile = "jetson-control-p2p-aabbccddeeff"
        self.runner.external_groups = ["p2p-wlan0-9"]
        self.assertEqual(self.controller.prepare(), "READY")
        self.assertEqual(self.controller.group_interface, "p2p-wlan0-0")
        self.clock.wall += 1_790_000_000
        self.controller.monitor()
        self.assert_state("DISCOVERABLE")
        self.assertFalse(self.runner.group_created)
        self.assertFalse(any("p2p_group_remove" in call for call in self.runner.calls))


if __name__ == "__main__":
    unittest.main()
