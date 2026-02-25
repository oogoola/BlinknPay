package com.example.blinknpay

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
    private var transactions: List<Payment>, // Changed to Payment
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
            // Scale animation on touch for that premium feel
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

        // Load sender profile image
        Glide.with(holder.itemView.context)
            .load(pmt.senderProfile)
            .placeholder(R.drawable.ic_profile_placeholder)
            .into(holder.senderProfileImage)

        // Updated to match your Payment.kt fields
        holder.receivedFrom.text = pmt.sender
        holder.receivedAmount.text = pmt.formattedAmount()
        holder.receivedTime.text = pmt.formattedTime()
        holder.transactionId.text = "Ref: ${pmt.transactionRef}"
    }

    override fun getItemCount(): Int = transactions.size

    // Crucial for the SwipeRefreshLayout update
    fun updateData(newList: List<Payment>) {
        this.transactions = newList
        notifyDataSetChanged()
    }
}