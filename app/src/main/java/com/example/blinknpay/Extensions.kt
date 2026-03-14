package com.example.blinknpay

import android.view.HapticFeedbackConstants
import android.view.View
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import java.text.DecimalFormat

/**
 * Charting Extensions
 */

// Allows lambda usage: barData.setValueFormatter { value -> "KSh ${value.toInt()}" }
fun BarData.setValueFormatter(formatLogic: (value: Float) -> String) {
    this.setValueFormatter(object : ValueFormatter() {
        override fun getFormattedValue(value: Float): String = formatLogic(value)
    })
}

/**
 * FIXED: Since BarDataSet doesn't support 'Indicators' (lines),
 * we control the highlight visibility via Alpha.
 * 0 = Hidden, 255 = Fully Opaque.
 */
fun BarDataSet.setHighlightEnabled(enabled: Boolean) {
    this.highLightAlpha = if (enabled) 120 else 0
}

/**
 * Currency & Number Extensions
 * Specifically localized for KSh (Kenyan Shilling).
 */

// Formats Double? safely to "KSh 1,200.00"
fun Double?.toCurrency(): String {
    val value = this ?: 0.0
    val formatter = DecimalFormat("#,##0.00")
    return "KSh ${formatter.format(value)}"
}

fun Float?.toCurrency(): String = this?.toDouble().toCurrency()

// Converts formatted UI strings like "KSh 5,000.00" back to Double for math/animations
fun String.parseCurrency(): Double {
    return try {
        this.replace("KSh", "").replace(",", "").trim().toDouble()
    } catch (e: Exception) {
        0.0
    }
}

/**
 * UI & Tactile Extensions
 */

fun View.hide() { this.visibility = View.GONE }

fun View.show() { this.visibility = View.VISIBLE }

/**
 * Triggers a premium tactile buzz (vibration).
 * Usage: myButton.hapticFeedback()
 */
fun View.hapticFeedback() {
    this.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
}

/**
 * Proximity Engine Utilities
 * Essential for RPID (Rotating Proximity ID) propagation.
 */

fun String.hexToByteArray(): ByteArray {
    val cleanedHex = this.replace("\\s".toRegex(), "")
    if (cleanedHex.length % 2 != 0) return byteArrayOf()

    return ByteArray(cleanedHex.length / 2) { i ->
        val index = i * 2
        ((Character.digit(cleanedHex[index], 16) shl 4) +
                Character.digit(cleanedHex[index + 1], 16)).toByte()
    }
}

fun BarDataSet.setDrawHighlightIndicators(enabled: Boolean) {
    this.highLightAlpha = if (enabled) 120 else 0
}