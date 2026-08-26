from __future__ import annotations

import argparse
import fcntl
import ipaddress
import json
import os
import re
import signal
import subprocess
import sys
import threading
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Callable, Dict, List, Optional, Sequence, TextIO, Tuple

from .config import DeviceConfig
from .network import (
    WIFI_DIRECT_RESUME_FILENAME,
    WIFI_MODE_DIRECT,
    WIFI_MODE_LAN,
    WIFI_RADIO_LOCK_FILENAME,
    WifiModeCoordinator,
    WifiModeRequest,
)


STATUS_PATH = Path("/run/jetson-control/wifi-direct.json")
WPA_CONTROL_PATH = "/run/wpa_supplicant"
WPA_DBUS_SERVICE = "fi.w1.wpa_supplicant1"
WPA_DBUS_ROOT = "/fi/w1/wpa_supplicant1"
WPA_ROOT_INTERFACE = "fi.w1.wpa_supplicant1"
WPA_INTERFACE = "fi.w1.wpa_supplicant1.Interface"
WPA_P2P_INTERFACE = "fi.w1.wpa_supplicant1.Interface.P2PDevice"
WPA_PEER_INTERFACE = "fi.w1.wpa_supplicant1.Peer"
DBUS_PROPERTIES_INTERFACE = "org.freedesktop.DBus.Properties"
PROFILE_PREFIX = "jetson-control-p2p-"
DNSMASQ_PATH = "/usr/sbin/dnsmasq"
DISCOVERY_RETRY_INITIAL_SECONDS = 5.0
DISCOVERY_RETRY_MAX_SECONDS = 60.0
DISCOVERY_RADIO_RECOVERY_FAILURE_THRESHOLD = 3
RADIO_RESET_SETTLE_SECONDS = 1.0
RADIO_INTERFACE_WAIT_ATTEMPTS = 40
RADIO_INTERFACE_WAIT_SECONDS = 0.25
WPA_SIGNAL_REBIND_ATTEMPTS = 20
WPA_SIGNAL_REBIND_SECONDS = 0.25
RADIO_RECOVERY_FILENAME = "wifi-direct-radio-recovery.json"
MANUAL_GROUP_JOIN_GRACE_SECONDS = 20.0
MANUAL_GROUP_IDLE_SECONDS = 8.0
ACTIVATION_CANCEL_JOIN_SECONDS = 5.0


class WifiDirectError(RuntimeError):
    pass


class WifiDirectActivationCancelled(WifiDirectError):
    pass


class WifiDirectRadioRecoveryCancelled(WifiDirectError):
    pass


def p2p_device_name(value: str) -> str:
    name = value.strip()
    while len(name.encode("utf-8")) > 32:
        name = name[:-1]
    return name or "Jetson"


def normalize_mac_address(value: str) -> str:
    compact = re.sub(r"[^0-9a-fA-F]", "", value)
    if len(compact) != 12:
        raise ValueError("Wi-Fi Direct peer address is invalid")
    return ":".join(compact[index:index + 2] for index in range(0, 12, 2)).upper()


def peer_address_from_path(value: str) -> Optional[str]:
    suffix = value.rstrip("/").rsplit("/", 1)[-1]
    try:
        return normalize_mac_address(suffix)
    except ValueError:
        return None


def frequency_channel(frequency: int) -> Tuple[int, int]:
    if 2412 <= frequency <= 2472 and (frequency - 2412) % 5 == 0:
        return 81, 1 + (frequency - 2412) // 5
    if frequency == 2484:
        return 82, 14
    if 5180 <= frequency <= 5240 and (frequency - 5000) % 5 == 0:
        return 115, (frequency - 5000) // 5
    if 5260 <= frequency <= 5320 and (frequency - 5000) % 5 == 0:
        return 118, (frequency - 5000) // 5
    if 5500 <= frequency <= 5720 and (frequency - 5000) % 5 == 0:
        return 121, (frequency - 5000) // 5
    if 5745 <= frequency <= 5805 and (frequency - 5000) % 5 == 0:
        return 124, (frequency - 5000) // 5
    raise ValueError("Wi-Fi Direct frequency is not a supported 2.4/5 GHz channel")


def parse_iw_interfaces(iw_output: str) -> Dict[str, str]:
    interfaces: Dict[str, str] = {}
    current_interface: Optional[str] = None
    for raw_line in iw_output.splitlines():
        line = raw_line.strip()
        if line.startswith("Interface "):
            current_interface = line.split(None, 1)[1]
        elif line.startswith("Unnamed/non-netdev interface"):
            current_interface = None
        elif line.startswith("type ") and current_interface:
            interfaces[current_interface] = line.split(None, 1)[1]
    return interfaces


def parse_p2p_group_interfaces(iw_output: str) -> List[str]:
    return [
        interface
        for interface, interface_type in parse_iw_interfaces(iw_output).items()
        if interface_type == "P2P-GO"
    ]


def parse_station_addresses(iw_output: str) -> List[str]:
    stations: List[str] = []
    for match in re.finditer(
        r"(?mi)^\s*Station\s+([0-9a-f]{2}(?::[0-9a-f]{2}){5})\b",
        iw_output,
    ):
        address = normalize_mac_address(match.group(1))
        if address not in stations:
            stations.append(address)
    return stations


def parse_ipv4_address(ip_output: str) -> Optional[str]:
    try:
        values = json.loads(ip_output)
    except json.JSONDecodeError:
        return None
    if not isinstance(values, list):
        return None
    for interface in values:
        if not isinstance(interface, dict):
            continue
        for address in interface.get("addr_info", []):
            if isinstance(address, dict) and address.get("family") == "inet":
                local = address.get("local")
                if isinstance(local, str):
                    return local
    return None


def configured_ipv4_address(ip_output: str, configured_address: str) -> Optional[str]:
    """Return the configured owner IP only when both its address and prefix match."""
    try:
        owner = ipaddress.ip_interface(configured_address)
        values = json.loads(ip_output)
    except (ValueError, json.JSONDecodeError):
        return None
    if not isinstance(values, list):
        return None
    for interface in values:
        if not isinstance(interface, dict):
            continue
        for address in interface.get("addr_info", []):
            if not isinstance(address, dict) or address.get("family") != "inet":
                continue
            try:
                local = ipaddress.ip_address(address.get("local", ""))
                prefix_length = int(address.get("prefixlen"))
            except (TypeError, ValueError):
                continue
            if local == owner.ip and prefix_length == owner.network.prefixlen:
                return str(owner.ip)
    return None


def parse_default_route_interfaces(ip_output: str) -> List[str]:
    try:
        values = json.loads(ip_output)
    except json.JSONDecodeError:
        return []
    if not isinstance(values, list):
        return []
    interfaces: List[str] = []
    for route in values:
        if not isinstance(route, dict) or route.get("dst") != "default":
            continue
        interface = route.get("dev")
        if isinstance(interface, str) and interface and interface not in interfaces:
            interfaces.append(interface)
    return interfaces


def parse_wiphy_name(iw_interface_output: str) -> Optional[str]:
    match = re.search(r"(?m)^\s*wiphy\s+(\d+)\s*$", iw_interface_output)
    return "phy{}".format(match.group(1)) if match else None


def managed_p2p_concurrency_capability(iw_phy_output: str) -> bool:
    """Parse whether a wiphy explicitly supports managed plus P2P-GO.

    Concurrent operation is only safe when ``iw`` advertises a combination that
    explicitly allows both interface types. Missing or incomplete capability
    information is therefore treated as unsupported.
    """
    if "interface combinations are not supported" in iw_phy_output:
        return False
    marker = "valid interface combinations:"
    if marker not in iw_phy_output:
        return False

    combinations: List[str] = []
    current: List[str] = []
    for raw_line in iw_phy_output.split(marker, 1)[1].splitlines():
        line = raw_line.strip()
        if line.startswith("* #{"):
            if current:
                combinations.append(" ".join(current))
            current = [line[1:].strip()]
        elif current:
            if line.startswith("* "):
                break
            current.append(line)
    if current:
        combinations.append(" ".join(current))

    for combination in combinations:
        total_match = re.search(r"\btotal\s*<=\s*(\d+)", combination)
        if total_match is None or int(total_match.group(1)) < 2:
            continue
        constraints = re.findall(r"#\{\s*([^}]+)\s*\}\s*<=\s*(\d+)", combination)
        seen_managed = False
        seen_p2p_go = False
        valid = True
        for raw_modes, raw_limit in constraints:
            modes = {mode.strip() for mode in raw_modes.split(",")}
            seen_managed = seen_managed or "managed" in modes
            seen_p2p_go = seen_p2p_go or "P2P-GO" in modes
            requested_modes = int("managed" in modes) + int("P2P-GO" in modes)
            if requested_modes > int(raw_limit):
                valid = False
                break
        if valid and seen_managed and seen_p2p_go:
            return True
    return False


