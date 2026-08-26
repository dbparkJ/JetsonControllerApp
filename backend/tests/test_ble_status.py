import struct
import unittest

from jetson_control.ble import (
    CMD_REQUEST_WIFI_DIRECT,
    MAGIC,
    MAX_WIFI_SSID_BYTES,
    PROTOCOL_VERSION,
    WIFI_STATUS_FLAG_CONNECTED,
    CommandCharacteristic,
    encode_status_packet,
)


class BleStatusPacketTest(unittest.TestCase):
    def test_connected_wifi_is_appended_to_legacy_status_fields(self) -> None:
        packet = encode_status_packet(
            (1, 10, 20, 30, 40, 0x05, 1024, 2048, True, "Field Wi-Fi")
        )

        self.assertEqual(
            struct.unpack("<BBBbBBII", packet[:14]),
            (1, 10, 20, 30, 40, 0x05, 1024, 2048),
        )
        self.assertEqual(packet[14], WIFI_STATUS_FLAG_CONNECTED)
        self.assertEqual(packet[15], len("Field Wi-Fi".encode("utf-8")))
        self.assertEqual(packet[16:].decode("utf-8"), "Field Wi-Fi")

    def test_disconnected_wifi_does_not_include_stale_ssid(self) -> None:
        packet = encode_status_packet(
            (1, 0, 0, 0, 0, 0, 0, 0, False, "stale network")
        )

        self.assertEqual(packet[14:], b"\x00\x00")

    def test_utf8_ssid_is_bounded_without_splitting_a_character(self) -> None:
        packet = encode_status_packet(
            (1, 0, 0, 0, 0, 0, 0, 0, True, "현장-" + "가" * 20)
        )

        self.assertLessEqual(packet[15], MAX_WIFI_SSID_BYTES)
        self.assertEqual(len(packet[16:]), packet[15])
        packet[16:].decode("utf-8")

    def test_authenticated_command_0x08_requests_wifi_direct_mode(self) -> None:
        class Auth:
            def __init__(self):
                self.refreshed = False

            def authorized(self, _device):
                return True

            def refresh(self, _device):
                self.refreshed = True

        class Wifi:
            def __init__(self):
                self.requests = 0

            def request_direct_mode(self):
                self.requests += 1

        auth = Auth()
        wifi = Wifi()
        characteristic = CommandCharacteristic.__new__(CommandCharacteristic)
        characteristic.auth = auth
        characteristic.commands = None
        characteristic.wifi = wifi
        header = bytes((MAGIC, PROTOCOL_VERSION, CMD_REQUEST_WIFI_DIRECT, 0))

        characteristic.WriteValue(header + bytes((sum(header) & 0xFF,)), {"device": "peer"})

        self.assertEqual(wifi.requests, 1)
        self.assertTrue(auth.refreshed)


if __name__ == "__main__":
    unittest.main()
