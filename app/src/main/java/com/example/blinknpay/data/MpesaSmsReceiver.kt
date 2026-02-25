package com.blinknpay.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony

class MpesaSmsReceiver(private val onMessageReceived: () -> Unit) : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            for (sms in messages) {
                if (sms.originatingAddress?.contains("MPESA", ignoreCase = true) == true) {
                    // Trigger the callback to refresh the UI via the ViewModel
                    onMessageReceived()
                }
            }
        }
    }
}