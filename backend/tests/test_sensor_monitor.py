import fcntl
import json
import os
import pwd
import signal
import tempfile
import unittest
from pathlib import Path
from unittest.mock import Mock, patch

from jetson_control.mobile_rtk import MobileRtkRelayRegistry, mobile_rtk_relay_environment
from jetson_control.sensor_handoff import SensorMonitorSettings, _open_lock
from jetson_control.sensor_monitor import (
    CameraProgressWatchdog, load_monitor_runtime, stop_child, supervise,
)
from jetson_control.sensors import SensorBridgeSnapshot


class SensorMonitorRuntimeTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)
        self.registry = self.root / "registry"
        self.pipeline_root = self.registry / "depthai-capture"
        self.release = self.pipeline_root / "releases" / "release-1"
        self.release.mkdir(parents=True)
        (self.release / "main.py").write_text("pass\n", encoding="utf-8")
        (self.release / "config.yaml").write_text("fps: 30\n", encoding="utf-8")
        for directory in (
            self.registry,
            self.pipeline_root,
            self.pipeline_root / "releases",
            self.release,
        ):
            directory.chmod(0o750)
        (self.release / "main.py").chmod(0o640)
        (self.release / "config.yaml").chmod(0o640)
        (self.pipeline_root / "current").symlink_to(self.release)

        self.virtualenv = self.root / "venv"
        python = self.virtualenv / "bin" / "python"
        python.parent.mkdir(parents=True)
        python.write_text("#!/bin/sh\n", encoding="utf-8")
        python.chmod(0o755)
        self.working = self.root / "working"
        self.working.mkdir()
        self.secrets_root = self.root / "secrets"
        self.secrets_root.mkdir(mode=0o700)
        manifest = {
            "schema_version": 1,
            "id": "depthai-capture",
            "release": self.release.name,
            "virtualenv": str(self.virtualenv),
            "python": str(python),
            "entrypoint": "main.py",
            "config": "config.yaml",
            "config_argument": "--config",
            "working_directory": str(self.working),
            "arguments": ["--output-dir", "/data/collections"],
            "user": pwd.getpwuid(os.getuid()).pw_name,
        }
        manifest_path = self.pipeline_root / "pipeline.json"
        manifest_path.write_text(
            json.dumps(manifest), encoding="utf-8"
        )
        manifest_path.chmod(0o644)
        self.settings = SensorMonitorSettings(
            pipeline_id="depthai-capture",
            bridge_dir=self.root / "bridge",
            registry_root=self.registry,
            monitor_arguments=("--monitor-only", "--allow-usb2", "--fps", "4"),
        )

    def tearDown(self) -> None:
        self.temporary.cleanup()

    def _write_policy(self, required=(), optional=()):
        path = self.pipeline_root / "run-policy.json"
        path.write_text(json.dumps({
            "schemaVersion": 1, "pipelineId": self.settings.pipeline_id,
            "configured": True, "requiredSensors": list(required),
            "optionalSensors": list(optional),
        }), encoding="utf-8")
        path.chmod(0o600)
        return path

    def test_runtime_uses_existing_required_camera_policy_without_new_settings(self):
        self._write_policy(required=("camera", "gnss"))
        runtime = load_monitor_runtime(
            self.settings, expected_owner_uid=os.getuid(), pipeline_environment={},
        )
        self.assertTrue(runtime.camera_required)

    def test_runtime_does_not_require_missing_or_optional_camera(self):
        for optional in (False, True):
            with self.subTest(optional=optional):
                if optional:
                    self._write_policy(required=("gnss",), optional=("camera",))
                runtime = load_monitor_runtime(
                    self.settings, expected_owner_uid=os.getuid(), pipeline_environment={},
                )
                self.assertFalse(runtime.camera_required)

    def test_runtime_rejects_untrusted_or_mismatched_camera_policy(self):
        path = self._write_policy(required=("camera",))
        path.chmod(0o666)
        with self.assertRaisesRegex(ValueError, "permissions are unsafe"):
            load_monitor_runtime(self.settings, expected_owner_uid=os.getuid())
        path.chmod(0o600)
        value = json.loads(path.read_text())
        value["pipelineId"] = "another-device"
        path.write_text(json.dumps(value))
        with self.assertRaisesRegex(ValueError, "run policy is invalid"):
            load_monitor_runtime(self.settings, expected_owner_uid=os.getuid())

    def test_runtime_appends_non_recording_low_bandwidth_arguments(self) -> None:
        runtime = load_monitor_runtime(
            self.settings,
            expected_owner_uid=os.getuid(),
            pipeline_env_root=self.secrets_root,
        )

        self.assertEqual(runtime.command[0], str(self.virtualenv / "bin" / "python"))
        self.assertEqual(
            runtime.command[-4:],
            ("--monitor-only", "--allow-usb2", "--fps", "4"),
        )
        self.assertEqual(
            runtime.environment["JETSON_PIPELINE_SENSOR_BRIDGE_DIR"],
            str(self.settings.bridge_dir),
        )
        self.assertNotIn("JETSON_PIPELINE_RESULTS_DIR", runtime.environment)
        self.assertEqual(runtime.release, self.release)

    def test_runtime_loads_protected_pipeline_environment(self) -> None:
        secrets = self.secrets_root / "depthai-capture.env"
        secrets.write_text(
            "# NTRIP credentials\n"
            "NTRIP_USERNAME='field user'\n"
            'NTRIP_PASSWORD="field-password"\n',
            encoding="utf-8",
        )
        secrets.chmod(0o600)

        runtime = load_monitor_runtime(
            self.settings,
            expected_owner_uid=os.getuid(),
            pipeline_env_root=self.secrets_root,
        )

        self.assertEqual(runtime.environment["NTRIP_USERNAME"], "field user")
        self.assertEqual(runtime.environment["NTRIP_PASSWORD"], "field-password")
        self.assertEqual(runtime.pipeline_environment["NTRIP_USERNAME"], "field user")

    def test_runtime_reuses_preloaded_pipeline_environment(self) -> None:
        unavailable_secrets_root = self.root / "unavailable-secrets"

        runtime = load_monitor_runtime(
            self.settings,
            expected_owner_uid=os.getuid(),
            pipeline_env_root=unavailable_secrets_root,
            pipeline_environment={
                "NTRIP_USERNAME": "field-user",
                "NTRIP_PASSWORD": "field-password",
            },
        )

        self.assertEqual(runtime.environment["NTRIP_USERNAME"], "field-user")
        self.assertEqual(runtime.environment["NTRIP_PASSWORD"], "field-password")

    def test_runtime_applies_current_relay_and_restores_base_route_after_unregister(self) -> None:
        marker = self.root / "relay.json"
        registry = MobileRtkRelayRegistry(marker, owner_uid=os.getuid())
        registry.register(self.settings.pipeline_id, "192.168.49.71", 32101)
        base = {"NTRIP_HOST": "caster.example", "NTRIP_PORT": "2101",
                "NTRIP_USERNAME": "field-user", "NTRIP_PASSWORD": "field-password"}

        def load():
            return load_monitor_runtime(
                self.settings, expected_owner_uid=os.getuid(),
                pipeline_environment=base, relay_path=marker,
            )

        runtime = load()
        self.assertEqual(runtime.environment["NTRIP_HOST"], "192.168.49.71")
        self.assertEqual(runtime.environment["NTRIP_PORT"], "32101")
        self.assertEqual(runtime.environment["JETSON_PIPELINE_MOBILE_RTK_RELAY"], "1")
        self.assertEqual(runtime.environment["NTRIP_PASSWORD"], base["NTRIP_PASSWORD"])
        self.assertEqual(runtime.pipeline_environment, base)
        registry.unregister(self.settings.pipeline_id)
        with patch.dict(os.environ, {"JETSON_PIPELINE_MOBILE_RTK_RELAY": "1"}):
            restored = load()
        self.assertEqual(restored.environment["NTRIP_HOST"], "caster.example")
        self.assertEqual(restored.environment["NTRIP_PORT"], "2101")
        self.assertNotIn("JETSON_PIPELINE_MOBILE_RTK_RELAY", restored.environment)
        self.assertEqual(restored.relay_environment, {})

    def test_runtime_rejects_public_pipeline_environment(self) -> None:
        secrets = self.secrets_root / "depthai-capture.env"
        secrets.write_text("NTRIP_PASSWORD=secret\n", encoding="utf-8")
        secrets.chmod(0o640)

        with self.assertRaisesRegex(ValueError, "secrets permissions are unsafe"):
            load_monitor_runtime(
                self.settings,
                expected_owner_uid=os.getuid(),
                pipeline_env_root=self.secrets_root,
            )

    def test_runtime_rejects_release_symlink_outside_registry(self) -> None:
        outside = self.root / "outside"
        outside.mkdir()
        (self.pipeline_root / "current").unlink()
        (self.pipeline_root / "current").symlink_to(outside)

        with self.assertRaisesRegex(ValueError, "leaves the release"):
            load_monitor_runtime(
                self.settings,
                expected_owner_uid=os.getuid(),
                pipeline_env_root=self.secrets_root,
            )

    def test_runtime_rejects_writable_or_wrong_owner_pipeline_metadata(self) -> None:
        manifest = self.pipeline_root / "pipeline.json"
        manifest.chmod(0o660)
        with self.assertRaisesRegex(ValueError, "permissions are unsafe"):
            load_monitor_runtime(
                self.settings,
                expected_owner_uid=os.getuid(),
            )

        manifest.chmod(0o640)
        with self.assertRaisesRegex(ValueError, "ownership or permissions are unsafe"):
            load_monitor_runtime(
                self.settings,
                expected_owner_uid=os.getuid() + 1,
                pipeline_env_root=self.secrets_root,
            )

    def test_runtime_rejects_writable_release_and_root_execution(self) -> None:
        self.release.chmod(0o770)
        with self.assertRaisesRegex(ValueError, "current release.*unsafe"):
            load_monitor_runtime(
                self.settings,
                expected_owner_uid=os.getuid(),
                pipeline_env_root=self.secrets_root,
            )

        self.release.chmod(0o750)
        manifest_path = self.pipeline_root / "pipeline.json"
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
        manifest["user"] = "root"
        manifest_path.write_text(json.dumps(manifest), encoding="utf-8")
        manifest_path.chmod(0o640)
        with self.assertRaisesRegex(ValueError, "must not run as root"):
            load_monitor_runtime(
                self.settings,
                expected_owner_uid=os.getuid(),
                pipeline_env_root=self.secrets_root,
            )

    def test_runtime_reloads_after_current_release_changes(self) -> None:
        second = self.pipeline_root / "releases" / "release-2"
        second.mkdir(mode=0o750)
        (second / "main.py").write_text("print('second')\n", encoding="utf-8")
        (second / "config.yaml").write_text("fps: 4\n", encoding="utf-8")
        (second / "main.py").chmod(0o640)
        (second / "config.yaml").chmod(0o640)
        manifest_path = self.pipeline_root / "pipeline.json"
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
        manifest["release"] = second.name
        manifest_path.write_text(json.dumps(manifest), encoding="utf-8")
        manifest_path.chmod(0o640)
        (self.pipeline_root / "current").unlink()
        (self.pipeline_root / "current").symlink_to(second)

        runtime = load_monitor_runtime(
            self.settings,
            expected_owner_uid=os.getuid(),
            pipeline_env_root=self.secrets_root,
        )

        self.assertEqual(runtime.release, second)
        self.assertIn(str(second / "main.py"), runtime.command)

    def test_stop_child_escalates_against_the_whole_process_group(self) -> None:
        child = Mock(pid=4242)
        child.wait.return_value = 0
        with patch(
            "jetson_control.sensor_monitor._process_group_exists",
            return_value=True,
        ), patch(
            "jetson_control.sensor_monitor._wait_for_process_group",
            side_effect=[False, False, True],
        ), patch("jetson_control.sensor_monitor.os.killpg") as killpg:
            stop_child(
                child,
                timeout_seconds=0,
                terminate_timeout_seconds=0,
                kill_timeout_seconds=0,
            )

        self.assertEqual(
            killpg.call_args_list,
            [
                unittest.mock.call(4242, signal.SIGINT),
                unittest.mock.call(4242, signal.SIGTERM),
                unittest.mock.call(4242, signal.SIGKILL),
            ],
        )

    def test_supervisor_retries_missing_pipeline_without_exiting(self) -> None:
        handlers = {}
        attempts = []

        def install_handler(signum, handler):
            handlers[signum] = handler

        def unavailable(_settings):
            attempts.append(True)
            raise FileNotFoundError("snapshot is being replaced")

        def stop_during_retry(_seconds):
            handlers[signal.SIGTERM](signal.SIGTERM, None)

        with patch(
            "jetson_control.sensor_monitor.signal.signal",
            side_effect=install_handler,
        ), patch(
            "jetson_control.sensor_monitor.time.sleep",
            side_effect=stop_during_retry,
        ):
            result = supervise(
                self.settings,
                pwd.getpwuid(os.getuid()).pw_name,
                retry_seconds=0.1,
                runtime_loader=unavailable,
            )

        self.assertEqual(result, 0)
        self.assertEqual(attempts, [True])

    def _run_supervisor(self, mutate, *, duration=6.0, initial_relay=True,
                        snapshot=None, capture=None, retry_seconds=0.2):
        marker = self.root / "relay.json"
        now = [0.0]
        registry = MobileRtkRelayRegistry(
            marker, owner_uid=os.getuid(), clock_millis=lambda: int(now[0] * 1000),
        )
        if initial_relay:
            registry.register(self.settings.pipeline_id, "192.168.49.71", 32101)
        handlers = {}
        starts = []
        stops = []

        def route(pipeline_id, path, **_kwargs):
            return mobile_rtk_relay_environment(
                pipeline_id, path, expected_owner_uid=os.getuid(),
                clock_millis=lambda: int(now[0] * 1000),
            )

        def load(settings):
            runtime = load_monitor_runtime(
                settings, expected_owner_uid=os.getuid(), pipeline_environment={},
                relay_path=marker,
            )
            return runtime

        def start(*_args, **kwargs):
            child = Mock(pid=4000 + len(starts))
            child.poll.return_value = None
            starts.append((now[0], kwargs["env"]))
            return child

        def stop(child, **kwargs):
            descriptor = _open_lock(self.settings.device_lock_path)
            try:
                with self.assertRaises(BlockingIOError):
                    fcntl.flock(descriptor, fcntl.LOCK_EX | fcntl.LOCK_NB)
            finally:
                os.close(descriptor)
            stops.append((now[0], kwargs))
            child.poll.return_value = 0

        def sleep(seconds):
            now[0] += seconds
            mutate(now[0], registry)
            if now[0] >= duration:
                handlers[signal.SIGTERM](signal.SIGTERM, None)

        bridge = Mock()
        bridge.status.side_effect = lambda: (
            snapshot(now[0]) if snapshot else sensor_snapshot()
        )
        with patch("jetson_control.sensor_monitor.signal.signal", side_effect=lambda s, h: handlers.update({s: h})), \
                patch("jetson_control.sensor_monitor.time.sleep", side_effect=sleep), \
                patch("jetson_control.sensor_monitor.time.monotonic", side_effect=lambda: now[0]), \
                patch("jetson_control.sensor_monitor.mobile_rtk_relay_environment", side_effect=route), \
                patch("jetson_control.sensor_monitor.subprocess.Popen", side_effect=start), \
                patch("jetson_control.sensor_monitor.stop_child", side_effect=stop), \
                patch("jetson_control.sensor_monitor.SensorBridgeStore", return_value=bridge), \
                patch("jetson_control.sensor_monitor.capture_request_active", side_effect=lambda _: capture(now[0]) if capture else False):
            result = supervise(
                self.settings, pwd.getpwuid(os.getuid()).pw_name,
                retry_seconds=retry_seconds, runtime_loader=load, relay_path=marker,
            )
        self.assertEqual(result, 0)
        return starts, stops

    def test_supervisor_restarts_on_route_change_expiry_and_unregister(self) -> None:
        for change in ("register", "host", "port", "expiry", "unregister", "other"):
            with self.subTest(change=change):
                changed = []

                def mutate(now, registry):
                    if now < 1 or changed:
                        return
                    changed.append(True)
                    if change in ("register", "host", "port", "other"):
                        registry.register(
                            "other" if change == "other" else self.settings.pipeline_id,
                            "192.168.49.72" if change == "host" else "192.168.49.71",
                            32102 if change == "port" else 32101,
                        )
                    elif change == "unregister":
                        registry.unregister(self.settings.pipeline_id)
                    else:
                        value = json.loads(registry.path.read_text())
                        value["expiresAtEpochMillis"] = 0
                        registry._atomic_write(value)

                starts, stops = self._run_supervisor(
                    mutate, initial_relay=change != "register",
                )
                self.assertEqual(len(starts), 2)
                self.assertEqual(stops[0][1]["timeout_seconds"], 2.0)
                if change in ("expiry", "unregister", "other"):
                    self.assertNotIn("JETSON_PIPELINE_MOBILE_RTK_RELAY", starts[1][1])

    def test_supervisor_does_not_restart_for_lease_heartbeats(self) -> None:
        def heartbeat(_now, registry):
            registry.register(self.settings.pipeline_id, "192.168.49.71", 32101)
        starts, _stops = self._run_supervisor(heartbeat)
        self.assertEqual(len(starts), 1)

    def test_supervisor_recovers_stalled_camera_without_extra_gnss_rtk_gap(self) -> None:
        def snapshot(now):
            sample = int(min(now, 46) * 1000) + 1
            return sensor_snapshot(sample, sample, updated=int(now * 1000))
        starts, stops = self._run_supervisor(
            lambda _, registry: registry.register(self.settings.pipeline_id, "192.168.49.71", 32101),
            duration=72, snapshot=snapshot,
            retry_seconds=3.0,
        )
        self.assertEqual(len(starts), 2)
        self.assertGreaterEqual(stops[0][0], 61)
        self.assertLess(stops[0][0], 63)
        self.assertLessEqual(starts[1][0] - stops[0][0], 3.0)
        self.assertEqual(starts[0][1]["NTRIP_HOST"], starts[1][1]["NTRIP_HOST"])
        self.assertEqual(starts[0][1]["NTRIP_PORT"], starts[1][1]["NTRIP_PORT"])
        self.assertEqual(stops[0][1]["terminate_timeout_seconds"], 2.0)

    def test_supervisor_required_camera_recovers_before_any_frame_or_bridge_update(self):
        self._write_policy(required=("camera",))
        absent = sensor_snapshot()
        absent.camera["connected"] = False
        missing = SensorBridgeSnapshot(False, False, None, None, {}, {}, {}, {})
        for state in (absent, missing):
            with self.subTest(state=state):
                starts, stops = self._run_supervisor(
                    lambda *_: None, duration=52, snapshot=lambda _: state,
                    initial_relay=False, retry_seconds=3.0,
                )
                self.assertEqual(len(starts), 2)
                self.assertGreaterEqual(stops[0][0], 45)
                self.assertLess(stops[0][0], 47)
                self.assertLessEqual(starts[1][0] - stops[0][0], 3.0)

    def test_supervisor_optional_camera_absence_does_not_restart(self):
        self._write_policy(required=("gnss",), optional=("camera",))
        absent = sensor_snapshot()
        absent.camera["connected"] = False
        starts, _ = self._run_supervisor(
            lambda *_: None, duration=100, snapshot=lambda _: absent,
            initial_relay=False,
        )
        self.assertEqual(len(starts), 1)

    def test_supervisor_repeated_camera_failure_retries_fast_with_startup_grace(self):
        self._write_policy(required=("camera",))
        starts, stops = self._run_supervisor(
            lambda *_: None, duration=110, snapshot=lambda _: sensor_snapshot(),
            initial_relay=False, retry_seconds=3.0,
        )
        self.assertEqual(len(starts), 3)
        recoveries = [stop for stop in stops if stop[1].get("timeout_seconds") == 2.0]
        self.assertEqual(len(recoveries), 2)
        for index, (stopped, _) in enumerate(recoveries):
            self.assertGreaterEqual(stopped - starts[index][0], 45.0)
            self.assertLessEqual(starts[index + 1][0] - stopped, 3.0)

    def test_supervisor_handoff_precedes_relay_reload_and_reuses_relay_after_capture(self) -> None:
        changed = []
        def mutate(now, registry):
            if now >= 1 and not changed:
                registry.register(self.settings.pipeline_id, "192.168.49.72", 32102)
                changed.append(True)
        starts, stops = self._run_supervisor(
            mutate, capture=lambda now: 1 <= now < 3,
        )
        self.assertEqual(len(starts), 2)
        self.assertEqual(stops[0][1], {"timeout_seconds": 2.0, "terminate_timeout_seconds": 2.0})
        self.assertGreaterEqual(starts[1][0], 3)
        self.assertEqual(starts[1][1]["NTRIP_HOST"], "192.168.49.72")
        self.assertEqual(starts[1][1]["NTRIP_PORT"], "32102")


