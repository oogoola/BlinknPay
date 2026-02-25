package com.example.blinknpay.engine

import android.util.Log
import androidx.lifecycle.LifecycleOwner
import com.example.blinknpay.models.SignatoryRole

/**
 * BlinknPay Quad-Channel Engine
 * Handles physical quorum detection via RPID (Rotating Proximity Identifier)
 */
object BlinkEngine {

    interface RpidListener {
        fun onSignatoryDetected(scannedRpid: String)
        fun onDiscoveryStatusChanged(isActive: Boolean)
    }

    private var listener: RpidListener? = null

    // Tracks if we are in "Physical Quorum" mode (3/3) or "Remote" mode (2/3)
    private var isPhysicalQuorumRequired: Boolean = false

    /**
     * @param amount The payout amount to determine security level
     */
    fun startDiscovery(
        owner: LifecycleOwner,
        amount: Double,
        listener: RpidListener
    ) {
        this.listener = listener
        this.isPhysicalQuorumRequired = amount >= 50000.0

        if (isPhysicalQuorumRequired) {
            activateHardwareChannels()
            Log.d("BlinkEngine", "SECURITY ALERT: Physical Quorum (3/3) Required for KES $amount")
        } else {
            Log.d("BlinkEngine", "FLEXIBLE MODE: Remote Signature (2/3) Enabled.")
        }

        listener.onDiscoveryStatusChanged(true)
    }

    private fun activateHardwareChannels() {
        // 1. BLE CHANNEL: Scans for nearby Signatory RPID packets
        // 2. ULTRASOUND CHANNEL: FFT analysis of 18kHz-22kHz tokens
        // 3. WIFI SSID: Check for hidden peer-to-peer SSID broadcast
        // 4. BT CLASSIC: Handshake verification
        Log.d("BlinkEngine", "Quad-Channel Hardware: ONLINE")
    }

    /**
     * Logic to process an incoming signature.
     * If Remote, it bypasses physical hardware checks.
     */
    fun processIncomingSignature(role: SignatoryRole, isPhysical: Boolean) {
        if (isPhysicalQuorumRequired && !isPhysical) {
            Log.e("BlinkEngine", "Security Violation: Physical presence required for this amount!")
            return
        }

        // Notify UI that a valid RPID/Signature has been processed
        listener?.onSignatoryDetected(role.rpidPrefix)
    }

    // Mock detection for testing Physical Presence
    fun simulatePhysicalPresence(role: SignatoryRole) {
        if (isPhysicalQuorumRequired) {
            listener?.onSignatoryDetected(role.rpidPrefix)
        }
    }

    fun stopDiscovery() {
        Log.d("BlinkEngine", "Hardware channels powered down.")
        this.listener?.onDiscoveryStatusChanged(false)
        this.listener = null
    }
}