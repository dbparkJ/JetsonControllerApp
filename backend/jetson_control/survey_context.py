"""Jetson-authoritative survey project and section persistence."""
from __future__ import annotations

import hashlib
import json
import os
import re
import threading
import time
import uuid
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Callable, Dict, List, Mapping, Optional


SURVEY_SCHEMA_VERSION = 1
MAX_PROJECTS = 512
MAX_SECTIONS_PER_PROJECT = 2048
MAX_IDEMPOTENCY_RECORDS = 1024
ENTITY_ID = re.compile(r"^[0-9a-f]{32}$")
REQUEST_ID = re.compile(r"^[A-Za-z0-9][A-Za-z0-9_.:-]{7,127}$")


class SurveyContextError(RuntimeError):
    pass


class SurveyContextNotFound(SurveyContextError):
    pass


class SurveyContextConflict(SurveyContextError):
    def __init__(self, code: str, message: str, current: object = None) -> None:
        super().__init__(message)
        self.code = code
        self.current = current


def utc_timestamp(clock: Callable[[], float] = time.time) -> str:
    return datetime.fromtimestamp(clock(), timezone.utc).isoformat().replace("+00:00", "Z")


def _label(value: object) -> str:
    if not isinstance(value, str):
        raise ValueError("Survey label is required")
    normalized = value.strip()
    if (
        not normalized
        or len(normalized.encode("utf-8")) > 128
        or any(ord(character) < 32 or ord(character) == 127 for character in normalized)
    ):
        raise ValueError("Survey label must contain 1 to 128 UTF-8 bytes")
    return normalized


def _request_id(value: object) -> str:
    if not isinstance(value, str) or REQUEST_ID.fullmatch(value) is None:
        raise ValueError("clientRequestId is invalid")
    return value


def _body_hash(value: Mapping[str, object]) -> str:
    encoded = json.dumps(value, sort_keys=True, separators=(",", ":"), ensure_ascii=False)
    return hashlib.sha256(encoded.encode("utf-8")).hexdigest()


