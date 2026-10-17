"""Persistent, capability-gated device event reporting for the upload receiver."""
from __future__ import annotations

import copy
import email.utils
import json
import logging
import math
import os
import re
import threading
import time
import uuid
from datetime import datetime, timezone
from pathlib import Path
from typing import Callable, Dict, List, Mapping, Optional, Sequence, Tuple

LOGGER = logging.getLogger(__name__)
EVENT_SCHEMA = "jetson.event.v1"
RUN_TERMINAL_STATES = {"COMPLETED", "FAILED", "STOPPED"}
MAX_QUEUED_EVENTS = 2000
EVENT_TTL_SECONDS = 24 * 60 * 60
CAPABILITIES_CACHE_SECONDS = 10 * 60
DEFAULT_HEARTBEAT_SECONDS = 300
TEMP_EVALUATION_SECONDS = 30
STORAGE_EVALUATION_SECONDS = 5 * 60
RUN_POLL_SECONDS = 30
SEND_INTERVAL_SECONDS = 10
MAX_BACKOFF_SECONDS = 10 * 60
EVENT_HTTP_TIMEOUT_SECONDS = 15


def _timestamp(epoch_seconds: float) -> str:
    return datetime.fromtimestamp(epoch_seconds).astimezone().isoformat(timespec="seconds")


def _parse_timestamp(value: object) -> Optional[float]:
    if not isinstance(value, str):
        return None
    try:
        parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
        if parsed.tzinfo is None:
            return None
        return parsed.timestamp()
    except (OSError, OverflowError, ValueError):
        return None


