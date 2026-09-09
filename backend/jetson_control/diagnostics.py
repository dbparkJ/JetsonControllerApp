"""Bounded, private connection evidence. Never stores request bodies or identifiers.

The queue is intentionally lossy under pressure: connection work must not wait for
storage. These records prove server observations, not delivery to the phone.
"""
from __future__ import annotations

import argparse
import collections
import hashlib
import json
import logging
import os
import queue
import re
import stat
import threading
import time
import uuid
import zipfile
from pathlib import Path
from typing import Any, Optional

from . import __version__

LOGGER = logging.getLogger(__name__)
SCHEMA = 1
MAX_FILE_BYTES = 1024 * 1024
RETENTION_SECONDS = 7 * 24 * 60 * 60
CLOCK_NAME = "boottime" if hasattr(time, "CLOCK_BOOTTIME") else "monotonic"


def elapsed_millis() -> int:
    if hasattr(time, "CLOCK_BOOTTIME"):
        return int(time.clock_gettime(time.CLOCK_BOOTTIME) * 1000)
    return int(time.monotonic() * 1000)


def request_ref(nonce: object) -> Optional[str]:
    # Mirror the existing nonce grammar; raw nonce never reaches the sink.
    if not isinstance(nonce, str) or not re.fullmatch(r"[a-zA-Z0-9_.:-]{8,128}", nonce):
        return None
    return hashlib.sha256(("STAB1:" + nonce).encode("utf-8")).hexdigest()[:16]


ROUTES = {
    "hello", "capabilities", "status", "camera", "commands", "filesystem",
    "uploads", "upload_targets", "upload_library", "wifi", "wifi_direct",
    "pipelines", "pipeline_logs", "pipeline_config", "pipeline_control",
    "system_time", "system_fan", "rtk", "other",
}


def route_category(path: str) -> str:
    # Only the returned constant is recorded. No dynamic path/query components.
    for prefix, category in (
        ("/v1/hello", "hello"), ("/v1/capabilities", "capabilities"),
        ("/v1/status", "status"), ("/v1/camera/", "camera"),
        ("/v1/commands/", "commands"), ("/v1/fs/", "filesystem"),
        ("/v1/uploads", "uploads"), ("/v1/upload/targets", "upload_targets"),
        ("/v1/upload/library/", "upload_library"),
        ("/v1/upload/source-summary", "uploads"),
        ("/v1/network/wifi-direct/", "wifi_direct"),
        ("/v1/network/wifi", "wifi"), ("/v1/system/time", "system_time"),
        ("/v1/system/fan", "system_fan"), ("/v1/rtk/", "rtk"),
    ):
        if path == prefix or path.startswith(prefix if prefix.endswith("/") else prefix + "/"):
            return category
    if path == "/v1/pipelines" or path.startswith("/v1/pipelines/"):
        tail = path.split("/")[4:]
        if tail and tail[0] in {"logs", "log-files"}:
            return "pipeline_logs"
        if tail and tail[0] == "config":
            return "pipeline_config"
        if tail and tail[0] in {"start", "stop", "restart"}:
            return "pipeline_control"
        if tail and tail[0] == "mobile-rtk":
            return "rtk"
        return "pipelines"
    return "other"


