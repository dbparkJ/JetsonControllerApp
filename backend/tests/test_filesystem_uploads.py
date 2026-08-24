from __future__ import annotations

import hashlib
import json
import struct
import tempfile
import threading
import time
import unittest
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from unittest.mock import patch
from urllib.parse import parse_qs, urlsplit

from jetson_control.filesystem import FileTooLarge, StorageRegistry, WorkspaceRegistry
from jetson_control.uploads import (
    FILE_BATCH_MAGIC,
    HTTP_COMPLETE_RESPONSE_TIMEOUT,
    UploadCapacityExceeded,
    UploadConflict,
    UploadManager,
    UploadTarget,
    UploadVerificationMismatch,
)


class UploadReceiverHandler(BaseHTTPRequestHandler):
    token = "test-receiver-token"
    advertise_file_batch = True
    advertise_deferred_hashes = True
    manifest = None
    files = {}
    completed = False
    deleted = False
    batch_requests = 0
    batch_offset_requests = 0
    legacy_offset_requests = 0

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
            type(self).deleted = False
            type(self).batch_requests = 0
            type(self).batch_offset_requests = 0
            type(self).legacy_offset_requests = 0
            response = {"sessionId": "session-test"}
            if type(self).advertise_file_batch:
                response["fileBatch"] = {
                    "version": 1,
                    "maxBytes": 1024 * 1024,
                    "maxFiles": 32,
                }
            self._json_response(201, response)
            return
        if parsed.path == "/v1/upload-sessions/session-test/files/offsets":
            type(self).batch_offset_requests += 1
            paths = body.get("paths", [])
            self._json_response(
                200,
                {
                    "files": [
                        {"path": path, "nextOffset": len(type(self).files[path])}
                        for path in paths
                    ]
                },
            )
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
        if parsed.path == "/v1/capabilities":
            if not type(self).advertise_deferred_hashes:
                self._json_response(404, {"detail": "not found"})
                return
            self._json_response(
                200,
                {
                    "deferredFileHashes": {
                        "version": 1,
                        "manifestHashMode": "deferred-v1",
                    }
                },
            )
            return
        if parsed.path == "/v1/library/sessions/session-test/verification":
            if not type(self).completed or type(self).deleted:
                self._json_response(404, {"detail": "not found"})
                return
            manifest = type(self).manifest
            entries = [
                (
                    path,
                    len(content),
                    hashlib.sha256(bytes(content)).hexdigest(),
                )
                for path, content in sorted(type(self).files.items())
            ]
            self._json_response(
                200,
                {
                    "sessionId": "session-test",
                    "clientJobId": manifest["clientJobId"],
                    "sourceName": manifest["sourceName"],
                    "folderName": manifest["sourceName"],
                    "state": "COMPLETED",
                    "totalBytes": sum(size for _path, size, _digest in entries),
                    "fileCount": len(entries),
                    "contentSha256": UploadManager._content_digest(
                        manifest["sourceName"], entries
                    ),
                    "completedAt": "2026-08-21T00:00:00Z",
                },
            )
            return
        if parsed.path != "/v1/upload-sessions/session-test/files/offset":
            self._json_response(404, {"detail": "not found"})
            return
        relative_path = parse_qs(parsed.query).get("path", [""])[0]
        type(self).legacy_offset_requests += 1
        if relative_path not in type(self).files:
            self._json_response(404, {"detail": "unknown file"})
            return
        self._json_response(200, {"nextOffset": len(type(self).files[relative_path])})

    def do_DELETE(self) -> None:
        if not self._require_auth():
            return
        parsed = urlsplit(self.path)
        if parsed.path == "/v1/library/sessions/session-test":
            if not type(self).completed:
                self._json_response(409, {"detail": "not completed"})
                return
            type(self).deleted = True
            self._json_response(
                200,
                {"sessionId": "session-test", "state": "DELETED"},
            )
            return
        if parsed.path == "/v1/upload-sessions/session-test":
            self._json_response(200, {"state": "CANCELLED"})
            return
        self._json_response(404, {"detail": "not found"})

    def do_PUT(self) -> None:
        if not self._require_auth():
            return
        parsed = urlsplit(self.path)
        if parsed.path == "/v1/upload-sessions/session-test/files/batch":
            length = int(self.headers.get("Content-Length", "0"))
            body = self.rfile.read(length)
            if hashlib.sha256(body).hexdigest() != self.headers.get("X-Batch-SHA256"):
                self._json_response(422, {"detail": "checksum mismatch"})
                return
            try:
                entries = self._decode_batch(body)
            except (UnicodeDecodeError, ValueError, struct.error):
                self._json_response(400, {"detail": "invalid batch"})
                return
            offsets = []
            for path, content in entries:
                destination = type(self).files.get(path)
                if destination is None:
                    self._json_response(404, {"detail": "unknown file"})
                    return
                if destination and bytes(destination) != content:
                    self._json_response(409, {"detail": "offset mismatch"})
                    return
                if not destination:
                    destination.extend(content)
                offsets.append({"path": path, "nextOffset": len(destination)})
            type(self).batch_requests += 1
            self._json_response(200, {"files": offsets})
            return
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

    @staticmethod
    def _decode_batch(body: bytes) -> list[tuple[str, bytes]]:
        if not body.startswith(FILE_BATCH_MAGIC):
            raise ValueError("invalid magic")
        cursor = len(FILE_BATCH_MAGIC)
        count = struct.unpack_from(">I", body, cursor)[0]
        cursor += 4
        entries = []
        for _index in range(count):
            path_size, content_size = struct.unpack_from(">IQ", body, cursor)
            cursor += 12
            path = body[cursor : cursor + path_size].decode("utf-8")
            cursor += path_size
            content = body[cursor : cursor + content_size]
            cursor += content_size
            entries.append((path, content))
        if cursor != len(body):
            raise ValueError("trailing data")
        return entries


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
        (self.source / "folder-link").symlink_to(
            self.source / "folder",
            target_is_directory=True,
        )
        entries = self.storage.list_directory("data", "")
        self.assertEqual([entry["name"] for entry in entries], ["folder", "note.txt"])
        with self.assertRaises(ValueError):
            self.storage.resolve("data", "../../etc/passwd")
        root, resolved = self.storage.resolve("data", "folder-link")
        with self.assertRaises(UploadConflict):
            UploadManager._resolve_deletion_source(
                root.path,
                "folder-link",
                resolved,
            )

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
        self.assertEqual(
            workspace.locate(self.source / "folder"),
            ("workspace-home", "folder"),
        )
        self.assertIsNone(workspace.locate(self.base / "destination"))
        target, content = workspace.read_file(
            "workspace-home",
            "note.txt",
            max_bytes=100,
        )
        self.assertEqual(target.name, "note.txt")
        self.assertEqual(content, b"hello")

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
        summary = self.uploads.source_summary("data", "")
        self.assertEqual(summary["sourceName"], "source")
        self.assertEqual(summary["folderName"], "source")
        self.assertEqual(summary["sourceType"], "DIRECTORY")
        self.assertEqual(summary["filesTotal"], 2)
        self.assertEqual(summary["bytesTotal"], 3 * 4096 + len(b"hello"))

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
        self.assertEqual(current["sourceName"], "source")
        self.assertEqual(current["folderName"], "source")
        self.assertGreater(current["throughputBytesPerSecond"], 0)
        self.assertEqual(current["etaSeconds"], 0)
        copied = list(self.destination.rglob("sample.bin"))
        self.assertEqual(len(copied), 1)
        self.assertEqual(copied[0].read_bytes(), b"abc" * 4096)

    def test_transfer_progress_persists_throughput_and_eta(self) -> None:
        job = UploadManager._new_job(
            "0123456789abcdef0123456789abcdef",
            "data",
            "note.txt",
            "archive",
        )
        job["bytesTotal"] = 1000
        self.uploads._save_job(job)
        with patch(
            "jetson_control.uploads.time.monotonic",
            side_effect=[10.0, 12.0],
        ):
            self.uploads._begin_transfer(str(job["id"]))
            self.uploads._record_transfer(
                str(job["id"]),
                400,
                newly_acknowledged=400,
            )

        persisted = self.uploads.get(str(job["id"]))
        self.assertEqual(persisted["bytesTransferred"], 400)
        self.assertEqual(persisted["throughputBytesPerSecond"], 200)
        self.assertEqual(persisted["etaSeconds"], 3)

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
            self.assertEqual(current["remoteSessionId"], "session-test")
            self.assertGreater(current["throughputBytesPerSecond"], 0)
            self.assertEqual(current["etaSeconds"], 0)
            self.assertTrue(UploadReceiverHandler.completed)
            self.assertEqual(UploadReceiverHandler.batch_requests, 1)
            self.assertEqual(UploadReceiverHandler.batch_offset_requests, 1)
            self.assertEqual(UploadReceiverHandler.legacy_offset_requests, 0)
            self.assertEqual(
                UploadReceiverHandler.manifest["deviceId"],
                "00000000-0000-0000-0000-000000000001",
            )
            self.assertEqual(UploadReceiverHandler.manifest["hashMode"], "deferred-v1")
            self.assertNotIn("sha256", UploadReceiverHandler.manifest["files"][0])
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
                self.assertEqual(
                    uploaded,
                    (self.source / manifest_file["path"]).read_bytes(),
                )
        finally:
            server.shutdown()
            server.server_close()
            server_thread.join(timeout=2)

    def test_remote_verification_gates_source_and_library_deletion(self) -> None:
        token_file = self.base / "verification-receiver.token"
        token_file.write_text(UploadReceiverHandler.token, encoding="utf-8")
        self.targets.write_text(
            json.dumps(
                {
                    "cloud": {
                        "label": "Verification receiver",
                        "type": "http",
                        "base_url": "placeholder",
                        "token_file": str(token_file),
                        "verify_tls": False,
                    }
                }
            ),
            encoding="utf-8",
        )
        server = ThreadingHTTPServer(("127.0.0.1", 0), UploadReceiverHandler)
        server_thread = threading.Thread(target=server.serve_forever, daemon=True)
        server_thread.start()
        try:
            target_config = json.loads(self.targets.read_text(encoding="utf-8"))
            target_config["cloud"]["base_url"] = (
                f"http://127.0.0.1:{server.server_port}"
            )
            self.targets.write_text(json.dumps(target_config), encoding="utf-8")
            uploads = UploadManager(
                self.storage,
                self.targets,
                self.state / "verified-delete",
                "00000000-0000-0000-0000-000000000001",
            )
            job = uploads.start("data", "folder", "cloud")
            deadline = time.monotonic() + 10
            while time.monotonic() < deadline:
                current = uploads.get(str(job["id"]))
                if current["state"] in {"COMPLETED", "FAILED"}:
                    break
                time.sleep(0.02)
            self.assertEqual(current["state"], "COMPLETED", current)
            self.assertEqual(current["remoteSessionId"], "session-test")

            verification = uploads.verify_completed_source(str(job["id"]))
            self.assertEqual(verification["state"], "MATCHED")
            self.assertTrue(verification["deletionAllowed"])

            source_file = self.source / "folder" / "sample.bin"
            original = source_file.read_bytes()
            source_file.write_bytes(b"z" * len(original))
            mismatch = uploads.verify_completed_source(str(job["id"]))
            self.assertEqual(mismatch["state"], "MISMATCH")
            with self.assertRaises(UploadVerificationMismatch):
                uploads.delete_completed_source(str(job["id"]), confirmed=True)
            self.assertTrue((self.source / "folder").is_dir())

            source_file.write_bytes(original)
            with self.assertRaises(UploadConflict):
                uploads.delete_completed_source(str(job["id"]), confirmed=False)
            deleted_job = uploads.delete_completed_source(
                str(job["id"]),
                confirmed=True,
            )
            self.assertFalse((self.source / "folder").exists())
            self.assertTrue(deleted_job["sourceDeleted"])
            self.assertIsNotNone(deleted_job["sourceDeletedAt"])

            with self.assertRaises(UploadConflict):
                uploads.delete_library_session(
                    "cloud",
                    "session-test",
                    confirmed=False,
                )
            self.assertEqual(
                uploads.delete_library_session(
                    "cloud",
                    "session-test",
                    confirmed=True,
                ),
                {"sessionId": "session-test", "state": "DELETED"},
            )
            self.assertTrue(UploadReceiverHandler.deleted)
        finally:
            server.shutdown()
            server.server_close()
            server_thread.join(timeout=2)

    def test_external_http_upload_reports_manifest_hashing_progress(self) -> None:
        token_file = self.base / "progress-receiver.token"
        token_file.write_text(UploadReceiverHandler.token, encoding="utf-8")
        server = ThreadingHTTPServer(("127.0.0.1", 0), UploadReceiverHandler)
        server_thread = threading.Thread(target=server.serve_forever, daemon=True)
        server_thread.start()
        hashing_started = threading.Event()
        release_hashing = threading.Event()
        UploadReceiverHandler.advertise_deferred_hashes = False

        def controlled_hash(path, cancellation, _job_id, on_chunk=None):
            content = path.read_bytes()
            midpoint = max(1, len(content) // 2)
            digest = hashlib.sha256()
            digest.update(content[:midpoint])
            if on_chunk is not None:
                on_chunk(midpoint)
            hashing_started.set()
            if not release_hashing.wait(timeout=5):
                raise RuntimeError("Test did not release manifest hashing")
            if cancellation.is_set():
                raise RuntimeError("Upload was unexpectedly cancelled")
            digest.update(content[midpoint:])
            if on_chunk is not None:
                on_chunk(len(content) - midpoint)
            return digest.hexdigest()

        try:
            self.targets.write_text(
                json.dumps(
                    {
                        "progress": {
                            "label": "Progress receiver",
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
                self.state / "progress-external",
                "00000000-0000-0000-0000-000000000001",
            )
            with patch.object(UploadManager, "_sha256_file", side_effect=controlled_hash):
                job = uploads.start("data", "", "progress")
                self.assertTrue(hashing_started.wait(timeout=5))
                current = uploads.get(str(job["id"]))
                self.assertEqual(current["state"], "SCANNING")
                self.assertEqual(current["bytesTransferred"], 0)
                self.assertGreater(current["bytesPrepared"], 0)
                self.assertEqual(current["filesTransferred"], 0)
                self.assertEqual(current["filesPrepared"], 0)
                self.assertIsNotNone(current["currentFile"])

                release_hashing.set()
                deadline = time.monotonic() + 10
                while time.monotonic() < deadline:
                    current = uploads.get(str(job["id"]))
                    if current["state"] in {"COMPLETED", "FAILED"}:
                        break
                    time.sleep(0.02)

            self.assertEqual(current["state"], "COMPLETED", current)
            self.assertEqual(current["bytesTransferred"], current["bytesTotal"])
            self.assertEqual(current["filesTransferred"], current["filesTotal"])
        finally:
            release_hashing.set()
            UploadReceiverHandler.advertise_deferred_hashes = True
            server.shutdown()
            server.server_close()
            server_thread.join(timeout=2)

    def test_external_http_upload_falls_back_for_legacy_receiver(self) -> None:
        token_file = self.base / "legacy-receiver.token"
        token_file.write_text(UploadReceiverHandler.token, encoding="utf-8")
        UploadReceiverHandler.advertise_file_batch = False
        UploadReceiverHandler.advertise_deferred_hashes = False
        server = ThreadingHTTPServer(("127.0.0.1", 0), UploadReceiverHandler)
        server_thread = threading.Thread(target=server.serve_forever, daemon=True)
        server_thread.start()
        try:
            self.targets.write_text(
                json.dumps(
                    {
                        "legacy": {
                            "label": "Legacy receiver",
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
                self.state / "legacy-external",
                "00000000-0000-0000-0000-000000000001",
            )
            job = uploads.start("data", "", "legacy")
            deadline = time.monotonic() + 10
            while time.monotonic() < deadline:
                current = uploads.get(str(job["id"]))
                if current["state"] in {"COMPLETED", "FAILED"}:
                    break
                time.sleep(0.02)

            self.assertEqual(current["state"], "COMPLETED", current)
            self.assertEqual(UploadReceiverHandler.batch_requests, 0)
            self.assertEqual(UploadReceiverHandler.batch_offset_requests, 0)
            self.assertEqual(UploadReceiverHandler.legacy_offset_requests, 2)
            self.assertEqual(
                bytes(UploadReceiverHandler.files["folder/sample.bin"]),
                b"abc" * 4096,
            )
            self.assertEqual(bytes(UploadReceiverHandler.files["note.txt"]), b"hello")
        finally:
            UploadReceiverHandler.advertise_file_batch = True
            UploadReceiverHandler.advertise_deferred_hashes = True
            server.shutdown()
            server.server_close()
            server_thread.join(timeout=2)

    def test_managed_http_targets_can_be_saved_updated_and_deleted(self) -> None:
        created = self.uploads.save_http_target(
            target_id="field-server",
            label="Field server",
            base_url="https://upload.example.com/v1/",
            token="first-secret",
        )

        self.assertEqual(created["id"], "field-server")
        self.assertEqual(created["baseUrl"], "https://upload.example.com/v1")
        self.assertTrue(created["editable"])
        token_files = list((self.state / "upload-target-tokens").glob("*.token"))
        self.assertEqual(len(token_files), 1)
        token_path = token_files[0]
        self.assertEqual(token_path.read_text(encoding="utf-8"), "first-secret\n")
        self.assertEqual(token_path.stat().st_mode & 0o777, 0o600)

        updated = self.uploads.save_http_target(
            target_id="field-server",
            label="Field server 2",
            base_url="https://upload.example.com/v2",
            token=None,
        )
        self.assertEqual(updated["label"], "Field server 2")
        self.assertEqual(token_path.read_text(encoding="utf-8"), "first-secret\n")
        self.uploads.save_http_target(
            target_id="field-server",
            label="Field server 2",
            base_url="https://upload.example.com/v2",
            token="second-secret",
        )
        self.assertFalse(token_path.exists())
        token_files = list((self.state / "upload-target-tokens").glob("*.token"))
        self.assertEqual(len(token_files), 1)
        token_path = token_files[0]
        self.assertEqual(token_path.read_text(encoding="utf-8"), "second-secret\n")
        targets = self.uploads.targets_response()
        self.assertEqual(
            {target["id"] for target in targets},
            {"archive", "field-server"},
        )
        self.assertFalse(
            next(target for target in targets if target["id"] == "archive")["editable"]
        )

        self.uploads.delete_http_target("field-server")
        self.assertFalse(token_path.exists())
        self.assertEqual(
            [target["id"] for target in self.uploads.targets_response()],
            ["archive"],
        )

    def test_managed_target_requires_https_and_cannot_replace_admin_target(self) -> None:
        with self.assertRaises(ValueError):
            self.uploads.save_http_target(
                target_id="insecure",
                label="Insecure",
                base_url="http://upload.example.com",
                token="secret",
            )
        with self.assertRaises(ValueError):
            self.uploads.save_http_target(
                target_id="local",
                label="Local",
                base_url="https://127.0.0.1:9443",
                token="secret",
            )
        with self.assertRaises(UploadConflict):
            self.uploads.save_http_target(
                target_id="archive",
                label="Replacement",
                base_url="https://upload.example.com",
                token="secret",
            )

    def test_active_job_filter_excludes_terminal_jobs(self) -> None:
        active = UploadManager._new_job(
            "0123456789abcdef0123456789abcdef",
            "data",
            "note.txt",
            "archive",
        )
        completed = UploadManager._new_job(
            "abcdef0123456789abcdef0123456789",
            "data",
            "folder",
            "archive",
        )
        completed["state"] = "COMPLETED"
        self.uploads._save_job(active)
        self.uploads._save_job(completed)

        self.assertEqual(
            [job["id"] for job in self.uploads.list_jobs(active_only=True)],
            [active["id"]],
        )

    def test_active_job_blocks_managed_target_changes(self) -> None:
        self.uploads.save_http_target(
            target_id="field-server",
            label="Field server",
            base_url="https://upload.example.com",
            token="secret",
        )
        active = UploadManager._new_job(
            "0123456789abcdef0123456789abcdef",
            "data",
            "note.txt",
            "field-server",
        )
        self.uploads._save_job(active)

        with self.assertRaises(UploadConflict):
            self.uploads.save_http_target(
                target_id="field-server",
                label="Changed",
                base_url="https://other.example.com",
                token="new-secret",
            )
        with self.assertRaises(UploadConflict):
            self.uploads.delete_http_target("field-server")

    def test_completion_uses_extended_response_timeout_only(self) -> None:
        calls = []

        class TimeoutCaptureUploadManager(UploadManager):
            def _http_connection(self, _target):
                class Socket:
                    def settimeout(_self, value):
                        calls.append(("timeout", value))

                class Response:
                    status = 200

                    @staticmethod
                    def read(_limit):
                        return b'{"state":"COMPLETED"}'

                class Connection:
                    sock = Socket()

                    @staticmethod
                    def request(*_args, **_kwargs):
                        calls.append(("request", None))

                    @staticmethod
                    def getresponse():
                        calls.append(("response", None))
                        return Response()

                    @staticmethod
                    def close():
                        pass

                return Connection(), ""

        manager = TimeoutCaptureUploadManager(
            self.storage,
            self.targets,
            self.state / "completion-timeout",
            "device-test",
            allow_local_targets=True,
        )
        target = UploadTarget(
            id="receiver",
            label="Receiver",
            kind="http",
            base_url="https://uploads.example.com",
            token_file=self.base / "unused-token",
        )
        response = manager._http_json(
            target,
            "token",
            "POST",
            "/v1/upload-sessions/session/complete",
            {},
            response_timeout=HTTP_COMPLETE_RESPONSE_TIMEOUT,
        )
        self.assertEqual(response, {"state": "COMPLETED"})
        self.assertEqual(
            calls,
            [
                ("request", None),
                ("timeout", HTTP_COMPLETE_RESPONSE_TIMEOUT),
                ("response", None),
            ],
        )

    def test_json_response_size_is_bounded(self) -> None:
        class OversizedResponseUploadManager(UploadManager):
            def _http_connection(self, _target):
                class Response:
                    status = 200

                    @staticmethod
                    def read(limit):
                        return b"x" * limit

                class Connection:
                    sock = None

                    @staticmethod
                    def request(*_args, **_kwargs):
                        pass

                    @staticmethod
                    def getresponse():
                        return Response()

                    @staticmethod
                    def close():
                        pass

                return Connection(), ""

        manager = OversizedResponseUploadManager(
            self.storage,
            self.targets,
            self.state / "oversized-json-response",
            "device-test",
            allow_local_targets=True,
        )
        target = UploadTarget(
            id="receiver",
            label="Receiver",
            kind="http",
            base_url="https://uploads.example.com",
            token_file=self.base / "unused-token",
        )
        with self.assertRaisesRegex(RuntimeError, "response is too large"):
            manager._http_json(
                target,
                "token",
                "GET",
                "/v1/library/sessions",
                None,
                max_response_bytes=8,
            )

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
