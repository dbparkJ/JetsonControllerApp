from __future__ import annotations

import subprocess
import threading
from dataclasses import dataclass
from typing import Dict, Optional, Tuple


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
    def __init__(self, interface: str, run=subprocess.run) -> None:
        self.interface = interface
        self._run = run
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
        worker.start()
        return {"accepted": True, "state": "CONNECTING", "ssid": ssid}

    def status(self) -> Dict[str, object]:
        with self._state_lock:
            return dict(self._state)

    def _set_state(self, state: str, ssid: Optional[str], message: Optional[str]) -> None:
        with self._state_lock:
            self._state = {"state": state, "ssid": ssid, "message": message}

    def _connect(self, ssid: str, password: str, hidden: bool) -> None:
        command = [
            "/usr/bin/nmcli",
            "--wait",
            "35",
            "--ask",
            "device",
            "wifi",
            "connect",
            ssid,
        ]
        command.extend(("ifname", self.interface))
        if hidden:
            command.extend(("hidden", "yes"))

        try:
            result = self._run(
                command,
                check=False,
                capture_output=True,
                text=True,
                input=(password + "\n") if password else None,
                timeout=40,
            )
            if result.returncode == 0:
                self._set_state("CONNECTED", ssid, "Wi-Fi connection completed")
            else:
                self._set_state("FAILED", ssid, "NetworkManager rejected the connection")
        except subprocess.TimeoutExpired:
            self._set_state("FAILED", ssid, "Wi-Fi connection timed out")
        except OSError:
            self._set_state("FAILED", ssid, "NetworkManager command is unavailable")
        finally:
            password = ""
            self._lock.release()
