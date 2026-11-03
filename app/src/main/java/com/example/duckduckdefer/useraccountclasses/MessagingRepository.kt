package com.example.duckduckdefer.useraccountclasses

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class MessagingRepository {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    companion object {
        private const val TAG = "MessagingRepository"
        private const val CONVERSATIONS = "conversations"
        private const val MESSAGES = "messages"
        private const val USERS = "UserAccounts"
    }

    fun getCurrentUserId(): String? = auth.currentUser?.uid

    fun searchUsersByEmail(email: String, currentUserRole: Int = -1, onResult: (List<UserMembersClass>) -> Unit) {
        val currentUserId = getCurrentUserId() ?: return onResult(emptyList())

        db.collection(USERS)
            .whereGreaterThanOrEqualTo("thisEmail", email.lowercase())
            .whereLessThanOrEqualTo("thisEmail", email.lowercase() + "\uf8ff")
            .limit(10)
            .get()
            .addOnSuccessListener { snapshot ->
                val users = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(UserMembersClass::class.java)?.apply { thisUID = doc.id }
                }.filter { user ->
                    // Exclude current user and non-approved users
                    user.thisUID != currentUserId && user.thisApprovalStatus == 1 &&
                    // If current user is a student (role 3), filter out other students
                    !(currentUserRole == 3 && user.thisRole == 3)
                }
                onResult(users)
            }
            .addOnFailureListener { onResult(emptyList()) }
    }

    fun getOrCreateConversation(currentUser: UserMembersClass, otherUser: UserMembersClass, onResult: (String?) -> Unit) {
        db.collection(CONVERSATIONS)
            .whereArrayContains("participants", currentUser.thisUID)
            .get()
            .addOnSuccessListener { snapshot ->
                val existing = snapshot.documents.find { doc ->
                    (doc.get("participants") as? List<*>)?.contains(otherUser.thisUID) == true
                }

                if (existing != null) {
                    onResult(existing.id)
                } else {
                    val conv = hashMapOf(
                        "participants" to listOf(currentUser.thisUID, otherUser.thisUID),
                        "participantNames" to mapOf(currentUser.thisUID to currentUser.thisUserName, otherUser.thisUID to otherUser.thisUserName),
                        "participantEmails" to mapOf(currentUser.thisUID to currentUser.thisEmail, otherUser.thisUID to otherUser.thisEmail),
                        "participantAvatars" to mapOf(currentUser.thisUID to currentUser.profilePictureUrl, otherUser.thisUID to otherUser.profilePictureUrl),
                        "lastMessage" to "", "lastSenderId" to "",
                        "lastMessageTime" to FieldValue.serverTimestamp(),
                        "unreadCount" to mapOf(currentUser.thisUID to 0, otherUser.thisUID to 0),
                        "createdBy" to currentUser.thisUID,
                        "hasMessages" to false
                    )
                    db.collection(CONVERSATIONS).add(conv)
                        .addOnSuccessListener { onResult(it.id) }
                        .addOnFailureListener { onResult(null) }
                }
            }
            .addOnFailureListener { onResult(null) }
    }

    fun getConversationsFlow(userId: String): Flow<List<Conversation>> = callbackFlow {
        val listener = db.collection(CONVERSATIONS)
            .whereArrayContains("participants", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error loading conversations: ${error.message}", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                val conversations = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Conversation::class.java)?.copy(id = doc.id)
                }?.filter { conversation ->
                    // Show conversation if:
                    // 1. User created it (they should always see their own created conversations)
                    // 2. OR it has messages (the other user should only see it after a message is sent)
                    conversation.createdBy == userId || conversation.hasMessages
                }?.sortedByDescending { it.lastMessageTime } ?: emptyList()

                trySend(conversations)
            }
        awaitClose { listener.remove() }
    }

    fun getMessagesFlow(conversationId: String): Flow<List<Message>> = callbackFlow {
        val listener = db.collection(CONVERSATIONS).document(conversationId)
            .collection(MESSAGES).orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, _ ->
                val messages = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Message::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                trySend(messages)
            }
        awaitClose { listener.remove() }
    }

    fun sendMessage(conversationId: String, senderId: String, senderName: String, content: String, onResult: (Boolean) -> Unit) {
        val message = hashMapOf(
            "senderId" to senderId, "senderName" to senderName, "content" to content,
            "timestamp" to FieldValue.serverTimestamp(), "isRead" to false
        )
        db.collection(CONVERSATIONS).document(conversationId).collection(MESSAGES).add(message)
            .addOnSuccessListener {
                updateLastMessage(conversationId, senderId, content)
                // Mark conversation as having messages so it appears for the other user
                db.collection(CONVERSATIONS).document(conversationId).update("hasMessages", true)
                onResult(true)
            }
            .addOnFailureListener { onResult(false) }
    }

    private fun updateLastMessage(conversationId: String, senderId: String, content: String) {
        db.collection(CONVERSATIONS).document(conversationId).get()
            .addOnSuccessListener { doc ->
                val participants = doc.get("participants") as? List<String> ?: return@addOnSuccessListener
                val otherUserId = participants.find { it != senderId } ?: return@addOnSuccessListener
                val currentUnread = (doc.get("unreadCount") as? Map<String, Long>)?.get(otherUserId)?.toInt() ?: 0

                db.collection(CONVERSATIONS).document(conversationId).update(
                    mapOf("lastMessage" to content, "lastSenderId" to senderId,
                        "lastMessageTime" to FieldValue.serverTimestamp(),
                        "unreadCount.$otherUserId" to currentUnread + 1)
                )
            }
    }

    fun markMessagesAsRead(conversationId: String, userId: String) {
        db.collection(CONVERSATIONS).document(conversationId).update("unreadCount.$userId", 0)
        db.collection(CONVERSATIONS).document(conversationId).collection(MESSAGES)
            .whereEqualTo("isRead", false).whereNotEqualTo("senderId", userId).get()
            .addOnSuccessListener { snapshot ->
                val batch = db.batch()
                snapshot.documents.forEach { batch.update(it.reference, "isRead", true) }
                batch.commit()
            }
    }

    fun getTotalUnreadCountFlow(userId: String): Flow<Int> = callbackFlow {
        val listener = db.collection(CONVERSATIONS)
            .whereArrayContains("participants", userId)
            .addSnapshotListener { snapshot, _ ->
                var total = 0
                snapshot?.documents?.forEach { doc ->
                    total += (doc.get("unreadCount") as? Map<String, Long>)?.get(userId)?.toInt() ?: 0
                }
                trySend(total)
            }
        awaitClose { listener.remove() }
    }

    fun getConversation(conversationId: String, onResult: (Conversation?) -> Unit) {
        db.collection(CONVERSATIONS).document(conversationId).get()
            .addOnSuccessListener { doc -> onResult(doc.toObject(Conversation::class.java)?.copy(id = doc.id)) }
            .addOnFailureListener { onResult(null) }
    }

    fun updateFcmToken(userId: String, token: String) {
        Log.d(TAG, "Updating FCM token for user: $userId")
        db.collection(USERS).document(userId).update("fcmToken", token)
            .addOnSuccessListener {
                Log.d(TAG, "FCM token updated successfully for user: $userId")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to update FCM token: ${e.message}")
                // If the document doesn't exist or the field doesn't exist, try set with merge
                db.collection(USERS).document(userId)
                    .set(mapOf("fcmToken" to token), com.google.firebase.firestore.SetOptions.merge())
                    .addOnSuccessListener {
                        Log.d(TAG, "FCM token set with merge for user: $userId")
                    }
                    .addOnFailureListener { e2 ->
                        Log.e(TAG, "Failed to set FCM token with merge: ${e2.message}")
                    }
            }
    }

    fun clearFcmToken(userId: String, onComplete: () -> Unit = {}) {
        db.collection(USERS).document(userId).update("fcmToken", FieldValue.delete())
            .addOnSuccessListener {
                Log.d(TAG, "FCM token cleared for user: $userId")
                onComplete()
            }
            .addOnFailureListener {
                Log.e(TAG, "Failed to clear FCM token: ${it.message}")
                onComplete()
            }
    }

    fun getUserById(userId: String, onResult: (UserMembersClass?) -> Unit) {
        db.collection(USERS).document(userId).get()
            .addOnSuccessListener { doc -> onResult(doc.toObject(UserMembersClass::class.java)?.apply { thisUID = doc.id }) }
            .addOnFailureListener { onResult(null) }
    }
}
