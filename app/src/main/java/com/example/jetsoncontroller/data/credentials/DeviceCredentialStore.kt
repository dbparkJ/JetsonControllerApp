package com.example.jetsoncontroller.data.credentials

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.jetsoncontroller.model.PairingInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "device_credentials")

class DeviceCredentialStore(private val context: Context) {

    private val KEY_ALIAS = "jetson_auth_key"
    private val ANDROID_KEYSTORE = "AndroidKeyStore"
    private val AES_MODE = "AES/GCM/NoPadding"

    data class StoredCredential(
        val deviceId: String,
        val deviceName: String,
        val encryptedSecretBase64: String,
        val ivBase64: String
    )

    private fun getSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        keyStore.getKey(KEY_ALIAS, null)?.let { return it as SecretKey }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return keyGenerator.generateKey()
    }

    private fun encrypt(data: String): Pair<String, String> {
        val cipher = Cipher.getInstance(AES_MODE)
        cipher.init(Cipher.ENCRYPT_MODE, getSecretKey())
        val iv = cipher.iv
        val encrypted = cipher.doFinal(data.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(encrypted, Base64.DEFAULT) to Base64.encodeToString(iv, Base64.DEFAULT)
    }

    private fun decrypt(encryptedBase64: String, ivBase64: String): String {
        val encrypted = Base64.decode(encryptedBase64, Base64.DEFAULT)
        val iv = Base64.decode(ivBase64, Base64.DEFAULT)
        val cipher = Cipher.getInstance(AES_MODE)
        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), spec)
        return String(cipher.doFinal(encrypted), Charsets.UTF_8)
    }

    suspend fun saveCredential(pairingInfo: PairingInfo) {
        val (encryptedSecret, iv) = encrypt(pairingInfo.bootstrapSecretHex)
        val key = stringPreferencesKey("device_${pairingInfo.deviceId}")
        context.dataStore.edit { prefs ->
            // In a real app we might store more metadata, for now we store a simple serialized format or multiple keys
            prefs[key] = "${pairingInfo.expectedBleName}|$encryptedSecret|$iv"
        }
    }

    suspend fun getSecret(deviceId: String): String? {
        val key = stringPreferencesKey("device_$deviceId")
        val data = context.dataStore.data.first()[key] ?: return null
        val parts = data.split("|")
        if (parts.size < 3) return null
        return decrypt(parts[1], parts[2])
    }

    val registeredDevices: Flow<List<StoredCredential>> = context.dataStore.data.map { prefs ->
        prefs.asMap().keys
            .filter { it.name.startsWith("device_") }
            .mapNotNull { key ->
                val deviceId = key.name.removePrefix("device_")
                val data = prefs[stringPreferencesKey(key.name)] ?: return@mapNotNull null
                val parts = data.split("|")
                if (parts.size < 3) return@mapNotNull null
                StoredCredential(deviceId, parts[0], parts[1], parts[2])
            }
    }
}
