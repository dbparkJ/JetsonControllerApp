from __future__ import annotations

import json
import http.client
import hashlib
import ipaddress
import os
import shutil
import ssl
import struct
import threading
import time
import uuid
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Dict, Iterable, List, Optional, Tuple
from urllib.parse import urlencode, urlsplit

from .config import load_json_object, validate_config_id
from .filesystem import StorageRegistry


TERMINAL_STATES = {"COMPLETED", "FAILED", "CANCELLED"}
ACTIVE_STATES = {"QUEUED", "SCANNING", "UPLOADING"}
HTTP_CHUNK_SIZE = 4 * 1024 * 1024
HTTP_RETRY_DELAYS = (0.0, 1.0, 3.0, 7.0)
DEFAULT_MAX_CONCURRENT_JOBS = 2
HTTP_COMPLETE_RESPONSE_TIMEOUT = 24 * 60 * 60
FILE_BATCH_MAGIC = b"JETSONBATCH1\n"
FILE_BATCH_VERSION = 1
CLIENT_MAX_BATCH_BYTES = 32 * 1024 * 1024
CLIENT_MAX_BATCH_FILES = 256


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
        _, source = self.storage.resolve(root_id, relative_path)
        if not source.exists():
            raise FileNotFoundError("Upload source was not found")
        if not source.is_file() and not source.is_dir():
            raise ValueError("Upload source must be a regular file or directory")

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
            self._update(job_id, state="SCANNING")
            files = list(self.storage.iter_regular_files(source))
            self._raise_if_cancelled(job_id, cancellation)
            bytes_total = sum(path.stat().st_size for path in files)
            self._update(
                job_id,
                state="UPLOADING",
                bytesTotal=bytes_total,
                bytesTransferred=0,
                filesTotal=len(files),
                filesTransferred=0,
            )

            if target.kind == "local":
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
                    self._update(job_id, bytesTransferred=transferred)
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
        manifest_files = []
        for path in files:
            self._raise_if_cancelled(job_id, cancellation)
            manifest_files.append(
                {
                    "path": self._relative_file_path(source, path).as_posix(),
                    "sizeBytes": path.stat().st_size,
                    "sha256": self._sha256_file(path, cancellation, job_id),
                }
            )

        manifest = {
            "deviceId": self.device_id,
            "clientJobId": job_id,
            "sourceName": source.name or "root",
            "files": manifest_files,
        }
        response = self._http_json_with_retry(
            target, token, "POST", "/v1/upload-sessions", manifest
        )
        session_id = str(response.get("sessionId", ""))
        try:
            validate_config_id(session_id, "upload session")
        except ValueError as error:
            raise RuntimeError("Upload receiver returned an invalid session id")

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
                    self._update(
                        job_id,
                        bytesTransferred=transferred,
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
            self._update(
                job_id,
                bytesTransferred=transferred,
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
        self._update(job_id, bytesTransferred=transferred)
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
                self._update(job_id, bytesTransferred=transferred)

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
    ) -> Dict[str, object]:
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
            response_body = response.read(1024 * 1024)
            if response.status < 200 or response.status >= 300:
                raise RuntimeError("Upload receiver rejected the request")
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
            filesTotal=None,
            filesTransferred=0,
            currentFile=None,
            errorMessage=None,
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
            "state": "QUEUED",
            "bytesTotal": None,
            "bytesTransferred": 0,
            "filesTotal": None,
            "filesTransferred": 0,
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


class UploadConflict(RuntimeError):
    pass


class UploadCapacityExceeded(UploadConflict):
    pass
