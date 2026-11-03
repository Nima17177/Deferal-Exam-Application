package com.example.duckduckdefer.useraccountclasses

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp

data class Conversation(
    @DocumentId
    val id: String = "",
    val participants: List<String> = emptyList(), // List of user UIDs
    val participantNames: Map<String, String> = emptyMap(), // UID -> Display Name
    val participantEmails: Map<String, String> = emptyMap(), // UID -> Email
    val participantAvatars: Map<String, String> = emptyMap(), // UID -> Profile Picture URL
    val lastMessage: String = "",
    val lastSenderId: String = "",
    @ServerTimestamp
    val lastMessageTime: Timestamp? = null,
    val unreadCount: Map<String, Int> = emptyMap(), // UID -> unread count for that user
    val createdBy: String = "", // UID of user who created the conversation
    val hasMessages: Boolean = false // True after at least one message is sent
)
