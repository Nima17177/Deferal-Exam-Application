package com.example.duckduckdefer.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.duckduckdefer.BuildConfig
import com.example.duckduckdefer.components.chatbot.ChatbotActionExecutor
import com.example.duckduckdefer.components.chatbot.ChatbotActionRegistry
import com.example.duckduckdefer.components.chatbot.ChatbotRoutes
import com.example.duckduckdefer.components.chatbot.WizardInputType
import com.example.duckduckdefer.components.chatbot.WizardStep
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Drives the AI chatbot: manages conversation history, sends messages
 * to Gemini, parses action / navigation JSON from responses, and
 * executes actions via [ChatbotActionExecutor].
 */
class ChatbotViewModel : ViewModel() {

    companion object {
        private const val TAG = "ChatbotVM"
        // Gemini key is read from local.properties (GEMINI_API_KEY=...) at build time.
        // Never hardcode it here: this file is public on GitHub.
        private val GEMINI_API_KEY: String = BuildConfig.GEMINI_API_KEY
    }

    // ── Public state ────────────────────────────────────────
    data class ChatMessage(
        val text: String,
        val isUser: Boolean,
        val navigateRouteId: String? = null,
        val pendingAction: PendingAction? = null,
        val structuredData: StructuredData? = null,
        val quickReplies: List<String> = emptyList()
    )

    sealed class StructuredData {
        data class BookingList(val bookings: List<BookingCard>) : StructuredData()
        data class BookingDetail(val booking: BookingCard) : StructuredData()
        data class SessionList(val date: String, val rooms: List<RoomCard>) : StructuredData()
        data class DeferralList(val deferrals: List<DeferralCard>) : StructuredData()
        data class Stats(val stats: List<StatItem>) : StructuredData()
        data class Checklist(
            val items: List<ChecklistItem>,
            val exam: String, val date: String,
            val time: String, val room: String
        ) : StructuredData()
        data class NotificationList(
            val unreadCount: Int,
            val notifications: List<NotificationItem>
        ) : StructuredData()
    }

    data class BookingCard(
        val id: String, val examName: String, val date: String,
        val startTime: String, val endTime: String, val room: String,
        val status: String, val statusCode: Int
    )

    data class RoomCard(
        val name: String, val startTime: String, val endTime: String,
        val totalCap: Int, val availableSeats: Int
    )

    data class DeferralCard(
        val id: String, val courseName: String, val reason: String, val status: String
    )

    data class ChecklistItem(val label: String, val completed: Boolean)

    data class StatItem(val label: String, val value: String, val icon: String)

    data class NotificationItem(val title: String, val body: String, val read: Boolean)

    data class ActionResult(
        val text: String,
        val structuredData: StructuredData? = null
    )

    data class PendingAction(
        val actionId: String,
        val params: Map<String, String>
    )

    private data class BookPrecheckResult(
        val message: String? = null,
        val suggestedAction: PendingAction? = null
    )

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _quickChips = MutableStateFlow<List<String>>(emptyList())
    val quickChips: StateFlow<List<String>> = _quickChips

    // ── Search state ────────────────────────────────────────
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _searchResults = MutableStateFlow<List<ChatMessage>>(emptyList())
    val searchResults: StateFlow<List<ChatMessage>> = _searchResults

    private val _isSearchActive = MutableStateFlow(false)
    val isSearchActive: StateFlow<Boolean> = _isSearchActive

    private val _hasMoreHistory = MutableStateFlow(false)
    val hasMoreHistory: StateFlow<Boolean> = _hasMoreHistory

    private var searchJob: kotlinx.coroutines.Job? = null
    private var oldestLoadedTimestamp: com.google.firebase.Timestamp? = null

    // ── Wizard state ────────────────────────────────────────
    data class WizardState(
        val actionId: String,
        val steps: List<WizardStep>,
        val currentStepIndex: Int = 0,
        val collectedParams: MutableMap<String, String> = mutableMapOf(),
        val availableOptions: List<String> = emptyList()
    )

    private val _wizardState = MutableStateFlow<WizardState?>(null)
    val wizardState: StateFlow<WizardState?> = _wizardState

    // ── Internal state ──────────────────────────────────────
    private var userRole: Int = 3
    private var userUid: String = ""
    private var userName: String = ""
    private var initialized = false
    private var systemPrompt: String = ""
    private val conversationTurns = mutableListOf<Pair<String, String>>()

    private val executor = ChatbotActionExecutor()
    private val firestoreDb = FirebaseFirestore.getInstance()

    /** Phase 6: current screen route for context-aware help */
    private var currentScreenRoute: String = "dashboard"

    /** Phase 6: user language preference, default English */
    private var userLanguage: String = "English"

    /** Tracks the last time we sent a request to avoid spamming the API. */
    private var lastRequestTimeMs: Long = 0L
    private val minRequestIntervalMs: Long = 4000L // 4 seconds between requests

    /** Set after clearConversation to prevent loading stale history before the Firestore delete completes. */
    private var skipHistoryOnNextInit = false

    // ── Initialization ──────────────────────────────────────

    fun initialize(role: Int, uid: String, name: String) {
        if (initialized && uid == userUid) return
        userRole = role
        userUid = uid
        userName = name
        initialized = true

        systemPrompt = buildSystemPrompt(role, uid, name)
        conversationTurns.clear()

        _quickChips.value = buildQuickChips(role)

        _messages.value = listOf(
            ChatMessage(
                text = "Hi $name! 👋 I'm DuckBot, your DuckDuckDefer assistant. " +
                        "Tap one of the options below or type a question!",
                isUser = false
            )
        )

        viewModelScope.launch {
            if (skipHistoryOnNextInit) {
                skipHistoryOnNextInit = false
            } else {
                loadConversationHistory()
            }
            generateDailyBriefing()
        }
    }

    /** Phase 6: Update current screen for context-aware help */
    fun updateCurrentScreen(route: String) {
        if (currentScreenRoute != route) {
            currentScreenRoute = route
            if (initialized) {
                systemPrompt = buildSystemPrompt(userRole, userUid, userName)
            }
        }
    }

    /** Phase 6: Set language preference */
    fun setLanguagePreference(language: String) {
        userLanguage = language
        if (initialized) {
            systemPrompt = buildSystemPrompt(userRole, userUid, userName)
        }
    }

    fun cleanup() {
        initialized = false
        userRole = 3
        userUid = ""
        userName = ""
        systemPrompt = ""
        conversationTurns.clear()
        _messages.value = emptyList()
        _quickChips.value = emptyList()
        _isLoading.value = false
    }

    // ── Send a user message ─────────────────────────────────

