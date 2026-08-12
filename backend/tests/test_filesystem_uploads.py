import hashlib
import json
import tempfile
import threading
import time
import unittest
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlsplit

from jetson_control.filesystem import FileTooLarge, StorageRegistry, WorkspaceRegistry
from jetson_control.uploads import UploadCapacityExceeded, UploadManager


class UploadReceiverHandler(BaseHTTPRequestHandler):
    token = "test-receiver-token"
    manifest = None
    files = {}
    completed = False

    def log_message(self, _format, *_args) -> None:
        pass

    def _authorized(self) -> bool:
        return self.headers.get("Authorization") == f"Bearer {self.token}"

    def _json_response(self, status: int, value: dict) -> None:
        body = json.dumps(value, separators=(",", ":")).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _require_auth(self) -> bool:
        if self._authorized():
            return True
        self._json_response(401, {"detail": "unauthorized"})
        return False

    def do_POST(self) -> None:
        if not self._require_auth():
            return
        parsed = urlsplit(self.path)
        length = int(self.headers.get("Content-Length", "0"))
        body = json.loads(self.rfile.read(length) or b"{}")
        if parsed.path == "/v1/upload-sessions":
            type(self).manifest = body
            type(self).files = {item["path"]: bytearray() for item in body["files"]}
            type(self).completed = False
            self._json_response(201, {"sessionId": "session-test"})
            return
        if parsed.path == "/v1/upload-sessions/session-test/complete":
            type(self).completed = True
            self._json_response(200, {"state": "COMPLETED"})
            return
        self._json_response(404, {"detail": "not found"})

    def do_GET(self) -> None:
        if not self._require_auth():
            return
        parsed = urlsplit(self.path)
        if parsed.path != "/v1/upload-sessions/session-test/files/offset":
            self._json_response(404, {"detail": "not found"})
            return
        relative_path = parse_qs(parsed.query).get("path", [""])[0]
        if relative_path not in type(self).files:
            self._json_response(404, {"detail": "unknown file"})
            return
        self._json_response(200, {"nextOffset": len(type(self).files[relative_path])})

    def do_PUT(self) -> None:
        if not self._require_auth():
            return
        parsed = urlsplit(self.path)
        if parsed.path != "/v1/upload-sessions/session-test/files":
            self._json_response(404, {"detail": "not found"})
            return
        query = parse_qs(parsed.query)
        relative_path = query.get("path", [""])[0]
        offset = int(query.get("offset", ["-1"])[0])
        destination = type(self).files.get(relative_path)
        if destination is None or offset != len(destination):
            self._json_response(409, {"detail": "offset mismatch"})
            return
        length = int(self.headers.get("Content-Length", "0"))
        chunk = self.rfile.read(length)
        if hashlib.sha256(chunk).hexdigest() != self.headers.get("X-Chunk-SHA256"):
            self._json_response(422, {"detail": "checksum mismatch"})
            return
        destination.extend(chunk)
        self._json_response(200, {"nextOffset": len(destination)})


class FilesystemAndUploadsTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory()
        self.base = Path(self.temporary.name)
        self.source = self.base / "source"
        self.destination = self.base / "destination"
        self.state = self.base / "state"
        self.source.mkdir()
        (self.source / "folder").mkdir()
        (self.source / "folder" / "sample.bin").write_bytes(b"abc" * 4096)
        (self.source / "note.txt").write_text("hello", encoding="utf-8")

        roots = self.base / "roots.json"
        roots.write_text(
            json.dumps(
                {
                    "data": {
                        "label": "Data",
                        "path": str(self.source),
                        "path_hint": "/data",
                    }
                }
            ),
            encoding="utf-8",
        )
        self.targets = self.base / "targets.json"
        self.targets.write_text(
            json.dumps(
                {
                    "archive": {
                        "label": "Archive",
                        "type": "local",
                        "path": str(self.destination),
                    }
                }
            ),
            encoding="utf-8",
        )
        self.storage = StorageRegistry(roots)
        self.uploads = UploadManager(
            self.storage,
            self.targets,
            self.state,
            "device-test",
            allow_local_targets=True,
        )

    def tearDown(self) -> None:
        self.temporary.cleanup()

    def test_lists_directories_before_files_and_blocks_traversal(self) -> None:
        (self.source / "file-link").symlink_to(self.source / "note.txt")
        entries = self.storage.list_directory("data", "")
        self.assertEqual([entry["name"] for entry in entries], ["folder", "note.txt"])
        with self.assertRaises(ValueError):
            self.storage.resolve("data", "../../etc/passwd")

    def test_reads_bounded_files_and_locates_collection_paths(self) -> None:
        target, content = self.storage.read_file("data", "note.txt", max_bytes=100)
        self.assertEqual(target.name, "note.txt")
        self.assertEqual(content, b"hello")
        self.assertEqual(self.storage.locate(self.source / "folder"), ("data", "folder"))
        with self.assertRaises(FileTooLarge):
            self.storage.read_file("data", "note.txt", max_bytes=2)

    def test_workspace_is_limited_to_configured_home(self) -> None:
        workspace = WorkspaceRegistry(self.source)
        self.assertEqual(workspace.roots_response()[0]["pathHint"], "~/")
        self.assertEqual(
            [entry["name"] for entry in workspace.list_directory("workspace-home", "")],
            ["folder", "note.txt"],
        )
        with self.assertRaises(ValueError):
            workspace.resolve("workspace-home", "../../etc")

    def test_concurrent_uploads_are_limited(self) -> None:
        copy_started = threading.Event()
        release_copy = threading.Event()

        class BlockingUploadManager(UploadManager):
            def _copy_to_local_target(self, *args, **kwargs) -> None:
                copy_started.set()
                release_copy.wait(timeout=2)

        uploads = BlockingUploadManager(
            self.storage,
            self.targets,
            self.state / "limited",
            "device-test",
            allow_local_targets=True,
            max_concurrent_jobs=1,
        )
        first = uploads.start("data", "note.txt", "archive")
        self.assertTrue(copy_started.wait(timeout=2))
        with self.assertRaises(UploadCapacityExceeded):
            uploads.start("data", "folder/sample.bin", "archive")
        uploads.cancel(str(first["id"]))
        release_copy.set()
        deadline = time.monotonic() + 2
        while time.monotonic() < deadline and uploads._cancellations:
            time.sleep(0.01)
        self.assertFalse(uploads._cancellations)

    def test_local_upload_is_persisted_and_completed(self) -> None:
        job = self.uploads.start("data", "", "archive")
        deadline = time.monotonic() + 5
        while time.monotonic() < deadline:
            current = self.uploads.get(str(job["id"]))
            if current["state"] in {"COMPLETED", "FAILED"}:
                break
            time.sleep(0.02)

        self.assertEqual(current["state"], "COMPLETED")
        self.assertEqual(current["filesTransferred"], 2)
        self.assertEqual(current["bytesTransferred"], current["bytesTotal"])
        copied = list(self.destination.rglob("sample.bin"))
        self.assertEqual(len(copied), 1)
        self.assertEqual(copied[0].read_bytes(), b"abc" * 4096)

    def test_external_http_upload_uses_resumable_receiver_contract(self) -> None:
        token_file = self.base / "receiver.token"
        token_file.write_text(UploadReceiverHandler.token, encoding="utf-8")
        server = ThreadingHTTPServer(("127.0.0.1", 0), UploadReceiverHandler)
        server_thread = threading.Thread(target=server.serve_forever, daemon=True)
        server_thread.start()
        try:
            self.targets.write_text(
                json.dumps(
                    {
                        "cloud": {
                            "label": "External receiver",
                            "type": "http",
                            "base_url": f"http://127.0.0.1:{server.server_port}",
                            "token_file": str(token_file),
                            "verify_tls": False,
                        }
                    }
                ),
                encoding="utf-8",
            )
            uploads = UploadManager(
                self.storage,
                self.targets,
                self.state / "external",
                "00000000-0000-0000-0000-000000000001",
            )
            job = uploads.start("data", "", "cloud")
            deadline = time.monotonic() + 10
            while time.monotonic() < deadline:
                current = uploads.get(str(job["id"]))
                if current["state"] in {"COMPLETED", "FAILED"}:
                    break
                time.sleep(0.02)

            self.assertEqual(current["state"], "COMPLETED", current)
            self.assertTrue(UploadReceiverHandler.completed)
            self.assertEqual(
                UploadReceiverHandler.manifest["deviceId"],
                "00000000-0000-0000-0000-000000000001",
            )
            self.assertEqual(
                bytes(UploadReceiverHandler.files["folder/sample.bin"]),
                b"abc" * 4096,
            )
            self.assertEqual(
                bytes(UploadReceiverHandler.files["note.txt"]),
                b"hello",
            )
            for manifest_file in UploadReceiverHandler.manifest["files"]:
                uploaded = bytes(UploadReceiverHandler.files[manifest_file["path"]])
                self.assertEqual(hashlib.sha256(uploaded).hexdigest(), manifest_file["sha256"])
        finally:
            server.shutdown()
            server.server_close()
            server_thread.join(timeout=2)

    def test_cancellation_cannot_be_overwritten_by_worker_completion(self) -> None:
        copy_started = threading.Event()
        release_copy = threading.Event()

        class BlockingUploadManager(UploadManager):
            def _copy_to_local_target(self, *args, **kwargs) -> None:
                copy_started.set()
                release_copy.wait(timeout=2)

        uploads = BlockingUploadManager(
            self.storage,
            self.targets,
            self.state / "cancellation",
            "device-test",
            allow_local_targets=True,
        )
        job = uploads.start("data", "note.txt", "archive")
        self.assertTrue(copy_started.wait(timeout=2))
        uploads.cancel(str(job["id"]))
        release_copy.set()

        deadline = time.monotonic() + 2
        while time.monotonic() < deadline:
            current = uploads.get(str(job["id"]))
            if current["state"] == "CANCELLED" and not uploads._cancellations:
                break
            time.sleep(0.01)
        self.assertEqual(current["state"], "CANCELLED")

    def test_cancellation_cannot_be_overwritten_by_worker_error(self) -> None:
        copy_started = threading.Event()
        release_copy = threading.Event()

        class FailingUploadManager(UploadManager):
            def _copy_to_local_target(self, *args, **kwargs) -> None:
                copy_started.set()
                release_copy.wait(timeout=2)
                raise OSError("simulated late write failure")

        uploads = FailingUploadManager(
            self.storage,
            self.targets,
            self.state / "cancel-error",
            "device-test",
            allow_local_targets=True,
        )
        job = uploads.start("data", "note.txt", "archive")
        self.assertTrue(copy_started.wait(timeout=2))
        uploads.cancel(str(job["id"]))
        release_copy.set()

        deadline = time.monotonic() + 2
        while time.monotonic() < deadline:
            current = uploads.get(str(job["id"]))
            if current["state"] == "CANCELLED" and not uploads._cancellations:
                break
            time.sleep(0.01)
        self.assertEqual(current["state"], "CANCELLED")

    def test_failed_upload_can_retry_with_same_job_id(self) -> None:
        class FailOnceUploadManager(UploadManager):
            attempts = 0

            def _copy_to_local_target(self, *args, **kwargs) -> None:
                self.attempts += 1
                if self.attempts == 1:
                    raise OSError("simulated first failure")
                return super()._copy_to_local_target(*args, **kwargs)

        uploads = FailOnceUploadManager(
            self.storage,
            self.targets,
            self.state / "retry",
            "device-test",
            allow_local_targets=True,
        )
        original = uploads.start("data", "note.txt", "archive")
        deadline = time.monotonic() + 2
        while time.monotonic() < deadline:
            failed = uploads.get(str(original["id"]))
            if failed["state"] == "FAILED" and not uploads._cancellations:
                break
            time.sleep(0.01)
        self.assertEqual(failed["state"], "FAILED")

        retried = uploads.retry(str(original["id"]))
        self.assertEqual(retried["id"], original["id"])
        deadline = time.monotonic() + 2
        while time.monotonic() < deadline:
            completed = uploads.get(str(original["id"]))
            if completed["state"] in {"COMPLETED", "FAILED"}:
                break
            time.sleep(0.01)
        self.assertEqual(completed["state"], "COMPLETED", completed)

    def test_active_upload_resumes_after_manager_restart(self) -> None:
        restart_state = self.state / "restart"
        jobs_dir = restart_state / "upload-jobs"
        jobs_dir.mkdir(parents=True)
        job = UploadManager._new_job(
            "0123456789abcdef0123456789abcdef",
            "data",
            "note.txt",
            "archive",
        )
        (jobs_dir / f"{job['id']}.json").write_text(
            json.dumps(job), encoding="utf-8"
        )

        uploads = UploadManager(
            self.storage,
            self.targets,
            restart_state,
            "device-test",
            allow_local_targets=True,
        )
        deadline = time.monotonic() + 2
        while time.monotonic() < deadline:
            current = uploads.get(str(job["id"]))
            if current["state"] in {"COMPLETED", "FAILED"}:
                break
            time.sleep(0.01)
        self.assertEqual(current["state"], "COMPLETED", current)


if __name__ == "__main__":
    unittest.main()
