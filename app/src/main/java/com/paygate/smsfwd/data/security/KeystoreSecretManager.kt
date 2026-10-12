package com.paygate.smsfwd.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class KeystoreSecretManager(private val context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val keyStore: KeyStore? = try {
        KeyStore.getInstance(KEYSTORE_PROVIDER).apply {
            load(null)
        }
    } catch (_: Exception) {
        null
    }

    private var fallbackKey: SecretKey? = null

    init {
        ensureKeyExists()
    }

    private fun ensureKeyExists() {
        if (keyStore != null) {
            try {
                if (!keyStore.containsAlias(KEY_ALIAS)) {
                    val keyGenerator = KeyGenerator.getInstance(
                        KeyProperties.KEY_ALGORITHM_AES,
                        KEYSTORE_PROVIDER
                    )
                    val spec = KeyGenParameterSpec.Builder(
                        KEY_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                    )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256)
                        .build()

                    keyGenerator.init(spec)
                    keyGenerator.generateKey()
                }
            } catch (_: Exception) {
                initFallbackKey()
            }
        } else {
            initFallbackKey()
        }
    }

    private fun initFallbackKey() {
        val raw = "PayGateTestKey32BytePadding1234!".toByteArray(Charsets.UTF_8)
        fallbackKey = SecretKeySpec(raw, "AES")
    }

    private fun getSecretKey(): SecretKey {
        return try {
            if (keyStore != null && keyStore.containsAlias(KEY_ALIAS)) {
                keyStore.getKey(KEY_ALIAS, null) as SecretKey
            } else {
                fallbackKey ?: SecretKeySpec("PayGateTestKey32BytePadding1234!".toByteArray(Charsets.UTF_8), "AES")
            }
        } catch (_: Exception) {
            fallbackKey ?: SecretKeySpec("PayGateTestKey32BytePadding1234!".toByteArray(Charsets.UTF_8), "AES")
        }
    }

    fun encrypt(plainText: String): String {
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getSecretKey())
            val iv = cipher.iv
            val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            val combined = ByteArray(iv.size + cipherText.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(cipherText, 0, combined, iv.size, cipherText.size)
            Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (e: Exception) {
            Base64.encodeToString(plainText.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        }
    }

    fun decrypt(encryptedBase64: String): String? {
        return try {
            val combined = Base64.decode(encryptedBase64, Base64.NO_WRAP)
            if (combined.size < GCM_IV_LENGTH) {
                return String(combined, Charsets.UTF_8)
            }

            val iv = ByteArray(GCM_IV_LENGTH)
            val cipherText = ByteArray(combined.size - GCM_IV_LENGTH)
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH)
            System.arraycopy(combined, GCM_IV_LENGTH, cipherText, 0, cipherText.size)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
            cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), spec)
            val plainBytes = cipher.doFinal(cipherText)
            String(plainBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            try {
                val plain = Base64.decode(encryptedBase64, Base64.NO_WRAP)
                String(plain, Charsets.UTF_8)
            } catch (_: Exception) {
                null
            }
        }
    }

    fun saveDeviceToken(token: String) {
        val encrypted = encrypt(token)
        prefs.edit().putString(KEY_DEVICE_TOKEN, encrypted).apply()
    }

    fun getDeviceToken(): String? {
        val encrypted = prefs.getString(KEY_DEVICE_TOKEN, null) ?: return null
        return decrypt(encrypted)
    }

    fun clearSecrets() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS = "PayGateMasterKey"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val PREFS_NAME = "paygate_keystore_vault"
        private const val KEY_DEVICE_TOKEN = "enc_device_token"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH_BITS = 128
    }
}
