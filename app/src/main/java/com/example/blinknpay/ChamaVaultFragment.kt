package com.example.blinknpay.ui

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.blinknpay.R
import com.example.blinknpay.api.RetrofitClient
import com.example.blinknpay.databinding.FragmentChamaVaultBinding
import com.example.blinknpay.databinding.ViewSignatoryOrbBinding
import com.example.blinknpay.engine.BlinkEngine
import com.example.blinknpay.models.SignatoryRole
import com.example.blinknpay.utils.VaultManager
import kotlinx.coroutines.launch
import com.example.blinknpay.BuildConfig


/**
 * High-Security Vault for Chama savings.
 * Features: Live Currency Exchange (USD, GBP, CAD, EUR) & Quad-Channel Auth.
 */
class ChamaVaultFragment : Fragment(), BlinkEngine.RpidListener {

    private var _binding: FragmentChamaVaultBinding? = null
    private val binding get() = _binding!!

    private val currentUserRole = SignatoryRole.TREASURER
    private val detectedSignatories = mutableSetOf<SignatoryRole>()
    private val SECURITY_THRESHOLD = 50000.0
    private var currentPendingAmount = 65000.0

    // API State
    private var liveRates: Map<String, Double>? = null
    private val baseBalanceKES = 1240500.0 // Source: Unified Payment Interface

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentChamaVaultBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupVaultUI()
        fetchLiveExchangeRates()

