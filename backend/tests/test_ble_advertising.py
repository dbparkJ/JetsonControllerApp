import unittest

from jetson_control.ble import (
    ADVERTISEMENT_INTERFACE,
    LEGACY_ADVERTISING_LIMIT,
    MAX_ADVERTISED_NAME_BYTES,
    SCAN_RESPONSE_HEADROOM,
    SERVICE_UUID,
    Advertisement,
    advertised_local_name,
    legacy_advertising_payload_sizes,
)


class BleAdvertisingTest(unittest.TestCase):
    def test_canonical_name_and_uuid_fit_separate_legacy_packets(self) -> None:
        primary_size, scan_response_size = legacy_advertising_payload_sizes(
            "MMS-D137"
        )

        self.assertEqual(primary_size, 21)
        self.assertEqual(scan_response_size, 10)
        self.assertLess(primary_size, LEGACY_ADVERTISING_LIMIT)
        self.assertLess(scan_response_size, LEGACY_ADVERTISING_LIMIT)

    def test_long_utf8_name_is_truncated_without_splitting_a_character(self) -> None:
        advertised = advertised_local_name("MMS-장비-" + "가" * 20)
        encoded = advertised.encode("utf-8")

        self.assertLessEqual(len(encoded), MAX_ADVERTISED_NAME_BYTES)
        self.assertEqual(encoded.decode("utf-8"), advertised)
        _, scan_response_size = legacy_advertising_payload_sizes(advertised)
        self.assertLessEqual(
            scan_response_size,
            LEGACY_ADVERTISING_LIMIT - SCAN_RESPONSE_HEADROOM,
        )

    def test_advertisement_is_connectable_discoverable_and_split_by_bluez(self) -> None:
        advertisement = Advertisement.__new__(Advertisement)
        advertisement.device_name = advertised_local_name("MMS-D137")

        properties = advertisement.get_properties()[ADVERTISEMENT_INTERFACE]

        self.assertEqual(str(properties["Type"]), "peripheral")
        self.assertTrue(bool(properties["Discoverable"]))
        self.assertEqual(
            [str(value) for value in properties["ServiceUUIDs"]],
            [SERVICE_UUID],
        )
        self.assertEqual(str(properties["LocalName"]), "MMS-D137")
        self.assertNotIn("Includes", properties)


if __name__ == "__main__":
    unittest.main()
