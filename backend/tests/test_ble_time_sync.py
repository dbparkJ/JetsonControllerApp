import os
import struct
import subprocess
import tempfile
import unittest
from pathlib import Path

from jetson_control.ble import (
    AUTH_CAP_MOBILE_TIME_SYNC, CMD_SYNC_TIME, AuthStateCharacteristic,
    BleAuthenticator, CommandCharacteristic, Failed, NotAuthorized,
)
from jetson_control.system_control import SystemTimeSynchronizer
from tests.test_auth import device_config


def frame(payload):
    body = bytes((0x5A, 1, CMD_SYNC_TIME, len(payload))) + payload
    return body + bytes((sum(body) & 0xFF,))


class BleTimeSyncTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.root.chmod(0o700)
        self.current = 308.506335
        self.commands = []

        def run(command, **kwargs):
            self.commands.append(command)
            self.current = float(command[-1][1:])
            return subprocess.CompletedProcess(command, 0, "", "")

        self.synchronizer = SystemTimeSynchronizer(
            self.root / "time.json", run=run, clock=lambda: self.current,
            marker_owner_uid=os.getuid(),
        )
        self.auth = BleAuthenticator(device_config())
        self.device = "/phone"
        self.characteristic = CommandCharacteristic.__new__(CommandCharacteristic)
        self.characteristic.auth = self.auth
        self.characteristic.system_time = self.synchronizer

    def authorize(self):
        challenge = self.auth.challenge(self.device)
        import hashlib
        import hmac
        response = hmac.new(
            self.auth.config.bootstrap_secret,
            b"JETSONCTRL1|" + self.auth.device_id_bytes + b"|" + challenge,
            hashlib.sha256,
        ).digest()[:16]
        self.assertTrue(self.auth.verify(self.device, response))

    def test_unauthenticated_or_other_peer_cannot_set_time(self):
        request = frame(struct.pack("<Q", 1_792_728_600_000))
        with self.assertRaises(NotAuthorized):
            self.characteristic.WriteValue(request, {"device": self.device})
        self.authorize()
        with self.assertRaises(NotAuthorized):
            self.characteristic.WriteValue(request, {"device": "/other"})
        self.assertEqual(self.commands, [])

    def test_authenticated_sync_precedes_p2p_and_http_repeat_is_idempotent(self):
        self.authorize()
        requested = 1_792_728_600_000
        self.characteristic.WriteValue(frame(struct.pack("<Q", requested)), {"device": self.device})
        # The GATT write returns only after date verification and marker commit.
        self.assertTrue(self.synchronizer.status()["synchronized"])
        self.assertEqual(len(self.commands), 1)
        self.current += 2
        http_sync = SystemTimeSynchronizer(
            self.synchronizer.marker_path, run=lambda *a, **k: self.fail("second date"),
            clock=lambda: self.current, marker_owner_uid=os.getuid(),
        )
        self.assertTrue(http_sync.synchronize(requested + 2000)["synchronized"])
        self.assertEqual(len(self.commands), 1)

    def test_invalid_payload_range_and_checksum_cannot_set_time(self):
        self.authorize()
        for request in (frame(b""), frame(b"short"), frame(struct.pack("<Q", 0)),
                        frame(struct.pack("<Q", 4_102_444_800_000)),
                        frame(struct.pack("<Q", 1_792_728_600_000))[:-1] + b"\x00"):
            with self.subTest(request=request), self.assertRaises(Failed):
                self.characteristic.WriteValue(request, {"device": self.device})
        self.assertEqual(self.commands, [])

    def test_auth_state_preserves_legacy_byte_and_advertises_capability(self):
        state = AuthStateCharacteristic.__new__(AuthStateCharacteristic)
        state.auth = self.auth
        self.assertEqual(bytes(state.ReadValue({"device": self.device})), bytes((0, AUTH_CAP_MOBILE_TIME_SYNC)))
        self.authorize()
        self.assertEqual(bytes(state.ReadValue({"device": self.device})), bytes((1, AUTH_CAP_MOBILE_TIME_SYNC)))

    def test_date_failure_is_not_acknowledged(self):
        self.authorize()
        self.synchronizer._run = lambda command, **kwargs: subprocess.CompletedProcess(command, 1, "", "denied")
        with self.assertRaises(Failed):
            self.characteristic.WriteValue(frame(struct.pack("<Q", 1_792_728_600_000)), {"device": self.device})
        self.assertFalse(self.synchronizer.status()["synchronized"])
