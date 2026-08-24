import os
import subprocess
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from jetson_control.system_control import (
    FanControlError,
    FanController,
    FanUnavailable,
    SystemTimeSynchronizer,
    TimeSyncConflict,
    TimeSyncError,
    read_time_sync_marker,
)


class FakeFanSystemctl:
    def __init__(self, *, loaded: bool = True, active: bool = True) -> None:
        self.loaded = loaded
        self.active = active
        self.commands = []

    def __call__(self, command, **_kwargs):
        self.commands.append(list(command))
        if command[1] == "show":
            output = (
                f"LoadState={'loaded' if self.loaded else 'not-found'}\n"
                f"ActiveState={'active' if self.active else 'inactive'}\n"
            )
            return subprocess.CompletedProcess(command, 0, output, "")
        if command[1] == "stop":
            self.active = False
        elif command[1] == "restart":
            self.active = True
        return subprocess.CompletedProcess(command, 0, "", "")


class SystemTimeSynchronizerTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)
        self.root.chmod(0o700)
        self.marker = self.root / "time-synchronized.json"

    def tearDown(self) -> None:
        self.temporary.cleanup()

    def test_sets_clock_verifies_it_and_writes_trusted_marker(self) -> None:
        requested = 1_777_000_123_456
        current = {"seconds": (requested - 60_000) / 1000.0}
        commands = []
        clock_change_callbacks = []

        def run(command, **_kwargs):
            commands.append(list(command))
            current["seconds"] = requested / 1000.0
            return subprocess.CompletedProcess(command, 0, "", "")

        synchronizer = SystemTimeSynchronizer(
            self.marker,
            run=run,
            clock=lambda: current["seconds"],
            marker_owner_uid=os.getuid(),
            on_clock_changed=lambda: clock_change_callbacks.append(True),
        )
        response = synchronizer.synchronize(requested)

        self.assertTrue(response["synchronized"])
        self.assertEqual(response["sourceTimeEpochMillis"], requested)
        self.assertEqual(
            commands,
            [["/usr/bin/date", "--utc", "--set", "@1777000123.456"]],
        )
        self.assertEqual(self.marker.stat().st_mode & 0o777, 0o644)
        self.assertEqual(clock_change_callbacks, [True])
        self.assertIsNotNone(
            read_time_sync_marker(
                self.marker,
                expected_owner_uid=os.getuid(),
            )
        )

    def test_rejects_large_second_correction_during_same_boot(self) -> None:
        requested = 1_777_000_000_000
        current = {"seconds": requested / 1000.0}
        synchronizer = SystemTimeSynchronizer(
            self.marker,
            run=lambda command, **kwargs: subprocess.CompletedProcess(command, 0, "", ""),
            clock=lambda: current["seconds"],
            marker_owner_uid=os.getuid(),
        )
        synchronizer.synchronize(requested)

        with self.assertRaisesRegex(TimeSyncConflict, "only allowed once"):
            synchronizer.synchronize(requested + 10 * 60 * 1000)

    def test_bounded_second_sync_never_steps_an_active_capture_clock(self) -> None:
        requested = 1_777_000_000_000
        current = {"seconds": requested / 1000.0}
        commands = []
        callbacks = []
        synchronizer = SystemTimeSynchronizer(
            self.marker,
            run=lambda command, **kwargs: commands.append(list(command)),
            clock=lambda: current["seconds"],
            marker_owner_uid=os.getuid(),
            on_clock_changed=lambda: callbacks.append(True),
        )
        first = synchronizer.synchronize(requested)
        current["seconds"] += 30

        second = synchronizer.synchronize(requested)

        self.assertEqual(commands, [])
        self.assertEqual(callbacks, [True])
        self.assertEqual(second["sourceTimeEpochMillis"], first["sourceTimeEpochMillis"])
        self.assertEqual(second["synchronizedAtEpochMillis"], first["synchronizedAtEpochMillis"])

    def test_failed_date_command_does_not_release_pipelines(self) -> None:
        requested = 1_777_000_000_000
        synchronizer = SystemTimeSynchronizer(
            self.marker,
            run=lambda command, **kwargs: subprocess.CompletedProcess(
                command,
                1,
                "",
                "permission denied",
            ),
            clock=lambda: (requested - 60_000) / 1000.0,
            marker_owner_uid=os.getuid(),
        )

        with self.assertRaisesRegex(TimeSyncError, "permission denied"):
            synchronizer.synchronize(requested)
        self.assertFalse(self.marker.exists())

    def test_unsafe_marker_directory_is_rejected_before_clock_change(self) -> None:
        requested = 1_777_000_000_000
        commands = []
        self.root.chmod(0o777)
        synchronizer = SystemTimeSynchronizer(
            self.marker,
            run=lambda command, **kwargs: commands.append(list(command)),
            clock=lambda: (requested - 60_000) / 1000.0,
            marker_owner_uid=os.getuid(),
        )

        with self.assertRaisesRegex(TimeSyncError, "writable by others"):
            synchronizer.synchronize(requested)
        self.assertEqual(commands, [])
        self.assertFalse(self.marker.exists())

    def test_rejects_non_integer_and_implausible_mobile_times(self) -> None:
        synchronizer = SystemTimeSynchronizer(
            self.marker,
            marker_owner_uid=os.getuid(),
        )
        for value in (True, 1.5, 0, 4_102_444_800_000):
            with self.subTest(value=value), self.assertRaises(ValueError):
                synchronizer.synchronize(value)  # type: ignore[arg-type]


class FanControllerTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name) / "sys"
        self.hwmon = self.root / "devices" / "platform" / "pwm-fan" / "hwmon" / "hwmon0"
        self.hwmon.mkdir(parents=True)
        (self.hwmon / "pwm1").write_text("128\n", encoding="ascii")
        (self.hwmon / "pwm1_max").write_text("255\n", encoding="ascii")
        (self.hwmon / "fan1_input").write_text("3210\n", encoding="ascii")
        self.systemctl = FakeFanSystemctl()
        self.controller = FanController(sysfs_root=self.root, run=self.systemctl)

    def tearDown(self) -> None:
        self.temporary.cleanup()

    def test_reports_pwm_rpm_and_automatic_controller_state(self) -> None:
        status = self.controller.status()

        self.assertTrue(status["available"])
        self.assertEqual(status["mode"], "AUTO")
        self.assertEqual(status["percent"], 50)
        self.assertEqual(status["rpm"], 3210)
        self.assertTrue(status["autoAvailable"])

    def test_reads_legacy_jetson_tachometer_path(self) -> None:
        (self.hwmon / "fan1_input").unlink()
        tachometer = (
            self.root
            / "devices"
            / "generic_pwm_tachometer"
            / "hwmon"
            / "hwmon1"
        )
        tachometer.mkdir(parents=True)
        (tachometer / "rpm").write_text("2875\n", encoding="ascii")

        self.assertEqual(self.controller.status()["rpm"], 2875)

    def test_reads_jetson_pwm_tach_hwmon_name(self) -> None:
        (self.hwmon / "fan1_input").unlink()
        tachometer = self.root / "class" / "hwmon" / "hwmon7"
        tachometer.mkdir(parents=True)
        (tachometer / "name").write_text("pwm_tach\n", encoding="ascii")
        (tachometer / "rpm").write_text("2762\n", encoding="ascii")

        self.assertEqual(self.controller.status()["rpm"], 2762)

    def test_manual_speed_stops_daemon_and_writes_bounded_pwm(self) -> None:
        status = self.controller.set("MANUAL", 40)

        self.assertEqual((self.hwmon / "pwm1").read_text(encoding="ascii"), "102\n")
        self.assertEqual(status["mode"], "MANUAL")
        self.assertEqual(status["percent"], 40)
        self.assertIn(
            ["/usr/bin/systemctl", "stop", "nvfancontrol.service"],
            self.systemctl.commands,
        )

    def test_automatic_mode_restarts_nvfancontrol(self) -> None:
        self.systemctl.active = False
        status = self.controller.set("AUTO")

        self.assertEqual(status["mode"], "AUTO")
        self.assertIn(
            ["/usr/bin/systemctl", "restart", "nvfancontrol.service"],
            self.systemctl.commands,
        )

    def test_unsafe_manual_percentages_are_rejected(self) -> None:
        for value in (0, 19, 101, True):
            with self.subTest(value=value), self.assertRaises(ValueError):
                self.controller.set("MANUAL", value)

    def test_write_failure_restores_automatic_controller(self) -> None:
        with patch.object(
            self.controller,
            "_write_pwm",
            side_effect=FanControlError("write failed"),
        ), self.assertRaisesRegex(FanControlError, "write failed"):
            self.controller.set_manual(50)

        self.assertTrue(self.systemctl.active)
        self.assertIn(
            ["/usr/bin/systemctl", "restart", "nvfancontrol.service"],
            self.systemctl.commands,
        )

    def test_auto_mode_reports_unavailable_without_nvfancontrol(self) -> None:
        self.systemctl.loaded = False
        self.systemctl.active = False
        with self.assertRaises(FanUnavailable):
            self.controller.set_auto()


if __name__ == "__main__":
    unittest.main()
