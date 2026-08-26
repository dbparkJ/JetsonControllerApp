#!/usr/bin/env python3
from __future__ import annotations

import hashlib
import hmac
import json
import secrets
import struct
import time
import uuid
from typing import Dict, Optional, Tuple

import dbus
import dbus.exceptions
import dbus.mainloop.glib
import dbus.service
from gi.repository import GLib

from .ble_crypto import decrypt_wifi_payload, derive_session_key
from .commands import CommandRunner
from .config import DeviceConfig, RuntimePaths
from .filesystem import StorageRegistry
from .network import WifiProvisioner, decode_wifi_payload
from .status import StatusCollector


BLUEZ = "org.bluez"
OBJECT_MANAGER = "org.freedesktop.DBus.ObjectManager"
PROPERTIES = "org.freedesktop.DBus.Properties"
GATT_MANAGER = "org.bluez.GattManager1"
GATT_SERVICE = "org.bluez.GattService1"
GATT_CHARACTERISTIC = "org.bluez.GattCharacteristic1"
ADVERTISEMENT_MANAGER = "org.bluez.LEAdvertisingManager1"
ADVERTISEMENT_INTERFACE = "org.bluez.LEAdvertisement1"

SERVICE_UUID = "a1000000-0000-0000-0000-000000000001"
COMMAND_UUID = "a1000000-0000-0000-0000-000000000002"
STATUS_UUID = "a1000000-0000-0000-0000-000000000003"
SYSTEM_INFO_UUID = "a1000000-0000-0000-0000-000000000004"
WIFI_CONFIG_UUID = "a1000000-0000-0000-0000-000000000005"
DEVICE_ID_UUID = "a1000000-0000-0000-0000-000000000006"
AUTH_CHALLENGE_UUID = "a1000000-0000-0000-0000-000000000007"
AUTH_RESPONSE_UUID = "a1000000-0000-0000-0000-000000000008"
AUTH_STATE_UUID = "a1000000-0000-0000-0000-000000000009"

MAGIC = 0x5A
PROTOCOL_VERSION = 0x01
CMD_START = 0x01
CMD_STOP = 0x02
CMD_RESTART = 0x03
CMD_REBOOT = 0x04
CMD_SHUTDOWN = 0x05
CMD_GET_STATUS = 0x06
CMD_SET_WIFI = 0x07
CMD_REQUEST_WIFI_DIRECT = 0x08

AUTH_CONTEXT = b"JETSONCTRL1|"
CHALLENGE_TTL_SECONDS = 30
SESSION_TTL_SECONDS = 600

# BlueZ 5.55 serializes ServiceUUIDs into the primary advertising packet and
# LocalName into the scan response. Keep both structures comfortably below the
# 31-byte legacy limit; some Realtek/Samsung combinations are unreliable at the
# exact boundary even though BlueZ accepts it.
LEGACY_ADVERTISING_LIMIT = 31
DISCOVERABLE_FLAGS_AD_SIZE = 3
UUID128_AD_SIZE = 18
AD_STRUCTURE_HEADER_SIZE = 2
SCAN_RESPONSE_HEADROOM = 5
MAX_ADVERTISED_NAME_BYTES = (
    LEGACY_ADVERTISING_LIMIT
    - AD_STRUCTURE_HEADER_SIZE
    - SCAN_RESPONSE_HEADROOM
)
MAX_WIFI_SSID_BYTES = 32
WIFI_STATUS_FLAG_CONNECTED = 0x01


class InvalidArgs(dbus.exceptions.DBusException):
    _dbus_error_name = "org.freedesktop.DBus.Error.InvalidArgs"


class NotSupported(dbus.exceptions.DBusException):
    _dbus_error_name = "org.bluez.Error.NotSupported"


class NotAuthorized(dbus.exceptions.DBusException):
    _dbus_error_name = "org.bluez.Error.NotAuthorized"


class InvalidLength(dbus.exceptions.DBusException):
    _dbus_error_name = "org.bluez.Error.InvalidValueLength"


class InvalidOffset(dbus.exceptions.DBusException):
    _dbus_error_name = "org.bluez.Error.InvalidOffset"


