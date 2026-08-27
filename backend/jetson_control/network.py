from __future__ import annotations

import hashlib
import subprocess
import threading
import time
from typing import Dict, Optional, Tuple


SYSTEMCTL_PATH = "/usr/bin/systemctl"
NMCLI_PATH = "/usr/bin/nmcli"
WIFI_DIRECT_SERVICE = "jetson-wifi-direct.service"
WIFI_DIRECT_HANDOFF_GRACE_SECONDS = 0.75
WIFI_DIRECT_STOP_TIMEOUT_SECONDS = 20
WIFI_CONNECT_WAIT_SECONDS = 60
WIFI_CONNECT_TIMEOUT_SECONDS = 70
APP_WIFI_PROFILE_PREFIX = "jetson-app-"


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
        sleep=time.sleep,
    ) -> None:
        self.interface = interface
        self._run = run
        self._coordinate_wifi_direct = coordinate_wifi_direct
        self._sleep = sleep
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

        self._set_state("CONNECTING", ssid, None)
        worker = threading.Thread(
            target=self._connect,
            args=(ssid, password, hidden),
            name="wifi-provision",
            daemon=True,
        )
        try:
            worker.start()
        except Exception:
            self._lock.release()
            raise
        return {"accepted": True, "state": "CONNECTING", "ssid": ssid}

    def status(self) -> Dict[str, object]:
        with self._state_lock:
            return dict(self._state)

    def _set_state(self, state: str, ssid: Optional[str], message: Optional[str]) -> None:
        with self._state_lock:
            self._state = {"state": state, "ssid": ssid, "message": message}

    def _connect(self, ssid: str, password: str, hidden: bool) -> None:
        if self._coordinate_wifi_direct:
            # A request can arrive over the Direct HTTP path. Let the accepted
            # response leave the socket before releasing that radio link.
            self._sleep(WIFI_DIRECT_HANDOFF_GRACE_SECONDS)

        profile_name = self._app_profile_name(ssid)
        try:
            if self._coordinate_wifi_direct and not self._set_wifi_direct_service("stop"):
                self._set_state(
                    "FAILED",
                    ssid,
                    "Wi-Fi Direct did not release the wireless interface",
                )
                return

            # Only replace profiles created by this application. A fresh
            # profile prevents NetworkManager from silently reusing stale
            # credentials from an earlier request.
            self._run(
                [NMCLI_PATH, "connection", "delete", "id", profile_name],
                check=False,
                capture_output=True,
                text=True,
                timeout=10,
            )
            self._run(
                [NMCLI_PATH, "device", "wifi", "rescan", "ifname", self.interface],
                check=False,
                capture_output=True,
                text=True,
                timeout=15,
            )
            if password:
                result = self._connect_secured_wifi(
                    profile_name,
                    ssid,
                    password,
                    hidden,
                )
            else:
                result = self._connect_open_wifi(profile_name, ssid, hidden)
            if result.returncode == 0:
                self._set_state("CONNECTED", ssid, "Wi-Fi connection completed")
            else:
                self._delete_app_profile(profile_name)
                self._set_state("FAILED", ssid, "NetworkManager rejected the connection")
        except subprocess.TimeoutExpired:
            self._delete_app_profile(profile_name)
            self._set_state("FAILED", ssid, "Wi-Fi connection timed out")
        except OSError:
            self._delete_app_profile(profile_name)
            self._set_state("FAILED", ssid, "NetworkManager command is unavailable")
        finally:
            if self._coordinate_wifi_direct:
                self._set_wifi_direct_service("start")
            password = ""
            self._lock.release()

    def _connect_secured_wifi(
        self,
        profile_name: str,
        ssid: str,
        password: str,
        hidden: bool,
    ) -> subprocess.CompletedProcess:
        add_command = [
            NMCLI_PATH,
            "connection",
            "add",
            "type",
            "wifi",
            "ifname",
            self.interface,
            "con-name",
            profile_name,
            "ssid",
            ssid,
        ]
        if hidden:
            add_command.extend(("802-11-wireless.hidden", "yes"))

        result = self._run(
            add_command,
            check=False,
            capture_output=True,
            text=True,
            timeout=10,
        )
        if result.returncode != 0:
            return result

        result = self._run(
            [
                NMCLI_PATH,
                "connection",
                "modify",
                "id",
                profile_name,
                "802-11-wireless-security.key-mgmt",
                "wpa-psk",
            ],
            check=False,
            capture_output=True,
            text=True,
            timeout=10,
        )
        if result.returncode != 0:
            return result

        # NetworkManager 1.22 does not consume redirected stdin for --ask.
        # passwd-file explicitly reads the secret from /dev/stdin, keeping it
        # out of the process arguments and avoiding a temporary secret file.
        return self._run(
            [
                NMCLI_PATH,
                "--wait",
                str(WIFI_CONNECT_WAIT_SECONDS),
                "connection",
                "up",
                "id",
                profile_name,
                "ifname",
                self.interface,
                "passwd-file",
                "/dev/stdin",
            ],
            check=False,
            capture_output=True,
            text=True,
            input=f"802-11-wireless-security.psk:{password}\n",
            timeout=WIFI_CONNECT_TIMEOUT_SECONDS,
        )

    def _connect_open_wifi(
        self,
        profile_name: str,
        ssid: str,
        hidden: bool,
    ) -> subprocess.CompletedProcess:
        command = [
            NMCLI_PATH,
            "--wait",
            str(WIFI_CONNECT_WAIT_SECONDS),
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
        return self._run(
            command,
            check=False,
            capture_output=True,
            text=True,
            timeout=WIFI_CONNECT_TIMEOUT_SECONDS,
        )

    @staticmethod
    def _app_profile_name(ssid: str) -> str:
        fingerprint = hashlib.sha256(ssid.encode("utf-8")).hexdigest()[:12]
        return APP_WIFI_PROFILE_PREFIX + fingerprint

    def _delete_app_profile(self, profile_name: str) -> None:
        try:
            self._run(
                [NMCLI_PATH, "connection", "delete", "id", profile_name],
                check=False,
                capture_output=True,
                text=True,
                timeout=10,
            )
        except (OSError, subprocess.TimeoutExpired):
            pass

    def _set_wifi_direct_service(self, action: str) -> bool:
        try:
            result = self._run(
                [SYSTEMCTL_PATH, action, WIFI_DIRECT_SERVICE],
                check=False,
                capture_output=True,
                text=True,
                timeout=WIFI_DIRECT_STOP_TIMEOUT_SECONDS,
            )
            return result.returncode == 0
        except (OSError, subprocess.TimeoutExpired):
            return False
