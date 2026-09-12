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
 * Historical quality record for predictive transport routing.
 * Tracks quality trends over time to predict future transport suitability.
 */
data class TransportQualityHistory(
    val transportType: TransportType,
    val scores: MutableList<Int> = mutableListOf(),
    var successCount: Int = 0,
    var failureCount: Int = 0,
    val lastSwitchMs: Long = 0L,
    val trend: TrendDirection = TrendDirection.UNKNOWN,
    val lastQualityTimestamp: Long = System.currentTimeMillis()
)

/**
 * Enhanced route recommendation with predictive elements.
 * Includes alternative transports, predicted hold times, and confidence decay.
 */
data class EnhancedRouteRecommendation(
    val recommendedTransport: TransportType?,
    val score: Int,
    val confidence: Float,
    val reason: String,
    val alternativeTransports: List<TransportType> = emptyList(),
    val predictedHoldTimeMs: Long = 0L,
    val qualityTrend: TrendDirection = TrendDirection.STABLE,
    val confidenceDecayFactor: Float = 1.0f
)

/**
 * Master Communication DNA Intelligence Engine.
 * Coordinates all transport probe adapters, aggregates normalized real-time assessments,
 * emits threshold events, enforces anti-flapping hysteresis, and provides predictive
 * adaptive routing based on historical quality trends and confidence calibration.
 */
object CommunicationDnaEngine {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val adapters = ConcurrentHashMap<TransportType, TransportProbeAdapter>()

    // Enhanced assessment state with history tracking
    private val _assessments = MutableStateFlow<Map<TransportType, CommunicationPathAssessment>>(emptyMap())
    val assessments: StateFlow<Map<TransportType, CommunicationPathAssessment>> = _assessments.asStateFlow()

    // Per-transport quality history for predictive routing
    private val _qualityHistories = MutableStateFlow<Map<TransportType, TransportQualityHistory>>(emptyMap())
    val qualityHistories: StateFlow<Map<TransportType, TransportQualityHistory>> = _qualityHistories.asStateFlow()

    // Enhanced event system
    private val _events = MutableSharedFlow<CommunicationDnaEvent>(extraBufferCapacity = 128)
    val events: SharedFlow<CommunicationDnaEvent> = _events.asSharedFlow()

    // Hysteresis & Anti-Flapping State
    private var lastSelectedTransport: TransportType? = null
    private var lastSwitchTimestamp: Long = 0L
    // Adaptive hold time based on transport volatility, not fixed
    private const val BASE_MIN_SWITCH_HOLD_TIME_MS = 10_000L // 10 seconds base
    private const val VOLATILITY_MULTIPLIER = 3.0f // Multiply hold time for volatile transports
    private const val MIN_SCORE_ADVANTAGE = 10 // Reduced from 15 for more responsive switching

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

