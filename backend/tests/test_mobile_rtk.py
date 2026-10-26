import json
import os
import tempfile
import unittest
from pathlib import Path

from jetson_control.mobile_rtk import MobileRtkRelayRegistry, mobile_rtk_relay_environment


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
        invalid_routes = (
            ("not-an-ip", 32101),
            ("0.0.0.0", 32101),
            ("192.168.49.71", 80),
        )
        for host, port in invalid_routes:
            with self.subTest(host=host, port=port), self.assertRaises(ValueError):
                self.registry.register("capture", host, port)

    def environment(self, pipeline_id="capture"):
        return mobile_rtk_relay_environment(
            pipeline_id, self.path, expected_owner_uid=os.getuid(),
            clock_millis=lambda: self.now,
        )

    def test_environment_tracks_endpoint_but_ignores_heartbeat_expiry_extension(self) -> None:
        self.registry.register("capture", "192.168.49.71", 32101)
        initial = self.environment()
        self.now += 1000
        self.registry.register("capture", "192.168.49.71", 32101)
        self.assertEqual(self.environment(), initial)
        self.registry.register("capture", "192.168.49.72", 32102)
        self.assertNotEqual(self.environment(), initial)
        self.assertEqual(self.environment("other"), {})
        self.now += 30_000
        self.assertEqual(self.environment(), {})
        self.registry.register("capture", "192.168.49.71", 32101)
        self.registry.unregister("capture")
        self.assertEqual(self.environment(), {})

    def test_environment_reuses_registry_trust_and_route_validation(self) -> None:
        self.registry.register("capture", "192.168.49.71", 32101)
        self.path.chmod(0o666)
        self.assertEqual(self.environment(), {})
        self.path.chmod(0o644)
        self.assertEqual(mobile_rtk_relay_environment(
            "capture", self.path, expected_owner_uid=os.getuid() + 1,
            clock_millis=lambda: self.now,
        ), {})
        original = self.path.read_text()
        for host in ("0.0.0.0", "224.0.0.1", "::1", "bad-host"):
            with self.subTest(host=host):
                value = json.loads(original)
                value["relayHost"] = host
                self.path.write_text(json.dumps(value))
                self.assertEqual(self.environment(), {})

    def test_environment_rejects_symlink_and_malformed_marker(self) -> None:
        self.registry.register("capture", "192.168.49.71", 32101)
        target = self.path.with_name("target.json")
        self.path.rename(target)
        self.path.symlink_to(target)
        self.assertEqual(self.environment(), {})
        self.path.unlink()
        self.path.write_text("{broken")
        self.assertEqual(self.environment(), {})


if __name__ == "__main__":
    unittest.main()
