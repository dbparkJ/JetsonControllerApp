#!/usr/bin/env python3
from __future__ import annotations

import argparse
import datetime as dt
import json
import os
import pwd
import re
import shutil
import stat
import subprocess
import sys
import tempfile
from pathlib import Path
from typing import Iterable, List, Sequence


PIPELINE_ID = re.compile(r"^[a-z0-9][a-z0-9.-]{0,63}$")
REGISTRY_ROOT = Path(os.environ.get("JETSON_PIPELINE_REGISTRY", "/opt/jetson-pipelines"))
SYSTEMD_ROOT = Path(os.environ.get("JETSON_PIPELINE_SYSTEMD_ROOT", "/etc/systemd/system"))


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Snapshot a Git worktree and register its Python entrypoint with systemd."
    )
    parser.add_argument("--id")
    parser.add_argument("--label")
    parser.add_argument("--description", default="")
    parser.add_argument("--repo", type=Path)
    parser.add_argument("--venv", type=Path)
    parser.add_argument("--entry")
    parser.add_argument("--config", default="config.yaml")
    parser.add_argument("--working-dir", type=Path)
    parser.add_argument("--write-path", action="append", default=[], type=Path)
    parser.add_argument("--argument", action="append", default=[])
    parser.add_argument("--user")
    parser.add_argument("--autostart", action="store_true")
    parser.add_argument("--no-autostart", action="store_true")
    parser.add_argument("--start-now", action="store_true")
    parser.add_argument("--restart-running", action="store_true")
    parser.add_argument("--remove", metavar="PIPELINE_ID")
    return parser.parse_args()


def run(command: Sequence[str], *, timeout: int = 60) -> subprocess.CompletedProcess:
    return subprocess.run(
        list(command),
        check=False,
        capture_output=True,
        text=True,
        timeout=timeout,
    )


def checked(command: Sequence[str], *, timeout: int = 60) -> str:
    result = run(command, timeout=timeout)
    if result.returncode != 0:
        detail = (result.stderr or result.stdout or "command failed").strip()
        raise RuntimeError(detail)
    return result.stdout.strip()


def validate_id(value: str) -> str:
    if not PIPELINE_ID.fullmatch(value):
        raise ValueError("Pipeline id must use lowercase letters, digits, dots, or hyphens")
    return value


def relative_path(value: str, kind: str) -> Path:
    path = Path(value)
    if not value or path.is_absolute() or ".." in path.parts or "\x00" in value:
        raise ValueError(f"Invalid {kind}: {value!r}")
    normalized = Path(path.as_posix())
    if normalized in {Path(""), Path(".")}:
        raise ValueError(f"Invalid {kind}: {value!r}")
    return normalized


def git_safe_directories(repo: Path) -> List[Path]:
    directories = []
    for candidate in (repo, *repo.parents):
        if candidate == candidate.parent:
            break
        directories.append(candidate)
    return directories


def git_run(
    repo: Path,
    *arguments: str,
    timeout: int = 60,
    text: bool = True,
) -> subprocess.CompletedProcess:
    # Git 2.25 security backports only honor safe.directory in protected config.
    # Use a throwaway global config so root can inspect this user-owned worktree.
    with tempfile.TemporaryDirectory(prefix="jetson-pipeline-git-") as temporary_home:
        global_config = Path(temporary_home) / ".gitconfig"
        for directory in git_safe_directories(repo):
            checked(
                [
                    "git",
                    "config",
                    "--file",
                    str(global_config),
                    "--add",
                    "safe.directory",
                    str(directory),
                ]
            )
        environment = os.environ.copy()
        environment["HOME"] = temporary_home
        environment.pop("XDG_CONFIG_HOME", None)
        environment.pop("GIT_CONFIG_GLOBAL", None)
        return subprocess.run(
            ["git", "-C", str(repo), *arguments],
            check=False,
            capture_output=True,
            text=text,
            timeout=timeout,
            env=environment,
        )


def git(repo: Path, *arguments: str, timeout: int = 60) -> str:
    result = git_run(repo, *arguments, timeout=timeout)
    if result.returncode != 0:
        detail = (result.stderr or result.stdout or "git command failed").strip()
        raise RuntimeError(detail)
    return result.stdout.strip()