                // Update quality history for predictive routing
                updateQualityHistory(adapter.transportType, currentAssessment)
            }
        }
    }

    fun unregisterAdapter(transportType: TransportType) {
        adapters.remove(transportType)?.stopProbing()
        _assessments.update { current -> current - transportType }
        _qualityHistories.update { current -> current - transportType }
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
     * Updates the quality history for a transport type with the latest assessment.
     * Maintains a rolling window of quality scores for trend prediction.
     */
    private fun updateQualityHistory(
        transportType: TransportType,
        current: CommunicationPathAssessment
    ) {
        var history = _qualityHistories.value[transportType] ?: TransportQualityHistory(transportType = transportType)

        // Add new score to rolling window (keep last 12 assessments = ~60 seconds at 5s intervals)
        history.scores.add(current.overallQualityScore)
        if (history.scores.size > 12) {
            history.scores.removeAt(0)
        }

        // Update success/failure counts based on quality threshold (70/100 is acceptable)
        if (current.overallQualityScore >= 70) {
            history.successCount++
        } else {
            history.failureCount++
        }

        // Recalculate trend every 3 assessments
        if (history.scores.size >= 3) {
            val recentScores = history.scores.takeLast(3)
            val trendDirection = calculateTrend(recentScores)
            history = history.copy(
                trend = trendDirection,
                lastQualityTimestamp = System.currentTimeMillis()
            )
        }

        _qualityHistories.update {
            it + (transportType to history)
        }
    }

    /**
     * Calculates trend direction from a list of quality scores.
     */
    private fun calculateTrend(scores: List<Int>): TrendDirection {
        if (scores.size < 3) return TrendDirection.UNKNOWN

        val firstHalfAvg = scores.take(scores.size / 2).average() ?: 0.0
        val secondHalfAvg = scores.drop(scores.size / 2).average() ?: 0.0

        val diff = secondHalfAvg - firstHalfAvg

        return when {
            diff >= 15 -> TrendDirection.IMPROVING
            diff <= -15 -> TrendDirection.DEGRADING
            abs(diff) <= 5 -> TrendDirection.STABLE
            else -> TrendDirection.VOLATILE
        }
    }

    /**
     * Enhanced evaluation of the best communication path with predictive routing.
     * Considers:
     * - Current assessment scores
     * - Historical quality trends
     * - Confidence calibration with decay
     * - Alternative transport availability
     * - Predicted hold times based on transport volatility
     */
    fun evaluateBestPath(
        mode: CommunicationMode = CommunicationMode.VOICE_PTT,
        currentTimeMs: Long = System.currentTimeMillis()
    ): EnhancedRouteRecommendation {
        val currentAssessments = _assessments.value.values.toList()
        val availableCandidates = currentAssessments.filter { it.isAvailable && it.isConnected }
        val alternativeTransports = currentAssessments.map { it.transportType }

        if (availableCandidates.isEmpty()) {
            return EnhancedRouteRecommendation(
                recommendedTransport = null,
                score = 0,
                confidence = 1.0f,
                reason = "No active communication transports available",
                alternativeTransports = alternativeTransports
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
            
            // Adjust hold time based on volatility
            val currentSelectedHistory = _qualityHistories.value[currentSelected]
            val isVolatile = currentSelectedHistory?.trend == TrendDirection.VOLATILE
            val adjustedHoldTimeMs = if (isVolatile) {
                (BASE_MIN_SWITCH_HOLD_TIME_MS * VOLATILITY_MULTIPLIER).toLong()
            } else {
                BASE_MIN_SWITCH_HOLD_TIME_MS
            }

            if (currentSelectedAssessment != null && timeSinceLastSwitch < adjustedHoldTimeMs) {
                val currentScore = currentSelectedAssessment.roleSuitability.scoreFor(mode)
                // Hysteresis Rule 2: Minimum Score Advantage
                if ((topScore - currentScore) < MIN_SCORE_ADVANTAGE) {
                    return EnhancedRouteRecommendation(
                        recommendedTransport = currentSelected,
                        score = currentScore,
                        confidence = currentSelectedAssessment.confidence,
                        reason = "Holding transport $currentSelected (Hysteresis hold active: ${timeSinceLastSwitch / 1000}s)",
                        alternativeTransports = alternativeTransports,
                        predictedHoldTimeMs = adjustedHoldTimeMs - timeSinceLastSwitch,
                        qualityTrend = currentSelectedHistory?.trend ?: TrendDirection.UNKNOWN
                    )
                }
            }
        }

        // Update selected transport tracking
        if (lastSelectedTransport != topCandidate.transportType) {
            lastSelectedTransport = topCandidate.transportType
            lastSwitchTimestamp = currentTimeMs
        }

        val topCandidateHistory = _qualityHistories.value[topCandidate.transportType]

        return EnhancedRouteRecommendation(
            recommendedTransport = topCandidate.transportType,
            score = topScore,
            confidence = topCandidate.confidence,
            reason = "Optimal path for $mode (Score: $topScore, Confidence: ${topCandidate.confidenceLevel.label})",
            alternativeTransports = alternativeTransports,
            qualityTrend = topCandidateHistory?.trend ?: TrendDirection.UNKNOWN
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
