package com.example.blinknpay

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.TextView

/**
 * 🛡️ BLINKNPAY MOTHER CODE: UNIVERSAL ACCESSIBILITY BRIDGE
 * This service automates the M-Pesa STK Push interaction after biometric validation.
 */
class BlinknPayOverlayService : AccessibilityService() {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // 1. SECURITY GATE: Only proceed if a payment is flagged as pending in Globals
        if (!BlinknPayGlobals.isPaymentPending) return

        val rootNode = rootInActiveWindow ?: return

        // 2. DETECTION: Listen for STK Window or "Enter PIN" text
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {

            val packageName = event.packageName?.toString() ?: ""

            // Universal Detection for Samsung A-series and generic Android STK apps
            val isMpesaWindow = packageName.contains("stk", ignoreCase = true) ||
                    packageName.contains("mpesa", ignoreCase = true) ||
                    rootNode.findAccessibilityNodeInfosByText("Enter PIN").isNotEmpty()

            if (isMpesaWindow) {
                showBlinknPayUI()

                // 3. AUTO-FILL: Check the Global Biometric state
                if (BlinknPayGlobals.isBiometricAuthenticated) {
                    // Slight delay to allow the Samsung UI to focus on the input field
                    Handler(Looper.getMainLooper()).postDelayed({
                        autoFillMpesaPin(rootNode, "1234") // Fetch secure PIN in Prod
                    }, 400)
                }
            }
        }

        // 4. SUCCESS MONITORING: Dismiss overlay when confirmation appears
        val successKeywords = arrayOf("Sent", "M-PESA", "received", "confirmed", "Balance", "Success")
        for (keyword in successKeywords) {
            if (rootNode.findAccessibilityNodeInfosByText(keyword).isNotEmpty()) {
                showSuccessState()
                BlinknPayGlobals.resetSecureBridge() // Reset global states
                break
            }
        }
    }

    private fun showBlinknPayUI() {
        if (overlayView != null) return

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        overlayView = inflater.inflate(R.layout.blinknpay_auth_screen, null)

        // UI Setup: Dynamic Merchant Name from Globals
        overlayView?.findViewById<TextView>(R.id.statusText)?.text = "Paying ${BlinknPayGlobals.activeMerchantName}"

        // Start Pulsating Animation
        overlayView?.findViewById<View>(R.id.ring1)?.startAnimation(
            AnimationUtils.loadAnimation(this, R.anim.pulse_fade)
        )

        windowManager?.addView(overlayView, params)

        // Launch Fingerprint Bridge to capture the side-scanner touch
        val intent = Intent(this, BiometricBridgeActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    private fun autoFillMpesaPin(rootNode: AccessibilityNodeInfo, pin: String) {
        val inputNodes = rootNode.findAccessibilityNodeInfosByViewId("com.android.stk:id/edit_text")
        if (inputNodes.isNotEmpty()) {
            val pinField = inputNodes[0]
            val arguments = Bundle()
            arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, pin)
            pinField.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)

            // Universal Button Click (Supports Samsung and Stock IDs)
            val okIds = arrayOf("android:id/button1", "com.android.stk:id/button1", "com.android.stk:id/ok_button")
            for (id in okIds) {
                val okButtons = rootNode.findAccessibilityNodeInfosByViewId(id)
                if (okButtons.isNotEmpty()) {
                    okButtons[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    return
                }
            }
            // Text-based fallback
            rootNode.findAccessibilityNodeInfosByText("OK").firstOrNull()?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
    }

    private fun showSuccessState() {
        overlayView?.let { view ->
            val checkmark = view.findViewById<ImageView>(R.id.successCheckmark)
            if (checkmark.visibility == View.VISIBLE) return

            Handler(Looper.getMainLooper()).post {
                view.findViewById<View>(R.id.ring1)?.apply { clearAnimation(); visibility = View.GONE }
                view.findViewById<View>(R.id.blinknpayLogo)?.visibility = View.GONE
                view.findViewById<View>(R.id.blink_auth_fingerprint)?.visibility = View.GONE

                checkmark.visibility = View.VISIBLE
                val statusText = view.findViewById<TextView>(R.id.statusText)
                statusText.text = "Payment Complete!"
                statusText.setTextColor(Color.parseColor("#00E676"))

                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                Handler(Looper.getMainLooper()).postDelayed({ hideBlinknPayUI() }, 2500)
            }
        }
    }

    private fun hideBlinknPayUI() {
        try {
            overlayView?.let {
                windowManager?.removeView(it)
                overlayView = null
                Log.d("BlinknPay_UI", "Bridge Closed")
            }
        } catch (e: Exception) {
            Log.e("BlinknPay_UI", "Removal failed: ${e.message}")
        }
    }

    override fun onInterrupt() { hideBlinknPayUI() }
}