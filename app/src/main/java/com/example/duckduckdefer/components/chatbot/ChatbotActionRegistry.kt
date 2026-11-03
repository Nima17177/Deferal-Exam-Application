package com.example.duckduckdefer.components.chatbot

/**
 * Declarative registry of all chatbot actions available per role.
 * Adding a new action = one entry here + one handler in ChatbotActionExecutor.
 */
data class ChatbotAction(
    val actionId: String,
    val description: String,
    val requiredParams: List<String>,
    val allowedRoles: List<Int>, // 0=Admin, 1=Faculty, 2=Proctor, 3=Student
    val isDestructive: Boolean = false
)

enum class WizardInputType {
    TEXT, DATE_PICKER, ROOM_PICKER, TIME_PICKER, EMAIL, BOOLEAN
}

data class WizardStep(
    val paramName: String,
    val prompt: String,
    val inputType: WizardInputType
)

object ChatbotActionRegistry {

    val allActions: List<ChatbotAction> = listOf(
        // ── Admin actions ───────────────────────────────────
        ChatbotAction(
            actionId = "search_user_by_email",
            description = "Search for a user account by email address",
            requiredParams = listOf("email"),
            allowedRoles = listOf(0)
        ),
        ChatbotAction(
            actionId = "deactivate_account",
            description = "Deactivate a user account (requires confirmation)",
            requiredParams = listOf("email"),
            allowedRoles = listOf(0),
            isDestructive = true
        ),
        ChatbotAction(
            actionId = "reactivate_account",
            description = "Reactivate a previously deactivated user account",
            requiredParams = listOf("email"),
            allowedRoles = listOf(0)
        ),
        ChatbotAction(
            actionId = "list_sessions_by_date",
            description = "List all exam sessions on a given date. If the user doesn't know the exact time, call this to show all sessions so they can pick one.",
            requiredParams = listOf("dateID"),
            allowedRoles = listOf(0, 1)
        ),
        ChatbotAction(
            actionId = "book_session",
            description = "Create an exam booking for a student in a specific room/time. For admins, include facultyEmail; for faculty, the app uses the current faculty account.",
            requiredParams = listOf("studentEmail", "examName", "dateID", "roomName", "startTime", "endTime"),
            allowedRoles = listOf(0, 1),
            isDestructive = true
        ),
        ChatbotAction(
            actionId = "delete_session",
            description = "Delete a specific exam session by its ID (requires confirmation)",
            requiredParams = listOf("sessionId"),
            allowedRoles = listOf(0),
            isDestructive = true
        ),
        ChatbotAction(
            actionId = "get_pending_accounts",
            description = "Get the list of accounts waiting for admin approval",
            requiredParams = emptyList(),
            allowedRoles = listOf(0)
        ),

        // ── Faculty actions ─────────────────────────────────
        ChatbotAction(
            actionId = "get_deferral_count",
            description = "Get the number of students currently taking deferrals for this faculty member",
            requiredParams = emptyList(),
            allowedRoles = listOf(1)
        ),
        ChatbotAction(
            actionId = "get_faculty_availability",
            description = "Get this faculty member's availability / scheduled sessions for the current week",
            requiredParams = emptyList(),
            allowedRoles = listOf(1)
        ),

        // ── Student actions (Phase 1) ────────────────────────
        ChatbotAction(
            actionId = "get_my_bookings",
            description = "Get the current user's upcoming exam bookings with date, time, room, and status",
            requiredParams = emptyList(),
            allowedRoles = listOf(1, 3)
        ),
        ChatbotAction(
            actionId = "get_booking_status",
            description = "Get detailed status of a specific booking by its ID",
            requiredParams = listOf("bookingId"),
            allowedRoles = listOf(0, 1, 2, 3)
        ),
        ChatbotAction(
            actionId = "get_my_deferrals",
            description = "Get the current student's deferral history and status",
            requiredParams = emptyList(),
            allowedRoles = listOf(3)
        ),
        ChatbotAction(
            actionId = "get_exam_checklist",
            description = "Get a checklist of what the student needs for their next upcoming exam (payment, documents, time, room)",
            requiredParams = emptyList(),
            allowedRoles = listOf(3)
        ),

        // ── Proctor actions (Phase 2) ────────────────────────
        ChatbotAction(
            actionId = "get_my_assigned_sessions",
            description = "Get the proctor's assigned sessions. Optionally filter by dateID.",
            requiredParams = emptyList(),
            allowedRoles = listOf(2)
        ),
        ChatbotAction(
            actionId = "update_availability",
            description = "Set availability for a specific date as a proctor",
            requiredParams = listOf("dateID", "available"),
            allowedRoles = listOf(2)
        ),
        ChatbotAction(
            actionId = "get_booking_details",
            description = "Get full details of a booking by its ID (student, faculty, exam, status, time, room)",
            requiredParams = listOf("bookingId"),
            allowedRoles = listOf(0, 1, 2)
        ),
        ChatbotAction(
            actionId = "checkin_student",
            description = "Check in a student for their exam by booking ID (sets status to checked-in)",
            requiredParams = listOf("bookingId"),
            allowedRoles = listOf(0, 2),
            isDestructive = true
        ),
        ChatbotAction(
            actionId = "report_incident",
            description = "Report an incident during an exam. Requires booking ID and a description of the incident.",
            requiredParams = listOf("bookingId", "description"),
            allowedRoles = listOf(0, 2),
            isDestructive = true
        ),
        ChatbotAction(
            actionId = "mark_exam_complete",
            description = "Mark an exam as finished by booking ID (requires confirmation)",
            requiredParams = listOf("bookingId"),
            allowedRoles = listOf(0, 2),
            isDestructive = true
        ),

        // ── Enhanced Faculty actions (Phase 3) ───────────────
        ChatbotAction(
            actionId = "cancel_booking",
            description = "Cancel an existing booking by its ID. Restores seat capacity. (requires confirmation)",
            requiredParams = listOf("bookingId"),
            allowedRoles = listOf(0, 1),
            isDestructive = true
        ),
        ChatbotAction(
            actionId = "get_my_deferral_requests",
            description = "Get deferral requests submitted for this faculty member's courses",
            requiredParams = emptyList(),
            allowedRoles = listOf(1)
        ),
        ChatbotAction(
            actionId = "send_message_to_user",
            description = "Send a direct message to another user by their email address",
            requiredParams = listOf("recipientEmail", "messageText"),
            allowedRoles = listOf(0, 1)
        ),

        // ── Enhanced Admin actions (Phase 4) ─────────────────
        ChatbotAction(
            actionId = "approve_account",
            description = "Approve a pending user account by email (requires confirmation)",
            requiredParams = listOf("email"),
            allowedRoles = listOf(0),
            isDestructive = true
        ),
        ChatbotAction(
            actionId = "reject_account",
            description = "Reject a pending user account by email (requires confirmation)",
            requiredParams = listOf("email"),
            allowedRoles = listOf(0),
            isDestructive = true
        ),
        ChatbotAction(
            actionId = "assign_proctor",
            description = "Assign a proctor to a specific room on a given date",
            requiredParams = listOf("proctorEmail", "dateID", "roomName"),
            allowedRoles = listOf(0),
            isDestructive = true
        ),
        ChatbotAction(
            actionId = "get_system_stats",
            description = "Get system statistics: active users by role, today's bookings, pending accounts, upcoming sessions",
            requiredParams = emptyList(),
            allowedRoles = listOf(0)
        ),
        ChatbotAction(
            actionId = "send_announcement",
            description = "Send a push notification announcement to users. Optionally filter by role (0=Admin,1=Faculty,2=Proctor,3=Student).",
            requiredParams = listOf("message"),
            allowedRoles = listOf(0),
            isDestructive = true
        ),
        ChatbotAction(
            actionId = "manage_room",
            description = "Add or update a room for a session date. Provide capacity, start/end times.",
            requiredParams = listOf("dateID", "roomName", "totalCap", "startTime", "endTime"),
            allowedRoles = listOf(0),
            isDestructive = true
        ),
        ChatbotAction(
            actionId = "generate_report",
            description = "Generate a summary report for a date range: bookings, completion rate, incidents, room usage",
            requiredParams = listOf("startDate", "endDate"),
            allowedRoles = listOf(0)
        ),

        // ── Cross-role actions (Phase 6) ─────────────────────
        ChatbotAction(
            actionId = "get_notification_summary",
            description = "Summarize the user's recent unread notifications",
            requiredParams = emptyList(),
            allowedRoles = listOf(0, 1, 2, 3)
        )
        // ──────────────────────────────────────────────
        // ADD NEW ACTIONS ABOVE THIS LINE
        // ──────────────────────────────────────────────
    )

