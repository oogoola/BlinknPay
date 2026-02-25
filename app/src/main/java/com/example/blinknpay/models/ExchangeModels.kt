package com.example.blinknpay.models

import com.google.gson.annotations.SerializedName

data class ExchangeResponse(
    val result: String,
    @SerializedName("base_code") val baseCode: String,
    @SerializedName("conversion_rates") val rates: Map<String, Double>
)