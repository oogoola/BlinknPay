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
// 🔑 CRITICAL IMPORTS FIXED HERE
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import java.util.concurrent.ConcurrentHashMap



class BlinknPayApp : Application() {

    companion object {
        val registeredMerchants: ConcurrentHashMap<String, Merchant> = ConcurrentHashMap()

        private const val USE_EMULATOR = false
        private const val EMULATOR_IP = "192.168.0.103"
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

        preloadMerchants()
    }

    private fun setupProductionFirestore() {
        val firestore = FirebaseFirestore.getInstance()
        val settings = FirebaseFirestoreSettings.Builder()
            .setPersistenceEnabled(true)
            .setCacheSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
            .build()
        firestore.firestoreSettings = settings
        Log.d("BlinknPayApp", "Connected to PRODUCTION Firebase Cloud")
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
            // 🔑 Fixed: Using the standard getInstance() for Functions
            FirebaseFunctions.getInstance().useEmulator(EMULATOR_IP, 5001)

            Log.d("BlinknPayApp", "DEBUG: Connected to Firebase emulators")
        } catch (e: Exception) {
            Log.e("BlinknPayApp", "Emulator error: ${e.message}")
        }
    }

    private fun preloadMerchants() {
        appScope.launch {
            delay(1000)
            val db = Firebase.firestore
            try {
                // 🔑 .await() now works because of the import: kotlinx.coroutines.tasks.await
                val snapshot = db.collection("merchants").get().await()

                registeredMerchants.clear()

                // 🔑 Explicitly loop through documents to avoid 'it' ambiguity
                for (document in snapshot.documents) {
                    val merchant = document.toObject(Merchant::class.java)
                    merchant?.let { m ->
                        m.advertId?.let { if(it.isNotEmpty()) registeredMerchants[it] = m }
                        m.bluetoothName?.let { if(it.isNotEmpty()) registeredMerchants[it] = m }
                    }
                }
                Log.d("BlinknPayApp", "✅ Cached ${registeredMerchants.size} merchants.")
            } catch (e: Exception) {
                Log.e("BlinknPayApp", "❌ Preload Failed: ${e.message}")
            }
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        appScope.cancel()
    }
}