"""Versioned run policy, preflight, and immutable contextual run evidence."""
from __future__ import annotations

import fnmatch
import grp
import hashlib
import json
import os
import pwd
import re
import stat
import threading
import time
import uuid
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Callable, Dict, List, Mapping, Optional, Sequence, Tuple

from .field_quality import FieldQualitySampler
from .system_control import read_time_sync_marker


RUN_POLICY_SCHEMA_VERSION = 1
RUN_CONTEXT_SCHEMA_VERSION = 1
SUPPORTED_SENSORS = ("camera", "gnss", "imu")
TERMINAL_STATES = frozenset({"STOPPED", "COMPLETED", "FAILED"})
ACTIVE_STATES = frozenset({"STARTING", "RUNNING", "STOPPING", "RETRYING", "WAITING_FOR_TIME_SYNC"})
RUN_ID = re.compile(r"^[a-z0-9][a-z0-9_.-]{0,63}/run-\d{8}T\d{6}\.\d{6}Z-\d+\.log$")
PIPELINE_ID = re.compile(r"^[a-z0-9][a-z0-9_.-]{0,63}$")
LOG_ID = re.compile(r"^run-\d{8}T\d{6}\.\d{6}Z-\d+\.log$")
REQUEST_ID = re.compile(r"^[A-Za-z0-9][A-Za-z0-9_.:-]{7,127}$")
REVISION = re.compile(r"^[0-9a-f]{64}$")
MAX_PREFLIGHTS = 512
MAX_START_REQUESTS = 1024
MAX_OUTPUT_FILES = 100_000
PREFLIGHT_MAX_AGE_MILLIS = 120_000


class RunContextError(RuntimeError):
    pass


class RunContextNotFound(RunContextError):
    pass


class RunPolicyNotConfigured(RunContextNotFound):
    pass


class RunContextConflict(RunContextError):
    def __init__(self, code: str, message: str, current: object = None) -> None:
        super().__init__(message)
        self.code = code
        self.current = current


def _utc_now(clock: Callable[[], float]) -> str:
    return datetime.fromtimestamp(clock(), timezone.utc).isoformat().replace("+00:00", "Z")


def _boot_id(path: Path = Path("/proc/sys/kernel/random/boot_id")) -> str:
    try:
        value = path.read_text(encoding="ascii").strip().lower()
    except OSError as error:
        raise RunContextError("Could not read device boot identity") from error
    if re.fullmatch(r"[0-9a-f-]{32,36}", value) is None:
        raise RunContextError("Device boot identity is invalid")
    return value


def _atomic_json(path: Path, value: Mapping[str, object], mode: int = 0o600) -> None:
    path.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    temporary = path.with_name(
        "." + path.name + "." + str(os.getpid()) + "." + str(time.monotonic_ns()) + ".tmp"
    )
    descriptor = None
    try:
        descriptor = os.open(
            temporary,
            os.O_WRONLY | os.O_CREAT | os.O_EXCL | getattr(os, "O_NOFOLLOW", 0),
            mode,
        )
        with os.fdopen(descriptor, "w", encoding="utf-8") as output:
            descriptor = None
            json.dump(value, output, sort_keys=True, separators=(",", ":"), ensure_ascii=False)
            output.write("\n")
            output.flush()
            os.fsync(output.fileno())
        os.chmod(temporary, mode)
        os.replace(temporary, path)
        try:
            directory = os.open(path.parent, os.O_RDONLY | os.O_DIRECTORY)
            try:
                os.fsync(directory)
            finally:
                os.close(directory)
        except OSError:
            pass
    except OSError as error:
        raise RunContextError(f"Could not persist run context: {error}") from error
    finally:
        if descriptor is not None:
            os.close(descriptor)
        try:
            temporary.unlink()
        except FileNotFoundError:
            pass


