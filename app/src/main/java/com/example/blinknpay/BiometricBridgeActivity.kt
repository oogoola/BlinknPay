package com.example.blinknpay

import android.os.Bundle
import android.os.CancellationSignal
import android.util.Log
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

class BiometricBridgeActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Make the window invisible immediately
        window.setBackgroundDrawableResource(android.R.color.transparent)

        setupBiometricPrompt()
    }

    private fun setupBiometricPrompt() {
        val executor = ContextCompat.getMainExecutor(this)

        val biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)

                    // 1. Signal the Service that the fingerprint was successful
                    // We use a static flag in the Service to trigger the mapping
                    // Inside onAuthenticationSucceeded
                    BlinknPayGlobals.isBiometricAuthenticated = true

                    Log.d("BlinknPay_Bridge", "Fingerprint Success - Mapping to PIN")
                    finish() // Close the bridge activity
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    Log.e("BlinknPay_Bridge", "Auth Error: $errString")

                    if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                        // User clicked "Use Password" - handle your pulsating password UI here
                    }
                    finish()
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    Log.d("BlinknPay_Bridge", "Fingerprint not recognized")
                }
            })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("BlinknPay Secure")
            .setSubtitle("Confirming transaction")
            .setNegativeButtonText("Use Password")
            .setAllowedAuthenticators(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()

        // Trigger the Samsung A15 hardware scanner
        biometricPrompt.authenticate(promptInfo)
    }
}
