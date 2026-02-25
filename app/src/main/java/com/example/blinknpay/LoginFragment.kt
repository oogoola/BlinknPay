package com.example.blinknpay

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.ScaleAnimation
import android.widget.EditText
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.blinknpay.databinding.FragmentLoginBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.example.blinknpay.utils.SecurePrefs
import java.util.concurrent.Executor
import android.util.Log

class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    private lateinit var pinBoxes: List<EditText>
    private var pinInput = ""
    private var isOtpLogin = false

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var executor: Executor

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        executor = ContextCompat.getMainExecutor(requireContext())
        return binding.root
    }




    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        pinBoxes = listOf(binding.otp1, binding.otp2, binding.otp3, binding.otp4)
        Log.d("LoginFragment", "onViewCreated: Checking for existing FirebaseAuth session")

        val user = auth.currentUser
        if (user != null) {
            Log.d("LoginFragment", "User signed in with UID: ${user.uid}")

            // Check if user exists in Firestore
            db.collection("users").document(user.uid).get()
                .addOnSuccessListener { doc ->
                    if (!isAdded) return@addOnSuccessListener
                    Log.d("LoginFragment", "Firestore doc fetched: exists=${doc.exists()}")

                    if (doc.exists()) {
                        Log.d("LoginFragment", "User exists — navigating to Home")
                        (activity as? MainActivity)?.showBottomNav()
                        findNavController().navigate(R.id.navigation_home)
                    } else {
                        Log.d("LoginFragment", "User not found in Firestore — stay on Login")
                        Toast.makeText(requireContext(), "Please complete registration.", Toast.LENGTH_SHORT).show()
                        // User stays on Login, do not auto-navigate to Register
                    }
                }
                .addOnFailureListener { e ->
                    if (!isAdded) return@addOnFailureListener
                    Log.e("LoginFragment", "Error fetching user: ${e.message}", e)
                    Toast.makeText(requireContext(), "Error checking user data", Toast.LENGTH_SHORT).show()
                }
        } else {
            Log.d("LoginFragment", "No FirebaseAuth session — stay on Login")
            // Do NOT navigate automatically to RegisterFragment
        }

        // --- Number pad setup ---
        val buttons = listOf(
            binding.btn1, binding.btn2, binding.btn3,
            binding.btn4, binding.btn5, binding.btn6,
            binding.btn7, binding.btn8, binding.btn9,
            binding.btn0
        )
        buttons.forEach { btn -> btn.setOnClickListener { addDigit(btn.text.toString()) } }
        binding.btnDelete.setOnClickListener { removeDigit() }

        // --- Biometric login setup ---
        val biometricManager = BiometricManager.from(requireContext())
        if (biometricManager.canAuthenticate() == BiometricManager.BIOMETRIC_SUCCESS) {
            binding.btnFingerprint.setOnClickListener {
                Log.d("LoginFragment", "Fingerprint authentication triggered")
                showBiometricPrompt()
            }
        } else {
            Log.d("LoginFragment", "Fingerprint not supported or unavailable")
            binding.btnFingerprint.visibility = View.GONE
        }

        // --- Forgot PIN handler ---


        // --- Manual navigation to Register ---
        binding.txtGoToRegister.setOnClickListener {
            Log.d("LoginFragment", "Navigating to RegisterFragment manually")
            findNavController().navigate(R.id.action_loginFragment_to_registerFragment)
        }
    }





















    private fun addDigit(digit: String) {
        if (pinInput.length < 4) {
            pinInput += digit
            updatePinBoxes()
        }
    }

    private fun removeDigit() {
        if (pinInput.isNotEmpty()) {
            pinInput = pinInput.dropLast(1)
            updatePinBoxes()
        }
    }


    private fun updatePinBoxes() {
        for (i in pinBoxes.indices) {
            if (i < pinInput.length) {
                pinBoxes[i].setBackgroundResource(R.drawable.otp_digit_background_green)
                pinBoxes[i].setText("•")
                animatePinBox(pinBoxes[i])
            } else {
                pinBoxes[i].setBackgroundResource(R.drawable.otp_digit_background)
                pinBoxes[i].setText("")
            }
        }

        // Only check PIN if user is manually entering PIN, not after OTP
        if (pinInput.length == 4 && !isOtpLogin) {
            checkPin(pinInput)
        }
    }








    private fun handleOtpLogin() {
        val user = auth.currentUser
        if (user == null) {
            Toast.makeText(requireContext(), "No authenticated user found", Toast.LENGTH_SHORT).show()
            return
        }

        db.collection("users").document(user.uid).get()
            .addOnSuccessListener { doc ->
                if (!isAdded) return@addOnSuccessListener

                if (doc.exists()) {
                    // User already exists → go to Home
                    navigateToHome()
                } else {
                    // First-time OTP login → create Firestore user doc
                    val newUser = hashMapOf(
                        "uid" to user.uid,
                        "phone" to (user.phoneNumber ?: "")
                    )

                    db.collection("users").document(user.uid)
                        .set(newUser)
                        .addOnSuccessListener {
                            // Directly go to Home (no Setup PIN)
                            navigateToHome()
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(
                                requireContext(),
                                "Failed to create user: ${e.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                            Log.e("LoginFragment", "Error creating user document", e)
                        }
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Error fetching user data: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e("LoginFragment", "Error fetching user document", e)
            }
    }










    private fun navigateToHome() {
        (activity as? MainActivity)?.showBottomNav()
        findNavController().navigate(R.id.navigation_home)
    }










    private fun animatePinBox(box: EditText) {
        val scale = ScaleAnimation(
            1f, 1.2f, 1f, 1.2f,
            ScaleAnimation.RELATIVE_TO_SELF, 0.5f,
            ScaleAnimation.RELATIVE_TO_SELF, 0.5f
        )
        scale.duration = 100
        scale.repeatMode = ScaleAnimation.REVERSE
        scale.repeatCount = 1
        box.startAnimation(scale)
    }



    private fun checkPin(pin: String) {
        val user = auth.currentUser

        if (user == null) {
            Toast.makeText(requireContext(), "No active session. Please log in again.", Toast.LENGTH_SHORT).show()
            findNavController().navigate(R.id.action_loginFragment_to_registerFragment)
            return
        }

        val localPin = SecurePrefs.getEncryptedPin(requireContext())

        // ✅ 1. First check locally stored PIN
        if (!localPin.isNullOrEmpty() && localPin == pin) {
            (activity as? MainActivity)?.showBottomNav()
            findNavController().navigate(R.id.navigation_home)
            return
        }

        // ✅ 2. Then check Firestore user record
        db.collection("users").document(user.uid).get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) {
                    Toast.makeText(requireContext(), "No profile found. Please register.", Toast.LENGTH_SHORT).show()
                    findNavController().navigate(R.id.action_loginFragment_to_registerFragment)
                    return@addOnSuccessListener
                }

                val storedPin = doc.getString("pin")
                val name = doc.getString("name") ?: "User"

                if (storedPin == pin) {
                    // ✅ 3. Save encrypted PIN locally for future logins
                    SecurePrefs.saveEncryptedPin(requireContext(), pin)

                    // ✅ 4. Greet and go to home
                    Toast.makeText(requireContext(), "Welcome back, $name!", Toast.LENGTH_SHORT).show()
                    (activity as? MainActivity)?.showBottomNav()
                    findNavController().navigate(R.id.navigation_home)
                } else {
                    Toast.makeText(requireContext(), "Incorrect PIN. Try again.", Toast.LENGTH_SHORT).show()
                    clearPinBoxes()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Error checking user data: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }






    private fun clearPinBoxes() {
        pinInput = ""
        pinBoxes.forEach {
            it.text.clear()
            it.setBackgroundResource(R.drawable.otp_digit_background)
        }
    }






    private fun showBiometricPrompt() {
        val prompt = BiometricPrompt(this@LoginFragment, executor,

        object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    Toast.makeText(requireContext(), "Fingerprint verified", Toast.LENGTH_SHORT).show()
                    auth.currentUser?.let {
                        (activity as? MainActivity)?.showBottomNav()
                        findNavController().navigate(R.id.navigation_home)
                    }
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    Toast.makeText(requireContext(), "Authentication error: $errString", Toast.LENGTH_SHORT).show()
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    Toast.makeText(requireContext(), "Fingerprint not recognized", Toast.LENGTH_SHORT).show()
                }
            })

        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Login with Fingerprint")
            .setNegativeButtonText("Cancel")
            .build()

        prompt.authenticate(info)
    }





    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
