from __future__ import annotations

import os
import sqlite3
from contextlib import contextmanager
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
"""


class Database:
    def __init__(self, path: Path) -> None:
        self.path = path

    def connect(self) -> sqlite3.Connection:
        connection = sqlite3.connect(self.path, timeout=10.0)
        connection.row_factory = sqlite3.Row
        connection.execute("PRAGMA foreign_keys=ON")
        connection.execute("PRAGMA busy_timeout=10000")
        connection.execute("PRAGMA synchronous=FULL")
        return connection

    def initialize(self) -> None:
        with self.connect() as connection:
            connection.execute("PRAGMA journal_mode=WAL")
            connection.executescript(SCHEMA)
        os.chmod(self.path, 0o600)

    @contextmanager
    def immediate(self) -> Iterator[sqlite3.Connection]:
        connection = self.connect()
        try:
            connection.execute("BEGIN IMMEDIATE")
            yield connection
            connection.commit()
        except Exception:
            connection.rollback()
            raise
        finally:
            connection.close()