def dhcp_lease_range(configured_address: str) -> Tuple[str, str, str]:
    owner = ipaddress.ip_interface(configured_address)
    network = owner.network
    if owner.version != 4 or network.prefixlen > 30:
        raise ValueError("Wi-Fi Direct DHCP requires an IPv4 subnet of /30 or larger")

    first = network.network_address + 1
    last = network.broadcast_address - 1
    if owner.ip == first:
        first += 1
    elif owner.ip == last:
        last -= 1
    else:
        lower_size = int(owner.ip) - int(first)
        upper_size = int(last) - int(owner.ip)
        if upper_size >= lower_size:
            first = owner.ip + 1
        else:
            last = owner.ip - 1
    if first > last:
        raise ValueError("Wi-Fi Direct DHCP subnet has no address available for a peer")
    return str(first), str(last), str(network.netmask)


@dataclass(frozen=True)
class WifiDirectSettings:
    interface: str
    device_name: str
    frequency: int = 2412
    address: str = "192.168.49.1/24"

    @classmethod
    def from_device_config(cls, config: DeviceConfig) -> "WifiDirectSettings":
        return cls(
            interface=config.wifi_interface,
            device_name=p2p_device_name(config.device_name),
            frequency=config.wifi_direct_frequency,
            address=config.wifi_direct_address,
        ).validated()

    def validated(self) -> "WifiDirectSettings":
        if not re.fullmatch(r"[a-zA-Z0-9_.:-]{1,32}", self.interface):
            raise ValueError("Wi-Fi Direct interface is invalid")
        if (
            not self.device_name
            or len(self.device_name.encode("utf-8")) > 32
            or any(ord(character) < 32 or ord(character) == 127 for character in self.device_name)
        ):
            raise ValueError("Wi-Fi Direct device name must contain 1 to 32 UTF-8 bytes")
        frequency_channel(self.frequency)
        owner = ipaddress.ip_interface(self.address)
        if owner.version != 4:
            raise ValueError("Wi-Fi Direct currently requires an IPv4 address")
        dhcp_lease_range(self.address)
        return self

    @property
    def owner_ip(self) -> str:
        return str(ipaddress.ip_interface(self.address).ip)


def read_wifi_direct_status(path: Path = STATUS_PATH) -> Dict[str, object]:
    try:
        with path.open("r", encoding="utf-8") as source:
            value = json.load(source)
    except (FileNotFoundError, json.JSONDecodeError, OSError):
        return {
            "enabled": False,
            "state": "UNAVAILABLE",
            "message": "Wi-Fi Direct service has not published a status",
        }
    if not isinstance(value, dict):
        return {
            "enabled": False,
            "state": "UNAVAILABLE",
            "message": "Wi-Fi Direct status is invalid",
        }
    return value


