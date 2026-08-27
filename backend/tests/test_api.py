import json
import os
import tempfile
import threading
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
from jetson_control.mobile_rtk import MobileRtkRelayRegistry
from jetson_control.uploads import UploadConfirmationRequired, UploadManager


class ApiContractTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory()
        base = Path(self.temporary.name)
        self.base = base
        source = base / "source"
        source.mkdir()
        (source / "hello world.txt").write_text("hello", encoding="utf-8")
        destination = base / "destination"
        sensor_bridge = base / "sensors"
        sensor_bridge.mkdir()

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
            sensor_bridge_dir=sensor_bridge,
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
        self.uploads = uploads
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
        self.pipelines.log_files.return_value = {
            "pipelineId": "capture",
            "files": [
                {
                    "id": "run-20260814T000000.000001Z-100.log",
                    "startedAt": "2026-08-14T00:00:00.000001Z",
                    "modifiedAt": "2026-08-14T00:00:01Z",
                    "sizeBytes": 15,
                    "active": True,
                }
            ],
        }
        self.pipelines.read_log_file.return_value = {
            "pipelineId": "capture",
            "logId": "run-20260814T000000.000001Z-100.log",
            "content": "capture started",
            "offset": 0,
            "nextOffset": 15,
            "sizeBytes": 15,
            "modifiedAt": "2026-08-14T00:00:01Z",
            "eof": True,
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
        self.pipelines.config_fields.return_value = {
            "pipelineId": "capture",
            "path": "config.yaml",
            "revision": "a" * 64,
            "fields": [
                {"path": "/fps", "label": "fps", "type": "INTEGER", "value": "30"}
            ],
        }
        self.pipelines.update_config_fields.return_value = {
            "pipelineId": "capture",
            "path": "config.yaml",
            "revision": "b" * 64,
            "fields": [
                {"path": "/fps", "label": "fps", "type": "INTEGER", "value": "15"}
            ],
        }
        self.pipelines.mobile_rtk_config.return_value = {
            "pipelineId": "capture",
            "available": True,
            "upstreamHost": "www.gnssdata.or.kr",
            "upstreamPort": 2101,
        }
        self.pipelines.discover_folder.return_value = {
            "pipelineId": "capture",
            "repository": str(base / "jobs" / "capture"),
            "virtualenv": str(base / "jobs" / "capture" / ".venv"),
            "entrypoint": "main.py",
            "config": "config.yaml",
            "workingDirectory": str(base / "jobs" / "capture"),
            "resultsDirectory": str(base / "jobs" / "capture" / "results"),
            "resultsExists": False,
            "logDirectory": "/var/log/jetson-pipelines/capture",
            "autostartDefault": True,
        }
        self.pipelines.register_folder.return_value = {
            "id": "capture",
            "label": "카메라 수집",
            "state": "WAITING_FOR_TIME_SYNC",
            "writablePaths": [str(base / "jobs" / "capture" / "results")],
        }
        self.system_time = Mock()
        self.system_time.status.return_value = {
            "synchronized": True,
            "deviceTimeEpochMillis": 1777000123456,
        }
        self.system_time.synchronize.return_value = {
            "synchronized": True,
            "deviceTimeEpochMillis": 1777000123456,
            "clockChanged": False,
        }
        self.fan = Mock()
        self.fan.status.return_value = {
            "available": True,
            "mode": "AUTO",
            "percent": 35,
            "minimumManualPercent": 20,
        }
        self.fan.set.return_value = {
            "available": True,
            "mode": "MANUAL",
            "percent": 40,
            "minimumManualPercent": 20,
        }
        self.mobile_rtk = MobileRtkRelayRegistry(
            base / "mobile-rtk-relay.json",
            owner_uid=os.getuid(),
        )

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
            mobile_rtk_registry=self.mobile_rtk,
            time_synchronizer=self.system_time,
            fan_controller=self.fan,
            tls_fingerprint=self.tls_fingerprint,
        )
        self.client = TestClient(app, client=("192.168.10.20", 50000))
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

    def test_authenticated_camera_preview_contract(self) -> None:
        now_millis = int(time.time() * 1000)
        self.paths.sensor_bridge_dir.joinpath("status.json").write_text(
            json.dumps(
                {
                    "schemaVersion": 1,
                    "updatedAtEpochMillis": now_millis,
                    "pipeline": {"active": True},
                    "camera": {
                        "configured": True,
                        "connected": True,
                        "active": True,
                        "previewAvailable": True,
                        "previewUpdatedAtEpochMillis": now_millis,
                    },
                    "gnss": {},
                    "imu": {},
                }
            ),
            encoding="utf-8",
        )
        preview = b"\xff\xd8camera-preview\xff\xd9"
        self.paths.sensor_bridge_dir.joinpath("camera-preview.jpg").write_bytes(preview)

        path = "/v1/camera/preview/frame"
        self.assertEqual(self.client.get(path).status_code, 401)
        response = self.signed_request("GET", path)

        self.assertEqual(response.status_code, 200, response.text)
        self.assertEqual(response.headers["content-type"], "image/jpeg")
        self.assertEqual(response.headers["cache-control"], "no-store")
        self.assertEqual(
            int(response.headers["X-Preview-Revision"]),
            self.paths.sensor_bridge_dir.joinpath("camera-preview.jpg").stat().st_mtime_ns,
        )
        self.assertEqual(response.content, preview)
        self.assertIn("X-Response-Signature", response.headers)

        revision = int(response.headers["X-Preview-Revision"])
        updated_preview = b"\xff\xd8next-camera-preview\xff\xd9"

        def publish_next_frame() -> None:
            time.sleep(0.05)
            temporary = self.paths.sensor_bridge_dir / ".camera-preview.next.jpg"
            temporary.write_bytes(updated_preview)
            os.replace(
                temporary,
                self.paths.sensor_bridge_dir / "camera-preview.jpg",
            )

        publisher = threading.Thread(target=publish_next_frame)
        publisher.start()
        next_path = (
            "/v1/camera/preview/frame"
            f"?afterRevision={revision}&waitMillis=500"
        )
        next_response = self.signed_request("GET", next_path)
        publisher.join(timeout=1)

        self.assertEqual(next_response.status_code, 200, next_response.text)
        self.assertEqual(next_response.content, updated_preview)
        self.assertNotEqual(
            int(next_response.headers["X-Preview-Revision"]),
            revision,
        )

    def test_camera_preview_rejects_stale_sensor_heartbeat(self) -> None:
        self.paths.sensor_bridge_dir.joinpath("status.json").write_text(
            json.dumps(
                {
                    "schemaVersion": 1,
                    "updatedAtEpochMillis": int((time.time() - 10) * 1000),
                    "pipeline": {"active": True},
                    "camera": {
                        "configured": True,
                        "connected": True,
                        "active": True,
                        "previewAvailable": True,
                    },
                    "gnss": {},
                    "imu": {},
                }
            ),
            encoding="utf-8",
        )

        response = self.signed_request("GET", "/v1/camera/preview/frame")

        self.assertEqual(response.status_code, 409)

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

    def test_mobile_rtk_relay_registration_requires_wifi_direct_peer(self) -> None:
        body = json.dumps(
            {"pipelineId": "capture", "port": 32101},
            separators=(",", ":"),
        ).encode()

        denied = self.signed_request("PUT", "/v1/rtk/mobile-relay", body)
        self.assertEqual(denied.status_code, 403, denied.text)

        lan_client = self.client
        p2p_client = TestClient(lan_client.app, client=("192.168.49.71", 50000))
        self.client = p2p_client
        try:
            accepted = self.signed_request("PUT", "/v1/rtk/mobile-relay", body)
            self.assertEqual(accepted.status_code, 200, accepted.text)
            self.assertEqual(accepted.json()["relayHost"], "192.168.49.71")
            self.assertEqual(self.mobile_rtk.read()["pipelineId"], "capture")

            removed = self.signed_request("DELETE", "/v1/rtk/mobile-relay/capture")
            self.assertEqual(removed.status_code, 204, removed.text)
            self.assertIsNone(self.mobile_rtk.read())
        finally:
            self.client = lan_client
            p2p_client.close()

    def test_mobile_rtk_config_does_not_return_ntrip_credentials(self) -> None:
        response = self.signed_request(
            "GET",
            "/v1/rtk/mobile-relay/config/capture",
        )

        self.assertEqual(response.status_code, 200, response.text)
        self.assertEqual(response.json()["upstreamHost"], "www.gnssdata.or.kr")
        self.assertNotIn("password", response.text.lower())

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

    def test_device_storage_entry_delete_requires_confirmation(self) -> None:
        path = "/v1/fs/entry?root=data&path=hello%20world.txt"

        unconfirmed = self.signed_request("DELETE", path, b"{}")
        self.assertEqual(unconfirmed.status_code, 409, unconfirmed.text)
        self.assertTrue((self.base / "source" / "hello world.txt").is_file())

        body = json.dumps({"confirmed": True}, separators=(",", ":")).encode()
        deleted = self.signed_request("DELETE", path, body)
        self.assertEqual(deleted.status_code, 200, deleted.text)
        self.assertEqual(deleted.json()["state"], "DELETED")
        self.assertEqual(deleted.json()["relativePath"], "hello world.txt")
        self.assertFalse((self.base / "source" / "hello world.txt").exists())

        root_path = "/v1/fs/entry?root=data&path="
        rejected_root = self.signed_request("DELETE", root_path, body)
        self.assertEqual(rejected_root.status_code, 409, rejected_root.text)
        self.assertTrue((self.base / "source").is_dir())

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

    def test_upload_start_is_rejected_over_wifi_direct(self) -> None:
        body = json.dumps(
            {"rootId": "data", "relativePath": "", "targetId": "archive"},
            separators=(",", ":"),
        ).encode("utf-8")
        lan_client = self.client
        p2p_client = TestClient(
            lan_client.app,
            client=("192.168.49.20", 50000),
        )
        self.client = p2p_client
        try:
            response = self.signed_request("POST", "/v1/uploads", body)
        finally:
            self.client = lan_client
            p2p_client.close()

        self.assertEqual(response.status_code, 403, response.text)
        self.assertIn("LAN", response.json()["detail"])
        self.assertEqual(self.uploads.list_jobs(), [])

    def test_upload_retry_is_rejected_over_wifi_direct(self) -> None:
        self.uploads.retry = Mock()
        lan_client = self.client
        p2p_client = TestClient(
            lan_client.app,
            client=("192.168.49.20", 50000),
        )
        self.client = p2p_client
        try:
            response = self.signed_request(
                "POST",
                "/v1/uploads/job-over-p2p/retry",
            )
        finally:
            self.client = lan_client
            p2p_client.close()

        self.assertEqual(response.status_code, 403, response.text)
        self.assertIn("LAN", response.json()["detail"])
        self.uploads.retry.assert_not_called()

    def test_upload_source_summary_contract(self) -> None:
        path = "/v1/upload/source-summary?root=data&path="

        response = self.signed_request("GET", path)

        self.assertEqual(response.status_code, 200, response.text)
        self.assertEqual(response.json()["rootId"], "data")
        self.assertEqual(response.json()["relativePath"], "")
        self.assertEqual(response.json()["sourceName"], "source")
        self.assertEqual(response.json()["folderName"], "source")
        self.assertEqual(response.json()["filesTotal"], 1)
        self.assertEqual(response.json()["bytesTotal"], 5)

    def test_completed_upload_verification_contract(self) -> None:
        verification = {
            "jobId": "job-1",
            "targetId": "server",
            "remoteSessionId": "session-1",
            "sourceName": "capture",
            "state": "MISMATCH",
            "matched": False,
            "deletionAllowed": False,
            "bytesTotal": 12,
            "filesTotal": 1,
            "contentSha256": "a" * 64,
            "verifiedAt": "2026-08-21T00:00:00Z",
        }
        self.uploads.verify_completed_source = Mock(return_value=verification)

        response = self.signed_request("POST", "/v1/uploads/job-1/verify")

        self.assertEqual(response.status_code, 200, response.text)
        self.assertEqual(response.json(), verification)
        self.uploads.verify_completed_source.assert_called_once_with("job-1")

    def test_upload_source_delete_requires_confirmation_and_returns_job(self) -> None:
        self.uploads.delete_completed_source = Mock(
            side_effect=UploadConfirmationRequired("confirmation required")
        )

        unconfirmed = self.signed_request(
            "DELETE",
            "/v1/uploads/job-1/source",
            b"{}",
        )

        self.assertEqual(unconfirmed.status_code, 409, unconfirmed.text)
        self.uploads.delete_completed_source.assert_called_once_with(
            "job-1", confirmed=False
        )

        deleted_job = {
            "id": "job-1",
            "state": "COMPLETED",
            "sourceDeleted": True,
        }
        self.uploads.delete_completed_source = Mock(return_value=deleted_job)
        body = json.dumps({"confirmed": True}, separators=(",", ":")).encode()

        confirmed = self.signed_request(
            "DELETE",
            "/v1/uploads/job-1/source",
            body,
        )

        self.assertEqual(confirmed.status_code, 200, confirmed.text)
        self.assertEqual(confirmed.json(), deleted_job)
        self.uploads.delete_completed_source.assert_called_once_with(
            "job-1", confirmed=True
        )

    def test_upload_history_delete_requires_confirmation(self) -> None:
        self.uploads.delete_job = Mock(
            side_effect=UploadConfirmationRequired("confirmation required")
        )

        unconfirmed = self.signed_request("DELETE", "/v1/uploads/job-1", b"{}")

        self.assertEqual(unconfirmed.status_code, 409, unconfirmed.text)
        self.uploads.delete_job.assert_called_once_with("job-1", confirmed=False)

        self.uploads.delete_job = Mock(return_value={"id": "job-1", "state": "DELETED"})
        body = json.dumps({"confirmed": True}, separators=(",", ":")).encode()
        confirmed = self.signed_request("DELETE", "/v1/uploads/job-1", body)

        self.assertEqual(confirmed.status_code, 204, confirmed.text)
        self.assertEqual(confirmed.content, b"")
        self.uploads.delete_job.assert_called_once_with("job-1", confirmed=True)

    def test_upload_library_session_delete_requires_confirmation(self) -> None:
        self.uploads.delete_library_session = Mock(
            side_effect=UploadConfirmationRequired("confirmation required")
        )
        path = "/v1/upload/library/sessions/session-1?target=server"

        unconfirmed = self.signed_request("DELETE", path, b"{}")

        self.assertEqual(unconfirmed.status_code, 409, unconfirmed.text)
        self.uploads.delete_library_session.assert_called_once_with(
            "server", "session-1", confirmed=False
        )

        deletion = {"sessionId": "session-1", "state": "DELETED"}
        self.uploads.delete_library_session = Mock(return_value=deletion)
        body = json.dumps({"confirmed": True}, separators=(",", ":")).encode()

        confirmed = self.signed_request("DELETE", path, body)

        self.assertEqual(confirmed.status_code, 200, confirmed.text)
        self.assertEqual(confirmed.json(), deletion)
        self.uploads.delete_library_session.assert_called_once_with(
            "server", "session-1", confirmed=True
        )

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

    def test_upload_library_proxy_contract(self) -> None:
        self.uploads.library_sessions = Mock(
            return_value={
                "sessions": [
                    {
                        "sessionId": "session-1",
                        "sourceName": "capture",
                        "totalBytes": 12,
                        "fileCount": 1,
                        "completedAt": "2026-08-13T00:00:00Z",
                    }
                ],
                "nextOffset": None,
            }
        )
        self.uploads.library_files = Mock(
            return_value={
                "sessionId": "session-1",
                "path": "",
                "entries": [
                    {
                        "name": "front.jpg",
                        "relativePath": "front.jpg",
                        "type": "FILE",
                        "sizeBytes": 12,
                        "modifiedAt": "2026-08-13T00:00:00Z",
                    }
                ],
                "truncated": False,
            }
        )
        self.uploads.library_file = Mock(return_value=("image/jpeg", b"preview"))

        sessions_path = "/v1/upload/library/sessions?target=server&offset=0"
        sessions = self.signed_request("GET", sessions_path)
        self.assertEqual(sessions.status_code, 200, sessions.text)
        self.assertEqual(sessions.json()["sessions"][0]["sessionId"], "session-1")
        self.uploads.library_sessions.assert_called_once_with("server", offset=0)

        files_path = (
            "/v1/upload/library/files?target=server&session=session-1&path="
        )
        files = self.signed_request("GET", files_path)
        self.assertEqual(files.status_code, 200, files.text)
        self.assertEqual(files.json()["entries"][0]["name"], "front.jpg")

        file_path = (
            "/v1/upload/library/file?target=server&session=session-1&path=front.jpg"
        )
        preview = self.signed_request("GET", file_path)
        self.assertEqual(preview.status_code, 200, preview.text)
        self.assertEqual(preview.content, b"preview")
        self.assertEqual(preview.headers["content-type"], "image/jpeg")

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

    def test_convention_pipeline_time_fan_and_workspace_contracts(self) -> None:
        discover_body = json.dumps(
            {"rootId": "workspace-home", "path": "jobs/capture"},
            separators=(",", ":"),
        ).encode()
        discovered = self.signed_request(
            "POST", "/v1/pipelines/discover-folder", discover_body
        )
        self.assertEqual(discovered.status_code, 200, discovered.text)
        self.assertEqual(discovered.json()["entrypoint"], "main.py")
        self.pipelines.discover_folder.assert_called_once_with(
            self.base / "jobs" / "capture"
        )

        register_body = json.dumps(
            {
                "rootId": "workspace-home",
                "path": "jobs/capture",
                "name": "카메라 수집",
                "autostart": True,
            },
            separators=(",", ":"),
        ).encode()
        registered = self.signed_request(
            "POST", "/v1/pipelines/register-folder", register_body
        )
        self.assertEqual(registered.status_code, 201, registered.text)
        self.assertEqual(registered.json()["outputRootId"], "workspace-home")
        self.assertEqual(registered.json()["outputPath"], "jobs/capture/results")

        time_body = json.dumps(
            {"mobileTimeEpochMillis": 1777000123456}, separators=(",", ":")
        ).encode()
        synchronized = self.signed_request("PUT", "/v1/system/time", time_body)
        self.assertEqual(synchronized.status_code, 200, synchronized.text)
        self.system_time.synchronize.assert_called_once_with(1777000123456)

        fan_body = json.dumps(
            {"mode": "MANUAL", "percent": 40}, separators=(",", ":")
        ).encode()
        fan = self.signed_request("PUT", "/v1/system/fan", fan_body)
        self.assertEqual(fan.status_code, 200, fan.text)
        self.assertEqual(fan.json()["percent"], 40)
        self.fan.set.assert_called_once_with("MANUAL", 40)

        workspace_file = self.signed_request(
            "GET",
            "/v1/fs/workspace/file?root=workspace-home&path=source%2Fhello%20world.txt",
        )
        self.assertEqual(workspace_file.status_code, 200, workspace_file.text)
        self.assertEqual(workspace_file.content, b"hello")

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

        files = self.signed_request("GET", "/v1/pipelines/capture/log-files")
        self.assertEqual(files.status_code, 200, files.text)
        log_id = files.json()["files"][0]["id"]
        self.assertTrue(files.json()["files"][0]["active"])
        chunk = self.signed_request(
            "GET",
            f"/v1/pipelines/capture/log-files/{log_id}?offset=0&limit=4096",
        )
        self.assertEqual(chunk.status_code, 200, chunk.text)
        self.assertEqual(chunk.json()["content"], "capture started")
        self.pipelines.log_files.assert_called_once_with("capture")
        self.pipelines.read_log_file.assert_called_once_with(
            "capture", log_id, 0, 4096
        )

        config = self.signed_request("GET", "/v1/pipelines/capture/config")
        self.assertEqual(config.status_code, 200, config.text)
        self.assertEqual(config.json()["content"], "fps: 30\n")

        body = json.dumps({"content": "fps: 15\n"}, separators=(",", ":")).encode()
        updated = self.signed_request("PUT", "/v1/pipelines/capture/config", body)
        self.assertEqual(updated.status_code, 200, updated.text)
        self.pipelines.update_config.assert_called_once_with("capture", "fps: 15\n")

        fields = self.signed_request(
            "GET", "/v1/pipelines/capture/config/fields"
        )
        self.assertEqual(fields.status_code, 200, fields.text)
        self.assertEqual(fields.json()["fields"][0]["path"], "/fps")

        fields_body = json.dumps(
            {"revision": "a" * 64, "values": {"/fps": "15"}},
            separators=(",", ":"),
        ).encode()
        updated_fields = self.signed_request(
            "PATCH", "/v1/pipelines/capture/config/fields", fields_body
        )
        self.assertEqual(updated_fields.status_code, 200, updated_fields.text)
        self.pipelines.update_config_fields.assert_called_once_with(
            "capture", "a" * 64, {"/fps": "15"}
        )

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
