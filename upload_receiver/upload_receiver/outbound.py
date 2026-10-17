from __future__ import annotations

import http.client
import json
import logging
import random
import re
import ssl
import threading
import time
import uuid
from dataclasses import dataclass, field
from email.utils import parsedate_to_datetime
from typing import Callable, Mapping
from urllib.parse import urlsplit

from .database import Database
from .geon_work import GeonWorkConfig, GeonWorkConfiguration, parse_timestamp, timestamp
from .signing import attempt_nonce, canonical_json, sign_payload


LOGGER = logging.getLogger(__name__)
ERROR_CODE_PATTERN = re.compile(r"^[A-Z0-9_]{1,64}$")


def enqueue(
    connection, device_id: str, payload: Mapping[str, object], now: float,
    *, target: str = "geon-work", kind: str = "webhook",
    idempotency_key: str | None = None,
    incident_key: str | None = None, status_text: str | None = None,
) -> str | None:
    incident_key = incident_key if incident_key is not None else str(payload["incidentKey"])
    status_text = status_text if status_text is not None else str(payload["status"])
    last = connection.execute(
        """SELECT status_text FROM outbound_messages
           WHERE target=? AND kind=? AND incident_key=? ORDER BY rowid DESC LIMIT 1""",
        (target, kind, incident_key),
    ).fetchone()
    if last is not None and last["status_text"] == status_text:
        return None
    message_id = uuid.uuid4().hex
    at = timestamp(now)
    inserted = connection.execute(
        """
        INSERT OR IGNORE INTO outbound_messages(
            message_id, target, kind, device_id, incident_key, status_text,
            idempotency_key, payload_json, state, attempts, next_attempt_at,
            created_at, updated_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', 0, ?, ?, ?)
        """,
        (message_id, target, kind, device_id, incident_key, status_text,
         idempotency_key, canonical_json(payload).decode("utf-8"), at, at, at),
    ).rowcount
    return message_id if inserted else None


@dataclass(frozen=True)
class OutboundRequest:
    url: str = field(repr=False)
    body: bytes = field(repr=False)
    headers: Mapping[str, str] = field(repr=False)
    method: str = "POST"
    timeout: float = 15.0


@dataclass(frozen=True)
class SendResult:
    status: int
    error_code: str | None = None
    retry_after: str | None = None


def http_sender(request: OutboundRequest) -> SendResult:
    """No redirect following, proxy discovery, or third-party HTTP client."""
    url = urlsplit(request.url)
    if url.scheme != "https" or not url.hostname:
        raise ValueError("Invalid outbound URL")
    connection = http.client.HTTPSConnection(
        url.hostname, url.port or 443, timeout=request.timeout,
        context=ssl.create_default_context(),
    )
    try:
        path = url.path or "/"
        if url.query:
            path += "?" + url.query
        connection.request(request.method, path, request.body, dict(request.headers))
        response = connection.getresponse()
        retry_after = response.getheader("Retry-After")
        raw = response.read(65536)
        code = None
        try:
            value = json.loads(raw)
            if isinstance(value, dict):
                code = value.get("code") or value.get("errorCode")
                if not code and isinstance(value.get("error"), dict):
                    code = value["error"].get("code")
        except (ValueError, UnicodeError):
            pass
        return SendResult(response.status, _safe_code(code), retry_after)
    finally:
        connection.close()


def _safe_code(value: object) -> str | None:
    return value if isinstance(value, str) and ERROR_CODE_PATTERN.fullmatch(value) else None


def webhook_request(
    config: GeonWorkConfig, row: Mapping[str, object], now: float,
) -> OutboundRequest:
    payload = json.loads(str(row["payload_json"]))
    payload["channelId"] = config.channel_id
    body, headers = sign_payload(
        payload, config.signing_secret, str(int(now)),
        attempt_nonce(str(row["message_id"]), int(row["attempts"])),
    )
    return OutboundRequest(config.webhook_url, body, headers)


