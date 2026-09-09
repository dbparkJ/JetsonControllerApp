import asyncio
import hashlib
import json
import os
import tempfile
import threading
import time
import unittest
import zipfile
from pathlib import Path
from unittest.mock import patch

from jetson_control.diagnostics import (
    DiagnosticsStore, RequestEvidenceMiddleware, export_logs, request_ref,
)


class DiagnosticsStoreTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)
        self.stores = []

    def tearDown(self):
        for store in self.stores:
            store.close()
        self.temporary.cleanup()

    def store(self, **options):
        store = DiagnosticsStore(self.root, "api", **options)
        self.stores.append(store)
        store.start()
        self.assertTrue(store.flush())
        return store

    def records(self, prefix="events-"):
        return [json.loads(line) for path in (self.root / "diagnostics" / "api").glob(prefix + "*.jsonl")
                for line in path.read_text().splitlines()]

    def test_private_allowlist_correlation_and_export(self):
        store = self.store()
        nonce = "fixture-request-nonce"
        reference = hashlib.sha256(("STAB1:" + nonce).encode()).hexdigest()[:16]
        self.assertEqual(request_ref(nonce), reference)
        self.assertIsNone(request_ref("\ninvalid"))
        store.record("api_reply_sent", requestRef=reference, route="status", auth="verified",
                     status=200, responseSigned=True, token="private-token", body="private-body",
                     deviceId="private-device", address="111.111.111.192", method="GET\nsecret")
        self.assertTrue(store.flush())
        lines = self.records()
        reply = next(row for row in lines if row["event"] == "api_reply_sent")
        self.assertEqual(reply["requestRef"], reference)
        self.assertEqual(reply["auth"], "verified")
        self.assertNotIn("address", reply)
        self.assertNotIn("method", reply)
        self.assertEqual(len({r["seq"] for r in lines}), len(lines))
        # An unrelated file and even a malicious extra field in a schema record
        # cannot be exported by the dedicated safe-log exporter.
        (self.root / "secret.json").write_text("private-config")
        path = next(store.directory.glob("events-*.jsonl"))
        with path.open("a") as output:
            contaminated = dict(reply, token="private-token", state="secret-state")
            output.write(json.dumps(contaminated) + "\n{truncated")
        destination = self.root / "evidence.zip"
        manifest = export_logs([self.root], destination)
        self.assertEqual(manifest["skippedLines"], 1)
        with zipfile.ZipFile(destination) as archive:
            contents = b"".join(archive.read(name) for name in archive.namelist())
        for secret in (nonce, "private-token", "private-body", "private-device", "private-config", "111.111.111.192", "secret-state"):
            self.assertNotIn(secret.encode(), contents)
        self.assertEqual(destination.stat().st_mode & 0o777, 0o600)
        self.assertEqual(store.directory.stat().st_mode & 0o777, 0o700)
        self.assertTrue(all(p.stat().st_mode & 0o777 == 0o600 for p in store.directory.glob("*.jsonl")))
        with self.assertRaises(FileExistsError):
            export_logs([self.root], destination)

    def test_rotation_incident_prelude_post_window_and_cooldown(self):
        clock = [1000000]
        store = self.store(file_bytes=8192, elapsed=lambda: clock[0])
        store.record("api_reply_sent", status=200, route="status", recovered=True, episode="status")
        clock[0] += 10
        store.record("api_reply_sent", status=503, route="status", incident=True, episode="status")
        for _ in range(20):
            store.record("api_reply_sent", status=503, route="status", incident=True, episode="status")
        self.assertTrue(store.flush())
        self.assertEqual(len(list(store.directory.glob("incident-*.jsonl"))), 1)
        incident = self.records("incident-")
        self.assertTrue(any(row.get("status") == 200 for row in incident))
        clock[0] += 60001
        store.record("api_reply_sent", status=200, recovered=True, episode="status")
        self.assertTrue(store.flush())
        self.assertTrue(any(row["event"] == "incident_completed" for row in self.records("incident-")))
        # A new short episode within 120 seconds remains in the ring and does not
        # replace the preserved incident window.
        store.record("api_reply_sent", status=503, incident=True, episode="status")
        self.assertTrue(store.flush())
        self.assertEqual(len(list(store.directory.glob("incident-*.jsonl"))), 1)
        for _ in range(5):
            clock[0] += 120001
            store.record("api_reply_sent", status=200, recovered=True, episode="status")
            store.record("api_reply_sent", status=503, incident=True, episode="status")
            self.assertTrue(store.flush())
        for _ in range(200):
            store.record("api_reply_sent", status=200, route="status")
        self.assertTrue(store.flush())
        self.assertLessEqual(len(list(store.directory.glob("events-*.jsonl"))), 4)
        self.assertLessEqual(len(list(store.directory.glob("incident-*.jsonl"))), 3)
        self.assertTrue(all(p.stat().st_size <= 8192 for p in store.directory.glob("*.jsonl")))

    def test_queue_pressure_and_disk_failure_never_escape_and_are_visible(self):
        store = self.store(queue_capacity=1)
        entered = threading.Event()
        release = threading.Event()
        original = store._consume

        def slow(item):
            entered.set()
            release.wait(2)
            original(item)

        with patch.object(store, "_consume", side_effect=slow):
            store.record("api_request_received", route="status")
            self.assertTrue(entered.wait(1))
            for _ in range(50):
                store.record("api_request_received", route="status")
            self.assertGreater(store.health()["droppedEvents"], 0)
            release.set()
            deadline = time.monotonic() + 2
            while not store.flush() and time.monotonic() < deadline:
                time.sleep(.01)
        with patch.object(store, "_append", side_effect=OSError("private-path-secret")):
            store.record("api_reply_sent", status=503, incident=True)
            self.assertTrue(store.flush())
        self.assertGreater(store.health()["storageFailures"], 0)
        store.record("api_request_received", route="status")
        self.assertTrue(store.flush())
        health = [row for row in self.records() if row["event"] == "diagnostics_health"]
        self.assertTrue(any(row["droppedEvents"] > 0 and row["storageFailures"] > 0 for row in health))

    def test_backward_wall_clock_still_enforces_caps_with_active_oldest(self):
        directory = self.root / "diagnostics" / "api"
        directory.mkdir(parents=True)
        future = time.time() + 3600
        for prefix, count in (("events-", 4), ("incident-", 3)):
            for index in range(count):
                path = directory / (prefix + "prior-%d.jsonl" % index)
                path.write_text("{}\n")
                os.utime(path, (future + index, future + index))
        store = self.store()
        store.record("api_reply_sent", status=503, incident=True)
        self.assertTrue(store.flush())
        self.assertEqual(len(list(directory.glob("events-*.jsonl"))), 4)
        self.assertEqual(len(list(directory.glob("incident-*.jsonl"))), 3)
        self.assertTrue(store._regular_path.exists())
        self.assertTrue(store._incident_path.exists())

    def test_export_skips_rotated_file_symlink_and_fifo(self):
        store = self.store()
        regular = next(store.directory.glob("events-*.jsonl"))
        (store.directory / "events-symlink.jsonl").symlink_to(regular)
        os.mkfifo(str(store.directory / "events-fifo.jsonl"))
        original_open = os.open

        def raced_open(path, flags, *args, **kwargs):
            if str(path) == str(regular):
                raise FileNotFoundError("rotation fixture")
            return original_open(path, flags, *args, **kwargs)

        with patch("jetson_control.diagnostics.os.open", side_effect=raced_open):
            result = export_logs([self.root], self.root / "raced.zip")
        self.assertEqual(result["skippedFiles"], 1)
        self.assertEqual(result["files"], 0)

    def test_old_files_pruned_and_restart_evidence_preserved(self):
        directory = self.root / "diagnostics" / "api"
        directory.mkdir(parents=True)
        old = directory / "events-old.jsonl"
        old.write_text("old")
        os.utime(old, (1, 1))
        first = self.store()
        first.record("api_reply_sent", status=503, incident=True)
        self.assertTrue(first.close())
        second = self.store()
        self.assertFalse(old.exists())
        runs = {row["runId"] for row in self.records() if row["event"] == "process_start"}
        self.assertIn(first.run_id, runs)
        self.assertIn(second.run_id, runs)
        self.assertTrue(any(row.get("reason") == "process_stop" and row.get("complete") is False
                            for row in self.records("incident-")))


