package com.example.blinknpay

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.*
import kotlin.math.pow
import android.os.Build;
import android.util.Log
import android.view.*
import android.widget.*
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.appcompat.app.AlertDialog
import android.bluetooth.BluetoothDevice
import java.util.UUID
import java.util.*
import java.util.Locale
import com.google.android.gms.location.Priority
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.provider.Settings
import androidx.activity.result.IntentSenderRequest
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.LinearLayoutManager
import android.annotation.SuppressLint
import android.net.wifi.WifiManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.app.ActivityCompat
import android.content.IntentSender
import android.location.LocationManager
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.common.api.ResolvableApiException
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.findNavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.example.blinknpay.ui.merchant.MerchantSwipeCallback
import androidx.recyclerview.widget.ItemTouchHelper
import com.example.blinknpay.databinding.DialogPaymentBinding
import com.blinknpay.viewmodel.PaymentViewModel

import android.graphics.Color
import android.telephony.SubscriptionManager

import com.google.firebase.functions.FirebaseFunctions

import android.widget.EditText
import android.widget.Toast
import java.util.Date
import androidx.lifecycle.ViewModelProvider
import okhttp3.*
import java.io.IOException

import android.widget.TextView

import androidx.navigation.fragment.NavHostFragment


class BluetoothFragment : Fragment(){
    // Initialize the ViewModel
    private lateinit var viewModel: PaymentViewModel
    private lateinit var enableBluetoothLauncher: ActivityResultLauncher<Intent>

    private val repo = ProfileRepository()
    // ✅ MUST be here (class level)

    private lateinit var paymentRepo: PaymentRepository
    private val merchants = mutableListOf<Merchant>()

    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private var bluetoothLeAdvertiser: BluetoothLeAdvertiser? = null

    private var bluetoothAdapter: BluetoothAdapter? = null

    private val handler = Handler(Looper.getMainLooper())
    private var isAdvertising = false

    private lateinit var transactionAdapter: TransactionAdapter
    private lateinit var smsSyncManager: SmsSyncManager
    private lateinit var paymentRepository: PaymentRepository
    private val recentPayments = mutableListOf<Payment>()

    private val recentTransactions = mutableListOf<Payment>()




    private lateinit var merchantRecycler: RecyclerView

    

    private lateinit var currentMerchantText: TextView




    private var advertiseCallback: AdvertiseCallback? = null


    private var foundMerchant = false
    private lateinit var amountInput: EditText

    private val MIC_PERMISSION_REQUEST = 2001
    private val PERMISSION_REQUEST_READ_PHONE_STATE = 101

    private lateinit var merchantAdapter:MerchantAdapter


    private var isScanning = false


    private val PREFS_NAME = "BlinkModePrefs"
    private val KEY_MODE = "isMerchantMode"


    private val merchantUUID: UUID = UUID.fromString("323e4567-e89b-12d3-a456-426614174002")
    private var nearestMerchantId: String? = null

    private var isListeningUltrasound = false
    private var audioRecord: AudioRecord? = null
    private var detectThread: Thread? = null

    private val scanResults: MutableList<Receiver> = mutableListOf()


    private val distanceHistory = HashMap<String, MutableList<Double>>()



// REMOVE THESE:
// private lateinit var chipMpesa: com.google.android.material.chip.Chip
// private lateinit var chipKcb: com.google.android.material.chip.Chip

    // ADD THESE:
    private lateinit var cardMpesa: com.google.android.material.card.MaterialCardView
    private lateinit var cardEquity: com.google.android.material.card.MaterialCardView
    private lateinit var cardKCB: com.google.android.material.card.MaterialCardView
    private lateinit var cardAirtel:com.google.android.material.card.MaterialCardView


    private lateinit var gpsLauncher: androidx.activity.result.ActivityResultLauncher<IntentSenderRequest>



    // Add these near your other private variables
    // Add these to your class variables
    private lateinit var historyAdapter: PaymentHistoryAdapter
    private val paymentHistoryList = mutableListOf<Payment>()


    private var selectedRail: String = "MPESA" // To track selection

    companion object {
        // Bluetooth / Permissions
        private const val REQUEST_ENABLE_BT = 1001
        private const val PERMISSION_REQUEST_CODE = 101
        private const val DISTANCE_SMOOTH_WINDOW = 5

        // Daraja / M-Pesa STK Push
        const val SHORTCODE = "174379" // Sandbox shortcode
        const val PASSKEY = "bfb279f9aa9bdbcf158e97dd71a467cd2e0c893059b10f78e6b72ada1ed2c919" // Full sandbox passkey
        const val CALLBACK_URL = "https://us-central1-blinknpay.cloudfunctions.net/stkPushCallback"

        const val CONSUMER_KEY = "4V1w6zy7LeQDGy3JiFGyfPUP30jG9rmVkH9CGhAuEudZk4Re"
        const val CONSUMER_SECRET = "5q8P3hoxt6VG1mHgTccAR9ONUvs1dp95phnWdZH2xJhZl7K6ZRsEzdFz8EYcuwE4"
    }




    data class STKPushResponse(
        val MerchantRequestID: String? = null,
        val CheckoutRequestID: String? = null,
        val ResponseCode: String? = null,
        val ResponseDescription: String? = null,
        val CustomerMessage: String? = null,

        // Result fields for the actual callback (used in the cloud side)
        val ResultCode: String? = null,
        val ResultDesc: String? = null
    )







    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (BluetoothDevice.ACTION_FOUND == intent?.action) {
                val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                val scannedName = device?.name ?: return // Ignore devices without a name immediately

                val db = FirebaseFirestore.getInstance()

                // 🛑 THE FILTER START: Only check our database
                db.collection("merchant_mappings").document(scannedName).get()
                    .addOnSuccessListener { doc ->
                        val mId = doc.getString("merchantId")

                        if (mId != null) {
                            // ✅ MATCH FOUND: Now we fetch details and show it in UI
                            db.collection("merchants").document(mId).get()
                                .addOnSuccessListener { profile ->
                                    val businessName = profile.getString("name") ?: scannedName
                                    val upi = profile.getString("upiId") ?: ""

                                    // Ensure we don't add the same merchant twice
                                    if (!scanResults.any { it.businessName == businessName }) {
                                        // Receiver(Name, Status/Subtext, ID)
                                        scanResults.add(Receiver(businessName, "BlinknPay Merchant Found", mId))
                                        merchantAdapter.notifyDataSetChanged()

                                        // Optional: Haptic feedback to tell user a merchant was found
                                        Log.d("BlinknPay", "Validated Merchant Found: $businessName")
                                    }
                                }
                        } else {
                            // ❌ NO MATCH: This is a "random" device. We do nothing.
                            Log.d("BlinknPay Filter", "Ignored random device: $scannedName")
                        }
                    }
                    .addOnFailureListener {
                        // Ignore errors, likely just a network hiccup or random signal
                    }
            }
        }
    }































    // Extension property to map merchant codes (e.g., "SRN", "NVS") to readable names
    // Instead of mapping codes, rely on the data
    val String.businessName: String
        get() = this.ifEmpty { "Unknown Merchant" } // Keeps the actual merchant name from discovery


    // Convert RSSI to estimated distance (0.3m, 1.2m, 2.5m…)
    private fun rssiToDistance(rssi: Int): Double {
        val txPower = -59  // Default calibrated TX power value
        val n = 2.0        // Environmental factor (2=open space, 3=indoors)
        return Math.pow(10.0, (txPower - rssi) / (10 * n))
    }

    // Extract merchantUUID from SSID "BlinknPay|MERCHANT|<UUID>"
    private fun parseMerchantIdFromSSID(ssid: String): String? {
        if (!ssid.startsWith("BlinknPay|MERCHANT|")) return null
        return ssid.split("|").getOrNull(2)
    }





    // ADD THIS INTERFACE HERE
    interface BlinkSyncCallback {
        fun onDiscoveryStarted()
        fun onDiscoveryStopped()
    }

    // ADD THIS METHOD TO CONTROL THE ENGINE
    fun setBlinkSyncDiscoveryEnabled(enabled: Boolean, callback: BlinkSyncCallback?) {
        if (enabled) {
            isScanning = true
            startAdaptiveDiscovery()
            // Add ultrasound/WiFi start logic here
            callback?.onDiscoveryStarted()
        } else {
            isScanning = false
            // Add stop logic here
            callback?.onDiscoveryStopped()
        }
    }



    val tvMainBalance = view?.findViewById<TextView>(R.id.tvMainBalance)
    val btnTogglePrivacy = view?.findViewById<ImageView>(R.id.btnTogglePrivacy)




    data class DiscoveredDevice(
        val device: BluetoothDevice,
        var rssi: Int,
        var lastSeen: Long
    )









    // 2. PASTE THE LAUNCHER HERE (Class level)
    private val requestSmsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.refreshBalance()
        } else {
            Toast.makeText(requireContext(), "SMS permission denied. Balance hidden.", Toast.LENGTH_LONG).show()
        }
    }






    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // ✅ Register the GPS enable launcher here
        gpsLauncher =
            registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
                if (result.resultCode == AppCompatActivity.RESULT_OK) {
                    // ✅ User enabled GPS successfully
                    Toast.makeText(requireContext(), "✅ GPS enabled", Toast.LENGTH_SHORT).show()
                    setupBluetoothComponents()
                } else {
                    Toast.makeText(
                        requireContext(),
                        "⚠️ GPS is required for operation",
                        Toast.LENGTH_LONG
                    ).show()
                    forceEnableGPS() // 🔁 Keep prompting until GPS is enabled
                }
            }

        return inflater.inflate(R.layout.bluetooth_fragment, container, false)

    }

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)



