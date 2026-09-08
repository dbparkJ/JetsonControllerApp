#!/usr/bin/env python3
"""Reviewed six-file backend deployment; default is read-only plan validation.

Run only after the documented USB/independent-management/idle prerequisites.
No install.sh, package installation, config/key/unit changes, or privilege setup.
"""
import argparse
import datetime
import hashlib
import http.client
import json
import multiprocessing
import os
from pathlib import Path
import re
import shutil
import shlex
import signal
import socket
import ssl
import subprocess
import tempfile
import time
import uuid

HERE = Path(__file__).resolve().parent
SOURCE = HERE.parent.parent / 'backend' / 'jetson_control'
INSTALL = Path('/opt/jetson-control')
PACKAGE = INSTALL / 'jetson_control'
UNIT_DIRECTORY = Path('/etc/systemd/system')
STATE_ROOT = Path('/var/lib/jetson-control')
RUNTIME_ROOT = Path('/run/jetson-control')
UNITS = ('jetson-control-api.service', 'jetson-wifi-direct.service')
FILES = ('api.py', 'diagnostics.py', 'mobile_rtk.py', 'pipelines.py', 'status.py', 'wifi_direct.py')


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def command(args, timeout=30):
    result = subprocess.run(args, capture_output=True, text=True, timeout=timeout, check=False)
    if result.returncode:
        raise RuntimeError('Required command failed: ' + Path(args[0]).name)
    return result.stdout


def unit_values(unit):
    raw = command(['/usr/bin/systemctl', 'show', unit, '--no-pager',
                   '--property=ActiveState', '--property=SubState', '--property=MainPID',
                   '--property=WorkingDirectory', '--property=ExecStart', '--property=Environment',
                   '--property=EnvironmentFiles', '--property=DropInPaths'])
    return dict(line.split('=', 1) for line in raw.splitlines() if '=' in line)



def validate_services(manifest):
    values = {unit: unit_values(unit) for unit in UNITS}
    for unit, value in values.items():
        if value.get('ActiveState') != 'active' or value.get('WorkingDirectory') != str(INSTALL):
            raise RuntimeError('Unexpected service state or installation root')
        if value.get('EnvironmentFiles'):
            raise RuntimeError('Service environment files require a new deployment review')
        dropins = shlex.split(value.get('DropInPaths', ''))
        reviewed_dropins = manifest.get('unitDropIns', {}).get(unit, {})
        if set(dropins) != set(reviewed_dropins):
            raise RuntimeError('Service drop-in set changed; repeat deployment review')
        for path, expected in reviewed_dropins.items():
            if Path(path).is_symlink() or digest(Path(path)) != expected:
                raise RuntimeError('Reviewed service drop-in changed')
        environment = dict(entry.split('=', 1) for entry in shlex.split(value.get('Environment', '')) if '=' in entry)
        if any(key.startswith('JETSON_CONTROL_') for key in environment):
            raise RuntimeError('Custom runtime paths require a new deployment review')
        if environment.get('PYTHONPATH', str(INSTALL)) != str(INSTALL) or 'PYTHONHOME' in environment:
            raise RuntimeError('Custom Python import paths require a new deployment review')
        module = 'jetson_control.asgi:app' if unit == UNITS[0] else 'jetson_control.wifi_direct'
        if module not in value.get('ExecStart', ''):
            raise RuntimeError('Unexpected service entry module')
    return values


def read_json(path):
    # State is inspected only for safety flags; no payload is printed/copied.
    if path.stat().st_size > 4 * 1024 * 1024:
        raise RuntimeError('Safety state exceeds expected size')
    return json.loads(path.read_text())