    fun sendMessage(text: String) {
        if (text.isBlank()) return

        val trimmed = text.trim()

        // If wizard is active, capture typed input as the current step value
        val activeWizard = _wizardState.value
        if (activeWizard != null) {
            val step = activeWizard.steps.getOrNull(activeWizard.currentStepIndex)
            if (step != null) {
                val current = _messages.value.toMutableList()
                current.add(ChatMessage(text = trimmed, isUser = true))
                _messages.value = current
                persistMessage(trimmed, isUser = true)
                advanceWizard(trimmed)
                return
            }
        }

        val pendingAction = findLatestPendingAction()
        if (pendingAction != null && isConfirmText(trimmed)) {
            val currentMessages = _messages.value.toMutableList()
            currentMessages.add(ChatMessage(text = text, isUser = true))
            _messages.value = currentMessages
            confirmAction(pendingAction)
            return
        }
        if (pendingAction != null && isCancelText(trimmed)) {
            val currentMessages = _messages.value.toMutableList()
            currentMessages.add(ChatMessage(text = text, isUser = true))
            _messages.value = currentMessages
            cancelAction(pendingAction)
            return
        }

        val currentMessages = _messages.value.toMutableList()
        currentMessages.add(ChatMessage(text = text, isUser = true))
        _messages.value = currentMessages
        persistMessage(text, isUser = true)

        val directAction = parseDirectActionFromUser(trimmed)
        if (directAction != null) {
            val actionDef = ChatbotActionRegistry.findAction(directAction.actionId)
            if (actionDef == null || userRole !in actionDef.allowedRoles) {
                addBotMessage("Sorry, you don't have permission to do that action.")
                return
            }

            val missing = actionDef.requiredParams.filter {
                directAction.params[it].isNullOrBlank()
            }
            if (missing.isNotEmpty()) {
                if (ChatbotActionRegistry.wizardSteps.containsKey(directAction.actionId)) {
                    startWizardWithPartialParams(directAction.actionId, directAction.params)
                    return
                }
                addBotMessage("I still need: ${missing.joinToString(", ")}")
                return
            }

            val validationError = validateActionParams(directAction.actionId, directAction.params)
            if (validationError != null) {
                addBotMessage(validationError)
                return
            }

            if (actionDef.isDestructive) {
                if (directAction.actionId == "book_session") {
                    _isLoading.value = true
                    viewModelScope.launch {
                        val precheck = precheckBookSession(directAction.params)
                        _isLoading.value = false
                        if (precheck.message != null) {
                            addBotMessage(precheck.message, pendingAction = precheck.suggestedAction)
                        } else {
                            val summary = buildActionSummary(directAction.actionId, directAction.params)
                            addBotMessage("$summary\n\nPlease confirm to proceed.", pendingAction = directAction)
                        }
                    }
                    return
                }

                val summary = buildActionSummary(directAction.actionId, directAction.params)
                addBotMessage("$summary\n\nPlease confirm to proceed.", pendingAction = directAction)
                return
            }

            _isLoading.value = true
            viewModelScope.launch {
                val result = executeAction(directAction.actionId, directAction.params)
                val chips = ChatbotActionRegistry.followUpChips[directAction.actionId] ?: emptyList()
                addBotMessage(result.text, structuredData = result.structuredData, quickReplies = chips)
                _isLoading.value = false
            }
            return
        }

        _isLoading.value = true

        viewModelScope.launch {
            try {
                val reply = sendWithRetry(text)
                processAiResponse(reply)
            } catch (e: Exception) {
                Log.e(TAG, "Gemini error", e)
                val msg = e.localizedMessage ?: e.message ?: ""
                when {
                    msg.contains("limit: 0", ignoreCase = true) ||
                    msg.contains("limit:0", ignoreCase = true) ->
                        addBotMessage("🔧 The Gemini API isn't enabled for your project. " +
                                "Go to Google AI Studio → Get API Key → Create a NEW key in a new project, then update it in the app.")
                    msg.contains("quota", ignoreCase = true) ||
                    msg.contains("rate", ignoreCase = true) ||
                    msg.contains("429") ||
                    msg.contains("RESOURCE_EXHAUSTED", ignoreCase = true) ->
                        addBotMessage("⏳ Rate limited. The free Gemini API allows ~15 requests/min. Please wait about 60 seconds and try again.")
                    msg.contains("API key", ignoreCase = true) ||
                    msg.contains("API_KEY_INVALID", ignoreCase = true) ||
                    msg.contains("401") ||
                    msg.contains("403") ->
                        addBotMessage("🔑 Invalid API key. Go to https://aistudio.google.com/apikey to get a valid key.")
                    msg.contains("not found", ignoreCase = true) ||
                    msg.contains("404") ->
                        addBotMessage("⚠️ Model not available. The Gemini model may not be accessible with your API key.")
                    else ->
                        addBotMessage("⚠️ Error: ${e.localizedMessage}")
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Sends a message to Gemini with automatic retry on quota/rate-limit errors.
     * Enforces a minimum interval between requests and uses longer backoff (15s → 30s → 60s).
     */
    private suspend fun sendWithRetry(text: String, maxRetries: Int = 3): String {
        // Enforce minimum interval between requests to avoid hitting rate limits
        val now = System.currentTimeMillis()
        val elapsed = now - lastRequestTimeMs
        if (elapsed < minRequestIntervalMs) {
            delay(minRequestIntervalMs - elapsed)
        }

        var lastException: Exception? = null
        val backoffSeconds = longArrayOf(15, 30, 60) // much longer waits for free tier

        for (attempt in 0 until maxRetries) {
            try {
                lastRequestTimeMs = System.currentTimeMillis()
                val response = sendToGemini(text)
                return response.ifBlank { "Sorry, I couldn't generate a response. Please try rephrasing your message." }
            } catch (e: Exception) {
                lastException = e
                val msg = e.localizedMessage ?: e.message ?: ""
                val isRateLimit = msg.contains("quota", ignoreCase = true) ||
                        msg.contains("rate", ignoreCase = true) ||
                        msg.contains("429") ||
                        msg.contains("RESOURCE_EXHAUSTED", ignoreCase = true)
                if (isRateLimit && attempt < maxRetries - 1) {
                    val waitSec = backoffSeconds[attempt]
                    Log.w(TAG, "Rate limited, retrying in ${waitSec}s (attempt ${attempt + 1}/$maxRetries)")
                    // Update the loading message so user knows we're waiting
                    addBotMessage("⏳ Rate limited by Gemini. Retrying in ${waitSec}s…")
                    delay(waitSec * 1000)
                    // Remove the waiting message before retrying
                    val msgs = _messages.value.toMutableList()
                    if (msgs.isNotEmpty() && msgs.last().text.startsWith("⏳ Rate limited")) {
                        msgs.removeAt(msgs.lastIndex)
                        _messages.value = msgs
                    }
                } else {
                    throw e
                }
            }
        }
        throw lastException ?: Exception("Failed after $maxRetries retries")
    }

    // ── Send PDF text for analysis ──────────────────────────

    fun sendPdfText(pdfText: String) {
        val displayMessage = "📄 Uploaded a course outline PDF for analysis."
        val current = _messages.value.toMutableList()
        current.add(ChatMessage(text = displayMessage, isUser = true))
        _messages.value = current

        val prompt = "The user uploaded a course outline. Analyze it and find the professor's deferral policy. " +
                "Highlight likely accepted deferral reasons and any deadlines.\n\n--- PDF CONTENT ---\n$pdfText"

        _isLoading.value = true
        viewModelScope.launch {
            try {
                val reply = sendWithRetry(prompt)
                processAiResponse(reply)
            } catch (e: Exception) {
                Log.e(TAG, "Gemini error (PDF)", e)
                val msg = e.localizedMessage ?: e.message ?: ""
                when {
                    msg.contains("quota", ignoreCase = true) ||
                    msg.contains("rate", ignoreCase = true) ||
                    msg.contains("429") ||
                    msg.contains("RESOURCE_EXHAUSTED", ignoreCase = true) ->
                        addBotMessage("⏳ Rate limited. Please wait about 60 seconds and try again.")
                    else ->
                        addBotMessage("⚠️ Error analyzing PDF: ${e.localizedMessage}")
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    // ── Confirm / cancel destructive action ─────────────────

    fun confirmAction(action: PendingAction) {
        viewModelScope.launch {
            _isLoading.value = true
            // Remove the pending-action message and replace with executing
            val updated = _messages.value.toMutableList()
            val idx = updated.indexOfLast { it.pendingAction == action }
            if (idx >= 0) {
                updated[idx] = updated[idx].copy(pendingAction = null, text = updated[idx].text + "\n\n⏳ Executing…")
                _messages.value = updated
            }
            val result = executeAction(action.actionId, action.params)
            val chips = ChatbotActionRegistry.followUpChips[action.actionId] ?: emptyList()
            addBotMessage(result.text, structuredData = result.structuredData, quickReplies = chips)
            _isLoading.value = false
        }
    }

    fun cancelAction(action: PendingAction) {
        val updated = _messages.value.toMutableList()
        val idx = updated.indexOfLast { it.pendingAction == action }
        if (idx >= 0) {
            updated[idx] = updated[idx].copy(pendingAction = null, text = "Action cancelled.")
            _messages.value = updated
        }
    }

    // ── Clear conversation ──────────────────────────────────

    fun clearConversation() {
        initialized = false
        conversationTurns.clear()
        _messages.value = emptyList()
        skipHistoryOnNextInit = true
        if (userUid.isNotBlank()) {
            firestoreDb.collection("ChatbotConversations").document(userUid)
                .collection("messages").get()
                .addOnSuccessListener { snap ->
                    val batch = firestoreDb.batch()
                    for (doc in snap.documents) batch.delete(doc.reference)
                    batch.commit()
                }
        }
        initialize(userRole, userUid, userName)
    }

    // ── Search functions ─────────────────────────────────────

    fun toggleSearch() {
        val newActive = !_isSearchActive.value
        _isSearchActive.value = newActive
        if (!newActive) {
            _searchQuery.value = ""
            _searchResults.value = emptyList()
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        searchJob?.cancel()
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            return
        }
        searchJob = viewModelScope.launch {
            delay(300)
            val lowerQuery = query.lowercase()
            val results = _messages.value.filter {
                it.text.lowercase().contains(lowerQuery)
            }
            _searchResults.value = results
        }
    }

    fun loadMoreHistory() {
        if (userUid.isBlank()) return
        viewModelScope.launch {
            try {
                var query = firestoreDb.collection("ChatbotConversations").document(userUid)
                    .collection("messages")
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .limit(20)

                if (oldestLoadedTimestamp != null) {
                    query = query.startAfter(oldestLoadedTimestamp)
                }

                val snap = query.get().await()
                if (snap.isEmpty) {
                    _hasMoreHistory.value = false
                    return@launch
                }

                val lastDoc = snap.documents.lastOrNull()
                oldestLoadedTimestamp = lastDoc?.getTimestamp("timestamp")
                _hasMoreHistory.value = snap.size() >= 20

                val older = snap.documents.reversed().map { doc ->
                    ChatMessage(
                        text = doc.getString("text") ?: "",
                        isUser = doc.getBoolean("isUser") ?: false
                    )
                }

                val current = _messages.value.toMutableList()
                current.addAll(0, older)
                _messages.value = current

                for (msg in older) {
                    conversationTurns.add(0, (if (msg.isUser) "user" else "model") to msg.text)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not load more history", e)
            }
        }
    }

    // ── Wizard functions ─────────────────────────────────────

    fun startWizard(actionId: String) {
        val steps = ChatbotActionRegistry.wizardSteps[actionId] ?: return
        val wizard = WizardState(actionId = actionId, steps = steps)
        _wizardState.value = wizard
        presentCurrentWizardStep()
    }

    /**
     * Starts a wizard but pre-fills any params Gemini (or the direct parser)
     * already collected, skipping those steps automatically.
     */
    private fun startWizardWithPartialParams(actionId: String, existingParams: Map<String, String>) {
        val allSteps = ChatbotActionRegistry.wizardSteps[actionId] ?: return
        val collected = mutableMapOf<String, String>()
        existingParams.forEach { (k, v) -> if (v.isNotBlank()) collected[k] = v }

        val firstMissing = allSteps.indexOfFirst { it.paramName !in collected }
        if (firstMissing == -1) {
            // All params already provided — go straight to confirmation
            val summary = buildWizardSummary(WizardState(actionId, allSteps, allSteps.size, collected))
            addBotMessage(
                "$summary\n\nPlease confirm to proceed.",
                pendingAction = PendingAction(actionId, collected.toMap())
            )
            return
        }

        val wizard = WizardState(
            actionId = actionId,
            steps = allSteps,
            currentStepIndex = firstMissing,
            collectedParams = collected
        )
        _wizardState.value = wizard
        addBotMessage("Let me guide you through the remaining details.")
        presentCurrentWizardStep()
    }

    fun advanceWizard(value: String) {
        val wizard = _wizardState.value ?: return
        val step = wizard.steps.getOrNull(wizard.currentStepIndex) ?: return

        wizard.collectedParams[step.paramName] = value

        if (wizard.currentStepIndex + 1 >= wizard.steps.size) {
            val summary = buildWizardSummary(wizard)
            addBotMessage(
                "$summary\n\nPlease confirm to proceed.",
                pendingAction = PendingAction(wizard.actionId, wizard.collectedParams.toMap())
            )
            _wizardState.value = null
        } else {
            _wizardState.value = wizard.copy(
                currentStepIndex = wizard.currentStepIndex + 1,
                availableOptions = emptyList()
            )
            presentCurrentWizardStep()
        }
    }

    fun cancelWizard() {
        _wizardState.value = null
        addBotMessage("Wizard cancelled.")
    }

    private fun presentCurrentWizardStep() {
        val wizard = _wizardState.value ?: return
        val step = wizard.steps.getOrNull(wizard.currentStepIndex) ?: return

        when (step.inputType) {
            WizardInputType.ROOM_PICKER -> {
                val dateID = wizard.collectedParams["dateID"]
                if (dateID != null) {
                    viewModelScope.launch {
                        addBotMessage(step.prompt)
                        try {
                            val result = executor.listSessionsByDateStructured(dateID)
                            val rooms = (result.structuredData as? StructuredData.SessionList)?.rooms
                            if (rooms != null && rooms.isNotEmpty()) {
                                _wizardState.value = wizard.copy(
                                    availableOptions = rooms.map { it.name }
                                )
                            }
                        } catch (_: Exception) { }
                    }
                } else {
                    addBotMessage(step.prompt)
                }
            }
            else -> addBotMessage(step.prompt)
        }
    }

    private fun buildWizardSummary(wizard: WizardState): String {
        val actionDef = ChatbotActionRegistry.findAction(wizard.actionId)
        val name = actionDef?.description ?: wizard.actionId
        val params = wizard.collectedParams.entries.joinToString("\n") { "• ${it.key}: ${it.value}" }
        return "Ready to execute: **$name**\n$params"
    }

    private suspend fun sendToGemini(userText: String): String = withContext(Dispatchers.IO) {
        val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-lite:generateContent?key=$GEMINI_API_KEY"
        val url = URL(endpoint)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20000
            readTimeout = 30000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }

        val body = JSONObject().apply {
            put(
                "system_instruction",
                JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemPrompt)))
            )

            val history = JSONArray()
            conversationTurns.takeLast(12).forEach { (role, text) ->
                history.put(
                    JSONObject()
                        .put("role", role)
                        .put("parts", JSONArray().put(JSONObject().put("text", text)))
                )
            }
            history.put(
                JSONObject()
                    .put("role", "user")
                    .put("parts", JSONArray().put(JSONObject().put("text", userText)))
            )
            put("contents", history)
        }

        OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }

        val status = conn.responseCode
        val stream = if (status in 200..299) conn.inputStream else conn.errorStream
        val responseText = BufferedReader(stream.reader()).use { it.readText() }

        if (status !in 200..299) {
            val errorMessage = try {
                JSONObject(responseText).optJSONObject("error")?.optString("message")
                    ?: "Gemini request failed with HTTP $status"
            } catch (_: Exception) {
                "Gemini request failed with HTTP $status"
            }
            throw Exception(errorMessage)
        }

        val json = JSONObject(responseText)
        val candidates = json.optJSONArray("candidates")
        val candidate = candidates?.optJSONObject(0)

        val replyText = candidate
            ?.optJSONObject("content")
            ?.optJSONArray("parts")
            ?.optJSONObject(0)
            ?.optString("text")
            ?: ""

        if (replyText.isBlank()) {
            val blockReason = json.optJSONObject("promptFeedback")
                ?.optString("blockReason").orEmpty()
            val finishReason = candidate?.optString("finishReason").orEmpty()
            Log.w(TAG, "Empty Gemini response — blockReason=$blockReason finishReason=$finishReason")
        }

        conversationTurns.add("user" to userText)
        if (replyText.isNotBlank()) {
            conversationTurns.add("model" to replyText)
        }

        replyText
    }

    // ── Private helpers ─────────────────────────────────────

    private fun processAiResponse(rawReply: String) {
        // Try to extract action JSON
        val actionJson = extractJson(rawReply, "action")
        if (actionJson != null) {
            val actionId = actionJson.optString("action")
            val paramsObj = actionJson.optJSONObject("params")
            val params = mutableMapOf<String, String>()
            paramsObj?.keys()?.forEach { key -> params[key] = paramsObj.optString(key) }

            val normalizedParams = normalizeActionParams(actionId, params)

            // Security check – role gate
            val actionDef = ChatbotActionRegistry.findAction(actionId)
            if (actionDef == null || userRole !in actionDef.allowedRoles) {
                addBotMessage("Sorry, you don't have permission to do that.")
                return
            }

            // Check for missing params — launch wizard if available
            val missing = actionDef.requiredParams.filter { it !in normalizedParams || normalizedParams[it].isNullOrBlank() }
            if (missing.isNotEmpty()) {
                if (ChatbotActionRegistry.wizardSteps.containsKey(actionId)) {
                    startWizardWithPartialParams(actionId, normalizedParams)
                    return
                }
                addBotMessage(cleanJsonFromText(rawReply))
                return
            }

            val validationError = validateActionParams(actionId, normalizedParams)
            if (validationError != null) {
                addBotMessage(validationError)
                return
            }

            if (actionDef.isDestructive) {
                if (actionId == "book_session") {
                    viewModelScope.launch {
                        _isLoading.value = true
                        val precheck = precheckBookSession(normalizedParams)
                        _isLoading.value = false
                        if (precheck.message != null) {
                            addBotMessage(precheck.message, pendingAction = precheck.suggestedAction)
                        } else {
                            val cleanText = cleanJsonFromText(rawReply).ifBlank {
                                "I'm about to execute: ${actionDef.description}. Please confirm."
                            }
                            addBotMessage(cleanText, pendingAction = PendingAction(actionId, normalizedParams))
                        }
                    }
                    return
                }

                // Show confirmation
                val cleanText = cleanJsonFromText(rawReply).ifBlank {
                    "I'm about to execute: ${actionDef.description}. Please confirm."
                }
                addBotMessage(cleanText, pendingAction = PendingAction(actionId, normalizedParams))
                return
            }

            // Non-destructive: execute immediately
            viewModelScope.launch {
                _isLoading.value = true
                val result = executeAction(actionId, normalizedParams)
                val chips = ChatbotActionRegistry.followUpChips[actionId] ?: emptyList()
                addBotMessage(result.text, structuredData = result.structuredData, quickReplies = chips)
                _isLoading.value = false
            }
            return
        }

        // Try to extract navigation JSON
        val navJson = extractJson(rawReply, "navigate")
        val navigateRouteId = navJson?.optString("navigate")
        val cleanText = cleanJsonFromText(rawReply)
        addBotMessage(cleanText, navigateRouteId = navigateRouteId)
    }

    private suspend fun executeAction(actionId: String, params: Map<String, String>): ActionResult {
        return try {
            when (actionId) {
                "search_user_by_email" -> ActionResult(executor.searchUserByEmail(params["email"]!!))
                "deactivate_account" -> ActionResult(executor.deactivateAccount(params["email"]!!))
                "reactivate_account" -> ActionResult(executor.reactivateAccount(params["email"]!!))
                "get_pending_accounts" -> ActionResult(executor.getPendingAccounts())
                "list_sessions_by_date" -> executor.listSessionsByDateStructured(params["dateID"]!!)
                "book_session" -> ActionResult(executor.bookSession(
                    studentEmail = params["studentEmail"]!!,
                    examName = params["examName"]!!,
                    dateID = params["dateID"]!!,
                    roomName = params["roomName"]!!,
                    startTime = params["startTime"]!!,
                    endTime = params["endTime"]!!,
                    facultyEmail = params["facultyEmail"]
                ))
                "delete_session" -> ActionResult(executor.deleteSession(params["sessionId"]!!))
                "get_deferral_count" -> ActionResult(executor.getDeferralCount(userUid))
                "get_faculty_availability" -> ActionResult(executor.getFacultyAvailability(userUid))
                "get_my_bookings" -> executor.getMyBookingsStructured(userUid, userRole)
                "get_booking_status" -> executor.getBookingStatusStructured(params["bookingId"]!!)
                "get_my_deferrals" -> executor.getMyDeferralsStructured(userUid)
                "get_exam_checklist" -> executor.getExamChecklistStructured(userUid)
                "get_my_assigned_sessions" -> ActionResult(executor.getMyAssignedSessions(userUid))
                "update_availability" -> ActionResult(executor.updateAvailability(
                    params["dateID"]!!, params["available"]!!.toBooleanStrictOrNull() ?: true
                ))
                "get_booking_details" -> executor.getBookingDetailsStructured(params["bookingId"]!!)
                "checkin_student" -> ActionResult(executor.checkinStudent(params["bookingId"]!!))
                "report_incident" -> ActionResult(executor.reportIncident(params["bookingId"]!!, params["description"]!!))
                "mark_exam_complete" -> ActionResult(executor.markExamComplete(params["bookingId"]!!))
                "cancel_booking" -> ActionResult(executor.cancelBooking(params["bookingId"]!!))
                "get_my_deferral_requests" -> executor.getMyDeferralRequestsStructured(userUid)
                "send_message_to_user" -> ActionResult(executor.sendMessageToUser(
                    params["recipientEmail"]!!, params["messageText"]!!
                ))
                "approve_account" -> ActionResult(executor.approveAccount(params["email"]!!))
                "reject_account" -> ActionResult(executor.rejectAccount(params["email"]!!))
                "assign_proctor" -> ActionResult(executor.assignProctor(
                    params["proctorEmail"]!!, params["dateID"]!!, params["roomName"]!!
                ))
                "get_system_stats" -> executor.getSystemStatsStructured()
                "send_announcement" -> ActionResult(executor.sendAnnouncement(
                    params["message"]!!, params["targetRole"]
                ))
                "manage_room" -> ActionResult(executor.manageRoom(
                    params["dateID"]!!, params["roomName"]!!,
                    params["totalCap"]!!.toInt(),
                    params["startTime"]!!, params["endTime"]!!
                ))
                "generate_report" -> ActionResult(executor.generateReport(params["startDate"]!!, params["endDate"]!!))
                "get_notification_summary" -> executor.getNotificationSummaryStructured(userUid)
                else -> ActionResult("Unknown action: $actionId")
            }
        } catch (e: Exception) {
            ActionResult("Error executing action: ${e.localizedMessage}")
        }
    }

    private fun addBotMessage(
        text: String,
        navigateRouteId: String? = null,
        pendingAction: PendingAction? = null,
        structuredData: StructuredData? = null,
        quickReplies: List<String> = emptyList()
    ) {
        val current = _messages.value.toMutableList()
        current.add(
            ChatMessage(
                text = text,
                isUser = false,
                navigateRouteId = navigateRouteId,
                pendingAction = pendingAction,
                structuredData = structuredData,
                quickReplies = quickReplies
            )
        )
        _messages.value = current
        persistMessage(text, isUser = false)
    }

    // ── Phase 6: Conversation persistence ────────────────────

    private fun persistMessage(text: String, isUser: Boolean) {
        if (userUid.isBlank()) return
        val data = mapOf(
            "text" to text,
            "isUser" to isUser,
            "timestamp" to com.google.firebase.Timestamp.now()
        )
        firestoreDb.collection("ChatbotConversations").document(userUid)
            .collection("messages").add(data)
    }

    private suspend fun loadConversationHistory() {
        if (userUid.isBlank()) return
        try {
            val snap = firestoreDb.collection("ChatbotConversations").document(userUid)
                .collection("messages")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(20)
                .get().await()

            if (snap.isEmpty) return

            val lastDoc = snap.documents.lastOrNull()
            oldestLoadedTimestamp = lastDoc?.getTimestamp("timestamp")
            _hasMoreHistory.value = snap.size() >= 20

            val restored = snap.documents.reversed().map { doc ->
                ChatMessage(
                    text = doc.getString("text") ?: "",
                    isUser = doc.getBoolean("isUser") ?: false
                )
            }

            val current = _messages.value.toMutableList()
            current.addAll(0, restored)
            _messages.value = current

            for (msg in restored) {
                val role = if (msg.isUser) "user" else "model"
                conversationTurns.add(role to msg.text)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not load chat history", e)
        }
    }

    // ── Phase 5: Proactive daily briefing ────────────────────

    private suspend fun generateDailyBriefing() {
        try {
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            val briefing = when (userRole) {
                3 -> { // Student
                    val indexDoc = firestoreDb.collection("StudentBookings").document(userUid).get().await()
                    @Suppress("UNCHECKED_CAST")
                    val ids = indexDoc.get("bookings") as? List<String> ?: emptyList()
                    val todayExams = mutableListOf<String>()
                    for (id in ids) {
                        val doc = firestoreDb.collection("BookingsCollection").document(id).get().await()
                        if (doc.getString("sessionDateID") == today) {
                            todayExams.add("${doc.getString("examName") ?: "Exam"} at ${doc.getString("startTime") ?: "?"} in ${doc.getString("roomName") ?: "?"}")
                        }
                    }
                    if (todayExams.isNotEmpty()) "📅 You have ${todayExams.size} exam(s) today: ${todayExams.joinToString("; ")}" else null
                }
                2 -> { // Proctor
                    val snap = firestoreDb.collection("SessionsCollection").document(today).get().await()
                    if (!snap.exists()) null
                    else {
                        @Suppress("UNCHECKED_CAST")
                        val rooms = snap.get("rooms") as? Map<String, Map<String, Any>> ?: emptyMap()
                        val myRooms = rooms.filter { (_, data) ->
                            @Suppress("UNCHECKED_CAST")
                            (data["assignedProctors"] as? List<String> ?: emptyList()).contains(userUid)
                        }
                        if (myRooms.isNotEmpty()) "📅 You're proctoring ${myRooms.size} room(s) today: ${myRooms.keys.joinToString(", ")}" else null
                    }
                }
                1 -> { // Faculty
                    val snap = firestoreDb.collection("BookingsCollection")
                        .whereEqualTo("facultyUID", userUid)
                        .whereEqualTo("sessionDateID", today)
                        .get().await()
                    if (!snap.isEmpty) "📅 You have ${snap.size()} student booking(s) today." else null
                }
                0 -> { // Admin
                    val pending = firestoreDb.collection("UserAccounts")
                        .whereEqualTo("thisApprovalStatus", 0).get().await()
                    val todayBookings = firestoreDb.collection("BookingsCollection")
                        .whereEqualTo("sessionDateID", today).get().await()
                    val parts = mutableListOf<String>()
                    if (!pending.isEmpty) parts.add("${pending.size()} pending account(s)")
                    if (!todayBookings.isEmpty) parts.add("${todayBookings.size()} booking(s) today")
                    if (parts.isNotEmpty()) "📅 Daily overview: ${parts.joinToString(", ")}." else null
                }
                else -> null
            }

            if (briefing != null) {
                val current = _messages.value.toMutableList()
                current.add(ChatMessage(text = briefing, isUser = false))
                _messages.value = current
            }
        } catch (e: Exception) {
            Log.w(TAG, "Daily briefing failed", e)
        }
    }

    // ── Phase 5: Natural language date/time parsing ──────────

    private fun resolveNaturalDate(text: String): String? {
        val lower = text.lowercase().trim()
        val cal = Calendar.getInstance()
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)

        return when {
            lower == "today" -> fmt.format(cal.time)
            lower == "tomorrow" -> {
                cal.add(Calendar.DAY_OF_YEAR, 1); fmt.format(cal.time)
            }
            lower == "yesterday" -> {
                cal.add(Calendar.DAY_OF_YEAR, -1); fmt.format(cal.time)
            }
            lower.startsWith("next ") -> {
                val dayName = lower.removePrefix("next ").trim()
                val targetDay = dayOfWeek(dayName) ?: return null
                val current = cal.get(Calendar.DAY_OF_WEEK)
                val daysAhead = ((targetDay - current + 7) % 7).let { if (it == 0) 7 else it }
                cal.add(Calendar.DAY_OF_YEAR, daysAhead)
                fmt.format(cal.time)
            }
            lower.startsWith("this ") -> {
                val dayName = lower.removePrefix("this ").trim()
                val targetDay = dayOfWeek(dayName) ?: return null
                val current = cal.get(Calendar.DAY_OF_WEEK)
                val daysAhead = ((targetDay - current + 7) % 7).let { if (it == 0) 7 else it }
                cal.add(Calendar.DAY_OF_YEAR, daysAhead)
                fmt.format(cal.time)
            }
            lower.matches(Regex("in \\d+ days?")) -> {
                val n = Regex("\\d+").find(lower)?.value?.toIntOrNull() ?: return null
                cal.add(Calendar.DAY_OF_YEAR, n); fmt.format(cal.time)
            }
            else -> tryParseWrittenDate(text.trim())
        }
    }

    private fun tryParseWrittenDate(text: String): String? {
        val formats = arrayOf(
            "MMMM d yyyy", "MMMM d, yyyy",
            "MMM d yyyy", "MMM d, yyyy",
            "MMMM dd yyyy", "MMMM dd, yyyy",
            "MMM dd yyyy", "MMM dd, yyyy",
            "d MMMM yyyy", "d MMM yyyy",
            "dd MMMM yyyy", "dd MMM yyyy",
            "MMMM d", "MMM d",
            "MMMM dd", "MMM dd"
        )
        val outFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        for (pattern in formats) {
            try {
                val parser = SimpleDateFormat(pattern, Locale.US).apply { isLenient = false }
                val date = parser.parse(text) ?: continue
                val parsed = Calendar.getInstance().apply { time = date }
                if (!pattern.contains("yyyy")) {
                    parsed.set(Calendar.YEAR, Calendar.getInstance().get(Calendar.YEAR))
                }
                return outFmt.format(parsed.time)
            } catch (_: Exception) { }
        }
        return null
    }

    private fun dayOfWeek(name: String): Int? = when (name.lowercase()) {
        "sunday", "sun" -> Calendar.SUNDAY
        "monday", "mon" -> Calendar.MONDAY
        "tuesday", "tue", "tues" -> Calendar.TUESDAY
        "wednesday", "wed" -> Calendar.WEDNESDAY
        "thursday", "thu", "thur", "thurs" -> Calendar.THURSDAY
        "friday", "fri" -> Calendar.FRIDAY
        "saturday", "sat" -> Calendar.SATURDAY
        else -> null
    }

    /** Extract a JSON object from text that contains the given key. */
    private fun extractJson(text: String, key: String): JSONObject? {
        // Prefer fenced JSON blocks first.
        val fenced = Regex("""```json\s*(\{[\s\S]*?\})\s*```""", RegexOption.IGNORE_CASE)
        fenced.findAll(text).forEach { match ->
            val candidate = match.groupValues.getOrNull(1)?.trim().orEmpty()
            if (candidate.isNotBlank()) {
                try {
                    val obj = JSONObject(candidate)
                    if (obj.has(key)) return obj
                } catch (_: Exception) { }
            }
        }

        // Fallback: scan balanced {...} objects inline.
        var depth = 0
        var start = -1
        text.forEachIndexed { index, ch ->
            if (ch == '{') {
                if (depth == 0) start = index
                depth++
            } else if (ch == '}') {
                if (depth > 0) {
                    depth--
                    if (depth == 0 && start >= 0) {
                        val candidate = text.substring(start, index + 1)
                        try {
                            val obj = JSONObject(candidate)
                            if (obj.has(key)) return obj
                        } catch (_: Exception) { }
                        start = -1
                    }
                }
            }
        }
        return null
    }

    /** Remove JSON blocks from text so we show clean prose. */
    private fun cleanJsonFromText(text: String): String {
        var cleaned = text
        // Remove ```json ... ``` blocks
        cleaned = Regex("""```json\s*\{.*?\}\s*```""", RegexOption.DOT_MATCHES_ALL).replace(cleaned, "")
        // Remove inline action JSON with nested params
        cleaned = Regex(
            """\{\s*"action"\s*:\s*"[^"]+"\s*,\s*"params"\s*:\s*\{.*?\}\s*\}""",
            RegexOption.DOT_MATCHES_ALL
        ).replace(cleaned, "")
        // Remove inline navigation JSON
        cleaned = Regex("""\{\s*"navigate"\s*:\s*"[^"]+"\s*\}""").replace(cleaned, "")
        return cleaned.trim()
    }

    private fun findLatestPendingAction(): PendingAction? =
        _messages.value.asReversed().firstOrNull { !it.isUser && it.pendingAction != null }?.pendingAction

    private fun isConfirmText(text: String): Boolean {
        val t = text.lowercase()
        return t == "yes" || t == "y" || t == "confirm" || t == "proceed" || t == "go ahead"
    }

    private fun isCancelText(text: String): Boolean {
        val t = text.lowercase()
        return t == "no" || t == "n" || t == "cancel" || t == "stop" || t == "don't"
    }

    private fun parseDirectActionFromUser(text: String): PendingAction? {
        val lower = text.lowercase()

        // Student/universal direct commands
        if (lower.matches(Regex(".*\\b(show|list|get|view)\\b.*\\bmy\\b.*\\b(bookings?|exams?)\\b.*"))) {
            if (userRole == 3 || userRole == 1) return PendingAction("get_my_bookings", emptyMap())
        }
        if (lower.matches(Regex(".*\\b(show|get|view)\\b.*\\bmy\\b.*\\b(deferrals?)\\b.*"))) {
            if (userRole == 3) return PendingAction("get_my_deferrals", emptyMap())
        }
        if (lower.matches(Regex(".*\\b(exam|booking)\\b.*\\bchecklist\\b.*")) ||
            lower.matches(Regex(".*\\bchecklist\\b.*\\b(exam|booking)\\b.*"))) {
            if (userRole == 3) return PendingAction("get_exam_checklist", emptyMap())
        }
        if (lower.matches(Regex(".*\\bmy\\b.*\\b(assigned|proctoring)\\b.*\\bsessions?\\b.*")) ||
            lower.matches(Regex(".*\\bsessions?\\b.*\\b(this|next)\\b.*\\bweek\\b.*"))) {
            if (userRole == 2) return PendingAction("get_my_assigned_sessions", emptyMap())
        }
        if (lower.matches(Regex(".*\\bnotification\\b.*\\bsummar(y|ize)\\b.*")) ||
            lower.matches(Regex(".*\\bsummar(y|ize)\\b.*\\bnotification\\b.*"))) {
            return PendingAction("get_notification_summary", emptyMap())
        }
        if (lower.matches(Regex(".*\\b(system|platform)\\b.*\\bstat(s|istics)\\b.*"))) {
            if (userRole == 0) return PendingAction("get_system_stats", emptyMap())
        }
        if (lower.matches(Regex(".*\\bpending\\b.*\\b(accounts?|approvals?)\\b.*"))) {
            if (userRole == 0) return PendingAction("get_pending_accounts", emptyMap())
        }

        // Admin/Faculty: list sessions
        if (userRole in listOf(0, 1)) {
            val listMatch = Regex("""\b(list|show)\b.*\b(sessions?)\b.*?(\d{4}-\d{2}-\d{2})""", RegexOption.IGNORE_CASE)
                .find(text)
            if (listMatch != null) {
                val dateID = listMatch.groupValues[3]
                return PendingAction("list_sessions_by_date", mapOf("dateID" to dateID))
            }

            // Natural language date for sessions
            val nlDateMatch = Regex("""\b(list|show)\b.*\b(sessions?)\b.*\b(today|tomorrow|next \w+|this \w+)\b""", RegexOption.IGNORE_CASE)
                .find(text)
            if (nlDateMatch != null) {
                val resolved = resolveNaturalDate(nlDateMatch.groupValues[3])
                if (resolved != null) return PendingAction("list_sessions_by_date", mapOf("dateID" to resolved))
            }

            val bookRegex = Regex(
                """book.*student\s+([A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,})\s+on\s+(\d{4}-\d{2}-\d{2})\s+from\s+([0-2]\d:[0-5]\d)\s+to\s+([0-2]\d:[0-5]\d)\s+in\s+(.+?)\s+for\s+exam\s+["']?(.+?)["']?(?:\s+under\s+faculty\s+([A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}))?\.?$""",
                RegexOption.IGNORE_CASE
            )
            val bookMatch = bookRegex.find(text)
            if (bookMatch != null) {
                val params = mutableMapOf(
                    "studentEmail" to bookMatch.groupValues[1].trim(),
                    "dateID" to bookMatch.groupValues[2].trim(),
                    "startTime" to bookMatch.groupValues[3].trim(),
                    "endTime" to bookMatch.groupValues[4].trim(),
                    "roomName" to bookMatch.groupValues[5].trim(),
                    "examName" to bookMatch.groupValues[6].trim()
                )
                val facultyEmail = bookMatch.groupValues.getOrNull(7).orEmpty().trim()
                if (facultyEmail.isNotBlank()) {
                    params["facultyEmail"] = facultyEmail
                }
                return PendingAction("book_session", params)
            }
        }

        // Catch-all wizard-eligible intents (no specific params provided)
        if (userRole in listOf(0, 1) &&
            lower.matches(Regex(".*\\b(book|schedule)\\b.*\\b(session|exam|student)\\b.*"))
        ) {
            return PendingAction("book_session", emptyMap())
        }
        if (userRole in listOf(0, 1) &&
            lower.matches(Regex(".*\\b(list|show|view|check)\\b.*\\bsessions?\\b.*"))
        ) {
            return PendingAction("list_sessions_by_date", emptyMap())
        }
        if (userRole == 2 &&
            lower.matches(Regex(".*\\b(update|set|change)\\b.*\\bavailabilit(y|ies)\\b.*"))
        ) {
            return PendingAction("update_availability", emptyMap())
        }
        if (userRole == 0 &&
            lower.matches(Regex(".*\\b(generate|create|build|run)\\b.*\\breport\\b.*"))
        ) {
            return PendingAction("generate_report", emptyMap())
        }
        if (userRole == 0 &&
            lower.matches(Regex(".*\\b(send|make|post)\\b.*\\bannouncement\\b.*"))
        ) {
            return PendingAction("send_announcement", emptyMap())
        }
        if (userRole in listOf(0, 1) &&
            lower.matches(Regex(".*\\b(send|write)\\b.*\\b(message|dm)\\b.*\\b(to|user)\\b.*"))
        ) {
            return PendingAction("send_message_to_user", emptyMap())
        }
        return null
    }

    private fun buildActionSummary(actionId: String, params: Map<String, String>): String {
        return when (actionId) {
            "book_session" -> {
                val faculty = params["facultyEmail"]?.let { " under faculty $it" } ?: ""
                "I am about to book \"${params["examName"]}\" for ${params["studentEmail"]} on ${params["dateID"]} from ${params["startTime"]} to ${params["endTime"]} in ${params["roomName"]}$faculty."
            }
            "cancel_booking" -> "I am about to cancel booking ${params["bookingId"]}."
            "deactivate_account" -> "I am about to deactivate the account for ${params["email"]}."
            "delete_session" -> "I am about to delete session ${params["sessionId"]}."
            "checkin_student" -> "I am about to check in the student for booking ${params["bookingId"]}."
            "report_incident" -> "I am about to report an incident for booking ${params["bookingId"]}: ${params["description"]}."
            "mark_exam_complete" -> "I am about to mark booking ${params["bookingId"]} as finished."
            "approve_account" -> "I am about to approve the account for ${params["email"]}."
            "reject_account" -> "I am about to reject the account for ${params["email"]}."
            "assign_proctor" -> "I am about to assign proctor ${params["proctorEmail"]} to ${params["roomName"]} on ${params["dateID"]}."
            "send_announcement" -> "I am about to send an announcement: \"${params["message"]}\"."
            "manage_room" -> "I am about to create/update room ${params["roomName"]} on ${params["dateID"]} (cap: ${params["totalCap"]}, ${params["startTime"]}-${params["endTime"]})."
            else -> "I am about to run action: $actionId."
        }
    }

    private suspend fun precheckBookSession(params: Map<String, String>): BookPrecheckResult {
        val dateID = params["dateID"].orEmpty()
        val requestedRoom = params["roomName"].orEmpty()
        val sessionSummary = executor.listSessionsByDate(dateID)

        if (sessionSummary.startsWith("No sessions found")) {
            return BookPrecheckResult(
                message = "No sessions exist on $dateID. Please choose another date or create sessions first."
            )
        }

        val availableRooms = extractRoomsFromSessionSummary(sessionSummary)
        if (availableRooms.isNotEmpty() && availableRooms.none { it.equals(requestedRoom, ignoreCase = true) }) {
            val suggestion = closestMatch(requestedRoom, availableRooms)
            val roomList = availableRooms.joinToString(", ")
            return if (suggestion != null) {
                val suggestedParams = params.toMutableMap().apply { this["roomName"] = suggestion }
                BookPrecheckResult(
                    message = "Room \"$requestedRoom\" is not available on $dateID. Did you mean \"$suggestion\"? Available rooms: $roomList. Press 'Confirm' to use $suggestion and continue.",
                    suggestedAction = PendingAction("book_session", suggestedParams)
                )
            } else {
                BookPrecheckResult(
                    message = "Room \"$requestedRoom\" is not available on $dateID. Available rooms: $roomList."
                )
            }
        }

        if (userRole == 0) {
            val facultyEmail = params["facultyEmail"].orEmpty().trim()
            if (facultyEmail.isBlank()) {
                return BookPrecheckResult(
                    message = "For admin bookings, include a faculty email (example: under faculty prof@mymacewan.ca)."
                )
            }

            val facultySearch = executor.searchUserByEmail(facultyEmail)
            val facultyEmails = extractApprovedFacultyEmails(facultySearch)
            val exactFaculty = facultyEmails.any { it.equals(facultyEmail, ignoreCase = true) }
            if (!exactFaculty) {
                val suggestion = closestMatch(facultyEmail, facultyEmails)
                return if (suggestion != null) {
                    val suggestedParams = params.toMutableMap().apply { this["facultyEmail"] = suggestion }
                    BookPrecheckResult(
                        message = "I couldn't find an approved faculty account for \"$facultyEmail\". Did you mean \"$suggestion\"? Reply 'yes' to use $suggestion and continue.",
                        suggestedAction = PendingAction("book_session", suggestedParams)
                    )
                } else {
                    BookPrecheckResult(
                        message = "I couldn't find an approved faculty account for \"$facultyEmail\"."
                    )
                }
            }
        }

        return BookPrecheckResult()
    }

    private fun extractRoomsFromSessionSummary(text: String): List<String> {
        val regex = Regex("""(?m)^-\s+([^:]+):""")
        return regex.findAll(text).map { it.groupValues[1].trim() }.toList()
    }

    private fun extractApprovedFacultyEmails(text: String): List<String> {
        val lineRegex = Regex("""(?m)^•\s+.*\(([^)]+)\)\s+—\s+status:\s*Approved,\s*role:\s*Faculty\s*$""")
        return lineRegex.findAll(text).map { it.groupValues[1].trim() }.toList()
    }

    private fun closestMatch(target: String, candidates: List<String>): String? {
        if (target.isBlank() || candidates.isEmpty()) return null
        val scored = candidates.map { it to levenshtein(target.lowercase(), it.lowercase()) }
        val best = scored.minByOrNull { it.second } ?: return null
        return if (best.second <= 4) best.first else null
    }

    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length

        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j

        for (i in 1..a.length) {
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost
                )
            }
        }
        return dp[a.length][b.length]
    }

    private fun normalizeActionParams(actionId: String, params: Map<String, String>): Map<String, String> {
        val normalized = params.toMutableMap()

        // Normalize date aliases
        val dateActions = setOf(
            "list_sessions_by_date", "book_session", "update_availability",
            "assign_proctor", "manage_room"
        )
        if (actionId in dateActions) {
            if (normalized["dateID"].isNullOrBlank()) {
                normalized["dateID"] = normalized["date"] ?: ""
            }
            // Phase 5: resolve natural language dates
            val dateVal = normalized["dateID"].orEmpty()
            if (dateVal.isNotBlank() && !dateVal.matches(Regex("^\\d{4}-\\d{2}-\\d{2}$"))) {
                resolveNaturalDate(dateVal)?.let { normalized["dateID"] = it }
            }
        }

        if (actionId == "book_session") {
            if (normalized["studentEmail"].isNullOrBlank()) {
                normalized["studentEmail"] = normalized["student"] ?: ""
            }
        }

        // Normalize start/end date aliases for generate_report
        if (actionId == "generate_report") {
            for (key in listOf("startDate", "endDate")) {
                val v = normalized[key].orEmpty()
                if (v.isNotBlank() && !v.matches(Regex("^\\d{4}-\\d{2}-\\d{2}$"))) {
                    resolveNaturalDate(v)?.let { normalized[key] = it }
                }
            }
        }

        return normalized
    }

    private fun validateActionParams(actionId: String, params: Map<String, String>): String? {
        val dateRegex = Regex("^\\d{4}-\\d{2}-\\d{2}$")
        val timeRegex = Regex("^([01]\\d|2[0-3]):[0-5]\\d$")

        return when (actionId) {
            "list_sessions_by_date", "update_availability", "get_my_assigned_sessions" -> {
                val dateID = params["dateID"].orEmpty()
                if (dateID.isNotBlank() && !dateRegex.matches(dateID))
                    "Please provide the date as YYYY-MM-DD (for example, 2026-03-20), or use 'today', 'tomorrow', 'next Monday', etc."
                else null
            }

            "book_session" -> {
                val dateID = params["dateID"].orEmpty()
                val start = params["startTime"].orEmpty()
                val end = params["endTime"].orEmpty()
                when {
                    !dateRegex.matches(dateID) -> "Please provide the booking date as YYYY-MM-DD."
                    !timeRegex.matches(start) -> "Please provide startTime using HH:mm (24-hour), for example 10:00."
                    !timeRegex.matches(end) -> "Please provide endTime using HH:mm (24-hour), for example 11:30."
                    start >= end -> "startTime must be earlier than endTime."
                    userRole == 0 && params["facultyEmail"].isNullOrBlank() -> "For admin bookings, facultyEmail is required."
                    else -> null
                }
            }

            "manage_room" -> {
                val dateID = params["dateID"].orEmpty()
                val start = params["startTime"].orEmpty()
                val end = params["endTime"].orEmpty()
                val cap = params["totalCap"].orEmpty()
                when {
                    !dateRegex.matches(dateID) -> "Please provide the date as YYYY-MM-DD."
                    !timeRegex.matches(start) -> "startTime must be HH:mm format."
                    !timeRegex.matches(end) -> "endTime must be HH:mm format."
                    start >= end -> "startTime must be earlier than endTime."
                    cap.toIntOrNull() == null || cap.toInt() <= 0 -> "totalCap must be a positive number."
                    else -> null
                }
            }

            "generate_report" -> {
                val s = params["startDate"].orEmpty()
                val e = params["endDate"].orEmpty()
                when {
                    !dateRegex.matches(s) -> "startDate must be YYYY-MM-DD."
                    !dateRegex.matches(e) -> "endDate must be YYYY-MM-DD."
                    s > e -> "startDate must be before endDate."
                    else -> null
                }
            }

            "assign_proctor" -> {
                val dateID = params["dateID"].orEmpty()
                if (!dateRegex.matches(dateID)) "dateID must be YYYY-MM-DD." else null
            }

            else -> null
        }
    }

    // ── System prompt builder ───────────────────────────────

    private fun buildSystemPrompt(role: Int, uid: String, name: String): String = buildString {
        appendLine("You are DuckBot, an AI assistant for DuckDuckDefer — a university exam deferral management app.")
        appendLine("The current user is: $name (role: ${roleLabel(role)}, uid: $uid).")
        appendLine("Never bypass role permissions. If an action is unavailable for this role, explain and offer allowed alternatives.")
        appendLine()

        // Phase 6: language preference
        if (userLanguage != "English") {
            appendLine("The user prefers responses in $userLanguage. Respond in $userLanguage whenever possible.")
            appendLine()
        }

        // Phase 6: context-aware help
        appendLine("=== Current Context ===")
        appendLine("The user is currently on the \"$currentScreenRoute\" screen.")
        appendLine("Provide contextually relevant help based on what they might be doing on this screen.")
        appendLine()

        // FAQ section (all roles)
        appendLine("=== Deferral FAQ ===")
        appendLine("Q: How do I defer? A: Go to your Student Dashboard, click 'New Deferral Request', fill in the form with your course, reason, and supporting documents, then submit. Or say 'Request a deferral' and I can do it for you!")
        appendLine("Q: How long does it take to get my deferral approved? A: Typically 3-5 business days. Your professor and admin review the request.")
        appendLine("Q: What's my professor's deferral policy? A: Each professor may have different policies. Upload the course outline PDF and I can analyze it for you.")
        appendLine("Q: How do I cancel a deferral? A: Say 'cancel deferral' and provide the deferral ID, or go to Dashboard → My Deferrals.")
        appendLine("Q: Would my use-case be deferred? A: Common accepted reasons include medical emergencies, family emergencies, and religious observances. Upload your course outline for specifics.")
        appendLine("Q: Where can I see my past deferrals? A: Say 'show my deferrals' or navigate to Dashboard → My Deferrals.")
        appendLine()

        // MacEwan University Deferral Guidelines
        appendLine("=== MacEwan University Deferral Guidelines ===")
        appendLine("Use the following deferral rules when answering deferral questions. These guidelines reflect MacEwan University's general deferral practices. Always recommend students verify with the Registrar's Office or macewan.ca for the most up-to-date information.")
        appendLine()
        appendLine("**What is a Deferred Exam?**")
        appendLine("A deferred exam allows a student to write a final exam at a later date when they were unable to write it at the originally scheduled time due to an approved reason.")
        appendLine()
        appendLine("**Eligibility — Accepted Reasons for Deferral:**")
        appendLine("1. Medical reasons — illness or injury supported by a medical certificate from a licensed physician or nurse practitioner. The certificate must cover the date of the exam.")
        appendLine("2. Compassionate reasons — a death or serious illness of an immediate family member (parent, spouse, child, sibling, grandparent).")
        appendLine("3. Religious observance — a conflict with a bona fide religious obligation. Should be declared early in the term.")
        appendLine("4. Conflicting exams — two or more final exams scheduled at the same date and time, or three final exams within a 24-hour period.")
        appendLine("5. University-sanctioned activity — varsity athletics, field trips, or university-approved events that directly conflict with the exam time.")
        appendLine("6. Other extraordinary circumstances — at the discretion of the Dean or department, such as a court summons, military duty, or a serious unforeseen emergency.")
        appendLine()
        appendLine("**NOT Accepted as Deferral Reasons:**")
        appendLine("- Travel or vacation plans (including pre-booked flights).")
        appendLine("- Work schedule conflicts.")
        appendLine("- Misreading the exam schedule.")
        appendLine("- Oversleeping or routine transportation issues.")
        appendLine("- General stress or test anxiety without a supporting medical certificate.")
        appendLine("- Technology or internet problems for in-person exams.")
        appendLine()
        appendLine("**How to Apply for a Deferral:**")
        appendLine("1. Apply as soon as possible — ideally within 2 business days of the missed exam.")
        appendLine("2. Submit a Deferred Final Exam Application form to your department or the Registrar's Office.")
        appendLine("3. Attach all required supporting documentation (see below).")
        appendLine("4. Pay the applicable deferred exam fee at the time of application.")
        appendLine("5. In DuckDuckDefer: go to Dashboard → 'New Deferral Request', fill in course name, reason, attach documents, and submit. Or say 'Request a deferral' here!")
        appendLine()
        appendLine("**Deferred Exam Fee:**")
        appendLine("- The fee is \$53.50 per deferral, to a maximum of \$150 per semester for MacEwan students.")
        appendLine("- The fee is non-refundable once the deferral is approved.")
        appendLine("- Fee must be paid at the time of application.")
        appendLine()
        appendLine("**Timeline and Scheduling:**")
        appendLine("- Deferred exams are typically scheduled during the official deferred exam period, usually within the first two weeks of the following term.")
        appendLine("- Students are notified of the exact date, time, and room via their MacEwan email and through this app.")
        appendLine("- It is the student's responsibility to check the schedule and arrive on time.")
        appendLine()
        appendLine("**Important Rules:**")
        appendLine("- A student may not defer a deferred exam. If you miss the deferred exam, you receive a grade of 0 or F for the exam component.")
        appendLine("- The deferred exam may be in a different format than the original (e.g., oral, essay, or different questions) at the professor's discretion.")
        appendLine("- Students must be in good academic standing to write a deferred exam.")
        appendLine("- Providing fraudulent documentation is considered academic misconduct under MacEwan's Academic Integrity Policy and may result in disciplinary action.")
        appendLine("- An approved deferral may appear on the student's transcript as 'DEF' until the deferred exam is completed and graded.")
        appendLine()
        appendLine("**Supporting Documentation Requirements:**")
        appendLine("- Medical: a certificate from a licensed physician or nurse practitioner, dated on or before the exam date, stating the student was unable to write.")
        appendLine("- Compassionate: a death certificate, obituary, or documentation of a family member's serious illness.")
        appendLine("- Religious: a written explanation of the religious observance, ideally declared at the start of the term.")
        appendLine("- Exam conflict: a screenshot or printout of the official exam schedule showing the overlap.")
        appendLine("- University-sanctioned: a letter from the coach, instructor, or activity supervisor confirming the date conflict.")
        appendLine()
        appendLine("**Contact:**")
        appendLine("- For deferral questions, contact the Registrar's Office at MacEwan University.")
        appendLine("- Visit macewan.ca or check the Academic Calendar for the latest policies, fees, and deadlines.")
        appendLine("- For course-specific deferral policies, contact your professor or department directly.")
        appendLine()
        appendLine("When answering deferral questions, reference these guidelines. If a student describes their situation, evaluate it against the accepted/not-accepted reasons above and advise accordingly. Always recommend they confirm details with the Registrar's Office.")
        appendLine()

        // Navigation routes
        appendLine(ChatbotRoutes.toSystemPromptBlock())
        appendLine()

        // Role-specific actions
        val actionsBlock = ChatbotActionRegistry.toSystemPromptBlock(role)
        if (actionsBlock.isNotBlank()) {
            appendLine("=== Available Actions ===")
            appendLine(actionsBlock)
        }

        // PDF analysis instructions
        appendLine("=== PDF Analysis ===")
        appendLine("When the user uploads a PDF (course outline), you will receive its text content.")
        appendLine("Analyze it to find: 1) The professor's deferral policy, 2) Likely accepted deferral reasons, 3) Any deadlines.")
        appendLine("After analysis, suggest navigating to the professor's profile if identifiable.")
        appendLine()

        // Phase 5: smart scheduling
        appendLine("=== Smart Scheduling ===")
        appendLine("When a booking fails due to full capacity, suggest alternative available time slots or rooms from the same date.")
        appendLine("When listing sessions, highlight rooms with the most available seats.")
        appendLine("The user can use natural language dates: 'today', 'tomorrow', 'next Monday', etc. You should resolve them to ISO format.")
        appendLine()

        appendLine("=== Response Guidelines ===")
        appendLine("- Be concise and helpful.")
        appendLine("- Use emojis sparingly for friendliness.")
        appendLine("- When suggesting a screen, include the navigation JSON AND describe the path (e.g. 'Go to Dashboard → Settings').")
        appendLine("- NEVER make up information about the user's deferrals or bookings — use actions to query real data.")
        appendLine("- For destructive actions, clearly state what will happen before the confirmation prompt.")
        appendLine("- For automation, gather missing required parameters first. Do not guess IDs, emails, dates, or times.")
        appendLine("- Only emit action JSON after the user asks to proceed with the operation.")
        appendLine("- Return at most one JSON block per message.")
        appendLine("- Use action JSON only in this shape: {\"action\":\"<actionId>\",\"params\":{...}}")
        appendLine("- Use navigation JSON only in this shape: {\"navigate\":\"<routeId>\"}")
        appendLine("- If the user asks for information you can fetch with an action, use the action rather than guessing.")
        appendLine("- Proactively suggest relevant actions based on what the user is asking about.")
        appendLine()

        appendLine("=== Formatting ===")
        appendLine("When displaying structured data (schedules, bookings, availability, sessions, lists of items with multiple fields), use markdown pipe tables:")
        appendLine("| Column1 | Column2 | Column3 |")
        appendLine("|---------|---------|---------|")
        appendLine("| value   | value   | value   |")
        appendLine("Always include the header row and separator row. Keep column headers short.")
    }

    private fun buildQuickChips(role: Int): List<String> {
        return when (role) {
            0 -> listOf(
                "Today's statistics",
                "View pending approvals",
                "Approve pending accounts",
                "Assign a proctor",
                "List sessions for a date",
                "Book a student exam slot",
                "Send an announcement",
                "Generate weekly report",
                "Search a user",
                "Notification summary"
            )
            1 -> listOf(
                "Show my bookings",
                "List sessions for a date",
                "Book a student exam slot",
                "Cancel a booking",
                "My pending deferral requests",
                "Approve a deferral",
                "How many students have deferrals?",
                "What's my availability this week?",
                "Message a student",
                "Notification summary"
            )
            2 -> listOf(
                "My sessions this week",
                "Check in a student",
                "Report an incident",
                "Mark exam complete",
                "Update my availability",
                "View booking details",
                "Notification summary"
            )
            3 -> listOf(
                "Show my upcoming exams",
                "What's my exam status?",
                "Exam checklist",
                "Request a deferral",
                "Show my deferral history",
                "Cancel a deferral",
                "How do I defer?",
                "MacEwan deferral rules",
                "What reasons are accepted for deferral?",
                "How much is the deferral fee?",
                "Professor's deferral policy?",
                "Notification summary"
            )
            else -> listOf(
                "How do I defer?",
                "How long for approval?",
                "Notification summary"
            )
        }
    }

    private fun roleLabel(role: Int): String = when (role) {
        0 -> "Admin"
        1 -> "Faculty"
        2 -> "Proctor"
        3 -> "Student"
        else -> "Unknown"
    }
}


