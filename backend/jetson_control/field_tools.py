"""Authenticated field capture, bounded terminal, and persistent run history."""
from __future__ import annotations

import base64
import json
import os
import pwd
import re
import selectors
import signal
import stat
import subprocess
import threading
import time
from datetime import datetime, timezone
from pathlib import Path

from fastapi import HTTPException, Query
from pydantic import BaseModel, Field
from starlette.concurrency import run_in_threadpool

from .field_quality import (
    FIELD_QUALITY_SCHEMA_VERSION,
    attach_route_indexes,
    summarize_quality,
)
from .local_trash import TrashConflict
from .run_context import RunContextError, RunContextNotFound

RUN_NAME = re.compile(r"run-(\d{8}T\d{6}\.\d{6}Z)-\d+\.log")
PIPELINE_ID = re.compile(r"[A-Za-z0-9][A-Za-z0-9_.-]{0,63}")
MAX_QUALITY_SUMMARY_BYTES = 256 * 1024
MAX_QUALITY_EVIDENCE_BYTES = 8 * 1024 * 1024


def safe_read(path: Path, limit: int = 65536, tail: bool = False) -> bytes:
    fd = os.open(path, os.O_RDONLY | os.O_NOFOLLOW)
    try:
        metadata = os.fstat(fd)
        if not stat.S_ISREG(metadata.st_mode):
            raise ValueError("Not a regular file")
        return os.pread(fd, limit, max(0, metadata.st_size - limit) if tail else 0)
    finally:
        os.close(fd)


def read_run_quality(path: Path, route_points=None, allow_evidence_fallback: bool = False):
    """Read a bounded persisted summary, deriving it from evidence only for one run."""
    summary_path = Path(str(path) + '.quality.json')
    try:
        encoded = safe_read(summary_path, MAX_QUALITY_SUMMARY_BYTES + 1)
        if len(encoded) > MAX_QUALITY_SUMMARY_BYTES:
            return None
        summary = json.loads(encoded.decode('utf-8'))
        if (not isinstance(summary, dict)
                or summary.get('schemaVersion') != FIELD_QUALITY_SCHEMA_VERSION
                or summary.get('sampleState') not in ('NO_SAMPLES', 'UNKNOWN', 'INSUFFICIENT_TIMING', 'OBSERVED')
                or not isinstance(summary.get('problemIntervals'), list)
                or not isinstance(summary.get('sensors'), list)):
            raise ValueError('Invalid quality summary')
    except (FileNotFoundError, OSError, UnicodeDecodeError, json.JSONDecodeError, ValueError):
        if not allow_evidence_fallback:
            return None
        evidence_path = Path(str(path) + '.quality.jsonl')
        try:
            encoded = safe_read(evidence_path, MAX_QUALITY_EVIDENCE_BYTES + 1)
        except OSError:
            return None
        truncated = len(encoded) > MAX_QUALITY_EVIDENCE_BYTES
        encoded = encoded[:MAX_QUALITY_EVIDENCE_BYTES]
        if truncated:
            encoded = encoded.rsplit(b'\n', 1)[0]
        observations = []
        for line in encoded.splitlines():
            try:
                value = json.loads(line)
                if isinstance(value, dict):
                    observations.append(value)
            except (UnicodeDecodeError, json.JSONDecodeError):
                continue
        summary = summarize_quality(observations, truncated=truncated)
    if route_points is not None:
        return attach_route_indexes(summary, route_points)
    return summary


