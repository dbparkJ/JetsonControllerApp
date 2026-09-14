import tempfile
import unittest
from pathlib import Path

from jetson_control.survey_context import SurveyContextConflict, SurveyContextStore


class SurveyContextStoreTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.clock_value = 1_777_000_000.0
        self.store = SurveyContextStore(
            Path(self.temporary.name) / "survey-context.json",
            clock=lambda: self.clock_value,
        )

    def tearDown(self):
        self.temporary.cleanup()

    def test_project_and_section_crud_persists_revisions(self):
        project = self.store.create_project("도로 조사", "project-request-0001")
        replay = self.store.create_project("도로 조사", "project-request-0001")
        self.assertEqual(replay, project)
        section = self.store.create_section(
            project["surveyProjectId"], "1 구간", "section-request-0001"
        )

        updated = self.store.update_section(
            project["surveyProjectId"], section["surveySectionId"], "2 구간", 1
        )
        self.assertEqual(updated["revision"], 2)
        self.assertEqual(
            SurveyContextStore(self.store.path).get_section(
                project["surveyProjectId"], section["surveySectionId"]
            )["label"],
            "2 구간",
        )

        deleted = self.store.delete_section(
            project["surveyProjectId"], section["surveySectionId"], 2
        )
        self.assertTrue(deleted["deleted"])

    def test_mutation_rejects_stale_revision_and_changed_replay(self):
        project = self.store.create_project("A", "project-request-0002")
        with self.assertRaisesRegex(SurveyContextConflict, "changed") as stale:
            self.store.update_project(project["surveyProjectId"], "B", 9)
        self.assertEqual(stale.exception.code, "REVISION_MISMATCH")

        with self.assertRaises(SurveyContextConflict) as replay:
            self.store.create_project("different", "project-request-0002")
        self.assertEqual(replay.exception.code, "IDEMPOTENCY_CONFLICT")

    def test_snapshot_keeps_access_project_out_of_survey_context(self):
        project = self.store.create_project("Survey", "project-request-0003")
        section = self.store.create_section(
            project["surveyProjectId"], "Section", "section-request-0003"
        )
        snapshot = self.store.snapshot(
            project["surveyProjectId"], section["surveySectionId"], 1, 1, "device-1"
        )
        self.assertEqual(snapshot["surveyProjectId"], project["surveyProjectId"])
        self.assertEqual(snapshot["surveySectionId"], section["surveySectionId"])
        self.assertNotIn("projectId", snapshot)
        self.assertNotIn("accessProjectId", snapshot)


if __name__ == "__main__":
    unittest.main()