class Failed(dbus.exceptions.DBusException):
    _dbus_error_name = "org.bluez.Error.Failed"


def dbus_bytes(data: bytes) -> dbus.Array:
    return dbus.Array([dbus.Byte(value) for value in data], signature="y")


def device_from(options: Dict[str, object]) -> str:
    value = options.get("device")
    return str(value) if value else ""


def offset_from(options: Dict[str, object]) -> int:
    try:
        return int(options.get("offset", 0))
    except (TypeError, ValueError):
        return 0


def sliced(data: bytes, options: Dict[str, object]) -> dbus.Array:
    offset = offset_from(options)
    if offset < 0 or offset > len(data):
        raise InvalidOffset()
    return dbus_bytes(data[offset:])


def advertised_local_name(device_name: str) -> str:
    encoded = device_name.encode("utf-8")
    if len(encoded) <= MAX_ADVERTISED_NAME_BYTES:
        return device_name
    return encoded[:MAX_ADVERTISED_NAME_BYTES].decode("utf-8", errors="ignore")


def utf8_prefix(value: str, maximum_bytes: int) -> bytes:
    encoded = value.encode("utf-8")
    if len(encoded) <= maximum_bytes:
        return encoded
    return encoded[:maximum_bytes].decode("utf-8", errors="ignore").encode("utf-8")


def encode_status_packet(
    values: Tuple[int, int, int, int, int, int, int, int, bool, str]
) -> bytes:
    base = struct.pack("<BBBbBBII", *values[:8])
    wifi_connected = bool(values[8])
    wifi_ssid = utf8_prefix(values[9], MAX_WIFI_SSID_BYTES) if wifi_connected else b""
    wifi_flags = WIFI_STATUS_FLAG_CONNECTED if wifi_connected else 0
    return base + struct.pack("<BB", wifi_flags, len(wifi_ssid)) + wifi_ssid


def legacy_advertising_payload_sizes(device_name: str) -> Tuple[int, int]:
    """Return primary ADV and scan-response sizes produced by BlueZ 5.55."""
    local_name = advertised_local_name(device_name).encode("utf-8")
    primary_size = DISCOVERABLE_FLAGS_AD_SIZE + UUID128_AD_SIZE
    scan_response_size = AD_STRUCTURE_HEADER_SIZE + len(local_name)
    return primary_size, scan_response_size


def parse_command_frame(data: bytes) -> Tuple[int, bytes]:
    if len(data) < 5:
        raise ValueError("Command frame is too short")
    magic, version, command, payload_length = data[:4]
    if magic != MAGIC or version != PROTOCOL_VERSION:
        raise ValueError("Command frame header is invalid")
    if len(data) != 4 + payload_length + 1:
        raise ValueError("Command frame length is invalid")
    if sum(data[:-1]) & 0xFF != data[-1]:
        raise ValueError("Command frame checksum is invalid")
    return command, data[4 : 4 + payload_length]


