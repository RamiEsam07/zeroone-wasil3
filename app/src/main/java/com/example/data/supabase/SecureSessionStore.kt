package com.example.data.supabase

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts persisted auth tokens with an AES-GCM key protected by Android Keystore.
 * The access/refresh tokens are never written to preferences as plaintext.
 */
class SecureSessionStore(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences("wasil_auth_session", Context.MODE_PRIVATE)

    fun save(session: SupabaseSession) {
        val payload = JSONObject()
            .put("user_id", session.userId)
            .put("email", session.email)
            .put("access_token", session.accessToken)
            .put("refresh_token", session.refreshToken)
            .put("expires_at", session.expiresAtEpochSeconds)
            .toString()
            .toByteArray(Charsets.UTF_8)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(payload)
        val combined = ByteArrayOutputStream().apply {
            write(cipher.iv)
            write(encrypted)
        }.toByteArray()

        check(preferences.edit().putString(KEY_SESSION, Base64.encodeToString(combined, Base64.NO_WRAP)).commit()) {
            "Could not persist the encrypted session"
        }
    }

    fun load(): SupabaseSession? {
        val encoded = preferences.getString(KEY_SESSION, null) ?: return null
        return try {
            val combined = Base64.decode(encoded, Base64.NO_WRAP)
            require(combined.size > IV_LENGTH) { "Invalid encrypted session" }
            val iv = combined.copyOfRange(0, IV_LENGTH)
            val encrypted = combined.copyOfRange(IV_LENGTH, combined.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            val json = JSONObject(String(cipher.doFinal(encrypted), Charsets.UTF_8))
            SupabaseSession(
                userId = json.getString("user_id"),
                email = json.optString("email").takeIf { it.isNotBlank() && it != "null" },
                accessToken = json.getString("access_token"),
                refreshToken = json.getString("refresh_token"),
                expiresAtEpochSeconds = json.getLong("expires_at"),
            )
        } catch (_: Exception) {
            clear()
            null
        }
    }

    fun clear() {
        preferences.edit().remove(KEY_SESSION).commit()
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "wasil_supabase_session_aes"
        const val KEY_SESSION = "encrypted_session"
        const val IV_LENGTH = 12
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
