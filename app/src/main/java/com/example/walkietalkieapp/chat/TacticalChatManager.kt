package com.example.walkietalkieapp.chat

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.walkietalkieapp.socket.SupabaseRealtimeManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

/**
 * Represents a tactical text transmission or GPS beacon dropped in the squad room.
 */
data class TacticalMessage(
    val id: String = UUID.randomUUID().toString(),
    val senderName: String,
    val isSelf: Boolean,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationLabel: String? = null,
    val isBeacon: Boolean = false
)

/**
 * Pre-defined tactical quick action templates for 1-tap transmission.
 */
enum class TacticalQuickStatus(val chipLabel: String, val transmissionText: String) {
    ON_MY_WAY("⚡ ON MY WAY", "ON MY WAY — Moving to target location"),
    RADIO_SILENCE("🤫 RADIO SILENCE", "RADIO SILENCE — Maintaining stealth profile"),
    POSITION_SECURED("🛡️ SECURED", "POSITION SECURED — Area consolidated"),
    ASSISTANCE_REQUIRED("⚠️ ASSISTANCE", "ASSISTANCE REQUIRED — Need support at my position"),
    VISUAL_CONTACT("👁️ CONTACT", "VISUAL CONTACT — Target/unit in sight"),
    DROP_BEACON("📍 GPS BEACON", "DROPPED GPS BEACON — Coordinates broadcasted")
}

/**
 * Manages the tactical micro-chat, rolling in-memory buffer, unread counters,
 * and high-precision GPS coordinate beacon acquisitions.
 */
object TacticalChatManager {
    private const val TAG = "TacticalChatManager"
    private const val MAX_MESSAGES = 50

    private val _messages = MutableStateFlow<List<TacticalMessage>>(emptyList())
    val messages: StateFlow<List<TacticalMessage>> = _messages.asStateFlow()

    private val _unreadCount = MutableStateFlow(0)
    val unreadCount: StateFlow<Int> = _unreadCount.asStateFlow()

    @Volatile
    var isChatDialogVisible: Boolean = false

    /**
     * Clears the unread message badge count.
     */
    fun markAllRead() {
        _unreadCount.value = 0
    }

    /**
     * Clears all tactical messages (e.g. on squad exit or manual purge).
     */
    fun clearMessages() {
        _messages.value = emptyList()
        _unreadCount.value = 0
    }

    /**
     * Dispatches an outgoing text message to the squad room.
     */
    fun sendTextMessage(senderName: String, text: String) {
        val cleanText = text.trim()
        if (cleanText.isBlank()) return

        val msg = TacticalMessage(
            senderName = senderName,
            isSelf = true,
            content = cleanText,
            timestamp = System.currentTimeMillis()
        )

        appendMessage(msg)

        // Broadcast to squad peers via real-time data link
        try {
            SupabaseRealtimeManager.sendTacticalMessage(
                textContent = cleanText,
                latitude = null,
                longitude = null,
                locationLabel = null,
                isBeacon = false
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error broadcasting tactical message: ${e.message}", e)
        }
    }

    /**
     * Dispatches an outgoing tactical GPS coordinate beacon.
     */
    fun sendGpsBeacon(
        context: Context,
        senderName: String,
        label: String = "SQUAD BEACON",
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        fetchCurrentLocation(context) { location ->
            if (location != null) {
                val lat = location.latitude
                val lng = location.longitude
                val beaconText = "BEACON DROPPED: ${String.format(java.util.Locale.US, "%.5f", lat)}°, ${String.format(java.util.Locale.US, "%.5f", lng)}°"

                val msg = TacticalMessage(
                    senderName = senderName,
                    isSelf = true,
                    content = beaconText,
                    timestamp = System.currentTimeMillis(),
                    latitude = lat,
                    longitude = lng,
                    locationLabel = label,
                    isBeacon = true
                )

                appendMessage(msg)

                // Sync self location with SquadRadarManager
                com.example.walkietalkieapp.location.SquadRadarManager.updateMyLocation(
                    latitude = lat,
                    longitude = lng,
                    altitude = location.altitude,
                    accuracy = location.accuracy,
                    speed = location.speed,
                    bearing = location.bearing
                )

                try {
                    SupabaseRealtimeManager.sendTacticalMessage(
                        textContent = beaconText,
                        latitude = lat,
                        longitude = lng,
                        locationLabel = label,
                        isBeacon = true
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error broadcasting GPS beacon: ${e.message}", e)
                }

                onComplete?.invoke(true)
            } else {
                Log.w(TAG, "Location unavailable for GPS beacon transmission")
                onComplete?.invoke(false)
            }
        }
    }

    /**
     * Ingests an incoming tactical message received from a squad peer.
     */
    fun receiveMessage(
        sender: String,
        content: String,
        timestamp: Long = System.currentTimeMillis(),
        latitude: Double? = null,
        longitude: Double? = null,
        locationLabel: String? = null,
        isBeacon: Boolean = false
    ) {
        val msg = TacticalMessage(
            senderName = sender,
            isSelf = false,
            content = content,
            timestamp = timestamp,
            latitude = latitude,
            longitude = longitude,
            locationLabel = locationLabel,
            isBeacon = isBeacon
        )

        appendMessage(msg)

        // If message contains GPS coordinates, automatically plot peer on Squad Radar
        if (latitude != null && longitude != null) {
            val isSos = isBeacon && (locationLabel?.contains("SOS", ignoreCase = true) == true || content.contains("SOS", ignoreCase = true))
            com.example.walkietalkieapp.location.SquadRadarManager.updatePeerLocation(
                callsign = sender,
                latitude = latitude,
                longitude = longitude,
                isSos = isSos
            )
        }

        if (!isChatDialogVisible) {
            _unreadCount.update { it + 1 }
        }
    }

    private fun appendMessage(msg: TacticalMessage) {
        _messages.update { currentList ->
            val updated = currentList + msg
            if (updated.size > MAX_MESSAGES) {
                updated.drop(updated.size - MAX_MESSAGES)
            } else {
                updated
            }
        }
    }

    /**
     * Queries Android's native LocationManager for high-precision GPS coordinates,
     * gracefully falling back to Network provider if GPS lock is unavailable.
     */
    @SuppressLint("MissingPermission")
    fun fetchCurrentLocation(context: Context, onResult: (Location?) -> Unit) {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: run {
            onResult(null)
            return
        }

        val hasFine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val hasCoarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasFine && !hasCoarse) {
            Log.w(TAG, "Location permissions not granted")
            onResult(null)
            return
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                lm.getCurrentLocation(
                    LocationManager.GPS_PROVIDER,
                    null,
                    context.mainExecutor
                ) { loc ->
                    if (loc != null) {
                        onResult(loc)
                    } else {
                        // Fallback to network provider or last known
                        val netLoc = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                        val gpsLoc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                        onResult(bestOf(gpsLoc, netLoc))
                    }
                }
            } else {
                val gpsLoc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                val netLoc = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                onResult(bestOf(gpsLoc, netLoc))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error acquiring location: ${e.message}", e)
            onResult(null)
        }
    }

    private fun bestOf(locA: Location?, locB: Location?): Location? {
        return when {
            locA == null -> locB
            locB == null -> locA
            locA.time > locB.time -> locA
            else -> locB
        }
    }
}