class BleAuthenticator:
    def __init__(self, config: DeviceConfig) -> None:
        self.config = config
        self.device_id_bytes = uuid.UUID(config.device_id).bytes
        self.challenges: Dict[str, Tuple[bytes, float]] = {}
        self.sessions: Dict[str, Tuple[float, bytes]] = {}

    def cleanup(self) -> None:
        now = time.monotonic()
        self.challenges = {
            device: value
            for device, value in self.challenges.items()
            if now - value[1] <= CHALLENGE_TTL_SECONDS
        }
        self.sessions = {
            device: value
            for device, value in self.sessions.items()
            if now < value[0]
        }

    def challenge(self, device: str) -> bytes:
        self.cleanup()
        if not device:
            raise NotAuthorized("Missing Bluetooth peer")
        value = secrets.token_bytes(16)
        self.challenges[device] = (value, time.monotonic())
        print("[AUTH] challenge issued", flush=True)
        return value

    def verify(self, device: str, response: bytes) -> bool:
        self.cleanup()
        challenge_entry = self.challenges.pop(device, None)
        if challenge_entry is None:
            self.sessions.pop(device, None)
            return False
        challenge, issued_at = challenge_entry
        if time.monotonic() - issued_at > CHALLENGE_TTL_SECONDS:
            self.sessions.pop(device, None)
            return False
        message = AUTH_CONTEXT + self.device_id_bytes + b"|" + challenge
        expected = hmac.new(
            self.config.bootstrap_secret, message, hashlib.sha256
        ).digest()[:16]
        accepted = hmac.compare_digest(expected, response)
        if accepted:
            session_key = derive_session_key(
                self.config.bootstrap_secret,
                self.device_id_bytes,
                challenge,
            )
            self.sessions[device] = (
                time.monotonic() + SESSION_TTL_SECONDS,
                session_key,
            )
            print("[AUTH] peer authorized", flush=True)
        else:
            self.sessions.pop(device, None)
            print("[AUTH] peer rejected", flush=True)
        return accepted

    def authorized(self, device: str) -> bool:
        self.cleanup()
        return bool(device) and device in self.sessions

    def refresh(self, device: str) -> None:
        if self.authorized(device):
            _, session_key = self.sessions[device]
            self.sessions[device] = (
                time.monotonic() + SESSION_TTL_SECONDS,
                session_key,
            )

    def session_key(self, device: str) -> bytes:
        if not self.authorized(device):
            raise NotAuthorized("Authenticate first")
        return self.sessions[device][1]

    def has_session(self) -> bool:
        self.cleanup()
        return bool(self.sessions)


class Application(dbus.service.Object):
    PATH = "/com/jm/jetson"

    def __init__(self, bus: dbus.SystemBus) -> None:
        self.path = self.PATH
        self.services = []
        super().__init__(bus, self.path)

    def get_path(self) -> dbus.ObjectPath:
        return dbus.ObjectPath(self.path)

    def add_service(self, service: "Service") -> None:
        self.services.append(service)

    @dbus.service.method(OBJECT_MANAGER, out_signature="a{oa{sa{sv}}}")
    def GetManagedObjects(self):
        response = {}
        for service in self.services:
            response[service.get_path()] = service.get_properties()
            for characteristic in service.characteristics:
                response[characteristic.get_path()] = characteristic.get_properties()
        return response


class Service(dbus.service.Object):
    PATH_BASE = "/com/jm/jetson/service"

    def __init__(self, bus: dbus.SystemBus, index: int, service_uuid: str) -> None:
        self.path = f"{self.PATH_BASE}{index}"
        self.uuid = service_uuid
        self.characteristics = []
        super().__init__(bus, self.path)

    def get_path(self) -> dbus.ObjectPath:
        return dbus.ObjectPath(self.path)

    def add(self, characteristic: "Characteristic") -> None:
        self.characteristics.append(characteristic)

    def get_properties(self) -> Dict[str, object]:
        return {
            GATT_SERVICE: {
                "UUID": dbus.String(self.uuid),
                "Primary": dbus.Boolean(True),
                "Characteristics": dbus.Array(
                    [item.get_path() for item in self.characteristics], signature="o"
                ),
            }
        }

    @dbus.service.method(PROPERTIES, in_signature="s", out_signature="a{sv}")
    def GetAll(self, interface):
        if interface != GATT_SERVICE:
            raise InvalidArgs()
        return self.get_properties()[GATT_SERVICE]


