package com.example.duckduckdefer.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.duckduckdefer.useraccountclasses.UserMembersClass
import com.example.duckduckdefer.viewmodels.MessagingViewModel

/**
 * Reusable button to start a conversation with a user.
 * This can be used anywhere in the app where you want to provide
 * a "message this user" action.
 *
 * @param currentUser The logged-in user
 * @param targetUser The user to message
 * @param viewModel MessagingViewModel instance
 * @param onConversationStarted Callback with the conversation ID when started
 * @param enabled Whether the button is enabled
 * @param modifier Modifier for the button
 */
@Composable
fun MessageUserButton(
    currentUser: UserMembersClass,
    targetUser: UserMembersClass,
    viewModel: MessagingViewModel,
    onConversationStarted: (String) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    buttonStyle: MessageButtonStyle = MessageButtonStyle.FILLED
) {
    var isLoading by remember { mutableStateOf(false) }

    when (buttonStyle) {
        MessageButtonStyle.FILLED -> {
            Button(
                onClick = {
                    isLoading = true
                    viewModel.startConversation(currentUser, targetUser) { conversationId ->
                        isLoading = false
                        conversationId?.let { onConversationStarted(it) }
                    }
                },
                enabled = enabled && !isLoading,
                modifier = modifier
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Email,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text("Message")
            }
        }

        MessageButtonStyle.OUTLINED -> {
            OutlinedButton(
                onClick = {
                    isLoading = true
                    viewModel.startConversation(currentUser, targetUser) { conversationId ->
                        isLoading = false
                        conversationId?.let { onConversationStarted(it) }
                    }
                },
                enabled = enabled && !isLoading,
                modifier = modifier
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Email,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text("Message")
            }
        }

        MessageButtonStyle.ICON_ONLY -> {
            IconButton(
                onClick = {
                    isLoading = true
                    viewModel.startConversation(currentUser, targetUser) { conversationId ->
                        isLoading = false
                        conversationId?.let { onConversationStarted(it) }
                    }
                },
                enabled = enabled && !isLoading,
                modifier = modifier
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Email,
                        contentDescription = "Message ${targetUser.thisUserName}"
                    )
                }
            }
        }
    }
}

enum class MessageButtonStyle {
    FILLED,
    OUTLINED,
    ICON_ONLY
}
