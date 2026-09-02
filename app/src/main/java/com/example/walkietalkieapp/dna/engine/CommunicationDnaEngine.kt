package com.example.walkietalkieapp.dna.engine

import android.content.Context
import com.example.walkietalkieapp.dna.adapter.BluetoothDnaAdapter
import com.example.walkietalkieapp.dna.adapter.InternetDnaAdapter
import com.example.walkietalkieapp.dna.adapter.TransportProbeAdapter
import com.example.walkietalkieapp.dna.adapter.WifiDirectDnaAdapter
import com.example.walkietalkieapp.dna.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/**
 * Result recommendation from the intelligence engine for future routing decisions.
 */
data class RouteRecommendation(
    val recommendedTransport: TransportType?,
    val score: Int,
    val confidence: Float,
    val reason: String,
    val alternativeAssessments: List<CommunicationPathAssessment>
)

/**
 * Master Communication DNA Intelligence Engine.
 * Coordinates all transport probe adapters, aggregates normalized real-time assessments,
 * emits threshold events, and enforces anti-flapping hysteresis for future adaptive routing.
 */
object CommunicationDnaEngine {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val adapters = ConcurrentHashMap<TransportType, TransportProbeAdapter>()

    private val _assessments = MutableStateFlow<Map<TransportType, CommunicationPathAssessment>>(emptyMap())
    val assessments: StateFlow<Map<TransportType, CommunicationPathAssessment>> = _assessments.asStateFlow()

    private val _events = MutableSharedFlow<CommunicationDnaEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<CommunicationDnaEvent> = _events.asSharedFlow()

    // Hysteresis & Anti-Flapping State
    private var lastSelectedTransport: TransportType? = null
    private var lastSwitchTimestamp: Long = 0L
    private const val MIN_SWITCH_HOLD_TIME_MS = 10_000L // 10 seconds hold time
    private const val MIN_SCORE_ADVANTAGE = 15 // Must beat current transport by 15 points to trigger handoff

    // Direct adapter accessors for existing subsystem reporting
    var internetAdapter: InternetDnaAdapter? = null
        private set
    var wifiDirectAdapter: WifiDirectDnaAdapter? = null
        private set
    var bluetoothAdapter: BluetoothDnaAdapter? = null
        private set

    /**
     * Initializes the DNA Engine with standard platform adapters.
     */
    fun initialize(context: Context) {
        val internet = InternetDnaAdapter(context.applicationContext, scope)
        val wifiDirect = WifiDirectDnaAdapter()
        val bluetooth = BluetoothDnaAdapter()

        internetAdapter = internet
        wifiDirectAdapter = wifiDirect
        bluetoothAdapter = bluetooth

        registerAdapter(internet)
        registerAdapter(wifiDirect)
        registerAdapter(bluetooth)

        start()
    }

    fun registerAdapter(adapter: TransportProbeAdapter) {
        adapters[adapter.transportType] = adapter
        scope.launch {
            var previousAssessment: CommunicationPathAssessment? = null
            adapter.assessment.collect { currentAssessment ->
                updateAssessment(adapter.transportType, currentAssessment, previousAssessment)
                previousAssessment = currentAssessment
            }
        }
    }

    fun unregisterAdapter(transportType: TransportType) {
        adapters.remove(transportType)?.stopProbing()
        _assessments.update { current -> current - transportType }
    }

    fun start() {
        adapters.values.forEach { it.startProbing() }
    }

    fun stop() {
        adapters.values.forEach { it.stopProbing() }
    }

    fun onAudioSessionActive(isActive: Boolean) {
        adapters.values.forEach { it.onAudioSessionStateChanged(isActive) }
    }