class WifiDirectController:
    def __init__(
        self,
        settings: WifiDirectSettings,
        run: Callable[..., subprocess.CompletedProcess] = subprocess.run,
        start_process: Callable[..., subprocess.Popen] = subprocess.Popen,
        status_path: Path = STATUS_PATH,
        sleep: Callable[[float], None] = time.sleep,
        monotonic: Callable[[], float] = time.monotonic,
        mode_coordinator: Optional[WifiModeCoordinator] = None,
        radio_recovery_callback: Optional[Callable[[], None]] = None,
    ) -> None:
        self.settings = settings.validated()
        self._run_process = run
        self._start_process = start_process
        self._sleep = sleep
        self._monotonic = monotonic
        self.status_path = status_path
        self.wpa_client_path = status_path.parent / "wpa-cli"
        self.dnsmasq_lease_path = status_path.parent / "wifi-direct.leases"
        self.dnsmasq_pid_path = status_path.parent / "wifi-direct-dnsmasq.pid"
        self.wifi_radio_lock_path = status_path.parent / WIFI_RADIO_LOCK_FILENAME
        self.wifi_direct_resume_path = status_path.parent / WIFI_DIRECT_RESUME_FILENAME
        self.radio_recovery_path = status_path.parent / RADIO_RECOVERY_FILENAME
        self.wifi_mode = mode_coordinator or WifiModeCoordinator(status_path.parent)
        self._radio_recovery_callback = radio_recovery_callback
        self.management_interface: Optional[str] = None
        self.group_interface: Optional[str] = None
        self.active_profile: Optional[str] = None
        self.active_peer: Optional[str] = None
        self._suspended_wifi_profile: Optional[str] = None
        self._manual_owner_mode = False
        self._manual_address_interface: Optional[str] = None
        self._manual_peer_seen = False
        self._manual_group_empty_since: Optional[float] = None
        self._dnsmasq_process: Optional[subprocess.Popen] = None
        self._state = "UNAVAILABLE"
        self._activation_lock = threading.Lock()
        self._activation_thread: Optional[threading.Thread] = None
        self._activation_cancel = threading.Event()
        self._status_lock = threading.Lock()
        self._last_discovery = 0.0
        self._discovery_paused_for_provisioning = False
        self._discovery_paused_for_managed_wifi = False
        self._discovery_failure_count = 0
        self._next_discovery_retry = 0.0
        self._last_discovery_error: Optional[str] = None
        self._radio_recovery_request_id: Optional[str] = None
        self._handled_mode_request_id: Optional[str] = None

    def prepare(self) -> str:
        self._publish("STARTING", "Preparing NetworkManager Wi-Fi Direct")
        self.wpa_client_path.mkdir(parents=True, exist_ok=True, mode=0o700)
        os.chmod(str(self.wpa_client_path), 0o700)
        self._wait_for_wpa_supplicant()
        self._configure_p2p_identity()
        self.management_interface = self._wait_for_management_interface()

        mode_request = self.wifi_mode.current_request()
        if mode_request is not None:
            transitioned = self._process_mode_request(mode_request)
            if transitioned is not None:
                return transitioned
            self._publish("PAUSED", "Waiting to switch the Wi-Fi radio mode")
            return "PAUSED"

        active_profile = self._active_managed_profile()
        group_interface = self._first_group_interface()
        if active_profile and group_interface:
            self.active_profile = active_profile
            self.group_interface = group_interface
            address = self._interface_address(group_interface)
            if address:
                self._publish(
                    "READY",
                    "Wi-Fi Direct group is connected",
                    address=address,
                )
                return "READY"
            self._delete_active_profile()
            self.group_interface = None

        self._cleanup_stale_groups()
        return self._resume_discovery("Waiting for the Android connection request")

    def refresh_discovery(self) -> bool:
        now = self._monotonic()
        if (
            self._discovery_failure_count > 0
            and now < self._next_discovery_retry
        ):
            return False

        radio_lock = self._try_wifi_radio_lock()
        if radio_lock is None:
            self._discovery_paused_for_provisioning = True
            return False
        try:
            return self._refresh_discovery_locked()
        finally:
            self._release_wifi_radio_lock(radio_lock)

    def _refresh_discovery_locked(self) -> bool:
        # Some drivers reject a new p2p_find while the previous timed find is
        # still active. Stop it explicitly so periodic refreshes and service
        # restarts cannot turn an already-running discovery into a fatal FAIL.
        self._discovery_paused_for_provisioning = False
        try:
            self.wifi_direct_resume_path.unlink()
        except OSError:
            pass
        try:
            self._wpa(self.settings.interface, "p2p_stop_find", allow_failure=True)
            try:
                # Omitting the optional timeout keeps discovery active until
                # it is explicitly stopped. Periodically replacing a timed
                # find can wedge RTL8822CE while its final scan is pending.
                self._wpa(self.settings.interface, "p2p_find")
            except (OSError, subprocess.TimeoutExpired, WifiDirectError):
                # A failed stop can leave the driver's scan request wedged.
                # Abort that stale request and retry once. If it remains
                # unavailable, the monitor backs off without restarting
                # NetworkManager or otherwise disrupting network state.
                self._wpa(self.settings.interface, "abort_scan", allow_failure=True)
                self._sleep(0.2)
                self._wpa(self.settings.interface, "p2p_stop_find", allow_failure=True)
                self._wpa(self.settings.interface, "p2p_find")
        except (OSError, subprocess.TimeoutExpired, WifiDirectError) as error:
            self._record_discovery_failure(error)
            return False

        self._last_discovery = self._monotonic()
        self._discovery_failure_count = 0
        self._next_discovery_retry = 0.0
        self._last_discovery_error = None
        return True

    def _record_discovery_failure(self, error: object) -> None:
        self._discovery_failure_count += 1
        exponent = min(self._discovery_failure_count - 1, 4)
        retry_delay = min(
            DISCOVERY_RETRY_INITIAL_SECONDS * (2 ** exponent),
            DISCOVERY_RETRY_MAX_SECONDS,
        )
        self._next_discovery_retry = self._monotonic() + retry_delay
        self._last_discovery_error = str(error)

    def _radio_recovery_was_attempted(self, request: WifiModeRequest) -> bool:
        if self._radio_recovery_request_id == request.request_id:
            return True
        try:
            value = json.loads(self.radio_recovery_path.read_text(encoding="utf-8"))
        except (OSError, ValueError):
            return False
        if not isinstance(value, dict) or value.get("requestId") != request.request_id:
            return False
        self._radio_recovery_request_id = request.request_id
        return True

    def _remember_radio_recovery(self, request: WifiModeRequest) -> None:
        self._radio_recovery_request_id = request.request_id
        self.status_path.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
        temporary = self.radio_recovery_path.with_suffix(".tmp")
        try:
            with temporary.open("w", encoding="utf-8") as output:
                json.dump(
                    {
                        "version": 1,
                        "requestId": request.request_id,
                        "attemptedAtMonotonicSeconds": self._monotonic(),
                        "pid": os.getpid(),
                    },
                    output,
                    separators=(",", ":"),
                    sort_keys=True,
                )
                output.write("\n")
                output.flush()
                os.fsync(output.fileno())
            os.chmod(str(temporary), 0o600)
            os.replace(str(temporary), str(self.radio_recovery_path))
        finally:
            try:
                temporary.unlink()
            except OSError:
                pass

    def _radio_recovery_context_is_valid(self, request: WifiModeRequest) -> bool:
        activation = self._activation_thread
        return (
            self.wifi_mode.current_request() == request
            and request.mode == WIFI_MODE_DIRECT
            and self._handled_mode_request_id == request.request_id
            and self._state != "CONNECTING"
            and self.active_peer is None
            and (activation is None or not activation.is_alive())
            and not self.wifi_mode.provisioning_active()
            and self._active_managed_wifi_profile() is None
            and self._first_group_interface() is None
        )

    def _ensure_device_autoconnect_disabled(self) -> None:
        command = [
            "/usr/bin/nmcli",
            "--terse",
            "--get-values",
            "GENERAL.AUTOCONNECT",
            "device",
            "show",
            self.settings.interface,
        ]
        result = self._run(command, allow_failure=True)
        if result.returncode == 0 and result.stdout.strip().lower() == "no":
            return
        self._run(
            [
                "/usr/bin/nmcli",
                "device",
                "set",
                self.settings.interface,
                "autoconnect",
                "no",
            ]
        )
        result = self._run(command, allow_failure=True)
        if result.returncode != 0 or result.stdout.strip().lower() != "no":
            raise WifiDirectError(
                "NetworkManager did not disable wlan autoconnect before radio recovery"
            )

    def _wait_for_radio_disabled(self) -> None:
        disabled_states = {"unavailable", "unmanaged", "unknown"}
        for _attempt in range(RADIO_INTERFACE_WAIT_ATTEMPTS):
            devices = self._run(
                [
                    "/usr/bin/nmcli",
                    "--terse",
                    "--fields",
                    "DEVICE,TYPE,STATE",
                    "device",
                    "status",
                ],
                allow_failure=True,
            )
            base_disabled = True
            p2p_disabled = True
            for line in devices.stdout.splitlines():
                fields = line.split(":", 2)
                if len(fields) != 3:
                    continue
                device, device_type, state = fields
                if device == self.settings.interface and state not in disabled_states:
                    base_disabled = False
                if device_type == "wifi-p2p" and state not in disabled_states:
                    p2p_disabled = False
            ping = self._wpa(self.settings.interface, "ping", allow_failure=True)
            ping_lines = [line.strip() for line in ping.stdout.splitlines() if line.strip()]
            wpa_disabled = not (
                ping.returncode == 0 and ping_lines and ping_lines[-1] == "PONG"
            )
            if base_disabled and p2p_disabled and wpa_disabled:
                return
            self._sleep(RADIO_INTERFACE_WAIT_SECONDS)
        raise WifiDirectError(
            "Wi-Fi interfaces did not disappear after NetworkManager disabled the radio"
        )

    def _disconnect_station_after_radio_reset(self, request: WifiModeRequest) -> None:
        if (
            self.wifi_mode.current_request() != request
            or self.wifi_mode.provisioning_active()
        ):
            raise WifiDirectRadioRecoveryCancelled(
                "LAN provisioning superseded Wi-Fi radio recovery"
            )
        if self._active_managed_wifi_profile() is None:
            return
        self._run(
            [
                "/usr/bin/nmcli",
                "--wait",
                "20",
                "device",
                "disconnect",
                self.settings.interface,
            ],
            timeout=25,
        )
        for _attempt in range(RADIO_INTERFACE_WAIT_ATTEMPTS):
            if self._active_managed_wifi_profile() is None:
                return
            self._sleep(RADIO_INTERFACE_WAIT_SECONDS)
        raise WifiDirectError(
            "A managed wlan connection remained active after Wi-Fi radio recovery"
        )

    def _recover_stuck_discovery_radio(self, request: WifiModeRequest) -> bool:
        """Reset a wedged single radio once for one explicit Direct generation."""
        if (
            request.mode != WIFI_MODE_DIRECT
            or self._discovery_failure_count
            < DISCOVERY_RADIO_RECOVERY_FAILURE_THRESHOLD
            or self._radio_recovery_was_attempted(request)
        ):
            return False

        # An activation owns a shared lock and LAN cleanup/provisioning owns the
        # exclusive lock. Never wait behind either operation or reset underneath it.
        radio_lock = self._try_wifi_radio_lock(exclusive=True)
        if radio_lock is None:
            return False
        activation_lock_acquired = self._activation_lock.acquire(blocking=False)
        if not activation_lock_acquired:
            self._release_wifi_radio_lock(radio_lock)
            return False
        try:
            # All eligibility checks are repeated after exclusive ownership. In
            # particular, an NM station may autoconnect between monitor ticks.
            if (
                not self._radio_recovery_context_is_valid(request)
                or self._radio_recovery_was_attempted(request)
            ):
                return False

            # Persist before touching the radio. This prevents a systemd restart
            # from repeatedly power-cycling Wi-Fi for the same request generation.
            self._remember_radio_recovery(request)
            self._ensure_device_autoconnect_disabled()
            self._publish(
                "PAUSED",
                "Resetting the Wi-Fi radio after repeated Direct discovery failures",
            )

            reset_errors: List[Exception] = []
            try:
                self._run(
                    ["/usr/bin/nmcli", "--wait", "15", "radio", "wifi", "off"],
                    timeout=20,
                )
                self.management_interface = None
                self._wait_for_radio_disabled()
                self._sleep(RADIO_RESET_SETTLE_SECONDS)
            except (OSError, subprocess.TimeoutExpired, WifiDirectError) as error:
                reset_errors.append(error)
            try:
                # Always re-enable Wi-Fi: nmcli may apply the state change before
                # reporting an error or timing out.
                self._run(
                    ["/usr/bin/nmcli", "--wait", "15", "radio", "wifi", "on"],
                    timeout=20,
                )
                self._sleep(RADIO_RESET_SETTLE_SECONDS)
            except (OSError, subprocess.TimeoutExpired, WifiDirectError) as error:
                reset_errors.append(error)
            if reset_errors:
                raise WifiDirectError(
                    "Wi-Fi radio reset failed: {}".format(reset_errors[-1])
                )

            radio_owner_unchanged = lambda: (
                self.wifi_mode.current_request() == request
                and self._handled_mode_request_id == request.request_id
                and not self.wifi_mode.provisioning_active()
                and self.active_peer is None
            )
            self._wait_for_wpa_supplicant(continue_if=radio_owner_unchanged)
            self._disconnect_station_after_radio_reset(request)
            still_direct = lambda: self._radio_recovery_context_is_valid(request)
            if not still_direct():
                raise WifiDirectRadioRecoveryCancelled(
                    "Wi-Fi radio recovery was superseded by LAN or station mode"
                )
            self._configure_p2p_identity()
            if not still_direct():
                raise WifiDirectRadioRecoveryCancelled(
                    "Wi-Fi radio recovery was superseded before P2P discovery"
                )
            self.management_interface = self._wait_for_management_interface(
                continue_if=still_direct,
            )
            if not still_direct():
                raise WifiDirectRadioRecoveryCancelled(
                    "Wi-Fi radio recovery was superseded before P2P discovery"
                )
            if self._radio_recovery_callback is None:
                raise WifiDirectError(
                    "Wi-Fi Direct event receiver cannot be rebound after radio recovery"
                )
            self._radio_recovery_callback()
            if not still_direct():
                raise WifiDirectRadioRecoveryCancelled(
                    "Wi-Fi radio recovery was superseded before P2P discovery"
                )
            if self._refresh_discovery_locked():
                self._publish(
                    "DISCOVERABLE",
                    "Wi-Fi Direct radio recovered; waiting for Android",
                )
                return True
            self._publish(
                "PAUSED",
                "Wi-Fi radio reset completed, but Direct discovery is still unavailable; "
                "retrying with backoff",
            )
            return False
        except WifiDirectRadioRecoveryCancelled:
            # The radio has already been turned back on. Leave all further work to
            # the newer LAN/station owner and do not resume P2P from this path.
            return False
        except (OSError, subprocess.TimeoutExpired, WifiDirectError) as error:
            self._record_discovery_failure(error)
            if self.wifi_mode.current_request() == request:
                self._publish(
                    "PAUSED",
                    "Wi-Fi radio recovery failed; Direct discovery will retry with backoff",
                )
            return False
        finally:
            self._activation_lock.release()
            self._release_wifi_radio_lock(radio_lock)

    def _resume_discovery(self, success_message: str) -> str:
        mode_request = self.wifi_mode.current_request()
        if mode_request is not None and mode_request.mode == WIFI_MODE_LAN:
            self._publish("PAUSED", "LAN mode is active; Wi-Fi Direct is paused")
            return "PAUSED"
        if (
            not (
                mode_request is not None
                and mode_request.mode == WIFI_MODE_DIRECT
            )
            and self._active_managed_wifi_profile()
        ):
            return self._pause_discovery_for_managed_wifi()

        self._discovery_paused_for_managed_wifi = False
        if self.refresh_discovery():
            self._publish("DISCOVERABLE", success_message)
            return "DISCOVERABLE"
        if self._discovery_failure_count > 0:
            self._publish(
                "PAUSED",
                "Wi-Fi Direct discovery is temporarily unavailable; "
                "retrying automatically",
            )
        else:
            self._publish("PAUSED", "Waiting for Wi-Fi provisioning to finish")
        return "PAUSED"

    def _pause_discovery_for_managed_wifi(self) -> str:
        already_paused = self._discovery_paused_for_managed_wifi
        if not already_paused:
            self._wpa(self.settings.interface, "p2p_stop_find", allow_failure=True)
        self._discovery_paused_for_managed_wifi = True
        self._discovery_paused_for_provisioning = False
        self._discovery_failure_count = 0
        self._next_discovery_retry = 0.0
        self._last_discovery_error = None
        if not already_paused or self._state != "PAUSED":
            self._publish(
                "PAUSED",
                "Managed Wi-Fi is active; Wi-Fi Direct discovery is paused",
            )
        return "PAUSED"

    def request_connection(self, peer_address: str) -> bool:
        peer = normalize_mac_address(peer_address)
        with self._activation_lock:
            mode_request = self.wifi_mode.current_request()
            if mode_request is not None and mode_request.mode == WIFI_MODE_LAN:
                return False
            if self._activation_thread is not None and self._activation_thread.is_alive():
                return False
            if self._state == "READY":
                return False
            self._activation_cancel.clear()
            self._activation_thread = threading.Thread(
                target=self._activate_peer,
                args=(peer,),
                name="wifi-direct-activate",
                daemon=True,
            )
            self._activation_thread.start()
        return True

    def activate_peer_for_test(self, peer_address: str) -> str:
        self._activation_cancel.clear()
        return self._activate_peer(normalize_mac_address(peer_address))

    def monitor(self) -> bool:
        mode_request = self.wifi_mode.current_request()
        if mode_request is not None and mode_request.mode == WIFI_MODE_LAN:
            if not self._cancel_activation_for_lan():
                self._publish(
                    "PAUSED",
                    "Stopping the active Wi-Fi Direct connection for LAN mode",
                )
                return True
            if self.wifi_mode.current_request() != mode_request:
                return True
            group_exists = self._first_group_interface() is not None
            if (
                self._handled_mode_request_id != mode_request.request_id
                or self._state != "PAUSED"
                or group_exists
            ):
                self._process_mode_request(mode_request)
            return True
        if (
            mode_request is not None
            and mode_request.mode == WIFI_MODE_DIRECT
            and self._handled_mode_request_id != mode_request.request_id
        ):
            self._process_mode_request(mode_request)
            return True

        if self._wifi_radio_is_busy():
            self._discovery_paused_for_provisioning = True
            return True
        resume_requested = (
            self._discovery_paused_for_provisioning or
            self.wifi_direct_resume_path.exists()
        )

        group_interface = self._first_group_interface()
        if group_interface:
            address = self._interface_address(group_interface)
            if address:
                self.group_interface = group_interface
                if self._manual_owner_mode and self._state == "CONNECTING":
                    return True
                if self._manual_owner_mode and not self._dnsmasq_is_running():
                    self._cleanup_direct_connection()
                    self.active_peer = None
                    try:
                        self._restore_suspended_wifi_if_allowed()
                    except WifiDirectError as error:
                        self._publish("ERROR", str(error))
                        return True
                    self._resume_discovery("DHCP stopped; waiting again")
                    return True
                if self._manual_owner_mode and not self._manual_group_has_station(group_interface):
                    if self._manual_group_empty_since is None:
                        self._manual_group_empty_since = self._monotonic()
                    idle_limit = (
                        MANUAL_GROUP_IDLE_SECONDS
                        if self._manual_peer_seen
                        else MANUAL_GROUP_JOIN_GRACE_SECONDS
                    )
                    if self._monotonic() - self._manual_group_empty_since >= idle_limit:
                        self._cleanup_direct_connection()
                        self.active_peer = None
                        try:
                            self._restore_suspended_wifi_if_allowed()
                        except WifiDirectError as error:
                            self._publish("ERROR", str(error))
                            return True
                        self._resume_discovery(
                            "Wi-Fi Direct peer left; waiting for a fresh connection",
                        )
                        return True
                elif self._manual_owner_mode:
                    self._manual_peer_seen = True
                    self._manual_group_empty_since = None
                if self._state != "READY":
                    self._publish(
                        "READY",
                        "Wi-Fi Direct group is connected",
                        address=address,
                    )
                return True

        if self._state == "READY":
            self.active_peer = None
            self._cleanup_direct_connection()
            try:
                self._restore_suspended_wifi_if_allowed()
            except WifiDirectError as error:
                self._publish("ERROR", str(error))
                return True
            self._resume_discovery("Wi-Fi Direct disconnected; waiting again")
        elif (
            self._state == "ERROR"
            and self._suspended_wifi_profile
            and not self._direct_mode_requested()
        ):
            try:
                self._restore_suspended_wifi_if_allowed()
                self._resume_discovery("Wi-Fi restored; waiting again")
            except WifiDirectError:
                pass
        elif (
            self._state != "CONNECTING"
            and not self._direct_mode_requested()
            and self._active_managed_wifi_profile()
        ):
            self._pause_discovery_for_managed_wifi()
            return True
        elif self._discovery_paused_for_managed_wifi:
            self._discovery_paused_for_managed_wifi = False
            self._resume_discovery(
                "Managed Wi-Fi disconnected; Wi-Fi Direct discovery resumed",
            )
            return True
        elif (
            self._state == "DISCOVERABLE"
            and resume_requested
        ):
            self._resume_discovery("Waiting for the Android connection request")
        elif self._state in {"PAUSED", "STARTING"}:
            retry_due = (
                self._discovery_failure_count > 0
                and self._monotonic() >= self._next_discovery_retry
            )
            resume_due = resume_requested and self._discovery_failure_count == 0
            if retry_due or resume_due:
                self._resume_discovery("Wi-Fi Direct discovery resumed; waiting again")
        if (
            self._state == "PAUSED"
            and mode_request is not None
            and mode_request.mode == WIFI_MODE_DIRECT
            and self._discovery_failure_count
            >= DISCOVERY_RADIO_RECOVERY_FAILURE_THRESHOLD
        ):
            self._recover_stuck_discovery_radio(mode_request)
        return True

    def _direct_mode_requested(self) -> bool:
        request = self.wifi_mode.current_request()
        return request is not None and request.mode == WIFI_MODE_DIRECT

    def _process_mode_request(
        self,
        request: WifiModeRequest,
    ) -> Optional[str]:
        radio_lock = self._try_wifi_radio_lock(exclusive=True)
        if radio_lock is None:
            return None
        try:
            if request.mode == WIFI_MODE_LAN:
                return self._enter_lan_mode(request)
            return self._enter_direct_mode(request)
        except (OSError, ValueError, WifiDirectError) as error:
            message = "Wi-Fi mode transition failed: {}".format(error)
            # A failed Direct transition can leave managed Wi-Fi active. Keep
            # retrying the generation instead of starting P2P beside it.
            self._handled_mode_request_id = (
                request.request_id if request.mode == WIFI_MODE_LAN else None
            )
            self._publish("ERROR", message)
            self.wifi_mode.acknowledge(request, False, message)
            return "ERROR"
        finally:
            self._release_wifi_radio_lock(radio_lock)

    def _enter_lan_mode(self, request: WifiModeRequest) -> str:
        self._wpa(self.settings.interface, "p2p_stop_find")
        self._wpa(self.settings.interface, "p2p_cancel", allow_failure=True)
        self._wpa(self.settings.interface, "abort_scan", allow_failure=True)
        if self._suspended_wifi_profile:
            self.wifi_mode.remember_suspended_profile(self._suspended_wifi_profile)
            self._suspended_wifi_profile = None
        self._cleanup_direct_connection()
        self._cleanup_stale_groups()
        if (
            self._first_group_interface() is not None
            or self._dnsmasq_is_running()
            or self._active_managed_profile() is not None
        ):
            raise WifiDirectError(
                "Wi-Fi Direct cleanup did not finish before the LAN handoff"
            )
        self.group_interface = None
        self.active_peer = None
        self._discovery_paused_for_managed_wifi = False
        self._discovery_paused_for_provisioning = False
        self._discovery_failure_count = 0
        self._next_discovery_retry = 0.0
        self._handled_mode_request_id = request.request_id
        message = "Wi-Fi Direct stopped; the radio is ready for LAN provisioning"
        self._publish("PAUSED", message)
        self.wifi_mode.acknowledge(request, True, message)
        return "PAUSED"

    def _enter_direct_mode(self, request: WifiModeRequest) -> str:
        profile = self._active_managed_wifi_profile()
        if profile:
            self._suspended_wifi_profile = profile
            self.wifi_mode.remember_suspended_profile(profile)
            self._run(
                [
                    "/usr/bin/nmcli",
                    "--wait",
                    "20",
                    "device",
                    "disconnect",
                    self.settings.interface,
                ],
                timeout=25,
            )
            self._wait_for_wpa_supplicant()
            self._configure_p2p_identity()

        group_interface = self._first_group_interface()
        address = self._interface_address(group_interface) if group_interface else None
        if group_interface and not address:
            self._cleanup_direct_connection()
            self._cleanup_stale_groups()
            group_interface = None
        self._handled_mode_request_id = request.request_id
        if group_interface and address:
            self.group_interface = group_interface
            state = "READY"
            message = "Wi-Fi Direct group is connected"
            self._publish(state, message, address=address)
        else:
            self._discovery_failure_count = 0
            self._next_discovery_retry = 0.0
            if self._refresh_discovery_locked():
                state = "DISCOVERABLE"
                message = "Wi-Fi Direct mode is ready"
                self._publish(
                    state,
                    "Direct mode requested over Bluetooth; waiting for Android",
                )
            else:
                state = "PAUSED"
                message = "Wi-Fi Direct discovery is retrying automatically"
                self._publish(state, message)
        self.wifi_mode.acknowledge(request, True, message)
        return state

    def _try_wifi_radio_lock(self, exclusive: bool = False) -> Optional[TextIO]:
        handle: Optional[TextIO] = None
        try:
            self.status_path.parent.mkdir(parents=True, exist_ok=True)
            handle = self.wifi_radio_lock_path.open("a+", encoding="utf-8")
            operation = fcntl.LOCK_EX if exclusive else fcntl.LOCK_SH
            fcntl.flock(handle.fileno(), operation | fcntl.LOCK_NB)
            return handle
        except (BlockingIOError, OSError):
            if handle is not None:
                handle.close()
            return None

    @staticmethod
    def _release_wifi_radio_lock(handle: TextIO) -> None:
        try:
            fcntl.flock(handle.fileno(), fcntl.LOCK_UN)
        finally:
            handle.close()

    def _wifi_radio_is_busy(self) -> bool:
        handle = self._try_wifi_radio_lock()
        if handle is None:
            return True
        self._release_wifi_radio_lock(handle)
        return False

    def _cancel_activation_for_lan(self) -> bool:
        """Interrupt an in-flight activation before the LAN cleanup takes ownership."""
        self._activation_cancel.set()
        activation = self._activation_thread
        if activation is None or not activation.is_alive():
            return True

        # These are cancellation signals, not the final cleanup. The activation
        # thread still owns the shared radio lock; monitor() waits for it to exit
        # before acquiring the exclusive lock and acknowledging the LAN handoff.
        profile = self.active_profile
        try:
            self._wpa(self.settings.interface, "p2p_cancel", allow_failure=True)
        except (OSError, subprocess.TimeoutExpired, WifiDirectError):
            pass
        if profile:
            try:
                self._run(
                    [
                        "/usr/bin/nmcli",
                        "--wait",
                        "5",
                        "connection",
                        "down",
                        profile,
                    ],
                    allow_failure=True,
                    timeout=10,
                )
            except (OSError, subprocess.TimeoutExpired, WifiDirectError):
                pass

        activation.join(timeout=ACTIVATION_CANCEL_JOIN_SECONDS)
        return not activation.is_alive()

    def stop(self) -> None:
        if not self._cancel_activation_for_lan():
            # Do not race cleanup against a thread that still owns the shared
            # radio lock. The daemon thread and its children leave with this
            # service process; the next start performs stale-group cleanup.
            self._publish("STOPPED", "Wi-Fi Direct service is stopping")
            return
        self._cleanup_direct_connection()
        restore_error: Optional[WifiDirectError] = None
        try:
            self._restore_suspended_wifi_if_allowed()
        except WifiDirectError as error:
            restore_error = error
        finally:
            self._wpa(self.settings.interface, "p2p_stop_find", allow_failure=True)
            self.group_interface = None
            self.active_peer = None
        if restore_error is not None:
            self._publish("ERROR", str(restore_error))
            raise restore_error
        self._publish("STOPPED", "Wi-Fi Direct service stopped")

    def _activate_peer(self, peer: str) -> str:
        radio_lock = self._try_wifi_radio_lock()
        if radio_lock is None:
            self.active_peer = None
            self._publish("PAUSED", "Wi-Fi radio mode is changing; try Direct again")
            return ""
        try:
            mode_request = self.wifi_mode.current_request()
            if mode_request is not None and mode_request.mode == WIFI_MODE_LAN:
                self.active_peer = None
                self._publish("PAUSED", "LAN mode is active; Wi-Fi Direct is paused")
                return ""
            self._raise_if_activation_cancelled()
            return self._activate_peer_locked(peer)
        finally:
            self._release_wifi_radio_lock(radio_lock)

    def _activate_peer_locked(self, peer: str) -> str:
        profile = PROFILE_PREFIX + peer.replace(":", "").lower()
        self.active_peer = peer
        self._publish("CONNECTING", "Android requested a Wi-Fi Direct connection")
        try:
            self._raise_if_activation_cancelled()
            if self._supports_concurrent_managed_and_p2p():
                self._raise_if_activation_cancelled()
                group_interface = self._activate_peer_with_networkmanager(profile, peer)
            else:
                self._raise_if_activation_cancelled()
                group_interface = self._activate_peer_as_manual_owner(peer)
            self._raise_if_activation_cancelled()
            address = self._wait_for_interface_address(group_interface)
            self._raise_if_activation_cancelled()
            self.group_interface = group_interface
            self._publish(
                "READY",
                "Wi-Fi Direct group is connected",
                address=address,
            )
            return group_interface
        except (OSError, subprocess.TimeoutExpired, ValueError, WifiDirectError) as error:
            if isinstance(error, WifiDirectActivationCancelled) or self._activation_should_cancel():
                # The LAN transition owns rollback. Restoring the prior station
                # profile or restarting discovery here would race the provisioner.
                self.active_peer = None
                self._publish("PAUSED", "LAN mode requested; Direct activation stopped")
                return ""
            self._cleanup_direct_connection()
            self.active_peer = None
            restore_failed = False
            try:
                self._restore_suspended_wifi_if_allowed()
            except WifiDirectError as restore_error:
                error = WifiDirectError("{}; {}".format(error, restore_error))
                restore_failed = True
            self._publish("ERROR", str(error))
            self._sleep(2)
            if restore_failed:
                return ""
            self._resume_discovery(
                "Connection failed; waiting for another Android request",
            )
            return ""

    def _activate_peer_with_networkmanager(self, profile: str, peer: str) -> str:
        self._raise_if_activation_cancelled()
        self.active_profile = profile
        self._run(
            ["/usr/bin/nmcli", "connection", "delete", profile],
            allow_failure=True,
        )
        self._raise_if_activation_cancelled()
        self._run(
            [
                "/usr/bin/nmcli",
                "connection",
                "add",
                "save",
                "no",
                "type",
                "wifi-p2p",
                "ifname",
                self.management_interface or "p2p-dev-{}".format(self.settings.interface),
                "con-name",
                profile,
                "autoconnect",
                "no",
                "wifi-p2p.peer",
                peer,
                "wifi-p2p.wps-method",
                "pbc",
                "ipv4.method",
                "shared",
                "ipv4.addresses",
                self.settings.address,
                "ipv4.never-default",
                "yes",
                "ipv6.method",
                "ignore",
            ],
            timeout=20,
        )
        self._raise_if_activation_cancelled()
        self._run(
            [
                "/usr/bin/nmcli",
                "--wait",
                "50",
                "connection",
                "up",
                profile,
                "ifname",
                self.management_interface or "p2p-dev-{}".format(self.settings.interface),
            ],
            timeout=60,
        )
        self._raise_if_activation_cancelled()
        return self._wait_for_group_interface()

    def _activate_peer_as_manual_owner(self, peer: str) -> str:
        self._raise_if_activation_cancelled()
        self._prepare_manual_owner()
        self._raise_if_activation_cancelled()
        self._manual_owner_mode = True
        self._wpa(self.settings.interface, "p2p_stop_find", allow_failure=True)
        self._raise_if_activation_cancelled()
        self._wpa(
            self.settings.interface,
            "p2p_connect",
            peer,
            "pbc",
            "go_intent=15",
            "freq={}".format(self.settings.frequency),
        )
        self._raise_if_activation_cancelled()
        group_interface = self._wait_for_group_interface()
        self._raise_if_activation_cancelled()
        self.group_interface = group_interface
        self._configure_manual_owner_address(group_interface)
        self._raise_if_activation_cancelled()
        self._start_dnsmasq(group_interface)
        self._raise_if_activation_cancelled()
        self._manual_peer_seen = False
        self._manual_group_empty_since = self._monotonic()
        return group_interface

    def _wait_for_wpa_supplicant(
        self,
        cancel_activation: bool = False,
        continue_if: Optional[Callable[[], bool]] = None,
    ) -> None:
        last_error: Optional[Exception] = None
        for _attempt in range(30):
            if cancel_activation:
                self._raise_if_activation_cancelled()
            if continue_if is not None and not continue_if():
                raise WifiDirectRadioRecoveryCancelled(
                    "Wi-Fi radio recovery was superseded while waiting for wpa_supplicant"
                )
            try:
                self._wpa(self.settings.interface, "ping")
                if cancel_activation:
                    self._raise_if_activation_cancelled()
                if continue_if is not None and not continue_if():
                    raise WifiDirectRadioRecoveryCancelled(
                        "Wi-Fi radio recovery was superseded while waiting for wpa_supplicant"
                    )
                return
            except WifiDirectRadioRecoveryCancelled:
                raise
            except WifiDirectError as error:
                last_error = error
                self._sleep(1)
        raise WifiDirectError("wpa_supplicant control interface is unavailable") from last_error

    def _configure_p2p_identity(self) -> None:
        operating_class, channel = frequency_channel(self.settings.frequency)
        self._wpa(self.settings.interface, "set", "device_name", self.settings.device_name)
        self._wpa(self.settings.interface, "set", "device_type", "1-0050F204-1")
        self._wpa(
            self.settings.interface,
            "set",
            "p2p_oper_reg_class",
            str(operating_class),
        )
        self._wpa(self.settings.interface, "set", "p2p_oper_channel", str(channel))
        self._wpa(
            self.settings.interface,
            "set",
            "p2p_pref_chan",
            "{}:{}".format(operating_class, channel),
        )

    def _wait_for_management_interface(
        self,
        continue_if: Optional[Callable[[], bool]] = None,
    ) -> str:
        preferred = "p2p-dev-{}".format(self.settings.interface)
        for _attempt in range(30):
            if continue_if is not None and not continue_if():
                raise WifiDirectRadioRecoveryCancelled(
                    "Wi-Fi radio recovery was superseded while waiting for NetworkManager"
                )
            result = self._run(
                [
                    "/usr/bin/nmcli",
                    "--terse",
                    "--fields",
                    "DEVICE,TYPE,STATE",
                    "device",
                    "status",
                ]
            )
            candidates = []
            for line in result.stdout.splitlines():
                fields = line.split(":", 2)
                if (
                    len(fields) == 3
                    and fields[1] == "wifi-p2p"
                    and fields[2] not in {"unavailable", "unmanaged", "unknown"}
                ):
                    candidates.append(fields[0])
            if preferred in candidates:
                return preferred
            if len(candidates) == 1:
                return candidates[0]
            self._sleep(1)
        raise WifiDirectError(
            "NetworkManager Wi-Fi P2P device is unavailable; restart NetworkManager if a stale placeholder exists"
        )

    def _cleanup_stale_groups(self) -> None:
        interfaces = parse_iw_interfaces(self._run(["/usr/sbin/iw", "dev"]).stdout)
        pattern = re.compile(r"^p2p-{}-[0-9]+$".format(re.escape(self.settings.interface)))
        for interface, interface_type in interfaces.items():
            if interface_type != "P2P-GO":
                continue
            is_base_interface = interface == self.settings.interface
            if not is_base_interface and not pattern.fullmatch(interface):
                continue
            if is_base_interface and self._interface_address(interface):
                self._run(
                    [
                        "/usr/sbin/ip",
                        "-4",
                        "address",
                        "delete",
                        self.settings.address,
                        "dev",
                        interface,
                    ],
                    allow_failure=True,
                )
            self._wpa(
                self.settings.interface,
                "p2p_group_remove",
                interface,
                allow_failure=True,
            )
            self._sleep(0.2)
            if is_base_interface:
                continue
            if interface in parse_iw_interfaces(self._run(["/usr/sbin/iw", "dev"]).stdout):
                self._run(
                    ["/usr/sbin/iw", "dev", interface, "del"],
                    allow_failure=True,
                )

    def _first_group_interface(self) -> Optional[str]:
        result = self._run(["/usr/sbin/iw", "dev"], allow_failure=True)
        groups = parse_p2p_group_interfaces(result.stdout)
        return groups[0] if groups else None

    def _wait_for_group_interface(self) -> str:
        for _attempt in range(60):
            self._raise_if_activation_cancelled()
            interface = self._first_group_interface()
            if interface:
                return interface
            self._sleep(0.25)
        raise WifiDirectError("NetworkManager did not create a P2P Group Owner interface")

    def _interface_address(self, interface: str) -> Optional[str]:
        result = self._run(
            ["/usr/sbin/ip", "-j", "-4", "address", "show", "dev", interface],
            allow_failure=True,
        )
        return configured_ipv4_address(result.stdout, self.settings.address)

    def _wait_for_interface_address(self, interface: str) -> str:
        for _attempt in range(40):
            self._raise_if_activation_cancelled()
            address = self._interface_address(interface)
            if address:
                return address
            self._sleep(0.25)
        raise WifiDirectError("NetworkManager did not assign the Wi-Fi Direct address")

    def _active_managed_profile(self) -> Optional[str]:
        result = self._run(
            [
                "/usr/bin/nmcli",
                "--terse",
                "--fields",
                "NAME,TYPE",
                "connection",
                "show",
                "--active",
            ],
            allow_failure=True,
        )
        for line in result.stdout.splitlines():
            fields = line.split(":", 1)
            if len(fields) == 2 and fields[0].startswith(PROFILE_PREFIX) and fields[1] == "wifi-p2p":
                return fields[0]
        return None

    def _active_managed_wifi_profile(self) -> Optional[str]:
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
            allow_failure=True,
        )
        for line in result.stdout.splitlines():
            fields = line.split(":", 2)
            if (
                len(fields) == 3
                and fields[0]
                and fields[1] in {"802-11-wireless", "wifi"}
                and fields[2] == self.settings.interface
            ):
                return fields[0]
        return None

    def _supports_concurrent_managed_and_p2p(self) -> bool:
        interface = self._run(
            ["/usr/sbin/iw", "dev", self.settings.interface, "info"],
            allow_failure=True,
        )
        wiphy = parse_wiphy_name(interface.stdout)
        if not wiphy:
            return False
        capabilities = self._run(
            ["/usr/sbin/iw", "phy", wiphy, "info"],
            allow_failure=True,
        )
        capability = managed_p2p_concurrency_capability(capabilities.stdout)
        return capability

    def _has_alternate_default_route(self) -> bool:
        routes = self._run(
            ["/usr/sbin/ip", "-j", "-4", "route", "show", "default"],
            allow_failure=True,
        )
        return any(
            interface != self.settings.interface and not interface.startswith("p2p-")
            for interface in parse_default_route_interfaces(routes.stdout)
        )

    def _prepare_manual_owner(self) -> None:
        self._raise_if_activation_cancelled()
        self._run([DNSMASQ_PATH, "--version"], timeout=5)
        self._raise_if_activation_cancelled()
        profile = self._active_managed_wifi_profile()
        # A disconnected single-interface adapter can become the P2P owner
        # without sacrificing an existing route. Requiring Ethernet here made
        # Wi-Fi Direct impossible on exactly the offline recovery path it is
        # intended to provide.
        if not profile:
            return
        if not self._has_alternate_default_route():
            raise WifiDirectError(
                "The active managed Wi-Fi connection cannot be suspended for "
                "manual Wi-Fi Direct because no alternate default route is available"
            )
        self._suspended_wifi_profile = profile
        self.wifi_mode.remember_suspended_profile(profile)
        self._raise_if_activation_cancelled()
        self._run(
            [
                "/usr/bin/nmcli",
                "--wait",
                "20",
                "device",
                "disconnect",
                self.settings.interface,
            ],
            timeout=25,
        )
        self._raise_if_activation_cancelled()
        self._wait_for_wpa_supplicant(cancel_activation=True)
        self._raise_if_activation_cancelled()
        self._configure_p2p_identity()

    def _activation_should_cancel(self) -> bool:
        if self._activation_cancel.is_set():
            return True
        request = self.wifi_mode.current_request()
        if request is not None and request.mode == WIFI_MODE_LAN:
            self._activation_cancel.set()
            return True
        return False

    def _raise_if_activation_cancelled(self) -> None:
        if self._activation_should_cancel():
            raise WifiDirectActivationCancelled("Wi-Fi Direct activation was cancelled")

    def _configure_manual_owner_address(self, interface: str) -> None:
        self._run(["/usr/sbin/ip", "link", "set", "dev", interface, "up"])
        self._raise_if_activation_cancelled()
        self._run(
            [
                "/usr/sbin/ip",
                "-4",
                "address",
                "flush",
                "dev",
                interface,
                "scope",
                "global",
            ]
        )
        self._raise_if_activation_cancelled()
        self._run(
            [
                "/usr/sbin/ip",
                "-4",
                "address",
                "add",
                self.settings.address,
                "dev",
                interface,
            ]
        )
        self._raise_if_activation_cancelled()
        self._manual_address_interface = interface

    def _start_dnsmasq(self, interface: str) -> None:
        lease_start, lease_end, netmask = dhcp_lease_range(self.settings.address)
        for path in (self.dnsmasq_lease_path, self.dnsmasq_pid_path):
            try:
                path.unlink()
            except OSError:
                pass
        command = [
            DNSMASQ_PATH,
            "--keep-in-foreground",
            "--conf-file=",
            "--port=0",
            "--bind-interfaces",
            "--interface={}".format(interface),
            "--listen-address={}".format(self.settings.owner_ip),
            "--no-hosts",
            "--no-resolv",
            "--dhcp-authoritative",
            "--dhcp-lease-max=4",
            "--dhcp-range={},{},{},1h".format(lease_start, lease_end, netmask),
            "--dhcp-option=option:router,{}".format(self.settings.owner_ip),
            "--dhcp-leasefile={}".format(self.dnsmasq_lease_path),
            "--pid-file={}".format(self.dnsmasq_pid_path),
        ]
        process = self._start_process(
            command,
            stdin=subprocess.DEVNULL,
            close_fds=True,
        )
        self._dnsmasq_process = process
        self._sleep(0.2)
        if process.poll() is not None:
            self._dnsmasq_process = None
            raise WifiDirectError("Wi-Fi Direct DHCP service failed to start")

    def _dnsmasq_is_running(self) -> bool:
        return self._dnsmasq_process is not None and self._dnsmasq_process.poll() is None

    def _manual_group_has_station(self, interface: str) -> bool:
        result = self._run(
            ["/usr/sbin/iw", "dev", interface, "station", "dump"],
            allow_failure=True,
        )
        return bool(parse_station_addresses(result.stdout))

    def _stop_dnsmasq(self) -> None:
        process = self._dnsmasq_process
        self._dnsmasq_process = None
        if process is not None and process.poll() is None:
            try:
                process.terminate()
                process.wait(timeout=2)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait(timeout=2)
            except OSError:
                pass
        for path in (self.dnsmasq_lease_path, self.dnsmasq_pid_path):
            try:
                path.unlink()
            except OSError:
                pass

    def _cleanup_direct_connection(self) -> None:
        if not self._manual_owner_mode:
            self._delete_active_profile()
            self.group_interface = None
            return

        self._stop_dnsmasq()
        if self._manual_address_interface:
            self._run(
                [
                    "/usr/sbin/ip",
                    "-4",
                    "address",
                    "delete",
                    self.settings.address,
                    "dev",
                    self._manual_address_interface,
                ],
                allow_failure=True,
            )
        self._manual_address_interface = None
        self._manual_peer_seen = False
        self._manual_group_empty_since = None
        self._wpa(self.settings.interface, "p2p_cancel", allow_failure=True)
        group_interface = self.group_interface or self._first_group_interface()
        if group_interface:
            self._wpa(
                self.settings.interface,
                "p2p_group_remove",
                group_interface,
                allow_failure=True,
            )
            for _attempt in range(20):
                if self._first_group_interface() is None:
                    break
                self._sleep(0.1)
        self._wpa(self.settings.interface, "p2p_flush", allow_failure=True)
        self._manual_owner_mode = False
        self.group_interface = None

    def _restore_suspended_wifi(self) -> None:
        profile = self._suspended_wifi_profile
        if not profile:
            return
        try:
            self._run(
                [
                    "/usr/bin/nmcli",
                    "--wait",
                    "45",
                    "connection",
                    "up",
                    "uuid",
                    profile,
                    "ifname",
                    self.settings.interface,
                ],
                timeout=50,
            )
        except WifiDirectError as error:
            raise WifiDirectError(
                "Failed to restore the managed Wi-Fi connection ({})".format(error)
            ) from error
        self._suspended_wifi_profile = None
        self.wifi_mode.clear_suspended_profile()

    def _restore_suspended_wifi_if_allowed(self) -> None:
        # Explicit Direct mode remains authoritative until a later LAN request.
        # Losing a peer therefore returns to discovery without waking the
        # station profile that was deliberately suspended for Direct mode.
        if self._direct_mode_requested():
            return
        self._restore_suspended_wifi()

    def _delete_active_profile(self) -> None:
        profile = self.active_profile or self._active_managed_profile()
        if not profile:
            return
        self._run(
            ["/usr/bin/nmcli", "connection", "down", profile],
            allow_failure=True,
            timeout=20,
        )
        self._run(
            ["/usr/bin/nmcli", "connection", "delete", profile],
            allow_failure=True,
        )
        self.active_profile = None

    def _wpa(
        self,
        interface: str,
        *arguments: str,
        allow_failure: bool = False,
    ) -> subprocess.CompletedProcess:
        return self._run(
            [
                "/usr/sbin/wpa_cli",
                "-p",
                WPA_CONTROL_PATH,
                "-s",
                str(self.wpa_client_path),
                "-i",
                interface,
                *arguments,
            ],
            allow_failure=allow_failure,
        )

    def _run(
        self,
        command: Sequence[str],
        allow_failure: bool = False,
        timeout: int = 15,
    ) -> subprocess.CompletedProcess:
        result = self._run_process(
            list(command),
            check=False,
            capture_output=True,
            text=True,
            timeout=timeout,
        )
        output_lines = [line.strip() for line in result.stdout.splitlines() if line.strip()]
        control_failed = bool(output_lines and output_lines[-1].startswith("FAIL"))
        if not allow_failure and (result.returncode != 0 or control_failed):
            detail = result.stderr.strip() or result.stdout.strip() or "no output"
            raise WifiDirectError(
                "Command failed: {} ({})".format(" ".join(command), detail)
            )
        return result

    def _publish(
        self,
        state: str,
        message: str,
        address: Optional[str] = None,
    ) -> None:
        self._state = state
        payload: Dict[str, object] = {
            "enabled": state in {"DISCOVERABLE", "CONNECTING", "READY"},
            "state": state,
            "message": message,
            "deviceName": self.settings.device_name,
            "mainInterface": self.settings.interface,
            "managementInterface": self.management_interface,
            "groupInterface": self.group_interface,
            "peerAddress": self.active_peer,
            "address": address or self.settings.owner_ip,
            "ownerMode": "manual" if self._manual_owner_mode else "networkmanager",
            "dhcpActive": self._dnsmasq_is_running(),
            "frequencyMhz": self.settings.frequency,
            "updatedAtEpochSeconds": int(time.time()),
        }
        with self._status_lock:
            self.status_path.parent.mkdir(parents=True, exist_ok=True)
            temporary = self.status_path.with_suffix(".tmp")
            with temporary.open("w", encoding="utf-8") as output:
                json.dump(payload, output, separators=(",", ":"), sort_keys=True)
                output.write("\n")
            os.chmod(str(temporary), 0o644)
            os.replace(str(temporary), str(self.status_path))


