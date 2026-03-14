package com.example.blinknpay

import android.os.Parcelable
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.IgnoreExtraProperties
import kotlinx.parcelize.Parcelize

/**
 * Merchant data model representing a business in the BlinknPay ecosystem.
 * Annotated for seamless Firestore mapping and cross-fragment Parcelable passing.
 */
@IgnoreExtraProperties // Prevents crashes if Firestore has extra fields we haven't defined yet
@Parcelize
data class Merchant(
    // 🛡️ IDENTIFIERS
    val id: String = "",                  // Firestore Document ID (e.g., MFS_001)
    val merchantName: String = "",                // Display Name (e.g., "Mama Faith Shop")
    val upiId: String = "",               // Payment Handle (e.g., mamafaith@bank)
    val proximityId: String = "",         // Bluetooth/SSID Identifier for the Discovery Engine

    // 🏷️ CLASSIFICATION (The link to your Analytics)
    val category: String = "GENERAL",     // "FOOD & DRINK", "TRANSPORT", etc. (Dynamic from DB)

    // 🛰️ ENGINE DATA (Used during active scanning)
    val rssi: Int = 0,                    // Signal strength
    val distance: Double = 0.0,           // Calculated distance in meters

    // 🛡️ STATUS & CONFIGURATION
    // Using explicit PropertyNames ensures Kotlin's "is" prefix doesn't break Firestore mapping
    @get:PropertyName("isActive")
    @set:PropertyName("isActive")
    var isActive: Boolean = false

) : Parcelable {

    /**
     * Helper to check if a merchant is "close enough" to trigger a payment prompt.
     * Based on your BlinknPay proximity threshold of ~2.0 meters.
     */
    fun isInProximity(): Boolean = distance > 0 && distance < 2.5
}