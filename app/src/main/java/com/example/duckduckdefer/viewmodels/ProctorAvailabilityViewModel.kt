package com.example.duckduckdefer.viewmodels

import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import com.example.duckduckdefer.components.DayStats
import com.example.duckduckdefer.useraccountclasses.FacultySubclass
import com.example.duckduckdefer.useraccountclasses.SessionsClass
import com.google.firebase.firestore.FirebaseFirestore
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

data class AvailabilityRange(
    val start: String,
    val end: String,
    val isAssigned: Boolean
)

@RequiresApi(Build.VERSION_CODES.O)
class ProctorAvailabilityViewModel : ViewModel() {
    var selectedDate by mutableStateOf(LocalDate.now())
    var currentPivotDate by mutableStateOf(LocalDate.now().with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)))
    var isMonthView by mutableStateOf(false)
    
    val calendarDayStats = mutableStateMapOf<LocalDate, DayStats>()
    var refreshTrigger by mutableIntStateOf(0)
    
    var currentAvailabilityForSelectedDate by mutableStateOf<List<Pair<String, String>>>(emptyList())
    var currentAssignedSlots by mutableStateOf<Set<String>>(emptySet())
    var isLoadingDetails by mutableStateOf(false)

    // minimum date for booking availability is today (present day)
    val minDate: LocalDate = LocalDate.now()
    private val outFmt = DateTimeFormatter.ofPattern("h:mm a")

    val availabilityRanges: List<AvailabilityRange> by derivedStateOf {
        currentAvailabilityForSelectedDate.map { range ->
            AvailabilityRange(range.first, range.second, isRangeAssigned(range))
        }
    }

    private fun isRangeAssigned(range: Pair<String, String>): Boolean {
        val rs = LocalTime.parse(range.first)
        val re = LocalTime.parse(range.second)
        return currentAssignedSlots.any { slot ->
            val parts = slot.split(" - ")
            if (parts.size == 2) {
                val ss = LocalTime.parse(parts[0])
                val se = LocalTime.parse(parts[1])
                (ss.isAfter(rs) || ss.equals(rs)) && (se.isBefore(re) || se.equals(re))
            } else false
        }
    }

    val totalHours: Double by derivedStateOf {
        currentAvailabilityForSelectedDate.sumOf { (start, end) ->
            try {
                val s = LocalTime.parse(start)
                val e = LocalTime.parse(end)
                Duration.between(s, e).toMinutes().toDouble() / 60.0
            } catch (ex: Exception) { 0.0 }
        }
    }

    fun refreshStats(facultyUser: FacultySubclass?) {
        if (facultyUser == null) return
        
        FirebaseFirestore.getInstance().collection("ProctorAvailability")
            .document(facultyUser.thisUID)
            .get()
            .addOnSuccessListener { document ->
                calendarDayStats.clear()
                val availabilityList = (document.get("availability") as? List<Map<String, String>>) ?: emptyList()
                
                FirebaseFirestore.getInstance().collection("SessionsCollection").get()
                    .addOnSuccessListener { sessionSnapshot ->
                        val assignedDates = mutableSetOf<String>()
                        sessionSnapshot.documents.forEach { doc ->
                            val session = doc.toObject(SessionsClass::class.java)
                            if (session != null) {
                                val isAssigned = session.rooms.values.any { it.assignedProctors.contains(facultyUser.thisUID) }
                                if (isAssigned) assignedDates.add(session.dateID)
                            }
                        }

                        val datesWithAvailability = availabilityList.mapNotNull { it["date"] }.toSet()
                        datesWithAvailability.forEach { dateId ->
                            try {
                                val dateKey = LocalDate.parse(dateId)
                                val stats = DayStats(date = dateKey)
                                if (assignedDates.contains(dateId)) stats.proctored = 1 else stats.available = 1
                                calendarDayStats[dateKey] = stats
                            } catch (_: Exception) {}
                        }
                    }
                    .addOnFailureListener { e ->
                        android.util.Log.e("ProctorAvailabilityVM", "Failed to load sessions: ${e.message}")
                    }
            }
            .addOnFailureListener { e ->
                android.util.Log.e("ProctorAvailabilityVM", "Failed to load availability: ${e.message}")
            }
    }

    fun loadDetails(facultyUser: FacultySubclass?) {
        if (facultyUser == null) return
        isLoadingDetails = true
        val dateStr = selectedDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
        
        facultyUser.getAvailability(dateStr) { ranges ->
            currentAvailabilityForSelectedDate = ranges.distinct().sortedBy { it.first }
            
            FirebaseFirestore.getInstance().collection("SessionsCollection").document(dateStr).get()
                .addOnSuccessListener { doc ->
                    val session = doc.toObject(SessionsClass::class.java)
                    val assigned = mutableSetOf<String>()
                    session?.rooms?.values?.forEach { room ->
                        if (room.assignedProctors.contains(facultyUser.thisUID)) {
                            val s = LocalTime.parse(room.startTime)
                            val e = LocalTime.parse(room.endTime)
                            var curr = s
                            while (curr.isBefore(e)) {
                                val next = curr.plusMinutes(30)
                                assigned.add("${curr.format(DateTimeFormatter.ofPattern("HH:mm"))} - ${next.format(DateTimeFormatter.ofPattern("HH:mm"))}")
                                curr = next
                            }
                        }
                    }
                    currentAssignedSlots = assigned
                    isLoadingDetails = false
                }
                .addOnFailureListener { isLoadingDetails = false }
        }
    }

    fun deleteRange(facultyUser: FacultySubclass?, date: LocalDate, range: Pair<String, String>) {
        if (isRangeAssigned(range)) {
            return
        }
        val updated = currentAvailabilityForSelectedDate.filter { it != range }
        facultyUser?.setAvailability(date.format(DateTimeFormatter.ISO_LOCAL_DATE), updated) { if (it) refreshTrigger++ }
    }

    fun updateRange(facultyUser: FacultySubclass?, date: LocalDate, old: Pair<String, String>, ns: String, ne: String, ctx: Context) {
        if (isRangeAssigned(old)) {
            Toast.makeText(ctx, "Cannot edit an assigned shift.", Toast.LENGTH_SHORT).show()
            return
        }
        
        val start = try { LocalTime.parse(ns) } catch (e: Exception) { null }
        val end = try { LocalTime.parse(ne) } catch (e: Exception) { null }
        
        if (start == null || end == null || !start.isBefore(end)) {
            Toast.makeText(ctx, "Invalid time range.", Toast.LENGTH_SHORT).show()
            return
        }
        
        val filtered = currentAvailabilityForSelectedDate.filter { it != old }
        
        val overlappingRange = filtered.find { r -> 
            val rs = LocalTime.parse(r.first)
            val re = LocalTime.parse(r.second)
            start.isBefore(re) && rs.isBefore(end) 
        }
        
        if (overlappingRange != null) {
            val overlapText = "${LocalTime.parse(overlappingRange.first).format(outFmt)} - ${LocalTime.parse(overlappingRange.second).format(outFmt)}"
            Toast.makeText(ctx, "Overlap error: Conflict with $overlapText", Toast.LENGTH_SHORT).show()
            return 
        }
        
        val updated = (filtered + (ns to ne)).sortedBy { it.first }
        facultyUser?.setAvailability(date.format(DateTimeFormatter.ISO_LOCAL_DATE), updated) { if (it) refreshTrigger++ }
    }

    fun saveAvailability(facultyUser: FacultySubclass?, dates: Set<LocalDate>, s: String, e: String, ctx: Context, weeks: Int, onComplete: () -> Unit) {
        val start = try { LocalTime.parse(s) } catch (e: Exception) { null }
        val end = try { LocalTime.parse(e) } catch (e: Exception) { null }
        
        if (start == null || end == null || !start.isBefore(end)) {
            Toast.makeText(ctx, "Invalid time range selected.", Toast.LENGTH_SHORT).show()
            return
        }
        
        val allDatesToProcess = mutableSetOf<LocalDate>()
        dates.forEach { baseDate -> 
            for (i in 0 until weeks) { 
                allDatesToProcess.add(baseDate.plusWeeks(i.toLong())) 
            } 
        }
        
        var remaining = allDatesToProcess.size
        var overlapCount = 0
        val uniqueOverlaps = mutableSetOf<String>()

        if (remaining == 0) return
        
        allDatesToProcess.forEach { date ->
            val dID = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
            facultyUser?.getAvailability(dID) { existing ->
                val overlappingRange = existing.find { r -> 
                    val rs = LocalTime.parse(r.first)
                    val re = LocalTime.parse(r.second)
                    start.isBefore(re) && rs.isBefore(end)
                }

                if (overlappingRange != null) {
                    val overlapText = "${LocalTime.parse(overlappingRange.first).format(outFmt)} - ${LocalTime.parse(overlappingRange.second).format(outFmt)}"
                    uniqueOverlaps.add(overlapText)
                    overlapCount++
                    remaining--
                    if (remaining == 0) { 
                        if (overlapCount > 0) {
                            val msg = if (uniqueOverlaps.size == 1) "Overlap: Conflict with ${uniqueOverlaps.first()}" else "Overlap: Multiple conflicts found"
                            Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
                        }
                        refreshTrigger++
                        onComplete() 
                    }
                } else {
                    val combined = (existing + (s to e)).distinct().sortedBy { it.first }
                    facultyUser.setAvailability(dID, combined) {
                        remaining--
                        if (remaining == 0) { 
                            if (overlapCount > 0) {
                                val msg = if (uniqueOverlaps.size == 1) "Overlap: Conflict with ${uniqueOverlaps.first()}" else "Overlap: Multiple conflicts found"
                                Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(ctx, "Availability saved successfully.", Toast.LENGTH_SHORT).show()
                            }
                            refreshTrigger++
                            onComplete() 
                        }
                    }
                }
            }
        }
    }

    fun clearDay(facultyUser: FacultySubclass?, ctx: Context) {
        val dateStr = selectedDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
        FirebaseFirestore.getInstance().collection("SessionsCollection").document(dateStr).get()
            .addOnSuccessListener { doc ->
                val session = doc.toObject(SessionsClass::class.java)
                var hasAssigned = false
                session?.rooms?.values?.forEach { room ->
                    if (room.assignedProctors.contains(facultyUser?.thisUID)) {
                        hasAssigned = true
                    }
                }
                
                if (hasAssigned) {
                    Toast.makeText(ctx, "This day has booked shifts. Please contact admin to clear.", Toast.LENGTH_LONG).show()
                    return@addOnSuccessListener
                }

                facultyUser?.setAvailability(dateStr, emptyList()) {
                    refreshTrigger++
                    Toast.makeText(ctx, "All availability cleared for this day.", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(ctx, "Failed to check session: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