def run_history(
    logs_root: Path,
    pipelines: list,
    offset: int,
    limit: int,
    run_context=None,
) -> dict:
    known = {str(p['id']): p for p in pipelines}
    candidates = []
    if logs_root.exists():
        for directory in logs_root.iterdir():
            if directory.is_symlink() or not directory.is_dir() or not PIPELINE_ID.fullmatch(directory.name):
                continue
            for path in directory.glob('run-*.log'):
                match = RUN_NAME.fullmatch(path.name)
                if match and not path.is_symlink():
                    candidates.append((match.group(1), directory.name, path))
    candidates.sort(reverse=True)
    latest = {}
    for stamp, pipeline_id, path in candidates:
        latest.setdefault(pipeline_id, path.name)
    runs = []
    for stamp, pipeline_id, path in candidates[offset:offset + limit]:
        try:
            end = safe_read(path, tail=True).decode('utf-8', errors='replace')
            footer = re.search(r'=== Jetson pipeline run finished ===\nfinished_at=([^\n]+)\nexit_code=(\d+)\n?$', end)
            pipeline = known.get(pipeline_id, {})
            active = latest[pipeline_id] == path.name and pipeline.get('state') in ('RUNNING', 'STARTING', 'STOPPING', 'RETRYING')
            state = ('COMPLETED' if int(footer[2]) == 0 else 'STOPPED' if int(footer[2]) in (130, 143) else 'FAILED') if footer else ('RUNNING' if active else 'UNKNOWN')
            run = dict(id=pipeline_id + '/' + path.name, pipelineId=pipeline_id,
                label=pipeline.get('label', pipeline_id), logId=path.name,
                startedAt=datetime.strptime(stamp, '%Y%m%dT%H%M%S.%fZ').replace(tzinfo=timezone.utc).isoformat(),
                finishedAt=footer[1] if footer else None, state=state,
                exitCode=int(footer[2]) if footer else None,
                quality=read_run_quality(path))
            if run_context is not None:
                try:
                    canonical = run_context.get_run(run['id'])
                except RunContextNotFound:
                    canonical = None
                if isinstance(canonical, dict):
                    for key in (
                        'active', 'state', 'startedAt', 'finishedAt', 'exitCode',
                        'stopReason', 'clientRequestId', 'contextSnapshot',
                        'policySnapshot', 'preflightSnapshot', 'sourceRevision',
                        'sourceDirty', 'release', 'configRevision', 'output',
                        'uploadContext',
                    ):
                        if key in canonical:
                            run[key] = canonical[key]
            runs.append(run)
        except (OSError, ValueError):
            continue
    return dict(runs=runs, nextOffset=offset + limit if offset + limit < len(candidates) else None)


def delete_run_history(
    logs_root: Path,
    pipelines: list,
    pipeline_id: str,
    log_id: str,
    *,
    run_context=None,
    trash=None,
) -> dict:
    """Delete one inactive run's log/route, never its acquisition output directory."""
    if not PIPELINE_ID.fullmatch(pipeline_id) or not RUN_NAME.fullmatch(log_id):
        raise HTTPException(400, 'Invalid run')
    root_fd = os.open(logs_root, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW)
    try:
        directory_fd = os.open(pipeline_id, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW, dir_fd=root_fd)
    finally:
        os.close(root_fd)
    try:
        names = [name for name in os.listdir(directory_fd) if RUN_NAME.fullmatch(name)
                 and stat.S_ISREG(os.stat(name, dir_fd=directory_fd, follow_symlinks=False).st_mode)]
        current = next((p for p in pipelines if p['id'] == pipeline_id), {})
        if run_context is not None:
            try:
                canonical = run_context.get_run(pipeline_id + '/' + log_id)
            except RunContextNotFound:
                canonical = None
            if isinstance(canonical, dict) and canonical.get('active') is True:
                raise HTTPException(409, '실행 중인 작업 이력은 삭제할 수 없습니다. 작업을 중지한 뒤 다시 확인하세요.')
        if names and log_id == max(names) and current.get('state') in ('RUNNING', 'STARTING', 'STOPPING', 'RETRYING'):
            raise HTTPException(409, '실행 중인 작업 이력은 삭제할 수 없습니다. 작업을 중지한 뒤 다시 확인하세요.')
        metadata = os.stat(log_id, dir_fd=directory_fd, follow_symlinks=False)
        if not stat.S_ISREG(metadata.st_mode):
            raise HTTPException(400, 'Unsafe run log')
        sidecars = []
        for sidecar_id, label in (
            (log_id + '.route.jsonl', 'route'),
            (log_id + '.quality.jsonl', 'quality evidence'),
            (log_id + '.quality.json', 'quality summary'),
        ):
            try:
                sidecar = os.stat(sidecar_id, dir_fd=directory_fd, follow_symlinks=False)
            except FileNotFoundError:
                continue
            if not stat.S_ISREG(sidecar.st_mode):
                raise HTTPException(400, 'Unsafe run ' + label)
            sidecars.append(sidecar_id)
        if trash is not None:
            return trash.trash_run_history(logs_root, pipeline_id, log_id)
        for sidecar_id in sidecars:
            os.unlink(sidecar_id, dir_fd=directory_fd)
        os.unlink(log_id, dir_fd=directory_fd)
        return {'deleted': True}
    finally:
        os.close(directory_fd)


