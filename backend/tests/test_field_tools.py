import os
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch

from jetson_control.field_tools import run_history, safe_read, terminal
from jetson_control.route_recorder import RouteRecorder


class FieldToolsTest(unittest.TestCase):
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
                SimpleNamespace(fresh=True, gnss=dict(active=True, latitude=37.1, longitude=127.1, lastSampleAtEpochMillis=100)),
                SimpleNamespace(fresh=True, gnss=dict(active=True, latitude=37.1, longitude=127.1, lastSampleAtEpochMillis=100)),
                SimpleNamespace(fresh=False, gnss={}),
                SimpleNamespace(fresh=True, gnss=dict(active=True, latitude=37.2, longitude=127.2, lastSampleAtEpochMillis=200)),
                SimpleNamespace(fresh=True, gnss=dict(active=True, latitude=37.3, longitude=127.3, lastSampleAtEpochMillis=20000)),
            ]
            recorder.bridge = SimpleNamespace(status=lambda: samples.pop(0))
            class Stop:
                def is_set(self): return not samples
                def wait(self, _): return None
            recorder.stop_event = Stop()
            recorder._record()
            import json
            points = [json.loads(line) for line in recorder.path.read_text().splitlines()]
            self.assertEqual([p['timestamp'] for p in points], [100, 200, 20000])
            self.assertEqual([p['segment'] for p in points], [0, 1, 2])
