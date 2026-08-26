from __future__ import annotations

import fcntl
import hashlib
import json
import os
import stat
import subprocess
import threading
import time
import uuid
from contextlib import contextmanager
from dataclasses import dataclass
from pathlib import Path
from typing import Dict, Mapping, Optional, TextIO, Tuple


WPA_CLI_PATH = "/usr/sbin/wpa_cli"
WPA_CONTROL_PATH = "/run/wpa_supplicant"
WPA_CLIENT_PATH = Path("/run/jetson-control/wpa-provision")
WIFI_RADIO_LOCK_FILENAME = "wifi-radio.lock"
WIFI_DIRECT_RESUME_FILENAME = "wifi-direct-resume"
WIFI_MODE_REQUEST_FILENAME = "wifi-mode-request.json"
WIFI_MODE_ACK_FILENAME = "wifi-mode-ack.json"
WIFI_SUSPENDED_PROFILE_FILENAME = "wifi-suspended-profile.json"
WIFI_PROVISIONING_FILENAME = "wifi-provisioning.json"
WIFI_MODE_LOCK_FILENAME = "wifi-mode-state.lock"
WIFI_MODE_DIRECT = "DIRECT"
WIFI_MODE_LAN = "LAN"
APP_WIFI_PROFILE_PREFIX = "jetson-app-"
RADIO_SETTLE_SECONDS = 1.0
WIFI_PROVISION_RESPONSE_GRACE_SECONDS = 0.75
# A P2P activation already in flight can hold the shared radio lock for close
# to a minute. LAN handoff waits long enough for that owner to finish cleanly.
WIFI_MODE_TRANSITION_TIMEOUT_SECONDS = 90.0
WIFI_CONNECT_WAIT_SECONDS = 60
WIFI_CONNECT_TIMEOUT_SECONDS = 70


@dataclass(frozen=True)
class WifiModeRequest:
    request_id: str
    mode: str


