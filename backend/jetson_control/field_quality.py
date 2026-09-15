"""Persistable, threshold-free field sensor and RTK quality calculations."""
from __future__ import annotations

import bisect
import math
from typing import Dict, Iterable, List, Mapping, Optional, Sequence, Tuple

from .mobile_rtk import (
    RTK_FIX_FIXED,
    RTK_FIX_NO_SAMPLE,
    RTK_FIX_UNKNOWN,
    classify_rtk_fix,
)


FIELD_QUALITY_SCHEMA_VERSION = 1
DEFAULT_OBSERVATION_WINDOW_MILLIS = 2_000
MAX_PROBLEM_INTERVALS = 512

REQUIREMENT_REQUIRED = "REQUIRED"
REQUIREMENT_OPTIONAL = "OPTIONAL"
REQUIREMENT_UNSPECIFIED = "UNSPECIFIED"

SENSOR_ACTIVE = "ACTIVE"
SENSOR_STALE = "STALE"
SENSOR_LOST = "LOST"
SENSOR_ERROR = "ERROR"
SENSOR_NOT_CONFIGURED = "NOT_CONFIGURED"
SENSOR_UNKNOWN = "UNKNOWN"


def _integer(value: object) -> Optional[int]:
    if isinstance(value, bool):
        return None
    try:
        result = int(value)
    except (TypeError, ValueError, OverflowError):
        return None
    return result


def _boolean(value: object) -> bool:
    return value is True


def _mapping(value: object) -> Mapping[str, object]:
    return value if isinstance(value, dict) else {}


def normalize_requirement(value: object) -> str:
    normalized = str(value).strip().upper() if value is not None else ""
    if normalized in (REQUIREMENT_REQUIRED, REQUIREMENT_OPTIONAL):
        return normalized
    return REQUIREMENT_UNSPECIFIED


def _sensor_payloads(snapshot: object) -> Tuple[bool, bool, Optional[int], Dict[str, Mapping[str, object]]]:
    if snapshot is None:
        return False, False, None, {"camera": {}, "gnss": {}, "imu": {}}
    available = getattr(snapshot, "available", True) is True
    fresh = getattr(snapshot, "fresh", False) is True
    updated_at = _integer(getattr(snapshot, "updated_at_epoch_millis", None))
    return available, fresh, updated_at, {
        "camera": _mapping(getattr(snapshot, "camera", {})),
        "gnss": _mapping(getattr(snapshot, "gnss", {})),
        "imu": _mapping(getattr(snapshot, "imu", {})),
    }


def _sample_timestamp(sensor: str, payload: Mapping[str, object]) -> Optional[int]:
    key = "lastFrameAtEpochMillis" if sensor == "camera" else "lastSampleAtEpochMillis"
    value = _integer(payload.get(key))
    return value if value is not None and value >= 0 else None


class FieldQualitySampler:
    """Turn bridge snapshots into compact observations with explicit uncertainty."""

    def __init__(
        self,
        sensor_requirements: Optional[Mapping[str, object]] = None,
        observation_window_millis: int = DEFAULT_OBSERVATION_WINDOW_MILLIS,
    ) -> None:
        requirements = sensor_requirements or {}
        self.requirements = {
            sensor: normalize_requirement(requirements.get(sensor))
            for sensor in ("camera", "gnss", "imu")
        }
        window = _integer(observation_window_millis)
        self.observation_window_millis = max(1, window or DEFAULT_OBSERVATION_WINDOW_MILLIS)
        self.previous_samples: Dict[str, int] = {}

    def observe(self, snapshot: object, observed_at_epoch_millis: int) -> Dict[str, object]:
        observed_at = _integer(observed_at_epoch_millis)
        if observed_at is None or observed_at < 0:
            raise ValueError("Observation time is invalid")
        available, fresh, updated_at, payloads = _sensor_payloads(snapshot)
        sensors: Dict[str, Dict[str, object]] = {}
        for sensor, payload in payloads.items():
            sample_at = _sample_timestamp(sensor, payload)
            configured = _boolean(payload.get("configured"))
            connected = _boolean(payload.get("connected"))
            active = _boolean(payload.get("active"))
            error = payload.get("error") if isinstance(payload.get("error"), str) else None
            clock_backward = (
                sample_at is not None
                and sensor in self.previous_samples
                and sample_at < self.previous_samples[sensor]
            )
            clock_future = sample_at is not None and sample_at > observed_at
            duplicate = (
                sample_at is not None
                and self.previous_samples.get(sensor) == sample_at
            )
            sample_too_old = (
                sample_at is not None
                and observed_at - sample_at > self.observation_window_millis
            )
            if not available:
                state = SENSOR_UNKNOWN
            elif not fresh:
                state = SENSOR_STALE
            elif error:
                state = SENSOR_ERROR
            elif active:
                if sample_at is None:
                    state = SENSOR_LOST
                elif duplicate or clock_backward or clock_future or sample_too_old:
                    state = SENSOR_STALE
                else:
                    state = SENSOR_ACTIVE
            elif configured or connected:
                state = SENSOR_LOST
            else:
                state = SENSOR_NOT_CONFIGURED
            if sample_at is not None and state == SENSOR_ACTIVE:
                self.previous_samples[sensor] = sample_at
            sensors[sensor] = {
                "requirement": self.requirements[sensor],
                "state": state,
                "sampleAtEpochMillis": sample_at,
                "clockIssue": clock_backward or clock_future,
            }
            if error:
                sensors[sensor]["error"] = error[:256]

        gnss = payloads["gnss"]
        gnss_state = sensors["gnss"]["state"]
        rtk_fix_state = classify_rtk_fix(
            gnss.get("fixType"),
            gnss.get("rtkStatus"),
            gnss.get("fixQuality"),
            has_sample=gnss_state == SENSOR_ACTIVE,
        )
        return {
            "schemaVersion": FIELD_QUALITY_SCHEMA_VERSION,
            "observedAtEpochMillis": observed_at,
            "observationWindowMillis": self.observation_window_millis,
            "bridgeAvailable": available,
            "bridgeFresh": fresh,
            "bridgeUpdatedAtEpochMillis": updated_at,
            "rtkFixState": rtk_fix_state,
            "sensors": sensors,
        }