def ensure_idle():
    raw = command(['/usr/bin/systemctl', 'list-units', '--type=service',
                   '--all', '--plain', '--no-legend', '--no-pager'])
    for line in raw.splitlines():
        words = line.split()
        if words and words[0].startswith('jetson-pipeline@'):
            state = unit_values(words[0])
            if state.get('ActiveState') not in {'inactive', 'failed'} or state.get('MainPID') != '0':
                raise RuntimeError('A running or transitioning pipeline must finish before deployment')
    relay = RUNTIME_ROOT / 'mobile-rtk-relay.json'
    if relay.exists():
        value = read_json(relay)
        expiry = value.get('expiresAtEpochMillis')
        if type(expiry) is not int or expiry > int(time.time() * 1000):
            raise RuntimeError('Active or unknown RTK lease prevents deployment')
    jobs = STATE_ROOT / 'upload-jobs'
    if not jobs.is_dir() or jobs.is_symlink():
        raise RuntimeError('Upload state directory is unavailable; idle is unknown')
    if jobs.exists():
        for job in jobs.glob('*.json'):
            value = read_json(job)
            state = value.get('state')
            if state not in {'COMPLETED', 'FAILED', 'SOURCE_DELETED', 'DELETED'}:
                raise RuntimeError('Active or unknown upload state prevents deployment')
    raw = command(['/usr/sbin/iw', 'dev'])
    if re.search(r'(?m)^\s*type\s+P2P-(?:GO|client)\s*$', raw):
        raise RuntimeError('Existing P2P group prevents deployment in this maintenance plan')
    status_path = RUNTIME_ROOT / 'wifi-direct.json'
    if not status_path.is_file() or status_path.is_symlink():
        raise RuntimeError('Active P2P service status is unavailable; idle is unknown')
    if status_path.exists():
        status = read_json(status_path)
        if status.get('state') not in {'DISCOVERABLE', 'DISABLED', 'STOPPED', 'UNAVAILABLE'}:
            raise RuntimeError('P2P is active, negotiating, or unknown')
        if status.get('dhcpActive') is True:
            raise RuntimeError('Active DHCP prevents deployment')



def reject_symlink_parents(path):
    for candidate in (path,) + tuple(path.parents):
        if candidate.is_symlink():
            raise RuntimeError('Symlink deployment or backup path rejected')


def validate_payload(manifest):
    for path in (INSTALL, PACKAGE, SOURCE, INSTALL / 'backups'):
        reject_symlink_parents(path)
    if tuple(manifest['files']) != FILES:
        raise RuntimeError('Unexpected deployment file set')
    for name, entry in manifest['files'].items():
        source = SOURCE / name
        target = PACKAGE / name
        if source.is_symlink() or target.is_symlink():
            raise RuntimeError('Symlink source/target rejected')
        if digest(source) != entry['afterSha256']:
            raise RuntimeError('Payload changed after review')
        compile(source.read_bytes(), name, 'exec')
        before = entry['beforeSha256']
        if before is None:
            if target.exists():
                raise RuntimeError('Previously absent file now exists; repeat preflight')
        elif not target.is_file() or digest(target) != before:
            raise RuntimeError('Deployment baseline changed; repeat preflight')
    for name, expected in manifest['untouchedModules'].items():
        if digest(PACKAGE / name) != expected:
            raise RuntimeError('An untouched dependency changed; repeat preflight')
    for name, expected in manifest['unitSha256'].items():
        if digest(UNIT_DIRECTORY / name) != expected:
            raise RuntimeError('Service unit changed; repeat preflight')
    if digest(INSTALL / 'requirements.txt') != manifest['requirementsSha256']:
        raise RuntimeError('Requirements changed; repeat preflight')
    runtime_hash = hashlib.sha256()
    for name in ('diagnostics.py', 'api.py', 'wifi_direct.py', 'auth.py'):
        runtime_hash.update(((SOURCE if name in FILES else PACKAGE) / name).read_bytes())
    if runtime_hash.hexdigest() != manifest.get('runtimeBuildId'):
        raise RuntimeError('Runtime build fingerprint does not match the reviewed file set')


def atomic_write(target, data, mode, uid, gid):
    descriptor, temporary = tempfile.mkstemp(prefix='.connection-deploy-', dir=str(target.parent))
    path = Path(temporary)
    try:
        with os.fdopen(descriptor, 'wb') as output:
            output.write(data)
            output.flush()
            os.fsync(output.fileno())
        os.chmod(path, mode)
        os.chown(path, uid, gid)
        os.replace(path, target)
    finally:
        if path.exists():
            path.unlink()


def start_previous_services(previous):
    for unit in UNITS:
        if previous[unit]['ActiveState'] == 'active':
            command(['/usr/bin/systemctl', 'start', unit], timeout=60)


