package com.example.blinknpay


import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.text.InputType
import android.util.Log
import com.google.firebase.Timestamp
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.blinknpay.databinding.FragmentRegisterBinding

import com.google.firebase.firestore.DocumentSnapshot
import com.example.blinknpay.User

import com.example.blinknpay.utils.SecurePrefs
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.FirebaseException
import com.google.firebase.auth.*
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit
import com.google.firebase.firestore.SetOptions


class RegisterFragment : Fragment() {

    private var _binding: FragmentRegisterBinding? = null
    private val binding get() = _binding!!

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private var otpInput: EditText? = null
    val tilRegPhone: TextInputLayout? = null

    private var verificationId: String? = null
    private var resendToken: PhoneAuthProvider.ForceResendingToken? = null
    private var otpVerified = false
    private var otpTimer: CountDownTimer? = null


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRegisterBinding.inflate(inflater, container, false)
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        return binding.root
    }







    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Apply ripple background to Register button
        val rippleDrawable = ContextCompat.getDrawable(requireContext(), R.drawable.ripple_bg)
        binding.btnRegister.background = rippleDrawable

        // LINK CCP with EditText
        binding.ccp.registerCarrierNumberEditText(binding.etRegPhone)

        // Button Clicks
        binding.btnRegister.setOnClickListener { sendOtp() }

        binding.btnGoToLogin.setOnClickListener {
            findNavController().navigate(R.id.action_registerFragment_to_loginFragment)
        }
    }

    /**
     * STEP 1: Validate input and send OTP (Firebase + CCP)
     */
    private fun sendOtp() {

        val name = binding.etRegName.text.toString().trim()
        val pin = binding.etRegPin.text.toString().trim()
        val emailOptional = binding.etRegEmail.text.toString().trim().takeIf { it.isNotEmpty() }

        // -------- VALIDATION --------
        if (name.isEmpty()) {
            binding.etRegName.error = "Enter full name"
            binding.etRegName.requestFocus()
            return
        }



        if (!binding.ccp.isValidFullNumber) {
            tilRegPhone?.error = "Enter valid phone number"
            binding.etRegPhone.requestFocus()
            return
        } else {
            tilRegPhone?.error = null
        }






        if (pin.length != 4) {
            binding.etRegPin.error = "PIN must be 4 digits"
            binding.etRegPin.requestFocus()
            return
        }

        // Get full phone number with country code (+254...)
        val phoneNumber = binding.ccp.fullNumberWithPlus

        // Disable register button to prevent multiple clicks
        binding.btnRegister.isEnabled = false
        binding.btnRegister.alpha = 0.5f

        // -------- FIREBASE OTP --------
        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phoneNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(requireActivity())
            .setCallbacks(phoneAuthCallbacks(name, phoneNumber, pin, emailOptional))
            .build()

        PhoneAuthProvider.verifyPhoneNumber(options)

        Toast.makeText(requireContext(), "OTP sent to $phoneNumber", Toast.LENGTH_SHORT).show()

        // Re-enable button after timeout
        binding.btnRegister.postDelayed({
            if (isAdded) {
                binding.btnRegister.isEnabled = true
                binding.btnRegister.alpha = 1f
            }
        }, 60000)
    }
































    // STEP 2: Firebase phone auth callbacks (native, seamless flow)
    private fun phoneAuthCallbacks(
        name: String,
        phone: String,
        pin: String,
        email: String?
    ) = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {

        // ✅ Auto-retrieval or instant verification



        override fun onVerificationCompleted(credential: PhoneAuthCredential) {
            Log.d("RegisterFragment", "OTP auto-verified for $phone")

            // Auto-fill OTP in dialog if code is available
            credential.smsCode?.let { code ->
                otpInput?.setText(code)
                Log.d("RegisterFragment", "OTP auto-filled in dialog: $code")
            }

            Toast.makeText(requireContext(), "OTP verified automatically", Toast.LENGTH_SHORT).show()

            // Mark OTP as verified
            otpVerified = true

            // Sign in and create user document
            signInWithPhoneAuthCredential(credential, name, phone, pin, email)
        }








        // ❌ Verification failed
        override fun onVerificationFailed(e: FirebaseException) {
            Log.e("RegisterFragment", "OTP verification failed: ${e.message}", e)
            Toast.makeText(
                requireContext(),
                "OTP failed: ${e.localizedMessage ?: "Try again"}",
                Toast.LENGTH_SHORT
            ).show()

            // Re-enable register button for retry
            if (isAdded) {
                binding.btnRegister.isEnabled = true
                binding.btnRegister.alpha = 1f
            }
        }

        // 🔔 OTP code sent manually, show dialog for user input
        override fun onCodeSent(
            verificationId: String,
            token: PhoneAuthProvider.ForceResendingToken
        ) {
            Log.d("RegisterFragment", "OTP code sent to $phone, waiting for input")

            this@RegisterFragment.verificationId = verificationId
            resendToken = token

            // Launch OTP dialog for user to enter code
            showOtpDialog(name, phone, pin, email)
        }
    }





    private fun showOtpDialog(
        name: String,
        phone: String,
        pin: String,
        email: String?
    ) {
        // Build dialog
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Verify Phone")

        // OTP input
        otpInput = EditText(requireContext()).apply {
            hint = "Enter 6-digit OTP"
            inputType = InputType.TYPE_CLASS_NUMBER
            isSingleLine = true
            requestFocus()
            post {
                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
            }
        }

        // Countdown timer text
        val countdownText = TextView(requireContext()).apply {
            text = "Waiting for OTP..."
            setPadding(0, 16, 0, 0)
        }

        // Layout container
        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
            addView(otpInput)
            addView(countdownText)
        }

        builder.setView(layout)
        builder.setCancelable(false)

        // Positive button: verify OTP manually
        builder.setPositiveButton("Verify") { dialog, _ ->
            val code = otpInput?.text.toString().trim()
            if (code.isNotEmpty() && verificationId != null) {
                Log.d("RegisterFragment", "Manually verifying OTP: $code for $phone")
                val credential = PhoneAuthProvider.getCredential(verificationId!!, code)
                signInWithPhoneAuthCredential(credential, name, phone, pin, email)
                dialog.dismiss()
            } else {
                Toast.makeText(requireContext(), "Enter OTP", Toast.LENGTH_SHORT).show()
            }

            _binding?.btnRegister?.isEnabled = true
            _binding?.btnRegister?.alpha = 1f



        }

        // Negative button: cancel OTP
        builder.setNegativeButton("Cancel") { dialog, _ ->
            otpVerified = false
            dialog.dismiss()
            binding.btnRegister.isEnabled = true
            binding.btnRegister.alpha = 1f
        }

        val otpDialog = builder.create()
        otpDialog.show()

        // Countdown timer for OTP expiry


        // Cancel any existing timer first (safety)
        otpTimer?.cancel()

        otpTimer = object : CountDownTimer(60000, 1000) {

            override fun onTick(millisUntilFinished: Long) {
                // Safe because this TextView belongs to dialog layout
                countdownText.text = "Waiting for OTP: ${millisUntilFinished / 1000}s"
            }

            override fun onFinish() {
                if (!otpVerified) {
                    countdownText.text = "OTP expired. Try again."
                }

                // 🔐 Safe binding access
                _binding?.btnRegister?.isEnabled = true
                _binding?.btnRegister?.alpha = 1f
            }

        }.start()







        viewLifecycleOwner.lifecycleScope.launchWhenResumed {
            while (otpDialog.isShowing && !otpVerified) {
                if (auth.currentUser != null) {
                    otpDialog.dismiss()
                    return@launchWhenResumed  // exit coroutine instead of break
                }
                kotlinx.coroutines.delay(500)
            }
        }








    }

    // Call this from onVerificationCompleted to autofill OTP automatically
    private fun autofillOtp(code: String?) {
        code?.let {
            otpInput?.setText(it)
            Log.d("RegisterFragment", "OTP auto-filled: $it")
        }
    }













    private fun signInWithPhoneAuthCredential(
        credential: PhoneAuthCredential,
        name: String,
        phone: String,
        pin: String,
        email: String?
    ) {
        auth.signInWithCredential(credential)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    if (user == null) {
                        Log.e("RegisterFragment", "User is null after OTP verification")
                        Toast.makeText(
                            requireContext(),
                            "Sign-in error: User not found",
                            Toast.LENGTH_SHORT
                        ).show()
                        return@addOnCompleteListener
                    }

                    otpVerified = true
                    val userId = user.uid
                    Log.d("RegisterFragment", "✅ OTP verified for $phone (UID=$userId)")

                    viewLifecycleOwner.lifecycleScope.launch {
                        try {
                            val online = isOnline()
                            val usersRef = firestore.collection("users")

                            // Check if phone already exists online
                            val phoneExists = if (online) {
                                val querySnapshot = usersRef.whereEqualTo("phone", phone).get().await()
                                !querySnapshot.isEmpty
                            } else false

                            if (phoneExists) {
                                Toast.makeText(
                                    requireContext(),
                                    "Phone already registered. Please log in.",
                                    Toast.LENGTH_SHORT
                                ).show()
                                (activity as? MainActivity)?.showBottomNav()
                                findNavController().navigate(R.id.navigation_home)
                            } else {
                                // Register new user (online or offline)
                                generateUserNumberAndSave(userId, name, phone, pin, email)
                            }
                        } catch (e: Exception) {
                            Log.e("RegisterFragment", "Error during registration: ${e.message}", e)
                            Toast.makeText(
                                requireContext(),
                                "Registration error: ${e.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                            // Fallback: proceed with cached registration
                            generateUserNumberAndSave(userId, name, phone, pin, email)
                        }
                    }
                } else {
                    otpVerified = false
                    val exception = task.exception
                    Log.e(
                        "RegisterFragment",
                        "signInWithCredential failed: ${exception?.message}",
                        exception
                    )
                    Toast.makeText(
                        requireContext(),
                        "Sign-in failed: ${exception?.message ?: "Unknown error"}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
    }








    @SuppressLint("MissingPermission")
    private fun generateUserNumberAndSave(
        userId: String,
        name: String,
        phone: String,
        pin: String,
        email: String?
    ) {
        val counterRef = firestore.collection("counters").document("users")

        viewLifecycleOwner.lifecycleScope.launch {
            val userNumber = try {
                if (isOnline()) {
                    // Generate user number safely with Firestore transaction
                    firestore.runTransaction { transaction ->
                        val snapshot = transaction.get(counterRef)
                        val lastNumber = snapshot.getLong("lastUserNumber") ?: 1000L
                        val newNumber = lastNumber + 1
                        transaction.set(counterRef, mapOf("lastUserNumber" to newNumber))
                        newNumber
                    }.await()
                } else {
                    // Offline fallback
                    if (isAdded) {
                        Toast.makeText(
                            requireContext(),
                            "No internet. Using local user number.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    System.currentTimeMillis() % 1000000L + 1000L
                }
            } catch (e: Exception) {
                Log.e("RegisterFragment", "Failed to generate user number: ${e.message}", e)
                if (isAdded) {
                    Toast.makeText(
                        requireContext(),
                        "Failed to generate user number. Using fallback.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                System.currentTimeMillis() % 1000000L + 1000L
            }

            // Save user document safely
            saveUserDocument(userId, name, phone, pin, email, userNumber)
        }
    }





    private fun saveUserDocument(
        userId: String,
        name: String,
        phone: String,
        pin: String,
        email: String?,
        userNumber: Long
    ) {
        // ⚠️ TEMP hash – replace with BCrypt or a stronger hash in production
        val pinHash = pin.hashCode().toString()

        val user = User(
            uid = userId,
            userNumber = userNumber,
            fullName = name,
            phone = phone,
            email = email,
            pinHash = pinHash,                 // ✅ hashed PIN
            profileImageUrl = "",              // empty initially
            createdAt = Timestamp.now(),       // Firestore Timestamp
            lastLoginAt = Timestamp.now(),     // Firestore Timestamp
            isActive = true
        )

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                if (!isAdded) return@launch

                // 🔌 Check connectivity
                if (!isOnline()) {
                    Toast.makeText(
                        requireContext(),
                        "No internet connection. Please try again.",
                        Toast.LENGTH_LONG
                    ).show()
                    return@launch
                }

                // 🔥 Save to Firestore (merge-safe)
                firestore.collection("users")
                    .document(userId)  // UID as document ID
                    .set(user, SetOptions.merge())
                    .await()

                if (!isAdded) return@launch

                // 🔐 Save encrypted PIN locally (for quick unlock)
                SecurePrefs.saveEncryptedPin(requireContext(), pin)

                // ✅ UI state updates
                (requireActivity() as MainActivity).apply {
                    isUserLoggedIn = true
                    invalidateOptionsMenu()
                    showBottomNav()
                }

                Toast.makeText(
                    requireContext(),
                    "Registration successful 🎉",
                    Toast.LENGTH_SHORT
                ).show()

                // 🚀 Navigate to Home
                findNavController().navigate(R.id.navigation_home)

                Log.d("RegisterFragment", "✅ User registered: $userId")

            } catch (e: Exception) {
                if (!isAdded) return@launch

                Log.e("RegisterFragment", "❌ Registration failed", e)

                Toast.makeText(
                    requireContext(),
                    "Registration failed. Please try again.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }


























    /**
     * Checks if the device is online.
     * Single unified method for all API levels.
     */
    @SuppressLint("MissingPermission")
    private fun isOnline(): Boolean {
        return try {
            val cm = requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val network = cm.activeNetwork
                val capabilities = cm.getNetworkCapabilities(network)
                capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
            } else {
                val networkInfo = cm.activeNetworkInfo
                networkInfo != null && networkInfo.isConnected
            }
        } catch (e: Exception) {
            Log.e("RegisterFragment", "Error checking network status: ${e.message}")
            false
        }
    }





    private fun registerUser() {
        val name = binding.etRegName.text.toString().trim()
        val pin = binding.etRegPin.text.toString().trim()
        val email = binding.etRegEmail.text.toString().trim().takeIf { it.isNotEmpty() }

        if (name.isEmpty()) { binding.etRegName.error = "Enter full name"; return }
        if (!binding.ccp.isValidFullNumber) { binding.etRegPhone.error = "Invalid phone"; return }
        if (pin.length != 4) { binding.etRegPin.error = "PIN must be 4 digits"; return }

        val phoneNumber = binding.ccp.fullNumberWithPlus

        // For test numbers, we can skip real OTP
        val testOtp = when(phoneNumber) {
            "+254114344673" -> "123456"
            "+16055551234" -> "252525"
            else -> null
        }

        if (testOtp != null) {
            val credential = PhoneAuthProvider.getCredential("TEST_VERIFICATION_ID", testOtp)
            signInWithPhoneAuthCredential(credential, name, phoneNumber, pin, email)
            return
        }

        // Otherwise, send real OTP
        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phoneNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(requireActivity())
            .setCallbacks(phoneAuthCallbacks(name, phoneNumber, pin, email))
            .build()
        PhoneAuthProvider.verifyPhoneNumber(options)

        Toast.makeText(requireContext(), "OTP sent to $phoneNumber", Toast.LENGTH_SHORT).show()
    }


























    override fun onDestroyView() {
        otpTimer?.cancel()
        otpTimer = null
        _binding = null
        super.onDestroyView()
    }

}
