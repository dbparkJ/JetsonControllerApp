import json
import os
import stat
import tempfile
import unittest
from datetime import datetime
from pathlib import Path
from types import SimpleNamespace
import uuid

from jetson_control.device_events import (
    DeviceEventQueue,
    DeviceEventService,
    StorageLevelDetector,
    TemperatureLevelDetector,
    select_device_events_target,
)
from jetson_control.uploads import UploadManager


class FakeClock:
    def __init__(self, value=1_800_000_000.0):
        self.value = value

    def __call__(self):
        return self.value

    def advance(self, seconds):
        self.value += seconds


class FakeUploadManager:
    def __init__(self, targets=None, active_jobs=None):
        self._targets = targets or {}
        self.active_jobs = active_jobs or []

    def targets(self):
        return self._targets

    @staticmethod
    def _read_target_token(_target):
        return "fixture-token"

    def list_jobs(self, active_only=False):
        return self.active_jobs if active_only else []


class FakeStatus:
    def __init__(self):
        self.value = {
            "statusFresh": True,
            "temperatureC": 40.0,
            "storageAvailableBytes": 50,
            "storageTotalBytes": 100,
            "metricValidity": {
                "temperatureC": {"validity": "valid"},
                "storageAvailableBytes": {"validity": "valid"},
                "storageTotalBytes": {"validity": "valid"},
            },
        }

    def snapshot(self):
        return dict(self.value)


class FakeRunContext:
    def __init__(self, records=None):
        self.records = records or {}
        self.calls = []

    def get_run(self, run_id):
        self.calls.append(run_id)
        record = self.records.get(run_id)
        if isinstance(record, Exception):
            raise record
        return record


class FakeTransport:
    def __init__(self, capabilities=None, responses=None):
        self.capabilities = capabilities or {
            "deviceEvents": {
                "version": 1,
                "maxEvents": 50,
                "maxBytes": 65536,
                "heartbeatSeconds": 300,
            }
        }
        self.responses = list(responses or [])
        self.calls = []

    def __call__(self, target, method, endpoint, token, body):
        self.calls.append((method, endpoint, token, body))
        if endpoint == "/v1/capabilities":
            return 200, json.dumps(self.capabilities).encode(), {}
        if not self.responses:
            envelope = json.loads(body.decode())
            return 200, json.dumps({
                "results": [
                    {"eventId": event["eventId"], "state": "ACCEPTED"}
                    for event in envelope["events"]
                ]
            }).encode(), {}
        response = self.responses.pop(0)
        if isinstance(response, Exception):
            raise response
        if callable(response):
            return response(target, method, endpoint, token, body)
        return response


def event(event_id, occurred_at):
    return {
        "eventId": event_id,
        "type": "device.temperature",
        "occurredAt": occurred_at,
        "level": "WARNING",
        "temperatureC": 81.0,
        "thresholdC": 80.0,
    }


class TemperatureDetectorTest(unittest.TestCase):
    def test_two_sample_rise_critical_recovery_and_peak(self):
        clock = FakeClock()
        detector = TemperatureLevelDetector(clock=clock)
        self.assertIsNone(detector.evaluate(80, clock()))
        self.assertIsNone(detector.evaluate(95, clock() + 29))
        self.assertIsNone(detector.evaluate(95, clock() + 30))
        critical = detector.evaluate(94, clock() + 60)
        self.assertEqual(critical["level"], "CRITICAL")
        self.assertEqual(critical["peakC"], 95.0)

        self.assertIsNone(detector.evaluate(86, clock() + 90))
        warning = detector.evaluate(86, clock() + 690)
        self.assertEqual(warning["level"], "WARNING")
        self.assertEqual(warning["peakC"], 95.0)
        self.assertIsNone(detector.evaluate(76, clock() + 720))
        normal = detector.evaluate(76, clock() + 1320)
        self.assertEqual(normal["level"], "NORMAL")
        self.assertIn("lastNormalAt", normal)

    def test_rise_and_recovery_hysteresis_do_not_flap(self):
        detector = TemperatureLevelDetector()
        now = 1_800_000_000.0
        self.assertIsNone(detector.evaluate(81, now))
        self.assertIsNone(detector.evaluate(79, now + 30))
        self.assertIsNone(detector.evaluate(81, now + 60))
        warning = detector.evaluate(81, now + 90)
        self.assertEqual(warning["level"], "WARNING")
        self.assertIsNone(detector.evaluate(76, now + 120))
        self.assertIsNone(detector.evaluate(78, now + 420))
        self.assertIsNone(detector.evaluate(76, now + 720))
        self.assertEqual(detector.level, "WARNING")
        recovered = detector.evaluate(76, now + 1320)
        self.assertEqual(recovered["level"], "NORMAL")


