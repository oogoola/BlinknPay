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
        // Set texts
        // ----------------------------
        holder.nameText.text = receiver.businessName ?: "Unknown"
        holder.categoryText.text = receiver.category ?: "Uncategorized"

        holder.locationText.text = when {
            receiver.distance > 1000 -> {
                String.format(Locale.getDefault(), "%.2f km away", receiver.distance / 1000)
            }
            receiver.distance > 0 -> {
                String.format(Locale.getDefault(), "%.0f m away", receiver.distance)
            }
            else -> "Unknown Location"
        }

        // ----------------------------
        // Set icon (logo image or initials)
        // ----------------------------
        when {
            receiver.logoResId != null -> {
                holder.iconText.visibility = View.GONE
                holder.iconImage.visibility = View.VISIBLE
                holder.iconImage.setImageResource(receiver.logoResId)
            }
            !receiver.logoUrl.isNullOrEmpty() -> {
                holder.iconText.visibility = View.GONE
                holder.iconImage.visibility = View.VISIBLE
                Glide.with(context)
                    .load(receiver.logoUrl)
                    .placeholder(R.drawable.blink) // fallback placeholder
                    .error(R.drawable.blink)
                    .circleCrop()
                    .into(holder.iconImage)
            }
            else -> {
                holder.iconImage.visibility = View.GONE
                holder.iconText.visibility = View.VISIBLE

                val firstChar = receiver.businessName?.firstOrNull()?.toString()?.toUpperCase() ?: "?"

                holder.iconText.text = firstChar.toString()

                val bg = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(ContextCompat.getColor(context, R.color.maroon))
                }
                holder.iconText.background = bg
            }
        }

        // ----------------------------
        // Click listener
        // ----------------------------
        holder.itemView.setOnClickListener {
            onMerchantClick(receiver)
        }
    }

    override fun getItemCount(): Int = merchants.size

    // ----------------------------
    // Update merchants safely
    // ----------------------------
    fun updateMerchants(newMerchants: List<Receiver>) {
        merchants.clear()
        merchants.addAll(newMerchants.distinctBy { it.id }) // prevent duplicates if Firestore sends duplicates
        notifyDataSetChanged()
    }
}
