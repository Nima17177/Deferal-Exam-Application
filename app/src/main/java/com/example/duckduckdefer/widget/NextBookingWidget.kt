package com.example.duckduckdefer.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import com.example.duckduckdefer.MainActivity
import com.example.duckduckdefer.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Home-screen widget that shows the user's next upcoming booking or proctoring session
 * with a gradient style (light/dark), student, faculty, and proctor names.
 *
 * Update strategy:
 *  • System-triggered every 30 min (updatePeriodMillis in XML).
 *  • Manual refresh when user taps the widget.
 *  • Broadcast-triggered – call [requestWidgetUpdate] from anywhere in the app.
 */
class NextBookingWidget : AppWidgetProvider() {

    companion object {
        private const val TAG = "NextBookingWidget"
        const val ACTION_REFRESH = "com.example.duckduckdefer.WIDGET_REFRESH"

        /** Call this from anywhere in the app to force-refresh the widget. */
        fun requestWidgetUpdate(context: Context) {
            val intent = Intent(context, NextBookingWidget::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            }
            val ids = AppWidgetManager.getInstance(context)
                .getAppWidgetIds(ComponentName(context, NextBookingWidget::class.java))
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            context.sendBroadcast(intent)
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (widgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, widgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, NextBookingWidget::class.java))
            onUpdate(context, mgr, ids)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Core update logic
    // ─────────────────────────────────────────────────────────────────────────

    private fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        widgetId: Int
    ) {
        // Show loading state
        val views = RemoteViews(context.packageName, R.layout.widget_next_booking)
        setTapAction(context, views)
        views.setTextViewText(R.id.widget_exam_name, "Loading…")
        views.setTextViewText(R.id.widget_date, "")
        views.setTextViewText(R.id.widget_time, "")
        views.setTextViewText(R.id.widget_room, "")
        views.setViewVisibility(R.id.widget_empty, View.GONE)
        views.setViewVisibility(R.id.widget_people_section, View.GONE)
        appWidgetManager.updateAppWidget(widgetId, views)

        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            showEmpty(context, appWidgetManager, widgetId, "Sign in to see bookings")
            return
        }

