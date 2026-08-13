from __future__ import annotations

import errno
import fcntl
import hashlib
import hmac
import json
import os
import posixpath
import re
import secrets
import shutil
import sqlite3
import stat
import struct
import threading
import time
import unicodedata
import uuid
from collections import deque
from contextlib import contextmanager, nullcontext
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Deque, Dict, Iterator, Mapping

from .config import Settings
from .database import Database


SESSION_ID_PATTERN = re.compile(r"^[A-Za-z0-9][A-Za-z0-9_.-]{0,63}$")
CLIENT_JOB_ID_PATTERN = re.compile(r"^[a-f0-9]{32}$")
SHA256_PATTERN = re.compile(r"^[a-f0-9]{64}$")
CONTENT_RANGE_PATTERN = re.compile(r"^bytes ([0-9]+)-([0-9]+)/([0-9]+)$")
EMPTY_SHA256 = hashlib.sha256(b"").hexdigest()
FILE_BATCH_MAGIC = b"JETSONBATCH1\n"


class ReceiverError(Exception):
    def __init__(self, status: int, detail: str, *, retry_after: int | None = None) -> None:
        super().__init__(detail)
        self.status = status
        self.detail = detail
        self.retry_after = retry_after


@dataclass(frozen=True)
class Device:
    device_id: str
    quota_bytes: int


@dataclass(frozen=True)
class ManifestFile:
    path: str
    size_bytes: int
    sha256: str
    file_id: str


@dataclass(frozen=True)
class Manifest:
    device_id: str
    client_job_id: str
    source_name: str
    files: tuple[ManifestFile, ...]
    total_bytes: int
    canonical_json: str
    digest: str


@dataclass(frozen=True)
class BatchFile:
    path: str
    body: bytes


