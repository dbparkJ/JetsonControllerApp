#!/usr/bin/env python3
from __future__ import annotations

import json
import os
import re
import stat
import sys
from pathlib import Path
from typing import Any, Mapping


PIPELINE_ID = re.compile(r"^[a-z0-9][a-z0-9.-]{0,63}$")
REGISTRY_ROOT = Path(os.environ.get("JETSON_PIPELINE_REGISTRY", "/opt/jetson-pipelines"))


def fail(message: str) -> "NoReturn":
    print(message, file=sys.stderr)
    raise SystemExit(1)


def relative_path(value: object, kind: str) -> Path:
    if not isinstance(value, str):
        fail(f"Manifest {kind} must be a string")
    path = Path(value)
    if not value or path.is_absolute() or ".." in path.parts or "\x00" in value:
        fail(f"Manifest {kind} is invalid")
    return path


def required_string(manifest: Mapping[str, Any], key: str) -> str:
    value = manifest.get(key)
    if not isinstance(value, str) or not value:
        fail(f"Manifest field is invalid: {key}")
    return value


def main() -> int:
    if len(sys.argv) != 2 or not PIPELINE_ID.fullmatch(sys.argv[1]):
        fail("Usage: run-pipeline.py <pipeline-id>")
    pipeline_id = sys.argv[1]
    pipeline_root = REGISTRY_ROOT / pipeline_id
    manifest_path = pipeline_root / "pipeline.json"
    try:
        manifest_stat = manifest_path.stat()
        if manifest_stat.st_uid != 0 or manifest_stat.st_mode & (stat.S_IWGRP | stat.S_IWOTH):
            fail("Pipeline manifest ownership or permissions are unsafe")
        with manifest_path.open("r", encoding="utf-8") as source:
            manifest = json.load(source)
    except (FileNotFoundError, json.JSONDecodeError, OSError) as error:
        fail(f"Could not load pipeline manifest: {error}")
    if not isinstance(manifest, dict) or manifest.get("schema_version") != 1:
        fail("Pipeline manifest schema is invalid")
    if manifest.get("id") != pipeline_id:
        fail("Pipeline manifest identity mismatch")

    releases_root = (pipeline_root / "releases").resolve(strict=True)
    release = (pipeline_root / "current").resolve(strict=True)
    try:
        release.relative_to(releases_root)
    except ValueError:
        fail("Current pipeline release leaves the release directory")
    for path in (pipeline_root, releases_root, release):
        path_stat = path.stat()
        if path_stat.st_uid != 0 or path_stat.st_mode & (stat.S_IWGRP | stat.S_IWOTH):
            fail(f"Pipeline release ownership or permissions are unsafe: {path}")

    virtualenv = Path(required_string(manifest, "virtualenv")).resolve(strict=True)
    python = Path(required_string(manifest, "python"))
    expected_python = virtualenv / "bin" / "python"
    if python != expected_python:
        fail("Selected Python does not belong to the configured virtualenv")
    if not python.is_file() or not os.access(python, os.X_OK):
        fail("Selected virtualenv Python is not executable")
    working_directory = Path(required_string(manifest, "working_directory")).resolve(strict=True)
    if not working_directory.is_dir():
        fail("Pipeline working directory is unavailable")

    entrypoint_relative = relative_path(manifest.get("entrypoint"), "entrypoint")
    config_relative = relative_path(manifest.get("config"), "config")
    entrypoint = (release / entrypoint_relative).resolve(strict=True)
    config = (release / config_relative).resolve(strict=True)
    for path, kind in ((entrypoint, "entrypoint"), (config, "config")):
        try:
            path.relative_to(release)
        except ValueError:
            fail(f"Pipeline {kind} leaves the current release")
        if not path.is_file():
            fail(f"Pipeline {kind} is not a file")

    arguments = manifest.get("arguments", [])
    if not isinstance(arguments, list) or any(not isinstance(value, str) for value in arguments):
        fail("Pipeline arguments must be an array of strings")
    config_argument = required_string(manifest, "config_argument")

    environment = os.environ.copy()
    environment.update(
        {
            "JETSON_PIPELINE_ID": pipeline_id,
            "JETSON_PIPELINE_RELEASE": str(release),
            "PATH": f"{python.parent}:{environment.get('PATH', '')}",
            "PYTHONPATH": (
                f"{release}:{environment['PYTHONPATH']}"
                if environment.get("PYTHONPATH")
                else str(release)
            ),
            "PYTHONUNBUFFERED": "1",
            "PYTHONDONTWRITEBYTECODE": "1",
        }
    )
    os.chdir(working_directory)
    command = [
        str(python),
        "-u",
        str(entrypoint),
        config_argument,
        str(config),
        *arguments,
    ]
    os.execve(str(python), command, environment)
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
