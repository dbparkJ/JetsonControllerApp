from __future__ import annotations

import hashlib
import http.client
import json
import os
import ssl
import struct
import tempfile
import threading
import time
import unittest
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Callable, Dict, Optional, Tuple
from unittest.mock import patch
from urllib.parse import parse_qs, urlsplit

from jetson_control.filesystem import FileTooLarge, StorageRegistry, WorkspaceRegistry
from jetson_control.uploads import (
    FILE_BATCH_MAGIC,
    HTTP_COMPLETE_RESPONSE_TIMEOUT,
    UploadCapacityExceeded,
    UploadConfirmationRequired,
    UploadConflict,
    UploadManager,
    UploadTarget,
    UploadVerificationMismatch,
    _tls_client_context,
    _UploadHttpClient,
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

    def test_directory_listing_reports_recursive_logical_sizes(self) -> None:
        nested = self.source / "folder" / "nested"
        nested.mkdir()
        (nested / "more.bin").write_bytes(b"1234567")
        (self.source / "folder" / "ignored-link").symlink_to(
            self.source / "note.txt"
        )

        entries = self.storage.list_directory("data", "")

        folder = next(entry for entry in entries if entry["name"] == "folder")
        self.assertEqual(folder["sizeBytes"], 3 * 4096 + 7)
        note = next(entry for entry in entries if entry["name"] == "note.txt")
        self.assertEqual(note["sizeBytes"], 5)

    def test_directory_size_does_not_follow_a_swapped_symlink(self) -> None:
        outside = self.base / "outside"
        outside.mkdir()
        (outside / "secret.bin").write_bytes(b"s" * 100)
        original = self.source / "folder-original"
        real_open = os.open
        swapped = False

        def swap_before_open(path, flags, *args, **kwargs):
            nonlocal swapped
            if not swapped and Path(path) == self.source / "folder":
                swapped = True
                (self.source / "folder").rename(original)
                (self.source / "folder").symlink_to(outside, target_is_directory=True)
            return real_open(path, flags, *args, **kwargs)

        with patch("jetson_control.filesystem.os.open", side_effect=swap_before_open):
            entries = self.storage.list_directory("data", "")

        folder = next(entry for entry in entries if entry["name"] == "folder")
        self.assertTrue(swapped)
        self.assertIsNone(folder["sizeBytes"])

    def test_reads_bounded_files_and_locates_collection_paths(self) -> None:
        target, content = self.storage.read_file("data", "note.txt", max_bytes=100)
        self.assertEqual(target.name, "note.txt")
        self.assertEqual(content, b"hello")
        self.assertEqual(self.storage.locate(self.source / "folder"), ("data", "folder"))
        with self.assertRaises(FileTooLarge):
            self.storage.read_file("data", "note.txt", max_bytes=2)

    def test_user_confirmed_storage_delete_is_recoverable_and_blocks_root(self) -> None:
        with self.assertRaises(UploadConflict):
            self.uploads.delete_storage_entry("data", "note.txt", confirmed=False)
        self.assertTrue((self.source / "note.txt").is_file())

        deleted = self.uploads.delete_storage_entry(
            "data",
            "folder",
            confirmed=True,
        )
        self.assertEqual(deleted["state"], "TRASHED")
        self.assertEqual(deleted["entryType"], "DIRECTORY")
        self.assertFalse((self.source / "folder").exists())
        restored = self.uploads.trash.restore(str(deleted["trashId"]), confirmed=True)
        self.assertEqual(restored["state"], "RESTORED")
        self.assertTrue((self.source / "folder").is_dir())

        with self.assertRaises(UploadConflict):
            self.uploads.delete_storage_entry("data", "", confirmed=True)
        self.assertTrue(self.source.is_dir())

        with patch.object(self.uploads, "_source_overlaps_active_upload", return_value=True):
            with self.assertRaises(UploadConflict):
                self.uploads.delete_storage_entry("data", "note.txt", confirmed=True)
        self.assertTrue((self.source / "note.txt").is_file())

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

    def test_linked_upload_requires_trusted_runtime_context_and_unchanged_source(self) -> None:
        context = {
            "schemaVersion": 1,
            "surveyProjectId": "survey-alpha",
            "surveySectionId": "section-01",
            "runId": "capture/run-20260914T010203.000004Z-123.log",
            "deviceId": "device-test",
            "pipelineId": "capture",
            "sourceRevision": "1234567890abcdef1234567890abcdef12345678",
            "configSha256": "a" * 64,
            "outputId": "output-0123456789abcdef",
            "createdAt": "2026-09-14T01:02:03Z",
        }
        context_path = self.source / "folder" / ".jetson-output-context.json"
        context_path.write_text(json.dumps(context), encoding="utf-8")

        with self.assertRaisesRegex(UploadConflict, "trusted runtime record"):
            self.uploads.start("data", "folder", "archive", context=context)
        with self.assertRaisesRegex(UploadConflict, "does not match"):
            self.uploads.start(
                "data",
                "folder",
                "archive",
                context=context,
                expected_context={**context, "surveySectionId": "section-02"},
            )

        job = self.uploads.start(
            "data", "folder", "archive", context=context, expected_context=context
        )
        self.assertEqual(job["context"], context)
        self.assertRegex(str(job["sourceIdentity"]), r"^[a-f0-9]{64}$")
        deadline = time.monotonic() + 5
        while time.monotonic() < deadline:
            current = self.uploads.get(str(job["id"]))
            if current["state"] in {"COMPLETED", "FAILED"}:
                break
            time.sleep(0.02)
        self.assertEqual(current["state"], "COMPLETED", current)
        (self.source / "folder" / "sample.bin").write_bytes(b"changed")
        current["state"] = "FAILED"
        self.uploads._save_job(current)
        with self.assertRaisesRegex(UploadConflict, "source changed"):
            self.uploads.retry(str(job["id"]))

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

    def test_only_confirmed_terminal_upload_history_can_be_deleted(self) -> None:
        job_id = "0123456789abcdef0123456789abcdef"
        job = UploadManager._new_job(job_id, "data", "note.txt", "archive")
        self.uploads._save_job(job)

        with self.assertRaises(UploadConfirmationRequired):
            self.uploads.delete_job(job_id, confirmed=False)
        with self.assertRaises(UploadConflict):
            self.uploads.delete_job(job_id, confirmed=True)
        self.assertEqual(self.uploads.get(job_id)["state"], "QUEUED")

        job["state"] = "CANCELLED"
        self.uploads._save_job(job)
        self.uploads._cancellations[job_id] = threading.Event()
        with self.assertRaisesRegex(UploadConflict, "still stopping"):
            self.uploads.delete_job(job_id, confirmed=True)
        self.uploads._cancellations.pop(job_id)

        job["state"] = "COMPLETED"
        self.uploads._save_job(job)
        deleted = self.uploads.delete_job(job_id, confirmed=True)

        self.assertEqual(deleted["state"], "DELETED")
        self.assertEqual(deleted["previousState"], "COMPLETED")
        with self.assertRaises(KeyError):
            self.uploads.get(job_id)
        self.assertEqual((self.source / "note.txt").read_bytes(), b"hello")

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
            self.assertFalse(deleted_job["sourceDeleted"])
            self.assertTrue(deleted_job["sourceRecoverable"])
            self.assertIsNotNone(deleted_job["sourceTrashedAt"])
            self.assertFalse((self.source / "folder").exists())
            duplicate = uploads.delete_completed_source(
                str(job["id"]),
                confirmed=True,
            )
            self.assertEqual(duplicate["sourceTrashId"], deleted_job["sourceTrashId"])
            uploads.trash.restore(str(deleted_job["sourceTrashId"]), confirmed=True)
            self.assertTrue((self.source / "folder").is_dir())
            source_file.write_bytes(b"z" * len(original))
            restored_mismatch = uploads.verify_completed_source(str(job["id"]))
            self.assertEqual(restored_mismatch["state"], "MISMATCH")
            self.assertFalse(restored_mismatch["matched"])
            restored_job = uploads.get(str(job["id"]))
            self.assertIsNone(restored_job["sourceTrashId"])
            self.assertIsNone(restored_job["sourceTrashedAt"])
            self.assertFalse(restored_job["sourceRecoverable"])
            self.assertFalse(restored_job["deletionEligible"])

            source_file.write_bytes(original)
            self.assertEqual(uploads.verify_completed_source(str(job["id"]))["state"], "MATCHED")
            retrash = uploads.delete_completed_source(str(job["id"]), confirmed=True)
            retrash_id = str(retrash["sourceTrashId"])
            journal_path = uploads.trash.journal_dir / f"{retrash_id}.json"
            journal = json.loads(journal_path.read_text(encoding="utf-8"))
            journal["state"] = "PURGING"
            uploads.trash._write_record(journal)
            purging_job = uploads.get(str(job["id"]))
            self.assertEqual(purging_job["sourceDeletionState"], "PURGING")
            self.assertFalse(purging_job["sourceDeleted"])
            self.assertFalse(purging_job["sourceRecoverable"])
            self.assertEqual(
                uploads.verify_completed_source(str(job["id"]))["state"],
                "SOURCE_PURGING",
            )
            uploads.trash.empty([retrash_id], confirmed=True)
            purged_job = uploads.get(str(job["id"]))
            self.assertEqual(purged_job["sourceDeletionState"], "PURGED")
            self.assertTrue(purged_job["sourceDeleted"])
            self.assertFalse(purged_job["sourceRecoverable"])
            self.assertEqual(
                uploads.verify_completed_source(str(job["id"]))["state"],
                "SOURCE_PURGED",
            )

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


class KeepAliveReceiverHandler(UploadReceiverHandler):
    """Receiver stand-in that keeps HTTP/1.1 connections open and counts them."""

    protocol_version = "HTTP/1.1"
    counter_lock = threading.Lock()
    connections_opened = 0
    connections_open = 0
    close_silently_after_next_request = False

    def setup(self) -> None:
        super().setup()
        with type(self).counter_lock:
            type(self).connections_opened += 1
            type(self).connections_open += 1

    def finish(self) -> None:
        try:
            super().finish()
        finally:
            with type(self).counter_lock:
                type(self).connections_open -= 1

    def handle_one_request(self) -> None:
        super().handle_one_request()
        with type(self).counter_lock:
            close_now = type(self).close_silently_after_next_request
            type(self).close_silently_after_next_request = False
        if close_now:
            # Close without a Connection header, as an idle keep-alive timeout does.
            self.close_connection = True


class UploadThroughputTest(unittest.TestCase):
    """Connection reuse and job-state persistence, exercised through real uploads."""

    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.base = Path(self.temporary.name)
        self.source = self.base / "source"
        self.source.mkdir()
        (self.source / "folder").mkdir()
        (self.source / "folder" / "sample.bin").write_bytes(b"abc" * 4096)
        (self.source / "note.txt").write_text("hello", encoding="utf-8")
        # Larger than the receiver's 1 MiB batch limit, so it travels as resumable chunks.
        self.large = os.urandom(3 * 512 * 1024)
        (self.source / "large.bin").write_bytes(self.large)
        roots = self.base / "roots.json"
        roots.write_text(
            json.dumps(
                {"data": {"label": "Data", "path": str(self.source), "path_hint": "/data"}}
            ),
            encoding="utf-8",
        )
        self.storage = StorageRegistry(roots)
        self.targets = self.base / "targets.json"
        self.targets.write_text("{}", encoding="utf-8")
        self.token_file = self.base / "receiver.token"
        self.token_file.write_text(UploadReceiverHandler.token, encoding="utf-8")
        self.state = self.base / "state"
        self.uploads = UploadManager(
            self.storage,
            self.targets,
            self.state / "persist",
            "device-test",
            allow_local_targets=True,
        )

    def _receiver(self, handler_class: type) -> ThreadingHTTPServer:
        server = ThreadingHTTPServer(("127.0.0.1", 0), handler_class)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        self.addCleanup(thread.join, 2)
        self.addCleanup(server.server_close)
        self.addCleanup(server.shutdown)
        return server

    def _run_http_job(self, port: int) -> Tuple[UploadManager, Dict[str, object]]:
        self.targets.write_text(
            json.dumps(
                {
                    "cloud": {
                        "label": "External receiver",
                        "type": "http",
                        "base_url": f"http://127.0.0.1:{port}",
                        "token_file": str(self.token_file),
                        "verify_tls": False,
                    }
                }
            ),
            encoding="utf-8",
        )
        uploads = UploadManager(
            self.storage,
            self.targets,
            self.state / "http",
            "00000000-0000-0000-0000-000000000002",
        )
        job = uploads.start("data", "", "cloud")
        return uploads, self._wait_for_terminal(uploads, str(job["id"]))

    @staticmethod
    def _wait_for_terminal(uploads: UploadManager, job_id: str) -> Dict[str, object]:
        deadline = time.monotonic() + 15
        current: Dict[str, object] = {}
        while time.monotonic() < deadline:
            current = uploads.get(job_id)
            if current["state"] in {"COMPLETED", "FAILED", "CANCELLED"}:
                break
            time.sleep(0.02)
        return current

    @staticmethod
    def _wait_until(predicate: Callable[[], bool], timeout: float = 3.0) -> bool:
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            if predicate():
                return True
            time.sleep(0.02)
        return predicate()

    @staticmethod
    def _disk_job(uploads: UploadManager, job_id: str) -> Dict[str, object]:
        return UploadManager._load_path(uploads.jobs_dir / f"{job_id}.json")

    def test_http_job_uses_one_connection_for_offsets_batches_chunks_and_completion(self) -> None:
        handler = type("CountingHandler", (KeepAliveReceiverHandler,), {})
        server = self._receiver(handler)
        with patch("jetson_control.uploads.HTTP_CHUNK_SIZE", 128 * 1024):
            _uploads, current = self._run_http_job(server.server_port)

        self.assertEqual(current["state"], "COMPLETED", current)
        self.assertGreater(handler.batch_requests, 0)
        self.assertEqual(bytes(handler.files["large.bin"]), self.large)
        self.assertEqual(handler.connections_opened, 1)
        # The job closes its connection when it ends.
        self.assertTrue(self._wait_until(lambda: handler.connections_open == 0))

    def test_idle_connection_dropped_by_receiver_is_replaced_without_failing_the_job(self) -> None:
        handler = type(
            "DroppingHandler",
            (KeepAliveReceiverHandler,),
            {"close_silently_after_next_request": True},
        )
        server = self._receiver(handler)
        _uploads, current = self._run_http_job(server.server_port)

        self.assertEqual(current["state"], "COMPLETED", current)
        self.assertEqual(handler.connections_opened, 2)
        self.assertEqual(bytes(handler.files["large.bin"]), self.large)

    def test_completed_local_job_leaves_its_final_progress_on_disk(self) -> None:
        self.targets.write_text(
            json.dumps(
                {
                    "archive": {
                        "label": "Archive",
                        "type": "local",
                        "path": str(self.base / "destination"),
                    }
                }
            ),
            encoding="utf-8",
        )
        uploads = UploadManager(
            self.storage,
            self.targets,
            self.state / "throttled",
            "device-test",
            allow_local_targets=True,
        )
        # A frozen clock keeps every progress update inside the throttle window.
        uploads._job_clock = lambda: 0.0
        job_id = str(uploads.start("data", "", "archive")["id"])
        current = self._wait_for_terminal(uploads, job_id)

        self.assertEqual(current["state"], "COMPLETED", current)
        on_disk = self._disk_job(uploads, job_id)
        self.assertEqual(on_disk["state"], "COMPLETED")
        self.assertEqual(on_disk["bytesTransferred"], on_disk["bytesTotal"])
        self.assertEqual(on_disk["filesTransferred"], on_disk["filesTotal"])
        self.assertNotIn(job_id, uploads._unsaved_jobs)
        self.assertNotIn(job_id, uploads._last_job_save)

    def test_progress_is_throttled_while_state_changes_are_written_at_once(self) -> None:
        job = UploadManager._new_job(
            "0123456789abcdef0123456789abcdef",
            "data",
            "note.txt",
            "archive",
        )
        job_id = str(job["id"])
        job["bytesTotal"] = 1000
        self.uploads._save_job(job)
        clock = ManualClock()
        self.uploads._job_clock = clock

        self.uploads._update(job_id, state="UPLOADING")
        self.assertEqual(self._disk_job(self.uploads, job_id)["state"], "UPLOADING")

        clock.now += 0.2
        self.uploads._record_transfer(job_id, 100, newly_acknowledged=100)
        clock.now += 0.2
        self.uploads._record_transfer(job_id, 300, newly_acknowledged=200)
        self.assertEqual(self._disk_job(self.uploads, job_id)["bytesTransferred"], 0)
        self.assertEqual(self.uploads.get(job_id)["bytesTransferred"], 300)

        clock.now += 1.0
        self.uploads._record_transfer(job_id, 500, newly_acknowledged=200)
        self.assertEqual(self._disk_job(self.uploads, job_id)["bytesTransferred"], 500)

        # A transition inside the throttle window is still written immediately.
        clock.now += 0.1
        self.uploads._update(job_id, state="COMPLETED")
        self.assertEqual(self._disk_job(self.uploads, job_id)["state"], "COMPLETED")

    def test_flush_writes_unsaved_progress_and_clears_it(self) -> None:
        job = UploadManager._new_job(
            "fedcba9876543210fedcba9876543210",
            "data",
            "note.txt",
            "archive",
        )
        job_id = str(job["id"])
        job["bytesTotal"] = 1000
        self.uploads._save_job(job)
        clock = ManualClock()
        self.uploads._job_clock = clock
        self.uploads._update(job_id, state="UPLOADING")
        clock.now += 0.1
        self.uploads._record_transfer(job_id, 250, newly_acknowledged=250)
        self.assertEqual(self._disk_job(self.uploads, job_id)["bytesTransferred"], 0)

        self.uploads._flush_job(job_id)

        self.assertEqual(self._disk_job(self.uploads, job_id)["bytesTransferred"], 250)
        self.assertNotIn(job_id, self.uploads._unsaved_jobs)

    def test_crash_before_a_throttled_save_leaves_the_last_durable_state(self) -> None:
        job_id = "0123456789abcdef0123456789abcdef"
        job = UploadManager._new_job(job_id, "data", "note.txt", "archive")
        job["bytesTotal"] = 1000
        crash_state = self.state / "crash"
        with patch.object(UploadManager, "_recover_interrupted_jobs", lambda self: None):
            first = UploadManager(
                self.storage,
                self.targets,
                crash_state,
                "device-test",
                allow_local_targets=True,
            )
            first._job_clock = ManualClock()
            first._save_job(job)
            first._update(job_id, state="UPLOADING")
            first._record_transfer(job_id, 600, newly_acknowledged=600)
            # The process dies here: the 600-byte progress was never written.
            restarted = UploadManager(
                self.storage,
                self.targets,
                crash_state,
                "device-test",
                allow_local_targets=True,
            )

        recovered = restarted.get(job_id)
        self.assertEqual(recovered["state"], "UPLOADING")
        self.assertEqual(recovered["bytesTransferred"], 0)
        self.assertNotEqual(recovered["state"], "COMPLETED")


class ManualClock:
    def __init__(self) -> None:
        self.now = 100.0

    def __call__(self) -> float:
        return self.now


class ScriptedResponse:
    def __init__(self, status: int, body: bytes) -> None:
        self.status = status
        self.will_close = False
        self._body = body

    def read(self, limit: int = -1) -> bytes:
        size = len(self._body) if limit < 0 else min(limit, len(self._body))
        data, self._body = self._body[:size], self._body[size:]
        return data

    def isclosed(self) -> bool:
        return not self._body


class ScriptedConnection:
    """Stands in for an http.client connection; each request consumes one scripted step."""

    def __init__(self, steps: list, opened: list) -> None:
        self._steps = list(steps)
        self._response: Optional[ScriptedResponse] = None
        self.sock = None
        self.requests: list = []
        self.closed = False
        opened.append(self)

    def request(self, method: str, url: str, body=None, headers=None) -> None:
        self.requests.append((method, url, body))
        step = self._steps.pop(0)
        if isinstance(step, BaseException):
            raise step
        status, payload = step
        self._response = ScriptedResponse(status, payload)

    def getresponse(self) -> ScriptedResponse:
        assert self._response is not None
        return self._response

    def close(self) -> None:
        self.closed = True


class UploadHttpClientTest(unittest.TestCase):
    @staticmethod
    def make_client(scripts: list, *, keep_alive: bool = True):
        opened: list = []
        pending = list(scripts)

        def connect():
            return ScriptedConnection(pending.pop(0), opened), ""

        return _UploadHttpClient(connect, keep_alive=keep_alive), opened

    def test_normal_keep_alive_reuses_one_connection(self) -> None:
        client, opened = self.make_client([[(200, b"{}"), (200, b"{}"), (200, b"{}")]])
        for _ in range(3):
            self.assertEqual(
                client.request("GET", "/a", body=None, headers={}, read_limit=64),
                (200, b"{}"),
            )
        self.assertEqual(len(opened), 1)
        self.assertFalse(opened[0].closed)
        client.close()
        self.assertTrue(opened[0].closed)

    def test_connection_dropped_while_idle_is_replaced_and_the_request_repeated(self) -> None:
        errors = [
            ConnectionResetError(),
            ConnectionAbortedError(),
            BrokenPipeError(),
            http.client.RemoteDisconnected("closed by peer"),
            http.client.CannotSendRequest("previous request unfinished"),
        ]
        for error in errors:
            with self.subTest(error=type(error).__name__):
                client, opened = self.make_client(
                    [
                        [(200, b"{}"), error],
                        [(200, b'{"ok":true}')],
                    ]
                )
                self.assertEqual(
                    client.request("GET", "/a", body=None, headers={}, read_limit=64),
                    (200, b"{}"),
                )
                self.assertEqual(
                    client.request("PUT", "/b", body=b"x", headers={}, read_limit=64),
                    (200, b'{"ok":true}'),
                )
                self.assertEqual(len(opened), 2)
                self.assertTrue(opened[0].closed)
                self.assertEqual(opened[1].requests, [("PUT", "/b", b"x")])
                client.close()
                self.assertTrue(opened[1].closed)

    def test_retry_is_limited_to_one_repeat_on_a_reused_connection(self) -> None:
        client, opened = self.make_client(
            [
                [(200, b"{}"), ConnectionResetError()],
                [ConnectionResetError()],
            ]
        )
        client.request("GET", "/a", body=None, headers={}, read_limit=64)
        with self.assertRaises(ConnectionResetError):
            client.request("GET", "/b", body=None, headers={}, read_limit=64)
        self.assertEqual(len(opened), 2)

    def test_failure_on_a_fresh_connection_is_left_to_the_caller_retry_loop(self) -> None:
        client, opened = self.make_client([[http.client.RemoteDisconnected("gone")]])
        with self.assertRaises(http.client.RemoteDisconnected):
            client.request("GET", "/a", body=None, headers={}, read_limit=64)
        self.assertEqual(len(opened), 1)
        self.assertTrue(opened[0].closed)

    def test_error_status_and_unfinished_body_close_the_connection(self) -> None:
        client, opened = self.make_client(
            [
                [(409, b'{"detail":"offset"}')],
                [(200, b"{}"), (200, b"x" * 100)],
                [(200, b"{}")],
            ]
        )
        self.assertEqual(
            client.request("PUT", "/a", body=b"", headers={}, read_limit=64),
            (409, b'{"detail":"offset"}'),
        )
        self.assertTrue(opened[0].closed)
        self.assertEqual(
            client.request("GET", "/b", body=None, headers={}, read_limit=64),
            (200, b"{}"),
        )
        self.assertFalse(opened[1].closed)
        status, data = client.request("GET", "/c", body=None, headers={}, read_limit=10)
        self.assertEqual((status, len(data)), (200, 10))
        self.assertTrue(opened[1].closed)
        self.assertEqual(
            client.request("GET", "/d", body=None, headers={}, read_limit=64),
            (200, b"{}"),
        )
        self.assertEqual(len(opened), 3)

    def test_connection_idle_past_the_limit_is_replaced_before_reuse(self) -> None:
        client, opened = self.make_client([[(200, b"{}")], [(200, b"{}")]])
        with patch("jetson_control.uploads.HTTP_KEEPALIVE_MAX_IDLE_SECONDS", -1.0):
            client.request("GET", "/a", body=None, headers={}, read_limit=64)
            client.request("GET", "/b", body=None, headers={}, read_limit=64)
        self.assertEqual(len(opened), 2)
        self.assertTrue(opened[0].closed)

    def test_one_shot_client_never_keeps_a_connection(self) -> None:
        client, opened = self.make_client(
            [[(200, b"{}")], [(200, b"{}")]],
            keep_alive=False,
        )
        client.request("GET", "/a", body=None, headers={}, read_limit=64)
        client.request("GET", "/b", body=None, headers={}, read_limit=64)
        self.assertEqual(len(opened), 2)
        self.assertTrue(all(connection.closed for connection in opened))


class TlsClientContextTest(unittest.TestCase):
    def setUp(self) -> None:
        _tls_client_context.cache_clear()
        self.addCleanup(_tls_client_context.cache_clear)

    def test_context_is_built_once_per_verification_mode(self) -> None:
        with patch(
            "jetson_control.uploads.ssl.create_default_context",
            wraps=ssl.create_default_context,
        ) as factory:
            first = _tls_client_context(True)
            second = _tls_client_context(True)
        self.assertIs(first, second)
        self.assertEqual(factory.call_count, 1)
        self.assertTrue(first.check_hostname)
        self.assertEqual(first.verify_mode, ssl.CERT_REQUIRED)

        insecure = _tls_client_context(False)
        self.assertFalse(insecure.check_hostname)
        self.assertEqual(insecure.verify_mode, ssl.CERT_NONE)

    def test_https_connections_share_the_cached_context(self) -> None:
        target = UploadTarget(
            id="receiver",
            label="Receiver",
            kind="http",
            base_url="https://uploads.example.com",
            token_file=Path("unused-token"),
        )
        first, _path = UploadManager._http_connection(target)
        second, _path = UploadManager._http_connection(target)
        self.assertIs(first._context, second._context)
        self.assertIs(first._context, _tls_client_context(True))


if __name__ == "__main__":
    unittest.main()
