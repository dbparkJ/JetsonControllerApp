import json
import os
import pwd
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import Mock

from jetson_control.filesystem import StorageRegistry
from jetson_control.run_context import RunContextConflict, RunContextService
from jetson_control.survey_context import SurveyContextStore


class FakePipelines:
    def __init__(self, registry, output_root):
        self.registry = registry
        self.output_root = output_root.resolve()
        self.state = "STOPPED"
        self.active_run_id = None
        self.control_error = None

    def runtime_identity(self, pipeline_id):
        if pipeline_id != "capture":
            raise ValueError("unknown pipeline")
        return {
            "pipelineId": pipeline_id,
            "label": "Capture",
            "sourceRevision": "a" * 40,
            "sourceDirty": False,
            "release": "/opt/fixture",
            "config": "config.yaml",
            "configSha256": "b" * 64,
            "writablePaths": [str(self.output_root)],
        }

    def assert_output_allowed(self, pipeline_id, output_directory):
        Path(output_directory).resolve().relative_to(self.output_root)

    def status(self, pipeline_id):
        return {
            "id": pipeline_id,
            "label": "Capture",
            "state": self.state,
            "activeRunId": self.active_run_id,
        }

    def control(self, pipeline_id, action):
        launch = json.loads((self.registry / pipeline_id / "next-run.json").read_text())
        self.state = "RUNNING"
        self.active_run_id = launch["runId"]
        if self.control_error is not None:
            raise self.control_error
        return self.status(pipeline_id)


class RunContextServiceTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.base = Path(self.temporary.name)
        self.clock_value = 1_777_000_000.0
        self.registry = self.base / "registry"
        (self.registry / "capture").mkdir(parents=True)
        self.logs = self.base / "logs"
        self.logs.mkdir()
        self.output = self.base / "output"
        self.output.mkdir()
        roots = self.base / "roots.json"
        roots.write_text(json.dumps({
            "recordings": {"label": "Records", "path": str(self.output)}
        }))
        self.marker = self.base / "time.json"
        self.marker.write_text(json.dumps({
            "schemaVersion": 1,
            "synchronized": True,
            "source": "MOBILE",
            "sourceTimeEpochMillis": int(self.clock_value * 1000),
            "synchronizedAtEpochMillis": int(self.clock_value * 1000),
            "offsetBeforeMillis": 0,
        }))
        self.marker.chmod(0o600)
        self.survey = SurveyContextStore(
            self.base / "state" / "survey.json", clock=lambda: self.clock_value
        )
        self.pipelines = FakePipelines(self.registry, self.output)
        now = int(self.clock_value * 1000)
        self.sensor = SimpleNamespace(status=lambda: SimpleNamespace(
            available=True,
            fresh=True,
            updated_at_epoch_millis=now,
            camera={"configured": True, "connected": True, "active": True, "lastFrameAtEpochMillis": now},
            gnss={"configured": True, "connected": True, "active": True, "lastSampleAtEpochMillis": now},
            imu={"configured": True, "connected": True, "active": True, "lastSampleAtEpochMillis": now},
        ))
        self.service = RunContextService(
            state_dir=self.base / "state",
            registry_root=self.registry,
            logs_root=self.logs,
            device_id="00000000-0000-0000-0000-000000000001",
            pipeline_user=pwd.getpwuid(os.geteuid()).pw_name,
            pipelines=self.pipelines,
            survey=self.survey,
            sensor_bridge=self.sensor,
            storage=StorageRegistry(roots),
            time_sync_marker=self.marker,
            time_sync_owner_uid=os.geteuid(),
            clock=lambda: self.clock_value,
        )
        self.project = self.service.create_project("Survey", "project-request-1001")
        self.section = self.service.create_section(
            self.project["surveyProjectId"], "Section", "section-request-1001"
        )

    def tearDown(self):
        self.temporary.cleanup()

    def configure_policy(self, request_id="policy-request-1001"):
        return self.service.set_policy(
            "capture",
            required_sensors=["camera", "gnss"],
            optional_sensors=["imu"],
            min_free_bytes=0,
            output_root_id="recordings",
            output_path="",
            expected_output={"minFiles": 1, "minBytes": 1, "patterns": ["*.jsonl"]},
            expected_revision=None,
            client_request_id=request_id,
        )

    def preflight(self, policy):
        return self.service.preflight(
            "capture",
            self.project["surveyProjectId"],
            self.section["surveySectionId"],
            1,
            1,
            policy["revision"],
        )

    def start(self, policy, preflight, request_id="start-request-1001"):
        return self.service.contextual_start(
            "capture",
            project_id=self.project["surveyProjectId"],
            section_id=self.section["surveySectionId"],
            project_revision=1,
            section_revision=1,
            policy_revision=policy["revision"],
            preflight_id=preflight["preflightId"],
            client_request_id=request_id,
        )

    def test_policy_is_versioned_and_replay_does_not_increment(self):
        first = self.configure_policy()
        replay = self.configure_policy()
        self.assertEqual(replay, first)
        self.assertEqual(first["policyVersion"], 1)
        self.assertNotIn("_clientRequestId", first)

        second = self.service.set_policy(
            "capture",
            required_sensors=["camera"], optional_sensors=["gnss", "imu"],
            min_free_bytes=1024, output_root_id="recordings", output_path="",
            expected_output={"minFiles": 1, "minBytes": 1, "patterns": []},
            expected_revision=first["revision"], client_request_id="policy-request-1002",
        )
        self.assertEqual(second["policyVersion"], 2)
        self.assertEqual(self.configure_policy(), first)

    def test_policy_replay_succeeds_while_active_but_new_mutation_is_locked(self):
        policy = self.configure_policy()
        self.start(policy, self.preflight(policy))
        self.assertEqual(self.configure_policy(), policy)
        with self.assertRaises(RunContextConflict) as conflict:
            self.service.set_policy(
                "capture", required_sensors=["camera"], optional_sensors=[],
                min_free_bytes=0, output_root_id="recordings", output_path="",
                expected_output={"minFiles": 0, "minBytes": 0, "patterns": []},
                expected_revision=policy["revision"], client_request_id="policy-request-1002",
            )
        self.assertEqual(conflict.exception.code, "CONTEXT_LOCKED")

    def test_large_policy_replays_remain_readable_after_restart(self):
        patterns = [("segment-%02d-" % index) + "x" * 180 for index in range(64)]
        first = self.service.set_policy(
            "capture", required_sensors=["camera"], optional_sensors=["gnss", "imu"],
            min_free_bytes=0, output_root_id="recordings", output_path="",
            expected_output={"minFiles": 1, "minBytes": 1, "patterns": patterns},
            expected_revision=None, client_request_id="large-policy-request-000",
        )
        current = first
        for index in range(1, 20):
            current = self.service.set_policy(
                "capture", required_sensors=["camera"], optional_sensors=["gnss", "imu"],
                min_free_bytes=index, output_root_id="recordings", output_path="",
                expected_output={"minFiles": 1, "minBytes": 1, "patterns": patterns},
                expected_revision=current["revision"],
                client_request_id="large-policy-request-%03d" % index,
            )
        self.assertLess(
            self.service.policy_requests_path.stat().st_size,
            512 * 1024,
        )
        restarted = RunContextService(
            state_dir=self.base / "state", registry_root=self.registry,
            logs_root=self.logs, device_id=self.service.device_id,
            pipeline_user=self.service.pipeline_user, pipelines=self.pipelines,
            survey=self.survey, sensor_bridge=self.sensor, storage=self.service.storage,
            time_sync_marker=self.marker, time_sync_owner_uid=os.geteuid(),
            clock=lambda: self.clock_value,
        )
        replay = restarted.set_policy(
            "capture", required_sensors=["camera"], optional_sensors=["gnss", "imu"],
            min_free_bytes=0, output_root_id="recordings", output_path="",
            expected_output={"minFiles": 1, "minBytes": 1, "patterns": patterns},
            expected_revision=None, client_request_id="large-policy-request-000",
        )
        self.assertEqual(replay, first)

    def test_preflight_preserves_context_snapshot_and_has_no_rtk_threshold(self):
        policy = self.configure_policy()
        preflight = self.preflight(policy)
        self.assertTrue(preflight["ready"])
        self.assertEqual(preflight["checks"]["storage"]["state"], "READY")
        self.assertNotIn("rtk", repr(preflight).lower())

        self.clock_value += 1
        started = self.start(policy, preflight)
        self.assertEqual(
            started["run"]["contextSnapshot"]["capturedAt"],
            preflight["contextSnapshot"]["capturedAt"],
        )

    def test_contextual_start_replay_is_same_run_and_changed_body_conflicts(self):
        policy = self.configure_policy()
        preflight = self.preflight(policy)
        first = self.start(policy, preflight)
        replay = self.start(policy, preflight)
        self.assertEqual(replay["run"]["runId"], first["run"]["runId"])
        self.assertEqual(replay["outcome"], "ALREADY_ACCEPTED")

        with self.assertRaises(RunContextConflict) as conflict:
            self.service.contextual_start(
                "capture",
                project_id=self.project["surveyProjectId"],
                section_id=self.section["surveySectionId"],
                project_revision=1,
                section_revision=1,
                policy_revision=policy["revision"],
                preflight_id="0" * 32,
                client_request_id="start-request-1001",
            )
        self.assertEqual(conflict.exception.code, "IDEMPOTENCY_CONFLICT")

    def test_start_rejects_release_change_after_preflight(self):
        policy = self.configure_policy()
        preflight = self.preflight(policy)
        original = self.pipelines.runtime_identity

        def changed(pipeline_id):
            identity = original(pipeline_id)
            identity["release"] = "/opt/re-registered-release"
            return identity

        self.pipelines.runtime_identity = changed
        with self.assertRaises(RunContextConflict) as conflict:
            self.start(policy, preflight)
        self.assertEqual(conflict.exception.code, "PIPELINE_CHANGED")

    def test_start_rejects_preflight_from_previous_boot_even_when_fresh(self):
        self.service.boot_id = lambda: "boot-before-restart"
        policy = self.configure_policy()
        preflight = self.preflight(policy)
        self.service.boot_id = lambda: "boot-after-restart"
        with self.assertRaises(RunContextConflict) as conflict:
            self.start(policy, preflight)
        self.assertEqual(conflict.exception.code, "PIPELINE_CHANGED")

    def test_terminal_evidence_counts_only_run_directory_and_tombstones_launch(self):
        policy = self.configure_policy()
        started = self.start(policy, self.preflight(policy))
        run = started["run"]
        output_directory = Path(run["outputDirectory"])
        (self.output / "old.jsonl").write_text("legacy")
        (output_directory / "new.jsonl").write_text("new")
        log_directory = self.logs / "capture"
        log_directory.mkdir()
        (log_directory / run["logId"]).write_text(
            "=== Jetson pipeline run finished ===\n"
            "finished_at=2026-09-14T10:00:00Z\n"
            "exit_code=0\nterminal_state=COMPLETED\nstop_signal=\n"
        )
        self.pipelines.state = "STOPPED"
        self.pipelines.active_run_id = None

        observed = self.service.get_run(run["runId"])
        self.assertEqual(observed["state"], "COMPLETED")
        self.assertEqual(observed["output"]["manifest"]["fileCount"], 1)
        self.assertEqual(observed["output"]["manifest"]["expectationState"], "SATISFIED")
        self.assertEqual(observed["uploadContext"]["runId"], run["runId"])
        tombstone = json.loads((self.registry / "capture" / "next-run.json").read_text())
        self.assertTrue(tombstone["consumed"])

    def test_command_error_keeps_reservation_until_exact_run_reconciliation(self):
        policy = self.configure_policy()
        preflight = self.preflight(policy)
        self.pipelines.control_error = RuntimeError("lost response")
        with self.assertRaisesRegex(RuntimeError, "lost response"):
            self.start(policy, preflight)
        active = self.service.active_run("capture")
        self.assertIsNotNone(active)
        self.assertEqual(active["runId"], self.pipelines.active_run_id)

    def test_non_running_pipeline_state_does_not_promote_starting_record(self):
        policy = self.configure_policy()
        started = self.start(policy, self.preflight(policy))
        self.pipelines.state = "STARTING"
        self.pipelines.active_run_id = started["run"]["runId"]
        observed = self.service.get_run(started["run"]["runId"])
        self.assertEqual(observed["state"], "STARTING")

    def test_stopped_run_without_footer_recovers_as_failed(self):
        policy = self.configure_policy()
        run = self.start(policy, self.preflight(policy))["run"]
        log_directory = self.logs / "capture"
        log_directory.mkdir()
        (log_directory / run["logId"]).write_text("partial output\n")
        self.pipelines.state = "STOPPED"
        self.pipelines.active_run_id = None
        observed = self.service.get_run(run["runId"])
        self.assertEqual(observed["state"], "FAILED")
        self.assertEqual(observed["stopReason"], "INTERRUPTED_NO_TERMINAL_EVIDENCE")

    def test_unknown_status_never_unlocks_delayed_or_previously_running_run(self):
        policy = self.configure_policy()
        run = self.start(policy, self.preflight(policy))["run"]
        self.pipelines.state = "UNKNOWN"
        self.pipelines.active_run_id = None
        self.clock_value += 10
        delayed = self.service.get_run(run["runId"])
        self.assertTrue(delayed["active"])
        self.assertEqual(delayed["state"], "STARTING")

        self.pipelines.state = "RUNNING"
        self.pipelines.active_run_id = run["runId"]
        running = self.service.get_run(run["runId"])
        self.assertEqual(running["state"], "RUNNING")
        self.pipelines.state = "UNKNOWN"
        self.pipelines.active_run_id = None
        uncertain = self.service.get_run(run["runId"])
        self.assertTrue(uncertain["active"])
        self.assertEqual(uncertain["state"], "RUNNING")

    def test_operator_stop_intent_wins_completion_race(self):
        policy = self.configure_policy()
        run = self.start(policy, self.preflight(policy))["run"]
        self.service.record_stop_intent("capture")
        stopping = self.service.get_run(run["runId"])
        self.assertEqual(stopping["state"], "STOPPING")
        self.assertTrue(stopping["active"])
        log_directory = self.logs / "capture"
        log_directory.mkdir()
        (log_directory / run["logId"]).write_text(
            "=== Jetson pipeline run finished ===\n"
            "finished_at=2026-09-14T10:00:00Z\n"
            "exit_code=0\nterminal_state=COMPLETED\nstop_signal=\n"
        )
        self.pipelines.state = "STOPPED"
        self.pipelines.active_run_id = None
        observed = self.service.get_run(run["runId"])
        self.assertEqual(observed["state"], "STOPPED")
        self.assertEqual(observed["stopReason"], "OPERATOR")

    def test_pipeline_user_permission_is_checked_instead_of_api_root(self):
        self.output.chmod(0o500)
        policy = self.configure_policy()
        preflight = self.preflight(policy)
        self.assertFalse(preflight["ready"])
        self.assertEqual(preflight["checks"]["storage"]["state"], "UNAVAILABLE")

    def test_pipeline_user_must_be_able_to_traverse_output_ancestors(self):
        policy = self.configure_policy()
        self.output.chmod(0o777)
        self.service.pipeline_user = "nobody"
        preflight = self.preflight(policy)
        self.assertFalse(preflight["ready"])
        self.assertEqual(preflight["checks"]["storage"]["state"], "UNAVAILABLE")

    def test_untrusted_sidecar_without_root_record_is_rejected(self):
        directory = self.output / "untrusted"
        directory.mkdir()
        (directory / ".jetson-output-context.json").write_text("{}")
        with self.assertRaises(RunContextConflict) as conflict:
            self.service.expected_context_for_source("recordings", "untrusted")
        self.assertEqual(conflict.exception.code, "UNTRUSTED_OUTPUT_CONTEXT")

    def test_active_output_and_its_ancestor_are_locked_from_storage_mutation(self):
        policy = self.configure_policy()
        run = self.start(policy, self.preflight(policy))["run"]
        operation = Mock()

        with self.assertRaises(RunContextConflict) as conflict:
            self.service.mutate_source(
                "recordings",
                "",
                operation,
                "recordings",
                "",
            )

        self.assertEqual(conflict.exception.code, "ACTIVE_RUN_OUTPUT_LOCKED")
        self.assertEqual(conflict.exception.current["runId"], run["runId"])
        operation.assert_not_called()

    def test_linked_upload_requires_terminal_run_and_final_manifest(self):
        policy = self.configure_policy()
        run = self.start(policy, self.preflight(policy))["run"]
        relative_output = str(run["output"]["path"])
        operation = Mock(return_value={"id": "upload-1"})

        with self.assertRaises(RunContextConflict) as conflict:
            self.service.mutate_source(
                "recordings",
                relative_output,
                operation,
                "recordings",
                relative_output,
                require_final_context=True,
            )
        self.assertEqual(conflict.exception.code, "ACTIVE_RUN_OUTPUT_LOCKED")

        output_directory = Path(run["outputDirectory"])
        (output_directory / "new.jsonl").write_text("new", encoding="utf-8")
        log_directory = self.logs / "capture"
        log_directory.mkdir()
        (log_directory / run["logId"]).write_text(
            "=== Jetson pipeline run finished ===\n"
            "finished_at=2026-09-14T10:00:00Z\n"
            "exit_code=0\nterminal_state=COMPLETED\nstop_signal=\n",
            encoding="utf-8",
        )
        self.pipelines.state = "STOPPED"
        self.pipelines.active_run_id = None

        result = self.service.mutate_source(
            "recordings",
            relative_output,
            operation,
            "recordings",
            relative_output,
            require_final_context=True,
        )

        self.assertEqual(result, {"id": "upload-1"})
        self.assertEqual(
            operation.call_args.kwargs["expected_context"]["runId"],
            run["runId"],
        )


if __name__ == "__main__":
    unittest.main()
