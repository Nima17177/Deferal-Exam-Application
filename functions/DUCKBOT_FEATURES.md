# DuckBot AI Chatbot - Complete Feature List

DuckBot is the AI assistant built into DuckDuckDefer, powered by **Google Gemini 2.5 Flash Lite**. It provides role-specific actions, natural language interaction, and automation for all four user roles.

---

## Core Platform Features

| Feature | Description |
|---------|-------------|
| **Gemini AI Conversation** | Free-form natural language chat powered by Google Gemini 2.5 Flash Lite |
| **Role-Based Permissions** | Every action is gated by user role (Admin, Faculty, Proctor, Student). The AI cannot bypass permissions. |
| **Destructive Action Confirmation** | Actions that modify data (book, cancel, delete, etc.) require explicit user confirmation before execution |
| **PDF Course Outline Analysis** | Upload a PDF and DuckBot extracts text to identify deferral policies, deadlines, and accepted reasons |
| **Voice Input** | Microphone button with Android SpeechRecognizer for hands-free interaction |
| **In-Chat Navigation** | DuckBot can suggest app screens and render clickable "Go to..." buttons |
| **Quick Suggestion Chips** | Role-specific shortcut chips shown at the start of a conversation |
| **Conversation Persistence** | Chat history is saved to Firestore (`ChatbotConversations/{uid}/messages`) and restored on app restart |
| **Context-Aware Help** | DuckBot knows which screen the user is currently on and tailors responses accordingly |
| **Proactive Daily Briefing** | On chatbot open, auto-generates a role-specific summary of today's activity |
| **Natural Language Date Parsing** | Accepts "today", "tomorrow", "next Monday", "in 3 days", etc. and resolves to ISO dates |
| **Multi-Language Support** | Language preference is passed to the system prompt so Gemini responds in the user's preferred language |
| **Smart Scheduling Suggestions** | When a booking slot is full, suggests alternative available rooms/times |
| **Retry with Backoff** | Automatic retry on Gemini rate limits (15s, 30s, 60s backoff) |
| **Direct Command Parsing** | Structured commands (e.g. "show my bookings") are parsed client-side without needing a Gemini round-trip |

---

## Actions by Role

### Student (Role 3) - 6 Actions

| Action | Description | Params | Destructive |
|--------|-------------|--------|:-----------:|
| `get_my_bookings` | Fetch upcoming exam bookings with date, time, room, and status | _none_ | No |
| `get_booking_status` | Detailed status of a specific booking | `bookingId` | No |
| `get_my_deferrals` | Deferral request history and current status | _none_ | No |
| `get_exam_checklist` | Preparation checklist for next exam: payment, documents, time, room | _none_ | No |

**Quick Chips:** Show my upcoming exams, What's my exam status?, Exam checklist, Show my deferral history, How do I defer?, Professor's deferral policy?, Notification summary

---

### Proctor (Role 2) - 6 Actions

| Action | Description | Params | Destructive |
|--------|-------------|--------|:-----------:|
| `get_my_assigned_sessions` | View upcoming sessions assigned to this proctor | _none_ | No |
| `update_availability` | Set availability for a specific date | `dateID`, `available` | No |
| `get_booking_details` | Full details of a booking (student, faculty, exam, status, room) | `bookingId` | No |
| `checkin_student` | Check in a student for their exam (sets status to Checked In) | `bookingId` | Yes |
| `report_incident` | File an incident report during an exam | `bookingId`, `description` | Yes |
| `mark_exam_complete` | Mark an exam as finished | `bookingId` | Yes |

**Quick Chips:** My sessions this week, Check in a student, Report an incident, Mark exam complete, Update my availability, View booking details, Notification summary

---

### Faculty (Role 1) - 11 Actions

| Action | Description | Params | Destructive |
|--------|-------------|--------|:-----------:|
| `list_sessions_by_date` | List all exam sessions and rooms on a given date | `dateID` | No |
| `book_session` | Create an exam booking for a student in a room/time | `studentEmail`, `examName`, `dateID`, `roomName`, `startTime`, `endTime` | Yes |
| `get_deferral_count` | Count of deferral requests for this faculty | _none_ | No |
| `get_faculty_availability` | View scheduled sessions for the current week | _none_ | No |
| `get_my_bookings` | Fetch all bookings associated with this faculty | _none_ | No |
| `get_booking_status` | Detailed status of a specific booking | `bookingId` | No |
| `cancel_booking` | Cancel a booking and restore seat capacity | `bookingId` | Yes |
| `get_my_deferral_requests` | View deferral requests submitted for this faculty's courses | _none_ | No |
| `send_message_to_user` | Send a direct message to another user by email | `recipientEmail`, `messageText` | No |

**Quick Chips:** Show my bookings, List sessions for a date, Book a student exam slot, Cancel a booking, My pending deferral requests, How many students have deferrals?, What's my availability this week?, Message a student, Notification summary

