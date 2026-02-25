package com.example.blinknpay

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.NumberFormat
import java.util.Locale
import com.example.blinknpay.R
import com.example.blinknpay.Merchant



class ProductAdapter(
    private val products: List<Product>,
    private val onProductClick: ((Product) -> Unit)? = null
) : RecyclerView.Adapter<ProductAdapter.ProductViewHolder>() {

    // Formatter for Kenyan Shillings
    private val currencyFormatter: NumberFormat =
        NumberFormat.getCurrencyInstance(Locale("en", "KE"))

    inner class ProductViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val image: ImageView = itemView.findViewById(R.id.imgProduct)
        val name: TextView = itemView.findViewById(R.id.tvProductName)
        val price: TextView = itemView.findViewById(R.id.tvProductPrice)

        init {
            itemView.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onProductClick?.invoke(products[position])
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProductViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_product, parent, false)
        return ProductViewHolder(view)
    }

    override fun onBindViewHolder(holder: ProductViewHolder, position: Int) {
        val product = products[position]

        holder.name.text = product.name
        holder.price.text = currencyFormatter.format(product.price)
        holder.image.setImageResource(product.imageRes)
    }

    override fun getItemCount(): Int = products.size
}
