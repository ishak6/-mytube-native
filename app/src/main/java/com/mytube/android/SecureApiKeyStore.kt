package com.mytube.android

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts the YouTube Data API key with an AES-GCM key held by Android Keystore.
 * The key itself is never written to ordinary app preferences.
 */
object SecureApiKeyStore {
    private const val PREFS = "mytube_secure"
    private const val VALUE = "youtube_api_key"
    private const val LEGACY_PREFS = "mytube_youtube"
    private const val LEGACY_VALUE = "api_key"
    private const val KEY_ALIAS = "mytube_youtube_api_aes"

    fun load(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val stored = prefs.getString(VALUE, null)
        if (!stored.isNullOrBlank()) {
            return try {
                val parts = stored.split(":", limit = 2)
                require(parts.size == 2) { "Invalid encrypted API key" }
                val iv = Base64.decode(parts[0], Base64.NO_WRAP)
                val ciphertext = Base64.decode(parts[1], Base64.NO_WRAP)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
                String(cipher.doFinal(ciphertext), Charsets.UTF_8)
            } catch (_: Exception) {
                ""
            }
        }

        // Migrate the previous plaintext preference once, then remove it.
        val legacyPrefs = context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
        val legacy = legacyPrefs.getString(LEGACY_VALUE, "").orEmpty()
        if (legacy.isNotBlank()) {
            save(context, legacy)
            legacyPrefs.edit().remove(LEGACY_VALUE).apply()
        }
        return legacy
    }

    fun save(context: Context, apiKey: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (apiKey.isBlank()) {
            prefs.edit().remove(VALUE).apply()
            return
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val ciphertext = cipher.doFinal(apiKey.toByteArray(Charsets.UTF_8))
        val encoded = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(ciphertext, Base64.NO_WRAP)
        prefs.edit().putString(VALUE, encoded).apply()
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }
}
