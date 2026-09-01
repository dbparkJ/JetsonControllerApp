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
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "device_credentials")

class DeviceCredentialStore(private val context: Context) {

    data class StoredCredential(
        val deviceId: String,
        val deviceName: String,
        val encryptedSecretBase64: String,
        val ivBase64: String
    )

    private data class EncodedCredential(
        val deviceName: String,
        val encryptedSecretBase64: String,
        val ivBase64: String
    )

    @Synchronized
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
        val encrypted = cipher.doFinal(data.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(encrypted, Base64.NO_WRAP) to
            Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
    }

    private fun decrypt(encryptedBase64: String, ivBase64: String): String {
        val encrypted = Base64.decode(encryptedBase64, Base64.DEFAULT)
        val iv = Base64.decode(ivBase64, Base64.DEFAULT)
        val cipher = Cipher.getInstance(AES_MODE)
        cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(encrypted), Charsets.UTF_8)
    }

    suspend fun saveCredential(
        pairingInfo: PairingInfo,
        deviceName: String = pairingInfo.expectedBleName
    ) {
        val (encryptedSecret, iv) = encrypt(pairingInfo.bootstrapSecretHex)
        val normalizedDeviceId = pairingInfo.deviceId.lowercase()
        val key = stringPreferencesKey("device_$normalizedDeviceId")
        val encoded = JSONObject()
            .put("version", 1)
            .put("deviceName", deviceName)
            .put("encryptedSecret", encryptedSecret)
            .put("iv", iv)
            .toString()

        context.dataStore.edit { prefs ->
            prefs.asMap().keys
                .map { it.name }
                .filter {
                    it.startsWith("device_") &&
                        it.removePrefix("device_").equals(normalizedDeviceId, ignoreCase = true)
                }
                .forEach { prefs.remove(stringPreferencesKey(it)) }
            prefs[key] = encoded
        }
    }

    suspend fun getSecret(deviceId: String): String? {
        val normalizedDeviceId = deviceId.lowercase()
        val prefs = context.dataStore.data.first()
        val value = prefs[stringPreferencesKey("device_$normalizedDeviceId")]
            ?: (prefs.asMap().entries.firstOrNull {
                it.key.name.startsWith("device_") &&
                    it.key.name.removePrefix("device_")
                        .equals(normalizedDeviceId, ignoreCase = true)
            }?.value as? String)
            ?: return null
        val credential = decodeCredential(value) ?: return null
        return runCatching {
            decrypt(credential.encryptedSecretBase64, credential.ivBase64)
        }.getOrNull()
    }

    suspend fun removeCredential(deviceId: String) {
        val normalizedDeviceId = deviceId.lowercase()
        context.dataStore.edit { prefs ->
            prefs.asMap().keys
                .map { it.name }
                .filter {
                    it.startsWith("device_") &&
                        it.removePrefix("device_").equals(normalizedDeviceId, ignoreCase = true)
                }
                .forEach { prefs.remove(stringPreferencesKey(it)) }
            if (prefs[PREFERRED_DEVICE_ID_KEY]
                    ?.equals(normalizedDeviceId, ignoreCase = true) == true
            ) {
                prefs.remove(PREFERRED_DEVICE_ID_KEY)
            }
        }
    }

    suspend fun getPreferredDeviceId(): String? =
        context.dataStore.data.first()[PREFERRED_DEVICE_ID_KEY]
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.lowercase()

    suspend fun setPreferredDeviceId(deviceId: String?) {
        context.dataStore.edit { prefs ->
            val normalizedDeviceId = deviceId?.trim()?.takeIf { it.isNotEmpty() }?.lowercase()
            if (normalizedDeviceId == null) {
                prefs.remove(PREFERRED_DEVICE_ID_KEY)
            } else {
                prefs[PREFERRED_DEVICE_ID_KEY] = normalizedDeviceId
            }
        }
    }

    val registeredDevices: Flow<List<StoredCredential>> = context.dataStore.data.map { prefs ->
        prefs.asMap().keys
            .filter { it.name.startsWith("device_") }
            .mapNotNull { key ->
                val deviceId = key.name.removePrefix("device_").lowercase()
                val data = prefs[stringPreferencesKey(key.name)] ?: return@mapNotNull null
                val credential = decodeCredential(data) ?: return@mapNotNull null
                StoredCredential(
                    deviceId = deviceId,
                    deviceName = credential.deviceName,
                    encryptedSecretBase64 = credential.encryptedSecretBase64,
                    ivBase64 = credential.ivBase64
                )
            }
            .distinctBy { it.deviceId }
    }

    private fun decodeCredential(value: String): EncodedCredential? = runCatching {
        if (value.trimStart().startsWith("{")) {
            val json = JSONObject(value)
            require(json.getInt("version") == 1)
            EncodedCredential(
                deviceName = json.getString("deviceName"),
                encryptedSecretBase64 = json.getString("encryptedSecret"),
                ivBase64 = json.getString("iv")
            )
        } else {
            val parts = value.split('|', limit = 3)
            require(parts.size == 3)
            EncodedCredential(parts[0], parts[1], parts[2])
        }
    }.getOrNull()

    private companion object {
        val PREFERRED_DEVICE_ID_KEY = stringPreferencesKey("preferred_device_id")
        const val KEY_ALIAS = "jetson_auth_key"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val AES_MODE = "AES/GCM/NoPadding"
    }
}
