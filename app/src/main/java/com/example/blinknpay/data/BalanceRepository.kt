package com.blinknpay.data

import android.content.Context
import android.net.Uri
import android.util.Log
import java.util.Locale
class BalanceRepository(private val context: Context) {

    /**
     * ✅ Updated with 'rail' parameter to filter SMS by provider
     */
    fun fetchLatestBalance(rail: String): Double {
        var balance = 0.0

        // 1. Determine which SMS senders to look for based on the Rail


        // Import this at the top of your file


// Inside your function:
        val selectionArgs = when (rail.toUpperCase(Locale.ROOT)) {
            "MPESA" -> arrayOf("MPESA", "M-PESA", "Safaricom")
            "AIRTEL" -> arrayOf("AirtelMoney", "AIRTEL", "Airtel")
            "EQUITY" -> arrayOf("EQUITY", "EquityBank")
            "KCB" -> arrayOf("KCB", "KCBBANK")
            else -> arrayOf(rail) // Fallback to the rail name itself
        }




        // 2. Build the query selection string dynamically (?, ?, ?)
        val placeholders = selectionArgs.joinToString(" OR ") { "address = ?" }
        val uri = Uri.parse("content://sms/")
        val projection = arrayOf("body", "address", "date")
        val sortOrder = "date DESC LIMIT 20"

        try {
            context.contentResolver.query(uri, projection, placeholders, selectionArgs, sortOrder)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val body = cursor.getString(cursor.getColumnIndexOrThrow("body"))

                    // 3. Robust Regex for Kenyan Mobile Money & Bank SMS
                    // Matches "Balance is KES 1,200.50", "Ksh 500.00", etc.
                    val pattern = Regex("(?i)balance\\s*(?:is|was)?\\s*(?:KES|Ksh|KSH)?\\s*([\\d,]+\\.\\d{2})")
                    val match = pattern.find(body)

                    if (match != null) {
                        // 4. Clean formatting: remove commas to prevent Double conversion crashes
                        val balanceString = match.groupValues[1].replace(",", "")
                        balance = balanceString.toDouble()

                        Log.d("BlinknPay_Balance", "Success! Found $rail Balance: $balance")
                        return balance // Return immediately once the newest balance is found
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("BlinknPay_Error", "SMS Query failed for $rail: ${e.message}")
        }

        Log.d("BlinknPay_Balance", "No balance found for $rail")
        return balance
    }
}