class Characteristic(dbus.service.Object):
    def __init__(
        self,
        bus: dbus.SystemBus,
        index: int,
        characteristic_uuid: str,
        flags: list[str],
        service: Service,
    ) -> None:
        self.path = f"{service.path}/char{index}"
        self.uuid = characteristic_uuid
        self.flags = flags
        self.service = service
        super().__init__(bus, self.path)

    def get_path(self) -> dbus.ObjectPath:
        return dbus.ObjectPath(self.path)

    def get_properties(self) -> Dict[str, object]:
        return {
            GATT_CHARACTERISTIC: {
                "Service": self.service.get_path(),
                "UUID": dbus.String(self.uuid),
                "Flags": dbus.Array(self.flags, signature="s"),
                "Descriptors": dbus.Array([], signature="o"),
            }
        }

    @dbus.service.method(PROPERTIES, in_signature="s", out_signature="a{sv}")
    def GetAll(self, interface):
        if interface != GATT_CHARACTERISTIC:
            raise InvalidArgs()
        return self.get_properties()[GATT_CHARACTERISTIC]

    @dbus.service.method(GATT_CHARACTERISTIC, in_signature="a{sv}", out_signature="ay")
    def ReadValue(self, options):
        raise NotSupported()

    @dbus.service.method(GATT_CHARACTERISTIC, in_signature="aya{sv}", out_signature="")
    def WriteValue(self, value, options):
        raise NotSupported()

    @dbus.service.method(GATT_CHARACTERISTIC)
    def StartNotify(self):
        raise NotSupported()

    @dbus.service.method(GATT_CHARACTERISTIC)
    def StopNotify(self):
        raise NotSupported()

    @dbus.service.signal(PROPERTIES, signature="sa{sv}as")
    def PropertiesChanged(self, interface, changed, invalidated):
        pass


class CommandCharacteristic(Characteristic):
    def __init__(self, bus, index, service, auth, commands, wifi) -> None:
        super().__init__(bus, index, COMMAND_UUID, ["write"], service)
        self.auth = auth
        self.commands = commands
        self.wifi = wifi

    def WriteValue(self, value, options) -> None:
        device = device_from(options)
        if not self.auth.authorized(device):
            raise NotAuthorized("Authenticate first")
        try:
            command, payload = parse_command_frame(bytes(int(item) for item in value))
            actions = {
                CMD_START: "start-system",
                CMD_STOP: "stop-system",
                CMD_RESTART: "restart-services",
                CMD_REBOOT: "reboot",
                CMD_SHUTDOWN: "shutdown",
            }
            if command in actions:
                self.commands.execute(actions[command])
            elif command == CMD_GET_STATUS:
                if payload:
                    raise ValueError("GET_STATUS does not accept a payload")
            elif command == CMD_SET_WIFI:
                plaintext = decrypt_wifi_payload(
                    payload,
                    self.auth.session_key(device),
                    self.auth.device_id_bytes,
                )
                ssid, password, hidden = decode_wifi_payload(plaintext)
                self.wifi.submit(ssid, password, hidden)
            elif command == CMD_REQUEST_WIFI_DIRECT:
                if payload:
                    raise ValueError("REQUEST_WIFI_DIRECT does not accept a payload")
                self.wifi.request_direct_mode()
            else:
                raise NotSupported("Unknown command")
            self.auth.refresh(device)
            print(f"[CMD] 0x{command:02X} accepted", flush=True)
        except dbus.exceptions.DBusException:
            raise
        except Exception as error:
            print(f"[CMD] 0x{command if 'command' in locals() else 0:02X} failed", flush=True)
            raise Failed(str(error))


class StatusCharacteristic(Characteristic):
    def __init__(self, bus, index, service, auth, collector) -> None:
        super().__init__(bus, index, STATUS_UUID, ["read", "notify"], service)
        self.auth = auth
        self.collector = collector
        self.notifying = False
        self.timer: Optional[int] = None

    def packet(self) -> bytes:
        return encode_status_packet(self.collector.ble_packet_values())

    def ReadValue(self, options) -> dbus.Array:
        device = device_from(options)
        if not self.auth.authorized(device):
            raise NotAuthorized("Authenticate first")
        self.auth.refresh(device)
        return sliced(self.packet(), options)

    def emit(self) -> bool:
        if not self.notifying:
            return False
        self.PropertiesChanged(
            GATT_CHARACTERISTIC, {"Value": dbus_bytes(self.packet())}, []
        )
        return True

    def StartNotify(self) -> None:
        if not self.auth.has_session():
            raise NotAuthorized("Authenticate first")
        if self.notifying:
            return
        self.notifying = True
        self.emit()
        self.timer = GLib.timeout_add(2000, self.emit)
        print("[STATUS] notifications started", flush=True)

    def StopNotify(self) -> None:
        self.notifying = False
        if self.timer is not None:
            GLib.source_remove(self.timer)
            self.timer = None
        print("[STATUS] notifications stopped", flush=True)


