from __future__ import annotations

import json
import http.client
import hashlib
import ipaddress
import math
import os
import shutil
import ssl
import stat
import struct
import threading
import time
import uuid
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Callable, Dict, Iterable, List, Optional, Tuple
from urllib.parse import urlencode, urlsplit

from .config import load_json_object, validate_config_id
from .filesystem import FileTooLarge, StorageRegistry


TERMINAL_STATES = {"COMPLETED", "FAILED", "CANCELLED"}
ACTIVE_STATES = {"QUEUED", "SCANNING", "UPLOADING"}
HTTP_CHUNK_SIZE = 4 * 1024 * 1024
HTTP_RETRY_DELAYS = (0.0, 1.0, 3.0, 7.0)
DEFAULT_MAX_CONCURRENT_JOBS = 2
HTTP_COMPLETE_RESPONSE_TIMEOUT = 24 * 60 * 60
FILE_BATCH_MAGIC = b"JETSONBATCH1\n"
FILE_BATCH_VERSION = 1
DEFERRED_FILE_HASH_VERSION = 1
DEFERRED_FILE_HASH_MODE = "deferred-v1"
CLIENT_MAX_BATCH_BYTES = 32 * 1024 * 1024
CLIENT_MAX_BATCH_FILES = 256
HASH_PROGRESS_INTERVAL_SECONDS = 0.5
HTTP_JSON_MAX_RESPONSE_BYTES = 1024 * 1024
HTTP_LIBRARY_MAX_RESPONSE_BYTES = 4 * 1024 * 1024
CONTENT_DIGEST_MAGIC = b"JETSON-UPLOAD-CONTENT-V1\x00"


@dataclass(frozen=True)
class UploadTarget:
    id: str
    label: str
    kind: str
    path: Optional[Path] = None
    base_url: Optional[str] = None
    token_file: Optional[Path] = None
    verify_tls: bool = True
    editable: bool = False


@dataclass(frozen=True)
class FileBatchLimits:
    max_bytes: int
    max_files: int


@dataclass
class TransferProgress:
    started_at: float
    acknowledged_this_run: int = 0


