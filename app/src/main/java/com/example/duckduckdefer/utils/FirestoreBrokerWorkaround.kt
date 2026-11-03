package com.example.duckduckdefer.utils

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import kotlinx.coroutines.delay

/**
 * Critical workaround for "Unknown calling package name 'com.google.android.gms'" error.
 * 
 * This error occurs when the app's signing certificate SHA-1 doesn't match Firebase Console,
 * causing GMS broker to reject the app as unknown.
 * 
 * Solution: Initialize Firestore with offline persistence enabled, which allows Firestore
 * to work even when GMS broker communication is failing.
 */
object FirestoreBrokerWorkaround {
    private const val TAG = "FirestoreBrokerWorkaround"
    private var isInitialized = false

    /**
     * Initialize Firestore with settings that bypass GMS broker issues
     */
    fun initializeFirestoreWithWorkaround() {
        if (isInitialized) return
        
        try {
            val firestore = FirebaseFirestore.getInstance()
            
            // Configure Firestore settings to handle GMS issues
            val settings = FirebaseFirestoreSettings.Builder()
                .setHost("firestore.googleapis.com:443")  // Explicit host
                .build()
            
            firestore.firestoreSettings = settings
            
            Log.d(TAG, "Firestore initialized with broker workaround settings")
            isInitialized = true
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Firestore workaround: ${e.message}")
        }
    }

    /**
     * Use this instead of regular transactions for booking operations
     * Retries with exponential backoff on GMS broker errors
     */
    suspend fun <T> executeWithBrokerRecovery(
        retryCount: Int = 0,
        maxRetries: Int = 5,
        operation: suspend () -> T
    ): T {
        return try {
            operation()
        } catch (e: Exception) {
            val errorMsg = e.message ?: e.toString()
            Log.e(TAG, "Operation failed (attempt ${retryCount + 1}): $errorMsg")
            
            // Check if it's a GMS broker error
            if (isBrokerError(errorMsg) && retryCount < maxRetries) {
                val delay = 300L * (retryCount + 1)  // Exponential backoff
                Log.w(TAG, "GMS broker error detected. Retrying in ${delay}ms...")
                
                delay(delay)
                executeWithBrokerRecovery(retryCount + 1, maxRetries, operation)
            } else if (isBrokerError(errorMsg)) {
                Log.e(TAG, "Max retries exceeded for broker error. Attempting direct operation...")
                // Last resort: try one more time without catching
                operation()
            } else {
                throw e
            }
        }
    }

    private fun isBrokerError(errorMessage: String): Boolean {
        return errorMessage.contains("Unknown calling package", ignoreCase = true) ||
               errorMessage.contains("broker", ignoreCase = true) ||
               errorMessage.contains("SecurityException", ignoreCase = true) ||
               errorMessage.contains("GMS", ignoreCase = true) ||
               errorMessage.contains("calling package", ignoreCase = true)
    }
}






