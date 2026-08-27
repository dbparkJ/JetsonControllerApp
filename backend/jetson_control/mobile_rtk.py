from __future__ import annotations

import ipaddress
import json
import os
import stat
import time
from pathlib import Path
from typing import Dict, Optional

from .config import validate_config_id


MOBILE_RTK_RELAY_SCHEMA_VERSION = 1
MOBILE_RTK_RELAY_LEASE_MILLIS = 30_000


class MobileRtkRelayRegistry:
    """Persist a short-lived, non-secret route from a pipeline to the phone."""

    def __init__(
        self,
        path: Path,
        *,
        clock_millis=lambda: int(time.time() * 1000),
        owner_uid: Optional[int] = 0,
    ) -> None:
        self.path = path
        self.clock_millis = clock_millis
        self.owner_uid = owner_uid

    def register(self, pipeline_id: str, relay_host: str, relay_port: int) -> Dict[str, object]:
        pipeline_id = validate_config_id(pipeline_id, "pipeline")
        try:
            address = ipaddress.ip_address(relay_host)
        except ValueError as error:
            raise ValueError("Mobile RTK relay host is invalid") from error
        if address.version != 4 or address.is_unspecified or address.is_multicast:
            raise ValueError("Mobile RTK relay host is invalid")
        if isinstance(relay_port, bool) or not isinstance(relay_port, int):
            raise ValueError("Mobile RTK relay port is invalid")
        if not 1024 <= relay_port <= 65535:
            raise ValueError("Mobile RTK relay port is invalid")

        expires_at = self.clock_millis() + MOBILE_RTK_RELAY_LEASE_MILLIS
        value: Dict[str, object] = {
            "schemaVersion": MOBILE_RTK_RELAY_SCHEMA_VERSION,
            "pipelineId": pipeline_id,
            "relayHost": str(address),
            "relayPort": relay_port,
            "expiresAtEpochMillis": expires_at,
        }
        self._atomic_write(value)
        return {**value, "active": True}

    def unregister(self, pipeline_id: str) -> bool:
        pipeline_id = validate_config_id(pipeline_id, "pipeline")
        current = self.read(require_active=False)
        if current is not None and current.get("pipelineId") != pipeline_id:
            return False
        try:
            self.path.unlink()
            return True
        except FileNotFoundError:
            return False

    def read(self, *, require_active: bool = True) -> Optional[Dict[str, object]]:
        descriptor: Optional[int] = None
        try:
            flags = os.O_RDONLY | getattr(os, "O_CLOEXEC", 0)
            if hasattr(os, "O_NOFOLLOW"):
                flags |= os.O_NOFOLLOW
            descriptor = os.open(self.path, flags)
            metadata = os.fstat(descriptor)
            if (
                not stat.S_ISREG(metadata.st_mode)
                or metadata.st_mode & (stat.S_IWGRP | stat.S_IWOTH)
                or (self.owner_uid is not None and metadata.st_uid != self.owner_uid)
            ):
                return None
            with os.fdopen(descriptor, "r", encoding="utf-8") as source:
                descriptor = None
                value = json.load(source)
        except (FileNotFoundError, json.JSONDecodeError, OSError, UnicodeDecodeError):
            return None
        finally:
            if descriptor is not None:
                os.close(descriptor)
        if not isinstance(value, dict) or value.get("schemaVersion") != 1:
            return None
        try:
            validate_config_id(value.get("pipelineId"), "pipeline")
            address = ipaddress.ip_address(value.get("relayHost"))
        except (TypeError, ValueError):
            return None
        port = value.get("relayPort")
        expires_at = value.get("expiresAtEpochMillis")
        if (
            address.version != 4
            or isinstance(port, bool)
            or not isinstance(port, int)
            or not 1024 <= port <= 65535
            or isinstance(expires_at, bool)
            or not isinstance(expires_at, int)
        ):
            return None
        if require_active and expires_at <= self.clock_millis():
            return None
        return value

    def _atomic_write(self, value: Dict[str, object]) -> None:
        self.path.parent.mkdir(parents=True, exist_ok=True)
        temporary = self.path.with_name(
            f".{self.path.name}.{os.getpid()}.{time.monotonic_ns()}.tmp"
        )
        encoded = (json.dumps(value, separators=(",", ":")) + "\n").encode("utf-8")
        try:
            flags = os.O_WRONLY | os.O_CREAT | os.O_EXCL | getattr(os, "O_CLOEXEC", 0)
            if hasattr(os, "O_NOFOLLOW"):
                flags |= os.O_NOFOLLOW
            descriptor = os.open(temporary, flags, 0o644)
            try:
                # The API service uses a restrictive umask, while pipelines may
                # run unprivileged. This file only contains a short-lived route.
                os.fchmod(descriptor, 0o644)
                with os.fdopen(descriptor, "wb") as output:
                    descriptor = -1
                    output.write(encoded)
                    output.flush()
                    os.fsync(output.fileno())
            finally:
                if descriptor >= 0:
                    os.close(descriptor)
            os.replace(temporary, self.path)
        finally:
            try:
                temporary.unlink()
            except FileNotFoundError:
                pass
