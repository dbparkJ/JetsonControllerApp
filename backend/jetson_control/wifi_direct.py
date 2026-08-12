from __future__ import annotations

import argparse
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
from typing import Callable, Dict, List, Optional, Sequence, Tuple

from .config import DeviceConfig


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
DISCOVERY_SECONDS = 600
DISCOVERY_REFRESH_SECONDS = 540


class WifiDirectError(RuntimeError):
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
        status_path: Path = STATUS_PATH,
        sleep: Callable[[float], None] = time.sleep,
    ) -> None:
        self.settings = settings.validated()
        self._run_process = run
        self._sleep = sleep
        self.status_path = status_path
        self.wpa_client_path = status_path.parent / "wpa-cli"
        self.management_interface: Optional[str] = None
        self.group_interface: Optional[str] = None
        self.active_profile: Optional[str] = None
        self.active_peer: Optional[str] = None
        self._state = "UNAVAILABLE"
        self._activation_lock = threading.Lock()
        self._activation_thread: Optional[threading.Thread] = None
        self._status_lock = threading.Lock()
        self._last_discovery = 0.0

    def prepare(self) -> str:
        self._publish("STARTING", "Preparing NetworkManager Wi-Fi Direct")
        self.wpa_client_path.mkdir(parents=True, exist_ok=True, mode=0o700)
        os.chmod(str(self.wpa_client_path), 0o700)
        self._wait_for_wpa_supplicant()
        self._configure_p2p_identity()
        self.management_interface = self._wait_for_management_interface()

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

        self._cleanup_stale_groups()
        self.refresh_discovery()
        self._publish("DISCOVERABLE", "Waiting for the Android connection request")
        return "DISCOVERABLE"

    def refresh_discovery(self) -> None:
        self._wpa(self.settings.interface, "p2p_find", str(DISCOVERY_SECONDS))
        self._last_discovery = time.monotonic()

    def request_connection(self, peer_address: str) -> bool:
        peer = normalize_mac_address(peer_address)
        with self._activation_lock:
            if self._activation_thread is not None and self._activation_thread.is_alive():
                return False
            if self._state == "READY":
                return False
            self._activation_thread = threading.Thread(
                target=self._activate_peer,
                args=(peer,),
                name="wifi-direct-activate",
                daemon=True,
            )
            self._activation_thread.start()
        return True

    def activate_peer_for_test(self, peer_address: str) -> str:
        return self._activate_peer(normalize_mac_address(peer_address))

    def monitor(self) -> bool:
        group_interface = self._first_group_interface()
        if group_interface:
            address = self._interface_address(group_interface)
            if address:
                self.group_interface = group_interface
                if self._state != "READY":
                    self._publish(
                        "READY",
                        "Wi-Fi Direct group is connected",
                        address=address,
                    )
                return True

        if self._state == "READY":
            self.group_interface = None
            self.active_peer = None
            self._delete_active_profile()
            self.refresh_discovery()
            self._publish("DISCOVERABLE", "Wi-Fi Direct disconnected; waiting again")
        elif (
            self._state == "DISCOVERABLE"
            and time.monotonic() - self._last_discovery >= DISCOVERY_REFRESH_SECONDS
        ):
            self.refresh_discovery()
        return True

    def stop(self) -> None:
        activation = self._activation_thread
        if activation is not None and activation.is_alive():
            activation.join(timeout=2)
        self._delete_active_profile()
        self._wpa(self.settings.interface, "p2p_stop_find", allow_failure=True)
        self.group_interface = None
        self.active_peer = None
        self._publish("STOPPED", "Wi-Fi Direct service stopped")

    def _activate_peer(self, peer: str) -> str:
        profile = PROFILE_PREFIX + peer.replace(":", "").lower()
        self.active_profile = profile
        self.active_peer = peer
        self._publish("CONNECTING", "Android requested a Wi-Fi Direct connection")
        try:
            self._run(
                ["/usr/bin/nmcli", "connection", "delete", profile],
                allow_failure=True,
            )
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
            group_interface = self._wait_for_group_interface()
            address = self._wait_for_interface_address(group_interface)
            self.group_interface = group_interface
            self._publish(
                "READY",
                "Wi-Fi Direct group is connected",
                address=address,
            )
            return group_interface
        except (OSError, ValueError, WifiDirectError) as error:
            self._delete_active_profile()
            self.group_interface = None
            self.active_peer = None
            self._publish("ERROR", str(error))
            self._sleep(2)
            try:
                self.refresh_discovery()
                self._publish(
                    "DISCOVERABLE",
                    "Connection failed; waiting for another Android request",
                )
            except WifiDirectError:
                pass
            return ""

    def _wait_for_wpa_supplicant(self) -> None:
        last_error: Optional[Exception] = None
        for _attempt in range(30):
            try:
                self._wpa(self.settings.interface, "ping")
                return
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

    def _wait_for_management_interface(self) -> str:
        preferred = "p2p-dev-{}".format(self.settings.interface)
        for _attempt in range(30):
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
            if not pattern.fullmatch(interface):
                continue
            if interface_type == "P2P-GO":
                self._wpa(
                    self.settings.interface,
                    "p2p_group_remove",
                    interface,
                    allow_failure=True,
                )
                self._sleep(0.2)
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
        return parse_ipv4_address(result.stdout)

    def _wait_for_interface_address(self, interface: str) -> str:
        for _attempt in range(40):
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

    def run(self) -> int:
        try:
            import dbus
            from dbus.mainloop.glib import DBusGMainLoop
            from gi.repository import GLib
        except ImportError as error:
            raise WifiDirectError("python3-dbus and python3-gi are required") from error

        DBusGMainLoop(set_as_default=True)
        self.bus = dbus.SystemBus()
        self.wpa_interface_path = self._find_wpa_interface_path()
        self.bus.add_signal_receiver(
            self._on_go_negotiation_request,
            signal_name="GONegotiationRequest",
            dbus_interface=WPA_P2P_INTERFACE,
            path=self.wpa_interface_path,
        )
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
