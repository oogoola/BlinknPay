package com.example.blinknpay

import android.graphics.Color
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import de.hdodenhof.circleimageview.CircleImageView
import com.google.android.material.card.MaterialCardView

class TransactionAdapter(
    private var transactions: List<Payment>,
    private val onItemClick: (Payment) -> Unit
) : RecyclerView.Adapter<TransactionAdapter.TransactionViewHolder>() {

    inner class TransactionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val senderProfileImage: CircleImageView = itemView.findViewById(R.id.senderProfileImage)
        val receivedFrom: TextView = itemView.findViewById(R.id.receivedFrom)
        val receivedAmount: TextView = itemView.findViewById(R.id.receivedAmount)
        val receivedTime: TextView = itemView.findViewById(R.id.receivedTime)
        val transactionId: TextView = itemView.findViewById(R.id.transactionId)
        val cardTransaction: MaterialCardView = itemView.findViewById(R.id.cardTransaction)

        init {
            cardTransaction.setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        v.animate().scaleX(0.97f).scaleY(0.97f).setDuration(100).start()
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        v.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
                    }
                }
                false
            }

            itemView.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(transactions[position])
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransactionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.transaction_item, parent, false)
        return TransactionViewHolder(view)
    }

    override fun onBindViewHolder(holder: TransactionViewHolder, position: Int) {
        val pmt = transactions[position]
        val context = holder.itemView.context

        // 1. BRAND LOGO LOGIC: Show M-Pesa/Airtel/KCB icons instead of empty profiles
        val brandIcon = when (pmt.rail.toUpperCase()) {
            "MPESA" -> R.drawable.ic_mpesa
            "AIRTEL" -> R.drawable.airtel
            "KCB" -> R.drawable.ic_kcb
            else -> R.drawable.ic_profile_placeholder
        }

        Glide.with(context)
            .load(brandIcon)
            .placeholder(R.drawable.ic_profile_placeholder)
            .into(holder.senderProfileImage)

        // 2. UPDATED FIELDS: Fixed 'Unresolved reference' errors
        holder.receivedFrom.text = pmt.externalPartyName // Replaces .sender
        holder.receivedAmount.text = pmt.formattedAmount()
        holder.receivedTime.text = pmt.formattedTime()
        holder.transactionId.text = "Ref: ${pmt.transactionRef}"

        // 3. DIRECTIONAL STYLING: Green for Income, Pink/Red for Expense
        val statusColor = Color.parseColor(pmt.getDirectionColor())
        holder.receivedAmount.setTextColor(statusColor)
    }

    override fun getItemCount(): Int = transactions.size

    fun updateData(newList: List<Payment>) {
        this.transactions = newList
        notifyDataSetChanged()
    }
}