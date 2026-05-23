package com.example.data

import android.content.Context
import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object CryptEngine {
    private const val ALGORITHM = "AES"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val KEY_SIZE = 256
    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_LENGTH = 128
    
    private const val PREFS_NAME = "docdriver_crypto_prefs"
    private const val KEY_PREF = "aes_key"

    @Synchronized
    fun getSecretKey(context: Context): SecretKey {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val keyBase64 = prefs.getString(KEY_PREF, null)
        if (keyBase64 != null) {
            val decoded = Base64.decode(keyBase64, Base64.DEFAULT)
            return SecretKeySpec(decoded, ALGORITHM)
        } else {
            val keyGenerator = KeyGenerator.getInstance(ALGORITHM)
            keyGenerator.init(KEY_SIZE)
            val key = keyGenerator.generateKey()
            val encoded = Base64.encodeToString(key.encoded, Base64.DEFAULT)
            prefs.edit().putString(KEY_PREF, encoded).apply()
            return key
        }
    }

    fun encrypt(context: Context, data: ByteArray): ByteArray {
        val key = getSecretKey(context)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val iv = ByteArray(GCM_IV_LENGTH)
        SecureRandom().nextBytes(iv)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.ENCRYPT_MODE, key, spec)
        val cipherText = cipher.doFinal(data)
        
        // Return IV + CipherText (IV prepended)
        val combined = ByteArray(iv.size + cipherText.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(cipherText, 0, combined, iv.size, cipherText.size)
        return combined
    }

    fun decrypt(context: Context, encryptedData: ByteArray): ByteArray {
        val key = getSecretKey(context)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val iv = ByteArray(GCM_IV_LENGTH)
        System.arraycopy(encryptedData, 0, iv, 0, iv.size)
        
        val cipherTextSize = encryptedData.size - iv.size
        val cipherText = ByteArray(cipherTextSize)
        System.arraycopy(encryptedData, iv.size, cipherText, 0, cipherTextSize)
        
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, spec)
        return cipher.doFinal(cipherText)
    }
}
