from __future__ import annotations

import json
import os
import stat
import subprocess
import threading
import time
from pathlib import Path
from typing import Callable, Dict, Mapping, Optional, Tuple


DEFAULT_TIME_SYNC_MARKER = Path("/run/jetson-control/time-synchronized.json")


class SystemControlError(RuntimeError):
    pass


class TimeSyncError(SystemControlError):
    pass


class TimeSyncConflict(TimeSyncError):
    pass


class FanControlError(SystemControlError):
    pass


class FanUnavailable(FanControlError):
    pass


CommandRunner = Callable[..., subprocess.CompletedProcess]


def read_time_sync_marker(
    path: Path = DEFAULT_TIME_SYNC_MARKER,
    *,
    expected_owner_uid: int = 0,
) -> Optional[Dict[str, object]]:
    """Return a trusted boot-local time marker, or ``None`` when it is unsafe."""

    try:
        metadata = os.lstat(path)
        if (
            not stat.S_ISREG(metadata.st_mode)
            or metadata.st_uid != expected_owner_uid
            or metadata.st_mode & (stat.S_IWGRP | stat.S_IWOTH)
        ):
            return None
        with path.open("r", encoding="utf-8") as source:
            value = json.load(source)
    except (FileNotFoundError, json.JSONDecodeError, OSError):
        return None
    if not isinstance(value, dict):
        return None
    if value.get("schemaVersion") != 1 or value.get("source") != "MOBILE":
        return None
    for key in (
        "sourceTimeEpochMillis",
        "synchronizedAtEpochMillis",
        "offsetBeforeMillis",
    ):
        field = value.get(key)
        if isinstance(field, bool) or not isinstance(field, int):
            return None
    if value.get("synchronized") is not True:
        return None
    return dict(value)