EVENTS = {
    "process_start", "process_stop", "api_request_received", "api_reply_sent",
    "api_request_failed", "p2p_state", "p2p_observation", "p2p_connect_request",
    "p2p_worker_start", "p2p_worker_end", "p2p_cleanup_requested",
    "p2p_cleanup_completed", "p2p_cleanup_failed", "p2p_command_result",
    "diagnostics_health", "incident_started", "incident_completed", "manual_marker",
}
ENUM_FIELDS = {
    "route": ROUTES,
    "method": {"GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS", "OTHER"},
    "auth": {"unverified", "public", "verified", "rejected"},
    "outcome": {"complete", "exception", "cancelled", "accepted", "rejected", "timeout", "unavailable"},
    "state": {"UNAVAILABLE", "STARTING", "DISCOVERABLE", "CONNECTING", "READY", "ERROR", "STOPPED", "DISABLED"},
    "previousState": {"UNAVAILABLE", "STARTING", "DISCOVERABLE", "CONNECTING", "READY", "ERROR", "STOPPED", "DISABLED"},
    "group": {"present", "absent", "unknown"},
    "address": {"present", "absent", "unknown"},
    "peer": {"present", "absent", "unknown"},
    "ownerMode": {"manual", "networkmanager"},
    "command": {"iw", "ip", "nmcli", "p2p_group_remove", "p2p_group_add", "p2p_connect", "p2p_cancel", "p2p_find", "p2p_stop_find", "p2p_flush", "other"},
    "reason": {"failure", "recovered", "manual", "window_elapsed", "process_stop", "size_limit"},
    "socketPath": {"direct", "other_ip", "loopback", "unknown"},
}
INT_FIELDS = {"status", "durationMillis", "attempt", "pid", "droppedEvents", "storageFailures", "queueDepth", "incidentCount", "frequencyMhz", "groupFrequencyMhz"}
BOOL_FIELDS = {"responseSigned", "groupPresent", "dhcpActive", "workerAlive", "osAccepted", "confirmed", "complete"}
HEX_FIELDS = {"requestRef": 16, "buildId": 64, "incidentId": 32}


def safe_fields(fields: dict) -> dict:
    result = {}
    for key, value in fields.items():
        if key == "codeVersion" and isinstance(value, str) and re.fullmatch(r"[0-9]{1,5}(?:\.[0-9]{1,5}){1,3}", value):
            result[key] = value
        elif key in ENUM_FIELDS and isinstance(value, str) and value in ENUM_FIELDS[key]:
            result[key] = value
        elif key in INT_FIELDS and type(value) is int and 0 <= value <= 2**63 - 1:
            result[key] = value
        elif key in BOOL_FIELDS and type(value) is bool:
            result[key] = value
        elif key in HEX_FIELDS and isinstance(value, str) and re.fullmatch("[0-9a-f]{%d}" % HEX_FIELDS[key], value):
            result[key] = value
    return result


class NullDiagnostics:
    def record(self, event: str, **fields: Any) -> None:
        pass

    def close(self, timeout: float = 2.0) -> bool:
        return True


