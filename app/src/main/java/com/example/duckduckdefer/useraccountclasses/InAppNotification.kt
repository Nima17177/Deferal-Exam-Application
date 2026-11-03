package com.example.duckduckdefer.useraccountclasses

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp

/**
 * Data class representing an in-app notification stored in Firestore.
 * These are saved when push notifications arrive so users can view them later.
 */
data class InAppNotification(
    @DocumentId
    val id: String = "",
    val userId: String = "",
    val title: String = "",
    val body: String = "",
    val type: String = "system", // chat, booking_created, status_update, incident, proctor_assigned, system
    val isRead: Boolean = false,
    @ServerTimestamp
    val timestamp: Timestamp? = null,
    
    // Optional navigation data
    val conversationId: String? = null,
    val bookingId: String? = null,
    val senderId: String? = null,
    val senderName: String? = null,
    val senderPhoto: String? = null
) {
    /**
     * Returns a display-friendly category name based on notification type
     */
    fun getCategoryDisplayName(): String = when (type) {
        "chat" -> "Message"
        "booking_created" -> "New Booking"
        "status_update" -> "Booking Update"
        "incident" -> "Incident Report"
        "proctor_assigned" -> "Proctor Assignment"
        else -> "System"
    }

    /**
     * Returns a Material Icon name suggestion for UI rendering
     */
    fun getIconType(): NotificationIconType = when (type) {
        "chat" -> NotificationIconType.MESSAGE
        "booking_created" -> NotificationIconType.BOOKING
        "status_update" -> NotificationIconType.UPDATE
        "incident" -> NotificationIconType.WARNING
        "proctor_assigned" -> NotificationIconType.ASSIGNMENT
        else -> NotificationIconType.INFO
    }
}

enum class NotificationIconType {
    MESSAGE,
    BOOKING,
    UPDATE,
    WARNING,
    ASSIGNMENT,
    INFO
}

