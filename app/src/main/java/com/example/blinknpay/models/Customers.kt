package com.example.blinknpay.models

/**
 * Data class representing a customer in BlinknPay.
 * Fully Firestore-compatible and ready for storage/retrieval.
 */
data class Customer(
    val uid: String = "",                     // Firebase UID (unique user ID)
    val userNumber: Long = 0L,               // Custom incremental unique number
    val name: String = "",                    // Full name
    val phone: String = "",                   // Phone number
    val pin: String = "",                     // 4-digit app-level PIN
    val email: String? = null,               // Optional email
    val createdAt: Long = System.currentTimeMillis(), // Registration timestamp
    val lastLoginAt: Long? = null,           // Optional: last login timestamp
    val isActive: Boolean = true             // Optional: account status
)
