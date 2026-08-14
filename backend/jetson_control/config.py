from __future__ import annotations

import ipaddress
import json
import os
import re
import uuid
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Dict, Mapping


CONFIG_ID_PATTERN = re.compile(r"^[a-zA-Z0-9][a-zA-Z0-9_.-]{0,63}$")
UNIT_PATTERN = re.compile(r"^[a-zA-Z0-9][a-zA-Z0-9_.@:-]{0,127}$")


@dataclass(frozen=True)
class RuntimePaths:
    device_config: Path = field(
        default_factory=lambda: Path(
            os.environ.get(
                "JETSON_CONTROL_DEVICE_CONFIG",
                "/etc/jetson-control/device.json",
            )
        )
    )
    storage_roots: Path = field(
        default_factory=lambda: Path(
            os.environ.get(
                "JETSON_CONTROL_STORAGE_ROOTS",
                "/etc/jetson-control/storage_roots.json",
            )
        )
    )
    upload_targets: Path = field(
        default_factory=lambda: Path(
            os.environ.get(
                "JETSON_CONTROL_UPLOAD_TARGETS",
                "/etc/jetson-control/upload_targets.json",
            )
        )
    )
    tls_certificate: Path = field(
        default_factory=lambda: Path(
            os.environ.get(
                "JETSON_CONTROL_TLS_CERTIFICATE",
                "/etc/jetson-control/tls.crt",
            )
        )
    )
    state_dir: Path = field(
        default_factory=lambda: Path(
            os.environ.get(
                "JETSON_CONTROL_STATE_DIR",
                "/var/lib/jetson-control",
            )
        )
    )
    pipeline_registry: Path = field(
        default_factory=lambda: Path(
            os.environ.get(
                "JETSON_CONTROL_PIPELINE_REGISTRY",
                "/opt/jetson-pipelines",
            )
        )
    )
    pipeline_registrar: Path = field(
        default_factory=lambda: Path(
            os.environ.get(
                "JETSON_CONTROL_PIPELINE_REGISTRAR",
                "/opt/jetson-control/register-pipeline.py",
            )
        )
    )
    pipeline_logs: Path = field(
        default_factory=lambda: Path(
            os.environ.get(
                "JETSON_CONTROL_PIPELINE_LOGS",
                "/var/log/jetson-pipelines",
            )
        )
    )