---

### Admin (Role 0) - 19 Actions

| Action | Description | Params | Destructive |
|--------|-------------|--------|:-----------:|
| `search_user_by_email` | Search for a user account by email | `email` | No |
| `deactivate_account` | Deactivate a user account | `email` | Yes |
| `reactivate_account` | Reactivate a previously deactivated account | `email` | No |
| `list_sessions_by_date` | List all sessions on a given date | `dateID` | No |
| `book_session` | Create an exam booking (requires `facultyEmail` for admin) | `studentEmail`, `examName`, `dateID`, `roomName`, `startTime`, `endTime` | Yes |
| `delete_session` | Delete an entire exam session | `sessionId` | Yes |
| `get_pending_accounts` | List accounts waiting for approval | _none_ | No |
| `get_booking_status` | Detailed status of any booking | `bookingId` | No |
| `get_booking_details` | Full details of any booking | `bookingId` | No |
| `cancel_booking` | Cancel any booking and restore seats | `bookingId` | Yes |
| `checkin_student` | Check in a student for an exam | `bookingId` | Yes |
| `report_incident` | File an incident report on a booking | `bookingId`, `description` | Yes |
| `mark_exam_complete` | Mark an exam as finished | `bookingId` | Yes |
| `approve_account` | Approve a pending user account | `email` | Yes |
| `reject_account` | Reject a pending user account | `email` | Yes |
| `assign_proctor` | Assign a proctor to a room on a date | `proctorEmail`, `dateID`, `roomName` | Yes |
| `get_system_stats` | System-wide statistics (users by role, bookings, pending accounts) | _none_ | No |
| `send_announcement` | Broadcast a push notification to users (optionally by role) | `message`, `targetRole` (optional) | Yes |
| `manage_room` | Add or update a room for a session date | `dateID`, `roomName`, `totalCap`, `startTime`, `endTime` | Yes |
| `generate_report` | Summary report for a date range (bookings, completion rate, incidents) | `startDate`, `endDate` | No |
| `send_message_to_user` | Send a DM to any user by email | `recipientEmail`, `messageText` | No |

**Quick Chips:** Today's statistics, View pending approvals, Approve pending accounts, Assign a proctor, List sessions for a date, Book a student exam slot, Send an announcement, Generate weekly report, Search a user, Notification summary

---

### All Roles - 1 Cross-Role Action

| Action | Description | Params | Destructive |
|--------|-------------|--------|:-----------:|
| `get_notification_summary` | Summarize recent unread notifications | _none_ | No |

---

## Navigable Screens

DuckBot can suggest and link to these in-app screens via clickable buttons:

| Route ID | Display Name | Description |
|----------|-------------|-------------|
| `dashboard` | Dashboard | Main dashboard screen |
| `settings` | Settings | App settings and profile options |
| `messages` | Messages | Conversations and messaging |
| `profile_self` | My Profile | View your own profile |
| `edit_profile` | Edit Profile | Edit your profile information |
| `create_weekly_sessions` | Create Weekly Sessions | Admin: create weekly exam sessions |
| `my_bookings` | My Bookings | View your bookings (faculty/student) |
| `notification_center` | Notifications | View notification history |
| `proctor_availability` | Proctor Availability | Manage or view proctor availability |
| `manage_users` | Manage Users | Admin: approve/reject/manage accounts |
| `proctor_assignment` | Proctor Assignment | Admin: assign proctors to sessions |
| `leaderboard` | Leaderboard | Gamification leaderboard |
| `notification_settings` | Notification Settings | Configure notification preferences |
| `accessibility_settings` | Accessibility Settings | Adjust theme, font size, and contrast |

---

## Cloud Functions (Backend Endpoints)

All write operations go through authenticated Firebase Cloud Functions with role and approval checks.

| Function | Method | Allowed Roles | Purpose |
|----------|--------|:-------------:|---------|
| `chatbotListSessionsHttp` | POST | Admin, Faculty | List sessions for a date |
| `chatbotBookSessionHttp` | POST | Admin, Faculty | Create an exam booking (transactional) |
| `chatbotUpdateAvailabilityHttp` | POST | Proctor | Update proctor availability for a date |
| `chatbotCheckinStudentHttp` | POST | Admin, Proctor | Check in a student (status -> 3) |
| `chatbotReportIncidentHttp` | POST | Admin, Proctor | Add incident report to a booking |
| `chatbotMarkExamCompleteHttp` | POST | Admin, Proctor | Mark exam finished (status -> 4) |
| `chatbotCancelBookingHttp` | POST | Admin, Faculty | Cancel booking and restore seat capacity (transactional) |
| `chatbotSendMessageHttp` | POST | Admin, Faculty | Send a DM (creates conversation if needed) |
| `chatbotApproveAccountHttp` | POST | Admin | Approve a pending user account |
| `chatbotRejectAccountHttp` | POST | Admin | Reject a pending user account |
| `chatbotAssignProctorHttp` | POST | Admin | Assign proctor to session room |
| `chatbotSendAnnouncementHttp` | POST | Admin | Broadcast push notification to users |
| `chatbotManageRoomHttp` | POST | Admin | Create or update a room for a session |

