from __future__ import annotations

import os
import sqlite3
from contextlib import closing, contextmanager
from pathlib import Path
from typing import Iterator


SCHEMA = """
CREATE TABLE IF NOT EXISTS devices (
    device_id TEXT PRIMARY KEY,
    token_digest TEXT UNIQUE NOT NULL,
    enabled INTEGER NOT NULL DEFAULT 1 CHECK (enabled IN (0, 1)),
    quota_bytes INTEGER NOT NULL CHECK (quota_bytes >= 0),
    token_expires_at TEXT,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS upload_sessions (
    session_id TEXT PRIMARY KEY,
    device_id TEXT NOT NULL REFERENCES devices(device_id),
    client_job_id TEXT NOT NULL,
    source_name TEXT NOT NULL,
    state TEXT NOT NULL CHECK (
        state IN ('OPEN', 'FINALIZING', 'COMPLETED', 'CANCELLED', 'FAILED')
    ),
    total_bytes INTEGER NOT NULL CHECK (total_bytes >= 0),
    file_count INTEGER NOT NULL CHECK (file_count >= 0),
    manifest_hash TEXT NOT NULL,
    manifest_json TEXT NOT NULL,
    failure_code TEXT,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    completed_at TEXT,
    UNIQUE (device_id, client_job_id)
);

CREATE TABLE IF NOT EXISTS upload_files (
    session_id TEXT NOT NULL REFERENCES upload_sessions(session_id) ON DELETE CASCADE,
    relative_path TEXT NOT NULL,
    file_id TEXT NOT NULL,
    size_bytes INTEGER NOT NULL CHECK (size_bytes >= 0),
    sha256 TEXT NOT NULL,
    next_offset INTEGER NOT NULL DEFAULT 0,
    state TEXT NOT NULL CHECK (
        state IN ('PENDING', 'UPLOADING', 'RECEIVED', 'VERIFIED', 'FAILED')
    ),
    staging_key TEXT NOT NULL,
    final_key TEXT NOT NULL,
    PRIMARY KEY (session_id, relative_path),
    UNIQUE (file_id),
    CHECK (next_offset >= 0 AND next_offset <= size_bytes)
);

CREATE INDEX IF NOT EXISTS upload_sessions_device_state_idx
ON upload_sessions(device_id, state);

CREATE TABLE IF NOT EXISTS projects (
    project_id TEXT PRIMARY KEY,
    display_name TEXT NOT NULL,
    enabled INTEGER NOT NULL DEFAULT 1 CHECK (enabled IN (0, 1)),
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

-- A device belongs to one server-side access project. This access grouping is
-- deliberately separate from the survey/project metadata produced by a pipeline.
CREATE TABLE IF NOT EXISTS project_devices (
    project_id TEXT NOT NULL REFERENCES projects(project_id) ON DELETE CASCADE,
    device_id TEXT NOT NULL UNIQUE REFERENCES devices(device_id) ON DELETE CASCADE,
    created_at TEXT NOT NULL,
    PRIMARY KEY (project_id, device_id)
);

CREATE TABLE IF NOT EXISTS employees (
    employee_id TEXT PRIMARY KEY,
    display_name TEXT NOT NULL,
    role TEXT NOT NULL CHECK (role IN ('VIEWER', 'OPERATOR', 'ADMIN')),
    token_digest TEXT UNIQUE NOT NULL,
    enabled INTEGER NOT NULL DEFAULT 1 CHECK (enabled IN (0, 1)),
    token_expires_at TEXT,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS employee_project_grants (
    employee_id TEXT NOT NULL REFERENCES employees(employee_id) ON DELETE CASCADE,
    project_id TEXT NOT NULL REFERENCES projects(project_id) ON DELETE CASCADE,
    created_at TEXT NOT NULL,
    PRIMARY KEY (employee_id, project_id)
);

CREATE TABLE IF NOT EXISTS library_trash (
    session_id TEXT PRIMARY KEY REFERENCES upload_sessions(session_id) ON DELETE CASCADE,
    project_id TEXT NOT NULL REFERENCES projects(project_id),
    employee_id TEXT NOT NULL REFERENCES employees(employee_id),
    state TEXT NOT NULL CHECK (
        state IN ('MOVING_TO_TRASH', 'TRASHED', 'RESTORING')
    ),
    trashed_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

-- Permanent deletion is tracked separately so existing library_trash tables do
-- not need a destructive CHECK-constraint migration. The upload session and file
-- rows remain as an ownership/idempotency tombstone after the payload is removed.
CREATE TABLE IF NOT EXISTS library_purges (
    session_id TEXT PRIMARY KEY REFERENCES upload_sessions(session_id) ON DELETE CASCADE,
    project_id TEXT NOT NULL REFERENCES projects(project_id),
    employee_id TEXT NOT NULL REFERENCES employees(employee_id),
    state TEXT NOT NULL CHECK (state IN ('PURGING', 'PURGED')),
    requested_at TEXT NOT NULL,
    purged_at TEXT,
    updated_at TEXT NOT NULL,
    error TEXT
);

CREATE INDEX IF NOT EXISTS library_purges_project_state_idx
ON library_purges(project_id, state, updated_at DESC);

CREATE INDEX IF NOT EXISTS project_devices_project_idx
ON project_devices(project_id, device_id);

CREATE INDEX IF NOT EXISTS employee_project_grants_project_idx
ON employee_project_grants(project_id, employee_id);

CREATE TABLE IF NOT EXISTS audit_events (
    event_id TEXT PRIMARY KEY,
    actor_kind TEXT NOT NULL,
    actor_id TEXT NOT NULL,
    action TEXT NOT NULL,
    outcome TEXT NOT NULL,
    project_id TEXT,
    session_id TEXT,
    target_id TEXT,
    details_json TEXT NOT NULL,
    created_at TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS audit_events_project_created_idx
ON audit_events(project_id, created_at DESC, event_id DESC);

CREATE TABLE IF NOT EXISTS device_events (
    device_id TEXT NOT NULL REFERENCES devices(device_id),
    event_id TEXT NOT NULL,
    type TEXT NOT NULL,
    occurred_at TEXT NOT NULL,
    received_at TEXT NOT NULL,
    body_json TEXT NOT NULL,
    PRIMARY KEY (device_id, event_id)
);
CREATE INDEX IF NOT EXISTS device_events_run_idx
ON device_events(device_id, type, json_extract(body_json, '$.runId'), received_at DESC);
CREATE INDEX IF NOT EXISTS device_events_type_idx
ON device_events(device_id, type, received_at DESC);

CREATE TABLE IF NOT EXISTS device_presence (
    device_id TEXT PRIMARY KEY REFERENCES devices(device_id),
    display_name TEXT,
    last_seen_at TEXT NOT NULL,
    on_duty INTEGER NOT NULL CHECK (on_duty IN (0, 1)),
    active_run_id TEXT,
    heartbeat_state TEXT NOT NULL CHECK (heartbeat_state IN ('OK', 'MISSING')),
    missing_since TEXT,
    updated_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS outbound_messages (
    message_id TEXT PRIMARY KEY,
    target TEXT NOT NULL,
    kind TEXT NOT NULL,
    device_id TEXT NOT NULL REFERENCES devices(device_id),
    incident_key TEXT NOT NULL,
    status_text TEXT NOT NULL,
    idempotency_key TEXT,
    payload_json TEXT NOT NULL,
    state TEXT NOT NULL CHECK (state IN ('PENDING', 'SENDING', 'SENT', 'DEDUP', 'DEAD')),
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TEXT NOT NULL,
    last_http_status INTEGER,
    last_error_code TEXT,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    completed_at TEXT
);
CREATE INDEX IF NOT EXISTS outbound_messages_due_idx
ON outbound_messages(state, next_attempt_at);
CREATE UNIQUE INDEX IF NOT EXISTS outbound_messages_idempotency_idx
ON outbound_messages(idempotency_key) WHERE idempotency_key IS NOT NULL;
CREATE INDEX IF NOT EXISTS outbound_messages_incident_idx
ON outbound_messages(target, incident_key, created_at DESC);

CREATE TABLE IF NOT EXISTS integration_state (
    target TEXT PRIMARY KEY,
    state TEXT NOT NULL,
    detail TEXT,
    config_fingerprint TEXT,
    updated_at TEXT NOT NULL
);
"""


class Database:
    def __init__(self, path: Path) -> None:
        self.path = path

    def connect(self, *, timeout: float = 10.0) -> sqlite3.Connection:
        connection = sqlite3.connect(self.path, timeout=timeout)
        connection.row_factory = sqlite3.Row
        connection.execute("PRAGMA foreign_keys=ON")
        connection.execute(f"PRAGMA busy_timeout={int(timeout * 1000)}")
        connection.execute("PRAGMA synchronous=FULL")
        return connection

    def initialize(self) -> None:
        with closing(self.connect()) as connection:
            connection.execute("PRAGMA journal_mode=WAL")
            connection.executescript(SCHEMA)
        os.chmod(self.path, 0o600)

    @contextmanager
    def immediate(self, *, timeout: float = 10.0) -> Iterator[sqlite3.Connection]:
        connection = self.connect(timeout=timeout)
        try:
            connection.execute("BEGIN IMMEDIATE")
            yield connection
            connection.commit()
        except Exception:
            connection.rollback()
            raise
        finally:
            connection.close()