class WifiModeCoordinator:
    """Atomic cross-process desired-mode and transition acknowledgement store."""

    def __init__(
        self,
        runtime_path: Path,
        sleep=time.sleep,
        monotonic=time.monotonic,
    ) -> None:
        self.runtime_path = Path(runtime_path)
        self.request_path = self.runtime_path / WIFI_MODE_REQUEST_FILENAME
        self.ack_path = self.runtime_path / WIFI_MODE_ACK_FILENAME
        self.suspended_profile_path = (
            self.runtime_path / WIFI_SUSPENDED_PROFILE_FILENAME
        )
        self.provisioning_path = self.runtime_path / WIFI_PROVISIONING_FILENAME
        self.state_lock_path = self.runtime_path / WIFI_MODE_LOCK_FILENAME
        self._sleep = sleep
        self._monotonic = monotonic
        self._owner_uid = os.geteuid()

    def request(self, mode: str) -> WifiModeRequest:
        if mode not in {WIFI_MODE_DIRECT, WIFI_MODE_LAN}:
            raise ValueError("Wi-Fi mode must be DIRECT or LAN")
        with self._state_guard():
            return self._request_unlocked(mode)

    def request_direct(self) -> WifiModeRequest:
        with self._state_guard():
            if self._active_provisioning_unlocked() is not None:
                raise RuntimeError("Wi-Fi provisioning is in progress")
            return self._request_unlocked(WIFI_MODE_DIRECT)

    def begin_provisioning(self) -> str:
        with self._state_guard():
            if self._active_provisioning_unlocked() is not None:
                raise RuntimeError("A Wi-Fi provisioning request is already running")
            token = uuid.uuid4().hex
            process_start = self._process_start_time(os.getpid())
            if process_start is None:
                raise RuntimeError("Cannot identify the Wi-Fi provisioning owner")
            self._atomic_write(
                self.provisioning_path,
                {
                    "version": 1,
                    "token": token,
                    "pid": os.getpid(),
                    "processStartTime": process_start,
                    "createdAtMonotonicSeconds": self._monotonic(),
                },
            )
            return token

    def finish_provisioning(self, token: str) -> None:
        with self._state_guard():
            value = self._read_object(self.provisioning_path)
            if value is None or value.get("token") != token:
                return
            try:
                self.provisioning_path.unlink()
            except OSError:
                pass

    def provisioning_active(self) -> bool:
        with self._state_guard():
            return self._active_provisioning_unlocked() is not None

    def _active_provisioning_unlocked(self) -> Optional[Mapping[str, object]]:
        value = self._read_object(self.provisioning_path)
        if value is None:
            return None
        try:
            pid = int(value.get("pid"))
            process_start = str(value.get("processStartTime"))
            created = float(value.get("createdAtMonotonicSeconds"))
            token = str(value.get("token"))
        except (TypeError, ValueError):
            pid = -1
            process_start = ""
            created = -1.0
            token = ""
        stale = (
            value.get("version") != 1
            or not re_fullmatch_hex_id(token)
            or pid <= 0
            or self._process_start_time(pid) != process_start
            or created < 0
            or self._monotonic() < created
        )
        if not stale:
            return value
        try:
            self.provisioning_path.unlink()
        except OSError:
            pass
        return None

    @staticmethod
    def _process_start_time(pid: int) -> Optional[str]:
        try:
            value = Path("/proc/{}/stat".format(pid)).read_text(encoding="utf-8")
            fields = value[value.rfind(")") + 2 :].split()
            return fields[19]
        except (IndexError, OSError):
            return None

    def _request_unlocked(self, mode: str) -> WifiModeRequest:
        request = WifiModeRequest(uuid.uuid4().hex, mode)
        self._atomic_write(
            self.request_path,
            {
                "version": 1,
                "requestId": request.request_id,
                "mode": request.mode,
                "requestedAtEpochSeconds": int(time.time()),
            },
        )
        return request

    def current_request(self) -> Optional[WifiModeRequest]:
        value = self._read_object(self.request_path)
        if value is None or value.get("version") != 1:
            return None
        request_id = value.get("requestId")
        mode = value.get("mode")
        if (
            not isinstance(request_id, str)
            or not re_fullmatch_hex_id(request_id)
            or mode not in {WIFI_MODE_DIRECT, WIFI_MODE_LAN}
        ):
            return None
        return WifiModeRequest(request_id, str(mode))

    def is_current(self, request: WifiModeRequest) -> bool:
        return self.current_request() == request

    def acknowledge(
        self,
        request: WifiModeRequest,
        ready: bool,
        message: str,
    ) -> None:
        self._atomic_write(
            self.ack_path,
            {
                "version": 1,
                "requestId": request.request_id,
                "mode": request.mode,
                "state": "READY" if ready else "FAILED",
                "message": str(message)[:240],
                "updatedAtEpochSeconds": int(time.time()),
            },
        )

    def wait_for_ready(
        self,
        request: WifiModeRequest,
        timeout: float = WIFI_MODE_TRANSITION_TIMEOUT_SECONDS,
    ) -> Tuple[bool, str]:
        deadline = self._monotonic() + timeout
        while self._monotonic() < deadline:
            value = self._read_object(self.ack_path)
            if (
                value is not None
                and value.get("version") == 1
                and value.get("requestId") == request.request_id
                and value.get("mode") == request.mode
            ):
                message = str(value.get("message") or "")[:240]
                if value.get("state") == "READY":
                    return True, message
                if value.get("state") == "FAILED":
                    return False, message
            self._sleep(0.1)
        return False, "Wi-Fi mode transition timed out"

    def remember_suspended_profile(self, profile_uuid: str) -> None:
        try:
            normalized = str(uuid.UUID(profile_uuid))
        except (ValueError, AttributeError) as error:
            raise ValueError("Managed Wi-Fi profile UUID is invalid") from error
        self._atomic_write(
            self.suspended_profile_path,
            {"version": 1, "uuid": normalized},
        )

    def suspended_profile(self) -> Optional[str]:
        value = self._read_object(self.suspended_profile_path)
        if value is None or value.get("version") != 1:
            return None
        try:
            return str(uuid.UUID(str(value.get("uuid"))))
        except (ValueError, AttributeError):
            return None

    def clear_suspended_profile(self) -> None:
        try:
            self.suspended_profile_path.unlink()
        except OSError:
            pass

    def _read_object(self, path: Path) -> Optional[Mapping[str, object]]:
        descriptor: Optional[int] = None
        try:
            metadata = path.lstat()
            if (
                not stat.S_ISREG(metadata.st_mode)
                or metadata.st_uid != self._owner_uid
                or metadata.st_mode & (stat.S_IWGRP | stat.S_IWOTH)
            ):
                return None
            descriptor = os.open(path, os.O_RDONLY | getattr(os, "O_NOFOLLOW", 0))
            opened = os.fstat(descriptor)
            if opened.st_ino != metadata.st_ino or opened.st_dev != metadata.st_dev:
                return None
            with os.fdopen(descriptor, "r", encoding="utf-8") as source:
                descriptor = None
                value = json.load(source)
            return value if isinstance(value, dict) else None
        except (FileNotFoundError, json.JSONDecodeError, OSError):
            return None
        finally:
            if descriptor is not None:
                os.close(descriptor)

    def _atomic_write(self, path: Path, value: Mapping[str, object]) -> None:
        self.runtime_path.mkdir(parents=True, exist_ok=True, mode=0o700)
        temporary = path.with_name(
            ".{}.{}.{}.tmp".format(path.name, os.getpid(), uuid.uuid4().hex)
        )
        descriptor: Optional[int] = None
        try:
            descriptor = os.open(
                temporary,
                os.O_WRONLY
                | os.O_CREAT
                | os.O_EXCL
                | getattr(os, "O_NOFOLLOW", 0),
                0o600,
            )
            with os.fdopen(descriptor, "w", encoding="utf-8") as output:
                descriptor = None
                json.dump(value, output, separators=(",", ":"), sort_keys=True)
                output.write("\n")
                output.flush()
                os.fsync(output.fileno())
            os.replace(str(temporary), str(path))
        finally:
            if descriptor is not None:
                os.close(descriptor)
            try:
                temporary.unlink()
            except OSError:
                pass

    @contextmanager
    def _state_guard(self):
        self.runtime_path.mkdir(parents=True, exist_ok=True, mode=0o700)
        descriptor = os.open(
            self.state_lock_path,
            os.O_RDWR | os.O_CREAT | getattr(os, "O_NOFOLLOW", 0),
            0o600,
        )
        handle = os.fdopen(descriptor, "a+", encoding="utf-8")
        try:
            metadata = os.fstat(handle.fileno())
            if (
                not stat.S_ISREG(metadata.st_mode)
                or metadata.st_uid != self._owner_uid
                or metadata.st_mode & (stat.S_IWGRP | stat.S_IWOTH)
            ):
                raise OSError("Wi-Fi mode lock is unsafe")
            fcntl.flock(handle.fileno(), fcntl.LOCK_EX)
            yield
        finally:
            try:
                fcntl.flock(handle.fileno(), fcntl.LOCK_UN)
            finally:
                handle.close()


