package com.example.blinknpay

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.lifecycleScope



import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.facebook.shimmer.ShimmerFrameLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import android.util.Log


class TransactionsFragment : Fragment() {

    private lateinit var rvTransactions: RecyclerView
    private lateinit var tvEmptyState: TextView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var shimmerLayout: ShimmerFrameLayout

    private lateinit var adapter: PaymentHistoryAdapter
    private val transactions = mutableListOf<Payment>()

    private lateinit var smsSyncManager: SmsSyncManager
    private lateinit var paymentRepository: PaymentRepository

    // Permission Launcher
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            syncAndLoadData()
        } else {
            Toast.makeText(requireContext(), "Permission denied to read SMS history", Toast.LENGTH_SHORT).show()
            loadFromFirestoreOnly() // Fallback
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_transactions, container, false)

        // Init Components
        smsSyncManager = SmsSyncManager(requireContext())
        paymentRepository = PaymentRepository(this)


        rvTransactions = view.findViewById(R.id.rvTransactions) // Updated ID
        tvEmptyState = view.findViewById(R.id.tvEmptyState)
        swipeRefresh = view.findViewById(R.id.swipeRefresh)
        shimmerLayout = view.findViewById(R.id.shimmerLayout)

        rvTransactions.layoutManager = LinearLayoutManager(requireContext())

        adapter = PaymentHistoryAdapter(transactions) { payment ->
            val action = TransactionsFragmentDirections
                .actionTransactionsFragmentToTransactionDetailsFragment()

            action.transactionId = payment.id
            action.amount = payment.amount.toFloat()
            action.sender = payment.sender
            action.transactionRef = payment.transactionRef
            action.timestamp = payment.timestamp

            findNavController().navigate(action)
        }
        rvTransactions.adapter = adapter

        swipeRefresh.setOnRefreshListener { checkSmsPermissionAndSync() }

        checkSmsPermissionAndSync()
        return view
    }

    private fun checkSmsPermissionAndSync() {
        when {
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED -> {
                syncAndLoadData()
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.READ_SMS)
            }
        }
    }






    private fun syncAndLoadData() {
        shimmerLayout.visibility = View.VISIBLE
        shimmerLayout.startShimmer()

        // 1. Launch in IO for background work
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val localSms = smsSyncManager.fetchLocalTransactions()

                // 2. Move to Main Thread for UI and Lifecycle access
                withContext(Dispatchers.Main) {
                    if (isAdded && view != null) {
                        if (localSms.isNotEmpty()) {
                            transactions.clear()
                            transactions.addAll(localSms)
                            transactions.sortByDescending { it.timestamp }
                            updateUIState()
                        }
                    }
                }

                // 3. Trigger Cloud Sync
                paymentRepository.syncSmsToCloud { success ->
                    // The callback returns here. We must jump to Main to touch the View/Lifecycle
                    activity?.runOnUiThread {
                        if (isAdded && view != null) {
                            // Now we can safely use viewLifecycleOwner
                            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                                val refreshedSms = smsSyncManager.fetchLocalTransactions()

                                withContext(Dispatchers.Main) {
                                    if (isAdded && view != null) {
                                        transactions.clear()
                                        transactions.addAll(refreshedSms)
                                        updateUIState()
                                        Log.d("BlinknPay_UI", "Cloud Sync Complete.")
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("BlinknPay_Error", "Sync failed: ${e.message}")
                withContext(Dispatchers.Main) {
                    if (isAdded && view != null) {
                        shimmerLayout.stopShimmer()
                        shimmerLayout.visibility = View.GONE
                    }
                }
            }
        }
    }
















    private fun loadFromFirestoreOnly() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        // Start a timer to measure response speed
        val startTime = System.currentTimeMillis()
        Log.d("BlinknPay_Firebase", "DATABASE_START: Fetching from cloud for $uid")

        val db = FirebaseFirestore.getInstance()

        db.collection("users")
            .document(uid)
            .collection("payments")
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { snapshot ->
                // Calculate and log the delay
                val delay = System.currentTimeMillis() - startTime
                Log.d("BlinknPay_Firebase", "DATABASE_SUCCESS: Received ${snapshot.size()} items in ${delay}ms")

                transactions.clear()

                // In 4.1.3, we ensure the loop is clean
                if (!snapshot.isEmpty) {
                    for (doc in snapshot.documents) {
                        try {
                            val payment = doc.toObject(Payment::class.java)
                            if (payment != null) {
                                transactions.add(payment)
                            }
                        } catch (e: Exception) {
                            Log.e("BlinknPay_Firebase", "MODEL_ERROR: Data mismatch for doc ${doc.id}: ${e.message}")
                        }
                    }
                } else {
                    Log.w("BlinknPay_Firebase", "DATABASE_EMPTY: Path exists but contains no records.")
                }

                updateUIState()
            }
            .addOnFailureListener { e ->
                val delay = System.currentTimeMillis() - startTime
                Log.e("BlinknPay_Firebase", "DATABASE_ERROR: Failed after ${delay}ms. Reason: ${e.message}")

                updateUIState()
                Toast.makeText(requireContext(), "Cloud Sync Failed", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateUIState() {
        // 1. UI Cleanup: Stop loading animations
        shimmerLayout.stopShimmer()
        shimmerLayout.visibility = View.GONE
        swipeRefresh.isRefreshing = false

        // 2. Data Integrity: Remove duplicates (id) and Sort
        // This handles cases where an SMS and a Firestore doc describe the same payment
        val distinctTransactions = transactions.distinctBy { it.id }
            .sortedByDescending { it.timestamp }

        // 3. Visibility Logic
        if (distinctTransactions.isEmpty()) {
            tvEmptyState.visibility = View.VISIBLE
            rvTransactions.visibility = View.GONE
            tvEmptyState.text = "No transactions found.\nPull down to sync M-Pesa/Airtel."
        } else {
            tvEmptyState.visibility = View.GONE
            rvTransactions.visibility = View.VISIBLE

            // 4. Adapter Update
            // We pass the cleaned, sorted list to the adapter
            try {
                adapter.replaceAll(distinctTransactions)

                // If replaceAll doesn't automatically call notifyDataSetChanged,
                // you can uncomment the line below:
                // adapter.notifyDataSetChanged()

                Log.d("BlinknPay_UI", "UI Updated with ${distinctTransactions.size} unique transactions")
            } catch (e: Exception) {
                Log.e("BlinknPay_UI", "Adapter update failed: ${e.message}")
                // Fallback: reload adapter if replaceAll crashes
                rvTransactions.adapter = adapter
            }
        }
    }



}