def _valid_observations(observations: Iterable[object]) -> List[Dict[str, object]]:
    valid = []
    for value in observations:
        if not isinstance(value, dict) or value.get("schemaVersion") != FIELD_QUALITY_SCHEMA_VERSION:
            continue
        observed_at = _integer(value.get("observedAtEpochMillis"))
        if observed_at is None or observed_at < 0:
            continue
        window = _integer(value.get("observationWindowMillis"))
        normalized = dict(value)
        normalized["observedAtEpochMillis"] = observed_at
        normalized["observationWindowMillis"] = max(
            1, window or DEFAULT_OBSERVATION_WINDOW_MILLIS
        )
        valid.append(normalized)
    return valid


def _problem(
    kind: str,
    started_at: int,
    ended_at: Optional[int],
    *,
    sensor: Optional[str] = None,
    requirement: str = REQUIREMENT_UNSPECIFIED,
) -> Dict[str, object]:
    return {
        "kind": kind,
        "sensor": sensor,
        "requirement": normalize_requirement(requirement),
        "startedAtEpochMillis": started_at,
        "endedAtEpochMillis": ended_at,
        "durationMillis": ended_at - started_at if ended_at is not None else None,
        "startRoutePointIndex": None,
        "endRoutePointIndex": None,
    }


def _append_problem(problems: List[Dict[str, object]], value: Dict[str, object]) -> None:
    if problems:
        previous = problems[-1]
        if (
            previous["kind"] == value["kind"]
            and previous["sensor"] == value["sensor"]
            and previous["requirement"] == value["requirement"]
            and previous["endedAtEpochMillis"] == value["startedAtEpochMillis"]
            and value["endedAtEpochMillis"] is not None
        ):
            previous["endedAtEpochMillis"] = value["endedAtEpochMillis"]
            previous["durationMillis"] = (
                int(previous["endedAtEpochMillis"]) - int(previous["startedAtEpochMillis"])
            )
            return
    problems.append(value)


def _coalesce_problems(problems: List[Dict[str, object]]) -> List[Dict[str, object]]:
    grouped: Dict[Tuple[object, object, object], List[Dict[str, object]]] = {}
    for problem in problems:
        key = (problem.get("kind"), problem.get("sensor"), problem.get("requirement"))
        grouped.setdefault(key, []).append(dict(problem))
    merged = []
    for values in grouped.values():
        values.sort(key=lambda item: int(item["startedAtEpochMillis"]))
        current = None
        for value in values:
            if (
                current is not None
                and current.get("endedAtEpochMillis") == value.get("startedAtEpochMillis")
                and value.get("endedAtEpochMillis") is not None
            ):
                current["endedAtEpochMillis"] = value["endedAtEpochMillis"]
                current["durationMillis"] = (
                    int(current["endedAtEpochMillis"]) - int(current["startedAtEpochMillis"])
                )
            else:
                current = value
                merged.append(current)
    merged.sort(key=lambda item: (
        int(item["startedAtEpochMillis"]),
        str(item["kind"]),
        str(item.get("sensor") or ""),
    ))
    return merged