class StorageDetectorTest(unittest.TestCase):
    def test_thresholds_recovery_dwell_and_stability(self):
        detector = StorageLevelDetector()
        now = 1_800_000_000.0
        warning = detector.evaluate(149, 1000, now)
        self.assertEqual(warning["level"], "WARNING")
        critical = detector.evaluate(49, 1000, now + 1)
        self.assertEqual(critical["level"], "CRITICAL")
        self.assertIsNone(detector.evaluate(80, 1000, now + 2))
        self.assertEqual(detector.evaluate(100, 1000, now + 602)["level"], "WARNING")
        self.assertIsNone(detector.evaluate(180, 1000, now + 603))
        normal = detector.evaluate(200, 1000, now + 1203)
        self.assertEqual(normal["level"], "NORMAL")
        self.assertIn("lastNormalAt", normal)
        self.assertIsNone(detector.evaluate(200, 1000, now + 1204))


class DeviceEventQueueTest(unittest.TestCase):
    def test_bounds_persistence_expiry_and_private_atomic_state(self):
        clock = FakeClock()
        with tempfile.TemporaryDirectory() as temp:
            directory = Path(temp) / "device-events"
            queue = DeviceEventQueue(directory, clock=clock, max_events=2)
            time_text = datetime.fromtimestamp(clock()).astimezone().isoformat(timespec="seconds")
            queue.enqueue(event("one", time_text))
            queue.enqueue(event("two", time_text))
            queue.enqueue(event("three", time_text))
            queue.enqueue({"eventId": "heartbeat", "type": "device.heartbeat", "occurredAt": time_text})
            self.assertEqual([item["eventId"] for item in queue.events()], ["two", "three"])
            if os.name != "nt":
                mode = stat.S_IMODE((directory / "queue.json").stat().st_mode)
                self.assertEqual(mode, 0o600)

            restarted = DeviceEventQueue(directory, clock=clock, max_events=2)
            self.assertEqual([item["eventId"] for item in restarted.events()], ["two", "three"])
            clock.advance(24 * 60 * 60 + 1)
            self.assertEqual(restarted.events(flush=True), [])


class DeviceEventServiceTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.base = Path(self.temp.name)
        self.clock = FakeClock()
        self.target = SimpleNamespace(kind="http", device_events=True, id="receiver")
        self.uploads = FakeUploadManager({"receiver": self.target})
        self.status = FakeStatus()
        self.runs = FakeRunContext()
        self.transport = FakeTransport()
        self.service = DeviceEventService(
            state_dir=self.base,
            device_name="Fixture Jetson",
            upload_manager=self.uploads,
            status_snapshots=self.status,
            run_context=self.runs,
            clock=self.clock,
            transport=self.transport,
        )
        self.service.target = self.target

    def tearDown(self):
        self.temp.cleanup()

    def test_capability_gating_and_cache(self):
        self.transport = FakeTransport(capabilities={"deviceEvents": {"version": 2}})
        self.service.transport = self.transport
        self.service._enqueue({"type": "device.storage", "level": "WARNING"})
        self.service.process_due(force_send=True)
        self.service.process_due(force_send=True)
        self.assertIsNone(self.service.capabilities)
        self.assertEqual([call[1] for call in self.transport.calls], ["/v1/capabilities"])

    def test_batching_per_event_results_and_envelope(self):
        def partial(_target, _method, _endpoint, _token, body):
            sent = json.loads(body.decode())
            return 200, json.dumps({"results": [
                {"eventId": sent["events"][0]["eventId"], "state": "ACCEPTED"},
                {"eventId": sent["events"][1]["eventId"], "state": "REJECTED", "reason": "unknown type"},
            ]}).encode(), {}

        self.transport = FakeTransport(responses=[partial])
        self.service.transport = self.transport
        for number in range(3):
            self.service._enqueue({"type": "device.storage", "level": "WARNING", "n": number})
        self.service.process_due(force_send=True)
        post = next(call for call in self.transport.calls if call[1] == "/v1/device-events")
        envelope = json.loads(post[3].decode())
        self.assertEqual(envelope["schema"], "jetson.event.v1")
        self.assertEqual(envelope["device"], {"name": "Fixture Jetson"})
        self.assertEqual(len(envelope["events"]), 3)  # receiver advertises a 50-event batch
        self.assertEqual(uuid.UUID(envelope["events"][0]["eventId"]).version, 4)
        self.assertIsNotNone(datetime.fromisoformat(envelope["events"][0]["occurredAt"]).utcoffset())
        self.assertEqual(len(self.service.queue.events()), 1)
        self.assertEqual(self.service.queue.events()[0]["n"], 2)

    def test_batch_limit_and_413_halves_the_next_request(self):
        self.transport.responses = [
            (413, b"{}", {}),
            lambda _t, _m, _e, _token, body: (
                200,
                json.dumps({"results": [{"eventId": item["eventId"], "state": "ACCEPTED"}
                            for item in json.loads(body)["events"]]}).encode(),
                {},
            ),
        ]
        self.service.capabilities = {"maxEvents": 50, "maxBytes": 65536, "heartbeatSeconds": 300}
        self.service._capability_checked_at = self.clock()
        self.service._batch_limit = 50
        self.service.target = self.target
        for number in range(4):
            self.service._enqueue({"type": "device.storage", "level": "WARNING", "n": number})
        self.service.process_due(force_send=True)
        self.assertEqual(self.service._batch_limit, 2)
        self.clock.advance(10)
        self.service.process_due(force_send=True)
        posts = [call for call in self.transport.calls if call[1] == "/v1/device-events"]
        self.assertEqual(len(json.loads(posts[-1][3])["events"]), 2)
        self.assertEqual(len(self.service.queue.events()), 2)

    def test_max_bytes_splits_batches(self):
        first = self.service._new_event({"type": "device.storage", "level": "WARNING", "freeBytes": 10})
        second = self.service._new_event({"type": "device.storage", "level": "WARNING", "freeBytes": 20})
        limit = len(self.service._encode_envelope([first]))
        self.service.capabilities = {"maxEvents": 50, "maxBytes": limit, "heartbeatSeconds": 300}
        self.service._capability_checked_at = self.clock()
        self.service._enqueue({"type": "device.storage", "level": "WARNING", "freeBytes": 10})
        self.service._enqueue({"type": "device.storage", "level": "WARNING", "freeBytes": 20})
        self.service.process_due(force_send=True)
        posts = [call for call in self.transport.calls if call[1] == "/v1/device-events"]
        self.assertEqual(len(json.loads(posts[0][3])["events"]), 1)
        self.assertEqual(len(self.service.queue.events()), 1)

    def test_request_error_backoffs_and_drop_rules(self):
        cases = (
            (401, {}, 1800, 2),
            (403, {}, 1800, 2),
            (429, {"Retry-After": "120"}, 120, 2),
            (400, {}, 0, 0),
            (500, {}, 30, 2),
        )
        for status, headers, expected_backoff, remaining in cases:
            with self.subTest(status=status):
                service = DeviceEventService(
                    state_dir=self.base / str(status), device_name="Fixture",
                    upload_manager=self.uploads, status_snapshots=self.status,
                    run_context=self.runs, clock=self.clock,
                    transport=FakeTransport(responses=[(status, b"{}", headers)]),
                )
                service.target = self.target
                service.capabilities = {"maxEvents": 50, "maxBytes": 65536, "heartbeatSeconds": 300}
                service._capability_checked_at = self.clock()
                for number in range(2):
                    service._enqueue({"type": "run.started", "runId": str(number)})
                service.process_due(force_send=True)
                self.assertEqual(max(0, service._backoff_until - self.clock()), expected_backoff)
                self.assertEqual(len(service.queue.events()), remaining)

    def test_5xx_and_network_backoff_double_and_cap(self):
        def fail_batches(_target, _method, _endpoint, _token, body):
            events = json.loads(body)["events"]
            if events[0]["type"] == "device.heartbeat":
                return 200, b"{}", {}
            return 503, b"{}", {}

        transport = FakeTransport(responses=[fail_batches] * 16)
        service = DeviceEventService(
            state_dir=self.base / "retries", device_name="Fixture",
            upload_manager=self.uploads, status_snapshots=self.status,
            run_context=self.runs, clock=self.clock, transport=transport,
        )
        service.target = self.target
        service.capabilities = {"maxEvents": 10, "maxBytes": 65536, "heartbeatSeconds": 300}
        service._capability_checked_at = self.clock()
        service._enqueue({"type": "run.started", "runId": "one"})
        delays = []
        for _ in range(8):
            service.process_due(force_send=True)
            delays.append(service._backoff_until - self.clock())
            self.clock.advance(delays[-1])
        self.assertEqual(delays[:5], [30, 60, 120, 240, 480])
        self.assertEqual(delays[-1], 600)

        network = DeviceEventService(
            state_dir=self.base / "network", device_name="Fixture",
            upload_manager=self.uploads, status_snapshots=self.status,
            run_context=self.runs, clock=self.clock,
            transport=FakeTransport(responses=[OSError("network")]),
        )
        network.target = self.target
        network.capabilities = {"maxEvents": 10, "maxBytes": 65536, "heartbeatSeconds": 300}
        network._capability_checked_at = self.clock()
        network._enqueue({"type": "run.started", "runId": "one"})
        network.process_due(force_send=True)
        self.assertEqual(network._backoff_until - self.clock(), 30)

    def test_heartbeat_on_duty_and_never_queued(self):
        run_id = "capture/run-20261018T120000.000000Z-1.log"
        self.runs.records[run_id] = {"runId": run_id, "state": "RUNNING", "active": True}
        self.service.on_run_started({"runId": run_id, "pipelineId": "capture"})
        self.service.queue.acknowledge([self.service.queue.events()[0]["eventId"]])
        self.uploads.active_jobs = [{"id": "job-1", "state": "UPLOADING"}]
        self.service.capabilities = {"maxEvents": 50, "maxBytes": 65536, "heartbeatSeconds": 300}
        self.transport.responses = [lambda _t, _m, _e, _token, _body: (200, b"{}", {})]
        self.service._send_heartbeat()
        call = next(call for call in self.transport.calls if call[1] == "/v1/device-events")
        heartbeat = json.loads(call[3])["events"][0]
        self.assertTrue(heartbeat["onDuty"])
        self.assertEqual(heartbeat["activeRunId"], run_id)
        self.assertEqual(heartbeat["activeUploadJobId"], "job-1")
        self.service.queue.enqueue(heartbeat)
        self.assertEqual(self.service.queue.events(), [])

    def test_run_start_is_idempotent_and_restart_reconciles_finished(self):
        run_id = "capture/run-20261018T120000.000000Z-1.log"
        started = {
            "runId": run_id,
            "pipelineId": "capture",
            "state": "RUNNING",
            "contextSnapshot": {"surveyProjectLabel": "Project", "surveySectionLabel": "North"},
        }
        self.assertTrue(self.service.on_run_started(started))
        self.assertFalse(self.service.on_run_started(started))
        self.assertEqual(self.service.queue.events()[0]["type"], "run.started")

        terminal = {
            "runId": run_id,
            "pipelineId": "capture",
            "state": "COMPLETED",
            "contextSnapshot": started["contextSnapshot"],
            "durationMillis": 12_500,
            "output": {"manifest": {"bytesTotal": 456}},
            "quality": {"preciseFixRatio": 0.75},
        }
        restarted = DeviceEventService(
            state_dir=self.base, device_name="Fixture Jetson",
            upload_manager=self.uploads, status_snapshots=self.status,
            run_context=FakeRunContext({run_id: terminal}), clock=self.clock,
            transport=FakeTransport(capabilities={"deviceEvents": {"version": 2}}),
        )
        restarted.target = self.target
        restarted.process_due()
        self.assertEqual(restarted.run_context.calls, [run_id])
        self.assertEqual(restarted.queue.started_runs(), {})
        finished = restarted.queue.events()[-1]
        self.assertEqual(finished["type"], "run.finished")
        self.assertEqual(finished["result"], "COMPLETED")
        self.assertEqual(finished["surveyProjectLabel"], "Project")
        self.assertEqual(finished["durationSeconds"], 12.5)
        self.assertEqual(finished["bytesWritten"], 456)
        self.assertEqual(finished["preciseFixRatio"], 0.75)

    def test_upload_failure_count_resets_on_completion_and_uses_job_fallback(self):
        run_id = "capture/run-20261018T120000.000000Z-1.log"
        self.runs.records[run_id] = {
            "runId": run_id,
            "contextSnapshot": {"surveyProjectLabel": "Project"},
        }
        job = {"id": "job1", "context": {"runId": run_id}}
        self.service.on_upload_transition(job, "FAILED", "STORAGE_IO")
        self.service.on_upload_transition({"id": "job2", "context": {"runId": run_id}}, "FAILED", "RECEIVER_HTTP_503")
        self.assertEqual([e["failureCount"] for e in self.service.queue.events()], [1, 2])
        self.assertEqual(self.service.queue.events()[0]["surveyProjectLabel"], "Project")
        restarted = DeviceEventService(
            state_dir=self.base, device_name="Fixture Jetson",
            upload_manager=self.uploads, status_snapshots=self.status,
            run_context=self.runs, clock=self.clock, transport=self.transport,
        )
        restarted.on_upload_transition({"id": "job3", "context": {"runId": run_id}}, "FAILED", "STORAGE_IO")
        self.assertEqual(restarted.queue.events()[-1]["failureCount"], 3)
        restarted.on_upload_transition({"id": "job4", "context": {"runId": run_id}}, "COMPLETED", None)
        restarted.on_upload_transition({"id": "job5", "context": {"runId": run_id}}, "FAILED", "STORAGE_IO")
        restarted.on_upload_transition({"id": "fallback"}, "FAILED", "raw message must not pass")
        events = restarted.queue.events()
        self.assertEqual([item["failureCount"] for item in events], [1, 2, 3, 1, 1])
        self.assertEqual(events[-1]["errorCode"], "UNEXPECTED_FAILURE")
        self.assertNotIn("runId", events[-1])

    def test_no_target_starts_no_worker_or_network(self):
        uploads = FakeUploadManager({
            "one": SimpleNamespace(kind="http", device_events=False),
            "two": SimpleNamespace(kind="http", device_events=False),
        })
        service = DeviceEventService(
            state_dir=self.base / "disabled", device_name="Fixture",
            upload_manager=uploads, status_snapshots=self.status,
            run_context=self.runs, clock=self.clock, transport=FakeTransport(),
        )
        service.start()
        self.assertIsNone(service.target)
        self.assertIsNone(service._thread)

    def test_start_again_picks_up_a_target_added_later(self):
        uploads = FakeUploadManager({})
        service = DeviceEventService(
            state_dir=self.base / "later", device_name="Fixture",
            upload_manager=uploads, status_snapshots=self.status,
            run_context=self.runs, clock=self.clock, transport=FakeTransport(),
        )
        service.start()
        self.assertIsNone(service._thread)
        added = SimpleNamespace(kind="http", device_events=True)
        uploads._targets = {"receiver": added}
        service.start()
        try:
            self.assertIs(service.target, added)
            self.assertIsNotNone(service._thread)
        finally:
            service.stop()


