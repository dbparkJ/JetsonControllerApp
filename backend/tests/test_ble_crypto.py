import unittest
from uuid import UUID

try:
    from jetson_control.ble_crypto import decrypt_wifi_payload, derive_session_key

    CRYPTO_AVAILABLE = True
except ImportError:
    CRYPTO_AVAILABLE = False


@unittest.skipUnless(CRYPTO_AVAILABLE, "python3-cryptography is not installed")
class BleCryptoTest(unittest.TestCase):
    def test_matches_android_encrypted_wifi_vector(self) -> None:
        device_id = UUID("00000000-0000-0000-0000-000000000001").bytes
        key = derive_session_key(bytes(range(32)), device_id, bytes(range(16)))
        self.assertEqual(
            key.hex(),
            "00c03ba2e92cd466dc132d90f0bc2698b280244043b60aee352fde5f04d5baca",
        )
        wire = bytes.fromhex(
            "02000102030405060708090a0baa2b754289fe7603d1e122753af38aa834521d"
            "616ffc1dca2a0185d950f1dbab74938da5f9fc513c"
        )
        self.assertEqual(
            decrypt_wifi_payload(wire, key, device_id),
            bytes((1, 1, 9, 11)) + b"JetsonNetpassword123",
        )

    def test_rejects_modified_ciphertext(self) -> None:
        device_id = UUID("00000000-0000-0000-0000-000000000001").bytes
        key = derive_session_key(bytes(range(32)), device_id, bytes(range(16)))
        wire = bytearray.fromhex(
            "02000102030405060708090a0baa2b754289fe7603d1e122753af38aa834521d"
            "616ffc1dca2a0185d950f1dbab74938da5f9fc513c"
        )
        wire[-1] ^= 1
        with self.assertRaises(ValueError):
            decrypt_wifi_payload(bytes(wire), key, device_id)


if __name__ == "__main__":
    unittest.main()
