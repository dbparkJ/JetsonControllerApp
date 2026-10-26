from __future__ import annotations

import copy
import io
import json
import os
import tempfile
import threading
import unittest
import uuid
from collections import deque
from concurrent.futures import ThreadPoolExecutor
from contextlib import closing, redirect_stdout
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import Mock, patch
from urllib.parse import parse_qs, urlsplit

from upload_receiver.database import Database
from upload_receiver.config import Settings
from upload_receiver.geon_work import (
    GeonWorkConfiguration, TicketsConfiguration, parse_timestamp, timestamp,
    validate_config, validate_tickets_config,
)
from upload_receiver.outbound import Dispatcher, SendResult, enqueue
from upload_receiver.signing import canonical_json
from upload_receiver.tickets import (
    MAX_GEOMETRY_BYTES, PROPERTIES, TARGET, TicketSync, resolve_schema, ticket_geometry,
)
from tests.test_device_events import CONFIG, DEVICE_ID, NOW, SECOND_DEVICE_ID


TICKETS = {"apiBaseUrl": "https://board.invalid", "apiToken": "geon_test-private-token",
           "boardId": "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee"}


def identifier(name):
    return str(uuid.uuid5(uuid.NAMESPACE_URL, name))


class FakeBoard:
    """In-process board API, including exact-body idempotency and revision checks."""

    def __init__(self):
        self.board = {
            "id": TICKETS["boardId"], "name": "데이터 수집 구간",
            "properties": [{"id": identifier(name), "name": name, "type": kind}
                           for name, kind in reversed(list(PROPERTIES.items()))],
            # Real GEO& Work shape: '막힘' shares IN_PROGRESS and is told apart by systemKey.
            "statusOptions": [{"id": identifier(name), "name": name, "category": category, "systemKey": key}
                              for name, category, key in (("완료", "DONE", "DONE"), ("할 일", "TODO", "TODO"),
                                                          ("진행 중", "IN_PROGRESS", "IN_PROGRESS"),
                                                          ("막힘", "IN_PROGRESS", "BLOCKED"))],
        }
        self.units = [{"id": identifier("unit-z"), "name": "geo X-1", "color": "blue"},
                      {"id": identifier("unit-a"), "name": "geo X-2", "color": "green"}]
        # SELECT options arrive in properties[].config.options.
        next(p for p in self.board["properties"] if p["name"] == "호기")["config"] = {"options": self.units}
        self.rows = {}
        self.requests = []
        self.faults = deque()
        self.replays = {}
        self.page_size = 2

    def status(self, name):
        return copy.deepcopy(next(s for s in self.board["statusOptions"] if s["name"] == name))

    def add(self, row_id="row-1", *, status="할 일", unit=0, run_id=None, archived=False, updated=NOW, revision=1):
        row = {"id": row_id, "title": "구간 " + row_id, "values": {
            identifier("프로젝트"): {"type": "TEXT", "value": "조사 프로젝트"},
            identifier("호기"): {"type": "SELECT", "value": self.units[unit]["id"] if unit is not None else None},
            identifier("runId"): {"type": "TEXT", "value": run_id},
        }, "status": self.status(status), "assignees": [{"id": "person", "displayName": "담당자"}],
               "dueDate": "2026-10-25", "revision": revision, "updatedAt": timestamp(updated),
               "archivedAt": timestamp(updated) if archived else None}
        self.rows[row_id] = row
        return row

    def __call__(self, request):
        self.requests.append(request)
        if request.headers.get("Authorization") != "Bearer " + TICKETS["apiToken"]:
            return SendResult(401, "UNAUTHORIZED")
        if self.faults:
            fault = self.faults.popleft()
            if isinstance(fault, Exception):
                raise fault
            return fault
        path = urlsplit(request.url)
        base = "/api/v1/boards/" + self.board["id"]
        if path.path == base and request.method == "GET":
            return SendResult(200, data=copy.deepcopy(self.board))
        if path.path == base + "/rows" and request.method == "GET":
            query = parse_qs(path.query)
            assert query["limit"] == ["100"]
            rows = sorted(self.rows.values(), key=lambda r: r["id"])
            if query["status"] == ["open"]:
                rows = [r for r in rows if r["status"]["category"] != "DONE" and not r["archivedAt"]]
            if "updatedSince" in query:
                since = parse_timestamp(query["updatedSince"][0])
                rows = [r for r in rows if parse_timestamp(r["updatedAt"]) >= since]
            offset = int(query.get("cursor", ["0"])[0])
            page = rows[offset:offset + self.page_size]
            cursor = str(offset + len(page)) if offset + len(page) < len(rows) else None
            return SendResult(200, data=copy.deepcopy({"items": page, "nextCursor": cursor}))
        if request.method == "PATCH" and path.path.startswith(base + "/rows/"):
            row_id = path.path.rsplit("/", 1)[-1]
            body = json.loads(request.body)
            key = body["idempotencyKey"]
            identity = (request.url, request.body)
            if key in self.replays:
                previous, response = self.replays[key]
                if previous != identity:
                    return SendResult(409, "IDEMPOTENCY_KEY_CONFLICT")
                return SendResult(200, data={**copy.deepcopy(response), "deduplicated": True})
            row = self.rows[row_id]
            if row["archivedAt"]:
                return SendResult(400, "ROW_ARCHIVED")
            if row["revision"] != body["expectedRevision"]:
                return SendResult(409, "ROW_REVISION_CONFLICT", data={"current": copy.deepcopy(row)})
            row["status"] = copy.deepcopy(next(s for s in self.board["statusOptions"] if s["id"] == body["status"]))
            row["values"].update(body.get("values", {}))
            row["revision"] += 1
            row["updatedAt"] = timestamp(NOW + row["revision"])
            response = {"row": copy.deepcopy(row), "event": {"id": "event"}, "deduplicated": False}
            self.replays[key] = (identity, response)
            return SendResult(200, data=response)
        raise AssertionError("Unexpected board request")


class TicketsCoreTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.database = Database(Path(temporary.name) / "db")
        self.database.initialize()
        with self.database.immediate() as connection:
            connection.execute("INSERT INTO devices VALUES (?, ?, 1, 10000, NULL, ?, ?)",
                               (DEVICE_ID, DEVICE_ID, timestamp(NOW), timestamp(NOW)))
        self.configuration = TicketsConfiguration(Path(temporary.name) / "unused", self.database)
        self.configuration._config = validate_tickets_config(TICKETS)
        self.configuration._set_state("ACTIVE", None, "tickets-fixture", NOW)
        refresh = patch.object(self.configuration, "refresh", side_effect=lambda now: self.configuration.current)
        refresh.start()
        self.addCleanup(refresh.stop)
        self.webhook = GeonWorkConfiguration(Path(temporary.name) / "unused", self.database)
        self.webhook._config = validate_config(CONFIG)
        self.webhook._set_state("ACTIVE", None, "webhook-fixture", NOW)
        refresh = patch.object(self.webhook, "refresh", return_value=self.webhook.current)
        refresh.start()
        self.addCleanup(refresh.stop)
        self.api = FakeBoard()
        self.api.add()
        self.sync = TicketSync(self.database, self.configuration, sender=self.api, clock=lambda: NOW)
        self.dispatcher = Dispatcher(self.database, self.webhook, Mock(), tickets=self.sync, sender=self.api,
                                     jitter=lambda a, b: 1)

    def initialize(self):
        self.assertTrue(self.sync.run_once(NOW))
        self.assertTrue(self.sync.run_once(NOW + 0.5))

    def messages(self):
        with closing(self.database.connect()) as connection:
            return [dict(r) for r in connection.execute("SELECT * FROM outbound_messages ORDER BY rowid")]

    def tickets(self):
        with closing(self.database.connect()) as connection:
            return [dict(r) for r in connection.execute("SELECT * FROM board_tickets ORDER BY row_id")]

    def board_reads(self):
        base = "/api/v1/boards/" + self.api.board["id"]
        return [r for r in self.api.requests if urlsplit(r.url).path == base and r.method == "GET"]

    def device_unit(self, unit=None):
        with self.database.immediate() as connection:
            if unit is not None:
                connection.execute("INSERT INTO device_units VALUES (?, ?)", (DEVICE_ID, unit))
            return connection.execute("SELECT unit FROM device_units WHERE device_id=?", (DEVICE_ID,)).fetchone()[0]

    def stored_units(self):
        with closing(self.database.connect()) as connection:
            schema = json.loads(connection.execute("SELECT schema_json FROM ticket_sync").fetchone()[0])
        return [u["name"] for u in schema["units"]]

    def enqueue(self, kind="STARTED", run_id="capture/run-123456789", row_id="row-1"):
        key = f"run:{run_id}:" + ("started" if kind == "STARTED" else "completed")
        payload = {"rowId": row_id, "runId": run_id, "type": kind, "originalKey": key,
                   "fingerprint": "tickets-fixture", "status": "진행 중" if kind == "STARTED" else "완료",
                   "values": {"runId": run_id, "수집 작업자": "작업자"} if kind == "STARTED" else {},
                   "comment": "수집 시작 · geo X-1 · 장치"}
        with self.database.immediate() as connection:
            return enqueue(connection, DEVICE_ID, payload, NOW + 1, target=TARGET, kind="board-patch",
                           incident_key=f"ticket:{row_id}:{run_id}", status_text=kind, idempotency_key=key)

    def ticket_api(self, now):
        from upload_receiver.ticket_api import TicketAPI
        receiver = SimpleNamespace(database=self.database, ticket_configuration=self.configuration,
                                   _require_project_grant=lambda employee, project: project)
        api = TicketAPI(receiver, clock=lambda: now)
        api.sync = self.sync
        return api

    def test_default_incremental_interval_is_thirty_seconds(self):
        self.initialize()
        self.assertFalse(self.sync.run_once(NOW + 30))
        self.assertTrue(self.sync.run_once(NOW + 30.5))
        query = parse_qs(urlsplit(self.api.requests[-1].url).query)
        self.assertEqual(query["status"], ["all"])
        self.assertIn("updatedSince", query)

    def test_configured_interval_and_schema_deadline(self):
        for interval in (10, 45, 600):
            with self.subTest(interval=interval):
                sync = TicketSync(self.database, self.configuration, sender=self.api,
                                  clock=lambda: NOW, interval_seconds=interval)
                self.assertTrue(sync.run_once(NOW))
                self.assertTrue(sync.run_once(NOW + 0.5))
                self.assertFalse(sync.run_once(NOW + min(interval, 599.5)))
                if interval < 600:
                    self.assertTrue(sync.run_once(NOW + interval + 0.5))
                # The schema still refreshes at ten minutes with a long sync interval.
                self.assertTrue(sync.run_once(NOW + 600))
                self.assertEqual(urlsplit(self.api.requests[-1].url).query, "")
                self.assertTrue(sync.run_once(NOW + 600.5))
                self.assertEqual(parse_qs(urlsplit(self.api.requests[-1].url).query)["status"], ["open"])

    def test_read_refresh_requires_more_than_fifteen_seconds(self):
        self.initialize()
        self.assertFalse(self.sync.request_sync_if_stale(NOW + 15.5))
        self.assertTrue(self.sync.request_sync_if_stale(NOW + 15.501))
        self.assertTrue(self.sync.run_once(NOW + 15.501))
        self.assertFalse(self.sync.request_sync_if_stale(NOW + 16))

    def test_concurrent_read_refresh_is_scheduled_and_run_once(self):
        self.initialize()
        with ThreadPoolExecutor(max_workers=8) as pool:
            scheduled = list(pool.map(self.sync.request_sync_if_stale, [NOW + 16] * 32))
            self.assertEqual(sum(scheduled), 1)
            ran = list(pool.map(self.sync.run_once, [NOW + 16] * 32))
        self.assertEqual(sum(ran), 1)
        self.assertEqual(len(self.api.requests), 3)
        self.assertFalse(self.sync.request_sync_if_stale(NOW + 16.1))

    @unittest.skipUnless(os.name == "posix", "Ticket API imports receiver storage, which requires Linux/WSL")
    def test_list_returns_snapshot_before_scheduled_refresh(self):
        self.initialize()
        self.api.add(revision=2, updated=NOW + 10)
        api = self.ticket_api(NOW + 16)
        for _ in range(10):
            response = api.list("employee", "project")
            self.assertEqual(response["tickets"][0]["revision"], 1)
            self.assertEqual(response["syncedAt"], timestamp(NOW + 0.5))
        self.assertEqual(len(self.api.requests), 2)
        self.assertTrue(self.sync.run_once(NOW + 16))
        self.assertEqual(api.list("employee", "project")["tickets"][0]["revision"], 2)
        self.assertFalse(self.sync.run_once(NOW + 16.5))

    @unittest.skipUnless(os.name == "posix", "Ticket API imports receiver storage, which requires Linux/WSL")
    def test_list_does_not_wait_for_inflight_network_request(self):
        self.initialize()
        api = self.ticket_api(NOW + 16)
        self.assertTrue(self.sync.request_sync_if_stale(NOW + 16))
        started, release = threading.Event(), threading.Event()

        def send(request):
            started.set()
            if not release.wait(5):
                raise AssertionError("Test did not release network request")
            return self.api(request)

        self.sync.sender = send
        with ThreadPoolExecutor(max_workers=2) as pool:
            syncing = pool.submit(self.sync.run_once, NOW + 16)
            try:
                self.assertTrue(started.wait(2))
                reading = pool.submit(api.list, "employee", "project")
                self.assertEqual(reading.result(timeout=2)["tickets"][0]["revision"], 1)
                self.assertFalse(syncing.done())
                self.assertFalse(self.sync.request_sync_if_stale(NOW + 17))
            finally:
                release.set()
            self.assertTrue(syncing.result(timeout=2))

    def test_read_refresh_does_not_wait_for_sync_guard(self):
        self.initialize()
        with self.sync._guard:
            self.assertFalse(self.sync.request_sync_if_stale(NOW + 16))
        self.assertTrue(self.sync.request_sync_if_stale(NOW + 16))

    def test_reads_during_paging_do_not_schedule_another_pass(self):
        self.initialize()
        self.api.add("row-2", updated=NOW + 10)
        self.api.add("row-3", updated=NOW + 11)
        self.assertTrue(self.sync.request_sync_if_stale(NOW + 16))
        self.assertTrue(self.sync.run_once(NOW + 16))
        self.assertFalse(self.sync.request_sync_if_stale(NOW + 32))
        self.assertTrue(self.sync.run_once(NOW + 32))
        self.assertFalse(self.sync.run_once(NOW + 32.5))
        self.assertEqual(len(self.api.requests), 4)

    def test_read_refresh_does_not_advance_patch_backoff(self):
        self.initialize()
        self.enqueue()
        self.api.faults.append(SendResult(503))
        self.assertTrue(self.dispatcher.run_once(NOW + 1))
        deadline = self.sync._next_sync
        for now in (NOW + 16, NOW + 30):
            self.assertFalse(self.sync.request_sync_if_stale(now))
            self.assertEqual(self.sync._next_sync, deadline)
            self.assertFalse(self.sync.run_once(now))
        self.assertTrue(self.sync.run_once(NOW + 31))

    def test_read_refresh_does_not_advance_sync_failure_backoff(self):
        self.initialize()
        self.assertTrue(self.sync.request_sync_if_stale(NOW + 16))
        self.api.faults.append(SendResult(429, "RATE_LIMITED", "90"))
        with self.assertLogs("upload_receiver.tickets", level="WARNING"):
            self.assertTrue(self.sync.run_once(NOW + 16))
        for now in (NOW + 32, NOW + 105.5):
            self.assertFalse(self.sync.request_sync_if_stale(now))
            self.assertFalse(self.sync.run_once(now))
        self.assertEqual(len(self.api.requests), 3)
        self.assertTrue(self.sync.run_once(NOW + 106))

    def test_frequent_reads_and_paging_share_budget_with_schema_and_patch(self):
        self.api.page_size = 1
        self.api.add("row-2")
        self.api.add("row-3")
        sent_at = []
        now = NOW

        def send(request):
            sent_at.append(now)
            return self.api(request)

        self.sync.sender = self.dispatcher.sender = send
        self.sync.clock = lambda: now
        for offset in range(20):
            now = NOW + offset / 10
            self.sync.run_once(now)
        self.enqueue()
        # Force a new schema/full pass, then saturate the shared budget with pages and PATCH.
        now = NOW + 600
        for offset in range(600):
            now = NOW + 600 + offset / 10
            self.sync.request_sync_if_stale(now)
            self.sync.run_once(now)
            self.dispatcher.run_once(now)
        self.assertTrue(any(r.method == "PATCH" for r in self.api.requests))
        self.assertEqual(len(self.board_reads()), 2)
        self.assertTrue(all(b - a >= 0.5 for a, b in zip(sent_at, sent_at[1:])))
        self.assertLessEqual(sum(NOW + 600 <= t < NOW + 660 for t in sent_at), 120)

    def test_idle_default_cadence_fits_existing_budget(self):
        # One-page board: two row reads per minute plus periodic schema/full reads.
        for offset in range(1202):
            self.sync.run_once(NOW + offset / 2)
        self.assertLessEqual(len(self.api.requests), 23)
        self.assertEqual(len(self.board_reads()), 2)

    def test_schema_names_types_options_and_mismatch_no_patch(self):
        schema = resolve_schema(self.api.board)
        self.assertEqual(set(schema["properties"]), set(PROPERTIES))
        self.assertEqual(schema["units"], self.api.units)
        for name in PROPERTIES:
            board = copy.deepcopy(self.api.board)
            next(p for p in board["properties"] if p["name"] == name)["type"] = "WRONG"
            with self.assertRaisesRegex(ValueError, "BOARD_SCHEMA_MISMATCH"):
                resolve_schema(board)
        board = copy.deepcopy(self.api.board)
        board["statusOptions"] = [s for s in board["statusOptions"] if s["name"] != "진행 중"]
        with self.assertRaises(ValueError):
            resolve_schema(board)
        board = copy.deepcopy(self.api.board)
        board["properties"].pop()
        with self.assertRaises(ValueError):
            resolve_schema(board)
        # Extra options such as '막힘' are not required.
        board = copy.deepcopy(self.api.board)
        board["statusOptions"] = [s for s in board["statusOptions"] if s["name"] != "막힘"]
        self.assertEqual(resolve_schema(board)["blockedStatusIds"], [])
        self.api.board["statusOptions"][0].pop("category")
        self.sync.run_once(NOW)
        self.assertEqual(self.configuration.state(), {"state": "ERROR", "detail": "BOARD_SCHEMA_MISMATCH"})
        self.assertFalse(self.dispatcher.run_once(NOW + 1))
        self.assertEqual(len(self.api.requests), 1)

    def test_geometry_validation_checks_size_and_json_shape_only(self):
        board = copy.deepcopy(self.api.board)
        prop_id = identifier("수집 구역")
        board["properties"].append({"id": prop_id, "name": "수집 구역", "type": "TEXT"})
        schema = resolve_schema(board)
        geometry = {
            "schemaVersion": 1,
            "features": [],
            "range": {"bufferM": 10, "polygon": {"type": "Polygon", "coordinates": []},
                      "bbox": [1, 2, 3, 4], "lengthM": 100, "areaM2": 200},
            "revision": 1,
            "unrecognizedDraftField": {"kept": True},
        }
        row = {"values": {prop_id: {"type": "GEOMETRY", "value": json.dumps(geometry, ensure_ascii=False)}}}
        self.assertEqual(json.loads(ticket_geometry(row, schema)), geometry)
        row["values"][prop_id]["value"] = geometry
        self.assertEqual(json.loads(ticket_geometry(row, schema)), geometry)
        # Still accepted: a route-less range (lengthM null), the first draft's wrapper and a text revision.
        for accepted in (
            {**geometry, "range": {**geometry["range"], "lengthM": None}},
            {**geometry, "features": {"type": "FeatureCollection", "features": []}, "revision": "zone-r1"},
        ):
            row["values"][prop_id]["value"] = accepted
            self.assertEqual(json.loads(ticket_geometry(row, schema)), accepted)

        for value in (
            "not json",
            json.dumps([geometry]),
            json.dumps({**geometry, "schemaVersion": 2}),
            json.dumps({**geometry, "features": [{"type": "Feature", "geometry": {"type": "Circle"}}]}),
            json.dumps({**geometry, "features": [{"type": "Feature", "geometry": {"type": "Point"},
                                                  "properties": {"role": "SPOT"}}]}),
            json.dumps({**geometry, "revision": None}),
            json.dumps({**geometry, "range": []}),
            " " * (MAX_GEOMETRY_BYTES + 1),
        ):
            row["values"][prop_id]["value"] = value
            self.assertIsNone(ticket_geometry(row, schema), value[:80])

    def test_ticket_geometry_is_cached_for_a_synced_board_row(self):
        prop_id = identifier("수집 구역")
        self.api.board["properties"].append({"id": prop_id, "name": "수집 구역", "type": "TEXT"})
        geometry = {
            "schemaVersion": 1,
            "features": [],
            "range": {"bufferM": 10, "polygon": {"type": "Polygon", "coordinates": []},
                      "bbox": [1, 2, 3, 4], "lengthM": 100, "areaM2": 200},
            "revision": 3,
            "extra": ["preserved"],
        }
        self.api.rows["row-1"]["values"][prop_id] = {
            "type": "TEXT", "value": json.dumps(geometry, ensure_ascii=False),
        }
        self.initialize()
        with closing(self.database.connect()) as connection:
            stored = connection.execute(
                "SELECT geometry_json FROM board_ticket_geometry WHERE row_id='row-1'"
            ).fetchone()
        self.assertIsNotNone(stored)
        self.assertEqual(json.loads(stored[0]), geometry)

    def test_full_then_incremental_overlap_paging_done_archived_and_no_regression(self):
        self.api.add("row-2", unit=1, updated=NOW + 2)
        self.api.add("row-3", updated=NOW + 8)
        self.initialize()
        self.assertEqual(len(self.tickets()), 2)
        self.assertTrue(self.sync.run_once(NOW + 1))
        self.assertEqual(len(self.tickets()), 3)
        self.assertEqual(parse_qs(urlsplit(self.api.requests[-1].url).query)["cursor"], ["2"])
        self.assertFalse(self.sync.run_once(NOW + 30.5))
        self.api.add("row-1", status="완료", updated=NOW + 10, revision=2)
        self.api.add("row-2", archived=True, updated=NOW + 9, revision=2)
        self.api.add("row-4", updated=NOW + 6)
        self.assertTrue(self.sync.run_once(NOW + 121))
        query = parse_qs(urlsplit(self.api.requests[-1].url).query)
        self.assertEqual(query["status"], ["all"])
        self.assertEqual(parse_timestamp(query["updatedSince"][0]), NOW + 3)
        self.sync.run_once(NOW + 121.5)
        rows = self.tickets()
        self.assertEqual(rows[0]["status_category"], "DONE")
        self.assertIsNotNone(rows[1]["archived_at"])
        self.assertEqual(rows[1]["unit"], "geo X-1")
        self.assertEqual(json.loads(rows[0]["assignees_json"]), ["담당자"])
        self.assertEqual(rows[0]["project"], "조사 프로젝트")
        self.assertEqual(rows[0]["due_date"], "2026-10-25")
        # A late sync snapshot must not overwrite a newer PATCH revision.
        self.api.add("row-1", revision=1, updated=NOW + 10)
        self.sync.run_once(NOW + 242)
        self.assertEqual(self.tickets()[0]["revision"], 2)

    def test_unit_renamed_on_the_board_reaches_tickets_and_device_units_without_restart(self):
        self.initialize()
        self.device_unit("geo X-1")
        self.api.units[0]["name"] = "GEO-X-1"
        self.assertTrue(self.sync.run_once(NOW + 121))
        self.assertEqual(len(self.board_reads()), 1)
        self.assertEqual(self.tickets()[0]["unit"], "geo X-1")
        # Ten minutes after the last schema read, the next sync reads the board again.
        self.assertTrue(self.sync.run_once(NOW + 601))
        self.assertEqual(len(self.board_reads()), 2)
        self.assertEqual(self.tickets()[0]["unit"], "GEO-X-1")
        self.assertEqual(self.device_unit(), "GEO-X-1")
        self.assertEqual(self.stored_units(), ["GEO-X-1", "geo X-2"])
        self.assertTrue(self.sync.run_once(NOW + 601.5))
        self.assertEqual(parse_qs(urlsplit(self.api.requests[-1].url).query)["status"], ["open"])
        # Ten minutes later the schema is read again, followed by another full pass.
        self.assertTrue(self.sync.run_once(NOW + 1202))
        self.assertEqual(len(self.board_reads()), 3)
        self.assertTrue(self.sync.run_once(NOW + 1202.5))
        self.assertEqual(parse_qs(urlsplit(self.api.requests[-1].url).query)["status"], ["open"])

    def test_rows_that_leave_the_board_unseen_are_closed_by_the_next_full_pass(self):
        for row_id in ("row-2", "row-3", "row-4"):
            self.api.add(row_id)
        self.initialize()
        self.assertTrue(self.sync.run_once(NOW + 1))
        self.assertEqual(len(self.tickets()), 4)
        # A deleted row drops out of every listing without an archivedAt update.
        removed = self.api.rows.pop("row-2")
        self.assertTrue(self.sync.run_once(NOW + 121))
        self.assertTrue(self.sync.run_once(NOW + 121.5))
        self.assertIsNone(self.tickets()[1]["archived_at"])
        self.assertTrue(self.sync.run_once(NOW + 601))
        # Closing waits for the last page of the full pass.
        self.assertTrue(self.sync.run_once(NOW + 601.5))
        self.assertIsNone(self.tickets()[1]["archived_at"])
        self.assertTrue(self.sync.run_once(NOW + 602))
        self.assertEqual([t["row_id"] for t in self.tickets() if t["archived_at"]], ["row-2"])
        # A restored row is open again after the following full pass.
        self.api.rows["row-2"] = {**removed, "revision": 2}
        for moment in (NOW + 1202, NOW + 1202.5, NOW + 1203):
            self.assertTrue(self.sync.run_once(moment))
        self.assertEqual([t["row_id"] for t in self.tickets() if t["archived_at"]], [])

    def test_restart_carries_a_unit_rename_into_tickets_and_device_units(self):
        self.initialize()
        self.device_unit("geo X-1")
        self.api.units[0]["name"] = "GEO-X-1"
        restarted = TicketSync(self.database, self.configuration, sender=self.api, clock=lambda: NOW)
        self.assertTrue(restarted.run_once(NOW + 1))
        self.assertEqual(self.tickets()[0]["unit"], "GEO-X-1")
        self.assertEqual(self.device_unit(), "GEO-X-1")

    def test_swapped_unit_names_follow_their_option_ids(self):
        self.api.add("row-2", unit=1)
        self.initialize()
        self.device_unit("geo X-1")
        self.api.units[0]["name"], self.api.units[1]["name"] = "geo X-2", "geo X-1"
        self.assertTrue(self.sync.run_once(NOW + 601))
        self.assertEqual([t["unit"] for t in self.tickets()], ["geo X-2", "geo X-1"])
        self.assertEqual(self.device_unit(), "geo X-2")

    def test_schema_mismatch_found_on_a_later_read_pauses_tickets(self):
        self.initialize()
        self.api.board["properties"].pop()
        self.sync.run_once(NOW + 601)
        self.assertEqual(self.configuration.state(), {"state": "ERROR", "detail": "BOARD_SCHEMA_MISMATCH"})

    def test_shared_rate_limit_and_retry_after_backoff(self):
        self.api.faults.append(SendResult(429, "RATE_LIMITED", "9"))
        self.assertTrue(self.sync.run_once(NOW))
        self.assertFalse(self.sync.run_once(NOW + 8))
        self.assertTrue(self.sync.run_once(NOW + 9))
        self.assertFalse(self.sync.run_once(NOW + 9.49))
        self.sync.run_once(NOW + 9.5)
        self.enqueue()
        self.assertFalse(self.dispatcher.run_once(NOW + 9.9))
        self.api.faults.append(OSError("private token " + TICKETS["apiToken"]))
        self.dispatcher.run_once(NOW + 10)
        self.assertEqual(parse_timestamp(self.messages()[0]["next_attempt_at"]), NOW + 40)
        self.assertFalse(self.sync.run_once(NOW + 11))
        self.dispatcher.run_once(NOW + 40)
        self.assertEqual(self.messages()[0]["state"], "SENT")

    def test_patch_body_frozen_across_rate_server_network_retries_and_restart(self):
        self.initialize()
        self.enqueue()
        self.api.faults.append(SendResult(429, "RATE_LIMITED", "7"))
        self.dispatcher.run_once(NOW + 1)
        original = self.api.requests[-1].body
        frozen = json.loads(self.messages()[0]["payload_json"])["body"]
        self.assertEqual(json.loads(original), frozen)
        with self.database.immediate() as connection:
            connection.execute("UPDATE board_tickets SET revision=999")
        self.api.faults.append(SendResult(503, "UNAVAILABLE"))
        self.dispatcher.run_once(NOW + 8)
        self.assertEqual(self.api.requests[-1].body, original)
        # A receiver restart still reads the committed body.
        self.dispatcher = Dispatcher(self.database, self.webhook, Mock(), sender=self.api, tickets=self.sync)
        self.api.faults.append(OSError("private"))
        self.dispatcher.run_once(NOW + 68)
        self.assertEqual(self.api.requests[-1].body, original)
        self.dispatcher.run_once(NOW + 188)
        self.assertEqual(self.api.requests[-1].body, original)
        self.assertEqual(self.messages()[0]["state"], "SENT")

    def test_idempotent_replay_after_lost_success_response(self):
        self.initialize()
        self.enqueue()
        def lost(request):
            self.api(request)
            raise OSError("lost response")
        self.dispatcher.sender = lost
        self.dispatcher.run_once(NOW + 1)
        original = self.api.requests[-1].body
        self.dispatcher.sender = self.api
        self.dispatcher.run_once(NOW + 31)
        self.assertEqual(self.api.requests[-1].body, original)
        self.assertEqual(self.messages()[0]["state"], "DEDUP")
        self.assertEqual(self.tickets()[0]["revision"], 2)
        # The fake also rejects a reused key with a different body.
        from upload_receiver.outbound import OutboundRequest
        previous = self.api.requests[-1]
        body = json.loads(previous.body)
        body["comment"] = "changed"
        result = self.api(OutboundRequest(previous.url, canonical_json(body), previous.headers, method="PATCH"))
        self.assertEqual(result.error_code, "IDEMPOTENCY_KEY_CONFLICT")

    def test_revision_conflict_attempt2_and_human_changed(self):
        self.initialize()
        self.enqueue()
        self.api.rows["row-1"]["revision"] = 5
        self.dispatcher.run_once(NOW + 1)
        message = self.messages()[0]
        self.assertEqual(message["state"], "PENDING")
        self.assertEqual(message["idempotency_key"], "run:capture/run-123456789:started:attempt2")
        self.dispatcher.run_once(NOW + 1.5)
        body = json.loads(self.api.requests[-1].body)
        self.assertEqual(body["expectedRevision"], 5)
        self.assertEqual(body["idempotencyKey"], message["idempotency_key"])
        self.assertEqual(self.messages()[0]["state"], "SENT")
        self.enqueue("COMPLETED")
        self.api.rows["row-1"]["revision"] += 1
        self.api.rows["row-1"]["status"] = self.api.status("완료")
        self.dispatcher.run_once(NOW + 2)
        self.assertEqual(self.messages()[1]["last_error_code"], "HUMAN_CHANGED")
        self.assertEqual(self.messages()[1]["state"], "DEAD")
        self.assertEqual(self.tickets()[0]["status_name"], "완료")

    def test_revision_conflict_foreign_run_and_archived_are_human_changes(self):
        self.initialize()
        self.enqueue()
        current = copy.deepcopy(self.api.rows["row-1"])
        current["revision"] = 2
        current["status"] = self.api.status("진행 중")
        current["values"][identifier("runId")]["value"] = "other/run"
        self.api.faults.append(SendResult(409, "ROW_REVISION_CONFLICT", data={"current": current}))
        self.dispatcher.run_once(NOW + 1)
        self.assertEqual(self.messages()[0]["last_error_code"], "HUMAN_CHANGED")

    def test_permanent_errors_logs_and_independent_pause(self):
        self.initialize()
        self.enqueue()
        self.webhook._set_state("ERROR", "SIGNATURE_REJECTED", "webhook-fixture", NOW)
        with self.assertLogs("upload_receiver.tickets", level="WARNING") as logs:
            self.api.faults.append(SendResult(403, "INTEGRATION_BOARD_FORBIDDEN"))
            self.assertTrue(self.dispatcher.run_once(NOW + 1))
        self.assertEqual(self.configuration.state()["state"], "ERROR")
        self.assertNotIn(TICKETS["apiToken"], " ".join(logs.output))
        self.assertNotIn("수집 시작", " ".join(logs.output))
        self.webhook._set_state("ACTIVE", None, "webhook-fixture", NOW)
        with self.database.immediate() as connection:
            enqueue(connection, DEVICE_ID, {"incidentKey": "incident", "status": "status"}, NOW + 2)
        self.dispatcher.sender = lambda request: SendResult(201)
        self.assertTrue(self.dispatcher.run_once(NOW + 2))
        self.assertEqual(self.messages()[1]["state"], "SENT")
        self.assertEqual(self.configuration.state()["state"], "ERROR")

    def test_archived_patch_is_dead(self):
        self.initialize()
        self.enqueue()
        self.api.rows["row-1"]["archivedAt"] = timestamp(NOW)
        with self.assertLogs("upload_receiver.tickets", level="WARNING"):
            self.dispatcher.run_once(NOW + 1)
        self.assertEqual(self.messages()[0]["last_error_code"], "ROW_ARCHIVED")
        self.assertEqual(self.messages()[0]["state"], "DEAD")

    def test_start_report_precedes_completion_even_when_start_backs_off(self):
        self.initialize()
        self.enqueue()
        self.enqueue("COMPLETED")
        self.api.faults.append(SendResult(500))
        self.dispatcher.run_once(NOW + 1)
        self.assertFalse(self.dispatcher.run_once(NOW + 2))
        self.dispatcher.run_once(NOW + 31)
        self.dispatcher.run_once(NOW + 31.5)
        self.assertEqual([m["state"] for m in self.messages()], ["SENT", "SENT"])
        self.assertEqual(self.tickets()[0]["status_name"], "완료")

    def test_sync_backoff_doubles_caps_and_http_retry_after_date(self):
        from email.utils import formatdate
        from upload_receiver.tickets import retry_delay
        self.assertEqual([retry_delay(SendResult(503), NOW, n) for n in range(1, 8)],
                         [30, 60, 120, 240, 480, 600, 600])
        self.assertEqual(retry_delay(SendResult(429, retry_after=formatdate(NOW + 90, usegmt=True)), NOW, 1), 90)
        for value in ("0", "-1", "nan", "inf"):
            self.assertEqual(retry_delay(SendResult(429, retry_after=value), NOW, 1), 0.5)
        self.api.faults.extend([SendResult(503), OSError("private " + TICKETS["apiToken"])])
        with self.assertLogs("upload_receiver.tickets", level="WARNING") as logs:
            self.sync.run_once(NOW)
            self.assertFalse(self.sync.run_once(NOW + 29.5))
            self.sync.run_once(NOW + 30)
            self.assertFalse(self.sync.run_once(NOW + 89.5))
        self.assertNotIn(TICKETS["apiToken"], " ".join(logs.output))
        self.sync.run_once(NOW + 90)
        self.assertEqual(len(self.api.requests), 3)

    def test_sync_authorization_error_pauses_until_tickets_config_changes(self):
        self.api.faults.append(SendResult(401, "UNAUTHORIZED"))
        with self.assertLogs("upload_receiver.tickets", level="WARNING"):
            self.sync.run_once(NOW)
        self.assertFalse(self.sync.run_once(NOW + 1000))
        self.assertEqual(self.configuration.state()["state"], "ERROR")
        self.configuration._set_state("ACTIVE", None, "new-ticket-config", NOW + 1001)
        self.assertTrue(self.sync.run_once(NOW + 1001))
        self.assertIsNotNone(self.sync._schema)

    def test_every_permanent_patch_code_is_dead_without_retry(self):
        self.initialize()
        codes = [(400, "VALIDATION_ERROR"), (400, "INTEGRATION_STATUS_INVALID"),
                 (400, "PROPERTY_VALUE_INVALID"), (400, "BOARD_ARCHIVED"),
                 (400, "ROW_ARCHIVED"), (409, "IDEMPOTENCY_KEY_CONFLICT"),
                 (401, "UNAUTHORIZED"), (403, "INTEGRATION_BOARD_FORBIDDEN")]
        for index, (status, code) in enumerate(codes):
            with self.subTest(code=code):
                self.configuration._set_state("ACTIVE", None, "tickets-fixture", NOW)
                self.enqueue(run_id=f"capture/run-{index}")
                self.api.faults.append(SendResult(status, code))
                with self.assertLogs("upload_receiver.tickets", level="WARNING"):
                    self.dispatcher.run_once(NOW + index + 1)
                message = self.messages()[-1]
                self.assertEqual((message["state"], message["last_error_code"]), ("DEAD", code))
                self.assertEqual(self.configuration.state()["state"], "ERROR" if status in (401, 403) else "ACTIVE")

    def test_revision_conflict_in_progress_empty_or_matching_run_is_safe(self):
        self.initialize()
        for index, (kind, linked) in enumerate((("STARTED", None), ("STARTED", "ours"),
                                              ("COMPLETED", None), ("COMPLETED", "ours"))):
            run_id = f"capture/run-{index}"
            self.enqueue(kind, run_id)
            current = self.api.add(status="진행 중", run_id=run_id if linked else None,
                                   revision=10 + index, updated=NOW + index)
            self.api.faults.append(SendResult(409, "ROW_REVISION_CONFLICT", data={"current": copy.deepcopy(current)}))
            self.dispatcher.run_once(NOW + 1 + index * 2)
            self.assertEqual(self.messages()[-1]["state"], "PENDING")
            self.assertTrue(self.messages()[-1]["idempotency_key"].endswith(":attempt2"))
            self.dispatcher.run_once(NOW + 1.5 + index * 2)
            self.assertEqual(self.messages()[-1]["state"], "SENT")
            # Reset the local snapshot for the next independent request.
            with self.database.immediate() as connection:
                connection.execute("UPDATE board_tickets SET status_name='진행 중', status_category='IN_PROGRESS', run_id=NULL")

    def test_synced_human_change_before_first_send_is_not_overwritten(self):
        self.initialize()
        self.enqueue()
        with self.database.immediate() as connection:
            connection.execute("UPDATE board_tickets SET status_name='완료', status_category='DONE', revision=2")
        with self.assertLogs("upload_receiver.tickets", level="WARNING"):
            self.dispatcher.run_once(NOW + 1)
        self.assertEqual(self.messages()[0]["last_error_code"], "HUMAN_CHANGED")
        self.assertFalse(any(request.method == "PATCH" for request in self.api.requests))

    def test_token_rotation_keeps_frozen_body_and_key(self):
        self.initialize()
        self.enqueue()
        self.api.faults.append(SendResult(429, "RATE_LIMITED", "1"))
        self.dispatcher.run_once(NOW + 1)
        original = self.api.requests[-1].body
        self.configuration._set_state("ACTIVE", None, "rotated", NOW + 2)
        self.sync.run_once(NOW + 2)
        self.dispatcher.run_once(NOW + 2.5)
        self.assertEqual(self.api.requests[-1].body, original)
        self.assertEqual(self.messages()[0]["state"], "SENT")
        self.assertEqual(json.loads(self.messages()[0]["payload_json"])["fingerprint"], "rotated")

    def test_schema_rechecked_on_worker_restart_and_mismatch_blocks_pending_patch(self):
        self.initialize()
        self.enqueue()
        self.api.board["properties"].pop()
        restarted = TicketSync(self.database, self.configuration, sender=self.api, clock=lambda: NOW)
        self.dispatcher.tickets = restarted
        self.dispatcher.request_builders["board-patch"] = restarted.build_request
        self.assertFalse(self.dispatcher.run_once(NOW + 1))
        restarted.run_once(NOW + 1)
        self.assertEqual(self.configuration.state()["detail"], "BOARD_SCHEMA_MISMATCH")
        self.assertFalse(self.dispatcher.run_once(NOW + 2))

    def test_board_change_keeps_history_but_never_sends_old_pending_report_to_new_board(self):
        self.initialize()
        self.enqueue()
        replacement = "bbbbbbbb-cccc-4ddd-8eee-ffffffffffff"
        self.configuration._config = validate_tickets_config({**TICKETS, "boardId": replacement})
        self.configuration._set_state("ACTIVE", None, "different-board", NOW + 1)
        self.api.board["id"] = replacement
        self.api.rows.clear()
        self.api.add("new-row")
        self.sync.run_once(NOW + 1)
        self.assertEqual(self.messages()[0]["last_error_code"], "CONFIG_CHANGED")
        self.sync.run_once(NOW + 1.5)
        self.assertEqual({r["row_id"] for r in self.tickets()}, {"row-1", "new-row"})
        self.assertFalse(self.dispatcher.run_once(NOW + 2))
        self.assertFalse(any(r.method == "PATCH" for r in self.api.requests))

    def test_config_token_validation(self):
        self.assertNotIn(TICKETS["apiToken"], repr(validate_tickets_config(TICKETS)))
        for value in ({"apiToken": "bad"}, {"apiToken": "geon_"}, {"apiToken": "geon_x y"},
                      {"apiBaseUrl": "http://board.invalid"}, {"apiBaseUrl": "https://board.invalid?q=1"},
                      {"apiBaseUrl": "https://u:p@board.invalid"}, {"boardId": "bad"}):
            with self.assertRaises(ValueError):
                validate_tickets_config({**TICKETS, **value})