class SystemTimeSynchronizer:
    MIN_EPOCH_MILLIS = 1_577_836_800_000  # 2020-01-01T00:00:00Z
    MAX_EPOCH_MILLIS = 4_102_444_800_000  # 2100-01-01T00:00:00Z
    SET_THRESHOLD_MILLIS = 1_000
    VERIFY_TOLERANCE_MILLIS = 5_000
    MAX_RESYNC_CORRECTION_MILLIS = 5 * 60 * 1_000

    def __init__(
        self,
        marker_path: Path = DEFAULT_TIME_SYNC_MARKER,
        *,
        run: CommandRunner = subprocess.run,
        clock: Callable[[], float] = time.time,
        date_command: Path = Path("/usr/bin/date"),
        marker_owner_uid: Optional[int] = None,
        on_clock_changed: Optional[Callable[[], None]] = None,
    ) -> None:
        self.marker_path = marker_path
        self._run = run
        self._clock = clock
        self.date_command = date_command
        self.marker_owner_uid = (
            os.geteuid() if marker_owner_uid is None else marker_owner_uid
        )
        self._on_clock_changed = on_clock_changed
        self._lock = threading.Lock()

    def status(self) -> Dict[str, object]:
        marker = read_time_sync_marker(
            self.marker_path,
            expected_owner_uid=self.marker_owner_uid,
        )
        response: Dict[str, object] = {
            "synchronized": marker is not None,
            "deviceTimeEpochMillis": int(self._clock() * 1000),
        }
        if marker is not None:
            response.update(marker)
        return response

    def synchronize(self, mobile_time_epoch_millis: int) -> Dict[str, object]:
        requested = self._validate_mobile_time(mobile_time_epoch_millis)
        with self._lock:
            self._validate_marker_directory()
            previous = read_time_sync_marker(
                self.marker_path,
                expected_owner_uid=self.marker_owner_uid,
            )
            before = int(self._clock() * 1000)
            offset = requested - before
            if (
                previous is not None
                and abs(offset) > self.MAX_RESYNC_CORRECTION_MILLIS
            ):
                raise TimeSyncConflict(
                    "A large clock correction is only allowed once per device boot"
                )

            clock_changed = abs(offset) > self.SET_THRESHOLD_MILLIS
            if clock_changed:
                seconds, milliseconds = divmod(requested, 1000)
                target = f"@{seconds}.{milliseconds:03d}"
                try:
                    result = self._run(
                        [str(self.date_command), "--utc", "--set", target],
                        capture_output=True,
                        text=True,
                        timeout=10,
                        check=False,
                        env={"LC_ALL": "C", "LANG": "C", "PATH": "/usr/bin:/bin"},
                    )
                except (OSError, subprocess.SubprocessError) as error:
                    raise TimeSyncError(f"Could not set the Jetson clock: {error}") from error
                if result.returncode != 0:
                    detail = (result.stderr or result.stdout or "date failed").strip()
                    raise TimeSyncError(f"Could not set the Jetson clock: {detail}")

            after = int(self._clock() * 1000)
            if abs(after - requested) > self.VERIFY_TOLERANCE_MILLIS:
                raise TimeSyncError("Jetson clock verification failed after synchronization")
            if (previous is None or clock_changed) and self._on_clock_changed is not None:
                try:
                    self._on_clock_changed()
                except Exception as error:
                    raise TimeSyncError(
                        "Clock changed but dependent authentication state could not be reset"
                    ) from error

            marker: Dict[str, object] = {
                "schemaVersion": 1,
                "synchronized": True,
                "source": "MOBILE",
                "sourceTimeEpochMillis": requested,
                "synchronizedAtEpochMillis": after,
                "offsetBeforeMillis": offset,
                "clockChanged": clock_changed,
            }
            self._write_marker(marker)
            return self.status()

    @classmethod
    def _validate_mobile_time(cls, value: int) -> int:
        if isinstance(value, bool) or not isinstance(value, int):
            raise ValueError("Mobile time must be an integer Unix timestamp in milliseconds")
        if value < cls.MIN_EPOCH_MILLIS or value >= cls.MAX_EPOCH_MILLIS:
            raise ValueError("Mobile time is outside the supported 2020-2100 range")
        return value

    def _write_marker(self, value: Mapping[str, object]) -> None:
        parent = self._validate_marker_directory()

        encoded = (json.dumps(value, sort_keys=True) + "\n").encode("utf-8")
        temporary = self.marker_path.with_name(
            f".{self.marker_path.name}.tmp-{os.getpid()}-{threading.get_ident()}"
        )
        flags = os.O_WRONLY | os.O_CREAT | os.O_EXCL
        if hasattr(os, "O_NOFOLLOW"):
            flags |= os.O_NOFOLLOW
        descriptor: Optional[int] = None
        try:
            descriptor = os.open(temporary, flags, 0o644)
            with os.fdopen(descriptor, "wb") as output:
                descriptor = None
                output.write(encoded)
                output.flush()
                os.fsync(output.fileno())
            os.chmod(temporary, 0o644)
            os.replace(temporary, self.marker_path)
            directory_descriptor = os.open(parent, os.O_RDONLY)
            try:
                os.fsync(directory_descriptor)
            finally:
                os.close(directory_descriptor)
        except OSError as error:
            raise TimeSyncError(f"Could not record time synchronization: {error}") from error
        finally:
            if descriptor is not None:
                os.close(descriptor)
            try:
                temporary.unlink()
            except FileNotFoundError:
                pass

    def _validate_marker_directory(self) -> Path:
        parent = self.marker_path.parent
        try:
            metadata = os.lstat(parent)
        except FileNotFoundError as error:
            raise TimeSyncError("Time synchronization runtime directory is missing") from error
        if (
            stat.S_ISLNK(metadata.st_mode)
            or not stat.S_ISDIR(metadata.st_mode)
            or metadata.st_uid != self.marker_owner_uid
        ):
            raise TimeSyncError("Time synchronization runtime directory is unsafe")
        if metadata.st_mode & (stat.S_IWGRP | stat.S_IWOTH):
            raise TimeSyncError("Time synchronization runtime directory is writable by others")
        return parent


