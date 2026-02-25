package com.example.blinknpay

import java.util.Locale
import java.util.UUID
import com.example.blinknpay.Receiver



data class Receiver(
    val businessName: String = "",
    val id: String = UUID.randomUUID().toString(),  // now has a default unique value
    val category: String = "",
    val uuid: String = "",
    val code: String = "",
    val logoResId: Int? = null,
    val logoUrl: String? = null,
    var rssi: Int = 0,
    var distance: Double = 0.0
) {

    val location: String
        get() = if (distance > 0) String.format(Locale.getDefault(), "%.2fm away", distance) else "Unknown Location"

    fun getUuidObject(): UUID? {
        return try {
            UUID.fromString(uuid)
        } catch (e: IllegalArgumentException) {
            null
        }
    }
}
