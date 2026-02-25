package com.example.blinknpay

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import de.hdodenhof.circleimageview.CircleImageView
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class TransactionDetailsFragment : Fragment() {

    private lateinit var senderProfileImage: CircleImageView
    private lateinit var receivedAmount: TextView
    private lateinit var receivedFrom: TextView
    private lateinit var receivedTime: TextView
    private lateinit var transactionId: TextView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_transaction_details, container, false)

        // Bind views
        senderProfileImage = view.findViewById(R.id.senderProfileImage)
        receivedAmount = view.findViewById(R.id.receivedAmount)
        receivedFrom = view.findViewById(R.id.receivedFrom)
        receivedTime = view.findViewById(R.id.receivedTime)
        transactionId = view.findViewById(R.id.transactionId)

        // Get arguments using Safe Args
        val args = TransactionDetailsFragmentArgs.fromBundle(requireArguments())
        val txnId = args.transactionId
        val amount = args.amount
        val sender = args.sender
        val txnRef = args.transactionRef
        val timestamp = args.timestamp

        // Format amount as KES currency
        val formattedAmount = NumberFormat.getCurrencyInstance(Locale("en", "KE")).apply {
            currency = Currency.getInstance("KES")
            maximumFractionDigits = 2
        }.format(amount)

        // Format timestamp
        val formattedTime = try {
            val date = Date(timestamp)
            SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(date)
        } catch (e: Exception) {
            "Unknown time"
        }

        // Update UI
        receivedFrom.text = "From: $sender"
        receivedAmount.text = formattedAmount
        receivedTime.text = formattedTime
        transactionId.text = "Transaction ID: $txnRef"

        // Load profile image if available (optional)
        Glide.with(this)
            .load("") // Placeholder; no profile URL from M-Pesa callback
            .placeholder(R.drawable.ic_profile_placeholder)
            .error(R.drawable.ic_profile_placeholder)
            .into(senderProfileImage)

        return view
    }
}
