package com.blinknpay.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log

class MpesaSmsReceiver(
    private val onTransactionDetected: (amount: Double, type: String) -> Unit
) : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            for (sms in messages) {
                val body = sms.displayMessageBody ?: ""
                val address = sms.originatingAddress ?: ""

                // 1. Filter for M-PESA or Airtel Money
                if (address.contains("MPESA", ignoreCase = true) ||
                    address.contains("AIRTEL", ignoreCase = true)) {

                    Log.d("BlinknPay_Receiver", "M-Pesa SMS Detected: $body")
                    parseAndNotify(body)
                }
            }
        }
    }

    private fun parseAndNotify(body: String) {
        val upperBody = body.toUpperCase()

        // 2. Identify Direction (Fixes the "Confused" classification)
        val type = when {
            upperBody.contains("SENT TO") ||
                    upperBody.contains("PAID TO") ||
                    upperBody.contains("BOUGHT") ||
                    upperBody.contains("PAY BILL") -> "SENT"

            upperBody.contains("RECEIVED") ||
                    upperBody.contains("DEPOSIT") -> "RECEIVED"

            else -> "UNKNOWN"
        }

        // 3. Extract Amount (Regex for KES 1,234.56)
        val amountPattern = java.util.regex.Pattern.compile("KES\\s*([\\d,]+\\.\\d{2})")
        val matcher = amountPattern.matcher(body)

        val amount = if (matcher.find()) {
            matcher.group(1)?.replace(",", "")?.toDouble() ?: 0.0
        } else 0.0

        // 4. Trigger callback with parsed data
        if (amount > 0.0) {
            onTransactionDetected(amount, type)
        }
    }
}