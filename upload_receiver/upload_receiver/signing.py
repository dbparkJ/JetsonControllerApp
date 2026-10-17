from __future__ import annotations

import hashlib
import hmac
import json
import secrets


def canonical_json(value: object) -> bytes:
    return json.dumps(
        value, ensure_ascii=False, separators=(",", ":"), sort_keys=True
    ).encode("utf-8")


def sign_payload(
    payload: object, secret: str, timestamp: str, nonce: str
) -> tuple[bytes, dict[str, str]]:
    body = canonical_json(payload)
    signed = timestamp.encode("utf-8") + b"." + nonce.encode("utf-8") + b"." + body
    signature = hmac.new(secret.encode("utf-8"), signed, hashlib.sha256).hexdigest()
    return body, {
        "Content-Type": "application/json",
        "x-geon-timestamp": timestamp,
        "x-geon-nonce": nonce,
        "x-geon-signature": "v1=" + signature,
    }


def attempt_nonce(message_id: str, attempt: int) -> str:
    return f"jc-{message_id.replace('-', '')[:12]}-{attempt}-{secrets.token_hex(4)}"
