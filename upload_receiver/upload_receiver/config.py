from __future__ import annotations

import os
from dataclasses import dataclass
from pathlib import Path


DEFAULT_DATA_ROOT = Path("/data/server_storage/jetson-upload-receiver")
DEFAULT_EXPECTED_MOUNT = Path("/data/server_storage")


def _positive_int(name: str, default: int) -> int:
    raw = os.environ.get(name, str(default))
    try:
        value = int(raw)
    except ValueError as error:
        raise RuntimeError(f"{name} must be an integer") from error
    if value < 1:
        raise RuntimeError(f"{name} must be at least 1")
    return value


def _boolean(name: str, default: bool) -> bool:
    raw = os.environ.get(name)
    if raw is None:
        return default
    normalized = raw.strip().lower()
    if normalized in {"1", "true", "yes", "on"}:
        return True
    if normalized in {"0", "false", "no", "off"}:
        return False
    raise RuntimeError(f"{name} must be true or false")


@dataclass(frozen=True)
class Settings:
    data_root: Path
    expected_mount: Path | None
    require_mount: bool
    max_manifest_bytes: int = 32 * 1024 * 1024
    max_chunk_bytes: int = 4 * 1024 * 1024
    max_batch_bytes: int = 32 * 1024 * 1024
    max_batch_files: int = 256
    max_files_per_session: int = 100_000
    max_session_bytes: int = 5 * 1024**4
    max_concurrent_puts_per_device: int = 2
    max_active_sessions_per_device: int = 8
    max_stored_sessions_per_device: int = 10_000
    max_stored_files_per_device: int = 1_000_000
    max_manifest_requests_per_minute: int = 30
    readiness_cache_seconds: int = 5
    max_preview_bytes: int = 12 * 1024 * 1024

    @classmethod
    def from_env(cls) -> "Settings":
        expected_text = os.environ.get(
            "UPLOAD_RECEIVER_EXPECTED_MOUNT", str(DEFAULT_EXPECTED_MOUNT)
        ).strip()
        return cls(
            data_root=Path(
                os.environ.get("UPLOAD_RECEIVER_DATA_ROOT", str(DEFAULT_DATA_ROOT))
            ).expanduser(),
            expected_mount=Path(expected_text).expanduser() if expected_text else None,
            require_mount=_boolean("UPLOAD_RECEIVER_REQUIRE_MOUNT", True),
            max_manifest_bytes=_positive_int(
                "UPLOAD_RECEIVER_MAX_MANIFEST_BYTES", 32 * 1024 * 1024
            ),
            max_chunk_bytes=_positive_int(
                "UPLOAD_RECEIVER_MAX_CHUNK_BYTES", 4 * 1024 * 1024
            ),
            max_batch_bytes=_positive_int(
                "UPLOAD_RECEIVER_MAX_BATCH_BYTES", 32 * 1024 * 1024
            ),
            max_batch_files=_positive_int(
                "UPLOAD_RECEIVER_MAX_BATCH_FILES", 256
            ),
            max_files_per_session=_positive_int(
                "UPLOAD_RECEIVER_MAX_FILES", 100_000
            ),
            max_session_bytes=_positive_int(
                "UPLOAD_RECEIVER_MAX_SESSION_BYTES", 5 * 1024**4
            ),
            max_concurrent_puts_per_device=_positive_int(
                "UPLOAD_RECEIVER_MAX_DEVICE_PUTS", 2
            ),
            max_active_sessions_per_device=_positive_int(
                "UPLOAD_RECEIVER_MAX_ACTIVE_SESSIONS", 8
            ),
            max_stored_sessions_per_device=_positive_int(
                "UPLOAD_RECEIVER_MAX_STORED_SESSIONS", 10_000
            ),
            max_stored_files_per_device=_positive_int(
                "UPLOAD_RECEIVER_MAX_STORED_FILES", 1_000_000
            ),
            max_manifest_requests_per_minute=_positive_int(
                "UPLOAD_RECEIVER_MAX_MANIFESTS_PER_MINUTE", 30
            ),
            readiness_cache_seconds=_positive_int(
                "UPLOAD_RECEIVER_READINESS_CACHE_SECONDS", 5
            ),
            max_preview_bytes=_positive_int(
                "UPLOAD_RECEIVER_MAX_PREVIEW_BYTES", 12 * 1024 * 1024
            ),
        )

    @property
    def database_path(self) -> Path:
        return self.data_root / "db" / "receiver.sqlite3"

    @property
    def pepper_path(self) -> Path:
        return self.data_root / "secrets" / "token-pepper"

    @property
    def staging_root(self) -> Path:
        return self.data_root / "storage" / "staging"

    @property
    def objects_root(self) -> Path:
        return self.data_root / "storage" / "objects"

    @property
    def locks_root(self) -> Path:
        return self.data_root / "storage" / "locks"

    @property
    def runtime_root(self) -> Path:
        return self.data_root / "runtime"

    def prepare(self, *, create_pepper: bool = False) -> None:
        data_root = self.data_root.resolve()
        if self.expected_mount is not None:
            expected = self.expected_mount.resolve()
            if data_root == expected:
                raise RuntimeError(
                    f"Upload data root must be a child of the expected mount: {expected}"
                )
            try:
                data_root.relative_to(expected)
            except ValueError as error:
                raise RuntimeError(
                    f"Upload data root must be below the expected mount: {expected}"
                ) from error
            if self.require_mount and not expected.is_mount():
                raise RuntimeError(f"Required upload disk is not mounted: {expected}")

        for path, mode in (
            (self.data_root, 0o700),
            (self.data_root / "storage", 0o700),
            (self.database_path.parent, 0o700),
            (self.pepper_path.parent, 0o700),
            (self.staging_root, 0o700),
            (self.objects_root, 0o700),
            (self.locks_root, 0o700),
            (self.runtime_root, 0o700),
        ):
            path.mkdir(parents=True, exist_ok=True, mode=mode)
            path.chmod(mode)

        if (
            self.require_mount
            and self.expected_mount is not None
            and self.data_root.stat().st_dev != self.expected_mount.stat().st_dev
        ):
            raise RuntimeError(
                "Upload data root and expected mount are not on the same filesystem"
            )

        if create_pepper and not self.pepper_path.exists():
            descriptor = os.open(
                self.pepper_path,
                os.O_WRONLY | os.O_CREAT | os.O_EXCL,
                0o600,
            )
            try:
                os.write(descriptor, os.urandom(32))
                os.fsync(descriptor)
            finally:
                os.close(descriptor)
        if not self.pepper_path.is_file():
            raise RuntimeError(f"Token pepper is missing: {self.pepper_path}")
        self.pepper_path.chmod(0o600)
        if len(self.pepper_path.read_bytes()) < 32:
            raise RuntimeError("Token pepper must contain at least 32 bytes")