class SystemInfoCharacteristic(Characteristic):
    def __init__(self, bus, index, service, config) -> None:
        super().__init__(bus, index, SYSTEM_INFO_UUID, ["read"], service)
        self.config = config

    def ReadValue(self, options) -> dbus.Array:
        value = json.dumps(
            {"api": 1, "name": self.config.device_name, "id": self.config.device_id},
            separators=(",", ":"),
        ).encode("utf-8")
        return sliced(value, options)


class WifiCharacteristic(Characteristic):
    def __init__(self, bus, index, service, auth, wifi) -> None:
        super().__init__(bus, index, WIFI_CONFIG_UUID, ["write"], service)
        self.auth = auth
        self.wifi = wifi

    def WriteValue(self, value, options) -> None:
        device = device_from(options)
        if not self.auth.authorized(device):
            raise NotAuthorized("Authenticate first")
        try:
            plaintext = decrypt_wifi_payload(
                bytes(int(item) for item in value),
                self.auth.session_key(device),
                self.auth.device_id_bytes,
            )
            ssid, password, hidden = decode_wifi_payload(plaintext)
            self.wifi.submit(ssid, password, hidden)
            self.auth.refresh(device)
        except Exception as error:
            raise Failed(str(error))


class DeviceIdCharacteristic(Characteristic):
    def __init__(self, bus, index, service, config) -> None:
        super().__init__(bus, index, DEVICE_ID_UUID, ["read"], service)
        self.value = uuid.UUID(config.device_id).bytes

    def ReadValue(self, options) -> dbus.Array:
        return sliced(self.value, options)


class ChallengeCharacteristic(Characteristic):
    def __init__(self, bus, index, service, auth) -> None:
        super().__init__(bus, index, AUTH_CHALLENGE_UUID, ["read"], service)
        self.auth = auth

    def ReadValue(self, options) -> dbus.Array:
        if offset_from(options) != 0:
            raise InvalidOffset()
        return dbus_bytes(self.auth.challenge(device_from(options)))


class ResponseCharacteristic(Characteristic):
    def __init__(self, bus, index, service, auth) -> None:
        super().__init__(bus, index, AUTH_RESPONSE_UUID, ["write"], service)
        self.auth = auth

    def WriteValue(self, value, options) -> None:
        response = bytes(int(item) for item in value)
        if len(response) != 16:
            raise InvalidLength()
        self.auth.verify(device_from(options), response)


class AuthStateCharacteristic(Characteristic):
    def __init__(self, bus, index, service, auth) -> None:
        super().__init__(bus, index, AUTH_STATE_UUID, ["read"], service)
        self.auth = auth

    def ReadValue(self, options) -> dbus.Array:
        state = 1 if self.auth.authorized(device_from(options)) else 0
        return dbus_bytes(bytes((state,)))


class JetsonService(Service):
    def __init__(self, bus, config, auth, collector, commands, wifi) -> None:
        super().__init__(bus, 0, SERVICE_UUID)
        self.add(CommandCharacteristic(bus, 0, self, auth, commands, wifi))
        self.add(StatusCharacteristic(bus, 1, self, auth, collector))
        self.add(SystemInfoCharacteristic(bus, 2, self, config))
        self.add(WifiCharacteristic(bus, 3, self, auth, wifi))
        self.add(DeviceIdCharacteristic(bus, 4, self, config))
        self.add(ChallengeCharacteristic(bus, 5, self, auth))
        self.add(ResponseCharacteristic(bus, 6, self, auth))
        self.add(AuthStateCharacteristic(bus, 7, self, auth))


