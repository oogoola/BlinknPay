package com.example.blinknpay

import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.android.material.button.MaterialButton

class ProfileFragment : Fragment() {

    private val repo = ProfileRepository()
    private val auth = FirebaseAuth.getInstance()

    private lateinit var ivProfile: de.hdodenhof.circleimageview.CircleImageView
    private lateinit var btnEditPhoto: ImageButton
    private lateinit var tvName: TextView
    private lateinit var tvUsername: TextView
    private lateinit var tvPhone: TextView
    private lateinit var tvBalance: TextView

    private lateinit var etFullName: EditText
    private lateinit var etUsername: EditText
    private lateinit var etEmail: EditText
    private lateinit var btnSave: MaterialButton
    private lateinit var btnTopUp: MaterialButton
    private lateinit var btnReceiveQR: MaterialButton
    private lateinit var btnLogout: MaterialButton

    private var chosenImageUri: Uri? = null

    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let { chosenImageUri = it; uploadProfileImage(it) }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_profile, container, false)

        // ---------- Bind UI ----------
        ivProfile = view.findViewById(R.id.ivProfile)
        btnEditPhoto = view.findViewById(R.id.btnEditPhoto)
        tvName = view.findViewById(R.id.tvName)
        tvUsername = view.findViewById(R.id.tvUsername)
        tvPhone = view.findViewById(R.id.tvPhone)
        tvBalance = view.findViewById(R.id.tvBalance)

        etFullName = view.findViewById(R.id.etFullName)
        etUsername = view.findViewById(R.id.etUsername)
        etEmail = view.findViewById(R.id.etEmail)

        btnSave = view.findViewById(R.id.btnSaveProfile)
        btnTopUp = view.findViewById(R.id.btnTopUp)
        btnReceiveQR = view.findViewById(R.id.btnReceiveQR)
        btnLogout = view.findViewById(R.id.btnLogout)

        // ---------- Setup Events ----------
        btnEditPhoto.setOnClickListener { openImagePicker() }
        btnSave.setOnClickListener { saveProfile() }
        btnTopUp.setOnClickListener { showToast("Top up not implemented") }
        btnReceiveQR.setOnClickListener { showToast("Receive QR not implemented") }
        btnLogout.setOnClickListener { logout() }

        etFullName.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { btnSave.isEnabled = true }
            override fun afterTextChanged(s: Editable?) {}
        })

        // ---------- Load Profile ----------
        loadProfile()

        return view
    }

    // ---------- Image Picker ----------
    private fun openImagePicker() {
        pickImageLauncher.launch("image/*")
    }

    private fun uploadProfileImage(uri: Uri) {
        val progressDialog = AlertDialog.Builder(requireContext())
            .setView(ProgressBar(requireContext()))
            .setCancelable(false)
            .create()
        progressDialog.show()

        repo.uploadProfileImage(uri) { task ->
            progressDialog.dismiss()
            if (task.isSuccessful) {
                val downloadUri = task.result
                val data = mapOf("profileImageUrl" to (downloadUri?.toString() ?: ""))
                repo.updateProfileFields(data)
                    .addOnSuccessListener {
                        showToast("Profile photo updated")
                        loadProfile()
                    }
                    .addOnFailureListener { showToast("Failed saving profile photo") }
            } else {
                showToast("Upload failed")
            }
        }
    }

    // ---------- Load User ----------


    private fun loadProfile() {
        val docTask = repo.getUserDoc()
        if (docTask == null) {
            showToast("No authenticated user")
            return
        }

        docTask.addOnSuccessListener { doc ->
            if (!isAdded) return@addOnSuccessListener

            if (!doc.exists()) {
                showToast("User profile not found")
                return@addOnSuccessListener
            }

            val user = doc.toObject(User::class.java)
            if (user == null) {
                showToast("Failed to parse user data")
                return@addOnSuccessListener
            }

            // ---------- Populate UI ----------
            tvName.text = user.fullName.ifBlank { "Your name" }
            tvUsername.text = "@${user.uid.take(6)}"
            tvPhone.text = user.phone.ifBlank { "No phone" }
            etFullName.setText(user.fullName)
            etUsername.setText(user.uid)
            etEmail.setText(user.email ?: "")
            tvBalance.text = "KSh 0" // You can later update to actual balance if available

            // ---------- Load profile image ----------
            val imageUrl = user.profileImageUrl
            if (imageUrl.isNotBlank()) {
                Glide.with(this)
                    .load(imageUrl)
                    .placeholder(R.drawable.ic_profile_placeholder)
                    .into(ivProfile)
            } else {
                ivProfile.setImageResource(R.drawable.ic_profile_placeholder)
            }

        }.addOnFailureListener { e ->
            if (!isAdded) return@addOnFailureListener
            showToast("Failed to load profile: ${e.localizedMessage ?: "Unknown error"}")
            e.printStackTrace()
        }
    }








    private fun saveProfile() {
        val name = etFullName.text.toString().trim()
        val email = etEmail.text.toString().trim()

        if (name.isEmpty()) {
            etFullName.error = "Enter your name"
            return
        }

        if (email.isNotEmpty() && !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.error = "Enter a valid email"
            return
        }

        val updates = mapOf(
            "fullName" to name,
            "email" to email
        )

        repo.updateProfileFields(updates)
            .addOnSuccessListener {
                showToast("Profile saved")
                loadProfile()
            }
            .addOnFailureListener { e ->
                showToast("Failed to save: ${e.localizedMessage ?: "Unknown error"}")
            }
    }








    // ---------- Logout ----------
    private fun logout() {
        AlertDialog.Builder(requireContext())
            .setMessage("Log out of this account?")
            .setPositiveButton("Log out") { _, _ ->
                auth.signOut()
                requireActivity().finish()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ---------- Utility ----------
    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }
}
