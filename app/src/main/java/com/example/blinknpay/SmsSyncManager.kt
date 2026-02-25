package com.example.blinknpay

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import java.util.regex.Pattern

class SmsSyncManager(private val context: Context) {

    private val db = FirebaseFirestore.getInstance()

    fun fetchLocalTransactions(): List<Payment> {
        val transactionList = mutableListOf<Payment>()
        val seenIds = mutableSetOf<String>()
        val uri = Uri.parse("content://sms/inbox")

        // Querying for common East African Telco headers
        val cursor = context.contentResolver.query(
            uri,
            arrayOf("body", "date", "address"),
            "address LIKE ? OR address LIKE ? OR address LIKE ?",
            arrayOf("%MPESA%", "%AIRTEL%", "%SAFARICOM%"),
            "date DESC"
        )

        cursor?.use {
            val bodyIdx = it.getColumnIndex("body")
            val dateIdx = it.getColumnIndex("date")

            while (it.moveToNext()) {
                val body = it.getString(bodyIdx) ?: ""
                val date = it.getLong(dateIdx)

                // --- 1. M-PESA REGEX (Standard 10-char, Global, and Sandbox) ---
                val mpesaPattern = Pattern.compile("([A-Z0-9]{8,12}).*?(?:Ksh|Ksh\\s)([\\d,]+\\.\\d{2})")

                // --- 2. AIRTEL REGEX ---
                val airtelPattern = Pattern.compile("(?:ID:|Txn ID:)\\s?([A-Z0-9]+).*?(?:Amt:|Ksh)\\s?([\\d,]+\\.\\d{2})")

                val mpesaMatcher = mpesaPattern.matcher(body)
                val airtelMatcher = airtelPattern.matcher(body)

                when {
                    mpesaMatcher.find() -> {
                        val txnId = mpesaMatcher.group(1) ?: ""
                        if (!seenIds.contains(txnId)) {
                            val amt = mpesaMatcher.group(2)?.replace(",", "")?.toDoubleOrNull() ?: 0.0
                            transactionList.add(createPayment(txnId, body, amt, date, "MPESA"))
                            seenIds.add(txnId)
                        }
                    }
                    airtelMatcher.find() -> {
                        val txnId = airtelMatcher.group(1) ?: ""
                        if (!seenIds.contains(txnId)) {
                            val amt = airtelMatcher.group(2)?.replace(",", "")?.toDoubleOrNull() ?: 0.0
                            transactionList.add(createPayment(txnId, body, amt, date, "AIRTEL"))
                            seenIds.add(txnId)
                        }
                    }
                }
            }
        }

        Log.d("BlinknPay_SmsSync", "Total records parsed: ${transactionList.size}")
        return transactionList
    }

    /**
     * Uploads the latest transactions to Firestore to keep UI in sync across devices.
     */
    fun syncTransactionsToCloud(payments: List<Payment>) {
        if (payments.isEmpty()) return

        val batch = db.batch()
        // Sync only the most recent 15 to stay within batch limits and keep it fast
        payments.take(15).forEach { payment ->
            val docRef = db.collection("transactions").document(payment.id)
            batch.set(docRef, payment)
        }

        batch.commit()
            .addOnSuccessListener { Log.d("BlinknPay_SmsSync", "✅ Successfully synced to Firestore") }
            .addOnFailureListener { e -> Log.e("BlinknPay_SmsSync", "❌ Cloud sync failed: ${e.message}") }
    }

    private fun createPayment(id: String, body: String, amt: Double, date: Long, type: String): Payment {
        val isSandbox = body.contains("Test_Paybill", ignoreCase = true) || body.contains("Sandbox", ignoreCase = true)
        val recipient = extractRecipient(body)

        // Set the primary "Sender" label for the UI list
        val displayLabel = when {
            body.contains("paid to", ignoreCase = true) -> if (isSandbox) "Sandbox: $recipient" else recipient
            body.contains("received", ignoreCase = true) -> "Funds from $recipient"
            else -> "$type: $recipient"
        }

        return Payment(
            id = id,
            sender = displayLabel,
            amount = amt,
            timestamp = date,
            transactionRef = id,
            type = type,
            recipientName = recipient
        )
    }

    private fun extractRecipient(body: String): String {
        // Improved pattern to capture business names or people
        val pattern = Pattern.compile("(?:to|from)\\s+([A-Z\\s]{3,25})(?:on|at|\\.|\\d{2}/|\\s\\d{10})")
        val matcher = pattern.matcher(body)
        return if (matcher.find()) {
            matcher.group(1)?.trim() ?: "BlinknPay User"
        } else {
            "Internal Transaction"
        }
    }
}