class TicketSyncSettingsTest(unittest.TestCase):
    def test_default_and_environment_interval_bounds(self):
        with patch.dict(os.environ, {}, clear=True):
            self.assertEqual(Settings.from_env().ticket_sync_interval_seconds, 30)
            for interval in (10, 30, 45, 600):
                with self.subTest(interval=interval), patch.dict(
                    os.environ, {"UPLOAD_RECEIVER_TICKET_SYNC_INTERVAL_SECONDS": str(interval)}
                ):
                    self.assertEqual(Settings.from_env().ticket_sync_interval_seconds, interval)

    def test_invalid_environment_intervals_are_rejected(self):
        for interval in ("", "abc", "30.5", "0", "9", "601"):
            with self.subTest(interval=interval), patch.dict(
                os.environ, {"UPLOAD_RECEIVER_TICKET_SYNC_INTERVAL_SECONDS": interval}
            ):
                with self.assertRaisesRegex(RuntimeError, "UPLOAD_RECEIVER_TICKET_SYNC_INTERVAL_SECONDS"):
                    Settings.from_env()

    def test_direct_configuration_also_rejects_invalid_intervals(self):
        for interval in (None, True, 9, 601, 10.0, "30"):
            with self.subTest(interval=interval):
                with self.assertRaises(RuntimeError):
                    Settings(data_root=Path("unused"), expected_mount=None, require_mount=False,
                             ticket_sync_interval_seconds=interval)
                with self.assertRaises(ValueError):
                    TicketSync(Mock(), Mock(), interval_seconds=interval)