class DiagnosticsStore:
    """One writer per API/P2P service; unique run names survive service restart.

    Four regular files and three incident files per source, each at most 1 MiB.
    Retention is enforced on writes/startup/export, not by a new OS polling task.
    The normal systemd configuration runs one process for each source.
    """
    def __init__(self, state_dir: Path, source: str, *, file_bytes: int = MAX_FILE_BYTES,
                 queue_capacity: int = 256, prelude_events: int = 60,
                 post_seconds: float = 60.0, wall=time.time, elapsed=elapsed_millis) -> None:
        if source not in {"api", "p2p"} or file_bytes < 1024 or queue_capacity < 1:
            raise ValueError("Invalid diagnostic store limits")
        self.directory = Path(state_dir) / "diagnostics" / source
        self.source = source
        self.run_id = uuid.uuid4().hex
        self.file_bytes = min(file_bytes, MAX_FILE_BYTES)
        self._wall, self._elapsed = wall, elapsed
        self._post_millis = int(max(0, min(post_seconds, 60)) * 1000)
        self._prelude = collections.deque(maxlen=min(max(prelude_events, 0), 60))
        self._queue = queue.Queue(maxsize=queue_capacity)
        self._lock = threading.Lock()
        self._thread = None
        self._closing = False
        self._seq = 0
        self._file_index = 0
        self._regular_path = None
        self._incident_path = None
        self._incident_end = 0
        self._incident_id = None
        self._episodes = set()
        self._dropped = 0
        self._failures = 0
        self._last_warning = -60.0
        self._last_health = None
        self._last_prune = 0.0
        self._last_incident_at = -120000
        self._last_heartbeat = time.monotonic()

    def start(self) -> None:
        with self._lock:
            if self._thread is not None or self._closing:
                return
            try:
                self._thread = threading.Thread(target=self._run, name="connection-evidence-" + self.source, daemon=True)
                self._thread.start()
            except Exception:
                self._thread = None
                self._storage_failure()
                return
        # Hash only the source files present at process start. This is a disk
        # build fingerprint, not a claim that arbitrary hot edits were loaded.
        digest = hashlib.sha256()
        try:
            for name in ("diagnostics.py", "api.py", "wifi_direct.py", "auth.py"):
                digest.update((Path(__file__).parent / name).read_bytes())
            build_id = digest.hexdigest()
        except OSError:
            build_id = None
        self.record("process_start", pid=os.getpid(), buildId=build_id, codeVersion=__version__)

    def record(self, event: str, *, incident: bool = False, recovered: bool = False,
               episode: str = "other", **fields: Any) -> None:
        try:
            if event not in EVENTS:
                return
            clean = safe_fields(fields)
            with self._lock:
                if self._closing or self._thread is None:
                    return
                self._seq += 1
                record = {"schema": SCHEMA, "source": self.source, "runId": self.run_id,
                          "seq": self._seq, "utcEpochMillis": int(self._wall() * 1000),
                          "elapsedMillis": self._elapsed(), "clock": CLOCK_NAME, "event": event}
                record.update(clean)
                key = episode if episode in ROUTES or episode == "p2p" else "other"
                try:
                    self._queue.put_nowait((record, incident, recovered, key))
                except queue.Full:
                    self._dropped += 1
        except Exception:
            # A diagnostic input or clock failure must not escape into control.
            self._failures += 1

    def note_failure(self) -> None:
        self._failures += 1

    def health(self) -> dict:
        return {"droppedEvents": self._dropped, "storageFailures": self._failures,
                "queueDepth": self._queue.qsize()}

    def flush(self, timeout: float = 2.0) -> bool:
        if self._thread is None:
            return True
        done = threading.Event()
        try:
            self._queue.put(done, timeout=max(0, timeout))
        except queue.Full:
            return False
        return done.wait(timeout)

    def close(self, timeout: float = 2.0) -> bool:
        self.record("process_stop")
        self._closing = True
        if self._thread is None:
            return True
        self._thread.join(timeout)
        return not self._thread.is_alive()

    def _new_record(self, event: str, **fields: Any) -> dict:
        # Sequence numbers mark local acquisition, including writer metadata;
        # compare elapsedMillis as well when records were queued concurrently.
        with self._lock:
            self._seq += 1
            sequence = self._seq
        record = {"schema": SCHEMA, "source": self.source, "runId": self.run_id,
                  "seq": sequence, "utcEpochMillis": int(self._wall() * 1000),
                  "elapsedMillis": self._elapsed(), "clock": CLOCK_NAME, "event": event}
        record.update(safe_fields(fields))
        return record

    def _line(self, record: dict) -> bytes:
        return (json.dumps(record, separators=(",", ":"), sort_keys=True) + "\n").encode("utf-8")

    def _prepare_directory(self) -> None:
        if self.directory.is_symlink() or self.directory.parent.is_symlink():
            raise OSError("Diagnostic directory must not be a symlink")
        self.directory.mkdir(parents=True, exist_ok=True, mode=0o700)
        os.chmod(self.directory.parent, 0o700)
        os.chmod(self.directory, 0o700)

    def _append(self, path: Path, data: bytes) -> None:
        # No symlink following, no open descriptors shared with request threads.
        flags = os.O_WRONLY | os.O_CREAT | os.O_APPEND | getattr(os, "O_NOFOLLOW", 0) | getattr(os, "O_NONBLOCK", 0)
        descriptor = os.open(str(path), flags, 0o600)
        try:
            if not stat.S_ISREG(os.fstat(descriptor).st_mode):
                raise OSError("Diagnostic output must be a regular file")
            os.fchmod(descriptor, 0o600)
            with os.fdopen(descriptor, "ab", closefd=False) as output:
                output.write(data)
        finally:
            os.close(descriptor)

    def _prune(self) -> None:
        now = self._wall()
        for prefix, keep in (("events-", 4), ("incident-", 3)):
            files = sorted((p for p in self.directory.glob(prefix + "*.jsonl") if p.is_file() and not p.is_symlink()),
                           key=lambda p: (p.stat().st_mtime_ns, p.name))
            protected = {self._regular_path, self._incident_path}
            remaining = []
            for path in files:
                if path not in protected and now - path.stat().st_mtime > RETENTION_SECONDS:
                    path.unlink()
                else:
                    remaining.append(path)
            # Wall-clock changes can make the active file the oldest by mtime.
            # Always remove the excess number of *unprotected* files.
            excess = max(0, len(remaining) - keep)
            for path in [p for p in remaining if p not in protected][:excess]:
                path.unlink()
        self._last_prune = now

    def _write_regular(self, line: bytes) -> None:
        if self._regular_path is None or (self._regular_path.exists() and self._regular_path.stat().st_size + len(line) > self.file_bytes):
            self._file_index += 1
            self._regular_path = self.directory / ("events-%s-%06d.jsonl" % (self.run_id, self._file_index))
        self._append(self._regular_path, line)
        self._prune()

    def _finish_incident(self, reason: str) -> None:
        if self._incident_path is None:
            return
        line = self._line(self._new_record("incident_completed", incidentId=self._incident_id, reason=reason,
                                           complete=reason == "window_elapsed"))
        if self._incident_path.exists() and self._incident_path.stat().st_size + len(line) <= self.file_bytes:
            self._append(self._incident_path, line)
        self._incident_path = None
        self._incident_id = None

    def _consume(self, item: tuple) -> None:
        record, incident, recovered, episode = item
        if recovered:
            self._episodes.discard(episode)
        trigger = incident and episode not in self._episodes
        if incident:
            self._episodes.add(episode)
        self._prepare_directory()
        if self._incident_path and record["elapsedMillis"] >= self._incident_end:
            self._finish_incident("window_elapsed")
        if trigger and self._incident_path is None and record["elapsedMillis"] - self._last_incident_at >= 120000:
            self._last_incident_at = record["elapsedMillis"]
            self._incident_id = uuid.uuid4().hex
            self._incident_path = self.directory / ("incident-%s-%s.jsonl" % (self.run_id, self._incident_id))
            self._incident_end = record["elapsedMillis"] + self._post_millis
            prelude = b"".join(self._prelude)
            header = self._line(self._new_record("incident_started", incidentId=self._incident_id, reason="failure"))
            # Reserve room for at least onset and completion even with test caps.
            while len(prelude) + len(header) > self.file_bytes - 1024 and self._prelude:
                self._prelude.popleft()
                prelude = b"".join(self._prelude)
            self._append(self._incident_path, prelude + header)
        line = self._line(record)
        self._write_regular(line)
        if self._incident_path is not None:
            if self._incident_path.stat().st_size + len(line) <= self.file_bytes - 400:
                self._append(self._incident_path, line)
            else:
                self._finish_incident("size_limit")
        self._prelude.append(line)
        health = (self._dropped, self._failures)
        if health != self._last_health:
            self._write_regular(self._line(self._new_record("diagnostics_health", **self.health())))
            self._last_health = health

    def _run(self) -> None:
        while not self._closing or not self._queue.empty():
            try:
                item = self._queue.get(timeout=0.2)
            except queue.Empty:
                try:
                    if self._incident_path and self._elapsed() >= self._incident_end:
                        self._finish_incident("window_elapsed")
                    # Writer health only: no new radio, route or API probe.
                    if time.monotonic() - self._last_heartbeat >= 60:
                        self._last_heartbeat = time.monotonic()
                        self._prepare_directory()
                        self._write_regular(self._line(self._new_record("diagnostics_health", **self.health())))
                except Exception:
                    self._storage_failure()
                continue
            try:
                if isinstance(item, threading.Event):
                    item.set()
                else:
                    self._consume(item)
            except Exception:
                self._storage_failure()
            finally:
                self._queue.task_done()
        try:
            self._finish_incident("process_stop")
        except Exception:
            self._storage_failure()

    def _storage_failure(self) -> None:
        self._failures += 1
        # A failed incident open/write must not poison later regular writes.
        # A partial incident without completion remains explicitly incomplete.
        self._incident_path = None
        self._incident_id = None
        now = time.monotonic()
        if now - self._last_warning >= 60:
            self._last_warning = now
            LOGGER.warning("Connection diagnostics storage unavailable; evidence may be missing")


