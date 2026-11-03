package com.example.duckduckdefer.utils

import android.content.Context
import android.util.Log
import com.google.android.gms.common.GoogleApiAvailability
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Helper utility for handling Firebase and Google Play Services (GMS) initialization issues.
 * Provides methods to check GMS availability and handle GMS-related errors gracefully.
 */
object FirebaseGMSHelper {
    private const val TAG = "FirebaseGMSHelper"

    /**
     * Check if Google Play Services is available and properly initialized.
     * This helps diagnose GMS-related issues early.
     */
    fun isGooglePlayServicesAvailable(context: Context): Boolean {
        return try {
            val result = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context)
            val available = result == com.google.android.gms.common.ConnectionResult.SUCCESS
            if (!available) {
                Log.w(TAG, "Google Play Services is not available. Error code: $result")
            }
            available
        } catch (e: Exception) {
            Log.e(TAG, "Error checking Google Play Services availability: ${e.message}")
            false
        }
    }

    /**
     * Check if an error is GMS-related (e.g., SecurityException with "Unknown calling package")
     */
    fun isGMSRelatedError(error: Throwable): Boolean {
        val errorMessage = error.message ?: return false
        return errorMessage.contains("Unknown calling package", ignoreCase = true) ||
               errorMessage.contains("SecurityException", ignoreCase = true) ||
               errorMessage.contains("GMS", ignoreCase = true) ||
               errorMessage.contains("google.android.gms", ignoreCase = true) ||
               error is SecurityException
    }

    /**
     * Get a user-friendly error message for GMS-related errors
     */
    fun getGMSErrorMessage(error: Throwable): String {
        return when {
            error.message?.contains("Unknown calling package", ignoreCase = true) == true ->
                "Google Play Services configuration issue. Please update Google Play Services or restart the app."
            error.message?.contains("SecurityException", ignoreCase = true) == true ->
                "Security error with Google Play Services. Your device may need a service update."
            error is SecurityException ->
                "Google Play Services security error. Please ensure Google Play Services is up to date."
            else -> "Google Play Services error: ${error.message}"
        }
    }

    /**
     * Initialize Firestore with proper error handling
     */
    fun initializeFirestore(): FirebaseFirestore {
        return try {
            val firestore = FirebaseFirestore.getInstance()
            // Firestore initialization is automatic, no need to set persistence settings
            // which have been deprecated
            Log.d(TAG, "Firestore initialized successfully")
            firestore
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Firestore: ${e.message}", e)
            FirebaseFirestore.getInstance()
        }
    }
}


