package com.example.blinknpay

data class Merchant(
    val id: String,
    val name: String,
    val amount: Int = 0,
    val rssi: Int = 0
)
