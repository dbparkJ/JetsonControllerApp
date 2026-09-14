"""Record device GNSS and quality evidence independently of mobile connectivity."""
import json
import math
import os
import re
import stat
import threading
import time
from pathlib import Path

from .field_quality import (
    FieldQualitySampler,
    observation_route_fields,
    summarize_quality,
)
from .sensors import SensorBridgeStore


MAX_ROUTE_BYTES = 8 * 1024 * 1024
MAX_QUALITY_EVIDENCE_BYTES = 8 * 1024 * 1024
MAX_QUALITY_SUMMARY_BYTES = 256 * 1024
SUMMARY_EVERY_OBSERVATIONS = 5
QUALITY_SIDECAR = re.compile(r"run-\d{8}T\d{6}\.\d{6}Z-\d+\.log\.quality\.(?:json|jsonl)")


class RouteRecorder:
    def __init__(
        self,
        log_path: Path,
        bridge_dir: Path,
        *,
        sensor_requirements=None,
        clock_millis=lambda: int(time.time() * 1000),
    ):
        self.log_path = Path(log_path)
        self.path = Path(str(log_path) + ".route.jsonl")
        self.quality_evidence_path = Path(str(log_path) + ".quality.jsonl")
        self.quality_summary_path = Path(str(log_path) + ".quality.json")
        self.bridge = SensorBridgeStore(bridge_dir)
        self.sampler = FieldQualitySampler(sensor_requirements)
        self.clock_millis = clock_millis
        self.stop_event = threading.Event()
        self.thread = threading.Thread(target=self._record, daemon=True)

    def start(self):
        self.thread.start()

    def close(self):
        self.stop_event.set()
        self.thread.join(timeout=3)

    @staticmethod
    def _open_new(path: Path):
        flags = os.O_WRONLY | os.O_CREAT | os.O_EXCL | getattr(os, "O_NOFOLLOW", 0)
        descriptor = os.open(path, flags, 0o640)
        return os.fdopen(descriptor, "w", encoding="utf-8")

    def _cleanup_orphan_quality(self):
        """Remove bounded sidecars after the runner has pruned their owning log."""
        try:
            entries = list(self.log_path.parent.iterdir())
        except OSError:
            return
        for path in entries:
            if not QUALITY_SIDECAR.fullmatch(path.name):
                continue
            log_name = path.name.rsplit(".quality.", 1)[0]
            if (self.log_path.parent / log_name).exists():
                continue
            try:
                metadata = os.lstat(path)
                if stat.S_ISREG(metadata.st_mode):
                    path.unlink()
            except OSError:
                continue

    def _write_summary(self, observations, truncated):
        summary = summarize_quality(observations, truncated=truncated)
        encoded = (json.dumps(summary, separators=(",", ":")) + "\n").encode("utf-8")
        if len(encoded) > MAX_QUALITY_SUMMARY_BYTES:
            return
        temporary = self.quality_summary_path.with_name(
            "." + self.quality_summary_path.name + f".{os.getpid()}.{time.monotonic_ns()}.tmp"
        )
        descriptor = None
        try:
            flags = os.O_WRONLY | os.O_CREAT | os.O_EXCL | getattr(os, "O_NOFOLLOW", 0)
            descriptor = os.open(temporary, flags, 0o640)
            with os.fdopen(descriptor, "wb") as stream:
                descriptor = None
                stream.write(encoded)
                stream.flush()
                os.fsync(stream.fileno())
            os.replace(temporary, self.quality_summary_path)
        except OSError:
            return
        finally:
            if descriptor is not None:
                os.close(descriptor)
            try:
                temporary.unlink()
            except FileNotFoundError:
                pass

    def _record(self):
        self._cleanup_orphan_quality()
        route_stream = None
        quality_stream = None
        try:
            route_stream = self._open_new(self.path)
        except OSError:
            pass
        try:
            quality_stream = self._open_new(self.quality_evidence_path)
        except OSError:
            pass
        if route_stream is None and quality_stream is None:
            return

        last_stamp = None
        segment = 0
        gap = False
        route_full = route_stream is None
        quality_full = quality_stream is None
        observations = []
        if not quality_full:
            self._write_summary(observations, truncated=False)
        try:
            while not self.stop_event.is_set():
                observed_at = self.clock_millis()
                try:
                    status = self.bridge.status()
                except (OSError, ValueError, TypeError):
                    status = None
                try:
                    observation = self.sampler.observe(status, observed_at)
                except (ValueError, TypeError, OverflowError):
                    observation = self.sampler.observe(None, max(0, int(time.time() * 1000)))

                if not quality_full and quality_stream is not None:
                    encoded = json.dumps(observation, separators=(",", ":")) + "\n"
                    if quality_stream.tell() + len(encoded.encode("utf-8")) <= MAX_QUALITY_EVIDENCE_BYTES:
                        try:
                            quality_stream.write(encoded)
                            quality_stream.flush()
                            observations.append(observation)
                            if len(observations) % SUMMARY_EVERY_OBSERVATIONS == 0:
                                self._write_summary(observations, truncated=False)
                        except OSError:
                            quality_full = True
                    else:
                        quality_full = True

                try:
                    point = status.gnss if status is not None else {}
                    gnss_observation = observation.get("sensors", {}).get("gnss", {})
                    lat, lon = point.get("latitude"), point.get("longitude")
                    stamp = point.get("lastSampleAtEpochMillis")
                    valid = (
                        getattr(status, "fresh", False) is True
                        and point.get("active") is True
                        and gnss_observation.get("state") == "ACTIVE"
                        and isinstance(stamp, int)
                        and not isinstance(stamp, bool)
                        and isinstance(lat, (float, int))
                        and not isinstance(lat, bool)
                        and isinstance(lon, (float, int))
                        and not isinstance(lon, bool)
                        and math.isfinite(lat)
                        and math.isfinite(lon)
                        and -90 <= lat <= 90
                        and -180 <= lon <= 180
                    )
                    if valid and stamp != last_stamp and not route_full and route_stream is not None:
                        if gap or (last_stamp is not None and (stamp < last_stamp or stamp - last_stamp > 15000)):
                            segment += 1
                        gap = False
                        last_stamp = stamp
                        route_point = dict(
                            latitude=lat,
                            longitude=lon,
                            timestamp=stamp,
                            segment=segment,
                            **observation_route_fields(observation),
                        )
                        encoded = json.dumps(route_point, separators=(",", ":")) + "\n"
                        if route_stream.tell() + len(encoded.encode("utf-8")) <= MAX_ROUTE_BYTES:
                            route_stream.write(encoded)
                            route_stream.flush()
                        else:
                            route_full = True
                    elif not valid:
                        gap = True
                except (OSError, ValueError, TypeError, AttributeError):
                    gap = True

                if route_full and quality_full:
                    break
                self.stop_event.wait(self.sampler.observation_window_millis / 1000.0)
        finally:
            if quality_stream is not None:
                try:
                    self._write_summary(observations, truncated=quality_full)
                finally:
                    quality_stream.close()
            if route_stream is not None:
                route_stream.close()
