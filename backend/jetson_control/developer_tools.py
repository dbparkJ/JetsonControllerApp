"""Read-only network and sensor tests using the existing sensor owner/bridge."""
from __future__ import annotations

import ipaddress
import re
import socket
import subprocess
import threading
import time
from pathlib import Path

import psutil
from fastapi import HTTPException


def network_addresses() -> dict:
    interfaces = []
    stats = psutil.net_if_stats()
    for name, addresses in psutil.net_if_addrs().items():
        if name.startswith(('lo', 'docker', 'veth', 'br-', 'dummy')):
            continue
        if name not in stats or not stats[name].isup:
            continue
        for address in addresses:
            if address.family not in (socket.AF_INET, socket.AF_INET6):
                continue
            parsed = ipaddress.ip_address(address.address.split('%')[0])
            if parsed.is_loopback or parsed.is_link_local or parsed.is_unspecified:
                continue
            kind = ('Tailscale' if name.startswith('tailscale') else
                    'Wi-Fi Direct' if name.startswith('p2p') else
                    'Wi-Fi' if name.startswith(('wlan', 'wlp')) else 'Ethernet / 기타')
            host = f'[{parsed}]' if parsed.version == 6 else str(parsed)
            interfaces.append(dict(interface=name, kind=kind, address=str(parsed),
                                   family=f'IPv{parsed.version}', apiUrl=f'https://{host}:8765'))
    interfaces.sort(key=lambda item: (item['kind'] == 'Tailscale', item['interface'], item['family']))
    return dict(interfaces=interfaces, checkedAtEpochMillis=int(time.time() * 1000))


def usb_inventory(root: Path = Path('/sys/bus/usb/devices')) -> list:
    devices = []
    for path in sorted(root.glob('*')):
        try:
            vendor = (path / 'idVendor').read_text().strip().lower()
            product_id = (path / 'idProduct').read_text().strip().lower()
        except OSError:
            continue
        def read(name):
            try:
                return (path / name).read_text().strip()[:128]
            except OSError:
                return ''
        product = read('product')
        manufacturer = read('manufacturer')
        identity = (product + ' ' + manufacturer).lower()
        role = ('camera' if vendor == '03e7' else
                'gps' if vendor == '1546' or any(token in identity for token in ('u-blox', 'ublox', 'gnss', 'gps'))
                else 'other')
        devices.append(dict(port=path.name, vendor=vendor, productId=product_id,
                            product=product, role=role, speed=read('speed')))
    return devices


def serial_inventory(root: Path = Path('/dev')) -> list:
    # Inventory only. Never open a serial port already owned by the monitor.
    return sorted(str(path) for pattern in ('ttyACM*', 'ttyUSB*', 'serial/by-id/*')
                  for path in root.glob(pattern) if path.exists())


def recent_usb_power_error() -> bool:
    try:
        result = subprocess.run(['/bin/dmesg'], capture_output=True, text=True,
                                check=False, timeout=2)
        uptime = float(Path('/proc/uptime').read_text().split()[0])
    except (OSError, ValueError, subprocess.SubprocessError):
        return False
    if result.returncode:
        return False
    for line in result.stdout[-65536:].splitlines():
        match = re.match(r'\[\s*([0-9.]+)\]', line)
        if match and 'over-current' in line.lower() and 0 <= uptime - float(match[1]) <= 60:
            return True
    return False


