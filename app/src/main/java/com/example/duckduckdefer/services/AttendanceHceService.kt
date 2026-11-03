package com.example.duckduckdefer.services

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import android.util.Log

/**
 * Host-based Card Emulation (HCE) service that makes the student's phone
 * act as a contactless NFC endpoint for attendance check-in.
 *
 * Uses a **custom proprietary AID** (F044554B415454) so it does NOT conflict
 * with Google Pay, contactless payment apps, or the system NDEF handler.
 *
 * Protocol (one round-trip):
 *   1. Proctor phone sends SELECT with our custom AID
 *   2. Student phone responds with the attendance payload bytes + 9000 (OK)
 *   3. Done!
 *
 * Usage:
 *   AttendanceHceService.setPayload("attendance:bookingId:uid")  // student starts
 *   AttendanceHceService.clearPayload()                           // student stops
 */
class AttendanceHceService : HostApduService() {

    companion object {
        private const val TAG = "AttendanceHCE"

        /**
         * Custom proprietary AID: F044554B415454
         * F0 = proprietary range (won't conflict with payment/NDEF)
         * 44554B415454 = "DUKATT" in ASCII hex
         */
        const val AID_HEX = "F044554B415454"
        val AID_BYTES: ByteArray = hexToBytes(AID_HEX)

        // ISO 7816 status words
        val SW_OK = byteArrayOf(0x90.toByte(), 0x00)
        val SW_NOT_FOUND = byteArrayOf(0x6A.toByte(), 0x82.toByte())
        val SW_CONDITIONS_NOT_SATISFIED = byteArrayOf(0x69.toByte(), 0x85.toByte())

        /** The current attendance payload. Null = inactive / not broadcasting.
         *  Also persisted via SharedPreferences so it survives process recreation. */
        @Volatile
        private var currentPayload: String? = null

        private const val PREFS_NAME = "hce_attendance_prefs"
        private const val KEY_PAYLOAD = "current_payload"

        fun setPayload(payload: String) {
            currentPayload = payload
            Log.d(TAG, "HCE payload set: $payload")
        }

        /**
         * Persist the payload to SharedPreferences so it survives the OS
         * killing and recreating this service. Call from an Activity/Context.
         */
        fun setPayload(payload: String, context: android.content.Context) {
            currentPayload = payload
            context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
                .edit().putString(KEY_PAYLOAD, payload).apply()
            Log.d(TAG, "HCE payload set & persisted: $payload")
        }

        fun clearPayload() {
            currentPayload = null
            Log.d(TAG, "HCE payload cleared")
        }

        fun clearPayload(context: android.content.Context) {
            currentPayload = null
            context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
                .edit().remove(KEY_PAYLOAD).apply()
            Log.d(TAG, "HCE payload cleared & removed from prefs")
        }

        fun isActive(): Boolean = currentPayload != null

        /** Build the SELECT APDU that the reader side sends to reach this service. */
        fun buildSelectApdu(): ByteArray {
            val aid = AID_BYTES
            // CLA=00 INS=A4 P1=04 P2=00 Lc=len AID
            val apdu = ByteArray(5 + aid.size)
            apdu[0] = 0x00                   // CLA
            apdu[1] = 0xA4.toByte()          // INS = SELECT
            apdu[2] = 0x04                   // P1 = Select by AID
            apdu[3] = 0x00                   // P2
            apdu[4] = aid.size.toByte()      // Lc
            System.arraycopy(aid, 0, apdu, 5, aid.size)
            return apdu
        }

        private fun hexToBytes(hex: String): ByteArray {
            val len = hex.length
            val data = ByteArray(len / 2)
            for (i in 0 until len step 2) {
                data[i / 2] = ((Character.digit(hex[i], 16) shl 4) +
                        Character.digit(hex[i + 1], 16)).toByte()
            }
            return data
        }
    }

    /**
     * Restore persisted payload when the OS recreates this service after killing it.
     */
    override fun onCreate() {
        super.onCreate()
        if (currentPayload == null) {
            val restored = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getString(KEY_PAYLOAD, null)
            if (restored != null) {
                currentPayload = restored
                Log.d(TAG, "Restored persisted HCE payload: $restored")
            }
        }
    }

    /**
     * Called by the NFC system when a reader device sends an APDU command.
     *
     * We only handle SELECT with our custom AID.
     * The response is the attendance payload bytes + SW_OK.
     */
    override fun processCommandApdu(commandApdu: ByteArray, extras: Bundle?): ByteArray {
        Log.d(TAG, "Received APDU: ${commandApdu.joinToString("") { "%02X".format(it) }}")

        if (commandApdu.size < 4) return SW_NOT_FOUND

        val ins = commandApdu[1]
        val p1 = commandApdu[2]

        // Only handle SELECT by AID (INS=A4, P1=04)
        if (ins == 0xA4.toByte() && p1 == 0x04.toByte()) {
            val dataLen = if (commandApdu.size > 4) commandApdu[4].toInt() and 0xFF else 0
            if (commandApdu.size >= 5 + dataLen) {
                val receivedAid = commandApdu.copyOfRange(5, 5 + dataLen)
                if (receivedAid.contentEquals(AID_BYTES)) {
                    // Our AID was selected → respond with attendance payload
                    val payload = currentPayload
                    if (payload != null) {
                        val payloadBytes = payload.toByteArray(Charsets.UTF_8)
                        Log.d(TAG, "Responding with payload: $payload (${payloadBytes.size} bytes)")
                        return payloadBytes + SW_OK
                    } else {
                        Log.w(TAG, "AID selected but no payload set")
                        return SW_CONDITIONS_NOT_SATISFIED
                    }
                }
            }
        }

        return SW_NOT_FOUND
    }

    override fun onDeactivated(reason: Int) {
        Log.d(TAG, "HCE deactivated, reason=$reason")
    }
}