// Initialize Managers
        smsSyncManager = SmsSyncManager(requireContext())
        paymentRepository = PaymentRepository(this)




        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        requireActivity().registerReceiver(bluetoothReceiver, filter)
        bluetoothAdapter?.startDiscovery() // This kicks off the "Classic" search








        // Correct way to initialize an AndroidViewModel in Studio 4.1.3
        val factory = ViewModelProvider.AndroidViewModelFactory.getInstance(requireActivity().application)
        viewModel = ViewModelProvider(this, factory).get(PaymentViewModel::class.java)



        viewModel.balance.observe(viewLifecycleOwner) { newBalance ->
            val tvMainBalance = view?.findViewById<TextView>(R.id.tvMainBalance)

            // 1. Update text
            tvMainBalance?.text = "KES ${String.format("%,.2f", newBalance)}"

            // 2. Trigger the "Magical" Shimmer
            tvMainBalance?.let { applyShimmerEffect(it) }

            // 3. Keep your existing Pulse animation for extra feedback
            tvMainBalance?.animate()
                ?.scaleX(1.05f)?.scaleY(1.05f)
                ?.setDuration(150)
                ?.withEndAction {
                    tvMainBalance.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start()
                }?.start()
        }




// --- Add this inside onViewCreated after paymentRepo init ---


// Now the rest of your code won't be red
        btnTogglePrivacy?.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            viewModel.togglePrivacy()
        }

        // -------------------------
        // 3. ViewModel Observers (Magical & Real-time)
        // -------------------------

        // Observe Balance - Triggered by SMS ContentObserver
        viewModel.balance.observe(viewLifecycleOwner) { balance ->
            if (tvMainBalance != null) {
                if (btnTogglePrivacy != null) {
                    updateBalanceUI(tvMainBalance, btnTogglePrivacy)
                }
            }

            // Magical Pulse Animation on every auto-update
            if (tvMainBalance != null) {
                tvMainBalance.animate()
                    .scaleX(1.05f).scaleY(1.05f)
                    .setDuration(150)
                    .withEndAction {
                        if (tvMainBalance != null) {
                            tvMainBalance.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start()
                        }
                    }.start()
            }
        }

        // Observe Privacy Mode
        viewModel.isPrivacyActive.observe(viewLifecycleOwner) { _ ->
            if (tvMainBalance != null) {
                if (btnTogglePrivacy != null) {
                    updateBalanceUI(tvMainBalance, btnTogglePrivacy)
                }
            }
        }

        // -------------------------
        // 4. Click Listeners
        // -------------------------
        if (btnTogglePrivacy != null) {
            btnTogglePrivacy.setOnClickListener {
                // Haptic feedback for a premium feel
                it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                viewModel.togglePrivacy()
            }
        }
















        // -------------------------
        // 1. Bluetooth & Permissions Setup
        // -------------------------
        enableBluetoothLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                initBluetooth()
            } else {
                Toast.makeText(requireContext(), "Bluetooth is required", Toast.LENGTH_SHORT).show()
            }
        }

        // -------------------------
        // 2. Initialize Repositories & UI Bindings
        // -------------------------
        paymentRepo = PaymentRepository(this)
        merchantRecycler = view.findViewById(R.id.merchantRecycler)
        currentMerchantText = view.findViewById(R.id.currentMerchantText)

        // Payment Rail Cards
        cardMpesa = view.findViewById(R.id.cardMpesa)
        cardEquity = view.findViewById(R.id.cardEquity)
        cardKCB = view.findViewById(R.id.cardKCB)
        cardAirtel = view.findViewById(R.id.cardAirtel)

        // Quick Actions & Balance UI (The new Card)
        val tvMainBalance = view.findViewById<TextView>(R.id.tvMainBalance)
        val btnTogglePrivacy = view.findViewById<ImageView>(R.id.btnTogglePrivacy)
        val btnPay = view.findViewById<LinearLayout>(R.id.btnPay)
        val btnSend = view.findViewById<LinearLayout>(R.id.btnSend)
        val btnWithdraw = view.findViewById<LinearLayout>(R.id.btnWithdraw)
        val btnDeposit = view.findViewById<LinearLayout>(R.id.btnDeposit)

        // -------------------------
        // 3. ViewModel Observers (Light Architecture)
        // -------------------------

        // Observe Balance and Privacy Mode together
        viewModel.balance.observe(viewLifecycleOwner) { balance ->
            updateBalanceUI(tvMainBalance, btnTogglePrivacy)
        }

        viewModel.isPrivacyActive.observe(viewLifecycleOwner) { _ ->
            updateBalanceUI(tvMainBalance, btnTogglePrivacy)
        }

        // Observe Rail Selection to update UI checkmarks/borders
        viewModel.selectedRail.observe(viewLifecycleOwner) { rail ->
            selectedRail = rail // Sync with your local variable
            updatePaymentSelectionUI()
        }

        // -------------------------
        // 4. Click Listeners
        // -------------------------

        // Privacy Toggle
        btnTogglePrivacy.setOnClickListener {
            viewModel.togglePrivacy()
        }

        // Quick Action Buttons
        btnPay.setOnClickListener {
            // Trigger your Quad-Channel Engine to find nearest merchant
            Toast.makeText(requireContext(), "Searching for RPID...", Toast.LENGTH_SHORT).show()
        }

        btnSend.setOnClickListener {
            Toast.makeText(requireContext(), "Send feature coming soon", Toast.LENGTH_SHORT).show()
        }

        btnWithdraw.setOnClickListener {
            val withdrawSheet = WithdrawOptionsBottomSheet()
            withdrawSheet.show(parentFragmentManager, "WithdrawOptions")
        }

        btnDeposit.setOnClickListener {
            Toast.makeText(requireContext(), "Deposit to $selectedRail", Toast.LENGTH_SHORT).show()
        }

        // Payment Rail Selection
        cardMpesa.setOnClickListener {
            viewModel.setRail("MPESA")
            Toast.makeText(requireContext(), "M-Pesa Daraja Selected", Toast.LENGTH_SHORT).show()
        }

        cardEquity.setOnClickListener {
            viewModel.setRail("EQUITY")
            Toast.makeText(requireContext(), "Equity Jenga Selected", Toast.LENGTH_SHORT).show()
        }

        cardKCB.setOnClickListener {
            viewModel.setRail("KCB")
            Toast.makeText(requireContext(), "KCB Bank Selected", Toast.LENGTH_SHORT).show()
        }

        cardAirtel.setOnClickListener {
            viewModel.setRail("AIRTEL")
            Toast.makeText(requireContext(), "Airtel Money Selected", Toast.LENGTH_SHORT).show()
        }

        // -------------------------
        // 5. Merchant Adapter & Swipe Logic
        // -------------------------
        merchantAdapter = MerchantAdapter(scanResults) { merchant ->
            showPaymentPrompt(merchant.businessName)
        }
        merchantRecycler.layoutManager = LinearLayoutManager(requireContext(), RecyclerView.VERTICAL, false)
        merchantRecycler.adapter = merchantAdapter

        val swipeCallback = MerchantSwipeCallback(
            context = requireContext(),
            merchants = scanResults,
            adapter = merchantAdapter,
            listener = object : MerchantSwipeCallback.SwipeActionListener {
                override fun onSwipeRight(position: Int) {
                    val merchant = scanResults.getOrNull(position) ?: return
                    showPaymentPrompt(merchant.businessName)
                }

                override fun onSwipeLeft(position: Int) {
                    val moved = scanResults.removeAt(position)
                    scanResults.add(moved)
                    merchantAdapter.notifyItemMoved(position, scanResults.size - 1)
                }
            }
        )
        ItemTouchHelper(swipeCallback).attachToRecyclerView(merchantRecycler)

        // -------------------------
        // 6. Navigation & QR Handling
        // -------------------------
        val toolbar = requireActivity().findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        toolbar?.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_scan) {
                findNavController().navigate(R.id.navigation_qr_scanner)
                true
            } else false
        }

        findNavController().currentBackStackEntry?.savedStateHandle
            ?.getLiveData<String>("qr_result")
            ?.observe(viewLifecycleOwner) { qrValue ->
                if (!qrValue.isNullOrEmpty()) {
                    handleScannedQr(qrValue)
                    findNavController().currentBackStackEntry?.savedStateHandle?.remove<String>("qr_result")
                }
            }


        // Initial balance fetch based on default SIM
        val defaultRail = detectDefaultRailFromSim() ?: "MPESA"
        viewModel.setRail(defaultRail)



        // 3. Update the UI when balance changes
        viewModel.balance.observe(viewLifecycleOwner) { newBalance ->
            // Update your TextView/UI here
            // balanceTextView.text = "KES ${String.format("%.2f", newBalance)}"
        }
















        // -------------------------
        // 7. Initialize Engine & Data
        // -------------------------
        checkPermissionsAndInitBluetooth()
        startNearestMerchantWatcher()
        testFirestoreConnection()
        checkBluetoothReady()
        checkSmsPermission()

    }



















    private fun handlePaymentSuccess(merchantName: String, amount: Double) {
        // 1. Show the Toast ONLY now
        Toast.makeText(requireContext(), "✅ Payment Successful!", Toast.LENGTH_LONG).show()

        // 2. Refresh local balance immediately
        viewModel.refreshBalance()

        // 3. Trigger the quad-channel sync to Firestore
        val smsManager = SmsSyncManager(requireContext())
        val localTxns = smsManager.fetchLocalTransactions()

        // Use the repository to push the new data to the cloud
        paymentRepo.syncSmsToCloud(localTxns) {
            Log.d("BlinknPay_Sync", "Cloud sync completed after successful payment")
        }

        // 4. (Optional) Navigate to a receipt screen or show a success dialog
        // findNavController().navigate(R.id.navigation_transactions)
    }

    // After successful STK Push




    private fun fetchHistory() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        FirebaseFirestore.getInstance()
            .collection("users").document(userId)
            .collection("transactions") // or "payments" - check your Firestore collection name
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e("BlinknPay", "History fetch failed", e)
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val newList = snapshot.toObjects(Payment::class.java)
                    historyAdapter.replaceAll(newList)
                }
            }
    }





    private fun updateBalanceUI(tvBalance: TextView?, btnToggle: ImageView?) {
        // 1. Get current state from ViewModel
        val isHidden = viewModel.isPrivacyActive.value ?: false
        val balance = viewModel.balance.value ?: 0.0

        if (isHidden) {
            // Privacy Mode: Hide balance with dots
            tvBalance?.text = "KES • • • •"

            // Visual feedback for 'Locked' state: semi-transparent icon
            btnToggle?.setImageResource(R.drawable.ic_eye_sparkle)
            btnToggle?.alpha = 0.4f
        } else {
            // Active Mode: Show formatted balance
            val formattedBalance = String.format("%,.2f", balance)
            tvBalance?.text = "KES $formattedBalance"

            // Reset icon to full brightness
            btnToggle?.setImageResource(R.drawable.ic_eye_sparkle)
            btnToggle?.alpha = 1.0f

            // Trigger Shimmer only when showing the real numbers
            tvBalance?.let { applyShimmerEffect(it) }
        }
    }






    private fun applyShimmerEffect(targetView: TextView) {
        val paint = targetView.paint
        val textString = targetView.text.toString()
        if (textString.isEmpty()) return

        val textWidth = paint.measureText(textString)
        val currentTextColor = targetView.currentTextColor

        val shimmerShader = android.graphics.LinearGradient(
            -textWidth, 0f, 0f, 0f,
            intArrayOf(currentTextColor, android.graphics.Color.WHITE, currentTextColor),
            floatArrayOf(0f, 0.5f, 1f),
            android.graphics.Shader.TileMode.CLAMP
        )

        paint.shader = shimmerShader

        // ✅ Using the full path to android.animation.ValueAnimator to fix the "Red" error
        val animator = android.animation.ValueAnimator.ofFloat(0f, textWidth * 3f)
        animator.duration = 1200

        animator.addUpdateListener { anim ->
            val offset = anim.animatedValue as Float
            val matrix = android.graphics.Matrix()
            matrix.setTranslate(offset, 0f)
            shimmerShader.setLocalMatrix(matrix)
            targetView.invalidate()
        }

        animator.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                paint.shader = null
                targetView.invalidate()
            }
        })

        animator.start()
    }









    private fun checkSmsPermission() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_SMS)
            == PackageManager.PERMISSION_GRANTED) {
            viewModel.refreshBalance()
        } else {
            requestSmsPermissionLauncher.launch(Manifest.permission.READ_SMS)
        }
    }



            @RequiresApi(Build.VERSION_CODES.LOLLIPOP_MR1)
    private fun updatePaymentSelectionUI() {
        val activeStroke = 4
        val inactiveStroke = 1

        val activeColor = Color.parseColor("#6CAF10")
        val inactiveColor = Color.parseColor("#DDDDDD")

        val activeBg = Color.parseColor("#E8F5E9")
        val inactiveBg = Color.parseColor("#F9F9F9")

        // M-PESA
        cardMpesa.strokeWidth = if (selectedRail == "MPESA") activeStroke else inactiveStroke
        cardMpesa.strokeColor = if (selectedRail == "MPESA") activeColor else inactiveColor
        cardMpesa.setCardBackgroundColor(if (selectedRail == "MPESA") activeBg else inactiveBg)

        // Equity
        cardEquity.strokeWidth = if (selectedRail == "EQUITY") activeStroke else inactiveStroke
        cardEquity.strokeColor = if (selectedRail == "EQUITY") activeColor else inactiveColor
        cardEquity.setCardBackgroundColor(if (selectedRail == "EQUITY") activeBg else inactiveBg)

        // KCB
        cardKCB.strokeWidth = if (selectedRail == "KCB") activeStroke else inactiveStroke
        cardKCB.strokeColor = if (selectedRail == "KCB") activeColor else inactiveColor
        cardKCB.setCardBackgroundColor(if (selectedRail == "KCB") activeBg else inactiveBg)

        // Airtel
        cardAirtel?.let {
            it.strokeWidth = if (selectedRail == "AIRTEL") activeStroke else inactiveStroke
            it.strokeColor = if (selectedRail == "AIRTEL") activeColor else inactiveColor
            it.setCardBackgroundColor(if (selectedRail == "AIRTEL") activeBg else inactiveBg)
        }
    }





    // -------------------------
