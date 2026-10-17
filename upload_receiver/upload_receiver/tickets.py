from __future__ import annotations

import json
import logging
import math
import threading
import time
from contextlib import closing
from email.utils import parsedate_to_datetime
from urllib.parse import quote, urlencode

from .geon_work import TicketsConfiguration, parse_timestamp, timestamp
from .outbound import OutboundRequest, PayloadRejected, SendResult, _safe_code, http_sender
from .signing import canonical_json


LOGGER = logging.getLogger(__name__)
TARGET = "geon-work-tickets"
PROPERTIES = {
    "프로젝트": "TEXT", "호기": "SELECT", "담당": "PERSON", "마감": "DATE",
    "상태": "STATUS", "수집 작업자": "TEXT", "runId": "TEXT",
    "수집 시간": "NUMBER", "용량(GB)": "NUMBER", "정밀 위치 비율": "NUMBER",
}


def resolve_schema(board: dict) -> dict:
    try:
        properties = {}
        for name, kind in PROPERTIES.items():
            matches = [p for p in board["properties"] if p["name"] == name]
            if len(matches) != 1 or matches[0]["type"] != kind or not matches[0]["id"]:
                raise ValueError()
            properties[name] = matches[0]
        statuses = {}
        for name in ("할 일", "진행 중", "완료"):
            matches = [s for s in board["statusOptions"] if s["name"] == name]
            if len(matches) != 1 or not matches[0]["id"] or not matches[0]["category"]:
                raise ValueError()
            statuses[name] = matches[0]
        if statuses["완료"]["category"] != "DONE":
            raise ValueError()
        prop = properties["호기"]
        units = prop.get("options", prop.get("config", {}).get("options"))
        if not isinstance(units, list) or any(
            not isinstance(u.get("name"), str) or not u.get("id") for u in units
        ):
            raise ValueError()
        return {"properties": properties, "statuses": statuses, "units": units}
    except (KeyError, TypeError, AttributeError, ValueError) as error:
        raise ValueError("BOARD_SCHEMA_MISMATCH") from error


def row_values(row: dict, schema: dict) -> dict:
    values = row.get("values") or {}
    return {name: values.get(prop["id"], {}).get("value")
            for name, prop in schema["properties"].items()}


def upsert_ticket(connection, row: dict, schema: dict, now: float) -> None:
    values = row_values(row, schema)
    unit = next((u["name"] for u in schema["units"] if u["id"] == values["호기"]), None)
    assignees = [a["displayName"] for a in row.get("assignees", [])]
    status = row["status"]
    updated = timestamp(parse_timestamp(row["updatedAt"]))
    connection.execute(
        """INSERT INTO board_tickets VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
           ON CONFLICT(row_id) DO UPDATE SET title=excluded.title, project=excluded.project,
               unit=excluded.unit, assignees_json=excluded.assignees_json,
               due_date=excluded.due_date, status_id=excluded.status_id,
               status_name=excluded.status_name, status_category=excluded.status_category,
               revision=excluded.revision, updated_at=excluded.updated_at,
               archived_at=excluded.archived_at, run_id=excluded.run_id, synced_at=excluded.synced_at,
               destination=excluded.destination
           WHERE excluded.revision>=board_tickets.revision""",
        (row["id"], row["title"], values["프로젝트"], unit,
         json.dumps(assignees, ensure_ascii=False), row.get("dueDate"), status["id"],
         status["name"], status["category"], row["revision"], updated,
         row.get("archivedAt"), values["runId"], timestamp(now), schema["destination"]),
    )


def retry_delay(result: SendResult, now: float, failures: int) -> float:
    delay = min(600.0, 30.0 * 2 ** min(max(0, failures - 1), 5))
    if result.status == 429 and result.retry_after:
        try:
            delay = float(result.retry_after)
        except ValueError:
            try:
                delay = parsedate_to_datetime(result.retry_after).timestamp() - now
            except (TypeError, ValueError, OverflowError):
                pass
    return max(0.5, delay) if math.isfinite(delay) else 0.5


