"""Durable, recoverable trash for configured storage and run-history files."""
from __future__ import annotations

import json
import ctypes
import errno
import os
import re
import stat
import threading
import uuid
from datetime import datetime, timezone
from pathlib import Path
from typing import Dict, Iterable, List, Mapping, Optional, Sequence, Tuple

from fastapi import HTTPException, Query
from pydantic import BaseModel, ConfigDict, Field, StrictBool, field_validator
from starlette.concurrency import run_in_threadpool

from .filesystem import StorageRegistry


TRASH_DIRECTORY_NAME = ".jetson-control-trash"
TRASH_ID = re.compile(r"[a-f0-9]{32}")
PIPELINE_ID = re.compile(r"[A-Za-z0-9][A-Za-z0-9_.-]{0,63}")
RUN_LOG_ID = re.compile(r"run-\d{8}T\d{6}\.\d{6}Z-\d+\.log")
RUN_SIDECAR_SUFFIXES = (
    ".route.jsonl",
    ".quality.jsonl",
    ".quality.json",
    ".context.json",
)
RENAME_NOREPLACE = 1
_LIBC = ctypes.CDLL(None, use_errno=True)


class TrashConflict(RuntimeError):
    pass


class RestoreTrashRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    confirmed: StrictBool = False


class EmptyTrashRequest(BaseModel):
    model_config = ConfigDict(extra="forbid", populate_by_name=True)

    confirmed: StrictBool = False
    trash_ids: List[str] = Field(alias="trashIds", min_length=1, max_length=200)

    @field_validator("trash_ids")
    @classmethod
    def validate_trash_ids(cls, value: List[str]) -> List[str]:
        if len(set(value)) != len(value):
            raise ValueError("Trash identifiers must be unique")
        if any(not TRASH_ID.fullmatch(trash_id) for trash_id in value):
            raise ValueError("Invalid trash identifier")
        return value


def _timestamp() -> str:
    return datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")


def _fsync_directory(path: Path) -> None:
    descriptor = os.open(path, os.O_RDONLY | os.O_DIRECTORY)
    try:
        os.fsync(descriptor)
    finally:
        os.close(descriptor)