class UploadManager:
    def __init__(
        self,
        storage: StorageRegistry,
        targets_path: Path,
        state_dir: Path,
        device_id: str = "unknown",
        allow_local_targets: bool = False,
        max_concurrent_jobs: int = DEFAULT_MAX_CONCURRENT_JOBS,
    ) -> None:
        if max_concurrent_jobs < 1:
            raise ValueError("max_concurrent_jobs must be at least 1")
        self.storage = storage
        self.targets_path = targets_path
        self.managed_targets_path = state_dir / "managed-upload-targets.json"
        self.managed_tokens_dir = state_dir / "upload-target-tokens"
        self.jobs_dir = state_dir / "upload-jobs"
        self.device_id = device_id
        self.allow_local_targets = allow_local_targets
        self.max_concurrent_jobs = max_concurrent_jobs
        self.jobs_dir.mkdir(parents=True, exist_ok=True)
        self._lock = threading.RLock()
        self._cancellations: Dict[str, threading.Event] = {}
        self._transfer_progress: Dict[str, TransferProgress] = {}
        self._recover_interrupted_jobs()

    def targets(self) -> Dict[str, UploadTarget]:
        targets = self._load_targets(self.targets_path, editable=False)
        managed_targets = self._load_targets(
            self.managed_targets_path,
            editable=True,
            allow_missing=True,
        )
        duplicate_ids = targets.keys() & managed_targets.keys()
        if duplicate_ids:
            duplicate = sorted(duplicate_ids)[0]
            raise ValueError(
                f"Managed upload target {duplicate!r} conflicts with administrator configuration"
            )
        targets.update(managed_targets)
        return targets

    def _load_targets(
        self,
        path: Path,
        *,
        editable: bool,
        allow_missing: bool = False,
    ) -> Dict[str, UploadTarget]:
        if allow_missing and not path.exists():
            return {}
        try:
            raw = load_json_object(path)
        except RuntimeError:
            if allow_missing and not path.exists():
                return {}
            raise
        targets: Dict[str, UploadTarget] = {}
        for target_id, value in raw.items():
            validate_config_id(target_id, "upload target")
            if not isinstance(value, dict):
                raise ValueError(f"Upload target {target_id!r} must be an object")
            target_type = str(value.get("type", "local")).strip().lower()
            label = str(value.get("label", target_id)).strip()
            if not label:
                raise ValueError(f"Upload target {target_id!r} needs a label")

            if target_type == "local":
                if not self.allow_local_targets:
                    continue
                path_text = str(value.get("path", "")).strip()
                if not path_text:
                    raise ValueError(f"Upload target {target_id!r} needs a path")
                targets[target_id] = UploadTarget(
                    id=target_id,
                    label=label,
                    kind="local",
                    path=Path(path_text).expanduser().resolve(),
                    editable=editable,
                )
            elif target_type == "http":
                base_url = str(value.get("base_url", "")).strip().rstrip("/")
                token_text = str(value.get("token_file", "")).strip()
                parsed = urlsplit(base_url)
                verify_tls_value = value.get("verify_tls", True)
                if not isinstance(verify_tls_value, bool):
                    raise ValueError(
                        f"Upload target {target_id!r} verify_tls must be a boolean"
                    )
                verify_tls = verify_tls_value
                if (
                    parsed.scheme not in {"http", "https"}
                    or not parsed.hostname
                    or parsed.username is not None
                    or parsed.password is not None
                    or parsed.query
                    or parsed.fragment
                ):
                    raise ValueError(f"Upload target {target_id!r} has an invalid base_url")
                if parsed.scheme != "https" and verify_tls:
                    raise ValueError(
                        f"Upload target {target_id!r} must use HTTPS or set verify_tls=false"
                    )
                if not token_text:
                    raise ValueError(f"Upload target {target_id!r} needs a token_file")
                targets[target_id] = UploadTarget(
                    id=target_id,
                    label=label,
                    kind="http",
                    base_url=base_url,
                    token_file=Path(token_text).expanduser().resolve(),
                    verify_tls=verify_tls,
                    editable=editable,
                )
            else:
                raise ValueError(f"Upload target {target_id!r} has an unsupported type")
        return targets

    def targets_response(self) -> List[Dict[str, object]]:
        return [
            {
                "id": target.id,
                "label": target.label,
                "type": target.kind,
                "baseUrl": target.base_url,
                "editable": target.editable,
            }
            for target in sorted(
                self.targets().values(), key=lambda value: value.label.lower()
            )
        ]

    def library_sessions(
        self,
        target_id: str,
        *,
        offset: int = 0,
    ) -> Dict[str, object]:
        if offset < 0 or offset > 10_000:
            raise ValueError("Upload library offset is invalid")
        target, token = self._library_target(target_id)
        response = self._library_json(
            target,
            token,
            f"/v1/library/sessions?{urlencode({'limit': 100, 'offset': offset})}",
        )
        if not isinstance(response.get("sessions"), list):
            raise RuntimeError("Upload server returned an invalid library response")
        return response

    def library_files(
        self,
        target_id: str,
        session_id: str,
        relative_path: str,
    ) -> Dict[str, object]:
        target, token = self._library_target(target_id)
        session_id = validate_config_id(session_id, "upload session")
        relative_path = self._validate_library_path(relative_path, allow_empty=True)
        query = urlencode({"path": relative_path})
        response = self._library_json(
            target,
            token,
            f"/v1/library/sessions/{session_id}/files?{query}",
        )
        if not isinstance(response.get("entries"), list):
            raise RuntimeError("Upload server returned an invalid file listing")
        return response

    def library_file(
        self,
        target_id: str,
        session_id: str,
        relative_path: str,
        *,
        max_bytes: int,
    ) -> Tuple[str, bytes]:
        target, token = self._library_target(target_id)
        session_id = validate_config_id(session_id, "upload session")
        relative_path = self._validate_library_path(relative_path, allow_empty=False)
        query = urlencode({"path": relative_path})
        return self._http_bytes(
            target,
            token,
            f"/v1/library/sessions/{session_id}/file?{query}",
            max_bytes=max_bytes,
        )

    def source_summary(
        self,
        root_id: str,
        relative_path: str,
    ) -> Dict[str, object]:
        """Calculate upload size before a job is started."""
        source = self._resolve_source(root_id, relative_path)
        files = list(self.storage.iter_regular_files(source))
        total_bytes = 0
        for source_file in files:
            total_bytes += source_file.stat().st_size
        return {
            "rootId": root_id,
            "relativePath": relative_path,
            "sourceName": source.name or "root",
            "folderName": source.name or "root",
            "sourceType": "FILE" if source.is_file() else "DIRECTORY",
            "bytesTotal": total_bytes,
            "filesTotal": len(files),
            "calculatedAt": self._timestamp(),
        }

    def verify_completed_source(self, job_id: str) -> Dict[str, object]:
        """Compare a completed HTTP upload with the current local source."""
        validate_config_id(job_id, "upload job")
        job = self.get(job_id)
        if job.get("state") != "COMPLETED":
            raise UploadConflict("Only completed uploads can be verified")
        if job.get("sourceDeletedAt") is not None:
            return {
                "jobId": job_id,
                "state": "SOURCE_DELETED",
                "matched": True,
                "deletionAllowed": False,
                "verifiedAt": job.get("verifiedAt"),
            }
        root_id, relative_path, target_id = self._job_parameters(job)
        source, target = self._resolve_source_and_target(
            root_id,
            relative_path,
            target_id,
        )
        return self._verify_completed_source_at(job, source, target)

    def delete_completed_source(
        self,
        job_id: str,
        *,
        confirmed: bool,
    ) -> Dict[str, object]:
        """Delete a local source only after a fresh remote content verification."""
        if not confirmed:
            raise UploadConfirmationRequired(
                "Source deletion requires explicit user confirmation"
            )
        validate_config_id(job_id, "upload job")
        with self._lock:
            job = self.get(job_id)
            if job.get("state") != "COMPLETED":
                raise UploadConflict("Only completed uploads can delete their source")
            if job.get("sourceDeletedAt") is not None:
                return job
            root_id, relative_path, target_id = self._job_parameters(job)
            root, resolved_source = self.storage.resolve(root_id, relative_path)
            source = self._resolve_deletion_source(
                root.path,
                relative_path,
                resolved_source,
            )
            if source == root.path:
                raise UploadConflict("A configured storage root cannot be deleted")
            if not source.exists():
                raise FileNotFoundError("Upload source was not found")
            self._assert_deletable_source(source)
            try:
                target = self.targets()[target_id]
            except KeyError as error:
                raise ValueError("Unknown upload target") from error
            if target.kind != "http":
                raise UploadConflict(
                    "Source deletion requires a verified remote upload target"
                )
            if self._source_overlaps_active_upload(source, excluding_job_id=job_id):
                raise UploadConflict("Source is being used by another active upload")

            tombstone = source.with_name(
                f".{source.name}.uploaded-{job_id[:8]}-{uuid.uuid4().hex}.deleting"
            )
            os.replace(source, tombstone)

        try:
            verification = self._verify_completed_source_at(job, tombstone, target)
            if not verification["matched"]:
                raise UploadVerificationMismatch(
                    "Local source no longer matches the completed remote upload"
                )
            self._remove_local_source(tombstone)
        except Exception:
            if tombstone.exists() and not source.exists():
                try:
                    os.replace(tombstone, source)
                except OSError:
                    pass
            raise

        deleted_at = self._timestamp()
        return self._update(
            job_id,
            sourceDeleted=True,
            sourceDeletedAt=deleted_at,
            deletionEligible=False,
        )

    def delete_library_session(
        self,
        target_id: str,
        session_id: str,
        *,
        confirmed: bool,
    ) -> Dict[str, object]:
        if not confirmed:
            raise UploadConfirmationRequired(
                "Library deletion requires explicit user confirmation"
            )
        target, token = self._library_target(target_id)
        session_id = validate_config_id(session_id, "upload session")
        response = self._http_json(
            target,
            token,
            "DELETE",
            f"/v1/library/sessions/{session_id}",
            None,
        )
        if response.get("sessionId") != session_id or response.get("state") != "DELETED":
            raise RuntimeError("Upload server returned an invalid deletion response")
        return response

    def _verify_completed_source_at(
        self,
        job: Dict[str, object],
        source: Path,
        target: UploadTarget,
    ) -> Dict[str, object]:
        if target.kind != "http":
            raise UploadConflict(
                "Source verification requires a remote upload target"
            )
        job_id = str(job["id"])
        session_id = job.get("remoteSessionId")
        if not isinstance(session_id, str):
            raise UploadConflict("Completed upload has no remote session identifier")
        validate_config_id(session_id, "upload session")
        source_name = job.get("sourceName")
        if not isinstance(source_name, str) or not source_name:
            source_name = source.name or "root"
        local = self._local_content_receipt(source, source_name)
        token = self._read_target_token(target)
        remote = self._library_json(
            target,
            token,
            f"/v1/library/sessions/{session_id}/verification",
        )
        expected = {
            "sessionId": session_id,
            "clientJobId": job_id,
            "sourceName": source_name,
            "state": "COMPLETED",
            "totalBytes": local["totalBytes"],
            "fileCount": local["fileCount"],
            "contentSha256": local["contentSha256"],
        }
        matched = all(remote.get(key) == value for key, value in expected.items())
        verified_at = self._timestamp()
        result = {
            "jobId": job_id,
            "targetId": job["targetId"],
            "remoteSessionId": session_id,
            "sourceName": source_name,
            "state": "MATCHED" if matched else "MISMATCH",
            "matched": matched,
            "deletionAllowed": matched,
            "bytesTotal": local["totalBytes"],
            "filesTotal": local["fileCount"],
            "contentSha256": local["contentSha256"],
            "verifiedAt": verified_at,
        }
        self._update(
            job_id,
            verification=result,
            verifiedAt=verified_at,
            deletionEligible=matched,
        )
        return result

    def _local_content_receipt(
        self,
        source: Path,
        source_name: str,
    ) -> Dict[str, object]:
        files = list(self.storage.iter_regular_files(source))
        entries: List[Tuple[str, int, str]] = []
        for source_file in files:
            relative_path = (
                source_name
                if source.is_file()
                else source_file.relative_to(source).as_posix()
            )
            before = source_file.stat()
            expected_size = before.st_size
            digest = hashlib.sha256()
            with source_file.open("rb") as input_file:
                while True:
                    chunk = input_file.read(4 * 1024 * 1024)
                    if not chunk:
                        break
                    digest.update(chunk)
            after = source_file.stat()
            if (
                after.st_dev,
                after.st_ino,
                after.st_size,
                after.st_mtime_ns,
            ) != (
                before.st_dev,
                before.st_ino,
                before.st_size,
                before.st_mtime_ns,
            ):
                raise OSError("Source file changed while verifying the upload")
            entries.append((relative_path, expected_size, digest.hexdigest()))
        if [path.resolve() for path in self.storage.iter_regular_files(source)] != [
            path.resolve() for path in files
        ]:
            raise OSError("Source file list changed while verifying the upload")
        entries.sort(key=lambda item: item[0])
        return {
            "totalBytes": sum(item[1] for item in entries),
            "fileCount": len(entries),
            "contentSha256": self._content_digest(source_name, entries),
        }

    @staticmethod
    def _content_digest(
        source_name: str,
        files: Iterable[Tuple[str, int, str]],
    ) -> str:
        digest = hashlib.sha256()
        digest.update(CONTENT_DIGEST_MAGIC)

        def add_field(value: bytes) -> None:
            digest.update(struct.pack(">I", len(value)))
            digest.update(value)

        add_field(source_name.encode("utf-8"))
        for relative_path, size_bytes, sha256 in files:
            add_field(relative_path.encode("utf-8"))
            digest.update(struct.pack(">Q", size_bytes))
            try:
                file_digest = bytes.fromhex(sha256)
            except ValueError as error:
                raise RuntimeError("Upload content digest is invalid") from error
            if len(file_digest) != hashlib.sha256().digest_size:
                raise RuntimeError("Upload content digest is invalid")
            digest.update(file_digest)
        return digest.hexdigest()

    def _source_overlaps_active_upload(
        self,
        source: Path,
        *,
        excluding_job_id: str,
    ) -> bool:
        for job in self.list_jobs(active_only=True):
            if job.get("id") == excluding_job_id:
                continue
            try:
                root_id, relative_path, _target_id = self._job_parameters(job)
                _root, active_source = self.storage.resolve(root_id, relative_path)
            except (ValueError, FileNotFoundError):
                continue
            try:
                source.relative_to(active_source)
                return True
            except ValueError:
                pass
            try:
                active_source.relative_to(source)
                return True
            except ValueError:
                pass
        return False

    @staticmethod
    def _assert_deletable_source(source: Path) -> None:
        source_metadata = source.lstat()
        if stat.S_ISLNK(source_metadata.st_mode):
            raise UploadConflict("Symbolic-link upload sources cannot be deleted")
        if stat.S_ISREG(source_metadata.st_mode):
            return
        if not stat.S_ISDIR(source_metadata.st_mode):
            raise UploadConflict("Upload source is not a regular file or directory")
        for directory, directory_names, file_names in os.walk(
            source,
            followlinks=False,
        ):
            base = Path(directory)
            for name in directory_names + file_names:
                metadata = (base / name).lstat()
                if stat.S_ISLNK(metadata.st_mode):
                    raise UploadConflict(
                        "Upload source contains a symbolic link that was not uploaded"
                    )
                if not stat.S_ISDIR(metadata.st_mode) and not stat.S_ISREG(
                    metadata.st_mode
                ):
                    raise UploadConflict(
                        "Upload source contains unsupported data that was not uploaded"
                    )

    @staticmethod
    def _resolve_deletion_source(
        root: Path,
        relative_path: str,
        resolved_source: Path,
    ) -> Path:
        relative = Path(relative_path.lstrip("/"))
        if ".." in relative.parts:
            raise UploadConflict("Upload source path is not safe to delete")
        candidate = root
        for part in relative.parts:
            if part in {"", "."}:
                continue
            candidate = candidate / part
            try:
                metadata = candidate.lstat()
            except FileNotFoundError as error:
                raise FileNotFoundError("Upload source was not found") from error
            if stat.S_ISLNK(metadata.st_mode):
                raise UploadConflict(
                    "Upload source path contains a symbolic link and cannot be deleted"
                )
        if candidate.resolve() != resolved_source:
            raise UploadConflict("Upload source path changed and cannot be deleted")
        return candidate

    @staticmethod
    def _remove_local_source(source: Path) -> None:
        metadata = source.lstat()
        if stat.S_ISLNK(metadata.st_mode):
            raise UploadConflict("Refusing to delete a symbolic-link upload source")
        if stat.S_ISDIR(metadata.st_mode):
            shutil.rmtree(source)
        elif stat.S_ISREG(metadata.st_mode):
            source.unlink()
        else:
            raise UploadConflict("Refusing to delete an unsupported upload source")

    def _library_target(self, target_id: str) -> Tuple[UploadTarget, str]:
        target_id = validate_config_id(target_id, "upload target")
        try:
            target = self.targets()[target_id]
        except KeyError as error:
            raise ValueError("Upload target was not found") from error
        if target.kind != "http":
            raise ValueError("Upload target does not provide a remote library")
        return target, self._read_target_token(target)

    def _library_json(
        self,
        target: UploadTarget,
        token: str,
        endpoint: str,
    ) -> Dict[str, object]:
        try:
            return self._http_json(
                target,
                token,
                "GET",
                endpoint,
                None,
                max_response_bytes=HTTP_LIBRARY_MAX_RESPONSE_BYTES,
            )
        except UploadReceiverHttpError as error:
            if error.status_code in {404, 405}:
                raise UploadLibraryUnavailable(
                    "Upload server library is not installed"
                ) from error
            raise

    @staticmethod
    def _validate_library_path(value: str, *, allow_empty: bool) -> str:
        if value == "" and allow_empty:
            return value
        if (
            not value
            or value.startswith("/")
            or "\\" in value
            or "\x00" in value
            or len(value.encode("utf-8")) > 4096
        ):
            raise ValueError("Upload library path is invalid")
        parts = value.split("/")
        if any(
            not part or part in {".", ".."} or len(part.encode("utf-8")) > 255
            for part in parts
        ):
            raise ValueError("Upload library path is invalid")
        return value

    def save_http_target(
        self,
        target_id: str,
        label: str,
        base_url: str,
        token: Optional[str],
    ) -> Dict[str, object]:
        target_id = validate_config_id(target_id, "upload target")
        normalized_label = self._validate_target_label(label)
        normalized_url = self._validate_managed_base_url(base_url)

        with self._lock:
            administrator_targets = self._load_targets(
                self.targets_path,
                editable=False,
            )
            managed = self._load_managed_target_config()
            if target_id in administrator_targets:
                raise UploadConflict(
                    "Administrator-managed upload targets cannot be changed from the app"
                )
            if self._target_has_active_jobs(target_id):
                raise UploadConflict("An active upload is using this server")

            existing = managed.get(target_id)
            old_token_path = self._managed_token_path(existing)
            new_token_path: Optional[Path] = None
            if token is None or not token.strip():
                if old_token_path is None or not old_token_path.is_file():
                    raise ValueError("A server access token is required")
                token_path = old_token_path
            else:
                token_path = self.managed_tokens_dir / (
                    f"{target_id}-{uuid.uuid4().hex}.token"
                )
                self._write_secret(token_path, self._validate_target_token(token))
                new_token_path = token_path

            managed[target_id] = {
                "label": normalized_label,
                "type": "http",
                "base_url": normalized_url,
                "token_file": str(token_path),
                "verify_tls": True,
            }
            try:
                self._write_json(self.managed_targets_path, managed)
            except Exception:
                if new_token_path is not None:
                    new_token_path.unlink(missing_ok=True)
                raise
            if new_token_path is not None and old_token_path != new_token_path:
                if old_token_path is not None:
                    old_token_path.unlink(missing_ok=True)

        target = self.targets()[target_id]
        return {
            "id": target.id,
            "label": target.label,
            "type": target.kind,
            "baseUrl": target.base_url,
            "editable": target.editable,
        }

    def delete_http_target(self, target_id: str) -> None:
        target_id = validate_config_id(target_id, "upload target")
        with self._lock:
            managed = self._load_managed_target_config()
            if target_id not in managed:
                raise KeyError(target_id)
            if self._target_has_active_jobs(target_id):
                raise UploadConflict("An active upload is using this server")

            target_config = managed.pop(target_id)
            token_path = self._managed_token_path(target_config)
            self._write_json(self.managed_targets_path, managed)
            if token_path is not None:
                token_path.unlink(missing_ok=True)

    def _target_has_active_jobs(self, target_id: str) -> bool:
        return any(
            job.get("targetId") == target_id and job.get("state") in ACTIVE_STATES
            for job in self.list_jobs(active_only=True)
        )

    def _managed_token_path(self, value: object) -> Optional[Path]:
        if value is None:
            return None
        if not isinstance(value, dict):
            raise ValueError("Managed upload target configuration is invalid")
        token_text = str(value.get("token_file", "")).strip()
        if not token_text:
            return None
        token_path = Path(token_text).expanduser().resolve()
        try:
            token_path.relative_to(self.managed_tokens_dir.resolve())
        except ValueError as error:
            raise ValueError("Managed upload target token path is invalid") from error
        return token_path

    def _load_managed_target_config(self) -> Dict[str, object]:
        if not self.managed_targets_path.exists():
            return {}
        try:
            return dict(load_json_object(self.managed_targets_path))
        except RuntimeError:
            if not self.managed_targets_path.exists():
                return {}
            raise

    @staticmethod
    def _validate_target_label(label: str) -> str:
        normalized = label.strip()
        if (
            not normalized
            or len(normalized.encode("utf-8")) > 64
            or any(ord(character) < 32 or ord(character) == 127 for character in normalized)
        ):
            raise ValueError("Upload target label must contain 1 to 64 UTF-8 bytes")
        return normalized

    @staticmethod
    def _validate_managed_base_url(base_url: str) -> str:
        normalized = base_url.strip().rstrip("/")
        parsed = urlsplit(normalized)
        try:
            parsed_port = parsed.port
        except ValueError as error:
            raise ValueError("Upload server URL has an invalid port") from error
        if (
            parsed.scheme != "https"
            or not parsed.hostname
            or parsed.username is not None
            or parsed.password is not None
            or parsed.query
            or parsed.fragment
            or (parsed_port is not None and parsed_port < 1)
        ):
            raise ValueError("Upload server must be a valid HTTPS base URL")
        hostname = parsed.hostname.lower()
        if hostname == "localhost" or hostname.endswith(".local"):
            raise ValueError("Upload server must be reachable through the public internet")
        try:
            address = ipaddress.ip_address(hostname)
        except ValueError:
            address = None
        if address is not None and not address.is_global:
            raise ValueError("Upload server IP address must be public")
        return normalized

    @staticmethod
    def _validate_target_token(token: str) -> str:
        normalized = token.strip()
        if not normalized or len(normalized) > 4096 or "\n" in normalized or "\r" in normalized:
            raise ValueError("Upload server token must be a single line")
        return normalized

    def _write_secret(self, path: Path, value: str) -> None:
        path.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
        temporary = path.with_name(f".{path.name}.{uuid.uuid4().hex}.tmp")
        try:
            with temporary.open("x", encoding="utf-8") as output:
                output.write(value)
                output.write("\n")
                output.flush()
                os.fsync(output.fileno())
            os.chmod(temporary, 0o600)
            os.replace(temporary, path)
        finally:
            try:
                temporary.unlink()
            except FileNotFoundError:
                pass

    def _write_json(self, path: Path, value: Dict[str, object]) -> None:
        path.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
        temporary = path.with_name(f".{path.name}.{uuid.uuid4().hex}.tmp")
        try:
            with temporary.open("x", encoding="utf-8") as output:
                json.dump(value, output, ensure_ascii=True, indent=2, sort_keys=True)
                output.write("\n")
                output.flush()
                os.fsync(output.fileno())
            os.chmod(temporary, 0o600)
            os.replace(temporary, path)
        finally:
            try:
                temporary.unlink()
            except FileNotFoundError:
                pass

    def start(self, root_id: str, relative_path: str, target_id: str) -> Dict[str, object]:
        job_id = uuid.uuid4().hex
        job = self._new_job(job_id, root_id, relative_path, target_id)
        cancellation = threading.Event()
        with self._lock:
            source, target = self._resolve_source_and_target(
                root_id, relative_path, target_id
            )
            job["sourceName"] = source.name or "root"
            job["folderName"] = source.name or "root"
            self._reserve_and_save(job_id, cancellation, job)

        self._launch_worker(job_id, source, target, cancellation)
        return job

    def retry(self, job_id: str) -> Dict[str, object]:
        validate_config_id(job_id, "upload job")
        with self._lock:
            job = self.get(job_id)
            if job.get("state") != "FAILED":
                raise UploadConflict("Only failed uploads can be retried")
            root_id, relative_path, target_id = self._job_parameters(job)
            source, target = self._resolve_source_and_target(
                root_id, relative_path, target_id
            )
            cancellation = threading.Event()
            self._reset_for_retry(job)
            self._reserve_and_save(job_id, cancellation, job)

        self._launch_worker(job_id, source, target, cancellation)
        return job

    def _resolve_source_and_target(
        self, root_id: str, relative_path: str, target_id: str
    ) -> Tuple[Path, UploadTarget]:
        source = self._resolve_source(root_id, relative_path)
        try:
            target = self.targets()[target_id]
        except KeyError as error:
            raise ValueError("Unknown upload target") from error

        if target.kind == "local" and target.path is not None:
            try:
                target.path.relative_to(source)
            except ValueError:
                pass
            else:
                raise ValueError("Upload target cannot be inside the source path")
        return source, target

    def _resolve_source(self, root_id: str, relative_path: str) -> Path:
        _, source = self.storage.resolve(root_id, relative_path)
        if not source.exists():
            raise FileNotFoundError("Upload source was not found")
        if not source.is_file() and not source.is_dir():
            raise ValueError("Upload source must be a regular file or directory")
        if source.is_symlink():
            raise ValueError("Symbolic links cannot be uploaded")
        return source

    def _reserve_worker(
        self, job_id: str, cancellation: threading.Event
    ) -> None:
        if job_id in self._cancellations:
            raise UploadConflict("The previous upload worker is still stopping")
        if len(self._cancellations) >= self.max_concurrent_jobs:
            raise UploadCapacityExceeded(
                f"At most {self.max_concurrent_jobs} uploads can run at once"
            )
        self._cancellations[job_id] = cancellation

    def _reserve_and_save(
        self,
        job_id: str,
        cancellation: threading.Event,
        job: Dict[str, object],
    ) -> None:
        self._reserve_worker(job_id, cancellation)
        try:
            self._save_job(job)
        except Exception:
            self._cancellations.pop(job_id, None)
            raise

    def _launch_worker(
        self,
        job_id: str,
        source: Path,
        target: UploadTarget,
        cancellation: threading.Event,
    ) -> None:
        worker = threading.Thread(
            target=self._run_job,
            args=(job_id, source, target, cancellation),
            name=f"upload-{job_id[:8]}",
            daemon=True,
        )
        worker.start()

    def list_jobs(self, active_only: bool = False) -> List[Dict[str, object]]:
        jobs = []
        for path in self.jobs_dir.glob("*.json"):
            try:
                job = self._load_path(path)
                if not active_only or job.get("state") in ACTIVE_STATES:
                    jobs.append(job)
            except (OSError, ValueError, json.JSONDecodeError):
                continue
        jobs.sort(key=lambda job: str(job.get("createdAt", "")), reverse=True)
        return jobs

    def get(self, job_id: str) -> Dict[str, object]:
        validate_config_id(job_id, "upload job")
        path = self.jobs_dir / f"{job_id}.json"
        if not path.exists():
            raise KeyError(job_id)
        return self._load_path(path)

    def cancel(self, job_id: str) -> Dict[str, object]:
        with self._lock:
            job = self.get(job_id)
            if job["state"] in TERMINAL_STATES:
                return job
            event = self._cancellations.get(job_id)
            if event is not None:
                event.set()
            job["state"] = "CANCELLED"
            job["errorMessage"] = None
            job["updatedAt"] = self._timestamp()
            self._save_job(job)
        return self.get(job_id)

    def _run_job(
        self,
        job_id: str,
        source: Path,
        target: UploadTarget,
        cancellation: threading.Event,
    ) -> None:
        try:
            self._raise_if_cancelled(job_id, cancellation)
            self._update(
                job_id,
                state="SCANNING",
                sourceName=source.name or "root",
                folderName=source.name or "root",
            )
            files = list(self.storage.iter_regular_files(source))
            self._raise_if_cancelled(job_id, cancellation)
            bytes_total = sum(path.stat().st_size for path in files)
            self._update(
                job_id,
                bytesTotal=bytes_total,
                bytesTransferred=0,
                bytesPrepared=0,
                filesTotal=len(files),
                filesTransferred=0,
                filesPrepared=0,
            )

            if target.kind == "local":
                self._begin_transfer(job_id)
                self._copy_to_local_target(
                    job_id, source, files, target, cancellation
                )
            else:
                self._upload_to_http_target(
                    job_id, source, files, target, cancellation
                )

            self._finish_success(
                job_id,
                cancellation,
                bytes_total,
                len(files),
            )
        except UploadCancelled:
            pass
        except Exception as error:
            if cancellation.is_set():
                self._update(
                    job_id,
                    state="CANCELLED",
                    currentFile=None,
                    errorMessage=None,
                )
            else:
                self._update(
                    job_id,
                    state="FAILED",
                    currentFile=None,
                    errorMessage=self._public_error(error),
                )
        finally:
            with self._lock:
                if self._cancellations.get(job_id) is cancellation:
                    self._cancellations.pop(job_id, None)
                self._transfer_progress.pop(job_id, None)

    def _copy_to_local_target(
        self,
        job_id: str,
        source: Path,
        files: List[Path],
        target: UploadTarget,
        cancellation: threading.Event,
    ) -> None:
        if target.path is None:
            raise ValueError("Local upload target path is missing")
        target.path.mkdir(parents=True, exist_ok=True)
        destination_root = target.path / self._destination_name(source, job_id)
        destination_root.mkdir(parents=True, exist_ok=False)

        transferred = 0
        for index, source_file in enumerate(files, start=1):
            self._raise_if_cancelled(job_id, cancellation)
            relative = self._relative_file_path(source, source_file)
            destination = destination_root / relative
            destination.parent.mkdir(parents=True, exist_ok=True)
            self._update(job_id, currentFile=relative.as_posix())

            with source_file.open("rb") as source_stream, destination.open("wb") as target_stream:
                while True:
                    self._raise_if_cancelled(job_id, cancellation)
                    chunk = source_stream.read(1024 * 1024)
                    if not chunk:
                        break
                    target_stream.write(chunk)
                    transferred += len(chunk)
                    self._record_transfer(
                        job_id,
                        transferred,
                        newly_acknowledged=len(chunk),
                    )
            shutil.copystat(source_file, destination)
            self._update(job_id, filesTransferred=index)

    def _upload_to_http_target(
        self,
        job_id: str,
        source: Path,
        files: List[Path],
        target: UploadTarget,
        cancellation: threading.Event,
    ) -> None:
        token = self._read_target_token(target)
        capabilities = self._http_receiver_capabilities(target, token)
        deferred_hashes = self._supports_deferred_file_hashes(capabilities)
        if deferred_hashes:
            manifest_files = [
                {
                    "path": self._relative_file_path(source, path).as_posix(),
                    "sizeBytes": path.stat().st_size,
                }
                for path in files
            ]
        else:
            manifest_files = self._build_manifest_files(
                job_id,
                source,
                files,
                cancellation,
            )

        manifest = {
            "deviceId": self.device_id,
            "clientJobId": job_id,
            "sourceName": source.name or "root",
            "files": manifest_files,
        }
        if deferred_hashes:
            manifest["hashMode"] = DEFERRED_FILE_HASH_MODE
        response = self._http_json_with_retry(
            target, token, "POST", "/v1/upload-sessions", manifest
        )
        session_id = str(response.get("sessionId", ""))
        try:
            validate_config_id(session_id, "upload session")
        except ValueError as error:
            raise RuntimeError("Upload receiver returned an invalid session id")
        self._update(
            job_id,
            remoteSessionId=session_id,
        )
        self._begin_transfer(job_id)
        self._update(
            job_id,
            state="UPLOADING",
            bytesTransferred=0,
            filesTransferred=0,
            currentFile=None,
        )

        try:
            batch_limits = self._file_batch_limits(response)
            if batch_limits is None:
                self._upload_http_files_legacy(
                    job_id=job_id,
                    source=source,
                    files=files,
                    target=target,
                    token=token,
                    session_id=session_id,
                    cancellation=cancellation,
                )
            else:
                self._upload_http_files_batched(
                    job_id=job_id,
                    source=source,
                    files=files,
                    target=target,
                    token=token,
                    session_id=session_id,
                    cancellation=cancellation,
                    limits=batch_limits,
                )
            self._raise_if_cancelled(job_id, cancellation)
            self._http_json_with_retry(
                target,
                token,
                "POST",
                f"/v1/upload-sessions/{session_id}/complete",
                {},
                response_timeout=HTTP_COMPLETE_RESPONSE_TIMEOUT,
            )
        except Exception as error:
            if not isinstance(error, UploadCancelled) and not cancellation.is_set():
                raise
            try:
                self._http_json(
                    target,
                    token,
                    "DELETE",
                    f"/v1/upload-sessions/{session_id}",
                    None,
                )
            except Exception:
                pass
            raise UploadCancelled() from error

    def _build_manifest_files(
        self,
        job_id: str,
        source: Path,
        files: List[Path],
        cancellation: threading.Event,
    ) -> List[Dict[str, object]]:
        manifest_files: List[Dict[str, object]] = []
        bytes_prepared = 0
        files_prepared = 0
        last_report = 0.0

        for path in files:
            self._raise_if_cancelled(job_id, cancellation)
            relative_path = self._relative_file_path(source, path).as_posix()
            expected_size = path.stat().st_size
            file_start = bytes_prepared

            def report_chunk(size: int) -> None:
                nonlocal bytes_prepared, last_report
                bytes_prepared += size
                now = time.monotonic()
                if now - last_report >= HASH_PROGRESS_INTERVAL_SECONDS:
                    self._update(
                        job_id,
                        bytesPrepared=bytes_prepared,
                        filesPrepared=files_prepared,
                        currentFile=relative_path,
                    )
                    last_report = now

            digest = self._sha256_file(
                path,
                cancellation,
                job_id,
                report_chunk,
            )
            if bytes_prepared - file_start != expected_size:
                raise OSError("Source file changed while preparing upload metadata")
            files_prepared += 1
            manifest_files.append(
                {
                    "path": relative_path,
                    "sizeBytes": expected_size,
                    "sha256": digest,
                }
            )

        self._update(
            job_id,
            bytesPrepared=bytes_prepared,
            filesPrepared=files_prepared,
            currentFile=None,
        )
        return manifest_files

    def _http_receiver_capabilities(
        self,
        target: UploadTarget,
        token: str,
    ) -> Dict[str, object]:
        try:
            return self._http_json(
                target,
                token,
                "GET",
                "/v1/capabilities",
                None,
            )
        except UploadReceiverHttpError as error:
            if error.status_code in {404, 405}:
                return {}
            raise

    @staticmethod
    def _supports_deferred_file_hashes(capabilities: Dict[str, object]) -> bool:
        raw = capabilities.get("deferredFileHashes")
        return (
            isinstance(raw, dict)
            and raw.get("version") == DEFERRED_FILE_HASH_VERSION
            and raw.get("manifestHashMode") == DEFERRED_FILE_HASH_MODE
        )

    def _upload_http_files_legacy(
        self,
        job_id: str,
        source: Path,
        files: List[Path],
        target: UploadTarget,
        token: str,
        session_id: str,
        cancellation: threading.Event,
    ) -> None:
        transferred = 0
        for index, source_file in enumerate(files, start=1):
            self._raise_if_cancelled(job_id, cancellation)
            relative = self._relative_file_path(source, source_file).as_posix()
            self._update(job_id, currentFile=relative)
            transferred = self._http_put_file_resumable(
                target=target,
                token=token,
                session_id=session_id,
                relative_path=relative,
                source_file=source_file,
                transferred=transferred,
                job_id=job_id,
                cancellation=cancellation,
            )
            self._update(job_id, filesTransferred=index)

    def _upload_http_files_batched(
        self,
        job_id: str,
        source: Path,
        files: List[Path],
        target: UploadTarget,
        token: str,
        session_id: str,
        cancellation: threading.Event,
        limits: FileBatchLimits,
    ) -> None:
        transferred = 0
        files_transferred = 0
        for group in self._file_batch_groups(source, files, limits):
            self._raise_if_cancelled(job_id, cancellation)
            relative_paths = [
                self._relative_file_path(source, path).as_posix() for path in group
            ]
            offsets = self._http_get_file_offsets(
                target,
                token,
                session_id,
                relative_paths,
            )
            batch_files: List[Tuple[str, Path, int]] = []
            for source_file, relative_path in zip(group, relative_paths):
                self._raise_if_cancelled(job_id, cancellation)
                size = source_file.stat().st_size
                offset = offsets[relative_path]
                if offset < 0 or offset > size:
                    raise RuntimeError(
                        "Upload receiver returned an out-of-range file offset"
                    )
                if offset == size:
                    transferred += size
                    files_transferred += 1
                    self._record_transfer(
                        job_id,
                        transferred,
                        newly_acknowledged=0,
                        filesTransferred=files_transferred,
                    )
                elif (
                    offset == 0
                    and self._file_batch_entry_size(relative_path, size)
                    + len(FILE_BATCH_MAGIC)
                    + 4
                    <= limits.max_bytes
                ):
                    batch_files.append((relative_path, source_file, size))
                else:
                    self._update(job_id, currentFile=relative_path)
                    transferred = self._http_put_file_from_offset(
                        target=target,
                        token=token,
                        session_id=session_id,
                        relative_path=relative_path,
                        source_file=source_file,
                        file_size=size,
                        offset=offset,
                        transferred=transferred,
                        job_id=job_id,
                        cancellation=cancellation,
                    )
                    files_transferred += 1
                    self._update(job_id, filesTransferred=files_transferred)

            if not batch_files:
                continue

            label = batch_files[0][0]
            if len(batch_files) > 1:
                label += f" (+{len(batch_files) - 1} files)"
            self._update(job_id, currentFile=label)
            body = self._encode_file_batch(batch_files, cancellation, job_id)
            acknowledged = self._http_put_file_batch_with_retry(
                target,
                token,
                session_id,
                body,
                [path for path, _source_file, _size in batch_files],
            )
            expected_offsets = {path: size for path, _source_file, size in batch_files}
            if acknowledged != expected_offsets:
                raise RuntimeError("Upload receiver returned invalid batch offsets")
            transferred += sum(expected_offsets.values())
            files_transferred += len(batch_files)
            self._record_transfer(
                job_id,
                transferred,
                newly_acknowledged=sum(expected_offsets.values()),
                filesTransferred=files_transferred,
            )

    def _file_batch_groups(
        self,
        source: Path,
        files: List[Path],
        limits: FileBatchLimits,
    ) -> Iterable[List[Path]]:
        base_size = len(FILE_BATCH_MAGIC) + 4
        group: List[Path] = []
        group_size = base_size
        for source_file in files:
            relative_path = self._relative_file_path(source, source_file).as_posix()
            entry_size = self._file_batch_entry_size(
                relative_path,
                source_file.stat().st_size,
            )
            if group and (
                len(group) >= limits.max_files
                or group_size + entry_size > limits.max_bytes
            ):
                yield group
                group = []
                group_size = base_size
            group.append(source_file)
            group_size += entry_size
            if group_size > limits.max_bytes:
                yield group
                group = []
                group_size = base_size
        if group:
            yield group

    @staticmethod
    def _file_batch_limits(response: Dict[str, object]) -> Optional[FileBatchLimits]:
        raw = response.get("fileBatch")
        if not isinstance(raw, dict) or raw.get("version") != FILE_BATCH_VERSION:
            return None
        max_bytes = raw.get("maxBytes")
        max_files = raw.get("maxFiles")
        if (
            isinstance(max_bytes, bool)
            or not isinstance(max_bytes, int)
            or isinstance(max_files, bool)
            or not isinstance(max_files, int)
        ):
            return None
        max_bytes = min(max_bytes, CLIENT_MAX_BATCH_BYTES)
        max_files = min(max_files, CLIENT_MAX_BATCH_FILES)
        if max_bytes <= len(FILE_BATCH_MAGIC) + 4 or max_files < 1:
            return None
        return FileBatchLimits(max_bytes=max_bytes, max_files=max_files)

    @staticmethod
    def _file_batch_entry_size(relative_path: str, size: int) -> int:
        return 4 + 8 + len(relative_path.encode("utf-8")) + size

    def _encode_file_batch(
        self,
        files: List[Tuple[str, Path, int]],
        cancellation: threading.Event,
        job_id: str,
    ) -> bytes:
        body = bytearray(FILE_BATCH_MAGIC)
        body.extend(struct.pack(">I", len(files)))
        for relative_path, source_file, expected_size in files:
            self._raise_if_cancelled(job_id, cancellation)
            path_bytes = relative_path.encode("utf-8")
            body.extend(struct.pack(">IQ", len(path_bytes), expected_size))
            body.extend(path_bytes)
            bytes_read = 0
            with source_file.open("rb") as source_stream:
                while True:
                    self._raise_if_cancelled(job_id, cancellation)
                    chunk = source_stream.read(1024 * 1024)
                    if not chunk:
                        break
                    body.extend(chunk)
                    bytes_read += len(chunk)
            if bytes_read != expected_size:
                raise OSError("Source file changed while preparing an upload batch")
        return bytes(body)

    def _http_put_file_resumable(
        self,
        target: UploadTarget,
        token: str,
        session_id: str,
        relative_path: str,
        source_file: Path,
        transferred: int,
        job_id: str,
        cancellation: threading.Event,
    ) -> int:
        size = source_file.stat().st_size
        status_response = self._http_json_with_retry(
            target,
            token,
            "GET",
            f"/v1/upload-sessions/{session_id}/files/offset?"
            + urlencode({"path": relative_path}),
            None,
        )
        try:
            offset = int(status_response.get("nextOffset", 0))
        except (TypeError, ValueError) as error:
            raise RuntimeError("Upload receiver returned an invalid file offset") from error
        if offset < 0 or offset > size:
            raise RuntimeError("Upload receiver returned an out-of-range file offset")

        return self._http_put_file_from_offset(
            target=target,
            token=token,
            session_id=session_id,
            relative_path=relative_path,
            source_file=source_file,
            file_size=size,
            offset=offset,
            transferred=transferred,
            job_id=job_id,
            cancellation=cancellation,
        )

    def _http_put_file_from_offset(
        self,
        target: UploadTarget,
        token: str,
        session_id: str,
        relative_path: str,
        source_file: Path,
        file_size: int,
        offset: int,
        transferred: int,
        job_id: str,
        cancellation: threading.Event,
    ) -> int:
        if source_file.stat().st_size != file_size:
            raise OSError("Source file changed during upload")
        transferred += offset
        self._record_transfer(job_id, transferred, newly_acknowledged=0)
        with source_file.open("rb") as source_stream:
            source_stream.seek(offset)
            while offset < file_size:
                self._raise_if_cancelled(job_id, cancellation)
                chunk = source_stream.read(min(HTTP_CHUNK_SIZE, file_size - offset))
                if not chunk:
                    raise OSError("Source file ended before its declared size")

                next_offset = self._put_chunk_with_retry(
                    target=target,
                    token=token,
                    session_id=session_id,
                    relative_path=relative_path,
                    file_size=file_size,
                    offset=offset,
                    chunk=chunk,
                )
                if next_offset <= offset or next_offset > offset + len(chunk):
                    raise RuntimeError("Upload receiver acknowledged an invalid offset")

                acknowledged = next_offset - offset
                transferred += acknowledged
                offset = next_offset
                source_stream.seek(offset)
                self._record_transfer(
                    job_id,
                    transferred,
                    newly_acknowledged=acknowledged,
                )

        return transferred

    def _http_get_file_offsets(
        self,
        target: UploadTarget,
        token: str,
        session_id: str,
        relative_paths: List[str],
    ) -> Dict[str, int]:
        response = self._http_json_with_retry(
            target,
            token,
            "POST",
            f"/v1/upload-sessions/{session_id}/files/offsets",
            {"paths": relative_paths},
        )
        return self._parse_file_offsets(response, relative_paths)

    @staticmethod
    def _parse_file_offsets(
        response: Dict[str, object],
        expected_paths: List[str],
    ) -> Dict[str, int]:
        raw_files = response.get("files")
        if not isinstance(raw_files, list) or len(raw_files) != len(expected_paths):
            raise RuntimeError("Upload receiver returned invalid batch offsets")
        offsets: Dict[str, int] = {}
        for item in raw_files:
            if not isinstance(item, dict):
                raise RuntimeError("Upload receiver returned invalid batch offsets")
            path = item.get("path")
            next_offset = item.get("nextOffset")
            if (
                not isinstance(path, str)
                or isinstance(next_offset, bool)
                or not isinstance(next_offset, int)
                or path in offsets
            ):
                raise RuntimeError("Upload receiver returned invalid batch offsets")
            offsets[path] = next_offset
        if set(offsets) != set(expected_paths):
            raise RuntimeError("Upload receiver returned invalid batch offsets")
        return offsets

    def _http_put_file_batch_with_retry(
        self,
        target: UploadTarget,
        token: str,
        session_id: str,
        body: bytes,
        expected_paths: List[str],
    ) -> Dict[str, int]:
        last_error: Optional[Exception] = None
        for delay in HTTP_RETRY_DELAYS:
            if delay:
                time.sleep(delay)
            try:
                response = self._http_put_file_batch(
                    target,
                    token,
                    session_id,
                    body,
                )
                return self._parse_file_offsets(response, expected_paths)
            except (OSError, http.client.HTTPException, RuntimeError) as error:
                last_error = error
        raise RuntimeError("External upload batch failed after multiple retries") from last_error

    def _http_put_file_batch(
        self,
        target: UploadTarget,
        token: str,
        session_id: str,
        body: bytes,
    ) -> Dict[str, object]:
        connection, base_path = self._http_connection(target)
        request_path = f"{base_path}/v1/upload-sessions/{session_id}/files/batch"
        try:
            connection.request(
                "PUT",
                request_path,
                body=body,
                headers={
                    "Authorization": f"Bearer {token}",
                    "Content-Type": "application/vnd.jetson.upload-batch-v1",
                    "Content-Length": str(len(body)),
                    "X-Batch-SHA256": hashlib.sha256(body).hexdigest(),
                },
            )
            response = connection.getresponse()
            response_body = response.read(4 * 1024 * 1024)
            if response.status < 200 or response.status >= 300:
                raise RuntimeError("Upload receiver rejected a file batch")
            parsed = json.loads(response_body.decode("utf-8"))
            if not isinstance(parsed, dict):
                raise RuntimeError("Upload receiver returned an invalid batch response")
            return parsed
        except (UnicodeDecodeError, json.JSONDecodeError) as error:
            raise RuntimeError("Upload receiver returned an invalid batch response") from error
        finally:
            connection.close()

    def _put_chunk_with_retry(
        self,
        target: UploadTarget,
        token: str,
        session_id: str,
        relative_path: str,
        file_size: int,
        offset: int,
        chunk: bytes,
    ) -> int:
        last_error: Optional[Exception] = None
        for delay in HTTP_RETRY_DELAYS:
            if delay:
                time.sleep(delay)
            try:
                return self._http_put_chunk(
                    target,
                    token,
                    session_id,
                    relative_path,
                    file_size,
                    offset,
                    chunk,
                )
            except (OSError, http.client.HTTPException, RuntimeError) as error:
                last_error = error
                try:
                    status_response = self._http_json(
                        target,
                        token,
                        "GET",
                        f"/v1/upload-sessions/{session_id}/files/offset?"
                        + urlencode({"path": relative_path}),
                        None,
                    )
                    remote_offset = int(status_response.get("nextOffset", -1))
                    if remote_offset > offset:
                        return remote_offset
                except Exception:
                    pass
        raise RuntimeError("External upload failed after multiple retries") from last_error

    def _http_put_chunk(
        self,
        target: UploadTarget,
        token: str,
        session_id: str,
        relative_path: str,
        file_size: int,
        offset: int,
        chunk: bytes,
    ) -> int:
        connection, base_path = self._http_connection(target)
        request_path = (
            f"{base_path}/v1/upload-sessions/{session_id}/files?"
            + urlencode({"path": relative_path, "offset": offset})
        )
        end_offset = offset + len(chunk) - 1
        try:
            connection.request(
                "PUT",
                request_path,
                body=chunk,
                headers={
                    "Authorization": f"Bearer {token}",
                    "Content-Type": "application/octet-stream",
                    "Content-Length": str(len(chunk)),
                    "Content-Range": f"bytes {offset}-{end_offset}/{file_size}",
                    "X-Chunk-SHA256": hashlib.sha256(chunk).hexdigest(),
                },
            )
            response = connection.getresponse()
            response_body = response.read(1024 * 1024)
            if response.status < 200 or response.status >= 300:
                raise RuntimeError("Upload receiver rejected a data chunk")
            parsed = json.loads(response_body.decode("utf-8"))
            return int(parsed["nextOffset"])
        except (KeyError, TypeError, ValueError, json.JSONDecodeError) as error:
            raise RuntimeError("Upload receiver returned an invalid chunk response") from error
        finally:
            connection.close()

    def _http_json(
        self,
        target: UploadTarget,
        token: str,
        method: str,
        endpoint: str,
        value: Optional[Dict[str, object]],
        response_timeout: Optional[float] = None,
        max_response_bytes: int = HTTP_JSON_MAX_RESPONSE_BYTES,
    ) -> Dict[str, object]:
        if max_response_bytes < 1:
            raise ValueError("HTTP response size limit is invalid")
        connection, base_path = self._http_connection(target)
        body = None if value is None else json.dumps(
            value, ensure_ascii=True, separators=(",", ":")
        ).encode("utf-8")
        headers = {"Authorization": f"Bearer {token}", "Accept": "application/json"}
        if body is not None:
            headers["Content-Type"] = "application/json"
            headers["Content-Length"] = str(len(body))
        try:
            connection.request(method, f"{base_path}{endpoint}", body=body, headers=headers)
            if response_timeout is not None and connection.sock is not None:
                connection.sock.settimeout(response_timeout)
            response = connection.getresponse()
            if response.status < 200 or response.status >= 300:
                raise UploadReceiverHttpError(response.status)
            response_body = response.read(max_response_bytes + 1)
            if len(response_body) > max_response_bytes:
                raise RuntimeError("Upload receiver response is too large")
            if not response_body:
                return {}
            parsed = json.loads(response_body.decode("utf-8"))
            if not isinstance(parsed, dict):
                raise RuntimeError("Upload receiver returned an invalid response")
            return parsed
        finally:
            connection.close()

    def _http_json_with_retry(
        self,
        target: UploadTarget,
        token: str,
        method: str,
        endpoint: str,
        value: Optional[Dict[str, object]],
        response_timeout: Optional[float] = None,
    ) -> Dict[str, object]:
        last_error: Optional[Exception] = None
        for delay in HTTP_RETRY_DELAYS:
            if delay:
                time.sleep(delay)
            try:
                return self._http_json(
                    target,
                    token,
                    method,
                    endpoint,
                    value,
                    response_timeout=response_timeout,
                )
            except (OSError, http.client.HTTPException, RuntimeError) as error:
                last_error = error
        raise RuntimeError("External upload request failed after multiple retries") from last_error

    def _http_bytes(
        self,
        target: UploadTarget,
        token: str,
        endpoint: str,
        *,
        max_bytes: int,
    ) -> Tuple[str, bytes]:
        if max_bytes < 1:
            raise ValueError("Preview size limit is invalid")
        connection, base_path = self._http_connection(target)
        try:
            connection.request(
                "GET",
                f"{base_path}{endpoint}",
                headers={
                    "Authorization": f"Bearer {token}",
                    "Accept": "*/*",
                },
            )
            response = connection.getresponse()
            if response.status < 200 or response.status >= 300:
                if response.status in {404, 405}:
                    raise UploadLibraryUnavailable(
                        "Upload server library file is unavailable"
                    )
                if response.status == 413:
                    raise FileTooLarge("Upload server file is too large to preview")
                raise UploadReceiverHttpError(response.status)
            declared = response.getheader("Content-Length")
            if declared is not None:
                try:
                    declared_size = int(declared)
                except ValueError as error:
                    raise RuntimeError(
                        "Upload server returned invalid file metadata"
                    ) from error
                if declared_size > max_bytes:
                    raise FileTooLarge("Upload server file is too large to preview")
            body = response.read(max_bytes + 1)
            if len(body) > max_bytes:
                raise FileTooLarge("Upload server file is too large to preview")
            media_type = response.getheader("Content-Type", "application/octet-stream")
            return media_type.split(";", 1)[0].strip(), body
        finally:
            connection.close()

    @staticmethod
    def _http_connection(target: UploadTarget) -> Tuple[http.client.HTTPConnection, str]:
        if target.base_url is None:
            raise ValueError("HTTP upload target URL is missing")
        parsed = urlsplit(target.base_url)
        timeout = 60
        if parsed.scheme == "https":
            context = (
                ssl.create_default_context()
                if target.verify_tls
                else ssl._create_unverified_context()
            )
            connection = http.client.HTTPSConnection(
                parsed.hostname, parsed.port or 443, timeout=timeout, context=context
            )
        else:
            connection = http.client.HTTPConnection(
                parsed.hostname, parsed.port or 80, timeout=timeout
            )
        return connection, parsed.path.rstrip("/")

    @staticmethod
    def _read_target_token(target: UploadTarget) -> str:
        if target.token_file is None:
            raise ValueError("HTTP upload target token file is missing")
        token = target.token_file.read_text(encoding="utf-8").strip()
        if not token or len(token) > 4096 or "\n" in token:
            raise ValueError("HTTP upload target token is invalid")
        return token

    @staticmethod
    def _relative_file_path(source: Path, source_file: Path) -> Path:
        return Path(source_file.name) if source.is_file() else source_file.relative_to(source)

    @staticmethod
    def _sha256_file(
        path: Path,
        cancellation: threading.Event,
        job_id: str,
        on_chunk: Optional[Callable[[int], None]] = None,
    ) -> str:
        digest = hashlib.sha256()
        with path.open("rb") as source:
            while True:
                if cancellation.is_set():
                    raise UploadCancelled()
                chunk = source.read(1024 * 1024)
                if not chunk:
                    return digest.hexdigest()
                digest.update(chunk)
                if on_chunk is not None:
                    on_chunk(len(chunk))

    def _begin_transfer(self, job_id: str) -> None:
        with self._lock:
            self._transfer_progress[job_id] = TransferProgress(
                started_at=time.monotonic()
            )
        self._update(
            job_id,
            state="UPLOADING",
            bytesTransferred=0,
            filesTransferred=0,
            throughputBytesPerSecond=0,
            etaSeconds=None,
            transferStartedAt=self._timestamp(),
        )

    def _record_transfer(
        self,
        job_id: str,
        bytes_transferred: int,
        *,
        newly_acknowledged: int,
        **changes: object,
    ) -> Dict[str, object]:
        if bytes_transferred < 0 or newly_acknowledged < 0:
            raise ValueError("Upload progress cannot be negative")
        with self._lock:
            progress = self._transfer_progress.get(job_id)
            if progress is None:
                progress = TransferProgress(started_at=time.monotonic())
                self._transfer_progress[job_id] = progress
            progress.acknowledged_this_run += newly_acknowledged
            elapsed = max(time.monotonic() - progress.started_at, 0.001)
            throughput = int(progress.acknowledged_this_run / elapsed)
            job = self.get(job_id)
            raw_total = job.get("bytesTotal")
            remaining = (
                max(int(raw_total) - bytes_transferred, 0)
                if isinstance(raw_total, int) and not isinstance(raw_total, bool)
                else None
            )
            eta_seconds = (
                math.ceil(remaining / throughput)
                if remaining is not None and throughput > 0
                else None
            )
            changes.update(
                bytesTransferred=bytes_transferred,
                throughputBytesPerSecond=throughput,
                etaSeconds=eta_seconds,
            )
            job.update(changes)
            job["updatedAt"] = self._timestamp()
            self._save_job(job)
            return job

    def _raise_if_cancelled(
        self, job_id: str, cancellation: threading.Event
    ) -> None:
        if cancellation.is_set():
            self._update(job_id, state="CANCELLED", errorMessage=None)
            raise UploadCancelled()

    def _finish_success(
        self,
        job_id: str,
        cancellation: threading.Event,
        bytes_total: int,
        files_total: int,
    ) -> None:
        with self._lock:
            job = self.get(job_id)
            if cancellation.is_set():
                job.update(state="CANCELLED", errorMessage=None)
                job["updatedAt"] = self._timestamp()
                self._save_job(job)
                raise UploadCancelled()
            job.update(
                state="COMPLETED",
                bytesTransferred=bytes_total,
                filesTransferred=files_total,
                etaSeconds=0,
                currentFile=None,
                errorMessage=None,
            )
            job["updatedAt"] = self._timestamp()
            self._save_job(job)

    def _update(self, job_id: str, **changes: object) -> Dict[str, object]:
        with self._lock:
            job = self.get(job_id)
            job.update(changes)
            job["updatedAt"] = self._timestamp()
            self._save_job(job)
            return job

    def _save_job(self, job: Dict[str, object]) -> None:
        path = self.jobs_dir / f"{job['id']}.json"
        temporary = self.jobs_dir / f".{job['id']}.tmp"
        with temporary.open("w", encoding="utf-8") as output:
            json.dump(job, output, ensure_ascii=True, separators=(",", ":"))
            output.flush()
            os.fsync(output.fileno())
        os.replace(temporary, path)

    @staticmethod
    def _load_path(path: Path) -> Dict[str, object]:
        with path.open("r", encoding="utf-8") as source:
            value = json.load(source)
        if not isinstance(value, dict):
            raise ValueError("Upload job is not a JSON object")
        return value

    def _recover_interrupted_jobs(self) -> None:
        recovered = []
        for job in self.list_jobs():
            if job.get("state") in ACTIVE_STATES:
                job_id: Optional[str] = None
                try:
                    if not isinstance(job.get("id"), str):
                        raise ValueError("Stored upload job is invalid")
                    job_id = job["id"]
                    validate_config_id(job_id, "upload job")
                    root_id, relative_path, target_id = self._job_parameters(job)
                    source, target = self._resolve_source_and_target(
                        root_id, relative_path, target_id
                    )
                    cancellation = threading.Event()
                    self._reset_for_retry(job)
                    self._reserve_and_save(job_id, cancellation, job)
                    recovered.append((job_id, source, target, cancellation))
                except Exception as error:
                    if job_id is not None:
                        self._cancellations.pop(job_id, None)
                    job["state"] = "FAILED"
                    job["currentFile"] = None
                    job["errorMessage"] = self._public_error(error)
                    job["updatedAt"] = self._timestamp()
                    self._save_job(job)

        for job_id, source, target, cancellation in recovered:
            self._launch_worker(job_id, source, target, cancellation)

    @staticmethod
    def _job_parameters(job: Dict[str, object]) -> Tuple[str, str, str]:
        values = tuple(job.get(key) for key in ("rootId", "relativePath", "targetId"))
        if any(not isinstance(value, str) for value in values):
            raise ValueError("Stored upload job is invalid")
        return values  # type: ignore[return-value]

    @staticmethod
    def _reset_for_retry(job: Dict[str, object]) -> None:
        job.update(
            state="QUEUED",
            bytesTotal=None,
            bytesTransferred=0,
            bytesPrepared=0,
            filesTotal=None,
            filesTransferred=0,
            filesPrepared=0,
            throughputBytesPerSecond=0,
            etaSeconds=None,
            transferStartedAt=None,
            currentFile=None,
            errorMessage=None,
            verification=None,
            verifiedAt=None,
            deletionEligible=False,
            updatedAt=UploadManager._timestamp(),
        )

    @staticmethod
    def _new_job(
        job_id: str, root_id: str, relative_path: str, target_id: str
    ) -> Dict[str, object]:
        now = UploadManager._timestamp()
        return {
            "id": job_id,
            "rootId": root_id,
            "relativePath": relative_path,
            "targetId": target_id,
            "sourceName": None,
            "folderName": None,
            "state": "QUEUED",
            "bytesTotal": None,
            "bytesTransferred": 0,
            "bytesPrepared": 0,
            "filesTotal": None,
            "filesTransferred": 0,
            "filesPrepared": 0,
            "throughputBytesPerSecond": 0,
            "etaSeconds": None,
            "transferStartedAt": None,
            "remoteSessionId": None,
            "verification": None,
            "verifiedAt": None,
            "deletionEligible": False,
            "sourceDeleted": False,
            "sourceDeletedAt": None,
            "currentFile": None,
            "errorMessage": None,
            "createdAt": now,
            "updatedAt": now,
        }

    @staticmethod
    def _destination_name(source: Path, job_id: str) -> str:
        name = source.name or "root"
        return f"{name}-{job_id[:8]}"

    @staticmethod
    def _timestamp() -> str:
        return datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")

    @staticmethod
    def _public_error(error: Exception) -> str:
        if isinstance(error, UploadCancelled):
            return "Upload cancelled"
        if isinstance(error, PermissionError):
            return "Permission denied while reading or writing upload data"
        if isinstance(error, OSError):
            return "Storage I/O failed during upload"
        if isinstance(error, RuntimeError):
            return str(error)
        if isinstance(error, ValueError):
            return str(error)
        return "Unexpected upload failure"


class UploadCancelled(Exception):
    pass


class UploadReceiverHttpError(RuntimeError):
    def __init__(self, status_code: int) -> None:
        self.status_code = status_code
        super().__init__(f"Upload receiver rejected the request (HTTP {status_code})")


class UploadLibraryUnavailable(RuntimeError):
    pass


class UploadConflict(RuntimeError):
    pass


class UploadConfirmationRequired(UploadConflict):
    pass


class UploadVerificationMismatch(UploadConflict):
    pass


class UploadCapacityExceeded(UploadConflict):
    pass