class Advertisement(dbus.service.Object):
    PATH = "/com/jm/jetson/advertisement0"

    def __init__(self, bus, device_name: str) -> None:
        self.path = self.PATH
        self.device_name = advertised_local_name(device_name)
        super().__init__(bus, self.path)

    def get_path(self) -> dbus.ObjectPath:
        return dbus.ObjectPath(self.path)

    def get_properties(self) -> Dict[str, object]:
        return {
            ADVERTISEMENT_INTERFACE: {
                "Type": dbus.String("peripheral"),
                # Per-advertisement discoverability adds the standard LE
                # General Discoverable flags to the primary packet without
                # exposing the adapter to classic Bluetooth pairing.
                "Discoverable": dbus.Boolean(True),
                "ServiceUUIDs": dbus.Array([SERVICE_UUID], signature="s"),
                "LocalName": dbus.String(self.device_name),
            }
        }

    @dbus.service.method(PROPERTIES, in_signature="s", out_signature="a{sv}")
    def GetAll(self, interface):
        if interface != ADVERTISEMENT_INTERFACE:
            raise InvalidArgs()
        return self.get_properties()[ADVERTISEMENT_INTERFACE]

    @dbus.service.method(ADVERTISEMENT_INTERFACE)
    def Release(self):
        print("[BLE] advertisement released", flush=True)


def find_adapter(bus: dbus.SystemBus) -> Optional[str]:
    manager = dbus.Interface(bus.get_object(BLUEZ, "/"), OBJECT_MANAGER)
    for path, interfaces in manager.GetManagedObjects().items():
        if GATT_MANAGER in interfaces and ADVERTISEMENT_MANAGER in interfaces:
            return str(path)
    return None


def main() -> None:
    paths = RuntimePaths()
    config = DeviceConfig.load(paths.device_config)
    auth = BleAuthenticator(config)
    storage = StorageRegistry(paths.storage_roots)
    collector = StatusCollector(config, storage_path=storage.primary_path())
    commands = CommandRunner(config)
    wifi = WifiProvisioner(
        config.wifi_interface,
        coordinate_wifi_direct=config.wifi_direct_enabled,
    )

    dbus.mainloop.glib.DBusGMainLoop(set_as_default=True)
    bus = dbus.SystemBus()
    adapter_path = find_adapter(bus)
    if adapter_path is None:
        raise RuntimeError("No Bluetooth adapter supports GATT and LE advertising")

    adapter = bus.get_object(BLUEZ, adapter_path)
    dbus.Interface(adapter, PROPERTIES).Set(
        "org.bluez.Adapter1", "Powered", dbus.Boolean(True)
    )
    gatt_manager = dbus.Interface(adapter, GATT_MANAGER)
    advertisement_manager = dbus.Interface(adapter, ADVERTISEMENT_MANAGER)

    application = Application(bus)
    application.add_service(
        JetsonService(bus, config, auth, collector, commands, wifi)
    )
    advertisement = Advertisement(bus, config.device_name)
    loop = GLib.MainLoop()
    registered = {"application": False, "advertisement": False}

    def stop() -> bool:
        if registered["advertisement"]:
            try:
                advertisement_manager.UnregisterAdvertisement(advertisement.get_path())
            except Exception:
                pass
        if registered["application"]:
            try:
                gatt_manager.UnregisterApplication(application.get_path())
            except Exception:
                pass
        loop.quit()
        return False

    def advertisement_ready() -> None:
        registered["advertisement"] = True
        print(
            f"[READY] name={config.device_name} service={SERVICE_UUID} "
            f"device_id={config.device_id}",
            flush=True,
        )

    def advertisement_failed(error: Exception) -> None:
        print(f"[ERROR] advertisement registration failed: {error}", flush=True)
        stop()

    def application_ready() -> None:
        registered["application"] = True
        print("[GATT] application registered", flush=True)
        advertisement_manager.RegisterAdvertisement(
            advertisement.get_path(), {},
            reply_handler=advertisement_ready,
            error_handler=advertisement_failed,
        )

    def application_failed(error: Exception) -> None:
        print(f"[ERROR] GATT registration failed: {error}", flush=True)
        stop()

    GLib.unix_signal_add(GLib.PRIORITY_DEFAULT, 2, stop)
    GLib.unix_signal_add(GLib.PRIORITY_DEFAULT, 15, stop)
    print(f"[START] adapter={adapter_path} name={config.device_name}", flush=True)
    gatt_manager.RegisterApplication(
        application.get_path(), {},
        reply_handler=application_ready,
        error_handler=application_failed,
    )
    loop.run()


if __name__ == "__main__":
    main()
