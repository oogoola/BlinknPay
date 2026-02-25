package com.example.blinknpay

import com.google.firebase.Timestamp

data class User(
    val uid: String = "",
    val userNumber: Long = 0,
    val fullName: String = "",
    val phone: String = "",
    val email: String? = null,
    val pinHash: String = "",
    val profileImageUrl: String = "",
    val createdAt: Timestamp = Timestamp.now(),      // Firestore Timestamp
    val lastLoginAt: Timestamp? = Timestamp.now(),   // Firestore Timestamp (nullable)
    val isActive: Boolean = true
)