def atomic_json(path: Path, value: object, mode: int, uid: int, gid: int) -> None:
    temporary = path.with_name(f".{path.name}.tmp-{os.getpid()}")
    with temporary.open("x", encoding="utf-8") as output:
        json.dump(value, output, indent=2, sort_keys=True)
        output.write("\n")
        output.flush()
        os.fsync(output.fileno())
    os.chmod(temporary, mode)
    os.chown(temporary, uid, gid)
    os.replace(temporary, path)


def atomic_text(path: Path, value: str, mode: int = 0o644) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(f".{path.name}.tmp-{os.getpid()}")
    with temporary.open("x", encoding="utf-8") as output:
        output.write(value)
        output.flush()
        os.fsync(output.fileno())
    os.chmod(temporary, mode)
    os.chown(temporary, 0, 0)
    os.replace(temporary, path)


def git_files(repo: Path) -> List[Path]:
    result = git_run(
        repo,
        "ls-files",
        "--cached",
        "--others",
        "--exclude-standard",
        "-z",
        "--",
        ".",
        text=False,
    )
    if result.returncode != 0:
        raise RuntimeError(result.stderr.decode("utf-8", "replace").strip())
    paths = []
    for raw in result.stdout.split(b"\0"):
        if not raw:
            continue
        value = os.fsdecode(raw)
        path = relative_path(value, "Git path")
        paths.append(path)
    return paths


def copy_snapshot(repo: Path, destination: Path, files: Iterable[Path]) -> None:
    for relative in files:
        source = repo / relative
        target = destination / relative
        source_stat = source.lstat()
        target.parent.mkdir(parents=True, exist_ok=True)
        if stat.S_ISREG(source_stat.st_mode):
            shutil.copy2(source, target, follow_symlinks=False)
        elif stat.S_ISLNK(source_stat.st_mode):
            link_target = os.readlink(source)
            resolved_target = (source.parent / link_target).resolve()
            try:
                resolved_target.relative_to(repo)
            except ValueError as error:
                raise ValueError(f"Git symlink leaves repository: {relative}") from error
            os.symlink(link_target, target)
        else:
            raise ValueError(f"Unsupported Git entry type: {relative}")


def secure_tree(root: Path, uid: int, gid: int) -> None:
    for directory, directory_names, file_names in os.walk(root, followlinks=False):
        directory_path = Path(directory)
        os.chmod(directory_path, 0o750)
        os.chown(directory_path, uid, gid)
        for name in directory_names:
            path = directory_path / name
            if path.is_symlink():
                os.lchown(path, uid, gid)
        for name in file_names:
            path = directory_path / name
            if path.is_symlink():
                os.lchown(path, uid, gid)
                continue
            source_mode = path.stat().st_mode
            mode = 0o750 if source_mode & stat.S_IXUSR else 0o640
            os.chmod(path, mode)
            os.chown(path, uid, gid)


def systemd_quote(value: str) -> str:
    if any(character in value for character in "\r\n\x00"):
        raise ValueError("A systemd value contains control characters")
    escaped = value.replace("\\", "\\\\").replace('"', '\\"').replace("%", "%%")
    return f'"{escaped}"'


def systemd_path(value: Path) -> str:
    text = str(value)
    if not value.is_absolute() or any(character in text for character in "\r\n\x00"):
        raise ValueError("A systemd path must be absolute and contain no control characters")
    safe = b"/abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_.:-"
    escaped = []
    for byte in text.encode("utf-8"):
        if byte == ord("%"):
            escaped.append("%%")
        elif byte in safe:
            escaped.append(chr(byte))
        else:
            escaped.append(f"\\x{byte:02x}")
    return "".join(escaped)


def service_is_active(pipeline_id: str) -> bool:
    return run(["systemctl", "is-active", "--quiet", f"jetson-pipeline@{pipeline_id}.service"]).returncode == 0


def write_override(
    pipeline_id: str,
    username: str,
    groupname: str,
    home: Path,
    working_directory: Path,
    writable_paths: Sequence[Path],
) -> None:
    rows = [
        "[Service]",
        f"User={username}",
        f"Group={groupname}",
        f"WorkingDirectory={systemd_path(working_directory)}",
        f"Environment={systemd_quote('HOME=' + str(home))}",
    ]
    for path in writable_paths:
        rows.append(f"ReadWritePaths={systemd_path(path)}")
    rows.append("")
    override = SYSTEMD_ROOT / f"jetson-pipeline@{pipeline_id}.service.d" / "override.conf"
    atomic_text(override, "\n".join(rows))


