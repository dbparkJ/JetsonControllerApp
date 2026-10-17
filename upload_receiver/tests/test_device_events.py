from __future__ import annotations

import io
import json
import os
import sqlite3
import tempfile
import unittest
import uuid
from contextlib import closing
from pathlib import Path
from unittest.mock import patch

from upload_receiver.config import Settings
from upload_receiver.database import Database, SCHEMA
from upload_receiver.device_events import DeviceEvents, DeviceEventsError, validate_event
from upload_receiver.event_mapping import incident_key, map_event, project_label
from upload_receiver.geon_work import GeonWorkConfiguration, parse_timestamp, timestamp, validate_config
from upload_receiver.outbound import Dispatcher, OutboundRequest, SendResult, enqueue, http_sender
from upload_receiver.signing import attempt_nonce, canonical_json, sign_payload


DEVICE_ID = "d606c26d-98d6-4b09-99d7-c3da7dda4de0"
SECOND_DEVICE_ID = "7dbe212d-a91f-4f0f-a5a4-09b840c6a7a6"
CHANNEL_ID = "11111111-2222-4333-8444-555555555555"
NOW = 1792400000.0
CONFIG = {
    "version": 1,
    "webhookUrl": "https://example.invalid/v1/webhooks/incoming/test",
    "channelId": CHANNEL_ID,
    "signingSecret": "test-signing-secret-0123456789abcdef",
}


class FakeClock:
    def __init__(self, now: float = NOW) -> None:
        self.now = now

    def __call__(self) -> float:
        return self.now


class FakeSender:
    def __init__(self, result=None) -> None:
        self.result = result or SendResult(201)
        self.requests: list[OutboundRequest] = []

    def __call__(self, request: OutboundRequest) -> SendResult:
        self.requests.append(request)
        if isinstance(self.result, Exception):
            raise self.result
        return self.result


def event(kind: str = "device.temperature", **changes) -> dict[str, object]:
    value = {"eventId": str(uuid.uuid4()), "type": kind, "occurredAt": timestamp(NOW)}
    value.update({
        "device.temperature": {"level": "WARNING", "temperatureC": 82.5, "thresholdC": 80},
        "device.storage": {"level": "WARNING", "freePercent": 10, "freeBytes": 10, "totalBytes": 100},
        "device.heartbeat": {"onDuty": True},
        "run.started": {"runId": "capture/run-1", "pipelineId": "capture"},
        "run.finished": {"runId": "capture/run-1", "pipelineId": "capture", "result": "COMPLETED"},
        "upload.failed": {"jobId": "job-1", "failureCount": 1, "errorCode": "UPLOAD_FAILED"},
        "upload.completed": {"runId": "capture/run-1", "files": 2, "bytes": 123},
        "device.missing": {"lastHealthyAt": timestamp(NOW - 1800)},
        "device.recovered": {},
    }.get(kind, {}))
    value.update(changes)
    return value


def body(*events, name="MMS 3호기") -> dict[str, object]:
    return {"schema": "jetson.event.v1", "device": {"name": name}, "events": list(events)}