class Dispatcher:
    """One claim/send at a time. Request builders can later add Bearer PATCH kinds."""

    def __init__(
        self, database: Database, configuration: GeonWorkConfiguration, events,
        *, sender: Callable[[OutboundRequest], SendResult] = http_sender,
        clock: Callable[[], float] = time.time,
        jitter: Callable[[float, float], float] = random.uniform,
        request_builders: Mapping[str, Callable] | None = None,
    ) -> None:
        self.database = database
        self.configuration = configuration
        self.events = events
        self.sender = sender
        self.clock = clock
        self.jitter = jitter
        self.request_builders = dict(request_builders or {"webhook": webhook_request})
        self._guard = threading.Lock()
        self._stop = threading.Event()
        self._thread: threading.Thread | None = None
        self._last_send: float | None = None
        self._last_judgement: float | None = None
        self._recovered = False

    def start(self) -> None:
        self._thread = threading.Thread(target=self._run, name="geon-work-outbox", daemon=True)
        self._thread.start()

    def stop(self) -> None:
        self._stop.set()
        if self._thread is not None:
            self._thread.join()

    def _run(self) -> None:
        while not self._stop.is_set():
            try:
                self.run_once(self.clock())
            except Exception:
                # Exception text and tracebacks can contain secrets or payloads.
                LOGGER.warning("message_id=- http_status=- error_code=DISPATCH_ERROR")
            self._stop.wait(0.5)

    def run_once(self, now: float) -> bool:
        if not self._guard.acquire(blocking=False):
            return False
        try:
            config = self.configuration.refresh(now)
            if self._last_judgement is None or now - self._last_judgement >= 60:
                self.events.judge_heartbeats(now, config=config)
                self._last_judgement = now
            at = timestamp(now)
            with self.database.immediate() as connection:
                if not self._recovered:
                    connection.execute(
                        """UPDATE outbound_messages SET state='PENDING', updated_at=?
                           WHERE target='geon-work' AND state='SENDING'""", (at,),
                    )
                    self._recovered = True
                connection.execute(
                    """UPDATE outbound_messages SET state='DEAD', last_error_code='STALE',
                       updated_at=?, completed_at=? WHERE target='geon-work'
                       AND state='PENDING' AND created_at<=?""",
                    (at, at, timestamp(now - 86400)),
                )
                integration = connection.execute(
                    "SELECT * FROM integration_state WHERE target='geon-work'"
                ).fetchone()
                if (
                    config is None or integration["state"] != "ACTIVE"
                    or self._stop.is_set()
                    or (self._last_send is not None and now - self._last_send < 0.5)
                ):
                    return False
                placeholders = ",".join("?" for _ in self.request_builders)
                row = connection.execute(
                    f"""SELECT * FROM outbound_messages WHERE target='geon-work'
                        AND state='PENDING' AND next_attempt_at<=? AND kind IN ({placeholders})
                        ORDER BY next_attempt_at, rowid LIMIT 1""",
                    (at, *self.request_builders),
                ).fetchone()
                if row is None:
                    return False
                message = dict(row)
                if message["attempts"] >= 10:
                    connection.execute(
                        """UPDATE outbound_messages SET state='DEAD',
                           last_error_code='RETRY_EXHAUSTED', updated_at=?, completed_at=?
                           WHERE message_id=?""", (at, at, message["message_id"]),
                    )
                    return False
                message["attempts"] += 1
                connection.execute(
                    """UPDATE outbound_messages SET state='SENDING', attempts=?, updated_at=?
                       WHERE message_id=?""", (message["attempts"], at, message["message_id"]),
                )
                fingerprint = integration["config_fingerprint"]
            self._last_send = now
            try:
                request = self.request_builders[message["kind"]](config, message, now)
            except Exception:
                self._finish(message, now, None, "INVALID_PAYLOAD", None, fingerprint)
                return True
            try:
                result = self.sender(request)
                status, code, retry_after = (
                    result.status, _safe_code(result.error_code), result.retry_after,
                )
            except Exception:
                status, code, retry_after = None, "NETWORK_ERROR", None
            self._finish(message, now, status, code, retry_after, fingerprint)
            return True
        except Exception:
            # A failed result write leaves SENDING durable. Reclaim it on the
            # next cycle, with a fresh nonce and the existing attempt count.
            self._recovered = False
            raise
        finally:
            self._guard.release()

    def _finish(self, message, now, status, code, retry_after, fingerprint) -> None:
        state = "DEAD"
        pause = None
        if status == 201 or (status == 409 and code == "WEBHOOK_REPLAY"):
            state, code = "SENT", None
        elif status == 200:
            state, code = "DEDUP", None
        elif status == 401:
            code, pause = "SIGNATURE_REJECTED", "ERROR"
        elif status == 403:
            code, pause = "FORBIDDEN", "ERROR"
        elif status == 404 and code == "INTEGRATION_NOT_FOUND":
            pause = "EXPIRED"
        elif status == 409 and code == "INTEGRATION_CHANNEL_UNAVAILABLE":
            code, pause = "CHANNEL_UNAVAILABLE", "ERROR"
        elif code != "INVALID_PAYLOAD" and (status is None or status == 429 or status >= 500):
            state = "PENDING" if message["attempts"] < 10 else "DEAD"
            if state == "DEAD":
                code = "RETRY_EXHAUSTED"
        code = code or (f"HTTP_{status}" if state in {"DEAD", "PENDING"} else None)
        base_delay = min(900.0, 15.0 * 2 ** (message["attempts"] - 1))
        delay = min(900.0, base_delay * self.jitter(0.9, 1.1))
        if status == 429 and retry_after:
            try:
                delay = max(0.0, float(retry_after))
            except (ValueError, TypeError):
                try:
                    delay = max(0.0, parsedate_to_datetime(retry_after).timestamp() - now)
                except (ValueError, TypeError, OverflowError):
                    pass
        # Non-finite or zero Retry-After must not create a busy retry loop.
        if not 0 < delay < float("inf"):
            delay = 0.5
        delay = min(delay, 86400.0)
        at = timestamp(now)
        with self.database.immediate() as connection:
            connection.execute(
                """UPDATE outbound_messages SET state=?, next_attempt_at=?,
                   last_http_status=?, last_error_code=?, updated_at=?, completed_at=?
                   WHERE message_id=?""",
                (state, timestamp(now + delay), status, code, at,
                 None if state == "PENDING" else at, message["message_id"]),
            )
            if pause:
                connection.execute(
                    """UPDATE integration_state SET state=?, detail=?, updated_at=?
                       WHERE target='geon-work' AND config_fingerprint=?""",
                    (pause, code, at, fingerprint),
                )
        LOGGER.info(
            "message_id=%s http_status=%s error_code=%s", message["message_id"], status, code,
        )
