from __future__ import annotations

import json
import subprocess
from pathlib import Path
from typing import Any, Callable, Dict, List, Mapping, Optional, Sequence

from .config import validate_config_id


PIPELINE_ACTIONS = frozenset({"start", "stop", "restart", "enable", "disable"})


class PipelineError(RuntimeError):
    pass


class PipelineNotFound(PipelineError):
    pass


class PipelineConflict(PipelineError):
    pass


CommandRunner = Callable[..., subprocess.CompletedProcess]


class PipelineManager:
    def __init__(
        self,
        registry_root: Path,
        registrar: Path,
        pipeline_user: str,
        command_runner: Optional[CommandRunner] = None,
    ) -> None:
        self.registry_root = registry_root
        self.registrar = registrar
        self.pipeline_user = pipeline_user
        self.command_runner = command_runner or subprocess.run

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
        return value

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
