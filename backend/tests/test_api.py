import json
import tempfile
import time
import unittest
from pathlib import Path
from unittest.mock import Mock

from fastapi.testclient import TestClient

from jetson_control.api import create_app
from jetson_control.auth import (
    RequestAuthenticator,
    sign_hello,
    sign_request,
    sign_response,
)
from jetson_control.config import DeviceConfig, RuntimePaths
from jetson_control.filesystem import StorageRegistry, WorkspaceRegistry
from jetson_control.uploads import UploadManager


class ApiContractTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory()
        base = Path(self.temporary.name)
        self.base = base
        source = base / "source"
        source.mkdir()
        (source / "hello world.txt").write_text("hello", encoding="utf-8")
        destination = base / "destination"

        roots_path = base / "roots.json"
        roots_path.write_text(
            json.dumps({"data": {"label": "Data", "path": str(source)}}),
            encoding="utf-8",
        )
        targets_path = base / "targets.json"
        targets_path.write_text(
            json.dumps(
                {
                    "archive": {
                        "label": "Archive",
                        "type": "local",
                        "path": str(destination),
                    }
                }
            ),
            encoding="utf-8",
        )
        self.paths = RuntimePaths(
            device_config=base / "unused-device.json",
            storage_roots=roots_path,
            upload_targets=targets_path,
            state_dir=base / "state",
        )
        self.config = DeviceConfig(
            device_id="00000000-0000-0000-0000-000000000001",
            device_name="MMS-TEST",
            bootstrap_secret=bytes(range(32)),
            controlled_services=(),
            service_flags={"camera": "", "lidar": "", "gnss": "", "mms": ""},
            allow_power_commands=True,
            wifi_interface="wlan0",
        )
        self.request_timestamp = "1700000000"
        self.auth = RequestAuthenticator(
            self.config,
            boot_nonce="test-boot",
            clock=lambda: int(self.request_timestamp),
        )
        self.tls_fingerprint = "a" * 64
        storage = StorageRegistry(roots_path)
        uploads = UploadManager(
            storage,
            targets_path,
            self.paths.state_dir,
            self.config.device_id,
            allow_local_targets=True,
        )
        status_collector = Mock()
        status_collector.collect.return_value = {
            "cpuPercent": 12,
            "gpuPercent": 3,
            "ramUsedMb": 100,
            "ramTotalMb": 1000,
            "temperatureC": 42.5,
            "storagePercent": 50,
            "cameraRunning": False,
            "lidarRunning": False,
            "gnssRunning": False,
            "mmsRunning": False,
        }
        command_runner = Mock()
        command_runner.execute.return_value = {"accepted": True}
        wifi = Mock()
        wifi.submit.return_value = {"accepted": True, "state": "CONNECTING"}
        wifi.status.return_value = {"state": "IDLE", "ssid": None, "message": None}
        self.pipelines = Mock()
        self.pipelines.list_pipelines.return_value = []
        self.pipelines.register.return_value = {
            "id": "capture",
            "label": "Capture",
            "state": "STOPPED",
        }
        self.pipelines.control.return_value = {
            "id": "capture",
            "label": "Capture",
            "state": "RUNNING",
        }
        self.pipelines.logs.return_value = {
            "pipelineId": "capture",
            "lines": ["capture started"],
        }
        self.pipelines.config_document.return_value = {
            "pipelineId": "capture",
            "path": "config.yaml",
            "content": "fps: 30\n",
        }
        self.pipelines.update_config.return_value = {
            "pipelineId": "capture",
            "path": "config.yaml",
            "content": "fps: 15\n",
        }

        app = create_app(
            paths=self.paths,
            config=self.config,
            authenticator=self.auth,
            status_collector=status_collector,
            command_runner=command_runner,
            storage=storage,
            workspace_storage=WorkspaceRegistry(base),
            upload_manager=uploads,
            wifi_provisioner=wifi,
            pipeline_manager=self.pipelines,
            tls_fingerprint=self.tls_fingerprint,
        )
        self.client = TestClient(app)
        self.nonce_counter = 0

    def tearDown(self) -> None:
        self.temporary.cleanup()

    def signed_request(self, method: str, path: str, body: bytes = b""):
        self.nonce_counter += 1
        nonce = f"request-{self.nonce_counter:04d}"
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
        return self.client.request(
            method,
            path,
            content=body or None,
            headers={
                "Content-Type": "application/json",
                "X-Device-Id": self.config.device_id,
                "X-Request-Nonce": nonce,
                "X-Request-Timestamp": self.request_timestamp,
                "X-Signature": signature,
            },
        )

    def test_hello_and_authenticated_status(self) -> None:
        hello = self.client.get("/v1/hello")
        self.assertEqual(hello.status_code, 200)
        self.assertEqual(hello.json()["bootNonce"], "test-boot")
        hello_body = hello.json()
        self.assertEqual(hello_body["tlsCertificateSha256"], self.tls_fingerprint)
        self.assertEqual(
            hello_body["helloProof"],
            sign_hello(
                self.config.bootstrap_secret,
                hello_body["apiVersion"],
                self.config.device_id,
                self.config.device_name,
                self.auth.boot_nonce,
                hello_body["serverTimeEpochSeconds"],
                hello_body["authScheme"],
                self.tls_fingerprint,
            ),
        )
        self.assertEqual(self.client.get("/v1/status").status_code, 401)

        response = self.signed_request("GET", "/v1/status")
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json()["temperatureC"], 42.5)
        expected_response_signature = sign_response(
            self.config.bootstrap_secret,
            self.config.device_id,
            self.auth.boot_nonce,
            "request-0001",
            self.request_timestamp,
            response.status_code,
            response.content,
        )
        self.assertEqual(
            response.headers["X-Response-Signature"],
            expected_response_signature,
        )

    def test_file_query_is_part_of_signature(self) -> None:
        path = "/v1/fs/list?root=data&path="
        response = self.signed_request("GET", path)
        self.assertEqual(response.status_code, 200, response.text)
        self.assertEqual(response.json()["entries"][0]["name"], "hello world.txt")

    def test_authenticated_unknown_v1_route_returns_signed_404(self) -> None:
        response = self.signed_request("GET", "/v1/not-supported")

        self.assertEqual(response.status_code, 404)
        expected_signature = sign_response(
            self.config.bootstrap_secret,
            self.config.device_id,
            self.auth.boot_nonce,
            "request-0001",
            self.request_timestamp,
            response.status_code,
            response.content,
        )
        self.assertEqual(
            response.headers["X-Response-Signature"],
            expected_signature,
        )

    def test_unhandled_api_error_returns_signed_500(self) -> None:
        self.pipelines.list_pipelines.side_effect = RuntimeError("simulated failure")

        with self.assertLogs("jetson_control.api", level="ERROR"):
            response = self.signed_request("GET", "/v1/pipelines")

        self.assertEqual(response.status_code, 500)
        self.assertEqual(response.json()["detail"], "Jetson backend internal error")
        expected_signature = sign_response(
            self.config.bootstrap_secret,
            self.config.device_id,
            self.auth.boot_nonce,
            "request-0001",
            self.request_timestamp,
            response.status_code,
            response.content,
        )
        self.assertEqual(
            response.headers["X-Response-Signature"],
            expected_signature,
        )

    def test_authenticated_file_and_workspace_access(self) -> None:
        file_path = "/v1/fs/file?root=data&path=hello%20world.txt"
        response = self.signed_request("GET", file_path)
        self.assertEqual(response.status_code, 200, response.text)
        self.assertEqual(response.content, b"hello")

        roots = self.signed_request("GET", "/v1/fs/workspaces")
        self.assertEqual(roots.status_code, 200, roots.text)
        self.assertEqual(roots.json()[0]["id"], "workspace-home")

        listing_path = "/v1/fs/workspace/list?root=workspace-home&path="
        listing = self.signed_request("GET", listing_path)
        self.assertEqual(listing.status_code, 200, listing.text)
        self.assertIn("source", [entry["name"] for entry in listing.json()["entries"]])

    def test_wifi_direct_status_requires_authentication(self) -> None:
        path = "/v1/network/wifi-direct/status"
        self.assertEqual(self.client.get(path).status_code, 401)
        response = self.signed_request("GET", path)
        self.assertEqual(response.status_code, 200, response.text)
        self.assertIn(
            response.json()["state"],
            {
                "UNAVAILABLE",
                "STARTING",
                "DISCOVERABLE",
                "CONNECTING",
                "READY",
                "STOPPED",
                "DISABLED",
                "ERROR",
            },
        )

    def test_upload_request_uses_android_field_names(self) -> None:
        body = json.dumps(
            {"rootId": "data", "relativePath": "", "targetId": "archive"},
            separators=(",", ":"),
        ).encode("utf-8")
        response = self.signed_request("POST", "/v1/uploads", body)
        self.assertEqual(response.status_code, 202, response.text)
        self.assertEqual(response.json()["rootId"], "data")
        job_id = response.json()["id"]
        deadline = time.monotonic() + 5
        while time.monotonic() < deadline:
            job = self.client.app.state.upload_manager.get(job_id)
            if job["state"] in {"COMPLETED", "FAILED", "CANCELLED"}:
                break
            time.sleep(0.02)
        self.assertEqual(job["state"], "COMPLETED", job)

    def test_upload_target_management_and_active_queue(self) -> None:
        body = json.dumps(
            {
                "label": "Field receiver",
                "baseUrl": "https://upload.example.com/v1",
                "token": "server-token",
            },
            separators=(",", ":"),
        ).encode("utf-8")
        created = self.signed_request(
            "PUT",
            "/v1/upload/targets/field-server",
            body,
        )
        self.assertEqual(created.status_code, 200, created.text)
        self.assertEqual(created.json()["baseUrl"], "https://upload.example.com/v1")
        self.assertTrue(created.json()["editable"])
        self.assertNotIn("token", created.json())

        targets = self.signed_request("GET", "/v1/upload/targets")
        self.assertEqual(targets.status_code, 200, targets.text)
        self.assertEqual(
            {target["id"] for target in targets.json()},
            {"archive", "field-server"},
        )

        active = self.signed_request("GET", "/v1/uploads?active=true")
        self.assertEqual(active.status_code, 200, active.text)
        self.assertEqual(active.json(), [])

        deleted = self.signed_request(
            "DELETE",
            "/v1/upload/targets/field-server",
        )
        self.assertEqual(deleted.status_code, 204, deleted.text)

    def test_pipeline_registration_resolves_paths_inside_storage_root(self) -> None:
        body = json.dumps(
            {
                "id": "capture",
                "label": "Capture",
                "repositoryRootId": "data",
                "repositoryPath": "project",
                "virtualenvRootId": "data",
                "virtualenvPath": ".venv",
                "entrypoint": "main.py",
                "config": "config.yaml",
                "workingDirectory": ".",
                "writableDirectories": ["records"],
                "autostart": True,
            },
            separators=(",", ":"),
        ).encode("utf-8")
        response = self.signed_request("POST", "/v1/pipelines", body)
        self.assertEqual(response.status_code, 201, response.text)
        source = Path(self.temporary.name) / "source"
        self.pipelines.register.assert_called_once_with(
            pipeline_id="capture",
            label="Capture",
            repository=source / "project",
            virtualenv=source / ".venv",
            entrypoint="main.py",
            config="config.yaml",
            working_directory=source / "project",
            writable_paths=[source / "project" / "records"],
            autostart=True,
        )

    def test_pipeline_control_and_remove(self) -> None:
        response = self.signed_request("POST", "/v1/pipelines/capture/start", b"{}")
        self.assertEqual(response.status_code, 200, response.text)
        self.pipelines.control.assert_called_once_with("capture", "start")

        response = self.signed_request("DELETE", "/v1/pipelines/capture")
        self.assertEqual(response.status_code, 204, response.text)
        self.pipelines.remove.assert_called_once_with("capture")

    def test_pipeline_logs_and_yaml_config(self) -> None:
        logs = self.signed_request("GET", "/v1/pipelines/capture/logs?lines=300")
        self.assertEqual(logs.status_code, 200, logs.text)
        self.assertEqual(logs.json()["lines"], ["capture started"])
        self.pipelines.logs.assert_called_once_with("capture", 300)

        config = self.signed_request("GET", "/v1/pipelines/capture/config")
        self.assertEqual(config.status_code, 200, config.text)
        self.assertEqual(config.json()["content"], "fps: 30\n")

        body = json.dumps({"content": "fps: 15\n"}, separators=(",", ":")).encode()
        updated = self.signed_request("PUT", "/v1/pipelines/capture/config", body)
        self.assertEqual(updated.status_code, 200, updated.text)
        self.pipelines.update_config.assert_called_once_with("capture", "fps: 15\n")

    def test_pipeline_path_traversal_is_rejected(self) -> None:
        body = json.dumps(
            {
                "id": "capture",
                "label": "Capture",
                "repositoryRootId": "data",
                "repositoryPath": "project",
                "virtualenvRootId": "data",
                "virtualenvPath": ".venv",
                "entrypoint": "../main.py",
                "config": "config.yaml",
            },
            separators=(",", ":"),
        ).encode("utf-8")
        response = self.signed_request("POST", "/v1/pipelines", body)
        self.assertEqual(response.status_code, 400, response.text)
        self.pipelines.register.assert_not_called()


if __name__ == "__main__":
    unittest.main()