def _sensor_problem_kind(sensor: str, state: object, requirement: str) -> Optional[str]:
    prefix = "GNSS" if sensor == "gnss" else "SENSOR"
    if state == SENSOR_STALE:
        return prefix + "_STALE"
    if state == SENSOR_LOST:
        return prefix + "_LOST"
    if state == SENSOR_ERROR:
        return "SENSOR_ERROR"
    if state == SENSOR_UNKNOWN:
        return prefix + "_UNKNOWN"
    if state == SENSOR_NOT_CONFIGURED and requirement == REQUIREMENT_REQUIRED:
        return prefix + "_NOT_CONFIGURED"
    return None


def summarize_quality(
    observations: Iterable[object],
    *,
    truncated: bool = False,
) -> Dict[str, object]:
    """Calculate timing-weighted metrics without carrying a state through gaps."""
    values = _valid_observations(observations)
    sensor_names = sorted({
        str(sensor)
        for value in values
        for sensor in _mapping(value.get("sensors"))
        if isinstance(sensor, str) and sensor
    } | {"camera", "gnss", "imu"})
    durations = {
        sensor: {
            SENSOR_ACTIVE: 0,
            SENSOR_STALE: 0,
            SENSOR_LOST: 0,
            SENSOR_ERROR: 0,
            SENSOR_UNKNOWN: 0,
            SENSOR_NOT_CONFIGURED: 0,
        }
        for sensor in sensor_names
    }
    requirements = {sensor: REQUIREMENT_UNSPECIFIED for sensor in sensor_names}
    elapsed = 0
    observed = 0
    rtk_observed = 0
    fixed = 0
    problems: List[Dict[str, object]] = []
    high_watermark = int(values[0]["observedAtEpochMillis"]) if values else None

    for current, following in zip(values, values[1:]):
        start = int(current["observedAtEpochMillis"])
        end = int(following["observedAtEpochMillis"])
        delta = end - start
        if delta <= 0:
            _append_problem(problems, _problem("CLOCK_GAP", start, None))
            continue
        assert high_watermark is not None
        effective_start = max(start, high_watermark)
        if end <= effective_start:
            _append_problem(problems, _problem("CLOCK_GAP", start, None))
            continue
        window = int(current["observationWindowMillis"])
        covered_end = (
            effective_start if start < high_watermark else min(end, start + window)
        )
        covered_start = effective_start
        covered = max(0, covered_end - covered_start)
        elapsed += end - effective_start
        observed += covered
        current_sensors = _mapping(current.get("sensors"))
        gnss = _mapping(current_sensors.get("gnss"))
        gnss_sample = (
            gnss.get("state") == SENSOR_ACTIVE
            and current.get("rtkFixState") != RTK_FIX_NO_SAMPLE
            and covered > 0
        )
        gnss_measured = gnss_sample and current.get("rtkFixState") != RTK_FIX_UNKNOWN
        if gnss_measured:
            rtk_observed += covered
            if current.get("rtkFixState") == RTK_FIX_FIXED:
                fixed += covered
        if gnss_measured and current.get("rtkFixState") != RTK_FIX_FIXED:
            _append_problem(
                problems,
                _problem(
                    "RTK_NOT_FIXED",
                    covered_start,
                    covered_end,
                    sensor="gnss",
                    requirement=normalize_requirement(gnss.get("requirement")),
                ),
            )
        if gnss_sample and current.get("rtkFixState") == RTK_FIX_UNKNOWN:
            _append_problem(
                problems,
                _problem(
                    "RTK_UNKNOWN",
                    covered_start,
                    covered_end,
                    sensor="gnss",
                    requirement=normalize_requirement(gnss.get("requirement")),
                ),
            )
        for sensor in sensor_names:
            detail = _mapping(current_sensors.get(sensor))
            requirement = normalize_requirement(detail.get("requirement"))
            requirements[sensor] = requirement
            state = detail.get("state")
            if state in durations[sensor]:
                durations[sensor][str(state)] += covered
            problem_kind = _sensor_problem_kind(sensor, state, requirement)
            if problem_kind is not None and covered > 0:
                _append_problem(
                    problems,
                    _problem(
                        problem_kind,
                        covered_start,
                        covered_end,
                        sensor=sensor,
                        requirement=requirement,
                    ),
                )
            if detail.get("clockIssue") is True and covered > 0:
                _append_problem(
                    problems,
                    _problem(
                        "CLOCK_GAP",
                        covered_start,
                        covered_end,
                        sensor=sensor,
                        requirement=requirement,
                    ),
                )
        if end > max(effective_start, covered_end):
            _append_problem(problems, _problem("CLOCK_GAP", max(effective_start, covered_end), end))
        high_watermark = max(high_watermark, end)

    problems = _coalesce_problems(problems)
    problem_total = len(problems)
    clipped_problems = problems[:MAX_PROBLEM_INTERVALS]
    gnss_samples = [
        value for value in values
        if _mapping(_mapping(value.get("sensors")).get("gnss")).get("state") == SENSOR_ACTIVE
        and value.get("rtkFixState") != RTK_FIX_NO_SAMPLE
    ]
    has_recognized_rtk_sample = any(
        value.get("rtkFixState") != RTK_FIX_UNKNOWN for value in gnss_samples
    )
    sample_state = (
        "NO_SAMPLES" if not gnss_samples else
        "OBSERVED" if rtk_observed > 0 else
        "INSUFFICIENT_TIMING" if has_recognized_rtk_sample else
        "UNKNOWN"
    )
    sensors = []
    for sensor in sensor_names:
        state_durations = durations[sensor]
        sensor_active = state_durations[SENSOR_ACTIVE]
        sensor_sample_state = (
            "INSUFFICIENT_TIMING" if observed == 0 and values else
            "OBSERVED" if sensor_active > 0 else
            "NO_SAMPLES"
        )
        sensors.append({
            "sensor": sensor,
            "requirement": requirements[sensor],
            "sampleState": sensor_sample_state,
            "observedDurationMillis": observed if observed > 0 else None,
            "activeDurationMillis": state_durations[SENSOR_ACTIVE] if observed > 0 else None,
            "staleDurationMillis": state_durations[SENSOR_STALE] if observed > 0 else None,
            "lostDurationMillis": state_durations[SENSOR_LOST] if observed > 0 else None,
            "unknownDurationMillis": state_durations[SENSOR_UNKNOWN] if observed > 0 else None,
            "notConfiguredDurationMillis": state_durations[SENSOR_NOT_CONFIGURED] if observed > 0 else None,
            "problemCount": sum(1 for problem in problems if problem.get("sensor") == sensor),
        })
    return {
        "schemaVersion": FIELD_QUALITY_SCHEMA_VERSION,
        "sampleState": sample_state,
        "firstObservedAtEpochMillis": int(values[0]["observedAtEpochMillis"]) if values else None,
        "lastObservedAtEpochMillis": high_watermark,
        "elapsedDurationMillis": elapsed if observed > 0 else None,
        "observedDurationMillis": observed if observed > 0 else None,
        "unknownDurationMillis": elapsed - observed if observed > 0 else None,
        "rtkObservedDurationMillis": rtk_observed if rtk_observed > 0 else None,
        "rtkUnknownDurationMillis": elapsed - rtk_observed if observed > 0 else None,
        "rtkFixDurationMillis": fixed if rtk_observed > 0 else None,
        "rtkFixRatio": round(fixed / rtk_observed, 6) if rtk_observed > 0 else None,
        "observationCount": len(values),
        "truncated": bool(truncated or problem_total > len(clipped_problems)),
        "problemIntervals": clipped_problems,
        "sensors": sensors,
    }


