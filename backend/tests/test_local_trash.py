from __future__ import annotations

import json
import os
import tempfile
import threading
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

    def test_empty_purges_only_explicit_snapshot_and_is_idempotent(self) -> None:
        for name in ("first.bin", "second.bin", "new.bin"):
            (self.storage_root / name).write_bytes(name.encode())
        first = self.trash.trash_storage_entry("data", "first.bin")
        second = self.trash.trash_storage_entry("data", "second.bin")
        snapshot = [str(first["trashId"]), str(second["trashId"])]
        newer = self.trash.trash_storage_entry("data", "new.bin")

        listing = self.trash.list_entries()
        self.assertTrue(listing["emptySupported"])
        self.assertTrue(all(item["purgeSupported"] for item in listing["entries"]))
        emptied = self.trash.empty(snapshot, confirmed=True)

        self.assertEqual([item["state"] for item in emptied["results"]], ["PURGED", "PURGED"])
        self.assertEqual(
            [item["trashId"] for item in self.trash.list_entries()["entries"]],
            [newer["trashId"]],
        )
        repeated = self.trash.empty(snapshot, confirmed=True)
        self.assertEqual([item["state"] for item in repeated["results"]], ["PURGED", "PURGED"])

    def test_empty_validates_confirmation_identifiers_and_state(self) -> None:
        source = self.storage_root / "restored.bin"
        source.write_bytes(b"data")
        entry = self.trash.trash_storage_entry("data", "restored.bin")
        trash_id = str(entry["trashId"])
        self.trash.restore(trash_id, confirmed=True)

        with self.assertRaises(TrashConflict):
            self.trash.empty([trash_id], confirmed=False)
        for identifiers in (
            [],
            [trash_id, trash_id],
            ["bad"],
            [f"{index:032x}" for index in range(201)],
        ):
            with self.assertRaises(ValueError):
                self.trash.empty(identifiers, confirmed=True)
        result = self.trash.empty([trash_id, "0" * 32], confirmed=True)
        self.assertEqual([item["state"] for item in result["results"]], ["FAILED", "FAILED"])
        self.assertEqual(source.read_bytes(), b"data")

    def test_purge_unlinks_nested_symlink_without_touching_target(self) -> None:
        outside = self.base / "outside-data"
        outside.mkdir()
        protected = outside / "protected.bin"
        protected.write_bytes(b"keep")
        source = self.storage_root / "tree"
        source.mkdir()
        (source / "link").symlink_to(outside, target_is_directory=True)
        (source / "local.bin").write_bytes(b"remove")
        entry = self.trash.trash_storage_entry("data", "tree")

        result = self.trash.empty([str(entry["trashId"])], confirmed=True)

        self.assertEqual(result["results"][0]["state"], "PURGED")
        self.assertEqual(protected.read_bytes(), b"keep")

    def test_purge_never_touches_replacement_at_original_path(self) -> None:
        source = self.storage_root / "replaced.bin"
        source.write_bytes(b"old")
        entry = self.trash.trash_storage_entry("data", "replaced.bin")
        source.write_bytes(b"new")

        result = self.trash.empty([str(entry["trashId"])], confirmed=True)

        self.assertEqual(result["results"][0]["state"], "PURGED")
        self.assertEqual(source.read_bytes(), b"new")

    def test_purging_journal_recovers_after_final_write_failure(self) -> None:
        source = self.storage_root / "recover-purge.bin"
        source.write_bytes(b"remove")
        entry = self.trash.trash_storage_entry("data", "recover-purge.bin")
        trash_id = str(entry["trashId"])
        real_write = self.trash._write_record
        failed = False

        def fail_final_write(record):
            nonlocal failed
            if record.get("state") == "PURGED" and not failed:
                failed = True
                raise OSError("simulated final purge journal failure")
            real_write(record)

        with patch.object(self.trash, "_write_record", side_effect=fail_final_write):
            result = self.trash.empty([trash_id], confirmed=True)
        self.assertEqual(result["results"][0]["state"], "PURGING")
        self.assertFalse((self.storage_root / ".jetson-control-trash" / trash_id).exists())

        recovered = LocalTrashManager(self.storage, self.state, self.logs_root)
        self.assertEqual(recovered.get_entry(trash_id)["state"], "PURGED")
        self.assertEqual(recovered.list_entries()["entries"], [])

    def test_empty_reports_partial_failure_and_restart_resumes_it(self) -> None:
        for name in ("ok.bin", "retry.bin"):
            (self.storage_root / name).write_bytes(name.encode())
        ok = self.trash.trash_storage_entry("data", "ok.bin")
        retry = self.trash.trash_storage_entry("data", "retry.bin")
        real_purge = self.trash._purge_payload_directory

        def fail_retry(root, trash_id):
            if trash_id == retry["trashId"]:
                raise OSError("simulated payload failure")
            return real_purge(root, trash_id)

        with patch.object(self.trash, "_purge_payload_directory", side_effect=fail_retry):
            result = self.trash.empty(
                [str(ok["trashId"]), str(retry["trashId"])], confirmed=True
            )
        self.assertEqual([item["state"] for item in result["results"]], ["PURGED", "PURGING"])

        recovered = LocalTrashManager(self.storage, self.state, self.logs_root)
        self.assertEqual(recovered.get_entry(str(retry["trashId"]))["state"], "PURGED")

    def test_restore_and_purge_race_has_one_consistent_winner(self) -> None:
        source = self.storage_root / "race-operation.bin"
        source.write_bytes(b"data")
        entry = self.trash.trash_storage_entry("data", "race-operation.bin")
        trash_id = str(entry["trashId"])
        barrier = threading.Barrier(3)
        outcomes = []

        def restore():
            barrier.wait()
            try:
                outcomes.append(("restore", self.trash.restore(trash_id, confirmed=True)["state"]))
            except TrashConflict:
                outcomes.append(("restore", "FAILED"))

        def purge():
            barrier.wait()
            outcomes.append(("purge", self.trash.empty([trash_id], confirmed=True)["results"][0]["state"]))

        threads = [threading.Thread(target=restore), threading.Thread(target=purge)]
        for thread in threads:
            thread.start()
        barrier.wait()
        for thread in threads:
            thread.join(timeout=2)
            self.assertFalse(thread.is_alive())

        final = self.trash.get_entry(trash_id)["state"]
        self.assertIn(final, {"RESTORED", "PURGED"})
        self.assertEqual(source.exists(), final == "RESTORED")
        if final == "PURGED":
            self.assertIn(("restore", "FAILED"), outcomes)
        else:
            self.assertIn(("purge", "FAILED"), outcomes)

    def test_recovery_rejects_traversal_and_mismatched_journal_ids(self) -> None:
        for name in ("first-corrupt.bin", "second-safe.bin"):
            (self.storage_root / name).write_bytes(name.encode())
        first = self.trash.trash_storage_entry("data", "first-corrupt.bin")
        second = self.trash.trash_storage_entry("data", "second-safe.bin")
        first_id = str(first["trashId"])
        second_id = str(second["trashId"])
        first_journal = self.trash.journal_dir / f"{first_id}.json"
        corrupted = json.loads(first_journal.read_text(encoding="utf-8"))
        corrupted.update(state="PURGING", trashId=second_id)
        first_journal.write_text(json.dumps(corrupted), encoding="utf-8")

        outside = self.storage_root / "outside"
        outside.mkdir(mode=0o700)
        protected = outside / "protected.bin"
        protected.write_bytes(b"keep")
        traversal_path = self.trash.journal_dir / f"{'f' * 32}.json"
        traversal = dict(corrupted, trashId="../outside")
        traversal_path.write_text(json.dumps(traversal), encoding="utf-8")

        recovered = LocalTrashManager(self.storage, self.state, self.logs_root)

        self.assertEqual(protected.read_bytes(), b"keep")
        self.assertTrue((self.storage_root / ".jetson-control-trash" / first_id).is_dir())
        self.assertTrue((self.storage_root / ".jetson-control-trash" / second_id).is_dir())
        self.assertEqual(recovered.get_entry(second_id)["state"], "TRASHED")
        self.assertEqual(
            recovered.empty([first_id], confirmed=True)["results"][0]["state"],
            "FAILED",
        )
        self.assertTrue((self.storage_root / ".jetson-control-trash" / second_id).is_dir())
        with self.assertRaises(ValueError):
            recovered._purge_payload_directory(self.storage_root, "../outside")


if __name__ == "__main__":
    unittest.main()