def stop_services():
    for unit in reversed(UNITS):
        command(['/usr/bin/systemctl', 'stop', unit], timeout=60)


def restore(backup):
    # Verify the complete rollback before interrupting a running service. Read
    # bytes now so later backup changes cannot cause a partial restore.
    reject_symlink_parents(backup)
    reject_symlink_parents(PACKAGE)
    if (backup / 'rollback.json').is_symlink():
        raise RuntimeError('Symlink rollback manifest rejected')
    saved = read_json(backup / 'rollback.json')
    if tuple(saved['files']) != FILES or tuple(saved['services']) != UNITS:
        raise RuntimeError('Unexpected rollback file or service set')
    if PACKAGE.is_symlink() or not PACKAGE.is_dir():
        raise RuntimeError('Unexpected rollback package directory')
    restored_bytes = {}
    for name, meta in saved['files'].items():
        target = PACKAGE / name
        if type(meta.get('existed')) is not bool:
            raise RuntimeError('Invalid rollback existence metadata')
        after = meta.get('afterSha256')
        if not isinstance(after, str) or not re.fullmatch(r'[0-9a-f]{64}', after):
            raise RuntimeError('Invalid rollback deployment hash')
        allowed_current = {after}
        if meta['existed']:
            payload = backup / name
            expected = meta.get('sha256')
            if not isinstance(expected, str) or not re.fullmatch(r'[0-9a-f]{64}', expected):
                raise RuntimeError('Invalid backup hash metadata')
            if payload.is_symlink() or not payload.is_file() or digest(payload) != expected:
                raise RuntimeError('Backup hash mismatch')
            for key in ('mode', 'uid', 'gid', 'atimeNs', 'mtimeNs'):
                if type(meta.get(key)) is not int or not 0 <= meta[key] <= 2**63 - 1:
                    raise RuntimeError('Invalid rollback file metadata')
            if meta['mode'] > 0o777:
                raise RuntimeError('Unsafe rollback file mode')
            restored_bytes[name] = payload.read_bytes()
            if hashlib.sha256(restored_bytes[name]).hexdigest() != expected:
                raise RuntimeError('Backup changed during verification')
            allowed_current.add(expected)
        if target.is_symlink() or (target.exists() and not target.is_file()):
            raise RuntimeError('Unexpected rollback target')
        if target.exists() and digest(target) not in allowed_current:
            raise RuntimeError('Installed source changed after deployment; preserve it and review rollback')
    if any(value.get('ActiveState') != 'active' for value in saved['services'].values()):
        raise RuntimeError('Unexpected rollback service baseline')
    stop_services()
    for name, meta in saved['files'].items():
        target = PACKAGE / name
        if meta['existed']:
            atomic_write(target, restored_bytes[name], meta['mode'], meta['uid'], meta['gid'])
            os.utime(target, ns=(meta['atimeNs'], meta['mtimeNs']))
        elif target.exists():
            target.unlink()
    start_previous_services(saved['services'])
    for unit, previous in saved['services'].items():
        if unit_values(unit)['ActiveState'] != previous['ActiveState']:
            raise RuntimeError('Restored service state differs from baseline')


def hello_health(unit):
    # Validate TLS against the original certificate and its own SAN identity.
    # No key/config body is loaded; no verification is disabled.
    args = unit['ExecStart']
    port_match = re.search(r'--port\s+([0-9]{1,5})\b', args)
    cert_match = re.search(r'--ssl-certfile\s+(/[^\s;]+)', args)
    if not port_match or not cert_match:
        raise RuntimeError('Unknown API listener configuration')
    certificate = Path(cert_match.group(1))
    decoded = ssl._ssl._test_decode_cert(str(certificate))
    identities = [value for kind, value in decoded.get('subjectAltName', ()) if kind in {'DNS', 'IP Address'}]
    if not identities:
        raise RuntimeError('Certificate SAN identity unavailable')
    identity = identities[0]
    if not re.fullmatch(r'[a-zA-Z0-9._:-]{1,255}', identity):
        raise RuntimeError('Certificate identity cannot be used for health check')
    context = ssl.create_default_context(cafile=str(certificate))
    with socket.create_connection(('127.0.0.1', int(port_match.group(1))), timeout=5) as plain:
        with context.wrap_socket(plain, server_hostname=identity) as stream:
            stream.sendall(('GET /v1/hello HTTP/1.1\r\nHost: ' + identity + '\r\nConnection: close\r\n\r\n').encode('ascii'))
            response = http.client.HTTPResponse(stream)
            response.begin()
            body = response.read(16384)
            if response.status != 200 or json.loads(body).get('apiVersion') != 1:
                raise RuntimeError('Public hello health failed')