class ReceiverService:
    def __init__(self, settings: Settings, *, create_pepper: bool = False) -> None:
        self.settings = settings
        settings.prepare(create_pepper=create_pepper)
        self.database = Database(settings.database_path)
        self.database.initialize()
        self._expected_mount_device = (
            settings.expected_mount.stat().st_dev
            if settings.require_mount and settings.expected_mount is not None
            else None
        )
        self._pepper = settings.pepper_path.read_bytes()
        self._thread_locks: Dict[str, threading.RLock] = {}
        self._thread_locks_guard = threading.Lock()
        self._hasher_cache: Dict[tuple[str, str], tuple[int, object]] = {}
        self._put_counts: Dict[str, int] = {}
        self._put_counts_guard = threading.Lock()
        self._manifest_requests: Dict[str, Deque[float]] = {}
        self._manifest_requests_guard = threading.Lock()
        self._readiness_cache: tuple[float, bool] | None = None
        self._readiness_guard = threading.Lock()
        self.recover()

    @staticmethod
    def timestamp() -> str:
        return datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")

    def _token_digest(self, token: str) -> str:
        return hmac.new(self._pepper, token.encode("utf-8"), hashlib.sha256).hexdigest()

    def issue_token(
        self,
        device_id: str,
        *,
        quota_bytes: int,
        expires_at: str | None = None,
    ) -> str:
        self.ensure_storage_available()
        canonical_device_id = self.validate_token_configuration(
            device_id,
            quota_bytes=quota_bytes,
            expires_at=expires_at,
        )
        token = self.generate_token()
        self.activate_token(
            canonical_device_id,
            token,
            quota_bytes=quota_bytes,
            expires_at=expires_at,
        )
        return token

    @staticmethod
    def generate_token() -> str:
        return secrets.token_urlsafe(48)

    def activate_token(
        self,
        device_id: str,
        token: str,
        *,
        quota_bytes: int,
        expires_at: str | None = None,
    ) -> None:
        self.ensure_storage_available()
        canonical_device_id = self.validate_token_configuration(
            device_id,
            quota_bytes=quota_bytes,
            expires_at=expires_at,
        )
        if not token or len(token) > 4096 or any(character.isspace() for character in token):
            raise ValueError("token is invalid")
        digest = self._token_digest(token)
        now = self.timestamp()
        with self.database.immediate() as connection:
            connection.execute(
                """
                INSERT INTO devices(
                    device_id, token_digest, enabled, quota_bytes,
                    token_expires_at, created_at, updated_at
                ) VALUES (?, ?, 1, ?, ?, ?, ?)
                ON CONFLICT(device_id) DO UPDATE SET
                    token_digest=excluded.token_digest,
                    enabled=1,
                    quota_bytes=excluded.quota_bytes,
                    token_expires_at=excluded.token_expires_at,
                    updated_at=excluded.updated_at
                """,
                (
                    canonical_device_id,
                    digest,
                    quota_bytes,
                    expires_at,
                    now,
                    now,
                ),
            )

    def validate_token_configuration(
        self,
        device_id: str,
        *,
        quota_bytes: int,
        expires_at: str | None,
    ) -> str:
        canonical_device_id = self.validate_device_id(device_id)
        if quota_bytes < 1:
            raise ValueError("quota_bytes must be positive")
        if expires_at is not None:
            self._parse_timestamp(expires_at)
        return canonical_device_id

    def disable_device(self, device_id: str) -> None:
        self.ensure_storage_available()
        canonical_device_id = self.validate_device_id(device_id)
        with self.database.immediate() as connection:
            cursor = connection.execute(
                "UPDATE devices SET enabled=0, updated_at=? WHERE device_id=?",
                (self.timestamp(), canonical_device_id),
            )
            if cursor.rowcount != 1:
                raise ValueError("Device was not found")

    def cleanup_staging(self, *, older_than_hours: int) -> int:
        self.ensure_storage_available()
        if older_than_hours < 1:
            raise ValueError("older_than_hours must be positive")
        cutoff = datetime.now(timezone.utc).timestamp() - older_than_hours * 60 * 60
        with self.database.connect() as connection:
            rows = connection.execute(
                """
                SELECT session_id, device_id, state, updated_at
                FROM upload_sessions
                WHERE state IN ('OPEN', 'FAILED', 'CANCELLED')
                """
            ).fetchall()
        removed = 0
        for row in rows:
            try:
                updated_at = self._parse_timestamp(row["updated_at"]).timestamp()
            except ValueError:
                continue
            if updated_at > cutoff:
                continue
            session_id = str(row["session_id"])
            with self._guard(f"session:{session_id}"):
                with self.database.connect() as connection:
                    current = connection.execute(
                        """
                        SELECT state, updated_at FROM upload_sessions WHERE session_id=?
                        """,
                        (session_id,),
                    ).fetchone()
                if current is None or current["state"] not in {
                    "OPEN",
                    "FAILED",
                    "CANCELLED",
                }:
                    continue
                if self._parse_timestamp(current["updated_at"]).timestamp() > cutoff:
                    continue
                if current["state"] == "OPEN":
                    self._mark_failed(session_id, "staging_expired")
                self._remove_tree(
                    self._staging_directory(str(row["device_id"]), session_id)
                )
                with self.database.immediate() as connection:
                    connection.execute(
                        """
                        DELETE FROM upload_sessions
                        WHERE session_id=? AND state IN ('FAILED', 'CANCELLED')
                        """,
                        (session_id,),
                    )
                self._drop_session_hashers(session_id)
                removed += 1
        return removed

    def authenticate(self, authorization: str | None) -> Device:
        self.ensure_storage_available()
        if not authorization or not authorization.startswith("Bearer "):
            raise ReceiverError(401, "Authentication failed")
        token = authorization[7:]
        if not token or len(token) > 4096 or any(character.isspace() for character in token):
            raise ReceiverError(401, "Authentication failed")
        digest = self._token_digest(token)
        with self.database.connect() as connection:
            row = connection.execute(
                """
                SELECT device_id, enabled, quota_bytes, token_expires_at
                FROM devices WHERE token_digest=?
                """,
                (digest,),
            ).fetchone()
        if row is None or not row["enabled"]:
            raise ReceiverError(401, "Authentication failed")
        if row["token_expires_at"] is not None:
            expires_at = self._parse_timestamp(row["token_expires_at"])
            if expires_at <= datetime.now(timezone.utc):
                raise ReceiverError(401, "Authentication failed")
        return Device(row["device_id"], row["quota_bytes"])

    def reserve_manifest_request(self, device: Device) -> None:
        now = time.monotonic()
        cutoff = now - 60.0
        with self._manifest_requests_guard:
            requests = self._manifest_requests.setdefault(device.device_id, deque())
            while requests and requests[0] <= cutoff:
                requests.popleft()
            if len(requests) >= self.settings.max_manifest_requests_per_minute:
                raise ReceiverError(
                    429,
                    "Device is creating upload sessions too quickly",
                    retry_after=60,
                )
            requests.append(now)

    def parse_manifest(self, value: object) -> Manifest:
        if not isinstance(value, dict):
            raise ReceiverError(400, "Manifest must be a JSON object")
        try:
            device_value = value["deviceId"]
            client_value = value["clientJobId"]
            source_value = value["sourceName"]
            files_value = value["files"]
        except KeyError as error:
            raise ReceiverError(400, "Manifest is missing a required field") from error

        if not isinstance(device_value, str):
            raise ReceiverError(400, "deviceId is invalid")
        device_id = self.validate_device_id(device_value)
        if not isinstance(client_value, str) or not CLIENT_JOB_ID_PATTERN.fullmatch(
            client_value
        ):
            raise ReceiverError(400, "clientJobId is invalid")
        if not isinstance(source_value, str):
            raise ReceiverError(400, "sourceName is invalid")
        source_name = unicodedata.normalize("NFC", source_value)
        try:
            source_name_bytes = source_name.encode("utf-8")
        except UnicodeEncodeError as error:
            raise ReceiverError(400, "sourceName is invalid") from error
        if (
            source_name != source_value
            or not source_name
            or len(source_name_bytes) > 1024
            or any(ord(character) < 32 or ord(character) == 127 for character in source_name)
        ):
            raise ReceiverError(400, "sourceName is invalid")
        if not isinstance(files_value, list):
            raise ReceiverError(400, "files must be an array")
        if len(files_value) > self.settings.max_files_per_session:
            raise ReceiverError(413, "Session contains too many files")

        files = []
        seen_paths = set()
        total_bytes = 0
        for entry in files_value:
            if not isinstance(entry, dict):
                raise ReceiverError(400, "Manifest file entry is invalid")
            path_value = entry.get("path")
            size_value = entry.get("sizeBytes")
            sha_value = entry.get("sha256")
            if not isinstance(path_value, str):
                raise ReceiverError(400, "File path is invalid")
            relative_path = self.validate_relative_path(path_value)
            if relative_path in seen_paths:
                raise ReceiverError(400, "Manifest contains a duplicate file path")
            seen_paths.add(relative_path)
            if isinstance(size_value, bool) or not isinstance(size_value, int) or size_value < 0:
                raise ReceiverError(400, "File size is invalid")
            if size_value > self.settings.max_session_bytes:
                raise ReceiverError(413, "File is larger than the session limit")
            if not isinstance(sha_value, str) or not SHA256_PATTERN.fullmatch(sha_value):
                raise ReceiverError(400, "File SHA-256 is invalid")
            if size_value == 0 and sha_value != EMPTY_SHA256:
                raise ReceiverError(422, "Empty file SHA-256 does not match")
            total_bytes += size_value
            if total_bytes > self.settings.max_session_bytes:
                raise ReceiverError(413, "Session is larger than the configured limit")
            files.append(
                ManifestFile(
                    path=relative_path,
                    size_bytes=size_value,
                    sha256=sha_value,
                    file_id=uuid.uuid4().hex,
                )
            )

        canonical_value = {
            "deviceId": device_id,
            "clientJobId": client_value,
            "sourceName": source_name,
            "files": [
                {"path": item.path, "sizeBytes": item.size_bytes, "sha256": item.sha256}
                for item in sorted(files, key=lambda item: item.path.encode("utf-8"))
            ],
        }
        canonical_json = json.dumps(
            canonical_value,
            ensure_ascii=False,
            sort_keys=True,
            separators=(",", ":"),
        )
        return Manifest(
            device_id=device_id,
            client_job_id=client_value,
            source_name=source_name,
            files=tuple(files),
            total_bytes=total_bytes,
            canonical_json=canonical_json,
            digest=hashlib.sha256(canonical_json.encode("utf-8")).hexdigest(),
        )

    def create_session(self, device: Device, manifest: Manifest) -> tuple[str, int]:
        self.ensure_storage_available()
        if manifest.device_id != device.device_id:
            raise ReceiverError(403, "Manifest device does not match the token")
        guard_key = f"job:{device.device_id}:{manifest.client_job_id}"
        reactivate_session_id: str | None = None
        with self._guard(guard_key):
            with self.database.connect() as connection:
                existing = connection.execute(
                    """
                    SELECT session_id, manifest_hash, state
                    FROM upload_sessions
                    WHERE device_id=? AND client_job_id=?
                    """,
                    (device.device_id, manifest.client_job_id),
                ).fetchone()
            if existing is not None:
                if not hmac.compare_digest(existing["manifest_hash"], manifest.digest):
                    raise ReceiverError(409, "clientJobId is already used by another manifest")
                if existing["state"] == "FAILED":
                    reactivate_session_id = str(existing["session_id"])
                else:
                    return str(existing["session_id"]), 200

            if reactivate_session_id is None:
                session_id = str(uuid.uuid4())
                staging_directory = self._staging_directory(device.device_id, session_id)
                staging_directory.mkdir(parents=True, exist_ok=False, mode=0o700)
                self._fsync_directory(staging_directory.parent)
                now = self.timestamp()
                try:
                    with self.database.immediate() as connection:
                        self._enforce_quota(connection, device, manifest.total_bytes)
                        self._enforce_metadata_limits(
                            connection,
                            device,
                            requested_files=len(manifest.files),
                        )
                        connection.execute(
                            """
                            INSERT INTO upload_sessions(
                                session_id, device_id, client_job_id, source_name, state,
                                total_bytes, file_count, manifest_hash, manifest_json,
                                created_at, updated_at
                            ) VALUES (?, ?, ?, ?, 'OPEN', ?, ?, ?, ?, ?, ?)
                            """,
                            (
                                session_id,
                                device.device_id,
                                manifest.client_job_id,
                                manifest.source_name,
                                manifest.total_bytes,
                                len(manifest.files),
                                manifest.digest,
                                manifest.canonical_json,
                                now,
                                now,
                            ),
                        )
                        for item in manifest.files:
                            staging_key = self._staging_key(
                                device.device_id, session_id, item.file_id
                            )
                            final_key = self._final_key(
                                device.device_id, session_id, item.file_id
                            )
                            state = "VERIFIED" if item.size_bytes == 0 else "PENDING"
                            connection.execute(
                                """
                                INSERT INTO upload_files(
                                    session_id, relative_path, file_id, size_bytes, sha256,
                                    next_offset, state, staging_key, final_key
                                ) VALUES (?, ?, ?, ?, ?, 0, ?, ?, ?)
                                """,
                                (
                                    session_id,
                                    item.path,
                                    item.file_id,
                                    item.size_bytes,
                                    item.sha256,
                                    state,
                                    staging_key,
                                    final_key,
                                ),
                            )
                            if item.size_bytes == 0:
                                self._create_empty_file(self.settings.data_root / staging_key)
                except Exception:
                    self._remove_tree(staging_directory)
                    raise
                return session_id, 201

        try:
            self._reactivate_failed_session(device, reactivate_session_id)
        except ReceiverError as error:
            if error.status == 404:
                return self.create_session(device, manifest)
            raise
        return reactivate_session_id, 200

    def get_offset(self, device: Device, session_id: str, relative_path: str) -> int:
        self.ensure_storage_available()
        session_id = self.validate_session_id(session_id)
        relative_path = self.validate_relative_path(relative_path)
        with self.database.connect() as connection:
            session = self._owned_session(connection, device, session_id)
            if session["state"] in {"FAILED", "CANCELLED"}:
                raise ReceiverError(409, "Upload session is not open")
            row = connection.execute(
                """
                SELECT next_offset FROM upload_files
                WHERE session_id=? AND relative_path=?
                """,
                (session_id, relative_path),
            ).fetchone()
        if row is None:
            raise ReceiverError(404, "Upload file was not found")
        return int(row["next_offset"])

    def get_offsets(
        self,
        device: Device,
        session_id: str,
        relative_paths: list[str],
    ) -> Dict[str, int]:
        self.ensure_storage_available()
        session_id = self.validate_session_id(session_id)
        if not relative_paths or len(relative_paths) > self.settings.max_batch_files:
            raise ReceiverError(400, "Batch offset request has an invalid file count")
        normalized = [self.validate_relative_path(path) for path in relative_paths]
        if len(set(normalized)) != len(normalized):
            raise ReceiverError(400, "Batch offset request contains duplicate paths")

        offsets: Dict[str, int] = {}
        with self.database.connect() as connection:
            session = self._owned_session(connection, device, session_id)
            if session["state"] in {"FAILED", "CANCELLED"}:
                raise ReceiverError(409, "Upload session is not open")
            for relative_path in normalized:
                row = connection.execute(
                    """
                    SELECT next_offset FROM upload_files
                    WHERE session_id=? AND relative_path=?
                    """,
                    (session_id, relative_path),
                ).fetchone()
                if row is None:
                    raise ReceiverError(404, "Upload file was not found")
                offsets[relative_path] = int(row["next_offset"])
        return offsets

    def parse_file_batch(self, body: bytes) -> tuple[BatchFile, ...]:
        if len(body) > self.settings.max_batch_bytes:
            raise ReceiverError(413, "File batch is larger than the configured limit")
        header_size = len(FILE_BATCH_MAGIC) + 4
        if len(body) < header_size or not body.startswith(FILE_BATCH_MAGIC):
            raise ReceiverError(400, "File batch header is invalid")
        cursor = len(FILE_BATCH_MAGIC)
        file_count = struct.unpack_from(">I", body, cursor)[0]
        cursor += 4
        if file_count < 1 or file_count > self.settings.max_batch_files:
            raise ReceiverError(400, "File batch has an invalid file count")

        files = []
        seen_paths = set()
        for _index in range(file_count):
            if cursor + 12 > len(body):
                raise ReceiverError(400, "File batch entry is truncated")
            path_size, content_size = struct.unpack_from(">IQ", body, cursor)
            cursor += 12
            if path_size < 1 or path_size > 4096 or cursor + path_size > len(body):
                raise ReceiverError(400, "File batch path is invalid")
            try:
                path = body[cursor : cursor + path_size].decode("utf-8")
            except UnicodeDecodeError as error:
                raise ReceiverError(400, "File batch path is invalid") from error
            cursor += path_size
            path = self.validate_relative_path(path)
            if path in seen_paths:
                raise ReceiverError(400, "File batch contains a duplicate path")
            seen_paths.add(path)
            if content_size > self.settings.max_batch_bytes or cursor + content_size > len(body):
                raise ReceiverError(400, "File batch content is truncated")
            files.append(
                BatchFile(path=path, body=body[cursor : cursor + content_size])
            )
            cursor += content_size
        if cursor != len(body):
            raise ReceiverError(400, "File batch has trailing data")
        return tuple(files)

    def put_file_batch(
        self,
        device: Device,
        session_id: str,
        files: tuple[BatchFile, ...],
        *,
        slot_reserved: bool = False,
    ) -> Dict[str, int]:
        self.ensure_storage_available()
        session_id = self.validate_session_id(session_id)
        if not files or len(files) > self.settings.max_batch_files:
            raise ReceiverError(400, "File batch has an invalid file count")

        slot = nullcontext() if slot_reserved else self._put_slot(device.device_id)
        with slot, self._guard(f"session:{session_id}"):
            rows = []
            with self.database.connect() as connection:
                session = self._owned_session(connection, device, session_id)
                if session["state"] != "OPEN":
                    raise ReceiverError(409, "Upload session is not open")
                for item in files:
                    row = connection.execute(
                        "SELECT * FROM upload_files WHERE session_id=? AND relative_path=?",
                        (session_id, item.path),
                    ).fetchone()
                    if row is None:
                        raise ReceiverError(404, "Upload file was not found")
                    next_offset = int(row["next_offset"])
                    size_bytes = int(row["size_bytes"])
                    if next_offset not in {0, size_bytes}:
                        raise ReceiverError(
                            409,
                            "Partially uploaded files must use resumable chunks",
                        )
                    if len(item.body) != size_bytes:
                        raise ReceiverError(400, "File batch size does not match the manifest")
                    if not hmac.compare_digest(
                        hashlib.sha256(item.body).hexdigest(),
                        row["sha256"],
                    ):
                        raise ReceiverError(422, "File batch SHA-256 does not match")
                    rows.append((item, row, next_offset, size_bytes))

            pending = [entry for entry in rows if entry[2] == 0 and entry[3] > 0]
            for item, row, _next_offset, _size_bytes in pending:
                staging_path = self._key_path(
                    row["staging_key"],
                    self.settings.staging_root,
                )
                self._reconcile_staging_file(staging_path, 0, session_id)
                self._durable_write(staging_path, 0, item.body)

            if pending:
                now = self.timestamp()
                with self.database.immediate() as connection:
                    for item, _row, _next_offset, size_bytes in pending:
                        cursor = connection.execute(
                            """
                            UPDATE upload_files
                            SET next_offset=?, state='VERIFIED'
                            WHERE session_id=? AND relative_path=? AND next_offset=0
                            """,
                            (size_bytes, session_id, item.path),
                        )
                        if cursor.rowcount != 1:
                            raise ReceiverError(409, "File batch offset changed concurrently")
                        self._hasher_cache.pop((session_id, item.path), None)
                    connection.execute(
                        "UPDATE upload_sessions SET updated_at=? WHERE session_id=?",
                        (now, session_id),
                    )

            return {item.path: size_bytes for item, _row, _offset, size_bytes in rows}

    def put_chunk(
        self,
        device: Device,
        session_id: str,
        relative_path: str,
        offset: int,
        content_range: str,
        chunk_sha256: str,
        body: bytes,
        *,
        slot_reserved: bool = False,
    ) -> int:
        self.ensure_storage_available()
        session_id = self.validate_session_id(session_id)
        relative_path = self.validate_relative_path(relative_path)
        if offset < 0:
            raise ReceiverError(400, "Chunk offset is invalid")
        if not SHA256_PATTERN.fullmatch(chunk_sha256):
            raise ReceiverError(400, "X-Chunk-SHA256 is invalid")
        if len(body) > self.settings.max_chunk_bytes:
            raise ReceiverError(413, "Chunk is larger than the configured limit")
        range_match = CONTENT_RANGE_PATTERN.fullmatch(content_range)
        if range_match is None:
            raise ReceiverError(400, "Content-Range is invalid")
        range_start, range_end, declared_size = map(int, range_match.groups())
        if (
            not body
            or range_start != offset
            or range_end < range_start
            or range_end - range_start + 1 != len(body)
        ):
            raise ReceiverError(400, "Chunk range and body length do not match")
        if not hmac.compare_digest(hashlib.sha256(body).hexdigest(), chunk_sha256):
            raise ReceiverError(422, "Chunk SHA-256 does not match")

        slot = nullcontext() if slot_reserved else self._put_slot(device.device_id)
        with slot, self._guard(f"session:{session_id}"):
            with self.database.connect() as connection:
                session = self._owned_session(connection, device, session_id)
                if session["state"] != "OPEN":
                    raise ReceiverError(409, "Upload session is not open")
                row = connection.execute(
                    "SELECT * FROM upload_files WHERE session_id=? AND relative_path=?",
                    (session_id, relative_path),
                ).fetchone()
            if row is None:
                raise ReceiverError(404, "Upload file was not found")
            expected_offset = int(row["next_offset"])
            size_bytes = int(row["size_bytes"])
            if offset != expected_offset:
                raise ReceiverError(409, "Chunk offset does not match the server offset")
            if declared_size != size_bytes or offset + len(body) > size_bytes:
                raise ReceiverError(400, "Content-Range file size is invalid")

            staging_path = self._key_path(row["staging_key"], self.settings.staging_root)
            self._reconcile_staging_file(staging_path, expected_offset, session_id)
            hasher = self._hasher_for(session_id, relative_path, expected_offset)
            candidate_hasher = hasher.copy() if hasher is not None else None
            if candidate_hasher is not None:
                candidate_hasher.update(body)
            next_offset = offset + len(body)

            self._durable_write(staging_path, offset, body)
            if (
                next_offset == size_bytes
                and candidate_hasher is not None
                and not hmac.compare_digest(candidate_hasher.hexdigest(), row["sha256"])
            ):
                now = self.timestamp()
                with self.database.immediate() as connection:
                    connection.execute(
                        """
                        UPDATE upload_files
                        SET next_offset=?, state='FAILED'
                        WHERE session_id=? AND relative_path=? AND next_offset=?
                        """,
                        (next_offset, session_id, relative_path, offset),
                    )
                    connection.execute(
                        """
                        UPDATE upload_sessions
                        SET state='FAILED', failure_code='file_hash_mismatch', updated_at=?
                        WHERE session_id=?
                        """,
                        (now, session_id),
                    )
                self._hasher_cache.pop((session_id, relative_path), None)
                raise ReceiverError(422, "File SHA-256 does not match")

            new_state = (
                "VERIFIED"
                if next_offset == size_bytes and candidate_hasher is not None
                else "RECEIVED"
                if next_offset == size_bytes
                else "UPLOADING"
            )
            now = self.timestamp()
            with self.database.immediate() as connection:
                cursor = connection.execute(
                    """
                    UPDATE upload_files
                    SET next_offset=?, state=?
                    WHERE session_id=? AND relative_path=? AND next_offset=?
                    """,
                    (next_offset, new_state, session_id, relative_path, offset),
                )
                if cursor.rowcount != 1:
                    raise ReceiverError(409, "Chunk offset changed concurrently")
                connection.execute(
                    "UPDATE upload_sessions SET updated_at=? WHERE session_id=?",
                    (now, session_id),
                )
            if candidate_hasher is not None:
                self._hasher_cache[(session_id, relative_path)] = (
                    next_offset,
                    candidate_hasher,
                )
            return next_offset

    def complete(self, device: Device, session_id: str) -> str:
        self.ensure_storage_available()
        session_id = self.validate_session_id(session_id)
        with self._guard(f"session:{session_id}"):
            with self.database.connect() as connection:
                session = self._owned_session(connection, device, session_id)
                state = session["state"]
                if state == "COMPLETED":
                    return "COMPLETED"
                if state == "FINALIZING":
                    try:
                        self._finish_finalizing(dict(session))
                    except ReceiverError:
                        raise
                    except OSError as error:
                        if self._is_permanent_object_error(error):
                            self._mark_failed(session_id, "finalization_failed")
                            raise ReceiverError(
                                503,
                                "Finalization storage is inconsistent",
                                retry_after=5,
                            ) from error
                        raise ReceiverError(
                            503,
                            "Final storage is temporarily unavailable",
                            retry_after=5,
                        ) from error
                    except (RuntimeError, ValueError, json.JSONDecodeError) as error:
                        self._mark_failed(session_id, "finalization_failed")
                        raise ReceiverError(
                            503,
                            "Finalization storage is inconsistent",
                            retry_after=5,
                        ) from error
                    return "COMPLETED"
                if state != "OPEN":
                    raise ReceiverError(409, "Upload session cannot be completed")
                incomplete = connection.execute(
                    """
                    SELECT COUNT(*) AS count FROM upload_files
                    WHERE session_id=? AND (
                        next_offset != size_bytes OR state NOT IN ('RECEIVED', 'VERIFIED')
                    )
                    """,
                    (session_id,),
                ).fetchone()["count"]
                session_data = dict(session)
            if incomplete:
                raise ReceiverError(409, "Upload session has incomplete files")

            self._verify_staged_files(session_id)

            now = self.timestamp()
            with self.database.immediate() as connection:
                connection.execute(
                    """
                    UPDATE upload_sessions
                    SET state='FINALIZING', updated_at=?
                    WHERE session_id=? AND state='OPEN'
                    """,
                    (now, session_id),
                )
            session_data["state"] = "FINALIZING"
            try:
                self._finish_finalizing(session_data, staged_verified=True)
            except ReceiverError:
                raise
            except OSError as error:
                if self._is_permanent_object_error(error):
                    self._mark_failed(session_id, "finalization_failed")
                    raise ReceiverError(
                        503,
                        "Finalization storage is inconsistent",
                        retry_after=5,
                    ) from error
                raise ReceiverError(
                    503,
                    "Final storage is temporarily unavailable",
                    retry_after=5,
                ) from error
            except (RuntimeError, ValueError, json.JSONDecodeError) as error:
                self._mark_failed(session_id, "finalization_failed")
                raise ReceiverError(
                    503,
                    "Finalization storage is inconsistent",
                    retry_after=5,
                ) from error
            self._drop_session_hashers(session_id)
            return "COMPLETED"

    def cancel(self, device: Device, session_id: str) -> str:
        self.ensure_storage_available()
        session_id = self.validate_session_id(session_id)
        with self._guard(f"session:{session_id}"):
            with self.database.connect() as connection:
                session = self._owned_session(connection, device, session_id)
            if session["state"] == "CANCELLED":
                self._remove_tree(
                    self._staging_directory(device.device_id, session_id)
                )
                self._release_staging_accounting(session_id)
                return "CANCELLED"
            if session["state"] in {"COMPLETED", "FINALIZING"}:
                raise ReceiverError(409, "Completed upload session cannot be cancelled")
            now = self.timestamp()
            with self.database.immediate() as connection:
                connection.execute(
                    """
                    UPDATE upload_sessions
                    SET state='CANCELLED', failure_code=NULL, updated_at=?
                    WHERE session_id=?
                    """,
                    (now, session_id),
                )
            self._remove_tree(self._staging_directory(device.device_id, session_id))
            self._release_staging_accounting(session_id)
            self._drop_session_hashers(session_id)
            return "CANCELLED"

    def health_ready(self) -> bool:
        now = time.monotonic()
        with self._readiness_guard:
            if (
                self._readiness_cache is not None
                and now - self._readiness_cache[0]
                < self.settings.readiness_cache_seconds
            ):
                return self._readiness_cache[1]
            ready = self._probe_readiness()
            self._readiness_cache = (time.monotonic(), ready)
            return ready

    def _probe_readiness(self) -> bool:
        try:
            self.ensure_storage_available()
            with self.database.connect() as connection:
                connection.execute("SELECT 1").fetchone()
            probe = self.settings.runtime_root / f".ready-{uuid.uuid4().hex}"
            descriptor = os.open(probe, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
            try:
                os.write(descriptor, b"ready")
                os.fsync(descriptor)
            finally:
                os.close(descriptor)
            probe.unlink()
            self._fsync_directory(self.settings.runtime_root)
            return True
        except (OSError, sqlite3.Error, ReceiverError):
            return False

    def metrics(self) -> str:
        self.ensure_storage_available()
        with self.database.connect() as connection:
            states = connection.execute(
                "SELECT state, COUNT(*) AS count FROM upload_sessions GROUP BY state"
            ).fetchall()
            bytes_row = connection.execute(
                """
                SELECT COALESCE(SUM(next_offset), 0) AS received,
                       COALESCE(SUM(size_bytes), 0) AS declared
                FROM upload_files
                """
            ).fetchone()
        lines = [
            "# HELP jetson_upload_sessions Upload sessions by state.",
            "# TYPE jetson_upload_sessions gauge",
        ]
        for row in states:
            lines.append(
                f'jetson_upload_sessions{{state="{row["state"]}"}} {row["count"]}'
            )
        lines.extend(
            [
                "# TYPE jetson_upload_received_bytes gauge",
                f'jetson_upload_received_bytes {bytes_row["received"]}',
                "# TYPE jetson_upload_declared_bytes gauge",
                f'jetson_upload_declared_bytes {bytes_row["declared"]}',
            ]
        )
        return "\n".join(lines) + "\n"

    def recover(self) -> None:
        with self.database.connect() as connection:
            rows = connection.execute(
                "SELECT * FROM upload_sessions WHERE state IN ('OPEN', 'FINALIZING', 'CANCELLED')"
            ).fetchall()
        for row in rows:
            session = dict(row)
            with self._guard(f"session:{session['session_id']}"):
                if session["state"] == "FINALIZING":
                    try:
                        self._finish_finalizing(session)
                    except OSError as error:
                        if self._is_permanent_object_error(error):
                            self._mark_failed(
                                session["session_id"],
                                "finalization_recovery_failed",
                            )
                    except ReceiverError as error:
                        if error.status < 500:
                            self._mark_failed(
                                session["session_id"],
                                "finalization_recovery_failed",
                            )
                    except sqlite3.Error:
                        pass
                    except (RuntimeError, ValueError, json.JSONDecodeError):
                        self._mark_failed(session["session_id"], "finalization_recovery_failed")
                elif session["state"] == "CANCELLED":
                    self._remove_tree(
                        self._staging_directory(session["device_id"], session["session_id"])
                    )
                    self._release_staging_accounting(str(session["session_id"]))
                else:
                    self._recover_open_session(session)

    def _recover_open_session(self, session: Mapping[str, object]) -> None:
        with self.database.connect() as connection:
            files = connection.execute(
                "SELECT * FROM upload_files WHERE session_id=?",
                (session["session_id"],),
            ).fetchall()
        for row in files:
            offset = int(row["next_offset"])
            path = self._key_path(row["staging_key"], self.settings.staging_root)
            if not path.exists():
                if offset > 0 or int(row["size_bytes"]) == 0:
                    self._mark_failed(str(session["session_id"]), "staging_data_missing")
                    return
                continue
            if path.is_symlink() or not path.is_file():
                self._mark_failed(str(session["session_id"]), "invalid_staging_file")
                return
            size = path.stat().st_size
            if size < offset:
                self._mark_failed(str(session["session_id"]), "staging_data_shorter_than_offset")
                return
            if size > offset:
                with path.open("r+b", buffering=0) as output:
                    output.truncate(offset)
                    os.fsync(output.fileno())

    def _finish_finalizing(
        self,
        session: Mapping[str, object],
        *,
        staged_verified: bool = False,
    ) -> None:
        device_id = str(session["device_id"])
        session_id = str(session["session_id"])
        staging = self._staging_directory(device_id, session_id)
        final = self._final_directory(device_id, session_id)
        if final.exists() and not staging.exists():
            self._verify_finalized_session(session)
        elif staging.exists() and not final.exists():
            if not staged_verified:
                self._verify_staged_files(session_id)
            manifest_path = staging / "manifest.json"
            self._atomic_write(
                manifest_path,
                self._final_manifest_bytes(session),
            )
            with self.database.connect() as connection:
                file_names = {
                    f'{row["file_id"]}.blob'
                    for row in connection.execute(
                        "SELECT file_id FROM upload_files WHERE session_id=?",
                        (session_id,),
                    ).fetchall()
                }
            if {entry.name for entry in staging.iterdir()} != file_names | {
                "manifest.json"
            }:
                raise RuntimeError("Staging upload contains unexpected objects")
            final.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
            self._fsync_directory(final.parent)
            os.replace(staging, final)
            self._fsync_directory(staging.parent)
            self._fsync_directory(final.parent)
        else:
            raise RuntimeError("Finalization storage state is inconsistent")
        now = self.timestamp()
        with self.database.immediate() as connection:
            connection.execute(
                """
                UPDATE upload_sessions
                SET state='COMPLETED', failure_code=NULL, updated_at=?, completed_at=?
                WHERE session_id=? AND state='FINALIZING'
                """,
                (now, now, session_id),
            )

    def _reactivate_failed_session(self, device: Device, session_id: str) -> None:
        with self._guard(f"session:{session_id}"):
            with self.database.connect() as connection:
                session = self._owned_session(connection, device, session_id)
            if session["state"] != "FAILED":
                return
            self._remove_tree(self._staging_directory(device.device_id, session_id))
            self._remove_tree(self._final_directory(device.device_id, session_id))
            staging = self._staging_directory(device.device_id, session_id)
            staging.mkdir(parents=True, exist_ok=False, mode=0o700)
            self._fsync_directory(staging.parent)
            now = self.timestamp()
            with self.database.immediate() as connection:
                self._enforce_quota(
                    connection,
                    device,
                    int(session["total_bytes"]),
                    exclude_session_id=session_id,
                )
                self._enforce_active_session_limit(
                    connection,
                    device,
                    exclude_session_id=session_id,
                )
                files = connection.execute(
                    "SELECT * FROM upload_files WHERE session_id=?",
                    (session_id,),
                ).fetchall()
                for row in files:
                    if int(row["size_bytes"]) == 0:
                        self._create_empty_file(
                            self._key_path(row["staging_key"], self.settings.staging_root)
                        )
                connection.execute(
                    """
                    UPDATE upload_files
                    SET next_offset=0,
                        state=CASE WHEN size_bytes=0 THEN 'VERIFIED' ELSE 'PENDING' END
                    WHERE session_id=?
                    """,
                    (session_id,),
                )
                connection.execute(
                    """
                    UPDATE upload_sessions
                    SET state='OPEN', failure_code=NULL, updated_at=?
                    WHERE session_id=? AND state='FAILED'
                    """,
                    (now, session_id),
                )
            self._drop_session_hashers(session_id)

    def _enforce_quota(
        self,
        connection: sqlite3.Connection,
        device: Device,
        requested_bytes: int,
        *,
        exclude_session_id: str | None = None,
    ) -> None:
        excluded = exclude_session_id or ""
        reserved = connection.execute(
            """
            SELECT COALESCE(SUM(total_bytes), 0) AS total
            FROM upload_sessions
            WHERE device_id=? AND state IN ('OPEN', 'FINALIZING', 'COMPLETED')
              AND session_id != ?
            """,
            (device.device_id, excluded),
        ).fetchone()["total"]
        retained_staging = connection.execute(
            """
            SELECT COALESCE(SUM(upload_files.next_offset), 0) AS total
            FROM upload_files
            JOIN upload_sessions USING (session_id)
            WHERE upload_sessions.device_id=?
              AND upload_sessions.state IN ('FAILED', 'CANCELLED')
              AND upload_sessions.session_id != ?
            """,
            (device.device_id, excluded),
        ).fetchone()["total"]
        if int(reserved) + int(retained_staging) + requested_bytes > device.quota_bytes:
            raise ReceiverError(413, "Device quota would be exceeded")

    def _enforce_metadata_limits(
        self,
        connection: sqlite3.Connection,
        device: Device,
        *,
        requested_files: int,
    ) -> None:
        counts = connection.execute(
            """
            SELECT COUNT(*) AS sessions,
                   COALESCE(SUM(file_count), 0) AS files,
                   COALESCE(SUM(CASE
                       WHEN state IN ('OPEN', 'FINALIZING') THEN 1 ELSE 0
                   END), 0) AS active
            FROM upload_sessions
            WHERE device_id=?
            """,
            (device.device_id,),
        ).fetchone()
        self._raise_if_active_limit_reached(int(counts["active"]))
        if int(counts["sessions"]) + 1 > self.settings.max_stored_sessions_per_device:
            raise ReceiverError(413, "Device session metadata limit would be exceeded")
        if int(counts["files"]) + requested_files > self.settings.max_stored_files_per_device:
            raise ReceiverError(413, "Device file metadata limit would be exceeded")

    def _enforce_active_session_limit(
        self,
        connection: sqlite3.Connection,
        device: Device,
        *,
        exclude_session_id: str,
    ) -> None:
        active = connection.execute(
            """
            SELECT COUNT(*) AS count FROM upload_sessions
            WHERE device_id=? AND state IN ('OPEN', 'FINALIZING')
              AND session_id != ?
            """,
            (device.device_id, exclude_session_id),
        ).fetchone()["count"]
        self._raise_if_active_limit_reached(int(active))

    def _raise_if_active_limit_reached(self, active: int) -> None:
        if active >= self.settings.max_active_sessions_per_device:
            raise ReceiverError(
                429,
                "Device has too many active upload sessions",
                retry_after=60,
            )

    def _owned_session(
        self, connection: sqlite3.Connection, device: Device, session_id: str
    ) -> sqlite3.Row:
        row = connection.execute(
            "SELECT * FROM upload_sessions WHERE session_id=?", (session_id,)
        ).fetchone()
        if row is None:
            raise ReceiverError(404, "Upload session was not found")
        if row["device_id"] != device.device_id:
            raise ReceiverError(403, "Upload session belongs to another device")
        return row

    def _mark_failed(self, session_id: str, failure_code: str) -> None:
        with self.database.immediate() as connection:
            connection.execute(
                """
                UPDATE upload_sessions
                SET state='FAILED', failure_code=?, updated_at=?
                WHERE session_id=? AND state != 'COMPLETED'
                """,
                (failure_code, self.timestamp(), session_id),
            )

    @contextmanager
    def _guard(self, key: str) -> Iterator[None]:
        digest = hashlib.sha256(key.encode("utf-8")).hexdigest()
        stripe = int(digest[:16], 16) % 256
        lock_name = f"{stripe:03d}.lock"
        with self._thread_locks_guard:
            thread_lock = self._thread_locks.setdefault(lock_name, threading.RLock())
        lock_path = self.settings.locks_root / lock_name
        with thread_lock:
            descriptor = os.open(lock_path, os.O_RDWR | os.O_CREAT | os.O_NOFOLLOW, 0o600)
            try:
                fcntl.flock(descriptor, fcntl.LOCK_EX)
                yield
            finally:
                fcntl.flock(descriptor, fcntl.LOCK_UN)
                os.close(descriptor)

    @contextmanager
    def _put_slot(self, device_id: str) -> Iterator[None]:
        self.acquire_put_slot(device_id)
        try:
            yield
        finally:
            self.release_put_slot(device_id)

    def acquire_put_slot(self, device_id: str) -> None:
        with self._put_counts_guard:
            active = self._put_counts.get(device_id, 0)
            if active >= self.settings.max_concurrent_puts_per_device:
                raise ReceiverError(429, "Device has too many concurrent uploads", retry_after=1)
            self._put_counts[device_id] = active + 1

    def release_put_slot(self, device_id: str) -> None:
        with self._put_counts_guard:
            remaining = self._put_counts.get(device_id, 1) - 1
            if remaining:
                self._put_counts[device_id] = remaining
            else:
                self._put_counts.pop(device_id, None)

    def _hasher_for(
        self,
        session_id: str,
        relative_path: str,
        offset: int,
    ):
        cached = self._hasher_cache.get((session_id, relative_path))
        if cached is not None and cached[0] == offset:
            return cached[1]
        if offset:
            return None
        digest = hashlib.sha256()
        self._hasher_cache[(session_id, relative_path)] = (offset, digest)
        return digest

    def _verify_staged_files(self, session_id: str) -> None:
        with self.database.connect() as connection:
            rows = connection.execute(
                """
                SELECT relative_path, staging_key, size_bytes, sha256
                FROM upload_files
                WHERE session_id=? AND state IN ('RECEIVED', 'VERIFIED')
                """,
                (session_id,),
            ).fetchall()
        for row in rows:
            path = self._key_path(row["staging_key"], self.settings.staging_root)
            digest = hashlib.sha256()
            try:
                descriptor = os.open(path, os.O_RDONLY | os.O_NOFOLLOW)
                try:
                    file_stat = os.fstat(descriptor)
                    if not stat.S_ISREG(file_stat.st_mode):
                        raise OSError("Staging object is not a regular file")
                    if file_stat.st_size != int(row["size_bytes"]):
                        raise ReceiverError(422, "File size does not match")
                    while True:
                        chunk = os.read(descriptor, 4 * 1024 * 1024)
                        if not chunk:
                            break
                        digest.update(chunk)
                finally:
                    os.close(descriptor)
            except ReceiverError:
                self._fail_file_hash(session_id, row["relative_path"])
                raise
            except OSError as error:
                if self._is_permanent_object_error(error):
                    self._mark_failed(session_id, "staging_data_missing")
                raise ReceiverError(
                    503, "Staging storage is unavailable", retry_after=5
                ) from error
            if not hmac.compare_digest(digest.hexdigest(), row["sha256"]):
                self._fail_file_hash(session_id, row["relative_path"])
                raise ReceiverError(422, "File SHA-256 does not match")
        if rows:
            with self.database.immediate() as connection:
                connection.execute(
                    """
                    UPDATE upload_files SET state='VERIFIED'
                    WHERE session_id=? AND state='RECEIVED'
                    """,
                    (session_id,),
                )

    def _final_manifest_bytes(self, session: Mapping[str, object]) -> bytes:
        session_id = str(session["session_id"])
        public_manifest = json.loads(str(session["manifest_json"]))
        with self.database.connect() as connection:
            file_rows = connection.execute(
                """
                SELECT relative_path, file_id, size_bytes, sha256
                FROM upload_files WHERE session_id=? ORDER BY relative_path
                """,
                (session_id,),
            ).fetchall()
        public_manifest["files"] = [
            {
                "path": row["relative_path"],
                "sizeBytes": row["size_bytes"],
                "sha256": row["sha256"],
                "storedObject": f'{row["file_id"]}.blob',
            }
            for row in file_rows
        ]
        public_manifest["storageVersion"] = 1
        return json.dumps(
            public_manifest,
            ensure_ascii=False,
            sort_keys=True,
            separators=(",", ":"),
        ).encode("utf-8")

    def _verify_finalized_session(self, session: Mapping[str, object]) -> None:
        device_id = str(session["device_id"])
        session_id = str(session["session_id"])
        final = self._final_directory(device_id, session_id)
        if final.is_symlink() or not final.is_dir():
            raise RuntimeError("Final upload directory is invalid")
        with self.database.connect() as connection:
            rows = connection.execute(
                """
                SELECT relative_path, file_id, size_bytes, sha256
                FROM upload_files WHERE session_id=? ORDER BY relative_path
                """,
                (session_id,),
            ).fetchall()
        expected_names = {"manifest.json"}
        for row in rows:
            name = f'{row["file_id"]}.blob'
            expected_names.add(name)
            self._verify_regular_file(
                final / name,
                expected_size=int(row["size_bytes"]),
                expected_sha256=str(row["sha256"]),
            )
        if {entry.name for entry in final.iterdir()} != expected_names:
            raise RuntimeError("Final upload directory contains unexpected objects")
        expected_manifest = self._final_manifest_bytes(session)
        manifest_path = final / "manifest.json"
        descriptor = os.open(manifest_path, os.O_RDONLY | os.O_NOFOLLOW)
        try:
            manifest_stat = os.fstat(descriptor)
            if not stat.S_ISREG(manifest_stat.st_mode):
                raise RuntimeError("Final manifest is not a regular file")
            actual_manifest = bytearray()
            while True:
                chunk = os.read(descriptor, 1024 * 1024)
                if not chunk:
                    break
                actual_manifest.extend(chunk)
        finally:
            os.close(descriptor)
        if not hmac.compare_digest(bytes(actual_manifest), expected_manifest):
            raise RuntimeError("Final manifest does not match receiver metadata")

    @staticmethod
    def _verify_regular_file(
        path: Path,
        *,
        expected_size: int,
        expected_sha256: str,
    ) -> None:
        digest = hashlib.sha256()
        descriptor = os.open(path, os.O_RDONLY | os.O_NOFOLLOW)
        try:
            file_stat = os.fstat(descriptor)
            if not stat.S_ISREG(file_stat.st_mode):
                raise RuntimeError("Final upload object is not a regular file")
            if file_stat.st_size != expected_size:
                raise RuntimeError("Final upload object size does not match")
            while True:
                chunk = os.read(descriptor, 4 * 1024 * 1024)
                if not chunk:
                    break
                digest.update(chunk)
        finally:
            os.close(descriptor)
        if not hmac.compare_digest(digest.hexdigest(), expected_sha256):
            raise RuntimeError("Final upload object SHA-256 does not match")

    def _fail_file_hash(self, session_id: str, relative_path: str) -> None:
        now = self.timestamp()
        with self.database.immediate() as connection:
            connection.execute(
                """
                UPDATE upload_files SET state='FAILED'
                WHERE session_id=? AND relative_path=?
                """,
                (session_id, relative_path),
            )
            connection.execute(
                """
                UPDATE upload_sessions
                SET state='FAILED', failure_code='file_hash_mismatch', updated_at=?
                WHERE session_id=?
                """,
                (now, session_id),
            )

    def _release_staging_accounting(self, session_id: str) -> None:
        with self.database.immediate() as connection:
            connection.execute(
                """
                UPDATE upload_files
                SET next_offset=0,
                    state=CASE WHEN size_bytes=0 THEN 'VERIFIED' ELSE 'PENDING' END
                WHERE session_id=?
                """,
                (session_id,),
            )

    def _drop_session_hashers(self, session_id: str) -> None:
        for key in list(self._hasher_cache):
            if key[0] == session_id:
                self._hasher_cache.pop(key, None)

    def _reconcile_staging_file(
        self, path: Path, expected_offset: int, session_id: str
    ) -> None:
        if not path.exists():
            if expected_offset:
                self._mark_failed(session_id, "staging_data_missing")
                raise ReceiverError(503, "Staging storage is inconsistent", retry_after=5)
            return
        if path.is_symlink() or not path.is_file():
            self._mark_failed(session_id, "invalid_staging_file")
            raise ReceiverError(503, "Staging storage is inconsistent", retry_after=5)
        size = path.stat().st_size
        if size < expected_offset:
            self._mark_failed(session_id, "staging_data_shorter_than_offset")
            raise ReceiverError(503, "Staging storage is inconsistent", retry_after=5)
        if size > expected_offset:
            with path.open("r+b", buffering=0) as output:
                output.truncate(expected_offset)
                os.fsync(output.fileno())

    def _durable_write(self, path: Path, offset: int, body: bytes) -> None:
        self.ensure_storage_available()
        path.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
        existed = path.exists()
        flags = os.O_RDWR | os.O_CREAT | os.O_NOFOLLOW
        descriptor = os.open(path, flags, 0o600)
        try:
            written = 0
            while written < len(body):
                count = os.pwrite(descriptor, body[written:], offset + written)
                if count < 1:
                    raise OSError("Short write to staging storage")
                written += count
            os.ftruncate(descriptor, offset + len(body))
            os.fsync(descriptor)
        finally:
            os.close(descriptor)
        if not existed:
            self._fsync_directory(path.parent)

    def _create_empty_file(self, path: Path) -> None:
        self.ensure_storage_available()
        path.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
        descriptor = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        try:
            os.fsync(descriptor)
        finally:
            os.close(descriptor)
        self._fsync_directory(path.parent)

    def _atomic_write(self, path: Path, body: bytes) -> None:
        self.ensure_storage_available()
        temporary = path.with_name(f".{path.name}.{uuid.uuid4().hex}.tmp")
        try:
            descriptor = os.open(
                temporary,
                os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW,
                0o600,
            )
            try:
                written = 0
                while written < len(body):
                    count = os.write(descriptor, body[written:])
                    if count < 1:
                        raise OSError("Short metadata write")
                    written += count
                os.fsync(descriptor)
            finally:
                os.close(descriptor)
            os.replace(temporary, path)
            self._fsync_directory(path.parent)
        except BaseException:
            temporary.unlink(missing_ok=True)
            raise

    def _key_path(self, key: str, required_root: Path) -> Path:
        path = self.settings.data_root / key
        try:
            path.parent.resolve().relative_to(required_root.resolve())
        except ValueError as error:
            raise RuntimeError("Stored object key escaped its namespace") from error
        return path

    def ensure_storage_available(self) -> None:
        if not self.settings.require_mount or self.settings.expected_mount is None:
            return
        try:
            expected = self.settings.expected_mount
            if not expected.is_mount():
                raise OSError("Expected upload mount is not mounted")
            mount_device = expected.stat().st_dev
            if (
                mount_device != self._expected_mount_device
                or self.settings.data_root.stat().st_dev != mount_device
            ):
                raise OSError("Upload storage filesystem changed")
        except OSError as error:
            raise ReceiverError(
                503,
                "Upload storage is unavailable",
                retry_after=5,
            ) from error

    @staticmethod
    def _is_permanent_object_error(error: OSError) -> bool:
        return error.errno in {
            errno.ENOENT,
            errno.ENOTDIR,
            errno.ELOOP,
        }

    def _staging_directory(self, device_id: str, session_id: str) -> Path:
        return self.settings.staging_root / device_id / session_id

    def _final_directory(self, device_id: str, session_id: str) -> Path:
        return self.settings.objects_root / device_id / session_id

    @staticmethod
    def _staging_key(device_id: str, session_id: str, file_id: str) -> str:
        return f"storage/staging/{device_id}/{session_id}/{file_id}.blob"

    @staticmethod
    def _final_key(device_id: str, session_id: str, file_id: str) -> str:
        return f"storage/objects/{device_id}/{session_id}/{file_id}.blob"

    @staticmethod
    def _remove_tree(path: Path) -> None:
        if path.exists():
            shutil.rmtree(path)

    @staticmethod
    def _fsync_directory(path: Path) -> None:
        descriptor = os.open(path, os.O_RDONLY | os.O_DIRECTORY)
        try:
            os.fsync(descriptor)
        finally:
            os.close(descriptor)

    @staticmethod
    def validate_device_id(value: str) -> str:
        try:
            canonical = str(uuid.UUID(value)).lower()
        except (ValueError, AttributeError) as error:
            raise ReceiverError(400, "deviceId is invalid") from error
        if value != canonical:
            raise ReceiverError(400, "deviceId must be a canonical lowercase UUID")
        return canonical

    @staticmethod
    def validate_session_id(value: str) -> str:
        if not SESSION_ID_PATTERN.fullmatch(value):
            raise ReceiverError(400, "sessionId is invalid")
        return value

    @staticmethod
    def validate_relative_path(value: str) -> str:
        try:
            encoded_value = value.encode("utf-8")
        except UnicodeEncodeError as error:
            raise ReceiverError(400, "File path is invalid") from error
        if (
            not value
            or value.startswith("/")
            or "\\" in value
            or "\x00" in value
            or len(encoded_value) > 4096
            or unicodedata.normalize("NFC", value) != value
        ):
            raise ReceiverError(400, "File path is invalid")
        segments = value.split("/")
        try:
            invalid_segment = any(
                not segment
                or segment in {".", ".."}
                or len(segment.encode("utf-8")) > 255
                for segment in segments
            )
        except UnicodeEncodeError as error:
            raise ReceiverError(400, "File path is invalid") from error
        if invalid_segment:
            raise ReceiverError(400, "File path is invalid")
        if posixpath.normpath(value) != value:
            raise ReceiverError(400, "File path is not normalized")
        return value

    @staticmethod
    def _parse_timestamp(value: str) -> datetime:
        try:
            parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
        except ValueError as error:
            raise ValueError("Timestamp must be ISO 8601") from error
        if parsed.tzinfo is None:
            raise ValueError("Timestamp must include a timezone")
        return parsed.astimezone(timezone.utc)
