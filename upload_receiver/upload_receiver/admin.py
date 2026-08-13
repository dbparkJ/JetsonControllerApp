from __future__ import annotations

import argparse
import os
import uuid
from pathlib import Path

from .config import Settings
from .service import ReceiverService


def _stage_secret(path: Path, value: str, *, force: bool) -> tuple[Path, Path]:
    path = Path(os.path.abspath(path.expanduser()))
    path.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    if not force:
        try:
            descriptor = os.open(path, os.O_RDONLY | os.O_NOFOLLOW)
        except FileNotFoundError:
            pass
        else:
            os.close(descriptor)
            raise FileExistsError(path)
    temporary = path.with_name(f".{path.name}.{uuid.uuid4().hex}.tmp")
    try:
        descriptor = os.open(
            temporary,
            os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW,
            0o600,
        )
        try:
            os.fchmod(descriptor, 0o600)
            payload = (value + "\n").encode("utf-8")
            written = 0
            while written < len(payload):
                count = os.write(descriptor, payload[written:])
                if count < 1:
                    raise OSError("Short token write")
                written += count
            os.fsync(descriptor)
        finally:
            os.close(descriptor)
        return path, temporary
    except BaseException:
        temporary.unlink(missing_ok=True)
        raise


def _publish_secret(path: Path, temporary: Path, *, force: bool) -> None:
    if force:
        os.replace(temporary, path)
    else:
        try:
            os.link(temporary, path, follow_symlinks=False)
        except Exception:
            temporary.unlink(missing_ok=True)
            raise
        temporary.unlink()
    descriptor = os.open(path.parent, os.O_RDONLY | os.O_DIRECTORY)
    try:
        os.fsync(descriptor)
    finally:
        os.close(descriptor)


def _write_secret(path: Path, value: str, *, force: bool) -> None:
    destination, temporary = _stage_secret(path, value, force=force)
    _publish_secret(destination, temporary, force=force)


def main() -> None:
    parser = argparse.ArgumentParser(description="Jetson upload receiver administration")
    subparsers = parser.add_subparsers(dest="command", required=True)

    subparsers.add_parser("init", help="initialize directories, pepper, and database")

    issue = subparsers.add_parser("issue-token", help="issue or rotate a device token")
    issue.add_argument("--device-id", required=True)
    issue.add_argument("--output", type=Path, required=True)
    issue.add_argument("--quota-bytes", type=int, default=5 * 1024**4)
    issue.add_argument("--expires-at")
    issue.add_argument("--force", action="store_true")

    disable = subparsers.add_parser("disable-device", help="reject a device token")
    disable.add_argument("--device-id", required=True)

    cleanup = subparsers.add_parser("cleanup", help="expire old staging sessions")
    cleanup.add_argument("--older-than-hours", type=int, default=72)

    args = parser.parse_args()
    settings = Settings.from_env()
    receiver = ReceiverService(settings, create_pepper=args.command == "init")
    if args.command == "init":
        print(f"Initialized receiver data root: {settings.data_root}")
        return
    if args.command == "disable-device":
        receiver.disable_device(args.device_id)
        print(f"Disabled device: {args.device_id}")
        return
    if args.command == "cleanup":
        removed = receiver.cleanup_staging(older_than_hours=args.older_than_hours)
        print(f"Cleaned staging sessions: {removed}")
        return
    token = receiver.generate_token()
    receiver.validate_token_configuration(
        args.device_id,
        quota_bytes=args.quota_bytes,
        expires_at=args.expires_at,
    )
    destination, temporary = _stage_secret(args.output, token, force=args.force)
    try:
        receiver.activate_token(
            args.device_id,
            token,
            quota_bytes=args.quota_bytes,
            expires_at=args.expires_at,
        )
        try:
            _publish_secret(destination, temporary, force=args.force)
        except Exception:
            receiver.disable_device(args.device_id)
            raise RuntimeError(
                "Token file publication failed; the device was disabled so no "
                "unrecoverable credential remains active"
            )
    finally:
        temporary.unlink(missing_ok=True)
    print(f"Device token written with mode 0600: {args.output.expanduser().resolve()}")


if __name__ == "__main__":
    main()