// Detect Default Payment Rail Based on SIM
// -------------------------
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP_MR1)
    private fun detectDefaultRailFromSim(): String? {
        // Check READ_PHONE_STATE permission first
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.READ_PHONE_STATE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // Request permission if not granted
            requestPermissions(
                arrayOf(Manifest.permission.READ_PHONE_STATE),
                PERMISSION_REQUEST_READ_PHONE_STATE
            )
            return null
        }

        // Get SubscriptionManager
        val subscriptionManager =
            requireContext().getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
                ?: return null

        // Get active SIMs
        val activeSubs = subscriptionManager.activeSubscriptionInfoList ?: return null

        // Iterate through subscriptions and detect carrier
        for (sub in activeSubs) {
            // Compatible with Kotlin < 1.5 / Android Studio 4.1.3
            val carrierName = sub.carrierName?.toString()?.toLowerCase(Locale.getDefault()) ?: continue

            when {
                carrierName.contains("safaricom") -> return "MPESA"
                carrierName.contains("airtel") -> return "AIRTEL"
            }
        }

        // If no known carrier found, return null
        return null
    }

    // -------------------------
// Handle Permission Result
// -------------------------
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            PERMISSION_REQUEST_READ_PHONE_STATE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Permission granted: detect SIM carrier
                    val defaultRail = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP_MR1) {
                        detectDefaultRailFromSim()
                    } else {
                        TODO("VERSION.SDK_INT < LOLLIPOP_MR1")
                    }
                    selectedRail = defaultRail ?: "MPESA" // Fallback to M-Pesa
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP_MR1) {
                        updatePaymentSelectionUI()
                    }
                } else {
                    // Permission denied: fallback
                    Toast.makeText(
                        requireContext(),
                        "Permission required to detect carrier. Defaulting to M-Pesa.",
                        Toast.LENGTH_SHORT
                    ).show()
                    selectedRail = "MPESA"
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP_MR1) {
                        updatePaymentSelectionUI()
                    }
                }
            }
        }
    }


    private fun checkBluetoothReady(): Boolean {
        // Initialize adapter if null
        if (bluetoothAdapter == null) {
            bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
            if (bluetoothAdapter == null) {
                Toast.makeText(requireContext(), "Bluetooth not supported", Toast.LENGTH_SHORT).show()
                return false
            }
        }

        // Check if Bluetooth is enabled
        if (bluetoothAdapter?.isEnabled != true) {
            promptEnableBluetooth()  // triggers enableBluetoothLauncher
            return false
        }

        // Check BLE Advertising capability
        if (bluetoothAdapter?.bluetoothLeAdvertiser == null) {
            Toast.makeText(requireContext(), "BLE Advertising not available", Toast.LENGTH_SHORT).show()
            return false
        }

        // Cache BLE advertiser
        bluetoothLeAdvertiser = bluetoothAdapter?.bluetoothLeAdvertiser
        return true
    }


// Inside BluetoothFragment class








    private fun initiatePayment(merchantName: String, amount: Double, rail: String) {
        Log.d("BlinknPay", "Paying KES $amount to $merchantName via $rail")

        if (rail == "MPESA") {
            // 1. Show immediate UI feedback
            Toast.makeText(requireContext(), "Opening M-Pesa Prompt...", Toast.LENGTH_SHORT).show()

            // 2. Trigger the STK Push logic we built
            performSTKPush(amount, merchantName)
        } else {
            // Future proofing for Equity/KCB/Airtel
            Toast.makeText(requireContext(), "$rail support coming soon", Toast.LENGTH_SHORT).show()
        }
    }










    // -------------------------
