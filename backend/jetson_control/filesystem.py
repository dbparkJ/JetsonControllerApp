from __future__ import annotations

import os
import shutil
import stat
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Dict, Iterable, Iterator, List, Mapping, Tuple

from .config import load_json_object, validate_config_id


@dataclass(frozen=True)
class StorageRoot:
    id: str
    label: str
    path: Path
    path_hint: str | None


class StorageRegistry:
    def __init__(self, config_path: Path) -> None:
        self.config_path = config_path

    def roots(self) -> Dict[str, StorageRoot]:
        raw = load_json_object(self.config_path)
        roots: Dict[str, StorageRoot] = {}
        for root_id, value in raw.items():
            validate_config_id(root_id, "storage root")
            if not isinstance(value, dict):
                raise ValueError(f"Storage root {root_id!r} must be an object")
            label = str(value.get("label", root_id)).strip()
            path_text = str(value.get("path", "")).strip()
            if not label or not path_text:
                raise ValueError(f"Storage root {root_id!r} needs label and path")
            roots[root_id] = StorageRoot(
                id=root_id,
                label=label,
                path=Path(path_text).expanduser().resolve(),
                path_hint=(
                    str(value["path_hint"])
                    if value.get("path_hint") is not None
                    else None
                ),
            )
        return roots

    def roots_response(self) -> List[Dict[str, object]]:
        response = []
        for root in self.roots().values():
            if not root.path.exists() or not root.path.is_dir():
                continue
            try:
                usage = shutil.disk_usage(root.path)
                capacity = {
                    "totalBytes": usage.total,
                    "usedBytes": usage.used,
                    "availableBytes": usage.free,
                }
            except OSError:
                capacity = {
                    "totalBytes": None,
                    "usedBytes": None,
                    "availableBytes": None,
                }
            response.append(
                {
                    "id": root.id,
                    "label": root.label,
                    "pathHint": root.path_hint,
                    **capacity,
                }
            )
        return response

    def primary_path(self) -> Path:
        for root in self.roots().values():
            if root.path.exists() and root.path.is_dir():
                return root.path
        return Path("/")

    def resolve(self, root_id: str, relative_path: str) -> Tuple[StorageRoot, Path]:
        roots = self.roots()
        try:
            root = roots[root_id]
        except KeyError as error:
            raise ValueError("Unknown storage root") from error

        relative = relative_path or ""
        if "\x00" in relative:
            raise ValueError("Path contains a null byte")

        target = (root.path / relative.lstrip("/")).resolve()
        try:
            target.relative_to(root.path)
        except ValueError as error:
            raise ValueError("Path traversal rejected") from error
        return root, target

    def list_directory(self, root_id: str, relative_path: str) -> List[Dict[str, object]]:
        root, target = self.resolve(root_id, relative_path)
        if not target.exists():
            raise FileNotFoundError("Path not found")
        if not target.is_dir():
            raise NotADirectoryError("Path is not a directory")

        entries: List[Dict[str, object]] = []
        try:
            children = list(target.iterdir())
        except PermissionError as error:
            raise PermissionError("Directory is not readable") from error

        for item in children:
            try:
                item_stat = item.lstat()
                if stat.S_ISDIR(item_stat.st_mode):
                    item_type = "DIRECTORY"
                elif stat.S_ISREG(item_stat.st_mode):
                    item_type = "FILE"
                else:
                    continue
                relative = item.relative_to(root.path).as_posix()
            except (OSError, ValueError):
                continue
            entries.append(
                {
                    "name": item.name,
                    "relativePath": relative,
                    "type": item_type,
                    "sizeBytes": None if item_type == "DIRECTORY" else item_stat.st_size,
                    "modifiedAt": datetime.fromtimestamp(
                        item_stat.st_mtime, tz=timezone.utc
                    ).isoformat().replace("+00:00", "Z"),
                }
            )

        entries.sort(key=lambda entry: (entry["type"] != "DIRECTORY", str(entry["name"]).casefold()))
        return entries

    def iter_regular_files(self, source: Path) -> Iterator[Path]:
        if source.is_symlink():
            raise ValueError("Symbolic links cannot be uploaded")
        if source.is_file():
            yield source
            return
        if not source.is_dir():
            raise ValueError("Upload source must be a file or directory")

        for directory, directory_names, file_names in os.walk(source, followlinks=False):
            base = Path(directory)
            directory_names[:] = [
                name for name in directory_names if not (base / name).is_symlink()
            ]
            directory_names.sort(key=str.casefold)
            for name in sorted(file_names, key=str.casefold):
                path = base / name
                if path.is_file() and not path.is_symlink():
                    yield path