    val wizardSteps: Map<String, List<WizardStep>> = mapOf(
        "book_session" to listOf(
            WizardStep("dateID", "Which date would you like to book? Pick one below or type a date (YYYY-MM-DD):", WizardInputType.DATE_PICKER),
            WizardStep("roomName", "Which room?", WizardInputType.ROOM_PICKER),
            WizardStep("startTime", "Start time? (HH:mm, e.g. 10:00)", WizardInputType.TIME_PICKER),
            WizardStep("endTime", "End time? (HH:mm, e.g. 11:30)", WizardInputType.TIME_PICKER),
            WizardStep("studentEmail", "Student's email address:", WizardInputType.EMAIL),
            WizardStep("examName", "What is the exam name?", WizardInputType.TEXT)
        ),
        "assign_proctor" to listOf(
            WizardStep("dateID", "Which date? Pick one or type a date (YYYY-MM-DD):", WizardInputType.DATE_PICKER),
            WizardStep("roomName", "Which room?", WizardInputType.ROOM_PICKER),
            WizardStep("proctorEmail", "Proctor's email address:", WizardInputType.EMAIL)
        ),
        "manage_room" to listOf(
            WizardStep("dateID", "Which date? (YYYY-MM-DD):", WizardInputType.DATE_PICKER),
            WizardStep("roomName", "Room name:", WizardInputType.TEXT),
            WizardStep("totalCap", "Total capacity (number of seats):", WizardInputType.TEXT),
            WizardStep("startTime", "Start time (HH:mm):", WizardInputType.TIME_PICKER),
            WizardStep("endTime", "End time (HH:mm):", WizardInputType.TIME_PICKER)
        ),
        "report_incident" to listOf(
            WizardStep("bookingId", "Which booking ID?", WizardInputType.TEXT),
            WizardStep("description", "Describe the incident:", WizardInputType.TEXT)
        ),

        // Date-based actions
        "list_sessions_by_date" to listOf(
            WizardStep("dateID", "Which date would you like to view sessions for?", WizardInputType.DATE_PICKER)
        ),
        "update_availability" to listOf(
            WizardStep("dateID", "Which date?", WizardInputType.DATE_PICKER),
            WizardStep("available", "Are you available on this date?", WizardInputType.BOOLEAN)
        ),
        "generate_report" to listOf(
            WizardStep("startDate", "Start date for the report:", WizardInputType.DATE_PICKER),
            WizardStep("endDate", "End date for the report:", WizardInputType.DATE_PICKER)
        ),

        // Email-based actions
        "search_user_by_email" to listOf(
            WizardStep("email", "Enter the user's email address:", WizardInputType.EMAIL)
        ),
        "deactivate_account" to listOf(
            WizardStep("email", "Email of the account to deactivate:", WizardInputType.EMAIL)
        ),
        "reactivate_account" to listOf(
            WizardStep("email", "Email of the account to reactivate:", WizardInputType.EMAIL)
        ),
        "approve_account" to listOf(
            WizardStep("email", "Email of the account to approve:", WizardInputType.EMAIL)
        ),
        "reject_account" to listOf(
            WizardStep("email", "Email of the account to reject:", WizardInputType.EMAIL)
        ),

        // Booking ID actions
        "get_booking_status" to listOf(
            WizardStep("bookingId", "Enter the booking ID:", WizardInputType.TEXT)
        ),
        "get_booking_details" to listOf(
            WizardStep("bookingId", "Enter the booking ID:", WizardInputType.TEXT)
        ),
        "cancel_booking" to listOf(
            WizardStep("bookingId", "Enter the booking ID to cancel:", WizardInputType.TEXT)
        ),
        "checkin_student" to listOf(
            WizardStep("bookingId", "Enter the booking ID to check in:", WizardInputType.TEXT)
        ),
        "mark_exam_complete" to listOf(
            WizardStep("bookingId", "Enter the booking ID to mark as complete:", WizardInputType.TEXT)
        ),

        // Session ID actions
        "delete_session" to listOf(
            WizardStep("sessionId", "Enter the session ID to delete:", WizardInputType.TEXT)
        ),

        // Multi-param text actions
        "send_announcement" to listOf(
            WizardStep("message", "What announcement would you like to send?", WizardInputType.TEXT)
        ),
        "send_message_to_user" to listOf(
            WizardStep("recipientEmail", "Recipient's email address:", WizardInputType.EMAIL),
            WizardStep("messageText", "Message to send:", WizardInputType.TEXT)
        )
    )

