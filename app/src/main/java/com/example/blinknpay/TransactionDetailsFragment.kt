package com.example.blinknpay

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import com.airbnb.lottie.LottieAnimationView
import com.bumptech.glide.Glide
import com.example.blinknpay.utils.ReceiptGenerator
import de.hdodenhof.circleimageview.CircleImageView
import java.io.File
import java.io.FileOutputStream

class TransactionDetailsFragment : Fragment() {

    private lateinit var senderProfileImage: CircleImageView
    private lateinit var receivedAmount: TextView
    private lateinit var receivedFrom: TextView
    private lateinit var receivedTime: TextView
    private lateinit var transactionId: TextView
    private lateinit var btnShareReceipt: Button

    // Animation Views
    private var successOverlay: FrameLayout? = null
    private var lottieCheckmark: LottieAnimationView? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        val view = inflater.inflate(R.layout.fragment_transaction_details, container, false)

        // 1. Bind views
        senderProfileImage = view.findViewById(R.id.senderProfileImage)
        receivedAmount = view.findViewById(R.id.receivedAmount)
        receivedFrom = view.findViewById(R.id.receivedFrom)
        receivedTime = view.findViewById(R.id.receivedTime)
        transactionId = view.findViewById(R.id.transactionId)
        btnShareReceipt = view.findViewById(R.id.btnShareReceipt)

        // Success Overlay (Must be added to your XML)
        successOverlay = view.findViewById(R.id.successOverlay)
        lottieCheckmark = view.findViewById(R.id.lottieCheckmark)

        // 2. Get arguments using Safe Args
        val args = TransactionDetailsFragmentArgs.fromBundle(requireArguments())

        val payment = Payment(
            id = args.transactionId,
            amount = args.amount.toDouble(),
            externalPartyName = args.sender,
            transactionRef = args.transactionRef,
            timestamp = args.timestamp
        )

        // 3. Update UI
        receivedFrom.text = payment.externalPartyName
        receivedAmount.text = payment.formattedAmount()
        receivedTime.text = payment.formattedTime()
        transactionId.text = "Ref: ${payment.transactionRef}"

        // 4. Dynamic Styling
        try {
            val statusColor = Color.parseColor(payment.getDirectionColor())
            receivedAmount.setTextColor(statusColor)
        } catch (e: Exception) {
            receivedAmount.setTextColor(Color.BLACK)
        }

        // 5. Brand Logo Loading
        val brandIcon = if (payment.transactionRef.startsWith("R")) R.drawable.ic_mpesa else R.drawable.ic_profile_placeholder

        Glide.with(this)
            .load(brandIcon)
            .circleCrop()
            .into(senderProfileImage)

        // 6. Share Receipt Action with Animation
        btnShareReceipt.setOnClickListener {
            showSuccessAndShare(payment)
        }

        return view
    }

    private fun showSuccessAndShare(payment: Payment) {
        successOverlay?.visibility = View.VISIBLE
        lottieCheckmark?.playAnimation()

        // Give the animation time to play before showing share sheet
        lottieCheckmark?.postDelayed({
            successOverlay?.visibility = View.GONE
            shareReceipt(payment)
        }, 1800)
    }

    private fun shareReceipt(payment: Payment) {
        try {
            val context = requireContext()
            val bitmap = ReceiptGenerator.generateReceiptBitmap(context, payment)

            // Save bitmap to cache
            val cachePath = File(context.cacheDir, "images")
            cachePath.mkdirs()
            val stream = FileOutputStream("$cachePath/receipt.png")
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            stream.close()

            // Share via FileProvider
            val imagePath = File(context.cacheDir, "images")
            val newFile = File(imagePath, "receipt.png")
            val contentUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                newFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                setDataAndType(contentUri, context.contentResolver.getType(contentUri))
                putExtra(Intent.EXTRA_STREAM, contentUri)
                type = "image/png"
            }
            startActivity(Intent.createChooser(shareIntent, "Share Receipt via"))

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}