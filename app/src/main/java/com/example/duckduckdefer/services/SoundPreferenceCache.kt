package com.example.duckduckdefer.services

import android.content.Context

/**
 * Simple SharedPreferences cache so the FCM background service
 * can quickly check which notification sound the user has equipped
 * without hitting Firestore on every notification.
 */
object SoundPreferenceCache {
    private const val PREFS_NAME = "notification_prefs"
    private const val KEY_SOUND = "notificationSound"

    /** Call this after login and whenever the user equips a new sound. */
    fun save(context: Context, soundName: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SOUND, soundName)
            .apply()
    }

    /** Read the cached value (defaults to "default"). */
    fun get(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_SOUND, "default") ?: "default"
    }
}