def re_fullmatch_hex_id(value: str) -> bool:
    return len(value) == 32 and all(character in "0123456789abcdef" for character in value)


def validate_wifi_credentials(ssid: str, password: str) -> Tuple[str, str]:
    if "\x00" in ssid or "\x00" in password:
        raise ValueError("Wi-Fi credentials cannot contain a null byte")
    ssid_bytes = ssid.encode("utf-8")
    password_bytes = password.encode("utf-8")
    if not 1 <= len(ssid_bytes) <= 32:
        raise ValueError("SSID must contain 1 to 32 UTF-8 bytes")
    if password_bytes and not 8 <= len(password_bytes) <= 63:
        raise ValueError("Wi-Fi password must be empty or contain 8 to 63 UTF-8 bytes")
    return ssid, password


def decode_wifi_payload(payload: bytes) -> Tuple[str, str, bool]:
    if len(payload) < 4:
        raise ValueError("Wi-Fi payload is too short")
    version, flags, ssid_length, password_length = payload[:4]
    if version != 1:
        raise ValueError("Unsupported Wi-Fi payload version")
    if flags & ~0x01:
        raise ValueError("Unsupported Wi-Fi payload flags")
    if len(payload) != 4 + ssid_length + password_length:
        raise ValueError("Wi-Fi payload length does not match its header")
    try:
        ssid = payload[4 : 4 + ssid_length].decode("utf-8", errors="strict")
        password = payload[4 + ssid_length :].decode("utf-8", errors="strict")
    except UnicodeDecodeError as error:
        raise ValueError("Wi-Fi payload is not valid UTF-8") from error
    ssid, password = validate_wifi_credentials(ssid, password)
    return ssid, password, bool(flags & 0x01)


