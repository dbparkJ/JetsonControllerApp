from __future__ import annotations

import json
import math
import re
import threading
import time
import uuid
from collections import deque
from contextlib import closing
from typing import Callable, Mapping

from .database import Database
from .event_mapping import map_event
from .geon_work import GeonWorkConfig, GeonWorkConfiguration, parse_timestamp, timestamp
from .outbound import enqueue
from .signing import canonical_json


DEVICE_EVENTS_CAPABILITY = {
    "version": 1, "maxEvents": 50, "maxBytes": 65536, "heartbeatSeconds": 300,
}
IDENTIFIER = re.compile(r"^[A-Za-z0-9][A-Za-z0-9_.:/-]{0,255}$")
ERROR_CODE = re.compile(r"^[A-Z0-9_]{1,64}$")
LABELS = ("label", "surveyProjectLabel", "surveySectionLabel")
EVENT_FIELDS = {
    "device.heartbeat": ("onDuty", "activeRunId", "activeUploadJobId"),
    "device.temperature": ("level", "temperatureC", "thresholdC", "peakC", "lastNormalAt"),
    "device.storage": ("level", "freePercent", "freeBytes", "totalBytes", "lastNormalAt"),
    "run.started": ("runId", "pipelineId", *LABELS),
    "run.finished": (
        "runId", "pipelineId", "result", "durationSeconds", "bytesWritten",
        "preciseFixRatio", *LABELS,
    ),
    "upload.failed": ("jobId", "runId", "failureCount", "errorCode", *LABELS),
}


class DeviceEventsError(ValueError):
    def __init__(self, status: int, detail: str, retry_after: int | None = None) -> None:
        super().__init__(detail)
        self.status = status
        self.detail = detail
        self.retry_after = retry_after


def validate_event(value: object, now: float) -> tuple[dict[str, object] | None, str | None]:
    if not isinstance(value, dict):
        return None, "INVALID_EVENT"
    kind = value.get("type")
    if not isinstance(kind, str) or kind not in EVENT_FIELDS:
        return None, "UNKNOWN_TYPE"
    try:
        event_id = value.get("eventId")
        if not isinstance(event_id, str) or uuid.UUID(event_id).version != 4:
            raise ValueError()
        at = value.get("occurredAt")
        if not isinstance(at, str):
            raise ValueError()
        occurred = parse_timestamp(at)
        if occurred < now - 7 * 86400:
            return None, "EVENT_TOO_OLD"
        event = {
            key: value[key]
            for key in ("eventId", "type", "occurredAt", *EVENT_FIELDS[kind])
            if key in value
        }
        event["eventId"] = str(uuid.UUID(event_id))
        for name in ("runId", "pipelineId", "jobId", "activeRunId", "activeUploadJobId"):
            if name in event and (
                not isinstance(event[name], str) or not IDENTIFIER.fullmatch(event[name])
            ):
                raise ValueError()
        for name in LABELS:
            if name in event:
                if not isinstance(event[name], str):
                    raise ValueError()
                event[name].encode("utf-8")
        if "lastNormalAt" in event:
            if not isinstance(event["lastNormalAt"], str):
                raise ValueError()
            parse_timestamp(event["lastNormalAt"])
        if kind == "device.heartbeat":
            if type(event.get("onDuty")) is not bool:
                raise ValueError()
        elif kind in {"device.temperature", "device.storage"}:
            if event.get("level") not in ("NORMAL", "WARNING", "CRITICAL"):
                raise ValueError()
            if kind == "device.temperature":
                _number(event["temperatureC"])
                _number(event["thresholdC"])
                if "peakC" in event:
                    _number(event["peakC"])
            else:
                _number(event["freePercent"], 0, 100)
                _integer(event["freeBytes"], 0)
                _integer(event["totalBytes"], 0)
                if event["freeBytes"] > event["totalBytes"]:
                    raise ValueError()
        elif kind in {"run.started", "run.finished"}:
            if "runId" not in event or "pipelineId" not in event:
                raise ValueError()
            if kind == "run.finished":
                if event.get("result") not in ("COMPLETED", "FAILED", "STOPPED"):
                    raise ValueError()
                if "durationSeconds" in event:
                    _number(event["durationSeconds"], 0)
                if "bytesWritten" in event:
                    _integer(event["bytesWritten"], 0)
                if "preciseFixRatio" in event:
                    _number(event["preciseFixRatio"], 0, 1)
        elif kind == "upload.failed":
            if "jobId" not in event:
                raise ValueError()
            _integer(event["failureCount"], 1)
            if (
                not isinstance(event.get("errorCode"), str)
                or not ERROR_CODE.fullmatch(event["errorCode"])
            ):
                raise ValueError()
        return event, None
    except (ValueError, TypeError, KeyError, OverflowError):
        return None, "INVALID_FIELDS"


