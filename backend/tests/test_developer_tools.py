import socket
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import Mock, patch

from fastapi import Depends, FastAPI, HTTPException
from fastapi.testclient import TestClient

from jetson_control.developer_tools import (
    network_addresses, register_developer_routes, test_sensor, usb_inventory,
)


def snapshot(sample=None, *, fresh=True, active=True, available=True, fix=1):
    return SimpleNamespace(available=available, fresh=fresh, pipeline={},
        camera=dict(connected=active, active=active, lastFrameAtEpochMillis=sample,
                    previewAvailable=True, previewUpdatedAtEpochMillis=sample,
                    frameWidth=1920, frameHeight=1200),
        gnss=dict(connected=active, active=active, lastSampleAtEpochMillis=sample,
                  fixQuality=fix, fixName='3D', latitude=37.5, longitude=127.0, satellites=8))


class DeveloperToolsTest(unittest.TestCase):
    def run_sensor(self, sensor, snapshots, *, devices=None, power=False):
        elapsed = [0.0]
        def sleep(seconds):
            elapsed[0] += seconds
        bridge = Mock()
        bridge.status.side_effect = snapshots
        result = test_sensor(sensor, bridge, duration=1.0, clock=lambda: elapsed[0],
            wall_clock=lambda: 100.0 + elapsed[0], sleep=sleep,
            inventory=lambda: devices or [], serial=lambda: [], power_error=lambda: power)
        return result, bridge

    def test_network_only_returns_active_routable_addresses(self):
        addresses = {
            'wlan0': [SimpleNamespace(family=socket.AF_INET, address='111.111.111.45'),
                      SimpleNamespace(family=socket.AF_INET6, address='fe80::1%wlan0')],
            'tailscale0': [SimpleNamespace(family=socket.AF_INET, address='100.76.81.78')],
            'eth0': [SimpleNamespace(family=socket.AF_INET, address='192.168.1.1')],
            'docker0': [SimpleNamespace(family=socket.AF_INET, address='172.17.0.1')],
            'lo': [SimpleNamespace(family=socket.AF_INET, address='127.0.0.1')],
        }
        stats = {name: SimpleNamespace(isup=name != 'eth0') for name in addresses}
        with patch('jetson_control.developer_tools.psutil.net_if_addrs', return_value=addresses), \
             patch('jetson_control.developer_tools.psutil.net_if_stats', return_value=stats):
            result = network_addresses()['interfaces']
        self.assertEqual([item['address'] for item in result], ['111.111.111.45', '100.76.81.78'])
        self.assertEqual(result[1]['kind'], 'Tailscale')

    def test_usb_inventory_distinguishes_gps_from_phone_and_imu(self):
        with tempfile.TemporaryDirectory() as directory:
            for port, vendor, product in [('1-1', '1546', 'u-blox GNSS'), ('1-2', '03e7', 'Luxonis'),
                                          ('1-3', '04e8', 'Android'), ('1-4', '10c4', 'CP2102')]:
                root = Path(directory) / port
                root.mkdir()
                (root / 'idVendor').write_text(vendor)
                (root / 'idProduct').write_text('0001')
                (root / 'product').write_text(product)
            result = usb_inventory(Path(directory))
        self.assertEqual([item['role'] for item in result], ['gps', 'camera', 'other', 'other'])

    def test_camera_requires_advancing_frame_and_valid_preview(self):
        result, bridge = self.run_sensor('camera', [snapshot(100000), snapshot(100500), snapshot(101000)])
        self.assertEqual(result['state'], 'PASSED')
        bridge.preview_frame.assert_called()

    def test_frozen_frame_is_not_a_success_even_with_fresh_heartbeat(self):
        result, bridge = self.run_sensor('camera', [snapshot(100000)] * 3)
        self.assertEqual(result['state'], 'NO_DATA')
        bridge.preview_frame.assert_not_called()

    def test_disconnect_during_test_is_not_reported_as_healthy(self):
        result, _ = self.run_sensor('camera', [snapshot(100000), snapshot(100500), snapshot(101000, active=False)])
        self.assertEqual(result['state'], 'NO_DATA')
        self.assertTrue(any('연결이 끊겼' in detail for detail in result['details']))

    def test_advancing_camera_frames_with_frozen_preview_do_not_pass(self):
        snapshots = [snapshot(100000), snapshot(100500), snapshot(101000)]
        for value in snapshots:
            value.camera['previewUpdatedAtEpochMillis'] = 100000
        result, _ = self.run_sensor('camera', snapshots)
        self.assertNotEqual(result['state'], 'PASSED')

    def test_invalid_jpeg_is_not_a_success(self):
        elapsed = [0]
        bridge = Mock()
        bridge.status.side_effect = [snapshot(100000), snapshot(100500)]
        bridge.preview_frame.side_effect = OSError('Camera preview is not a valid JPEG')
        result = test_sensor('camera', bridge, duration=0.5, clock=lambda: elapsed[0],
            wall_clock=lambda: 100 + elapsed[0], sleep=lambda seconds: elapsed.__setitem__(0, elapsed[0] + seconds),
            inventory=lambda: [], serial=lambda: [], power_error=lambda: False)
        self.assertEqual(result['state'], 'NO_DATA')
        self.assertIn('Camera preview is not a valid JPEG', result['details'])

    def test_gps_without_fix_still_proves_receiver_data(self):
        result, _ = self.run_sensor('gps', [snapshot(100000, fix=0), snapshot(100500, fix=0), snapshot(101000, fix=0)])
        self.assertEqual(result['state'], 'PASSED')
        self.assertIn('아직 확정되지', result['message'])

    def test_old_sample_cannot_pass_even_if_timestamp_advances(self):
        result, _ = self.run_sensor('gps', [snapshot(1000), snapshot(2000), snapshot(3000)])
        self.assertNotEqual(result['state'], 'PASSED')

    def test_missing_usb_includes_recent_power_error(self):
        result, _ = self.run_sensor('gps', [snapshot(active=False)] * 3, power=True)
        self.assertEqual(result['state'], 'NOT_DETECTED')
        self.assertTrue(any('over-current' in detail for detail in result['details']))

    def test_stale_bridge_never_passes(self):
        result, _ = self.run_sensor('camera', [snapshot(100000, fresh=False),
                                               snapshot(100500, fresh=False), snapshot(101000, fresh=False)])
        self.assertEqual(result['state'], 'STALE')

    def test_routes_require_authentication_and_reject_unknown_sensor(self):
        permitted = [False]
        def auth():
            if not permitted[0]:
                raise HTTPException(401, 'Authentication failed')
        app = FastAPI()
        register_developer_routes(app, [Depends(auth)], Mock())
        client = TestClient(app)
        with patch('jetson_control.developer_tools.test_sensor') as sensor_test:
            self.assertEqual(client.get('/v1/developer/network').status_code, 401)
            self.assertEqual(client.post('/v1/developer/sensors/camera/test').status_code, 401)
            permitted[0] = True
            self.assertEqual(client.post('/v1/developer/sensors/other/test').status_code, 404)
            sensor_test.assert_not_called()
            sensor_test.return_value = {'state': 'PASSED'}
            self.assertEqual(client.post('/v1/developer/sensors/gps/test').json()['state'], 'PASSED')


if __name__ == '__main__':
    unittest.main()
