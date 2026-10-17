from __future__ import annotations

import hashlib
from typing import Mapping


def _text(value: object, limit: int) -> str:
    return str(value).strip()[:limit].strip()


def incident_key(prefix: str, identifier: str, suffix: str = "") -> str:
    identifier = identifier.strip()
    key = prefix + identifier + suffix
    if len(key) > 160:
        key = prefix + hashlib.sha256(identifier.encode("utf-8")).hexdigest()[:32] + suffix
    return key


def project_label(event: Mapping[str, object]) -> str | None:
    labels = [
        str(event.get(field, "")).strip()
        for field in ("surveyProjectLabel", "surveySectionLabel")
    ]
    value = " · ".join(label for label in labels if label) or str(event.get("label", "")).strip()
    return _text(value, 120) if value else None


def map_event(
    device_id: str, display_name: str | None, channel_id: str, event: Mapping[str, object]
) -> dict[str, object] | None:
    """Map only contract schema fields, before canonicalization and signing."""
    kind = event["type"]
    if kind == "device.heartbeat":
        return None
    severity = "INFO"
    impact = None
    actions: list[str] = []
    retry_count = 0
    template = "JETSON_DEVICE" if str(kind).startswith("device.") else "ROADINVENTORY_MMS"
    detail_fields: tuple[str, ...]
    if kind == "device.temperature":
        level = event["level"]
        severity = "RECOVERY" if level == "NORMAL" else str(level)
        status = {"WARNING": "과열 경고", "CRITICAL": "과열 위험", "NORMAL": "온도 정상"}[level]
        key = incident_key("jetson:", device_id, ":temperature")
        detail_fields = ("temperatureC", "thresholdC")
        if level != "NORMAL":
            impact = "수집 속도 저하 가능" if level == "WARNING" else "장치 보호를 위해 꺼질 수 있음"
            actions = ["장치 환기 확인"]
            if level == "CRITICAL":
                actions.append("수집 중지 검토")
    elif kind == "device.storage":
        level = event["level"]
        severity = "RECOVERY" if level == "NORMAL" else str(level)
        status = {"WARNING": "저장 공간 부족", "CRITICAL": "저장 공간 위험", "NORMAL": "저장 공간 정상"}[level]
        key = incident_key("jetson:", device_id, ":storage")
        detail_fields = ("freePercent",)
        if level != "NORMAL":
            impact = "새 수집을 저장하지 못할 수 있음"
            actions = ["전송 끝난 수집 정리"]
    elif kind in {"device.missing", "device.recovered"}:
        missing = kind == "device.missing"
        severity = "WARNING" if missing else "RECOVERY"
        status = "응답 없음" if missing else "응답 복귀"
        key = incident_key("jetson:", device_id, ":heartbeat")
        detail_fields = ()
        if missing:
            impact = "수집·전송이 멈췄을 수 있음"
            actions = ["장치 전원·네트워크 확인"]
    elif kind in {"run.started", "run.finished"}:
        status = "수집 시작" if kind == "run.started" else "수집 종료"
        key = incident_key("run:", str(event["runId"]))
        detail_fields = ("runId", "result", "durationSeconds", "bytes")
    elif kind == "upload.failed":
        retry_count = int(event["failureCount"]) - 1
        severity = "WARNING" if retry_count < 3 else "CRITICAL"
        status = "전송 실패" if retry_count < 3 else "전송 실패 반복"
        key = incident_key("upload:", str(event.get("runId") or event["jobId"]))
        impact = "서버에 결과가 없음"
        actions = ["앱에서 다시 시도", "수신 서버 확인"]
        detail_fields = ("runId", "jobId", "errorCode")
    elif kind == "upload.completed":
        severity = "RECOVERY"
        status = "서버 수신 확인"
        key = incident_key("upload:", str(event["runId"]))
        detail_fields = ("runId", "files", "bytes")
    else:
        raise ValueError("Unsupported event type")
    lines = ["deviceId=" + device_id.strip()]
    for name in (*detail_fields, "occurredAt"):
        field = "bytesWritten" if name == "bytes" and kind == "run.finished" else name
        if field in event:
            value = str(event[field]).strip().replace("\r", " ").replace("\n", " ")
            lines.append(name + "=" + value)
    payload: dict[str, object] = {
        "template": template,
        "channelId": channel_id.strip(),
        "source": _text((display_name or "").strip() or "장치 " + device_id[:8], 100),
        "severity": severity,
        "status": status,
        "incidentKey": key,
        "details": _text("\n".join(lines), 1500),
        "actions": [_text(action, 200) for action in actions[:10]],
        "retryCount": retry_count,
    }
    project = project_label(event)
    if project:
        payload["project"] = project
    if impact:
        payload["impact"] = _text(impact, 500)
    healthy = event.get("lastNormalAt") or event.get("lastHealthyAt")
    if healthy:
        payload["lastHealthyAt"] = str(healthy).strip()
    return payload