class RunDeletionRequest(BaseModel):
    confirmed: bool = False


class TerminalRequest(BaseModel):
    command: str = Field(min_length=1, max_length=4096)


def terminal(command: str, username: str) -> dict:
    """Run as the configured acquisition user, never as root; bound time/output."""
    user = pwd.getpwnam(username)
    if user.pw_uid == 0:
        raise ValueError('원격 터미널에는 root가 아닌 pipeline_user 설정이 필요합니다.')
    args = ['/bin/sh', '-lc', command]
    if os.geteuid() == 0:
        args = ['/usr/sbin/runuser', '-u', username, '--'] + args
    elif os.geteuid() != user.pw_uid:
        raise ValueError('API 서비스 사용자와 pipeline_user 권한이 맞지 않습니다.')
    process = subprocess.Popen(args, cwd=user.pw_dir, env={'PATH': '/usr/local/bin:/usr/bin:/bin',
        'HOME': user.pw_dir, 'LANG': 'C.UTF-8', 'USER': username}, stdin=subprocess.DEVNULL,
        stdout=subprocess.PIPE, stderr=subprocess.STDOUT, start_new_session=True)
    output = bytearray()
    timed_out = False
    truncated = False
    selector = selectors.DefaultSelector()
    try:
        selector.register(process.stdout, selectors.EVENT_READ)
        deadline = time.monotonic() + 15
        while selector.get_map():
            if time.monotonic() >= deadline:
                timed_out = True
                break
            for key, _ in selector.select(0.1):
                chunk = os.read(key.fileobj.fileno(), 8192)
                if not chunk:
                    selector.unregister(key.fileobj)
                    break
                output.extend(chunk[:65536 - len(output)])
                if len(output) >= 65536:
                    truncated = True
                    break
            if truncated:
                break
        if not (timed_out or truncated):
            try:
                process.wait(timeout=max(0.01, deadline - time.monotonic()))
            except subprocess.TimeoutExpired:
                timed_out = True
    finally:
        try:
            os.killpg(process.pid, signal.SIGKILL)
        except ProcessLookupError:
            pass
        process.wait()
        selector.close()
        process.stdout.close()
    return dict(output=output.decode('utf-8', errors='replace'), exitCode=process.returncode,
                timedOut=timed_out, truncated=truncated)


