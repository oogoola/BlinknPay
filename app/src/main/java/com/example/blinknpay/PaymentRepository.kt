package com.example.blinknpay

import android.util.Log
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

class PaymentRepository(private val fragment: Fragment) {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    // Needed to fetch data if the list isn't passed directly
    private val smsSyncManager = SmsSyncManager(fragment.requireContext())

    /**
     * SYNC: Uploads a list of SMS-based transactions to the cloud.
     * Logic: If smsPayments is null, it fetches them locally first.
     */
    fun syncSmsToCloud(smsPayments: List<Payment>? = null, onComplete: (Boolean) -> Unit) {
        val uid = auth.currentUser?.uid ?: run {
            Log.e("BlinknPay_Firebase", "SYNC_ABORT: User not logged in.")
            onComplete(false)
            return
        }

        // Use provided list OR fetch fresh ones if null
        val transactionsToSync = smsPayments ?: smsSyncManager.fetchLocalTransactions()

        if (transactionsToSync.isEmpty()) {
            Log.d("BlinknPay_Firebase", "SYNC_SKIP: No local transactions found.")
            onComplete(true)
            return
        }

        // --- SAFEGUARD: Samsung A15 / Low-end device optimization ---
        // Batch limit is 500; we use 100 for memory stability during the quad-channel scan
        val syncLimit = 100
        val safePayments = if (transactionsToSync.size > syncLimit) transactionsToSync.take(syncLimit) else transactionsToSync

        Log.d("BlinknPay_Firebase", "SYNC_START: Processing batch for ${safePayments.size} items.")

        val batch = db.batch()
        val userPaymentsRef = db.collection("users").document(uid).collection("payments")

        for (payment in safePayments) {
            // Use M-Pesa/Airtel receipt code as doc ID to prevent duplicates
            val docRef = userPaymentsRef.document(payment.id)
            batch.set(docRef, payment, SetOptions.merge())
        }

        batch.commit()
            .addOnSuccessListener {
                Log.d("BlinknPay_Firebase", "SYNC_SUCCESS: Synced ${safePayments.size} items.")
                onComplete(true)
            }
            .addOnFailureListener { e ->
                Log.e("BlinknPay_Firebase", "SYNC_ERROR: ${e.message}")
                onComplete(false)
            }
    }

    /**
     * SINGLE SAVE: Used for STK Push results or RPID proximity trades.
     */
    fun savePaymentToFirestore(
        merchantName: String,
        merchantLogo: String?,
        amount: Double,
        transactionRef: String,
        type: String = "BLINKNPAY",
        onComplete: (() -> Unit)? = null
    ) {
        val uid = auth.currentUser?.uid ?: run {
            Log.e("BlinknPay_Firebase", "SAVE_ERROR: User UID is null")
            onComplete?.invoke()
            return
        }

        val finalId = transactionRef.ifEmpty { db.collection("users").document().id }
        val docRef = db.collection("users").document(uid).collection("payments").document(finalId)

        val payment = Payment(
            id = finalId,
            sender = merchantName,
            senderProfile = merchantLogo ?: "",
            amount = amount,
            timestamp = System.currentTimeMillis(),
            transactionRef = transactionRef,
            type = type
        )

        docRef.set(payment, SetOptions.merge())
            .addOnSuccessListener {
                Log.d("BlinknPay_Firebase", "SAVE_SUCCESS: Transaction $finalId recorded.")
                onComplete?.invoke()
            }
            .addOnFailureListener { e ->
                Log.e("BlinknPay_Firebase", "SAVE_ERROR: ${e.message}")
                // Safeguard against fragment detachment
                if (fragment.isAdded && fragment.context != null) {
                    Toast.makeText(fragment.requireContext(), "Save failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
                onComplete?.invoke()
            }
    }
}