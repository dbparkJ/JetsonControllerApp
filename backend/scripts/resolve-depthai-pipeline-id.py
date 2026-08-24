#!/usr/bin/env python3
"""Reuse the single registered pipeline that already represents a source repo."""

from __future__ import annotations

import argparse
import json
import os
import re
import stat
from pathlib import Path
from typing import List


PIPELINE_ID = re.compile(r"^[a-z0-9][a-z0-9_.-]{0,63}$")


def _trusted_manifest(path: Path, owner_uid: int) -> object:
    flags = os.O_RDONLY | getattr(os, "O_CLOEXEC", 0)
    if hasattr(os, "O_NOFOLLOW"):
        flags |= os.O_NOFOLLOW
    descriptor = os.open(path, flags)
    try:
        metadata = os.fstat(descriptor)
        if (
            not stat.S_ISREG(metadata.st_mode)
            or metadata.st_uid != owner_uid
            or metadata.st_mode & (stat.S_IWGRP | stat.S_IWOTH)
        ):
            raise ValueError(f"Pipeline manifest permissions are unsafe: {path}")
        with os.fdopen(os.dup(descriptor), "r", encoding="utf-8") as source:
            return json.load(source)
    finally:
        os.close(descriptor)


def matching_pipeline_ids(
    repository: Path,
    registry_root: Path,
    *,
    expected_owner_uid: int = 0,
) -> List[str]:
    requested = repository.expanduser().resolve(strict=False)
    try:
        registry_metadata = os.lstat(registry_root)
    except FileNotFoundError:
        return []
    if (
        stat.S_ISLNK(registry_metadata.st_mode)
        or not stat.S_ISDIR(registry_metadata.st_mode)
        or registry_metadata.st_uid != expected_owner_uid
        or registry_metadata.st_mode & (stat.S_IWGRP | stat.S_IWOTH)
    ):
        raise ValueError("Pipeline registry ownership or permissions are unsafe")

    matches = []
    for child in sorted(registry_root.iterdir(), key=lambda path: path.name):
        if child.name.startswith("."):
            continue
        try:
            metadata = os.lstat(child)
        except FileNotFoundError:
            continue
        if stat.S_ISLNK(metadata.st_mode) or not stat.S_ISDIR(metadata.st_mode):
            continue
        manifest_path = child / "pipeline.json"
        try:
            value = _trusted_manifest(manifest_path, expected_owner_uid)
        except FileNotFoundError:
            continue
        except (json.JSONDecodeError, OSError) as error:
            raise ValueError(f"Could not inspect pipeline manifest: {manifest_path}") from error
        if not isinstance(value, dict):
            continue
        source_value = value.get("source_repo")
        if not isinstance(source_value, str) or not source_value:
            continue
        if Path(source_value).expanduser().resolve(strict=False) != requested:
            continue
        pipeline_id = value.get("id")
        if (
            not isinstance(pipeline_id, str)
            or pipeline_id != child.name
            or PIPELINE_ID.fullmatch(pipeline_id) is None
        ):
            raise ValueError(f"Matching pipeline identity is invalid: {manifest_path}")
        matches.append(pipeline_id)
    return matches


def resolve_pipeline_id(
    repository: Path,
    registry_root: Path,
    fallback: str = "depthai-capture",
    *,
    expected_owner_uid: int = 0,
) -> str:
    if PIPELINE_ID.fullmatch(fallback) is None:
        raise ValueError("Fallback pipeline id is invalid")
    matches = matching_pipeline_ids(
        repository,
        registry_root,
        expected_owner_uid=expected_owner_uid,
    )
    if len(matches) > 1:
        raise ValueError(
            "Multiple registered pipelines refer to the DepthAI repository: "
            + ", ".join(matches)
        )
    return matches[0] if matches else fallback


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--repo", required=True, type=Path)
    parser.add_argument(
        "--registry",
        type=Path,
        default=Path("/opt/jetson-pipelines"),
    )
    parser.add_argument("--fallback", default="depthai-capture")
    arguments = parser.parse_args()
    try:
        print(
            resolve_pipeline_id(
                arguments.repo,
                arguments.registry,
                arguments.fallback,
            )
        )
    except (OSError, ValueError) as error:
        parser.exit(1, f"{error}\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
