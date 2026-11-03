package com.example.duckduckdefer.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.duckduckdefer.useraccountclasses.Conversation
import com.example.duckduckdefer.useraccountclasses.Message
import com.example.duckduckdefer.useraccountclasses.MessagingRepository
import com.example.duckduckdefer.useraccountclasses.UserMembersClass
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MessagingViewModel : ViewModel() {
    private val repository = MessagingRepository()

    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
    val conversations: StateFlow<List<Conversation>> = _conversations.asStateFlow()

    private val _currentMessages = MutableStateFlow<List<Message>>(emptyList())
    val currentMessages: StateFlow<List<Message>> = _currentMessages.asStateFlow()

    private val _totalUnreadCount = MutableStateFlow(0)
    val totalUnreadCount: StateFlow<Int> = _totalUnreadCount.asStateFlow()

    private val _searchResults = MutableStateFlow<List<UserMembersClass>>(emptyList())
    val searchResults: StateFlow<List<UserMembersClass>> = _searchResults.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _currentConversation = MutableStateFlow<Conversation?>(null)
    val currentConversation: StateFlow<Conversation?> = _currentConversation.asStateFlow()

    private val _participantProfiles = MutableStateFlow<Map<String, UserMembersClass>>(emptyMap())
    val participantProfiles: StateFlow<Map<String, UserMembersClass>> = _participantProfiles.asStateFlow()

    private var currentUserId: String? = null
    private var isInitialized = false
    
    private var messagesJob: Job? = null
    private var conversationsJob: Job? = null
    private var unreadCountJob: Job? = null

    companion object {
    }

    fun initialize(userId: String) {
        if (currentUserId != userId) {
            isInitialized = false
            cleanup() // Clear old data if user changed
        }

        if (isInitialized) return

        currentUserId = userId
        isInitialized = true
        loadConversations(userId)
        loadUnreadCount(userId)
    }

    fun refreshConversations() {
        currentUserId?.let { loadConversations(it) }
    }

    private fun loadConversations(userId: String) {
        conversationsJob?.cancel()
        conversationsJob = viewModelScope.launch {
            repository.getConversationsFlow(userId).collect { conversations ->
                _conversations.value = conversations
            }
        }
    }

    private fun loadUnreadCount(userId: String) {
        unreadCountJob?.cancel()
        unreadCountJob = viewModelScope.launch {
            repository.getTotalUnreadCountFlow(userId).collect { count ->
                _totalUnreadCount.value = count
            }
        }
    }

    fun loadMessages(conversationId: String) {
        _currentConversation.value = null
        _currentMessages.value = emptyList()
        
        messagesJob?.cancel()
        messagesJob = viewModelScope.launch {
            repository.getMessagesFlow(conversationId).collect { messages ->
                _currentMessages.value = messages
                
                // Mark messages as read when they arrive while the chat is open
                currentUserId?.let { userId ->
                    val hasUnread = messages.any { !it.isRead && it.senderId != userId }
                    if (hasUnread) {
                        repository.markMessagesAsRead(conversationId, userId)
                    }
                }
            }
        }

        repository.getConversation(conversationId) { conversation ->
            _currentConversation.value = conversation
        }
    }

    fun sendMessage(conversationId: String, senderName: String, content: String) {
        val userId = currentUserId ?: return
        if (content.isBlank()) return

        repository.sendMessage(conversationId, userId, senderName, content) { _ -> }
    }

    fun searchUsers(email: String, currentUserRole: Int = -1) {
        if (email.isBlank()) {
            _searchResults.value = emptyList()
            return
        }

        _isLoading.value = true
        repository.searchUsersByEmail(email, currentUserRole) { users ->
            _searchResults.value = users
            _isLoading.value = false
        }
    }

    fun clearSearch() {
        _searchResults.value = emptyList()
        _isLoading.value = false
    }

    fun startConversation(currentUser: UserMembersClass, otherUser: UserMembersClass, onResult: (String?) -> Unit) {
        _isLoading.value = true
        repository.getOrCreateConversation(currentUser, otherUser) { conversationId ->
            _isLoading.value = false
            onResult(conversationId)
        }
    }

    fun markAsRead(conversationId: String) {
        currentUserId?.let { userId ->
            repository.markMessagesAsRead(conversationId, userId)
        }
    }

    fun getOtherParticipantId(conversation: Conversation): String? {
        val userId = currentUserId ?: return null
        return conversation.participants.find { it != userId }
    }

    fun getOtherParticipantName(conversation: Conversation): String {
        val otherUserId = getOtherParticipantId(conversation) ?: return "Unknown"
        return _participantProfiles.value[otherUserId]?.thisUserName 
            ?: conversation.participantNames[otherUserId] ?: "Unknown"
    }

    fun getOtherParticipantEmail(conversation: Conversation): String {
        val otherUserId = getOtherParticipantId(conversation) ?: return ""
        return conversation.participantEmails[otherUserId] ?: ""
    }

    fun getOtherParticipantAvatar(conversation: Conversation): String {
        val otherUserId = getOtherParticipantId(conversation) ?: return ""
        return _participantProfiles.value[otherUserId]?.profilePictureUrl 
            ?: conversation.participantAvatars[otherUserId] ?: ""
    }

    fun fetchParticipantProfile(userId: String) {
        if (_participantProfiles.value.containsKey(userId)) return
        
        viewModelScope.launch {
            repository.getUserById(userId) { user ->
                if (user != null) {
                    _participantProfiles.value = _participantProfiles.value + (userId to user)
                }
            }
        }
    }

    fun getUnreadCount(conversation: Conversation): Int {
        val userId = currentUserId ?: return 0
        return conversation.unreadCount[userId] ?: 0
    }

    fun cleanup() {
        currentUserId = null
        isInitialized = false
        messagesJob?.cancel()
        conversationsJob?.cancel()
        unreadCountJob?.cancel()
        _conversations.value = emptyList()
        _currentMessages.value = emptyList()
        _totalUnreadCount.value = 0
        _searchResults.value = emptyList()
        _currentConversation.value = null
        _participantProfiles.value = emptyMap()
        _isLoading.value = false
    }
}