def remove_pipeline(pipeline_id: str) -> None:
    pipeline_id = validate_id(pipeline_id)
    pipeline_root = REGISTRY_ROOT / pipeline_id
    if not (pipeline_root / "pipeline.json").is_file():
        raise FileNotFoundError("Pipeline is not registered")

    unit = f"jetson-pipeline@{pipeline_id}.service"
    run(["systemctl", "disable", "--now", unit], timeout=60)
    shutil.rmtree(SYSTEMD_ROOT / f"jetson-pipeline@{pipeline_id}.service.d", ignore_errors=True)

    archive_root = REGISTRY_ROOT / ".archive"
    archive_root.mkdir(mode=0o700, parents=True, exist_ok=True)
    timestamp = dt.datetime.now(dt.timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    archive = archive_root / f"{pipeline_id}-{timestamp}"
    suffix = 1
    while archive.exists():
        archive = archive_root / f"{pipeline_id}-{timestamp}-{suffix}"
        suffix += 1
    os.replace(pipeline_root, archive)
    checked(["systemctl", "daemon-reload"])
    print(f"Unregistered {pipeline_id}; snapshots retained at {archive}")


def register(args: argparse.Namespace) -> None:
    missing = [
        name
        for name in ("id", "label", "repo", "venv", "entry", "config", "user")
        if not getattr(args, name)
    ]
    if missing:
        raise ValueError("Missing required options: " + ", ".join(f"--{name}" for name in missing))
    if args.autostart and args.no_autostart:
        raise ValueError("Choose either --autostart or --no-autostart")

    pipeline_id = validate_id(args.id)
    label = args.label.strip()
    if (
        not label
        or len(label.encode("utf-8")) > 64
        or any(ord(character) < 32 or ord(character) == 127 for character in label)
    ):
        raise ValueError("Pipeline label must contain 1 to 64 UTF-8 bytes")

    account = pwd.getpwnam(args.user)
    uid = account.pw_uid
    gid = account.pw_gid
    groupname = checked(["id", "-gn", args.user])
    home = Path(account.pw_dir).resolve()

    repo = args.repo.expanduser().resolve(strict=True)
    if not repo.is_dir():
        raise NotADirectoryError(f"Repository is not a directory: {repo}")
    git_root = Path(git(repo, "rev-parse", "--show-toplevel")).resolve(strict=True)
    try:
        repo.relative_to(git_root)
    except ValueError as error:
        raise ValueError(f"Repository is outside its Git worktree: {git_root}") from error

    virtualenv = args.venv.expanduser().resolve(strict=True)
    python = virtualenv / "bin" / "python"
    if not virtualenv.is_dir() or not python.is_file() or not os.access(python, os.X_OK):
        raise ValueError(f"Virtualenv does not contain an executable bin/python: {virtualenv}")

    entrypoint = relative_path(args.entry, "entrypoint")
    config = relative_path(args.config, "config")
    for path, kind in ((repo / entrypoint, "entrypoint"), (repo / config, "config")):
        if path.is_symlink() or not path.is_file():
            raise FileNotFoundError(f"Pipeline {kind} is not a regular file: {path}")
    if entrypoint.suffix.lower() != ".py":
        raise ValueError("Pipeline entrypoint must be a Python file")
    if config.suffix.lower() not in {".yaml", ".yml"}:
        raise ValueError("Pipeline config must be a YAML file")

    working_directory = (args.working_dir or repo).expanduser().resolve(strict=True)
    if not working_directory.is_dir():
        raise NotADirectoryError(f"Working directory is not a directory: {working_directory}")

    writable_paths = []
    for requested in args.write_path:
        path = requested.expanduser().resolve(strict=False)
        if path == Path("/"):
            raise ValueError("The filesystem root cannot be a writable pipeline path")
        path.mkdir(mode=0o750, parents=True, exist_ok=True)
        if not path.is_dir():
            raise NotADirectoryError(f"Writable path is not a directory: {path}")
        os.chown(path, uid, gid)
        writable_paths.append(path)

    python_version = checked([str(python), "--version"])
    compile_result = run(
        [
            str(python),
            "-c",
            "import pathlib,sys; compile(pathlib.Path(sys.argv[1]).read_bytes(), sys.argv[1], 'exec')",
            str(repo / entrypoint),
        ]
    )
    if compile_result.returncode != 0:
        raise ValueError((compile_result.stderr or "Python entrypoint does not compile").strip())

    running = service_is_active(pipeline_id)
    if running and not args.restart_running:
        raise RuntimeError("Pipeline is already running; stop it or use --restart-running")

    revision = git(repo, "rev-parse", "HEAD")
    branch_result = git_run(repo, "symbolic-ref", "--quiet", "--short", "HEAD")
    branch = branch_result.stdout.strip() if branch_result.returncode == 0 else "(detached)"
    dirty = bool(
        git(
            repo,
            "status",
            "--porcelain",
            "--untracked-files=normal",
            "--",
            ".",
        )
    )
    files = git_files(repo)
    if entrypoint not in files or config not in files:
        raise ValueError("Entrypoint and config must be tracked or unignored Git worktree files")

    timestamp = dt.datetime.now(dt.timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    release_name = f"{timestamp}-{revision[:12]}" + ("-dirty" if dirty else "")
    pipeline_root = REGISTRY_ROOT / pipeline_id
    releases_root = pipeline_root / "releases"
    pipeline_root.mkdir(mode=0o750, parents=True, exist_ok=True)
    releases_root.mkdir(mode=0o750, parents=True, exist_ok=True)
    os.chmod(pipeline_root, 0o750)
    os.chmod(releases_root, 0o750)
    os.chown(pipeline_root, 0, gid)
    os.chown(releases_root, 0, gid)

    release = releases_root / release_name
    counter = 1
    while release.exists():
        release = releases_root / f"{release_name}-{counter}"
        counter += 1
    temporary_release = releases_root / f".{release.name}.tmp-{os.getpid()}"
    temporary_release.mkdir(mode=0o700)
    try:
        copy_snapshot(repo, temporary_release, files)
        secure_tree(temporary_release, 0, gid)
        os.replace(temporary_release, release)
    except Exception:
        shutil.rmtree(temporary_release, ignore_errors=True)
        raise

    current_temporary = pipeline_root / f".current.tmp-{os.getpid()}"
    current_temporary.symlink_to(Path("releases") / release.name)
    os.replace(current_temporary, pipeline_root / "current")

    manifest = {
        "schema_version": 1,
        "id": pipeline_id,
        "label": label,
        "description": args.description.strip(),
        "source_repo": str(repo),
        "source_git_root": str(git_root),
        "source_revision": revision,
        "source_branch": branch,
        "source_dirty": dirty,
        "snapshot_created_at": dt.datetime.now(dt.timezone.utc).isoformat().replace("+00:00", "Z"),
        "release": release.name,
        "virtualenv": str(virtualenv),
        "python": str(python),
        "python_version": python_version,
        "entrypoint": entrypoint.as_posix(),
        "config": config.as_posix(),
        "config_argument": "--config",
        "working_directory": str(working_directory),
        "writable_paths": [str(path) for path in writable_paths],
        "arguments": args.argument,
        "user": args.user,
    }
    atomic_json(pipeline_root / "pipeline.json", manifest, 0o640, 0, gid)
    write_override(
        pipeline_id,
        args.user,
        groupname,
        home,
        working_directory,
        writable_paths,
    )

    checked(["systemctl", "daemon-reload"])
    unit = f"jetson-pipeline@{pipeline_id}.service"
    if args.autostart:
        checked(["systemctl", "enable", unit])
    elif args.no_autostart:
        checked(["systemctl", "disable", unit])
    if running and args.restart_running:
        checked(["systemctl", "restart", unit], timeout=60)
    elif args.start_now:
        checked(["systemctl", "start", unit], timeout=60)

    print(f"Registered {pipeline_id} from {branch}@{revision[:12]} ({'dirty' if dirty else 'clean'})")
    print(f"Runtime snapshot: {release}")
    print(f"Python: {python} ({python_version})")


def main() -> int:
    if os.geteuid() != 0:
        print("Run as root.", file=sys.stderr)
        return 1
    args = parse_args()
    try:
        if args.remove:
            remove_pipeline(args.remove)
        else:
            register(args)
    except (FileNotFoundError, KeyError, OSError, RuntimeError, ValueError, subprocess.SubprocessError) as error:
        print(str(error), file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
