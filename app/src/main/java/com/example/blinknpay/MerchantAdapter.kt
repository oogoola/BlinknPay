package com.example.blinkpay

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.blinknpay.R

import com.example.blinknpay.Receiver


class MerchantAdapter(
    private val merchants: List<Receiver>,
    private val onMerchantClick: (Receiver) -> Unit
) : RecyclerView.Adapter<MerchantAdapter.MerchantViewHolder>() {

    class MerchantViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nameText: TextView = view.findViewById(R.id.txtMerchantName)
        val categoryText: TextView = view.findViewById(R.id.txtMerchantCategory)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MerchantViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_merchant, parent, false)
        return MerchantViewHolder(view)
    }

    override fun onBindViewHolder(holder: MerchantViewHolder, position: Int) {
        val merchant = merchants[position]
        holder.nameText.text = merchant.businessName
        holder.categoryText.text = merchant.category

        holder.itemView.setOnClickListener { onMerchantClick(merchant) }
    }

    override fun getItemCount() = merchants.size
}
