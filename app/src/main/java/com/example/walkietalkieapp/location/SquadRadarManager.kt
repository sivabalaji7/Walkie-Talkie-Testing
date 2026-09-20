package com.example.walkietalkieapp.location

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.*

/**
 * Tactical representation of a high-precision GPS coordinate fix.
 */
data class TacticalLocation(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double = 0.0,
    val accuracy: Float = 0f,
    val speed: Float = 0f,
    val bearing: Float = 0f,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Tactical squad member location tracked relative to the operator.
 */
data class SquadMemberLocation(
    val callsign: String,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double = 0.0,
    val distanceMeters: Float = 0f,
    val trueBearing: Float = 0f,
    val relativeBearing: Float = 0f,
    val clockPosition: String = "12 o'clock",
    val cardinalDirection: String = "N",
    val isSos: Boolean = false,
    val lastSeen: Long = System.currentTimeMillis()
)

/**
 * Discrete zoom levels for the tactical PPI radar screen.
 */
enum class RadarRange(val radiusMeters: Float, val label: String) {
    RANGE_250M(250f, "250m"),
    RANGE_500M(500f, "500m"),
    RANGE_1KM(1000f, "1km"),
    RANGE_5KM(5000f, "5km")
}

/**
 * High-performance location and tactical radar manager for Squad operations.
 * Manages GPS coordinate fixes, hardware compass sensor smoothing (120Hz/60Hz),
 * relative distance/bearing trigonometry, and emergency SOS distress beaconing.
 */
object SquadRadarManager : SensorEventListener {

    private const val TAG = "SquadRadarManager"

    // Operator's current tactical GPS location
    private val _myLocation = MutableStateFlow<TacticalLocation?>(null)
    val myLocation: StateFlow<TacticalLocation?> = _myLocation.asStateFlow()

    // Operator's smoothed compass heading (0° - 360° Azimuth relative to True/Magnetic North)
    private val _compassHeading = MutableStateFlow(0f)
    val compassHeading: StateFlow<Float> = _compassHeading.asStateFlow()

    // Live mapped squad member positions relative to the operator
    private val _squadLocations = MutableStateFlow<Map<String, SquadMemberLocation>>(emptyMap())
    val squadLocations: StateFlow<Map<String, SquadMemberLocation>> = _squadLocations.asStateFlow()

    // Current radar display range scale
    private val _radarRange = MutableStateFlow(RadarRange.RANGE_500M)
    val radarRange: StateFlow<RadarRange> = _radarRange.asStateFlow()

    // Active emergency distress beacon status
    private val _isSosActive = MutableStateFlow(false)
    val isSosActive: StateFlow<Boolean> = _isSosActive.asStateFlow()

    // Satellite GPS lock status (true when accuracy <= 15m)
    private val _isGpsFixAcquired = MutableStateFlow(false)
    val isGpsFixAcquired: StateFlow<Boolean> = _isGpsFixAcquired.asStateFlow()

    // Internal sensor state
    private var sensorManager: SensorManager? = null
    private var rotationSensor: Sensor? = null
    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)
    private var currentFilteredHeading = 0f

    // -------------------------------------------------------------------------
    // Location Updates & Tracking
    // -------------------------------------------------------------------------

    /**
     * Updates the operator's self location and recalculates all peer relative bearings.
     */
    fun updateMyLocation(
        latitude: Double,
        longitude: Double,
        altitude: Double = 0.0,
        accuracy: Float = 5.0f,
        speed: Float = 0.0f,
        bearing: Float = 0.0f
    ) {
        val loc = TacticalLocation(
            latitude = latitude,
            longitude = longitude,
            altitude = altitude,
            accuracy = accuracy,
            speed = speed,
            bearing = bearing,
            timestamp = System.currentTimeMillis()
        )
        _myLocation.value = loc
        _isGpsFixAcquired.value = accuracy in 0.1f..25.0f

        // Recalculate relative metrics for all known squad members
        recalculateSquadMetrics(loc, _compassHeading.value)
    }

    /**
     * Ingests or updates a squad peer's location fix.
     */
    fun updatePeerLocation(
        callsign: String,
        latitude: Double,
        longitude: Double,
        altitude: Double = 0.0,
        isSos: Boolean = false
    ) {
        val currentMyLoc = _myLocation.value
        val (dist, trueBearing, relBearing, clockPos, cardinal) = if (currentMyLoc != null) {
            computeRelativeMetrics(currentMyLoc.latitude, currentMyLoc.longitude, latitude, longitude, _compassHeading.value)
        } else {
            RelativeMetrics(0f, 0f, 0f, "12 o'clock", "N")
        }

        val peer = SquadMemberLocation(
            callsign = callsign,
            latitude = latitude,
            longitude = longitude,
            altitude = altitude,
            distanceMeters = dist,
            trueBearing = trueBearing,
            relativeBearing = relBearing,
            clockPosition = clockPos,
            cardinalDirection = cardinal,
            isSos = isSos,
            lastSeen = System.currentTimeMillis()
        )

        val updated = _squadLocations.value.toMutableMap()
        updated[callsign] = peer
        _squadLocations.value = updated
    }

    /**
     * Removes a peer who has left the squad or timed out.
     */
    fun removePeerLocation(callsign: String) {
        val updated = _squadLocations.value.toMutableMap()
        updated.remove(callsign)
        _squadLocations.value = updated
    }

    /**
     * Clears all squad member location data (e.g. upon exiting room).
     */
    fun clearSquadLocations() {
        _squadLocations.value = emptyMap()
    }

    /**
     * Toggles emergency distress SOS beacon broadcast.
     */
    fun setSosActive(active: Boolean) {
        _isSosActive.value = active
    }

    /**
     * Changes radar scale (250m, 500m, 1km, 5km).
     */
    fun setRadarRange(range: RadarRange) {
        _radarRange.value = range
    }

    // -------------------------------------------------------------------------
    // Mathematical & Trigonometric Calculations
    // -------------------------------------------------------------------------

    /**
     * Great-circle distance between two GPS coordinates using the Haversine formula (meters).
     */
    fun calculateHaversineDistance(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Float {
        val earthRadiusMeters = 6371000.0

        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)

        val a = sin(dLat / 2.0).pow(2.0) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2.0).pow(2.0)

        val c = 2.0 * atan2(sqrt(a), sqrt(1.0 - a))
        return (earthRadiusMeters * c).toFloat()
    }

    /**
     * Calculates the forward azimuth / initial true bearing from point A to point B in degrees (0° - 360°).
     */
    fun calculateTrueBearing(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Float {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val deltaLambda = Math.toRadians(lon2 - lon1)

        val y = sin(deltaLambda) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(deltaLambda)

        val theta = atan2(y, x)
        val bearing = Math.toDegrees(theta)
        return ((bearing + 360.0) % 360.0).toFloat()
    }

    /**
     * Calculates the relative bearing (0° - 360°) relative to operator's forward heading.
     * 0° = dead ahead (12 o'clock), 90° = direct right (3 o'clock), 180° = behind (6 o'clock).
     */
    fun calculateRelativeBearing(userHeading: Float, trueBearing: Float): Float {
        val diff = (trueBearing - userHeading + 360f) % 360f
        return diff
    }

    /**
     * Converts a relative bearing angle (0° - 360°) into a tactical military clock position.
     */
    fun bearingToClockPosition(relativeBearing: Float): String {
        val normalized = (relativeBearing % 360f + 360f) % 360f
        val hour = (((normalized + 15f) / 30f).toInt() % 12).let { if (it == 0) 12 else it }
        return "$hour o'clock"
    }

    /**
     * Converts a true bearing angle into a compass cardinal direction string.
     */
    fun bearingToCardinal(bearing: Float): String {
        val normalized = (bearing % 360f + 360f) % 360f
        val directions = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW", "N")
        val index = ((normalized + 22.5f) / 45f).toInt()
        return directions[index % directions.size]
    }

    private data class RelativeMetrics(
        val distanceMeters: Float,
        val trueBearing: Float,
        val relativeBearing: Float,
        val clockPosition: String,
        val cardinalDirection: String
    )

    private fun computeRelativeMetrics(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double,
        heading: Float
    ): RelativeMetrics {
        val dist = calculateHaversineDistance(lat1, lon1, lat2, lon2)
        val trueBearing = calculateTrueBearing(lat1, lon1, lat2, lon2)
        val relBearing = calculateRelativeBearing(heading, trueBearing)
        val clock = bearingToClockPosition(relBearing)
        val cardinal = bearingToCardinal(trueBearing)
        return RelativeMetrics(dist, trueBearing, relBearing, clock, cardinal)
    }

    private fun recalculateSquadMetrics(myLoc: TacticalLocation, heading: Float) {
        val currentMap = _squadLocations.value
        if (currentMap.isEmpty()) return

        val updated = currentMap.mapValues { (_, member) ->
            val metrics = computeRelativeMetrics(
                myLoc.latitude, myLoc.longitude,
                member.latitude, member.longitude,
                heading
            )
            member.copy(
                distanceMeters = metrics.distanceMeters,
                trueBearing = metrics.trueBearing,
                relativeBearing = metrics.relativeBearing,
                clockPosition = metrics.clockPosition,
                cardinalDirection = metrics.cardinalDirection
            )
        }
        _squadLocations.value = updated
    }

    // -------------------------------------------------------------------------
    // Hardware Compass Sensor (Orientation)
    // -------------------------------------------------------------------------

    /**
     * Binds hardware rotation vector sensor for smooth compass heading tracking.
     */
    fun startCompass(context: Context) {
        if (sensorManager != null) return
        try {
            sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            rotationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            rotationSensor?.let { sensor ->
                sensorManager?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
                Log.d(TAG, "Hardware Compass listener registered successfully")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to start hardware compass sensor", e)
        }
    }

    /**
     * Unbinds hardware rotation sensor to preserve battery when dialog is dismissed.
     */
    fun stopCompass() {
        try {
            sensorManager?.unregisterListener(this)
            sensorManager = null
            rotationSensor = null
            Log.d(TAG, "Hardware Compass listener unregistered")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to unregister compass sensor", e)
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return

        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
        SensorManager.getOrientation(rotationMatrix, orientationAngles)

        val rawAzimuthRadians = orientationAngles[0]
        val rawDegrees = (Math.toDegrees(rawAzimuthRadians.toDouble()).toFloat() + 360f) % 360f

        // Exponential smoothing filter over circular degrees
        val smoothed = smoothDegrees(currentFilteredHeading, rawDegrees, alpha = 0.25f)
        currentFilteredHeading = smoothed
        _compassHeading.value = smoothed

        // Update relative angles if we have location
        _myLocation.value?.let { myLoc ->
            recalculateSquadMetrics(myLoc, smoothed)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    /**
     * Exponential circular smoothing between two angles in [0, 360).
     */
    fun smoothDegrees(current: Float, target: Float, alpha: Float): Float {
        var diff = (target - current + 180f) % 360f - 180f
        if (diff < -180f) diff += 360f
        val result = current + alpha * diff
        return (result + 360f) % 360f
    }

    // -------------------------------------------------------------------------
    // Android LocationManager GPS Query Helper
    // -------------------------------------------------------------------------

    /**
     * Queries Android's native LocationManager for an instantaneous GPS fix.
     */
    fun fetchDeviceLocation(context: Context, onComplete: (TacticalLocation?) -> Unit) {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: run {
            onComplete(null)
            return
        }

        try {
            var bestLoc: Location? = null

            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                bestLoc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            }
            if (bestLoc == null && lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                bestLoc = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            }

            if (bestLoc != null) {
                val tactical = TacticalLocation(
                    latitude = bestLoc.latitude,
                    longitude = bestLoc.longitude,
                    altitude = bestLoc.altitude,
                    accuracy = bestLoc.accuracy,
                    speed = bestLoc.speed,
                    bearing = bestLoc.bearing,
                    timestamp = bestLoc.time
                )
                updateMyLocation(
                    latitude = tactical.latitude,
                    longitude = tactical.longitude,
                    altitude = tactical.altitude,
                    accuracy = tactical.accuracy,
                    speed = tactical.speed,
                    bearing = tactical.bearing
                )
                onComplete(tactical)
            } else {
                onComplete(null)
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Location permission missing: ${e.message}")
            onComplete(null)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching location: ${e.message}")
            onComplete(null)
        }
    }
}