def safe_direct_network(value):
    import ipaddress
    try:
        return ipaddress.ip_interface(value).network
    except (ValueError, TypeError):
        return None


class RequestEvidenceMiddleware:
    """Passive ASGI observation. Does not consume receive or alter send messages."""
    def __init__(self, app, store: DiagnosticsStore, direct_network=None) -> None:
        self.app, self.store, self.direct_network = app, store, direct_network

    def _note_failure(self):
        try:
            self.store.note_failure()
        except Exception:
            pass

    def _elapsed(self):
        try:
            return elapsed_millis()
        except Exception:
            self._note_failure()
            return None

    def _record(self, event, fields, start=None, **values):
        try:
            if start is not None:
                now = self._elapsed()
                if now is not None:
                    values["durationMillis"] = max(0, now - start)
            self.store.record(event, **fields, **values)
        except Exception:
            self._note_failure()

    async def __call__(self, scope, receive, send) -> None:
        if scope["type"] != "http":
            await self.app(scope, receive, send)
            return
        try:
            route = route_category(scope.get("path", ""))
            method = scope.get("method", "OTHER")
            method = method if method in ENUM_FIELDS["method"] else "OTHER"
            nonce = next((value for key, value in scope.get("headers", ()) if key.lower() == b"x-request-nonce"), b"")
            try:
                ref = request_ref(nonce.decode("ascii"))
            except (UnicodeDecodeError, AttributeError):
                ref = None
            fields = {"route": route, "method": method, "requestRef": ref, "socketPath": self._socket_path(scope)}
        except Exception:
            # Metadata observation cannot stop dispatch of the original request.
            self._note_failure()
            await self.app(scope, receive, send)
            return
        start = self._elapsed()
        self._record("api_request_received", fields)
        response_status = 0
        signed = False
        completed = False

        async def observe_send(message):
            nonlocal response_status, signed, completed
            # Deliberately outside the evidence guard: original send failures
            # propagate unchanged and are never mistaken for successful delivery.
            await send(message)
            try:
                if message["type"] == "http.response.start":
                    response_status = message["status"]
                    signed = any(key.lower() == b"x-response-signature" for key, _ in message.get("headers", ()))
                elif message["type"] == "http.response.body" and not message.get("more_body", False):
                    completed = True
                    auth = scope.get("state", {}).get("diagnostics_auth", "unverified")
                    failed = response_status >= 400
                    self._record("api_reply_sent", fields, start=start, status=response_status, auth=auth,
                                 responseSigned=signed, outcome="complete", incident=failed,
                                 recovered=not failed, episode=route)
            except Exception:
                self._note_failure()
        try:
            await self.app(scope, receive, observe_send)
        finally:
            if not completed:
                try:
                    self._record("api_request_failed", fields, start=start, status=response_status,
                                 auth=scope.get("state", {}).get("diagnostics_auth", "unverified"),
                                 outcome="exception", responseSigned=signed, incident=True, episode=route)
                except Exception:
                    # Preserve even BaseException/cancellation from app/send.
                    self._note_failure()

    def _socket_path(self, scope) -> str:
        # Socket destination category is direct evidence at this server, with
        # addresses omitted. Unspecified bind address is explicitly unknown.
        import ipaddress
        try:
            address = ipaddress.ip_address(scope["server"][0].rsplit("%", 1)[0])
            if address.is_unspecified:
                return "unknown"
            if address.is_loopback:
                return "loopback"
            if self.direct_network is None:
                return "unknown"
            if address in self.direct_network:
                return "direct"
            return "other_ip"
        except (ValueError, TypeError, KeyError, IndexError, AttributeError):
            return "unknown"