// Open merchant catalog
// -------------------------
    private fun openMerchantCatalog(merchant: Receiver) {
        Toast.makeText(requireContext(), "Open catalog for ${merchant.businessName}", Toast.LENGTH_SHORT).show()
    }





        // Navigate




    // Example function to open a specific transaction
    private fun openTransactionDetails(txnId: String) {
        val bundle = Bundle().apply {
            putString("transactionId", txnId)
        }
        findNavController().navigate(R.id.transactionDetailsFragment, bundle)
    }





    private fun openReceivedFragment(amount: String, merchantName: String, timestamp: String) {
        val bundle = Bundle().apply {
            putString("amount", amount)
            putString("merchantName", merchantName)
            putString("timestamp", timestamp)
        }

        findNavController().navigate(R.id.receivedFragment, bundle)
    }







    private fun handleScannedQr(qrValue: String) {
        val db = FirebaseFirestore.getInstance()
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val mainActivity = (requireActivity() as? MainActivity)

        Log.d("BlinknPay_QR", "Processing QR Reference: $qrValue")

        // Target the 'payments' collection used by PaymentRepository
        db.collection("users").document(userId)
            .collection("payments")
            .whereEqualTo("transactionRef", qrValue)
            .get()
            .addOnSuccessListener { result ->
                if (!result.isEmpty) {
                    val doc = result.documents[0]

                    // Use unified Payment model
                    val payment = doc.toObject(Payment::class.java)?.apply {
                        id = doc.id
                    } ?: return@addOnSuccessListener

                    // 1. Use MainActivity's helper to switch tabs safely
                    mainActivity?.safeNavigateTo(R.id.navigation_transactions)

                    // 2. Use the activity's navView for the delay to ensure UI thread alignment
                    mainActivity?.navView?.postDelayed({
                        // Find the ReceivedFragment inside the NavHost
                        val navHostFragment = mainActivity.supportFragmentManager
                            .findFragmentById(R.id.nav_host_fragment) as? NavHostFragment

                        val currentFragment = navHostFragment?.childFragmentManager?.fragments
                            ?.firstOrNull { it is ReceivedFragment } as? ReceivedFragment

                        currentFragment?.let {
                            it.addTransaction(payment)
                            Toast.makeText(requireContext(), "Payment verified", Toast.LENGTH_SHORT).show()
                        } ?: run {
                            // Fallback: If fragment isn't in view yet, the data is still in Firestore
                            Log.d("BlinknPay_QR", "ReceivedFragment not active; data will load from Cloud.")
                        }
                    }, 350) // 350ms is the "sweet spot" for Jetpack Navigation transitions

                } else {
                    Toast.makeText(requireContext(), "Transaction not found", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Log.e("BlinknPay_QR", "Firestore error: ${e.message}")
                Toast.makeText(requireContext(), "Failed to fetch transaction", Toast.LENGTH_SHORT).show()
            }
    }












    // ✅ Stop Merchant Broadcast
    private fun stopMerchantBroadcast() {
        try {
            Toast.makeText(requireContext(), "Stopping merchant broadcast…", Toast.LENGTH_SHORT).show()

            stopBluetoothAdvertising()
            stopUltrasoundBeacon()

            Log.d("BlinknPay", "Merchant broadcast stopped.")
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(requireContext(), "Error stopping broadcast: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // 🧩 Helper placeholders (no errors, compile-safe)
    private fun stopBluetoothAdvertising() {
        Log.d("BlinknPay", "Bluetooth advertising stopped (placeholder).")
    }

    private fun stopUltrasoundBeacon() {
        Log.d("BlinknPay", "Ultrasound beacon stopped (placeholder).")
    }


    // ✅ Concrete implementation in BluetoothFragment
    private fun startMerchantBroadcast() {
        try {
            Toast.makeText(requireContext(), "Starting merchant broadcast…", Toast.LENGTH_SHORT).show()

            // Example: Bluetooth or ultrasonic signal could go here
            startBluetoothAdvertising()
            startUltrasoundBeacon(19000)

            Log.d("BlinknPay", "Merchant broadcast started successfully.")
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(requireContext(), "Error starting broadcast: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // 🧩 Helper placeholders (prevent future unresolved reference errors)
    private fun startBluetoothAdvertising() {
        Log.d("BlinknPay", "Bluetooth advertising started (placeholder).")
    }

    private fun startUltrasoundBeacon(frequencyHz: Int) {
        Log.d("BlinknPay", "Ultrasound beacon emitting at $frequencyHz Hz (placeholder).")
    }


    private fun startAdaptiveDiscovery() {
        // TODO: Implement Bluetooth scanning for customer mode
        Log.d("BlinknPay", "Started adaptive discovery (customer scanning).")
    }




    private var nearestMerchant: String? = null

    private fun startNearestMerchantWatcher() {
        Handler(Looper.getMainLooper()).postDelayed(object : Runnable {
            override fun run() {
                if (scanResults.isNotEmpty()) {
                    // Use Double.MAX_VALUE as fallback for null distances
                    val nearest = scanResults.minByOrNull { it.distance ?: Double.MAX_VALUE }

                    nearestMerchant = nearest?.businessName
                    nearest?.let {
                        currentMerchantText.text = "Your Nearest: ${it.businessName} — Tap to Pay"
                    }
                } else {
                    currentMerchantText.text = "Searching for nearby merchants..."
                }

                // Repeat every 5 seconds
                Handler(Looper.getMainLooper()).postDelayed(this, 5000)
            }
        }, 5000)
    }




    /**
     * Checks if a given phone number is a Safaricom number.
     * Accepts numbers in local (07XXXXXXX) or international format (2547XXXXXXX)
     */


    private fun isSafaricomNumber(phone: String): Boolean {
        val clean = phone.replace("+", "").trim()

        // Convert 2547XXXXXXXX to 07XXXXXXXX
        val normalized = when {
            clean.startsWith("254") -> "0" + clean.substring(3)
            clean.startsWith("0") -> clean
            else -> clean
        }

        if (normalized.length < 4) return false

        val prefix = normalized.substring(0, 4)

        val safaricomPrefixes = setOf(
            "0700","0701","0702","0703","0704","0705","0706","0707","0708","0709",
            "0710","0711","0712","0713","0714","0715","0716","0717","0718","0719",
            "0720","0721","0722","0723","0724","0725","0726","0727","0728","0729",
            "0740","0741","0742","0743","0744","0745","0746","0747","0748","0749",
            "0757","0758","0759",
            "0768","0769",
            "0790","0791","0792","0793","0794","0795","0796","0797","0798","0799",
            "0110","0111","0112","0113","0114","0115"
        )

        return safaricomPrefixes.contains(prefix)
    }

    private fun showPaymentPrompt(merchantName: String) {
        // Inflate binding
        val binding = DialogPaymentBinding.inflate(layoutInflater)

        // Create dialog
        val dialog = AlertDialog.Builder(requireContext())
            .setView(binding.root)
            .create()

        // ----------------------------
        // Update rail logo dynamically
        // ----------------------------
        when (selectedRail) {
            "MPESA" -> binding.ivRailLogo.setImageResource(R.drawable.ic_mpesa)
            "AIRTEL" -> binding.ivRailLogo.setImageResource(R.drawable.airtel)
            "EQUITY" -> binding.ivRailLogo.setImageResource(R.drawable.equity)
            "KCB" -> binding.ivRailLogo.setImageResource(R.drawable.ic_kcb)
            else -> binding.ivRailLogo.setImageResource(R.drawable.ic_mpesa) // fallback
        }

        // ----------------------------
        // Update titles
        // ----------------------------
        binding.tvDialogTitle.text = "Pay $merchantName"
        binding.tvDialogSubtitle.text = "Enter amount via $selectedRail for $merchantName"

        // ----------------------------
        // Cancel button
        // ----------------------------
        binding.btnCancel.setOnClickListener { dialog.dismiss() }

        // ----------------------------
        // Pay button
        // ----------------------------
        binding.btnPay.setOnClickListener {
            val amountText = binding.etAmountInput.text.toString()
            val amount = amountText.toDoubleOrNull()
            if (amount == null || amount <= 0) {
                binding.etAmountInput.error = "Enter a valid amount"
                return@setOnClickListener
            }
            initiatePayment(merchantName, amount, selectedRail)


            dialog.dismiss()

            // Call the appropriate payment method
            when (selectedRail) {
                "MPESA" -> performSTKPush(amount, merchantName)
                "EQUITY", "KCB" -> initiateCloudPayment(amount, merchantName)
                "AIRTEL" -> Toast.makeText(requireContext(), "Airtel payment not implemented yet", Toast.LENGTH_SHORT).show()
                else -> Toast.makeText(requireContext(), "Unsupported payment method", Toast.LENGTH_LONG).show()
            }
        }

        dialog.show()
    }

    // ----------------------------
// Updated STK Push & Cloud Payment to accept merchantName
// ----------------------------
    private fun performSTKPush(amount: Double, merchantName: String) {
        val rawPhone = FirebaseAuth.getInstance().currentUser?.phoneNumber
        val cleanPhone = rawPhone?.let { normalizePhone(it) }

        if (cleanPhone == null) {
            Toast.makeText(requireContext(), "Invalid phone number", Toast.LENGTH_SHORT).show()
            return
        }

        val timestamp = getTimestamp()
        val password = generatePassword(SHORTCODE, PASSKEY, timestamp)

        val request = STKPushRequest(
            BusinessShortCode = SHORTCODE,
            Password = password,
            Timestamp = timestamp,
            Amount = amount,
            PartyA = cleanPhone,
            PartyB = SHORTCODE,
            PhoneNumber = cleanPhone,
            CallBackURL = CALLBACK_URL,
            AccountReference = "BlinknPay",
            TransactionDesc = "Payment to $merchantName",
            TransactionType = "CustomerPayBillOnline"
        )

        getAccessToken(CONSUMER_KEY, CONSUMER_SECRET) { token ->
            activity?.runOnUiThread {
                if (!isAdded || token == null) {
                    if (token == null) Toast.makeText(requireContext(), "M-Pesa Token Error", Toast.LENGTH_SHORT).show()
                    return@runOnUiThread
                }

                // Using the new Extension Function
                RetrofitClient.darajaApi.stkPush("Bearer $token", request).enqueue(
                    onSuccess = { body ->
                        if (!isAdded) return@enqueue
                        if (body.ResponseCode == "0") {
                            Toast.makeText(requireContext(), "STK Push sent to phone", Toast.LENGTH_LONG).show()
                            savePendingTransaction(amount, merchantName)
                        } else {
                            Toast.makeText(requireContext(), "M-Pesa: ${body.CustomerMessage}", Toast.LENGTH_LONG).show()
                        }
                    },
                    onError = { code, errorBody ->
                        if (!isAdded) return@enqueue
                        Log.e("STK_ERROR", "Code: $code Body: $errorBody")
                        Toast.makeText(requireContext(), "STK Failed: $code", Toast.LENGTH_SHORT).show()
                    },
                    onFailure = { t ->
                        if (!isAdded) return@enqueue
                        Log.e("STK_FAIL", "Connection Failure: ${t.message}")
                        Toast.makeText(requireContext(), "Network Error", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }






    private fun initiateCloudPayment(amount: Double, merchantName: String) {
        val rawPhone = FirebaseAuth.getInstance().currentUser?.phoneNumber
        val userPhone = rawPhone?.let { normalizePhone(it) }

        if (userPhone == null) {
            Toast.makeText(requireContext(), "Invalid phone number", Toast.LENGTH_SHORT).show()
            return
        }

        if (nearestMerchantId == null) {
            Toast.makeText(requireContext(), "Merchant ID missing. Please re-scan.", Toast.LENGTH_SHORT).show()
            return
        }

        val data = hashMapOf(
            "merchantId" to nearestMerchantId,
            "amount" to amount,
            "phone" to userPhone,
            "railType" to selectedRail
        )

        FirebaseFunctions.getInstance()
            .getHttpsCallable("initiatePayment")
            .call(data)
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "✅ $selectedRail Payment Initiated", Toast.LENGTH_SHORT).show()
                savePendingTransaction(amount, merchantName)
            }
            .addOnFailureListener { e ->
                Log.e("BlinknPay", "Cloud Error", e)
                Toast.makeText(requireContext(), "❌ Payment Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    // Helper
    private fun normalizePhone(phone: String): String? {
        val clean = phone.replace("+", "").trim()
        return when {
            clean.startsWith("254") -> clean
            clean.startsWith("0") -> "254" + clean.substring(1)
            clean.length == 9 -> "254$clean"
            else -> null
        }
    }



















    fun savePendingTransaction(amount: Double, merchantName: String) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()

        val transaction = hashMapOf(
            "amount" to amount,
            "merchantName" to merchantName,
            "merchantId" to nearestMerchantId,
            "status" to "PENDING",
            "timestamp" to FieldValue.serverTimestamp()
        )

        db.collection("users")
            .document(userId)
            .collection("transactions")
            .add(transaction)
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Saved pending transaction", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Failed to save transaction", Toast.LENGTH_SHORT).show()
            }
    }











    // Utility functions
    private fun getTimestamp(): String {
        val sdf = java.text.SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault())
        return sdf.format(Date())
    }

    private fun generatePassword(shortcode: String, passkey: String, timestamp: String): String {
        val data = "$shortcode$passkey$timestamp"
        val bytes = data.toByteArray(Charsets.UTF_8)
        return android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
    }







    // Extension function for OkHttp Call to simplify enqueue
    fun okhttp3.Call.enqueueSimple(callback: (success: Boolean, responseBody: String?) -> Unit) {
        this.enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                e.printStackTrace()
                callback(false, null)
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.use {
                    if (!it.isSuccessful) {
                        callback(false, null)
                        return
                    }
                    callback(true, it.body?.string())
                }
            }
        })
    }

    // Function to get access token
    private fun getAccessToken(
        consumerKey: String,
        consumerSecret: String,
        callback: (token: String?) -> Unit
    ) {
        val url = "https://sandbox.safaricom.co.ke/oauth/v1/generate?grant_type=client_credentials"
        val credentials = "$consumerKey:$consumerSecret"
        val basicAuth = "Basic " + android.util.Base64.encodeToString(credentials.toByteArray(), android.util.Base64.NO_WRAP)

        val client = OkHttpClient()
        val request = Request.Builder()
            .url(url)
            .get()
            .addHeader("Authorization", basicAuth)
            .build()

        client.newCall(request).enqueueSimple { success, body ->
            if (success && body != null) {
                val json = org.json.JSONObject(body)
                val accessToken = json.optString("access_token", null)
                callback(accessToken)
            } else {
                callback(null)
            }
        }
    }



    val initiateCloudPayment = { amt: Double, mName: String, rail: String ->
        val userPhone = FirebaseAuth.getInstance().currentUser?.phoneNumber ?: "254795546075"

        if (nearestMerchantId == null) {
            activity?.runOnUiThread {
                Toast.makeText(requireContext(), "Merchant ID missing. Please re-scan.", Toast.LENGTH_SHORT).show()
            }
        } else {
            // We use the basic call without 'options' to satisfy your older compiler
            val functions = com.google.firebase.functions.FirebaseFunctions.getInstance()
            val callable = functions.getHttpsCallable("initiatePayment")

            val data = hashMapOf(
                "merchantId" to nearestMerchantId,
                "amount" to amt,
                "phone" to userPhone,
                "railType" to rail
            )

            callable.call(data)
                .addOnSuccessListener {
                    activity?.runOnUiThread {
                        Toast.makeText(requireContext(), "✅ $rail Payment Initiated", Toast.LENGTH_SHORT).show()
                        savePendingTransaction(amt, mName)
                    }
                }
                .addOnFailureListener { e ->
                    activity?.runOnUiThread {
                        Log.e("BlinknPay", "Cloud Function Error", e)
                        // This error will appear if the function takes > 60s (SDK default)
                        Toast.makeText(requireContext(), "❌ Rail Error: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
        }
    }
















    fun testFirestoreConnection() {
        val db = FirebaseFirestore.getInstance()


        val testData = hashMapOf(
            "message" to "Hello Firestore",
            "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp()
        )




        db.collection("testConnection")
            .add(testData)
            .addOnSuccessListener { documentRef ->
                Toast.makeText(
                    requireContext(),
                    "✅ Firestore connected! DocID: ${documentRef.id}",
                    Toast.LENGTH_LONG
                ).show()
                Log.d("FirestoreTest", "Document added with ID: ${documentRef.id}")
            }
            .addOnFailureListener { e ->
                Toast.makeText(
                    requireContext(),
                    "❌ Firestore failed: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
                e.printStackTrace()
            }
    }

    // ✅ Test reading from Firestore
    fun readTestFirestore() {
        val db = FirebaseFirestore.getInstance()
        db.collection("testConnection")
            .get()
            .addOnSuccessListener { snapshot ->
                for (doc in snapshot.documents) {
                    Log.d("FirestoreTest", "${doc.id} => ${doc.data}")
                }
                Toast.makeText(
                    requireContext(),
                    "✅ Read ${snapshot.size()} documents",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(
                    requireContext(),
                    "❌ Read failed: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
                e.printStackTrace()
            }
    }












    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action: String = intent.action ?: return

            if (BluetoothDevice.ACTION_FOUND == action) {
                val device: BluetoothDevice =
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE) ?: return

                val deviceName = device.name ?: return
                val deviceAddress = device.address

                // 🧠 FILTER: only show if registered
                val match = BlinknPayApp.registeredMerchants[deviceName]
                    ?: BlinknPayApp.registeredMerchants[deviceAddress]

                if (match != null) {
                    // registered merchant → show
                    addNearbyMerchant(match)
                } else {
                    // not registered → ignore
                    Log.d("BluetoothFragment", "Ignored unregistered device: $deviceName")
                }
            }
        }
    }







    private fun addNearbyMerchant(merchant: Merchant) {
        // Check if this merchant is already in the displayed list
        val exists = scanResults.any { it.businessName.equals(merchant.merchantName, ignoreCase = true) }
        if (!exists) {
            // Convert RSSI to distance in meters using standard formula
            val distanceMeters = rssiToDistance(merchant.rssi)

            val receiver = Receiver(
                businessName = merchant.merchantName,
                category = "Merchant (Nearby)",
                distance = distanceMeters
            )

            // Add on UI thread
            activity?.runOnUiThread {
                scanResults.add(receiver)
                merchantAdapter.notifyItemInserted(scanResults.size - 1)
            }



            Log.d(
                "BluetoothFragment",
                "✅ Added nearby merchant: ${merchant.merchantName} | RSSI: ${merchant.rssi} | Distance: ${"%.2f".format(distanceMeters)} m"
            )
        }
    }
















    private fun checkPermissionsAndInitBluetooth() {
        val permissionsNeeded = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= 31) {
            // ✅ Android 12+ (API 31 and above)
            if (ContextCompat.checkSelfPermission(requireContext(), "android.permission.BLUETOOTH_SCAN")
                != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add("android.permission.BLUETOOTH_SCAN")
            }
            if (ContextCompat.checkSelfPermission(requireContext(), "android.permission.BLUETOOTH_ADVERTISE")
                != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add("android.permission.BLUETOOTH_ADVERTISE")
            }
            if (ContextCompat.checkSelfPermission(requireContext(), "android.permission.BLUETOOTH_CONNECT")
                != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add("android.permission.BLUETOOTH_CONNECT")
            }
        } else {
            // ✅ Pre-Android 12 → location permission required for BLE
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }

        // ✅ Wi-Fi & location (for universal discovery)
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_WIFI_STATE)
            != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.ACCESS_WIFI_STATE)
        }
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CHANGE_WIFI_STATE)
            != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.CHANGE_WIFI_STATE)
        }
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        // ✅ Request all missing permissions at once, or initialize if all granted
        if (permissionsNeeded.isEmpty()) {
            initBluetooth()  // Only initialize Bluetooth here, do NOT call startUniversalDiscovery()
        } else {
            requestPermissions(permissionsNeeded.toTypedArray(), PERMISSION_REQUEST_CODE)
        }
    }



















    @SuppressLint("MissingPermission")
    private fun initBluetooth() {
        val bluetoothManager = requireContext().getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        // 1️⃣ Check if Bluetooth is supported
        if (bluetoothAdapter == null) {
            Toast.makeText(requireContext(), "❌ Bluetooth not supported on this device", Toast.LENGTH_LONG).show()
            return
        }

        // 2️⃣ If Bluetooth is OFF → prompt user to enable it
        if (bluetoothAdapter?.isEnabled == false) {
            promptEnableBluetooth()
            return
        }

        // 3️⃣ If Bluetooth is ON → ensure GPS is enabled
        forceEnableGPS()
    }






    private fun checkGPSAndBluetooth() {
        val locationRequest = LocationRequest.create().apply {
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }

        val builder = LocationSettingsRequest.Builder()
            .addLocationRequest(locationRequest)
            .setAlwaysShow(true)

        val client = LocationServices.getSettingsClient(requireActivity())
        val task = client.checkLocationSettings(builder.build())

        task.addOnSuccessListener {
            // ✅ GPS is ON → now check Bluetooth
            initBluetooth()
        }

        task.addOnFailureListener { exception ->
            if (exception is ResolvableApiException) {
                try {
                    exception.startResolutionForResult(requireActivity(), 1001)
                } catch (sendEx: IntentSender.SendIntentException) {
                    Log.e("GPS", "⚠️ Failed to show GPS enable dialog: ${sendEx.message}")
                }
            } else {
                Toast.makeText(requireContext(), "Please enable GPS manually", Toast.LENGTH_LONG).show()
            }
        }
    }





    private fun setupGpsLauncher() {
        gpsLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            if (result.resultCode == AppCompatActivity.RESULT_OK) {
                Log.d("GPS", "✅ User enabled GPS successfully.")
                setupBluetoothComponents()
            } else {
                Toast.makeText(requireContext(), "⚠️ GPS is required for scanning", Toast.LENGTH_LONG).show()
                forceEnableGPS() // 🔁 keep re-prompting if denied
            }
        }
    }












    private fun forceEnableGPS() {
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            1000L // check interval
        ).build()

        val builder = LocationSettingsRequest.Builder()
            .addLocationRequest(locationRequest)
            .setAlwaysShow(true) // always show GPS prompt

        val client = LocationServices.getSettingsClient(requireActivity())
        val task = client.checkLocationSettings(builder.build())

        task.addOnSuccessListener {
            Log.d("GPS", "✅ GPS is ON.")
            setupBluetoothComponents()
        }

        task.addOnFailureListener { exception ->
            if (exception is ResolvableApiException) {
                try {
                    val intentSenderRequest = IntentSenderRequest.Builder(exception.resolution).build()
                    gpsLauncher.launch(intentSenderRequest)
                } catch (sendEx: IntentSender.SendIntentException) {
                    Log.e("GPS", "⚠️ Could not launch GPS enable dialog: ${sendEx.message}")
                }
            } else {
                Toast.makeText(requireContext(), "⚠️ Please enable GPS manually", Toast.LENGTH_LONG).show()
            }
        }
    }









    private fun promptEnableBluetooth() {
        if (bluetoothAdapter?.isEnabled == true) {
            forceEnableGPS() // 🔹 Call new GPS enforcer instead of ensureGpsEnabled()
        } else {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            enableBluetoothLauncher.launch(enableBtIntent)
        }
    }

    @SuppressLint("MissingPermission")
    private fun setupBluetoothComponents() {
        // 🔹 Step 1: Get the Bluetooth adapter
        val bluetoothManager = requireContext().getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = bluetoothManager.adapter

        if (adapter == null) {
            Toast.makeText(requireContext(), "❌ Bluetooth not available on this device", Toast.LENGTH_LONG).show()
            return
        }

        // 🔹 Step 2: Ensure Bluetooth is ON
        if (!adapter.isEnabled) {
            promptEnableBluetooth()
            return
        }

        // 🔹 Step 3: Initialize BLE Scanner & Advertiser
        bluetoothLeScanner = adapter.bluetoothLeScanner
        bluetoothLeAdvertiser = adapter.bluetoothLeAdvertiser

        if (bluetoothLeScanner == null) {
            Toast.makeText(requireContext(), "⚠️ BLE Scanning not supported on this device", Toast.LENGTH_LONG).show()
            return
        }

        if (bluetoothLeAdvertiser == null) {
            Toast.makeText(requireContext(), "⚠️ BLE Advertising not supported on this device", Toast.LENGTH_LONG).show()
            return
        }

        // 🔹 Step 4: Check GPS again (needed for BLE scanning on Android 10+)
        val locationManager = requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            forceEnableGPS() // 🔹 Replaced ensureGpsEnabled() with new forceEnableGPS()
            return
        }

        // ✅ Step 5: Everything ready — start both operations
        Log.d("BLUETOOTH", "✅ BLE components initialized successfully — starting advertising & scanning")
        startAdvertising()
        startUniversalDiscovery()
        startContinuousScan() // 👈 kept, since you said it’s used elsewhere
    }

















    @SuppressLint("MissingPermission")
    private fun startAdvertising() {
        if (isAdvertising) return
        Log.d("ADAPTIVE", "🚀 Starting adaptive advertising...")

        startAdaptiveAdvertising(requireContext())
    }

    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission", "ServiceCast")
    private fun startAdaptiveAdvertising(context: Context) {

        var currentMerchantIndex = 0
        var isAdvertising = false
        var advertiseCallback: AdvertiseCallback? = null
        val handler = Handler(Looper.getMainLooper())
        val adapter = BluetoothAdapter.getDefaultAdapter()

        // 🔹 Dynamic merchant list – only discovered merchants
        val merchants = scanResults.mapNotNull { it.businessName }.distinct() // Remove duplicates & nulls

        if (merchants.isEmpty()) {
            Toast.makeText(context, "No merchants discovered for advertising!", Toast.LENGTH_SHORT).show()
            return
        }

        // 🔹 Check Bluetooth availability
        if (adapter == null || !adapter.isEnabled) {
            Log.w("ADAPTIVE", "⚠️ Bluetooth off or unsupported → using Wi-Fi fallback.")
            val currentMerchant = merchants.getOrNull(currentMerchantIndex)
            currentMerchant?.let { startWifiSSIDBroadcast(context, it) }
            return
        }

        val bluetoothLeAdvertiser = adapter.bluetoothLeAdvertiser
        if (bluetoothLeAdvertiser == null) {
            Log.w("ADAPTIVE", "⚠️ BLE advertiser unavailable → fallback to Classic BT.")
            startClassicBluetoothBroadcast(context, merchants[currentMerchantIndex])
            return
        }

        // 🔹 Stop advertising safely
        fun stopAdvertising() {
            if (!isAdvertising) return
            try {
                bluetoothLeAdvertiser.stopAdvertising(advertiseCallback)
                isAdvertising = false
                Log.d("BLE", "🛑 Advertising stopped.")
            } catch (e: Exception) {
                Log.e("BLE", "⚠️ Stop error: ${e.message}")
            }
        }

        // 🔹 Rotate merchants and start BLE advertising
        lateinit var rotateRunnable: Runnable
        rotateRunnable = object : Runnable {
            override fun run() {
                try {
                    if (isAdvertising) {
                        stopAdvertising()
                        Log.d("BLE", "🛑 Stopped current advertising for rotation.")
                    }

                    val merchantName = merchants[currentMerchantIndex]
                    val codeBytes = merchantName.toByteArray(Charsets.UTF_8)

                    Log.d("DEBUG", "Advertising merchant: $merchantName")

                    val advertiseData = AdvertiseData.Builder()
                        .addServiceData(ParcelUuid(merchantUUID), codeBytes)
                        .setIncludeDeviceName(false)
                        .build()

                    val settings = AdvertiseSettings.Builder()
                        .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                        .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                        .setConnectable(false)
                        .build()

                    advertiseCallback = object : AdvertiseCallback() {
                        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                            super.onStartSuccess(settingsInEffect)
                            isAdvertising = true
                            Log.d("BLE", "✅ BLE Advertising started: $merchantName")
                        }

                        override fun onStartFailure(errorCode: Int) {
                            super.onStartFailure(errorCode)
                            isAdvertising = false
                            Log.e("BLE", "❌ BLE advertising failed for $merchantName ($errorCode), fallback triggered.")
                            handleFallback(context, merchantName, errorCode)
                        }
                    }

                    handler.postDelayed({
                        try {
                            bluetoothLeAdvertiser.startAdvertising(settings, advertiseData, advertiseCallback)
                            isAdvertising = true
                            Log.d("BLE", "📢 BLE advertising initiated for $merchantName")

                            (context as? Activity)?.runOnUiThread {
                                currentMerchantText.text = "Advertising: $merchantName"
                            }
                        } catch (e: Exception) {
                            Log.e("BLE", "⚠️ Failed to start BLE advertising: ${e.message}")
                            isAdvertising = false
                            handleFallback(context, merchantName, -1)
                        }
                    }, 1500)

                    // Move to next merchant
                    currentMerchantIndex = (currentMerchantIndex + 1) % merchants.size

                    // Schedule next rotation
                    handler.postDelayed(this, 10_000)

                } catch (e: Exception) {
                    Log.e("BLE", "❌ Error in rotateNextMerchant loop: ${e.message}")
                }
            }
        }

        // 🔹 Start rotation loop
        handler.post(rotateRunnable)
    }

    // ✅ Handle BLE fallback
    private fun handleFallback(context: Context, merchantName: String, errorCode: Int) {
        Log.w("BLE", "Fallback for merchant $merchantName with error code $errorCode")
        // Implement your fallback logic here, e.g., startClassicBluetoothBroadcast or Wi-Fi fallback
    }

















    /** ------------------------- FALLBACK SYSTEMS -------------------------- */

    @SuppressLint("MissingPermission")
    private fun startClassicBluetoothBroadcast(context: Context, merchantCode: String) {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return

        try {
            adapter.name = "BlinknPay_$merchantCode"
            val merchantName = merchantCode.businessName
            Log.d("CLASSIC", "📻 Classic Bluetooth name set: ${adapter.name} ($merchantName)")
        } catch (e: Exception) {
            Log.e("CLASSIC", "⚠️ Classic BT failed: ${e.message}")
            // Fallback to Wi-Fi using just the merchant code
            startWifiSSIDBroadcast(context, merchantCode)
        }
    }




