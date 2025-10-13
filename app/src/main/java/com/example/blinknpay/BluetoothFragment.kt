package com.example.blinkpay

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.*
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.*
import android.widget.*
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import java.text.NumberFormat
import com.example.blinknpay.R
import androidx.appcompat.app.AlertDialog
import android.bluetooth.BluetoothDevice
import java.util.UUID
import com.example.blinknpay.Receiver
import java.util.*
import java.util.Locale
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.provider.Settings

import android.annotation.SuppressLint
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.switchmaterial.SwitchMaterial
import android.net.wifi.WifiManager

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import androidx.core.app.ActivityCompat




class BluetoothFragment : Fragment(), VoiceInputDialog.VoiceResultListener {

    private lateinit var enableBluetoothLauncher: ActivityResultLauncher<Intent>



    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private var bluetoothLeAdvertiser: BluetoothLeAdvertiser? = null

    private var bluetoothAdapter: BluetoothAdapter? = null

    private val handler = Handler(Looper.getMainLooper())
    private var isAdvertising = false

    private lateinit var amountInput: EditText
    private lateinit var voiceButton: ImageButton

    private lateinit var listView: ListView
    private lateinit var payButton: Button

    private lateinit var receiverAdapter: ReceiverAdapter
    private val scanResults = mutableListOf<Receiver>()
    private lateinit var currentMerchantText: TextView





    private lateinit var modeSwitch: SwitchMaterial



    private var advertiseCallback: AdvertiseCallback? = null


    private var foundMerchant = false

    private val PERMISSION_REQUEST_CODE = 1001
    private val MIC_PERMISSION_REQUEST = 2001



    private var isScanning = false

    private lateinit var merchantAdapter: MerchantAdapter

    private val PREFS_NAME = "BlinkModePrefs"
    private val KEY_MODE = "isMerchantMode"


    private val merchantUUID: UUID = UUID.fromString("323e4567-e89b-12d3-a456-426614174002")

    private var isListeningUltrasound = false
    private var audioRecord: AudioRecord? = null
    private var detectThread: Thread? = null



    // Place this at the top of the fragment, near other private vals

    companion object {
        private const val REQUEST_ENABLE_BT = 1001
        private const val PERMISSION_REQUEST_CODE = 101
    }