class WifiProvisioner:
    def __init__(
        self,
        interface: str,
        run=subprocess.run,
        coordinate_wifi_direct: bool = False,
        wpa_client_path: Path = WPA_CLIENT_PATH,
        sleep=time.sleep,
        mode_coordinator: Optional[WifiModeCoordinator] = None,
    ) -> None:
        self.interface = interface
        self._run = run
        self._coordinate_wifi_direct = coordinate_wifi_direct
        self._wpa_client_path = Path(wpa_client_path)
        self._runtime_path = self._wpa_client_path.parent
        self._sleep = sleep
        self._wifi_mode = mode_coordinator or WifiModeCoordinator(self._runtime_path)
        self._lock = threading.Lock()
        self._state_lock = threading.Lock()
        self._state: Dict[str, object] = {
            "state": "IDLE",
            "ssid": None,
            "message": None,
        }

    def submit(self, ssid: str, password: str, hidden: bool = False) -> Dict[str, object]:
        ssid, password = validate_wifi_credentials(ssid, password)
        if not self._lock.acquire(blocking=False):
            raise RuntimeError("A Wi-Fi provisioning request is already running")

        provisioning_token: Optional[str] = None
        try:
            if self._coordinate_wifi_direct:
                provisioning_token = self._wifi_mode.begin_provisioning()
        except Exception:
            self._lock.release()
            raise

        self._set_state("CONNECTING", ssid, None)
        worker = threading.Thread(
            target=self._connect,
            args=(ssid, password, hidden, provisioning_token),
            name="wifi-provision",
            daemon=True,
        )
        try:
            worker.start()
        except Exception:
            if provisioning_token is not None:
                self._wifi_mode.finish_provisioning(provisioning_token)
            self._lock.release()
            raise
        return {"accepted": True, "state": "CONNECTING", "ssid": ssid}

    def status(self) -> Dict[str, object]:
        with self._state_lock:
            return dict(self._state)

    def request_direct_mode(self) -> Dict[str, object]:
        if not self._coordinate_wifi_direct:
            raise RuntimeError("Wi-Fi Direct is disabled")
        request = self._wifi_mode.request_direct()
        return {
            "accepted": True,
            "state": "DIRECT_REQUESTED",
            "requestId": request.request_id,
        }

    def _set_state(self, state: str, ssid: Optional[str], message: Optional[str]) -> None:
        with self._state_lock:
            self._state = {"state": state, "ssid": ssid, "message": message}

    def _connect(
        self,
        ssid: str,
        password: str,
        hidden: bool,
        provisioning_token: Optional[str],
    ) -> None:
        if self._coordinate_wifi_direct:
            # A Wi-Fi provisioning request can arrive over the active Direct
            # HTTP path. Give FastAPI enough time to flush its accepted response
            # before the worker asks the daemon to tear that path down.
            self._sleep(WIFI_PROVISION_RESPONSE_GRACE_SECONDS)
        profile_name = self._app_profile_name(ssid, uuid.uuid4().hex[:8])
        command = [
            "/usr/bin/nmcli",
            "--wait",
            str(WIFI_CONNECT_WAIT_SECONDS),
            "--ask",
            "device",
            "wifi",
            "connect",
            ssid,
            "ifname",
            self.interface,
            "name",
            profile_name,
        ]
        if hidden:
            command.extend(("hidden", "yes"))

        radio_lock: Optional[TextIO] = None
        mode_request: Optional[WifiModeRequest] = None
        previous_active_profile: Optional[str] = None
        previous_profile: Optional[str] = None
        lan_transition_ready = False
        result_state = "FAILED"
        result_message = "NetworkManager command is unavailable"
        try:
            if self._coordinate_wifi_direct:
                # The Wi-Fi Direct daemon owns its group, DHCP child, and
                # discovery state. Ask it to tear those down and acknowledge the
                # LAN handoff before taking the exclusive radio lock. Reversing
                # that order would deadlock the daemon behind this provisioner.
                previous_active_profile = self._active_managed_wifi_profile()
                previous_profile = (
                    previous_active_profile
                    or self._wifi_mode.suspended_profile()
                )
                mode_request = self._wifi_mode.request(WIFI_MODE_LAN)
                ready, detail = self._wifi_mode.wait_for_ready(mode_request)
                if not ready:
                    result_message = detail or "Wi-Fi Direct did not release the radio"
                    return
                lan_transition_ready = True
                radio_lock = self._acquire_wifi_radio()
                if radio_lock is None:
                    result_message = "Wi-Fi radio coordination is unavailable"
                    return
                # The daemon has already stopped P2P. Abort only a residual
                # driver scan while the exclusive lock prevents new discovery.
                self._run_p2p_command("abort_scan")
                self._sleep(RADIO_SETTLE_SECONDS)

            # A per-attempt app-owned name forces nmcli to create a fresh
            # profile, so --ask consumes the stdin password even when a saved
            # profile for the same SSID exists. The prior profile remains
            # available for rollback and the password never appears in argv.
            self._delete_app_profile(profile_name)

            result: Optional[subprocess.CompletedProcess] = None
            for attempt in range(2):
                self._request_station_scan(ssid)
                try:
                    result = self._run(
                        command,
                        check=False,
                        capture_output=True,
                        text=True,
                        input=(password + "\n") if password else None,
                        timeout=WIFI_CONNECT_TIMEOUT_SECONDS,
                        env=self._nmcli_environment(),
                    )
                except subprocess.TimeoutExpired:
                    result_message = "Wi-Fi connection timed out"
                    break
                except OSError:
                    result_message = "NetworkManager command is unavailable"
                    break

                if result.returncode == 0:
                    result_state = "CONNECTED"
                    result_message = "Wi-Fi connection completed"
                    self._prioritize_app_profile(profile_name)
                    break

                result_message = self._connection_failure_message(result, password)
                if attempt == 0 and self._is_transient_scan_failure(result):
                    self._sleep(RADIO_SETTLE_SECONDS)
                    continue
                break

            if result_state != "CONNECTED":
                self._delete_app_profile(profile_name)
        finally:
            try:
                if self._coordinate_wifi_direct and mode_request is not None:
                    current_request = self._wifi_mode.current_request()
                    if result_state == "CONNECTED":
                        # Commit a fresh LAN generation while the provisioning busy
                        # marker still rejects stale/automatic Direct requests.
                        self._wifi_mode.request(WIFI_MODE_LAN)
                        self._wifi_mode.clear_suspended_profile()
                    elif current_request == mode_request and lan_transition_ready:
                        restored = bool(
                            previous_profile
                            and (
                                (
                                    radio_lock is None
                                    and previous_active_profile == previous_profile
                                )
                                or (
                                    radio_lock is not None
                                    and self._restore_managed_wifi_profile(previous_profile)
                                )
                            )
                        )
                        if restored:
                            self._wifi_mode.request(WIFI_MODE_LAN)
                            self._wifi_mode.clear_suspended_profile()
                        else:
                            self._wifi_mode.request(WIFI_MODE_DIRECT)
                            self._request_wifi_direct_resume()
                    elif (
                        current_request == mode_request
                        and previous_active_profile is None
                    ):
                        # The daemon did not ACK the teardown. Preserve an existing
                        # LAN connection, but return an already-suspended Direct
                        # session to discovery instead of leaving it stranded.
                        self._wifi_mode.request(WIFI_MODE_DIRECT)
                        self._request_wifi_direct_resume()
                    elif current_request == mode_request:
                        self._wifi_mode.request(WIFI_MODE_LAN)
                    elif (
                        current_request is not None
                        and current_request.mode == WIFI_MODE_DIRECT
                    ):
                        self._request_wifi_direct_resume()
            except (OSError, RuntimeError, ValueError) as error:
                result_state = "FAILED"
                result_message = "Wi-Fi mode recovery failed: {}".format(error)
            finally:
                if self._coordinate_wifi_direct:
                    self._release_wifi_radio(radio_lock)
                    if provisioning_token is not None:
                        try:
                            self._wifi_mode.finish_provisioning(provisioning_token)
                        except OSError:
                            pass
                password = ""
                self._set_state(result_state, ssid, result_message)
                self._lock.release()

    @staticmethod
    def _app_profile_name(ssid: str, attempt_id: str) -> str:
        digest = hashlib.sha256(ssid.encode("utf-8")).hexdigest()[:16]
        return "{}{}-{}".format(APP_WIFI_PROFILE_PREFIX, digest, attempt_id)

    def _active_managed_wifi_profile(self) -> Optional[str]:
        try:
            result = self._run(
                [
                    "/usr/bin/nmcli",
                    "--terse",
                    "--fields",
                    "UUID,TYPE,DEVICE",
                    "connection",
                    "show",
                    "--active",
                ],
                check=False,
                capture_output=True,
                text=True,
                timeout=10,
                env=self._nmcli_environment(),
            )
        except (OSError, subprocess.TimeoutExpired):
            return None
        for line in (result.stdout or "").splitlines():
            fields = line.split(":", 2)
            if (
                len(fields) == 3
                and fields[0]
                and fields[1] in {"802-11-wireless", "wifi"}
                and fields[2] == self.interface
            ):
                return fields[0]
        return None

    def _restore_managed_wifi_profile(self, profile_uuid: str) -> bool:
        try:
            result = self._run(
                [
                    "/usr/bin/nmcli",
                    "--wait",
                    "45",
                    "connection",
                    "up",
                    "uuid",
                    profile_uuid,
                    "ifname",
                    self.interface,
                ],
                check=False,
                capture_output=True,
                text=True,
                timeout=50,
                env=self._nmcli_environment(),
            )
            return result.returncode == 0
        except (OSError, subprocess.TimeoutExpired):
            return False

    @staticmethod
    def _nmcli_environment() -> Dict[str, str]:
        environment = dict(os.environ)
        environment["LC_ALL"] = "C"
        return environment

    def _delete_app_profile(self, profile_name: str) -> None:
        try:
            self._run(
                [
                    "/usr/bin/nmcli",
                    "connection",
                    "delete",
                    "id",
                    profile_name,
                ],
                check=False,
                capture_output=True,
                text=True,
                timeout=10,
                env=self._nmcli_environment(),
            )
        except (OSError, subprocess.TimeoutExpired):
            pass

    def _prioritize_app_profile(self, profile_name: str) -> None:
        try:
            self._run(
                [
                    "/usr/bin/nmcli",
                    "connection",
                    "modify",
                    "id",
                    profile_name,
                    "connection.autoconnect",
                    "yes",
                    "connection.autoconnect-priority",
                    "100",
                ],
                check=False,
                capture_output=True,
                text=True,
                timeout=10,
                env=self._nmcli_environment(),
            )
        except (OSError, subprocess.TimeoutExpired):
            pass

    def _request_station_scan(self, ssid: str) -> None:
        commands = (
            [
                "/usr/bin/nmcli",
                "--wait",
                "15",
                "device",
                "wifi",
                "rescan",
                "ifname",
                self.interface,
                "ssid",
                ssid,
            ],
            [
                "/usr/bin/nmcli",
                "--wait",
                "20",
                "--terse",
                "--fields",
                "SSID",
                "device",
                "wifi",
                "list",
                "ifname",
                self.interface,
                "--rescan",
                "yes",
            ],
        )
        for command in commands:
            try:
                self._run(
                    command,
                    check=False,
                    capture_output=True,
                    text=True,
                    timeout=25,
                    env=self._nmcli_environment(),
                )
            except (OSError, subprocess.TimeoutExpired):
                # The connect operation below can still perform its own scan.
                # Its return code and diagnostic remain the authoritative result.
                continue

    @staticmethod
    def _command_detail(
        result: subprocess.CompletedProcess,
        password: str,
    ) -> str:
        raw_detail = (result.stderr or "").strip() or (result.stdout or "").strip()
        detail = " ".join(raw_detail.split())
        if password:
            detail = detail.replace(password, "[redacted]")
        return detail[:240]

    @classmethod
    def _is_transient_scan_failure(cls, result: subprocess.CompletedProcess) -> bool:
        detail = cls._command_detail(result, "").lower()
        return result.returncode == 10 or any(
            marker in detail
            for marker in (
                "no network with ssid",
                "access point was not found",
                "no suitable access point",
                "scan is not allowed",
                "scan pending",
            )
        )

    @classmethod
    def _connection_failure_message(
        cls,
        result: subprocess.CompletedProcess,
        password: str,
    ) -> str:
        detail = cls._command_detail(result, password)
        lowered = detail.lower()
        if any(
            marker in lowered
            for marker in (
                "secrets were required",
                "invalid secret",
                "no secrets",
                "wrong password",
                "authentication",
            )
        ):
            return "Wi-Fi authentication failed; check the password"
        if cls._is_transient_scan_failure(result):
            return "Wi-Fi network was not found after scanning"
        if result.returncode == 3:
            return "Wi-Fi connection timed out"
        if result.returncode == 8:
            return "NetworkManager is unavailable"
        if detail:
            return "NetworkManager rejected the connection: {}".format(detail)
        return "NetworkManager rejected the connection (code {})".format(
            result.returncode,
        )

    def _acquire_wifi_radio(self) -> Optional[TextIO]:
        handle: Optional[TextIO] = None
        try:
            self._runtime_path.mkdir(parents=True, exist_ok=True, mode=0o700)
            handle = (self._runtime_path / WIFI_RADIO_LOCK_FILENAME).open(
                "a+",
                encoding="utf-8",
            )
            fcntl.flock(handle.fileno(), fcntl.LOCK_EX)
            return handle
        except OSError:
            if handle is not None:
                handle.close()
            return None

    def _release_wifi_radio(self, handle: Optional[TextIO]) -> None:
        if handle is None:
            return
        try:
            fcntl.flock(handle.fileno(), fcntl.LOCK_UN)
        finally:
            handle.close()

    def _request_wifi_direct_resume(self) -> None:
        try:
            self._runtime_path.mkdir(parents=True, exist_ok=True, mode=0o700)
            (self._runtime_path / WIFI_DIRECT_RESUME_FILENAME).touch(exist_ok=True)
        except OSError:
            pass

    def _run_p2p_command(self, *arguments: str) -> bool:
        try:
            self._wpa_client_path.mkdir(parents=True, exist_ok=True, mode=0o700)
            self._wpa_client_path.chmod(0o700)
            result = self._run(
                [
                    WPA_CLI_PATH,
                    "-p",
                    WPA_CONTROL_PATH,
                    "-s",
                    str(self._wpa_client_path),
                    "-i",
                    self.interface,
                    *arguments,
                ],
                check=False,
                capture_output=True,
                text=True,
                timeout=5,
            )
            output_lines = [
                line.strip()
                for line in (result.stdout or "").splitlines()
                if line.strip()
            ]
            return result.returncode == 0 and not (
                output_lines and output_lines[-1].startswith("FAIL")
            )
        except (OSError, subprocess.TimeoutExpired):
            return False
