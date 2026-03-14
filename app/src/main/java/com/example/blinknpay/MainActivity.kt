package com.example.blinknpay

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.NavigationUI
import androidx.navigation.ui.setupWithNavController
import com.example.blinknpay.databinding.ActivityMainBinding
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    lateinit var navController: NavController
    lateinit var navView: BottomNavigationView
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    var isUserLoggedIn = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // FIX 1: Correct ViewBinding initialization
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 1. Toolbar Setup
        setSupportActionBar(binding.toolbar)

        // 2. NavController Setup
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        // 3. Bottom Navigation Setup
        navView = binding.bottomNavigation
        navView.setupWithNavController(navController)

        // 4. Navigation Handling
        navView.setOnItemSelectedListener { item ->
            if (item.itemId == R.id.placeholder) return@setOnItemSelectedListener false

            val destinationExists = navController.graph.findNode(item.itemId) != null
            if (destinationExists) {
                NavigationUI.onNavDestinationSelected(item, navController)
            } else {
                when (item.itemId) {
                    // FIXED: Redirecting to correct Analytics Fragment ID
                    R.id.analyticsFragment -> safeNavigateTo(R.id.analyticsFragment)
                    R.id.transactionsFragment -> safeNavigateTo(R.id.transactionsFragment)
                    else -> handleManualNavigation(item.itemId)
                }
            }
            true
        }

        // 5. Visibility Logic
        navController.addOnDestinationChangedListener { _, destination, _ ->
            when (destination.id) {
                R.id.navigation_home,
                R.id.servicesFragment,
                R.id.transactionsFragment,
                R.id.navigation_profile,
                R.id.navigation_qr_scanner,
                R.id.analyticsFragment -> { // FIXED ID HERE
                    showBottomNav()
                    if (destination.id == R.id.servicesFragment) {
                        supportActionBar?.hide()
                    } else {
                        supportActionBar?.show()
                        supportActionBar?.title = destination.label
                    }
                }
                else -> {
                    hideBottomNav()
                    supportActionBar?.show()
                }
            }
        }

        // 6. FAB Action
        binding.fabBlinkAction.setOnClickListener {
            if (isUserLoggedIn) {
                safeNavigateTo(R.id.servicesFragment)
            } else {
                Toast.makeText(this, "Login to access BlinknPay Services", Toast.LENGTH_SHORT).show()
            }
        }

        navView.post { checkCustomerStatus() }
    }



    override fun onStart() {
        super.onStart()
        // Accessibility check moved here to catch when user returns from settings
        if (isUserLoggedIn && !isAccessibilityServiceEnabled()) {
            checkAndRequestAccessibility()
        }
    }

    // --- BLINKNPAY ACCESSIBILITY BRIDGE ---

    private fun isAccessibilityServiceEnabled(): Boolean {
        val serviceId = ComponentName(this, BlinknPayOverlayService::class.java).flattenToString()
        val mStringColonSplitter = TextUtils.SimpleStringSplitter(':')
        val settingValue = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)

        if (settingValue != null) {
            mStringColonSplitter.setString(settingValue)
            while (mStringColonSplitter.hasNext()) {
                if (mStringColonSplitter.next().equals(serviceId, ignoreCase = true)) return true
            }
        }
        return false
    }

    fun checkAndRequestAccessibility() {
        showA11yExplanationDialog {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        }
    }

    private fun showA11yExplanationDialog(onAccept: () -> Unit) {
        AlertDialog.Builder(this).apply {
            setIcon(R.drawable.blink)
            setTitle("Secure Biometric Bridge")
            setMessage("BlinknPay uses Accessibility Services to map your fingerprint to your M-PESA PIN safely.")
            setPositiveButton("GRANT PERMISSION") { _, _ -> onAccept() }
            setNegativeButton("NOT NOW") { dialog, _ -> dialog.dismiss() }
        }.create().show()
    }

    // --- DATA & STATUS ---

    fun checkCustomerStatus() {
        isUserLoggedIn = auth.currentUser != null
        invalidateOptionsMenu()
        val currentId = navController.currentDestination?.id

        if (!isUserLoggedIn) {
            hideBottomNav()
            if (currentId != R.id.loginFragment && currentId != R.id.splashFragment) safeNavigateTo(R.id.loginFragment)
        } else {
            showBottomNav()
            if (currentId == R.id.splashFragment || currentId == R.id.loginFragment) {
                safeNavigateToAction(currentId, R.id.action_splashFragment_to_homeFragment)
            }

            // Trigger balance fetch if logged in
            fetchAndShowBalance()
        }
    }

    private fun fetchAndShowBalance() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val balance = fetchMpesaBalance()
                withContext(Dispatchers.Main) {
                    // NOTE: Ensure tvMainBalance is in activity_main.xml or use findFragmentById
                    // If it's in the Fragment, move this logic there!
                    Log.d("BlinknPay", "Balance: $balance")
                }
            } catch (e: Exception) {
                Log.e("BlinknPay", "Fetch error", e)
            }
        }
    }

    private suspend fun fetchMpesaBalance(): String {
        return withContext(Dispatchers.IO) {
            Thread.sleep(1000)
            "31.00"
        }
    }

    // --- UI HELPERS ---

    fun showBottomNav() {
        binding.bottomNavigation.visibility = View.VISIBLE
        binding.bottomAppBar.visibility = View.VISIBLE
        binding.fabBlinkAction.visibility = View.VISIBLE
    }

    fun hideBottomNav() {
        binding.bottomNavigation.visibility = View.GONE
        binding.bottomAppBar.visibility = View.GONE
        binding.fabBlinkAction.visibility = View.GONE
    }

    private fun handleManualNavigation(itemId: Int) {
        if (itemId == R.id.navigation_home) safeNavigateTo(R.id.navigation_home)
    }

    fun safeNavigateTo(destinationId: Int) {
        if (navController.currentDestination?.id != destinationId) {
            try { navController.navigate(destinationId) } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun safeNavigateToAction(currentFragmentId: Int, actionId: Int) {
        if (navController.currentDestination?.id == currentFragmentId) {
            try { navController.navigate(actionId) } catch (e: Exception) { e.printStackTrace() }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.toolbar_menu, menu)
        menu?.findItem(R.id.action_scan)?.isVisible = isUserLoggedIn
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if (item.itemId == R.id.action_scan) {
            if (isUserLoggedIn) safeNavigateTo(R.id.navigation_qr_scanner)
            true
        } else super.onOptionsItemSelected(item)
    }
}