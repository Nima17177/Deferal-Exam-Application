package com.example.duckduckdefer.useraccountclasses

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FieldPath
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class ProctorAssignment {

    private val db = FirebaseFirestore.getInstance()
    private val TAG = "ProctorAssignment"

    private fun parseTime(time: String): LocalTime {
        val formats = listOf("HH:mm", "H:mm", "h:mm a", "HH:mm:ss")
        for (format in formats) {
            try {
                return LocalTime.parse(time, DateTimeFormatter.ofPattern(format))
            } catch (e: Exception) {}
        }
        return try { LocalTime.parse(time) } catch (e: Exception) { 
            Log.e(TAG, "Failed to parse time: $time")
            LocalTime.MIDNIGHT 
        }
    }

    data class Assignment(
        val date: String,
        val start: String,
        val end: String,
        val room: String
    )

    data class ProctorData(
        val uid: String,
        val availability: List<Pair<String, String>>,
        val assignments: MutableList<Assignment> = mutableListOf(),
        val newAssignments: MutableList<Assignment> = mutableListOf()
    )

    private fun overlaps(aStart: LocalTime, aEnd: LocalTime, bStart: LocalTime, bEnd: LocalTime): Boolean {
        return aStart.isBefore(bEnd) && bStart.isBefore(aEnd)
    }

    private fun hasConflict(proctor: ProctorData, date: String, start: LocalTime, end: LocalTime): Boolean {
        return proctor.assignments.any { a ->
            if (a.date != date) return@any false
            val aStart = parseTime(a.start)
            val aEnd = parseTime(a.end)
            overlaps(start, end, aStart, aEnd)
        }
    }

    private fun findSegments(
        proctors: List<ProctorData>,
        date: String,
        start: LocalTime,
        end: LocalTime
    ): List<Triple<ProctorData, LocalTime, LocalTime>> {
        val segments = mutableListOf<Triple<ProctorData, LocalTime, LocalTime>>()

        for (p in proctors) {
            for (slot in p.availability) {
                val availStart = parseTime(slot.first)
                val availEnd = parseTime(slot.second)

                // If proctor is available during the needed window
                if (overlaps(start, end, availStart, availEnd)) {
                    val actualStart = if (availStart.isAfter(start)) availStart else start
                    val actualEnd = if (availEnd.isBefore(end)) availEnd else end

                    if (actualStart.isBefore(actualEnd) && !hasConflict(p, date, actualStart, actualEnd)) {
                        segments.add(Triple(p, actualStart, actualEnd))
                    }
                }
            }
        }
        return segments.sortedBy { it.second } // Sort by start time
    }

    private fun assignRoom(
        proctors: List<ProctorData>,
        date: String,
        roomName: String,
        roomStart: LocalTime,
        roomEnd: LocalTime
    ): Boolean {
        var currentTime = roomStart

        while (currentTime.isBefore(roomEnd)) {
            val segments = findSegments(proctors, date, currentTime, roomEnd)
            
            // Look for the proctor who can start now (or earliest) and covers the most time
            val bestSegment = segments
                .filter { !it.second.isAfter(currentTime) }
                .maxByOrNull { it.third } // Greedy: pick the one that ends the latest

            if (bestSegment == null) {
                Log.d(TAG, "Gap found for $roomName at $currentTime")
                return false // Gap in coverage found
            }

            val p = bestSegment.first
            val assignEnd = if (bestSegment.third.isAfter(roomEnd)) roomEnd else bestSegment.third

            val assignment = Assignment(
                date = date,
                start = currentTime.format(DateTimeFormatter.ofPattern("HH:mm")),
                end = assignEnd.format(DateTimeFormatter.ofPattern("HH:mm")),
                room = roomName
            )
            
            p.assignments.add(assignment)
            p.newAssignments.add(assignment)
            
            currentTime = assignEnd
        }
        return true
    }

    fun runAutoAssignment(dateID: String, onComplete: (Boolean) -> Unit) {
        // 1. Fetch only Approved Faculty/Proctors
        db.collection("UserAccounts")
            .whereIn("thisRole", listOf(1, 2))
            .whereEqualTo("thisApprovalStatus", 1)
            .get()
            .addOnSuccessListener { userSnapshots ->
                val approvedUIDs = userSnapshots.documents.map { it.id }.toSet()
                
                // 2. Fetch Availability
                db.collection("ProctorAvailability").get().addOnSuccessListener { proctorSnapshots ->
                    val proctors = mutableListOf<ProctorData>()
                    for (doc in proctorSnapshots.documents) {
                        if (!approvedUIDs.contains(doc.id)) continue
                        
                        val availabilityRaw = doc.get("availability") as? List<*> ?: emptyList<Any>()
                        val dayRanges = availabilityRaw.mapNotNull { item ->
                            val map = item as? Map<*, *> ?: return@mapNotNull null
                            if (map["date"] == dateID) {
                                val s = map["startTime"] as? String
                                val e = map["endTime"] as? String
                                if (s != null && e != null) s to e else null
                            } else null
                        }

                        val existingAssignmentsRaw = doc.get("assignments") as? List<*> ?: emptyList<Any>()
                        val assignments = existingAssignmentsRaw.mapNotNull { item ->
                            val map = item as? Map<*, *> ?: return@mapNotNull null
                            Assignment(
                                date = map["date"] as? String ?: "",
                                start = map["startTime"] as? String ?: "",
                                end = map["endTime"] as? String ?: "",
                                room = map["roomName"] as? String ?: ""
                            )
                        }.toMutableList()

                        if (dayRanges.isNotEmpty()) {
                            proctors.add(ProctorData(uid = doc.id, availability = dayRanges, assignments = assignments))
                        }
                    }

                    // 3. Fetch Session & Assign
                    db.collection("SessionsCollection").document(dateID).get().addOnSuccessListener { sessionDoc ->
                        val session = sessionDoc.toObject(SessionsClass::class.java)
                        if (session == null || session.rooms.isEmpty()) {
                            Log.d(TAG, "No session or rooms found for $dateID")
                            onComplete(false)
                            return@addOnSuccessListener
                        }

                        val roomsToAssign = session.rooms.values
                            .filter { it.assignedProctors.isEmpty() }
                            .sortedBy { parseTime(it.startTime) }

                        if (roomsToAssign.isEmpty()) {
                            onComplete(true)
                            return@addOnSuccessListener
                        }

                        var allRoomsCovered = true
                        for (room in roomsToAssign) {
                            if (!assignRoom(proctors, dateID, room.roomName, parseTime(room.startTime), parseTime(room.endTime))) {
                                allRoomsCovered = false
                                break 
                            }
                        }

                        if (allRoomsCovered) {
                            saveAssignments(dateID, proctors, onComplete)
                        } else {
                            onComplete(false)
                        }
                    }.addOnFailureListener { onComplete(false) }
                }.addOnFailureListener { onComplete(false) }
            }.addOnFailureListener { onComplete(false) }
    }

    private fun saveAssignments(
        dateID: String,
        proctors: List<ProctorData>,
        onComplete: (Boolean) -> Unit
    ) {
        val batch = db.batch()
        val sessionRef = db.collection("SessionsCollection").document(dateID)

        // Track UIDs per room to update Session document
        val roomToProctorsMap = mutableMapOf<String, MutableSet<String>>()
        var hasChanges = false

        for (p in proctors) {
            if (p.newAssignments.isNotEmpty()) {
                hasChanges = true
                val proctorRef = db.collection("ProctorAvailability").document(p.uid)
                
                val assignmentMaps = p.newAssignments.map { a ->
                    roomToProctorsMap.getOrPut(a.room) { mutableSetOf() }.add(p.uid)
                    mapOf(
                        "date" to a.date,
                        "startTime" to a.start,
                        "endTime" to a.end,
                        "roomName" to a.room
                    )
                }
                batch.update(proctorRef, "assignments", FieldValue.arrayUnion(*assignmentMaps.toTypedArray()))
            }
        }

        if (!hasChanges) {
            onComplete(true)
            return
        }

        // Update the Session rooms with the new proctor UIDs
        roomToProctorsMap.forEach { (roomName, proctorIds) ->
            // Use FieldPath.of to safely update nested fields even if roomName contains dots or special characters
            val fieldPath = FieldPath.of("rooms", roomName, "assignedProctors")
            batch.update(sessionRef, fieldPath, FieldValue.arrayUnion(*proctorIds.toTypedArray()))
        }

        batch.commit()
            .addOnSuccessListener { onComplete(true) }
            .addOnFailureListener { e -> 
                Log.e(TAG, "Batch failed: ${e.message}")
                onComplete(false) 
            }
    }
}
