package com.example.blinknpay.utils

import android.content.Context
import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object SecurePrefs {

    private const val PREFS_NAME = "blinkn_secure_prefs"
    private const val KEY_PIN = "user_pin"

    // AES key: must be 16 bytes for AES-128
    private const val SECRET_KEY = "BlinknPaySecretK" // exactly 16 chars
    private val IV = "RandomInitVector".toByteArray()  // exactly 16 bytes

    // Save PIN securely
    fun saveEncryptedPin(context: Context, pin: String) {
        val encrypted = encrypt(pin)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_PIN, encrypted).apply()
    }

    // Retrieve decrypted PIN
    fun getEncryptedPin(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_PIN, null)?.let { decrypt(it) }
    }

    // AES Encryption
    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val keySpec = SecretKeySpec(SECRET_KEY.toByteArray(Charsets.UTF_8), "AES")
        val ivSpec = IvParameterSpec(IV)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }

    // AES Decryption
    private fun decrypt(encrypted: String): String {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val keySpec = SecretKeySpec(SECRET_KEY.toByteArray(Charsets.UTF_8), "AES")
        val ivSpec = IvParameterSpec(IV)
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
        val decoded = Base64.decode(encrypted, Base64.NO_WRAP)
        return String(cipher.doFinal(decoded), Charsets.UTF_8)
    }
}
