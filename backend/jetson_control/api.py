from __future__ import annotations

import logging
import mimetypes
import time
from pathlib import Path
from typing import Any, Dict, List, Optional

from fastapi import Body, Depends, FastAPI, HTTPException, Request, Response, status
from pydantic import BaseModel, ConfigDict, Field
from starlette.responses import JSONResponse

from . import __version__
from .auth import RequestAuthenticator, sign_hello, sign_response
from .commands import CommandDisabled, CommandError, CommandRunner
from .config import DeviceConfig, RuntimePaths
from .filesystem import FileTooLarge, StorageRegistry, WorkspaceRegistry
from .network import WifiProvisioner, validate_wifi_credentials
from .pipelines import (
    PIPELINE_ACTIONS,
    PipelineConflict,
    PipelineError,
    PipelineManager,
    PipelineNotFound,
)
from .status import StatusCollector
from .sensors import SensorBridgeStore
from .tls import certificate_sha256
from .uploads import (
    UploadCapacityExceeded,
    UploadConflict,
    UploadLibraryUnavailable,
    UploadManager,
)
from .wifi_direct import read_wifi_direct_status


LOGGER = logging.getLogger(__name__)


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


class UpdatePipelineConfigRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    content: str


class UpdatePipelineConfigFieldsRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    revision: str = Field(min_length=64, max_length=64)
    values: Dict[str, str]


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
    wifi = wifi_provisioner or WifiProvisioner(device_config.wifi_interface)
    pipelines = pipeline_manager or PipelineManager(
        registry_root=runtime_paths.pipeline_registry,
        registrar=runtime_paths.pipeline_registrar,
        pipeline_user=device_config.pipeline_user,
        logs_root=runtime_paths.pipeline_logs,
    )
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
    async def list_files(root: str, path: str = "") -> Dict[str, object]:
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

    @app.get("/v1/fs/workspaces", dependencies=authenticated)
    async def workspace_roots() -> List[Dict[str, object]]:
        return workspace_service.roots_response()

    @app.get("/v1/fs/workspace/list", dependencies=authenticated)
    async def list_workspace_files(root: str, path: str = "") -> Dict[str, object]:
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

    @app.post("/v1/uploads", status_code=202, dependencies=authenticated)
    async def start_upload(body: StartUploadRequest) -> Dict[str, object]:
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

    @app.post("/v1/uploads/{job_id}/cancel", dependencies=authenticated)
    async def cancel_upload(job_id: str) -> Dict[str, object]:
        try:
            return uploads.cancel(job_id)
        except (KeyError, ValueError) as error:
            raise HTTPException(status_code=404, detail="Upload job not found") from error

    @app.post(
        "/v1/uploads/{job_id}/retry",
        status_code=202,
        dependencies=authenticated,
    )
    async def retry_upload(job_id: str) -> Dict[str, object]:
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
            return pipeline_response(pipelines.control(pipeline_id, action))
        except PipelineNotFound as error:
            raise HTTPException(status_code=404, detail=str(error)) from error
        except ValueError as error:
            raise HTTPException(status_code=400, detail=str(error)) from error
        except PipelineError as error:
            raise HTTPException(status_code=502, detail=str(error)) from error

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