class RequestEvidenceTest(unittest.TestCase):
    def test_streaming_messages_and_request_body_remain_identical(self):
        with tempfile.TemporaryDirectory() as temporary:
            store = DiagnosticsStore(Path(temporary), "api")
            store.start()
            incoming = [{"type": "http.request", "body": b"private-body", "more_body": False}]
            outgoing = [
                {"type": "http.response.start", "status": 503, "headers": [(b"x-response-signature", b"private-signature")]},
                {"type": "http.response.body", "body": b"first", "more_body": True},
                {"type": "http.response.body", "body": b"second", "more_body": False},
            ]
            seen = []
            receives = []

            async def app(scope, receive, send):
                receives.append(await receive())
                scope.setdefault("state", {})["diagnostics_auth"] = "verified"
                for message in outgoing:
                    await send(message)

            async def receive():
                return incoming[0]

            async def send(message):
                seen.append(message)

            scope = {"type": "http", "path": "/v1/pipelines/private-id/logs", "method": "GET",
                     "headers": [(b"x-request-nonce", b"fixture-request-nonce")],
                     "query_string": b"secret=private-query", "server": ("0.0.0.0", 8765)}
            asyncio.run(RequestEvidenceMiddleware(app, store)(scope, receive, send))
            self.assertEqual(receives, incoming)
            self.assertTrue(all(actual is original for actual, original in zip(seen, outgoing)))
            store.close()
            rows = [json.loads(line) for p in store.directory.glob("events-*.jsonl") for line in p.read_text().splitlines()]
            reply = next(row for row in rows if row["event"] == "api_reply_sent")
            self.assertEqual(reply["route"], "pipeline_logs")
            self.assertEqual(reply["auth"], "verified")
            self.assertTrue(reply["responseSigned"])
            self.assertEqual(reply["socketPath"], "unknown")
            for private in ("private-id", "private-body", "private-query", "private-signature"):
                self.assertNotIn(private, json.dumps(rows))

    def test_clock_and_observer_exceptions_cannot_change_original_asgi_result(self):
        for failing in ("clock", "observer"):
            for fail_send in (False, True):
                with self.subTest(failing=failing, fail_send=fail_send), tempfile.TemporaryDirectory() as temporary:
                    store = DiagnosticsStore(Path(temporary), "api")
                    store.start()
                    outgoing = []
                    original_error = ConnectionError("original send failure")

                    async def app(scope, receive, send):
                        await send({"type": "http.response.start", "status": 200})
                        await send({"type": "http.response.body", "body": b"unchanged"})

                    async def send(message):
                        outgoing.append(message)
                        if fail_send and message["type"] == "http.response.body":
                            raise original_error

                    target = (patch("jetson_control.diagnostics.elapsed_millis", side_effect=OSError("clock fixture"))
                              if failing == "clock" else patch.object(store, "record", side_effect=OSError("store fixture")))
                    try:
                        with target:
                            call = RequestEvidenceMiddleware(app, store)(
                                {"type": "http", "path": "/v1/status", "method": "GET"}, None, send)
                            if fail_send:
                                with self.assertRaises(ConnectionError) as raised:
                                    asyncio.run(call)
                                self.assertIs(raised.exception, original_error)
                            else:
                                asyncio.run(call)
                        self.assertEqual(outgoing[-1]["body"], b"unchanged")
                        self.assertGreater(store.health()["storageFailures"], 0)
                    finally:
                        store.close()

    def test_send_failure_is_not_a_completed_reply(self):
        with tempfile.TemporaryDirectory() as temporary:
            store = DiagnosticsStore(Path(temporary), "api")
            store.start()

            async def app(scope, receive, send):
                await send({"type": "http.response.start", "status": 200})
                await send({"type": "http.response.body", "body": b"hidden"})

            async def send(message):
                if message["type"] == "http.response.body":
                    raise ConnectionError("private-address")

            async def receive():
                raise AssertionError("observer must not read receive")

            with self.assertRaises(ConnectionError):
                asyncio.run(RequestEvidenceMiddleware(app, store)(
                    {"type": "http", "path": "/v1/status", "method": "GET"}, receive, send))
            store.close()
            rows = [json.loads(line) for p in store.directory.glob("events-*.jsonl") for line in p.read_text().splitlines()]
            self.assertFalse(any(row["event"] == "api_reply_sent" for row in rows))
            self.assertTrue(any(row["event"] == "api_request_failed" for row in rows))