def _number(value: object, minimum: float | None = None, maximum: float | None = None) -> None:
    if type(value) not in (int, float) or not math.isfinite(value):
        raise ValueError()
    if (minimum is not None and value < minimum) or (maximum is not None and value > maximum):
        raise ValueError()


def _integer(value: object, minimum: int) -> None:
    if type(value) is not int or value < minimum:
        raise ValueError()


class DeviceEvents:
    def __init__(
        self, database: Database, configuration: GeonWorkConfiguration,
        *, clock: Callable[[], float] = time.time,
    ) -> None:
        self.database = database
        self.configuration = configuration
        self.clock = clock
        self._requests: dict[str, deque[float]] = {}
        self._requests_guard = threading.Lock()

    def reserve_request(self, device_id: str) -> None:
        now = self.clock()
        with self._requests_guard:
            requests = self._requests.setdefault(device_id, deque())
            while requests and requests[0] <= now - 60:
                requests.popleft()
            if len(requests) >= 60:
                raise DeviceEventsError(
                    429, "Device event request limit exceeded",
                    max(1, math.ceil(60 - (now - requests[0]))),
                )
            requests.append(now)

    def accept(self, device_id: str, value: object) -> dict[str, object]:
        if (
            not isinstance(value, dict) or value.get("schema") != "jetson.event.v1"
            or not isinstance(value.get("device"), dict)
            or not isinstance(value.get("events"), list)
            or not 1 <= len(value["events"]) <= 50
        ):
            raise DeviceEventsError(400, "Device event schema is invalid")
        name = value["device"].get("name")
        if name is not None and not isinstance(name, str):
            raise DeviceEventsError(400, "Device name is invalid")
        if name is not None:
            try:
                name.encode("utf-8")
            except UnicodeError as error:
                raise DeviceEventsError(400, "Device name is invalid") from error
            name = name.strip()[:100] or None
        now = self.clock()
        config = self.configuration.refresh(now)
        results = []
        with self.database.immediate() as connection:
            for value_event in value["events"]:
                event_id = value_event.get("eventId") if isinstance(value_event, dict) else None
                # Rejected events and replays still prove the device is reachable.
                self._seen(connection, device_id, name, now, config)
                try:
                    canonical_id = str(uuid.UUID(event_id)) if isinstance(event_id, str) else None
                except ValueError:
                    canonical_id = None
                if canonical_id and connection.execute(
                    "SELECT 1 FROM device_events WHERE device_id=? AND event_id=?",
                    (device_id, canonical_id),
                ).fetchone() is not None:
                    results.append({
                        "eventId": canonical_id, "state": "DUPLICATE", "reason": None,
                    })
                    continue
                event, reason = validate_event(value_event, now)
                if event is None:
                    results.append({"eventId": event_id, "state": "REJECTED", "reason": reason})
                    continue
                event_id = event["eventId"]
                inserted = connection.execute(
                    """INSERT OR IGNORE INTO device_events VALUES (?, ?, ?, ?, ?, ?)""",
                    (device_id, event_id, event["type"], event["occurredAt"],
                     timestamp(now), canonical_json(event).decode("utf-8")),
                ).rowcount
                if not inserted:
                    results.append({"eventId": event_id, "state": "DUPLICATE", "reason": None})
                    continue
                self._update_window(connection, device_id, event, now)
                if config:
                    display = connection.execute(
                        "SELECT display_name FROM device_presence WHERE device_id=?", (device_id,)
                    ).fetchone()["display_name"]
                    payload = map_event(device_id, display, config.channel_id, event)
                    if payload is not None:
                        enqueue(connection, device_id, payload, now)
                results.append({"eventId": event_id, "state": "ACCEPTED", "reason": None})
        return {"results": results}

    def _seen(
        self, connection, device_id: str, name: str | None, now: float,
        config: GeonWorkConfig | None,
    ) -> None:
        presence = connection.execute(
            "SELECT * FROM device_presence WHERE device_id=?", (device_id,),
        ).fetchone()
        if presence is not None and presence["heartbeat_state"] == "MISSING" and config:
            payload = map_event(device_id, name or presence["display_name"], config.channel_id, {
                "type": "device.recovered", "occurredAt": timestamp(now),
            })
            enqueue(connection, device_id, payload, now)
        at = timestamp(now)
        connection.execute(
            """INSERT INTO device_presence VALUES (?, ?, ?, 0, NULL, 'OK', NULL, ?)
               ON CONFLICT(device_id) DO UPDATE SET
                   display_name=COALESCE(excluded.display_name, device_presence.display_name),
                   last_seen_at=excluded.last_seen_at, heartbeat_state='OK',
                   missing_since=NULL, updated_at=excluded.updated_at""", (device_id, name, at, at),
        )

    @staticmethod
    def _has_upload(connection, device_id: str) -> bool:
        return connection.execute(
            """SELECT 1 FROM upload_sessions WHERE device_id=?
               AND state IN ('OPEN', 'FINALIZING') LIMIT 1""",
            (device_id,),
        ).fetchone() is not None

    @staticmethod
    def _heartbeat_on_duty(connection, device_id: str) -> bool:
        row = connection.execute(
            """SELECT body_json FROM device_events WHERE device_id=? AND type='device.heartbeat'
               ORDER BY received_at DESC, rowid DESC LIMIT 1""", (device_id,),
        ).fetchone()
        return row is not None and json.loads(row["body_json"])["onDuty"]

    def _update_window(
        self, connection, device_id: str, event: Mapping[str, object], now: float,
    ) -> None:
        row = connection.execute(
            "SELECT * FROM device_presence WHERE device_id=?", (device_id,),
        ).fetchone()
        active = row["active_run_id"]
        on_duty = row["on_duty"]
        if event["type"] == "run.started":
            active, on_duty = event["runId"], 1
        elif event["type"] == "run.finished" and active == event["runId"]:
            active = None
            on_duty = int(
                self._has_upload(connection, device_id)
                or self._heartbeat_on_duty(connection, device_id)
            )
        elif event["type"] == "device.heartbeat":
            if not event["onDuty"]:
                active, on_duty = None, 0
            else:
                # An upload that starts after run.finished reopens the window
                # until that run's server receipt.
                on_duty = 1
                if event.get("activeRunId"):
                    active = event["activeRunId"]
        connection.execute(
            "UPDATE device_presence SET active_run_id=?, on_duty=?, updated_at=? WHERE device_id=?",
            (active, on_duty, timestamp(now), device_id),
        )

    def judge_heartbeats(self, now: float, *, config: GeonWorkConfig | None = None) -> None:
        config = config or self.configuration.current
        with self.database.immediate() as connection:
            rows = connection.execute(
                "SELECT * FROM device_presence WHERE on_duty=1 AND heartbeat_state='OK'"
            ).fetchall()
            for row in rows:
                device_id = row["device_id"]
                if (
                    not row["active_run_id"] and not self._has_upload(connection, device_id)
                    and not self._heartbeat_on_duty(connection, device_id)
                ):
                    connection.execute(
                        "UPDATE device_presence SET on_duty=0, updated_at=? WHERE device_id=?",
                        (timestamp(now), device_id),
                    )
                    continue
                if parse_timestamp(row["last_seen_at"]) > now - 1800:
                    continue
                connection.execute(
                    """UPDATE device_presence SET heartbeat_state='MISSING', missing_since=?,
                       updated_at=? WHERE device_id=?""", (timestamp(now), timestamp(now), device_id),
                )
                if config:
                    payload = map_event(device_id, row["display_name"], config.channel_id, {
                        "type": "device.missing", "lastHealthyAt": row["last_seen_at"],
                        "occurredAt": timestamp(now),
                    })
                    enqueue(connection, device_id, payload, now)

    def upload_completed(self, session: Mapping[str, object], now: float) -> None:
        context = json.loads(session["manifest_json"]).get("context")
        if not isinstance(context, dict) or not context.get("runId"):
            return
        run_id, device_id = context["runId"], session["device_id"]
        config = self.configuration.current
        # Completion must never wait for the dispatcher or a busy database.
        with self.database.immediate(timeout=0) as connection:
            presence = connection.execute(
                "SELECT * FROM device_presence WHERE device_id=?", (device_id,),
            ).fetchone()
            if presence is not None and presence["active_run_id"] in (None, run_id):
                connection.execute(
                    """UPDATE device_presence SET on_duty=0, active_run_id=NULL,
                       updated_at=? WHERE device_id=?""",
                    (timestamp(now), device_id),
                )
            if config is None:
                return
            event = {
                "type": "upload.completed", "runId": run_id,
                "files": session["file_count"], "bytes": session["total_bytes"],
                "occurredAt": timestamp(now),
            }
            started_row = connection.execute(
                """SELECT body_json FROM device_events
                   WHERE device_id=? AND type='run.started'
                   AND json_extract(body_json, '$.runId')=?
                   ORDER BY received_at DESC, rowid DESC LIMIT 1""", (device_id, run_id),
            ).fetchone()
            if started_row:
                started = json.loads(started_row["body_json"])
                event.update({key: started[key] for key in LABELS if key in started})
            payload = map_event(
                device_id, presence["display_name"] if presence else None, config.channel_id, event,
            )
            enqueue(
                connection, device_id, payload, now,
                idempotency_key="upload-session:" + session["session_id"] + ":completed",
            )

    def status(self, device_id: str | None, now: float) -> dict[str, object]:
        self.configuration.refresh(now)
        where = "target='geon-work'" + (" AND device_id=?" if device_id is not None else "")
        parameters = (device_id,) if device_id is not None else ()
        with closing(self.database.connect()) as connection:
            counts = connection.execute(
                f"""SELECT state, COUNT(*) AS count FROM outbound_messages
                    WHERE {where} AND created_at>=? GROUP BY state""",
                (*parameters, timestamp(now - 86400)),
            ).fetchall()
            delivered = connection.execute(
                f"""SELECT status_text AS status, completed_at AS at FROM outbound_messages
                    WHERE {where} AND state IN ('SENT', 'DEDUP')
                    ORDER BY completed_at DESC, rowid DESC LIMIT 1""", parameters,
            ).fetchone()
            failure = connection.execute(
                f"""SELECT status_text AS status, last_error_code AS code, updated_at AS at
                    FROM outbound_messages WHERE {where} AND last_error_code IS NOT NULL
                    AND state IN ('PENDING', 'DEAD')
                    ORDER BY updated_at DESC, rowid DESC LIMIT 1""", parameters,
            ).fetchone()
            presence = connection.execute(
                "SELECT last_seen_at FROM device_presence WHERE device_id=?", (device_id,),
            ).fetchone()
        totals = {row["state"]: row["count"] for row in counts}
        return {
            "deviceId": device_id, "integration": self.configuration.state(),
            "window": {
                "hours": 24, "pending": totals.get("PENDING", 0) + totals.get("SENDING", 0),
                "sent": totals.get("SENT", 0), "dedup": totals.get("DEDUP", 0),
                "dead": totals.get("DEAD", 0),
            },
            "lastDelivered": dict(delivered) if delivered else None,
            "lastFailure": dict(failure) if failure else None,
            "lastSeenAt": presence["last_seen_at"] if presence else None,
        }
