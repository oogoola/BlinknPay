package com.example.blinknpay

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.blinknpay.databinding.FragmentQrScannerBinding
import com.google.mlkit.vision.barcode.Barcode
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

// Data class to hold parsed QR information
data class ParsedQrData(
    val paybill: String? = null,
    val till: String? = null,
    val phone: String? = null,
    val account: String? = null,
    val merchantName: String? = null,
    val amount: Double? = null
)

class QrScannerFragment : Fragment() {

    private var _binding: FragmentQrScannerBinding? = null
    private val binding get() = _binding!!

    private lateinit var cameraExecutor: ExecutorService
    private lateinit var scanner: BarcodeScanner
    private var scanned = false

    companion object {
        private const val TAG = "QrScannerFragment"
        private const val CAMERA_PERMISSION_REQUEST = 1001
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentQrScannerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Toolbar back navigation
        binding.toolbarQr.setNavigationOnClickListener { findNavController().popBackStack() }

        // Camera executor
        cameraExecutor = Executors.newSingleThreadExecutor()

        // ML Kit QR scanner
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
        scanner = BarcodeScanning.getClient(options)

        // Check permissions
        if (allPermissionsGranted()) startCamera()
        else ActivityCompat.requestPermissions(
            requireActivity(),
            arrayOf(Manifest.permission.CAMERA),
            CAMERA_PERMISSION_REQUEST
        )
    }

    private fun allPermissionsGranted() =
        ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
            }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build().also { analysis ->
                    analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                        processImageProxy(imageProxy)
                    }
                }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(viewLifecycleOwner, cameraSelector, preview, imageAnalysis)
            } catch (e: Exception) {
                Log.e(TAG, "Use case binding failed", e)
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    // STK Push trigger
    private fun initiateStkPush(payNumber: String, amount: String, account: String) {
        Toast.makeText(requireContext(), "Processing payment...", Toast.LENGTH_SHORT).show()

        MpesaApi.stkPush(
            businessShortCode = payNumber,
            accountReference = account,
            amount = amount,
            phoneNumber = UserSession.phoneNumber, // Logged-in user phone
            callback = { success, message ->
                if (success) Toast.makeText(requireContext(), "STK push sent!", Toast.LENGTH_LONG).show()
                else Toast.makeText(requireContext(), "Payment failed: $message", Toast.LENGTH_LONG).show()
            }
        )
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun processImageProxy(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

        if (scanned) {
            imageProxy.close()
            return
        }

        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                val qrValue = barcodes.firstOrNull()?.rawValue
                qrValue?.let {
                    scanned = true
                    Log.d(TAG, "QR Code scanned: $it")

                    val parsed = parsePaymentQr(it)

                    when {
                        parsed.phone != null -> {
                            initiateStkPush(payNumber = parsed.phone, amount = "1", account = "QR-PAY")
                        }
                        parsed.paybill != null -> {
                            initiateStkPush(payNumber = parsed.paybill, amount = parsed.amount?.toString() ?: "1", account = parsed.account ?: "Account")
                        }
                        parsed.till != null -> {
                            initiateStkPush(payNumber = parsed.till, amount = parsed.amount?.toString() ?: "1", account = "TILL")
                        }
                        else -> {
                            Toast.makeText(requireContext(), "Invalid payment QR", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
            .addOnFailureListener { e -> Log.e(TAG, "Barcode scanning failed", e) }
            .addOnCompleteListener { imageProxy.close() }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) startCamera()
            else Toast.makeText(requireContext(), "Camera permission is required to scan QR codes", Toast.LENGTH_LONG).show()
        }
    }

    // Parse EMVCO / Safaricom QR + simple phone/paybill/till
    private fun parsePaymentQr(qr: String): ParsedQrData {
        try {
            // Remove any whitespace
            val cleanedQr = qr.trim()

            // 1️⃣ Phone number (Kenya format)
            if (cleanedQr.matches(Regex("^2547\\d{8}\$"))) {
                return ParsedQrData(phone = cleanedQr)
            }

            // 2️⃣ Simple Paybill/Till (5–7 digits)
            if (cleanedQr.matches(Regex("^\\d{5,7}\$"))) {
                return ParsedQrData(paybill = cleanedQr, account = "Account")
            }

            // 3️⃣ EMV Merchant QR parsing
            var paybill: String? = null
            var account: String? = null
            var till: String? = null
            var merchantName: String? = null
            var amount: Double? = null

            // Merchant Name: Tag 59
            val tag59 = Regex("59(\\d{2})(.*?)\\d{0,}").find(cleanedQr)
            if (tag59 != null) merchantName = tag59.groupValues[2].trim()

            // Amount: Tag 54
            val tag54 = Regex("54(\\d{2})(\\d+(\\.\\d+)?)").find(cleanedQr)
            if (tag54 != null) amount = tag54.groupValues[2].toDoubleOrNull()

            // Paybill: pattern 0215 + 6 digits for paybill + account
            val paybillRegex = Regex("0215(\\d{6})(\\d+)")
            val paybillMatch = paybillRegex.find(cleanedQr)
            if (paybillMatch != null) {
                paybill = paybillMatch.groupValues[1]
                account = paybillMatch.groupValues[2]
            }

            // Till: pattern 010211 + till number
            val tillRegex = Regex("010211(\\d+)")
            val tillMatch = tillRegex.find(cleanedQr)
            if (tillMatch != null) till = tillMatch.groupValues[1]

            // 4️⃣ Fallback: any numeric sequence
            if (paybill == null && till == null && cleanedQr.all { it.isDigit() }) {
                paybill = cleanedQr
                account = "Account"
            }

            return ParsedQrData(
                paybill = paybill,
                till = till,
                phone = null,
                account = account,
                merchantName = merchantName,
                amount = amount
            )

        } catch (e: Exception) {
            // Fallback empty data on parsing error
            return ParsedQrData()
        }
    }






    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        cameraExecutor.shutdown()
        scanner.close()
    }
}
