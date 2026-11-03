package com.example.duckduckdefer.components.chatbot

/**
 * Registry of all navigable screen routes the chatbot can link to.
 * To add a new screen, just add a new entry here.
 *
 * @param routeId  Unique identifier used in navigation and by the AI
 * @param displayName  Human-readable name shown in chat
 * @param navRoute  The actual Compose Navigation route string (may contain placeholders)
 * @param description  Brief description for the AI system prompt
 */
data class ChatbotRoute(
    val routeId: String,
    val displayName: String,
    val navRoute: String,
    val description: String
)

object ChatbotRoutes {
    /**
     * Master list of screens the chatbot can navigate to.
     * Add new entries here to make them available to the chatbot.
     */
    val allRoutes: List<ChatbotRoute> = listOf(
        ChatbotRoute(
            routeId = "dashboard",
            displayName = "Dashboard",
            navRoute = "dashboard",
            description = "Main dashboard screen"
        ),
        ChatbotRoute(
            routeId = "settings",
            displayName = "Settings",
            navRoute = "settings",
            description = "App settings and profile options"
        ),
        ChatbotRoute(
            routeId = "messages",
            displayName = "Messages",
            navRoute = "messages",
            description = "Conversations & messaging screen"
        ),
        ChatbotRoute(
            routeId = "profile_self",
            displayName = "My Profile",
            navRoute = "profile/{userId}",
            description = "View your own profile"
        ),
        ChatbotRoute(
            routeId = "edit_profile",
            displayName = "Edit Profile",
            navRoute = "editProfile",
            description = "Edit your profile information"
        ),
        ChatbotRoute(
            routeId = "create_weekly_sessions",
            displayName = "Create Weekly Sessions",
            navRoute = "detailed_weekly_sessions",
            description = "Admin screen to create weekly exam sessions"
        ),
        ChatbotRoute(
            routeId = "my_bookings",
            displayName = "My Bookings",
            navRoute = "my_bookings",
            description = "View your bookings (faculty/student)"
        ),
        ChatbotRoute(
            routeId = "notification_center",
            displayName = "Notifications",
            navRoute = "notification_center",
            description = "View your notification history"
        ),
        ChatbotRoute(
            routeId = "proctor_availability",
            displayName = "Proctor Availability",
            navRoute = "proctor_availability",
            description = "Manage or view proctor availability"
        ),
        ChatbotRoute(
            routeId = "manage_users",
            displayName = "Manage Users",
            navRoute = "manage_users",
            description = "Admin screen to approve/reject/manage user accounts"
        ),
        ChatbotRoute(
            routeId = "proctor_assignment",
            displayName = "Proctor Assignment",
            navRoute = "proctor_assignment",
            description = "Admin screen to assign proctors to sessions"
        ),
        ChatbotRoute(
            routeId = "leaderboard",
            displayName = "Leaderboard",
            navRoute = "leaderboard",
            description = "View the gamification leaderboard"
        ),
        ChatbotRoute(
            routeId = "notification_settings",
            displayName = "Notification Settings",
            navRoute = "notificationSettings",
            description = "Configure your notification preferences"
        ),
        ChatbotRoute(
            routeId = "accessibility_settings",
            displayName = "Accessibility Settings",
            navRoute = "accessibilitySettings",
            description = "Adjust theme, font size, and contrast"
        ),
        ChatbotRoute(
            routeId = "help_onboarding",
            displayName = "Help & Onboarding",
            navRoute = "help_onboarding",
            description = "Guides, FAQs, walkthrough, and support resources"
        ),
        ChatbotRoute(
            routeId = "feedback",
            displayName = "Feedback",
            navRoute = "feedback",
            description = "Submit feedback, bug reports, or feature requests to the developers"
        ),
        ChatbotRoute(
            routeId = "view_feedback",
            displayName = "View Feedback",
            navRoute = "view_feedback",
            description = "Admin screen to view and review user feedback submissions"
        )
        // ──────────────────────────────────────────────
        // ADD NEW ROUTES ABOVE THIS LINE
        // ──────────────────────────────────────────────
    )

    /** Look up a route by its routeId. */
    fun findRoute(routeId: String): ChatbotRoute? =
        allRoutes.firstOrNull { it.routeId == routeId }

    /** Build a route description block for the system prompt. */
    fun toSystemPromptBlock(): String = buildString {
        appendLine("Available screens the user can navigate to:")
        allRoutes.forEach { route ->
            appendLine("- routeId=\"${route.routeId}\" displayName=\"${route.displayName}\": ${route.description}")
        }
        appendLine()
        appendLine("When you want to suggest navigating to a screen, include a JSON block in your response like:")
        appendLine("""{"navigate": "<routeId>"}""")
        appendLine("The app will render a clickable button for the user.")
    }
}

