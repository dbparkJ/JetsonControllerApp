from __future__ import annotations

import hashlib
import hmac
import re
import secrets
import threading
import time
from collections import OrderedDict
from typing import Callable, Optional

from .config import DeviceConfig


EMPTY_SHA256 = hashlib.sha256(b"").hexdigest()
NONCE_PATTERN = re.compile(r"^[a-zA-Z0-9_.:-]{8,128}$")
TIMESTAMP_PATTERN = re.compile(r"^[0-9]{1,16}$")


def canonical_message(
    device_id: str,
    boot_nonce: str,
    request_nonce: str,
    request_timestamp: str,
    method: str,
    path_and_query: str,
    body: bytes,
) -> bytes:
    body_hash = hashlib.sha256(body).hexdigest()
    return "\n".join(
        (
            "JETSONHTTP2",
            device_id.lower(),
            boot_nonce,
            request_nonce,
            request_timestamp,
            method.upper(),
            path_and_query,
            body_hash,
        )
    ).encode("utf-8")


def sign_request(
    secret: bytes,
    device_id: str,
    boot_nonce: str,
    request_nonce: str,
    request_timestamp: str,
    method: str,
    path_and_query: str,
    body: bytes = b"",
) -> str:
    message = canonical_message(
        device_id=device_id,
        boot_nonce=boot_nonce,
        request_nonce=request_nonce,
        request_timestamp=request_timestamp,
        method=method,
        path_and_query=path_and_query,
        body=body,
    )
    return hmac.new(secret, message, hashlib.sha256).hexdigest()


def canonical_response(
    device_id: str,
    boot_nonce: str,
    request_nonce: str,
    request_timestamp: str,
    status_code: int,
    body: bytes,
) -> bytes:
    return "\n".join(
        (
            "JETSONHTTPRESP1",
            device_id.lower(),
            boot_nonce,
            request_nonce,
            request_timestamp,
            str(status_code),
            hashlib.sha256(body).hexdigest(),
        )
    ).encode("utf-8")


def sign_response(
    secret: bytes,
    device_id: str,
    boot_nonce: str,
    request_nonce: str,
    request_timestamp: str,
    status_code: int,
    body: bytes,
) -> str:
    message = canonical_response(
        device_id=device_id,
        boot_nonce=boot_nonce,
        request_nonce=request_nonce,
        request_timestamp=request_timestamp,
        status_code=status_code,
        body=body,
    )
    return hmac.new(secret, message, hashlib.sha256).hexdigest()


def canonical_hello(
    api_version: int,
    device_id: str,
    device_name: str,
    boot_nonce: str,
    server_time_epoch_seconds: int,
    auth_scheme: str,
    tls_certificate_sha256: str,
) -> bytes:
    return "\n".join(
        (
            "JETSONHELLO1",
            str(api_version),
            device_id.lower(),
            device_name,
            boot_nonce,
            str(server_time_epoch_seconds),
            auth_scheme,
            tls_certificate_sha256.lower(),
        )
    ).encode("utf-8")


def sign_hello(
    secret: bytes,
    api_version: int,
    device_id: str,
    device_name: str,
    boot_nonce: str,
    server_time_epoch_seconds: int,
    auth_scheme: str,
    tls_certificate_sha256: str,
) -> str:
    message = canonical_hello(
        api_version=api_version,
        device_id=device_id,
        device_name=device_name,
        boot_nonce=boot_nonce,
        server_time_epoch_seconds=server_time_epoch_seconds,
        auth_scheme=auth_scheme,
        tls_certificate_sha256=tls_certificate_sha256,
    )
    return hmac.new(secret, message, hashlib.sha256).hexdigest()


class RequestAuthenticator:
    def __init__(
        self,
        config: DeviceConfig,
        boot_nonce: Optional[str] = None,
        nonce_capacity: int = 8192,
        max_clock_skew_seconds: int = 120,
        clock: Callable[[], float] = time.time,
    ) -> None:
        if nonce_capacity < 1:
            raise ValueError("nonce_capacity must be positive")
        if max_clock_skew_seconds < 1:
            raise ValueError("max_clock_skew_seconds must be positive")
        self.config = config
        self.boot_nonce = boot_nonce or secrets.token_hex(16)
        self.nonce_capacity = nonce_capacity
        self.max_clock_skew_seconds = max_clock_skew_seconds
        self._clock = clock
        self._seen_nonces: "OrderedDict[str, int]" = OrderedDict()
        self._nonce_lock = threading.Lock()

    def verify(
        self,
        device_id: str,
        request_nonce: str,
        request_timestamp: str,
        method: str,
        path_and_query: str,
        body: bytes,
        received_signature: str,
    ) -> bool:
        normalized_id = device_id.lower()
        if normalized_id != self.config.device_id:
            return False
        if not NONCE_PATTERN.fullmatch(request_nonce):
            return False
        if not TIMESTAMP_PATTERN.fullmatch(request_timestamp):
            return False
        timestamp = int(request_timestamp)
        if str(timestamp) != request_timestamp:
            return False
        now = int(self._clock())
        if abs(now - timestamp) > self.max_clock_skew_seconds:
            return False
        if not re.fullmatch(r"[0-9a-fA-F]{64}", received_signature):
            return False

        expected = sign_request(
            secret=self.config.bootstrap_secret,
            device_id=self.config.device_id,
            boot_nonce=self.boot_nonce,
            request_nonce=request_nonce,
            request_timestamp=request_timestamp,
            method=method,
            path_and_query=path_and_query,
            body=body,
        )
        if not hmac.compare_digest(expected, received_signature.lower()):
            return False

        with self._nonce_lock:
            cutoff = now - (self.max_clock_skew_seconds * 2)
            while self._seen_nonces:
                _, seen_at = next(iter(self._seen_nonces.items()))
                if seen_at >= cutoff:
                    break
                self._seen_nonces.popitem(last=False)
            if request_nonce in self._seen_nonces:
                return False
            if len(self._seen_nonces) >= self.nonce_capacity:
                return False
            self._seen_nonces[request_nonce] = now
        return True
