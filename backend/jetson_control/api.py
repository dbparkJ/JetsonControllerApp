from __future__ import annotations

import ipaddress
import logging
import mimetypes
import time
from pathlib import Path
from typing import Any, Dict, List, Optional, Union

from fastapi import Body, Depends, FastAPI, HTTPException, Request, Response, status
from pydantic import BaseModel, ConfigDict, Field
from starlette.concurrency import run_in_threadpool
from starlette.responses import JSONResponse

from . import __version__
from .auth import RequestAuthenticator, sign_hello, sign_response
from .commands import CommandDisabled, CommandError, CommandRunner
from .config import DeviceConfig, RuntimePaths
from .filesystem import FileTooLarge, StorageRegistry, WorkspaceRegistry
from .network import WifiProvisioner, validate_wifi_credentials
from .mobile_rtk import MobileRtkRelayRegistry
from .pipelines import (
    PIPELINE_ACTIONS,
    PipelineConflict,
    PipelineError,
    PipelineManager,
    PipelineNotFound,
)
from .status import StatusCollector
from .sensors import SensorBridgeStore
from .system_control import (
    FanControlError,
    FanController,
    FanUnavailable,
    SystemTimeSynchronizer,
    TimeSyncConflict,
    TimeSyncError,
)
from .tls import certificate_sha256
from .uploads import (
    UploadCapacityExceeded,
    UploadConfirmationRequired,
    UploadConflict,
    UploadLibraryUnavailable,
    UploadManager,
    UploadVerificationMismatch,
)
from .wifi_direct import read_wifi_direct_status


LOGGER = logging.getLogger(__name__)


IpAddress = Union[ipaddress.IPv4Address, ipaddress.IPv6Address]


def _scope_ip_address(value: object) -> Optional[IpAddress]:
    if not isinstance(value, str):
        return None
    # ASGI servers may include an IPv6 zone identifier in the host string.
    host = value.rsplit("%", 1)[0]
    try:
        return ipaddress.ip_address(host)
    except ValueError:
        return None


def is_lan_upload_request(request: Request, wifi_direct_address: str) -> bool:
    """Return whether an upload mutation arrived over a non-P2P IP path.

    The API listens on all interfaces for both LAN and Wi-Fi Direct control. Prefer
    the socket's local destination address when the ASGI server exposes it, then
    fall back to the peer address when it was bound to an unspecified address.
    Unknown, loopback, and Wi-Fi Direct addresses fail closed.
    """

    wifi_direct_network = ipaddress.ip_interface(wifi_direct_address).network
    server = request.scope.get("server")
    server_address = _scope_ip_address(
        server[0] if isinstance(server, (tuple, list)) and server else None
    )
    if server_address is not None and not server_address.is_unspecified:
        return (
            not server_address.is_loopback
            and server_address not in wifi_direct_network
        )

    client_address = _scope_ip_address(
        request.client.host if request.client is not None else None
    )
    return bool(
        client_address is not None
        and not client_address.is_unspecified
        and not client_address.is_loopback
        and client_address not in wifi_direct_network
    )


class WifiRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    ssid: str
    password: str = ""
    hidden: bool = False


class StartUploadRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    root_id: str = Field(alias="rootId")
    relative_path: str = Field(alias="relativePath")
    target_id: str = Field(alias="targetId")


class SaveUploadTargetRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    label: str = Field(min_length=1, max_length=64)
    base_url: str = Field(alias="baseUrl", min_length=1, max_length=2048)
    token: Optional[str] = Field(default=None, max_length=4096)


class ConfirmDeletionRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    confirmed: bool = False


class RegisterPipelineRequest(BaseModel):
    model_config = ConfigDict(extra="forbid", populate_by_name=True)

    pipeline_id: str = Field(alias="id", min_length=1, max_length=64)
    label: str = Field(min_length=1, max_length=64)
    repository_root_id: str = Field(alias="repositoryRootId")
    repository_path: str = Field(alias="repositoryPath")
    virtualenv_root_id: str = Field(alias="virtualenvRootId")
    virtualenv_path: str = Field(alias="virtualenvPath")
    entrypoint: str
    config: str = "config.yaml"
    working_directory: str = Field(alias="workingDirectory", default=".")
    writable_directories: List[str] = Field(
        alias="writableDirectories",
        default_factory=list,
    )
    autostart: bool = True


class PipelineFolderRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    root_id: str = Field(alias="rootId")
    path: str = ""


class RegisterPipelineFolderRequest(PipelineFolderRequest):
    name: str = Field(min_length=1, max_length=64)
    autostart: bool = True


class SynchronizeSystemTimeRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    mobile_time_epoch_millis: int = Field(alias="mobileTimeEpochMillis")


class SetFanRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    mode: str
    percent: Optional[int] = None


class UpdatePipelineConfigRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    content: str


class UpdatePipelineConfigFieldsRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    revision: str = Field(min_length=64, max_length=64)
    values: Dict[str, str]


class RegisterMobileRtkRelayRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    pipeline_id: str = Field(alias="pipelineId", min_length=1, max_length=64)
    port: int = Field(ge=1024, le=65535)


