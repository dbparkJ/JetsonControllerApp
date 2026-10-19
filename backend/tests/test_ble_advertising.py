import unittest

from jetson_control.ble import (
    ADVERTISEMENT_INTERFACE,
    LEGACY_ADVERTISING_LIMIT,
    MAX_ADVERTISED_NAME_BYTES,
    SCAN_RESPONSE_HEADROOM,
    SERVICE_UUID,
    Advertisement,
    PrimerAdvertisement,
    advertised_local_name,
    legacy_advertising_payload_sizes,
    register_advertisement,
)


class FakeAdvertisement:
    def __init__(self, path: str) -> None:
        self.path = path

    def get_path(self) -> str:
        return self.path


class FakeAdvertisingManager:
    def __init__(self, failing=()) -> None:
        self.calls = []
        self.failing = set(failing)

    def RegisterAdvertisement(self, path, options, reply_handler, error_handler) -> None:
        self.calls.append(("register", path))
        if path in self.failing:
            error_handler(Exception("rejected"))
        else:
            reply_handler()

    def UnregisterAdvertisement(self, path) -> None:
        self.calls.append(("unregister", path))


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

    def test_primer_carries_neither_the_real_name_nor_the_service(self) -> None:
        for name in ("GEO-X-1", "MMS-D137", "~"):
            real = Advertisement.__new__(Advertisement)
            real.device_name = advertised_local_name(name)
            primer = PrimerAdvertisement.__new__(PrimerAdvertisement)
            primer.device_name = advertised_local_name(name)

            real_properties = real.get_properties()[ADVERTISEMENT_INTERFACE]
            primer_properties = primer.get_properties()[ADVERTISEMENT_INTERFACE]

            self.assertEqual(str(primer_properties["Type"]), "broadcast")
            self.assertNotIn("ServiceUUIDs", primer_properties)
            self.assertNotIn("Discoverable", primer_properties)
            self.assertNotEqual(
                str(primer_properties["LocalName"]), str(real_properties["LocalName"])
            )

    def test_primer_is_registered_and_removed_before_the_real_advertisement(self) -> None:
        manager = FakeAdvertisingManager()
        ready = []

        register_advertisement(
            manager, FakeAdvertisement("/primer"), FakeAdvertisement("/real"),
            reply_handler=lambda: ready.append(True), error_handler=self.fail,
        )

        self.assertEqual(
            manager.calls,
            [("register", "/primer"), ("unregister", "/primer"), ("register", "/real")],
        )
        self.assertEqual(ready, [True])

    def test_a_rejected_primer_still_registers_the_real_advertisement(self) -> None:
        manager = FakeAdvertisingManager(failing={"/primer"})
        ready = []

        register_advertisement(
            manager, FakeAdvertisement("/primer"), FakeAdvertisement("/real"),
            reply_handler=lambda: ready.append(True), error_handler=self.fail,
        )

        self.assertEqual(manager.calls, [("register", "/primer"), ("register", "/real")])
        self.assertEqual(ready, [True])


if __name__ == "__main__":
    unittest.main()
