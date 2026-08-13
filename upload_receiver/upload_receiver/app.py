from __future__ import annotations

import json
from contextlib import asynccontextmanager

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse, PlainTextResponse
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
        return JSONResponse(status_code=status, content={"sessionId": session_id})

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