class MappingAndSigningTest(unittest.TestCase):
    def test_contract_signature_vector_byte_for_byte(self) -> None:
        expected = (
            '{"actions":["장치 환기 확인"],"channelId":"11111111-2222-4333-8444-555555555555",'
            '"details":"deviceId=jetson-01\\ntemperatureC=82.5\\nthresholdC=80",'
            '"impact":"수집 속도 저하 가능","incidentKey":"jetson:jetson-01:temperature",'
            '"lastHealthyAt":"2026-10-19T09:00:00+09:00","retryCount":0,"severity":"WARNING",'
            '"source":"jetson-01 (MMS 3호기)","status":"과열 경고","template":"JETSON_DEVICE"}'
        ).encode("utf-8")
        payload = map_event("jetson-01", " jetson-01 (MMS 3호기) ", CHANNEL_ID, {
            "type": "device.temperature", "level": "WARNING", "temperatureC": 82.5,
            "thresholdC": 80, "lastNormalAt": "2026-10-19T09:00:00+09:00",
        })
        encoded, headers = sign_payload(payload, CONFIG["signingSecret"], "1792240000", "evt-0001-a1b2c3d4")
        self.assertEqual(encoded, expected)
        self.assertEqual(headers["x-geon-signature"], "v1=fd84e54658f0112d8fce6f62559aabde9d2ded72b99510fa8509a559493b744f")
        self.assertEqual(headers["x-geon-timestamp"], "1792240000")
        self.assertEqual(headers["x-geon-nonce"], "evt-0001-a1b2c3d4")

    def test_canonical_json_sorts_recursively_and_keeps_unicode(self) -> None:
        self.assertEqual(canonical_json({"z": {"b": "한글", "a": 2}, "a": [1]}), '{"a":[1],"z":{"a":2,"b":"한글"}}'.encode())

    def test_nonce_prefix_attempt_and_random_suffix(self) -> None:
        message = "12345678-abcd-4321-aaaa-123456789abc"
        first = attempt_nonce(message, 1)
        second = attempt_nonce(message, 2)
        self.assertRegex(first, r"^jc-12345678abcd-1-[0-9a-f]{8}$")
        self.assertRegex(second, r"^jc-12345678abcd-2-[0-9a-f]{8}$")
        self.assertNotEqual(first, second)

    def test_mapping_every_event_and_level(self) -> None:
        cases = [
            ("device.temperature", "WARNING", "WARNING", "과열 경고", ["장치 환기 확인"], "수집 속도 저하 가능"),
            ("device.temperature", "CRITICAL", "CRITICAL", "과열 위험", ["장치 환기 확인", "수집 중지 검토"], "장치 보호를 위해 꺼질 수 있음"),
            ("device.temperature", "NORMAL", "RECOVERY", "온도 정상", [], None),
            ("device.storage", "WARNING", "WARNING", "저장 공간 부족", ["전송 끝난 수집 정리"], "새 수집을 저장하지 못할 수 있음"),
            ("device.storage", "CRITICAL", "CRITICAL", "저장 공간 위험", ["전송 끝난 수집 정리"], "새 수집을 저장하지 못할 수 있음"),
            ("device.storage", "NORMAL", "RECOVERY", "저장 공간 정상", [], None),
            ("device.missing", None, "WARNING", "응답 없음", ["장치 전원·네트워크 확인"], "수집·전송이 멈췄을 수 있음"),
            ("device.recovered", None, "RECOVERY", "응답 복귀", [], None),
            ("run.started", None, "INFO", "수집 시작", [], None),
            ("run.finished", None, "INFO", "수집 종료", [], None),
            ("upload.failed", None, "WARNING", "전송 실패", ["앱에서 다시 시도", "수신 서버 확인"], "서버에 결과가 없음"),
            ("upload.completed", None, "RECOVERY", "서버 수신 확인", [], None),
        ]
        fields = {"template", "channelId", "source", "severity", "status", "incidentKey", "details", "actions", "retryCount", "project", "impact", "lastHealthyAt"}
        for kind, level, severity, status, actions, impact in cases:
            with self.subTest(kind=kind, level=level):
                value = event(kind)
                if level:
                    value["level"] = level
                value["unknown"] = "must not leave receiver"
                mapped = map_event(DEVICE_ID, " MMS ", CHANNEL_ID, value)
                self.assertLessEqual(set(mapped), fields)
                self.assertEqual(mapped["source"], "MMS")
                self.assertEqual(mapped["severity"], severity)
                self.assertEqual(mapped["status"], status)
                self.assertEqual(mapped["actions"], actions)
                self.assertEqual(mapped.get("impact"), impact)
                self.assertIn("deviceId=" + DEVICE_ID, mapped["details"])
                self.assertNotIn("unknown", canonical_json(mapped).decode())
                self.assertEqual(json.loads(canonical_json(mapped)), mapped)
        self.assertIsNone(map_event(DEVICE_ID, None, CHANNEL_ID, event("device.heartbeat")))
        for count in (1, 3, 4, 10):
            mapped = map_event(DEVICE_ID, None, CHANNEL_ID, event("upload.failed", failureCount=count))
            self.assertEqual(mapped["retryCount"], count - 1)
            self.assertEqual(mapped["severity"], "WARNING" if count < 4 else "CRITICAL")
            self.assertEqual(mapped["status"], "전송 실패" if count < 4 else "전송 실패 반복")
        self.assertEqual(mapped["source"], "장치 " + DEVICE_ID[:8])
        self.assertEqual(map_event(DEVICE_ID, " ", CHANNEL_ID, event())["source"], "장치 " + DEVICE_ID[:8])
        finished = map_event(DEVICE_ID, None, CHANNEL_ID, event(
            "run.finished", durationSeconds=120, bytesWritten=456,
        ))
        self.assertIn("durationSeconds=120", finished["details"])
        self.assertIn("bytes=456", finished["details"])

    def test_project_rule_lengths_and_incident_shortening(self) -> None:
        self.assertIsNone(project_label({}))
        self.assertEqual(project_label({"surveyProjectLabel": " P ", "surveySectionLabel": " S "}), "P · S")
        self.assertEqual(project_label({"label": " fallback "}), "fallback")
        for prefix in ("run:", "upload:"):
            import hashlib
            identifier = "가" * 300
            self.assertEqual(incident_key(prefix, identifier), prefix + hashlib.sha256(identifier.encode()).hexdigest()[:32])
        mapped = map_event(DEVICE_ID, "가" * 200, CHANNEL_ID, event("run.started", runId="가" * 2000, label="나" * 200))
        self.assertEqual(len(mapped["source"]), 100)
        self.assertEqual(len(mapped["project"]), 120)
        self.assertLessEqual(len(mapped["details"]), 1500)
        self.assertLessEqual(len(mapped["incidentKey"]), 160)
        canonical_json(mapped).decode("utf-8")

    def test_validation_all_types_and_invalid_fields(self) -> None:
        for kind in ("device.heartbeat", "device.temperature", "device.storage", "run.started", "run.finished", "upload.failed"):
            self.assertIsNone(validate_event(event(kind), NOW)[1], kind)
        invalid = [
            event("unknown"), event(eventId="bad"), event(eventId=str(uuid.uuid1())),
            event(occurredAt="2026-10-19T09:12:03"), event(occurredAt="bad"),
            event(level="BAD"), event(temperatureC=True), event(temperatureC=float("nan")),
            event("device.heartbeat", onDuty=1), event("device.storage", freePercent=101),
            event("device.storage", freeBytes=-1), event("run.finished", preciseFixRatio=1.1),
            event("run.finished", result="BAD"), event("upload.failed", failureCount=0),
            event("upload.failed", errorCode="raw exception text"), event("upload.failed", jobId=""),
        ]
        for value in invalid:
            with self.subTest(value=value):
                self.assertIsNone(validate_event(value, NOW)[0])
        self.assertEqual(validate_event(event(occurredAt=timestamp(NOW - 7 * 86400 - 1)), NOW)[1], "EVENT_TOO_OLD")
        self.assertIsNone(validate_event(event(occurredAt=timestamp(NOW - 7 * 86400)), NOW)[1])

    def test_config_validation(self) -> None:
        self.assertEqual(validate_config(CONFIG).channel_id, CHANNEL_ID)
        for change in ({"version": 2}, {"version": True}, {"webhookUrl": "http://example.invalid"},
                       {"webhookUrl": "https://example.invalid:bad"}, {"webhookUrl": "https://user:pass@example.invalid"},
                       {"channelId": "bad"}, {"signingSecret": " "}):
            with self.subTest(change=change), self.assertRaises(ValueError):
                validate_config({**CONFIG, **change})
        self.assertNotIn(CONFIG["signingSecret"], repr(validate_config(CONFIG)))


