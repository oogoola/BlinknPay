package com.example.blinknpay

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.blinknpay.databinding.ItemPaymentHistoryBinding
import com.google.firebase.auth.FirebaseAuth
import java.util.*

class PaymentHistoryAdapter(
    private val payments: MutableList<Payment>,
    private val onItemClick: (Payment) -> Unit
) : RecyclerView.Adapter<PaymentHistoryAdapter.PaymentViewHolder>() {

    inner class PaymentViewHolder(val binding: ItemPaymentHistoryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(payment: Payment) {
            val ctx = binding.root.context

            // 1. IMPROVED LOGIC: Determine if Sent or Received
            // Checks if sender is the current User ID OR if the SMS manager labeled it "Sent Payment"
            val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
            val isSent = payment.sender == currentUserId ||
                    payment.sender?.contains("Sent", ignoreCase = true) == true ||
                    payment.sender?.contains("To:", ignoreCase = true) == true

            // 2. UI Display: Use the helper functions from your Payment model
            binding.txtReceiver.text = payment.sender ?: "Unknown Transaction"
            binding.txtAmount.text = payment.formattedAmount()
            binding.txtTimestamp.text = payment.formattedTime()

            // 3. Status Styling (Red for Outgoing, Green for Incoming)
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

            // 4. M-Pesa / Airtel Logo Logic
            // If it's a telco transaction, we can show a specific icon instead of a profile placeholder
            val profileImage = when (payment.type) {
                "MPESA" -> R.drawable.ic_mpesa // Ensure you have these in res/drawable
                "AIRTEL" -> R.drawable.airtel
                else -> R.drawable.ic_profile_placeholder
            }

            Glide.with(ctx)
                .load(payment.senderProfile.ifEmpty { null })
                .placeholder(profileImage)
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