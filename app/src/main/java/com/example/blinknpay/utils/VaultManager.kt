package com.example.blinknpay.utils

import java.text.DecimalFormat

object VaultManager {
    // Mock exchange rates (In production, source from your Live API)
    private const val KES_TO_USD = 131.50

    fun formatCurrency(amount: Double, code: String): String {
        val formatter = DecimalFormat("#,###.00")
        return "$code ${formatter.format(amount)}"
    }

    fun getInflationHedge(kesAmount: Double): String {
        val usdValue = kesAmount / KES_TO_USD
        val formatter = DecimalFormat("#,###.00")
        return "$ ${formatter.format(usdValue)} (Protected Value)"
    }
}