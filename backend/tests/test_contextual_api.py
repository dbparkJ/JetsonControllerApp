import json
import os
import pwd
import tempfile
import time
import unittest
from pathlib import Path
from unittest.mock import Mock

from fastapi.testclient import TestClient

from jetson_control.api import create_app
from jetson_control.auth import RequestAuthenticator, sign_request
from jetson_control.config import DeviceConfig, RuntimePaths
from jetson_control.filesystem import StorageRegistry, WorkspaceRegistry
from jetson_control.mobile_rtk import MobileRtkRelayRegistry
from jetson_control.run_context import RunContextService
from jetson_control.sensors import SensorBridgeStore
from jetson_control.survey_context import SurveyContextStore
from jetson_control.uploads import UploadManager


class FakePipelines:
    def __init__(self, registry: Path, output_root: Path, time_marker: Path) -> None:
        self.registry = registry
        self.output_root = output_root.resolve()
        self.pipeline_user = pwd.getpwuid(os.geteuid()).pw_name
        self.time_sync_marker = time_marker
        self.time_sync_marker_owner_uid = os.geteuid()
        self.state = "STOPPED"
        self.active_run_id = None
        self.control_calls = []
        self.stop_observed_state = None
        self.run_context = None

    def runtime_identity(self, pipeline_id):
        if pipeline_id != "capture":
            raise ValueError("unknown pipeline")
        return {
            "pipelineId": pipeline_id,
            "label": "Capture",
            "sourceRevision": "a" * 40,
            "sourceDirty": False,
            "release": "/opt/jetson-pipelines/capture/releases/fixture",
            "config": "config.yaml",
            "configSha256": "b" * 64,
            "writablePaths": [str(self.output_root)],
        }

    def assert_output_allowed(self, pipeline_id, output_directory):
        if pipeline_id != "capture":
            raise ValueError("unknown pipeline")
        Path(output_directory).resolve().relative_to(self.output_root)

    def _response(self):
        return {
            "id": "capture",
            "label": "Capture",
            "state": self.state,
            "activeRunId": self.active_run_id,
            "writablePaths": [str(self.output_root)],
        }

    def status(self, pipeline_id):
        if pipeline_id != "capture":
            raise ValueError("unknown pipeline")
        return self._response()

    get = status

    def control(self, pipeline_id, action):
        self.control_calls.append(action)
        if action == "start":
            launch = json.loads(
                (self.registry / pipeline_id / "next-run.json").read_text(encoding="utf-8")
            )
            self.state = "RUNNING"
            self.active_run_id = launch["runId"]
        elif action == "stop":
            active = self.run_context._read_record(self.active_run_id)
            self.stop_observed_state = active["state"]
            self.state = "STOPPED"
            self.active_run_id = None
        return self._response()


class ContextualApiContractTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.base = Path(self.temporary.name)
        self.output = self.base / "collections"
        self.output.mkdir()
        self.registry = self.base / "registry"
        (self.registry / "capture").mkdir(parents=True)
        self.logs = self.base / "logs"
        self.logs.mkdir()
        self.bridge = self.base / "sensors"
        self.bridge.mkdir()
        self.now = time.time()
        now_millis = int(self.now * 1000)
        (self.bridge / "status.json").write_text(json.dumps({
            "schemaVersion": 1,
            "updatedAtEpochMillis": now_millis,
            "pipeline": {"active": True},
            "camera": {
                "configured": True, "connected": True, "active": True,
                "lastFrameAtEpochMillis": now_millis,
            },
            "gnss": {
                "configured": True, "connected": True, "active": True,
                "lastSampleAtEpochMillis": now_millis,
                "latitude": 37.0, "longitude": 127.0,
            },
            "imu": {
                "configured": True, "connected": True, "active": True,
                "lastSampleAtEpochMillis": now_millis,
            },
        }), encoding="utf-8")
        self.time_marker = self.base / "time-synchronized.json"
        self.time_marker.write_text(json.dumps({
            "schemaVersion": 1,
            "synchronized": True,
            "source": "MOBILE",
            "sourceTimeEpochMillis": now_millis,
            "synchronizedAtEpochMillis": now_millis,
            "offsetBeforeMillis": 0,
        }), encoding="utf-8")
        self.time_marker.chmod(0o600)
        roots_path = self.base / "roots.json"
        roots_path.write_text(json.dumps({
            "collections": {"label": "Collections", "path": str(self.output)}
        }), encoding="utf-8")
        targets_path = self.base / "targets.json"
        targets_path.write_text(json.dumps({
            "archive": {
                "label": "Archive",
                "type": "local",
                "path": str(self.base / "archive"),
            }
        }), encoding="utf-8")
        self.paths = RuntimePaths(
            device_config=self.base / "device.json",
            storage_roots=roots_path,
            upload_targets=targets_path,
            tls_certificate=self.base / "unused.crt",
            state_dir=self.base / "state",
            pipeline_registry=self.registry,
            pipeline_registrar=self.base / "register-pipeline.py",
            pipeline_logs=self.logs,
            sensor_bridge_dir=self.bridge,
            mobile_rtk_relay=self.base / "mobile-rtk.json",
        )
        self.config = DeviceConfig(
            device_id="00000000-0000-0000-0000-000000000001",
            device_name="Context Fixture",
            bootstrap_secret=bytes(range(32)),
            controlled_services=(),
            service_flags={"camera": "", "lidar": "", "gnss": "", "mms": ""},
            allow_power_commands=False,
            wifi_interface="wlan0",
            pipeline_user=pwd.getpwuid(os.geteuid()).pw_name,
        )
        self.request_timestamp = str(int(self.now))
        self.auth = RequestAuthenticator(
            self.config,
            boot_nonce="context-fixture-boot",
            clock=lambda: int(self.request_timestamp),
        )
        storage = StorageRegistry(roots_path)
        survey = SurveyContextStore(
            self.paths.state_dir / "survey-context.json", clock=lambda: self.now
        )
        self.pipelines = FakePipelines(self.registry, self.output, self.time_marker)
        self.run_context = RunContextService(
            state_dir=self.paths.state_dir,
            registry_root=self.registry,
            logs_root=self.logs,
            device_id=self.config.device_id,
            pipeline_user=self.config.pipeline_user,
            pipelines=self.pipelines,
            survey=survey,
            sensor_bridge=SensorBridgeStore(self.bridge),
            storage=storage,
            time_sync_marker=self.time_marker,
            time_sync_owner_uid=os.geteuid(),
            clock=lambda: self.now,
            boot_id=lambda: "fixture-boot-id",
        )
        self.pipelines.run_context = self.run_context
        status = Mock()
        status.collect.return_value = {
            "cpuPercent": 0, "gpuPercent": 0, "ramUsedMb": 0,
            "ramTotalMb": 1, "temperatureC": 0, "storagePercent": 0,
            "cameraRunning": False, "lidarRunning": False,
            "gnssRunning": False, "mmsRunning": False,
        }
        wifi = Mock()
        wifi.status.return_value = {"state": "IDLE", "ssid": None, "message": None}
        system_time = Mock()
        system_time.status.return_value = {"synchronized": True}
        fan = Mock()
        fan.status.return_value = {"available": False, "mode": "UNAVAILABLE"}
        uploads = UploadManager(
            storage, targets_path, self.paths.state_dir, self.config.device_id,
            allow_local_targets=True,
        )
        app = create_app(
            paths=self.paths,
            config=self.config,
            authenticator=self.auth,
            status_collector=status,
            command_runner=Mock(),
            storage=storage,
            workspace_storage=WorkspaceRegistry(self.base),
            upload_manager=uploads,
            survey_store=survey,
            run_context_service=self.run_context,
            wifi_provisioner=wifi,
            pipeline_manager=self.pipelines,
            mobile_rtk_registry=MobileRtkRelayRegistry(
                self.paths.mobile_rtk_relay, owner_uid=os.geteuid()
            ),
            time_synchronizer=system_time,
            fan_controller=fan,
            tls_fingerprint="c" * 64,
        )
        self.client = TestClient(app, client=("192.168.10.20", 50000))
        self.client.__enter__()
        self.nonce = 0

    def tearDown(self):
        self.client.__exit__(None, None, None)
        self.temporary.cleanup()

    def signed(self, method, path, value=None):
        body = b"" if value is None else json.dumps(
            value, separators=(",", ":")
        ).encode("utf-8")
        self.nonce += 1
        nonce = "context-request-%04d" % self.nonce
        signature = sign_request(
            self.config.bootstrap_secret,
            self.config.device_id,
            self.auth.boot_nonce,
            nonce,
            self.request_timestamp,
            method,
            path,
            body,
        )
        return self.client.request(method, path, content=body or None, headers={
            "Content-Type": "application/json",
            "X-Device-Id": self.config.device_id,
            "X-Request-Nonce": nonce,
            "X-Request-Timestamp": self.request_timestamp,
            "X-Signature": signature,
        })

    def create_context(self):
        project = self.signed("POST", "/v1/survey/projects", {
            "label": "Road Survey", "clientRequestId": "project-request-0001",
        })
        self.assertEqual(project.status_code, 201, project.text)
        section_path = "/v1/survey/projects/%s/sections" % project.json()["surveyProjectId"]
        section = self.signed("POST", section_path, {
            "label": "North", "clientRequestId": "section-request-0001",
        })
        self.assertEqual(section.status_code, 201, section.text)
        return project.json(), section.json()

    def configure_policy(self):
        response = self.signed("PUT", "/v1/pipelines/capture/run-policy", {
            "requiredSensors": ["camera", "gnss"],
            "optionalSensors": ["imu"],
            "minFreeBytes": 0,
            "outputRootId": "collections",
            "outputPath": "",
            "expectedOutput": {"minFiles": 0, "minBytes": 0, "patterns": []},
            "expectedRevision": None,
            "clientRequestId": "policy-request-0001",
        })
        self.assertEqual(response.status_code, 200, response.text)
        return response.json()

    def test_survey_crud_revision_and_structured_conflict(self):
        project, section = self.create_context()
        projects = self.signed("GET", "/v1/survey/projects")
        sections = self.signed(
            "GET", "/v1/survey/projects/%s/sections" % project["surveyProjectId"]
        )
        self.assertEqual(projects.json()["projects"], [project])
        self.assertEqual(sections.json()["sections"], [section])

        path = "/v1/survey/projects/%s" % project["surveyProjectId"]
        conflict = self.signed("PUT", path, {
            "label": "Wrong revision", "expectedRevision": 99,
        })
        self.assertEqual(conflict.status_code, 409)
        self.assertEqual(conflict.json()["detail"]["code"], "REVISION_MISMATCH")
        self.assertEqual(conflict.json()["detail"]["current"]["revision"], 1)
        updated = self.signed("PUT", path, {
            "label": "Road Survey 2", "expectedRevision": 1,
        })
        self.assertEqual(updated.json()["revision"], 2)

        extra = self.signed("POST", "/v1/survey/projects", {
            "label": "Delete Me", "clientRequestId": "project-request-0002",
        }).json()
        deleted = self.signed(
            "DELETE", "/v1/survey/projects/%s" % extra["surveyProjectId"],
            {"expectedRevision": 1},
        )
        self.assertEqual(deleted.json(), {
            "deleted": True, "surveyProjectId": extra["surveyProjectId"],
        })

    def test_policy_preflight_flat_start_slash_get_and_legacy_lock(self):
        project, section = self.create_context()
        policy = self.configure_policy()
        replay = self.configure_policy()
        self.assertEqual(replay, policy)

        legacy = self.signed("POST", "/v1/pipelines/capture/start")
        self.assertEqual(legacy.status_code, 409)
        self.assertEqual(legacy.json()["detail"]["code"], "CONTEXT_REQUIRED")
        self.assertEqual(self.pipelines.control_calls, [])

        request = {
            "surveyProjectId": project["surveyProjectId"],
            "surveySectionId": section["surveySectionId"],
            "surveyProjectRevision": project["revision"],
            "surveySectionRevision": section["revision"],
            "policyRevision": policy["revision"],
        }
        preflight = self.signed(
            "POST", "/v1/pipelines/capture/preflight", request
        )
        self.assertEqual(preflight.status_code, 200, preflight.text)
        self.assertTrue(preflight.json()["ready"])
        self.assertEqual(preflight.json()["bootId"], "fixture-boot-id")
        start_request = {
            **request,
            "preflightId": preflight.json()["preflightId"],
            "clientRequestId": "start-request-0001",
        }
        started = self.signed(
            "POST", "/v1/pipelines/capture/contextual-start", start_request
        )
        self.assertEqual(started.status_code, 200, started.text)
        value = started.json()
        receipt = value["contextualStart"]
        self.assertNotIn("run", receipt)
        self.assertEqual(receipt["outcome"], "START_ACCEPTED")
        self.assertEqual(receipt["uploadContext"]["runId"], receipt["runId"])
        self.assertEqual(receipt["statusUrl"], "/v1/pipeline-runs/" + receipt["runId"])

        observed = self.signed("GET", receipt["statusUrl"])
        self.assertEqual(observed.status_code, 200, observed.text)
        self.assertEqual(observed.json()["runId"], receipt["runId"])
        self.assertEqual(observed.json()["state"], "RUNNING")
        self.assertEqual(observed.json()["uploadContext"], receipt["uploadContext"])

        stopped = self.signed(
            "POST",
            "/v1/pipelines/capture/contextual-stop",
            {"expectedRunId": receipt["runId"]},
        )
        self.assertEqual(stopped.status_code, 200, stopped.text)
        self.assertEqual(self.pipelines.stop_observed_state, "STOPPING")
        self.assertEqual(self.pipelines.control_calls, ["start", "stop"])

    def test_contextual_stop_rejects_stale_run_without_stopping_successor(self):
        project, section = self.create_context()
        policy = self.configure_policy()
        request = {
            "surveyProjectId": project["surveyProjectId"],
            "surveySectionId": section["surveySectionId"],
            "surveyProjectRevision": project["revision"],
            "surveySectionRevision": section["revision"],
            "policyRevision": policy["revision"],
        }

        first_preflight = self.signed(
            "POST", "/v1/pipelines/capture/preflight", request
        ).json()
        first = self.signed(
            "POST",
            "/v1/pipelines/capture/contextual-start",
            {
                **request,
                "preflightId": first_preflight["preflightId"],
                "clientRequestId": "start-request-stale-0001",
            },
        ).json()["contextualStart"]

        self.pipelines.active_run_id = "capture/untracked-successor.log"
        runtime_mismatch = self.signed(
            "POST",
            "/v1/pipelines/capture/contextual-stop",
            {"expectedRunId": first["runId"]},
        )
        self.assertEqual(runtime_mismatch.status_code, 409, runtime_mismatch.text)
        self.assertEqual(
            runtime_mismatch.json()["detail"]["code"], "ACTIVE_RUN_MISMATCH"
        )
        self.assertEqual(self.pipelines.state, "RUNNING")
        self.assertEqual(self.pipelines.control_calls, ["start"])
        self.pipelines.active_run_id = first["runId"]

        pipeline_id, log_id = first["runId"].split("/", 1)
        log_directory = self.logs / pipeline_id
        log_directory.mkdir(exist_ok=True)
        (log_directory / log_id).write_text(
            "=== Jetson pipeline run finished ===\n"
            "finished_at=2026-09-14T10:00:00Z\n"
            "exit_code=0\nterminal_state=COMPLETED\nstop_signal=\n",
            encoding="utf-8",
        )
        self.pipelines.state = "STOPPED"
        self.pipelines.active_run_id = None
        terminal = self.signed("GET", first["statusUrl"])
        self.assertEqual(terminal.json()["state"], "COMPLETED")

        second_preflight = self.signed(
            "POST", "/v1/pipelines/capture/preflight", request
        ).json()
        second = self.signed(
            "POST",
            "/v1/pipelines/capture/contextual-start",
            {
                **request,
                "preflightId": second_preflight["preflightId"],
                "clientRequestId": "start-request-stale-0002",
            },
        ).json()["contextualStart"]
        self.assertNotEqual(first["runId"], second["runId"])

        stale_stop = self.signed(
            "POST",
            "/v1/pipelines/capture/contextual-stop",
            {"expectedRunId": first["runId"]},
        )
        self.assertEqual(stale_stop.status_code, 409, stale_stop.text)
        self.assertEqual(
            stale_stop.json()["detail"]["code"], "ACTIVE_RUN_MISMATCH"
        )
        self.assertEqual(self.pipelines.state, "RUNNING")
        self.assertEqual(self.pipelines.active_run_id, second["runId"])
        self.assertEqual(self.pipelines.control_calls, ["start", "start"])

        current_stop = self.signed(
            "POST",
            "/v1/pipelines/capture/contextual-stop",
            {"expectedRunId": second["runId"]},
        )
        self.assertEqual(current_stop.status_code, 200, current_stop.text)
        self.assertEqual(self.pipelines.stop_observed_state, "STOPPING")
        self.assertEqual(self.pipelines.control_calls, ["start", "start", "stop"])

    def test_linked_upload_requires_terminal_run_and_trusted_source(self):
        project, section = self.create_context()
        policy = self.configure_policy()
        request = {
            "surveyProjectId": project["surveyProjectId"],
            "surveySectionId": section["surveySectionId"],
            "surveyProjectRevision": project["revision"],
            "surveySectionRevision": section["revision"],
            "policyRevision": policy["revision"],
        }
        preflight = self.signed(
            "POST", "/v1/pipelines/capture/preflight", request
        ).json()
        started = self.signed(
            "POST", "/v1/pipelines/capture/contextual-start", {
                **request,
                "preflightId": preflight["preflightId"],
                "clientRequestId": "start-request-0002",
            },
        ).json()["contextualStart"]
        upload_request = {
            "rootId": started["output"]["rootId"],
            "relativePath": started["output"]["path"],
            "targetId": "archive",
            "context": started["uploadContext"],
        }
        active = self.signed("POST", "/v1/uploads", upload_request)
        self.assertEqual(active.status_code, 409, active.text)
        self.assertEqual(
            active.json()["detail"]["code"], "ACTIVE_RUN_OUTPUT_LOCKED"
        )

        output_directory = self.output / started["output"]["path"]
        (output_directory / "observations.jsonl").write_text("{}\n", encoding="utf-8")
        pipeline_id, log_id = started["runId"].split("/", 1)
        log_directory = self.logs / pipeline_id
        log_directory.mkdir(exist_ok=True)
        (log_directory / log_id).write_text(
            "=== Jetson pipeline run finished ===\n"
            "finished_at=2026-09-14T10:00:00Z\n"
            "exit_code=0\nterminal_state=COMPLETED\nstop_signal=\n",
            encoding="utf-8",
        )
        self.pipelines.state = "STOPPED"
        self.pipelines.active_run_id = None
        terminal = self.signed("GET", started["statusUrl"])
        self.assertEqual(terminal.json()["state"], "COMPLETED")
        self.assertEqual(terminal.json()["output"]["manifestState"], "FINAL")

        accepted = self.signed("POST", "/v1/uploads", upload_request)
        self.assertEqual(accepted.status_code, 202, accepted.text)
        self.assertEqual(accepted.json()["context"], started["uploadContext"])
        job_id = accepted.json()["id"]
        for _ in range(100):
            job = self.signed("GET", "/v1/uploads/" + job_id)
            if job.json()["state"] in {"COMPLETED", "FAILED", "CANCELLED"}:
                break
            time.sleep(0.01)
        self.assertEqual(job.json()["state"], "COMPLETED", job.text)


if __name__ == "__main__":
    unittest.main()
