from __future__ import annotations

import os
import re
from dataclasses import dataclass
from pathlib import Path
from typing import Dict


PIPELINE_FOLDER_ID = re.compile(r"^[a-z0-9][a-z0-9_.-]{0,63}$")
CONFIG_FILENAMES = ("config.yaml", "config.yml")


@dataclass(frozen=True)
class PipelineFolderLayout:
    """Resolved paths for the intentionally small pipeline folder convention."""

    repository: Path
    pipeline_id: str
    virtualenv: Path
    entrypoint: Path
    config: Path
    results: Path

    def response(self) -> Dict[str, object]:
        return {
            "pipelineId": self.pipeline_id,
            "repository": str(self.repository),
            "virtualenv": str(self.virtualenv),
            "entrypoint": self.entrypoint.relative_to(self.repository).as_posix(),
            "config": self.config.relative_to(self.repository).as_posix(),
            "workingDirectory": str(self.repository),
            "resultsDirectory": str(self.results),
            "resultsExists": self.results.is_dir(),
        }


def discover_pipeline_folder(folder: Path) -> PipelineFolderLayout:
    """Validate and resolve a pipeline folder without modifying it.

    A valid folder is named with a systemd-safe lowercase id and contains exactly
    one root YAML configuration, ``main.py``, and ``.venv/bin/python``.  The
    ``results`` directory is allowed to be absent because registration creates it
    with the configured pipeline account as owner.
    """

    requested = folder.expanduser()
    if requested.is_symlink():
        raise ValueError("Pipeline folder must not be a symbolic link")
    repository = requested.resolve(strict=True)
    if not repository.is_dir():
        raise NotADirectoryError(f"Pipeline folder is not a directory: {repository}")
    if not PIPELINE_FOLDER_ID.fullmatch(repository.name):
        raise ValueError(
            "Pipeline folder name must use lowercase letters, digits, dots, underscores, or hyphens"
        )

    virtualenv = repository / ".venv"
    python = virtualenv / "bin" / "python"
    if virtualenv.is_symlink() or not virtualenv.is_dir():
        raise FileNotFoundError("Pipeline folder must contain a .venv directory")
    if not python.is_file() or not os.access(python, os.X_OK):
        raise FileNotFoundError(
            "Pipeline .venv must contain an executable bin/python"
        )

    entrypoint = repository / "main.py"
    if entrypoint.is_symlink() or not entrypoint.is_file():
        raise FileNotFoundError("Pipeline folder must contain a regular main.py file")

    config_candidates = [
        repository / name
        for name in CONFIG_FILENAMES
        if (repository / name).exists()
    ]
    if len(config_candidates) != 1:
        raise ValueError(
            "Pipeline folder must contain exactly one of config.yaml or config.yml"
        )
    config = config_candidates[0]
    if config.is_symlink() or not config.is_file():
        raise FileNotFoundError("Pipeline YAML config must be a regular file")

    results = repository / "results"
    if results.exists() and (results.is_symlink() or not results.is_dir()):
        raise ValueError("Pipeline results path must be a real directory")

    return PipelineFolderLayout(
        repository=repository,
        pipeline_id=repository.name,
        virtualenv=virtualenv,
        entrypoint=entrypoint,
        config=config,
        results=results,
    )