        if (SignatoryRole.isSignatory(currentUserRole)) {
            checkActivePayouts()
        }
    }

    private fun setupVaultUI() {
        // Initial Title (Overrides "Transaction History" if NavGraph label fails)
        (activity as? androidx.appcompat.app.AppCompatActivity)?.supportActionBar?.title = "Chama Yetu"

        // Set Default Balance View
        binding.tvTotalBalance.text = VaultManager.formatCurrency(baseBalanceKES, "KES")

        // Chip Listeners for Currency Switching
        binding.chipKes.setOnClickListener { updateEquivalentDisplay("KES") }
        binding.chipUsd.setOnClickListener { updateEquivalentDisplay("USD") }
        binding.chipGbp.setOnClickListener { updateEquivalentDisplay("GBP") }
        binding.chipCad.setOnClickListener { updateEquivalentDisplay("CAD") }
        binding.chipEur.setOnClickListener { updateEquivalentDisplay("EUR") }

        binding.btnBlinkAuthorize.setOnClickListener {
            initiateFinalAuthorization()
        }
    }


    private fun fetchLiveExchangeRates() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // 🔑 Using BuildConfig keeps your key out of this file
                val response = RetrofitClient.instance.getLatestKesRates(BuildConfig.EXCHANGE_API_KEY)

                if (response.isSuccessful && response.body()?.result == "success") {
                    liveRates = response.body()?.conversion_rates
                    updateEquivalentDisplay("USD") // Default view
                }
            } catch (e: Exception) {
                binding.tvHardEquivalent.text = "Offline Mode"
            }
        }
    }





    private fun updateEquivalentDisplay(currencyCode: String) {
        if (currencyCode == "KES") {
            binding.tvHardEquivalent.text = "Base Currency"
            return
        }

        val rate = liveRates?.get(currencyCode) ?: 1.0
        val converted = baseBalanceKES * rate

        val symbol = when (currencyCode) {
            "USD" -> "$"
            "GBP" -> "£"
            "CAD" -> "C$"
            "EUR" -> "€"
            else -> ""
        }

        binding.tvHardEquivalent.text = "≈ $symbol ${String.format("%,.2f", converted)}"
    }

    private fun checkActivePayouts() {
        if (currentPendingAmount > 0) {
            binding.cardActiveAuthorization.visibility = View.VISIBLE
            binding.tvAuthAmount.text = "KES ${String.format("%,.2f", currentPendingAmount)}"

            val requiredText = if (currentPendingAmount >= SECURITY_THRESHOLD) "3/3 Physical" else "2/3 Remote"
            binding.tvQuorumBadge.text = requiredText

            BlinkEngine.startDiscovery(viewLifecycleOwner, currentPendingAmount, this)
        }
    }

    // --- BlinkEngine.RpidListener ---

    override fun onSignatoryDetected(scannedRpid: String) {
        // 1. Resolve the RPID to a Role using the BlinknPay logic
        val role = SignatoryRole.fromRpid(scannedRpid) ?: return

        // 2. Switch to UI thread to update the signatory Orbs
        activity?.runOnUiThread {
            // Safe check for ViewBinding in AS 4.1.3
            if (_binding != null) {
                when (role) {
                    SignatoryRole.CHAIR -> {
                        handleOrbActivation(binding.sigChairperson, role)
                    }
                    SignatoryRole.TREASURER -> {
                        handleOrbActivation(binding.sigTreasurer, role)
                    }
                    SignatoryRole.SECRETARY_GENERAL -> {
                        handleOrbActivation(binding.sigSecretary, role)
                    }
                    // Exhaustive check for older Kotlin versions
                    else -> {
                        // Log or handle unexpected roles if necessary
                    }
                }
            }
        }
    }






    override fun onDiscoveryStatusChanged(isActive: Boolean) {
        activity?.runOnUiThread {
            _binding?.tvQuorumStatus?.text = if (isActive) {
                "Quad-Channel Active: Monitoring RPIDs..."
            } else {
                "Engine Offline"
            }
        }
    }

    private fun handleOrbActivation(orbBinding: ViewSignatoryOrbBinding, role: SignatoryRole) {
        if (detectedSignatories.add(role)) {
            orbBinding.presenceGlow.visibility = View.VISIBLE
            val pulse = AlphaAnimation(0.3f, 1.0f).apply {
                duration = 1000
                repeatMode = Animation.REVERSE
                repeatCount = Animation.INFINITE
            }
            orbBinding.presenceGlow.startAnimation(pulse)
            orbBinding.ivVerifiedBadge.visibility = View.VISIBLE
            orbBinding.ivSignatory.strokeColor = ColorStateList.valueOf(Color.parseColor("#6CAF10"))

            updateQuorumProgress()
        }
    }

    private fun updateQuorumProgress() {
        val count = detectedSignatories.size
        val required = if (currentPendingAmount >= SECURITY_THRESHOLD) 3 else 2
        binding.tvQuorumStatus.text = "$count/$required Signatories Verified"

        if (count >= required) {
            binding.tvQuorumStatus.setTextColor(Color.parseColor("#6CAF10"))
            binding.tvQuorumStatus.text = "QUORUM REACHED: READY TO BLINK"

            binding.btnBlinkAuthorize.apply {
                isEnabled = true
                alpha = 1.0f
                animate().scaleX(1.05f).scaleY(1.05f).setDuration(300).start()
            }
        }
    }

    private fun initiateFinalAuthorization() {
        val bundle = Bundle().apply {
            putFloat("amount", currentPendingAmount.toFloat())
        }
        findNavController().navigate(R.id.action_vault_to_approval, bundle)
    }


    private fun updateCurrencyUI(apiKey: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            val response = RetrofitClient.instance.getLatestKesRates(apiKey)

            if (response.isSuccessful && response.body() != null) {
                val rates = response.body()!!.conversion_rates
                val usdRate = rates["USD"] ?: 0.0

                // Assuming your KES balance is 1,240,500.00 from your XML
                val kesBalance = 1240500.00
                val usdEquivalent = kesBalance * usdRate

                binding.tvHardEquivalent.text = "≈ $ ${String.format("%.2f", usdEquivalent)}"
            }
        }
    }






    override fun onDestroyView() {
        super.onDestroyView()
        BlinkEngine.stopDiscovery()
        _binding = null
    }
}