    // Extension property to map merchant codes (e.g., "SRN", "NVS") to readable names
    val String.businessName: String
        get() = when (this.toUpperCase(Locale.ROOT)) {
            "SRN" -> "Serena Hotel"
            "NVS" -> "Naivas Supermarket"
            else -> "Unknown Merchant"
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










    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.bluetooth_fragment, container, false)
    }












    @RequiresApi(Build.VERSION_CODES.M)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 🔗 Bind UI components
        amountInput = view.findViewById(R.id.amountInput)
        payButton = view.findViewById(R.id.payButton)
        listView = view.findViewById(R.id.receiverList)
        currentMerchantText = view.findViewById(R.id.currentMerchantText)
        modeSwitch = view.findViewById(R.id.modeSwitch)
        val amountInputLayout = view.findViewById<TextInputLayout>(R.id.amountInputLayout)

        // Set up list adapter
        receiverAdapter = ReceiverAdapter(requireContext(), scanResults)
        listView.adapter = receiverAdapter

        // Format amount input (currency formatter)
        setupCurrencyFormatter()

        // Handle merchant click from list
        listView.setOnItemClickListener { _, _, position, _ ->
            val selectedMerchant = scanResults[position].businessName
            showPaymentPrompt(selectedMerchant)
        }

        // 🎤 Handle mic icon click
        amountInputLayout.setEndIconOnClickListener {
            startVoiceInput()
        }

        // ✅ Initialize Bluetooth launcher
        enableBluetoothLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                Log.d("BLE", "Bluetooth enabled")
                initBluetooth()
            } else {
                Toast.makeText(requireContext(), "Bluetooth is required", Toast.LENGTH_SHORT).show()
            }
        }

        // ✅ Mode Switch Toggle
        modeSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                currentMerchantText.text = "Status: Merchant Mode (Ready to Receive Payments)"
                if (checkBluetoothReady()) startMerchantBroadcast()
            } else {
                currentMerchantText.text = "Status: Customer Mode (Scanning for Merchants)"
                stopMerchantBroadcast()
                if (checkBluetoothReady()) startAdaptiveDiscovery()
            }
        }

        // Enable Bluetooth on startup if not ready
        if (!checkBluetoothReady()) {
            promptEnableBluetooth()
        }


        checkPermissionsAndInitBluetooth()

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
                    val nearest = scanResults.minByOrNull { it.distance ?: Int.MAX_VALUE }

                    nearestMerchant = nearest?.businessName
                    nearest?.let {
                        currentMerchantText.text = "Nearest: ${it.businessName} — Tap Pay Now"
                        payButton.isEnabled = true
                    }
                } else {
                    payButton.isEnabled = false
                    currentMerchantText.text = "Searching for nearby merchants..."
                }
                Handler(Looper.getMainLooper()).postDelayed(this, 5000)
            }
        }, 5000)
    }







    private fun showPaymentPrompt(merchantName: String) {
        val rawInput = amountInput.text.toString().trim()

        // Regex to remove "KSh", commas, spaces, or any non-numeric except decimal
        val clean = rawInput.replace("[^\\d.]".toRegex(), "")
        val amountValue = clean.toDoubleOrNull() ?: 0.0

        val currencyFormatter = NumberFormat.getCurrencyInstance(Locale("en", "KE"))
        currencyFormatter.currency = java.util.Currency.getInstance("KES")

        if (amountValue > 0.0) {
            val formatted = currencyFormatter.format(amountValue)

            AlertDialog.Builder(requireContext())
                .setTitle("Pay $merchantName")
                .setMessage("Do you want to pay $formatted to $merchantName?")
                .setPositiveButton("Pay") { _, _ ->
                    Toast.makeText(
                        requireContext(),
                        "Payment of $formatted sent to $merchantName",
                        Toast.LENGTH_SHORT
                    ).show()

                    // 🔗 Call M-Pesa STK Push here with `amountValue`

                    amountInput.text?.clear()
                }
                .setNegativeButton("Cancel", null)
                .show()
        } else {
            // Fallback if no amount typed in the main field
            val input = EditText(requireContext()).apply {
                hint = "Enter amount (KSh)"
                inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                        android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
                setPadding(32, 32, 32, 32)
            }

            val dialog = AlertDialog.Builder(requireContext())
                .setTitle("Pay $merchantName")
                .setView(input)
                .setPositiveButton("Pay", null)
                .setNegativeButton("Cancel", null)
                .create()

            dialog.setOnShowListener {
                val button = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                button.setOnClickListener {
                    val typed = input.text.toString().trim()
                    val cleanDialog = typed.replace("[^\\d.]".toRegex(), "")
                    val dialogAmount = cleanDialog.toDoubleOrNull() ?: 0.0

                    if (dialogAmount <= 0.0) {
                        input.error = "Please enter a valid amount"
                    } else {
                        val formatted = currencyFormatter.format(dialogAmount)

                        Toast.makeText(
                            requireContext(),
                            "Payment of $formatted sent to $merchantName",
                            Toast.LENGTH_SHORT
                        ).show()

                        // 🔗 Call M-Pesa STK Push here with `dialogAmount`

                        amountInput.text?.clear()
                        dialog.dismiss()
                    }
                }
            }

            dialog.show()
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
            initBluetooth()
        } else {
            // 👇 PLACE THIS LINE HERE
            requestPermissions(permissionsNeeded.toTypedArray(), PERMISSION_REQUEST_CODE)
        }
    }




    @SuppressLint("MissingPermission")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {

            // 🎤 Handle microphone permission (for voice input)
            MIC_PERMISSION_REQUEST -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    VoiceInputDialog().show(parentFragmentManager, "VoiceDialog")
                } else {
                    Toast.makeText(
                        requireContext(),
                        "Microphone permission is required for voice input.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            // 🔵 Handle Bluetooth, Wi-Fi, and Location permissions (for discovery)
            PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    Toast.makeText(requireContext(), "All permissions granted ✅", Toast.LENGTH_SHORT).show()
                    initBluetooth()
                    startUniversalDiscovery() // optional: start scanning immediately
                } else {
                    Toast.makeText(
                        requireContext(),
                        "Permissions denied ⚠️ — Bluetooth and discovery features may not work.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

            else -> {
                Log.w("PERMISSIONS", "Unhandled permission request code: $requestCode")
            }
        }
    }





















    private fun initBluetooth() {
        val bluetoothManager =
            requireContext().getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager

        bluetoothAdapter = bluetoothManager.adapter ?: run {
            Toast.makeText(requireContext(), "Bluetooth not supported", Toast.LENGTH_LONG).show()
            return
        }

        if (bluetoothAdapter?.isEnabled == false) {
            promptEnableBluetooth()
        } else {
            setupBluetoothComponents()
        }
    }

    private fun promptEnableBluetooth() {
        if (bluetoothAdapter?.isEnabled == true) {
            setupBluetoothComponents()
        } else {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            enableBluetoothLauncher.launch(enableBtIntent)
        }
    }








    @SuppressLint("MissingPermission")
    private fun setupBluetoothComponents() {
        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter == null) {
            Toast.makeText(requireContext(), "❌ Bluetooth not available on this device", Toast.LENGTH_LONG).show()
            return
        }

        bluetoothLeScanner = adapter.bluetoothLeScanner
        bluetoothLeAdvertiser = adapter.bluetoothLeAdvertiser

        if (bluetoothLeAdvertiser == null) {
            Toast.makeText(requireContext(), "⚠️ BLE Advertising not supported on this device", Toast.LENGTH_LONG).show()
            return
        }

        // ✅ Now start advertising and discovery safely
        startAdvertising()
        startUniversalDiscovery()
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

        // 🔹 State variables
        var currentMerchantIndex = 0
        var isAdvertising = false
        var advertiseCallback: AdvertiseCallback? = null
        val handler = Handler(Looper.getMainLooper())
        val adapter = BluetoothAdapter.getDefaultAdapter()

        // 🔹 Merchant definitions (replace with real objects if needed)
        val merchants = listOf("SRN", "NVS")

        // Helper to safely get businessName (for now using the string itself)
        fun String.businessName(): String = this // Replace with actual mapping if needed

        // 🔹 Check Bluetooth availability
        if (adapter == null || !adapter.isEnabled) {
            Log.w("ADAPTIVE", "⚠️ Bluetooth off or unsupported → using Wi-Fi fallback.")

            val currentCode = merchants.getOrNull(currentMerchantIndex)
            if (currentCode != null) {
                startWifiSSIDBroadcast(context, currentCode)
            } else {
                Toast.makeText(context, "No merchant available for Wi-Fi broadcast!", Toast.LENGTH_SHORT).show()
            }
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
                    // 1️⃣ Stop current advertising
                    if (isAdvertising) {
                        stopAdvertising()
                        Log.d("BLE", "🛑 Stopped current advertising for rotation.")
                    }

                    // 2️⃣ Get current merchant
                    val code = merchants[currentMerchantIndex]
                    val name = code.businessName()

                    Log.d("DEBUG", "Advertising merchant: $name")

                    // 3️⃣ Prepare BLE advertising data
                    val advertiseData = AdvertiseData.Builder()
                        .addServiceData(ParcelUuid(merchantUUID), code.toByteArray(Charsets.UTF_8))
                        .setIncludeDeviceName(false)
                        .build()

                    val settings = AdvertiseSettings.Builder()
                        .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                        .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                        .setConnectable(false)
                        .build()

                    // 4️⃣ Define AdvertiseCallback
                    advertiseCallback = object : AdvertiseCallback() {
                        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                            super.onStartSuccess(settingsInEffect)
                            isAdvertising = true
                            Log.d("BLE", "✅ BLE Advertising started: $name ($code)")
                        }

                        override fun onStartFailure(errorCode: Int) {
                            super.onStartFailure(errorCode)
                            isAdvertising = false
                            Log.e("BLE", "❌ BLE advertising failed for $name ($errorCode), fallback triggered.")
                            handleFallback(context, code, name, errorCode)
                        }
                    }

                    // 5️⃣ Start advertising after 1.5s delay
                    handler.postDelayed({
                        try {
                            bluetoothLeAdvertiser.startAdvertising(settings, advertiseData, advertiseCallback)
                            isAdvertising = true
                            Log.d("BLE", "📢 BLE advertising initiated for $name ($code)")

                            // Update UI safely
                            (context as? Activity)?.runOnUiThread {
                                currentMerchantText.text = "Advertising: $name ($code)"
                            }
                        } catch (e: Exception) {
                            Log.e("BLE", "⚠️ Failed to start BLE advertising: ${e.message}")
                            isAdvertising = false
                            handleFallback(context, code, name, -1)
                        }
                    }, 1500)

                    // 6️⃣ Move to next merchant
                    currentMerchantIndex = (currentMerchantIndex + 1) % merchants.size

                    // 7️⃣ Schedule next rotation (every 10s)
                    handler.postDelayed(this, 10_000)

                } catch (e: Exception) {
                    Log.e("BLE", "❌ Error in rotateNextMerchant loop: ${e.message}")
                }
            }
        }

        // 🔹 Start rotation loop
        handler.post(rotateRunnable)
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

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(requireActivity(), arrayOf(Manifest.permission.RECORD_AUDIO), 1001)
            return
        }

        if (!isListeningUltrasound) {
            isListeningUltrasound = true
            detectUltrasoundTone()
        }

        // Simulate your device as a merchant beacon (emitting ultrasound)
        playUltrasoundPing()
    }

    private fun playUltrasoundPing() {
        Thread {
            try {
                val sampleRate = 44100
                val freq = 19000.0 // 19 kHz ultrasound
                val durationSec = 1.0
                val numSamples = (durationSec * sampleRate).toInt()
                val samples = ShortArray(numSamples)

                for (i in samples.indices) {
                    samples[i] = (Math.sin(2.0 * Math.PI * i * freq / sampleRate) * Short.MAX_VALUE).toInt().toShort()
                }

                val audioTrack = AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    samples.size * 2,
                    AudioTrack.MODE_STATIC
                )
                audioTrack.write(samples, 0, samples.size)
                audioTrack.play()

                Log.d("ULTRASOUND", "📡 Ultrasound ping emitted at $freq Hz")

                Thread.sleep(1000)
                audioTrack.release()
            } catch (e: Exception) {
                Log.e("ULTRASOUND", "❌ Error emitting ultrasound: ${e.message}")
            }
        }.start()
    }

    private fun detectUltrasoundTone() {
        detectThread = Thread {
            try {
                val sampleRate = 44100
                val bufferSize = AudioRecord.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
                )

                val buffer = ShortArray(bufferSize)
                audioRecord?.startRecording()

                Log.d("ULTRASOUND", "👂 Listening for ultrasound tones...")

                while (isListeningUltrasound) {
                    val read = audioRecord?.read(buffer, 0, bufferSize) ?: 0
                    if (read > 0 && detect19kHzTone(buffer, read, sampleRate)) {
                        Handler(Looper.getMainLooper()).post {
                            Log.d("ULTRASOUND", "✅ Merchant ultrasound beacon detected!")
                            Toast.makeText(requireContext(), "Merchant detected via Ultrasound!", Toast.LENGTH_SHORT).show()
                            // Add to your merchant list
                        }
                        break
                    }
                }

                audioRecord?.stop()
                audioRecord?.release()
            } catch (e: Exception) {
                Log.e("ULTRASOUND", "❌ Error detecting ultrasound: ${e.message}")
            }
        }
        detectThread?.start()
    }

    private fun detect19kHzTone(buffer: ShortArray, read: Int, sampleRate: Int): Boolean {
        // Simple frequency detection using zero-crossing method
        var crossings = 0
        for (i in 1 until read) {
            if ((buffer[i - 1] >= 0 && buffer[i] < 0) || (buffer[i - 1] < 0 && buffer[i] >= 0)) {
                crossings++
            }
        }
        val freq = crossings * sampleRate / (2.0 * read)
        return freq in 18500.0..19500.0
    }

    private fun stopUltrasoundDiscovery() {
        isListeningUltrasound = false
        detectThread?.interrupt()
        audioRecord?.release()
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
        val scanInterval = 30_000L   // 60s per scan cycle
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
            .setServiceUuid(ParcelUuid(merchantUUID))
            .build()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        bluetoothLeScanner?.startScan(listOf(filter), settings, bleScanCallback)
        isScanning = true
        Log.d("SCAN", "📡 BLE scanning started...")

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































    private fun startUniversalDiscovery() {
        Log.d("DISCOVERY", "🚀 Starting universal merchant discovery...")

        // Clear existing results before scanning
        scanResults.clear()
        receiverAdapter.notifyDataSetChanged()

        // Start all available discovery modes in parallel
        startBleDiscovery()
        startClassicBluetoothDiscovery()
        startWifiSSIDDiscovery()
        startUltrasoundDiscovery() // 👈 added
        startNearestMerchantWatcher()
    }















    private fun addUniqueReceiver(receiver: Receiver) {
        val exists = scanResults.any { it.businessName.equals(receiver.businessName, ignoreCase = true) }
        if (!exists) {
            activity?.runOnUiThread {
                scanResults.add(receiver)
                receiverAdapter.notifyDataSetChanged()
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

    // --- 2. Classic Bluetooth Discovery ---
    private fun startClassicBluetoothDiscovery() {
        val adapter = bluetoothAdapter ?: return
        if (!adapter.isEnabled) {
            Log.w("DISCOVERY", "⚠️ Classic Bluetooth is off.")
            return
        }

        val btReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                        device?.name?.let { name ->
                            val merchantName = when {
                                name.contains("SRN", ignoreCase = true) -> "Serena Hotel"
                                name.contains("NVS", ignoreCase = true) -> "Naivas Supermarket"
                                else -> name
                            }

                            val receiver = Receiver(
                                businessName = merchantName,
                                category = "Merchant (Classic BT)",
                                distance = 1// placeholder RSSI
                            )

                            addUniqueReceiver(receiver)
                            Log.d("DISCOVERY", "🔸 Found Classic BT merchant: $merchantName")
                        }
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                        Log.d("DISCOVERY", "✅ Classic Bluetooth discovery finished.")
                        try {
                            requireContext().unregisterReceiver(this)
                        } catch (_: Exception) { }
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        requireContext().registerReceiver(btReceiver, filter)

        adapter.startDiscovery()
    }

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

                val merchantName = when {
                    ssid.contains("SRN", ignoreCase = true) -> "Serena Hotel"
                    ssid.contains("NVS", ignoreCase = true) -> "Naivas Supermarket"
                    else -> null
                }

                merchantName?.let {
                    val receiver = Receiver(
                        businessName = it,
                        category = "Merchant (Wi-Fi)",
                        distance = result.level // RSSI
                    )

                    addUniqueReceiver(receiver)
                    Log.d("DISCOVERY", "📡 Found Wi-Fi SSID merchant: $it (RSSI ${result.level})")
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

                        processMerchant(merchantName, "BLE", r.rssi)
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

    private fun processMerchant(merchantName: String, source: String, distance: Int) {
        val uniqueKey = "${merchantName}_$source"
        if (discoveredMerchants.add(uniqueKey)) {
            val receiver = Receiver(
                businessName = merchantName,
                category = "Merchant ($source)",
                distance = distance
            )
            activity?.runOnUiThread {
                scanResults.add(receiver)
                receiverAdapter.notifyDataSetChanged()
            }
            Log.d("DISCOVERY", "✅ Found $merchantName via $source (RSSI: $distance)")
        }
    }






    // ------------------- Classic Bluetooth Scan -------------------
    private val classicBtReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (BluetoothDevice.ACTION_FOUND == intent.action) {
                val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                device?.name?.let { name ->
                    val merchantName = when {
                        name.contains("SRN", ignoreCase = true) -> "Serena Hotel"
                        name.contains("NVS", ignoreCase = true) -> "Naivas Supermarket"
                        else -> name
                    }
                    processMerchant(merchantName, "Classic BT", -60) // dummy RSSI
                }
            }
        }
    }

    // ------------------- Wi-Fi Scan -------------------
    @SuppressLint("MissingPermission")
    private fun scanWifiSSIDs() {
        val wifiManager = requireContext().applicationContext
            .getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return

        val results = wifiManager.scanResults
        for (result in results) {
            val ssid = result.SSID
            val merchantName = when {
                ssid.contains("SRN", ignoreCase = true) -> "Serena Hotel"
                ssid.contains("NVS", ignoreCase = true) -> "Naivas Supermarket"
                else -> null
            }
            merchantName?.let {
                processMerchant(it, "Wi-Fi", result.level)
            }
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




    private fun startVoiceInput() {
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), MIC_PERMISSION_REQUEST)
        } else {
            val dialog = VoiceInputDialog()
            dialog.setVoiceResultListener(this)   // ✅ Explicitly set listener
            dialog.show(parentFragmentManager, "VoiceDialog")
        }
    }








    private fun setupCurrencyFormatter() {
        val currencyFormatter = NumberFormat.getCurrencyInstance(Locale("en", "KE"))
        currencyFormatter.currency = Currency.getInstance("KES")

        amountInput.addTextChangedListener(object : TextWatcher {
            private var current = ""

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                if (s.toString() != current) {
                    amountInput.removeTextChangedListener(this)

                    try {
                        // Remove everything except digits
                        val clean = s.toString().replace("[^\\d]".toRegex(), "")

                        if (clean.isNotEmpty()) {
                            // Divide by 100 to allow decimal entry
                            val parsed = clean.toDouble() / 100
                            val formatted = currencyFormatter.format(parsed)

                            current = formatted
                            amountInput.setText(formatted)
                            amountInput.setSelection(formatted.length)
                        } else {
                            current = ""
                            amountInput.setText("")
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    amountInput.addTextChangedListener(this)
                }
            }
        })
    }








    override fun onVoiceResult(text: String) {
        val spokenAmount = text.replace(Regex("[^\\d.]"), "")
        val parsed = spokenAmount.toDoubleOrNull() ?: 0.0
        val formatted = NumberFormat.getCurrencyInstance(Locale.getDefault()).format(parsed)
        amountInput.setText(formatted)
        amountInput.setSelection(formatted.length)
    }














}












