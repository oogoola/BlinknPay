package com.example.blinknpay

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import java.util.concurrent.ConcurrentHashMap

/**
 * Main Application class for BlinknPay.
 * Handles Firebase initialization, Emulator/Production switching,
 * and pre-loading the Merchant Registry into memory for instant discovery.
 */
class BlinknPayApp : Application() {

    companion object {
        // The "Source of Truth" for your proximity engine.
        // Key: proximityId (RPID/SSID/BLE Name) | Value: Merchant Object
        val registeredMerchants: ConcurrentHashMap<String, Merchant> = ConcurrentHashMap()

        private const val USE_EMULATOR = false
        private const val EMULATOR_IP = "10.0.2.2" // Standard Android Emulator IP
    }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)

        if (USE_EMULATOR && BuildConfig.DEBUG) {
            setupEmulators()
        } else {
            setupProductionFirestore()
        }

        // Start caching merchants as soon as the app process starts
        preloadMerchants()
    }

    private fun setupProductionFirestore() {
        val firestore = FirebaseFirestore.getInstance()
        val settings = FirebaseFirestoreSettings.Builder()
            .setPersistenceEnabled(true) // Enables offline M-Pesa tracking
            .setCacheSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
            .build()
        firestore.firestoreSettings = settings
        Log.d("BlinknPayApp", "✅ Production Firebase Initialized")
    }

    private fun setupEmulators() {
        try {
            val firestore = FirebaseFirestore.getInstance()
            firestore.useEmulator(EMULATOR_IP, 8080)

            val settings = FirebaseFirestoreSettings.Builder()
                .setPersistenceEnabled(false)
                .build()
            firestore.firestoreSettings = settings

            FirebaseDatabase.getInstance().useEmulator(EMULATOR_IP, 9000)
            FirebaseFunctions.getInstance().useEmulator(EMULATOR_IP, 5001)

            Log.d("BlinknPayApp", "🛠️ DEBUG: Connected to Local Emulators")
        } catch (e: Exception) {
            Log.e("BlinknPayApp", "❌ Emulator error: ${e.message}")
        }
    }

    /**
     * Pre-loads all merchants into a ConcurrentHashMap.
     * This ensures that when the Bluetooth Engine sees a "proximityId",
     * we don't need a network call to verify if they are a BlinknPay Merchant.
     */
    private fun preloadMerchants() {
        appScope.launch {
            // Wait slightly for Firebase networking to warm up
            delay(500)
            val db = Firebase.firestore
            try {
                // Fetch the verified merchant list
                val snapshot = db.collection("merchants")
                    .whereEqualTo("isActive", true) // Only cache active businesses
                    .get()
                    .await()

                registeredMerchants.clear()

                for (document in snapshot.documents) {
                    val merchant = document.toObject(Merchant::class.java)

                    merchant?.let { m ->
                        // 🚀 THE CORE FIX:
                        // We only map by proximityId and id.
                        // advertId and bluetoothName have been removed to match your new model.

                        if (m.proximityId.isNotEmpty()) {
                            registeredMerchants[m.proximityId] = m
                        }

                        // Also index by ID for direct lookups
                        if (m.id.isNotEmpty()) {
                            registeredMerchants[m.id] = m
                        }
                    }
                }

                Log.d("BlinknPayApp", "📦 Registry Ready: ${registeredMerchants.size} merchants cached.")
            } catch (e: Exception) {
                Log.e("BlinknPayApp", "❌ Registry Preload Failed: ${e.message}")
            }
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        appScope.cancel()
    }
}