class TargetSelectionTest(unittest.TestCase):
    def test_upload_target_configuration_flag_is_loaded(self):
        with tempfile.TemporaryDirectory() as temp:
            base = Path(temp)
            targets_path = base / "targets.json"
            targets_path.write_text(json.dumps({
                "receiver": {
                    "type": "http",
                    "base_url": "https://receiver.invalid",
                    "token_file": str(base / "token"),
                    "deviceEvents": True,
                }
            }), encoding="utf-8")
            manager = object.__new__(UploadManager)
            manager.allow_local_targets = False
            target = manager._load_targets(targets_path, editable=False)["receiver"]
            self.assertTrue(target.device_events)
            self.assertIs(select_device_events_target(FakeUploadManager({"receiver": target})), target)

    def test_flagged_single_target_and_ambiguous_rules(self):
        flagged = SimpleNamespace(kind="http", device_events=True, id="flagged")
        other = SimpleNamespace(kind="http", device_events=False, id="other")
        self.assertIs(select_device_events_target(FakeUploadManager({"a": other, "b": flagged})), flagged)
        self.assertIs(select_device_events_target(FakeUploadManager({"a": other})), other)
        self.assertIsNone(select_device_events_target(FakeUploadManager({"a": other, "b": SimpleNamespace(kind="http", device_events=False)})))
        second_flag = SimpleNamespace(kind="http", device_events=True, id="second")
        self.assertIsNone(select_device_events_target(FakeUploadManager({"a": flagged, "b": second_flag})))
        local = SimpleNamespace(kind="local", device_events=True, id="local")
        self.assertIsNone(select_device_events_target(FakeUploadManager({"a": local})))


class UploadTransitionHookTest(unittest.TestCase):
    def test_upload_manager_buffers_transitions_until_listener_is_attached(self):
        manager = object.__new__(UploadManager)
        import threading
        manager._event_lock = threading.Lock()
        manager._event_sink = None
        manager._pending_terminal_events = []
        manager._notify_terminal_transition({"id": "job"}, "FAILED", "STORAGE_IO")
        received = []
        manager.set_event_sink(lambda job, state, code: received.append((dict(job), state, code)))
        self.assertEqual(received, [({"id": "job"}, "FAILED", "STORAGE_IO")])

    def test_failure_classification_is_stable_and_message_free(self):
        self.assertEqual(UploadManager._failure_code(PermissionError("sensitive path")), "PERMISSION_DENIED")
        self.assertEqual(UploadManager._failure_code(OSError("private text")), "STORAGE_IO")
        self.assertEqual(UploadManager._failure_code(RuntimeError("raw error")), "UPLOAD_RUNTIME")


if __name__ == "__main__":
    unittest.main()