class ReceiverSchemaAndDispatcherTest(unittest.TestCase):
    """Portable SQLite checks; configuration file permissions are tested on Linux."""

    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.database = Database(Path(self.temporary.name) / "receiver.sqlite3")
        with closing(sqlite3.connect(self.database.path)) as connection:
            connection.executescript(SCHEMA.split("CREATE TABLE IF NOT EXISTS device_events", 1)[0])
            connection.execute(
                "INSERT INTO devices VALUES (?, ?, 1, 10000, NULL, ?, ?)",
                (DEVICE_ID, DEVICE_ID, timestamp(NOW), timestamp(NOW)),
            )
            connection.commit()
        self.database.initialize()
        self.configuration = GeonWorkConfiguration(Path(self.temporary.name) / "unused", self.database)
        self.configuration._config = validate_config(CONFIG)
        self.configuration._set_state("ACTIVE", None, "fixture", NOW)
        refresh = patch.object(self.configuration, "refresh", return_value=validate_config(CONFIG))
        refresh.start()
        self.addCleanup(refresh.stop)
        self.clock = FakeClock()
        self.events = DeviceEvents(self.database, self.configuration, clock=self.clock)
        self.sender = FakeSender()
        self.dispatcher = Dispatcher(
            self.database, self.configuration, self.events,
            sender=self.sender, jitter=lambda low, high: 1.0,
        )

    def test_schema_upgrades_in_place_constraints_and_reusable_payload(self) -> None:
        self.database.initialize()
        with self.database.immediate() as connection:
            self.assertEqual(connection.execute("SELECT device_id FROM devices").fetchone()[0], DEVICE_ID)
            tables = {row[0] for row in connection.execute("SELECT name FROM sqlite_master WHERE type='table'")}
            self.assertTrue({"device_events", "device_presence", "outbound_messages", "integration_state"} <= tables)
            first = enqueue(
                connection, DEVICE_ID, {"arbitrary": "future body"}, NOW,
                kind="future-kind", incident_key="run:r", status_text="started", idempotency_key="unique",
            )
            self.assertIsNotNone(first)
            self.assertIsNone(enqueue(
                connection, DEVICE_ID, {"another": True}, NOW,
                kind="future-kind", incident_key="run:r", status_text="completed", idempotency_key="unique",
            ))
            with self.assertRaises(sqlite3.IntegrityError):
                connection.execute("UPDATE outbound_messages SET state='INVALID'")

    def test_dispatch_response_table_without_files_network_or_sleep(self) -> None:
        cases = [
            (SendResult(201), "SENT", "ACTIVE", None),
            (SendResult(200), "DEDUP", "ACTIVE", None),
            (SendResult(409, "WEBHOOK_REPLAY"), "SENT", "ACTIVE", None),
            (SendResult(409, "INTEGRATION_CHANNEL_UNAVAILABLE"), "DEAD", "ERROR", "CHANNEL_UNAVAILABLE"),
            (SendResult(400), "DEAD", "ACTIVE", "HTTP_400"),
            (SendResult(401), "DEAD", "ERROR", "SIGNATURE_REJECTED"),
            (SendResult(403), "DEAD", "ERROR", "FORBIDDEN"),
            (SendResult(404, "INTEGRATION_NOT_FOUND"), "DEAD", "EXPIRED", "INTEGRATION_NOT_FOUND"),
            (SendResult(429, retry_after="90"), "PENDING", "ACTIVE", "HTTP_429"),
            (SendResult(500), "PENDING", "ACTIVE", "HTTP_500"),
            (OSError("private exception"), "PENDING", "ACTIVE", "NETWORK_ERROR"),
        ]
        for result, state, integration, code in cases:
            with self.subTest(result=result):
                with self.database.immediate() as connection:
                    connection.execute("DELETE FROM outbound_messages")
                self.configuration._set_state("ACTIVE", None, "fixture", self.clock())
                self.events.accept(DEVICE_ID, body(event()))
                self.sender.result = result
                self.assertTrue(self.dispatcher.run_once(self.clock()))
                with closing(self.database.connect()) as connection:
                    row = connection.execute("SELECT * FROM outbound_messages").fetchone()
                    self.assertEqual((row["state"], row["last_error_code"]), (state, code))
                    if state == "PENDING":
                        delay = 90 if row["last_http_status"] == 429 else 15
                        self.assertEqual(parse_timestamp(row["next_attempt_at"]), self.clock() + delay)
                self.assertEqual(self.configuration.state()["state"], integration)
                if integration != "ACTIVE":
                    self.events.accept(DEVICE_ID, body(event("device.storage")))
                    self.assertFalse(self.dispatcher.run_once(self.clock() + 0.5))
                self.clock.now += 1

    def test_failed_result_write_is_reclaimed_with_new_attempt(self) -> None:
        self.events.accept(DEVICE_ID, body(event()))
        with patch.object(self.dispatcher, "_finish", side_effect=sqlite3.OperationalError("busy")):
            with self.assertRaises(sqlite3.OperationalError):
                self.dispatcher.run_once(NOW)
        self.assertTrue(self.dispatcher.run_once(NOW + 1))
        self.assertNotEqual(self.sender.requests[0].headers["x-geon-nonce"], self.sender.requests[1].headers["x-geon-nonce"])
        with closing(self.database.connect()) as connection:
            row = connection.execute("SELECT state, attempts FROM outbound_messages").fetchone()
        self.assertEqual(tuple(row), ("SENT", 2))

    def test_subsecond_retry_spacing_staleness_and_attempt_limit(self) -> None:
        self.events.accept(DEVICE_ID, body(event()))
        self.sender.result = SendResult(500)
        self.dispatcher.jitter = lambda low, high: 0.9
        self.assertTrue(self.dispatcher.run_once(NOW))
        self.assertFalse(self.dispatcher.run_once(NOW + 13))
        self.assertTrue(self.dispatcher.run_once(NOW + 13.5))
        with closing(self.database.connect()) as connection:
            row = connection.execute("SELECT * FROM outbound_messages").fetchone()
            self.assertEqual(row["attempts"], 2)
            self.assertEqual(parse_timestamp(row["next_attempt_at"]), NOW + 40.5)
        with self.database.immediate() as connection:
            connection.execute("UPDATE outbound_messages SET attempts=9, next_attempt_at=?", (timestamp(NOW + 14),))
        self.assertTrue(self.dispatcher.run_once(NOW + 14))
        with closing(self.database.connect()) as connection:
            self.assertEqual(connection.execute("SELECT state, last_error_code FROM outbound_messages").fetchone()[1], "RETRY_EXHAUSTED")
        self.events.accept(DEVICE_ID, body(event("device.storage")))
        before = len(self.sender.requests)
        self.assertFalse(self.dispatcher.run_once(NOW + 86400))
        self.assertEqual(len(self.sender.requests), before)
        with closing(self.database.connect()) as connection:
            self.assertEqual(connection.execute("SELECT last_error_code FROM outbound_messages ORDER BY rowid DESC LIMIT 1").fetchone()[0], "STALE")

    def test_judgement_once_per_minute_and_one_in_flight(self) -> None:
        self.events.accept(DEVICE_ID, body(event()))
        sender = self.sender
        def checking_sender(request):
            self.assertFalse(self.dispatcher.run_once(NOW))
            return sender(request)
        self.dispatcher.sender = checking_sender
        with patch.object(self.events, "judge_heartbeats") as judgement:
            self.dispatcher.run_once(NOW)
            self.dispatcher.run_once(NOW + 59)
            self.dispatcher.run_once(NOW + 60)
        self.assertEqual(judgement.call_count, 2)

    def test_presence_missing_recovery_only_inside_monitoring_window(self) -> None:
        self.events.accept(DEVICE_ID, body(event("device.heartbeat", onDuty=False)))
        self.events.judge_heartbeats(NOW + 1800)
        with closing(self.database.connect()) as connection:
            self.assertEqual(connection.execute("SELECT heartbeat_state FROM device_presence").fetchone()[0], "OK")
        self.events.accept(DEVICE_ID, body(event("run.started")))
        self.events.judge_heartbeats(NOW + 1799)
        with closing(self.database.connect()) as connection:
            self.assertEqual(connection.execute("SELECT heartbeat_state FROM device_presence").fetchone()[0], "OK")
        self.events.judge_heartbeats(NOW + 1800)
        with closing(self.database.connect()) as connection:
            row = connection.execute("SELECT * FROM device_presence").fetchone()
            self.assertEqual(row["heartbeat_state"], "MISSING")
            payload = json.loads(connection.execute("SELECT payload_json FROM outbound_messages ORDER BY rowid DESC LIMIT 1").fetchone()[0])
        self.assertEqual(payload["lastHealthyAt"], timestamp(NOW))
        self.clock.now += 1801
        self.events.accept(DEVICE_ID, body(event("device.heartbeat", onDuty=False)))
        with closing(self.database.connect()) as connection:
            row = connection.execute("SELECT * FROM device_presence").fetchone()
            self.assertEqual((row["heartbeat_state"], row["on_duty"]), ("OK", 0))
            self.assertEqual(connection.execute("SELECT status_text FROM outbound_messages ORDER BY rowid DESC LIMIT 1").fetchone()[0], "응답 복귀")

    def test_upload_completion_uses_latest_run_label_and_closes_window(self) -> None:
        self.events.accept(DEVICE_ID, body(event("run.started", label="old")))
        self.events.accept(DEVICE_ID, body(event("run.started", surveyProjectLabel="P", surveySectionLabel="S")))
        manifest_json = json.dumps({"context": {"runId": "capture/run-1"}})
        with self.database.immediate() as connection:
            connection.execute(
                """INSERT INTO upload_sessions(session_id, device_id, client_job_id, source_name,
                   state, total_bytes, file_count, manifest_hash, manifest_json, created_at, updated_at)
                   VALUES ('session', ?, 'job', 'capture', 'OPEN', 123, 2, 'hash', ?, ?, ?)""",
                (DEVICE_ID, manifest_json, timestamp(NOW), timestamp(NOW)),
            )
        self.events.accept(DEVICE_ID, body(event("run.finished")))
        with closing(self.database.connect()) as connection:
            self.assertTrue(connection.execute("SELECT on_duty FROM device_presence").fetchone()[0])
        with self.database.immediate() as connection:
            connection.execute("UPDATE upload_sessions SET state='COMPLETED'")
            session = connection.execute("SELECT * FROM upload_sessions").fetchone()
        self.events.upload_completed(session, NOW)
        self.events.upload_completed(session, NOW)
        with closing(self.database.connect()) as connection:
            rows = connection.execute("SELECT payload_json FROM outbound_messages WHERE status_text='서버 수신 확인'").fetchall()
            self.assertEqual(len(rows), 1)
            payload = json.loads(rows[0][0])
            self.assertEqual(payload["project"], "P · S")
            self.assertIn("files=2", payload["details"])
            self.assertIn("bytes=123", payload["details"])
            self.assertFalse(connection.execute("SELECT on_duty FROM device_presence").fetchone()[0])

    def test_http_sender_uses_exact_bytes_default_tls_and_no_redirect(self) -> None:
        from unittest.mock import MagicMock
        connection = MagicMock()
        response = connection.getresponse.return_value
        response.status = 409
        response.getheader.return_value = None
        response.read.return_value = b'{"error":{"code":"WEBHOOK_REPLAY"}}'
        request = OutboundRequest(CONFIG["webhookUrl"], b'{"a":1}', {"Authorization": "Bearer future-token"}, method="PATCH")
        with patch("upload_receiver.outbound.http.client.HTTPSConnection", return_value=connection) as constructor:
            result = http_sender(request)
        self.assertEqual(result, SendResult(409, "WEBHOOK_REPLAY"))
        self.assertEqual(constructor.call_args.args, ("example.invalid", 443))
        self.assertEqual(constructor.call_args.kwargs["timeout"], 15)
        self.assertIsNotNone(constructor.call_args.kwargs["context"])
        connection.request.assert_called_once_with("PATCH", "/v1/webhooks/incoming/test", request.body, dict(request.headers))
        connection.close.assert_called_once()


