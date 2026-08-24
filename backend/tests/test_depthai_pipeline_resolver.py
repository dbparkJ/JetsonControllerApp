import importlib.util
import json
import os
import tempfile
import unittest
from pathlib import Path


SCRIPT = (
    Path(__file__).resolve().parents[1]
    / "scripts"
    / "resolve-depthai-pipeline-id.py"
)
SPEC = importlib.util.spec_from_file_location("depthai_pipeline_resolver", SCRIPT)
assert SPEC is not None and SPEC.loader is not None
RESOLVER = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(RESOLVER)


class DepthaiPipelineResolverTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)
        self.repository = self.root / "26_camera_record"
        self.repository.mkdir()
        self.registry = self.root / "registry"
        self.registry.mkdir()
        self.registry.chmod(0o755)

    def tearDown(self) -> None:
        self.temporary.cleanup()

    def register(self, pipeline_id: str, source: Path) -> None:
        root = self.registry / pipeline_id
        root.mkdir()
        manifest = root / "pipeline.json"
        manifest.write_text(
            json.dumps(
                {
                    "schema_version": 1,
                    "id": pipeline_id,
                    "source_repo": str(source),
                }
            ),
            encoding="utf-8",
        )
        manifest.chmod(0o640)
        os.chown(manifest, os.stat(self.registry).st_uid, -1)

    def test_reuses_single_convention_registration_for_same_repo(self) -> None:
        self.register("26_camera_record", self.repository)

        selected = RESOLVER.resolve_pipeline_id(
            self.repository,
            self.registry,
            expected_owner_uid=os.getuid(),
        )

        self.assertEqual(selected, "26_camera_record")

    def test_uses_canonical_fallback_when_repo_is_not_registered(self) -> None:
        self.register("other-capture", self.root / "other")

        selected = RESOLVER.resolve_pipeline_id(
            self.repository,
            self.registry,
            expected_owner_uid=os.getuid(),
        )

        self.assertEqual(selected, "depthai-capture")

    def test_fails_closed_when_multiple_ids_reference_same_repo(self) -> None:
        self.register("26_camera_record", self.repository)
        self.register("depthai-capture", self.repository)

        with self.assertRaisesRegex(ValueError, "Multiple registered pipelines"):
            RESOLVER.resolve_pipeline_id(
                self.repository,
                self.registry,
                expected_owner_uid=os.getuid(),
            )

    def test_rejects_writable_or_wrong_owner_registry(self) -> None:
        self.registry.chmod(0o775)
        with self.assertRaisesRegex(ValueError, "ownership or permissions"):
            RESOLVER.resolve_pipeline_id(
                self.repository,
                self.registry,
                expected_owner_uid=os.getuid(),
            )

        self.registry.chmod(0o755)
        with self.assertRaisesRegex(ValueError, "ownership or permissions"):
            RESOLVER.resolve_pipeline_id(
                self.repository,
                self.registry,
                expected_owner_uid=os.getuid() + 1,
            )


if __name__ == "__main__":
    unittest.main()
