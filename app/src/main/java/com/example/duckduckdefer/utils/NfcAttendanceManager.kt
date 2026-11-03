package com.example.duckduckdefer.utils

import android.app.Activity
import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.os.Build
import android.util.Log
import com.example.duckduckdefer.services.AttendanceHceService
import java.nio.charset.Charset

/**
 * Manages NFC operations for attendance check-in.
 *
 * Three modes:
 *
 * 1. **Phone-to-phone** (primary):
 *    - Student starts HCE emulation → phone acts as NFC endpoint with custom AID.
 *    - Proctor taps phone → we connect via IsoDep and send SELECT with our AID.
 *    - Student phone responds with the attendance payload. No conflicts with payment apps.
 *
 * 2. **Physical NFC tag** (also supported):
 *    - Student writes attendance data to a physical NDEF tag.
 *    - Proctor reads the physical tag via standard NDEF.
 *
 * 3. **QR code** (fallback):
 *    - Student shows QR code, Proctor scans it with camera.
 */
object NfcAttendanceManager {

    private const val TAG = "NfcAttendanceManager"
    const val ATTENDANCE_PREFIX = "attendance"

    // ── Availability ──────────────────────────────────────────

    fun isNfcAvailable(activity: Activity): Boolean {
        val adapter = NfcAdapter.getDefaultAdapter(activity)
        return adapter != null
    }

    fun isNfcEnabled(activity: Activity): Boolean {
        val adapter = NfcAdapter.getDefaultAdapter(activity)
        return adapter?.isEnabled == true
    }

    // ── Enable foreground dispatch (call in onResume) ────────

