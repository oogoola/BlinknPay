package com.example.blinknpay.models

import com.google.gson.annotations.SerializedName

/**
 * Data model for the ExchangeRate-API v6 response.
 * Optimized for BlinknPay's real-time KES conversion.
 */
data class ExchangeRateResponse(
    @SerializedName("result")
    val result: String,

    @SerializedName("documentation")
    val documentation: String,

    @SerializedName("terms_of_use")
    val termsOfUse: String,

    @SerializedName("time_last_update_unix")
    val timeLastUpdateUnix: Long,

    @SerializedName("time_last_update_utc")
    val timeLastUpdateUtc: String,

    @SerializedName("time_next_update_unix")
    val timeNextUpdateUnix: Long,

    @SerializedName("time_next_update_utc")
    val timeNextUpdateUtc: String,

    @SerializedName("base_code")
    val baseCode: String,

    // This matches the JSON key and provides the Map your logic expects
    @SerializedName("conversion_rates")
    val conversion_rates: Map<String, Double>
)