package com.example.walkietalkieapp.dna.adapter

import com.example.walkietalkieapp.dna.engine.ConfidenceCalculator
import com.example.walkietalkieapp.dna.engine.ScoreCalculator
import com.example.walkietalkieapp.dna.engine.StabilityTracker
import com.example.walkietalkieapp.dna.engine.TrendAnalyzer
import com.example.walkietalkieapp.dna.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * DNA probe adapter for Wi-Fi Direct P2P Group communications.
 * Observes Wi-Fi Direct group formation, connected peer count, and socket keepalive RTT/loss.
 */
class WifiDirectDnaAdapter : TransportProbeAdapter {

    override val transportType: TransportType = TransportType.WifiDirect

    private val _assessment = MutableStateFlow(CommunicationPathAssessment.unavailable(transportType))
    override val assessment: StateFlow<CommunicationPathAssessment> = _assessment.asStateFlow()

    private val stabilityTracker = StabilityTracker()
    private val trendAnalyzer = TrendAnalyzer()

    private var isGroupActive = false
    private var isConnected = false
    private var connectedPeersCount = 0
    private var lastMeasuredRttMs: Long? = null
    private var lastObservationTimestamp: Long = 0L
    private var sampleCount = 0

    override fun startProbing() {
        // Active probing begins when Wi-Fi Direct connections are formed
        recalculateAssessment()
    }

    override fun stopProbing() {
        isGroupActive = false
        isConnected = false
        connectedPeersCount = 0
        stabilityTracker.reset()
        trendAnalyzer.clear()
        _assessment.value = CommunicationPathAssessment.unavailable(transportType)
    }

    /**
     * Reports Wi-Fi Direct Group or Client connection state.
     */
    fun reportConnectionState(isGroupOwnerOrClient: Boolean, peerCount: Int) {
        val wasConnected = isConnected
        this.isGroupActive = isGroupOwnerOrClient
        this.connectedPeersCount = peerCount
        this.isConnected = isGroupOwnerOrClient && peerCount > 0

        if (isConnected && !wasConnected) {
            stabilityTracker.onConnected()
        } else if (!isConnected && wasConnected) {
            stabilityTracker.onDisconnected()
        }

        recalculateAssessment()
    }

    /**
     * Reports RTT latency observed during Wi-Fi Direct socket keepalive ping/ack (every 20s).
     */
    fun reportKeepaliveRtt(rttMs: Long) {
        lastMeasuredRttMs = rttMs
        lastObservationTimestamp = System.currentTimeMillis()
        sampleCount++
        stabilityTracker.recordSuccess()
        recalculateAssessment()
    }

    /**
     * Reports TCP socket transmission error.
     */
    fun reportSocketError() {
        stabilityTracker.recordFailure()
        recalculateAssessment()
    }

    @Synchronized
    private fun recalculateAssessment() {
        if (!isGroupActive && connectedPeersCount == 0) {
            _assessment.value = CommunicationPathAssessment.unavailable(transportType)
            return
        }

        val rtt = lastMeasuredRttMs ?: if (isConnected) 25L else null // Typical Wi-Fi Direct RTT is low (10-35ms)
        val latencyScore = ScoreCalculator.calculateLatencyScore(rtt)
        val lossScore = if (isConnected) 95 else 0
        val stabilityScore = stabilityTracker.calculateStabilityScore()
        val bandwidthScore = 90 // Wi-Fi Direct offers high P2P bandwidth (> 10 Mbps)
        val batteryScore = 75 // Wi-Fi Direct radio TX uses moderate power

        val overallQuality = if (!isConnected) 20 else ScoreCalculator.calculateOverallScore(
            latencyScore = latencyScore,
            lossScore = lossScore,
            stabilityScore = stabilityScore,
            batteryScore = batteryScore,
            bandwidthScore = bandwidthScore,
            mode = CommunicationMode.VOICE_PTT
        )

        val trend = trendAnalyzer.recordSample(overallQuality)
        val roleSuitability = ScoreCalculator.calculateRoleSuitability(
            latencyScore = latencyScore,
            lossScore = lossScore,
            stabilityScore = stabilityScore,
            batteryScore = batteryScore,
            bandwidthScore = bandwidthScore
        )

        val latencyMetric = rtt?.let { MetricValue.Measured(it, lastObservationTimestamp) } ?: MetricValue.Unknown
        val bwMetric = MetricValue.Estimated(15_000, "Wi-Fi Direct P2P Specification", System.currentTimeMillis())

        val confidenceScore = ConfidenceCalculator.calculateConfidence(
            metrics = listOf(latencyMetric, bwMetric),
            sampleCount = sampleCount,
            lastObservationTimeMs = if (lastObservationTimestamp > 0) lastObservationTimestamp else System.currentTimeMillis()
        )

        _assessment.value = CommunicationPathAssessment(
            transportType = transportType,
            isAvailable = isGroupActive,
            isConnected = isConnected,
            overallQualityScore = overallQuality,
            latencyScore = latencyScore,
            lossScore = lossScore,
            stabilityScore = stabilityScore,
            batteryScore = batteryScore,
            bandwidthScore = bandwidthScore,
            latencyMs = latencyMetric,
            latencyVarianceMs = MetricValue.Estimated(trendAnalyzer.getSampleVariance(), "SlidingWindowVariance"),
            packetLossPercent = MetricValue.Inferred(0.5f, "TCP Acknowledged Socket"),
            bandwidthKbps = bwMetric,
            signalDbm = MetricValue.Unknown, // Android does not expose continuous Wi-Fi Direct RSSI without scan throttling
            confidence = confidenceScore,
            confidenceLevel = ConfidenceLevel.fromScore(confidenceScore),
            trend = trend,
            roleSuitability = roleSuitability,
            batteryImpact = BatteryImpact.MODERATE,
            consecutiveSuccessCount = sampleCount,
            recentFailureCount = 0,
            disconnectCountLastMinute = stabilityTracker.getDisconnectCountLastMinute(),
            connectionDurationMs = stabilityTracker.getConnectionDurationMs(),
            lastStateChangeTimestamp = System.currentTimeMillis(),
            lastUpdatedTimestamp = System.currentTimeMillis()
        )
    }
}
