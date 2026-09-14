package com.example.jetsoncontroller.data.server

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.jetsoncontroller.data.storage.RecentServerJobsCache
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Context.serverProfileDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "direct_server_profiles")

data class StoredServerProfile(
    val profile: ServerEndpointProfile,
    val credentialRevision: String
)

data class ServerConnection(
    val profile: ServerEndpointProfile,
    val employeeToken: String,
    val credentialRevision: String
)

class ServerProfileStore(
    context: Context,
    private val recentCache: RecentServerJobsCache
) {
    private val appContext = context.applicationContext

    @Synchronized
    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        keyStore.getKey(KEY_ALIAS, null)?.let { return it as SecretKey }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            .apply {
                init(
                    KeyGenParameterSpec.Builder(
                        KEY_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                    )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .build()
                )
            }
            .generateKey()
    }

    suspend fun save(profile: ServerEndpointProfile, employeeToken: String) {
        val checked = profile.validated()
        require(employeeToken.startsWith("emp_") && employeeToken.none(Char::isWhitespace)) {
            "Invalid employee token"
        }
        val cipher = Cipher.getInstance(AES_MODE).apply {
            init(Cipher.ENCRYPT_MODE, secretKey())
        }
        val encrypted = cipher.doFinal(employeeToken.toByteArray(Charsets.UTF_8))
        val revision = UUID.randomUUID().toString()
        val encoded = JSONObject()
            .put("version", 1)
            .put("profileId", checked.profileId)
            .put("displayName", checked.displayName)
            .put("environment", checked.environment.name)
            .put("baseUrl", checked.baseUrl)
            .put("employeeId", checked.employeeId)
            .put("projectId", checked.projectId)
            .put("encryptedToken", Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .put("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .put("credentialRevision", revision)
            .toString()
        appContext.serverProfileDataStore.edit { preferences ->
            preferences[stringPreferencesKey("profile_${checked.profileId}")] = encoded
        }
        recentCache.clear()
    }

    suspend fun connection(profileId: String): ServerConnection? {
        val encoded = appContext.serverProfileDataStore.data
            .map { it[stringPreferencesKey("profile_$profileId")] }
            .first()
            ?: return null
        return decode(encoded, includeToken = true) as? ServerConnection
    }

    suspend fun remove(profileId: String) {
        appContext.serverProfileDataStore.edit { preferences ->
            preferences.remove(stringPreferencesKey("profile_$profileId"))
        }
        recentCache.clear()
    }

    val profiles: Flow<List<StoredServerProfile>> = appContext.serverProfileDataStore.data.map {
        preferences ->
        preferences.asMap().entries
            .filter { it.key.name.startsWith("profile_") }
            .mapNotNull { (_, value) -> decode(value as? String ?: return@mapNotNull null, false) }
            .mapNotNull { it as? StoredServerProfile }
            .sortedBy { it.profile.displayName.lowercase() }
    }

    private fun decode(encoded: String, includeToken: Boolean): Any? = runCatching {
        val json = JSONObject(encoded)
        require(json.getInt("version") == 1)
        val profile = ServerEndpointProfile(
            profileId = json.getString("profileId"),
            displayName = json.getString("displayName"),
            environment = ServerEnvironment.valueOf(json.getString("environment")),
            baseUrl = json.getString("baseUrl"),
            employeeId = json.getString("employeeId"),
            projectId = json.getString("projectId")
        ).validated()
        val revision = json.getString("credentialRevision")
        if (!includeToken) return@runCatching StoredServerProfile(profile, revision)
        val encrypted = Base64.decode(json.getString("encryptedToken"), Base64.DEFAULT)
        val iv = Base64.decode(json.getString("iv"), Base64.DEFAULT)
        val cipher = Cipher.getInstance(AES_MODE).apply {
            init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
        }
        val token = String(cipher.doFinal(encrypted), Charsets.UTF_8)
        require(token.startsWith("emp_") && token.none(Char::isWhitespace))
        ServerConnection(profile, token, revision)
    }.getOrNull()

    private companion object {
        const val KEY_ALIAS = "jetson_direct_server_employee_auth"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val AES_MODE = "AES/GCM/NoPadding"
    }
}