class SurveyContextStore:
    """Persist projects and sections with optimistic revisions and bounded replay data."""

    def __init__(self, path: Path, clock: Callable[[], float] = time.time) -> None:
        self.path = Path(path)
        self.clock = clock
        self._lock = threading.RLock()

    @staticmethod
    def _empty() -> Dict[str, object]:
        return {
            "schemaVersion": SURVEY_SCHEMA_VERSION,
            "projects": [],
            "idempotency": [],
        }

    def _load(self) -> Dict[str, object]:
        try:
            with self.path.open("r", encoding="utf-8") as source:
                value = json.load(source)
        except FileNotFoundError:
            return self._empty()
        except (OSError, UnicodeDecodeError, json.JSONDecodeError) as error:
            raise SurveyContextError("Survey context store is unavailable") from error
        if (
            not isinstance(value, dict)
            or value.get("schemaVersion") != SURVEY_SCHEMA_VERSION
            or not isinstance(value.get("projects"), list)
            or not isinstance(value.get("idempotency", []), list)
        ):
            raise SurveyContextError("Survey context store is invalid")
        return value

    def _save(self, value: Mapping[str, object]) -> None:
        self.path.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
        temporary = self.path.with_name(
            "." + self.path.name + "." + str(os.getpid()) + "." + str(time.monotonic_ns()) + ".tmp"
        )
        descriptor = None
        try:
            descriptor = os.open(
                temporary,
                os.O_WRONLY | os.O_CREAT | os.O_EXCL | getattr(os, "O_NOFOLLOW", 0),
                0o600,
            )
            with os.fdopen(descriptor, "w", encoding="utf-8") as output:
                descriptor = None
                json.dump(value, output, sort_keys=True, separators=(",", ":"), ensure_ascii=False)
                output.write("\n")
                output.flush()
                os.fsync(output.fileno())
            os.replace(temporary, self.path)
            try:
                directory = os.open(self.path.parent, os.O_RDONLY | os.O_DIRECTORY)
                try:
                    os.fsync(directory)
                finally:
                    os.close(directory)
            except OSError:
                pass
        except OSError as error:
            raise SurveyContextError("Could not persist survey context") from error
        finally:
            if descriptor is not None:
                os.close(descriptor)
            try:
                temporary.unlink()
            except FileNotFoundError:
                pass

    @staticmethod
    def _project(value: object) -> Dict[str, object]:
        if not isinstance(value, dict) or ENTITY_ID.fullmatch(str(value.get("surveyProjectId", ""))) is None:
            raise SurveyContextError("Survey project record is invalid")
        if not isinstance(value.get("label"), str) or not isinstance(value.get("revision"), int):
            raise SurveyContextError("Survey project record is invalid")
        sections = value.get("sections", [])
        if not isinstance(sections, list):
            raise SurveyContextError("Survey project sections are invalid")
        return value

    @staticmethod
    def _public_project(project: Mapping[str, object]) -> Dict[str, object]:
        return {key: project[key] for key in (
            "surveyProjectId", "label", "revision", "createdAt", "updatedAt"
        )}

    @staticmethod
    def _public_section(section: Mapping[str, object]) -> Dict[str, object]:
        return {key: section[key] for key in (
            "surveySectionId", "surveyProjectId", "label", "revision", "createdAt", "updatedAt"
        )}

    @staticmethod
    def _find_project(value: Mapping[str, object], project_id: str) -> Dict[str, object]:
        if ENTITY_ID.fullmatch(project_id) is None:
            raise ValueError("surveyProjectId is invalid")
        for raw in value["projects"]:  # type: ignore[index]
            project = SurveyContextStore._project(raw)
            if project["surveyProjectId"] == project_id:
                return project
        raise SurveyContextNotFound("Survey project does not exist")

    @staticmethod
    def _find_section(project: Mapping[str, object], section_id: str) -> Dict[str, object]:
        if ENTITY_ID.fullmatch(section_id) is None:
            raise ValueError("surveySectionId is invalid")
        for raw in project.get("sections", []):
            if isinstance(raw, dict) and raw.get("surveySectionId") == section_id:
                return raw
        raise SurveyContextNotFound("Survey section does not exist")

    @staticmethod
    def _check_revision(current: Mapping[str, object], expected: Optional[int]) -> None:
        if expected is None:
            raise SurveyContextConflict("REVISION_REQUIRED", "expectedRevision is required", dict(current))
        if isinstance(expected, bool) or not isinstance(expected, int) or expected < 1:
            raise ValueError("expectedRevision is invalid")
        if current.get("revision") != expected:
            raise SurveyContextConflict("REVISION_MISMATCH", "Survey context changed", dict(current))

    @staticmethod
    def _idempotent(
        value: Dict[str, object], request_id: str, operation: str, body: Mapping[str, object]
    ) -> Optional[Dict[str, object]]:
        digest = _body_hash(body)
        records = value.setdefault("idempotency", [])
        if not isinstance(records, list):
            raise SurveyContextError("Survey idempotency records are invalid")
        for record in records:
            if isinstance(record, dict) and record.get("clientRequestId") == request_id:
                if record.get("operation") != operation or record.get("bodyHash") != digest:
                    raise SurveyContextConflict(
                        "IDEMPOTENCY_CONFLICT",
                        "clientRequestId was already used for a different mutation",
                    )
                result = record.get("result")
                if not isinstance(result, dict):
                    raise SurveyContextError("Survey idempotency result is invalid")
                return dict(result)
        return None

    @staticmethod
    def _remember(
        value: Dict[str, object], request_id: str, operation: str,
        body: Mapping[str, object], result: Mapping[str, object]
    ) -> None:
        records = value.setdefault("idempotency", [])
        assert isinstance(records, list)
        records.append({
            "clientRequestId": request_id,
            "operation": operation,
            "bodyHash": _body_hash(body),
            "result": dict(result),
        })
        del records[:-MAX_IDEMPOTENCY_RECORDS]

    def list_projects(self) -> Dict[str, List[Dict[str, object]]]:
        with self._lock:
            projects = [self._public_project(self._project(item)) for item in self._load()["projects"]]
        projects.sort(key=lambda item: (str(item["label"]).casefold(), str(item["surveyProjectId"])))
        return {"projects": projects}

    def get_project(self, project_id: str) -> Dict[str, object]:
        with self._lock:
            return self._public_project(self._find_project(self._load(), project_id))

    def create_project(self, label: str, client_request_id: str) -> Dict[str, object]:
        normalized = _label(label)
        request_id = _request_id(client_request_id)
        body = {"label": normalized}
        with self._lock:
            value = self._load()
            replay = self._idempotent(value, request_id, "CREATE_PROJECT", body)
            if replay is not None:
                return replay
            projects = value["projects"]
            assert isinstance(projects, list)
            if len(projects) >= MAX_PROJECTS:
                raise SurveyContextConflict("CAPACITY_REACHED", "Survey project capacity is reached")
            timestamp = utc_timestamp(self.clock)
            project = {
                "surveyProjectId": uuid.uuid4().hex,
                "label": normalized,
                "revision": 1,
                "createdAt": timestamp,
                "updatedAt": timestamp,
                "sections": [],
            }
            projects.append(project)
            result = self._public_project(project)
            self._remember(value, request_id, "CREATE_PROJECT", body, result)
            self._save(value)
            return result

    def update_project(self, project_id: str, label: str, expected_revision: Optional[int]) -> Dict[str, object]:
        normalized = _label(label)
        with self._lock:
            value = self._load()
            project = self._find_project(value, project_id)
            self._check_revision(self._public_project(project), expected_revision)
            project["label"] = normalized
            project["revision"] = int(project["revision"]) + 1
            project["updatedAt"] = utc_timestamp(self.clock)
            self._save(value)
            return self._public_project(project)

    def delete_project(self, project_id: str, expected_revision: Optional[int]) -> Dict[str, object]:
        with self._lock:
            value = self._load()
            project = self._find_project(value, project_id)
            self._check_revision(self._public_project(project), expected_revision)
            projects = value["projects"]
            assert isinstance(projects, list)
            projects.remove(project)
            self._save(value)
            return {"deleted": True, "surveyProjectId": project_id}

    def list_sections(self, project_id: str) -> Dict[str, List[Dict[str, object]]]:
        with self._lock:
            project = self._find_project(self._load(), project_id)
            sections = [self._public_section(item) for item in project.get("sections", []) if isinstance(item, dict)]
        sections.sort(key=lambda item: (str(item["label"]).casefold(), str(item["surveySectionId"])))
        return {"sections": sections}

    def get_section(self, project_id: str, section_id: str) -> Dict[str, object]:
        with self._lock:
            project = self._find_project(self._load(), project_id)
            return self._public_section(self._find_section(project, section_id))

    def create_section(self, project_id: str, label: str, client_request_id: str) -> Dict[str, object]:
        normalized = _label(label)
        request_id = _request_id(client_request_id)
        body = {"surveyProjectId": project_id, "label": normalized}
        with self._lock:
            value = self._load()
            project = self._find_project(value, project_id)
            replay = self._idempotent(value, request_id, "CREATE_SECTION", body)
            if replay is not None:
                return replay
            sections = project["sections"]
            assert isinstance(sections, list)
            if len(sections) >= MAX_SECTIONS_PER_PROJECT:
                raise SurveyContextConflict("CAPACITY_REACHED", "Survey section capacity is reached")
            timestamp = utc_timestamp(self.clock)
            section = {
                "surveySectionId": uuid.uuid4().hex,
                "surveyProjectId": project_id,
                "label": normalized,
                "revision": 1,
                "createdAt": timestamp,
                "updatedAt": timestamp,
            }
            sections.append(section)
            result = self._public_section(section)
            self._remember(value, request_id, "CREATE_SECTION", body, result)
            self._save(value)
            return result

    def update_section(
        self, project_id: str, section_id: str, label: str, expected_revision: Optional[int]
    ) -> Dict[str, object]:
        normalized = _label(label)
        with self._lock:
            value = self._load()
            project = self._find_project(value, project_id)
            section = self._find_section(project, section_id)
            self._check_revision(self._public_section(section), expected_revision)
            section["label"] = normalized
            section["revision"] = int(section["revision"]) + 1
            section["updatedAt"] = utc_timestamp(self.clock)
            self._save(value)
            return self._public_section(section)

    def delete_section(
        self, project_id: str, section_id: str, expected_revision: Optional[int]
    ) -> Dict[str, object]:
        with self._lock:
            value = self._load()
            project = self._find_project(value, project_id)
            section = self._find_section(project, section_id)
            self._check_revision(self._public_section(section), expected_revision)
            sections = project["sections"]
            assert isinstance(sections, list)
            sections.remove(section)
            self._save(value)
            return {"deleted": True, "surveyProjectId": project_id, "surveySectionId": section_id}

    def snapshot(
        self,
        project_id: str,
        section_id: str,
        project_revision: int,
        section_revision: int,
        device_id: str,
    ) -> Dict[str, object]:
        with self._lock:
            project = self._find_project(self._load(), project_id)
            section = self._find_section(project, section_id)
            if project.get("revision") != project_revision or section.get("revision") != section_revision:
                raise SurveyContextConflict(
                    "REVISION_MISMATCH",
                    "Survey context changed; reload before continuing",
                    {"project": self._public_project(project), "section": self._public_section(section)},
                )
            return {
                "schemaVersion": SURVEY_SCHEMA_VERSION,
                "deviceId": device_id,
                "surveyProjectId": project_id,
                "surveyProjectLabel": project["label"],
                "surveyProjectRevision": project_revision,
                "surveySectionId": section_id,
                "surveySectionLabel": section["label"],
                "surveySectionRevision": section_revision,
                "capturedAt": utc_timestamp(self.clock),
            }