@unittest.skipUnless(os.name == "posix", "Receiver storage and permissions require Linux/WSL")
class DeviceEventsStorageTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.database = Database(self.root / "receiver.sqlite3")
        self.database.initialize()
        with self.database.immediate() as connection:
            for device_id in (DEVICE_ID, SECOND_DEVICE_ID):
                connection.execute("INSERT INTO devices VALUES (?, ?, 1, 10000, NULL, ?, ?)", (device_id, device_id, timestamp(NOW), timestamp(NOW)))
        self.clock = FakeClock()
        self.path = self.root / "geon-work.json"
        self.write_config()
        self.configuration = GeonWorkConfiguration(self.path, self.database)
        self.configuration.refresh(self.clock())
        self.events = DeviceEvents(self.database, self.configuration, clock=self.clock)
        self.sender = FakeSender()
        self.dispatcher = Dispatcher(self.database, self.configuration, self.events, sender=self.sender, clock=self.clock, jitter=lambda low, high: 1.0)

    def write_config(self, **changes) -> None:
        self.path.write_text(json.dumps({**CONFIG, **changes}), encoding="utf-8")
        self.path.chmod(0o600)

    def accept(self, *values, device_id=DEVICE_ID, name="MMS"):
        return self.events.accept(device_id, body(*values, name=name))

    def rows(self):
        with closing(self.database.connect()) as connection:
            return [dict(row) for row in connection.execute("SELECT * FROM outbound_messages ORDER BY rowid")]

    def presence(self, device_id=DEVICE_ID):
        with closing(self.database.connect()) as connection:
            row = connection.execute("SELECT * FROM device_presence WHERE device_id=?", (device_id,)).fetchone()
            return dict(row) if row else None

    def test_accept_duplicate_reject_unknown_fields_and_incident_dedup(self) -> None:
        first = event(unknown="ignored")
        unknown = event("unknown")
        response = self.accept(first, first, unknown, event(temperatureC=90))
        self.assertEqual([item["state"] for item in response["results"]], ["ACCEPTED", "DUPLICATE", "REJECTED", "ACCEPTED"])
        self.assertEqual(len(self.rows()), 1)
        with closing(self.database.connect()) as connection:
            saved = connection.execute("SELECT body_json FROM device_events WHERE event_id=?", (first["eventId"],)).fetchone()[0]
            self.assertNotIn("unknown", saved)
        self.accept(event(level="CRITICAL"), event(level="NORMAL"))
        self.assertEqual([row["status_text"] for row in self.rows()], ["과열 경고", "과열 위험", "온도 정상"])
        self.accept(event("device.heartbeat"))
        self.assertEqual(len(self.rows()), 3)
        self.clock.now += 8 * 86400
        self.assertEqual(self.accept(first)["results"][0]["state"], "DUPLICATE")
        self.clock.now = NOW
        self.accept(event(level="CRITICAL"), name=None)
        self.assertEqual(json.loads(self.rows()[-1]["payload_json"])["source"], "MMS")

    def test_request_rate_per_device_and_expiry(self) -> None:
        for _ in range(60):
            self.events.reserve_request(DEVICE_ID)
        with self.assertRaises(DeviceEventsError) as raised:
            self.events.reserve_request(DEVICE_ID)
        self.assertEqual(raised.exception.status, 429)
        self.assertEqual(raised.exception.retry_after, 60)
        self.events.reserve_request(SECOND_DEVICE_ID)
        self.clock.now += 60
        self.events.reserve_request(DEVICE_ID)

    def test_monitoring_on_duty_threshold_recovery_and_false_heartbeat(self) -> None:
        self.accept(event("run.started"))
        self.accept(event("device.heartbeat", onDuty=False), device_id=SECOND_DEVICE_ID)
        self.events.judge_heartbeats(NOW + 1799, config=self.configuration.current)
        self.assertEqual(self.presence()["heartbeat_state"], "OK")
        self.events.judge_heartbeats(NOW + 1800, config=self.configuration.current)
        self.assertEqual(self.presence()["heartbeat_state"], "MISSING")
        self.assertEqual(self.presence(SECOND_DEVICE_ID)["heartbeat_state"], "OK")
        missing = json.loads(self.rows()[-1]["payload_json"])
        self.assertEqual(missing["status"], "응답 없음")
        self.assertEqual(missing["lastHealthyAt"], timestamp(NOW))
        self.events.judge_heartbeats(NOW + 1900, config=self.configuration.current)
        self.assertEqual(len(self.rows()), 2)
        self.clock.now += 1901
        self.accept(event("device.heartbeat", onDuty=False))
        self.assertEqual(self.rows()[-1]["status_text"], "응답 복귀")
        self.assertEqual(self.presence()["heartbeat_state"], "OK")
        self.assertFalse(self.presence()["on_duty"])
        self.events.judge_heartbeats(NOW + 4000, config=self.configuration.current)
        self.assertEqual(self.presence()["heartbeat_state"], "OK")

    def test_upload_after_run_finish_reopens_the_window(self) -> None:
        self.accept(event("run.started"), event("run.finished"))
        self.assertFalse(self.presence()["on_duty"])
        self.accept(event("device.heartbeat", onDuty=True, activeUploadJobId="job-1"))
        self.assertTrue(self.presence()["on_duty"])
        self.events.judge_heartbeats(NOW + 1800, config=self.configuration.current)
        self.assertEqual(self.presence()["heartbeat_state"], "MISSING")
        self.assertEqual(self.rows()[-1]["status_text"], "응답 없음")

    def test_run_finish_window_and_last_heartbeat(self) -> None:
        self.accept(event("run.started"), event("run.finished"))
        self.assertFalse(self.presence()["on_duty"])
        self.accept(event("run.started"), event("device.heartbeat"), event("run.finished"))
        self.assertTrue(self.presence()["on_duty"])
        self.assertIsNone(self.presence()["active_run_id"])
        self.accept(event("device.heartbeat", onDuty=False))
        self.assertFalse(self.presence()["on_duty"])
        self.accept(event("run.started", runId="capture/another"), event("run.finished"))
        self.assertEqual(self.presence()["active_run_id"], "capture/another")
        self.assertTrue(self.presence()["on_duty"])

    def test_not_configured_and_invalid_configuration(self) -> None:
        self.path.unlink()
        self.accept(event())
        self.assertEqual(self.configuration.state()["state"], "NOT_CONFIGURED")
        self.assertEqual(self.rows(), [])
        self.write_config()
        self.path.chmod(0o640)
        self.configuration.refresh(NOW)
        self.assertEqual(self.configuration.state(), {"state": "ERROR", "detail": "CONFIG_INSECURE"})
        self.path.chmod(0o600)
        self.configuration.refresh(NOW)
        self.assertEqual(self.configuration.state()["state"], "ACTIVE")
        self.write_config(webhookUrl="http://example.invalid")
        self.configuration.refresh(NOW)
        self.assertEqual(self.configuration.state(), {"state": "ERROR", "detail": "CONFIG_INVALID"})
        self.path.write_text("{", encoding="utf-8")
        self.configuration.refresh(NOW)
        self.assertEqual(self.configuration.state()["detail"], "CONFIG_INVALID")
        self.write_config(signingSecret="a longer replacement secret")
        self.configuration.refresh(NOW)
        self.assertEqual(self.configuration.state()["state"], "ACTIVE")

    def test_dispatch_response_table(self) -> None:
        cases = [
            (SendResult(201), "SENT", "ACTIVE", None),
            (SendResult(200), "DEDUP", "ACTIVE", None),
            (SendResult(409, "WEBHOOK_REPLAY"), "SENT", "ACTIVE", None),
            (SendResult(409, "INTEGRATION_CHANNEL_UNAVAILABLE"), "DEAD", "ERROR", "CHANNEL_UNAVAILABLE"),
            (SendResult(400, "BAD_REQUEST"), "DEAD", "ACTIVE", "BAD_REQUEST"),
            (SendResult(401), "DEAD", "ERROR", "SIGNATURE_REJECTED"),
            (SendResult(403), "DEAD", "ERROR", "FORBIDDEN"),
            (SendResult(404, "INTEGRATION_NOT_FOUND"), "DEAD", "EXPIRED", "INTEGRATION_NOT_FOUND"),
            (SendResult(429, retry_after="120"), "PENDING", "ACTIVE", "HTTP_429"),
            (SendResult(500), "PENDING", "ACTIVE", "HTTP_500"),
            (OSError("must not log raw error"), "PENDING", "ACTIVE", "NETWORK_ERROR"),
        ]
        for result, state, integration, code in cases:
            with self.subTest(result=result):
                self.write_config(signingSecret=str(uuid.uuid4()))
                self.configuration.refresh(self.clock())
                with self.database.immediate() as connection:
                    connection.execute("DELETE FROM outbound_messages")
                self.accept(event())
                self.sender.result = result
                with self.assertLogs("upload_receiver.outbound", level="INFO") as logs:
                    self.assertTrue(self.dispatcher.run_once(self.clock()))
                row = self.rows()[0]
                self.assertEqual(row["state"], state)
                self.assertEqual(row["attempts"], 1)
                self.assertEqual(row["last_error_code"], code)
                self.assertEqual(self.configuration.state()["state"], integration)
                self.assertNotIn("must not log raw error", " ".join(logs.output))
                request = self.sender.requests[-1]
                self.assertEqual(request.timeout, 15)
                payload = json.loads(request.body)
                signed, headers = sign_payload(payload, self.configuration.current.signing_secret, request.headers["x-geon-timestamp"], request.headers["x-geon-nonce"])
                self.assertEqual(request.body, signed)
                self.assertEqual(dict(request.headers), headers)
                if state == "PENDING":
                    delay = 120 if isinstance(result, SendResult) and result.status == 429 else 15
                    self.assertEqual(parse_timestamp(row["next_attempt_at"]), self.clock() + delay)
                self.clock.now += 1

    def test_pause_persists_then_config_change_resumes_and_expires_pending(self) -> None:
        self.accept(event(), event("device.storage"))
        self.sender.result = SendResult(404, "INTEGRATION_NOT_FOUND")
        self.dispatcher.run_once(NOW)
        self.assertFalse(self.dispatcher.run_once(NOW + 10))
        another = GeonWorkConfiguration(self.path, self.database)
        another.refresh(NOW + 10)
        self.assertEqual(another.state()["state"], "EXPIRED")
        # A timestamp-only touch reloads, but the same fingerprint stays paused.
        info = self.path.stat()
        os.utime(self.path, ns=(info.st_atime_ns, info.st_mtime_ns + 1))
        self.assertFalse(self.dispatcher.run_once(NOW + 20))
        self.accept(event("upload.failed"))
        self.write_config(signingSecret="changed-secret")
        self.sender.result = SendResult(201)
        self.assertTrue(self.dispatcher.run_once(NOW + 30))
        self.assertEqual(self.configuration.state()["state"], "ACTIVE")
        self.assertEqual([row["state"] for row in self.rows()], ["DEAD", "SENT", "PENDING"])
        self.path.unlink()
        self.assertFalse(self.dispatcher.run_once(NOW + 86400))
        self.assertEqual(self.rows()[-1]["last_error_code"], "STALE")

    def test_spacing_one_in_flight_backoff_and_retry_exhaustion(self) -> None:
        self.accept(event(), event("device.storage"))
        original = self.sender
        def sender(request):
            self.assertFalse(self.dispatcher.run_once(NOW))
            return original(request)
        self.dispatcher.sender = sender
        self.assertTrue(self.dispatcher.run_once(NOW))
        self.assertFalse(self.dispatcher.run_once(NOW + 0.49))
        self.assertTrue(self.dispatcher.run_once(NOW + 0.5))
        self.accept(event("upload.failed"))
        self.dispatcher.sender = self.sender
        self.sender.result = SendResult(500)
        next_time = NOW + 1
        for attempt in range(1, 11):
            self.dispatcher.run_once(next_time)
            row = self.rows()[-1]
            self.assertEqual(row["attempts"], attempt)
            if attempt < 10:
                self.assertEqual(row["state"], "PENDING")
                delay = min(900, 15 * 2 ** (attempt - 1))
                self.assertEqual(parse_timestamp(row["next_attempt_at"]), next_time + delay)
                next_time += delay
            else:
                self.assertEqual(row["state"], "DEAD")
                self.assertEqual(row["last_error_code"], "RETRY_EXHAUSTED")

    def test_retry_after_http_date_and_sending_restart(self) -> None:
        from email.utils import formatdate
        self.accept(event())
        with self.database.immediate() as connection:
            connection.execute("UPDATE outbound_messages SET state='SENDING', attempts=1")
        self.sender.result = SendResult(429, retry_after=formatdate(NOW + 90, usegmt=True))
        self.dispatcher.run_once(NOW)
        row = self.rows()[0]
        self.assertEqual(row["attempts"], 2)
        self.assertEqual(parse_timestamp(row["next_attempt_at"]), NOW + 90)

    def test_stale_not_sent_and_idempotency_unique(self) -> None:
        self.accept(event())
        self.assertFalse(self.dispatcher.run_once(NOW + 86400))
        self.assertEqual(self.rows()[0]["last_error_code"], "STALE")
        self.assertEqual(self.sender.requests, [])
        payload = map_event(DEVICE_ID, None, CHANNEL_ID, event("upload.completed"))
        with self.database.immediate() as connection:
            first = enqueue(connection, DEVICE_ID, payload, NOW, idempotency_key="unique-key")
            payload["status"] = "changed"
            second = enqueue(connection, DEVICE_ID, payload, NOW, idempotency_key="unique-key")
        self.assertIsNotNone(first)
        self.assertIsNone(second)
        self.database.initialize()
        self.assertEqual(len(self.rows()), 2)

    def test_status_counts_window_and_no_secret(self) -> None:
        self.accept(event(), event("device.storage"), event("upload.failed"))
        self.dispatcher.run_once(NOW)
        self.sender.result = SendResult(200)
        self.dispatcher.run_once(NOW + 1)
        self.sender.result = SendResult(400, "BAD_REQUEST")
        self.dispatcher.run_once(NOW + 2)
        self.clock.now += 3
        self.accept(event("run.started"))
        status = self.events.status(DEVICE_ID, self.clock())
        self.assertEqual(status["window"], {"hours": 24, "pending": 1, "sent": 1, "dedup": 1, "dead": 1})
        self.assertEqual(status["lastDelivered"], {"status": "저장 공간 부족", "at": timestamp(NOW + 1)})
        self.assertEqual(status["lastFailure"]["code"], "BAD_REQUEST")
        self.assertEqual(status["lastSeenAt"], timestamp(NOW + 3))
        self.assertNotIn(CONFIG["signingSecret"], json.dumps(status))
        self.assertEqual(self.events.status(DEVICE_ID, NOW + 86404)["window"], {"hours": 24, "pending": 0, "sent": 0, "dedup": 0, "dead": 0})

    def test_http_sender_canonical_body_headers_response_and_tls(self) -> None:
        from unittest.mock import MagicMock
        connection = MagicMock()
        response = connection.getresponse.return_value
        response.status = 409
        response.getheader.return_value = None
        response.read.return_value = b'{"error":{"code":"WEBHOOK_REPLAY"}}'
        request = OutboundRequest(CONFIG["webhookUrl"], b'{"a":1}', {"Authorization": "Bearer future-token"}, method="PATCH")
        with patch("upload_receiver.outbound.http.client.HTTPSConnection", return_value=connection) as constructor:
            result = http_sender(request)
        self.assertEqual(result, SendResult(409, "WEBHOOK_REPLAY"))
        self.assertEqual(constructor.call_args.args, ("example.invalid", 443))
        self.assertEqual(constructor.call_args.kwargs["timeout"], 15)
        self.assertIsNotNone(constructor.call_args.kwargs["context"])
        connection.request.assert_called_once_with("PATCH", "/v1/webhooks/incoming/test", request.body, dict(request.headers))
        connection.close.assert_called_once()


