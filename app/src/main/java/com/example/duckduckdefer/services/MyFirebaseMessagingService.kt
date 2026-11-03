package com.example.duckduckdefer.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.duckduckdefer.MainActivity
import com.example.duckduckdefer.R
import com.example.duckduckdefer.useraccountclasses.MessagingRepository
import com.example.duckduckdefer.useraccountclasses.NotificationRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import java.net.HttpURLConnection
import java.net.URL

class MyFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "FCMService"
        
        // Channel IDs
        const val CHANNEL_MESSAGES_ID = "messages_channel"
        const val CHANNEL_BOOKINGS_ID = "bookings_channel"
        const val CHANNEL_SYSTEM_ID = "system_channel"

        // Quack-variant channel IDs (same importance, custom sound)
        const val CHANNEL_MESSAGES_QUACK_ID = "messages_channel_quack"
        const val CHANNEL_BOOKINGS_QUACK_ID = "bookings_channel_quack"

        // Group Keys
        const val GROUP_KEY_MESSAGES = "com.example.duckduckdefer.MESSAGES"
        const val GROUP_KEY_BOOKINGS = "com.example.duckduckdefer.BOOKINGS"
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New FCM token: $token")
        FirebaseAuth.getInstance().currentUser?.uid?.let { userId ->
            MessagingRepository().updateFcmToken(userId, token)
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        
        val data = remoteMessage.data
        val type = data["type"]
        val title = data["title"] ?: remoteMessage.notification?.title ?: "DuckDuckDefer"
        val body = data["body"] ?: remoteMessage.notification?.body ?: "You have a new update"
        
        // Handle recipient check if recipientId is present
        val recipientId = data["recipientId"]
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
        if (recipientId != null && currentUserId != null && recipientId != currentUserId) {
            return
        }

        // Always save notification to in-app notification center (even if suppressed from system tray)
        saveToNotificationCenter(title, body, type ?: "system", data)

        // Foreground Intelligence: Suppress system notifications if the user is already looking at the content
        if (NotificationRegistry.isAppInForeground.value && shouldSuppressNotification(type, data)) {
            Log.d(TAG, "System notification suppressed: User is currently on the relevant screen. Saved to in-app center.")
            return
        }

        showSystemNotification(title, body, data)
    }

    private fun shouldSuppressNotification(type: String?, data: Map<String, String>): Boolean {
        return when (type) {
            "chat" -> {
                val incomingConvId = data["conversationId"]
                val activeConvId = NotificationRegistry.activeConversationId.value
                incomingConvId != null && incomingConvId == activeConvId
            }
            else -> false
        }
    }

    private fun showSystemNotification(title: String, body: String, data: Map<String, String>) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val type = data["type"] ?: "system"

        // Check user's notification sound preference (cached in SharedPreferences)
        val soundPref = SoundPreferenceCache.get(this)
        val useQuack = soundPref == "quack"

        createNotificationChannels(notificationManager)

        val channelId = when (type) {
            "chat" -> if (useQuack) CHANNEL_MESSAGES_QUACK_ID else CHANNEL_MESSAGES_ID
            "booking_created", "status_update", "incident", "proctor_assigned", "proctor_needed" ->
                if (useQuack) CHANNEL_BOOKINGS_QUACK_ID else CHANNEL_BOOKINGS_ID
            else -> CHANNEL_SYSTEM_ID
        }

        val groupKey = when (type) {
            "chat" -> GROUP_KEY_MESSAGES
            else -> GROUP_KEY_BOOKINGS
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            data.forEach { (key, value) ->
                putExtra(key, value)
            }
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val quackSoundUri = Uri.parse("android.resource://${packageName}/${R.raw.quack}")

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.duck_logo2)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setGroup(groupKey)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        if (useQuack) {
            // Set custom quack sound (for pre-O and as a hint)
            builder.setSound(quackSoundUri)
            builder.setDefaults(NotificationCompat.DEFAULT_VIBRATE)
        } else {
            builder.setDefaults(NotificationCompat.DEFAULT_ALL)
        }

        // Rich Content: Large Icon for sender
        data["senderPhoto"]?.let { photoUrl ->
            if (photoUrl.isNotBlank()) {
                getBitmapFromUrl(photoUrl)?.let { bitmap ->
                    builder.setLargeIcon(bitmap)
                }
            }
        }

        val notificationId = data["bookingId"]?.hashCode() ?: data["conversationId"]?.hashCode() ?: System.currentTimeMillis().toInt()
        notificationManager.notify(notificationId, builder.build())

        // Create a summary notification for the group
        val summaryNotification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.duck_logo2)
            .setGroup(groupKey)
            .setGroupSummary(true)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(groupKey.hashCode(), summaryNotification)
    }

    private fun createNotificationChannels(notificationManager: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val quackSoundUri = Uri.parse("android.resource://${packageName}/${R.raw.quack}")
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .build()

            val channels = listOf(
                // Default channels
                NotificationChannel(
                    CHANNEL_MESSAGES_ID,
                    "Messages",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Instant alerts for new chat messages"
                    enableVibration(true)
                    setShowBadge(true)
                },
                NotificationChannel(
                    CHANNEL_BOOKINGS_ID,
                    "Exam Updates",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Critical alerts for exam bookings and status changes"
                    enableVibration(true)
                },
                NotificationChannel(
                    CHANNEL_SYSTEM_ID,
                    "General",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Other non-critical system updates"
                },

                // Quack sound channels
                NotificationChannel(
                    CHANNEL_MESSAGES_QUACK_ID,
                    "Messages (Quack 🦆)",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Chat messages with Quack notification sound"
                    enableVibration(true)
                    setShowBadge(true)
                    setSound(quackSoundUri, audioAttributes)
                },
                NotificationChannel(
                    CHANNEL_BOOKINGS_QUACK_ID,
                    "Exam Updates (Quack 🦆)",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Exam alerts with Quack notification sound"
                    enableVibration(true)
                    setSound(quackSoundUri, audioAttributes)
                }
            )
            notificationManager.createNotificationChannels(channels)
        }
    }

    private fun getBitmapFromUrl(imageUrl: String): Bitmap? {
        return try {
            val url = URL(imageUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.doInput = true
            connection.connect()
            BitmapFactory.decodeStream(connection.inputStream)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Save the notification to Firestore so it can be viewed in the in-app notification center.
     */
    private fun saveToNotificationCenter(title: String, body: String, type: String, data: Map<String, String>) {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        
        NotificationRepository().saveNotification(
            userId = currentUserId,
            title = title,
            body = body,
            type = type,
            data = data
        ) { success ->
            if (success) {
                Log.d(TAG, "Notification saved to in-app center")
            } else {
                Log.w(TAG, "Failed to save notification to in-app center")
            }
        }
    }
}