def _read_json(path: Path) -> Dict[str, object]:
    flags = os.O_RDONLY | getattr(os, "O_NOFOLLOW", 0)
    try:
        descriptor = os.open(path, flags)
    except FileNotFoundError as error:
        raise RunContextNotFound("Run context does not exist") from error
    try:
        metadata = os.fstat(descriptor)
        if not stat.S_ISREG(metadata.st_mode) or metadata.st_size > 1024 * 1024:
            raise RunContextError("Run context file is invalid")
        encoded = os.pread(descriptor, metadata.st_size, 0)
    finally:
        os.close(descriptor)
    try:
        value = json.loads(encoded.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as error:
        raise RunContextError("Run context file is invalid") from error
    if not isinstance(value, dict):
        raise RunContextError("Run context file is invalid")
    return value


def _canonical_hash(value: Mapping[str, object]) -> str:
    encoded = json.dumps(value, sort_keys=True, separators=(",", ":"), ensure_ascii=False)
    return hashlib.sha256(encoded.encode("utf-8")).hexdigest()


def _normalize_patterns(values: object) -> List[str]:
    if not isinstance(values, list) or len(values) > 64:
        raise ValueError("expectedOutput.patterns is invalid")
    patterns: List[str] = []
    for raw in values:
        if not isinstance(raw, str):
            raise ValueError("expectedOutput.patterns is invalid")
        pattern = raw.strip()
        path = Path(pattern)
        if (
            not pattern
            or len(pattern.encode("utf-8")) > 256
            or path.is_absolute()
            or ".." in path.parts
            or "\x00" in pattern
        ):
            raise ValueError("expectedOutput pattern is invalid")
        patterns.append(pattern)
    return list(dict.fromkeys(patterns))


def _bounded_nonnegative(value: object, name: str, maximum: int = (1 << 63) - 1) -> int:
    if isinstance(value, bool) or not isinstance(value, int) or not 0 <= value <= maximum:
        raise ValueError(name + " is invalid")
    return value


class RunContextService:
    """Coordinates root-owned context with one systemd pipeline invocation."""

    def __init__(
        self,
        *,
        state_dir: Path,
        registry_root: Path,
        logs_root: Path,
        device_id: str,
        pipeline_user: str,
        pipelines: object,
        survey: object,
        sensor_bridge: object,
        storage: object,
        time_sync_marker: Path,
        time_sync_owner_uid: int = 0,
        clock: Callable[[], float] = time.time,
    ) -> None:
        self.state_dir = Path(state_dir)
        self.registry_root = Path(registry_root)
        self.logs_root = Path(logs_root)
        self.device_id = device_id
        self.pipeline_user = pipeline_user
        self.pipelines = pipelines
        self.survey = survey
        self.sensor_bridge = sensor_bridge
        self.storage = storage
        self.time_sync_marker = Path(time_sync_marker)
        self.time_sync_owner_uid = time_sync_owner_uid
        self.clock = clock
        self._lock = threading.RLock()
        self.records_dir = self.state_dir / "pipeline-runs"
        self.preflights_dir = self.state_dir / "pipeline-preflights"
        self.start_requests_path = self.state_dir / "pipeline-start-requests.json"

    def _policy_path(self, pipeline_id: str) -> Path:
        if PIPELINE_ID.fullmatch(pipeline_id) is None:
            raise ValueError("pipelineId is invalid")
        return self.registry_root / pipeline_id / "run-policy.json"

    def _launch_path(self, pipeline_id: str) -> Path:
        if PIPELINE_ID.fullmatch(pipeline_id) is None:
            raise ValueError("pipelineId is invalid")
        return self.registry_root / pipeline_id / "next-run.json"

    def _record_path(self, run_id: str) -> Path:
        if RUN_ID.fullmatch(run_id) is None:
            raise ValueError("runId is invalid")
        return self.records_dir / (hashlib.sha256(run_id.encode("utf-8")).hexdigest() + ".json")

    def policy(self, pipeline_id: str) -> Dict[str, object]:
        try:
            value = _read_json(self._policy_path(pipeline_id))
        except RunContextNotFound as error:
            raise RunPolicyNotConfigured("Run policy is not configured") from error
        if (
            value.get("schemaVersion") != RUN_POLICY_SCHEMA_VERSION
            or value.get("pipelineId") != pipeline_id
            or value.get("configured") is not True
            or not isinstance(value.get("policyVersion"), int)
            or REVISION.fullmatch(str(value.get("revision", ""))) is None
        ):
            raise RunContextError("Run policy is invalid")
        return {key: item for key, item in value.items() if not key.startswith("_")}

    def _raw_policy(self, pipeline_id: str) -> Optional[Dict[str, object]]:
        try:
            return _read_json(self._policy_path(pipeline_id))
        except RunContextNotFound:
            return None

    def policy_or_none(self, pipeline_id: str) -> Optional[Dict[str, object]]:
        try:
            return self.policy(pipeline_id)
        except RunPolicyNotConfigured:
            return None

    @staticmethod
    def _policy_inputs(
        required_sensors: object,
        optional_sensors: object,
        min_free_bytes: object,
        output_root_id: object,
        output_path: object,
        expected_output: object,
    ) -> Dict[str, object]:
        if not isinstance(required_sensors, list) or not isinstance(optional_sensors, list):
            raise ValueError("Sensor policy must contain arrays")
        required = list(dict.fromkeys(required_sensors))
        optional = list(dict.fromkeys(optional_sensors))
        if any(sensor not in SUPPORTED_SENSORS for sensor in required + optional):
            raise ValueError("Sensor policy contains an unsupported sensor")
        if set(required) & set(optional):
            raise ValueError("A sensor cannot be both required and optional")
        if not isinstance(output_root_id, str) or not output_root_id:
            raise ValueError("outputRootId is required")
        if not isinstance(output_path, str) or "\x00" in output_path:
            raise ValueError("outputPath is invalid")
        relative = Path(output_path)
        if relative.is_absolute() or ".." in relative.parts or len(output_path.encode("utf-8")) > 1024:
            raise ValueError("outputPath is invalid")
        if not isinstance(expected_output, dict):
            raise ValueError("expectedOutput is invalid")
        return {
            "requiredSensors": required,
            "optionalSensors": optional,
            "minFreeBytes": _bounded_nonnegative(min_free_bytes, "minFreeBytes"),
            "outputRootId": output_root_id,
            "outputPath": output_path.strip("/"),
            "expectedOutput": {
                "minFiles": _bounded_nonnegative(expected_output.get("minFiles"), "expectedOutput.minFiles", 1_000_000),
                "minBytes": _bounded_nonnegative(expected_output.get("minBytes"), "expectedOutput.minBytes"),
                "patterns": _normalize_patterns(expected_output.get("patterns", [])),
            },
        }

    def set_policy(
        self,
        pipeline_id: str,
        *,
        required_sensors: object,
        optional_sensors: object,
        min_free_bytes: object,
        output_root_id: object,
        output_path: object,
        expected_output: object,
        expected_revision: Optional[str],
        client_request_id: str,
    ) -> Dict[str, object]:
        if REQUEST_ID.fullmatch(client_request_id or "") is None:
            raise ValueError("clientRequestId is invalid")
        self.pipelines.runtime_identity(pipeline_id)
        inputs = self._policy_inputs(
            required_sensors, optional_sensors, min_free_bytes,
            output_root_id, output_path, expected_output,
        )
        _, output_directory = self.storage.resolve(
            str(inputs["outputRootId"]), str(inputs["outputPath"])
        )
        self.pipelines.assert_output_allowed(pipeline_id, output_directory)
        request_body = {
            "pipelineId": pipeline_id,
            **inputs,
            "expectedRevision": expected_revision,
        }
        request_hash = _canonical_hash(request_body)
        with self._lock:
            self.assert_pipeline_mutable(pipeline_id)
            current = self.policy_or_none(pipeline_id)
            raw = self._raw_policy(pipeline_id)
            if current is not None and raw is not None and raw.get("_clientRequestId") == client_request_id:
                if raw.get("_requestHash") != request_hash:
                    raise RunContextConflict(
                        "IDEMPOTENCY_CONFLICT",
                        "clientRequestId was already used for a different run policy mutation",
                    )
                return {key: item for key, item in raw.items() if not key.startswith("_")}
            if current is None:
                if expected_revision is not None:
                    raise RunContextConflict("REVISION_MISMATCH", "Run policy does not exist")
                version = 1
            else:
                if expected_revision is None:
                    raise RunContextConflict("REVISION_REQUIRED", "expectedRevision is required", current)
                if expected_revision != current["revision"]:
                    raise RunContextConflict("REVISION_MISMATCH", "Run policy changed", current)
                version = int(current["policyVersion"]) + 1
            timestamp = _utc_now(self.clock)
            revision_body = {
                "schemaVersion": RUN_POLICY_SCHEMA_VERSION,
                "pipelineId": pipeline_id,
                "policyVersion": version,
                **inputs,
            }
            policy = {
                **revision_body,
                "revision": _canonical_hash(revision_body),
                "configured": True,
                "updatedAt": timestamp,
                "_clientRequestId": client_request_id,
                "_requestHash": request_hash,
            }
            _atomic_json(self._policy_path(pipeline_id), policy, 0o644)
            return {key: item for key, item in policy.items() if not key.startswith("_")}

    def enrich_pipeline(self, pipeline: Mapping[str, object]) -> Dict[str, object]:
        response = dict(pipeline)
        pipeline_id = str(response.get("id", ""))
        response["runPolicy"] = self.policy_or_none(pipeline_id) if pipeline_id else None
        active = self.active_run(pipeline_id) if pipeline_id else None
        response["activeContext"] = active.get("contextSnapshot") if active else None
        if active is not None:
            response["activeRunId"] = active["runId"]
            response["contextualExecution"] = active
        else:
            response.setdefault("contextualExecution", None)
        return response

    def _check_storage(self, policy: Mapping[str, object]) -> Tuple[Dict[str, object], Path]:
        root, target = self.storage.resolve(str(policy["outputRootId"]), str(policy["outputPath"]))
        if target.is_symlink() or not target.is_dir() or not self._pipeline_user_can_write(target):
            raise RunContextConflict("OUTPUT_UNAVAILABLE", "Selected output directory is unavailable")
        self.pipelines.assert_output_allowed(str(policy["pipelineId"]), target)
        minimum = int(policy["minFreeBytes"])
        try:
            usage = os.statvfs(target)
            available = usage.f_bavail * usage.f_frsize
            probe = target / (".jetson-context-preflight-" + uuid.uuid4().hex)
            descriptor = os.open(probe, os.O_WRONLY | os.O_CREAT | os.O_EXCL | getattr(os, "O_NOFOLLOW", 0), 0o600)
            try:
                os.write(descriptor, b"preflight\n")
                os.fsync(descriptor)
            finally:
                os.close(descriptor)
                probe.unlink()
        except OSError as error:
            raise RunContextConflict("OUTPUT_UNAVAILABLE", "Selected output directory is not writable") from error
        state = "READY" if available >= minimum else "INSUFFICIENT"
        return {
            "state": state,
            "rootId": root.id,
            "path": str(policy["outputPath"]),
            "availableBytes": available,
            "requiredBytes": minimum,
        }, target

    def preflight(
        self,
        pipeline_id: str,
        project_id: str,
        section_id: str,
        project_revision: int,
        section_revision: int,
        policy_revision: str,
    ) -> Dict[str, object]:
        with self._lock:
            policy = self.policy(pipeline_id)
            if policy["revision"] != policy_revision:
                raise RunContextConflict("REVISION_MISMATCH", "Run policy changed", policy)
            context = self.survey.snapshot(
                project_id, section_id, project_revision, section_revision, self.device_id
            )
            identity = self.pipelines.runtime_identity(pipeline_id)
            checked_at_millis = int(self.clock() * 1000)
            marker = read_time_sync_marker(
                self.time_sync_marker,
                expected_owner_uid=self.time_sync_owner_uid,
            )
            time_check = {
                "state": "READY" if marker is not None else "NOT_READY",
                "observedAt": _utc_now(self.clock),
            }
            if marker is not None:
                time_check["source"] = marker.get("source")
                time_check["synchronizedAtEpochMillis"] = marker.get("synchronizedAtEpochMillis")
            try:
                storage_check, _ = self._check_storage(policy)
            except RunContextConflict as error:
                storage_check = {
                    "state": "UNAVAILABLE",
                    "rootId": policy["outputRootId"],
                    "path": policy["outputPath"],
                    "availableBytes": None,
                    "requiredBytes": policy["minFreeBytes"],
                    "detail": str(error),
                }
            requirements = {
                sensor: "REQUIRED" for sensor in policy["requiredSensors"]
            }
            requirements.update({sensor: "OPTIONAL" for sensor in policy["optionalSensors"]})
            try:
                observation = FieldQualitySampler(requirements).observe(
                    self.sensor_bridge.status(), checked_at_millis
                )
            except (OSError, ValueError, TypeError, OverflowError):
                observation = FieldQualitySampler(requirements).observe(None, checked_at_millis)
            sensors = []
            problems = []
            for sensor in policy["requiredSensors"] + policy["optionalSensors"]:
                evidence = observation["sensors"][sensor]
                sample_at = evidence.get("sampleAtEpochMillis")
                item = {
                    "sensor": sensor,
                    "requirement": evidence["requirement"],
                    "state": evidence["state"],
                    "sampleAtEpochMillis": sample_at,
                    "ageMillis": (
                        checked_at_millis - int(sample_at)
                        if isinstance(sample_at, int) and not isinstance(sample_at, bool)
                        else None
                    ),
                }
                sensors.append(item)
                if evidence["state"] != "ACTIVE":
                    problems.append({
                        "code": "SENSOR_" + str(evidence["state"]),
                        "sensor": sensor,
                        "requirement": evidence["requirement"],
                        "detail": "Required sensor is not ready" if evidence["requirement"] == "REQUIRED" else "Optional sensor is not ready",
                    })
            if time_check["state"] != "READY":
                problems.append({"code": "TIME_NOT_SYNCHRONIZED", "detail": "Authenticated device time is required"})
            if storage_check["state"] != "READY":
                problems.append({"code": "STORAGE_NOT_READY", "detail": storage_check.get("detail") or "Storage policy is not satisfied"})
            required_ready = all(
                item["state"] == "ACTIVE" for item in sensors if item["requirement"] == "REQUIRED"
            )
            ready = required_ready and time_check["state"] == "READY" and storage_check["state"] == "READY"
            preflight_id = uuid.uuid4().hex
            preflight = {
                "schemaVersion": RUN_CONTEXT_SCHEMA_VERSION,
                "preflightId": preflight_id,
                "pipelineId": pipeline_id,
                "deviceId": self.device_id,
                "surveyProject": {
                    "surveyProjectId": context["surveyProjectId"],
                    "label": context["surveyProjectLabel"],
                    "revision": context["surveyProjectRevision"],
                },
                "surveySection": {
                    "surveySectionId": context["surveySectionId"],
                    "surveyProjectId": context["surveyProjectId"],
                    "label": context["surveySectionLabel"],
                    "revision": context["surveySectionRevision"],
                },
                "contextSnapshot": context,
                "policy": policy,
                "sourceRevision": identity["sourceRevision"],
                "sourceDirty": identity["sourceDirty"],
                "release": identity["release"],
                "configRevision": identity["configSha256"],
                "checkedAt": _utc_now(self.clock),
                "checkedAtEpochMillis": checked_at_millis,
                "checks": {"time": time_check, "storage": storage_check, "sensors": sensors},
                "ready": ready,
                "problems": problems,
            }
            _atomic_json(self.preflights_dir / (preflight_id + ".json"), preflight)
            self._prune(self.preflights_dir, MAX_PREFLIGHTS)
            return preflight

    @staticmethod
    def _prune(directory: Path, maximum: int) -> None:
        try:
            files = [path for path in directory.glob("*.json") if path.is_file() and not path.is_symlink()]
            files.sort(key=lambda path: path.stat().st_mtime_ns, reverse=True)
            for path in files[maximum:]:
                path.unlink()
        except OSError:
            pass

    def _load_preflight(self, preflight_id: str) -> Dict[str, object]:
        if re.fullmatch(r"[0-9a-f]{32}", preflight_id or "") is None:
            raise ValueError("preflightId is invalid")
        value = _read_json(self.preflights_dir / (preflight_id + ".json"))
        if value.get("preflightId") != preflight_id:
            raise RunContextError("Preflight identity is invalid")
        return value

    def _start_requests(self) -> Dict[str, object]:
        try:
            value = _read_json(self.start_requests_path)
        except RunContextNotFound:
            return {"schemaVersion": 1, "requests": []}
        if value.get("schemaVersion") != 1 or not isinstance(value.get("requests"), list):
            raise RunContextError("Start request store is invalid")
        return value

    def _save_start_request(self, store: Dict[str, object], request: Mapping[str, object]) -> None:
        requests = store["requests"]
        assert isinstance(requests, list)
        requests.append(dict(request))
        del requests[:-MAX_START_REQUESTS]
        _atomic_json(self.start_requests_path, store)

    def _find_start_request(self, request_id: str) -> Optional[Dict[str, object]]:
        for value in self._start_requests()["requests"]:
            if isinstance(value, dict) and value.get("clientRequestId") == request_id:
                return value
        try:
            records = list(self.records_dir.glob("*.json"))
        except OSError:
            records = []
        for path in records:
            try:
                record = _read_json(path)
            except RunContextError:
                continue
            if record.get("clientRequestId") == request_id:
                return {
                    "clientRequestId": request_id,
                    "bodyHash": record.get("requestBodyHash"),
                    "runId": record.get("runId"),
                    "createdAt": record.get("startedAt"),
                }
        return None

    def _new_log_id(self) -> str:
        stamp = datetime.fromtimestamp(self.clock(), timezone.utc).strftime("%Y%m%dT%H%M%S.%fZ")
        return "run-" + stamp + "-" + str(uuid.uuid4().int % 10_000_000_000) + ".log"

    def _prepare_output(
        self, pipeline_id: str, log_id: str, context: Mapping[str, object],
        policy: Mapping[str, object], identity: Mapping[str, object]
    ) -> Tuple[Dict[str, object], Path, Dict[str, object]]:
        root, base = self.storage.resolve(str(policy["outputRootId"]), str(policy["outputPath"]))
        self.pipelines.assert_output_allowed(pipeline_id, base)
        if not base.is_dir() or base.is_symlink() or not self._pipeline_user_can_write(base):
            raise RunContextConflict("OUTPUT_UNAVAILABLE", "Pipeline user cannot write selected output path")
        relative_name = pipeline_id + "/" + log_id[:-4]
        output_path = "/".join(part for part in (str(policy["outputPath"]).strip("/"), relative_name) if part)
        _, directory = self.storage.resolve(root.id, output_path)
        user = pwd.getpwnam(self.pipeline_user)
        pipeline_directory = directory.parent
        try:
            pipeline_directory.mkdir(mode=0o750)
            if os.geteuid() == 0:
                os.chown(pipeline_directory, user.pw_uid, user.pw_gid)
        except FileExistsError:
            if not pipeline_directory.is_dir() or not self._pipeline_user_can_write(pipeline_directory):
                raise RunContextConflict("OUTPUT_UNAVAILABLE", "Pipeline output parent is not writable")
        directory.mkdir(exist_ok=False, mode=0o750)
        if os.geteuid() == 0:
            os.chown(directory, user.pw_uid, user.pw_gid)
        output_id = uuid.uuid4().hex
        created_at = _utc_now(self.clock)
        canonical_context = {
            "schemaVersion": 1,
            "surveyProjectId": context["surveyProjectId"],
            "surveySectionId": context["surveySectionId"],
            "runId": pipeline_id + "/" + log_id,
            "deviceId": self.device_id,
            "pipelineId": pipeline_id,
            "sourceRevision": identity["sourceRevision"],
            "configSha256": identity["configSha256"],
            "outputId": output_id,
            "createdAt": created_at,
        }
        _atomic_json(directory / ".jetson-output-context.json", canonical_context, 0o440)
        output = {
            "outputId": output_id,
            "rootId": root.id,
            "path": output_path,
            "manifestState": "PENDING",
            "manifest": None,
        }
        return output, directory, canonical_context

    def _pipeline_user_can_write(self, directory: Path) -> bool:
        try:
            user = pwd.getpwnam(self.pipeline_user)
            groups = {user.pw_gid}
            for entry in grp.getgrall():
                if self.pipeline_user in entry.gr_mem:
                    groups.add(entry.gr_gid)
        except (KeyError, OSError):
            return False

        def permits(metadata: os.stat_result, *, write: bool) -> bool:
            if metadata.st_uid == user.pw_uid:
                execute_bit = stat.S_IXUSR
                write_bit = stat.S_IWUSR
            elif metadata.st_gid in groups:
                execute_bit = stat.S_IXGRP
                write_bit = stat.S_IWGRP
            else:
                execute_bit = stat.S_IXOTH
                write_bit = stat.S_IWOTH
            return bool(metadata.st_mode & execute_bit) and (
                not write or bool(metadata.st_mode & write_bit)
            )

        # A root API probe can succeed even when the systemd pipeline user
        # cannot traverse an ancestor. Check the full absolute path using the
        # pipeline user's ownership and supplementary groups.
        try:
            resolved = directory.resolve(strict=True)
            ancestors = list(resolved.parents)
            for ancestor in reversed(ancestors):
                metadata = os.stat(ancestor, follow_symlinks=False)
                if not stat.S_ISDIR(metadata.st_mode) or not permits(metadata, write=False):
                    return False
            metadata = os.stat(resolved, follow_symlinks=False)
            return stat.S_ISDIR(metadata.st_mode) and permits(metadata, write=True)
        except OSError:
            return False

    def contextual_start(
        self,
        pipeline_id: str,
        *,
        project_id: str,
        section_id: str,
        project_revision: int,
        section_revision: int,
        policy_revision: str,
        preflight_id: str,
        client_request_id: str,
    ) -> Dict[str, object]:
        if REQUEST_ID.fullmatch(client_request_id or "") is None:
            raise ValueError("clientRequestId is invalid")
        request_body = {
            "pipelineId": pipeline_id,
            "surveyProjectId": project_id,
            "surveySectionId": section_id,
            "surveyProjectRevision": project_revision,
            "surveySectionRevision": section_revision,
            "policyRevision": policy_revision,
            "preflightId": preflight_id,
        }
        body_hash = _canonical_hash(request_body)
        with self._lock:
            replay = self._find_start_request(client_request_id)
            if replay is not None:
                if replay.get("bodyHash") != body_hash:
                    raise RunContextConflict(
                        "IDEMPOTENCY_CONFLICT",
                        "clientRequestId was already used for a different contextual start",
                    )
                replay_record = self._read_record(str(replay["runId"]))
                if replay_record.get("commandOutcome") == "PREPARED":
                    _atomic_json(self._launch_path(pipeline_id), replay_record, 0o644)
                    replay_record["commandIssuedAt"] = _utc_now(self.clock)
                    replay_record["commandOutcome"] = "UNKNOWN"
                    _atomic_json(self._record_path(str(replay_record["runId"])), replay_record)
                    controlled = self.pipelines.control(pipeline_id, "start")
                    replay_record["commandOutcome"] = "COMMAND_COMPLETED"
                    _atomic_json(self._record_path(str(replay_record["runId"])), replay_record)
                    return {"run": replay_record, "pipeline": controlled, "outcome": "ALREADY_ACCEPTED"}
                run = self.get_run(str(replay["runId"]))
                return {"run": run, "pipeline": self.pipelines.status(pipeline_id), "outcome": "ALREADY_ACCEPTED"}
            current = self.active_run(pipeline_id)
            if current is not None:
                same = current.get("contextSnapshot", {})
                code = "ACTIVE_RUN_CONTEXT_MISMATCH"
                if same.get("surveyProjectId") == project_id and same.get("surveySectionId") == section_id:
                    code = "PIPELINE_ALREADY_ACTIVE"
                raise RunContextConflict(code, "Pipeline already has an active contextual run", current)
            pipeline = self.pipelines.status(pipeline_id)
            if pipeline.get("state") in ACTIVE_STATES:
                raise RunContextConflict("LEGACY_RUN_ACTIVE", "Pipeline is already active without this context", pipeline)
            policy = self.policy(pipeline_id)
            if policy.get("revision") != policy_revision:
                raise RunContextConflict("REVISION_MISMATCH", "Run policy changed", policy)
            current_context = self.survey.snapshot(
                project_id, section_id, project_revision, section_revision, self.device_id
            )
            preflight = self._load_preflight(preflight_id)
            preflight_context = preflight.get("contextSnapshot")
            context_keys = (
                "schemaVersion", "deviceId", "surveyProjectId", "surveyProjectLabel",
                "surveyProjectRevision", "surveySectionId", "surveySectionLabel",
                "surveySectionRevision",
            )
            if (
                preflight.get("pipelineId") != pipeline_id
                or preflight.get("deviceId") != self.device_id
                or not isinstance(preflight_context, dict)
                or any(preflight_context.get(key) != current_context.get(key) for key in context_keys)
                or not isinstance(preflight.get("policy"), dict)
                or preflight["policy"].get("revision") != policy_revision
            ):
                raise RunContextConflict("PREFLIGHT_MISMATCH", "Preflight does not match the requested context")
            context = dict(preflight_context)
            checked_at = preflight.get("checkedAtEpochMillis")
            if (
                preflight.get("ready") is not True
                or not isinstance(checked_at, int)
                or int(self.clock() * 1000) - checked_at > PREFLIGHT_MAX_AGE_MILLIS
                or int(self.clock() * 1000) < checked_at
            ):
                raise RunContextConflict("PREFLIGHT_NOT_READY", "A fresh successful preflight is required", preflight)
            identity = self.pipelines.runtime_identity(pipeline_id)
            if (
                preflight.get("sourceRevision") != identity["sourceRevision"]
                or preflight.get("sourceDirty") != identity["sourceDirty"]
                or preflight.get("release") != identity["release"]
                or preflight.get("configRevision") != identity["configSha256"]
            ):
                raise RunContextConflict("PIPELINE_CHANGED", "Pipeline source or config changed after preflight")
            log_id = self._new_log_id()
            run_id = pipeline_id + "/" + log_id
            output, output_directory, output_context = self._prepare_output(
                pipeline_id, log_id, context, policy, identity
            )
            record = {
                "schemaVersion": RUN_CONTEXT_SCHEMA_VERSION,
                "runId": run_id,
                "logId": log_id,
                "pipelineId": pipeline_id,
                "deviceId": self.device_id,
                "state": "STARTING",
                "active": True,
                "startedAt": _utc_now(self.clock),
                "startedAtEpochMillis": int(self.clock() * 1000),
                "bootId": _boot_id(),
                "finishedAt": None,
                "exitCode": None,
                "stopReason": None,
                "stopIntent": None,
                "clientRequestId": client_request_id,
                "requestBodyHash": body_hash,
                "commandIssuedAt": None,
                "commandOutcome": "PREPARED",
                "contextSnapshot": context,
                "policySnapshot": policy,
                "preflightSnapshot": preflight,
                "sourceRevision": identity["sourceRevision"],
                "sourceDirty": identity["sourceDirty"],
                "release": identity["release"],
                "configRevision": identity["configSha256"],
                "output": output,
                "outputDirectory": str(output_directory),
                "outputContext": output_context,
            }
            _atomic_json(self._record_path(run_id), record)
            launch = dict(record)
            launch["schemaVersion"] = RUN_CONTEXT_SCHEMA_VERSION
            _atomic_json(self._launch_path(pipeline_id), launch, 0o644)
            request_store = self._start_requests()
            self._save_start_request(request_store, {
                "clientRequestId": client_request_id,
                "bodyHash": body_hash,
                "runId": run_id,
                "createdAt": record["startedAt"],
            })
            record["commandIssuedAt"] = _utc_now(self.clock)
            record["commandOutcome"] = "UNKNOWN"
            _atomic_json(self._record_path(run_id), record)
            try:
                controlled = self.pipelines.control(pipeline_id, "start")
            except Exception:
                # A command transport error does not prove systemd rejected the
                # start. Keep the reservation locked until GET reconciliation
                # observes this exact run or a bounded no-log/inactive timeout.
                _atomic_json(self._record_path(run_id), record)
                raise
            record["commandOutcome"] = "COMMAND_COMPLETED"
            _atomic_json(self._record_path(run_id), record)
            return {"run": record, "pipeline": controlled, "outcome": "START_ACCEPTED"}

    def _read_record(self, run_id: str) -> Dict[str, object]:
        value = _read_json(self._record_path(run_id))
        if value.get("runId") != run_id or value.get("schemaVersion") != RUN_CONTEXT_SCHEMA_VERSION:
            raise RunContextError("Run record identity is invalid")
        return value

    @staticmethod
    def _read_tail(path: Path, limit: int = 64 * 1024) -> str:
        descriptor = os.open(path, os.O_RDONLY | getattr(os, "O_NOFOLLOW", 0))
        try:
            metadata = os.fstat(descriptor)
            if not stat.S_ISREG(metadata.st_mode):
                raise OSError("Run log is not a regular file")
            size = min(limit, metadata.st_size)
            return os.pread(descriptor, size, metadata.st_size - size).decode("utf-8", errors="replace")
        finally:
            os.close(descriptor)

    def _terminal_from_log(self, record: Mapping[str, object]) -> Optional[Dict[str, object]]:
        path = self.logs_root / str(record["pipelineId"]) / str(record["logId"])
        try:
            tail = self._read_tail(path)
        except OSError:
            return None
        match = re.search(
            r"=== Jetson pipeline run finished ===\nfinished_at=([^\n]+)\n"
            r"exit_code=(\d+)\nterminal_state=(STOPPED|COMPLETED|FAILED)\n"
            r"stop_signal=(\d*)\n?$",
            tail,
        )
        if match is not None:
            return {
                "finishedAt": match.group(1),
                "exitCode": int(match.group(2)),
                "state": match.group(3),
                "stopSignal": int(match.group(4)) if match.group(4) else None,
            }
        legacy = re.search(
            r"=== Jetson pipeline run finished ===\nfinished_at=([^\n]+)\nexit_code=(\d+)\n?$",
            tail,
        )
        if legacy is None:
            return None
        code = int(legacy.group(2))
        return {
            "finishedAt": legacy.group(1),
            "exitCode": code,
            "state": "COMPLETED" if code == 0 else "STOPPED" if code in (130, 143) else "FAILED",
            "stopSignal": None,
        }

    def _scan_output(self, record: Mapping[str, object]) -> Dict[str, object]:
        directory = Path(str(record["outputDirectory"]))
        expected = record["policySnapshot"]["expectedOutput"]  # type: ignore[index]
        files: List[Tuple[str, int]] = []
        truncated = False
        try:
            for root, directories, names in os.walk(directory, followlinks=False):
                base = Path(root)
                directories[:] = sorted(
                    name for name in directories if not (base / name).is_symlink()
                )
                for name in sorted(names):
                    path = base / name
                    relative = path.relative_to(directory).as_posix()
                    if relative in {".jetson-output-context.json", ".jetson-output-manifest.json"}:
                        continue
                    try:
                        metadata = os.lstat(path)
                    except OSError:
                        continue
                    if stat.S_ISREG(metadata.st_mode):
                        files.append((relative, metadata.st_size))
                        if len(files) >= MAX_OUTPUT_FILES:
                            truncated = True
                            break
                if truncated:
                    break
        except OSError:
            files = []
            truncated = True
        patterns = []
        for pattern in expected["patterns"]:  # type: ignore[index]
            matches = [size for relative, size in files if fnmatch.fnmatchcase(relative, str(pattern))]
            patterns.append({"pattern": pattern, "fileCount": len(matches), "bytesTotal": sum(matches)})
        file_count = len(files)
        bytes_total = sum(size for _, size in files)
        satisfied = (
            not truncated
            and file_count >= int(expected["minFiles"])  # type: ignore[index]
            and bytes_total >= int(expected["minBytes"])  # type: ignore[index]
            and all(item["fileCount"] > 0 for item in patterns)
        )
        return {
            "schemaVersion": 1,
            "runId": record["runId"],
            "generatedAt": _utc_now(self.clock),
            "finishedAt": record.get("finishedAt"),
            "fileCount": file_count,
            "bytesTotal": bytes_total,
            "truncated": truncated,
            "matchedPatterns": patterns,
            "expected": expected,
            "expectationState": "SATISFIED" if satisfied else "NOT_SATISFIED",
        }

    def _write_output_manifest(self, record: Dict[str, object]) -> None:
        manifest = self._scan_output(record)
        try:
            _atomic_json(Path(str(record["outputDirectory"])) / ".jetson-output-manifest.json", manifest, 0o440)
            state = "FINAL"
        except RunContextError:
            state = "MISSING"
        output = dict(record["output"])
        output["manifestState"] = state
        output["manifest"] = manifest
        record["output"] = output

    def get_run(self, run_id: str) -> Dict[str, object]:
        with self._lock:
            record = self._read_record(run_id)
            changed = False
            terminal = self._terminal_from_log(record)
            if terminal is not None and record.get("state") not in TERMINAL_STATES:
                if record.get("stopIntent") is not None and terminal["state"] == "COMPLETED":
                    terminal["state"] = "STOPPED"
                record.update(terminal)
                record["active"] = False
                record["stopReason"] = (
                    "OPERATOR" if record.get("stopIntent") is not None
                    else "SYSTEM_SIGNAL" if terminal["state"] == "STOPPED"
                    else "NATURAL_EXIT"
                )
                changed = True
            elif terminal is None and record.get("state") in {"STARTING", "RUNNING", "STOPPING"}:
                try:
                    pipeline = self.pipelines.status(str(record["pipelineId"]))
                    if (
                        pipeline.get("state") == "RUNNING"
                        and pipeline.get("activeRunId") == record.get("runId")
                    ):
                        record["state"] = "RUNNING"
                        changed = True
                    elif (
                        (self.logs_root / str(record["pipelineId"]) / str(record["logId"])).is_file()
                        and pipeline.get("state") in {"STOPPED", "FAILED"}
                    ):
                        record["state"] = "FAILED"
                        record["active"] = False
                        record["finishedAt"] = _utc_now(self.clock)
                        record["stopReason"] = "INTERRUPTED_NO_TERMINAL_EVIDENCE"
                        changed = True
                    elif (
                        pipeline.get("state") in {"STOPPED", "FAILED"}
                        and isinstance(record.get("startedAtEpochMillis"), int)
                        and int(self.clock() * 1000) - int(record["startedAtEpochMillis"]) > 5_000
                    ):
                        record["state"] = "FAILED"
                        record["active"] = False
                        record["finishedAt"] = _utc_now(self.clock)
                        record["stopReason"] = "START_COMMAND_UNOBSERVED"
                        changed = True
                except Exception:
                    pass
            if record.get("state") in TERMINAL_STATES and record["output"].get("manifestState") != "FINAL":  # type: ignore[index]
                self._write_output_manifest(record)
                changed = True
            if changed:
                _atomic_json(self._record_path(run_id), record)
            if record.get("state") in TERMINAL_STATES:
                self._tombstone_launch(record)
            response = dict(record)
            response.pop("outputDirectory", None)
            response["uploadContext"] = response.pop("outputContext")
            return response

    def _tombstone_launch(self, record: Mapping[str, object]) -> None:
        path = self._launch_path(str(record["pipelineId"]))
        try:
            current = _read_json(path)
        except RunContextError:
            return
        if current.get("runId") != record.get("runId") or current.get("consumed") is True:
            return
        _atomic_json(path, {
            "schemaVersion": RUN_CONTEXT_SCHEMA_VERSION,
            "pipelineId": record["pipelineId"],
            "runId": record["runId"],
            "consumed": True,
            "consumedAt": _utc_now(self.clock),
            "bootId": record.get("bootId"),
        }, 0o644)

    def active_run(self, pipeline_id: str) -> Optional[Dict[str, object]]:
        try:
            paths = list(self.records_dir.glob("*.json"))
        except OSError:
            return None
        candidates = []
        for path in paths:
            try:
                value = _read_json(path)
            except RunContextError:
                continue
            if value.get("pipelineId") == pipeline_id and value.get("active") is True:
                candidates.append(value)
        candidates.sort(key=lambda item: str(item.get("startedAt", "")), reverse=True)
        for value in candidates:
            try:
                current = self.get_run(str(value["runId"]))
            except RunContextError:
                continue
            if current.get("active") is True:
                return current
        return None

    def assert_pipeline_mutable(self, pipeline_id: str) -> None:
        active = self.active_run(pipeline_id)
        if active is not None:
            raise RunContextConflict("CONTEXT_LOCKED", "Active run locks pipeline context and policy", active)

    def assert_survey_mutable(self, project_id: str, section_id: Optional[str] = None) -> None:
        try:
            paths = list(self.records_dir.glob("*.json"))
        except OSError:
            return
        for path in paths:
            try:
                value = _read_json(path)
            except RunContextError:
                continue
            context = value.get("contextSnapshot", {})
            if not isinstance(context, dict) or context.get("surveyProjectId") != project_id:
                continue
            if section_id is not None and context.get("surveySectionId") != section_id:
                continue
            try:
                current = self.get_run(str(value["runId"]))
            except RunContextError:
                continue
            if current.get("active") is True:
                raise RunContextConflict("CONTEXT_LOCKED", "Active run locks survey context", current)

    def create_project(self, label: str, client_request_id: str) -> Dict[str, object]:
        with self._lock:
            return self.survey.create_project(label, client_request_id)

    def update_project(
        self, project_id: str, label: str, expected_revision: Optional[int]
    ) -> Dict[str, object]:
        with self._lock:
            self.assert_survey_mutable(project_id)
            return self.survey.update_project(project_id, label, expected_revision)

    def delete_project(self, project_id: str, expected_revision: Optional[int]) -> Dict[str, object]:
        with self._lock:
            self.assert_survey_mutable(project_id)
            return self.survey.delete_project(project_id, expected_revision)

    def create_section(self, project_id: str, label: str, client_request_id: str) -> Dict[str, object]:
        with self._lock:
            self.assert_survey_mutable(project_id)
            return self.survey.create_section(project_id, label, client_request_id)

    def update_section(
        self, project_id: str, section_id: str, label: str, expected_revision: Optional[int]
    ) -> Dict[str, object]:
        with self._lock:
            self.assert_survey_mutable(project_id, section_id)
            return self.survey.update_section(project_id, section_id, label, expected_revision)

    def delete_section(
        self, project_id: str, section_id: str, expected_revision: Optional[int]
    ) -> Dict[str, object]:
        with self._lock:
            self.assert_survey_mutable(project_id, section_id)
            return self.survey.delete_section(project_id, section_id, expected_revision)

    def mutate_pipeline(self, pipeline_id: str, operation: Callable[..., object], *args: object, **kwargs: object) -> object:
        with self._lock:
            self.assert_pipeline_mutable(pipeline_id)
            return operation(*args, **kwargs)

    def expected_context_for_source(self, root_id: str, relative_path: str) -> Optional[Dict[str, object]]:
        """Resolve an upload source to the exact root-owned run output record."""

        _, source = self.storage.resolve(root_id, relative_path)
        try:
            paths = list(self.records_dir.glob("*.json"))
        except OSError:
            paths = []
        for path in paths:
            try:
                record = _read_json(path)
            except RunContextError:
                continue
            if Path(str(record.get("outputDirectory", ""))).resolve() == source.resolve():
                context = record.get("outputContext")
                if not isinstance(context, dict):
                    raise RunContextError("Canonical output context is invalid")
                return dict(context)
        if (source / ".jetson-output-context.json").exists():
            raise RunContextConflict(
                "UNTRUSTED_OUTPUT_CONTEXT",
                "Output context sidecar has no matching root-owned run record",
            )
        return None

    def reject_legacy_action(self, pipeline_id: str, action: str) -> None:
        if action in {"start", "restart", "enable"} and self.policy_or_none(pipeline_id) is not None:
            raise RunContextConflict(
                "CONTEXT_REQUIRED",
                "Configured pipelines must use preflight and contextual-start",
            )

    def record_stop_intent(self, pipeline_id: str) -> Optional[Dict[str, object]]:
        with self._lock:
            active = self.active_run(pipeline_id)
            if active is None:
                return None
            record = self._read_record(str(active["runId"]))
            if record.get("stopIntent") is None:
                record["state"] = "STOPPING"
                record["stopIntent"] = {"kind": "OPERATOR", "requestedAt": _utc_now(self.clock)}
                _atomic_json(self._record_path(str(record["runId"])), record)
            return record