def register_field_routes(
    app,
    authenticated,
    paths,
    config,
    pipelines,
    sensor_bridge,
    storage,
    *,
    run_context=None,
    trash=None,
):
    terminal_lock = threading.Lock()

    @app.get('/v1/task-runs', dependencies=authenticated)
    async def history(offset: int = Query(0, ge=0), limit: int = Query(30, ge=1, le=100)):
        def read():
            return run_history(
                paths.pipeline_logs,
                pipelines.list_pipelines(),
                offset,
                limit,
                run_context=run_context,
            )
        return await run_in_threadpool(read)

    @app.delete('/v1/task-runs/{pipeline_id}/{log_id}', dependencies=authenticated)
    async def delete_history(pipeline_id: str, log_id: str, request: RunDeletionRequest):
        if not request.confirmed:
            raise HTTPException(400, '작업 이력 삭제 확인이 필요합니다.')
        try:
            return await run_in_threadpool(
                delete_run_history,
                paths.pipeline_logs,
                pipelines.list_pipelines(),
                pipeline_id,
                log_id,
                run_context=run_context,
                trash=trash,
            )
        except FileNotFoundError as error:
            raise HTTPException(404, '작업 이력을 찾지 못했습니다. 목록을 새로고침해 주세요.') from error
        except TrashConflict as error:
            raise HTTPException(409, str(error)) from error
        except RunContextError as error:
            raise HTTPException(503, str(error)) from error
        except OSError as error:
            raise HTTPException(503, '작업 이력을 삭제하지 못했습니다.') from error

    @app.get('/v1/task-runs/{pipeline_id}/{log_id}/route', dependencies=authenticated)
    async def route(pipeline_id: str, log_id: str):
        if not PIPELINE_ID.fullmatch(pipeline_id) or not RUN_NAME.fullmatch(log_id):
            raise HTTPException(400, 'Invalid run')
        directory = paths.pipeline_logs / pipeline_id
        if directory.is_symlink():
            raise HTTPException(400, 'Unsafe run directory')
        def read():
            try:
                lines = safe_read(directory / (log_id + '.route.jsonl'), 8 * 1024 * 1024).decode(
                    'utf-8', errors='replace').splitlines()
            except FileNotFoundError:
                lines = []
            points = []
            for line in lines:
                try:
                    point = json.loads(line)
                    latitude = point['latitude']
                    longitude = point['longitude']
                    timestamp = point['timestamp']
                    if (isinstance(latitude, (int, float)) and not isinstance(latitude, bool)
                            and isinstance(longitude, (int, float)) and not isinstance(longitude, bool)
                            and isinstance(timestamp, int) and not isinstance(timestamp, bool)
                            and -90 <= latitude <= 90 and -180 <= longitude <= 180):
                        normalized = dict(latitude=latitude, longitude=longitude, timestamp=timestamp,
                                          segment=point.get('segment', 0) if isinstance(point.get('segment', 0), int) else 0)
                        for key in ('fixState', 'sensorState'):
                            if isinstance(point.get(key), str):
                                normalized[key] = point[key][:32]
                        points.append(normalized)
                except (ValueError, KeyError, TypeError):
                    continue
            quality = read_run_quality(directory / log_id, points, allow_evidence_fallback=True)
            return {'points': points, 'quality': quality}
        return await run_in_threadpool(read)

    @app.get('/v1/task-runs/{pipeline_id}/{log_id}/log', dependencies=authenticated)
    async def run_log(pipeline_id: str, log_id: str):
        if not PIPELINE_ID.fullmatch(pipeline_id) or not RUN_NAME.fullmatch(log_id):
            raise HTTPException(400, 'Invalid run')
        directory = paths.pipeline_logs / pipeline_id
        if directory.is_symlink():
            raise HTTPException(400, 'Unsafe run directory')
        try:
            content = await run_in_threadpool(safe_read, directory / log_id, 65536, True)
        except OSError as error:
            raise HTTPException(404, 'Run log unavailable') from error
        return dict(pipelineId=pipeline_id, logId=log_id, content=content.decode('utf-8', errors='replace'),
                    offset=0, nextOffset=0, sizeBytes=len(content), modifiedAt='', eof=True)

    @app.post('/v1/camera/capture', dependencies=authenticated)
    async def capture():
        def save():
            current = sensor_bridge.status()
            if not current.fresh or not current.camera.get('active') or not current.camera.get('previewAvailable'):
                raise HTTPException(409, 'Camera sensor is not active')
            content, revision = sensor_bridge.preview_frame_with_revision()
            if not content or len(content) > 16 * 1024 * 1024:
                raise HTTPException(409, 'Camera frame is unavailable')
            roots = storage.roots()
            root = roots.get('recordings')
            if root is None:
                raise HTTPException(409, 'recordings storage root is required')
            directory = root.path / 'captures'
            if directory.is_symlink():
                raise HTTPException(409, 'Unsafe capture directory')
            directory.mkdir(parents=True, exist_ok=True)
            name = 'GEO_' + datetime.now(timezone.utc).strftime('%Y%m%d_%H%M%S_%f') + '.jpg'
            destination = directory / name
            try:
                with destination.open('xb') as stream:
                    stream.write(content)
            except OSError:
                destination.unlink(missing_ok=True)
                raise
            return dict(name=name, rootId='recordings', relativePath='captures/' + name,
                        jpegBase64=base64.b64encode(content).decode('ascii'), revision=revision)
        try:
            return await run_in_threadpool(save)
        except OSError as error:
            raise HTTPException(503, '장치 캡처 저장 실패') from error

    @app.post('/v1/developer/terminal', dependencies=authenticated)
    async def execute(request: TerminalRequest):
        if not terminal_lock.acquire(blocking=False):
            raise HTTPException(409, 'A terminal command is already running')
        try:
            return await run_in_threadpool(terminal, request.command, config.pipeline_user)
        except (OSError, ValueError, KeyError) as error:
            raise HTTPException(409, str(error)) from error
        finally:
            terminal_lock.release()
