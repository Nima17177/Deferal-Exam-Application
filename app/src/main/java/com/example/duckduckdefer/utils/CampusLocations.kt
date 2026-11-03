package com.example.duckduckdefer.utils

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * MacEwan University – City Centre Campus building locations.
 *
 * Room naming convention:  a-bcd
 *   a   = building number (5 through 11)
 *   b   = floor number
 *   cd  = room number on that floor
 *
 * Examples:
 *   "5-225"  → Building 5, Floor 2, Room 25
 *   "7-110"  → Building 7, Floor 1, Room 10
 *   "11-361" → Building 11 (Allard Hall), Floor 3, Room 61
 */
object CampusLocations {

    data class BuildingInfo(
        val buildingName: String,
        val mapsQuery: String,      // search query Google Maps can resolve
        val address: String
    )

    data class RoomLocation(
        val building: BuildingInfo,
        val floor: Int?,
        val roomNumber: String?
    ) {
        /** Human-readable label, e.g. "Building 7 · Floor 2 · Room 25" */
        fun displayLabel(): String {
            val parts = mutableListOf(building.buildingName)
            if (floor != null) parts.add("Floor $floor")
            if (!roomNumber.isNullOrBlank()) parts.add("Room $roomNumber")
            return parts.joinToString(" · ")
        }
    }

    // ── MacEwan City Centre Campus buildings ────────────────────────────
    // Uses human-readable search queries so Google Maps finds the exact
    // building entrance — far more reliable than lat/lng coordinates.

    private val buildings = mapOf(
        5  to BuildingInfo(
            buildingName = "Building 5 – Urban Beeyard",
            mapsQuery = "MacEwan University Urban Beeyard, Edmonton, AB",
            address = "10700 104 Ave NW, Edmonton, AB"
        ),
        6  to BuildingInfo(
            buildingName = "Building 6",
            mapsQuery = "MacEwan University Building 6, Edmonton, AB",
            address = "10700 104 Ave NW, Edmonton, AB"
        ),
        7  to BuildingInfo(
            buildingName = "Building 7",
            mapsQuery = "MacEwan University, 10700 104 Ave NW, Edmonton, AB",
            address = "10700 104 Ave NW, Edmonton, AB"
        ),
        8  to BuildingInfo(
            buildingName = "Building 8 – Centre for Sports and Wellness",
            mapsQuery = "MacEwan University Centre for Sports and Wellness, Edmonton, AB",
            address = "10700 104 Ave NW, Edmonton, AB"
        ),
        9  to BuildingInfo(
            buildingName = "Building 9 – Robbins Health Learning Centre",
            mapsQuery = "Robbins Health Learning Centre MacEwan University, Edmonton, AB",
            address = "10700 104 Ave NW, Edmonton, AB"
        ),
        10 to BuildingInfo(
            buildingName = "Building 10",
            mapsQuery = "MacEwan University Building 10, Edmonton, AB",
            address = "10700 104 Ave NW, Edmonton, AB"
        ),
        11 to BuildingInfo(
            buildingName = "Building 11 – Allard Hall",
            mapsQuery = "Allard Hall MacEwan University, Edmonton, AB",
            address = "10050 MacDonald Dr NW, Edmonton, AB"
        )
    )

    /** Fallback – main campus. */
    private val defaultBuilding = BuildingInfo(
        buildingName = "MacEwan University",
        mapsQuery = "MacEwan University, 10700 104 Ave NW, Edmonton, AB",
        address = "10700 104 Ave NW, Edmonton, AB"
    )

    // ── Parsing ─────────────────────────────────────────────────────────

    /**
     * Parse a MacEwan room name like "7-225" into its components.
     *
     * Returns a [RoomLocation] with building info, floor, and room number.
     * If the format doesn't match, the building is resolved as best as
     * possible and floor / room will be null.
     */
    fun parseRoom(roomName: String): RoomLocation {
        val trimmed = roomName.trim()

        // Expected format: "a-bcd"  (building-floorRoom)
        if ("-" in trimmed) {
            val parts = trimmed.split("-", limit = 2)
            val buildingNum = parts[0].toIntOrNull()
            val rest = parts.getOrNull(1) ?: ""

            val building = if (buildingNum != null) {
                buildings[buildingNum] ?: defaultBuilding
            } else defaultBuilding

            val floor = rest.firstOrNull()?.digitToIntOrNull()
            val room = if (rest.length > 1) rest.substring(1) else null

            return RoomLocation(building, floor, room)
        }

        // No dash – try leading digits as building number
        val leadingDigits = trimmed.takeWhile { it.isDigit() }
        val buildingNum = leadingDigits.toIntOrNull()
        val building = if (buildingNum != null) {
            buildings[buildingNum] ?: defaultBuilding
        } else defaultBuilding

        return RoomLocation(building, floor = null, roomNumber = null)
    }

    /** Shortcut that just returns the [BuildingInfo]. */
    fun getBuildingForRoom(roomName: String): BuildingInfo = parseRoom(roomName).building

    // ── Directions ──────────────────────────────────────────────────────

    /**
     * Open Google Maps (or browser fallback) with walking directions to
     * the building that contains [roomName].
     */
    fun openDirectionsToRoom(context: Context, roomName: String) {
        val building = getBuildingForRoom(roomName)
        openDirections(context, building)
    }

    /**
     * Open Google Maps with walking directions to a [BuildingInfo].
     * Uses a search query so Google Maps finds the real building pin.
     */
    fun openDirections(context: Context, building: BuildingInfo) {
        val encoded = Uri.encode(building.mapsQuery)

        // google.navigation with a query string – Maps resolves the name
        val navUri = Uri.parse("google.navigation:q=$encoded&mode=w")
        val mapIntent = Intent(Intent.ACTION_VIEW, navUri).apply {
            setPackage("com.google.android.apps.maps")
        }

        if (mapIntent.resolveActivity(context.packageManager) != null) {
            context.startActivity(mapIntent)
        } else {
            // Fallback: open directions in browser
            val webUri = Uri.parse(
                "https://www.google.com/maps/dir/?api=1" +
                        "&destination=${encoded}" +
                        "&travelmode=walking"
            )
            context.startActivity(Intent(Intent.ACTION_VIEW, webUri))
        }
    }
}


