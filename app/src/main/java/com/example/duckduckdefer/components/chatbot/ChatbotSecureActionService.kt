package com.example.duckduckdefer.components.chatbot

import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume

/**
 * Calls server-side callable functions used by chatbot actions.
 * Security-critical actions should run through this service, not direct client writes.
 */
class ChatbotSecureActionService {
    companion object {
        private const val TAG = "ChatbotSecureAction"
    }

    private val projectId: String?
        get() = FirebaseApp.getInstance().options.projectId

    private val region = "us-central1"

    suspend fun listSessionsByDate(dateID: String): String {
        val data = call("chatbotListSessionsHttp", mapOf("dateID" to dateID))
        return data["message"] as? String ?: "Unable to list sessions right now."
    }

    suspend fun bookSession(
        studentEmail: String,
        examName: String,
        dateID: String,
        roomName: String,
        startTime: String,
        endTime: String,
        facultyEmail: String?
    ): String {
        val payload = mutableMapOf<String, Any>(
            "studentEmail" to studentEmail,
            "examName" to examName,
            "dateID" to dateID,
            "roomName" to roomName,
            "startTime" to startTime,
            "endTime" to endTime
        )
        if (!facultyEmail.isNullOrBlank()) {
            payload["facultyEmail"] = facultyEmail
        }

        val data = call("chatbotBookSessionHttp", payload)
        return data["message"] as? String ?: "Booking request completed, but no message was returned."
    }

    // ── Phase 1: Student actions ─────────────────────────────

    suspend fun requestDeferral(courseName: String, reason: String): String {
        val data = call("chatbotRequestDeferralHttp", mapOf("courseName" to courseName, "reason" to reason))
        return data["message"] as? String ?: "Deferral request submitted."
    }

    suspend fun cancelDeferral(deferralId: String): String {
        val data = call("chatbotCancelDeferralHttp", mapOf("deferralId" to deferralId))
        return data["message"] as? String ?: "Deferral cancelled."
    }

    // ── Phase 2: Proctor actions ─────────────────────────────

    suspend fun updateAvailability(dateID: String, available: Boolean): String {
        val data = call("chatbotUpdateAvailabilityHttp", mapOf("dateID" to dateID, "available" to available))
        return data["message"] as? String ?: "Availability updated."
    }

    suspend fun checkinStudent(bookingId: String): String {
        val data = call("chatbotCheckinStudentHttp", mapOf("bookingId" to bookingId))
        return data["message"] as? String ?: "Student checked in."
    }

    suspend fun reportIncident(bookingId: String, description: String): String {
        val data = call("chatbotReportIncidentHttp", mapOf("bookingId" to bookingId, "description" to description))
        return data["message"] as? String ?: "Incident reported."
    }

    suspend fun markExamComplete(bookingId: String): String {
        val data = call("chatbotMarkExamCompleteHttp", mapOf("bookingId" to bookingId))
        return data["message"] as? String ?: "Exam marked complete."
    }

    // ── Phase 3: Enhanced Faculty actions ─────────────────────

    suspend fun cancelBooking(bookingId: String): String {
        val data = call("chatbotCancelBookingHttp", mapOf("bookingId" to bookingId))
        return data["message"] as? String ?: "Booking cancelled."
    }

    suspend fun approveDeferral(deferralId: String): String {
        val data = call("chatbotApproveDeferralHttp", mapOf("deferralId" to deferralId))
        return data["message"] as? String ?: "Deferral approved."
    }

    suspend fun rejectDeferral(deferralId: String): String {
        val data = call("chatbotRejectDeferralHttp", mapOf("deferralId" to deferralId))
        return data["message"] as? String ?: "Deferral rejected."
    }

    suspend fun sendMessageToUser(recipientEmail: String, messageText: String): String {
        val data = call("chatbotSendMessageHttp", mapOf("recipientEmail" to recipientEmail, "messageText" to messageText))
        return data["message"] as? String ?: "Message sent."
    }

    // ── Phase 4: Enhanced Admin actions ───────────────────────

    suspend fun approveAccount(email: String): String {
        val data = call("chatbotApproveAccountHttp", mapOf("email" to email))
        return data["message"] as? String ?: "Account approved."
    }

    suspend fun rejectAccount(email: String): String {
        val data = call("chatbotRejectAccountHttp", mapOf("email" to email))
        return data["message"] as? String ?: "Account rejected."
    }

    suspend fun assignProctor(proctorEmail: String, dateID: String, roomName: String): String {
        val data = call("chatbotAssignProctorHttp", mapOf(
            "proctorEmail" to proctorEmail, "dateID" to dateID, "roomName" to roomName
        ))
        return data["message"] as? String ?: "Proctor assigned."
    }

    suspend fun sendAnnouncement(message: String, targetRole: String?): String {
        val payload = mutableMapOf<String, Any>("message" to message)
        if (!targetRole.isNullOrBlank()) payload["targetRole"] = targetRole
        val data = call("chatbotSendAnnouncementHttp", payload)
        return data["message"] as? String ?: "Announcement sent."
    }

    suspend fun manageRoom(dateID: String, roomName: String, totalCap: Int, startTime: String, endTime: String): String {
        val data = call("chatbotManageRoomHttp", mapOf(
            "dateID" to dateID, "roomName" to roomName,
            "totalCap" to totalCap, "startTime" to startTime, "endTime" to endTime
        ))
        return data["message"] as? String ?: "Room updated."
    }

    private suspend fun call(name: String, payload: Map<String, Any>): Map<String, Any?> = withContext(Dispatchers.IO) {
        val resolvedProjectId = projectId
        if (resolvedProjectId.isNullOrBlank()) {
            Log.e(TAG, "Project ID is null/blank; cannot call $name")
            return@withContext mapOf("message" to "Unable to resolve Firebase project ID for secure actions.")
        }
        val idToken = getAuthToken() ?: return@withContext mapOf("message" to "Please sign in again to run secure actions.")
        val endpoint = "https://$region-$resolvedProjectId.cloudfunctions.net/$name"
        Log.d(TAG, "Calling $name at $endpoint with keys=${payload.keys}")

        val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20000
            readTimeout = 30000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer $idToken")
        }

        val requestBody = JSONObject().put("data", JSONObject(payload)).toString()
        OutputStreamWriter(conn.outputStream).use { it.write(requestBody) }

        val status = conn.responseCode
        val stream = if (status in 200..299) conn.inputStream else conn.errorStream
        val text = BufferedReader(stream.reader()).use { it.readText() }
        Log.d(TAG, "Response from $name status=$status body=$text")

        val obj = runCatching { JSONObject(text) }.getOrNull()
        if (status !in 200..299) {
            val message = obj?.optString("message")?.takeIf { it.isNotBlank() }
                ?: obj?.optJSONObject("error")?.optString("message")
                ?: "Request to $name failed with HTTP $status"
            return@withContext mapOf("message" to message)
        }

        val payloadObj = obj?.optJSONObject("result") ?: obj
        if (payloadObj == null) {
            mapOf("message" to "Unexpected response payload from $name.")
        } else {
            val result = mutableMapOf<String, Any?>()
            payloadObj.keys().forEach { key ->
                result[key] = payloadObj.opt(key)
            }
            result
        }
    }

    private suspend fun getAuthToken(): String? = suspendCancellableCoroutine { cont ->
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            cont.resume(null)
            return@suspendCancellableCoroutine
        }

        user.getIdToken(false)
            .addOnSuccessListener { cont.resume(it.token) }
            .addOnFailureListener { cont.resume(null) }
    }
}






