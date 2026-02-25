package com.example.blinknpay

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.NavigationUI
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth

class MainActivity : AppCompatActivity() {

    lateinit var navController: NavController
    lateinit var navView: BottomNavigationView

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    var isUserLoggedIn = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1. Toolbar Setup
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        // 2. NavController Setup
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        // 3. Bottom Navigation Setup
        navView = findViewById(R.id.bottom_navigation)
        navView.setupWithNavController(navController)

        // 4. Navigation Handling & Interception (CRASH FIX APPLIED HERE)
        navView.setOnItemSelectedListener { item ->
            if (item.itemId == R.id.placeholder) return@setOnItemSelectedListener false

            // Check if the menu ID exists in our NavGraph
            val destinationExists = navController.graph.findNode(item.itemId) != null

            if (destinationExists) {
                // Let NavigationUI handle it if IDs match (navigation_home, navigation_profile, etc.)
                NavigationUI.onNavDestinationSelected(item, navController)
            } else {
                // MANUAL MAPPING: Fixes mismatch between Menu IDs and Graph IDs
                when (item.itemId) {
                    R.id.navigation_transactions -> safeNavigateTo(R.id.transactionsFragment)
                    R.id.receivedFragment -> safeNavigateTo(R.id.receivedFragment)
                    else -> handleManualNavigation(item.itemId)
                }
            }
            true
        }

        // 5. Visibility & UI Logic
        navController.addOnDestinationChangedListener { _, destination, _ ->
            when (destination.id) {
                R.id.navigation_home,      // Bluetooth Fragment
                R.id.servicesFragment,    // Marketplace Hub
                R.id.transactionsFragment,
                R.id.navigation_profile,
                R.id.navigation_qr_scanner,
                R.id.receivedFragment -> {
                    showBottomNav()
                    // Services Hub uses a custom FluidHeader, hide standard Toolbar
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

        // 6. Login Status Check
        navView.post {
            checkCustomerStatus()
        }

        // 7. "Blink NOW" FAB - Opens Services Marketplace
        findViewById<View>(R.id.fab_blink_action)?.setOnClickListener {
            if (isUserLoggedIn) {
                safeNavigateTo(R.id.servicesFragment)
            } else {
                Toast.makeText(this, "Login to access BlinknPay Services", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun handleManualNavigation(itemId: Int) {
        when(itemId) {
            R.id.navigation_home -> safeNavigateTo(R.id.navigation_home)
        }
    }

    fun checkCustomerStatus() {
        isUserLoggedIn = auth.currentUser != null
        invalidateOptionsMenu()

        val currentId = navController.currentDestination?.id

        if (!isUserLoggedIn) {
            hideBottomNav()
            if (currentId != R.id.loginFragment && currentId != R.id.splashFragment) {
                safeNavigateTo(R.id.loginFragment)
            }
        } else {
            showBottomNav()
            // Send logged-in users to Bluetooth Home (navigation_home)
            if (currentId == R.id.splashFragment || currentId == R.id.loginFragment) {
                // Ensure this action ID matches your nav_graph.xml exactly
                safeNavigateToAction(currentId, R.id.action_splashFragment_to_homeFragment)
            }
        }
    }

    // --- UI Helpers ---

    fun showBottomNav() {
        navView.visibility = View.VISIBLE
        findViewById<View>(R.id.bottom_app_bar)?.visibility = View.VISIBLE
        findViewById<View>(R.id.fab_blink_action)?.visibility = View.VISIBLE
    }

    fun hideBottomNav() {
        navView.visibility = View.GONE
        findViewById<View>(R.id.bottom_app_bar)?.visibility = View.GONE
        findViewById<View>(R.id.fab_blink_action)?.visibility = View.GONE
    }

    // --- Navigation Safety Helpers ---

    fun safeNavigateTo(destinationId: Int) {
        if (navController.currentDestination?.id != destinationId) {
            try {
                navController.navigate(destinationId)
            } catch (e: Exception) {
                // This prevents crash if a destination is missing from graph
                e.printStackTrace()
            }
        }
    }

    fun safeNavigateToAction(currentFragmentId: Int, actionId: Int) {
        if (navController.currentDestination?.id == currentFragmentId) {
            try {
                navController.navigate(actionId)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.toolbar_menu, menu)
        menu?.findItem(R.id.action_scan)?.isVisible = isUserLoggedIn
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_scan -> {
                if (isUserLoggedIn) safeNavigateTo(R.id.navigation_qr_scanner)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}