    val followUpChips: Map<String, List<String>> = mapOf(
        "get_my_bookings" to listOf("View booking details", "Cancel a booking", "Exam checklist"),
        "get_booking_status" to listOf("Cancel this booking", "Show all my bookings"),
        "get_booking_details" to listOf("Check in student", "Report incident", "Mark complete"),
        "list_sessions_by_date" to listOf("Book a session", "Check another date"),
        "get_my_deferrals" to listOf("Show my bookings", "Exam checklist"),
        "get_my_deferral_requests" to listOf("Show my bookings", "List sessions for a date"),
        "get_exam_checklist" to listOf("Show my bookings"),
        "get_system_stats" to listOf("View pending approvals", "Generate report", "Send announcement"),
        "get_pending_accounts" to listOf("Approve an account", "Reject an account"),
        "book_session" to listOf("Book another", "Show my bookings", "List sessions"),
        "get_my_assigned_sessions" to listOf("Update my availability", "Check in a student"),
        "cancel_booking" to listOf("Show my bookings", "List sessions for a date"),
        "checkin_student" to listOf("View booking details", "Report an incident"),
        "mark_exam_complete" to listOf("My sessions this week", "Check in a student"),
        "report_incident" to listOf("Mark exam complete", "View booking details"),
        "get_notification_summary" to listOf("Show my bookings", "Dashboard"),
        "get_deferral_count" to listOf("My pending deferral requests", "Show my bookings"),
        "get_faculty_availability" to listOf("List sessions for a date", "Book a session"),
        "update_availability" to listOf("My sessions this week"),
        "send_message_to_user" to listOf("Send another message"),
        "approve_account" to listOf("View pending approvals", "Search a user"),
        "reject_account" to listOf("View pending approvals", "Search a user"),
        "assign_proctor" to listOf("List sessions for a date", "Assign another proctor"),
        "send_announcement" to listOf("Today's statistics"),
        "manage_room" to listOf("List sessions for a date"),
        "generate_report" to listOf("Today's statistics", "Send announcement"),
        "search_user_by_email" to listOf("Approve an account", "Deactivate account")
    )

