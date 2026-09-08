import importlib.util
import json
import hashlib
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch


SCRIPT = Path(__file__).resolve().parents[2] / 'scripts/diagnosis/deploy_backend.py'
spec = importlib.util.spec_from_file_location('connection_evidence_deployment', SCRIPT)
deploy = importlib.util.module_from_spec(spec)
spec.loader.exec_module(deploy)


class ConnectionEvidenceDeploymentTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)
        self.install = self.root / 'install'
        self.package = self.install / 'jetson_control'
        self.source = self.root / 'source'
        self.units = self.root / 'units'
        self.state = self.root / 'state'
        self.runtime = self.root / 'run'
        for directory in (self.package, self.source, self.units, self.state, self.runtime):
            directory.mkdir(parents=True)
        self.patches = [patch.object(deploy, name, value) for name, value in (
            ('INSTALL', self.install), ('PACKAGE', self.package), ('SOURCE', self.source),
            ('UNIT_DIRECTORY', self.units), ('STATE_ROOT', self.state), ('RUNTIME_ROOT', self.runtime),
        )]
        for item in self.patches:
            item.start()
        (self.state / 'upload-jobs').mkdir()
        (self.runtime / 'wifi-direct.json').write_text(json.dumps({'state': 'DISCOVERABLE', 'dhcpActive': False}))
        self.manifest = {'files': {}, 'untouchedModules': {}, 'unitSha256': {}}
        self.before = {}
        for name in deploy.FILES:
            (self.source / name).write_text('VALUE = "new"\n')
            old = None
            if name != 'diagnostics.py':
                content = ('VALUE = "old-' + name + '"\n').encode()
                (self.package / name).write_bytes(content)
                old = deploy.digest(self.package / name)
                self.before[name] = content
            self.manifest['files'][name] = {'beforeSha256': old, 'afterSha256': deploy.digest(self.source / name)}
        (self.package / '__init__.py').write_text('')
        self.manifest['untouchedModules']['__init__.py'] = deploy.digest(self.package / '__init__.py')
        (self.package / 'auth.py').write_text('')
        self.manifest['untouchedModules']['auth.py'] = deploy.digest(self.package / 'auth.py')
        runtime_hash = hashlib.sha256()
        for name in ('diagnostics.py', 'api.py', 'wifi_direct.py', 'auth.py'):
            runtime_hash.update(((self.source if name in deploy.FILES else self.package) / name).read_bytes())
        self.manifest['runtimeBuildId'] = runtime_hash.hexdigest()
        for name in deploy.UNITS:
            (self.units / name).write_text('fixture')
            self.manifest['unitSha256'][name] = deploy.digest(self.units / name)
        (self.install / 'requirements.txt').write_text('')
        self.manifest['requirementsSha256'] = deploy.digest(self.install / 'requirements.txt')

    def tearDown(self):
        for item in reversed(self.patches):
            item.stop()
        self.temporary.cleanup()

    def unit_values(self, unit):
        return {'ActiveState': 'active', 'SubState': 'running', 'MainPID': '101',
                'WorkingDirectory': str(self.install), 'ExecStart': 'jetson_control.asgi:app jetson_control.wifi_direct'}

    def test_preflight_rejects_changed_baseline_and_keeps_files(self):
        deploy.validate_payload(self.manifest)
        (self.package / 'api.py').write_text('user edit')
        with self.assertRaises(RuntimeError):
            deploy.validate_payload(self.manifest)
        self.assertEqual((self.package / 'api.py').read_text(), 'user edit')
        self.assertFalse((self.install / 'backups').exists())

    def test_unknown_upload_and_active_rtk_fail_closed(self):
        jobs = self.state / 'upload-jobs'
        job = jobs / 'fixture.json'
        job.write_text(json.dumps({'state': 'UNRECOGNIZED', 'token': 'private-fixture'}))
        with patch.object(deploy, 'command', return_value=''):
            with self.assertRaisesRegex(RuntimeError, 'unknown upload'):
                deploy.ensure_idle()
            job.write_text(json.dumps({'state': 'COMPLETED'}))
            (self.runtime / 'mobile-rtk-relay.json').write_text(json.dumps({'expiresAtEpochMillis': 2**62}))
            with self.assertRaisesRegex(RuntimeError, 'RTK'):
                deploy.ensure_idle()

    def test_missing_operational_state_and_unit_overrides_block_before_changes(self):
        with patch.object(deploy, 'command', return_value=''):
            (self.state / 'upload-jobs').rmdir()
            with self.assertRaisesRegex(RuntimeError, 'idle is unknown'):
                deploy.ensure_idle()
            (self.state / 'upload-jobs').mkdir()
            (self.runtime / 'wifi-direct.json').unlink()
            with self.assertRaisesRegex(RuntimeError, 'idle is unknown'):
                deploy.ensure_idle()
        for key, value in (('DropInPaths', '/fixture/override.conf'),
                           ('EnvironmentFiles', '/fixture/environment'),
                           ('Environment', 'JETSON_CONTROL_STATE_DIR=/fixture/custom')):
            with self.subTest(key=key):
                with patch.object(deploy, 'unit_values', side_effect=lambda unit: dict(self.unit_values(unit), **{key: value})):
                    with self.assertRaises(RuntimeError):
                        deploy.validate_services(self.manifest)

    def test_corrupt_later_backup_is_rejected_before_any_service_stop(self):
        with patch.object(deploy, 'ensure_idle'), patch.object(deploy, 'unit_values', side_effect=self.unit_values), \
             patch.object(deploy, 'command', return_value=''), patch.object(deploy.os, 'chown'), \
             patch.object(deploy, 'verify_started'):
            deploy.apply(self.manifest)
        backup = next((self.install / 'backups').iterdir())
        (backup / 'wifi_direct.py').write_text('corrupted backup')
        installed = {name: (self.package / name).read_bytes() for name in deploy.FILES}
        with patch.object(deploy, 'stop_services') as stop:
            with self.assertRaisesRegex(RuntimeError, 'Backup hash mismatch'):
                deploy.restore(backup)
            stop.assert_not_called()
        self.assertEqual(installed, {name: (self.package / name).read_bytes() for name in deploy.FILES})

    def test_pipeline_transition_and_cancelled_upload_are_not_idle_evidence(self):
        with patch.object(deploy, 'command', return_value='jetson-pipeline@fixture.service loaded activating start fixture'), \
             patch.object(deploy, 'unit_values', return_value={'ActiveState': 'activating', 'MainPID': '0'}):
            with self.assertRaisesRegex(RuntimeError, 'transitioning'):
                deploy.ensure_idle()
        (self.state / 'upload-jobs' / 'fixture.json').write_text(json.dumps({'state': 'CANCELLED'}))
        with patch.object(deploy, 'command', return_value=''):
            with self.assertRaisesRegex(RuntimeError, 'unknown upload'):
                deploy.ensure_idle()

    def test_new_pid_startup_record_also_requires_reviewed_build_fingerprint(self):
        previous = {unit: {'MainPID': '100'} for unit in deploy.UNITS}
        for source in ('api', 'p2p'):
            directory = self.state / 'diagnostics' / source
            directory.mkdir(parents=True)
            (directory / 'events-fixture.jsonl').write_text(json.dumps({
                'event': 'process_start', 'source': source, 'pid': 101, 'buildId': '0' * 64}) + '\n')
        with patch.object(deploy, 'unit_values', side_effect=self.unit_values), patch.object(deploy, 'hello_health'), \
             patch.object(deploy.time, 'monotonic', side_effect=[0, 1, 46]), patch.object(deploy.time, 'sleep'):
            with self.assertRaisesRegex(RuntimeError, 'budget exhausted'):
                deploy._poll_started(self.manifest, previous)
        for source in ('api', 'p2p'):
            (self.state / 'diagnostics' / source / 'events-fixture.jsonl').write_text(json.dumps({
                'event': 'process_start', 'source': source, 'pid': 101, 'buildId': self.manifest['runtimeBuildId']}) + '\n')
        with patch.object(deploy, 'unit_values', side_effect=self.unit_values), patch.object(deploy, 'hello_health'), \
             patch.object(deploy.time, 'monotonic', side_effect=[0, 1]):
            deploy._poll_started(self.manifest, previous)

    def test_symlink_package_parent_is_rejected(self):
        linked = self.root / 'linked-install'
        linked.symlink_to(self.install, target_is_directory=True)
        with patch.object(deploy, 'INSTALL', linked), patch.object(deploy, 'PACKAGE', linked / 'jetson_control'):
            with self.assertRaisesRegex(RuntimeError, 'Symlink'):
                deploy.validate_payload(self.manifest)

    def test_interruption_after_copy_restores_exact_files_and_absence(self):
        calls = []
        with patch.object(deploy, 'ensure_idle'), patch.object(deploy, 'unit_values', side_effect=self.unit_values), \
             patch.object(deploy, 'command', side_effect=lambda args, **kwargs: calls.append(args) or ''), \
             patch.object(deploy.os, 'chown'), patch.object(deploy, 'verify_started', side_effect=RuntimeError('health fixture')):
            with self.assertRaises(RuntimeError):
                deploy.apply(self.manifest)
        for name, contents in self.before.items():
            self.assertEqual((self.package / name).read_bytes(), contents)
        self.assertFalse((self.package / 'diagnostics.py').exists())
        backups = list((self.install / 'backups').iterdir())
        self.assertEqual(len(backups), 1)
        result = json.loads((backups[0] / 'result.json').read_text())
        self.assertEqual(result['status'], 'FAILED_ROLLED_BACK')
        operations = [args[1:3] for args in calls if args[0] == '/usr/bin/systemctl']
        self.assertIn(['stop', 'jetson-control-api.service'], operations)
        self.assertIn(['start', 'jetson-wifi-direct.service'], operations)
        self.assertFalse(any('NetworkManager' in str(args) for args in calls))

    def test_success_installs_only_six_files_with_recorded_backup(self):
        with patch.object(deploy, 'ensure_idle'), patch.object(deploy, 'unit_values', side_effect=self.unit_values), \
             patch.object(deploy, 'command', return_value=''), patch.object(deploy.os, 'chown'), \
             patch.object(deploy, 'verify_started'):
            deploy.apply(self.manifest)
        for name in deploy.FILES:
            self.assertEqual((self.package / name).read_bytes(), (self.source / name).read_bytes())
        self.assertEqual((self.package / '__init__.py').read_text(), '')
        self.assertEqual((self.install / 'requirements.txt').read_text(), '')
        backup = next((self.install / 'backups').iterdir())
        self.assertEqual(backup.stat().st_mode & 0o777, 0o700)
        self.assertEqual(json.loads((backup / 'result.json').read_text())['status'], 'DEPLOYED')


if __name__ == '__main__':
    unittest.main()
