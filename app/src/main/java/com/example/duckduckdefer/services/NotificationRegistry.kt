package com.example.duckduckdefer.services

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Singleton to track which screens or conversations are currently active in the UI.
 * This helps the FCM service decide whether to show a heads-up notification.
 */
object NotificationRegistry {
    private val _activeConversationId = MutableStateFlow<String?>(null)
    val activeConversationId = _activeConversationId.asStateFlow()

    private val _isAppInForeground = MutableStateFlow(false)
    val isAppInForeground = _isAppInForeground.asStateFlow()

    fun setActiveConversation(id: String?) {
        _activeConversationId.value = id
    }

    fun setAppForeground(inForeground: Boolean) {
        _isAppInForeground.value = inForeground
    }
}
