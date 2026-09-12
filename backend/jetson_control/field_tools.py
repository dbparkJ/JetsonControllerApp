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

RUN_NAME = re.compile(r"run-(\d{8}T\d{6}\.\d{6}Z)-\d+\.log")
PIPELINE_ID = re.compile(r"[A-Za-z0-9][A-Za-z0-9_.-]{0,63}")


def safe_read(path: Path, limit: int = 65536, tail: bool = False) -> bytes:
    fd = os.open(path, os.O_RDONLY | os.O_NOFOLLOW)
    try:
        metadata = os.fstat(fd)
        if not stat.S_ISREG(metadata.st_mode):
            raise ValueError("Not a regular file")
        return os.pread(fd, limit, max(0, metadata.st_size - limit) if tail else 0)
    finally:
        os.close(fd)


def run_history(logs_root: Path, pipelines: list, offset: int, limit: int) -> dict:
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
            runs.append(dict(id=pipeline_id + '/' + path.name, pipelineId=pipeline_id,
                label=pipeline.get('label', pipeline_id), logId=path.name,
                startedAt=datetime.strptime(stamp, '%Y%m%dT%H%M%S.%fZ').replace(tzinfo=timezone.utc).isoformat(),
                finishedAt=footer[1] if footer else None, state=state,
                exitCode=int(footer[2]) if footer else None))
        except (OSError, ValueError):
            continue
    return dict(runs=runs, nextOffset=offset + limit if offset + limit < len(candidates) else None)


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


def register_field_routes(app, authenticated, paths, config, pipelines, sensor_bridge, storage):
    terminal_lock = threading.Lock()

    @app.get('/v1/task-runs', dependencies=authenticated)
    async def history(offset: int = Query(0, ge=0), limit: int = Query(30, ge=1, le=100)):
        def read():
            return run_history(paths.pipeline_logs, pipelines.list_pipelines(), offset, limit)
        return await run_in_threadpool(read)

    @app.get('/v1/task-runs/{pipeline_id}/{log_id}/route', dependencies=authenticated)
    async def route(pipeline_id: str, log_id: str):
        if not PIPELINE_ID.fullmatch(pipeline_id) or not RUN_NAME.fullmatch(log_id):
            raise HTTPException(400, 'Invalid run')
        directory = paths.pipeline_logs / pipeline_id
        if directory.is_symlink():
            raise HTTPException(400, 'Unsafe run directory')
        def read():
            try:
                lines = safe_read(directory / (log_id + '.route.jsonl'), 8 * 1024 * 1024).decode().splitlines()
            except FileNotFoundError:
                return {'points': []}
            points = []
            for line in lines:
                try:
                    point = json.loads(line)
                    if -90 <= point['latitude'] <= 90 and -180 <= point['longitude'] <= 180:
                        points.append(point)
                except (ValueError, KeyError, TypeError):
                    continue
            return {'points': points}
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
