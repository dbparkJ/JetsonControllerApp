from __future__ import annotations

import hashlib
import hmac
import json
import mimetypes
from contextlib import asynccontextmanager

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse, PlainTextResponse, Response
from starlette.concurrency import run_in_threadpool

from .config import Settings
from .service import ReceiverError, ReceiverService


async def _bounded_body(request: Request, limit: int) -> bytes:
    declared = request.headers.get("content-length")
    if declared is not None:
        try:
            declared_size = int(declared)
        except ValueError as error:
            raise ReceiverError(400, "Content-Length is invalid") from error
        if declared_size < 0:
            raise ReceiverError(400, "Content-Length is invalid")
        if declared_size > limit:
            raise ReceiverError(413, "Request body is too large")
    chunks = []
    received = 0
    async for chunk in request.stream():
        received += len(chunk)
        if received > limit:
            raise ReceiverError(413, "Request body is too large")
        chunks.append(chunk)
    body = b"".join(chunks)
    if declared is not None and len(body) != int(declared):
        raise ReceiverError(400, "Content-Length does not match the request body")
    return body


def create_app(settings: Settings | None = None) -> FastAPI:
    effective_settings = settings or Settings.from_env()

    @asynccontextmanager
    async def lifespan(application: FastAPI):
        application.state.receiver = ReceiverService(effective_settings)
        yield

    application = FastAPI(
        title="Jetson Upload Receiver",
        docs_url=None,
        redoc_url=None,
        openapi_url=None,
        lifespan=lifespan,
    )

    @application.exception_handler(ReceiverError)
    async def receiver_error_handler(_request: Request, error: ReceiverError):
        headers = (
            {"Retry-After": str(error.retry_after)}
            if error.retry_after is not None
            else None
        )
        return JSONResponse(
            status_code=error.status,
            content={"detail": error.detail},
            headers=headers,
        )

    @application.exception_handler(RequestValidationError)
    async def validation_error_handler(_request: Request, _error: RequestValidationError):
        return JSONResponse(status_code=400, content={"detail": "Request is invalid"})

    @application.exception_handler(Exception)
    async def internal_error_handler(_request: Request, _error: Exception):
        return JSONResponse(status_code=500, content={"detail": "Internal server error"})

    def service(request: Request) -> ReceiverService:
        return request.app.state.receiver

    def _require_content_type(request: Request, expected: str) -> None:
        actual = request.headers.get("content-type", "").split(";", 1)[0].strip().lower()
        if actual != expected:
            raise ReceiverError(400, f"Content-Type must be {expected}")

    async def employee(request: Request):
        receiver = service(request)
        return await run_in_threadpool(
            receiver.authenticate_employee,
            request.headers.get("authorization"),
            request.headers.get("x-expected-server-environment"),
        )

    @application.get("/health/live")
    async def health_live():
        return {"state": "LIVE"}

    @application.get("/health/ready")
    async def health_ready(request: Request):
        ready = await run_in_threadpool(service(request).health_ready)
        return JSONResponse(
            status_code=200 if ready else 503,
            content={"state": "READY" if ready else "NOT_READY"},
        )

    @application.get("/metrics", response_class=PlainTextResponse)
    async def metrics(request: Request):
        return await run_in_threadpool(service(request).metrics)

    @application.get("/v1/capabilities")
    async def capabilities(request: Request):
        receiver = service(request)
        await run_in_threadpool(
            receiver.authenticate, request.headers.get("authorization")
        )
        return {
            "deferredFileHashes": {
                "version": 1,
                "manifestHashMode": "deferred-v1",
            },
            "fileBatch": {
                "version": 1,
                "maxBytes": effective_settings.max_batch_bytes,
                "maxFiles": effective_settings.max_batch_files,
            },
            "library": {
                "version": 1,
                "maxPreviewBytes": effective_settings.max_preview_bytes,
            },
        }

    @application.get("/v1/server/capabilities")
    async def server_capabilities(request: Request):
        receiver = service(request)
        principal = await employee(request)
        return await run_in_threadpool(receiver.server_identity, principal)

    @application.get("/v1/server/jobs")
    async def server_jobs(
        request: Request,
        projectId: str,
        limit: int = 100,
        offset: int = 0,
    ):
        receiver = service(request)
        principal = await employee(request)
        return await run_in_threadpool(
            receiver.list_server_jobs,
            principal,
            projectId,
            limit=limit,
            offset=offset,
        )

    @application.get("/v1/server/jobs/{session_id}/files")
    async def server_files(
        session_id: str,
        request: Request,
        projectId: str,
        path: str = "",
    ):
        receiver = service(request)
        principal = await employee(request)
        return await run_in_threadpool(
            receiver.list_server_files,
            principal,
            projectId,
            session_id,
            path,
        )

    @application.get("/v1/server/jobs/{session_id}/preview")
    async def server_preview(
        session_id: str,
        request: Request,
        projectId: str,
        path: str,
    ):
        receiver = service(request)
        principal = await employee(request)
        name, content = await run_in_threadpool(
            receiver.read_server_preview,
            principal,
            projectId,
            session_id,
            path,
        )
        media_type = mimetypes.guess_type(name)[0] or "application/octet-stream"
        return Response(
            content=content,
            media_type=media_type,
            headers={
                "Cache-Control": "private, no-store",
                "X-Server-Environment": effective_settings.server_environment,
                "X-Preview-Size-Bytes": str(len(content)),
                "X-Preview-Kind": "VIDEO" if media_type.startswith("video/") else "IMAGE",
            },
        )

    @application.get("/v1/server/jobs/{session_id}/receipt")
    async def server_receipt(session_id: str, request: Request, projectId: str):
        receiver = service(request)
        principal = await employee(request)
        return await run_in_threadpool(
            receiver.server_receipt,
            principal,
            projectId,
            session_id,
        )

    @application.get("/v1/server/trash")
    async def server_trash(
        request: Request,
        projectId: str,
        limit: int = 200,
        offset: int = 0,
    ):
        receiver = service(request)
        principal = await employee(request)
        return await run_in_threadpool(
            receiver.list_server_trash,
            principal,
            projectId,
            limit=limit,
            offset=offset,
        )

    @application.post("/v1/server/trash/empty")
    async def empty_server_trash(request: Request, projectId: str):
        _require_content_type(request, "application/json")
        raw = await _bounded_body(request, 32 * 1024)
        try:
            body = json.loads(raw)
        except (UnicodeDecodeError, json.JSONDecodeError) as error:
            raise ReceiverError(400, "Request body is invalid") from error
        if (
            not isinstance(body, dict)
            or set(body) != {"confirmed", "sessionIds"}
            or body.get("confirmed") is not True
            or not isinstance(body.get("sessionIds"), list)
        ):
            raise ReceiverError(400, "Trash empty confirmation is invalid")
        receiver = service(request)
        principal = await employee(request)
        return await run_in_threadpool(
            receiver.empty_server_trash,
            principal,
            projectId,
            body["sessionIds"],
        )

    @application.get("/v1/server/audit")
    async def server_audit(
        request: Request,
        projectId: str,
        limit: int = 100,
        offset: int = 0,
    ):
        receiver = service(request)
        principal = await employee(request)
        return await run_in_threadpool(
            receiver.list_server_audit,
            principal,
            projectId,
            limit=limit,
            offset=offset,
        )

    @application.delete("/v1/server/jobs/{session_id}")
    async def trash_server_job(session_id: str, request: Request, projectId: str):
        receiver = service(request)
        principal = await employee(request)
        return await run_in_threadpool(
            receiver.trash_server_job,
            principal,
            projectId,
            session_id,
        )

    @application.post("/v1/server/trash/{session_id}/restore")
    async def restore_server_job(session_id: str, request: Request, projectId: str):
        receiver = service(request)
        principal = await employee(request)
        return await run_in_threadpool(
            receiver.restore_server_job,
            principal,
            projectId,
            session_id,
        )

    @application.get("/v1/library/sessions")
    async def library_sessions(
        request: Request,
        limit: int = 100,
        offset: int = 0,
    ):
        receiver = service(request)
        device = await run_in_threadpool(
            receiver.authenticate, request.headers.get("authorization")
        )
        return await run_in_threadpool(
            receiver.list_library_sessions,
            device,
            limit=limit,
            offset=offset,
        )

    @application.get("/v1/library/sessions/{session_id}/files")
    async def library_files(session_id: str, request: Request, path: str = ""):
        receiver = service(request)
        device = await run_in_threadpool(
            receiver.authenticate, request.headers.get("authorization")
        )
        return await run_in_threadpool(
            receiver.list_library_files,
            device,
            session_id,
            path,
        )

    @application.get("/v1/library/sessions/{session_id}/verification")
    async def library_verification(session_id: str, request: Request):
        receiver = service(request)
        device = await run_in_threadpool(
            receiver.authenticate, request.headers.get("authorization")
        )
        return await run_in_threadpool(
            receiver.verify_library_session,
            device,
            session_id,
        )

    @application.delete("/v1/library/sessions/{session_id}")
    async def delete_library_session(session_id: str, request: Request):
        receiver = service(request)
        device = await run_in_threadpool(
            receiver.authenticate, request.headers.get("authorization")
        )
        state = await run_in_threadpool(
            receiver.delete_library_session,
            device,
            session_id,
        )
        return {"sessionId": session_id, "state": state}

    @application.get("/v1/library/sessions/{session_id}/file")
    async def library_file(session_id: str, request: Request, path: str):
        receiver = service(request)
        device = await run_in_threadpool(
            receiver.authenticate, request.headers.get("authorization")
        )
        name, content = await run_in_threadpool(
            receiver.read_library_file,
            device,
            session_id,
            path,
        )
        return Response(
            content=content,
            media_type=mimetypes.guess_type(name)[0] or "application/octet-stream",
        )

    @application.post("/v1/upload-sessions")
    async def create_session(request: Request):
        receiver = service(request)
        device = await run_in_threadpool(
            receiver.authenticate, request.headers.get("authorization")
        )
        await run_in_threadpool(receiver.reserve_manifest_request, device)
        _require_content_type(request, "application/json")
        body = await _bounded_body(request, effective_settings.max_manifest_bytes)
        try:
            value = json.loads(body.decode("utf-8"))
        except (UnicodeDecodeError, json.JSONDecodeError) as error:
            raise ReceiverError(400, "Manifest JSON is invalid") from error
        manifest = await run_in_threadpool(receiver.parse_manifest, value)
        session_id, status = await run_in_threadpool(
            receiver.create_session, device, manifest
        )
        return JSONResponse(
            status_code=status,
            content={
                "sessionId": session_id,
                "fileBatch": {
                    "version": 1,
                    "maxBytes": effective_settings.max_batch_bytes,
                    "maxFiles": effective_settings.max_batch_files,
                },
            },
        )

    @application.get("/v1/upload-sessions/{session_id}/files/offset")
    async def get_offset(session_id: str, request: Request):
        receiver = service(request)
        device = await run_in_threadpool(
            receiver.authenticate, request.headers.get("authorization")
        )
        paths = request.query_params.getlist("path")
        if len(paths) != 1:
            raise ReceiverError(400, "Exactly one path query parameter is required")
        offset = await run_in_threadpool(
            receiver.get_offset, device, session_id, paths[0]
        )
        return {"nextOffset": offset}

    @application.post("/v1/upload-sessions/{session_id}/files/offsets")
    async def get_offsets(session_id: str, request: Request):
        receiver = service(request)
        device = await run_in_threadpool(
            receiver.authenticate, request.headers.get("authorization")
        )
        _require_content_type(request, "application/json")
        body = await _bounded_body(request, effective_settings.max_manifest_bytes)
        try:
            value = json.loads(body.decode("utf-8"))
        except (UnicodeDecodeError, json.JSONDecodeError) as error:
            raise ReceiverError(400, "Batch offset JSON is invalid") from error
        if not isinstance(value, dict) or set(value) != {"paths"}:
            raise ReceiverError(400, "Batch offset request is invalid")
        paths = value["paths"]
        if not isinstance(paths, list) or not all(isinstance(path, str) for path in paths):
            raise ReceiverError(400, "Batch offset paths are invalid")
        offsets = await run_in_threadpool(
            receiver.get_offsets,
            device,
            session_id,
            paths,
        )
        return {
            "files": [
                {"path": path, "nextOffset": next_offset}
                for path, next_offset in offsets.items()
            ]
        }

    @application.put("/v1/upload-sessions/{session_id}/files")
    async def put_chunk(session_id: str, request: Request):
        receiver = service(request)
        device = await run_in_threadpool(
            receiver.authenticate, request.headers.get("authorization")
        )
        _require_content_type(request, "application/octet-stream")
        if request.headers.get("content-length") is None:
            raise ReceiverError(400, "Content-Length is required")
        paths = request.query_params.getlist("path")
        offsets = request.query_params.getlist("offset")
        if len(paths) != 1 or len(offsets) != 1:
            raise ReceiverError(400, "Exactly one path and offset are required")
        try:
            offset = int(offsets[0])
        except ValueError as error:
            raise ReceiverError(400, "Chunk offset is invalid") from error
        content_range = request.headers.get("content-range")
        chunk_sha256 = request.headers.get("x-chunk-sha256")
        if content_range is None or chunk_sha256 is None:
            raise ReceiverError(400, "Required chunk headers are missing")
        await run_in_threadpool(receiver.acquire_put_slot, device.device_id)
        try:
            body = await _bounded_body(request, effective_settings.max_chunk_bytes)
            next_offset = await run_in_threadpool(
                receiver.put_chunk,
                device,
                session_id,
                paths[0],
                offset,
                content_range,
                chunk_sha256,
                body,
                slot_reserved=True,
            )
        finally:
            receiver.release_put_slot(device.device_id)
        return {"nextOffset": next_offset}

    @application.put("/v1/upload-sessions/{session_id}/files/batch")
    async def put_file_batch(session_id: str, request: Request):
        receiver = service(request)
        device = await run_in_threadpool(
            receiver.authenticate, request.headers.get("authorization")
        )
        _require_content_type(request, "application/vnd.jetson.upload-batch-v1")
        batch_sha256 = request.headers.get("x-batch-sha256")
        if batch_sha256 is None:
            raise ReceiverError(400, "X-Batch-SHA256 is required")
        await run_in_threadpool(receiver.acquire_put_slot, device.device_id)
        try:
            body = await _bounded_body(request, effective_settings.max_batch_bytes)
            if not hmac.compare_digest(
                hashlib.sha256(body).hexdigest(),
                batch_sha256,
            ):
                raise ReceiverError(422, "File batch SHA-256 does not match")
            files = await run_in_threadpool(receiver.parse_file_batch, body)
            offsets = await run_in_threadpool(
                receiver.put_file_batch,
                device,
                session_id,
                files,
                slot_reserved=True,
            )
        finally:
            receiver.release_put_slot(device.device_id)
        return {
            "files": [
                {"path": path, "nextOffset": next_offset}
                for path, next_offset in offsets.items()
            ]
        }

    @application.post("/v1/upload-sessions/{session_id}/complete")
    async def complete(session_id: str, request: Request):
        receiver = service(request)
        device = await run_in_threadpool(
            receiver.authenticate, request.headers.get("authorization")
        )
        _require_content_type(request, "application/json")
        body = await _bounded_body(request, 1024)
        if body:
            try:
                value = json.loads(body.decode("utf-8"))
            except (UnicodeDecodeError, json.JSONDecodeError) as error:
                raise ReceiverError(400, "Completion JSON is invalid") from error
            if value != {}:
                raise ReceiverError(400, "Completion body must be an empty object")
        state = await run_in_threadpool(receiver.complete, device, session_id)
        return {"state": state}

    @application.delete("/v1/upload-sessions/{session_id}")
    async def cancel(session_id: str, request: Request):
        receiver = service(request)
        device = await run_in_threadpool(
            receiver.authenticate, request.headers.get("authorization")
        )
        state = await run_in_threadpool(receiver.cancel, device, session_id)
        return {"state": state}

    return application


app = create_app()