class WifiDirectRuntime:
    def __init__(self, controller: WifiDirectController) -> None:
        self.controller = controller
        self.loop = None
        self.bus = None
        self.wpa_interface_path: Optional[str] = None
        self.controller._radio_recovery_callback = self._rebind_wpa_signal_receiver

    def run(self) -> int:
        try:
            import dbus
            from dbus.mainloop.glib import DBusGMainLoop
            from gi.repository import GLib
        except ImportError as error:
            raise WifiDirectError("python3-dbus and python3-gi are required") from error

        DBusGMainLoop(set_as_default=True)
        self.bus = dbus.SystemBus()
        self._rebind_wpa_signal_receiver()
        self.controller.prepare()
        self.loop = GLib.MainLoop()

        def stop_loop(_signum, _frame) -> None:
            if self.loop is not None:
                GLib.idle_add(self.loop.quit)

        signal.signal(signal.SIGTERM, stop_loop)
        signal.signal(signal.SIGINT, stop_loop)
        GLib.timeout_add_seconds(2, self.controller.monitor)
        self.loop.run()
        return 0

    def _rebind_wpa_signal_receiver(self) -> None:
        last_error: Optional[WifiDirectError] = None
        for attempt in range(WPA_SIGNAL_REBIND_ATTEMPTS):
            try:
                self._rebind_wpa_signal_receiver_once()
                return
            except WifiDirectError as error:
                last_error = error
                if attempt + 1 < WPA_SIGNAL_REBIND_ATTEMPTS:
                    time.sleep(WPA_SIGNAL_REBIND_SECONDS)
        raise WifiDirectError(
            "wpa_supplicant Direct event receiver did not recover"
        ) from last_error

    def _rebind_wpa_signal_receiver_once(self) -> None:
        if self.bus is None:
            raise WifiDirectError("D-Bus is not initialized")
        # Resolve the replacement before removing the old match. During a radio
        # cycle wpa_supplicant can publish a fresh object path slightly after its
        # control socket starts answering PING; keeping the old receiver until
        # resolution succeeds avoids leaving the daemon with no signal match.
        try:
            interface_path = self._find_wpa_interface_path()
        except Exception as error:
            raise WifiDirectError(
                "Cannot resolve the wpa_supplicant Direct event interface"
            ) from error
        previous_path = self.wpa_interface_path
        try:
            if self.wpa_interface_path is not None:
                self.bus.remove_signal_receiver(
                    self._on_go_negotiation_request,
                    signal_name="GONegotiationRequest",
                    dbus_interface=WPA_P2P_INTERFACE,
                    path=self.wpa_interface_path,
                )
            self.bus.add_signal_receiver(
                self._on_go_negotiation_request,
                signal_name="GONegotiationRequest",
                dbus_interface=WPA_P2P_INTERFACE,
                path=interface_path,
            )
        except Exception as error:
            # A failure after removal is rare, but restoring the prior match is
            # safer than silently running without GO negotiation notifications.
            if previous_path is not None:
                try:
                    self.bus.add_signal_receiver(
                        self._on_go_negotiation_request,
                        signal_name="GONegotiationRequest",
                        dbus_interface=WPA_P2P_INTERFACE,
                        path=previous_path,
                    )
                except Exception:
                    pass
            raise WifiDirectError(
                "Cannot bind the wpa_supplicant Direct event receiver"
            ) from error
        self.wpa_interface_path = interface_path

    def _find_wpa_interface_path(self) -> str:
        import dbus

        if self.bus is None:
            raise WifiDirectError("D-Bus is not initialized")
        root = self.bus.get_object(WPA_DBUS_SERVICE, WPA_DBUS_ROOT)
        root_properties = dbus.Interface(root, DBUS_PROPERTIES_INTERFACE)
        paths = root_properties.Get(WPA_ROOT_INTERFACE, "Interfaces")
        for path in paths:
            interface = self.bus.get_object(WPA_DBUS_SERVICE, path)
            properties = dbus.Interface(interface, DBUS_PROPERTIES_INTERFACE)
            if str(properties.Get(WPA_INTERFACE, "Ifname")) == self.controller.settings.interface:
                return str(path)
        raise WifiDirectError("wpa_supplicant D-Bus interface is unavailable")

    def _on_go_negotiation_request(
        self,
        peer_path,
        device_password_id,
        _peer_go_intent,
    ) -> None:
        if int(device_password_id) != 4:
            self.controller._publish(
                "DISCOVERABLE",
                "Ignored a Wi-Fi Direct request that did not use WPS PBC",
            )
            return
        try:
            peer_address = self._peer_address(str(peer_path))
            self.controller.request_connection(peer_address)
        except (ValueError, WifiDirectError) as error:
            self.controller._publish("ERROR", str(error))

    def _peer_address(self, peer_path: str) -> str:
        address = peer_address_from_path(peer_path)
        if address:
            return address
        if self.bus is None:
            raise WifiDirectError("D-Bus is not initialized")
        peer = self.bus.get_object(WPA_DBUS_SERVICE, peer_path)
        import dbus

        properties = dbus.Interface(peer, DBUS_PROPERTIES_INTERFACE)
        raw_address = properties.Get(WPA_PEER_INTERFACE, "DeviceAddress")
        try:
            return normalize_mac_address("".join("{:02x}".format(int(value)) for value in raw_address))
        except (TypeError, ValueError) as error:
            raise WifiDirectError("Wi-Fi Direct peer address is unavailable") from error


