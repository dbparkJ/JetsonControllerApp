import unittest
import subprocess
import tempfile
import time
import os
import stat
import threading
from pathlib import Path

from jetson_control.network import (
    WIFI_DIRECT_RESUME_FILENAME,
    WIFI_MODE_DIRECT,
    WIFI_MODE_LAN,
    WIFI_PROVISION_RESPONSE_GRACE_SECONDS,
    WifiModeCoordinator,
    WifiModeRequest,
    WifiProvisioner,
    decode_wifi_payload,
    validate_wifi_credentials,
)


class ImmediateModeCoordinator:
    def __init__(self, ready=True, suspended_profile=None):
        self.ready = ready
        self._suspended_profile = suspended_profile
        self.requests = []
        self.current = None
        self.busy = False
        self.cleared_suspended_profile = False
        self.counter = 0

    def begin_provisioning(self):
        if self.busy:
            raise RuntimeError("A Wi-Fi provisioning request is already running")
        self.busy = True
        return "provisioning-token"

    def finish_provisioning(self, token):
        if token == "provisioning-token":
            self.busy = False

    def request(self, mode):
        self.counter += 1
        request = WifiModeRequest("{:032x}".format(self.counter), mode)
        self.requests.append(mode)
        self.current = request
        return request

    def request_direct(self):
        if self.busy:
            raise RuntimeError("Wi-Fi provisioning is in progress")
        return self.request(WIFI_MODE_DIRECT)

    def wait_for_ready(self, request):
        del request
        return self.ready, "" if self.ready else "Wi-Fi mode transition timed out"

    def current_request(self):
        return self.current

    def suspended_profile(self):
        return self._suspended_profile

    def clear_suspended_profile(self):
        self._suspended_profile = None
        self.cleared_suspended_profile = True