// ===============================
// 🔊 ULTRASOUND DISCOVERY MODULE
// ===============================

    

    private fun startUltrasoundDiscovery() {
        Log.d("ULTRASOUND", "🎵 Starting ultrasound-based merchant discovery...")

        // 🎤 Check microphone permission
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                requireActivity(),
                arrayOf(Manifest.permission.RECORD_AUDIO),
                1001
            )
            return
        }

        if (!isListeningUltrasound) {
            isListeningUltrasound = true
            detectUltrasoundTone()
        } else {
            Log.d("ULTRASOUND", "⚠️ Already listening for ultrasound signals.")
        }
    }

    /**
     * Continuously listens to the microphone input to detect a 19kHz ultrasonic tone
     */
    private fun detectUltrasoundTone() {
        detectThread = Thread {
            try {
                val sampleRate = 44100
                val bufferSize = AudioRecord.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )

                if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
                    Log.e("ULTRASOUND", "❌ Invalid buffer size for AudioRecord.")
                    return@Thread
                }

                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
                )

                if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                    Log.e("ULTRASOUND", "❌ AudioRecord initialization failed.")
                    return@Thread
                }

                val buffer = ShortArray(bufferSize)
                audioRecord?.startRecording()

                Log.d("ULTRASOUND", "👂 Listening for ultrasound tones (around 19kHz)...")

                while (isListeningUltrasound && !Thread.interrupted()) {
                    val read = audioRecord?.read(buffer, 0, bufferSize) ?: 0
                    if (read > 0 && detect19kHzTone(buffer, read, sampleRate)) {
                        Handler(Looper.getMainLooper()).post {
                            Log.d("ULTRASOUND", "✅ Merchant ultrasound beacon detected!")
                            Toast.makeText(
                                requireContext(),
                                "Merchant detected via Ultrasound!",
                                Toast.LENGTH_SHORT
                            ).show()

                            // TODO: Add logic to register this merchant (e.g. add to merchant list)
                        }
                        break // stop after detection
                    }
                }

                audioRecord?.stop()
                audioRecord?.release()
                audioRecord = null
                Log.d("ULTRASOUND", "🛑 AudioRecord stopped after detection loop.")

            } catch (e: Exception) {
                Log.e("ULTRASOUND", "❌ Error detecting ultrasound: ${e.message}")
            }
        }

        detectThread?.start()
    }

    /**
     * Detects a 19kHz tone in the recorded buffer using zero-crossing frequency estimation
     */
    private fun detect19kHzTone(buffer: ShortArray, read: Int, sampleRate: Int): Boolean {
        var crossings = 0
        for (i in 1 until read) {
            if ((buffer[i - 1] >= 0 && buffer[i] < 0) || (buffer[i - 1] < 0 && buffer[i] >= 0)) {
                crossings++
            }
        }

        val estimatedFreq = crossings * sampleRate / (2.0 * read)
        val isDetected = estimatedFreq in 18500.0..19500.0

        if (isDetected) {
            Log.d("ULTRASOUND", "📶 Detected ultrasonic tone: ~${estimatedFreq.toInt()} Hz")
        }

        return isDetected
    }

    /**
     * Stops the ultrasound discovery safely
     */
    private fun stopUltrasoundDiscovery() {
        isListeningUltrasound = false
        detectThread?.interrupt()
        detectThread = null

        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Exception) {}

        audioRecord = null
        Log.d("ULTRASOUND", "🛑 Ultrasound discovery stopped.")
    }














    private fun startWifiSSIDBroadcast(context: Context, merchantCode: String) {
        try {
            // Use your extension property to get the merchant’s display name
            val name = merchantCode.businessName
            val ssid = "Blink_$merchantCode"

            // Ensure Wi-Fi is turned on before "broadcasting"
            ensureWifiEnabled(context)

            Log.d("WIFI", "🌐 Wi-Fi SSID logically mapped: $ssid ($name)")
            Toast.makeText(context, "Broadcasting $name via Wi-Fi", Toast.LENGTH_SHORT).show()

            // ⚠️ Note: This is a logical mapping, not a real SSID broadcast
        } catch (e: Exception) {
            Log.e("WIFI", "⚠️ Wi-Fi SSID broadcast failed: ${e.message}")
            Toast.makeText(context, "Wi-Fi broadcast failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }











    private fun handleFallback(context: Context, merchantCode: String, merchantName: String, errorCode: Int) {
        val adapter = BluetoothAdapter.getDefaultAdapter()

        when {
            adapter != null && adapter.isEnabled -> {
                Log.d("ADAPTIVE", "📡 Switching to Classic Bluetooth for $merchantName")
                startClassicBluetoothBroadcast(context, merchantCode)
            }

            ensureWifiEnabled(context) -> {
                Log.d("ADAPTIVE", "🌐 Bluetooth unavailable → switching to Wi-Fi for $merchantName")
                startWifiSSIDBroadcast(context, merchantCode)
            }

            else -> {
                Log.w("ADAPTIVE", "⚠️ No broadcast channel available for $merchantName.")
                Toast.makeText(context, "No available connection method for $merchantName", Toast.LENGTH_SHORT).show()
            }
        }
    }








    /** ------------------------- WIFI CONTROL -------------------------- */
    @SuppressLint("MissingPermission")
    private fun ensureWifiEnabled(context: Context): Boolean {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        if (wifiManager == null) {
            Log.e("WiFi", "⚠️ WifiManager not available.")
            return false
        }

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // ⚠️ On Android 10+ (API 29+), apps cannot toggle Wi-Fi programmatically.
                if (!wifiManager.isWifiEnabled) {
                    Log.w("WiFi", "⚠️ Wi-Fi is OFF — prompting user to enable it manually.")
                    val panelIntent = Intent(Settings.Panel.ACTION_WIFI)
                    panelIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(panelIntent)
                } else {
                    Log.d("WiFi", "✅ Wi-Fi already ON (Android 10+).")
                }
                true
            } else {
                // ✅ For Android 9 and below
                if (!wifiManager.isWifiEnabled) {
                    wifiManager.isWifiEnabled = true
                    Log.d("WiFi", "📶 Wi-Fi turned ON programmatically.")
                } else {
                    Log.d("WiFi", "✅ Wi-Fi already ON.")
                }
                true
            }
        } catch (e: SecurityException) {
            Log.e("WiFi", "❌ Missing CHANGE_WIFI_STATE permission: ${e.message}")
            false
        } catch (e: Exception) {
            Log.e("WiFi", "⚠️ Could not enable Wi-Fi: ${e.message}")
            false
        }
    }



    @SuppressLint("MissingPermission")
    @Synchronized
    private fun startContinuousScan() {
        val scanInterval = 30_000L   // 30s per scan cycle
        val restartDelay = 5_000L    // wait 5s before restarting next scan

        fun scheduleNextScan() {
            handler.postDelayed({
                if (bluetoothAdapter?.isEnabled == true) {
                    Log.d("SCAN", "🔁 Restarting BLE scan...")
                    startContinuousScan() // recursively restart
                }
            }, restartDelay)
        }

        // Stop any previous scan before starting a new one
        if (isScanning) {
            bluetoothLeScanner?.stopScan(bleScanCallback)
            isScanning = false
        }

        if (bluetoothAdapter?.isEnabled != true) {
            Log.w("SCAN", "⚠️ Bluetooth is off, cannot scan.")
            return
        }

        val filter = ScanFilter.Builder()
            // You can filter by UUID if all merchants share one
            //.setServiceUuid(ParcelUuid(merchantUUID))
            .build()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            bluetoothLeScanner?.startScan(listOf(filter), settings, bleScanCallback)
            isScanning = true
            Log.d("SCAN", "📡 BLE scanning started...")
        } catch (e: SecurityException) {
            Log.e("SCAN", "🚫 Missing permissions for BLE scan: ${e.message}")
            return
        }

        // Stop after interval and restart
        handler.postDelayed({
            if (isScanning) {
                bluetoothLeScanner?.stopScan(bleScanCallback)
                isScanning = false
                Log.d("SCAN", "⏹️ BLE scan stopped.")
                scheduleNextScan()
            }
        }, scanInterval)
    }
















    fun stopContinuousScan() {
        try {
            handler.removeCallbacksAndMessages(null)
            if (isScanning) {
                bluetoothLeScanner?.stopScan(bleScanCallback)
                isScanning = false
            }
            Log.d("SCAN", "🧹 Continuous BLE scan stopped cleanly.")
        } catch (e: Exception) {
            Log.e("SCAN", "⚠️ Stop continuous scan error: ${e.message}")
        }
    }



















    private var isDiscovering = false  // flag to prevent multiple starts

    private fun startUniversalDiscovery() {
        if (isDiscovering) return  // already running, exit
        isDiscovering = true

        Log.d("DISCOVERY", "🚀 Starting universal merchant discovery...")
        scanResults.clear()
        merchantAdapter.notifyItemInserted(scanResults.size - 1)


        // Permissions should already be checked before calling this
        // Only start discovery tasks here
        Handler(Looper.getMainLooper()).post {
            startBleDiscovery()
            startClassicBluetoothDiscovery()
            startWifiSSIDDiscovery()
            startUltrasoundDiscovery()
            startNearestMerchantWatcher()
        }
    }
















    private fun addUniqueReceiver(receiver: Receiver) {
        // Check if receiver with same businessName already exists
        val exists = scanResults.any { it.businessName.equals(receiver.businessName, ignoreCase = true) }

        if (!exists) {
            activity?.runOnUiThread {
                scanResults.add(receiver)
                // Notify RecyclerView adapter about the new item
                merchantAdapter.notifyItemInserted(scanResults.size - 1)
            }
        }
    }





    // --- 1. BLE Discovery ---
    private fun startBleDiscovery() {
        Log.d("DISCOVERY", "🔹 BLE discovery starting...")
        try {
            startContinuousScan() // your existing BLE scan implementation
        } catch (e: Exception) {
            Log.e("DISCOVERY", "❌ BLE discovery failed: ${e.message}")
        }
    }


    //StartClassicBluetoothDiscovery

    private fun startClassicBluetoothDiscovery() {
        val adapter = bluetoothAdapter ?: return
        if (!adapter.isEnabled) {
            Log.w("DISCOVERY", "⚠️ Classic Bluetooth is off.")
            return
        }

        // Register receiver only once
        if (!isClassicReceiverRegistered) {
            isClassicReceiverRegistered = true

            val filter = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_FOUND)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            }
            requireContext().registerReceiver(classicBtReceiver, filter)
        }

        if (!adapter.isDiscovering) {
            Log.d("DISCOVERY", "🔄 Starting classic Bluetooth discovery…")
            adapter.startDiscovery()
        }
    }














    // --- Calculate distance from RSSI (Short) ---
    private fun calculateDistanceFromRSSI(
        rssi: Short,
        txPower: Double = -59.0,
        n: Double = 2.0
    ): Double {
        val distance = 10.0.pow((txPower - rssi) / (10.0 * n))
        return String.format("%.2f", distance).toDouble() // round to 2 decimals
    }

    // --- Calculate distance from RSSI (Double) ---
    private fun rssiToDistance(
        rssi: Double,
        txPower: Double = -59.0,
        n: Double = 2.0
    ): Double {
        val distance = 10.0.pow((txPower - rssi) / (10.0 * n))
        return String.format("%.2f", distance).toDouble() // round to 2 decimals
    }

    // Usage example:









    // --- 3. Wi-Fi SSID Discovery ---
