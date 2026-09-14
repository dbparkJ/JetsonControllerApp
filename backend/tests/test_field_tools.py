import json
import os
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch

from jetson_control.field_tools import delete_run_history, read_run_quality, run_history, safe_read, terminal
from fastapi import HTTPException
from jetson_control.route_recorder import RouteRecorder


class FieldToolsTest(unittest.TestCase):
    def test_delete_one_history_removes_log_and_route_but_preserves_other_runs_and_data(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            directory = root / 'capture'
            directory.mkdir()
            old = directory / 'run-20260912T010001.000001Z-123.log'
            recent = directory / 'run-20260912T010002.000001Z-123.log'
            for path in (old, recent):
                path.write_text('completed')
            route = directory / (old.name + '.route.jsonl')
            route.write_text('{"latitude":37.0}')
            quality = directory / (old.name + '.quality.json')
            quality.write_text('{"schemaVersion":1}')
            evidence = directory / (old.name + '.quality.jsonl')
            evidence.write_text('{"schemaVersion":1}\n')
            raw = directory / 'measurements.csv'
            raw.write_text('original data')
            result = delete_run_history(root, [{'id': 'capture', 'state': 'RUNNING'}], 'capture', old.name)
            self.assertTrue(result['deleted'])
            self.assertFalse(old.exists())
            self.assertFalse(route.exists())
            self.assertFalse(quality.exists())
            self.assertFalse(evidence.exists())
            self.assertTrue(recent.exists())
            self.assertEqual(raw.read_text(), 'original data')
            history = run_history(root, [], 0, 30)
            self.assertEqual([r['logId'] for r in history['runs']], [recent.name])

    def test_delete_refuses_latest_run_during_active_or_transition_states(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            directory = root / 'capture'
            directory.mkdir()
            log = directory / 'run-20260912T010001.000001Z-123.log'
            log.write_text('running')
            for state in ('RUNNING', 'STARTING', 'STOPPING', 'RETRYING'):
                with self.subTest(state=state), self.assertRaises(HTTPException) as error:
                    delete_run_history(root, [{'id': 'capture', 'state': state}], 'capture', log.name)
                self.assertEqual(error.exception.status_code, 409)
                self.assertTrue(log.exists())

    def test_delete_rejects_symlink_log_route_and_directory_without_touching_targets(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            directory = root / 'capture'
            directory.mkdir()
            target = root / 'keep.txt'
            target.write_text('keep')
            log = directory / 'run-20260912T010001.000001Z-123.log'
            log.symlink_to(target)
            with self.assertRaises(HTTPException):
                delete_run_history(root, [], 'capture', log.name)
            log.unlink()
            log.write_text('old log')
            route = directory / (log.name + '.route.jsonl')
            route.symlink_to(target)
            with self.assertRaises(HTTPException):
                delete_run_history(root, [], 'capture', log.name)
            self.assertTrue(log.exists())
            (root / 'linked').symlink_to(directory, target_is_directory=True)
            with self.assertRaises(OSError):
                delete_run_history(root, [], 'linked', log.name)
            self.assertEqual(target.read_text(), 'keep')

    def test_delete_rejects_path_traversal_before_reading_the_filesystem(self):
        for pipeline_id, log_id in [('..', 'run-20260912T010001.000001Z-123.log'),
                                    ('capture', '../keep.txt')]:
            with self.assertRaises(HTTPException) as error:
                delete_run_history(Path('/missing'), [], pipeline_id, log_id)
            self.assertEqual(error.exception.status_code, 400)

    def test_history_keeps_separate_runs_and_honest_results_after_unregister(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            directory = root / 'capture'
            directory.mkdir()
            for i, code in [(1, 0), (2, 143), (3, 1)]:
                (directory / f'run-20260912T01000{i}.000001Z-123.log').write_text(
                    f'=== Jetson pipeline run finished ===\nfinished_at=2026-09-12T01:01:00Z\nexit_code={code}\n')
            (directory / 'run-20260912T010004.000001Z-123.log').write_text('interrupted')
            page = run_history(root, [], 0, 2)
            self.assertEqual([r['state'] for r in page['runs']], ['UNKNOWN', 'FAILED'])
            self.assertEqual(page['nextOffset'], 2)
            page = run_history(root, [], 2, 2)
            self.assertEqual([r['state'] for r in page['runs']], ['STOPPED', 'COMPLETED'])
            self.assertIsNone(page['nextOffset'])
            self.assertNotEqual(page['runs'][0]['id'], page['runs'][1]['id'])
            self.assertTrue(all(run['quality'] is None for run in page['runs']))

    def test_only_latest_unfinished_run_is_running(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            (root / 'capture').mkdir()
            for i in (1, 2):
                (root / 'capture' / f'run-20260912T01000{i}.000001Z-123.log').write_text('running')
            history = run_history(root, [{'id': 'capture', 'label': 'Road', 'state': 'RUNNING'}], 0, 30)
            self.assertEqual([r['state'] for r in history['runs']], ['RUNNING', 'UNKNOWN'])

    def test_symlink_files_and_directories_are_not_read(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            (root / 'capture').mkdir()
            target = root / 'private.txt'
            target.write_text('secret')
            link = root / 'capture' / 'run-20260912T010001.000001Z-123.log'
            link.symlink_to(target)
            self.assertEqual(run_history(root, [], 0, 30)['runs'], [])
            with self.assertRaises(OSError):
                safe_read(link)

    def test_root_terminal_is_refused_before_process_spawn(self):
        with patch('jetson_control.field_tools.pwd.getpwnam', return_value=SimpleNamespace(pw_uid=0)), patch('jetson_control.field_tools.subprocess.Popen') as popen:
            with self.assertRaises(ValueError):
                terminal('pwd', 'root')
            popen.assert_not_called()

    def test_route_records_unique_fresh_samples_and_splits_reception_gaps(self):
        with tempfile.TemporaryDirectory() as temporary:
            recorder = RouteRecorder(Path(temporary) / 'run.log', Path(temporary))
            samples = [
                SimpleNamespace(fresh=True, gnss=dict(configured=True, active=True, latitude=37.1, longitude=127.1, lastSampleAtEpochMillis=100, fixType='rtk_fixed')),
                SimpleNamespace(fresh=True, gnss=dict(configured=True, active=True, latitude=37.1, longitude=127.1, lastSampleAtEpochMillis=100, fixType='rtk_fixed')),
                SimpleNamespace(fresh=False, gnss={}),
                SimpleNamespace(fresh=True, gnss=dict(configured=True, active=True, latitude=37.2, longitude=127.2, lastSampleAtEpochMillis=2200, fixType='rtk_float')),
                SimpleNamespace(fresh=True, gnss=dict(configured=True, active=True, latitude=37.3, longitude=127.3, lastSampleAtEpochMillis=20000, fixType='rtk_fixed')),
            ]
            recorder.bridge = SimpleNamespace(status=lambda: samples.pop(0))
            clocks = iter((100, 1000, 2000, 2200, 20000))
            recorder.clock_millis = lambda: next(clocks)
            class Stop:
                def is_set(self): return not samples
                def wait(self, _): return None
            recorder.stop_event = Stop()
            recorder._record()
            points = [json.loads(line) for line in recorder.path.read_text().splitlines()]
            self.assertEqual([p['timestamp'] for p in points], [100, 2200, 20000])
            self.assertEqual([p['segment'] for p in points], [0, 1, 2])
            self.assertEqual(points[0]['fixState'], 'FIXED')
            evidence = [json.loads(line) for line in recorder.quality_evidence_path.read_text().splitlines()]
            self.assertEqual(len(evidence), 5)
            summary = read_run_quality(Path(temporary) / 'run.log')
            self.assertEqual(summary['observationCount'], 5)
            self.assertGreater(summary['unknownDurationMillis'], 0)

    def test_invalid_sensor_data_is_recorded_and_does_not_stop_later_route_acquisition(self):
        with tempfile.TemporaryDirectory() as temporary:
            recorder = RouteRecorder(Path(temporary) / 'run.log', Path(temporary))
            samples = [
                SimpleNamespace(fresh=True, gnss=dict(configured=True, active=True,
                    latitude='invalid', longitude=127.1, lastSampleAtEpochMillis=None)),
                SimpleNamespace(fresh=True, gnss=dict(configured=True, active=True,
                    latitude=37.2, longitude=127.2, lastSampleAtEpochMillis=2000,
                    fixType='rtk_fixed')),
            ]
            recorder.bridge = SimpleNamespace(status=lambda: samples.pop(0))
            clocks = iter((0, 2000))
            recorder.clock_millis = lambda: next(clocks)
            class Stop:
                def is_set(self): return not samples
                def wait(self, _): return None
            recorder.stop_event = Stop()

            recorder._record()

            points = [json.loads(line) for line in recorder.path.read_text().splitlines()]
            self.assertEqual([point['timestamp'] for point in points], [2000])
            summary = read_run_quality(Path(temporary) / 'run.log')
            self.assertEqual(summary['observationCount'], 2)
            self.assertEqual(summary['sampleState'], 'INSUFFICIENT_TIMING')

    def test_future_gnss_position_is_quality_evidence_but_not_a_fresh_route_point(self):
        with tempfile.TemporaryDirectory() as temporary:
            recorder = RouteRecorder(Path(temporary) / 'run.log', Path(temporary))
            samples = [
                SimpleNamespace(fresh=True, gnss=dict(configured=True, active=True,
                    latitude=37.1, longitude=127.1, lastSampleAtEpochMillis=5000,
                    fixType='rtk_fixed')),
                SimpleNamespace(fresh=True, gnss=dict(configured=True, active=True,
                    latitude=37.2, longitude=127.2, lastSampleAtEpochMillis=2000,
                    fixType='rtk_fixed')),
            ]
            recorder.bridge = SimpleNamespace(status=lambda: samples.pop(0))
            clocks = iter((0, 2000))
            recorder.clock_millis = lambda: next(clocks)
            class Stop:
                def is_set(self): return not samples
                def wait(self, _): return None
            recorder.stop_event = Stop()

            recorder._record()

            points = [json.loads(line) for line in recorder.path.read_text().splitlines()]
            self.assertEqual([point['timestamp'] for point in points], [2000])
            evidence = [json.loads(line) for line in recorder.quality_evidence_path.read_text().splitlines()]
            self.assertEqual(evidence[0]['sensors']['gnss']['state'], 'STALE')
            self.assertTrue(evidence[0]['sensors']['gnss']['clockIssue'])

    def test_route_storage_failure_does_not_stop_jetson_quality_evidence(self):
        with tempfile.TemporaryDirectory() as temporary:
            recorder = RouteRecorder(Path(temporary) / 'run.log', Path(temporary))
            recorder.path.write_text('occupied', encoding='utf-8')
            samples = [
                SimpleNamespace(fresh=True, gnss=dict(configured=True, active=True,
                    latitude=37.2, longitude=127.2, lastSampleAtEpochMillis=0,
                    fixType='rtk_fixed')),
                SimpleNamespace(fresh=True, gnss=dict(configured=True, active=True,
                    latitude=37.3, longitude=127.3, lastSampleAtEpochMillis=2000,
                    fixType='rtk_float')),
            ]
            recorder.bridge = SimpleNamespace(status=lambda: samples.pop(0))
            clocks = iter((0, 2000))
            recorder.clock_millis = lambda: next(clocks)
            class Stop:
                def is_set(self): return not samples
                def wait(self, _): return None
            recorder.stop_event = Stop()

            recorder._record()

            self.assertEqual(recorder.path.read_text(), 'occupied')
            self.assertEqual(
                len(recorder.quality_evidence_path.read_text().splitlines()),
                2,
            )
            self.assertEqual(read_run_quality(Path(temporary) / 'run.log')['rtkFixRatio'], 1.0)

    def test_route_read_can_recover_summary_from_bounded_persisted_evidence(self):
        with tempfile.TemporaryDirectory() as temporary:
            log = Path(temporary) / 'run.log'
            evidence = Path(str(log) + '.quality.jsonl')
            evidence.write_text(
                '{"schemaVersion":1,"observedAtEpochMillis":0,"observationWindowMillis":2000,'
                '"rtkFixState":"FIXED","sensors":{"gnss":{"state":"ACTIVE"}}}\n'
                'not-json\n'
                '{"schemaVersion":1,"observedAtEpochMillis":2000,"observationWindowMillis":2000,'
                '"rtkFixState":"FLOAT","sensors":{"gnss":{"state":"ACTIVE"}}}\n',
                encoding='utf-8',
            )

            self.assertIsNone(read_run_quality(log))
            recovered = read_run_quality(log, allow_evidence_fallback=True)
            self.assertEqual(recovered['observationCount'], 2)
            self.assertEqual(recovered['rtkFixRatio'], 1.0)
