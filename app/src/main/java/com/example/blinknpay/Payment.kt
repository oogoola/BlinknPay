package com.example.blinknpay

import java.text.SimpleDateFormat
import java.util.*

data class Payment(
    var id: String = "",                             // Firestore document ID (Transaction Ref)
    var sender: String = "Unknown",                  // Name of the sender/recipient
    var senderProfile: String = "",                  // URL or path of profile image
    var recipientName: String = "",                  // Optional: recipient name
    var amount: Double = 0.0, // Amount paid

    var timestamp: Long = System.currentTimeMillis(), // Timestamp as Long
    var transactionRef: String = "",                 // M-Pesa/Airtel Receipt ID
    var type: String = "PAYMENT"                     // "MPESA", "AIRTEL", or "BLINKNPAY"
) {
    // Helper to format amount nicely (e.g., KSh 1,200.00)
    fun formattedAmount(): String {
        return "KSh %,.2f".format(amount)
    }

    // Helper to format timestamp nicely (e.g., 21 Feb 2026, 07:30 PM)
    fun formattedTime(): String {
        return try {
            val date = Date(timestamp)
            val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
            sdf.format(date)
        } catch (e: Exception) {
            "Unknown date"
        }
    }
}