package com.example.blinknpay

import android.content.Context
import android.content.SharedPreferences

object UserSession {

    private const val PREFS_NAME = "BlinknPayPrefs"
    private const val KEY_PHONE_NUMBER = "phoneNumber"

    private lateinit var sharedPrefs: SharedPreferences

    // In-memory phone number cache
    var phoneNumber: String = ""
        private set

    // Call this once in Application or MainActivity to initialize SharedPreferences
    fun init(context: Context) {
        sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        // Load saved phone number if any
        phoneNumber = sharedPrefs.getString(KEY_PHONE_NUMBER, "") ?: ""
    }

    // Call this after successful login/OTP verification
    fun login(phone: String) {
        phoneNumber = phone
        sharedPrefs.edit().putString(KEY_PHONE_NUMBER, phone).apply()
    }

    fun isLoggedIn(): Boolean {
        return phoneNumber.isNotEmpty()
    }

    fun logout() {
        phoneNumber = ""
        sharedPrefs.edit().remove(KEY_PHONE_NUMBER).apply()
    }
}
