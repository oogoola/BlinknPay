package com.example.blinknpay

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.airbnb.lottie.LottieAnimationView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.tabs.TabLayout

class WithdrawOptionsBottomSheet : BottomSheetDialogFragment() {

    private lateinit var tabLayout: TabLayout
    private lateinit var containerWallet: View
    // FIX: Changed from FrameLayout to LinearLayout to match your XML
    private lateinit var containerAgent: LinearLayout
    private lateinit var tvWalletBalance: TextView

    // Nearby Agent UI
    private lateinit var radarAnimation: LottieAnimationView
    private lateinit var rvAgents: RecyclerView
    private lateinit var agentAdapter: AgentAdapter

    private val handler = Handler(Looper.getMainLooper())
    private var isDiscovering = false

    // =====================================================
    // Lifecycle
    // =====================================================

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        val view = inflater.inflate(
            R.layout.withdraw_options_sheet,
            container,
            false
        )

        tabLayout = view.findViewById(R.id.tabWithdrawModes)
        containerWallet = view.findViewById(R.id.containerWallet)
        containerAgent = view.findViewById(R.id.containerAgent)
        tvWalletBalance = view.findViewById(R.id.tvWalletBalance)

        // Ensure UI is set up before tab logic triggers discovery
        setupNearbyAgentUI()
        setupTabs()
        displayPassedData()

        return view
    }

    // =====================================================
    // Setup Nearby Agent UI
    // =====================================================

    private fun setupNearbyAgentUI() {
        if (containerAgent.childCount == 0) {
            val nearbyView = layoutInflater.inflate(
                R.layout.withdraw_nearby_agent,
                containerAgent,
                true
            )

            radarAnimation = nearbyView.findViewById(R.id.radarAnimation)
            rvAgents = nearbyView.findViewById(R.id.rvAgents)

            agentAdapter = AgentAdapter()
            rvAgents.layoutManager = LinearLayoutManager(requireContext())
            rvAgents.adapter = agentAdapter
            rvAgents.setHasFixedSize(true)
        }
    }

    // =====================================================
    // Display Passed Data
    // =====================================================

    private fun displayPassedData() {
        val rail = arguments?.getString("selectedRail") ?: "MPESA"
    }

    // =====================================================
    // Tabs Logic
    // =====================================================

    private fun setupTabs() {
        updateTabUI(0)

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                updateTabUI(tab?.position ?: 0)
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun updateTabUI(position: Int) {
        when (position) {
            0 -> { // FROM WALLET
                containerWallet.visibility = View.VISIBLE
                containerAgent.visibility = View.GONE
                stopDiscovery()
            }
            1 -> { // NEARBY AGENT
                containerWallet.visibility = View.GONE
                containerAgent.visibility = View.VISIBLE
                startDiscovery()
            }
        }
    }

    // =====================================================
    // Discovery Logic
    // =====================================================

    private fun startDiscovery() {
        // Ensure UI elements are actually created before proceeding
        if (containerAgent.childCount == 0) setupNearbyAgentUI()

        if (isDiscovering) return
        isDiscovering = true

        // Safety check for lateinit properties
        if (::radarAnimation.isInitialized) {
            radarAnimation.playAnimation()
        }

        if (::agentAdapter.isInitialized) {
            agentAdapter.clearAgents()
        }

        toggleEngine(true)
        simulateAgents()
    }

    private fun stopDiscovery() {
        if (!isDiscovering) return
        isDiscovering = false

        if (::radarAnimation.isInitialized) {
            radarAnimation.pauseAnimation()
        }

        handler.removeCallbacksAndMessages(null)
        toggleEngine(false)
    }

    // =====================================================
    // Simulation
    // =====================================================

    private fun simulateAgents() {
        handler.postDelayed({
            // Safety check: The user might have closed the sheet or switched tabs
            // during the 2-second delay
            if (::agentAdapter.isInitialized && isAdded) {
                val agents = listOf(
                    DiscoveredAgent("Mama Ken M-PESA Agent", "MPESA", 120, 4),
                    DiscoveredAgent("Wiko M-PESA Agent", "MPESA", 230, 3),
                    DiscoveredAgent("Airtel Money Shop", "AIRTEL", 180, 5),
                    DiscoveredAgent("KCB Mtaani Booth", "KCB", 300, 2),
                    DiscoveredAgent("Equity Agent Hub", "EQUITY", 260, 3)
                )

                agents.forEach {
                    agentAdapter.addOrUpdateAgent(it)
                }
            }
        }, 2000)
    }

    // =====================================================
    // Bluetooth Engine Integration
    // =====================================================

    private fun toggleEngine(enabled: Boolean) {
        val bluetoothFragment = parentFragmentManager.fragments
            .firstOrNull { it is BluetoothFragment } as? BluetoothFragment

        bluetoothFragment?.setBlinkSyncDiscoveryEnabled(
            enabled,
            object : BluetoothFragment.BlinkSyncCallback {
                override fun onDiscoveryStarted() {}
                override fun onDiscoveryStopped() { stopDiscovery() }
                fun onAgentDiscovered(agent: DiscoveredAgent) {
                    // Safety check for callback from external fragment
                    if (::agentAdapter.isInitialized) {
                        agentAdapter.addOrUpdateAgent(agent)
                    }
                }
            }
        )
    }

    // =====================================================
    // Lifecycle Safety
    // =====================================================

    override fun onDismiss(dialog: android.content.DialogInterface) {
        stopDiscovery()
        super.onDismiss(dialog)
    }

    override fun onDestroyView() {
        stopDiscovery()
        super.onDestroyView()
    }
}