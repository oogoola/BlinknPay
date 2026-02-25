package com.example.blinknpay.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.blinknpay.engine.BlinkEngine
import com.example.blinknpay.models.SignatoryRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class PayoutViewModel : ViewModel() {

    // The current payout request details
    private val _payoutAmount = MutableStateFlow(0.0)
    val payoutAmount: StateFlow<Double> = _payoutAmount

    // Track which signatories have authorized
    private val _authorizedRoles = MutableStateFlow<Set<SignatoryRole>>(emptySet())
    val authorizedRoles: StateFlow<Set<SignatoryRole>> = _authorizedRoles

    // High-level UI state
    private val _isVaultUnlocked = MutableStateFlow(false)
    val isVaultUnlocked: StateFlow<Boolean> = _isVaultUnlocked

    private val SECURITY_THRESHOLD = 50000.0

    /**
     * Triggered when a payout is initiated via the UPI / Bank interface.
     */
    fun initiatePayoutRequest(amount: Double) {
        _payoutAmount.value = amount
        _authorizedRoles.value = emptySet()
        _isVaultUnlocked.value = false
    }

    /**
     * Logic to handle signatures from the BlinkEngine.
     * @param rpid The rotating ID detected by the Quad-Channel hardware.
     */
    fun processIncomingRpid(rpid: String) {
        val role = SignatoryRole.fromRpid(rpid) ?: return

        val currentSet = _authorizedRoles.value.toMutableSet()
        currentSet.add(role)
        _authorizedRoles.value = currentSet

        checkQuorumRequirements()
    }

    private fun checkQuorumRequirements() {
        val amount = _payoutAmount.value
        val signersCount = _authorizedRoles.value.size

        if (amount >= SECURITY_THRESHOLD) {
            // HIGH VALUE: 3/3 Physical Signatories required
            if (signersCount >= 3) {
                unlockVault()
            }
        } else {
            // LOW VALUE: 2/3 Signatories required (Remote or Physical)
            if (signersCount >= 2) {
                unlockVault()
            }
        }
    }

    private fun unlockVault() {
        _isVaultUnlocked.value = true
        // Logic to push the final authorization to the Bank API / UPI
    }
}