@unittest.skipUnless(os.name == "posix", "Receiver API and 0600 configuration require Linux/WSL")
class TicketsApiTest(unittest.TestCase):
    def setUp(self):
        from tests.test_receiver import ReceiverApiTest
        self.helpers = ReceiverApiTest
        worker = patch("upload_receiver.app.TicketSync.start")
        worker.start()
        self.addCleanup(worker.stop)
        self.helpers.setUp(self)
        self.receiver = self.client.app.state.receiver
        self.receiver.tickets.clock = lambda: NOW + 122
        self.api = FakeBoard()
        self.api.add()
        self.api.add("done", status="완료")
        self.api.add("archived", archived=True)
        self.api.add("unit-one", unit=1)
        self.path = self.settings.geon_work_config_path
        self.path.write_text(json.dumps({"version": 1, "tickets": TICKETS}), encoding="utf-8")
        self.path.chmod(0o600)
        self.sync = self.client.app.state.ticket_sync
        self.sync.sender = self.api
        self.sync.clock = lambda: NOW
        self.sync.run_once(NOW)
        self.sync.run_once(NOW + 0.5)
        self.sync.run_once(NOW + 121)
        self.sync.run_once(NOW + 121.5)
        self.token_employee = self.helpers.configure_employee_access(self)
        self.receiver.tickets.set_device_unit(DEVICE_ID, "geo X-1")

    def tearDown(self):
        self.helpers.tearDown(self)

    def headers(self, token=None):
        return self.helpers.employee_auth(self, token or self.token_employee)

    def test_list_uses_app_worker_and_returns_cached_revision_until_refresh(self):
        self.assertIs(self.receiver.tickets.sync, self.sync)
        self.assertEqual(self.sync.interval_seconds, self.settings.ticket_sync_interval_seconds)
        self.receiver.tickets.clock = lambda: NOW + 138
        self.api.page_size = 100
        self.api.add(revision=2, updated=NOW + 137)
        requests = len(self.api.requests)
        for _ in range(5):
            response = self.list()
            self.assertEqual(response.status_code, 200)
            self.assertEqual(next(t for t in response.json()["tickets"] if t["rowId"] == "row-1")["revision"], 1)
        self.assertEqual(len(self.api.requests), requests)
        self.assertTrue(self.sync.run_once(NOW + 138))
        response = self.list()
        self.assertEqual(next(t for t in response.json()["tickets"] if t["rowId"] == "row-1")["revision"], 2)
        self.assertFalse(self.sync.run_once(NOW + 138.5))

    def test_app_passes_configured_interval_to_worker(self):
        from dataclasses import replace
        from fastapi.testclient import TestClient
        from upload_receiver.app import create_app
        settings = replace(self.settings, ticket_sync_interval_seconds=45)
        with TestClient(create_app(settings)) as client:
            worker = client.app.state.ticket_sync
            self.assertEqual(worker.interval_seconds, 45)
            self.assertIs(client.app.state.receiver.tickets.sync, worker)

    def auth(self, token=None):
        return self.helpers.auth(self, token)

    def list(self, **params):
        return self.client.get("/v1/server/tickets", params={"projectId": "road-alpha", **params}, headers=self.headers())

    def start(self, row="row-1", run="capture/run-123456789", device=DEVICE_ID, headers=None, project="road-alpha"):
        return self.client.post(f"/v1/server/tickets/{row}/start", params={"projectId": project},
                                headers=headers or self.headers(), json={"runId": run, "deviceId": device})

    def complete(self, row="row-1", run="capture/run-123456789", headers=None):
        return self.client.post(f"/v1/server/tickets/{row}/complete", params={"projectId": "road-alpha"},
                                headers=headers or self.headers(), json={"runId": run})

    def sync_geometry(self, value, revision):
        prop_id = identifier("수집 구역")
        prop = next((p for p in self.api.board["properties"] if p["id"] == prop_id), None)
        if prop is None:
            self.api.board["properties"].append({"id": prop_id, "name": "수집 구역", "type": "TEXT"})
        row = self.api.rows["row-1"]
        row["revision"] = revision
        row["updatedAt"] = timestamp(NOW + revision + 1000)
        row["values"][prop_id] = {"type": "TEXT", "value": value}
        self.sync._schema = None
        now = NOW + revision + 2000
        self.sync.run_once(now)
        self.sync.run_once(now + 0.5)

    def test_list_shape_filters_units_auth_project_and_device_scope(self):
        result = self.list(deviceId=DEVICE_ID)
        self.assertEqual(result.status_code, 200, result.text)
        value = result.json()
        self.assertEqual(set(value), {"tickets", "units", "deviceUnit", "syncedAt", "integration"})
        self.assertEqual(value["units"], ["geo X-1", "geo X-2"])
        self.assertEqual(value["deviceUnit"], "geo X-1")
        self.assertEqual(value["integration"], {"state": "ACTIVE", "detail": None})
        self.assertIsNotNone(value["syncedAt"])
        self.assertEqual(len(value["tickets"]), 2)
        self.assertEqual(set(value["tickets"][0]), {"rowId", "title", "project", "unit", "assignees", "dueDate", "status", "revision", "updatedAt", "runId", "report"})
        self.assertIsNone(value["tickets"][0]["report"])
        self.assertIsNone(self.list().json()["deviceUnit"])
        self.assertEqual(len(self.list(status="all").json()["tickets"]), 4)
        self.assertEqual(len(self.list(unit="geo X-2").json()["tickets"]), 1)
        self.assertEqual(self.list(unit="unknown").json()["tickets"], [])
        self.assertEqual(self.list(status="bad").status_code, 400)
        self.assertEqual(self.list(projectId="other").status_code, 404)
        self.assertEqual(self.list(deviceId=SECOND_DEVICE_ID).status_code, 404)
        self.assertEqual(self.client.get("/v1/server/tickets", params={"projectId": "road-alpha"}).status_code, 401)
        self.assertEqual(self.client.get("/v1/server/tickets", params={"projectId": "road-alpha"}, headers=self.helpers.auth(self)).status_code, 401)
        self.assertEqual(self.client.get("/v1/server/tickets", params={"projectId": "road-alpha"}, headers={"Authorization": "Bearer " + self.token_employee}).status_code, 400)
        viewer = self.helpers.configure_employee_access(self, employee_id="viewer", role="VIEWER")
        self.assertEqual(self.client.get("/v1/server/tickets", params={"projectId": "road-alpha"}, headers=self.headers(viewer)).status_code, 200)

    def test_geometry_is_optional_and_round_trips_through_ticket_api(self):
        original = self.list().json()
        expected_ticket_fields = {
            "rowId", "title", "project", "unit", "assignees", "dueDate", "status",
            "revision", "updatedAt", "runId", "report",
        }
        self.assertEqual(set(original["tickets"][0]), expected_ticket_fields)

        geometry = {
            "schemaVersion": 1,
            "features": [
                {"type": "Feature", "geometry": {"type": "Point", "coordinates": [126.978, 37.5665]},
                 "properties": {"role": "POINT", "label": "A"}},
            ],
            "range": {"bufferM": 30, "polygon": {"type": "Polygon", "coordinates": []},
                      "bbox": [126.977, 37.5664, 126.979, 37.5666], "lengthM": None, "areaM2": 2827.4},
            "revision": 7,
            "hash": "draft-field-passthrough",
        }
        self.sync_geometry(json.dumps(geometry, ensure_ascii=False), 2)
        ticket = next(t for t in self.list().json()["tickets"] if t["rowId"] == "row-1")
        self.assertEqual(ticket["geometry"], geometry)
        self.assertEqual(set(ticket), expected_ticket_fields | {"geometry"})

        # A later invalid draft value invalidates the cached geometry without hiding the ticket.
        self.sync_geometry("{malformed", 3)
        ticket = next(t for t in self.list().json()["tickets"] if t["rowId"] == "row-1")
        self.assertEqual(set(ticket), expected_ticket_fields)
        self.assertEqual(self.list().json()["integration"]["state"], "ACTIVE")

    def test_blocked_ticket_is_listed_open_but_cannot_start(self):
        self.api.add("blocked", status="막힘", updated=NOW + 200)
        for offset in (250, 250.5, 251, 251.5, 252):
            self.sync.run_once(NOW + offset)
        tickets = {t["rowId"]: t for t in self.list().json()["tickets"]}
        self.assertEqual(tickets["blocked"]["status"], {"name": "막힘", "category": "IN_PROGRESS", "systemKey": "BLOCKED"})
        self.assertEqual(tickets["row-1"]["status"]["systemKey"], "TODO")
        result = self.start(row="blocked")
        self.assertEqual(result.status_code, 409)
        self.assertEqual(result.json()["detail"], "TICKET_BLOCKED")
        self.assertIn(self.api.status("막힘")["id"], resolve_schema(self.api.board)["blockedStatusIds"])

    def test_start_validation_idempotency_and_display_name_comment(self):
        self.assertEqual(self.start(row="unknown").status_code, 404)
        for row in ("done", "archived"):
            result = self.start(row=row)
            self.assertEqual(result.status_code, 409)
            self.assertEqual(result.json()["detail"], "TICKET_NOT_OPEN")
        self.assertEqual(self.start(device=SECOND_DEVICE_ID).status_code, 404)
        self.assertEqual(self.start(project="other").status_code, 404)
        viewer = self.helpers.configure_employee_access(self, employee_id="viewer", role="VIEWER")
        self.assertEqual(self.start(headers=self.headers(viewer)).status_code, 403)
        self.assertEqual(self.complete(headers=self.headers(viewer)).status_code, 403)
        self.receiver.device_events.accept(DEVICE_ID, {
            "schema": "jetson.event.v1", "device": {"name": "MMS 장치"},
            "events": [{"eventId": str(uuid.uuid4()), "type": "device.heartbeat", "occurredAt": timestamp(NOW), "onDuty": False}],
        })
        response = self.start()
        self.assertEqual(response.status_code, 202, response.text)
        self.assertEqual(response.json()["report"], {"type": "STARTED", "state": "PENDING", "code": None})
        self.assertEqual(self.start().json(), response.json())
        self.assertEqual(self.start(run="other/run").json()["detail"], "TICKET_IN_PROGRESS")
        self.assertEqual(self.start(row="unit-one").json()["detail"], "TICKET_IN_PROGRESS")
        with closing(self.receiver.database.connect()) as connection:
            rows = connection.execute("SELECT * FROM outbound_messages WHERE target=?", (TARGET,)).fetchall()
            self.assertEqual(len(rows), 1)
            payload = json.loads(rows[0]["payload_json"])
            self.assertEqual(payload["comment"], "수집 시작 · geo X-1 · MMS 장치")
            self.assertEqual(payload["values"]["수집 작업자"], "Employee employee.one")
            self.assertEqual(connection.execute("SELECT COUNT(*) FROM ticket_runs").fetchone()[0], 1)

    def test_complete_confirmation_values_rounding_comment_idempotency(self):
        self.assertEqual(self.complete().json()["detail"], "TICKET_RUN_NOT_LINKED")
        self.assertEqual(self.start().status_code, 202)
        self.assertEqual(self.complete().json()["detail"], "UPLOAD_NOT_CONFIRMED")
        run = "capture/run-123456789"
        manifest = self.helpers.manifest([("empty", b"")])
        manifest["context"] = self.helpers.survey_context(runId=run)
        created = self.helpers.create(self, manifest)
        self.assertEqual(created.status_code, 201, created.text)
        session_id = created.json()["sessionId"]
        self.assertEqual(self.complete().json()["detail"], "UPLOAD_NOT_CONFIRMED")
        # Use the real upload completion, then set numeric fixtures without allocating GB files.
        result = self.client.post(f"/v1/upload-sessions/{session_id}/complete", headers=self.helpers.auth(self), json={})
        self.assertEqual(result.status_code, 200, result.text)
        with self.receiver.database.immediate() as connection:
            connection.execute("UPDATE upload_sessions SET total_bytes=1234567890, file_count=7 WHERE session_id=?", (session_id,))
            connection.execute("INSERT INTO device_events VALUES (?, ?, 'run.finished', ?, ?, ?)",
                               (DEVICE_ID, str(uuid.uuid4()), timestamp(NOW), timestamp(NOW),
                                json.dumps({"runId": run, "durationSeconds": 4444, "preciseFixRatio": 0.98765})))
        response = self.complete()
        self.assertEqual(response.status_code, 202, response.text)
        self.assertEqual(self.complete().json(), response.json())
        with closing(self.receiver.database.connect()) as connection:
            row = connection.execute("SELECT * FROM outbound_messages WHERE target=? ORDER BY rowid DESC", (TARGET,)).fetchone()
            payload = json.loads(row["payload_json"])
            self.assertEqual(row["idempotency_key"], f"run:{run}:completed")
            self.assertEqual(payload["values"], {"수집 시간": 1.23, "용량(GB)": 1.23, "정밀 위치 비율": 98.8})
            self.assertEqual(payload["comment"], "수집 완료 · 작업자 Employee employee.one · 1.23시간 · 1.23 GB · 파일 7개 · run run-1234")
            self.assertIsNotNone(connection.execute("SELECT completed_at FROM ticket_runs").fetchone()[0])
        self.assertEqual(next(t for t in self.list().json()["tickets"] if t["rowId"] == "row-1")["report"]["type"], "COMPLETED")

    def test_unknown_duration_omitted_and_failed_upload_does_not_complete_ticket(self):
        self.start()
        run = "capture/run-123456789"
        with self.receiver.database.immediate() as connection:
            connection.execute("INSERT INTO upload_sessions VALUES (?, ?, ?, ?, 'FAILED', 0, 1, ?, ?, NULL, ?, ?, NULL)",
                               ("fixture", DEVICE_ID, "b" * 32, "capture", "a" * 64,
                                json.dumps({"context": {"runId": run}}), timestamp(NOW), timestamp(NOW)))
        self.assertEqual(self.complete().json()["detail"], "UPLOAD_NOT_CONFIRMED")
        with self.receiver.database.immediate() as connection:
            connection.execute("UPDATE upload_sessions SET state='COMPLETED' WHERE session_id='fixture'")
        self.assertEqual(self.complete().status_code, 202)
        with closing(self.receiver.database.connect()) as connection:
            payload = json.loads(connection.execute("SELECT payload_json FROM outbound_messages WHERE status_text='COMPLETED'").fetchone()[0])
            self.assertEqual(payload["values"], {"용량(GB)": 0.0})
            self.assertNotIn("시간", payload["comment"])

    def test_reports_expose_sent_conflict_failed_and_keep_device_scope_on_completion(self):
        self.start()
        dispatcher = self.client.app.state.dispatcher
        dispatcher.sender = self.api
        dispatcher.run_once(NOW + 123)
        ticket = next(t for t in self.list().json()["tickets"] if t["rowId"] == "row-1")
        self.assertEqual(ticket["report"], {"type": "STARTED", "state": "SENT", "code": None})
        self.assertEqual(ticket["revision"], 2)
        self.assertEqual(ticket["runId"], "capture/run-123456789")
        self.start(row="unit-one", run="capture/another-run")
        self.api.rows["unit-one"]["status"] = self.api.status("완료")
        self.api.rows["unit-one"]["revision"] = 2
        with self.assertLogs("upload_receiver.tickets", level="WARNING"):
            dispatcher.run_once(NOW + 124)
        ticket = next(t for t in self.list(status="all").json()["tickets"] if t["rowId"] == "unit-one")
        self.assertEqual(ticket["report"], {"type": "STARTED", "state": "CONFLICT", "code": "HUMAN_CHANGED"})
        with self.receiver.database.immediate() as connection:
            connection.execute("UPDATE outbound_messages SET last_error_code='ROW_ARCHIVED' WHERE json_extract(payload_json,'$.rowId')='unit-one'")
        ticket = next(t for t in self.list(status="all").json()["tickets"] if t["rowId"] == "unit-one")
        self.assertEqual(ticket["report"]["state"], "FAILED")
        # Project assignment is immutable once a device has jobs, so drop the
        # mapping directly to prove completion stays scoped to the project.
        with self.receiver.database.immediate() as connection:
            connection.execute("DELETE FROM project_devices WHERE device_id=?", (DEVICE_ID,))
        self.assertEqual(self.complete().status_code, 404)

    def test_start_unit_and_device_name_fallback_and_foreign_run_validation(self):
        with self.receiver.database.immediate() as connection:
            connection.execute("UPDATE board_tickets SET unit=NULL WHERE row_id='row-1'")
        self.assertEqual(self.start().status_code, 202)
        with closing(self.receiver.database.connect()) as connection:
            payload = json.loads(connection.execute("SELECT payload_json FROM outbound_messages WHERE target=?", (TARGET,)).fetchone()[0])
        self.assertEqual(payload["comment"], "수집 시작 · geo X-1 · 장치 " + DEVICE_ID[:8])
        with self.receiver.database.immediate() as connection:
            connection.execute("UPDATE board_tickets SET status_name='진행 중', status_category='IN_PROGRESS', run_id='someone/else' WHERE row_id='unit-one'")
        self.assertEqual(self.start(row="unit-one", run="capture/new").json()["detail"], "TICKET_IN_PROGRESS")

    def test_completed_session_for_another_device_cannot_confirm_run(self):
        self.start()
        with self.receiver.database.immediate() as connection:
            connection.execute("INSERT INTO upload_sessions VALUES (?, ?, ?, ?, 'COMPLETED', 0, 1, ?, ?, NULL, ?, ?, ?)",
                               ("foreign", SECOND_DEVICE_ID, "b" * 32, "capture", "a" * 64,
                                json.dumps({"context": {"runId": "capture/run-123456789"}}),
                                timestamp(NOW), timestamp(NOW), timestamp(NOW)))
        self.assertEqual(self.complete().json()["detail"], "UPLOAD_NOT_CONFIRMED")

    def test_paused_tickets_keep_accepting_reports_as_pending(self):
        with self.receiver.database.immediate() as connection:
            connection.execute("UPDATE integration_state SET state='ERROR', detail='UNAUTHORIZED' WHERE target=?", (TARGET,))
        response = self.start()
        self.assertEqual(response.status_code, 202, response.text)
        self.assertEqual(response.json()["report"]["state"], "PENDING")
        self.assertEqual(self.list().json()["integration"], {"state": "ERROR", "detail": "UNAUTHORIZED"})
        dispatcher = self.client.app.state.dispatcher
        dispatcher.sender = self.api
        self.assertFalse(dispatcher.run_once(NOW + 123))

    def test_admin_preserves_blocks_mode_status_device_unit_and_no_token_output(self):
        from upload_receiver import admin
        from upload_receiver.config import Settings
        def command(args, stdin=""):
            output = io.StringIO()
            with patch.object(Settings, "from_env", return_value=self.settings), patch("sys.argv", ["admin", *args]), patch("sys.stdin", io.StringIO(stdin)), redirect_stdout(output):
                admin.main()
            self.assertNotIn(TICKETS["apiToken"], output.getvalue())
            self.assertEqual(self.path.stat().st_mode & 0o777, 0o600)
            return output.getvalue()
        command(["configure-geon-work", "--webhook-url", CONFIG["webhookUrl"], "--channel-id", CONFIG["channelId"]], CONFIG["signingSecret"] + "\nunused\n")
        self.assertEqual(json.loads(self.path.read_text())["tickets"], TICKETS)
        command(["configure-geon-work-tickets", "--api-base-url", TICKETS["apiBaseUrl"], "--board-id", TICKETS["boardId"]], TICKETS["apiToken"] + "\nunused\n")
        value = json.loads(self.path.read_text())
        self.assertEqual(value, {**CONFIG, "tickets": TICKETS})
        command(["set-device-unit", "--device-id", DEVICE_ID, "--unit", "geo X-2"])
        self.assertEqual(self.list(deviceId=DEVICE_ID).json()["deviceUnit"], "geo X-2")
        status = json.loads(command(["geon-work-status"]))
        self.assertEqual(status["tickets"]["state"], "ACTIVE")
        self.assertIsNotNone(status["tickets"]["syncedAt"])
        self.assertNotIn(CONFIG["signingSecret"], json.dumps(status))
        self.assertEqual(list(self.path.parent.glob(".geon-work.json.*.tmp")), [])

    def test_config_sections_independent_fingerprints_pause_reload(self):
        self.receiver.geon_work.refresh(NOW)
        self.assertEqual(self.receiver.geon_work.state()["state"], "NOT_CONFIGURED")
        self.receiver.ticket_configuration.refresh(NOW)
        with self.receiver.database.immediate() as connection:
            connection.execute("UPDATE integration_state SET state='ERROR', detail='FORBIDDEN' WHERE target=?", (TARGET,))
        self.path.write_text(json.dumps({**CONFIG, "tickets": TICKETS}), encoding="utf-8")
        self.receiver.ticket_configuration.refresh(NOW + 1)
        self.assertEqual(self.receiver.ticket_configuration.state()["state"], "ERROR")
        self.receiver.geon_work.refresh(NOW + 1)
        self.assertEqual(self.receiver.geon_work.state()["state"], "ACTIVE")
        self.path.write_text(json.dumps({**CONFIG, "tickets": {**TICKETS, "apiToken": "geon_rotated"}}), encoding="utf-8")
        self.receiver.ticket_configuration.refresh(NOW + 2)
        self.assertEqual(self.receiver.ticket_configuration.state()["state"], "ACTIVE")
        self.assertTrue(self.sync.run_once(NOW + 122))
        self.assertEqual(self.api.requests[-1].method, "GET")
        self.assertEqual(urlsplit(self.api.requests[-1].url).path, "/api/v1/boards/" + TICKETS["boardId"])


if __name__ == "__main__":
    unittest.main()