def attach_route_indexes(
    summary: Mapping[str, object], route_points: Sequence[Mapping[str, object]]
) -> Dict[str, object]:
    """Attach bracketing route indexes while preserving intervals without positions."""
    result = dict(summary)
    located = []
    for index, point in enumerate(route_points):
        timestamp = _integer(point.get("timestamp"))
        latitude = point.get("latitude")
        longitude = point.get("longitude")
        if (
            timestamp is not None
            and isinstance(latitude, (int, float))
            and not isinstance(latitude, bool)
            and isinstance(longitude, (int, float))
            and not isinstance(longitude, bool)
            and math.isfinite(float(latitude))
            and math.isfinite(float(longitude))
        ):
            located.append((timestamp, index))
    located.sort()
    timestamps = [item[0] for item in located]
    indexes = [item[1] for item in located]
    intervals = []
    for raw in result.get("problemIntervals", []):
        if not isinstance(raw, dict):
            continue
        interval = dict(raw)
        start = _integer(interval.get("startedAtEpochMillis"))
        end = _integer(interval.get("endedAtEpochMillis"))
        overlaps_route = (
            timestamps
            and start is not None
            and start <= timestamps[-1]
            and (end is None and start >= timestamps[0] or end is not None and end >= timestamps[0])
        )
        if overlaps_route:
            before = max(0, bisect.bisect_right(timestamps, start) - 1)
            interval["startRoutePointIndex"] = indexes[before]
            if end is not None:
                after = min(len(timestamps) - 1, bisect.bisect_left(timestamps, end))
                interval["endRoutePointIndex"] = indexes[after]
        intervals.append(interval)
    result["problemIntervals"] = intervals
    return result


def observation_route_fields(observation: Mapping[str, object]) -> Dict[str, str]:
    gnss = _mapping(_mapping(observation.get("sensors")).get("gnss"))
    return {
        "fixState": str(observation.get("rtkFixState", "UNKNOWN")),
        "sensorState": str(gnss.get("state", SENSOR_UNKNOWN)),
    }