def export_logs(state_dirs, destination: Path) -> dict:
    """Export only schema/allowlisted evidence, never config, journal or payload.

    Sanitize again during export; a truncated last JSONL line is counted, skipped.
    Reading an active file is a bounded snapshot, not an atomic cross-host freeze.
    """
    destination = Path(destination)
    manifest = {"schema": SCHEMA, "exportedUtcEpochMillis": int(time.time() * 1000),
                "files": 0, "records": 0, "skippedLines": 0,
                "clockOffset": "NOT_MEASURED", "snapshot": "bounded_non_atomic",
                "droppedEvents": 0, "storageFailures": 0, "missingSources": [], "skippedFiles": 0}
    latest_health = {}
    flags = os.O_WRONLY | os.O_CREAT | os.O_EXCL | getattr(os, "O_NOFOLLOW", 0)
    fd = os.open(str(destination), flags, 0o600)
    try:
        with os.fdopen(fd, "wb", closefd=False) as output, zipfile.ZipFile(output, "w", zipfile.ZIP_DEFLATED) as archive:
            for root_index, state_dir in enumerate(state_dirs):
                for source in ("api", "p2p"):
                    directory = Path(state_dir) / "diagnostics" / source
                    if directory.is_symlink() or directory.parent.is_symlink():
                        manifest["missingSources"].append("%d/%s" % (root_index, source))
                        continue
                    if not directory.is_dir():
                        manifest["missingSources"].append("%d/%s" % (root_index, source))
                        continue
                    for prefix, limit in (("events-", 4), ("incident-", 3)):
                        candidates = []
                        for path in directory.glob(prefix + "*.jsonl"):
                            try:
                                info = path.lstat()
                                if stat.S_ISREG(info.st_mode) and time.time() - info.st_mtime <= RETENTION_SECONDS:
                                    candidates.append((info.st_mtime_ns, path))
                            except OSError:
                                manifest["skippedFiles"] += 1
                        paths = [path for _, path in sorted(candidates, reverse=True)[:limit]]
                        for file_index, path in enumerate(paths):
                            try:
                                file_fd = os.open(str(path), os.O_RDONLY | getattr(os, "O_NOFOLLOW", 0) | getattr(os, "O_NONBLOCK", 0))
                                try:
                                    if not stat.S_ISREG(os.fstat(file_fd).st_mode):
                                        manifest["skippedFiles"] += 1
                                        continue
                                    with os.fdopen(file_fd, "rb", closefd=False) as stream:
                                        data = stream.read(MAX_FILE_BYTES)
                                finally:
                                    os.close(file_fd)
                            except OSError:
                                # Rotation or source access can change during a
                                # live export; report the gap and keep other logs.
                                manifest["skippedFiles"] += 1
                                continue
                            clean_lines = []
                            for line in data.splitlines():
                                try:
                                    item = json.loads(line)
                                    if item.get("schema") != SCHEMA or item.get("source") != source or item.get("event") not in EVENTS:
                                        raise ValueError("Unknown evidence schema")
                                    if not re.fullmatch(r"[0-9a-f]{32}", item.get("runId", "")):
                                        raise ValueError("Invalid run reference")
                                    clean = {key: item[key] for key in ("schema", "source", "runId", "event")}
                                    for key in ("seq", "utcEpochMillis", "elapsedMillis"):
                                        if type(item.get(key)) is not int or not 0 <= item[key] <= 2**63 - 1:
                                            raise ValueError("Invalid evidence time")
                                        clean[key] = item[key]
                                    clean["clock"] = item.get("clock") if item.get("clock") in {"boottime", "monotonic"} else "unknown"
                                    clean.update(safe_fields(item))
                                    if clean["event"] == "diagnostics_health":
                                        key = (root_index, source, clean["runId"])
                                        old = latest_health.setdefault(key, [0, 0])
                                        old[0] = max(old[0], clean.get("droppedEvents", 0))
                                        old[1] = max(old[1], clean.get("storageFailures", 0))
                                    clean_lines.append(json.dumps(clean, separators=(",", ":")) + "\n")
                                except (ValueError, TypeError, KeyError, AttributeError):
                                    manifest["skippedLines"] += 1
                            archive.writestr("%d/%s/%s%d.jsonl" % (root_index, source, prefix, file_index), "".join(clean_lines))
                            manifest["files"] += 1
                            manifest["records"] += len(clean_lines)
            manifest["droppedEvents"] = sum(value[0] for value in latest_health.values())
            manifest["storageFailures"] = sum(value[1] for value in latest_health.values())
            archive.writestr("manifest.json", json.dumps(manifest, indent=2))
    finally:
        os.close(fd)
    return manifest


def main(argv=None) -> int:
    from .config import RuntimePaths
    parser = argparse.ArgumentParser(description="Export private connection evidence (no service changes)")
    parser.add_argument("--state-dir", type=Path, action="append", help="Actual runtime state root; repeat for separate roots")
    parser.add_argument("--output", type=Path, required=True, help="New ZIP destination; existing files are never overwritten")
    args = parser.parse_args(argv)
    try:
        manifest = export_logs(args.state_dir or [RuntimePaths().state_dir], args.output)
    except OSError:
        parser.exit(1, "Connection log export failed; verify source access and new destination permissions.\n")
    print(json.dumps(manifest, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
