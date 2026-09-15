from __future__ import annotations

import json
import os
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from jetson_control.filesystem import StorageRegistry
from jetson_control.local_trash import LocalTrashManager, TrashConflict


class LocalTrashManagerTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory()
        self.base = Path(self.temporary.name)
        self.storage_root = self.base / "storage"
        self.logs_root = self.base / "logs"
        self.storage_root.mkdir()
        self.logs_root.mkdir()
        config = self.base / "roots.json"
        config.write_text(
            json.dumps({"data": {"label": "Data", "path": str(self.storage_root)}}),
            encoding="utf-8",
        )
        self.storage = StorageRegistry(config)
        self.state = self.base / "state"
        self.trash = LocalTrashManager(self.storage, self.state, self.logs_root)

    def tearDown(self) -> None:
        self.temporary.cleanup()

    def test_storage_directory_is_atomically_trashed_listed_and_restored(self) -> None:
        source = self.storage_root / "capture"
        source.mkdir()
        (source / "frame.bin").write_bytes(b"frame")

        trashed = self.trash.trash_storage_entry("data", "capture")

        self.assertEqual(trashed["state"], "TRASHED")
        self.assertFalse(source.exists())
        self.assertEqual(
            self.trash.payload_path(str(trashed["trashId"])) .joinpath("frame.bin").read_bytes(),
            b"frame",
        )
        self.assertNotIn(
            ".jetson-control-trash",
            [entry["name"] for entry in self.storage.list_directory("data", "")],
        )
        restored = self.trash.restore(str(trashed["trashId"]), confirmed=True)
        self.assertEqual(restored["state"], "RESTORED")
        self.assertEqual((source / "frame.bin").read_bytes(), b"frame")
        self.assertEqual(self.trash.list_entries()["entries"], [])
        audit = self.trash.list_entries(include_restored=True)["entries"][0]["audit"]
        self.assertEqual([event["event"] for event in audit],
                         ["TRASH_REQUESTED", "TRASHED", "RESTORE_REQUESTED", "RESTORED"])

    def test_run_log_and_all_sidecars_move_and_restore_together(self) -> None:
        directory = self.logs_root / "capture"
        directory.mkdir()
        log_id = "run-20260914T010203.000004Z-123.log"
        names = [
            log_id,
            log_id + ".route.jsonl",
            log_id + ".quality.jsonl",
            log_id + ".quality.json",
            log_id + ".context.json",
        ]
        for name in names:
            (directory / name).write_text(name, encoding="utf-8")

        trashed = self.trash.trash_run_history(self.logs_root, "capture", log_id)
        self.assertTrue(all(not (directory / name).exists() for name in names))
        self.trash.restore(str(trashed["trashId"]), confirmed=True)
        self.assertTrue(all((directory / name).is_file() for name in names))

    def test_restore_refuses_existing_destination_without_overwrite(self) -> None:
        source = self.storage_root / "data.bin"
        source.write_bytes(b"original")
        trashed = self.trash.trash_storage_entry("data", "data.bin")
        source.write_bytes(b"new")
        with self.assertRaises(TrashConflict):
            self.trash.restore(str(trashed["trashId"]), confirmed=True)
        self.assertEqual(source.read_bytes(), b"new")
        self.assertEqual(self.trash.payload_path(str(trashed["trashId"])).read_bytes(), b"original")

    def test_startup_recovers_interrupted_move(self) -> None:
        source = self.storage_root / "recover.bin"
        source.write_bytes(b"recover")
        real_write = self.trash._write_record
        writes = 0

        def fail_after_move(record):
            nonlocal writes
            writes += 1
            if writes == 2:
                raise OSError("simulated transition journal failure")
            real_write(record)

        with patch.object(self.trash, "_write_record", side_effect=fail_after_move):
            with self.assertRaises(OSError):
                self.trash.trash_storage_entry("data", "recover.bin")
        recovered = LocalTrashManager(self.storage, self.state, self.logs_root)
        entry = recovered.list_entries()["entries"][0]
        self.assertEqual(entry["state"], "TRASHED")
        recovered.restore(str(entry["trashId"]), confirmed=True)
        self.assertEqual(source.read_bytes(), b"recover")

    def test_malformed_journal_never_changes_other_entry(self) -> None:
        first = self.storage_root / "first.bin"
        second = self.storage_root / "second.bin"
        first.write_bytes(b"first")
        second.write_bytes(b"second")
        first_entry = self.trash.trash_storage_entry("data", "first.bin")
        second_entry = self.trash.trash_storage_entry("data", "second.bin")
        before = {
            item["trashId"]: item
            for item in self.trash.list_entries()["entries"]
        }
        (self.trash.journal_dir / "00000000000000000000000000000000.json").write_text(
            "{malformed", encoding="utf-8"
        )
        (self.trash.journal_dir / "ffffffffffffffffffffffffffffffff.json").write_text(
            "[]", encoding="utf-8"
        )

        LocalTrashManager(self.storage, self.state, self.logs_root)

        after = {
            item["trashId"]: item
            for item in self.trash.list_entries()["entries"]
        }
        for trash_id in (str(first_entry["trashId"]), str(second_entry["trashId"])):
            self.assertEqual(after[trash_id]["state"], "TRASHED")
            self.assertEqual(after[trash_id]["audit"], before[trash_id]["audit"])

    def test_preexisting_symlink_trash_directory_is_rejected(self) -> None:
        outside = self.base / "outside"
        outside.mkdir()
        (self.storage_root / ".jetson-control-trash").symlink_to(
            outside, target_is_directory=True
        )
        source = self.storage_root / "safe.bin"
        source.write_bytes(b"safe")
        with self.assertRaises(OSError):
            self.trash.trash_storage_entry("data", "safe.bin")
        self.assertEqual(source.read_bytes(), b"safe")
        self.assertEqual(list(outside.iterdir()), [])

    def test_restore_race_never_overwrites_new_destination(self) -> None:
        source = self.storage_root / "race.bin"
        source.write_bytes(b"old")
        entry = self.trash.trash_storage_entry("data", "race.bin")
        real_rename = self.trash._rename_noreplace

        def create_destination_then_rename(source_fd, source_name, destination_fd, destination_name):
            descriptor = os.open(
                destination_name,
                os.O_WRONLY | os.O_CREAT | os.O_EXCL,
                0o600,
                dir_fd=destination_fd,
            )
            os.write(descriptor, b"new")
            os.close(descriptor)
            return real_rename(source_fd, source_name, destination_fd, destination_name)

        with patch.object(
            LocalTrashManager,
            "_rename_noreplace",
            side_effect=create_destination_then_rename,
        ):
            with self.assertRaises(TrashConflict):
                self.trash.restore(str(entry["trashId"]), confirmed=True)
        self.assertEqual(source.read_bytes(), b"new")
        self.assertEqual(self.trash.payload_path(str(entry["trashId"])).read_bytes(), b"old")


if __name__ == "__main__":
    unittest.main()
