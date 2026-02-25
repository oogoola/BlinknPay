package com.example.blinknpay

import android.net.Uri
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage

class ProfileRepository {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val storage: FirebaseStorage = FirebaseStorage.getInstance()

    /** Returns reference to 'users' collection */
    private fun usersRef() = db.collection("users")

    /** Returns current user's document reference, or null if not logged in */
    private fun userDoc() = auth.currentUser?.uid?.let { usersRef().document(it) }

    /**
     * Update specific fields of the current user's document.
     * @param fields Map of fields to update
     */
    fun updateProfileFields(fields: Map<String, Any>): Task<Void> {
        val doc = userDoc()
        return doc?.update(fields)
            ?: Tasks.forException(Exception("No authenticated user"))
    }

    /**
     * Save the full User object to Firestore.
     * Uses merge to avoid overwriting unspecified fields.
     * @param user User object to save
     */
    fun saveUser(user: User): Task<Void> {
        val doc = userDoc()
        return doc?.set(user, SetOptions.merge())
            ?: Tasks.forException(Exception("No authenticated user"))
    }

    /**
     * Upload profile image to Firebase Storage and return download URL.
     * @param uri Local Uri of image
     * @param onComplete Callback returning Task<Uri>
     */
    fun uploadProfileImage(uri: Uri, onComplete: (Task<Uri>) -> Unit) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            onComplete(Tasks.forException(Exception("No authenticated user")))
            return
        }

        val ref = storage.reference.child("profile_images/$uid/${System.currentTimeMillis()}.jpg")
        ref.putFile(uri)
            .continueWithTask { task ->
                if (!task.isSuccessful) task.exception?.let { throw it }
                ref.downloadUrl
            }
            .addOnCompleteListener { downloadTask ->
                onComplete(downloadTask)
            }
    }

    /** Get current user's document snapshot as Task<DocumentSnapshot> */
    fun getUserDoc() = userDoc()?.get()
}
