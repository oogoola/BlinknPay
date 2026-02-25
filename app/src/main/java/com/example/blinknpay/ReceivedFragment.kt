package com.example.blinknpay

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import de.hdodenhof.circleimageview.CircleImageView

class ReceivedFragment : Fragment() {

    private lateinit var userProfileImage: CircleImageView
    private lateinit var totalReceivedAmount: TextView
    private lateinit var receivedRecyclerView: RecyclerView
    private lateinit var emptyStateText: TextView

    // Use Payment model consistently
    private var transactions = mutableListOf<Payment>()
    private lateinit var adapter: TransactionAdapter

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var transactionListener: ListenerRegistration? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.received, container, false)

        userProfileImage = view.findViewById(R.id.userProfileImage)
        totalReceivedAmount = view.findViewById(R.id.totalReceivedAmount)
        receivedRecyclerView = view.findViewById(R.id.receivedRecyclerView)
        emptyStateText = view.findViewById(R.id.emptyStateText)

        // Setup RecyclerView using the updated TransactionAdapter (which takes Payment)
        adapter = TransactionAdapter(transactions) { payment ->
            // Use the navigation logic that matches your app structure
            val fragment = TransactionDetailsFragment().apply {
                arguments = Bundle().apply {
                    putString("transaction_id", payment.id)
                }
            }
            parentFragmentManager.beginTransaction()
                .replace(R.id.nav_host_fragment, fragment)
                .addToBackStack(null)
                .commit()
        }

        receivedRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        receivedRecyclerView.adapter = adapter

        loadUserProfile()
        setupTransactionListener()

        return view
    }

    private fun loadUserProfile() {
        val userId = auth.currentUser?.uid ?: return
        db.collection("users").document(userId).get()
            .addOnSuccessListener { doc ->
                val profileUrl = doc.getString("profileImageUrl")
                Glide.with(this)
                    .load(profileUrl)
                    .placeholder(R.drawable.ic_profile_placeholder)
                    .into(userProfileImage)
            }
    }

    private fun setupTransactionListener() {
        val userId = auth.currentUser?.uid ?: return

        // Listening to the same collection your Repository saves to
        transactionListener = db.collection("users").document(userId)
            .collection("payments") // Standardized collection name
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    emptyStateText.visibility = View.VISIBLE
                    emptyStateText.text = "Error: ${error.message}"
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    transactions.clear()
                    for (doc in snapshot.documents) {
                        // Map Firestore document directly to Payment object
                        val payment = doc.toObject(Payment::class.java)
                        payment?.let {
                            it.id = doc.id // Ensure ID is set from doc reference
                            transactions.add(it)
                        }
                    }

                    updateUI()
                }
            }
    }

    private fun updateUI() {
        if (transactions.isEmpty()) {
            emptyStateText.visibility = View.VISIBLE
            receivedRecyclerView.visibility = View.GONE
            totalReceivedAmount.text = "KSh 0.00"
        } else {
            emptyStateText.visibility = View.GONE
            receivedRecyclerView.visibility = View.VISIBLE
            adapter.notifyDataSetChanged()

            val total = transactions.sumOf { it.amount }
            totalReceivedAmount.text = "KSh %,.2f".format(total)
        }
    }

    // Add transaction locally (e.g., after proximity trade/RPID detect)
    fun addTransaction(payment: Payment) {
        transactions.add(0, payment)
        adapter.notifyItemInserted(0)
        receivedRecyclerView.scrollToPosition(0)
        updateUI()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        transactionListener?.remove()
    }
}

// *** DELETE THE LOCAL TRANSACTION DATA CLASS AT THE BOTTOM ***