class BoardRateLimit:
    """One shared token budget for schema, listing and PATCH; never sleep in a transaction."""

    def __init__(self):
        self._lock = threading.Lock()
        self._next = 0.0
        self._busy = False

    def reserve(self, now: float) -> bool:
        with self._lock:
            if self._busy or now < self._next:
                return False
            self._busy = True
            self._next = now + 0.5
            return True

    def defer(self, until: float) -> None:
        with self._lock:
            self._next = max(self._next, until)

    def release(self) -> None:
        with self._lock:
            self._busy = False


class TicketSync:
    def __init__(self, database, configuration: TicketsConfiguration, *, sender=http_sender,
                 clock=time.time):
        self.database = database
        self.configuration = configuration
        self.sender = sender
        self.clock = clock
        self.rate = BoardRateLimit()
        self._guard = threading.Lock()
        self._stop = threading.Event()
        self._thread = None
        self._fingerprint = None
        self._validated = None
        self._schema = None
        self._config = None
        self._query = None
        self._cursor = None
        self._maximum = None
        self._full = True
        self._next_sync = 0.0
        self._failures = 0

    def start(self):
        self._thread = threading.Thread(target=self._run, name="geon-work-tickets", daemon=True)
        self._thread.start()

    def stop(self):
        self._stop.set()
        if self._thread is not None:
            self._thread.join()

    def _run(self):
        while not self._stop.is_set():
            try:
                self.run_once(self.clock())
            except Exception:
                LOGGER.warning("http_status=- error_code=TICKET_SYNC_ERROR")
            self._stop.wait(0.5)

    def ready(self, connection) -> bool:
        state = connection.execute(
            "SELECT * FROM integration_state WHERE target=?", (TARGET,)
        ).fetchone()
        return (self._validated is not None and state is not None
                and state["state"] == "ACTIVE"
                and state["config_fingerprint"] == self._validated)

    def _request(self, path, *, method="GET", body=b"", config=None):
        config = config or self._config
        return OutboundRequest(
            config.api_base_url + "/api/v1/boards/" + config.board_id + path, body,
            {"Authorization": "Bearer " + config.api_token, "Content-Type": "application/json"},
            method=method,
        )

    def _pause(self, connection, code, fingerprint, now):
        connection.execute(
            """UPDATE integration_state SET state='ERROR', detail=?, updated_at=?
               WHERE target=? AND config_fingerprint=?""",
            (code, timestamp(now), TARGET, fingerprint),
        )

    def run_once(self, now: float) -> bool:
        if not self._guard.acquire(blocking=False):
            return False
        reserved = False
        try:
            config = self.configuration.refresh(now)
            with closing(self.database.connect()) as connection:
                state = connection.execute(
                    "SELECT * FROM integration_state WHERE target=?", (TARGET,)
                ).fetchone()
            if config is None or state["state"] != "ACTIVE" or self._stop.is_set():
                return False
            fingerprint = state["config_fingerprint"]
            if (self._fingerprint == fingerprint and self._schema is not None
                    and self._query is None and now < self._next_sync):
                return False
            if not self.rate.reserve(now):
                return False
            reserved = True
            if self._fingerprint != fingerprint:
                self._validated = self._schema = self._query = self._cursor = self._maximum = None
                self._full, self._next_sync, self._failures = True, 0.0, 0
                with self.database.immediate() as connection:
                    previous = connection.execute("SELECT * FROM ticket_sync WHERE target=?", (TARGET,)).fetchone()
                    if previous is None or previous["config_fingerprint"] != fingerprint:
                        destination = config.api_base_url + "/api/v1/boards/" + config.board_id
                        if previous and previous["destination"] == destination:
                            # Credential rotation keeps links and frozen bodies, including their keys.
                            connection.execute(
                                """UPDATE outbound_messages SET payload_json=json_set(payload_json, '$.fingerprint', ?)
                                   WHERE target=? AND state IN ('PENDING','SENDING')""", (fingerprint, TARGET),
                            )
                        else:
                            connection.execute(
                                """UPDATE outbound_messages SET state='DEAD', last_error_code='CONFIG_CHANGED',
                                   updated_at=?, completed_at=? WHERE target=? AND state IN ('PENDING','SENDING')""",
                                (timestamp(now), timestamp(now), TARGET),
                            )
                        connection.execute("INSERT OR REPLACE INTO ticket_sync VALUES (?, ?, NULL, NULL, NULL, ?)",
                                           (TARGET, fingerprint, destination))
                self._fingerprint = fingerprint
            if self._schema is None:
                request = self._request("", config=config)
            else:
                if self._query is None:
                    self._query = {"status": "open" if self._full else "all", "limit": 100}
                    if not self._full and self._maximum is not None:
                        self._query["updatedSince"] = timestamp(self._maximum - 5)
                query = dict(self._query)
                if self._cursor is not None:
                    query["cursor"] = self._cursor
                request = self._request("/rows?" + urlencode(query), config=config)
            try:
                # Claim/build may take time; space actual request starts too.
                self.rate.defer(max(now, self.clock()) + 0.5)
                result = self.sender(request)
            except Exception:
                result = SendResult(0, "NETWORK_ERROR")
            now = max(now, self.clock())
            code = _safe_code(result.error_code) or f"HTTP_{result.status}"
            if not 200 <= result.status < 300:
                LOGGER.warning("http_status=%s error_code=%s", result.status, code)
                if result.status in (401, 403) or code == "INTEGRATION_BOARD_FORBIDDEN":
                    with self.database.immediate() as connection:
                        self._pause(connection, code, fingerprint, now)
                elif result.status == 0 or result.status == 429 or result.status >= 500:
                    self._failures += 1
                    self.rate.defer(now + retry_delay(result, now, self._failures))
                else:
                    with self.database.immediate() as connection:
                        self._pause(connection, code, fingerprint, now)
                return True
            self._failures = 0
            data = result.data or {}
            if self._schema is None:
                try:
                    schema = resolve_schema(data)
                except ValueError:
                    with self.database.immediate() as connection:
                        self._pause(connection, "BOARD_SCHEMA_MISMATCH", fingerprint, now)
                    return True
                schema["destination"] = config.api_base_url + "/api/v1/boards/" + config.board_id
                with self.database.immediate() as connection:
                    connection.execute("UPDATE ticket_sync SET schema_json=? WHERE target=? AND config_fingerprint=?",
                                       (canonical_json(schema).decode(), TARGET, fingerprint))
                self._schema, self._validated, self._config = schema, fingerprint, config
            else:
                maximum = self._maximum
                with self.database.immediate() as connection:
                    for row in data["items"]:
                        upsert_ticket(connection, row, self._schema, now)
                        updated = parse_timestamp(row["updatedAt"])
                        maximum = updated if maximum is None else max(maximum, updated)
                    cursor = data["nextCursor"]
                    if cursor is None:
                        connection.execute(
                            "UPDATE ticket_sync SET synced_at=?, max_updated_at=? WHERE target=? AND config_fingerprint=?",
                            (timestamp(now), timestamp(maximum) if maximum is not None else None, TARGET, fingerprint),
                        )
                self._maximum, self._cursor = maximum, cursor
                if cursor is None:
                    self._full, self._query, self._next_sync = False, None, now + 120
            return True
        finally:
            if reserved:
                self.rate.release()
            self._guard.release()

    def build_request(self, _config, message, now):
        with self.database.immediate() as connection:
            stored = connection.execute("SELECT payload_json FROM outbound_messages WHERE message_id=?",
                                        (message["message_id"],)).fetchone()
            payload = json.loads(stored[0])
            if payload["fingerprint"] != self._validated:
                raise PayloadRejected("CONFIG_CHANGED")
            if "body" not in payload:
                ticket = connection.execute("SELECT * FROM board_tickets WHERE row_id=?",
                                            (payload["rowId"],)).fetchone()
                expected = ("할 일", "진행 중") if payload["type"] == "STARTED" else ("진행 중",)
                if (ticket is None or ticket["archived_at"] or ticket["status_name"] not in expected
                        or ticket["run_id"] not in (None, "", payload["runId"])):
                    raise PayloadRejected("HUMAN_CHANGED")
                schema = self._schema
                payload["body"] = {
                    "expectedRevision": ticket["revision"], "idempotencyKey": message["idempotency_key"],
                    "status": schema["statuses"][payload["status"]]["id"],
                    "values": {schema["properties"][name]["id"]: {
                        "type": schema["properties"][name]["type"], "value": value,
                    } for name, value in payload["values"].items()},
                    "comment": payload["comment"],
                }
                # Commit the exact body BEFORE HTTP. Recovery/retries cannot change it.
                connection.execute("UPDATE outbound_messages SET payload_json=? WHERE message_id=?",
                                   (canonical_json(payload).decode(), message["message_id"]))
        return self._request("/rows/" + quote(payload["rowId"], safe=""), method="PATCH",
                             body=canonical_json(payload["body"]))

    def finish(self, message, now, result: SendResult, fingerprint):
        status = result.status
        code = _safe_code(result.error_code)
        state, delay = "DEAD", 0.5
        at = timestamp(now)
        with self.database.immediate() as connection:
            payload = json.loads(connection.execute(
                "SELECT payload_json FROM outbound_messages WHERE message_id=?", (message["message_id"],)
            ).fetchone()[0])
            data = result.data or {}
            if 200 <= status < 300 and isinstance(data.get("row"), dict):
                upsert_ticket(connection, data["row"], self._schema, now)
                state, code = ("DEDUP" if data.get("deduplicated") is True else "SENT"), None
            elif status == 409 and code == "ROW_REVISION_CONFLICT" and isinstance(data.get("current"), dict):
                current = data["current"]
                upsert_ticket(connection, current, self._schema, now)
                run_id = row_values(current, self._schema)["runId"]
                expected = ("할 일", "진행 중") if payload["type"] == "STARTED" else ("진행 중",)
                if (not current.get("archivedAt") and current["status"]["name"] in expected
                        and run_id in (None, "", payload["runId"])):
                    attempt = payload.get("revisionAttempt", 1) + 1
                    payload["revisionAttempt"] = attempt
                    key = payload["originalKey"] + f":attempt{attempt}"
                    payload["body"]["expectedRevision"] = current["revision"]
                    payload["body"]["idempotencyKey"] = key
                    connection.execute("UPDATE outbound_messages SET payload_json=?, idempotency_key=? WHERE message_id=?",
                                       (canonical_json(payload).decode(), key, message["message_id"]))
                    state = "PENDING" if message["attempts"] < 10 else "DEAD"
                    code = None if state == "PENDING" else "RETRY_EXHAUSTED"
                else:
                    code = "HUMAN_CHANGED"
            elif status == 0 or status == 429 or status >= 500:
                state = "PENDING" if message["attempts"] < 10 else "DEAD"
                code = code or ("NETWORK_ERROR" if status == 0 else f"HTTP_{status}")
                if state == "DEAD":
                    code = "RETRY_EXHAUSTED"
                delay = retry_delay(result, now, message["attempts"])
                self.rate.defer(now + delay)
            code = code or (f"HTTP_{status}" if state == "DEAD" else None)
            connection.execute(
                """UPDATE outbound_messages SET state=?, next_attempt_at=?, last_http_status=?,
                   last_error_code=?, updated_at=?, completed_at=? WHERE message_id=?""",
                (state, timestamp(now + delay), status or None, code, at,
                 None if state == "PENDING" else at, message["message_id"]),
            )
            if status in (401, 403) or code == "INTEGRATION_BOARD_FORBIDDEN":
                self._pause(connection, code, fingerprint, now)
        log = LOGGER.warning if state == "DEAD" else LOGGER.info
        log("message_id=%s http_status=%s error_code=%s", message["message_id"], status, code)
