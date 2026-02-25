package com.blinknpay.viewmodel

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.telephony.TelephonyManager
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.blinknpay.data.BalanceRepository

class PaymentViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = BalanceRepository(application)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _balance = MutableLiveData<Double>(0.0)
    val balance: LiveData<Double> get() = _balance

    private val _isPrivacyActive = MutableLiveData<Boolean>(false)
    val isPrivacyActive: LiveData<Boolean> get() = _isPrivacyActive

    private val _selectedRail = MutableLiveData<String>("MPESA")
    val selectedRail: LiveData<String> get() = _selectedRail

    /**
     * ✅ Real-time SMS Observer
     * Listens for changes in the SMS ContentProvider
     */
    private val smsObserver = object : ContentObserver(mainHandler) {
        override fun onChange(selfChange: Boolean) {
            super.onChange(selfChange)
            Log.d("BlinknPay_Observer", "SMS Database change detected. Syncing...")

            // Wait 2 seconds for the message to fully commit to the database
            mainHandler.postDelayed({
                refreshBalance()
            }, 2000)
        }
    }

    init {
        // Register the observer for the base SMS URI
        try {
            application.contentResolver.registerContentObserver(
                Uri.parse("content://sms/"),
                true,
                smsObserver
            )
        } catch (e: Exception) {
            Log.e("BlinknPay_Error", "Failed to register SMS observer: ${e.message}")
        }

        // Initial fetch
        refreshBalance()
    }

    /**
     * Refreshes the balance by passing the active rail to the repository.
     */
    fun refreshBalance() {
        val currentRail = _selectedRail.value ?: "MPESA"
        val latestBalance = repository.fetchLatestBalance(currentRail)

        // Only update if the value actually changed to avoid UI flicker
        if (_balance.value != latestBalance) {
            _balance.postValue(latestBalance)
        }
    }

    fun togglePrivacy() {
        _isPrivacyActive.value = !(_isPrivacyActive.value ?: false)
    }





    fun setRail(rail: String) {
        // 1. Prevent redundant updates if the rail hasn't actually changed
        if (_selectedRail.value == rail) return

        _selectedRail.value = rail

        // 2. Logic Branching
        when (rail) {
            "MPESA", "AIRTEL" -> {
                // M-Pesa and Airtel are handled by the SMS ContentObserver.
                // Calling refreshBalance() pulls the latest from the SMS inbox.
                refreshBalance()
            }
            "KCB", "EQUITY" -> {
                /* IMPORTANT: For Banks, we reset the balance to 0.0 or a "stale" state.
                   This forces the Fragment to trigger the USSD Fetcher.
                   It also ensures the user doesn't accidentally think their M-Pesa
                   money is sitting in their Equity account.
                */
                _balance.postValue(0.0)

                // Log this so we can track the rail switch in Logcat
                Log.d("BlinknPay_Rail", "Switched to $rail. Awaiting USSD manual sync.")
            }
        }
    }



    /**
     * Manually updates the live balance for non-SMS rails (KCB/Equity/Airtel)
     * This ensures the 'Pay' dialog sees the correct float after a USSD fetch.
     */
    fun updateBalanceManually(newBalance: Double) {
        // 1. Update the LiveData so the UI (tvMainBalance) updates instantly
        _balance.postValue(newBalance)

        // 2. Optional: If you have a local database (Room), save it there
        // to keep the balance visible even after the app restarts.
        // repository.saveLastKnownBalance(selectedRail.value, newBalance)

        Log.d("BlinknPay_VM", "Balance manually updated for ${selectedRail.value}: $newBalance")
    }







    


    /**
     * ✅ Clean up to prevent memory leaks and battery drain
     */
    override fun onCleared() {
        super.onCleared()
        getApplication<Application>().contentResolver.unregisterContentObserver(smsObserver)
        mainHandler.removeCallbacksAndMessages(null)
    }
}