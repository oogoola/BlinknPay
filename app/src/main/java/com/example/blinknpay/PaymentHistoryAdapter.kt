package com.example.blinknpay

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.blinknpay.databinding.ItemPaymentHistoryBinding
import java.util.*

class PaymentHistoryAdapter(
    private val payments: MutableList<Payment>,
    private val onItemClick: (Payment) -> Unit
) : RecyclerView.Adapter<PaymentHistoryAdapter.PaymentViewHolder>() {

    inner class PaymentViewHolder(val binding: ItemPaymentHistoryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(payment: Payment) {
            val ctx = binding.root.context

            // 1. DIRECTION LOGIC: No more guessing or unresolved references
            val isSent = payment.direction == "SENT"

            // 2. TEXT DISPLAY: Using new model field names
            binding.txtReceiver.text = payment.externalPartyName
            binding.txtAmount.text = payment.formattedAmount()
            binding.txtTimestamp.text = payment.formattedTime()

            // Set the amount color based on the model's helper
            binding.txtAmount.setTextColor(Color.parseColor(payment.getDirectionColor()))

            // 3. STATUS STYLING (Red for Outgoing, Green for Incoming)
            if (isSent) {
                binding.txtType.text = "Sent"
                binding.txtType.setBackgroundResource(R.drawable.bg_status_tag)
                binding.txtType.background?.setTint(ContextCompat.getColor(ctx, R.color.red))
                binding.imgStatus.setImageResource(R.drawable.ic_arrow_upward)
                binding.imgStatus.setColorFilter(ContextCompat.getColor(ctx, R.color.red))
            } else {
                binding.txtType.text = "Received"
                binding.txtType.setBackgroundResource(R.drawable.bg_status_tag)
                binding.txtType.background?.setTint(ContextCompat.getColor(ctx, R.color.green))
                binding.imgStatus.setImageResource(R.drawable.ic_arrow_downward)
                binding.imgStatus.setColorFilter(ContextCompat.getColor(ctx, R.color.green))
            }

            // 4. RAIL LOGIC: Show M-Pesa or Airtel logos
            val placeholderIcon = when (payment.rail.toUpperCase()) {
                "MPESA" -> R.drawable.ic_mpesa
                "AIRTEL" -> R.drawable.airtel
                "KCB" -> R.drawable.ic_kcb // If you have these assets
                else -> R.drawable.ic_profile_placeholder
            }

            // Note: senderProfile was removed from the new model for simplicity in SMS parsing
            // We use the rail-specific icon as the primary visual
            Glide.with(ctx)
                .load(placeholderIcon) // Directly load the brand icon
                .circleCrop()
                .into(binding.imgProfile)

            binding.root.setOnClickListener { onItemClick(payment) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PaymentViewHolder {
        val binding = ItemPaymentHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PaymentViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PaymentViewHolder, position: Int) {
        holder.bind(payments[position])
    }

    override fun getItemCount(): Int = payments.size

    fun replaceAll(newList: List<Payment>) {
        this.payments.clear()
        this.payments.addAll(newList)
        notifyDataSetChanged()
    }
}