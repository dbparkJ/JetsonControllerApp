import unittest

from jetson_control.auth import RequestAuthenticator, sign_request, sign_response
from jetson_control.config import DeviceConfig


def device_config() -> DeviceConfig:
    return DeviceConfig(
        device_id="00000000-0000-0000-0000-000000000001",
        device_name="MMS-TEST",
        bootstrap_secret=bytes(range(32)),
        controlled_services=(),
        service_flags={"camera": "", "lidar": "", "gnss": "", "mms": ""},
        allow_power_commands=False,
        wifi_interface="wlan0",
    )


class RequestAuthenticatorTest(unittest.TestCase):
    def test_accepts_valid_signature_once(self) -> None:
        config = device_config()
        auth = RequestAuthenticator(
            config,
            boot_nonce="boot",
            nonce_capacity=4,
            clock=lambda: 1700000000,
        )
        signature = sign_request(
            config.bootstrap_secret,
            config.device_id,
            "boot",
            "request-0001",
            "1700000000",
            "GET",
            "/v1/fs/list?root=data&path=run%201",
        )

        self.assertTrue(
            auth.verify(
                config.device_id,
                "request-0001",
                "1700000000",
                "GET",
                "/v1/fs/list?root=data&path=run%201",
                b"",
                signature,
            )
        )
        self.assertFalse(
            auth.verify(
                config.device_id,
                "request-0001",
                "1700000000",
                "GET",
                "/v1/fs/list?root=data&path=run%201",
                b"",
                signature,
            )
        )

    def test_signature_covers_body_and_query(self) -> None:
        config = device_config()
        auth = RequestAuthenticator(
            config,
            boot_nonce="boot",
            clock=lambda: 1700000000,
        )
        signature = sign_request(
            config.bootstrap_secret,
            config.device_id,
            "boot",
            "request-0002",
            "1700000000",
            "POST",
            "/v1/uploads",
            b'{}',
        )
        self.assertFalse(
            auth.verify(
                config.device_id,
                "request-0002",
                "1700000000",
                "POST",
                "/v1/uploads",
                b'{"changed":true}',
                signature,
            )
        )

    def test_rejects_requests_outside_clock_window(self) -> None:
        config = device_config()
        auth = RequestAuthenticator(
            config,
            boot_nonce="boot",
            max_clock_skew_seconds=120,
            clock=lambda: 1700000500,
        )
        signature = sign_request(
            config.bootstrap_secret,
            config.device_id,
            "boot",
            "request-0003",
            "1700000000",
            "GET",
            "/v1/status",
        )
        self.assertFalse(
            auth.verify(
                config.device_id,
                "request-0003",
                "1700000000",
                "GET",
                "/v1/status",
                b"",
                signature,
            )
        )

    def test_clock_rebase_preserves_seen_nonces(self) -> None:
        config = device_config()
        now = [1700000000]
        auth = RequestAuthenticator(
            config,
            boot_nonce="boot",
            clock=lambda: now[0],
        )
        signature = sign_request(
            config.bootstrap_secret,
            config.device_id,
            "boot",
            "request-clock-1",
            "1700000000",
            "GET",
            "/v1/status",
        )
        request = (
            config.device_id,
            "request-clock-1",
            "1700000000",
            "GET",
            "/v1/status",
            b"",
            signature,
        )
        self.assertTrue(auth.verify(*request))

        now[0] -= 60
        auth.reset_after_clock_change()

        self.assertFalse(auth.verify(*request))
        fresh_signature = sign_request(
            config.bootstrap_secret,
            config.device_id,
            "boot",
            "request-clock-2",
            str(now[0]),
            "GET",
            "/v1/status",
        )
        self.assertTrue(
            auth.verify(
                config.device_id,
                "request-clock-2",
                str(now[0]),
                "GET",
                "/v1/status",
                b"",
                fresh_signature,
            )
        )

    def test_response_signature_covers_status_and_body(self) -> None:
        config = device_config()
        signature = sign_response(
            config.bootstrap_secret,
            config.device_id,
            "boot",
            "request-0004",
            "1700000000",
            200,
            b'{"ok":true}',
        )
        self.assertNotEqual(
            signature,
            sign_response(
                config.bootstrap_secret,
                config.device_id,
                "boot",
                "request-0004",
                "1700000000",
                200,
                b'{"ok":false}',
            ),
        )


if __name__ == "__main__":
    unittest.main()