def _atomic_json(path: Path, value: Mapping[str, object]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    try:
        os.chmod(path.parent, 0o700)
    except OSError:
        pass
    temporary = path.with_name("." + path.name + "." + uuid.uuid4().hex + ".tmp")
    descriptor: Optional[int] = None
    try:
        descriptor = os.open(
            temporary,
            os.O_WRONLY | os.O_CREAT | os.O_EXCL | getattr(os, "O_NOFOLLOW", 0),
            0o600,
        )
        with os.fdopen(descriptor, "w", encoding="utf-8") as output:
            descriptor = None
            json.dump(value, output, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
            output.write("\n")
            output.flush()
            os.fsync(output.fileno())
        os.chmod(temporary, 0o600)
        os.replace(temporary, path)
        try:
            directory_fd = os.open(path.parent, os.O_RDONLY | getattr(os, "O_DIRECTORY", 0))
            try:
                os.fsync(directory_fd)
            finally:
                os.close(directory_fd)
        except OSError:
            pass
    finally:
        if descriptor is not None:
            os.close(descriptor)
        try:
            temporary.unlink()
        except FileNotFoundError:
            pass


class DeviceEventQueue:
    """One atomic private state file for queued events, active runs, and failure counts."""

    def __init__(
        self,
        directory: Path,
        *,
        clock: Callable[[], float] = time.time,
        max_events: int = MAX_QUEUED_EVENTS,
    ) -> None:
        self.directory = Path(directory)
        self.path = self.directory / "queue.json"
        self.clock = clock
        self.max_events = max_events
        self._lock = threading.RLock()
        self.directory.mkdir(parents=True, exist_ok=True, mode=0o700)
        try:
            os.chmod(self.directory, 0o700)
        except OSError:
            pass
        self._state = self._load()

    def _load(self) -> Dict[str, object]:
        try:
            value = json.loads(self.path.read_text(encoding="utf-8"))
        except (OSError, UnicodeDecodeError, json.JSONDecodeError):
            value = None
        if not isinstance(value, dict) or value.get("version") != 1:
            return {"version": 1, "events": [], "startedRuns": {}, "failureCounts": {}}
        events = value.get("events")
        runs = value.get("startedRuns")
        counts = value.get("failureCounts")
        return {
            "version": 1,
            "events": [item for item in events if isinstance(item, dict)][-self.max_events:]
            if isinstance(events, list) else [],
            "startedRuns": dict(runs) if isinstance(runs, dict) else {},
            "failureCounts": {
                str(key): count for key, count in counts.items()
                if isinstance(count, int) and not isinstance(count, bool) and count > 0
            } if isinstance(counts, dict) else {},
        }

    def _save_locked(self) -> None:
        _atomic_json(self.path, self._state)

    def _expire_locked(self, now: float) -> bool:
        events = self._state["events"]
        kept = []
        for event in events:
            occurred_at = _parse_timestamp(event.get("occurredAt"))
            if occurred_at is not None and now - occurred_at <= EVENT_TTL_SECONDS:
                kept.append(event)
        if kept == events:
            return False
        self._state["events"] = kept
        return True

    def _append_locked(self, event: Mapping[str, object]) -> None:
        if event.get("type") == "device.heartbeat":
            return
        events = self._state["events"]
        events.append(dict(event))
        if len(events) > self.max_events:
            del events[: len(events) - self.max_events]

    def enqueue(self, event: Mapping[str, object]) -> None:
        with self._lock:
            before = len(self._state["events"])
            self._append_locked(event)
            if event.get("type") != "device.heartbeat" or len(self._state["events"]) != before:
                self._save_locked()

    def remember_started_run(
        self,
        run_id: str,
        info: Mapping[str, object],
        event: Mapping[str, object],
    ) -> bool:
        with self._lock:
            runs = self._state["startedRuns"]
            if run_id in runs:
                return False
            runs[run_id] = dict(info)
            self._append_locked(event)
            self._save_locked()
            return True

    def finish_run(self, run_id: str, event: Mapping[str, object]) -> bool:
        with self._lock:
            runs = self._state["startedRuns"]
            if run_id not in runs:
                return False
            self._append_locked(event)
            del runs[run_id]
            self._save_locked()
            return True

    def started_runs(self) -> Dict[str, Dict[str, object]]:
        with self._lock:
            return copy.deepcopy(self._state["startedRuns"])

    def events(self, *, flush: bool = False) -> List[Dict[str, object]]:
        with self._lock:
            if flush and self._expire_locked(self.clock()):
                self._save_locked()
            return copy.deepcopy(self._state["events"])

    def acknowledge(self, event_ids: Sequence[str]) -> None:
        acknowledged = set(event_ids)
        if not acknowledged:
            return
        with self._lock:
            self._state["events"] = [
                event for event in self._state["events"]
                if event.get("eventId") not in acknowledged
            ]
            self._save_locked()

    def next_failure_count(self, key: str) -> int:
        with self._lock:
            counts = self._state["failureCounts"]
            count = int(counts.get(key, 0)) + 1
            counts[key] = count
            self._save_locked()
            return count

    def reset_failure_count(self, key: str) -> None:
        with self._lock:
            counts = self._state["failureCounts"]
            if key in counts:
                del counts[key]
                self._save_locked()


class TemperatureLevelDetector:
    """Temperature level detector with a two-sample rise and ten-minute recovery dwell."""

    def __init__(self, *, clock: Callable[[], float] = time.time) -> None:
        self.clock = clock
        self.level = "NORMAL"
        self.peak_c: Optional[float] = None
        self.last_normal_at: Optional[str] = None
        self._last_evaluation: Optional[float] = None
        self._rise_level: Optional[str] = None
        self._rise_count = 0
        self._recovery_level: Optional[str] = None
        self._recovery_since: Optional[float] = None

    def evaluate(self, temperature_c: object, now: Optional[float] = None) -> Optional[Dict[str, object]]:
        if isinstance(temperature_c, bool) or not isinstance(temperature_c, (int, float)):
            return None
        temperature = float(temperature_c)
        if not math.isfinite(temperature):
            return None
        current = self.clock() if now is None else now
        if self._last_evaluation is not None and current - self._last_evaluation < TEMP_EVALUATION_SECONDS:
            return None
        self._last_evaluation = current

        if self.level == "NORMAL":
            target = "CRITICAL" if temperature >= 90 else "WARNING" if temperature >= 80 else None
            if target is None:
                self._rise_level = None
                self._rise_count = 0
                self.peak_c = None
                return None
            self.peak_c = max(self.peak_c or temperature, temperature)
            if target == self._rise_level:
                self._rise_count += 1
            else:
                self._rise_level = target
                self._rise_count = 1
            if self._rise_count < 2:
                return None
            self.level = target
            self._rise_level = None
            self._rise_count = 0
            return self._event(temperature)

        self.peak_c = max(self.peak_c or temperature, temperature)
        if self.level == "CRITICAL":
            recovery = "NORMAL" if temperature < 77 else "WARNING" if temperature < 87 else None
        else:
            recovery = "NORMAL" if temperature < 77 else None
        if recovery is None:
            self._recovery_level = None
            self._recovery_since = None
            if self.level == "WARNING" and temperature >= 90:
                self._track_rise("CRITICAL")
                if self._rise_count >= 2:
                    self.level = "CRITICAL"
                    self._rise_level = None
                    self._rise_count = 0
                    return self._event(temperature)
            else:
                self._rise_level = None
                self._rise_count = 0
            return None
        self._rise_level = None
        self._rise_count = 0
        if recovery != self._recovery_level:
            self._recovery_level = recovery
            self._recovery_since = current
            return None
        if self._recovery_since is None or current - self._recovery_since < 600:
            return None
        self.level = recovery
        if recovery == "NORMAL":
            self.last_normal_at = _timestamp(current)
        self._recovery_level = None
        self._recovery_since = None
        event = self._event(temperature)
        if recovery == "NORMAL":
            self.peak_c = None
        return event

    def _track_rise(self, level: str) -> None:
        if level == self._rise_level:
            self._rise_count += 1
        else:
            self._rise_level = level
            self._rise_count = 1

    def _event(self, temperature: float) -> Dict[str, object]:
        event: Dict[str, object] = {
            "type": "device.temperature",
            "level": self.level,
            "temperatureC": temperature,
            "thresholdC": 90.0 if self.level == "CRITICAL" else 80.0,
            "peakC": self.peak_c if self.peak_c is not None else temperature,
        }
        if self.last_normal_at is not None:
            event["lastNormalAt"] = self.last_normal_at
        return event


class StorageLevelDetector:
    """Storage threshold detector with percentage-point hysteresis and recovery dwell."""

    def __init__(self, *, clock: Callable[[], float] = time.time) -> None:
        self.clock = clock
        self.level = "NORMAL"
        self.last_normal_at: Optional[str] = None
        self._recovery_level: Optional[str] = None
        self._recovery_since: Optional[float] = None

    def evaluate(
        self,
        free_bytes: object,
        total_bytes: object,
        now: Optional[float] = None,
    ) -> Optional[Dict[str, object]]:
        if (
            isinstance(free_bytes, bool) or not isinstance(free_bytes, (int, float))
            or isinstance(total_bytes, bool) or not isinstance(total_bytes, (int, float))
            or total_bytes <= 0
        ):
            return None
        free = max(0.0, float(free_bytes))
        total = float(total_bytes)
        percent = min(100.0, free * 100 / total)
        current = self.clock() if now is None else now
        if self.level == "NORMAL":
            if percent < 5:
                self.level = "CRITICAL"
            elif percent < 15:
                self.level = "WARNING"
            else:
                return None
            return self._event(percent, int(free), int(total))

        if self.level == "CRITICAL":
            recovered = percent >= 8
            target = "NORMAL" if percent >= 18 else "WARNING"
        else:
            recovered = percent >= 18
            target = "NORMAL"
        if not recovered:
            self._recovery_level = None
            self._recovery_since = None
            if self.level == "WARNING" and percent < 5:
                self.level = "CRITICAL"
                return self._event(percent, int(free), int(total))
            return None
        if target != self._recovery_level:
            self._recovery_level = target
            self._recovery_since = current
            return None
        if self._recovery_since is None or current - self._recovery_since < 600:
            return None
        self.level = target
        if target == "NORMAL":
            self.last_normal_at = _timestamp(current)
        self._recovery_level = None
        self._recovery_since = None
        return self._event(percent, int(free), int(total))

    def _event(self, percent: float, free_bytes: int, total_bytes: int) -> Dict[str, object]:
        event: Dict[str, object] = {
            "type": "device.storage",
            "level": self.level,
            "freePercent": round(percent, 3),
            "freeBytes": free_bytes,
            "totalBytes": total_bytes,
        }
        if self.last_normal_at is not None:
            event["lastNormalAt"] = self.last_normal_at
        return event


def select_device_events_target(upload_manager: object) -> Optional[object]:
    """Choose the sole flagged target, or the sole configured target as fallback."""
    targets_method = getattr(upload_manager, "targets", None)
    if not callable(targets_method):
        return None
    configured = targets_method()
    values = list(configured.values()) if isinstance(configured, Mapping) else list(configured)
    flagged = [
        target for target in values
        if _target_value(target, "device_events", "deviceEvents") is True
    ]
    selected = flagged[0] if len(flagged) == 1 else None
    if not flagged and len(values) == 1:
        selected = values[0]
    if selected is None or _target_value(selected, "kind", "type") != "http":
        return None
    return selected


def _target_value(target: object, attribute: str, key: str) -> object:
    if isinstance(target, Mapping):
        return target.get(key, target.get(attribute))
    return getattr(target, attribute, None)


class DeviceEventService:
    """Coordinates detectors, run reconciliation, queue delivery, and heartbeats."""

    def __init__(
        self,
        *,
        state_dir: Path,
        device_name: str,
        upload_manager: object,
        status_snapshots: object,
        run_context: object,
        clock: Callable[[], float] = time.time,
        transport: Optional[Callable[[object, str, str, str, Optional[bytes]], Tuple[int, bytes, Mapping[str, str]]]] = None,
    ) -> None:
        self.clock = clock
        self.device_name = device_name
        self.upload_manager = upload_manager
        self.status_snapshots = status_snapshots
        self.run_context = run_context
        self.transport = transport or self._http_transport
        self.queue = DeviceEventQueue(Path(state_dir) / "device-events", clock=clock)
        self.temperature = TemperatureLevelDetector(clock=clock)
        self.storage = StorageLevelDetector(clock=clock)
        self.target: Optional[object] = None
        self.capabilities: Optional[Dict[str, int]] = None
        self._capability_checked_at: Optional[float] = None
        self._next_temperature_at: Optional[float] = None
        self._next_storage_at: Optional[float] = None
        self._next_run_poll_at: Optional[float] = None
        self._next_heartbeat_at: Optional[float] = None
        self._next_send_at: Optional[float] = None
        self._backoff_until = 0.0
        self._retry_delay = 30
        self._capability_backoff_until = 0.0
        self._capability_retry_delay = 30
        self._batch_limit: Optional[int] = None
        self._send_requested = False
        self._disabled_logged = False
        self._capability_disabled_logged = False
        self._startup_reconciled = False
        self._stop = threading.Event()
        self._wake = threading.Event()
        self._thread: Optional[threading.Thread] = None

    def start(self) -> None:
        """Select the target and start the worker; called again when upload targets change."""
        previous = self.target
        try:
            self.target = select_device_events_target(self.upload_manager)
        except Exception:
            self.target = None
        if self.target != previous:
            # Capabilities and backoff belong to the previous receiver.
            self.capabilities = None
            self._capability_checked_at = None
            self._backoff_until = 0.0
            self._capability_backoff_until = 0.0
        if self.target is None:
            if not self._disabled_logged:
                LOGGER.info("Device events disabled: no unambiguous HTTP upload target is configured")
                self._disabled_logged = True
            return
        if self._thread is not None and self._thread.is_alive():
            return
        self._stop.clear()
        self._wake.set()
        self._thread = threading.Thread(target=self._run, name="device-events", daemon=True)
        self._thread.start()

    def stop(self) -> None:
        self._stop.set()
        self._wake.set()
        thread = self._thread
        if thread is not None:
            thread.join(timeout=EVENT_HTTP_TIMEOUT_SECONDS + 2)
            if not thread.is_alive():
                self._thread = None

    def notify_new_event(self) -> None:
        self._send_requested = True
        self._wake.set()

    def _run(self) -> None:
        while not self._stop.is_set():
            try:
                self.process_due()
            except Exception:
                LOGGER.exception("Device event worker cycle failed")
            timeout = self._wait_timeout()
            woke = self._wake.wait(timeout)
            if woke:
                self._wake.clear()
                self._send_requested = True

    def _wait_timeout(self) -> float:
        now = self.clock()
        due = [now + 60]
        has_events = bool(self.queue.events())
        if has_events and self.capabilities is not None:
            due.append(
                self._next_send_at
                if self._next_send_at is not None and self._next_send_at > now
                else now + SEND_INTERVAL_SECONDS
            )
        for value in (
            self._next_temperature_at,
            self._next_storage_at,
            self._next_run_poll_at,
            self._next_heartbeat_at,
            self._next_send_at if has_events and self.capabilities is not None else None,
            self._capability_checked_at + CAPABILITIES_CACHE_SECONDS
            if self._capability_checked_at is not None else now,
            self._backoff_until if self._backoff_until > now else None,
            self._capability_backoff_until if self._capability_backoff_until > now else None,
        ):
            if value is not None and value > now:
                due.append(value)
        return max(0.05, min(60.0, min(due) - now))

    def process_due(self, *, force_send: bool = False) -> None:
        now = self.clock()
        if self.target is None:
            return
        if not self._startup_reconciled:
            self._reconcile_remembered_runs()
            self._startup_reconciled = True
            self._next_run_poll_at = now + RUN_POLL_SECONDS
        elif self._next_run_poll_at is None or now >= self._next_run_poll_at:
            self._poll_runs()
            self._next_run_poll_at = now + RUN_POLL_SECONDS
        if self._next_temperature_at is None or now >= self._next_temperature_at:
            self._evaluate_temperature(now)
            self._next_temperature_at = now + TEMP_EVALUATION_SECONDS
        if self._next_storage_at is None or now >= self._next_storage_at:
            self._evaluate_storage(now)
            self._next_storage_at = now + STORAGE_EVALUATION_SECONDS
        if self._capability_checked_at is None or now - self._capability_checked_at >= CAPABILITIES_CACHE_SECONDS:
            if now >= max(self._backoff_until, self._capability_backoff_until):
                self._fetch_capabilities(now)
        if self.capabilities is not None:
            if self._next_heartbeat_at is None:
                self._next_heartbeat_at = now + self.capabilities["heartbeatSeconds"]
            elif now >= self._next_heartbeat_at:
                self._send_heartbeat()
                self._next_heartbeat_at = now + self.capabilities["heartbeatSeconds"]
        pending = self.queue.events(flush=True)
        send_due = force_send or self._send_requested or (
            self._next_send_at is None or now >= self._next_send_at
        )
        self._send_requested = False
        if (
            self.capabilities is not None
            and pending
            and send_due
            and now >= max(self._backoff_until, self._capability_backoff_until)
        ):
            self._send_batch(pending, now)
            if self._next_send_at is None or self._next_send_at <= now:
                self._next_send_at = max(now + SEND_INTERVAL_SECONDS, self._backoff_until)

    def _fetch_capabilities(self, now: float) -> bool:
        self.capabilities = None
        try:
            status, body, headers = self._request("GET", "/v1/capabilities", None)
        except Exception:
            self._capability_network_backoff(now)
            return False
        if status == 401 or status == 403:
            self._set_auth_backoff(now, status)
            return False
        if status == 429:
            delay = self._retry_after(headers.get("Retry-After"), now)
            self._capability_backoff_until = now + delay
            LOGGER.warning("Device event capabilities request rate limited; retryAfter=%ss", int(delay))
            return False
        if status >= 500:
            LOGGER.warning("Device event capabilities request failed (HTTP %s)", status)
            self._capability_network_backoff(now)
            return False
        if status == 404:
            self.capabilities = None
            self._capability_checked_at = now
            self._capability_backoff_until = 0.0
            self._capability_retry_delay = 30
            self._log_capability_disabled()
            return False
        if status != 200:
            self._capability_network_backoff(now)
            return False
        try:
            raw = json.loads(body.decode("utf-8")) if body else {}
        except (UnicodeDecodeError, json.JSONDecodeError):
            self._capability_network_backoff(now)
            return False
        settings = raw.get("deviceEvents") if isinstance(raw, dict) else None
        if (
            not isinstance(settings, dict)
            or isinstance(settings.get("version"), bool)
            or settings.get("version") != 1
        ):
            self.capabilities = None
            self._capability_checked_at = now
            self._capability_backoff_until = 0.0
            self._capability_retry_delay = 30
            self._log_capability_disabled()
            return False
        max_events = settings.get("maxEvents")
        max_bytes = settings.get("maxBytes")
        heartbeat_seconds = settings.get("heartbeatSeconds", DEFAULT_HEARTBEAT_SECONDS)
        if any(
            isinstance(value, bool) or not isinstance(value, int) or value < 1
            for value in (max_events, max_bytes, heartbeat_seconds)
        ):
            self.capabilities = None
            self._capability_checked_at = now
            self._capability_backoff_until = 0.0
            self._capability_retry_delay = 30
            self._log_capability_disabled()
            return False
        self.capabilities = {
            "maxEvents": max_events,
            "maxBytes": max_bytes,
            "heartbeatSeconds": heartbeat_seconds,
        }
        self._capability_checked_at = now
        self._batch_limit = min(self._batch_limit or max_events, max_events)
        self._capability_disabled_logged = False
        self._capability_backoff_until = 0.0
        self._capability_retry_delay = 30
        return True

    def _log_capability_disabled(self) -> None:
        if not self._capability_disabled_logged:
            LOGGER.info("Device events disabled: receiver does not advertise deviceEvents version 1")
            self._capability_disabled_logged = True

    def _evaluate_temperature(self, now: float) -> None:
        snapshot = self._snapshot()
        if snapshot is None or not self._metric_valid(snapshot, "temperatureC"):
            return
        event = self.temperature.evaluate(snapshot.get("temperatureC"), now)
        if event is not None:
            self._enqueue(event)

    def _evaluate_storage(self, now: float) -> None:
        snapshot = self._snapshot()
        if snapshot is None or not self._metric_valid(snapshot, "storageAvailableBytes"):
            return
        if not self._metric_valid(snapshot, "storageTotalBytes"):
            return
        event = self.storage.evaluate(
            snapshot.get("storageAvailableBytes"), snapshot.get("storageTotalBytes"), now
        )
        if event is not None:
            self._enqueue(event)

    def _snapshot(self) -> Optional[Dict[str, object]]:
        method = getattr(self.status_snapshots, "snapshot", None)
        if not callable(method):
            return None
        try:
            value = method()
        except (LookupError, OSError, RuntimeError):
            return None
        return value if isinstance(value, dict) else None

    @staticmethod
    def _metric_valid(snapshot: Mapping[str, object], name: str) -> bool:
        if snapshot.get("statusFresh") is False:
            return False
        validity = snapshot.get("metricValidity")
        if isinstance(validity, dict) and name in validity:
            item = validity.get(name)
            if isinstance(item, dict) and item.get("validity") != "valid":
                return False
        return True

    def _enqueue(self, fields: Mapping[str, object]) -> Dict[str, object]:
        event = self._new_event(fields)
        self.queue.enqueue(event)
        self.notify_new_event()
        return event

    def _new_event(self, fields: Mapping[str, object]) -> Dict[str, object]:
        event = {"eventId": str(uuid.uuid4()), "occurredAt": _timestamp(self.clock())}
        event.update(fields)
        return event

    def on_run_started(self, run: Mapping[str, object]) -> bool:
        run_id = run.get("runId")
        if not isinstance(run_id, str) or not run_id:
            return False
        info = self._run_info(run)
        fields: Dict[str, object] = {
            "type": "run.started",
            "runId": run_id,
            "pipelineId": str(run.get("pipelineId", run_id.partition("/")[0])),
        }
        self._copy_run_labels(fields, run)
        event = self._new_event(fields)
        try:
            queued = self.queue.remember_started_run(run_id, info, event)
        except (OSError, TypeError, ValueError):
            LOGGER.error("Could not persist run.started event runId=%s", run_id)
            return False
        if queued:
            self.notify_new_event()
        return queued

    def _reconcile_remembered_runs(self) -> None:
        for run_id in self.queue.started_runs():
            self._resolve_run(run_id)

    def _poll_runs(self) -> None:
        for run_id in self.queue.started_runs():
            self._resolve_run(run_id)

    def _resolve_run(self, run_id: str) -> None:
        getter = getattr(self.run_context, "get_run", None)
        if not callable(getter):
            return
        try:
            run = getter(run_id)
        except Exception:
            return
        if not isinstance(run, Mapping) or run.get("state") not in RUN_TERMINAL_STATES:
            return
        event = self._run_finished_event(run_id, run)
        if self.queue.finish_run(run_id, self._new_event(event)):
            self.notify_new_event()

    def _run_finished_event(self, run_id: str, run: Mapping[str, object]) -> Dict[str, object]:
        fields: Dict[str, object] = {
            "type": "run.finished",
            "runId": run_id,
            "pipelineId": str(run.get("pipelineId", run_id.partition("/")[0])),
            "result": run.get("state"),
        }
        self._copy_run_labels(fields, run)
        quality = run.get("quality") if isinstance(run.get("quality"), Mapping) else {}
        telemetry = run.get("telemetry") if isinstance(run.get("telemetry"), Mapping) else {}
        duration = run.get("durationSeconds")
        if not isinstance(duration, (int, float)) or isinstance(duration, bool):
            duration_millis = run.get(
                "durationMillis",
                telemetry.get("durationMillis", quality.get("durationMillis")),
            )
            if isinstance(duration_millis, (int, float)) and not isinstance(duration_millis, bool):
                duration = float(duration_millis) / 1000
        if isinstance(duration, (int, float)) and not isinstance(duration, bool) and math.isfinite(float(duration)):
            fields["durationSeconds"] = max(0, float(duration))
        bytes_written = run.get("bytesWritten")
        if not isinstance(bytes_written, int) or isinstance(bytes_written, bool):
            output = run.get("output") if isinstance(run.get("output"), Mapping) else {}
            manifest = output.get("manifest") if isinstance(output.get("manifest"), Mapping) else {}
            bytes_written = manifest.get(
                "bytesTotal", telemetry.get("collectedBytes", quality.get("collectedBytes"))
            )
        if isinstance(bytes_written, int) and not isinstance(bytes_written, bool) and bytes_written >= 0:
            fields["bytesWritten"] = bytes_written
        precision = run.get("preciseFixRatio", quality.get("preciseFixRatio"))
        if (
            isinstance(precision, (int, float)) and not isinstance(precision, bool)
            and math.isfinite(float(precision)) and 0 <= float(precision) <= 1
        ):
            fields["preciseFixRatio"] = float(precision)
        return fields

    @staticmethod
    def _run_info(run: Mapping[str, object]) -> Dict[str, object]:
        return {
            key: value for key, value in run.items()
            if key in {
                "runId", "pipelineId", "startedAt", "startedAtEpochMillis",
                "contextSnapshot", "bootId",
            }
        }

    @staticmethod
    def _copy_run_labels(fields: Dict[str, object], run: Mapping[str, object]) -> None:
        context = run.get("contextSnapshot")
        if not isinstance(context, Mapping):
            context = {}
        for name in ("label", "surveyProjectLabel", "surveySectionLabel"):
            value = run.get(name, context.get(name))
            if isinstance(value, str) and value.strip():
                fields[name] = value.strip()

    def on_upload_transition(
        self,
        job: Mapping[str, object],
        state: str,
        error_code: Optional[str],
    ) -> None:
        context = job.get("context") if isinstance(job.get("context"), Mapping) else {}
        run_id = context.get("runId") if isinstance(context, Mapping) else None
        job_id = job.get("id")
        key = run_id if isinstance(run_id, str) and run_id else str(job_id or "unknown")
        if state == "COMPLETED":
            self.queue.reset_failure_count(key)
            return
        if state != "FAILED":
            return
        count = self.queue.next_failure_count(key)
        fields: Dict[str, object] = {
            "type": "upload.failed",
            "jobId": str(job_id or "unknown"),
            "failureCount": count,
            "errorCode": (
                error_code[:64]
                if isinstance(error_code, str) and re.fullmatch(r"[A-Z0-9_]{1,64}", error_code)
                else "UNEXPECTED_FAILURE"
            ),
        }
        if isinstance(run_id, str) and run_id:
            fields["runId"] = run_id
            fields.update(self._labels_for_run(run_id))
        elif isinstance(context, Mapping):
            for name in ("label", "surveyProjectLabel", "surveySectionLabel"):
                value = context.get(name)
                if isinstance(value, str) and value.strip():
                    fields[name] = value.strip()
        self._enqueue(fields)

    def _labels_for_run(self, run_id: str) -> Dict[str, object]:
        getter = getattr(self.run_context, "get_run", None)
        if not callable(getter):
            return {}
        try:
            run = getter(run_id)
        except Exception:
            return {}
        fields: Dict[str, object] = {}
        if isinstance(run, Mapping):
            self._copy_run_labels(fields, run)
        return fields

    def _send_batch(self, queued: Sequence[Mapping[str, object]], now: float) -> None:
        assert self.capabilities is not None
        batch = self._select_batch(queued)
        if not batch:
            return
        body = self._encode_envelope(batch)
        try:
            status, response_body, headers = self._request("POST", "/v1/device-events", body)
        except Exception:
            self._network_backoff(now)
            return
        if status == 200:
            self._backoff_until = 0.0
            self._retry_delay = 30
            try:
                response = json.loads(response_body.decode("utf-8")) if response_body else {}
            except (UnicodeDecodeError, json.JSONDecodeError):
                return
            results = response.get("results") if isinstance(response, dict) else None
            if not isinstance(results, list):
                return
            remove: List[str] = []
            batch_ids = {str(event.get("eventId")) for event in batch}
            for result in results:
                if not isinstance(result, dict):
                    continue
                event_id = result.get("eventId")
                state = result.get("state")
                if event_id not in batch_ids:
                    continue
                if state in {"ACCEPTED", "DUPLICATE"}:
                    remove.append(str(event_id))
                elif state == "REJECTED":
                    remove.append(str(event_id))
                    LOGGER.warning(
                        "Device event rejected by receiver eventId=%s reason=%s",
                        event_id,
                        str(result.get("reason", "unspecified"))[:256],
                    )
            self.queue.acknowledge(remove)
            return
        if status == 413:
            if len(batch) <= 1:
                self.queue.acknowledge([str(batch[0].get("eventId", ""))])
                LOGGER.warning("Dropping device event rejected as too large eventId=%s", batch[0].get("eventId"))
            else:
                self._batch_limit = max(1, len(batch) // 2)
                LOGGER.info("Reducing device event batch after HTTP 413; maxEvents=%s", self._batch_limit)
            return
        if status == 429:
            delay = self._retry_after(headers.get("Retry-After"), now)
            self._backoff_until = now + delay
            LOGGER.warning("Device event batch rate limited; retryAfter=%ss", int(delay))
            return
        if status in {401, 403}:
            self._set_auth_backoff(now, status)
            return
        if status >= 500:
            LOGGER.warning("Device event batch failed (HTTP %s)", status)
            self._network_backoff(now)
            return
        if status == 400:
            self.queue.acknowledge([str(event.get("eventId", "")) for event in batch])
            LOGGER.error("Dropping device event batch rejected by receiver (HTTP 400), events=%s", len(batch))
            return
        if status >= 400:
            self._network_backoff(now)

    def _select_batch(self, queued: Sequence[Mapping[str, object]]) -> List[Mapping[str, object]]:
        assert self.capabilities is not None
        max_events = min(self.capabilities["maxEvents"], self._batch_limit or self.capabilities["maxEvents"])
        selected: List[Mapping[str, object]] = []
        for event in queued:
            if len(selected) >= max_events:
                break
            candidate = selected + [event]
            if len(self._encode_envelope(candidate)) > self.capabilities["maxBytes"]:
                if selected:
                    break
                return [event]
            selected = candidate
        return selected

    def _encode_envelope(self, events: Sequence[Mapping[str, object]]) -> bytes:
        envelope = {
            "schema": EVENT_SCHEMA,
            "device": {"name": self.device_name},
            "events": [dict(event) for event in events],
        }
        return json.dumps(envelope, ensure_ascii=False, separators=(",", ":")).encode("utf-8")

    def _send_heartbeat(self) -> None:
        if self.capabilities is None or self.clock() < self._backoff_until:
            return
        active_run_id: Optional[str] = None
        for run_id in self.queue.started_runs():
            getter = getattr(self.run_context, "get_run", None)
            try:
                run = getter(run_id) if callable(getter) else None
            except Exception:
                run = None
            if not isinstance(run, Mapping) or run.get("state") not in RUN_TERMINAL_STATES:
                active_run_id = run_id
                break
        active_job_id: Optional[str] = None
        list_jobs = getattr(self.upload_manager, "list_jobs", None)
        if callable(list_jobs):
            try:
                jobs = list_jobs(active_only=True)
            except Exception:
                jobs = []
            if isinstance(jobs, list):
                for job in jobs:
                    if isinstance(job, Mapping) and isinstance(job.get("id"), str):
                        active_job_id = str(job["id"])
                        break
        fields: Dict[str, object] = {
            "type": "device.heartbeat",
            "onDuty": active_run_id is not None or active_job_id is not None,
        }
        if active_run_id is not None:
            fields["activeRunId"] = active_run_id
        if active_job_id is not None:
            fields["activeUploadJobId"] = active_job_id
        event = self._new_event(fields)
        try:
            status, _body, headers = self._request(
                "POST", "/v1/device-events", self._encode_envelope([event])
            )
        except Exception:
            return
        if status == 401 or status == 403:
            self._set_auth_backoff(self.clock(), status)
        elif status == 429:
            self._backoff_until = self.clock() + self._retry_after(headers.get("Retry-After"), self.clock())

    def _request(
        self,
        method: str,
        endpoint: str,
        body: Optional[bytes],
    ) -> Tuple[int, bytes, Mapping[str, str]]:
        if self.target is None:
            raise RuntimeError("No device event target")
        token_reader = getattr(self.upload_manager, "_read_target_token", None)
        if not callable(token_reader):
            raise RuntimeError("Upload manager cannot read target credentials")
        token = token_reader(self.target)
        return self.transport(self.target, method, endpoint, token, body)

    def _http_transport(
        self,
        target: object,
        method: str,
        endpoint: str,
        token: str,
        body: Optional[bytes],
    ) -> Tuple[int, bytes, Mapping[str, str]]:
        connection_factory = getattr(self.upload_manager, "_http_connection", None)
        if not callable(connection_factory):
            raise RuntimeError("Upload manager HTTP connection helper is unavailable")
        connection, base_path = connection_factory(target)
        try:
            connection.timeout = EVENT_HTTP_TIMEOUT_SECONDS
            headers = {"Authorization": f"Bearer {token}", "Accept": "application/json"}
            if body is not None:
                headers.update({"Content-Type": "application/json", "Content-Length": str(len(body))})
            connection.request(method, base_path + endpoint, body=body, headers=headers)
            response = connection.getresponse()
            response_body = response.read(1024 * 1024 + 1)
            response_headers = {
                "Retry-After": response.getheader("Retry-After", ""),
            }
            return response.status, response_body, response_headers
        finally:
            connection.close()

    def _network_backoff(self, now: float) -> None:
        self._backoff_until = now + self._retry_delay
        LOGGER.warning("Device event batch request failed; retryAfter=%ss", self._retry_delay)
        self._retry_delay = min(MAX_BACKOFF_SECONDS, self._retry_delay * 2)

    def _capability_network_backoff(self, now: float) -> None:
        self._capability_backoff_until = now + self._capability_retry_delay
        LOGGER.warning(
            "Device event capabilities request failed; retryAfter=%ss",
            self._capability_retry_delay,
        )
        self._capability_retry_delay = min(
            MAX_BACKOFF_SECONDS, self._capability_retry_delay * 2
        )

    def _set_auth_backoff(self, now: float, status: Optional[int] = None) -> None:
        self._backoff_until = now + 30 * 60
        LOGGER.warning(
            "Device event authorization failed (HTTP %s); retryAfter=1800s",
            status if status is not None else "unknown",
        )

    @staticmethod
    def _retry_after(value: Optional[str], now: float) -> float:
        if not value:
            return 60.0
        try:
            return max(0.0, float(value.strip()))
        except (TypeError, ValueError):
            try:
                parsed = email.utils.parsedate_to_datetime(value)
                if parsed.tzinfo is None:
                    parsed = parsed.replace(tzinfo=timezone.utc)
                return max(0.0, parsed.timestamp() - now)
            except (TypeError, ValueError, OverflowError):
                return 60.0
