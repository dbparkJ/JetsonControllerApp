from __future__ import annotations

import hashlib
import json
import os
import stat
import threading
import uuid
from contextlib import closing
from dataclasses import dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import urlsplit

from .database import Database


def timestamp(now: float) -> str:
    # Fixed precision keeps SQLite's text ordering chronological within a second.
    return datetime.fromtimestamp(now, timezone.utc).isoformat(
        timespec="microseconds"
    ).replace("+00:00", "Z")


def parse_timestamp(value: str) -> float:
    parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    if parsed.utcoffset() is None:
        raise ValueError("Timestamp must include an offset")
    return parsed.timestamp()


@dataclass(frozen=True)
class GeonWorkConfig:
    webhook_url: str
    channel_id: str
    signing_secret: str = field(repr=False)


def validate_config(value: object) -> GeonWorkConfig:
    if not isinstance(value, dict) or type(value.get("version")) is not int:
        raise ValueError("CONFIG_INVALID")
    if value["version"] != 1:
        raise ValueError("CONFIG_INVALID")
    url = value.get("webhookUrl")
    channel = value.get("channelId")
    secret = value.get("signingSecret")
    if not all(isinstance(item, str) and item.strip() for item in (url, channel, secret)):
        raise ValueError("CONFIG_INVALID")
    parsed = urlsplit(url)
    if (
        parsed.scheme != "https" or not parsed.hostname or parsed.username is not None
        or parsed.password is not None or parsed.fragment
        or any(character.isspace() for character in url)
    ):
        raise ValueError("CONFIG_INVALID")
    # Accessing port also validates malformed and out-of-range ports.
    parsed.port
    uuid.UUID(channel)
    return GeonWorkConfig(url, str(uuid.UUID(channel)), secret)


class GeonWorkConfiguration:
    """Reload local secrets; keep a remote rejection paused across restarts."""

    def __init__(self, path: Path, database: Database) -> None:
        self.path = path
        self.database = database
        self._lock = threading.RLock()
        self._metadata: tuple[int, int, int, int] | None = None
        self._loaded = False
        self._config: GeonWorkConfig | None = None

    @property
    def current(self) -> GeonWorkConfig | None:
        # Immutable snapshot, also used by completion without file I/O or waiting.
        return self._config

    def refresh(self, now: float) -> GeonWorkConfig | None:
        with self._lock:
            try:
                info = self.path.stat()
                metadata = (info.st_mtime_ns, info.st_size, info.st_mode, info.st_ino)
            except FileNotFoundError:
                metadata = None
            except OSError:
                self._config = None
                self._set_state("ERROR", "CONFIG_INVALID", None, now)
                return None
            if self._loaded and metadata == self._metadata:
                return self._config
            self._loaded = True
            self._metadata = metadata
            self._config = None
            if metadata is None:
                self._set_state("NOT_CONFIGURED", None, None, now)
                return None
            if not stat.S_ISREG(info.st_mode):
                self._set_state("ERROR", "CONFIG_INVALID", None, now)
                return None
            if info.st_mode & 0o077:
                self._set_state("ERROR", "CONFIG_INSECURE", None, now)
                return None
            try:
                # Check the opened file as well as the stat result to avoid a
                # replacement race. Never read group/other-accessible secrets.
                with self.path.open("rb") as source:
                    opened = os.fstat(source.fileno())
                    if not stat.S_ISREG(opened.st_mode) or opened.st_mode & 0o077:
                        self._set_state("ERROR", "CONFIG_INSECURE", None, now)
                        return None
                    raw = source.read(65537)
                if len(raw) > 65536:
                    raise ValueError("CONFIG_INVALID")
                config = validate_config(json.loads(raw))
                fingerprint = hashlib.sha256(raw).hexdigest()
            except (OSError, ValueError, TypeError, UnicodeError):
                self._set_state("ERROR", "CONFIG_INVALID", None, now)
                return None
            self._config = config
            with closing(self.database.connect()) as connection:
                previous = connection.execute(
                    "SELECT * FROM integration_state WHERE target='geon-work'"
                ).fetchone()
            if previous is None or previous["config_fingerprint"] != fingerprint:
                self._set_state("ACTIVE", None, fingerprint, now)
            return config

    def _set_state(
        self, state: str, detail: str | None, fingerprint: str | None, now: float
    ) -> None:
        with self.database.immediate() as connection:
            connection.execute(
                """
                INSERT INTO integration_state VALUES ('geon-work', ?, ?, ?, ?)
                ON CONFLICT(target) DO UPDATE SET state=excluded.state,
                    detail=excluded.detail, config_fingerprint=excluded.config_fingerprint,
                    updated_at=excluded.updated_at
                """,
                (state, detail, fingerprint, timestamp(now)),
            )

    def state(self) -> dict[str, object]:
        with closing(self.database.connect()) as connection:
            row = connection.execute(
                "SELECT state, detail FROM integration_state WHERE target='geon-work'"
            ).fetchone()
        return dict(row) if row is not None else {"state": "NOT_CONFIGURED", "detail": None}
