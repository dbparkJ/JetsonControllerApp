import os
import tempfile
import unittest
from pathlib import Path

from jetson_control.mobile_rtk import MobileRtkRelayRegistry


class MobileRtkRelayRegistryTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory()
        self.path = Path(self.temporary.name) / "mobile-rtk-relay.json"
        self.now = 1_777_000_000_000
        self.registry = MobileRtkRelayRegistry(
            self.path,
            clock_millis=lambda: self.now,
            owner_uid=os.getuid(),
        )

    def tearDown(self) -> None:
        self.temporary.cleanup()

    def test_registers_and_reads_short_lived_route(self) -> None:
        response = self.registry.register("capture", "192.168.49.71", 32101)

        self.assertTrue(response["active"])
        self.assertEqual(self.registry.read()["relayHost"], "192.168.49.71")
        self.assertEqual(self.path.stat().st_mode & 0o777, 0o644)

        self.now = int(response["expiresAtEpochMillis"])
        self.assertIsNone(self.registry.read())
        self.assertIsNotNone(self.registry.read(require_active=False))

    def test_route_is_readable_by_pipeline_under_service_umask(self) -> None:
        previous_umask = os.umask(0o077)
        try:
            self.registry.register("capture", "192.168.49.71", 32101)
        finally:
            os.umask(previous_umask)

        self.assertEqual(self.path.stat().st_mode & 0o777, 0o644)

    def test_unregister_cannot_remove_another_pipeline_lease(self) -> None:
        self.registry.register("capture", "192.168.49.71", 32101)

        self.assertFalse(self.registry.unregister("other"))
        self.assertTrue(self.path.exists())
        self.assertTrue(self.registry.unregister("capture"))
        self.assertFalse(self.path.exists())

    def test_rejects_unsafe_relay_addresses_and_ports(self) -> None:
        for host, port in (("not-an-ip", 32101), ("0.0.0.0", 32101), ("192.168.49.71", 80)):
            with self.subTest(host=host, port=port), self.assertRaises(ValueError):
                self.registry.register("capture", host, port)


if __name__ == "__main__":
    unittest.main()