def _poll_started(manifest, previous):
    deadline = time.monotonic() + 45  # Fixed before execution; never extended.
    while time.monotonic() < deadline:
        try:
            for unit in UNITS:
                current = unit_values(unit)
                if current['ActiveState'] != 'active' or current['MainPID'] in {'0', previous[unit]['MainPID']}:
                    raise RuntimeError('Service has not restarted')
            hello_health(unit_values(UNITS[0]))
            for source, unit in [('api', UNITS[0]), ('p2p', UNITS[1])]:
                pid = int(unit_values(unit)['MainPID'])
                found = False
                for path in (STATE_ROOT / 'diagnostics' / source).glob('events-*.jsonl'):
                    if path.stat().st_size > 1024 * 1024:
                        raise RuntimeError('Unexpected diagnostic file size')
                    for line in path.read_text().splitlines():
                        try:
                            row = json.loads(line)
                        except ValueError:
                            continue
                        if row.get('event') == 'process_start' and row.get('source') == source and row.get('pid') == pid and row.get('buildId') == manifest['runtimeBuildId']:
                            found = True
                if not found:
                    raise RuntimeError('New service diagnostic start record unavailable')
            return
        except (OSError, ValueError, RuntimeError):
            time.sleep(1)
    raise RuntimeError('New-service validation budget exhausted')



def _verification_child(manifest, previous, result):
    os.setsid()
    try:
        _poll_started(manifest, previous)
        result.send(True)
    except BaseException:
        result.send(False)
    finally:
        result.close()


def verify_started(manifest, previous):
    # A read-only child gives the entire startup check a hard deadline, including
    # TLS/header reads. Only this task-owned verifier group is stopped on timeout.
    context = multiprocessing.get_context('fork')
    reader, writer = context.Pipe(duplex=False)
    worker = context.Process(target=_verification_child, args=(manifest, previous, writer))
    worker.start()
    writer.close()
    try:
        worker.join(timeout=45)
        if worker.is_alive():
            raise RuntimeError('New-service validation budget exhausted')
        if worker.exitcode != 0 or not reader.poll() or reader.recv() is not True:
            raise RuntimeError('New-service validation failed')
    finally:
        # Also covers operator interruption while startup verification is running.
        for stop_signal in (signal.SIGTERM, signal.SIGKILL):
            if not worker.is_alive():
                break
            try:
                if os.getpgid(worker.pid) == worker.pid:
                    os.killpg(worker.pid, stop_signal)
                else:
                    os.kill(worker.pid, stop_signal)
            except ProcessLookupError:
                pass
            worker.join(timeout=2)
        reader.close()