All endpoints authenticate via `Authorization: Bearer <Firebase ID Token>` and verify the caller's `thisApprovalStatus === 1` and `thisRole` membership.

---

## Automation and Smart Features

| Feature | How It Works |
|---------|-------------|
| **Proactive Daily Briefing** | On chatbot open, queries Firestore for today's data and shows a role-specific summary (e.g. "You have 2 exams today at 10:00 and 14:00") |
| **Natural Language Dates** | Resolves "today", "tomorrow", "next Monday", "this Friday", "in 3 days" to YYYY-MM-DD before sending to Gemini or executing actions |
| **Smart Scheduling** | System prompt instructs Gemini to suggest alternative rooms/times when requested slots are full |
| **Conflict Detection** | Server-side booking Cloud Function checks for overlapping student bookings before creating |
| **Booking Pre-Check** | Client-side validation checks room availability, fuzzy-matches room names (Levenshtein distance), and verifies faculty email before confirming |
| **Seat Restoration on Cancel** | `chatbotCancelBookingHttp` runs a Firestore transaction to restore `availableSeats` slots when cancelling |

---

## AI and UX Features

| Feature | How It Works |
|---------|-------------|
| **Conversation Persistence** | Messages saved to `ChatbotConversations/{uid}/messages` with timestamps; last 20 restored on app restart |
| **Context-Aware Help** | Current screen route is injected into the system prompt so DuckBot gives screen-specific guidance |
| **Voice Input** | Microphone button launches Android `RecognizerIntent` with permission handling; transcribed text fills the input field |
| **PDF Analysis** | PDFBox extracts text from uploaded PDFs; content is sent to Gemini with analysis instructions |
| **Multi-Language** | `setLanguagePreference()` adds a language instruction to the system prompt |
| **Notification Summary** | Queries `UserNotifications/{uid}/notifications` and shows unread count + recent titles |
| **Markdown Rendering** | Bot messages support basic markdown (bold, links) via `parseMarkdownToAnnotatedString()` |
| **Draggable Bubble** | The floating chat bubble can be repositioned via long-press drag |
| **Rate Limit Handling** | Auto-retry with exponential backoff (15s -> 30s -> 60s) on Gemini API rate limits |
| **Minimum Request Interval** | 4-second cooldown between API calls to avoid hitting free-tier quotas |
| **Typing Indicator** | Animated bouncing dots in a bot-styled bubble while waiting for Gemini response |
| **Rich Message Cards** | Action results render as Material 3 cards (bookings, rooms, deferrals, checklists, stats, notifications) with status chips and progress bars |
| **Contextual Quick Replies** | After each action result, role-aware follow-up suggestion chips appear below the last bot message |
| **Chat Search** | Search icon in top bar opens a search overlay with debounced text matching and highlighted results |
| **Chat History Pagination** | "Load earlier messages" button paginates backward through Firestore conversation history |
| **Multi-Turn Wizards** | Complex actions (book_session, assign_proctor, etc.) offer step-by-step guided pickers: date chips, room cards, time slots |

---

## Deferral FAQ (Built Into System Prompt)

These are answered instantly without an API call:

1. **How do I defer?** - Step-by-step instructions; deferrals are handled outside the app
2. **How long for approval?** - Typically 3-5 business days
3. **Professor's deferral policy?** - Upload course outline PDF for analysis
4. **Would my case be deferred?** - Lists common accepted reasons; offers PDF analysis
5. **Where are my past deferrals?** - Use `get_my_deferrals` or navigate to My Deferrals

---

## Architecture Summary

```
User (Android App)
  |
  +-- ChatbotBubble.kt (UI: chat panel, voice input, PDF upload)
  |
  +-- ChatbotViewModel.kt (Gemini orchestrator, system prompt, action routing)
  |     |
  |     +-- ChatbotActionRegistry.kt (declarative action definitions)
  |     |
  |     +-- ChatbotActionExecutor.kt (Firestore reads + secure service calls)
  |           |
  |           +-- Direct Firestore queries (read-only actions)
  |           |
  |           +-- ChatbotSecureActionService.kt (authenticated HTTP calls)
  |                 |
  |                 +-- Firebase Cloud Functions (functions/index.js)
  |                       |
  |                       +-- Firestore writes (transactional)
  |                       +-- FCM notifications
  |                       +-- Email via SMTP
  |
  +-- Gemini API (generativelanguage.googleapis.com)
```

**Total Actions:** 28 registered actions across 4 roles
**Total Cloud Functions:** 17 chatbot HTTP endpoints
**Total Navigable Routes:** 14 in-app screens
