from __future__ import annotations

import codecs
import json
import hashlib
import math
import os
import re
import stat
import subprocess
from datetime import datetime, timezone
from io import StringIO
from pathlib import Path
from typing import Any, Callable, Dict, List, Mapping, Optional, Sequence

from ruamel.yaml import YAML

from .config import validate_config_id
from .pipeline_layout import PipelineFolderLayout, discover_pipeline_folder
from .system_control import DEFAULT_TIME_SYNC_MARKER, read_time_sync_marker


PIPELINE_ACTIONS = frozenset({"start", "stop", "restart", "enable", "disable"})
PIPELINE_LOG_ID = re.compile(r"^run-(\d{8}T\d{6}\.\d{6}Z)-\d+\.log$")


class PipelineError(RuntimeError):
    pass


class PipelineNotFound(PipelineError):
    pass


class PipelineConflict(PipelineError):
    pass


CommandRunner = Callable[..., subprocess.CompletedProcess]


class PipelineManager:
    MAX_CONFIG_BYTES = 512 * 1024
    MAX_CONFIG_FIELDS = 2048
    MAX_LOG_FILES = 20
    MAX_LOG_READ_BYTES = 128 * 1024

    def __init__(
        self,
        registry_root: Path,
        registrar: Path,
        pipeline_user: str,
        command_runner: Optional[CommandRunner] = None,
        logs_root: Path = Path("/var/log/jetson-pipelines"),
        folder_results_root: Optional[Path] = None,
        time_sync_marker: Path = DEFAULT_TIME_SYNC_MARKER,
        time_sync_marker_owner_uid: int = 0,
    ) -> None:
        self.registry_root = registry_root
        self.registrar = registrar
        self.pipeline_user = pipeline_user
        self.command_runner = command_runner or subprocess.run
        self.logs_root = logs_root
        self.folder_results_root = (
            folder_results_root.expanduser().resolve()
            if folder_results_root is not None
            else None
        )
        self.time_sync_marker = time_sync_marker
        self.time_sync_marker_owner_uid = time_sync_marker_owner_uid

    def discover_folder(self, repository: Path) -> Dict[str, object]:
        layout = discover_pipeline_folder(repository)
        response = layout.response()
        results = self._folder_results_directory(layout)
        response["resultsDirectory"] = str(results)
        response["resultsExists"] = results.is_dir()
        response["logDirectory"] = str(self.logs_root / layout.pipeline_id)
        response["autostartDefault"] = True
        return response

    def list_pipelines(self) -> List[Dict[str, object]]:
        if not self.registry_root.exists():
            return []
        if not self.registry_root.is_dir():
            raise PipelineError("Pipeline registry is not a directory")

        pipelines = []
        for child in sorted(self.registry_root.iterdir(), key=lambda item: item.name):
            if child.is_symlink() or not child.is_dir():
                continue
            try:
                pipeline_id = validate_config_id(child.name, "pipeline")
                manifest = self._load_manifest(pipeline_id)
                pipelines.append(self._response(manifest, self._status(pipeline_id)))
            except (OSError, ValueError, PipelineError):
                continue
        return pipelines

    def get(self, pipeline_id: str) -> Dict[str, object]:
        pipeline_id = validate_config_id(pipeline_id, "pipeline")
        manifest = self._load_manifest(pipeline_id)
        return self._response(manifest, self._status(pipeline_id))

    def control(self, pipeline_id: str, action: str) -> Dict[str, object]:
        pipeline_id = validate_config_id(pipeline_id, "pipeline")
        if action not in PIPELINE_ACTIONS:
            raise ValueError("Unknown pipeline action")
        self._load_manifest(pipeline_id)

        unit = self._unit(pipeline_id)
        result = self._run(["systemctl", action, unit], timeout=30)
        if result.returncode != 0:
            message = (result.stderr or result.stdout or "systemctl failed").strip()
            raise PipelineError(message)
        return self.get(pipeline_id)

    def logs(self, pipeline_id: str, lines: int = 200) -> Dict[str, object]:
        pipeline_id = validate_config_id(pipeline_id, "pipeline")
        self._load_manifest(pipeline_id)
        line_count = max(20, min(1000, lines))
        result = self._run(
            [
                "journalctl",
                "--unit",
                self._unit(pipeline_id),
                "--lines",
                str(line_count),
                "--output",
                "short-iso",
                "--no-pager",
            ],
            timeout=15,
        )
        if result.returncode != 0:
            message = (result.stderr or result.stdout or "journalctl failed").strip()
            raise PipelineError(message)
        return {
            "pipelineId": pipeline_id,
            "lines": result.stdout.splitlines(),
        }

    def log_files(self, pipeline_id: str) -> Dict[str, object]:
        pipeline_id = validate_config_id(pipeline_id, "pipeline")
        self._load_manifest(pipeline_id)
        directory = self._log_directory(pipeline_id)
        if directory is None:
            return {"pipelineId": pipeline_id, "files": []}

        files = []
        try:
            entries = list(directory.iterdir())
        except OSError as error:
            raise PipelineError(f"Could not list pipeline logs: {error}") from error
        for path in entries:
            match = PIPELINE_LOG_ID.fullmatch(path.name)
            if match is None:
                continue
            try:
                metadata = os.lstat(path)
            except FileNotFoundError:
                continue
            if not stat.S_ISREG(metadata.st_mode):
                continue
            try:
                started = datetime.strptime(
                    match.group(1), "%Y%m%dT%H%M%S.%fZ"
                ).replace(tzinfo=timezone.utc)
            except ValueError:
                continue
            files.append(
                {
                    "id": path.name,
                    "startedAt": started.isoformat().replace("+00:00", "Z"),
                    "modifiedAt": self._iso_timestamp(metadata.st_mtime),
                    "sizeBytes": metadata.st_size,
                    "active": False,
                    "_mtimeNs": metadata.st_mtime_ns,
                }
            )
        files.sort(key=lambda item: (int(item["_mtimeNs"]), str(item["id"])), reverse=True)
        files = files[: self.MAX_LOG_FILES]
        if files:
            status = self._status(pipeline_id)
            files[0]["active"] = (
                status.get("ActiveState") == "active"
                and status.get("SubState") == "running"
            )
        for item in files:
            item.pop("_mtimeNs", None)
        return {"pipelineId": pipeline_id, "files": files}

    def read_log_file(
        self,
        pipeline_id: str,
        log_id: str,
        offset: int = 0,
        limit: int = MAX_LOG_READ_BYTES,
    ) -> Dict[str, object]:
        pipeline_id = validate_config_id(pipeline_id, "pipeline")
        self._load_manifest(pipeline_id)
        if PIPELINE_LOG_ID.fullmatch(log_id) is None:
            raise ValueError("Pipeline log id is invalid")
        if offset < 0:
            raise ValueError("Pipeline log offset must not be negative")
        if limit < 1 or limit > self.MAX_LOG_READ_BYTES:
            raise ValueError("Pipeline log read limit is invalid")
        directory = self._log_directory(pipeline_id)
        if directory is None:
            raise PipelineNotFound("Pipeline log file does not exist")
        path = directory / log_id
        flags = os.O_RDONLY
        if hasattr(os, "O_NOFOLLOW"):
            flags |= os.O_NOFOLLOW
        try:
            descriptor = os.open(path, flags)
        except FileNotFoundError as error:
            raise PipelineNotFound("Pipeline log file does not exist") from error
        except OSError as error:
            raise PipelineError(f"Could not open pipeline log: {error}") from error
        try:
            metadata = os.fstat(descriptor)
            if not stat.S_ISREG(metadata.st_mode):
                raise PipelineError("Pipeline log path is not a regular file")
            if offset > metadata.st_size:
                raise ValueError("Pipeline log offset is beyond the file")
            data = os.pread(descriptor, limit, offset)
            metadata = os.fstat(descriptor)
        finally:
            os.close(descriptor)
        decoder = codecs.getincrementaldecoder("utf-8")(errors="replace")
        content = decoder.decode(data, final=False)
        pending, _ = decoder.getstate()
        consumed = len(data) - len(pending)
        next_offset = offset + consumed
        return {
            "pipelineId": pipeline_id,
            "logId": log_id,
            "content": content,
            "offset": offset,
            "nextOffset": next_offset,
            "sizeBytes": metadata.st_size,
            "modifiedAt": self._iso_timestamp(metadata.st_mtime),
            "eof": next_offset >= metadata.st_size and not pending,
        }

    def _log_directory(self, pipeline_id: str) -> Optional[Path]:
        directory = self.logs_root / pipeline_id
        try:
            metadata = os.lstat(directory)
        except FileNotFoundError:
            return None
        except OSError as error:
            raise PipelineError(f"Could not access pipeline logs: {error}") from error
        if stat.S_ISLNK(metadata.st_mode) or not stat.S_ISDIR(metadata.st_mode):
            raise PipelineError("Pipeline log directory is unsafe")
        return directory

    @staticmethod
    def _iso_timestamp(timestamp: float) -> str:
        return datetime.fromtimestamp(timestamp, timezone.utc).isoformat().replace(
            "+00:00", "Z"
        )

    def config_document(self, pipeline_id: str) -> Dict[str, str]:
        pipeline_id = validate_config_id(pipeline_id, "pipeline")
        manifest = self._load_manifest(pipeline_id)
        target = self._runtime_config_path(pipeline_id, manifest)
        if target.stat().st_size > self.MAX_CONFIG_BYTES:
            raise PipelineError("Pipeline config is too large to edit")
        try:
            content = target.read_text(encoding="utf-8")
        except UnicodeDecodeError as error:
            raise PipelineError("Pipeline config is not valid UTF-8") from error
        return {
            "pipelineId": pipeline_id,
            "path": str(manifest["config"]),
            "content": content,
        }

    def update_config(self, pipeline_id: str, content: str) -> Dict[str, str]:
        pipeline_id = validate_config_id(pipeline_id, "pipeline")
        if "\x00" in content:
            raise ValueError("Pipeline config contains a null byte")
        encoded = content.encode("utf-8")
        if len(encoded) > self.MAX_CONFIG_BYTES:
            raise ValueError("Pipeline config is too large")

        manifest = self._load_manifest(pipeline_id)
        target = self._runtime_config_path(pipeline_id, manifest)
        metadata = target.stat()
        temporary = target.with_name(f".{target.name}.tmp-{os.getpid()}")
        try:
            with temporary.open("xb") as output:
                output.write(encoded)
                output.flush()
                os.fsync(output.fileno())
            os.chmod(temporary, metadata.st_mode)
            os.chown(temporary, metadata.st_uid, metadata.st_gid)
            os.replace(temporary, target)
        finally:
            try:
                temporary.unlink()
            except FileNotFoundError:
                pass
        return self.config_document(pipeline_id)

    def config_fields(self, pipeline_id: str) -> Dict[str, object]:
        pipeline_id = validate_config_id(pipeline_id, "pipeline")
        manifest = self._load_manifest(pipeline_id)
        target = self._runtime_config_path(pipeline_id, manifest)
        content = self._read_config_bytes(target)
        document = self._parse_config(content)
        return {
            "pipelineId": pipeline_id,
            "path": str(manifest["config"]),
            "revision": hashlib.sha256(content).hexdigest(),
            "fields": self._config_scalar_fields(document),
        }

    def update_config_fields(
        self,
        pipeline_id: str,
        revision: str,
        values: Mapping[str, str],
    ) -> Dict[str, object]:
        pipeline_id = validate_config_id(pipeline_id, "pipeline")
        if len(values) > self.MAX_CONFIG_FIELDS:
            raise ValueError("Pipeline config contains too many edited values")
        if not revision or len(revision) != 64:
            raise ValueError("Pipeline config revision is invalid")

        manifest = self._load_manifest(pipeline_id)
        target = self._runtime_config_path(pipeline_id, manifest)
        content = self._read_config_bytes(target)
        if not hashlib.sha256(content).hexdigest() == revision:
            raise PipelineConflict("Pipeline config changed; reload before saving")

        document = self._parse_config(content)
        fields = {field["path"]: field for field in self._config_scalar_fields(document)}
        unknown = set(values) - set(fields)
        if unknown:
            raise ValueError("Pipeline config field path is invalid")
        edited_bytes = 0
        for pointer, text in values.items():
            if not isinstance(text, str) or "\x00" in text:
                raise ValueError("Pipeline config value is invalid")
            edited_bytes += len(text.encode("utf-8"))
            if edited_bytes > self.MAX_CONFIG_BYTES:
                raise ValueError("Pipeline config values are too large")
            field_type = str(fields[pointer]["type"])
            self._set_config_scalar(document, pointer, self._parse_scalar(text, field_type))

        yaml = self._yaml()
        output = StringIO()
        yaml.dump(document, output)
        encoded = output.getvalue().encode("utf-8")
        if len(encoded) > self.MAX_CONFIG_BYTES:
            raise ValueError("Pipeline config is too large")
        self._atomic_replace_config(target, encoded)
        return self.config_fields(pipeline_id)

    def _read_config_bytes(self, target: Path) -> bytes:
        if target.stat().st_size > self.MAX_CONFIG_BYTES:
            raise PipelineError("Pipeline config is too large to edit")
        content = target.read_bytes()
        try:
            content.decode("utf-8")
        except UnicodeDecodeError as error:
            raise PipelineError("Pipeline config is not valid UTF-8") from error
        return content

    @staticmethod
    def _yaml() -> YAML:
        yaml = YAML(typ="rt")
        yaml.preserve_quotes = True
        yaml.allow_duplicate_keys = False
        return yaml

    def _parse_config(self, content: bytes) -> object:
        try:
            document = self._yaml().load(content.decode("utf-8"))
        except Exception as error:
            raise PipelineError("Pipeline config is not valid YAML") from error
        if document is None:
            document = {}
        self._config_scalar_fields(document)
        return document

    def _config_scalar_fields(self, document: object) -> List[Dict[str, str]]:
        if not isinstance(document, (Mapping, list)):
            raise PipelineError("YAML config root must be a mapping or list")
        fields: List[Dict[str, str]] = []
        active_containers: set[int] = set()
        seen_containers: set[int] = set()

        def visit(value: object, segments: List[str]) -> None:
            if isinstance(value, Mapping):
                identity = id(value)
                if identity in active_containers:
                    raise PipelineError("Recursive YAML aliases cannot be edited")
                if identity in seen_containers:
                    raise PipelineError("YAML container aliases cannot be edited")
                seen_containers.add(identity)
                active_containers.add(identity)
                try:
                    for key, child in value.items():
                        if not isinstance(key, str) or not key:
                            raise PipelineError("YAML mapping keys must be non-empty strings")
                        visit(child, [*segments, key])
                finally:
                    active_containers.remove(identity)
                return
            if isinstance(value, list):
                identity = id(value)
                if identity in active_containers:
                    raise PipelineError("Recursive YAML aliases cannot be edited")
                if identity in seen_containers:
                    raise PipelineError("YAML container aliases cannot be edited")
                seen_containers.add(identity)
                active_containers.add(identity)
                try:
                    for index, child in enumerate(value):
                        visit(child, [*segments, str(index)])
                finally:
                    active_containers.remove(identity)
                return

            field_type, text = self._scalar_description(value)
            pointer = "/" + "/".join(self._escape_pointer(part) for part in segments)
            fields.append(
                {
                    "path": pointer,
                    "label": " > ".join(segments) if segments else "value",
                    "type": field_type,
                    "value": text,
                }
            )
            if len(fields) > self.MAX_CONFIG_FIELDS:
                raise PipelineError("Pipeline config has too many editable values")

        visit(document, [])
        return fields

    @staticmethod
    def _scalar_description(value: object) -> tuple[str, str]:
        if value is None:
            return "NULL", ""
        if isinstance(value, bool):
            return "BOOLEAN", "true" if value else "false"
        if isinstance(value, int):
            return "INTEGER", str(value)
        if isinstance(value, float):
            if not math.isfinite(value):
                raise PipelineError("Non-finite YAML numbers cannot be edited")
            return "DECIMAL", str(value)
        if isinstance(value, str):
            return "STRING", value
        raise PipelineError("YAML contains a value type that cannot be edited")

    @staticmethod
    def _parse_scalar(text: str, field_type: str) -> object:
        try:
            if field_type == "BOOLEAN":
                if text == "true":
                    return True
                if text == "false":
                    return False
                raise ValueError
            if field_type == "INTEGER":
                return int(text, 10)
            if field_type == "DECIMAL":
                value = float(text)
                if not math.isfinite(value):
                    raise ValueError
                return value
            if field_type == "NULL":
                return None if not text else text
            if field_type == "STRING":
                return text
        except ValueError as error:
            raise ValueError(f"Invalid {field_type.lower()} config value") from error
        raise ValueError("Pipeline config field type is invalid")

    @classmethod
    def _set_config_scalar(cls, document: object, pointer: str, value: object) -> None:
        segments = cls._pointer_segments(pointer)
        if not segments:
            raise ValueError("Root scalar YAML documents cannot be edited")
        parent = document
        for segment in segments[:-1]:
            if isinstance(parent, Mapping):
                parent = parent[segment]
            elif isinstance(parent, list):
                parent = parent[int(segment)]
            else:
                raise ValueError("Pipeline config field path is invalid")
        leaf = segments[-1]
        if isinstance(parent, Mapping):
            parent[leaf] = value
        elif isinstance(parent, list):
            parent[int(leaf)] = value
        else:
            raise ValueError("Pipeline config field path is invalid")

    @staticmethod
    def _escape_pointer(value: str) -> str:
        return value.replace("~", "~0").replace("/", "~1")

    @staticmethod
    def _pointer_segments(pointer: str) -> List[str]:
        if not pointer.startswith("/"):
            raise ValueError("Pipeline config field path is invalid")
        if pointer == "/":
            return []
        return [part.replace("~1", "/").replace("~0", "~") for part in pointer[1:].split("/")]

    @staticmethod
    def _atomic_replace_config(target: Path, encoded: bytes) -> None:
        metadata = target.stat()
        temporary = target.with_name(f".{target.name}.tmp-{os.getpid()}")
        try:
            with temporary.open("xb") as output:
                output.write(encoded)
                output.flush()
                os.fsync(output.fileno())
            os.chmod(temporary, metadata.st_mode)
            os.chown(temporary, metadata.st_uid, metadata.st_gid)
            os.replace(temporary, target)
        finally:
            temporary.unlink(missing_ok=True)

    def register(
        self,
        *,
        pipeline_id: str,
        label: str,
        repository: Path,
        virtualenv: Path,
        entrypoint: str,
        config: str,
        working_directory: Path,
        writable_paths: Sequence[Path],
        autostart: bool,
    ) -> Dict[str, object]:
        pipeline_id = validate_config_id(pipeline_id, "pipeline")
        if (
            not label.strip()
            or len(label.strip().encode("utf-8")) > 64
            or any(ord(character) < 32 or ord(character) == 127 for character in label)
        ):
            raise ValueError("Pipeline label is required")

        command = [
            "/usr/bin/python3",
            str(self.registrar),
            "--id",
            pipeline_id,
            "--label",
            label.strip(),
            "--repo",
            str(repository),
            "--venv",
            str(virtualenv),
            "--entry",
            entrypoint,
            "--config",
            config,
            "--working-dir",
            str(working_directory),
            "--user",
            self.pipeline_user,
        ]
        for path in writable_paths:
            command.extend(["--write-path", str(path)])
        command.append("--autostart" if autostart else "--no-autostart")

        result = self._run(command, timeout=120)
        if result.returncode != 0:
            message = (result.stderr or result.stdout or "Pipeline registration failed").strip()
            if "already running" in message.lower():
                raise PipelineConflict(message)
            raise PipelineError(message)
        return self.get(pipeline_id)

    def register_folder(
        self,
        *,
        label: str,
        repository: Path,
        autostart: bool = True,
    ) -> Dict[str, object]:
        """Register a convention-based folder while preserving the legacy path."""

        layout = discover_pipeline_folder(repository)
        results_directory = self._folder_results_directory(layout)
        normalized_label = label.strip()
        if (
            not normalized_label
            or len(normalized_label.encode("utf-8")) > 64
            or any(
                ord(character) < 32 or ord(character) == 127
                for character in normalized_label
            )
        ):
            raise ValueError("Pipeline label is required")
        command = [
            "/usr/bin/python3",
            str(self.registrar),
            "--folder",
            str(layout.repository),
            "--name",
            normalized_label,
            "--user",
            self.pipeline_user,
            "--results-dir",
            str(results_directory),
            "--use-template-defaults",
            "--autostart" if autostart else "--no-autostart",
        ]
        result = self._run(command, timeout=120)
        if result.returncode != 0:
            message = (
                result.stderr or result.stdout or "Pipeline registration failed"
            ).strip()
            if "already running" in message.lower():
                raise PipelineConflict(message)
            raise PipelineError(message)
        return self.get(layout.pipeline_id)

    def _folder_results_directory(self, layout: PipelineFolderLayout) -> Path:
        if self.folder_results_root is None:
            return layout.results
        return self.folder_results_root / layout.pipeline_id

    def remove(self, pipeline_id: str) -> None:
        pipeline_id = validate_config_id(pipeline_id, "pipeline")
        self._load_manifest(pipeline_id)
        result = self._run(
            [
                "/usr/bin/python3",
                str(self.registrar),
                "--remove",
                pipeline_id,
            ],
            timeout=60,
        )
        if result.returncode != 0:
            message = (result.stderr or result.stdout or "Pipeline removal failed").strip()
            raise PipelineError(message)

    def _load_manifest(self, pipeline_id: str) -> Mapping[str, Any]:
        manifest_path = self.registry_root / pipeline_id / "pipeline.json"
        try:
            with manifest_path.open("r", encoding="utf-8") as source:
                value = json.load(source)
        except FileNotFoundError as error:
            raise PipelineNotFound("Pipeline is not registered") from error
        except json.JSONDecodeError as error:
            raise PipelineError("Pipeline manifest is invalid") from error

        if not isinstance(value, dict):
            raise PipelineError("Pipeline manifest must contain an object")
        if value.get("schema_version") != 1 or value.get("id") != pipeline_id:
            raise PipelineError("Pipeline manifest identity is invalid")
        for key in (
            "label",
            "entrypoint",
            "config",
            "virtualenv",
            "source_revision",
            "source_branch",
            "snapshot_created_at",
        ):
            if not isinstance(value.get(key), str):
                raise PipelineError(f"Pipeline manifest field is invalid: {key}")
        if not isinstance(value.get("source_dirty"), bool):
            raise PipelineError("Pipeline manifest field is invalid: source_dirty")
        if not isinstance(value.get("results_directory", ""), str):
            raise PipelineError("Pipeline manifest field is invalid: results_directory")
        if not isinstance(value.get("folder_convention", False), bool):
            raise PipelineError("Pipeline manifest field is invalid: folder_convention")
        writable_paths = value.get("writable_paths", [])
        if not isinstance(writable_paths, list) or any(
            not isinstance(path, str) for path in writable_paths
        ):
            raise PipelineError("Pipeline manifest field is invalid: writable_paths")
        return value

    def _runtime_config_path(
        self,
        pipeline_id: str,
        manifest: Mapping[str, Any],
    ) -> Path:
        pipeline_root = (self.registry_root / pipeline_id).resolve(strict=True)
        releases_root = (pipeline_root / "releases").resolve(strict=True)
        release = (pipeline_root / "current").resolve(strict=True)
        try:
            release.relative_to(releases_root)
        except ValueError as error:
            raise PipelineError("Pipeline release is outside its registry") from error
        config_value = str(manifest["config"])
        config = (release / config_value).resolve(strict=True)
        try:
            config.relative_to(release)
        except ValueError as error:
            raise PipelineError("Pipeline config is outside its release") from error
        if config.suffix.lower() not in {".yaml", ".yml"} or not config.is_file():
            raise PipelineError("Pipeline config is not an editable YAML file")
        return config

    def _status(self, pipeline_id: str) -> Mapping[str, str]:
        result = self._run(
            [
                "systemctl",
                "show",
                self._unit(pipeline_id),
                "--property=LoadState",
                "--property=ActiveState",
                "--property=SubState",
                "--property=UnitFileState",
                "--property=ExecMainStatus",
                "--property=Result",
                "--property=NRestarts",
                "--no-pager",
            ],
            timeout=10,
        )
        if result.returncode != 0:
            return {
                "LoadState": "unknown",
                "ActiveState": "unknown",
                "SubState": "unknown",
                "UnitFileState": "unknown",
                "ExecMainStatus": "0",
                "Result": "unknown",
                "NRestarts": "0",
            }
        properties: Dict[str, str] = {}
        for line in result.stdout.splitlines():
            key, separator, value = line.partition("=")
            if separator:
                properties[key] = value
        return properties

    def _response(
        self,
        manifest: Mapping[str, Any],
        status: Mapping[str, str],
    ) -> Dict[str, object]:
        active_state = status.get("ActiveState", "unknown")
        sub_state = status.get("SubState", "unknown")
        if sub_state == "auto-restart":
            state = "RETRYING"
        else:
            state = {
                "active": "RUNNING",
                "activating": "STARTING",
                "deactivating": "STOPPING",
                "failed": "FAILED",
                "inactive": "STOPPED",
            }.get(active_state, "UNKNOWN")
        time_synchronized = read_time_sync_marker(
            self.time_sync_marker,
            expected_owner_uid=self.time_sync_marker_owner_uid,
        ) is not None
        if state in {"RUNNING", "STARTING"} and not time_synchronized:
            state = "WAITING_FOR_TIME_SYNC"
        unit_file_state = status.get("UnitFileState", "unknown")
        enabled = unit_file_state in {"enabled", "enabled-runtime", "linked", "linked-runtime"}
        try:
            exit_code = int(status.get("ExecMainStatus", "0"))
        except ValueError:
            exit_code = 0
        try:
            restart_count = int(status.get("NRestarts", "0"))
        except ValueError:
            restart_count = 0

        return {
            "id": manifest["id"],
            "label": manifest["label"],
            "description": str(manifest.get("description", "")),
            "state": state,
            "activeState": active_state,
            "subState": sub_state,
            "enabled": enabled,
            "lastExitCode": exit_code,
            "result": status.get("Result", "unknown"),
            "restartCount": restart_count,
            "entrypoint": manifest["entrypoint"],
            "config": manifest["config"],
            "virtualenv": manifest["virtualenv"],
            "pythonVersion": str(manifest.get("python_version", "")),
            "sourceBranch": manifest["source_branch"],
            "sourceRevision": manifest["source_revision"],
            "sourceDirty": manifest["source_dirty"],
            "snapshotCreatedAt": manifest["snapshot_created_at"],
            "writablePaths": list(manifest.get("writable_paths", [])),
            "resultsDirectory": str(manifest.get("results_directory", "")) or None,
            "folderConvention": bool(manifest.get("folder_convention", False)),
            "timeSynchronized": time_synchronized,
        }

    def _run(self, command: Sequence[str], timeout: int) -> subprocess.CompletedProcess:
        try:
            return self.command_runner(
                list(command),
                capture_output=True,
                text=True,
                timeout=timeout,
                check=False,
            )
        except (OSError, subprocess.SubprocessError) as error:
            raise PipelineError(f"Could not execute pipeline command: {error}") from error

    @staticmethod
    def _unit(pipeline_id: str) -> str:
        return f"jetson-pipeline@{pipeline_id}.service"
