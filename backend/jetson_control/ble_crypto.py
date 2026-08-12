from __future__ import annotations

import hashlib
import hmac

from cryptography.exceptions import InvalidTag
from cryptography.hazmat.primitives.ciphers.aead import AESGCM


SESSION_KEY_CONTEXT = b"JETSONBLEENC1|"
WIFI_AAD_CONTEXT = b"JETSONWIFI2|"
ENCRYPTED_WIFI_VERSION = 2
NONCE_SIZE = 12
TAG_SIZE = 16


def derive_session_key(
    bootstrap_secret: bytes,
    device_id_bytes: bytes,
    challenge: bytes,
) -> bytes:
    if len(bootstrap_secret) != 32 or len(device_id_bytes) != 16 or len(challenge) != 16:
        raise ValueError("Invalid BLE session key material")
    message = SESSION_KEY_CONTEXT + device_id_bytes + b"|" + challenge
    return hmac.new(bootstrap_secret, message, hashlib.sha256).digest()


def decrypt_wifi_payload(
    payload: bytes,
    session_key: bytes,
    device_id_bytes: bytes,
) -> bytes:
    minimum_size = 1 + NONCE_SIZE + TAG_SIZE
    if len(payload) < minimum_size or payload[0] != ENCRYPTED_WIFI_VERSION:
        raise ValueError("Encrypted Wi-Fi payload is invalid")
    nonce = payload[1 : 1 + NONCE_SIZE]
    ciphertext = payload[1 + NONCE_SIZE :]
    aad = WIFI_AAD_CONTEXT + device_id_bytes
    try:
        return AESGCM(session_key).decrypt(nonce, ciphertext, aad)
    except (InvalidTag, ValueError) as error:
        raise ValueError("Encrypted Wi-Fi payload authentication failed") from error