def sensor_snapshot(frame=None, imu=None, *, updated=1000, configured=True, source="oak"):
    return SensorBridgeSnapshot(
        True, True, updated, 0.0, {},
        {"configured": configured, "connected": configured, "active": False,
         "lastFrameAtEpochMillis": frame, "previewAvailable": True},
        {"active": True, "lastSampleAtEpochMillis": updated},
        {"configured": configured, "source": source, "lastSampleAtEpochMillis": imu},
    )


class CameraProgressWatchdogTest(unittest.TestCase):
    def test_required_camera_deadline_ignores_disconnected_status_and_source(self):
        for source in (None, "oak", "unknown"):
            with self.subTest(source=source):
                initial = sensor_snapshot(source=source)
                initial.camera["source"] = source
                initial.camera["connected"] = False
                watchdog = CameraProgressWatchdog(initial, 0, camera_required=True)
                current = sensor_snapshot(updated=44000, source=source)
                current.camera["source"] = source
                current.camera["connected"] = False
                self.assertFalse(watchdog.stalled(current, 44.9))
                self.assertTrue(watchdog.stalled(current, 45))

    def test_required_camera_deadline_survives_missing_bridge_and_old_capture_status(self):
        missing = SensorBridgeSnapshot(False, False, None, None, {}, {}, {}, {})
        for initial in (missing, sensor_snapshot(59760, 59760), sensor_snapshot(configured=False)):
            with self.subTest(initial=initial):
                watchdog = CameraProgressWatchdog(initial, 100, camera_required=True)
                self.assertFalse(watchdog.stalled(initial, 144.9))
                self.assertTrue(watchdog.stalled(initial, 145))

    def test_required_camera_first_frame_before_deadline_resumes_normal_progress(self):
        watchdog = CameraProgressWatchdog(sensor_snapshot(), 0, camera_required=True)
        self.assertFalse(watchdog.stalled(sensor_snapshot(1000, 1000), 44))
        self.assertFalse(watchdog.stalled(sensor_snapshot(1000, 1000), 45))
        self.assertTrue(watchdog.stalled(sensor_snapshot(1000, 1000), 59))

    def test_optional_camera_with_no_bridge_never_arms(self):
        missing = SensorBridgeSnapshot(False, False, None, None, {}, {}, {}, {})
        watchdog = CameraProgressWatchdog(missing, 0)
        self.assertFalse(watchdog.stalled(missing, 100))

    def test_live_gps_and_status_do_not_hide_stale_frames_or_oak_imu(self):
        watchdog = CameraProgressWatchdog(sensor_snapshot(), 0)
        self.assertFalse(watchdog.stalled(sensor_snapshot(59760, 59760), 1))
        self.assertFalse(watchdog.stalled(sensor_snapshot(59760, 59760), 44))
        self.assertTrue(watchdog.stalled(sensor_snapshot(59760, 59760, updated=45000), 45))

    def test_frame_stall_and_oak_imu_stall_are_independently_detected(self):
        for frame, imu in ((59760, 70000), (70000, 59760)):
            with self.subTest(frame=frame, imu=imu):
                watchdog = CameraProgressWatchdog(sensor_snapshot(), 0)
                watchdog.stalled(sensor_snapshot(59760, 59760), 44)
                self.assertTrue(watchdog.stalled(sensor_snapshot(frame, imu), 60))

    def test_transient_fault_recovers_without_restart_and_clock_jumps_are_safe(self):
        watchdog = CameraProgressWatchdog(sensor_snapshot(), 0)
        watchdog.stalled(sensor_snapshot(1000, 1000), 44)
        self.assertFalse(watchdog.stalled(sensor_snapshot(1000, 1000), 58))
        self.assertFalse(watchdog.stalled(sensor_snapshot(2000, 2000, updated=999999), 59))
        self.assertFalse(watchdog.stalled(sensor_snapshot(3000, 3000, updated=1), 60))

    def test_old_status_missing_camera_and_disabled_camera_do_not_arm(self):
        initial = sensor_snapshot(59760, 59760)
        watchdog = CameraProgressWatchdog(initial, 0)
        self.assertFalse(watchdog.stalled(initial, 100))
        self.assertFalse(watchdog.stalled(sensor_snapshot(None, 60000), 101))
        self.assertFalse(watchdog.stalled(sensor_snapshot(None, 60000), 200))
        watchdog.stalled(sensor_snapshot(70000, 70000), 201)
        self.assertFalse(watchdog.stalled(sensor_snapshot(configured=False), 220))

    def test_missing_bridge_after_camera_progress_is_detected(self):
        watchdog = CameraProgressWatchdog(sensor_snapshot(), 0)
        watchdog.stalled(sensor_snapshot(59760, 59760), 44)
        missing = SensorBridgeSnapshot(False, False, None, None, {}, {}, {}, {})
        self.assertTrue(watchdog.stalled(missing, 60))

    def test_new_connected_status_arms_even_if_first_frame_never_progresses(self):
        for sample in (None, 59760):
            with self.subTest(sample=sample):
                watchdog = CameraProgressWatchdog(sensor_snapshot(sample, sample), 0)
                self.assertFalse(watchdog.stalled(sensor_snapshot(sample, sample, updated=2000), 1))
                self.assertTrue(watchdog.stalled(sensor_snapshot(sample, sample, updated=45000), 45))

    def test_camera_without_hardware_remains_unarmed_despite_status_heartbeats(self):
        watchdog = CameraProgressWatchdog(sensor_snapshot(), 0)
        absent = sensor_snapshot(updated=2000)
        absent.camera["connected"] = False
        watchdog.stalled(absent, 1)
        absent.camera["connected"] = False
        self.assertFalse(watchdog.stalled(absent, 100))

    def test_external_serial_imu_does_not_trigger_camera_recovery(self):
        watchdog = CameraProgressWatchdog(sensor_snapshot(), 0)
        watchdog.stalled(sensor_snapshot(1000, 1000, source="external_imu"), 44)
        self.assertFalse(watchdog.stalled(sensor_snapshot(2000, 1000, source="external_imu"), 60))


if __name__ == "__main__":
    unittest.main()