def test_sensor(sensor, bridge, *, duration=5.0, clock=time.monotonic,
                sleep=time.sleep, inventory=usb_inventory, serial=serial_inventory,
                power_error=recent_usb_power_error, wall_clock=time.time) -> dict:
    if sensor not in ('camera', 'gps'):
        raise ValueError('Unknown sensor')
    start = clock()
    first = bridge.status()
    key = 'lastFrameAtEpochMillis' if sensor == 'camera' else 'lastSampleAtEpochMillis'
    values = first.camera if sensor == 'camera' else first.gnss
    baseline = values.get(key)
    preview_baseline = first.camera.get('previewUpdatedAtEpochMillis')
    seen = {}
    passed = False
    preview_error = None
    latest = first
    while clock() - start < duration:
        for item in inventory():
            seen[item['port']] = item
        sleep(min(0.5, max(0, duration - (clock() - start))))
        latest = bridge.status()
        values = latest.camera if sensor == 'camera' else latest.gnss
        sample = values.get(key)
        # A fresh bridge heartbeat alone is not proof of new sensor data.
        advancing = (isinstance(sample, int) and not isinstance(sample, bool)
                     and (baseline is None or sample > baseline)
                     and 0 <= wall_clock() * 1000 - sample <= 5000)
        if latest.fresh and values.get('connected') and values.get('active') and advancing:
            if sensor == 'camera':
                try:
                    if not values.get('previewAvailable'):
                        raise OSError('Camera preview is unavailable')
                    preview_sample = values.get('previewUpdatedAtEpochMillis')
                    if (not isinstance(preview_sample, int)
                            or not 0 <= wall_clock() * 1000 - preview_sample <= 5000
                            or (preview_baseline is not None and preview_sample <= preview_baseline)):
                        raise OSError('Camera preview is stale')
                    bridge.preview_frame()
                except OSError as error:
                    preview_error = str(error)
                else:
                    passed = True
            else:
                passed = True
    matching = [item for item in seen.values() if item['role'] == sensor]
    received = passed
    passed = passed and latest.fresh and bool(values.get('connected')) and bool(values.get('active'))
    details = [f"USB {item['port']}: {item['vendor']}:{item['productId']} {item['product']} · {item['speed']} Mbps"
               for item in matching]
    ports = serial() if sensor == 'gps' else []
    if sensor == 'gps':
        details.append('시리얼 포트: ' + (', '.join(ports) if ports else '없음'))
        details.extend([
            f"Fix: {values.get('fixName', 'unknown')} · 위성: {values.get('satellites')}",
            f"위도/경도: {values.get('latitude')} / {values.get('longitude')} · HDOP: {values.get('hdop')}",
        ])
    else:
        details.append(f"프레임: {values.get('frameWidth')} × {values.get('frameHeight')}")
    error = values.get('error') or (latest.pipeline.get('error') if sensor == 'camera' else None)
    for evidence in (error, preview_error):
        if evidence:
            details.append(str(evidence)[:256])
    if received and not passed:
        details.append('데이터가 잠시 수신되었으나 테스트가 끝나기 전에 센서 연결이 끊겼습니다.')
    if passed:
        state = 'PASSED'
        message = '새 카메라 프레임과 JPEG 프리뷰 수신을 확인했습니다.' if sensor == 'camera' else '새 GPS 데이터 수신을 확인했습니다.'
        if sensor == 'gps' and not values.get('fixQuality'):
            message += ' 위성 위치는 아직 확정되지 않았습니다.'
    elif not latest.available or not latest.fresh:
        state, message = 'STALE', '센서 모니터 상태가 없거나 오래되었습니다. 모니터 서비스와 로그를 확인하세요.'
    elif matching or ports or values.get('connected') or received:
        state, message = 'NO_DATA', '장치 후보가 있지만 새 센서 데이터를 확인하지 못했습니다. USB 권한·설정·모니터 로그를 확인하세요.'
    else:
        state, message = 'NOT_DETECTED', '테스트 중 USB 장치가 인식되지 않았습니다. 케이블·USB 포트·허브 전원을 확인하세요.'
    if not passed and power_error():
        details.append('최근 60초 내 USB over-current 오류: 다른 USB 포트 또는 전원 공급형 허브로 연결하세요.')
    return dict(sensor=sensor, state=state, message=message, details=details,
                durationSeconds=round(clock() - start, 1), checkedAtEpochMillis=int(wall_clock() * 1000))


def register_developer_routes(app, authenticated, sensor_bridge):
    lock = threading.Lock()

    @app.get('/v1/developer/network', dependencies=authenticated)
    def network():
        try:
            return network_addresses()
        except (OSError, ValueError) as error:
            raise HTTPException(503, 'Network addresses are unavailable') from error

    @app.post('/v1/developer/sensors/{sensor}/test', dependencies=authenticated)
    def sensor_test(sensor: str):
        if sensor not in ('camera', 'gps'):
            raise HTTPException(404, 'Unknown sensor')
        if not lock.acquire(blocking=False):
            raise HTTPException(409, 'A sensor test is already running')
        try:
            return test_sensor(sensor, sensor_bridge)
        finally:
            lock.release()
