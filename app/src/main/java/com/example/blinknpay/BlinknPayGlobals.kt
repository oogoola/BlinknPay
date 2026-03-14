package com.example.blinknpay

/**
 * 🛡️ UNIVERSAL GLOBAL STATE
 * This object acts as the 'Secure Bridge' between your Quad-Channel Engine
 * (Bluetooth/Ultrasound) and the M-Pesa Accessibility Overlay.
 */
object BlinknPayGlobals {

    // 1. Armed State: Set to TRUE when Daraja API returns a successful STK Push
    @JvmStatic
    var isPaymentPending: Boolean = false

    // 2. Auth State: Set to TRUE when the Samsung A15 side-scanner validates the finger
    @JvmStatic
    var isBiometricAuthenticated: Boolean = false

    // 3. Metadata: Store the current merchant name for the pulsating UI
    @JvmStatic
    var activeMerchantName: String = "BlinknPay Merchant"

    /**
     * Resets the bridge after a successful transaction or a timeout.
     * Prevents accidental triggers on subsequent M-Pesa messages.
     */
    fun resetSecureBridge() {
        isPaymentPending = false
        isBiometricAuthenticated = false
        activeMerchantName = "BlinknPay Merchant"
    }
}