class WifiPayloadTest(unittest.TestCase):
    def setUp(self) -> None:
        self._temporary_directory = tempfile.TemporaryDirectory()
        self.addCleanup(self._temporary_directory.cleanup)
        self.wpa_client_path = Path(self._temporary_directory.name) / "wpa-cli"
        self.mode_coordinator = ImmediateModeCoordinator()

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

    def test_mode_protocol_uses_secure_atomic_files_and_matching_ack(self) -> None:
        runtime_path = Path(self._temporary_directory.name) / "mode"
        coordinator = WifiModeCoordinator(runtime_path)

        request = coordinator.request(WIFI_MODE_DIRECT)
        coordinator.acknowledge(request, True, "ready")
        coordinator.remember_suspended_profile(
            "11111111-2222-3333-4444-555555555555",
        )

        self.assertEqual(coordinator.current_request(), request)
        self.assertEqual(coordinator.wait_for_ready(request, timeout=0.01), (True, "ready"))
        self.assertEqual(
            coordinator.suspended_profile(),
            "11111111-2222-3333-4444-555555555555",
        )
        for path in (
            coordinator.request_path,
            coordinator.ack_path,
            coordinator.suspended_profile_path,
            coordinator.state_lock_path,
        ):
            self.assertEqual(stat.S_IMODE(path.stat().st_mode), 0o600)

        token = coordinator.begin_provisioning()
        with self.assertRaisesRegex(RuntimeError, "in progress"):
            coordinator.request_direct()
        coordinator.finish_provisioning(token)
        self.assertFalse(coordinator.provisioning_active())

    def test_dead_provisioning_owner_is_reaped_instead_of_blocking_direct(self) -> None:
        runtime_path = Path(self._temporary_directory.name) / "stale-mode"
        coordinator = WifiModeCoordinator(runtime_path)
        coordinator._atomic_write(
            coordinator.provisioning_path,
            {
                "version": 1,
                "token": "1" * 32,
                "pid": 99999999,
                "processStartTime": "1",
                "createdAtMonotonicSeconds": time.monotonic(),
            },
        )

        self.assertFalse(coordinator.provisioning_active())
        self.assertFalse(coordinator.provisioning_path.exists())
        self.assertEqual(coordinator.request_direct().mode, WIFI_MODE_DIRECT)

    def test_live_provisioning_owner_does_not_expire_by_age(self) -> None:
        runtime_path = Path(self._temporary_directory.name) / "live-mode"
        now = time.monotonic()
        coordinator = WifiModeCoordinator(runtime_path, monotonic=lambda: now)
        process_start = coordinator._process_start_time(os.getpid())
        self.assertIsNotNone(process_start)
        coordinator._atomic_write(
            coordinator.provisioning_path,
            {
                "version": 1,
                "token": "1" * 32,
                "pid": os.getpid(),
                "processStartTime": process_start,
                "createdAtMonotonicSeconds": 1.0,
            },
        )

        self.assertTrue(coordinator.provisioning_active())
        self.assertTrue(coordinator.provisioning_path.exists())
        with self.assertRaisesRegex(RuntimeError, "provisioning is in progress"):
            coordinator.request_direct()

    def test_provisioning_marker_from_future_monotonic_clock_is_reaped(self) -> None:
        runtime_path = Path(self._temporary_directory.name) / "future-mode"
        coordinator = WifiModeCoordinator(runtime_path, monotonic=lambda: 100.0)
        coordinator._atomic_write(
            coordinator.provisioning_path,
            {
                "version": 1,
                "token": "2" * 32,
                "pid": os.getpid(),
                "processStartTime": coordinator._process_start_time(os.getpid()),
                "createdAtMonotonicSeconds": 101.0,
            },
        )

        self.assertFalse(coordinator.provisioning_active())
        self.assertFalse(coordinator.provisioning_path.exists())

    def test_mode_reader_rejects_a_symlink_even_when_target_is_valid_json(self) -> None:
        runtime_path = Path(self._temporary_directory.name) / "unsafe-mode"
        runtime_path.mkdir()
        outside = Path(self._temporary_directory.name) / "outside.json"
        outside.write_text(
            '{"version":1,"requestId":"' + "1" * 32 + '","mode":"DIRECT"}',
            encoding="utf-8",
        )
        os.chmod(str(outside), 0o600)
        coordinator = WifiModeCoordinator(runtime_path)
        coordinator.request_path.symlink_to(outside)

        self.assertIsNone(coordinator.current_request())

    def test_nmcli_receives_password_on_stdin_not_process_arguments(self) -> None:
        captured = []

        def run(command, **kwargs):
            captured.append((command, kwargs.get("input")))
            return subprocess.CompletedProcess(command, 0, "", "")

        provisioner = WifiProvisioner(
            "wlan0",
            run=run,
            coordinate_wifi_direct=True,
            mode_coordinator=self.mode_coordinator,
            wpa_client_path=self.wpa_client_path,
            sleep=lambda _seconds: None,
        )
        provisioner.submit("Office", "password123")
        self._wait_for_completion(provisioner)

        self.assertEqual(provisioner.status()["state"], "CONNECTED")
        nmcli_command, nmcli_input = next(
            call for call in captured if "--ask" in call[0]
        )
        self.assertNotIn("password123", nmcli_command)
        self.assertEqual(nmcli_input, "password123\n")

    def test_submit_returns_before_grace_allows_the_lan_transition(self) -> None:
        grace_entered = threading.Event()
        release_grace = threading.Event()
        commands = []

        def sleep(seconds):
            if seconds == WIFI_PROVISION_RESPONSE_GRACE_SECONDS:
                grace_entered.set()
                release_grace.wait(timeout=2)

        def run(command, **kwargs):
            commands.append(command)
            return subprocess.CompletedProcess(command, 0, "", "")

        provisioner = WifiProvisioner(
            "wlan0",
            run=run,
            coordinate_wifi_direct=True,
            mode_coordinator=self.mode_coordinator,
            wpa_client_path=self.wpa_client_path,
            sleep=sleep,
        )

        accepted = provisioner.submit("Office", "password123")

        self.assertEqual(accepted["state"], "CONNECTING")
        self.assertTrue(grace_entered.wait(timeout=1))
        self.assertEqual(self.mode_coordinator.requests, [])
        self.assertEqual(commands, [])

        release_grace.set()
        self._wait_for_completion(provisioner)
        self.assertEqual(provisioner.status()["state"], "CONNECTED")
        self.assertEqual(
            self.mode_coordinator.requests,
            [WIFI_MODE_LAN, WIFI_MODE_LAN],
        )

    def test_direct_request_is_rejected_while_provisioning_and_success_commits_lan(self) -> None:
        rejected = []
        provisioner = None

        def run(command, **kwargs):
            if "--ask" in command:
                try:
                    provisioner.request_direct_mode()
                except RuntimeError as error:
                    rejected.append(str(error))
            return subprocess.CompletedProcess(command, 0, "", "")

        provisioner = WifiProvisioner(
            "wlan0",
            run=run,
            coordinate_wifi_direct=True,
            mode_coordinator=self.mode_coordinator,
            wpa_client_path=self.wpa_client_path,
            sleep=lambda _seconds: None,
        )
        provisioner.submit("Office", "password123")
        self._wait_for_completion(provisioner)

        self.assertEqual(provisioner.status()["state"], "CONNECTED")
        self.assertEqual(rejected, ["Wi-Fi provisioning is in progress"])
        self.assertEqual(
            self.mode_coordinator.requests,
            [WIFI_MODE_LAN, WIFI_MODE_LAN],
        )

    def test_failed_provisioning_restores_previous_profile_and_commits_lan(self) -> None:
        managed_uuid = "11111111-2222-3333-4444-555555555555"
        commands = []

        def run(command, **kwargs):
            commands.append(command)
            if "UUID,TYPE,DEVICE" in command:
                return subprocess.CompletedProcess(
                    command, 0,
                    managed_uuid + ":802-11-wireless:wlan0\n", "",
                )
            if "--ask" in command:
                return subprocess.CompletedProcess(command, 4, "", "Authentication failed")
            return subprocess.CompletedProcess(command, 0, "", "")

        provisioner = WifiProvisioner(
            "wlan0",
            run=run,
            coordinate_wifi_direct=True,
            mode_coordinator=self.mode_coordinator,
            wpa_client_path=self.wpa_client_path,
            sleep=lambda _seconds: None,
        )
        provisioner.submit("Office", "password123")
        self._wait_for_completion(provisioner)

        self.assertEqual(provisioner.status()["state"], "FAILED")
        self.assertIn(
            [
                "/usr/bin/nmcli", "--wait", "45", "connection", "up", "uuid",
                managed_uuid, "ifname", "wlan0",
            ],
            commands,
        )
        self.assertEqual(
            self.mode_coordinator.requests,
            [WIFI_MODE_LAN, WIFI_MODE_LAN],
        )
        self.assertFalse(
            (self.wpa_client_path.parent / WIFI_DIRECT_RESUME_FILENAME).exists()
        )

    def test_provisioning_pauses_p2p_and_keeps_it_paused_after_success(self) -> None:
        commands = []
        delays = []

        def run(command, **kwargs):
            commands.append(command)
            return subprocess.CompletedProcess(command, 0, "", "")

        provisioner = WifiProvisioner(
            "wlan0",
            run=run,
            coordinate_wifi_direct=True,
            mode_coordinator=self.mode_coordinator,
            wpa_client_path=self.wpa_client_path,
            sleep=delays.append,
        )
        provisioner.submit("Office", "password123")
        self._wait_for_completion(provisioner)

        self.assertEqual(provisioner.status()["state"], "CONNECTED")
        self.assertTrue(self.wpa_client_path.is_dir())
        self.assertEqual(self.wpa_client_path.stat().st_mode & 0o777, 0o700)
        self.assertEqual(
            [command[-2:] for command in commands if command[0] == "/usr/sbin/wpa_cli"],
            [["wlan0", "abort_scan"]],
        )
        self.assertEqual(
            self.mode_coordinator.requests,
            [WIFI_MODE_LAN, WIFI_MODE_LAN],
        )
        self.assertFalse(self.mode_coordinator.busy)
        self.assertTrue(any("rescan" in command for command in commands))
        self.assertEqual(
            delays,
            [WIFI_PROVISION_RESPONSE_GRACE_SECONDS, 1.0],
        )
        self.assertFalse(
            (self.wpa_client_path.parent / WIFI_DIRECT_RESUME_FILENAME).exists()
        )

    def test_provisioning_restores_p2p_after_nmcli_failure(self) -> None:
        commands = []

        def run(command, **kwargs):
            commands.append(command)
            returncode = 10 if command[0] == "/usr/bin/nmcli" else 0
            return subprocess.CompletedProcess(command, returncode, "", "")

        provisioner = WifiProvisioner(
            "wlan0",
            run=run,
            coordinate_wifi_direct=True,
            mode_coordinator=self.mode_coordinator,
            wpa_client_path=self.wpa_client_path,
            sleep=lambda _seconds: None,
        )
        provisioner.submit("Office", "password123")
        self._wait_for_completion(provisioner)

        self.assertEqual(provisioner.status()["state"], "FAILED")
        self.assertEqual(commands[-1][0], "/usr/bin/nmcli")
        self.assertTrue(
            (self.wpa_client_path.parent / WIFI_DIRECT_RESUME_FILENAME).exists()
        )
        self.assertEqual(
            self.mode_coordinator.requests,
            [WIFI_MODE_LAN, WIFI_MODE_DIRECT],
        )

    def test_provisioning_requests_p2p_resume_after_nmcli_timeout(self) -> None:
        def run(command, **kwargs):
            if command[0] == "/usr/bin/nmcli":
                raise subprocess.TimeoutExpired(command, 40)
            return subprocess.CompletedProcess(command, 0, "", "")

        provisioner = WifiProvisioner(
            "wlan0",
            run=run,
            coordinate_wifi_direct=True,
            mode_coordinator=self.mode_coordinator,
            wpa_client_path=self.wpa_client_path,
            sleep=lambda _seconds: None,
        )
        provisioner.submit("Office", "password123")
        self._wait_for_completion(provisioner)

        self.assertEqual(provisioner.status()["state"], "FAILED")
        self.assertEqual(provisioner.status()["message"], "Wi-Fi connection timed out")
        self.assertTrue(
            (self.wpa_client_path.parent / WIFI_DIRECT_RESUME_FILENAME).exists()
        )
        self.assertEqual(
            self.mode_coordinator.requests,
            [WIFI_MODE_LAN, WIFI_MODE_DIRECT],
        )

    def test_p2p_coordination_failure_prevents_misleading_wifi_attempt(self) -> None:
        commands = []
        self.mode_coordinator.ready = False

        def run(command, **kwargs):
            commands.append(command)
            return subprocess.CompletedProcess(command, 0, "", "")

        provisioner = WifiProvisioner(
            "wlan0",
            run=run,
            coordinate_wifi_direct=True,
            mode_coordinator=self.mode_coordinator,
            wpa_client_path=self.wpa_client_path,
            sleep=lambda _seconds: None,
        )
        provisioner.submit("Office", "password123")
        self._wait_for_completion(provisioner)

        self.assertEqual(provisioner.status()["state"], "FAILED")
        self.assertEqual(
            provisioner.status()["message"],
            "Wi-Fi mode transition timed out",
        )
        self.assertFalse(any("--ask" in command for command in commands))
        self.assertEqual(
            self.mode_coordinator.requests,
            [WIFI_MODE_LAN, WIFI_MODE_DIRECT],
        )

    def test_failed_restore_from_direct_explicitly_returns_to_direct_mode(self) -> None:
        managed_uuid = "11111111-2222-3333-4444-555555555555"
        self.mode_coordinator._suspended_profile = managed_uuid

        def run(command, **kwargs):
            if "--ask" in command:
                return subprocess.CompletedProcess(command, 4, "", "Authentication failed")
            if command[:6] == [
                "/usr/bin/nmcli", "--wait", "45", "connection", "up", "uuid",
            ]:
                return subprocess.CompletedProcess(command, 4, "", "restore failed")
            return subprocess.CompletedProcess(command, 0, "", "")

        provisioner = WifiProvisioner(
            "wlan0",
            run=run,
            coordinate_wifi_direct=True,
            mode_coordinator=self.mode_coordinator,
            wpa_client_path=self.wpa_client_path,
            sleep=lambda _seconds: None,
        )
        provisioner.submit("Office", "password123")
        self._wait_for_completion(provisioner)

        self.assertEqual(provisioner.status()["state"], "FAILED")
        self.assertEqual(
            self.mode_coordinator.requests,
            [WIFI_MODE_LAN, WIFI_MODE_DIRECT],
        )
        self.assertTrue(
            (self.wpa_client_path.parent / WIFI_DIRECT_RESUME_FILENAME).exists()
        )

    def test_wifi_direct_disabled_does_not_run_p2p_coordination(self) -> None:
        commands = []

        def run(command, **kwargs):
            commands.append(command)
            return subprocess.CompletedProcess(command, 0, "", "")

        provisioner = WifiProvisioner(
            "wlan0",
            run=run,
            coordinate_wifi_direct=False,
            wpa_client_path=self.wpa_client_path,
        )
        provisioner.submit("Office", "password123")
        self._wait_for_completion(provisioner)

        self.assertFalse(any(command[0] == "/usr/sbin/wpa_cli" for command in commands))
        self.assertEqual(sum("--ask" in command for command in commands), 1)
        self.assertFalse(
            (self.wpa_client_path.parent / WIFI_DIRECT_RESUME_FILENAME).exists()
        )

    def test_transient_ssid_miss_rescans_and_retries_once(self) -> None:
        connect_attempts = 0
        commands = []

        def run(command, **kwargs):
            nonlocal connect_attempts
            commands.append(command)
            if "--ask" in command:
                connect_attempts += 1
                if connect_attempts == 1:
                    return subprocess.CompletedProcess(
                        command,
                        10,
                        "",
                        "Error: No network with SSID 'Office' found.",
                    )
            return subprocess.CompletedProcess(command, 0, "OK\n", "")

        delays = []
        provisioner = WifiProvisioner(
            "wlan0",
            run=run,
            coordinate_wifi_direct=True,
            mode_coordinator=self.mode_coordinator,
            wpa_client_path=self.wpa_client_path,
            sleep=delays.append,
        )
        provisioner.submit("Office", "password123")
        self._wait_for_completion(provisioner)

        self.assertEqual(provisioner.status()["state"], "CONNECTED")
        self.assertEqual(connect_attempts, 2)
        self.assertEqual(
            sum(
                any("rescan" in argument for argument in command)
                for command in commands
            ),
            4,
        )
        self.assertEqual(
            delays,
            [WIFI_PROVISION_RESPONSE_GRACE_SECONDS, 1.0, 1.0],
        )

    def test_authentication_failure_is_not_retried_and_redacts_password(self) -> None:
        connect_attempts = 0

        def run(command, **kwargs):
            nonlocal connect_attempts
            if "--ask" in command:
                connect_attempts += 1
                return subprocess.CompletedProcess(
                    command,
                    4,
                    "",
                    "Authentication failed for password password123",
                )
            return subprocess.CompletedProcess(command, 0, "OK\n", "")

        provisioner = WifiProvisioner(
            "wlan0",
            run=run,
            coordinate_wifi_direct=True,
            mode_coordinator=self.mode_coordinator,
            wpa_client_path=self.wpa_client_path,
            sleep=lambda _seconds: None,
        )
        provisioner.submit("Office", "password123")
        self._wait_for_completion(provisioner)

        self.assertEqual(provisioner.status()["state"], "FAILED")
        self.assertEqual(connect_attempts, 1)
        self.assertNotIn("password123", provisioner.status()["message"])
        self.assertEqual(
            provisioner.status()["message"],
            "Wi-Fi authentication failed; check the password",
        )

    def _wait_for_completion(self, provisioner: WifiProvisioner) -> None:
        deadline = time.monotonic() + 2
        while time.monotonic() < deadline and provisioner.status()["state"] == "CONNECTING":
            time.sleep(0.01)
        self.assertNotEqual(provisioner.status()["state"], "CONNECTING")


if __name__ == "__main__":
    unittest.main()