@dataclass(frozen=True)
class DeviceConfig:
    device_id: str
    device_name: str
    bootstrap_secret: bytes
    controlled_services: tuple[str, ...]
    service_flags: Mapping[str, str]
    allow_power_commands: bool
    wifi_interface: str
    pipeline_user: str = "root"
    wifi_direct_enabled: bool = True
    wifi_direct_frequency: int = 2412
    wifi_direct_address: str = "192.168.49.1/24"

    @classmethod
    def load(cls, path: Path) -> "DeviceConfig":
        raw = load_json_object(path)

        try:
            device_id = str(uuid.UUID(str(raw["device_id"]))).lower()
            device_name = str(raw["device_name"]).strip()
            bootstrap_secret = bytes.fromhex(str(raw["bootstrap_secret_hex"]))
        except (KeyError, TypeError, ValueError) as error:
            raise ValueError(f"Invalid device configuration: {path}") from error

        if (
            not device_name
            or len(device_name.encode("utf-8")) > 64
            or any(ord(character) < 32 or ord(character) == 127 for character in device_name)
        ):
            raise ValueError("device_name must contain 1 to 64 UTF-8 bytes")
        if len(bootstrap_secret) != 32:
            raise ValueError("bootstrap_secret_hex must contain exactly 32 bytes")

        controlled_services_value = raw.get("controlled_services", [])
        if not isinstance(controlled_services_value, list) or any(
            not isinstance(unit, str) for unit in controlled_services_value
        ):
            raise ValueError("controlled_services must be an array of systemd units")
        controlled_services = tuple(
            dict.fromkeys(unit.strip() for unit in controlled_services_value)
        )
        if any(not UNIT_PATTERN.fullmatch(unit) for unit in controlled_services):
            raise ValueError("controlled_services contains an invalid systemd unit")

        service_flags_value = raw.get("service_flags", {})
        if not isinstance(service_flags_value, dict):
            raise ValueError("service_flags must be an object")
        service_flags = {
            name: str(service_flags_value.get(name, "")).strip()
            for name in ("camera", "lidar", "gnss", "imu", "mms")
        }
        if any(unit and not UNIT_PATTERN.fullmatch(unit) for unit in service_flags.values()):
            raise ValueError("service_flags contains an invalid systemd unit")

        allow_power_commands = raw.get("allow_power_commands", False)
        if not isinstance(allow_power_commands, bool):
            raise ValueError("allow_power_commands must be a boolean")

        wifi_interface_value = raw.get("wifi_interface", "wlan0")
        if not isinstance(wifi_interface_value, str):
            raise ValueError("wifi_interface must be a string")
        wifi_interface = wifi_interface_value.strip()
        if not re.fullmatch(r"[a-zA-Z0-9_.:-]{1,32}", wifi_interface):
            raise ValueError("wifi_interface is invalid")

        pipeline_user_value = raw.get("pipeline_user", "root")
        if not isinstance(pipeline_user_value, str):
            raise ValueError("pipeline_user must be a string")
        pipeline_user = pipeline_user_value.strip()
        if not re.fullmatch(r"[a-z_][a-z0-9_-]{0,31}", pipeline_user):
            raise ValueError("pipeline_user is invalid")

        wifi_direct_frequency = raw.get("wifi_direct_frequency", 2412)
        valid_wifi_direct_frequencies = (
            set(range(2412, 2473, 5))
            | {2484}
            | set(range(5180, 5241, 20))
            | set(range(5260, 5321, 20))
            | set(range(5500, 5721, 20))
            | set(range(5745, 5806, 20))
        )
        if (
            isinstance(wifi_direct_frequency, bool)
            or not isinstance(wifi_direct_frequency, int)
            or wifi_direct_frequency not in valid_wifi_direct_frequencies
        ):
            raise ValueError("wifi_direct_frequency is invalid")

        wifi_direct_address_value = raw.get("wifi_direct_address", "192.168.49.1/24")
        if not isinstance(wifi_direct_address_value, str):
            raise ValueError("wifi_direct_address must be a string")
        wifi_direct_address = wifi_direct_address_value.strip()
        try:
            parsed_wifi_direct_address = ipaddress.ip_interface(wifi_direct_address)
        except ValueError as error:
            raise ValueError("wifi_direct_address is invalid") from error
        if parsed_wifi_direct_address.version != 4:
            raise ValueError("wifi_direct_address must be IPv4")

        wifi_direct_enabled = raw.get("wifi_direct_enabled", True)
        if not isinstance(wifi_direct_enabled, bool):
            raise ValueError("wifi_direct_enabled must be a boolean")

        return cls(
            device_id=device_id,
            device_name=device_name,
            bootstrap_secret=bootstrap_secret,
            controlled_services=controlled_services,
            service_flags=service_flags,
            allow_power_commands=allow_power_commands,
            wifi_interface=wifi_interface,
            pipeline_user=pipeline_user,
            wifi_direct_enabled=wifi_direct_enabled,
            wifi_direct_frequency=wifi_direct_frequency,
            wifi_direct_address=wifi_direct_address,
        )


def load_json_object(path: Path) -> Dict[str, Any]:
    try:
        with path.open("r", encoding="utf-8") as config_file:
            value = json.load(config_file)
    except FileNotFoundError as error:
        raise RuntimeError(f"Required configuration is missing: {path}") from error
    except json.JSONDecodeError as error:
        raise RuntimeError(f"Configuration is not valid JSON: {path}") from error

    if not isinstance(value, dict):
        raise RuntimeError(f"Configuration must contain a JSON object: {path}")
    return value


def validate_config_id(value: str, kind: str) -> str:
    if not CONFIG_ID_PATTERN.fullmatch(value):
        raise ValueError(f"Invalid {kind} id: {value!r}")
    return value
