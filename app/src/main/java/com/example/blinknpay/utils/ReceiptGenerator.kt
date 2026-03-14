package com.example.blinknpay.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.LayoutInflater
import android.view.View
import com.example.blinknpay.Payment
import com.example.blinknpay.databinding.LayoutReceiptShareBinding

object ReceiptGenerator {

    /**
     * Renders a hidden XML layout into a Bitmap for sharing.
     */
    fun generateReceiptBitmap(context: Context, payment: Payment): Bitmap {
        val binding = LayoutReceiptShareBinding.inflate(LayoutInflater.from(context))

        // Populate the Receipt View
        binding.receiptRef.text = payment.transactionRef
        binding.receiptAmount.text = payment.formattedAmount()
        binding.receiptParty.text = payment.externalPartyName
        binding.receiptDate.text = payment.formattedTime()
        binding.receiptStatus.text = "TRANSACTION COMPLETED"

        // Custom branding message
        binding.receiptBranding.text = "Generated via BlinknPay Unified Engine"

        // Measure and Layout the view in memory
        val spec = View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY)
        binding.root.measure(spec, View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        binding.root.layout(0, 0, binding.root.measuredWidth, binding.root.measuredHeight)

        // Draw to Bitmap
        val bitmap = Bitmap.createBitmap(
            binding.root.measuredWidth,
            binding.root.measuredHeight,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        binding.root.draw(canvas)

        return bitmap
    }
}