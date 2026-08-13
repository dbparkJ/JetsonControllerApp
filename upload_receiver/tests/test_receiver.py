from __future__ import annotations

import hashlib
import json
import os
import sqlite3
import struct
import tempfile
import unittest
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
from unittest.mock import patch

from fastapi.testclient import TestClient

from upload_receiver.app import create_app
from upload_receiver import admin
from upload_receiver.admin import _write_secret
from upload_receiver.config import Settings
from upload_receiver.service import FILE_BATCH_MAGIC, ReceiverError, ReceiverService


DEVICE_ID = "d606c26d-98d6-4b09-99d7-c3da7dda4de0"
SECOND_DEVICE_ID = "7dbe212d-a91f-4f0f-a5a4-09b840c6a7a6"
EMPTY_SHA256 = hashlib.sha256(b"").hexdigest()


class ReceiverApiTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory()
        self.data_root = Path(self.temporary.name) / "receiver"
        self.settings = Settings(
            data_root=self.data_root,
            expected_mount=None,
            require_mount=False,
            max_manifest_bytes=1024 * 1024,
            max_chunk_bytes=4 * 1024 * 1024,
            max_files_per_session=100,
            max_session_bytes=1024 * 1024 * 1024,
            max_concurrent_puts_per_device=2,
            max_preview_bytes=16,
        )
        administrator = ReceiverService(self.settings, create_pepper=True)
        self.token = administrator.issue_token(DEVICE_ID, quota_bytes=512 * 1024 * 1024)
        self.second_token = administrator.issue_token(
            SECOND_DEVICE_ID, quota_bytes=512 * 1024 * 1024
        )
        self.client_context = TestClient(
            create_app(self.settings), raise_server_exceptions=False
        )
        self.client = self.client_context.__enter__()

    def tearDown(self) -> None:
        self.client_context.__exit__(None, None, None)
        self.temporary.cleanup()

    @staticmethod
    def manifest(
        files: list[tuple[str, bytes]],
        *,
        device_id: str = DEVICE_ID,
        client_job_id: str = "0123456789abcdef0123456789abcdef",
    ) -> dict[str, object]:
        return {
            "deviceId": device_id,
            "clientJobId": client_job_id,
            "sourceName": "capture-20260813",
            "files": [
                {
                    "path": path,
                    "sizeBytes": len(body),
                    "sha256": hashlib.sha256(body).hexdigest(),
                }
                for path, body in files
            ],
        }

    @staticmethod
    def deferred_manifest(
        files: list[tuple[str, bytes]],
        *,
        device_id: str = DEVICE_ID,
        client_job_id: str = "fedcba9876543210fedcba9876543210",
    ) -> dict[str, object]:
        return {
            "deviceId": device_id,
            "clientJobId": client_job_id,
            "sourceName": "capture-20260813",
            "hashMode": "deferred-v1",
            "files": [
                {"path": path, "sizeBytes": len(body)} for path, body in files
            ],
        }

    def auth(self, token: str | None = None) -> dict[str, str]:
        return {"Authorization": f"Bearer {token or self.token}"}

    def create(self, manifest: dict[str, object], token: str | None = None):
        return self.client.post(
            "/v1/upload-sessions",
            headers={**self.auth(token), "Content-Type": "application/json"},
            content=json.dumps(manifest, ensure_ascii=False).encode("utf-8"),
        )

    def put(
        self,
        session_id: str,
        path: str,
        body: bytes,
        *,
        offset: int = 0,
        total: int | None = None,
        token: str | None = None,
        chunk_hash: str | None = None,
    ):
        total_size = len(body) + offset if total is None else total
        return self.client.put(
            f"/v1/upload-sessions/{session_id}/files",
            params={"path": path, "offset": offset},
            headers={
                **self.auth(token),
                "Content-Type": "application/octet-stream",
                "Content-Range": (
                    f"bytes {offset}-{offset + len(body) - 1}/{total_size}"
                ),
                "X-Chunk-SHA256": chunk_hash or hashlib.sha256(body).hexdigest(),
            },
            content=body,
        )

    @staticmethod
    def batch_body(files: list[tuple[str, bytes]]) -> bytes:
        body = bytearray(FILE_BATCH_MAGIC)
        body.extend(struct.pack(">I", len(files)))
        for path, content in files:
            path_bytes = path.encode("utf-8")
            body.extend(struct.pack(">IQ", len(path_bytes), len(content)))
            body.extend(path_bytes)
            body.extend(content)
        return bytes(body)

    def put_batch(
        self,
        session_id: str,
        files: list[tuple[str, bytes]],
        *,
        batch_hash: str | None = None,
    ):
        body = self.batch_body(files)
        return self.client.put(
            f"/v1/upload-sessions/{session_id}/files/batch",
            headers={
                **self.auth(),
                "Content-Type": "application/vnd.jetson.upload-batch-v1",
                "X-Batch-SHA256": batch_hash or hashlib.sha256(body).hexdigest(),
            },
            content=body,
        )

    def test_health_and_authentication(self) -> None:
        self.assertEqual(self.client.get("/health/live").json(), {"state": "LIVE"})
        self.assertEqual(self.client.get("/health/ready").status_code, 200)
        response = self.client.post("/v1/upload-sessions", json={})
        self.assertEqual(response.status_code, 401)
        self.assertEqual(response.json(), {"detail": "Authentication failed"})
        response = self.client.post(
            "/v1/upload-sessions",
            headers={"Authorization": "Bearer invalid"},
            json={},
        )
        self.assertEqual(response.status_code, 401)
        self.assertEqual(self.client.get("/v1/capabilities").status_code, 401)
        capabilities = self.client.get("/v1/capabilities", headers=self.auth())
        self.assertEqual(capabilities.status_code, 200)
        self.assertEqual(
            capabilities.json()["deferredFileHashes"],
            {"version": 1, "manifestHashMode": "deferred-v1"},
        )
        self.assertEqual(
            capabilities.json()["library"],
            {"version": 1, "maxPreviewBytes": 16},
        )

    def test_completed_upload_library_lists_and_reads_owned_files(self) -> None:
        files = [
            ("camera/front.jpg", b"jpeg-preview"),
            ("camera/rear.jpg", b"rear-preview"),
            ("notes.txt", b"field notes"),
        ]
        created = self.create(
            self.manifest(files, client_job_id="7" * 32)
        )
        session_id = created.json()["sessionId"]
        for path, body in files:
            self.assertEqual(self.put(session_id, path, body).status_code, 200)
        completed = self.client.post(
            f"/v1/upload-sessions/{session_id}/complete",
            headers=self.auth(),
            json={},
        )
        self.assertEqual(completed.status_code, 200, completed.text)

        sessions = self.client.get("/v1/library/sessions", headers=self.auth())
        self.assertEqual(sessions.status_code, 200, sessions.text)
        self.assertEqual(sessions.json()["sessions"][0]["sessionId"], session_id)
        self.assertEqual(sessions.json()["sessions"][0]["fileCount"], 3)

        root = self.client.get(
            f"/v1/library/sessions/{session_id}/files",
            headers=self.auth(),
        )
        self.assertEqual(root.status_code, 200, root.text)
        self.assertEqual(
            [(entry["name"], entry["type"]) for entry in root.json()["entries"]],
            [("camera", "DIRECTORY"), ("notes.txt", "FILE")],
        )
        camera = self.client.get(
            f"/v1/library/sessions/{session_id}/files",
            params={"path": "camera"},
            headers=self.auth(),
        )
        self.assertEqual(
            [entry["name"] for entry in camera.json()["entries"]],
            ["front.jpg", "rear.jpg"],
        )
        preview = self.client.get(
            f"/v1/library/sessions/{session_id}/file",
            params={"path": "camera/front.jpg"},
            headers=self.auth(),
        )
        self.assertEqual(preview.status_code, 200, preview.text)
        self.assertEqual(preview.content, b"jpeg-preview")
        self.assertEqual(preview.headers["content-type"], "image/jpeg")

        foreign = self.client.get(
            f"/v1/library/sessions/{session_id}/files",
            headers=self.auth(self.second_token),
        )
        self.assertEqual(foreign.status_code, 403)

    def test_library_preview_enforces_size_limit(self) -> None:
        body = b"x" * 17
        created = self.create(
            self.manifest([("large.bin", body)], client_job_id="8" * 32)
        )
        session_id = created.json()["sessionId"]
        self.assertEqual(self.put(session_id, "large.bin", body).status_code, 200)
        self.assertEqual(
            self.client.post(
                f"/v1/upload-sessions/{session_id}/complete",
                headers=self.auth(),
                json={},
            ).status_code,
            200,
        )
        response = self.client.get(
            f"/v1/library/sessions/{session_id}/file",
            params={"path": "large.bin"},
            headers=self.auth(),
        )
        self.assertEqual(response.status_code, 413)

    def test_library_file_listing_is_bounded(self) -> None:
        receiver = self.client.app.state.receiver
        device = receiver.authenticate(f"Bearer {self.token}")
        session_id = "library-limit"
        timestamp = "2026-08-13T00:00:00+00:00"
        file_rows = [
            (
                session_id,
                f"file-{index:03d}.txt",
                f"library-file-{index:03d}",
                0,
                EMPTY_SHA256,
                0,
                "VERIFIED",
                f"staging/{index:03d}",
                f"objects/{index:03d}",
            )
            for index in range(501)
        ]
        with receiver.database.connect() as connection:
            connection.execute(
                """
                INSERT INTO upload_sessions (
                    session_id, device_id, client_job_id, source_name, state,
                    total_bytes, file_count, manifest_hash, manifest_json,
                    failure_code, created_at, updated_at, completed_at
                ) VALUES (?, ?, ?, ?, 'COMPLETED', 0, ?, ?, '{}', NULL, ?, ?, ?)
                """,
                (
                    session_id,
                    device.device_id,
                    "library-limit-job",
                    "large-directory",
                    len(file_rows),
                    EMPTY_SHA256,
                    timestamp,
                    timestamp,
                    timestamp,
                ),
            )
            connection.executemany(
                """
                INSERT INTO upload_files (
                    session_id, relative_path, file_id, size_bytes, sha256,
                    next_offset, state, staging_key, final_key
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                file_rows,
            )

        response = self.client.get(
            f"/v1/library/sessions/{session_id}/files",
            headers=self.auth(),
        )
        self.assertEqual(response.status_code, 200, response.text)
        listing = response.json()
        self.assertTrue(listing["truncated"])
        self.assertEqual(len(listing["entries"]), 500)
        self.assertEqual(listing["entries"][0]["name"], "file-000.txt")
        self.assertEqual(listing["entries"][-1]["name"], "file-499.txt")

    def test_upload_resume_complete_and_idempotency(self) -> None:
        first = b"first chunk"
        second = "두 번째 + % 파일".encode("utf-8")
        files = [("camera/front image.bin", first), ("한글/%+ file.txt", second), ("empty", b"")]
        manifest = self.manifest(files)
        created = self.create(manifest)
        self.assertEqual(created.status_code, 201, created.text)
        session_id = created.json()["sessionId"]

        reordered = dict(manifest)
        reordered["files"] = list(reversed(manifest["files"]))
        repeated = self.create(reordered)
        self.assertEqual(repeated.status_code, 200, repeated.text)
        self.assertEqual(repeated.json()["sessionId"], session_id)

        response = self.put(session_id, files[0][0], first[:5], total=len(first))
        self.assertEqual(response.status_code, 200, response.text)
        self.assertEqual(response.json(), {"nextOffset": 5})
        offset = self.client.get(
            f"/v1/upload-sessions/{session_id}/files/offset",
            params={"path": files[0][0]},
            headers=self.auth(),
        )
        self.assertEqual(offset.json(), {"nextOffset": 5})
        duplicate = self.put(session_id, files[0][0], first[:5], total=len(first))
        self.assertEqual(duplicate.status_code, 409)
        response = self.put(
            session_id,
            files[0][0],
            first[5:],
            offset=5,
            total=len(first),
        )
        self.assertEqual(response.json(), {"nextOffset": len(first)})
        self.assertEqual(self.put(session_id, files[1][0], second).status_code, 200)

        completed = self.client.post(
            f"/v1/upload-sessions/{session_id}/complete",
            headers=self.auth(),
            json={},
        )
        self.assertEqual(completed.status_code, 200, completed.text)
        self.assertEqual(completed.json(), {"state": "COMPLETED"})
        repeated_completion = self.client.post(
            f"/v1/upload-sessions/{session_id}/complete",
            headers=self.auth(),
            json={},
        )
        self.assertEqual(repeated_completion.json(), {"state": "COMPLETED"})
        completed_offset = self.client.get(
            f"/v1/upload-sessions/{session_id}/files/offset",
            params={"path": files[0][0]},
            headers=self.auth(),
        )
        self.assertEqual(completed_offset.json(), {"nextOffset": len(first)})
        completed_manifest_retry = self.create(manifest)
        self.assertEqual(completed_manifest_retry.status_code, 200)
        self.assertEqual(completed_manifest_retry.json()["sessionId"], session_id)
        self.assertEqual(
            self.client.delete(
                f"/v1/upload-sessions/{session_id}", headers=self.auth()
            ).status_code,
            409,
        )

        final_directory = self.data_root / "storage" / "objects" / DEVICE_ID / session_id
        metadata = json.loads((final_directory / "manifest.json").read_text("utf-8"))
        self.assertEqual(metadata["deviceId"], DEVICE_ID)
        stored = {entry["path"]: entry for entry in metadata["files"]}
        for path, body in files:
            object_path = final_directory / stored[path]["storedObject"]
            self.assertEqual(object_path.read_bytes(), body)

    def test_batch_offsets_upload_and_idempotent_retry(self) -> None:
        files = [
            ("camera/front.bin", b"front-frame"),
            ("camera/한글.bin", b"rear-frame"),
            ("empty", b""),
        ]
        created = self.create(
            self.manifest(files),
            token=self.token,
        )
        self.assertEqual(created.status_code, 201, created.text)
        self.assertEqual(created.json()["fileBatch"]["version"], 1)
        session_id = created.json()["sessionId"]

        offsets = self.client.post(
            f"/v1/upload-sessions/{session_id}/files/offsets",
            headers={**self.auth(), "Content-Type": "application/json"},
            json={"paths": [path for path, _body in files]},
        )
        self.assertEqual(offsets.status_code, 200, offsets.text)
        self.assertEqual(
            {item["path"]: item["nextOffset"] for item in offsets.json()["files"]},
            {path: 0 for path, _body in files},
        )

        nonempty = files[:2]
        uploaded = self.put_batch(session_id, nonempty)
        self.assertEqual(uploaded.status_code, 200, uploaded.text)
        self.assertEqual(
            {item["path"]: item["nextOffset"] for item in uploaded.json()["files"]},
            {path: len(body) for path, body in nonempty},
        )
        repeated = self.put_batch(session_id, nonempty)
        self.assertEqual(repeated.status_code, 200, repeated.text)

        completed = self.client.post(
            f"/v1/upload-sessions/{session_id}/complete",
            headers=self.auth(),
            json={},
        )
        self.assertEqual(completed.status_code, 200, completed.text)

    def test_deferred_hashes_support_batch_and_resumed_chunks(self) -> None:
        files = [
            ("batch.bin", b"batched-content"),
            ("chunk.bin", b"first-half-second-half"),
            ("empty", b""),
        ]
        manifest = self.deferred_manifest(files)
        created = self.create(manifest)
        self.assertEqual(created.status_code, 201, created.text)
        session_id = created.json()["sessionId"]

        with sqlite3.connect(self.settings.database_path) as connection:
            initial_hashes = dict(
                connection.execute(
                    "SELECT relative_path, sha256 FROM upload_files WHERE session_id=?",
                    (session_id,),
                ).fetchall()
            )
        self.assertEqual(initial_hashes["batch.bin"], "")
        self.assertEqual(initial_hashes["chunk.bin"], "")
        self.assertEqual(initial_hashes["empty"], EMPTY_SHA256)

        uploaded = self.put_batch(session_id, [files[0]])
        self.assertEqual(uploaded.status_code, 200, uploaded.text)
        self.assertEqual(self.put_batch(session_id, [files[0]]).status_code, 200)

        chunk_body = files[1][1]
        split = len(chunk_body) // 2
        first = self.put(
            session_id,
            "chunk.bin",
            chunk_body[:split],
            total=len(chunk_body),
        )
        self.assertEqual(first.status_code, 200, first.text)

        restarted = ReceiverService(self.settings)
        device = restarted.authenticate(f"Bearer {self.token}")
        next_offset = restarted.put_chunk(
            device,
            session_id,
            "chunk.bin",
            split,
            f"bytes {split}-{len(chunk_body) - 1}/{len(chunk_body)}",
            hashlib.sha256(chunk_body[split:]).hexdigest(),
            chunk_body[split:],
        )
        self.assertEqual(next_offset, len(chunk_body))
        self.assertEqual(restarted.complete(device, session_id), "COMPLETED")

        with sqlite3.connect(self.settings.database_path) as connection:
            final_hashes = dict(
                connection.execute(
                    "SELECT relative_path, sha256 FROM upload_files WHERE session_id=?",
                    (session_id,),
                ).fetchall()
            )
        self.assertEqual(
            final_hashes,
            {path: hashlib.sha256(body).hexdigest() for path, body in files},
        )
        final_directory = self.data_root / "storage" / "objects" / DEVICE_ID / session_id
        metadata = json.loads((final_directory / "manifest.json").read_text("utf-8"))
        self.assertEqual(metadata["hashMode"], "deferred-v1")
        self.assertEqual(
            {item["path"]: item["sha256"] for item in metadata["files"]},
            final_hashes,
        )

    def test_batch_rejects_bad_hash_and_partial_file(self) -> None:
        body = b"part-one-part-two"
        session_id = self.create(
            self.manifest([("file.bin", body)], client_job_id="d" * 32)
        ).json()["sessionId"]
        bad_hash = self.put_batch(session_id, [("file.bin", body)], batch_hash="0" * 64)
        self.assertEqual(bad_hash.status_code, 422)

        split = len(body) // 2
        self.assertEqual(
            self.put(session_id, "file.bin", body[:split], total=len(body)).status_code,
            200,
        )
        partial = self.put_batch(session_id, [("file.bin", body)])
        self.assertEqual(partial.status_code, 409)

    def test_batch_rejects_duplicate_paths_and_trailing_data(self) -> None:
        content = b"contents"
        session_id = self.create(
            self.manifest([("file.bin", content)], client_job_id="e" * 32)
        ).json()["sessionId"]
        duplicate_body = self.batch_body(
            [("file.bin", content), ("file.bin", content)]
        )
        duplicate = self.client.put(
            f"/v1/upload-sessions/{session_id}/files/batch",
            headers={
                **self.auth(),
                "Content-Type": "application/vnd.jetson.upload-batch-v1",
                "X-Batch-SHA256": hashlib.sha256(duplicate_body).hexdigest(),
            },
            content=duplicate_body,
        )
        self.assertEqual(duplicate.status_code, 400)

        trailing_body = self.batch_body([("file.bin", content)]) + b"unexpected"
        trailing = self.client.put(
            f"/v1/upload-sessions/{session_id}/files/batch",
            headers={
                **self.auth(),
                "Content-Type": "application/vnd.jetson.upload-batch-v1",
                "X-Batch-SHA256": hashlib.sha256(trailing_body).hexdigest(),
            },
            content=trailing_body,
        )
        self.assertEqual(trailing.status_code, 400)

    def test_manifest_validation_and_idempotency_conflict(self) -> None:
        valid = self.manifest([("valid.bin", b"valid")])
        self.assertEqual(self.create(valid).status_code, 201)
        conflict = self.manifest([("other.bin", b"other")])
        self.assertEqual(self.create(conflict).status_code, 409)

        for path in ("../escape", "/absolute", "a//b", "a/./b", "a\\b"):
            invalid = self.manifest([(path, b"bad")], client_job_id=hashlib.md5(path.encode()).hexdigest())
            self.assertEqual(self.create(invalid).status_code, 400, path)
        duplicate = self.manifest([("same", b"1"), ("same", b"2")], client_job_id="1" * 32)
        self.assertEqual(self.create(duplicate).status_code, 400)
        unknown_hash_mode = self.deferred_manifest(
            [("file", b"x")],
            client_job_id="3" * 32,
        )
        unknown_hash_mode["hashMode"] = "future-mode"
        self.assertEqual(self.create(unknown_hash_mode).status_code, 400)
        mixed_hash_mode = self.deferred_manifest(
            [("file", b"x")],
            client_job_id="4" * 32,
        )
        mixed_hash_mode["files"][0]["sha256"] = hashlib.sha256(b"x").hexdigest()
        self.assertEqual(self.create(mixed_hash_mode).status_code, 400)
        wrong_device = self.manifest(
            [("file", b"x")],
            device_id=SECOND_DEVICE_ID,
            client_job_id="2" * 32,
        )
        self.assertEqual(self.create(wrong_device).status_code, 403)

        surrogate_source = (
            '{"deviceId":"%s","clientJobId":"%s",'
            '"sourceName":"\\ud800","files":[]}'
            % (DEVICE_ID, "9" * 32)
        ).encode("ascii")
        response = self.client.post(
            "/v1/upload-sessions",
            headers={**self.auth(), "Content-Type": "application/json"},
            content=surrogate_source,
        )
        self.assertEqual(response.status_code, 400)

        surrogate_path = (
            '{"deviceId":"%s","clientJobId":"%s",'
            '"sourceName":"valid","files":[{"path":"\\ud800",'
            '"sizeBytes":0,"sha256":"%s"}]}'
            % (DEVICE_ID, "a" * 32, EMPTY_SHA256)
        ).encode("ascii")
        response = self.client.post(
            "/v1/upload-sessions",
            headers={**self.auth(), "Content-Type": "application/json"},
            content=surrogate_path,
        )
        self.assertEqual(response.status_code, 400)

    def test_chunk_validation_hash_failure_and_failed_retry(self) -> None:
        body = b"expected contents"
        manifest = self.manifest([("file.bin", body)])
        session_id = self.create(manifest).json()["sessionId"]
        incomplete = self.client.post(
            f"/v1/upload-sessions/{session_id}/complete",
            headers=self.auth(),
            json={},
        )
        self.assertEqual(incomplete.status_code, 409)
        bad_chunk = self.put(
            session_id,
            "file.bin",
            b"X" * len(body),
            total=len(body),
        )
        self.assertEqual(bad_chunk.status_code, 422, bad_chunk.text)

        retried = self.create(manifest)
        self.assertEqual(retried.status_code, 200, retried.text)
        self.assertEqual(retried.json()["sessionId"], session_id)
        offset = self.client.get(
            f"/v1/upload-sessions/{session_id}/files/offset",
            params={"path": "file.bin"},
            headers=self.auth(),
        )
        self.assertEqual(offset.json(), {"nextOffset": 0})
        self.assertEqual(self.put(session_id, "file.bin", body).status_code, 200)

    def test_content_headers_size_limit_and_quota(self) -> None:
        manifest = self.manifest([("file.bin", b"abc")])
        session_id = self.create(manifest).json()["sessionId"]
        wrong_type = self.client.put(
            f"/v1/upload-sessions/{session_id}/files",
            params={"path": "file.bin", "offset": 0},
            headers={
                **self.auth(),
                "Content-Type": "text/plain",
                "Content-Range": "bytes 0-2/3",
                "X-Chunk-SHA256": hashlib.sha256(b"abc").hexdigest(),
            },
            content=b"abc",
        )
        self.assertEqual(wrong_type.status_code, 400)
        wrong_range = self.client.put(
            f"/v1/upload-sessions/{session_id}/files",
            params={"path": "file.bin", "offset": 0},
            headers={
                **self.auth(),
                "Content-Type": "application/octet-stream",
                "Content-Range": "bytes 1-3/3",
                "X-Chunk-SHA256": hashlib.sha256(b"abc").hexdigest(),
            },
            content=b"abc",
        )
        self.assertEqual(wrong_range.status_code, 400)
        oversized = self.put(
            session_id,
            "file.bin",
            b"x" * (self.settings.max_chunk_bytes + 1),
            total=self.settings.max_chunk_bytes + 1,
        )
        self.assertEqual(oversized.status_code, 413)

        administrator = ReceiverService(self.settings)
        administrator.issue_token(SECOND_DEVICE_ID, quota_bytes=2)
        quota_manifest = self.manifest(
            [("quota.bin", b"abc")],
            device_id=SECOND_DEVICE_ID,
            client_job_id="3" * 32,
        )
        self.assertEqual(
            self.create(quota_manifest, self.second_token).status_code,
            401,
        )
        rotated_token = administrator.issue_token(SECOND_DEVICE_ID, quota_bytes=2)
        self.assertEqual(self.create(quota_manifest, rotated_token).status_code, 413)

    def test_cancel_is_idempotent_and_enforces_ownership(self) -> None:
        manifest = self.manifest([("file.bin", b"contents")])
        session_id = self.create(manifest).json()["sessionId"]
        foreign = self.client.delete(
            f"/v1/upload-sessions/{session_id}", headers=self.auth(self.second_token)
        )
        self.assertEqual(foreign.status_code, 403)
        for _ in range(2):
            response = self.client.delete(
                f"/v1/upload-sessions/{session_id}", headers=self.auth()
            )
            self.assertEqual(response.json(), {"state": "CANCELLED"})
        self.assertFalse(
            (self.data_root / "storage" / "staging" / DEVICE_ID / session_id).exists()
        )

    def test_restart_truncates_uncommitted_tail(self) -> None:
        body = b"committed-and-uncommitted"
        manifest = self.manifest([("file.bin", body)])
        session_id = self.create(manifest).json()["sessionId"]
        committed = body[:9]
        self.assertEqual(
            self.put(session_id, "file.bin", committed, total=len(body)).status_code,
            200,
        )
        with sqlite3.connect(self.settings.database_path) as connection:
            staging_key = connection.execute(
                "SELECT staging_key FROM upload_files WHERE session_id=?",
                (session_id,),
            ).fetchone()[0]
        staging_path = self.data_root / staging_key
        with staging_path.open("ab") as output:
            output.write(b"uncommitted-tail")
            output.flush()
        ReceiverService(self.settings)
        self.assertEqual(staging_path.read_bytes(), committed)

    def test_restart_defers_full_hash_until_completion(self) -> None:
        body = b"first-half-second-half"
        manifest = self.manifest(
            [("file.bin", body)], client_job_id="4" * 32
        )
        session_id = self.create(manifest).json()["sessionId"]
        split = len(body) // 2
        self.assertEqual(
            self.put(session_id, "file.bin", body[:split], total=len(body)).status_code,
            200,
        )

        restarted = ReceiverService(self.settings)
        device = restarted.authenticate(f"Bearer {self.token}")
        next_offset = restarted.put_chunk(
            device,
            session_id,
            "file.bin",
            split,
            f"bytes {split}-{len(body) - 1}/{len(body)}",
            hashlib.sha256(body[split:]).hexdigest(),
            body[split:],
        )
        self.assertEqual(next_offset, len(body))
        with sqlite3.connect(self.settings.database_path) as connection:
            state = connection.execute(
                "SELECT state FROM upload_files WHERE session_id=?",
                (session_id,),
            ).fetchone()[0]
        self.assertEqual(state, "RECEIVED")
        self.assertEqual(restarted.complete(device, session_id), "COMPLETED")

    def test_completion_detects_same_size_staging_tampering(self) -> None:
        body = b"original-data"
        manifest = self.manifest(
            [("file.bin", body)], client_job_id="6" * 32
        )
        session_id = self.create(manifest).json()["sessionId"]
        self.assertEqual(self.put(session_id, "file.bin", body).status_code, 200)
        with sqlite3.connect(self.settings.database_path) as connection:
            staging_key = connection.execute(
                "SELECT staging_key FROM upload_files WHERE session_id=?",
                (session_id,),
            ).fetchone()[0]
        (self.data_root / staging_key).write_bytes(b"X" * len(body))
        completed = self.client.post(
            f"/v1/upload-sessions/{session_id}/complete",
            headers=self.auth(),
            json={},
        )
        self.assertEqual(completed.status_code, 422)

    def test_finalization_recovery_verifies_promoted_objects(self) -> None:
        body = b"finalized-data"
        manifest = self.manifest(
            [("file.bin", body)], client_job_id="b" * 32
        )
        session_id = self.create(manifest).json()["sessionId"]
        self.assertEqual(self.put(session_id, "file.bin", body).status_code, 200)
        completed = self.client.post(
            f"/v1/upload-sessions/{session_id}/complete",
            headers=self.auth(),
            json={},
        )
        self.assertEqual(completed.status_code, 200)
        final_directory = self.data_root / "storage" / "objects" / DEVICE_ID / session_id
        metadata = json.loads((final_directory / "manifest.json").read_text("utf-8"))
        (final_directory / metadata["files"][0]["storedObject"]).write_bytes(
            b"X" * len(body)
        )
        with sqlite3.connect(self.settings.database_path) as connection:
            connection.execute(
                """
                UPDATE upload_sessions
                SET state='FINALIZING', completed_at=NULL
                WHERE session_id=?
                """,
                (session_id,),
            )
            connection.commit()

        ReceiverService(self.settings)
        with sqlite3.connect(self.settings.database_path) as connection:
            state, failure_code = connection.execute(
                "SELECT state, failure_code FROM upload_sessions WHERE session_id=?",
                (session_id,),
            ).fetchone()
        self.assertEqual(state, "FAILED")
        self.assertEqual(failure_code, "finalization_recovery_failed")

        retried = self.create(manifest)
        self.assertEqual(retried.status_code, 200, retried.text)
        self.assertEqual(retried.json()["sessionId"], session_id)
        self.assertFalse(final_directory.exists())

    def test_finalization_recovery_preserves_data_on_transient_io_error(self) -> None:
        receiver: ReceiverService = self.client.app.state.receiver
        manifest = self.manifest(
            [("file.bin", b"transient")], client_job_id="1" * 31 + "c"
        )
        session_id = self.create(manifest).json()["sessionId"]
        with sqlite3.connect(self.settings.database_path) as connection:
            connection.execute(
                "UPDATE upload_sessions SET state='FINALIZING' WHERE session_id=?",
                (session_id,),
            )
            connection.commit()
        staging = self.data_root / "storage/staging" / DEVICE_ID / session_id
        with patch.object(
            ReceiverService,
            "_finish_finalizing",
            side_effect=OSError(5, "simulated I/O error"),
        ):
            ReceiverService(self.settings)
        with sqlite3.connect(self.settings.database_path) as connection:
            state, failure_code = connection.execute(
                "SELECT state, failure_code FROM upload_sessions WHERE session_id=?",
                (session_id,),
            ).fetchone()
        self.assertEqual(state, "FINALIZING")
        self.assertIsNone(failure_code)
        self.assertTrue(staging.exists())

    def test_normal_completion_hashes_staging_once(self) -> None:
        body = b"hash-once"
        manifest = self.manifest(
            [("file.bin", body)], client_job_id="c" * 32
        )
        session_id = self.create(manifest).json()["sessionId"]
        self.assertEqual(self.put(session_id, "file.bin", body).status_code, 200)
        receiver: ReceiverService = self.client.app.state.receiver
        original = receiver._verify_staged_files
        calls = []

        def counted(value: str) -> None:
            calls.append(value)
            original(value)

        receiver._verify_staged_files = counted
        completed = self.client.post(
            f"/v1/upload-sessions/{session_id}/complete",
            headers=self.auth(),
            json={},
        )
        self.assertEqual(completed.status_code, 200)
        self.assertEqual(calls, [session_id])

    def test_missing_staging_or_final_object_marks_session_failed(self) -> None:
        body = b"missing-object"
        staging_manifest = self.manifest(
            [("staging.bin", body)], client_job_id="1" * 31 + "a"
        )
        staging_session = self.create(staging_manifest).json()["sessionId"]
        self.assertEqual(
            self.put(staging_session, "staging.bin", body).status_code,
            200,
        )
        with sqlite3.connect(self.settings.database_path) as connection:
            staging_key = connection.execute(
                "SELECT staging_key FROM upload_files WHERE session_id=?",
                (staging_session,),
            ).fetchone()[0]
        (self.data_root / staging_key).unlink()
        completed = self.client.post(
            f"/v1/upload-sessions/{staging_session}/complete",
            headers=self.auth(),
            json={},
        )
        self.assertEqual(completed.status_code, 503)
        with sqlite3.connect(self.settings.database_path) as connection:
            state = connection.execute(
                "SELECT state FROM upload_sessions WHERE session_id=?",
                (staging_session,),
            ).fetchone()[0]
        self.assertEqual(state, "FAILED")

        final_manifest = self.manifest(
            [("final.bin", body)], client_job_id="1" * 31 + "b"
        )
        final_session = self.create(final_manifest).json()["sessionId"]
        self.assertEqual(self.put(final_session, "final.bin", body).status_code, 200)
        completed = self.client.post(
            f"/v1/upload-sessions/{final_session}/complete",
            headers=self.auth(),
            json={},
        )
        self.assertEqual(completed.status_code, 200)
        final_directory = self.data_root / "storage" / "objects" / DEVICE_ID / final_session
        final_metadata = json.loads((final_directory / "manifest.json").read_text("utf-8"))
        (final_directory / final_metadata["files"][0]["storedObject"]).unlink()
        with sqlite3.connect(self.settings.database_path) as connection:
            connection.execute(
                "UPDATE upload_sessions SET state='FINALIZING' WHERE session_id=?",
                (final_session,),
            )
            connection.commit()
        completed = self.client.post(
            f"/v1/upload-sessions/{final_session}/complete",
            headers=self.auth(),
            json={},
        )
        self.assertEqual(completed.status_code, 503)
        with sqlite3.connect(self.settings.database_path) as connection:
            state = connection.execute(
                "SELECT state FROM upload_sessions WHERE session_id=?",
                (final_session,),
            ).fetchone()[0]
        self.assertEqual(state, "FAILED")

    def test_failed_staging_counts_against_quota_until_cleanup(self) -> None:
        receiver: ReceiverService = self.client.app.state.receiver
        quota_token = receiver.issue_token(SECOND_DEVICE_ID, quota_bytes=5)
        failed_manifest = self.manifest(
            [("bad.bin", b"12345")],
            device_id=SECOND_DEVICE_ID,
            client_job_id="7" * 32,
        )
        session_id = self.create(failed_manifest, quota_token).json()["sessionId"]
        failed = self.put(
            session_id,
            "bad.bin",
            b"XXXXX",
            token=quota_token,
        )
        self.assertEqual(failed.status_code, 422)
        next_manifest = self.manifest(
            [("next.bin", b"1")],
            device_id=SECOND_DEVICE_ID,
            client_job_id="8" * 32,
        )
        self.assertEqual(self.create(next_manifest, quota_token).status_code, 413)

        with sqlite3.connect(self.settings.database_path) as connection:
            connection.execute(
                """
                UPDATE upload_sessions SET updated_at='2000-01-01T00:00:00Z'
                WHERE session_id=?
                """,
                (session_id,),
            )
            connection.commit()
        self.assertEqual(receiver.cleanup_staging(older_than_hours=72), 1)
        with sqlite3.connect(self.settings.database_path) as connection:
            self.assertIsNone(
                connection.execute(
                    "SELECT 1 FROM upload_sessions WHERE session_id=?", (session_id,)
                ).fetchone()
            )
        self.assertEqual(self.create(next_manifest, quota_token).status_code, 201)

    def test_manifest_rate_and_active_session_limits(self) -> None:
        repeated = self.manifest([], client_job_id="d" * 32)
        for index in range(self.settings.max_manifest_requests_per_minute):
            response = self.create(repeated)
            self.assertEqual(response.status_code, 201 if index == 0 else 200)
        self.assertEqual(self.create(repeated).status_code, 429)

        receiver: ReceiverService = self.client.app.state.receiver
        receiver._manifest_requests.clear()
        for index in range(1, self.settings.max_active_sessions_per_device):
            response = self.create(
                self.manifest([], client_job_id=f"{index:032x}")
            )
            self.assertEqual(response.status_code, 201, response.text)
        response = self.create(self.manifest([], client_job_id="e" * 32))
        self.assertEqual(response.status_code, 429)

    def test_failed_reactivation_respects_active_session_limit(self) -> None:
        failed_manifest = self.manifest(
            [("bad.bin", b"expected")], client_job_id="f" * 32
        )
        failed_session = self.create(failed_manifest).json()["sessionId"]
        self.assertEqual(
            self.put(failed_session, "bad.bin", b"XXXXXXXX").status_code,
            422,
        )
        for index in range(self.settings.max_active_sessions_per_device):
            response = self.create(
                self.manifest([], client_job_id=f"{index + 100:032x}")
            )
            self.assertEqual(response.status_code, 201, response.text)
        reactivated = self.create(failed_manifest)
        self.assertEqual(reactivated.status_code, 429)

    def test_readiness_probe_is_cached(self) -> None:
        receiver: ReceiverService = self.client.app.state.receiver
        calls = []
        original = receiver._probe_readiness

        def counted() -> bool:
            calls.append(True)
            return original()

        receiver._probe_readiness = counted
        self.assertTrue(receiver.health_ready())
        self.assertTrue(receiver.health_ready())
        self.assertEqual(len(calls), 1)

    def test_concurrent_duplicate_chunk_is_serialized(self) -> None:
        body = b"concurrent-body"
        manifest = self.manifest(
            [("file.bin", body)], client_job_id="5" * 32
        )
        session_id = self.create(manifest).json()["sessionId"]
        receiver: ReceiverService = self.client.app.state.receiver
        device = receiver.authenticate(f"Bearer {self.token}")

        def upload_once():
            try:
                return receiver.put_chunk(
                    device,
                    session_id,
                    "file.bin",
                    0,
                    f"bytes 0-{len(body) - 1}/{len(body)}",
                    hashlib.sha256(body).hexdigest(),
                    body,
                )
            except ReceiverError as error:
                return error.status

        with ThreadPoolExecutor(max_workers=2) as executor:
            results = list(executor.map(lambda _value: upload_once(), range(2)))
        self.assertCountEqual(results, [len(body), 409])

    def test_token_is_not_stored_in_plaintext(self) -> None:
        database_bytes = self.settings.database_path.read_bytes()
        self.assertNotIn(self.token.encode("utf-8"), database_bytes)
        with sqlite3.connect(self.settings.database_path) as connection:
            digest = connection.execute(
                "SELECT token_digest FROM devices WHERE device_id=?", (DEVICE_ID,)
            ).fetchone()[0]
        self.assertEqual(len(digest), 64)
        self.assertNotEqual(digest, self.token)


class SecretFileTest(unittest.TestCase):
    def test_force_write_is_atomic_private_and_does_not_follow_symlink(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            token_path = root / "receiver.token"
            token_path.write_text("old\n", encoding="utf-8")
            token_path.chmod(0o644)
            _write_secret(token_path, "new", force=True)
            self.assertEqual(token_path.read_text("utf-8"), "new\n")
            self.assertEqual(os.stat(token_path).st_mode & 0o777, 0o600)

            victim = root / "victim"
            victim.write_text("unchanged", encoding="utf-8")
            token_path.unlink()
            token_path.symlink_to(victim)
            _write_secret(token_path, "replacement", force=True)
            self.assertFalse(token_path.is_symlink())
            self.assertEqual(token_path.read_text("utf-8"), "replacement\n")
            self.assertEqual(victim.read_text("utf-8"), "unchanged")

    def test_non_force_write_never_overwrites_existing_file(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            token_path = Path(temporary) / "receiver.token"
            token_path.write_text("existing\n", encoding="utf-8")
            with self.assertRaises(FileExistsError):
                _write_secret(token_path, "new", force=False)
            self.assertEqual(token_path.read_text("utf-8"), "existing\n")

    def test_staging_failure_removes_token_temporary_file(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            token_path = Path(temporary) / "receiver.token"
            original_write = os.write

            def failed_write(descriptor: int, body: bytes) -> int:
                if b"token-value" in body:
                    raise OSError("simulated token write failure")
                return original_write(descriptor, body)

            with patch("upload_receiver.admin.os.write", side_effect=failed_write):
                with self.assertRaises(OSError):
                    admin._stage_secret(token_path, "token-value", force=False)
            self.assertEqual(list(token_path.parent.glob(".receiver.token.*.tmp")), [])

    def test_activation_failure_keeps_existing_published_token(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            data_root = root / "receiver"
            settings = Settings(
                data_root=data_root,
                expected_mount=None,
                require_mount=False,
            )
            receiver = ReceiverService(settings, create_pepper=True)
            token_path = root / "device.token"
            _write_secret(token_path, "old-token", force=True)
            destination, staged = admin._stage_secret(
                token_path,
                "new-token",
                force=True,
            )
            with patch.object(
                receiver,
                "activate_token",
                side_effect=sqlite3.OperationalError("simulated DB failure"),
            ):
                with self.assertRaises(sqlite3.OperationalError):
                    try:
                        receiver.activate_token(
                            DEVICE_ID,
                            "new-token",
                            quota_bytes=1024,
                        )
                    finally:
                        staged.unlink(missing_ok=True)
            self.assertEqual(destination.read_text("utf-8"), "old-token\n")


class MountGuardTest(unittest.TestCase):
    def test_runtime_mount_loss_returns_service_unavailable(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            expected_mount = Path(temporary) / "hdd"
            expected_mount.mkdir()
            settings = Settings(
                data_root=expected_mount / "receiver",
                expected_mount=expected_mount,
                require_mount=True,
            )
            with patch.object(Path, "is_mount", return_value=True):
                receiver = ReceiverService(settings, create_pepper=True)
            with patch.object(Path, "is_mount", return_value=False):
                with self.assertRaises(ReceiverError) as raised:
                    receiver.ensure_storage_available()
            self.assertEqual(raised.exception.status, 503)


if __name__ == "__main__":
    unittest.main()
