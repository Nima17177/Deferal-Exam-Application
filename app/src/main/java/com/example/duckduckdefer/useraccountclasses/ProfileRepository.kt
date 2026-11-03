package com.example.duckduckdefer.useraccountclasses

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import java.io.ByteArrayOutputStream

class ProfileRepository {
    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance("gs://duckduckdefer.firebasestorage.app")

    companion object {
        private const val TAG = "ProfileRepository"
        private const val USERS = "UserAccounts"
        private const val PROFILE_PICTURES = "profile_pictures"
        private const val MAX_IMAGE_SIZE = 512
        private const val COMPRESSION_QUALITY = 80
    }

    fun uploadProfilePicture(
        context: Context,
        userId: String,
        imageUri: Uri,
        onResult: (String?) -> Unit
    ) {
        try {
            // Verify Firebase Auth state
            val authUser = FirebaseAuth.getInstance().currentUser
            if (authUser == null) {
                Log.e(TAG, "Cannot upload: Firebase Auth user is NULL (not signed in)")
                onResult(null)
                return
            }
            Log.d(TAG, "Auth UID: ${authUser.uid}, Target userId: $userId, Match: ${authUser.uid == userId}")
            if (authUser.uid != userId) {
                Log.e(TAG, "UID MISMATCH: Auth UID '${authUser.uid}' != userId '$userId'. Storage rules will reject this write.")
            }

            // Read and compress the image
            val inputStream = context.contentResolver.openInputStream(imageUri)
            if (inputStream == null) {
                Log.e(TAG, "Failed to open input stream for URI: $imageUri")
                onResult(null)
                return
            }
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()

            if (originalBitmap == null) {
                Log.e(TAG, "Failed to decode image from URI: $imageUri")
                onResult(null)
                return
            }

            // Resize to max 512x512 while maintaining aspect ratio
            val resizedBitmap = resizeBitmap(originalBitmap, MAX_IMAGE_SIZE)

            // Compress to JPEG
            val outputStream = ByteArrayOutputStream()
            resizedBitmap.compress(Bitmap.CompressFormat.JPEG, COMPRESSION_QUALITY, outputStream)
            val imageData = outputStream.toByteArray()
            Log.d(TAG, "Image compressed to ${imageData.size} bytes for user $userId")

            // Upload to Firebase Storage
            val storageBucket = storage.reference.bucket
            Log.d(TAG, "Uploading to Firebase Storage bucket: $storageBucket, path: $PROFILE_PICTURES/$userId")

            val storageRef = storage.reference
                .child(PROFILE_PICTURES)
                .child(userId)

            val metadata = com.google.firebase.storage.StorageMetadata.Builder()
                .setContentType("image/jpeg")
                .build()

            storageRef.putBytes(imageData, metadata)
                .addOnSuccessListener {
                    Log.d(TAG, "Upload successful, getting download URL...")
                    storageRef.downloadUrl.addOnSuccessListener { downloadUrl ->
                        val url = downloadUrl.toString()
                        Log.d(TAG, "Download URL obtained: $url")
                        // Update user document with new profile picture URL
                        updateProfilePictureUrl(userId, url) { success ->
                            if (success) {
                                Log.d(TAG, "Profile picture URL saved to Firestore")
                                onResult(url)
                            } else {
                                Log.e(TAG, "Failed to save URL to Firestore")
                                onResult(null)
                            }
                        }
                    }.addOnFailureListener { e ->
                        Log.e(TAG, "Failed to get download URL", e)
                        onResult(null)
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to upload image to Firebase Storage. Bucket: $storageBucket, Error: ${e.message}", e)
                    onResult(null)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing image for upload", e)
            onResult(null)
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "OutOfMemoryError processing image - image too large", e)
            onResult(null)
        }
    }

    private fun resizeBitmap(bitmap: Bitmap, maxSize: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        if (width <= maxSize && height <= maxSize) {
            return bitmap
        }

        val ratio = width.toFloat() / height.toFloat()
        val newWidth: Int
        val newHeight: Int

        if (width > height) {
            newWidth = maxSize
            newHeight = (maxSize / ratio).toInt()
        } else {
            newHeight = maxSize
            newWidth = (maxSize * ratio).toInt()
        }

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    private fun updateProfilePictureUrl(userId: String, url: String, onComplete: (Boolean) -> Unit) {
        db.collection(USERS).document(userId)
            .update("profilePictureUrl", url)
            .addOnSuccessListener { onComplete(true) }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to update profile picture URL", e)
                onComplete(false)
            }
    }

    fun updateProfile(
        userId: String,
        biography: String,
        contactEmails: List<String>,
        phoneNumbers: List<String>,
        profilePictureUrl: String = "",
        onComplete: (Boolean) -> Unit
    ) {
        val updates = mapOf(
            "biography" to biography.take(500), // Enforce 500 char limit
            "contactEmails" to contactEmails,
            "phoneNumbers" to phoneNumbers,
            "profilePictureUrl" to profilePictureUrl
        )

        db.collection(USERS).document(userId)
            .update(updates)
            .addOnSuccessListener { onComplete(true) }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to update profile", e)
                onComplete(false)
            }
    }

    fun getUserProfile(userId: String, onResult: (UserMembersClass?) -> Unit) {
        db.collection(USERS).document(userId)
            .get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    // Try to get as the generic class first as it's the safest for the base UI
                    val user = doc.toObject(UserMembersClass::class.java)
                    if (user != null) {
                        user.thisUID = userId
                        onResult(user)
                    } else {
                        Log.e(TAG, "Failed to deserialize UserAccounts document: $userId")
                        onResult(null)
                    }
                } else {
                    Log.e(TAG, "User document does not exist: $userId")
                    onResult(null)
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Firestore error fetching user profile: $userId", e)
                onResult(null)
            }
    }

    fun getInitials(name: String): String {
        val parts = name.trim().split(" ").filter { it.isNotEmpty() }
        return when {
            parts.isEmpty() -> "?"
            parts.size == 1 -> parts[0].take(2).uppercase()
            else -> "${parts.first().first()}${parts.last().first()}".uppercase()
        }
    }
}