    @Synchronized
    private fun updateAssessment(
        type: TransportType,
        current: CommunicationPathAssessment,
        previous: CommunicationPathAssessment?
    ) {
        _assessments.update { currentMap ->
            currentMap + (type to current)
        }

        // Detect and emit discrete events
        if (previous != null) {
            if (!previous.isAvailable && current.isAvailable) {
                _events.tryEmit(CommunicationDnaEvent.TransportBecameAvailable(type))
            } else if (previous.isAvailable && !current.isAvailable) {
                _events.tryEmit(CommunicationDnaEvent.TransportBecameUnavailable(type, "Transport disconnected/unavailable"))
            }

            if (previous.trend != current.trend) {
                _events.tryEmit(CommunicationDnaEvent.TrendChanged(type, previous.trend, current.trend))
            }

            if (abs(previous.overallQualityScore - current.overallQualityScore) >= 15) {
                _events.tryEmit(
                    CommunicationDnaEvent.QualityCrossedThreshold(
                        transportType = type,
                        oldScore = previous.overallQualityScore,
                        newScore = current.overallQualityScore,
                        isSignificant = true
                    )
                )
            }

            if (current.disconnectCountLastMinute >= 2 && previous.disconnectCountLastMinute < 2) {
                _events.tryEmit(
                    CommunicationDnaEvent.TransportBecameUnstable(
                        transportType = type,
                        flapCount = current.disconnectCountLastMinute,
                        message = "Transport $type has disconnected ${current.disconnectCountLastMinute} times in the last minute"
                    )
                )
            }
        }
    }

    /**
     * Evaluates all currently available communication paths and determines the optimal route
     * for the requested communication mode with hysteresis anti-flapping protections.
     */
    fun evaluateBestPath(
        mode: CommunicationMode = CommunicationMode.VOICE_PTT,
        currentTimeMs: Long = System.currentTimeMillis()
    ): RouteRecommendation {
        val currentAssessments = _assessments.value.values.toList()
        val availableCandidates = currentAssessments.filter { it.isAvailable && it.isConnected }

        if (availableCandidates.isEmpty()) {
            return RouteRecommendation(
                recommendedTransport = null,
                score = 0,
                confidence = 1.0f,
                reason = "No active communication transports available",
                alternativeAssessments = currentAssessments
            )
        }

        // Rank candidates by their mode-specific suitability score * confidence
        val ranked = availableCandidates.sortedByDescending { assessment ->
            val suitability = assessment.roleSuitability.scoreFor(mode)
            // Weight candidate by confidence to avoid picking poorly-understood paths
            suitability * (0.5f + 0.5f * assessment.confidence)
        }

        val topCandidate = ranked.first()
        val topScore = topCandidate.roleSuitability.scoreFor(mode)

        // Hysteresis Rule 1: Minimum Hold Time
        val currentSelected = lastSelectedTransport
        if (currentSelected != null && currentSelected != topCandidate.transportType) {
            val timeSinceLastSwitch = currentTimeMs - lastSwitchTimestamp
            val currentSelectedAssessment = availableCandidates.find { it.transportType == currentSelected }

            if (currentSelectedAssessment != null && timeSinceLastSwitch < MIN_SWITCH_HOLD_TIME_MS) {
                val currentScore = currentSelectedAssessment.roleSuitability.scoreFor(mode)
                // Hysteresis Rule 2: Minimum Score Advantage
                if ((topScore - currentScore) < MIN_SCORE_ADVANTAGE) {
                    return RouteRecommendation(
                        recommendedTransport = currentSelected,
                        score = currentScore,
                        confidence = currentSelectedAssessment.confidence,
                        reason = "Holding transport $currentSelected (Hysteresis hold active: ${timeSinceLastSwitch / 1000}s)",
                        alternativeAssessments = currentAssessments
                    )
                }
            }
        }

        // Update selected transport tracking
        if (lastSelectedTransport != topCandidate.transportType) {
            lastSelectedTransport = topCandidate.transportType
            lastSwitchTimestamp = currentTimeMs
        }

        return RouteRecommendation(
            recommendedTransport = topCandidate.transportType,
            score = topScore,
            confidence = topCandidate.confidence,
            reason = "Optimal path for $mode (Score: $topScore, Confidence: ${topCandidate.confidenceLevel.label})",
            alternativeAssessments = currentAssessments
        )
    }

    /**
     * Diagnostic summary string for logging and debug displays.
     */
    fun dumpDiagnosticSummary(): String {
        val builder = StringBuilder("=== COMMUNICATION DNA INTELLIGENCE REPORT ===\n")
        _assessments.value.forEach { (type, a) ->
            builder.append("[${type.id}] Available: ${a.isAvailable} | Connected: ${a.isConnected} | Quality: ${a.overallQualityScore}/100 | Latency: ${a.latencyMs.valueOrNull() ?: "N/A"}ms | Trend: ${a.trend.name} | Conf: ${a.confidenceLevel.label} (${(a.confidence * 100).toInt()}%)\n")
        }
        return builder.toString()
    }
}
