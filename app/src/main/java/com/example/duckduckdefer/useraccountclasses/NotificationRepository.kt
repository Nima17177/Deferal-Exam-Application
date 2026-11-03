package com.example.duckduckdefer.useraccountclasses

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Repository for managing in-app notifications stored in Firestore.
 * Notifications are stored per-user and can be viewed, marked as read, or deleted.
 */
class NotificationRepository {
    private val db = FirebaseFirestore.getInstance()

    companion object {
        private const val TAG = "NotificationRepository"
        private const val NOTIFICATIONS_COLLECTION = "UserNotifications"
    }

    /**
     * Create a test notification for debugging purposes.
     * Call this to verify the notification system is working.
     */
    fun createTestNotification(userId: String, onComplete: (Boolean) -> Unit = {}) {
        saveNotification(
            userId = userId,
            title = "Test Notification",
            body = "This is a test notification to verify the in-app notification center is working correctly.",
            type = "system",
            data = emptyMap(),
            onComplete = onComplete
        )
    }

    /**
     * Save a notification to Firestore for later viewing in the app.
     * Called from FCM service when a notification is received.
     */
    fun saveNotification(
        userId: String,
        title: String,
        body: String,
        type: String,
        data: Map<String, String> = emptyMap(),
        onComplete: (Boolean) -> Unit = {}
    ) {
        if (userId.isBlank()) {
            Log.e(TAG, "Cannot save notification: userId is blank")
            onComplete(false)
            return
        }

        val notification = hashMapOf(
            "userId" to userId,
            "title" to title,
            "body" to body,
            "type" to type,
            "isRead" to false,
            "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
            "conversationId" to (data["conversationId"] ?: ""),
            "bookingId" to (data["bookingId"] ?: ""),
            "senderId" to (data["senderId"] ?: ""),
            "senderName" to (data["senderName"] ?: ""),
            "senderPhoto" to (data["senderPhoto"] ?: "")
        )

        Log.d(TAG, "Saving notification for user $userId: $title")

        db.collection(NOTIFICATIONS_COLLECTION)
            .document(userId)
            .collection("notifications")
            .add(notification)
            .addOnSuccessListener { docRef ->
                Log.d(TAG, "Notification saved successfully with ID: ${docRef.id}")
                onComplete(true)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to save notification: ${e.message}", e)
                onComplete(false)
            }
    }

    /**
     * Get a real-time flow of notifications for a user, ordered by most recent first.
     */
    fun getNotificationsFlow(userId: String): Flow<List<InAppNotification>> = callbackFlow {
        val listener = db.collection(NOTIFICATIONS_COLLECTION)
            .document(userId)
            .collection("notifications")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(100) // Reasonable limit
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error listening to notifications: ${error.message}")
                    return@addSnapshotListener
                }

                val notifications = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(InAppNotification::class.java)
                } ?: emptyList()

                trySend(notifications)
            }

        awaitClose { listener.remove() }
    }

    /**
     * Get the count of unread notifications for badge display.
     */
    fun getUnreadCountFlow(userId: String): Flow<Int> = callbackFlow {
        val listener = db.collection(NOTIFICATIONS_COLLECTION)
            .document(userId)
            .collection("notifications")
            .whereEqualTo("isRead", false)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error listening to unread count: ${error.message}")
                    trySend(0)
                    return@addSnapshotListener
                }
                trySend(snapshot?.size() ?: 0)
            }

        awaitClose { listener.remove() }
    }

    /**
     * Mark a single notification as read.
     */
    fun markAsRead(userId: String, notificationId: String, onComplete: (Boolean) -> Unit = {}) {
        db.collection(NOTIFICATIONS_COLLECTION)
            .document(userId)
            .collection("notifications")
            .document(notificationId)
            .update("isRead", true)
            .addOnSuccessListener { onComplete(true) }
            .addOnFailureListener { onComplete(false) }
    }

    /**
     * Mark all notifications as read for a user.
     */
    fun markAllAsRead(userId: String, onComplete: (Boolean) -> Unit = {}) {
        db.collection(NOTIFICATIONS_COLLECTION)
            .document(userId)
            .collection("notifications")
            .whereEqualTo("isRead", false)
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.isEmpty) {
                    onComplete(true)
                    return@addOnSuccessListener
                }

                val batch = db.batch()
                snapshot.documents.forEach { doc ->
                    batch.update(doc.reference, "isRead", true)
                }
                batch.commit()
                    .addOnSuccessListener { onComplete(true) }
                    .addOnFailureListener { onComplete(false) }
            }
            .addOnFailureListener { onComplete(false) }
    }

    /**
     * Delete a single notification.
     */
    fun deleteNotification(userId: String, notificationId: String, onComplete: (Boolean) -> Unit = {}) {
        db.collection(NOTIFICATIONS_COLLECTION)
            .document(userId)
            .collection("notifications")
            .document(notificationId)
            .delete()
            .addOnSuccessListener { onComplete(true) }
            .addOnFailureListener { onComplete(false) }
    }

    /**
     * Delete all notifications for a user.
     */
    fun deleteAllNotifications(userId: String, onComplete: (Boolean) -> Unit = {}) {
        db.collection(NOTIFICATIONS_COLLECTION)
            .document(userId)
            .collection("notifications")
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.isEmpty) {
                    onComplete(true)
                    return@addOnSuccessListener
                }

                val batch = db.batch()
                snapshot.documents.forEach { doc ->
                    batch.delete(doc.reference)
                }
                batch.commit()
                    .addOnSuccessListener { onComplete(true) }
                    .addOnFailureListener { onComplete(false) }
            }
            .addOnFailureListener { onComplete(false) }
    }

    /**
     * Delete notifications older than a certain number of days.
     */
    fun deleteOldNotifications(userId: String, daysOld: Int = 30, onComplete: (Boolean) -> Unit = {}) {
        val cutoffDate = com.google.firebase.Timestamp(
            java.util.Date(System.currentTimeMillis() - (daysOld * 24 * 60 * 60 * 1000L))
        )

        db.collection(NOTIFICATIONS_COLLECTION)
            .document(userId)
            .collection("notifications")
            .whereLessThan("timestamp", cutoffDate)
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.isEmpty) {
                    onComplete(true)
                    return@addOnSuccessListener
                }

                val batch = db.batch()
                snapshot.documents.forEach { doc ->
                    batch.delete(doc.reference)
                }
                batch.commit()
                    .addOnSuccessListener { onComplete(true) }
                    .addOnFailureListener { onComplete(false) }
            }
            .addOnFailureListener { onComplete(false) }
    }
}



