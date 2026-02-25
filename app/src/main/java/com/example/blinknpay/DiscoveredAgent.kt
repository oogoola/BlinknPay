package com.example.blinknpay

data class DiscoveredAgent(
    val name: String = "",
    val network: String = "",
    val distanceMeters: Int = 0,
    val signalStrength: Int = 3
)
