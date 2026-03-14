package com.example.blinknpay

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import java.util.Locale

class MerchantAdapter(
    private val merchants: MutableList<Receiver>,
    private val onMerchantClick: (Receiver) -> Unit
) : RecyclerView.Adapter<MerchantAdapter.MerchantViewHolder>() {

    class MerchantViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val iconText: TextView = view.findViewById(R.id.receiverIconText)
        val iconImage: ImageView = view.findViewById(R.id.receiverIconImage)
        val nameText: TextView = view.findViewById(R.id.txtMerchantName)
        val categoryText: TextView = view.findViewById(R.id.txtMerchantCategory)
        val locationText: TextView = view.findViewById(R.id.txtMerchantLocation)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MerchantViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_merchant, parent, false)
        return MerchantViewHolder(view)
    }

    override fun onBindViewHolder(holder: MerchantViewHolder, position: Int) {
        val receiver = merchants[position]
        val context = holder.itemView.context

        // ----------------------------
        // 1. Set Texts & Hierarchy
        // ----------------------------
        holder.nameText.text = receiver.businessName.ifEmpty { "Unknown Merchant" }

        // Ensure category is uppercase for clean UI, default to General
        val displayCategory = receiver.category.ifEmpty { "General" }.toUpperCase(Locale.getDefault())
        holder.categoryText.text = displayCategory

        // Location formatting (Smart rounding)
        holder.locationText.text = when {
            receiver.distance > 1000 -> String.format(Locale.getDefault(), "%.1f km away", receiver.distance / 1000)
            receiver.distance > 0 -> String.format(Locale.getDefault(), "%.0f m away", receiver.distance)
            else -> "Nearby"
        }

        // ----------------------------
        // 2. Dynamic Icon Logic (Logo > Emoji > Initials)
        // ----------------------------
        when {
            // Priority 1: Resource ID (Local testing/System merchants)
            receiver.logoResId != null -> {
                showImage(holder)
                holder.iconImage.setImageResource(receiver.logoResId)
            }

            // Priority 2: Remote URL (Registered Merchants with branding)
            !receiver.logoUrl.isNullOrEmpty() -> {
                showImage(holder)
                Glide.with(context)
                    .load(receiver.logoUrl)
                    .placeholder(R.drawable.blink)
                    .circleCrop()
                    .into(holder.iconImage)
            }

            // Priority 3: Smart Category Emoji (Dynamic Lookup)
            else -> {
                showTextIcon(holder)

                // Map the Firestore category string to a visual emoji
                val emoji = when (receiver.category.toUpperCase(Locale.getDefault())) {
                    "FOOD & DRINK", "RESTAURANT" -> "🍔"
                    "TRANSPORT", "TAXI" -> "🚗"
                    "AIRTIME", "SAFARICOM" -> "📱"
                    "UTILITIES", "KPLC" -> "💡"
                    "SHOPPING", "RETAIL" -> "🛍️"
                    "HEALTH", "PHARMACY" -> "🏥"
                    else -> receiver.businessName.take(1).toUpperCase() // Fallback to Initial
                }

                holder.iconText.text = emoji

                // Use a soft background for the emoji icon
                val bg = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(ContextCompat.getColor(context, R.color.light_gray_bg)) // Use a neutral color
                }
                holder.iconText.background = bg
            }
        }

        // ----------------------------
        // 3. Highlight Nearest (UX Polish)
        // ----------------------------
        // If merchant is within "Blink Range" (< 3m), give the card a subtle glow or border
        if (receiver.distance in 0.1..3.0) {
            holder.itemView.alpha = 1.0f
            // Optional: holder.nameText.setTextColor(ContextCompat.getColor(context, R.color.blink_green))
        } else {
            holder.itemView.alpha = 0.85f
        }

        holder.itemView.setOnClickListener { onMerchantClick(receiver) }
    }

    private fun showImage(holder: MerchantViewHolder) {
        holder.iconText.visibility = View.GONE
        holder.iconImage.visibility = View.VISIBLE
    }

    private fun showTextIcon(holder: MerchantViewHolder) {
        holder.iconImage.visibility = View.GONE
        holder.iconText.visibility = View.VISIBLE
    }

    override fun getItemCount(): Int = merchants.size

    fun updateMerchants(newMerchants: List<Receiver>) {
        merchants.clear()
        // DistinctBy ensures that even if scanning finds multiple signals for 1 merchant,
        // they only appear once in the list.
        merchants.addAll(newMerchants.distinctBy { it.id })
        notifyDataSetChanged()
    }
}