class WifiDirectEvidenceTest(unittest.TestCase):
    def test_unknown_observation_and_cleanup_are_distinct_without_new_commands(self):
        import subprocess
        from jetson_control.wifi_direct import WifiDirectController, WifiDirectSettings, WifiDirectError
        from tests.test_wifi_direct import FakeRunner

        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            runner = FakeRunner(concurrency_supported=False, managed_wifi_active=True,
                                single_interface_group=True)
            failure = [False]

            def run(command, **kwargs):
                if failure[0] and command == ["/usr/sbin/iw", "dev"]:
                    runner.calls.append(command)
                    return subprocess.CompletedProcess(command, 1, "", "private-observation-error")
                return runner(command, **kwargs)

            store = DiagnosticsStore(root, "p2p")
            store.start()
            controller = WifiDirectController(
                WifiDirectSettings(interface="wlan0", device_name="private-device-name"),
                run=run, start_process=runner.start_process, status_path=root / "wifi-direct.json",
                sleep=lambda _: None, diagnostics=store,
            )
            try:
                controller.prepare()
                controller.activate_peer_for_test("AA:BB:CC:DD:EE:FF")
                before = len(runner.calls)
                failure[0] = True
                controller.monitor()
                self.assertEqual(runner.calls[before:], [["/usr/sbin/iw", "dev"]])
                self.assertTrue(runner.group_created)
                failure[0] = False
                controller.monitor()
                controller.stop()
            finally:
                store.close()
            records = [json.loads(line) for path in store.directory.glob("events-*.jsonl")
                       for line in path.read_text().splitlines()]
            self.assertTrue(any(row.get("group") == "unknown" for row in records))
            self.assertTrue(any(row["event"] == "p2p_cleanup_requested" for row in records))
            completed = [row for row in records if row["event"] == "p2p_cleanup_completed"]
            self.assertTrue(completed)
            self.assertTrue(all(row["confirmed"] is False for row in completed))
            removal = [row for row in records if row.get("command") == "p2p_group_remove"]
            self.assertTrue(any(row["osAccepted"] and not row["confirmed"] for row in removal))
            for secret in ("AA:BB:CC:DD:EE:FF", "private-device-name", "private-observation-error", "192.168.49.1"):
                self.assertNotIn(secret, json.dumps(records))


if __name__ == "__main__":
    unittest.main()
