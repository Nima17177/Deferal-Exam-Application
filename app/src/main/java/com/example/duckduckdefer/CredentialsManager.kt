package com.example.duckduckdefer

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

class CredentialsManager(context: Context) {
    private val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
    private val sharedPreferences = try {
        createEncryptedSharedPreferences(context)
    } catch (e: Exception) {
        Log.e("CredentialsManager", "Error initializing EncryptedSharedPreferences, clearing and retrying", e)
        // Clear the corrupted shared preferences file
        context.getSharedPreferences("secure_prefs", Context.MODE_PRIVATE).edit().clear().apply()
        // Try creating it again
        createEncryptedSharedPreferences(context)
    }

    private fun createEncryptedSharedPreferences(context: Context) = EncryptedSharedPreferences.create(
        "secure_prefs",
        masterKeyAlias,
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveCredentials(email: String, password: String) {
        sharedPreferences.edit().apply {
            putString("email", email)
            putString("password", password)
            apply()
        }
    }

    fun getCredentials(): Pair<String?, String?> {
        val email = sharedPreferences.getString("email", null)
        val password = sharedPreferences.getString("password", null)
        return Pair(email, password)
    }

    fun clearCredentials() {
        sharedPreferences.edit().clear().apply()
    }

    fun hasCredentials(): Boolean {
        return sharedPreferences.contains("email") && sharedPreferences.contains("password")
    }
}