@unittest.skipUnless(os.name == "posix", "Receiver API requires Linux/WSL")
class DeviceEventsApiTest(unittest.TestCase):
    def setUp(self) -> None:
        from tests import test_receiver
        self.helpers = test_receiver.ReceiverApiTest
        self.helpers.setUp(self)
        self.clock = FakeClock()
        self.receiver = self.client.app.state.receiver
        self.receiver.device_events.clock = self.clock
        self.receiver.settings.geon_work_config_path.write_text(json.dumps(CONFIG), encoding="utf-8")
        self.receiver.settings.geon_work_config_path.chmod(0o600)
        self.receiver.geon_work.refresh(NOW)

    def tearDown(self) -> None:
        self.helpers.tearDown(self)

    def auth(self, token=None):
        return self.helpers.auth(self, token)

    def employee_auth(self, token, environment="development"):
        return self.helpers.employee_auth(self, token, environment)

    def post(self, value=None, *, token=None, content=None, content_type="application/json"):
        return self.client.post("/v1/device-events", headers={**self.auth(token), "Content-Type": content_type}, content=content if content is not None else json.dumps(value or body(event())).encode())

    def test_auth_body_limits_content_type_and_capability(self) -> None:
        self.assertEqual(self.client.post("/v1/device-events", json=body(event())).status_code, 401)
        self.assertEqual(self.post(token="bad").status_code, 401)
        for content in (b"{", b"[]", b'{}', b'\xff'):
            self.assertEqual(self.post(content=content).status_code, 400)
        self.assertEqual(self.post(content=b"x" * 65537).status_code, 413)
        self.assertEqual(self.post(content_type="text/plain").status_code, 400)
        self.assertEqual(self.post(body(*[event() for _ in range(51)])).status_code, 400)
        capability = self.client.get("/v1/capabilities", headers=self.auth())
        self.assertEqual(capability.json()["deviceEvents"], {"version": 1, "maxEvents": 50, "maxBytes": 65536, "heartbeatSeconds": 300})

    def test_per_event_results_unknown_tolerance_and_rate_limit(self) -> None:
        first = event(extra={"anything": True})
        value = body(first, first, event("unknown"), event(occurredAt=timestamp(NOW - 7 * 86400 - 1)))
        value["future"] = "ignored"
        value["device"]["future"] = 123
        response = self.post(value)
        self.assertEqual(response.status_code, 200)
        self.assertEqual([row["state"] for row in response.json()["results"]], ["ACCEPTED", "DUPLICATE", "REJECTED", "REJECTED"])
        for _ in range(59):
            self.assertEqual(self.post(body(first)).status_code, 200)
        limited = self.post(body(first))
        self.assertEqual(limited.status_code, 429)
        self.assertEqual(limited.headers["Retry-After"], "60")
        self.assertEqual(self.post(body(first), token=self.second_token).status_code, 200)

    def test_status_employee_auth_and_project_scoping(self) -> None:
        url = "/v1/server/device-events/status"
        params = {"projectId": "road-alpha", "deviceId": DEVICE_ID}
        self.assertEqual(self.client.get(url, params=params).status_code, 401)
        self.assertEqual(self.client.get(url, params=params, headers=self.auth()).status_code, 401)
        employee = self.helpers.configure_employee_access(self)
        self.post()
        response = self.client.get(url, params=params, headers=self.employee_auth(employee))
        self.assertEqual(response.status_code, 200)
        status = response.json()
        self.assertEqual(set(status), {"deviceId", "integration", "window", "lastDelivered", "lastFailure", "lastSeenAt"})
        self.assertNotIn(CONFIG["signingSecret"], response.text)
        self.assertEqual(self.client.get(url, params={**params, "deviceId": SECOND_DEVICE_ID}, headers=self.employee_auth(employee)).status_code, 404)
        self.assertEqual(self.client.get(url, params={**params, "projectId": "other"}, headers=self.employee_auth(employee)).status_code, 404)
        self.assertEqual(self.client.get(url, params=params, headers={"Authorization": "Bearer " + employee}).status_code, 400)

    def test_upload_completion_row_labels_counts_monitoring_and_failure_is_swallowed(self) -> None:
        context = self.helpers.survey_context()
        run_id = context["runId"]
        self.post(body(event("run.started", runId=run_id, surveyProjectLabel="P", surveySectionLabel="S")))
        manifest = self.helpers.manifest([("empty", b"")])
        manifest["context"] = context
        created = self.helpers.create(self, manifest)
        self.assertEqual(created.status_code, 201)
        session_id = created.json()["sessionId"]
        self.post(body(event("run.finished", runId=run_id)))
        with closing(self.receiver.database.connect()) as connection:
            self.assertTrue(connection.execute("SELECT on_duty FROM device_presence").fetchone()[0])
        url = f"/v1/upload-sessions/{session_id}/complete"
        response = self.client.post(url, headers=self.auth(), json={})
        self.assertEqual(response.status_code, 200)
        with closing(self.receiver.database.connect()) as connection:
            rows = connection.execute("SELECT * FROM outbound_messages WHERE status_text='서버 수신 확인'").fetchall()
            self.assertEqual(len(rows), 1)
            payload = json.loads(rows[0]["payload_json"])
            self.assertEqual(payload["project"], "P · S")
            self.assertIn("files=1", payload["details"])
            self.assertIn("bytes=0", payload["details"])
            self.assertFalse(connection.execute("SELECT on_duty FROM device_presence").fetchone()[0])
        self.assertEqual(self.client.post(url, headers=self.auth(), json={}).status_code, 200)
        manifest = self.helpers.manifest([("empty", b"")], client_job_id="b" * 32)
        manifest["context"] = context
        created = self.helpers.create(self, manifest)
        session_id = created.json()["sessionId"]
        with patch.object(self.receiver.device_events, "upload_completed", side_effect=RuntimeError("sensitive raw text")):
            with self.assertLogs("upload_receiver.service", level="WARNING") as logs:
                response = self.client.post(f"/v1/upload-sessions/{session_id}/complete", headers=self.auth(), json={})
        self.assertEqual(response.status_code, 200)
        self.assertNotIn("sensitive raw text", " ".join(logs.output))

    def test_admin_configure_stdin_atomic_mode_and_status_no_secret(self) -> None:
        from upload_receiver import admin
        from contextlib import redirect_stdout
        output = io.StringIO()
        with patch.object(Settings, "from_env", return_value=self.settings), patch("sys.argv", ["admin", "configure-geon-work", "--webhook-url", CONFIG["webhookUrl"], "--channel-id", CHANNEL_ID]), patch("sys.stdin", io.StringIO("new-secret-first-line\nnot-the-secret\n")), redirect_stdout(output):
            admin.main()
        path = self.settings.geon_work_config_path
        self.assertEqual(path.stat().st_mode & 0o777, 0o600)
        self.assertEqual(json.loads(path.read_text())["signingSecret"], "new-secret-first-line")
        self.assertNotIn("new-secret", output.getvalue())
        self.assertEqual(list(path.parent.glob(".geon-work.json.*.tmp")), [])
        output = io.StringIO()
        with patch.object(Settings, "from_env", return_value=self.settings), patch("sys.argv", ["admin", "geon-work-status"]), redirect_stdout(output):
            admin.main()
        self.assertEqual(json.loads(output.getvalue())["state"], "ACTIVE")
        self.assertNotIn("new-secret", output.getvalue())
        self.assertNotIn(CONFIG["webhookUrl"], output.getvalue())


if __name__ == "__main__":
    unittest.main()
