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

                val mpesaPattern = Pattern.compile("([A-Z0-9]{8,12}).*?(?:Ksh|Ksh\\s)([\\d,]+\\.\\d{2})")
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

    private fun createPayment(id: String, body: String, amt: Double, date: Long, rail: String): Payment {
        val upperBody = body.uppercase()
        val recipient = extractRecipient(body)

        // Determine Direction
        val direction = if (upperBody.contains("RECEIVED") || upperBody.contains("DEPOSIT")) {
            "RECEIVED"
        } else {
            "SENT"
        }

        // Determine Category
        val category = when {
            upperBody.contains("PAY BILL") || upperBody.contains("PAID TO") -> "PAYBILL"
            upperBody.contains("AIRTIME") -> "AIRTIME"
            upperBody.contains("POCHI") -> "POCHI"
            else -> "P2P"
        }

        // 🛡️ FIX: Use new Payment constructor parameters
        return Payment(
            id = id,
            transactionRef = id,
            amount = amt,
            direction = direction,
            category = category,
            externalPartyName = recipient,
            rail = rail,
            timestamp = date
        )
    }

    private fun extractRecipient(body: String): String {
        // Matches names after "to", "from", or "paid to" until "on" or date patterns
        val pattern = Pattern.compile("(?:to|from|paid to)\\s+([A-Z0-9\\s]{3,30})(?:on|at|\\.|\\d{2}/)", Pattern.CASE_INSENSITIVE)
        val matcher = pattern.matcher(body)
        return if (matcher.find()) {
            matcher.group(1)?.trim()?.replace(Regex("\\s+"), " ") ?: "Unknown Party"
        } else {
            "M-Pesa Transaction"
        }
    }
}