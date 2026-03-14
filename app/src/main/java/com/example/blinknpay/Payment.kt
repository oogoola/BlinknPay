package com.example.blinknpay

import java.text.SimpleDateFormat
import java.util.*

data class Payment(
    var id: String = "",                             // Firestore document ID

    // 🛡️ UNIQUE IDENTIFIER (Crucial for Syncing)
    // We'll keep transactionRef but use receiptId as the standard field for your sync logic
    var receiptId: String = "",                      // M-Pesa Receipt ID (e.g., RCL812J9KL)
    var transactionRef: String = "",                 // Keep for legacy/UI display if needed

    // 🛡️ CASHFLOW LOGIC
    var amount: Double = 0.0,
    var direction: String = "SENT",                  // "SENT" or "RECEIVED"
    var category: String = "GENERAL",                // "POCHI", "PAYBILL", "AIRTIME", "P2P", "WITHDRAWAL"

    // 👤 ENTITY DETAILS
    var senderId: String = "",                       // The BlinknPay UID of the person sending
    var receiverId: String = "",                     // The BlinknPay UID of the person receiving
    var externalPartyName: String = "Unknown",       // The M-Pesa Name (e.g., "JOHN DOE" or "Safaricom")
    var externalPartyNumber: String = "",            // The Phone Number or Till/Paybill Number

    // 🕒 METADATA
    var timestamp: Long = System.currentTimeMillis(),
    var rail: String = "MPESA",                      // "MPESA", "AIRTEL", "KCB", "EQUITY"
    var status: String = "COMPLETED",                // "COMPLETED", "PENDING", "FAILED"
    var note: String = "",                           // Optional user-added memo
    var source: String = "APP"                       // "APP", "SMS_SYNC", or "QR"
) {

    /**
     * Helper to format amount nicely (e.g., KSh 1,200.00).
     * Automatically adds a minus or plus sign based on direction.
     */
    fun formattedAmount(): String {
        val sign = if (direction.equals("SENT", true)) "-" else "+"
        return "$sign KSh %,.2f".format(amount)
    }

    /**
     * Extension to help the animateBalance function in AnalyticsFragment
     */
    fun Double.toCurrency(): String {
        return "KSh %,.2f".format(this)
    }

    /**
     * Returns a color resource/hex based on direction.
     * SENT = #C2185B (Pink/Red) | RECEIVED = #6CAF10 (Blink Green)
     */
    fun getDirectionColor(): String {
        return if (direction.equals("SENT", true)) "#C2185B" else "#6CAF10"
    }

    /**
     * Formats timestamp (e.g., 08 Mar 2026, 10:30 PM).
     */
    fun formattedTime(): String {
        return try {
            val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
            sdf.format(Date(timestamp))
        } catch (e: Exception) {
            "Unknown date"
        }
    }
}