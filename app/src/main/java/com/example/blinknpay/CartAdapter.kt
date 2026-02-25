package com.example.blinknpay.ui.merchant

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.blinknpay.R
import com.example.blinknpay.Product

class CartAdapter(
    private val items: MutableList<Product>
) : RecyclerView.Adapter<CartAdapter.CartViewHolder>() {

    inner class CartViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.tvProductName)
        val price: TextView = view.findViewById(R.id.tvProductPrice)
        val qty: TextView = view.findViewById(R.id.tvQuantity)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CartViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_cart_product, parent, false)
        return CartViewHolder(view)
    }

    override fun onBindViewHolder(holder: CartViewHolder, position: Int) {
        val item = items[position]

        holder.name.text = item.name
        holder.qty.text = "x${item.quantity}"
        holder.price.text = "KSh ${item.price * item.quantity}"

        // Optional: click to increment quantity
        holder.itemView.setOnClickListener {
            item.quantity++
            notifyItemChanged(position)
        }
    }

    override fun getItemCount(): Int = items.size

    // Helper function to calculate total cart value
    fun getTotalAmount(): Int {
        return items.sumOf { it.price * it.quantity }
    }

    // Optional: function to remove item from cart
    fun removeItem(position: Int) {
        if (position in items.indices) {
            items.removeAt(position)
            notifyItemRemoved(position)
        }
    }
}
