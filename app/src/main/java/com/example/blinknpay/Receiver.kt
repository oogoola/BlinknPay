package com.example.blinknpay

data class Receiver(
    val businessName: String = "",    // main display name
    val category: String = "",
    val uuid: String = "",
    val code: String = "",
    val logoResId: Int? = null,
    val logoUrl: String? = null,
    var rssi: Int = 0,
    var distance: Int? = null
) {
    fun getUuidObject(): java.util.UUID? {
        return try {
            java.util.UUID.fromString(uuid)
        } catch (e: IllegalArgumentException) {
            null
        }
    }
}
