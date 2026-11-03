package com.example.duckduckdefer.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.duckduckdefer.useraccountclasses.InAppNotification
import com.example.duckduckdefer.useraccountclasses.NotificationRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the Notification Center feature.
 * Manages in-app notifications display and actions.
 */
class NotificationCenterViewModel : ViewModel() {
    private val repository = NotificationRepository()

    private val _notifications = MutableStateFlow<List<InAppNotification>>(emptyList())
    val notifications: StateFlow<List<InAppNotification>> = _notifications.asStateFlow()

    private val _unreadCount = MutableStateFlow(0)
    val unreadCount: StateFlow<Int> = _unreadCount.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var currentUserId: String? = null
    private var notificationsJob: Job? = null
    private var unreadCountJob: Job? = null

    /**
     * Initialize the ViewModel with the current user's ID.
     * Sets up real-time listeners for notifications.
     */
    fun initialize(userId: String) {
        if (currentUserId == userId) return

        currentUserId = userId
        loadNotifications(userId)
        loadUnreadCount(userId)
    }

    private fun loadNotifications(userId: String) {
        notificationsJob?.cancel()
        notificationsJob = viewModelScope.launch {
            _isLoading.value = true
            repository.getNotificationsFlow(userId).collect { notifications ->
                _notifications.value = notifications
                _isLoading.value = false
            }
        }
    }

    private fun loadUnreadCount(userId: String) {
        unreadCountJob?.cancel()
        unreadCountJob = viewModelScope.launch {
            repository.getUnreadCountFlow(userId).collect { count ->
                _unreadCount.value = count
            }
        }
    }

    /**
     * Mark a single notification as read.
     */
    fun markAsRead(notificationId: String) {
        currentUserId?.let { userId ->
            repository.markAsRead(userId, notificationId)
        }
    }

    /**
     * Mark all notifications as read.
     */
    fun markAllAsRead() {
        currentUserId?.let { userId ->
            repository.markAllAsRead(userId)
        }
    }

    /**
     * Delete a single notification.
     */
    fun deleteNotification(notificationId: String) {
        currentUserId?.let { userId ->
            repository.deleteNotification(userId, notificationId)
        }
    }

    /**
     * Delete all notifications.
     */
    fun deleteAllNotifications() {
        currentUserId?.let { userId ->
            repository.deleteAllNotifications(userId)
        }
    }

    /**
     * Clean up old notifications (30+ days old).
     */
    fun cleanupOldNotifications() {
        currentUserId?.let { userId ->
            repository.deleteOldNotifications(userId, 30)
        }
    }

    /**
     * Clean up resources when ViewModel is cleared.
     */
    override fun onCleared() {
        super.onCleared()
        notificationsJob?.cancel()
        unreadCountJob?.cancel()
    }
}