class LocalTrashManager:
    """Move entries on their own filesystem and journal every transition.

    The journal lives in the service state directory. Payloads stay below a hidden
    directory inside their safe root so each source-to-trash and trash-to-source
    transition uses ``os.replace`` on one filesystem. Purge removes only an
    explicitly confirmed snapshot of payload identifiers and retains journals.
    """

    def __init__(
        self,
        storage: StorageRegistry,
        state_dir: Path,
        pipeline_logs: Optional[Path] = None,
    ) -> None:
        self.storage = storage
        self.state_dir = state_dir.expanduser().resolve()
        self.pipeline_logs = (
            pipeline_logs.expanduser().resolve() if pipeline_logs is not None else None
        )
        self.journal_dir = self.state_dir / "journal"
        self.journal_dir.mkdir(parents=True, exist_ok=True, mode=0o700)
        self._lock = threading.RLock()
        self.recover()

    def trash_storage_entry(
        self,
        root_id: str,
        relative_path: str,
        *,
        category: str = "STORAGE",
        metadata: Optional[Mapping[str, object]] = None,
    ) -> Dict[str, object]:
        root, resolved = self.storage.resolve(root_id, relative_path)
        source, relative = self._resolve_storage_source(
            root.path, relative_path, resolved
        )
        if relative == Path("."):
            raise TrashConflict("A configured storage root cannot be moved to trash")
        if relative.parts and relative.parts[0] == TRASH_DIRECTORY_NAME:
            raise TrashConflict("The reserved trash directory cannot be modified")
        return self._trash(
            category=category,
            root_kind="STORAGE",
            root_id=root_id,
            root_path=root.path,
            members=(source,),
            primary_relative=relative.as_posix(),
            metadata=metadata,
        )

    def trash_run_history(
        self,
        logs_root: Path,
        pipeline_id: str,
        log_id: str,
    ) -> Dict[str, object]:
        if not PIPELINE_ID.fullmatch(pipeline_id) or not RUN_LOG_ID.fullmatch(log_id):
            raise ValueError("Invalid run")
        root = logs_root.expanduser().resolve()
        if self.pipeline_logs is not None and root != self.pipeline_logs:
            raise TrashConflict("Run history root does not match the configured safe root")
        directory = root / pipeline_id
        source = directory / log_id
        members = [source]
        members.extend(
            candidate
            for suffix in RUN_SIDECAR_SUFFIXES
            if (candidate := directory / f"{log_id}{suffix}").exists()
        )
        return self._trash(
            category="RUN_HISTORY",
            root_kind="RUN_HISTORY",
            root_id="pipeline-logs",
            root_path=root,
            members=tuple(members),
            primary_relative=f"{pipeline_id}/{log_id}",
            metadata={"pipelineId": pipeline_id, "logId": log_id},
        )

    def list_entries(self, *, include_restored: bool = False) -> Dict[str, object]:
        with self._lock:
            entries = []
            for path in sorted(self.journal_dir.glob("*.json")):
                try:
                    record = self._read_record(path)
                except (OSError, ValueError, json.JSONDecodeError):
                    continue
                if record.get("state") == "PURGED":
                    continue
                if record.get("state") == "RESTORED" and not include_restored:
                    continue
                entries.append(self._public(record))
            entries.sort(key=lambda item: str(item.get("trashedAt", "")), reverse=True)
            return {
                "entries": entries,
                "refreshedAt": _timestamp(),
                "emptySupported": True,
            }

    def empty(self, trash_ids: Sequence[str], *, confirmed: bool) -> Dict[str, object]:
        """Purge only the caller's explicit trash snapshot, reporting each result."""
        if not confirmed:
            raise TrashConflict("Empty trash requires explicit user confirmation")
        if isinstance(trash_ids, (str, bytes)) or not 1 <= len(trash_ids) <= 200:
            raise ValueError("Trash identifiers must contain between 1 and 200 items")
        if len(set(trash_ids)) != len(trash_ids):
            raise ValueError("Trash identifiers must be unique")
        if any(not isinstance(trash_id, str) or not TRASH_ID.fullmatch(trash_id)
               for trash_id in trash_ids):
            raise ValueError("Invalid trash identifier")
        with self._lock:
            results = [self._purge_one(trash_id) for trash_id in trash_ids]
        return {"results": results, "refreshedAt": _timestamp()}

    def restore(self, trash_id: str, *, confirmed: bool) -> Dict[str, object]:
        if not confirmed:
            raise TrashConflict("Restore requires explicit user confirmation")
        if not TRASH_ID.fullmatch(trash_id):
            raise ValueError("Invalid trash identifier")
        with self._lock:
            path = self.journal_dir / f"{trash_id}.json"
            if not path.exists():
                raise KeyError(trash_id)
            record = self._read_record(path)
            state = record.get("state")
            if state == "RESTORED":
                return self._public(record)
            if state != "TRASHED":
                raise TrashConflict("Trash entry is not ready to restore")
            for original, payload in self._record_paths(record):
                if original.exists() or original.is_symlink():
                    raise TrashConflict("Restore destination already exists")
                self._validate_restore_parent(original, self._record_root(record))
                if not payload.exists() and not payload.is_symlink():
                    raise TrashConflict("Trash payload is missing")
            self._transition(record, "RESTORING", "RESTORE_REQUESTED")
            self._write_record(record)
            self._complete_restore(record)
            return self._public(record)

    def get_entry(self, trash_id: str) -> Dict[str, object]:
        """Return one current journal entry for internal lifecycle reconciliation."""
        if not TRASH_ID.fullmatch(trash_id):
            raise ValueError("Invalid trash identifier")
        with self._lock:
            path = self.journal_dir / f"{trash_id}.json"
            if not path.exists():
                raise KeyError(trash_id)
            return self._public(self._read_record(path))

    def payload_path(self, trash_id: str, member_index: int = 0) -> Path:
        """Return one validated payload path for an internal verification step."""
        if not TRASH_ID.fullmatch(trash_id) or member_index < 0:
            raise ValueError("Invalid trash identifier")
        with self._lock:
            record = self._read_record(self.journal_dir / f"{trash_id}.json")
            paths = list(self._record_paths(record))
            try:
                return paths[member_index][1]
            except IndexError as error:
                raise ValueError("Trash payload index is invalid") from error

    def recover(self) -> None:
        with self._lock:
            for path in sorted(self.journal_dir.glob("*.json")):
                record: Optional[Dict[str, object]] = None
                recovery_state: Optional[object] = None
                try:
                    loaded = self._read_record(path)
                    loaded_id = str(loaded.get("trashId", ""))
                    if not TRASH_ID.fullmatch(loaded_id) or loaded_id != path.stem:
                        continue
                    record = loaded
                    recovery_state = record.get("state")
                    if record.get("state") == "MOVING_TO_TRASH":
                        self._complete_trash(record)
                    elif record.get("state") == "RESTORING":
                        self._complete_restore(record)
                    elif record.get("state") == "PURGING":
                        self._complete_purge(record)
                except (OSError, ValueError, TrashConflict, json.JSONDecodeError) as error:
                    if record is None:
                        continue
                    try:
                        if recovery_state == "PURGING":
                            try:
                                persisted = self._read_record(path)
                            except (OSError, ValueError, json.JSONDecodeError):
                                persisted = record
                            persisted_id = str(persisted.get("trashId", ""))
                            if not TRASH_ID.fullmatch(persisted_id) or persisted_id != path.stem:
                                continue
                            if persisted.get("state") == "PURGED":
                                continue
                            record = persisted
                            record["lastError"] = str(error)[:512]
                            self._transition(record, "PURGING", "PURGE_RECOVERY_FAILED")
                        else:
                            record["lastError"] = str(error)[:512]
                            self._transition(record, "RECOVERY_REQUIRED", "RECOVERY_FAILED")
                        self._write_record(record)
                    except Exception:
                        continue

    def _trash(
        self,
        *,
        category: str,
        root_kind: str,
        root_id: str,
        root_path: Path,
        members: Sequence[Path],
        primary_relative: str,
        metadata: Optional[Mapping[str, object]],
    ) -> Dict[str, object]:
        if not members:
            raise FileNotFoundError("Trash source was not found")
        with self._lock:
            root = root_path.expanduser().resolve()
            normalized = []
            for member in members:
                relative = self._safe_relative(root, member)
                self._require_regular_entry(member)
                normalized.append((member, relative))
            self._reject_overlapping_members(normalized)
            trash_id = uuid.uuid4().hex
            payload_root = root / TRASH_DIRECTORY_NAME / trash_id
            self._create_payload_directory(root, trash_id)
            now = _timestamp()
            record: Dict[str, object] = {
                "schemaVersion": 1,
                "trashId": trash_id,
                "category": category,
                "state": "MOVING_TO_TRASH",
                "rootKind": root_kind,
                "rootId": root_id,
                "rootPath": str(root),
                "relativePath": primary_relative,
                "name": Path(primary_relative).name,
                "entryType": "DIRECTORY" if members[0].is_dir() else "FILE",
                "trashedAt": now,
                "restoredAt": None,
                "metadata": dict(metadata or {}),
                "members": [
                    {"relativePath": relative.as_posix(), "payloadName": f"{index:04d}"}
                    for index, (_member, relative) in enumerate(normalized)
                ],
                "audit": [{"event": "TRASH_REQUESTED", "at": now}],
            }
            self._write_record(record)
            self._complete_trash(record)
            return self._public(record)

    def _complete_trash(self, record: Dict[str, object]) -> None:
        root = self._record_root(record)
        for relative, payload_name in self._record_members(record):
            self._move_member(
                root, str(record["trashId"]), relative, payload_name,
                to_trash=True,
            )
        self._transition(record, "TRASHED", "TRASHED")
        record["lastError"] = None
        self._write_record(record)

    def _complete_restore(self, record: Dict[str, object]) -> None:
        root = self._record_root(record)
        for relative, payload_name in self._record_members(record):
            self._move_member(
                root, str(record["trashId"]), relative, payload_name,
                to_trash=False,
            )
        record["restoredAt"] = _timestamp()
        self._transition(record, "RESTORED", "RESTORED")
        record["lastError"] = None
        self._write_record(record)

    def _purge_one(self, trash_id: str) -> Dict[str, object]:
        path = self.journal_dir / f"{trash_id}.json"
        if not path.exists():
            return {"trashId": trash_id, "state": "FAILED", "error": "Trash entry was not found"}
        try:
            record = self._read_record(path)
            if record.get("trashId") != trash_id:
                raise ValueError("Trash journal identifier does not match its file")
            state = record.get("state")
            if state == "PURGED":
                return {"trashId": trash_id, "state": "PURGED"}
            if state not in {"TRASHED", "PURGING"}:
                return {
                    "trashId": trash_id,
                    "state": "FAILED",
                    "error": "Trash entry is not ready to purge",
                }
            if state == "TRASHED":
                self._transition(record, "PURGING", "PURGE_REQUESTED")
                record["lastError"] = None
                self._write_record(record)
            self._complete_purge(record)
            return {"trashId": trash_id, "state": "PURGED"}
        except (OSError, ValueError, TrashConflict, json.JSONDecodeError) as error:
            try:
                persisted = self._read_record(path)
            except (OSError, ValueError, json.JSONDecodeError):
                persisted = None
            if persisted is not None and persisted.get("trashId") != trash_id:
                persisted = None
            if persisted is not None and persisted.get("state") == "PURGED":
                return {"trashId": trash_id, "state": "PURGED"}
            if persisted is not None and persisted.get("state") == "PURGING":
                persisted["lastError"] = str(error)[:512]
                self._transition(persisted, "PURGING", "PURGE_FAILED")
                try:
                    self._write_record(persisted)
                except OSError:
                    pass
                return {"trashId": trash_id, "state": "PURGING", "error": str(error)[:512]}
            return {"trashId": trash_id, "state": "FAILED", "error": str(error)[:512]}

    def _complete_purge(self, record: Dict[str, object]) -> None:
        if record.get("state") != "PURGING":
            raise TrashConflict("Trash entry is not ready to purge")
        trash_id = str(record.get("trashId", ""))
        if not TRASH_ID.fullmatch(trash_id):
            raise ValueError("Trash record identifier is invalid")
        root = self._record_root(record)
        self._purge_payload_directory(root, trash_id)
        record["purgedAt"] = _timestamp()
        self._transition(record, "PURGED", "PURGED")
        record["lastError"] = None
        self._write_record(record)

    def _record_root(self, record: Mapping[str, object]) -> Path:
        root = Path(str(record.get("rootPath", ""))).resolve()
        root_kind = record.get("rootKind")
        if root_kind == "STORAGE":
            root_id = str(record.get("rootId", ""))
            configured = self.storage.roots().get(root_id)
            if configured is None or configured.path.resolve() != root:
                raise TrashConflict("Configured storage root changed")
        elif root_kind == "RUN_HISTORY":
            if self.pipeline_logs is None or root != self.pipeline_logs:
                raise TrashConflict("Configured run-history root changed")
        else:
            raise ValueError("Trash record root kind is invalid")
        return root

    def _record_paths(self, record: Mapping[str, object]) -> Iterable[Tuple[Path, Path]]:
        root = self._record_root(record)
        trash_id = str(record.get("trashId", ""))
        if not TRASH_ID.fullmatch(trash_id):
            raise ValueError("Trash record identifier is invalid")
        payload_root = root / TRASH_DIRECTORY_NAME / trash_id
        self._validate_payload_directory(root, trash_id)
        for relative, payload_name in self._record_members(record):
            original = root / relative
            yield original, payload_root / payload_name

    @staticmethod
    def _record_members(record: Mapping[str, object]) -> Iterable[Tuple[Path, str]]:
        members = record.get("members")
        if not isinstance(members, list) or not members:
            raise ValueError("Trash record members are invalid")
        for member in members:
            if not isinstance(member, dict):
                raise ValueError("Trash record member is invalid")
            relative = Path(str(member.get("relativePath", "")))
            payload_name = str(member.get("payloadName", ""))
            if (
                relative.is_absolute()
                or ".." in relative.parts
                or not relative.parts
                or relative.parts[0] == TRASH_DIRECTORY_NAME
            ):
                raise ValueError("Trash record path is invalid")
            if not re.fullmatch(r"\d{4}", payload_name):
                raise ValueError("Trash payload name is invalid")
            yield relative, payload_name

    @staticmethod
    def _safe_relative(root: Path, path: Path) -> Path:
        try:
            return path.resolve(strict=False).relative_to(root.resolve())
        except ValueError as error:
            raise TrashConflict("Trash path leaves its configured safe root") from error

    @classmethod
    def _resolve_storage_source(
        cls, root: Path, relative_path: str, resolved: Path
    ) -> Tuple[Path, Path]:
        relative = Path(relative_path.lstrip("/"))
        if relative.is_absolute() or ".." in relative.parts:
            raise TrashConflict("Storage path is unsafe")
        candidate = root
        for part in relative.parts:
            if part in {"", "."}:
                continue
            candidate = candidate / part
            try:
                metadata = candidate.lstat()
            except FileNotFoundError as error:
                raise FileNotFoundError("Trash source was not found") from error
            if stat.S_ISLNK(metadata.st_mode):
                raise TrashConflict("Storage path contains a symbolic link")
        if candidate.resolve() != resolved:
            raise TrashConflict("Storage path changed")
        return candidate, cls._safe_relative(root, candidate)

    @staticmethod
    def _open_directory(path: Path) -> int:
        return os.open(path, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW)

    @classmethod
    def _open_beneath(cls, root_fd: int, parts: Sequence[str]) -> int:
        descriptor = os.dup(root_fd)
        try:
            for part in parts:
                if part in {"", ".", ".."} or "/" in part:
                    raise TrashConflict("Trash path is unsafe")
                child = os.open(
                    part,
                    os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW,
                    dir_fd=descriptor,
                )
                os.close(descriptor)
                descriptor = child
            return descriptor
        except Exception:
            os.close(descriptor)
            raise

    @staticmethod
    def _require_private_owned_directory(descriptor: int) -> None:
        metadata = os.fstat(descriptor)
        if (
            not stat.S_ISDIR(metadata.st_mode)
            or metadata.st_uid != os.geteuid()
            or metadata.st_mode & 0o077
        ):
            raise TrashConflict("Reserved trash directory ownership or mode is unsafe")

    @classmethod
    def _open_payload_directory(
        cls, root_fd: int, trash_id: str, *, create: bool
    ) -> Tuple[int, int]:
        if not TRASH_ID.fullmatch(trash_id):
            raise ValueError("Trash identifier is invalid")
        if create:
            try:
                os.mkdir(TRASH_DIRECTORY_NAME, 0o700, dir_fd=root_fd)
                os.fsync(root_fd)
            except FileExistsError:
                pass
        trash_fd = os.open(
            TRASH_DIRECTORY_NAME,
            os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW,
            dir_fd=root_fd,
        )
        try:
            cls._require_private_owned_directory(trash_fd)
            if create:
                os.mkdir(trash_id, 0o700, dir_fd=trash_fd)
                os.fsync(trash_fd)
            payload_fd = os.open(
                trash_id,
                os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW,
                dir_fd=trash_fd,
            )
            cls._require_private_owned_directory(payload_fd)
            return trash_fd, payload_fd
        except Exception:
            os.close(trash_fd)
            raise

    @classmethod
    def _create_payload_directory(cls, root: Path, trash_id: str) -> None:
        root_fd = cls._open_directory(root)
        try:
            trash_fd, payload_fd = cls._open_payload_directory(
                root_fd, trash_id, create=True
            )
            os.close(payload_fd)
            os.close(trash_fd)
        finally:
            os.close(root_fd)

    @classmethod
    def _validate_payload_directory(cls, root: Path, trash_id: str) -> None:
        root_fd = cls._open_directory(root)
        try:
            trash_fd, payload_fd = cls._open_payload_directory(
                root_fd, trash_id, create=False
            )
            os.close(payload_fd)
            os.close(trash_fd)
        finally:
            os.close(root_fd)

    @classmethod
    def _purge_payload_directory(cls, root: Path, trash_id: str) -> None:
        """Remove one private payload tree without following any symbolic link."""
        if not TRASH_ID.fullmatch(trash_id):
            raise ValueError("Trash identifier is invalid")
        root_fd = cls._open_directory(root)
        trash_fd = payload_fd = None
        try:
            try:
                trash_fd = os.open(
                    TRASH_DIRECTORY_NAME,
                    os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW,
                    dir_fd=root_fd,
                )
            except FileNotFoundError:
                os.fsync(root_fd)
                return
            cls._require_private_owned_directory(trash_fd)
            metadata = cls._entry_metadata(trash_fd, trash_id)
            if metadata is None:
                os.fsync(trash_fd)
                return
            if stat.S_ISLNK(metadata.st_mode) or not stat.S_ISDIR(metadata.st_mode):
                raise TrashConflict("Trash payload directory is unsafe")
            payload_fd = os.open(
                trash_id,
                os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW,
                dir_fd=trash_fd,
            )
            cls._require_private_owned_directory(payload_fd)
            for name in os.listdir(payload_fd):
                cls._purge_entry(payload_fd, name)
            os.fsync(payload_fd)
            os.close(payload_fd)
            payload_fd = None
            os.rmdir(trash_id, dir_fd=trash_fd)
            os.fsync(trash_fd)
        finally:
            for descriptor in (payload_fd, trash_fd, root_fd):
                if descriptor is not None:
                    os.close(descriptor)

    @classmethod
    def _purge_entry(cls, parent_fd: int, name: str) -> None:
        if not name or name in {".", ".."} or "/" in name:
            raise TrashConflict("Trash payload entry is unsafe")
        metadata = cls._entry_metadata(parent_fd, name)
        if metadata is None:
            return
        if stat.S_ISDIR(metadata.st_mode) and not stat.S_ISLNK(metadata.st_mode):
            child_fd = os.open(
                name,
                os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW,
                dir_fd=parent_fd,
            )
            try:
                for child_name in os.listdir(child_fd):
                    cls._purge_entry(child_fd, child_name)
                os.fsync(child_fd)
            finally:
                os.close(child_fd)
            os.rmdir(name, dir_fd=parent_fd)
        else:
            os.unlink(name, dir_fd=parent_fd)
        os.fsync(parent_fd)

    @staticmethod
    def _entry_metadata(descriptor: int, name: str):
        try:
            return os.stat(name, dir_fd=descriptor, follow_symlinks=False)
        except FileNotFoundError:
            return None

    @staticmethod
    def _rename_noreplace(
        source_fd: int, source_name: str, destination_fd: int, destination_name: str
    ) -> None:
        result = _LIBC.renameat2(
            source_fd,
            os.fsencode(source_name),
            destination_fd,
            os.fsencode(destination_name),
            RENAME_NOREPLACE,
        )
        if result != 0:
            error_number = ctypes.get_errno()
            if error_number == errno.EEXIST:
                raise TrashConflict("Restore destination already exists")
            raise OSError(error_number, os.strerror(error_number))

    @classmethod
    def _move_member(
        cls,
        root: Path,
        trash_id: str,
        relative: Path,
        payload_name: str,
        *,
        to_trash: bool,
    ) -> None:
        root_fd = cls._open_directory(root)
        trash_fd = payload_fd = original_parent_fd = None
        try:
            trash_fd, payload_fd = cls._open_payload_directory(
                root_fd, trash_id, create=False
            )
            original_parent_fd = cls._open_beneath(root_fd, relative.parts[:-1])
            original_name = relative.parts[-1]
            original_metadata = cls._entry_metadata(original_parent_fd, original_name)
            payload_metadata = cls._entry_metadata(payload_fd, payload_name)
            if original_metadata is not None and payload_metadata is not None:
                raise TrashConflict("Both source and trash payload exist")
            if to_trash:
                if original_metadata is None:
                    if payload_metadata is None:
                        raise TrashConflict("Trash transition lost its source")
                    return
                if stat.S_ISLNK(original_metadata.st_mode) or not (
                    stat.S_ISREG(original_metadata.st_mode)
                    or stat.S_ISDIR(original_metadata.st_mode)
                ):
                    raise TrashConflict("Trash source type is unsafe")
                cls._rename_noreplace(
                    original_parent_fd, original_name, payload_fd, payload_name
                )
            else:
                if payload_metadata is None:
                    if original_metadata is None:
                        raise TrashConflict("Restore transition lost its payload")
                    return
                if original_metadata is not None:
                    raise TrashConflict("Restore destination already exists")
                if stat.S_ISLNK(payload_metadata.st_mode) or not (
                    stat.S_ISREG(payload_metadata.st_mode)
                    or stat.S_ISDIR(payload_metadata.st_mode)
                ):
                    raise TrashConflict("Trash payload type is unsafe")
                cls._rename_noreplace(
                    payload_fd, payload_name, original_parent_fd, original_name
                )
            os.fsync(original_parent_fd)
            os.fsync(payload_fd)
        finally:
            for descriptor in (original_parent_fd, payload_fd, trash_fd, root_fd):
                if descriptor is not None:
                    os.close(descriptor)

    @staticmethod
    def _require_regular_entry(path: Path) -> None:
        try:
            metadata = path.lstat()
        except FileNotFoundError as error:
            raise FileNotFoundError("Trash source was not found") from error
        if stat.S_ISLNK(metadata.st_mode):
            raise TrashConflict("Symbolic links cannot be moved to trash")
        if not stat.S_ISREG(metadata.st_mode) and not stat.S_ISDIR(metadata.st_mode):
            raise TrashConflict("Trash source type is unsupported")

    @staticmethod
    def _reject_overlapping_members(members: Sequence[Tuple[Path, Path]]) -> None:
        relatives = [relative for _path, relative in members]
        if len(set(relatives)) != len(relatives):
            raise TrashConflict("Trash members overlap")
        for index, candidate in enumerate(relatives):
            for other in relatives[index + 1 :]:
                if candidate in other.parents or other in candidate.parents:
                    raise TrashConflict("Trash members overlap")

    @staticmethod
    def _validate_restore_parent(path: Path, root: Path) -> None:
        try:
            relative_parent = path.parent.relative_to(root)
        except ValueError as error:
            raise TrashConflict("Restore destination leaves its safe root") from error
        current = root
        for part in relative_parent.parts:
            current = current / part
            metadata = current.lstat()
            if stat.S_ISLNK(metadata.st_mode) or not stat.S_ISDIR(metadata.st_mode):
                raise TrashConflict("Restore parent is unsafe")

    def _transition(self, record: Dict[str, object], state: str, event: str) -> None:
        now = _timestamp()
        record["state"] = state
        record["updatedAt"] = now
        audit = record.setdefault("audit", [])
        if isinstance(audit, list):
            audit.append({"event": event, "at": now})

    def _write_record(self, record: Mapping[str, object]) -> None:
        trash_id = str(record.get("trashId", ""))
        if not TRASH_ID.fullmatch(trash_id):
            raise ValueError("Trash record identifier is invalid")
        path = self.journal_dir / f"{trash_id}.json"
        temporary = self.journal_dir / f".{trash_id}.{uuid.uuid4().hex}.tmp"
        try:
            descriptor = os.open(
                temporary,
                os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW,
                0o600,
            )
            try:
                body = json.dumps(record, ensure_ascii=True, separators=(",", ":")).encode()
                written = 0
                while written < len(body):
                    count = os.write(descriptor, body[written:])
                    if count < 1:
                        raise OSError("Short trash journal write")
                    written += count
                os.fsync(descriptor)
            finally:
                os.close(descriptor)
            os.replace(temporary, path)
            _fsync_directory(self.journal_dir)
        finally:
            temporary.unlink(missing_ok=True)

    @staticmethod
    def _read_record(path: Path) -> Dict[str, object]:
        descriptor = os.open(path, os.O_RDONLY | os.O_NOFOLLOW)
        try:
            metadata = os.fstat(descriptor)
            if not stat.S_ISREG(metadata.st_mode) or metadata.st_size > 1024 * 1024:
                raise ValueError("Trash journal is invalid")
            body = os.read(descriptor, metadata.st_size + 1)
        finally:
            os.close(descriptor)
        value = json.loads(body.decode("utf-8"))
        if not isinstance(value, dict):
            raise ValueError("Trash journal is invalid")
        return value

    @staticmethod
    def _public(record: Mapping[str, object]) -> Dict[str, object]:
        result = {
            key: record.get(key)
            for key in (
                "trashId",
                "category",
                "state",
                "rootId",
                "relativePath",
                "name",
                "entryType",
                "trashedAt",
                "restoredAt",
                "purgedAt",
                "updatedAt",
                "lastError",
                "metadata",
                "audit",
            )
        }
        result["restoreSupported"] = record.get("state") in {"TRASHED", "RESTORED"}
        result["purgeSupported"] = record.get("state") in {"TRASHED", "PURGING"}
        return result


