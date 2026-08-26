import struct
import unittest

from jetson_control.ble import (
    MAX_WIFI_SSID_BYTES,
    WIFI_STATUS_FLAG_CONNECTED,
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


if __name__ == "__main__":
    unittest.main()
