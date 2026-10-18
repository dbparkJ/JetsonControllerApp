from __future__ import annotations

import json
import time
from contextlib import closing

from .device_events import IDENTIFIER
from .geon_work import timestamp
from .outbound import enqueue
from .service import ReceiverError
from .tickets import TARGET


def report(row):
    if row is None:
        return None
    payload = json.loads(row["payload_json"])
    state = row["state"]
    return {"type": payload["type"], "state": (
        "SENT" if state in ("SENT", "DEDUP") else
        "CONFLICT" if row["last_error_code"] == "HUMAN_CHANGED" else
        "FAILED" if state == "DEAD" else "PENDING"
    ), "code": row["last_error_code"]}


def latest_report(connection, row_id, run_id=None, kind=None):
    where = "target=? AND kind='board-patch' AND json_extract(payload_json, '$.rowId')=?"
    args = [TARGET, row_id]
    if run_id is not None:
        where += " AND json_extract(payload_json, '$.runId')=? AND json_extract(payload_json, '$.type')=?"
        args.extend((run_id, kind))
    return connection.execute(f"SELECT * FROM outbound_messages WHERE {where} ORDER BY rowid DESC LIMIT 1", args).fetchone()


class TicketAPI:
    def __init__(self, receiver, *, clock=time.time):
        self.receiver = receiver
        self.database = receiver.database
        self.configuration = receiver.ticket_configuration
        self.clock = clock

    def _destination(self):
        config = self.configuration.current
        return config.api_base_url + "/api/v1/boards/" + config.board_id if config else None

    def _project(self, employee, project_id, device_id=None):
        try:
            project_id = self.receiver._require_project_grant(employee, project_id)
        except ReceiverError as error:
            if error.status in (400, 403):
                raise ReceiverError(404, "Project was not found") from error
            raise
        if device_id is not None:
            try:
                device_id = self.receiver.validate_device_id(device_id)
            except ReceiverError as error:
                raise ReceiverError(404, "Device was not found in the selected project") from error
            with closing(self.database.connect()) as connection:
                if connection.execute("SELECT 1 FROM project_devices WHERE project_id=? AND device_id=?",
                                      (project_id, device_id)).fetchone() is None:
                    raise ReceiverError(404, "Device was not found in the selected project")
        return project_id

    def status(self):
        self.configuration.refresh(self.clock())
        with closing(self.database.connect()) as connection:
            sync = connection.execute("SELECT * FROM ticket_sync WHERE target=?", (TARGET,)).fetchone()
        return {**self.configuration.state(), "syncedAt": (
            sync["synced_at"] if sync and sync["destination"] == self._destination() else None
        )}

    def list(self, employee, project_id, device_id=None, unit=None, status="open"):
        self._project(employee, project_id, device_id)
        if status not in ("open", "all"):
            raise ReceiverError(400, "INVALID_TICKET_STATUS")
        self.configuration.refresh(self.clock())
        with closing(self.database.connect()) as connection:
            sync = connection.execute("SELECT * FROM ticket_sync WHERE target=?", (TARGET,)).fetchone()
            schema = (json.loads(sync["schema_json"]) if sync and sync["schema_json"]
                      and sync["destination"] == self._destination() else None)
            units = [u["name"] for u in schema["units"]] if schema else []
            status_keys = (schema or {}).get("statusKeys", {})
            device_unit = connection.execute("SELECT unit FROM device_units WHERE device_id=?", (device_id,)).fetchone()
            where, args = ["destination=?"], [self._destination()]
            if status == "open":
                where.append("status_category!='DONE' AND archived_at IS NULL")
            if unit is not None:
                where.append("unit=?")
                args.append(unit)
            rows = connection.execute("SELECT * FROM board_tickets" +
                                      (" WHERE " + " AND ".join(where) if where else "") +
                                      " ORDER BY row_id", args).fetchall()
            tickets = [{
                "rowId": r["row_id"], "title": r["title"], "project": r["project"],
                "unit": r["unit"], "assignees": json.loads(r["assignees_json"]),
                "dueDate": r["due_date"], "status": {"name": r["status_name"], "category": r["status_category"],
                                                     "systemKey": status_keys.get(r["status_id"])},
                "revision": r["revision"], "updatedAt": r["updated_at"], "runId": r["run_id"],
                "report": report(latest_report(connection, r["row_id"])),
            } for r in rows] if unit is None or unit in units else []
        return {"tickets": tickets, "units": units, "deviceUnit": device_unit[0] if device_unit else None,
                "syncedAt": sync["synced_at"] if sync and sync["destination"] == self._destination() else None,
                "integration": self.configuration.state()}

    def set_device_unit(self, device_id, unit):
        device_id = self.receiver.validate_device_id(device_id)
        unit = self.receiver.validate_display_name(unit)
        with self.database.immediate() as connection:
            if connection.execute("SELECT 1 FROM devices WHERE device_id=?", (device_id,)).fetchone() is None:
                raise ValueError("Device was not found")
            connection.execute("INSERT INTO device_units VALUES (?, ?) ON CONFLICT(device_id) DO UPDATE SET unit=excluded.unit",
                               (device_id, unit))

    @staticmethod
    def _run_id(value):
        if not isinstance(value, dict) or not isinstance(value.get("runId"), str) or not IDENTIFIER.fullmatch(value["runId"]):
            raise ReceiverError(400, "INVALID_RUN_ID")
        return value["runId"]

    def _ticket(self, connection, row_id):
        row = connection.execute("SELECT * FROM board_tickets WHERE row_id=? AND destination=?",
                                 (row_id, self._destination())).fetchone()
        if row is None:
            raise ReceiverError(404, "TICKET_NOT_FOUND")
        return row

    def _enqueue(self, connection, ticket, link, kind, values, comment, now):
        integration = connection.execute("SELECT * FROM integration_state WHERE target=?", (TARGET,)).fetchone()
        if integration is None or integration["config_fingerprint"] is None:
            raise ReceiverError(409, "TICKETS_NOT_CONFIGURED")
        key = f"run:{link['run_id']}:" + ("started" if kind == "STARTED" else "completed")
        enqueue(connection, link["device_id"], {
            "rowId": ticket["row_id"], "runId": link["run_id"], "type": kind,
            "originalKey": key, "fingerprint": integration["config_fingerprint"],
            "status": "진행 중" if kind == "STARTED" else "완료", "values": values, "comment": comment,
        }, now, target=TARGET, kind="board-patch", idempotency_key=key,
            incident_key=f"ticket:{ticket['row_id']}:{link['run_id']}", status_text=kind)
        return self._result(connection, ticket["row_id"], link["run_id"], kind)

    @staticmethod
    def _result(connection, row_id, run_id, kind):
        return {"rowId": row_id, "runId": run_id,
                "report": report(latest_report(connection, row_id, run_id, kind))}

    def start(self, employee, project_id, row_id, value):
        self.receiver._require_role(employee, "OPERATOR")
        run_id = self._run_id(value)
        device_id = value.get("deviceId")
        if not isinstance(device_id, str):
            raise ReceiverError(400, "INVALID_DEVICE_ID")
        self._project(employee, project_id, device_id)
        now = self.clock()
        self.configuration.refresh(now)
        with self.database.immediate() as connection:
            ticket = self._ticket(connection, row_id)
            existing = connection.execute("SELECT * FROM ticket_runs WHERE run_id=?", (run_id,)).fetchone()
            if existing:
                if existing["row_id"] != row_id or existing["device_id"] != device_id:
                    raise ReceiverError(409, "TICKET_IN_PROGRESS")
                return self._result(connection, row_id, run_id, "STARTED")
            if ticket["status_category"] == "DONE" or ticket["archived_at"]:
                raise ReceiverError(409, "TICKET_NOT_OPEN")
            # A person marked it blocked; starting must not silently move it to 진행 중.
            sync = connection.execute("SELECT schema_json, destination FROM ticket_sync WHERE target=?",
                                      (TARGET,)).fetchone()
            schema = (json.loads(sync["schema_json"]) if sync and sync["schema_json"]
                      and sync["destination"] == self._destination() else {})
            if ticket["status_id"] in schema.get("blockedStatusIds", []) or ticket["status_name"] == "막힘":
                raise ReceiverError(409, "TICKET_BLOCKED")
            pending = connection.execute(
                """SELECT 1 FROM ticket_runs r WHERE r.row_id=? AND EXISTS (
                       SELECT 1 FROM outbound_messages m WHERE m.target=?
                         AND json_extract(m.payload_json, '$.rowId')=r.row_id
                         AND json_extract(m.payload_json, '$.runId')=r.run_id
                         AND m.state IN ('PENDING','SENDING')
                   )""", (row_id, TARGET),
            ).fetchone()
            if pending or (ticket["status_name"] == "진행 중" and ticket["run_id"] not in (None, "", run_id)):
                raise ReceiverError(409, "TICKET_IN_PROGRESS")
            link = {"row_id": row_id, "run_id": run_id, "device_id": device_id,
                    "employee_id": employee.employee_id, "employee_display_name": employee.display_name}
            connection.execute("INSERT INTO ticket_runs VALUES (?, ?, ?, ?, ?, ?, NULL)",
                               (row_id, run_id, device_id, employee.employee_id, employee.display_name, timestamp(now)))
            presence = connection.execute("SELECT display_name FROM device_presence WHERE device_id=?", (device_id,)).fetchone()
            unit = connection.execute("SELECT unit FROM device_units WHERE device_id=?", (device_id,)).fetchone()
            unit_name = ticket["unit"] or (unit[0] if unit else "")
            device_name = (presence[0] if presence else None) or "장치 " + device_id[:8]
            return self._enqueue(connection, ticket, link, "STARTED", {
                "runId": run_id, "수집 작업자": employee.display_name,
            }, f"수집 시작 · {unit_name} · {device_name}", now)

    def complete(self, employee, project_id, row_id, value):
        self.receiver._require_role(employee, "OPERATOR")
        run_id = self._run_id(value)
        self._project(employee, project_id)
        now = self.clock()
        self.configuration.refresh(now)
        with self.database.immediate() as connection:
            ticket = self._ticket(connection, row_id)
            link = connection.execute("SELECT * FROM ticket_runs WHERE row_id=? AND run_id=?", (row_id, run_id)).fetchone()
            if link is None:
                raise ReceiverError(409, "TICKET_RUN_NOT_LINKED")
            if connection.execute("SELECT 1 FROM project_devices WHERE project_id=? AND device_id=?",
                                  (project_id, link["device_id"])).fetchone() is None:
                raise ReceiverError(404, "Device was not found in the selected project")
            if latest_report(connection, row_id, run_id, "COMPLETED"):
                return self._result(connection, row_id, run_id, "COMPLETED")
            session = connection.execute(
                """SELECT * FROM upload_sessions WHERE state='COMPLETED' AND device_id=?
                   AND json_extract(manifest_json, '$.context.runId')=?
                   ORDER BY completed_at DESC, rowid DESC LIMIT 1""", (link["device_id"], run_id),
            ).fetchone()
            if session is None:
                raise ReceiverError(409, "UPLOAD_NOT_CONFIRMED")
            finished = connection.execute(
                """SELECT body_json FROM device_events WHERE device_id=? AND type='run.finished'
                   AND json_extract(body_json, '$.runId')=? ORDER BY received_at DESC, rowid DESC LIMIT 1""",
                (link["device_id"], run_id),
            ).fetchone()
            event = json.loads(finished[0]) if finished else {}
            gb = round(session["total_bytes"] / 10**9, 2)
            values = {"용량(GB)": gb}
            parts = ["수집 완료", "작업자 " + link["employee_display_name"]]
            if "durationSeconds" in event:
                hours = round(event["durationSeconds"] / 3600, 2)
                values["수집 시간"] = hours
                parts.append(f"{hours:.2f}시간")
            if "preciseFixRatio" in event:
                values["정밀 위치 비율"] = round(event["preciseFixRatio"] * 100, 1)
            parts.extend((f"{gb:.2f} GB", f"파일 {session['file_count']}개", "run " + run_id.rsplit("/", 1)[-1][:8]))
            result = self._enqueue(connection, ticket, link, "COMPLETED", values, " · ".join(parts), now)
            connection.execute("UPDATE ticket_runs SET completed_at=? WHERE row_id=? AND run_id=?",
                               (timestamp(now), row_id, run_id))
            return result
