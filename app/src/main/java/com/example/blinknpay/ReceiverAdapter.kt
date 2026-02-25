package com.example.blinknpay

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import java.util.Locale

class ReceiverAdapter(
    context: Context,
    private val receivers: List<Receiver>
) : ArrayAdapter<Receiver>(context, 0, receivers) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val receiver = receivers[position]
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.item_receiver, parent, false)

        val iconText = view.findViewById<TextView>(R.id.receiverIconText)
        val iconImage = view.findViewById<ImageView>(R.id.receiverIconImage)
        val name = view.findViewById<TextView>(R.id.receiverName)
        val category = view.findViewById<TextView>(R.id.receiverCategory)
        val distanceView = view.findViewById<TextView>(R.id.receiverDistance)

        // Set merchant info
        name.text = receiver.businessName
        category.text = receiver.category

        // Show RSSI distance if available
        if (receiver.distance != null) {
            distanceView.visibility = View.VISIBLE
            distanceView.text = "${receiver.distance}m away"
        } else {
            distanceView.visibility = View.GONE
        }

        // Handle logo / first-letter icon
        when {
            receiver.logoResId != null -> {
                iconText.visibility = View.GONE
                iconImage.visibility = View.VISIBLE
                iconImage.setImageResource(receiver.logoResId)
            }
            !receiver.logoUrl.isNullOrEmpty() -> {
                iconText.visibility = View.GONE
                iconImage.visibility = View.VISIBLE
                Glide.with(context)
                    .load(receiver.logoUrl)
                    .placeholder(R.drawable.ic_placeholder_logo)
                    .circleCrop()
                    .into(iconImage)
            }
            else -> {
                iconImage.visibility = View.GONE
                iconText.visibility = View.VISIBLE

                // First letter fallback
                iconText.text = receiver.businessName.firstOrNull()?.toString()?.toUpperCase(Locale.getDefault()) ?: "?"

                // Color by merchant name
                val colorMap = mapOf(
                    "Serena Hotel" to "#FF6F00",      // orange
                    "Naivas Supermarket" to "#03A9F4" // blue
                )

                // Determine background color
                val bgColor = colorMap[receiver.businessName]?.let { android.graphics.Color.parseColor(it) }
                    ?: ContextCompat.getColor(context, R.color.maroon)

                // Set circular background
                val bg = GradientDrawable()
                bg.shape = GradientDrawable.OVAL
                bg.setColor(bgColor)
                iconText.background = bg
            }
        }

        return view
    }
}