// --- 3. Wi-Fi SSID Discovery ---
    @SuppressLint("MissingPermission")
    private fun startWifiSSIDDiscovery() {
        Log.d("DISCOVERY", "📶 Wi-Fi SSID discovery starting...")
        try {
            val wifiManager = requireContext().applicationContext
                .getSystemService(Context.WIFI_SERVICE) as WifiManager

            val results = wifiManager.scanResults
            for (result in results) {
                val ssid = result.SSID
                if (ssid.isNullOrBlank()) continue

                // Dynamically parse merchantId from SSID if standardized, e.g., "BlinknPay|MERCHANT|<id>"
                val merchantId = parseMerchantIdFromSSID(ssid) ?: continue

                // Convert RSSI to approximate distance in meters
                val distanceMeters = rssiToDistance(result.level)

                // Fetch merchant info from Firestore
                FirebaseFirestore.getInstance()
                    .collection("merchants")
                    .document(merchantId)
                    .get()
                    .addOnSuccessListener { doc ->
                        if (!doc.exists()) {
                            Log.d("DISCOVERY", "⛔ Ignored unknown merchant: $merchantId")
                            return@addOnSuccessListener
                        }

                        val merchantName = doc.getString("name") ?: "Unknown Store"
                        val category = doc.getString("category") ?: "Merchant"

                        val receiver = Receiver(
                            businessName = merchantName,
                            category = "Merchant (Wi-Fi)",
                            distance = distanceMeters
                        )

                        addUniqueReceiver(receiver)

                        activity?.runOnUiThread {
                            scanResults.add(receiver)
                            merchantAdapter.notifyDataSetChanged()
                        }

                        Log.d(
                            "DISCOVERY",
                            "📡 Merchant detected via Wi-Fi: $merchantName | ID: $merchantId | Distance: ${"%.2f".format(distanceMeters)} m"
                        )
                    }
                    .addOnFailureListener { e ->
                        Log.e("DISCOVERY", "❌ Firestore error for $merchantId: ${e.message}")
                    }
            }
        } catch (e: Exception) {
            Log.e("DISCOVERY", "❌ Wi-Fi SSID discovery failed: ${e.message}")
        }
    }


































    // ✅ Example ScanCallback to extract merchant name from scanResponse

    // ------------------- BLE Scan -------------------

    // ------------------- BLE Scan -------------------

    private val bleScanCallback: ScanCallback by lazy {
        object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                result?.let { r ->
                    val uuid = r.scanRecord?.serviceUuids?.firstOrNull()?.uuid ?: merchantUUID
                    if (uuid == merchantUUID) {
                        val serviceData = r.scanRecord?.getServiceData(ParcelUuid(merchantUUID))
                        val rawCode = try {
                            serviceData?.toString(Charsets.UTF_8)?.trim()
                        } catch (e: Exception) {
                            null
                        }

                        val merchantName = when (rawCode) {
                            "SRN" -> "Serena Hotel"
                            "NVS" -> "Naivas Supermarket"
                            else -> rawCode ?: "Unknown Merchant"
                        }

                        // Convert RSSI (Int) to Double distance
                        val distanceMeters = rssiToDistance(r.rssi.toDouble())

                        processMerchant(merchantName, "BLE", distanceMeters)
                    }
                }
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>?) {
                results?.forEach { onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, it) }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e("BLE", "Scan failed: $errorCode")
            }
        }
    }













    // ------------------- Universal Scan Callback -------------------
    private val discoveredMerchants = mutableSetOf<String>()


    // --- 3. Wi-Fi SSID Discovery ---


    @SuppressLint("LongLogTag")
    private fun processMerchant(merchantId: String, source: String, distance: Double) {
        // 🔍 Check if we already processed this merchant from this source
        val uniqueKey = "${merchantId}_$source"
        if (discoveredMerchants.contains(uniqueKey)) return

        // 🔥 Fetch merchant details dynamically from Firestore
        FirebaseFirestore.getInstance()
            .collection("merchants")
            .document(merchantId)
            .get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) {
                    Log.d("DISCOVERY", "⛔ Ignored unknown merchant: $merchantId")
                    return@addOnSuccessListener
                }

                val merchantName = doc.getString("name") ?: "Unknown Store"
                val category = doc.getString("category") ?: "Merchant"
                val businessType = doc.getString("type") ?: source

                val receiver = Receiver(
                    businessName = merchantName,
                    category = "Merchant ($businessType)",
                    distance = distance  // Now Double
                )

                // Mark as discovered
                discoveredMerchants.add(uniqueKey)

                // Update UI
                activity?.runOnUiThread {
                    scanResults.add(receiver)
                    merchantAdapter.notifyDataSetChanged()
                }

                Log.d(
                    "DISCOVERY",
                    "✅ Merchant detected: $merchantName | ID: $merchantId | via $source | Distance: ${"%.2f".format(distance)} m"
                )
            }
            .addOnFailureListener { e ->
                Log.e("DISCOVERY", "❌ Firestore error for $merchantId: ${e.message}")
            }
    }




















    // Converts RSSI to approximate distance in meters
    private fun rssiToDistance(rssi: Int, txPower: Int = -59): Double {
        // Free-space path loss model
        return 10.0.pow((txPower - rssi) / (10.0 * 2))  // n = 2 (path-loss exponent)
    }

    // Extract merchantId from standardized BT name
    private fun parseMerchantId(btName: String): String? {
        if (!btName.startsWith("BlinknPay|MERCHANT|")) return null
        return btName.split("|").getOrNull(2)
    }





    // Fetch merchant name dynamically from Firestore
    private fun fetchMerchantName(merchantId: String, callback: (String) -> Unit) {
        FirebaseFirestore.getInstance()
            .collection("merchants")
            .document(merchantId)
            .get()
            .addOnSuccessListener { doc ->
                val merchantName = doc.getString("name") ?: "Unknown Store"
                callback(merchantName)
            }
            .addOnFailureListener {
                callback("Unknown Store")
            }
    }







    /**
     * Smooth distance using a moving average filter.
     */
    fun smoothClassicDistance(deviceAddress: String, newDistance: Double): Double {

        val history = distanceHistory.getOrPut(deviceAddress) { mutableListOf() }

        // Keep only the last N entries
        if (history.size >= DISTANCE_SMOOTH_WINDOW) {
            history.removeAt(0)
        }

        history.add(newDistance)

        return history.average()
    }








    // ------------------- Classic Bluetooth Scan -------------------
    private var isClassicReceiverRegistered = false

    private val classicBtReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {

                BluetoothDevice.ACTION_FOUND -> {
                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE)

                    val distance = calculateDistanceFromRSSI(rssi)

                    if (device != null && device.name != null) {

                        val merchantName = when {
                            device.name.contains("SRN", ignoreCase = true) -> "Serena Hotel"
                            device.name.contains("NVS", ignoreCase = true) -> "Naivas Supermarket"
                            else -> device.name
                        }

                        addUniqueReceiver(
                            Receiver(
                                businessName = merchantName,
                                category = "Merchant (Quantum BlinknPay)",
                                distance = distance
                            )
                        )
                    }
                }

                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    Log.d("CLASSIC", "⏹ Discovery finished → restarting…")

                    // Restart automatically for continuous scan
                    bluetoothAdapter?.startDiscovery()
                }
            }
        }
    }












    // ------------------- Wi-Fi Scan -------------------
