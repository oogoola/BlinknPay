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
    private val smsSyncManager = SmsSyncManager(fragment.requireContext())

    /**
     * SYNC: Uploads a list of SMS-based transactions to the cloud.
     */
    fun syncSmsToCloud(smsPayments: List<Payment>? = null, onComplete: (Boolean) -> Unit) {
        val uid = auth.currentUser?.uid ?: run {
            Log.e("BlinknPay_Firebase", "SYNC_ABORT: User not logged in.")
            onComplete(false)
            return
        }

        val transactionsToSync = smsPayments ?: smsSyncManager.fetchLocalTransactions()

        if (transactionsToSync.isEmpty()) {
            Log.d("BlinknPay_Firebase", "SYNC_SKIP: No local transactions found.")
            onComplete(true)
            return
        }

        // Optimization for device stability
        val syncLimit = 100
        val safePayments = if (transactionsToSync.size > syncLimit) transactionsToSync.take(syncLimit) else transactionsToSync

        val batch = db.batch()
        val userPaymentsRef = db.collection("users").document(uid).collection("payments")

        for (payment in safePayments) {
            // Ensure the senderId/receiverId is set correctly before syncing
            if (payment.direction == "SENT") {
                payment.senderId = uid
            } else {
                payment.receiverId = uid
            }

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
     * Updated to match the new Payment model fields.
     */
    fun savePaymentToFirestore(
        merchantName: String,
        amount: Double,
        transactionRef: String,
        rail: String = "BLINKNPAY", // Replaces 'type'
        direction: String = "SENT", // Default for STK/Merchant payments
        category: String = "GENERAL",
        onComplete: (() -> Unit)? = null
    ) {
        val uid = auth.currentUser?.uid ?: run {
            Log.e("BlinknPay_Firebase", "SAVE_ERROR: User UID is null")
            onComplete?.invoke()
            return
        }

        val finalId = transactionRef.ifEmpty { db.collection("users").document().id }
        val docRef = db.collection("users").document(uid).collection("payments").document(finalId)

        // Using the updated Payment model constructor
        val payment = Payment(
            id = finalId,
            transactionRef = transactionRef,
            amount = amount,
            direction = direction,
            category = category,
            externalPartyName = merchantName,
            senderId = if (direction == "SENT") uid else "",
            receiverId = if (direction == "RECEIVED") uid else "",
            rail = rail,
            timestamp = System.currentTimeMillis()
        )

        docRef.set(payment, SetOptions.merge())
            .addOnSuccessListener {
                Log.d("BlinknPay_Firebase", "SAVE_SUCCESS: Transaction $finalId recorded.")
                onComplete?.invoke()
            }
            .addOnFailureListener { e ->
                Log.e("BlinknPay_Firebase", "SAVE_ERROR: ${e.message}")
                if (fragment.isAdded && fragment.context != null) {
                    Toast.makeText(fragment.requireContext(), "Save failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
                onComplete?.invoke()
            }
    }
}