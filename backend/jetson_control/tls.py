from __future__ import annotations

import hashlib
import re
import ssl
from pathlib import Path


SHA256_PATTERN = re.compile(r"^[0-9a-f]{64}$")


def certificate_sha256(path: Path) -> str:
    try:
        pem = path.read_text(encoding="ascii")
        der = ssl.PEM_cert_to_DER_cert(pem)
    except (OSError, UnicodeError, ValueError) as error:
        raise RuntimeError(f"TLS certificate is unreadable: {path}") from error
    fingerprint = hashlib.sha256(der).hexdigest()
    if not SHA256_PATTERN.fullmatch(fingerprint):
        raise RuntimeError("TLS certificate fingerprint is invalid")
    return fingerprint