// ------------------- Wi-Fi Scan (BlinknPay Merchant Detection) -------------------
    @SuppressLint("MissingPermission")
    private fun scanWifiSSIDs() {
        val wifiManager = requireContext().applicationContext
            .getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return

        val results = wifiManager.scanResults

        for (result in results) {
            val ssid = result.SSID ?: continue

            // 1️⃣ Parse merchant UUID from SSID
            val merchantUUID = parseMerchantIdFromSSID(ssid) ?: continue

            // 2️⃣ Check if merchant is registered
            val registered = BlinknPayApp.registeredMerchants[merchantUUID]
            if (registered == null) {
                Log.d("DISCOVERY", "⛔ Ignored unregistered Wi-Fi merchant: $ssid")
                continue
            }

            // 3️⃣ Convert RSSI to estimated distance
            val distance = rssiToDistance(result.level)

            // 4️⃣ Process & show in UI
            processMerchant(
                registered.merchantName,
                "Wi-Fi",
                distance
            )

            Log.d("DISCOVERY", "📶 Wi-Fi Merchant Found: ${registered.merchantName} | RSSI=${result.level} | Approx=${distance}m")
        }
    }





    fun stopAdvertising() {
        if (!isAdvertising) return

        try {
            bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback)
            isAdvertising = false
            Log.d("BLE", "🛑 Advertising stopped.")
        } catch (e: Exception) {
            Log.e("BLE", "⚠️ Stop advertising error: ${e.message}")
        }
    }











    private var isClassicBtRegistered = false

    fun stopAllDiscovery() {
        stopAdvertising()

        // Stop BLE scan safely
        bluetoothLeScanner?.let { scanner ->
            try { scanner.stopScan(bleScanCallback) }
            catch (e: Exception) { Log.w("DISCOVERY", "BLE scan stop error: ${e.message}") }
        }

        // Unregister Classic BT receiver safely
        if (isClassicBtRegistered) {
            try { requireContext().unregisterReceiver(classicBtReceiver) }
            catch (e: Exception) { Log.w("DISCOVERY", "Classic BT receiver unregister error: ${e.message}") }
            isClassicBtRegistered = false
        }
    }












    override fun onDestroyView() {
        super.onDestroyView()
        stopAllDiscovery()
        handler.removeCallbacksAndMessages(null)
    }













    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_OK) {
            setupBluetoothComponents()
        }
    }















































    @RequiresApi(Build.VERSION_CODES.M)
    override fun onResume() {

        super.onResume()

        val locationManager = requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val bluetoothManager = requireContext().getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter

        // 🔹 Check Bluetooth
        if (bluetoothAdapter == null) {
            Toast.makeText(requireContext(), "❌ Bluetooth not supported", Toast.LENGTH_LONG).show()
            return
        }

        if (!bluetoothAdapter.isEnabled) {
            promptEnableBluetooth()
            return
        }

        // 🔹 Force GPS on (even if user turned it off mid-session)
        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            Log.d("GPS", "⚠️ GPS is OFF, prompting user...")
            forceEnableGPS()
            return
        }

        // ✅ All good → proceed
        setupBluetoothComponents()
    }






    override fun onPause() {
        super.onPause()
        stopContinuousScan()

    }












}