class FanController:
    MIN_MANUAL_PERCENT = 20
    MAX_MANUAL_PERCENT = 100
    _FAN_NAMES = frozenset(
        {
            "pwmfan",
            "pwm-fan",
            "pwm_fan",
            "tegra_pwmfan",
            "tegra-pwmfan",
        }
    )
    _TACHOMETER_NAMES = frozenset(
        {
            "generic_pwm_tachometer",
            "pwm_tachometer",
            "pwm-tachometer",
        }
    )

    def __init__(
        self,
        *,
        sysfs_root: Path = Path("/sys"),
        run: CommandRunner = subprocess.run,
        systemctl: Path = Path("/usr/bin/systemctl"),
    ) -> None:
        self.sysfs_root = sysfs_root
        self._run = run
        self.systemctl = systemctl
        self._lock = threading.Lock()

    def status(self) -> Dict[str, object]:
        daemon_loaded, daemon_active = self._daemon_status()
        device = self._discover_device()
        if device is None:
            return {
                "available": False,
                "mode": "AUTO" if daemon_active else "UNAVAILABLE",
                "percent": None,
                "rpm": None,
                "pwm": None,
                "maxPwm": None,
                "controller": None,
                "autoAvailable": daemon_loaded,
                "minimumManualPercent": self.MIN_MANUAL_PERCENT,
            }

        pwm_path, maximum_path, rpm_path = device
        maximum = self._read_integer(maximum_path, default=255, minimum=1, maximum=65_535)
        pwm = self._read_integer(pwm_path, minimum=0, maximum=maximum)
        rpm = (
            self._read_integer(rpm_path, minimum=0, maximum=1_000_000)
            if rpm_path is not None
            else None
        )
        percent = int(round((pwm * 100.0) / maximum))
        return {
            "available": True,
            "mode": "AUTO" if daemon_active else "MANUAL",
            "percent": max(0, min(100, percent)),
            "rpm": rpm,
            "pwm": pwm,
            "maxPwm": maximum,
            "controller": str(pwm_path),
            "autoAvailable": daemon_loaded,
            "minimumManualPercent": self.MIN_MANUAL_PERCENT,
        }

    def set(self, mode: str, percent: Optional[int] = None) -> Dict[str, object]:
        if mode == "AUTO":
            if percent is not None:
                raise ValueError("Fan percent must be omitted in AUTO mode")
            return self.set_auto()
        if mode == "MANUAL":
            if percent is None:
                raise ValueError("Fan percent is required in MANUAL mode")
            return self.set_manual(percent)
        raise ValueError("Fan mode must be AUTO or MANUAL")

    def set_auto(self) -> Dict[str, object]:
        with self._lock:
            daemon_loaded, _ = self._daemon_status()
            if not daemon_loaded:
                raise FanUnavailable("Jetson automatic fan controller is unavailable")
            self._systemctl("restart")
            return self.status()

    def set_manual(self, percent: int) -> Dict[str, object]:
        if isinstance(percent, bool) or not isinstance(percent, int):
            raise ValueError("Fan percent must be an integer")
        if percent < self.MIN_MANUAL_PERCENT or percent > self.MAX_MANUAL_PERCENT:
            raise ValueError(
                f"Manual fan percent must be {self.MIN_MANUAL_PERCENT} to "
                f"{self.MAX_MANUAL_PERCENT}"
            )

        with self._lock:
            device = self._discover_device()
            if device is None:
                raise FanUnavailable("Jetson PWM fan control is unavailable")
            pwm_path, maximum_path, _ = device
            maximum = self._read_integer(
                maximum_path,
                default=255,
                minimum=1,
                maximum=65_535,
            )
            raw = max(1, min(maximum, int(round(maximum * percent / 100.0))))
            _, daemon_active = self._daemon_status()
            if daemon_active:
                self._systemctl("stop")
            try:
                self._write_pwm(pwm_path, raw)
                observed = self._read_integer(
                    pwm_path,
                    minimum=0,
                    maximum=maximum,
                )
                if observed != raw:
                    raise FanControlError("Jetson fan controller did not accept the requested speed")
            except Exception:
                if daemon_active:
                    try:
                        self._systemctl("restart")
                    except FanControlError:
                        pass
                raise
            return self.status()

    def _discover_device(self) -> Optional[Tuple[Path, Optional[Path], Optional[Path]]]:
        candidates = [
            self.sysfs_root / "devices" / "pwm-fan" / "target_pwm",
            *sorted(
                (self.sysfs_root / "devices" / "platform" / "pwm-fan" / "hwmon").glob(
                    "hwmon*/pwm1"
                )
            ),
            *sorted(
                (self.sysfs_root / "devices" / "platform" / "pwm-fan" / "hwmon").glob(
                    "hwmon*/target_pwm"
                )
            ),
        ]
        hwmon_root = self.sysfs_root / "class" / "hwmon"
        for directory in sorted(hwmon_root.glob("hwmon*")):
            name = self._read_text(directory / "name")
            if name is None or name.strip().lower() not in self._FAN_NAMES:
                continue
            candidates.extend((directory / "pwm1", directory / "target_pwm"))

        seen = set()
        for candidate in candidates:
            try:
                resolved = candidate.resolve(strict=True)
                resolved.relative_to(self.sysfs_root.resolve(strict=True))
            except (FileNotFoundError, OSError, ValueError):
                continue
            if resolved in seen or not resolved.is_file():
                continue
            seen.add(resolved)
            maximum = resolved.with_name("pwm1_max")
            if not maximum.is_file():
                maximum = None
            rpm = self._discover_rpm(resolved)
            return resolved, maximum, rpm
        return None

    def _discover_rpm(self, pwm_path: Path) -> Optional[Path]:
        candidates = [pwm_path.with_name("fan1_input"), pwm_path.with_name("rpm")]
        candidates.extend(
            sorted(
                (
                    self.sysfs_root
                    / "devices"
                    / "generic_pwm_tachometer"
                    / "hwmon"
                ).glob("hwmon*/rpm")
            )
        )
        hwmon_root = self.sysfs_root / "class" / "hwmon"
        for directory in sorted(hwmon_root.glob("hwmon*")):
            name = self._read_text(directory / "name")
            if name is None or name.strip().lower() not in self._TACHOMETER_NAMES:
                continue
            candidates.extend((directory / "rpm", directory / "fan1_input"))

        for candidate in candidates:
            try:
                resolved = candidate.resolve(strict=True)
                resolved.relative_to(self.sysfs_root.resolve(strict=True))
            except (FileNotFoundError, OSError, ValueError):
                continue
            if resolved.is_file():
                return resolved
        return None

    def _daemon_status(self) -> Tuple[bool, bool]:
        try:
            result = self._run(
                [
                    str(self.systemctl),
                    "show",
                    "nvfancontrol.service",
                    "--property=LoadState",
                    "--property=ActiveState",
                    "--no-pager",
                ],
                capture_output=True,
                text=True,
                timeout=10,
                check=False,
            )
        except (OSError, subprocess.SubprocessError):
            return False, False
        if result.returncode != 0:
            return False, False
        properties: Dict[str, str] = {}
        for line in result.stdout.splitlines():
            key, separator, value = line.partition("=")
            if separator:
                properties[key] = value
        return properties.get("LoadState") == "loaded", properties.get("ActiveState") == "active"

    def _systemctl(self, verb: str) -> None:
        if verb not in {"restart", "stop"}:
            raise ValueError("Unsupported fan controller action")
        try:
            result = self._run(
                [str(self.systemctl), verb, "nvfancontrol.service"],
                capture_output=True,
                text=True,
                timeout=30,
                check=False,
            )
        except (OSError, subprocess.SubprocessError) as error:
            raise FanControlError(f"Could not control nvfancontrol: {error}") from error
        if result.returncode != 0:
            detail = (result.stderr or result.stdout or "systemctl failed").strip()
            raise FanControlError(f"Could not control nvfancontrol: {detail}")

    @staticmethod
    def _read_text(path: Path) -> Optional[str]:
        try:
            return path.read_text(encoding="ascii").strip()
        except (OSError, UnicodeDecodeError):
            return None

    @classmethod
    def _read_integer(
        cls,
        path: Optional[Path],
        *,
        default: Optional[int] = None,
        minimum: int,
        maximum: int,
    ) -> int:
        if path is None:
            if default is None:
                raise FanControlError("Jetson fan status is unavailable")
            return default
        text = cls._read_text(path)
        try:
            value = int(text or "", 10)
        except ValueError as error:
            raise FanControlError("Jetson fan status is invalid") from error
        if value < minimum or value > maximum:
            raise FanControlError("Jetson fan status is outside the supported range")
        return value

    @staticmethod
    def _write_pwm(path: Path, value: int) -> None:
        flags = os.O_WRONLY | os.O_TRUNC
        if hasattr(os, "O_NOFOLLOW"):
            flags |= os.O_NOFOLLOW
        try:
            descriptor = os.open(path, flags)
            try:
                data = f"{value}\n".encode("ascii")
                if os.write(descriptor, data) != len(data):
                    raise OSError("short PWM write")
            finally:
                os.close(descriptor)
        except OSError as error:
            raise FanControlError(f"Could not set the Jetson fan speed: {error}") from error
