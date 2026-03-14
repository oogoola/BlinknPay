package com.example.blinknpay

import android.app.Application
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import java.util.regex.Pattern
import java.util.Locale

class PaymentViewModel(application: Application) : AndroidViewModel(application) {

    private val mainHandler = Handler(Looper.getMainLooper())

    // --- LIVEDATA ---
    private val _balance = MutableLiveData<Double>(0.0)
    val balance: LiveData<Double> get() = _balance

    private val _isPrivacyActive = MutableLiveData<Boolean>(true)
    val isPrivacyActive: LiveData<Boolean> get() = _isPrivacyActive

    private val _selectedRail = MutableLiveData<String>("MPESA")
    val selectedRail: LiveData<String> get() = _selectedRail

    // NEW: Category for the Analytics Card
    private val _currentCategory = MutableLiveData<String>("GENERAL")
    val currentCategory: LiveData<String> get() = _currentCategory

    private val _lastTransactionType = MutableLiveData<String>("UNKNOWN")
    val lastTransactionType: LiveData<String> get() = _lastTransactionType

    // --- SMS OBSERVER ---
    private val smsObserver = object : ContentObserver(mainHandler) {
        override fun onChange(selfChange: Boolean) {
            super.onChange(selfChange)
            Log.d("BlinknPay_Observer", "SMS Change detected. Refreshing...")
            // Wait 2 seconds for M-Pesa/Airtel to finish writing the SMS to the database
            mainHandler.postDelayed({ refreshBalance() }, 2000)
        }
    }

    init {
        registerObserver()
        refreshBalance()
    }

    private fun registerObserver() {
        try {
            getApplication<Application>().contentResolver.registerContentObserver(
                Uri.parse("content://sms/"),
                true,
                smsObserver
            )
        } catch (e: Exception) {
            Log.e("BlinknPay_Error", "Observer Registration Failed: ${e.message}")
        }
    }

    fun refreshBalance() {
        val currentRail = _selectedRail.value ?: "MPESA"
        fetchLatestTransactionData(currentRail)
    }

    private fun fetchLatestTransactionData(rail: String) {
        val sender = if (rail == "MPESA") "MPESA" else "AIRTELMONEY"
        val context = getApplication<Application>()

        val cursor = context.contentResolver.query(
            Uri.parse("content://sms/inbox"),
            arrayOf("body"),
            "address = ?",
            arrayOf(sender),
            "date DESC LIMIT 1"
        )

        cursor?.use {
            if (it.moveToFirst()) {
                val body = it.getString(0)

                // 1. Direction & Category Classification
                val direction = classifyDirection(body)
                val category = inferCategory(body)

                _lastTransactionType.postValue(direction)
                _currentCategory.postValue(category)

                // 2. Parse Balance
                parseBalanceFromBody(body)
            }
        }
    }

    private fun parseBalanceFromBody(body: String) {
        // Updated Pattern for 2026 M-Pesa formats (handles New Balance and Balance is...)
        val balancePattern = Pattern.compile("(?:New M-PESA balance is|balance is|New Balance) KES\\s*([\\d,]+\\.\\d{2})", Pattern.CASE_INSENSITIVE)
        val matcher = balancePattern.matcher(body)

        if (matcher.find()) {
            val amountStr = matcher.group(1)?.replace(",", "")
            _balance.postValue(amountStr?.toDouble() ?: 0.0)
        }
    }

    /**
     * 🧠 CATEGORY ENGINE: Inferred categorization for unregistered merchants
     */
    private fun inferCategory(body: String): String {
        val b = body.toUpperCase(Locale.getDefault())
        return when {
            // Food & Merchant logic
            b.contains("KFC") || b.contains("QUICKMART") || b.contains("NAIVAS") || b.contains("GLOVO") -> "FOOD & DRINK"

            // Transport logic
            b.contains("UBER") || b.contains("BOLT") || b.contains("SUPER METRO") || b.contains("EASYCOACH") -> "TRANSPORT"

            // Utility logic
            b.contains("KPLC") || b.contains("TOKEN") || b.contains("NAIROBI WATER") -> "UTILITIES"

            // Airtime
            b.contains("AIRTIME") -> "AIRTIME"

            else -> "GENERAL"
        }
    }

    private fun classifyDirection(body: String): String {
        val b = body.toUpperCase(Locale.getDefault())
        return when {
            b.contains("SENT TO") || b.contains("PAID TO") || b.contains("BOUGHT") ||
                    b.contains("PAY BILL") || b.contains("WITHDRAWN") -> "SENT"
            b.contains("RECEIVED") || b.contains("DEPOSIT") -> "RECEIVED"
            else -> "UNKNOWN"
        }
    }

    // --- UI ACTIONS ---
    fun togglePrivacy() {
        _isPrivacyActive.value = !(_isPrivacyActive.value ?: true)
    }

    fun setRail(rail: String) {
        if (_selectedRail.value == rail) return
        _selectedRail.value = rail
        refreshBalance()
    }

    fun setCategory(category: String) {
        _currentCategory.value = category
    }

    override fun onCleared() {
        super.onCleared()
        getApplication<Application>().contentResolver.unregisterContentObserver(smsObserver)
        mainHandler.removeCallbacksAndMessages(null)
    }
}