def register_local_trash_routes(app, authenticated, trash: LocalTrashManager) -> None:
    """Register HMAC-authenticated list/restore routes on the Jetson API."""

    @app.get("/v1/trash", dependencies=authenticated)
    async def list_local_trash(include_restored: bool = Query(False, alias="includeRestored")):
        return await run_in_threadpool(trash.list_entries, include_restored=include_restored)

    @app.post("/v1/trash/{trash_id}/restore", dependencies=authenticated)
    async def restore_local_trash(trash_id: str, request: RestoreTrashRequest):
        try:
            return await run_in_threadpool(
                trash.restore, trash_id, confirmed=request.confirmed
            )
        except KeyError as error:
            raise HTTPException(404, "Trash entry was not found") from error
        except FileNotFoundError as error:
            raise HTTPException(404, str(error)) from error
        except (TrashConflict, ValueError) as error:
            raise HTTPException(409, str(error)) from error
        except OSError as error:
            raise HTTPException(503, "Trash entry could not be restored") from error

    @app.post("/v1/trash/empty", dependencies=authenticated)
    async def empty_local_trash(request: EmptyTrashRequest):
        try:
            return await run_in_threadpool(
                trash.empty, request.trash_ids, confirmed=request.confirmed
            )
        except TrashConflict as error:
            raise HTTPException(409, str(error)) from error
        except ValueError as error:
            raise HTTPException(422, str(error)) from error
        except OSError as error:
            raise HTTPException(503, "Trash could not be emptied") from error
