package com.example.blinknpay.ui

import android.os.Bundle
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.blinknpay.R
import com.example.blinknpay.databinding.FragmentServicesBinding

/**
 * Main Services Portal for BlinknPay.
 * Orchestrates navigation between Quad-Channel enabled modules.
 */
class ServicesFragment : Fragment(R.layout.fragment_services) {

    private var _binding: FragmentServicesBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentServicesBinding.bind(view)

        setupNavigation()

        // In a real scenario, this would observe the actual RPID engine state
        updateEngineStatus(isActive = true)
    }

    private fun setupNavigation() {
        // 1. Split Bill: Peer-to-peer proximity payments
        binding.catSplitBill.setOnClickListener {
            findNavController().navigate(R.id.action_services_to_splitBill)
        }

        // 2. Exchange: Currency portal with RPID trading logic
        binding.catExchange.setOnClickListener {
            findNavController().navigate(R.id.action_services_to_exchange)
        }

        // 3. Chama Yetu: The high-security multi-sig Group Vault
        binding.catChama.setOnClickListener {
            findNavController().navigate(R.id.action_services_to_chamaVault)
        }

        // 4. Promo Card: Direct access to Exchange Rates
        binding.promoCard.setOnClickListener {
            findNavController().navigate(R.id.action_services_to_exchange)
        }
    }

    /**
     * Updates the UI to show the quad-channel engine status.
     * Triggers a pulse animation to indicate the device is propagating its RPID.
     */
    private fun updateEngineStatus(isActive: Boolean) {
        if (isActive) {
            binding.promoCard.alpha = 1.0f

            // Create a "Live" pulse effect for the hardware engine status
            val pulseAnimation = AlphaAnimation(0.6f, 1.0f).apply {
                duration = 1000
                repeatMode = Animation.REVERSE
                repeatCount = Animation.INFINITE
            }

            // This targets the 'Active' text section in your fragment_services.xml
            // If you have a specific ID for that text, replace promoCard with it.
            binding.promoCard.startAnimation(pulseAnimation)
        } else {
            binding.promoCard.clearAnimation()
            binding.promoCard.alpha = 0.5f
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Clear animations to prevent memory leaks/CPU cycles on an inactive fragment
        binding.promoCard.clearAnimation()
        _binding = null
    }
}