    fun enableForegroundDispatch(activity: Activity) {
        val adapter = NfcAdapter.getDefaultAdapter(activity) ?: return
        val intent = android.app.PendingIntent.getActivity(
            activity,
            0,
            Intent(activity, activity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                android.app.PendingIntent.FLAG_MUTABLE
            else 0
        )
        // Accept all NFC tag types
        adapter.enableForegroundDispatch(activity, intent, null, null)
    }

    // ── Disable foreground dispatch (call in onPause) ────────

    fun disableForegroundDispatch(activity: Activity) {
        val adapter = NfcAdapter.getDefaultAdapter(activity) ?: return
        try {
            adapter.disableForegroundDispatch(activity)
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Could not disable foreground dispatch", e)
        }
    }

    // ── Build / Parse attendance payload ─────────────────────

    fun buildAttendancePayload(bookingID: String, studentUID: String): String {
        return "$ATTENDANCE_PREFIX:$bookingID:$studentUID"
    }

    fun parseAttendancePayload(payload: String): Triple<String, String, String>? {
        val parts = payload.split(":")
        return if (parts.size == 3 && parts[0] == ATTENDANCE_PREFIX) {
            Triple(parts[0], parts[1], parts[2]) // prefix, bookingID, studentUID
        } else null
    }

    // ── Phone-to-phone: HCE emulation (student side) ────────

    fun startEmitting(bookingID: String, studentUID: String) {
        val payload = buildAttendancePayload(bookingID, studentUID)
        AttendanceHceService.setPayload(payload)
        Log.d(TAG, "HCE emulation started: $payload")
    }

    /** Context-aware version that persists the payload across process restarts. */
    fun startEmitting(bookingID: String, studentUID: String, context: android.content.Context) {
        val payload = buildAttendancePayload(bookingID, studentUID)
        AttendanceHceService.setPayload(payload, context)
        Log.d(TAG, "HCE emulation started (persisted): $payload")
    }

    fun stopEmitting() {
        AttendanceHceService.clearPayload()
        Log.d(TAG, "HCE emulation stopped")
    }

    /** Context-aware version that also clears the persisted payload. */
    fun stopEmitting(context: android.content.Context) {
        AttendanceHceService.clearPayload(context)
        Log.d(TAG, "HCE emulation stopped (persisted clear)")
    }

    fun isEmitting(): Boolean = AttendanceHceService.isActive()

    // ── Read from NFC intent (proctor side) ──────────────────

    /**
     * Attempt to read attendance data from an NFC intent.
     *
     * Tries in order:
     *   1. IsoDep → send SELECT with our custom AID (phone-to-phone HCE)
     *   2. NDEF messages in the intent (physical NDEF tag)
     *   3. Direct NDEF tag read (physical tag fallback)
     *
     * Returns the attendance payload string, or null if nothing was found.
     */
    fun readTagFromIntent(intent: Intent): String? {
        if (intent.action != NfcAdapter.ACTION_NDEF_DISCOVERED &&
            intent.action != NfcAdapter.ACTION_TECH_DISCOVERED &&
            intent.action != NfcAdapter.ACTION_TAG_DISCOVERED
        ) return null

        // Get the Tag object
        val tag: Tag? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
        }

        // 1. Try IsoDep (phone-to-phone via our custom HCE AID)
        tag?.let {
            val hceResult = readViaIsoDep(it)
            if (hceResult != null) return hceResult
        }

        // 2. Try NDEF messages in the intent (physical NDEF tag)
        val rawMessages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES, NdefMessage::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)
        }

        if (rawMessages != null && rawMessages.isNotEmpty()) {
            val ndefMessage = rawMessages[0] as NdefMessage
            for (record in ndefMessage.records) {
                val payload = readNdefRecordPayload(record)
                if (payload != null && payload.startsWith(ATTENDANCE_PREFIX)) {
                    return payload
                }
            }
        }

        // 3. Direct NDEF tag read fallback
        tag?.let {
            return readFromNdefTag(it)
        }

        return null
    }

    /**
     * Connect to the tag via IsoDep and send our custom SELECT APDU.
     * If the other device is running our AttendanceHceService, it will
     * respond with the attendance payload.
     */
    private fun readViaIsoDep(tag: Tag): String? {
        val isoDep = IsoDep.get(tag) ?: return null
        return try {
            isoDep.connect()
            isoDep.timeout = 5000 // 5 second timeout (phone-to-phone can be slow)

            // Send SELECT with our custom AID
            val selectApdu = AttendanceHceService.buildSelectApdu()
            Log.d(TAG, "Sending SELECT APDU: ${selectApdu.joinToString("") { "%02X".format(it) }}")

            // Try up to 2 times — first HCE tap often fails with TransceiveException
            var response: ByteArray? = null
            for (attempt in 1..2) {
                try {
                    response = isoDep.transceive(selectApdu)
                    break // success
                } catch (e: java.io.IOException) {
                    Log.w(TAG, "IsoDep transceive attempt $attempt failed: ${e.message}")
                    if (attempt == 2) throw e
                    Thread.sleep(200) // brief pause before retry
                }
            }
            isoDep.close()

            if (response != null && response.size >= 2) {
                // Last 2 bytes are the status word
                val sw1 = response[response.size - 2]
                val sw2 = response[response.size - 1]

                if (sw1 == 0x90.toByte() && sw2 == 0x00.toByte()) {
                    // Success! Extract payload (everything before the status word)
                    val payloadBytes = response.copyOfRange(0, response.size - 2)
                    val payload = String(payloadBytes, Charsets.UTF_8)
                    Log.d(TAG, "HCE read success: $payload")
                    if (payload.startsWith(ATTENDANCE_PREFIX)) payload else null
                } else {
                    Log.d(TAG, "HCE returned status: ${"%02X%02X".format(sw1, sw2)}")
                    null
                }
            } else {
                null
            }
        } catch (e: Exception) {
            Log.d(TAG, "IsoDep read failed (not an HCE device?): ${e.message}")
            try { isoDep.close() } catch (_: Exception) {}
            null
        }
    }

    // ── NDEF helpers (for physical tags) ─────────────────────

    private fun readNdefRecordPayload(record: NdefRecord): String? {
        return try {
            val payload = record.payload
            if (record.tnf == NdefRecord.TNF_WELL_KNOWN && record.type.contentEquals(NdefRecord.RTD_TEXT)) {
                val languageCodeLength = payload[0].toInt() and 0x3F
                String(payload, languageCodeLength + 1, payload.size - languageCodeLength - 1, Charset.forName("UTF-8"))
            } else {
                String(payload, Charset.forName("UTF-8"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read NDEF record", e)
            null
        }
    }

    private fun readFromNdefTag(tag: Tag): String? {
        return try {
            val ndef = Ndef.get(tag) ?: return null
            ndef.connect()
            val ndefMessage = ndef.ndefMessage
            ndef.close()
            ndefMessage?.records?.firstNotNullOfOrNull { readNdefRecordPayload(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read NDEF tag", e)
            null
        }
    }

    // ── Write physical NFC tag ───────────────────────────────

    fun writeTagFromIntent(intent: Intent, content: String): Boolean {
        val tag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
        } ?: return false

        return writeToTag(tag, content)
    }

    private fun writeToTag(tag: Tag, content: String): Boolean {
        return try {
            val record = createTextRecord(content)
            val message = NdefMessage(arrayOf(record))

            val ndef = Ndef.get(tag)
            if (ndef != null) {
                ndef.connect()
                if (!ndef.isWritable) {
                    ndef.close()
                    return false
                }
                ndef.writeNdefMessage(message)
                ndef.close()
                true
            } else {
                val formatable = NdefFormatable.get(tag)
                if (formatable != null) {
                    formatable.connect()
                    formatable.format(message)
                    formatable.close()
                    true
                } else {
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write NFC tag", e)
            false
        }
    }

    private fun createTextRecord(text: String): NdefRecord {
        val language = "en"
        val languageBytes = language.toByteArray(Charset.forName("US-ASCII"))
        val textBytes = text.toByteArray(Charset.forName("UTF-8"))
        val payload = ByteArray(1 + languageBytes.size + textBytes.size)
        payload[0] = languageBytes.size.toByte()
        System.arraycopy(languageBytes, 0, payload, 1, languageBytes.size)
        System.arraycopy(textBytes, 0, payload, 1 + languageBytes.size, textBytes.size)
        return NdefRecord(NdefRecord.TNF_WELL_KNOWN, NdefRecord.RTD_TEXT, ByteArray(0), payload)
    }
}