def apply(manifest):
    validate_payload(manifest)
    ensure_idle()
    previous = validate_services(manifest)
    backup = INSTALL / 'backups' / ('connection-evidence-' + datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ-') + uuid.uuid4().hex[:8])
    backup.mkdir(parents=True, mode=0o700)
    os.chmod(backup, 0o700)
    saved = {'files': {}, 'services': {unit: {key: value[key] for key in ('ActiveState', 'SubState', 'MainPID')} for unit, value in previous.items()}}
    for name in FILES:
        target = PACKAGE / name
        if target.exists():
            info = target.stat()
            shutil.copy2(target, backup / name)
            os.chmod(backup / name, 0o600)
            saved['files'][name] = {'existed': True, 'sha256': digest(target), 'mode': info.st_mode & 0o777, 'uid': info.st_uid, 'gid': info.st_gid, 'atimeNs': info.st_atime_ns, 'mtimeNs': info.st_mtime_ns, 'afterSha256': manifest['files'][name]['afterSha256']}
        else:
            saved['files'][name] = {'existed': False, 'afterSha256': manifest['files'][name]['afterSha256']}
    (backup / 'rollback.json').write_text(json.dumps(saved, indent=2) + '\n')
    os.chmod(backup / 'rollback.json', 0o600)
    # Validate imports against the exact closed file set before stopping services.
    with tempfile.TemporaryDirectory(prefix='connection-import-check-', dir=str(backup)) as temporary:
        stage = Path(temporary)
        (stage / 'jetson_control').mkdir()
        for name in manifest['untouchedModules']:
            shutil.copy2(PACKAGE / name, stage / 'jetson_control' / name)
        for name in FILES:
            shutil.copy2(SOURCE / name, stage / 'jetson_control' / name)
        script = 'import sys; sys.dont_write_bytecode=True; sys.path.insert(0,' + repr(str(stage)) + '); from jetson_control.api import create_app; from jetson_control.wifi_direct import WifiDirectController'
        command([str(INSTALL / 'venv/bin/python'), '-I', '-c', script])
    # Reject any work that started while files/imports were being checked.
    ensure_idle()
    try:
        stop_services()
        for name in FILES:
            meta = saved['files'][name]
            atomic_write(PACKAGE / name, (SOURCE / name).read_bytes(),
                         meta.get('mode', 0o644), meta.get('uid', 0), meta.get('gid', 0))
            if digest(PACKAGE / name) != manifest['files'][name]['afterSha256']:
                raise RuntimeError('Post-copy hash mismatch')
        start_previous_services(saved['services'])
        verify_started(manifest, previous)
        result = {'status': 'DEPLOYED', 'backup': str(backup), 'files': list(FILES), 'units': list(UNITS), 'publicHello': 'TLS_VERIFIED_200', 'newProcessStartEvidence': True, 'authenticatedPhoneSession': 'NOT_TESTED_BY_THIS_SCRIPT'}
    except BaseException:
        try:
            restore(backup)
            result = {'status': 'FAILED_ROLLED_BACK', 'backup': str(backup)}
        except BaseException:
            result = {'status': 'FAILED_ROLLBACK_INCOMPLETE', 'backup': str(backup)}
        (backup / 'result.json').write_text(json.dumps(result, indent=2) + '\n')
        print(json.dumps(result))
        raise
    (backup / 'result.json').write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps(result))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apply', action='store_true')
    parser.add_argument('--rollback', type=Path)
    parser.add_argument('--confirm-maintenance-idle', action='store_true')
    parser.add_argument('--confirm-usb-adb', action='store_true')
    parser.add_argument('--confirm-independent-management', action='store_true')
    args = parser.parse_args()
    manifest = read_json(HERE / 'backend-deployment-manifest.json')
    if not args.apply and args.rollback is None:
        validate_payload(manifest)
        validate_services(manifest)
        ensure_idle()
        print(json.dumps({'status': 'PREFLIGHT_PASSED_NO_MUTATION', 'files': list(FILES), 'units': list(UNITS), 'applyPrerequisites': ['root execution', 'verified USB ADB', 'working independent management path', 'confirmed idle maintenance window'], 'externalSafetyPrerequisites': 'NOT_AUTOMATICALLY_PROVEN; USB/independent path/operator idle confirmation still required'}))
        return
    if os.geteuid() != 0:
        raise RuntimeError('Run the reviewed script using an explicitly authorized root session')
    if not (args.confirm_maintenance_idle and args.confirm_usb_adb and args.confirm_independent_management):
        raise RuntimeError('The original USB/independent-management/maintenance preconditions must actually be verified')
    if args.rollback is not None:
        reject_symlink_parents(args.rollback.absolute())
        candidate = args.rollback.resolve()
        if candidate.parent != INSTALL / 'backups' or not candidate.name.startswith('connection-evidence-'):
            raise RuntimeError('Unexpected backup location')
        restore(candidate)
        print(json.dumps({'status': 'ROLLED_BACK', 'backup': str(candidate)}))
    else:
        apply(manifest)


if __name__ == '__main__':
    try:
        main()
    except Exception as error:
        # Do not print request/config/certificate bodies or unfiltered command output.
        print(json.dumps({'status': 'BLOCKED_OR_FAILED', 'errorType': type(error).__name__, 'reason': str(error) if isinstance(error, RuntimeError) else 'Required read or command failed; private exception contents omitted'}))
        raise SystemExit(1)
