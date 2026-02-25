package com.example.blinknpay.ui.merchant

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.blinknpay.R
import com.example.blinknpay.Product
import com.example.blinknpay.ProductAdapter
import com.google.android.material.bottomsheet.BottomSheetDialog

class MerchantCatalogFragment : Fragment(R.layout.fragment_merchant_catalog) {

    // -------------------------
    // Cart items
    // -------------------------
    private val cartItems = mutableListOf<Product>()

    // -------------------------
    // Views
    // -------------------------
    private lateinit var catalogRecycler: RecyclerView
    private lateinit var emptyText: TextView
    private lateinit var catalogTitle: TextView

    private val products = mutableListOf<Product>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // -------------------------
        // Bind views
        // -------------------------
        catalogRecycler = view.findViewById(R.id.catalogRecycler)
        emptyText = view.findViewById(R.id.emptyCatalogText)
        catalogTitle = view.findViewById(R.id.catalogTitle)

        // -------------------------
        // Safe Args: merchant info
        // -------------------------
        val args = MerchantCatalogFragmentArgs.fromBundle(requireArguments())
        catalogTitle.text = args.merchantName

        // -------------------------
        // Load products (dummy)
        // -------------------------
        loadDummyProducts()

        // -------------------------
        // Setup RecyclerView
        // -------------------------
        setupRecycler()

        // -------------------------
        // Toggle empty view
        // -------------------------
        toggleEmptyView()
    }

    private fun setupRecycler() {
        val gridLayoutManager = GridLayoutManager(requireContext(), 4)
        // Top 4 items = grid, rest = full width
        gridLayoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                return if (position < 4) 1 else 4
            }
        }

        catalogRecycler.layoutManager = gridLayoutManager
        catalogRecycler.adapter = ProductAdapter(products) { product ->
            addToCart(product)
        }
    }

    private fun loadDummyProducts() {
        products.clear()
        products.add(Product("1", "Rice 2kg", 450, R.drawable.ic_product))
        products.add(Product("2", "Cooking Oil 1L", 380, R.drawable.ic_product))
        products.add(Product("3", "Sugar 1kg", 220, R.drawable.ic_product))
        products.add(Product("4", "Tea 250g", 150, R.drawable.ic_product))
        products.add(Product("5", "Salt 1kg", 100, R.drawable.ic_product))
        products.add(Product("6", "Flour 2kg", 350, R.drawable.ic_product))
    }


    private fun toggleEmptyView() {
        val isEmpty = products.isEmpty()
        emptyText.visibility = if (isEmpty) View.VISIBLE else View.GONE
        catalogRecycler.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    // -------------------------
    // Cart logic
    // -------------------------
    private fun addToCart(product: Product) {
        // Check if already in cart
        val existing = cartItems.find { it.name == product.name }
        if (existing != null) {
            existing.quantity += 1
        } else {
            cartItems.add(product.copy(quantity = 1))
        }
        showCartBottomSheet()
    }

    private fun showCartBottomSheet() {
        val sheet = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.bottomsheet_cart, null)

        val recycler = view.findViewById<RecyclerView>(R.id.cartRecycler)
        val totalText = view.findViewById<TextView>(R.id.tvTotal)

        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = CartAdapter(cartItems)

        val total = cartItems.sumOf { it.price * it.quantity }
        totalText.text = "Total: KSh $total"

        sheet.setContentView(view)
        sheet.show()
    }
}