def run_daemon(controller: WifiDirectController) -> int:
    failure_message: Optional[str] = None
    try:
        return WifiDirectRuntime(controller).run()
    except (OSError, ValueError, WifiDirectError) as error:
        failure_message = str(error)
        print("Wi-Fi Direct error: {}".format(error), file=sys.stderr, flush=True)
        return 1
    finally:
        try:
            controller.stop()
        except (OSError, WifiDirectError) as cleanup_error:
            print("Wi-Fi Direct cleanup error: {}".format(cleanup_error), file=sys.stderr)
        if failure_message is not None:
            controller._publish("ERROR", failure_message)


def main(argv: Optional[Sequence[str]] = None) -> int:
    parser = argparse.ArgumentParser(description="Run Jetson NetworkManager Wi-Fi Direct")
    parser.add_argument(
        "--device-config",
        type=Path,
        default=Path("/etc/jetson-control/device.json"),
    )
    parser.add_argument("--status-path", type=Path, default=STATUS_PATH)
    args = parser.parse_args(argv)

    config = DeviceConfig.load(args.device_config)
    controller = WifiDirectController(
        WifiDirectSettings.from_device_config(config),
        status_path=args.status_path,
    )
    if not config.wifi_direct_enabled:
        controller._publish("DISABLED", "Wi-Fi Direct is disabled in device configuration")
        return 0
    return run_daemon(controller)


if __name__ == "__main__":
    raise SystemExit(main())