    /** Return only the actions available for the given role. */
    fun actionsForRole(role: Int): List<ChatbotAction> =
        allActions.filter { role in it.allowedRoles }

    /** Find an action by its ID. */
    fun findAction(actionId: String): ChatbotAction? =
        allActions.firstOrNull { it.actionId == actionId }

    /** Build a description block for the system prompt, filtered by role. */
    fun toSystemPromptBlock(role: Int): String = buildString {
        val actions = actionsForRole(role)
        if (actions.isEmpty()) return@buildString
        appendLine("You can execute the following actions on behalf of the user.")
        appendLine("To execute an action, respond with a JSON block:")
        appendLine("""{"action": "<actionId>", "params": {<key>: <value>, ...}}""")
        appendLine("Use ISO date format YYYY-MM-DD and time format HH:mm for all booking/session params.")
        appendLine("Return at most one action JSON block per response.")
        appendLine()
        actions.forEach { action ->
            append("- actionId=\"${action.actionId}\": ${action.description}")
            if (action.requiredParams.isNotEmpty()) {
                append(" | params: ${action.requiredParams.joinToString(", ")}")
            }
            if (action.isDestructive) {
                append(" [DESTRUCTIVE – the app will ask the user to confirm before executing]")
            }
            appendLine()
        }
        appendLine()
        appendLine("If a required parameter is missing, ask the user for it conversationally.")
    }
}