def create_app(
    paths: Optional[RuntimePaths] = None,
    config: Optional[DeviceConfig] = None,
    authenticator: Optional[RequestAuthenticator] = None,
    status_collector: Optional[StatusCollector] = None,
    command_runner: Optional[CommandRunner] = None,
    storage: Optional[StorageRegistry] = None,
    workspace_storage: Optional[WorkspaceRegistry] = None,
    upload_manager: Optional[UploadManager] = None,
    wifi_provisioner: Optional[WifiProvisioner] = None,
    pipeline_manager: Optional[PipelineManager] = None,
    mobile_rtk_registry: Optional[MobileRtkRelayRegistry] = None,
    time_synchronizer: Optional[SystemTimeSynchronizer] = None,
    fan_controller: Optional[FanController] = None,
    tls_fingerprint: Optional[str] = None,
) -> FastAPI:
    runtime_paths = paths or RuntimePaths()
    device_config = config or DeviceConfig.load(runtime_paths.device_config)
    request_auth = authenticator or RequestAuthenticator(device_config)
    storage_service = storage or StorageRegistry(runtime_paths.storage_roots)
    workspace_service = workspace_storage or WorkspaceRegistry.for_user(
        device_config.pipeline_user
    )
    sensor_bridge = SensorBridgeStore(runtime_paths.sensor_bridge_dir)
    status_service = status_collector or StatusCollector(
        device_config,
        storage_path=storage_service.primary_path(),
        sensor_bridge=sensor_bridge,
    )
    commands = command_runner or CommandRunner(device_config)
    uploads = upload_manager or UploadManager(
        storage=storage_service,
        targets_path=runtime_paths.upload_targets,
        state_dir=runtime_paths.state_dir,
        device_id=device_config.device_id,
    )
    wifi = wifi_provisioner or WifiProvisioner(
        device_config.wifi_interface,
        coordinate_wifi_direct=device_config.wifi_direct_enabled,
    )
    if pipeline_manager is None:
        configured_storage_roots = storage_service.roots()
        recordings_root = configured_storage_roots.get("recordings")
        pipeline_results_root = (
            recordings_root.path
            if recordings_root is not None
            else storage_service.primary_path()
        )
        pipelines = PipelineManager(
            registry_root=runtime_paths.pipeline_registry,
            registrar=runtime_paths.pipeline_registrar,
            pipeline_user=device_config.pipeline_user,
            logs_root=runtime_paths.pipeline_logs,
            folder_results_root=pipeline_results_root,
        )
    else:
        pipelines = pipeline_manager
    mobile_rtk = mobile_rtk_registry or MobileRtkRelayRegistry(
        runtime_paths.mobile_rtk_relay
    )
    system_time = time_synchronizer or SystemTimeSynchronizer(
        on_clock_changed=request_auth.reset_after_clock_change
    )
    fan = fan_controller or FanController()
    certificate_fingerprint = tls_fingerprint or certificate_sha256(
        runtime_paths.tls_certificate
    )

    app = FastAPI(
        title="Jetson Control API",
        version=__version__,
        description="Authenticated local control API for Jetson Controller Android.",
    )

    app.state.device_config = device_config
    app.state.authenticator = request_auth
    app.state.status_collector = status_service
    app.state.sensor_bridge = sensor_bridge
    app.state.command_runner = commands
    app.state.storage = storage_service
    app.state.workspace_storage = workspace_service
    app.state.upload_manager = uploads
    app.state.wifi_provisioner = wifi
    app.state.pipeline_manager = pipelines
    app.state.mobile_rtk_registry = mobile_rtk
    app.state.time_synchronizer = system_time
    app.state.fan_controller = fan

    async def authenticate_request(request: Request) -> None:
        if getattr(request.state, "auth_context", None) is not None:
            return

        body = await request.body()
        raw_path = request.scope.get("raw_path", request.url.path.encode("ascii"))
        query = request.scope.get("query_string", b"")
        path_and_query = raw_path.decode("ascii")
        if query:
            path_and_query += "?" + query.decode("ascii")

        valid = request_auth.verify(
            device_id=request.headers.get("X-Device-Id", ""),
            request_nonce=request.headers.get("X-Request-Nonce", ""),
            request_timestamp=request.headers.get("X-Request-Timestamp", ""),
            method=request.method,
            path_and_query=path_and_query,
            body=body,
            received_signature=request.headers.get("X-Signature", ""),
        )
        if not valid:
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail="Authentication failed",
            )
        request.state.auth_context = {
            "request_nonce": request.headers["X-Request-Nonce"],
            "request_timestamp": request.headers["X-Request-Timestamp"],
        }

    @app.middleware("http")
    async def authenticate_and_sign_v1_response(request: Request, call_next):
        if request.url.path.startswith("/v1/") and request.url.path != "/v1/hello":
            try:
                await authenticate_request(request)
            except HTTPException as error:
                return JSONResponse(
                    status_code=error.status_code,
                    content={"detail": error.detail},
                    headers=error.headers,
                )

        try:
            response = await call_next(request)
        except Exception:
            LOGGER.exception("Unhandled Jetson API request error")
            response = JSONResponse(
                status_code=500,
                content={"detail": "Jetson backend internal error"},
            )
        auth_context = getattr(request.state, "auth_context", None)
        if auth_context is None:
            return response

        body_iterator = getattr(response, "body_iterator", None)
        if body_iterator is None:
            body = bytes(getattr(response, "body", b""))
        else:
            body = b"".join([chunk async for chunk in body_iterator])
        headers = dict(response.headers)
        headers.pop("content-length", None)
        headers["X-Response-Signature"] = sign_response(
            secret=device_config.bootstrap_secret,
            device_id=device_config.device_id,
            boot_nonce=request_auth.boot_nonce,
            request_nonce=auth_context["request_nonce"],
            request_timestamp=auth_context["request_timestamp"],
            status_code=response.status_code,
            body=body,
        )
        return Response(
            content=body,
            status_code=response.status_code,
            headers=headers,
            background=response.background,
        )

    async def require_auth(request: Request) -> None:
        await authenticate_request(request)

    authenticated = [Depends(require_auth)]

    def require_lan_upload_request(request: Request) -> None:
        if not is_lan_upload_request(request, device_config.wifi_direct_address):
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="Server uploads can only be started or retried over LAN",
            )

    def wifi_direct_peer_address(request: Request) -> str:
        network = ipaddress.ip_interface(device_config.wifi_direct_address).network
        own_address = ipaddress.ip_interface(device_config.wifi_direct_address).ip
        client_address = _scope_ip_address(
            request.client.host if request.client is not None else None
        )
        if (
            client_address is None
            or client_address.version != 4
            or client_address not in network
            or client_address == own_address
        ):
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="Mobile RTK relay registration requires Wi-Fi Direct",
            )
        return str(client_address)

    def resolve_pipeline_source(root_id: str, relative_path: str) -> Path:
        if root_id == WorkspaceRegistry.ROOT_ID:
            _, target = workspace_service.resolve(root_id, relative_path)
        else:
            _, target = storage_service.resolve(root_id, relative_path)
        return target

    def pipeline_response(value: Dict[str, object]) -> Dict[str, object]:
        response = dict(value)
        writable_paths = response.pop("writablePaths", [])
        if isinstance(writable_paths, list):
            for writable_path in writable_paths:
                if not isinstance(writable_path, str):
                    continue
                location = storage_service.locate(Path(writable_path))
                if location is not None:
                    response["outputRootId"], response["outputPath"] = location
                    break
                workspace_location = workspace_service.locate(Path(writable_path))
                if workspace_location is not None:
                    response["outputRootId"], response["outputPath"] = workspace_location
                    break
        response.setdefault("outputRootId", None)
        response.setdefault("outputPath", None)
        return response

    @app.get("/v1/hello")
    async def hello() -> Dict[str, object]:
        server_time = int(time.time())
        response = {
            "apiVersion": 1,
            "deviceId": device_config.device_id,
            "deviceName": device_config.device_name,
            "bootNonce": request_auth.boot_nonce,
            "serverTimeEpochSeconds": server_time,
            "authScheme": "JETSONHTTP2",
            "tlsCertificateSha256": certificate_fingerprint,
        }
        response["helloProof"] = sign_hello(
            secret=device_config.bootstrap_secret,
            api_version=1,
            device_id=device_config.device_id,
            device_name=device_config.device_name,
            boot_nonce=request_auth.boot_nonce,
            server_time_epoch_seconds=server_time,
            auth_scheme="JETSONHTTP2",
            tls_certificate_sha256=certificate_fingerprint,
        )
        return response

    @app.get("/v1/capabilities", dependencies=authenticated)
    async def capabilities() -> Dict[str, object]:
        wifi_direct = read_wifi_direct_status()
        return {
            "status": True,
            "commands": sorted(CommandRunner.ACTIONS),
            "systemControlConfigured": bool(device_config.controlled_services),
            "powerCommandsEnabled": device_config.allow_power_commands,
            "fileBrowsing": True,
            "uploads": True,
            "wifiProvisioning": True,
            "wifiDirect": wifi_direct.get("state") == "READY",
            "pipelines": True,
            "pipelineFolderRegistration": True,
            "mobileTimeSync": True,
            "mobileRtkRelay": True,
            "fanControl": True,
        }

    @app.get("/v1/status", dependencies=authenticated)
    async def device_status() -> Dict[str, object]:
        return status_service.collect()

    @app.get("/v1/camera/preview/frame", dependencies=authenticated)
    async def camera_preview_frame() -> Response:
        bridge_status = sensor_bridge.status()
        if not bridge_status.fresh or not bool(bridge_status.camera.get("active")):
            raise HTTPException(status_code=409, detail="Camera sensor is not active")
        if not bool(bridge_status.camera.get("previewAvailable")):
            raise HTTPException(status_code=404, detail="Camera preview is not available")
        try:
            content = sensor_bridge.preview_frame()
        except OSError as error:
            raise HTTPException(status_code=404, detail="Camera preview is not available") from error
        return Response(
            content=content,
            media_type="image/jpeg",
            headers={
                "Cache-Control": "no-store",
                "X-Preview-Timestamp": str(
                    bridge_status.camera.get("previewUpdatedAtEpochMillis") or ""
                ),
            },
        )

    @app.post("/v1/commands/{action}", dependencies=authenticated)
    async def run_command(
        action: str,
        _body: Dict[str, Any] = Body(default_factory=dict),
    ) -> Dict[str, object]:
        try:
            return commands.execute(action)
        except KeyError as error:
            raise HTTPException(status_code=404, detail="Unknown command") from error
        except CommandDisabled as error:
            raise HTTPException(status_code=409, detail=str(error)) from error
        except CommandError as error:
            raise HTTPException(status_code=502, detail=str(error)) from error

    @app.get("/v1/fs/roots", dependencies=authenticated)
    async def filesystem_roots() -> List[Dict[str, object]]:
        try:
            return storage_service.roots_response()
        except (RuntimeError, ValueError) as error:
            raise HTTPException(status_code=500, detail=str(error)) from error

    @app.get("/v1/fs/list", dependencies=authenticated)
    def list_files(root: str, path: str = "") -> Dict[str, object]:
        try:
            return {
                "root": root,
                "path": path,
                "entries": storage_service.list_directory(root, path),
            }
        except (ValueError, FileNotFoundError, NotADirectoryError) as error:
            raise HTTPException(status_code=400, detail=str(error)) from error
        except PermissionError as error:
            raise HTTPException(status_code=403, detail=str(error)) from error

    @app.get("/v1/fs/file", dependencies=authenticated)
    async def read_file(root: str, path: str) -> Response:
        try:
            target, content = storage_service.read_file(
                root,
                path,
                max_bytes=12 * 1024 * 1024,
            )
            media_type = mimetypes.guess_type(target.name)[0] or "application/octet-stream"
            return Response(content=content, media_type=media_type)
        except FileNotFoundError as error:
            raise HTTPException(status_code=404, detail=str(error)) from error
        except FileTooLarge as error:
            raise HTTPException(status_code=413, detail=str(error)) from error
        except ValueError as error:
            raise HTTPException(status_code=400, detail=str(error)) from error
        except PermissionError as error:
            raise HTTPException(status_code=403, detail=str(error)) from error

    @app.delete("/v1/fs/entry", dependencies=authenticated)
    def delete_storage_entry(
        root: str,
        path: str,
        body: ConfirmDeletionRequest,
    ) -> Dict[str, object]:
        try:
            return uploads.delete_storage_entry(
                root,
                path,
                confirmed=body.confirmed,
            )
        except FileNotFoundError as error:
            raise HTTPException(status_code=404, detail=str(error)) from error
        except (UploadConflict, UploadConfirmationRequired) as error:
            raise HTTPException(status_code=409, detail=str(error)) from error
        except ValueError as error:
            raise HTTPException(status_code=400, detail=str(error)) from error
        except PermissionError as error:
            raise HTTPException(status_code=403, detail=str(error)) from error
        except OSError as error:
            raise HTTPException(
                status_code=500,
                detail="Storage entry could not be deleted",
            ) from error

    @app.get("/v1/fs/workspaces", dependencies=authenticated)
    async def workspace_roots() -> List[Dict[str, object]]:
        return workspace_service.roots_response()

    @app.get("/v1/fs/workspace/list", dependencies=authenticated)
    def list_workspace_files(root: str, path: str = "") -> Dict[str, object]:
        try:
            return {
                "root": root,
                "path": path,
                "entries": workspace_service.list_directory(root, path),
            }
        except (ValueError, FileNotFoundError, NotADirectoryError) as error:
            raise HTTPException(status_code=400, detail=str(error)) from error
        except PermissionError as error:
            raise HTTPException(status_code=403, detail=str(error)) from error

    @app.get("/v1/fs/workspace/file", dependencies=authenticated)
    async def read_workspace_file(root: str, path: str) -> Response:
        try:
            target, content = workspace_service.read_file(
                root,
                path,
                max_bytes=12 * 1024 * 1024,
            )
            media_type = mimetypes.guess_type(target.name)[0] or "application/octet-stream"
            return Response(content=content, media_type=media_type)
        except FileNotFoundError as error:
            raise HTTPException(status_code=404, detail=str(error)) from error
        except FileTooLarge as error:
            raise HTTPException(status_code=413, detail=str(error)) from error
        except ValueError as error:
            raise HTTPException(status_code=400, detail=str(error)) from error
        except PermissionError as error:
            raise HTTPException(status_code=403, detail=str(error)) from error

    @app.get("/v1/upload/targets", dependencies=authenticated)
    async def upload_targets() -> List[Dict[str, object]]:
        try:
            return uploads.targets_response()
        except (RuntimeError, ValueError) as error:
            raise HTTPException(status_code=500, detail=str(error)) from error

    @app.put("/v1/upload/targets/{target_id}", dependencies=authenticated)
    async def save_upload_target(
        target_id: str,
        body: SaveUploadTargetRequest,
    ) -> Dict[str, object]:
        try:
            return uploads.save_http_target(
                target_id=target_id,
                label=body.label,
                base_url=body.base_url,
                token=body.token,
            )
        except UploadConflict as error:
            raise HTTPException(status_code=409, detail=str(error)) from error
        except ValueError as error:
            raise HTTPException(status_code=400, detail=str(error)) from error
        except RuntimeError as error:
            raise HTTPException(status_code=500, detail=str(error)) from error

    @app.delete(
        "/v1/upload/targets/{target_id}",
        status_code=204,
        dependencies=authenticated,
    )
    async def delete_upload_target(target_id: str) -> Response:
        try:
            uploads.delete_http_target(target_id)
            return Response(status_code=204)
        except KeyError as error:
            raise HTTPException(status_code=404, detail="Upload target not found") from error
        except UploadConflict as error:
            raise HTTPException(status_code=409, detail=str(error)) from error
        except ValueError as error:
            raise HTTPException(status_code=400, detail=str(error)) from error

    @app.get("/v1/upload/library/sessions", dependencies=authenticated)
    async def upload_library_sessions(
        target: str,
        offset: int = 0,
    ) -> Dict[str, object]:
        try:
            return uploads.library_sessions(target, offset=offset)
        except UploadLibraryUnavailable as error:
            raise HTTPException(status_code=501, detail=str(error)) from error
        except ValueError as error:
            raise HTTPException(status_code=400, detail=str(error)) from error
        except RuntimeError as error:
            raise HTTPException(status_code=502, detail=str(error)) from error

    @app.get("/v1/upload/library/files", dependencies=authenticated)
    async def upload_library_files(
        target: str,
        session: str,
        path: str = "",
    ) -> Dict[str, object]:
        try:
            return uploads.library_files(target, session, path)
        except UploadLibraryUnavailable as error:
            raise HTTPException(status_code=501, detail=str(error)) from error
        except ValueError as error:
            raise HTTPException(status_code=400, detail=str(error)) from error
        except RuntimeError as error:
            raise HTTPException(status_code=502, detail=str(error)) from error

    @app.get("/v1/upload/library/file", dependencies=authenticated)
    async def upload_library_file(
        target: str,
        session: str,
        path: str,
    ) -> Response:
        try:
            media_type, content = uploads.library_file(
                target,
                session,
                path,
                max_bytes=12 * 1024 * 1024,
            )
            return Response(content=content, media_type=media_type)
        except UploadLibraryUnavailable as error:
            raise HTTPException(status_code=501, detail=str(error)) from error
        except FileTooLarge as error:
            raise HTTPException(status_code=413, detail=str(error)) from error
        except ValueError as error:
            raise HTTPException(status_code=400, detail=str(error)) from error
        except RuntimeError as error:
            raise HTTPException(status_code=502, detail=str(error)) from error

    @app.delete(
        "/v1/upload/library/sessions/{session_id}",
        dependencies=authenticated,
    )
    def delete_upload_library_session(
        session_id: str,
        target: str,
        body: ConfirmDeletionRequest,
    ) -> Dict[str, object]:
        try:
            return uploads.delete_library_session(
                target,
                session_id,
                confirmed=body.confirmed,
            )
        except UploadLibraryUnavailable as error:
            raise HTTPException(status_code=501, detail=str(error)) from error
        except UploadConfirmationRequired as error:
            raise HTTPException(status_code=409, detail=str(error)) from error
        except (KeyError, FileNotFoundError) as error:
            raise HTTPException(status_code=404, detail="Upload session not found") from error
        except ValueError as error:
            raise HTTPException(status_code=400, detail=str(error)) from error
        except RuntimeError as error:
            raise HTTPException(status_code=502, detail=str(error)) from error

    @app.get("/v1/upload/source-summary", dependencies=authenticated)
    def upload_source_summary(root: str, path: str = "") -> Dict[str, object]:
        try:
            return uploads.source_summary(root, path)
        except FileNotFoundError as error:
            raise HTTPException(status_code=404, detail=str(error)) from error
        except (ValueError, NotADirectoryError) as error:
            raise HTTPException(status_code=400, detail=str(error)) from error

    @app.post("/v1/uploads", status_code=202, dependencies=authenticated)
    async def start_upload(
        request: Request,
        body: StartUploadRequest,
    ) -> Dict[str, object]:
        require_lan_upload_request(request)
        try:
            return uploads.start(
                root_id=body.root_id,
                relative_path=body.relative_path,
                target_id=body.target_id,
            )
        except FileNotFoundError as error:
            raise HTTPException(status_code=404, detail=str(error)) from error
        except UploadCapacityExceeded as error:
            raise HTTPException(status_code=409, detail=str(error)) from error
        except ValueError as error:
            raise HTTPException(status_code=400, detail=str(error)) from error

    @app.get("/v1/uploads", dependencies=authenticated)
    async def list_uploads(active: bool = False) -> List[Dict[str, object]]:
        return uploads.list_jobs(active_only=active)

    @app.get("/v1/uploads/{job_id}", dependencies=authenticated)
    async def get_upload(job_id: str) -> Dict[str, object]:
        try:
            return uploads.get(job_id)
        except (KeyError, ValueError) as error:
            raise HTTPException(status_code=404, detail="Upload job not found") from error

    @app.delete(
        "/v1/uploads/{job_id}",
        status_code=204,
        dependencies=authenticated,
    )
    def delete_upload_job(
        job_id: str,
        body: ConfirmDeletionRequest,
    ) -> Response:
        try:
            uploads.delete_job(job_id, confirmed=body.confirmed)
            return Response(status_code=204)
        except KeyError as error:
            raise HTTPException(status_code=404, detail="Upload job not found") from error
        except (UploadConflict, UploadConfirmationRequired) as error:
            raise HTTPException(status_code=409, detail=str(error)) from error
        except ValueError as error:
            raise HTTPException(status_code=400, detail=str(error)) from error
        except OSError as error:
            raise HTTPException(
                status_code=500,
                detail="Upload history could not be deleted",
            ) from error

    @app.post("/v1/uploads/{job_id}/cancel", dependencies=authenticated)
    async def cancel_upload(job_id: str) -> Dict[str, object]:
        try:
            return uploads.cancel(job_id)
        except (KeyError, ValueError) as error:
            raise HTTPException(status_code=404, detail="Upload job not found") from error

    @app.post("/v1/uploads/{job_id}/verify", dependencies=authenticated)
    def verify_upload_source(job_id: str) -> Dict[str, object]:
        try:
            return uploads.verify_completed_source(job_id)
        except KeyError as error:
            raise HTTPException(status_code=404, detail="Upload job not found") from error
        except FileNotFoundError as error:
            raise HTTPException(status_code=404, detail=str(error)) from error
        except (UploadConflict, UploadVerificationMismatch) as error:
            raise HTTPException(status_code=409, detail=str(error)) from error
        except ValueError as error:
            raise HTTPException(status_code=400, detail=str(error)) from error
        except RuntimeError as error:
            raise HTTPException(status_code=502, detail=str(error)) from error

    @app.delete("/v1/uploads/{job_id}/source", dependencies=authenticated)
    def delete_upload_source(
        job_id: str,
        body: ConfirmDeletionRequest,
    ) -> Dict[str, object]:
        try:
            return uploads.delete_completed_source(job_id, confirmed=body.confirmed)
        except KeyError as error:
            raise HTTPException(status_code=404, detail="Upload job not found") from error
        except FileNotFoundError as error:
            raise HTTPException(status_code=404, detail=str(error)) from error
        except (UploadConflict, UploadConfirmationRequired, UploadVerificationMismatch) as error:
            raise HTTPException(status_code=409, detail=str(error)) from error
        except ValueError as error:
            raise HTTPException(status_code=400, detail=str(error)) from error
        except RuntimeError as error:
            raise HTTPException(status_code=502, detail=str(error)) from error

    @app.post(
        "/v1/uploads/{job_id}/retry",
        status_code=202,
        dependencies=authenticated,
    )
    async def retry_upload(request: Request, job_id: str) -> Dict[str, object]:
        require_lan_upload_request(request)
        try:
            return uploads.retry(job_id)
        except KeyError as error:
            raise HTTPException(status_code=404, detail="Upload job not found") from error
        except (UploadCapacityExceeded, UploadConflict) as error:
            raise HTTPException(status_code=409, detail=str(error)) from error
        except (FileNotFoundError, ValueError) as error:
            raise HTTPException(status_code=400, detail=str(error)) from error

    @app.post("/v1/network/wifi", status_code=202, dependencies=authenticated)
    async def configure_wifi(body: WifiRequest) -> Dict[str, object]:
        try:
            ssid, password = validate_wifi_credentials(body.ssid, body.password)
            return wifi.submit(ssid, password, body.hidden)
        except ValueError as error:
            raise HTTPException(status_code=400, detail=str(error)) from error
        except RuntimeError as error:
            raise HTTPException(status_code=409, detail=str(error)) from error

    @app.get("/v1/network/wifi/status", dependencies=authenticated)
    async def wifi_status() -> Dict[str, object]:
        return wifi.status()

    @app.get("/v1/network/wifi-direct/status", dependencies=authenticated)
    async def wifi_direct_status() -> Dict[str, object]:
        return read_wifi_direct_status()

    @app.get("/v1/pipelines", dependencies=authenticated)
    async def list_pipelines() -> List[Dict[str, object]]:
        try:
            return [pipeline_response(item) for item in pipelines.list_pipelines()]
        except PipelineError as error:
            raise HTTPException(status_code=500, detail=str(error)) from error

    @app.post("/v1/pipelines/discover-folder", dependencies=authenticated)
    def discover_pipeline_folder(body: PipelineFolderRequest) -> Dict[str, object]:
        try:
            repository = resolve_pipeline_source(body.root_id, body.path)
            return pipelines.discover_folder(repository)
        except (ValueError, FileNotFoundError, NotADirectoryError) as error:
            raise HTTPException(status_code=400, detail=str(error)) from error
        except PipelineError as error:
            raise HTTPException(status_code=502, detail=str(error)) from error

    @app.post(
        "/v1/pipelines/register-folder",
        status_code=201,
        dependencies=authenticated,
    )
    def register_pipeline_folder(
        body: RegisterPipelineFolderRequest,
    ) -> Dict[str, object]:
        try:
            repository = resolve_pipeline_source(body.root_id, body.path)
            return pipeline_response(
                pipelines.register_folder(
                    label=body.name,
                    repository=repository,
                    autostart=body.autostart,
                )
            )
        except PipelineConflict as error:
            raise HTTPException(status_code=409, detail=str(error)) from error
        except (ValueError, FileNotFoundError, NotADirectoryError) as error:
            raise HTTPException(status_code=400, detail=str(error)) from error
        except PipelineError as error:
            raise HTTPException(status_code=502, detail=str(error)) from error

    @app.post("/v1/pipelines", status_code=201, dependencies=authenticated)
    async def register_pipeline(body: RegisterPipelineRequest) -> Dict[str, object]:
        try:
            repository = resolve_pipeline_source(
                body.repository_root_id, body.repository_path
            )
            virtualenv = resolve_pipeline_source(
                body.virtualenv_root_id, body.virtualenv_path
            )
            working_directory = _resolve_child(
                repository,
                body.working_directory,
                "working directory",
            )
            writable_paths = [
                _resolve_child(repository, value, "writable directory")
                for value in body.writable_directories
            ]
            return pipeline_response(
                pipelines.register(
                    pipeline_id=body.pipeline_id,
                    label=body.label,
                    repository=repository,
                    virtualenv=virtualenv,
                    entrypoint=_relative_child(body.entrypoint, "entrypoint"),
                    config=_relative_child(body.config, "config"),
                    working_directory=working_directory,
                    writable_paths=writable_paths,
                    autostart=body.autostart,
                )
            )
        except PipelineConflict as error:
            raise HTTPException(status_code=409, detail=str(error)) from error
        except (ValueError, FileNotFoundError, NotADirectoryError) as error:
            raise HTTPException(status_code=400, detail=str(error)) from error
        except PipelineError as error:
            raise HTTPException(status_code=502, detail=str(error)) from error

    @app.get("/v1/system/time", dependencies=authenticated)
    def system_time_status() -> Dict[str, object]:
        return system_time.status()

    @app.put("/v1/system/time", dependencies=authenticated)
    def synchronize_system_time(
        body: SynchronizeSystemTimeRequest,
    ) -> Dict[str, object]:
        try:
            return system_time.synchronize(body.mobile_time_epoch_millis)
        except TimeSyncConflict as error:
            raise HTTPException(status_code=409, detail=str(error)) from error
        except ValueError as error:
            raise HTTPException(status_code=400, detail=str(error)) from error
        except TimeSyncError as error:
            raise HTTPException(status_code=502, detail=str(error)) from error

    @app.get("/v1/system/fan", dependencies=authenticated)
    def fan_status() -> Dict[str, object]:
        try:
            return fan.status()
        except FanControlError as error:
            raise HTTPException(status_code=502, detail=str(error)) from error

    @app.put("/v1/system/fan", dependencies=authenticated)
    def set_fan(body: SetFanRequest) -> Dict[str, object]:
        try:
            return fan.set(body.mode, body.percent)
        except ValueError as error:
            raise HTTPException(status_code=400, detail=str(error)) from error
        except FanUnavailable as error:
            raise HTTPException(status_code=409, detail=str(error)) from error
        except FanControlError as error:
            raise HTTPException(status_code=502, detail=str(error)) from error

    @app.delete("/v1/pipelines/{pipeline_id}", status_code=204, dependencies=authenticated)
    async def remove_pipeline(pipeline_id: str) -> Response:
        try:
            pipelines.remove(pipeline_id)
            return Response(status_code=204)
        except PipelineNotFound as error:
            raise HTTPException(status_code=404, detail=str(error)) from error
        except ValueError as error:
            raise HTTPException(status_code=400, detail=str(error)) from error
        except PipelineError as error:
            raise HTTPException(status_code=502, detail=str(error)) from error

    @app.get("/v1/pipelines/{pipeline_id}/logs", dependencies=authenticated)
    async def pipeline_logs(pipeline_id: str, lines: int = 200) -> Dict[str, object]:
        try:
            return pipelines.logs(pipeline_id, lines)
        except PipelineNotFound as error:
            raise HTTPException(status_code=404, detail=str(error)) from error
        except (ValueError, PipelineError) as error:
            raise HTTPException(status_code=400, detail=str(error)) from error

    @app.get("/v1/pipelines/{pipeline_id}/log-files", dependencies=authenticated)
    async def pipeline_log_files(pipeline_id: str) -> Dict[str, object]:
        try:
            return pipelines.log_files(pipeline_id)
        except PipelineNotFound as error:
            raise HTTPException(status_code=404, detail=str(error)) from error
        except (ValueError, PipelineError) as error:
            raise HTTPException(status_code=400, detail=str(error)) from error

    @app.get(
        "/v1/pipelines/{pipeline_id}/log-files/{log_id}",
        dependencies=authenticated,
    )
    async def pipeline_log_file(
        pipeline_id: str,
        log_id: str,
        offset: int = 0,
        limit: int = 128 * 1024,
    ) -> Dict[str, object]:
        try:
            return pipelines.read_log_file(pipeline_id, log_id, offset, limit)
        except PipelineNotFound as error:
            raise HTTPException(status_code=404, detail=str(error)) from error
        except (ValueError, PipelineError) as error:
            raise HTTPException(status_code=400, detail=str(error)) from error

    @app.get("/v1/pipelines/{pipeline_id}/config", dependencies=authenticated)
    async def pipeline_config(pipeline_id: str) -> Dict[str, str]:
        try:
            return pipelines.config_document(pipeline_id)
        except PipelineNotFound as error:
            raise HTTPException(status_code=404, detail=str(error)) from error
        except (ValueError, PipelineError) as error:
            raise HTTPException(status_code=400, detail=str(error)) from error

    @app.put("/v1/pipelines/{pipeline_id}/config", dependencies=authenticated)
    async def update_pipeline_config(
        pipeline_id: str,
        body: UpdatePipelineConfigRequest,
    ) -> Dict[str, str]:
        try:
            return pipelines.update_config(pipeline_id, body.content)
        except PipelineNotFound as error:
            raise HTTPException(status_code=404, detail=str(error)) from error
        except (ValueError, PipelineError) as error:
            raise HTTPException(status_code=400, detail=str(error)) from error

    @app.get("/v1/pipelines/{pipeline_id}/config/fields", dependencies=authenticated)
    async def pipeline_config_fields(pipeline_id: str) -> Dict[str, object]:
        try:
            return pipelines.config_fields(pipeline_id)
        except PipelineNotFound as error:
            raise HTTPException(status_code=404, detail=str(error)) from error
        except (ValueError, PipelineError) as error:
            raise HTTPException(status_code=400, detail=str(error)) from error

    @app.patch("/v1/pipelines/{pipeline_id}/config/fields", dependencies=authenticated)
    async def update_pipeline_config_fields(
        pipeline_id: str,
        body: UpdatePipelineConfigFieldsRequest,
    ) -> Dict[str, object]:
        try:
            return pipelines.update_config_fields(
                pipeline_id,
                body.revision,
                body.values,
            )
        except PipelineNotFound as error:
            raise HTTPException(status_code=404, detail=str(error)) from error
        except PipelineConflict as error:
            raise HTTPException(status_code=409, detail=str(error)) from error
        except (ValueError, PipelineError) as error:
            raise HTTPException(status_code=400, detail=str(error)) from error

    @app.post("/v1/pipelines/{pipeline_id}/{action}", dependencies=authenticated)
    async def control_pipeline(pipeline_id: str, action: str) -> Dict[str, object]:
        if action not in PIPELINE_ACTIONS:
            raise HTTPException(status_code=404, detail="Unknown pipeline action")
        try:
            # systemctl stop/restart can wait for a pipeline's graceful shutdown
            # timeout. Keep that blocking subprocess off Uvicorn's event loop so
            # status, camera, BLE handoff, and relay heartbeats remain responsive.
            controlled = await run_in_threadpool(
                pipelines.control,
                pipeline_id,
                action,
            )
            return pipeline_response(controlled)
        except PipelineNotFound as error:
            raise HTTPException(status_code=404, detail=str(error)) from error
        except ValueError as error:
            raise HTTPException(status_code=400, detail=str(error)) from error
        except PipelineError as error:
            raise HTTPException(status_code=502, detail=str(error)) from error

    @app.get(
        "/v1/rtk/mobile-relay/config/{pipeline_id}",
        dependencies=authenticated,
    )
    async def mobile_rtk_config(pipeline_id: str) -> Dict[str, object]:
        try:
            return await run_in_threadpool(pipelines.mobile_rtk_config, pipeline_id)
        except PipelineNotFound as error:
            raise HTTPException(status_code=404, detail=str(error)) from error
        except (ValueError, PipelineError) as error:
            raise HTTPException(status_code=400, detail=str(error)) from error

    @app.put("/v1/rtk/mobile-relay", dependencies=authenticated)
    async def register_mobile_rtk_relay(
        request: Request,
        body: RegisterMobileRtkRelayRequest,
    ) -> Dict[str, object]:
        relay_host = wifi_direct_peer_address(request)
        try:
            config = await run_in_threadpool(
                pipelines.mobile_rtk_config,
                body.pipeline_id,
            )
            if not config.get("available"):
                raise HTTPException(
                    status_code=409,
                    detail="Pipeline does not have mobile-relay-compatible NTRIP enabled",
                )
            return mobile_rtk.register(body.pipeline_id, relay_host, body.port)
        except PipelineNotFound as error:
            raise HTTPException(status_code=404, detail=str(error)) from error
        except (ValueError, PipelineError) as error:
            raise HTTPException(status_code=400, detail=str(error)) from error

    @app.delete("/v1/rtk/mobile-relay/{pipeline_id}", dependencies=authenticated)
    async def unregister_mobile_rtk_relay(pipeline_id: str) -> Response:
        try:
            mobile_rtk.unregister(pipeline_id)
        except ValueError as error:
            raise HTTPException(status_code=400, detail=str(error)) from error
        return Response(status_code=204)

    return app


def _relative_child(value: str, kind: str) -> str:
    candidate = Path(value)
    if not value or candidate.is_absolute() or ".." in candidate.parts or "\x00" in value:
        raise ValueError(f"Invalid {kind} path")
    normalized = candidate.as_posix()
    if normalized in {"", "."}:
        raise ValueError(f"Invalid {kind} path")
    return normalized


def _resolve_child(repository: Path, value: str, kind: str) -> Path:
    if value in {"", "."}:
        return repository
    relative = _relative_child(value, kind)
    target = (repository / relative).resolve()
    try:
        target.relative_to(repository)
    except ValueError as error:
        raise ValueError(f"Invalid {kind} path") from error
    return target