        fetchUserRole(uid) { role ->
            when (role) {
                3 -> fetchStudentBookings(context, appWidgetManager, widgetId, uid)
                1 -> fetchFacultyBookings(context, appWidgetManager, widgetId, uid)
                2 -> fetchProctorAssignments(context, appWidgetManager, widgetId, uid)
                0 -> fetchAllBookings(context, appWidgetManager, widgetId)
                else -> showEmpty(context, appWidgetManager, widgetId, "Sign in to see bookings")
            }
        }
    }

    private fun setTapAction(context: Context, views: RemoteViews) {
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Role detection
    // ─────────────────────────────────────────────────────────────────────────

    private fun fetchUserRole(uid: String, onResult: (Int) -> Unit) {
        FirebaseFirestore.getInstance()
            .collection("UserAccounts").document(uid).get()
            .addOnSuccessListener { doc ->
                onResult(doc.getLong("thisRole")?.toInt() ?: -1)
            }
            .addOnFailureListener {
                Log.w(TAG, "Failed to fetch role", it)
                onResult(-1)
            }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Student bookings
    // ─────────────────────────────────────────────────────────────────────────

    private fun fetchStudentBookings(
        context: Context, mgr: AppWidgetManager, widgetId: Int, uid: String
    ) {
        val db = FirebaseFirestore.getInstance()
        db.collection("StudentBookings").document(uid).get()
            .addOnSuccessListener { doc ->
                @Suppress("UNCHECKED_CAST")
                val ids = doc.get("bookings") as? List<String> ?: emptyList()
                if (ids.isEmpty()) {
                    showEmpty(context, mgr, widgetId, "No upcoming bookings 🎉")
                    return@addOnSuccessListener
                }
                findNextBookingFromIds(context, mgr, widgetId, ids, "EXAM")
            }
            .addOnFailureListener { showEmpty(context, mgr, widgetId, "Could not load bookings") }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Faculty bookings
    // ─────────────────────────────────────────────────────────────────────────

    private fun fetchFacultyBookings(
        context: Context, mgr: AppWidgetManager, widgetId: Int, uid: String
    ) {
        val db = FirebaseFirestore.getInstance()
        db.collection("FacultyBookings").document(uid).get()
            .addOnSuccessListener { doc ->
                @Suppress("UNCHECKED_CAST")
                val ids = doc.get("bookings") as? List<String> ?: emptyList()
                if (ids.isEmpty()) {
                    showEmpty(context, mgr, widgetId, "No upcoming bookings 🎉")
                    return@addOnSuccessListener
                }
                findNextBookingFromIds(context, mgr, widgetId, ids, "BOOKING")
            }
            .addOnFailureListener { showEmpty(context, mgr, widgetId, "Could not load bookings") }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Proctor assignments
    // ─────────────────────────────────────────────────────────────────────────

    private fun fetchProctorAssignments(
        context: Context, mgr: AppWidgetManager, widgetId: Int, uid: String
    ) {
        val db = FirebaseFirestore.getInstance()
        val today = todayString()

        db.collection("SessionsCollection")
            .whereGreaterThanOrEqualTo("dateID", today)
            .orderBy("dateID")
            .limit(14)
            .get()
            .addOnSuccessListener { snapshot ->
                var bestDate: String? = null
                var bestRoom: String? = null
                var bestStart: String? = null
                var bestEnd: String? = null
                var bestBookingIds: List<String> = emptyList()

                for (doc in snapshot.documents) {
                    val dateID = doc.getString("dateID") ?: doc.id
                    @Suppress("UNCHECKED_CAST")
                    val rooms = doc.get("rooms") as? Map<String, Map<String, Any>> ?: continue
                    for ((roomName, roomData) in rooms) {
                        val proctors = roomData["assignedProctors"] as? List<*> ?: continue
                        if (!proctors.contains(uid)) continue

                        val start = roomData["startTime"] as? String ?: ""
                        val end = roomData["endTime"] as? String ?: ""

                        if (dateID == today && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            try {
                                val endTime = parseTimeSafe(end)
                                if (endTime != null && endTime.isBefore(LocalTime.now())) continue
                            } catch (_: Exception) {}
                        }

                        if (bestDate == null || dateID < bestDate ||
                            (dateID == bestDate && compareTimes(start, bestStart ?: "") < 0)
                        ) {
                            bestDate = dateID
                            bestRoom = roomName
                            bestStart = start
                            bestEnd = end
                            @Suppress("UNCHECKED_CAST")
                            bestBookingIds = (roomData["bookings"] as? List<String>) ?: emptyList()
                        }
                    }
                }

                if (bestDate != null) {
                    // For proctor view, try to get the first booking's student/faculty info
                    if (bestBookingIds.isNotEmpty()) {
                        fetchBookingPeople(bestBookingIds.first()) { student, faculty, proctor ->
                            showBookingWithPeople(
                                context, mgr, widgetId,
                                title = "Proctoring: ${bestRoom ?: ""}",
                                dateId = bestDate!!,
                                startTime = bestStart ?: "",
                                endTime = bestEnd ?: "",
                                room = bestRoom ?: "",
                                badgeText = "PROCTORING",
                                studentName = student,
                                facultyName = faculty,
                                proctorName = null // it's the proctor themselves
                            )
                        }
                    } else {
                        showBookingWithPeople(
                            context, mgr, widgetId,
                            title = "Proctoring: ${bestRoom ?: ""}",
                            dateId = bestDate!!,
                            startTime = bestStart ?: "",
                            endTime = bestEnd ?: "",
                            room = bestRoom ?: "",
                            badgeText = "PROCTORING",
                            studentName = null, facultyName = null, proctorName = null
                        )
                    }
                } else {
                    showEmpty(context, mgr, widgetId, "No upcoming proctoring 🎉")
                }
            }
            .addOnFailureListener { showEmpty(context, mgr, widgetId, "Could not load schedule") }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Admin – next booking system-wide
    // ─────────────────────────────────────────────────────────────────────────

    private fun fetchAllBookings(
        context: Context, mgr: AppWidgetManager, widgetId: Int
    ) {
        val db = FirebaseFirestore.getInstance()
        val today = todayString()

        db.collection("BookingsCollection")
            .whereGreaterThanOrEqualTo("sessionDateID", today)
            .orderBy("sessionDateID")
            .limit(20)
            .get()
            .addOnSuccessListener { snap ->
                val next = pickNextBooking(snap.toObjects(BookingDto::class.java))
                if (next != null) {
                    resolveAndShowBooking(context, mgr, widgetId, next, "UP NEXT")
                } else {
                    showEmpty(context, mgr, widgetId, "No upcoming bookings 🎉")
                }
            }
            .addOnFailureListener { showEmpty(context, mgr, widgetId, "Could not load bookings") }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Shared: resolve booking IDs → pick soonest → fetch people names
    // ─────────────────────────────────────────────────────────────────────────

    private fun findNextBookingFromIds(
        context: Context, mgr: AppWidgetManager, widgetId: Int,
        ids: List<String>, badgeLabel: String
    ) {
        val db = FirebaseFirestore.getInstance()
        val chunks = ids.chunked(30)
        val allBookings = mutableListOf<BookingDto>()
        var remaining = chunks.size

        for (chunk in chunks) {
            db.collection("BookingsCollection")
                .whereIn("bookingID", chunk)
                .get()
                .addOnSuccessListener { snap ->
                    allBookings.addAll(snap.toObjects(BookingDto::class.java))
                    remaining--
                    if (remaining == 0) {
                        val next = pickNextBooking(allBookings)
                        if (next != null) {
                            resolveAndShowBooking(context, mgr, widgetId, next, badgeLabel)
                        } else {
                            showEmpty(context, mgr, widgetId, "No upcoming bookings 🎉")
                        }
                    }
                }
                .addOnFailureListener {
                    remaining--
                    if (remaining == 0) showEmpty(context, mgr, widgetId, "Could not load bookings")
                }
        }
    }

    /**
     * Once we have the next booking, resolve student/faculty/proctor names
     * and then render the widget.
     */
    private fun resolveAndShowBooking(
        context: Context, mgr: AppWidgetManager, widgetId: Int,
        booking: BookingDto, badgeLabel: String
    ) {
        val db = FirebaseFirestore.getInstance()
        var studentName: String? = null
        var facultyName: String? = null
        var proctorName: String? = null
        var callsRemaining = 3

        fun tryFinish() {
            callsRemaining--
            if (callsRemaining <= 0) {
                showBookingWithPeople(
                    context, mgr, widgetId,
                    title = booking.examName,
                    dateId = booking.sessionDateID,
                    startTime = booking.startTime,
                    endTime = booking.endTime,
                    room = booking.roomName,
                    badgeText = badgeLabel,
                    studentName = studentName,
                    facultyName = facultyName,
                    proctorName = proctorName
                )
            }
        }

        // Fetch student name
        if (booking.studentUID.isNotEmpty()) {
            db.collection("UserAccounts").document(booking.studentUID).get()
                .addOnSuccessListener { doc ->
                    studentName = doc.getString("thisUserName")
                    tryFinish()
                }
                .addOnFailureListener { tryFinish() }
        } else tryFinish()

        // Fetch faculty name
        if (booking.facultyUID.isNotEmpty()) {
            db.collection("UserAccounts").document(booking.facultyUID).get()
                .addOnSuccessListener { doc ->
                    facultyName = doc.getString("thisUserName")
                    tryFinish()
                }
                .addOnFailureListener { tryFinish() }
        } else tryFinish()

        // Fetch proctor from session
        if (booking.sessionDateID.isNotEmpty() && booking.roomName.isNotEmpty()) {
            db.collection("SessionsCollection").document(booking.sessionDateID).get()
                .addOnSuccessListener { doc ->
                    @Suppress("UNCHECKED_CAST")
                    val rooms = doc.get("rooms") as? Map<String, Map<String, Any>>
                    val roomData = rooms?.get(booking.roomName)
                    @Suppress("UNCHECKED_CAST")
                    val proctorUids = roomData?.get("assignedProctors") as? List<String>
                    val firstProctor = proctorUids?.firstOrNull()
                    if (firstProctor != null) {
                        db.collection("UserAccounts").document(firstProctor).get()
                            .addOnSuccessListener { pDoc ->
                                proctorName = pDoc.getString("thisUserName")
                                if (proctorUids.size > 1) {
                                    proctorName = "$proctorName +${proctorUids.size - 1}"
                                }
                                tryFinish()
                            }
                            .addOnFailureListener { tryFinish() }
                    } else {
                        proctorName = "Not assigned"
                        tryFinish()
                    }
                }
                .addOnFailureListener { tryFinish() }
        } else tryFinish()
    }

    /**
     * For proctor view: fetch student & faculty from a booking document.
     */
    private fun fetchBookingPeople(
        bookingId: String,
        onResult: (studentName: String?, facultyName: String?, proctorName: String?) -> Unit
    ) {
        val db = FirebaseFirestore.getInstance()
        db.collection("BookingsCollection").document(bookingId).get()
            .addOnSuccessListener { doc ->
                val sUid = doc.getString("studentUID") ?: ""
                val fUid = doc.getString("facultyUID") ?: ""
                var sName: String? = null
                var fName: String? = null
                var done = 0
                fun check() { done++; if (done >= 2) onResult(sName, fName, null) }

                if (sUid.isNotEmpty()) {
                    db.collection("UserAccounts").document(sUid).get()
                        .addOnSuccessListener { sName = it.getString("thisUserName"); check() }
                        .addOnFailureListener { check() }
                } else check()

                if (fUid.isNotEmpty()) {
                    db.collection("UserAccounts").document(fUid).get()
                        .addOnSuccessListener { fName = it.getString("thisUserName"); check() }
                        .addOnFailureListener { check() }
                } else check()
            }
            .addOnFailureListener { onResult(null, null, null) }
    }

    private fun pickNextBooking(bookings: List<BookingDto>): BookingDto? {
        val today = todayString()
        val now: LocalTime? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) LocalTime.now() else null

        return bookings
            .filter { b ->
                b.status in 1..3 &&
                b.sessionDateID.isNotEmpty() &&
                (b.sessionDateID > today ||
                    (b.sessionDateID == today && (now == null || run {
                        val end = parseTimeSafe(b.endTime)
                        end == null || !end.isBefore(now)
                    })))
            }
            .sortedWith(compareBy<BookingDto> { it.sessionDateID }.thenBy { it.startTime })
            .firstOrNull()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // UI rendering
    // ─────────────────────────────────────────────────────────────────────────

    private fun showBookingWithPeople(
        context: Context, mgr: AppWidgetManager, widgetId: Int,
        title: String, dateId: String, startTime: String, endTime: String,
        room: String, badgeText: String,
        studentName: String?, facultyName: String?, proctorName: String?
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_next_booking)
        setTapAction(context, views)

        // Booking info
        views.setViewVisibility(R.id.widget_empty, View.GONE)
        views.setViewVisibility(R.id.widget_exam_name, View.VISIBLE)
        views.setViewVisibility(R.id.widget_details_row, View.VISIBLE)
        views.setViewVisibility(R.id.widget_room, View.VISIBLE)

        views.setTextViewText(R.id.widget_status_badge, badgeText)
        views.setTextViewText(R.id.widget_exam_name, title.ifEmpty { "Untitled Exam" })
        views.setTextViewText(R.id.widget_date, "📅 ${formatDate(dateId)}")
        views.setTextViewText(R.id.widget_time, "🕐 $startTime – $endTime")
        views.setTextViewText(R.id.widget_room, "📍 $room")

        // People section
        val hasPeople = studentName != null || facultyName != null || proctorName != null
        views.setViewVisibility(R.id.widget_people_section, if (hasPeople) View.VISIBLE else View.GONE)

        if (studentName != null) {
            views.setViewVisibility(R.id.widget_student_row, View.VISIBLE)
            views.setTextViewText(R.id.widget_student_name, studentName)
        } else {
            views.setViewVisibility(R.id.widget_student_row, View.GONE)
        }

        if (facultyName != null) {
            views.setViewVisibility(R.id.widget_faculty_row, View.VISIBLE)
            views.setTextViewText(R.id.widget_faculty_name, facultyName)
        } else {
            views.setViewVisibility(R.id.widget_faculty_row, View.GONE)
        }

        if (proctorName != null) {
            views.setViewVisibility(R.id.widget_proctor_row, View.VISIBLE)
            views.setTextViewText(R.id.widget_proctor_name, proctorName)
        } else {
            views.setViewVisibility(R.id.widget_proctor_row, View.GONE)
        }

        mgr.updateAppWidget(widgetId, views)
    }

    private fun showEmpty(
        context: Context, mgr: AppWidgetManager, widgetId: Int, message: String
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_next_booking)
        setTapAction(context, views)

        views.setViewVisibility(R.id.widget_exam_name, View.GONE)
        views.setViewVisibility(R.id.widget_details_row, View.GONE)
        views.setViewVisibility(R.id.widget_room, View.GONE)
        views.setViewVisibility(R.id.widget_people_section, View.GONE)
        views.setViewVisibility(R.id.widget_empty, View.VISIBLE)

        views.setTextViewText(R.id.widget_empty, message)
        views.setTextViewText(R.id.widget_status_badge, "WIDGET")

        mgr.updateAppWidget(widgetId, views)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Date / time utilities
    // ─────────────────────────────────────────────────────────────────────────

    private fun todayString(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        else
            java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(java.util.Date())

    private fun formatDate(dateId: String): String {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val date = LocalDate.parse(dateId, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                val today = LocalDate.now()
                when {
                    date == today -> "Today"
                    date == today.plusDays(1) -> "Tomorrow"
                    else -> {
                        val dow = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                        val month = date.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                        "$dow, $month ${date.dayOfMonth}"
                    }
                }
            } else dateId
        } catch (_: Exception) { dateId }
    }

    private fun parseTimeSafe(time: String): LocalTime? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
        val formats = listOf("HH:mm", "h:mm a", "H:mm", "h:mma")
        for (fmt in formats) {
            try { return LocalTime.parse(time, DateTimeFormatter.ofPattern(fmt)) }
            catch (_: Exception) {}
        }
        return try { LocalTime.parse(time) } catch (_: Exception) { null }
    }

    private fun compareTimes(a: String, b: String): Int {
        val ta = parseTimeSafe(a)
        val tb = parseTimeSafe(b)
        if (ta == null || tb == null) return a.compareTo(b)
        return ta.compareTo(tb)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Lightweight DTO (includes UIDs for people lookup)
    // ─────────────────────────────────────────────────────────────────────────

    data class BookingDto(
        val bookingID: String = "",
        val examName: String = "",
        val roomName: String = "",
        val startTime: String = "",
        val endTime: String = "",
        val sessionDateID: String = "",
        val studentUID: String = "",
        val facultyUID: String = "",
        val proctorUID: String = "",
        val status: Int = 1
    )
}
