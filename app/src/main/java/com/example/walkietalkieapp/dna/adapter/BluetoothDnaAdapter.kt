package com.example.walkietalkieapp.dna.adapter

import com.example.walkietalkieapp.dna.engine.ConfidenceCalculator
import com.example.walkietalkieapp.dna.engine.ScoreCalculator
import com.example.walkietalkieapp.dna.engine.StabilityTracker
import com.example.walkietalkieapp.dna.engine.TrendAnalyzer
import com.example.walkietalkieapp.dna.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * DNA probe adapter for Bluetooth RFCOMM tactical mesh communications.
 * Observes Bluetooth radio state, connected RFCOMM sockets, and keepalive ping RTT.
 */
class BluetoothDnaAdapter : TransportProbeAdapter {

    override val transportType: TransportType = TransportType.Bluetooth

    private val _assessment = MutableStateFlow(CommunicationPathAssessment.unavailable(transportType))
    override val assessment: StateFlow<CommunicationPathAssessment> = _assessment.asStateFlow()

    private val stabilityTracker = StabilityTracker()
    private val trendAnalyzer = TrendAnalyzer()

    private var isBluetoothEnabled = false
    private var isConnected = false
    private var connectedPeersCount = 0
    private var lastMeasuredRttMs: Long? = null
    private var lastObservationTimestamp: Long = 0L
    private var sampleCount = 0

    override fun startProbing() {
        recalculateAssessment()
    }

    override fun stopProbing() {
        isConnected = false
        connectedPeersCount = 0
        stabilityTracker.reset()
        trendAnalyzer.clear()
        _assessment.value = CommunicationPathAssessment.unavailable(transportType)
    }

    /**
     * Reports Bluetooth adapter power state.
     */
    fun reportAdapterState(isEnabled: Boolean) {
        this.isBluetoothEnabled = isEnabled
        if (!isEnabled) {
            isConnected = false
            connectedPeersCount = 0
            stabilityTracker.onDisconnected()
        }
        recalculateAssessment()
    }

    /**
     * Reports RFCOMM connection status and member count.
     */
    fun reportConnectionState(connected: Boolean, peerCount: Int) {
        val wasConnected = isConnected
        this.isConnected = connected && peerCount > 0
        this.connectedPeersCount = peerCount

        if (isConnected && !wasConnected) {
            stabilityTracker.onConnected()
        } else if (!isConnected && wasConnected) {
            stabilityTracker.onDisconnected()
        }

        recalculateAssessment()
    }

    /**
     * Reports RTT latency observed on RFCOMM keepalive packets.
     */
    fun reportKeepaliveRtt(rttMs: Long) {
        lastMeasuredRttMs = rttMs
        lastObservationTimestamp = System.currentTimeMillis()
        sampleCount++
        stabilityTracker.recordSuccess()
        recalculateAssessment()
    }

    /**
     * Reports socket transmission error or EOF drop.
     */
    fun reportSocketError() {
        stabilityTracker.recordFailure()
        recalculateAssessment()
    }

    @Synchronized
    private fun recalculateAssessment() {
        if (!isBluetoothEnabled) {
            _assessment.value = CommunicationPathAssessment.unavailable(transportType)
            return
        }

        val rtt = lastMeasuredRttMs ?: if (isConnected) 65L else null // Typical Bluetooth RFCOMM RTT is 40-90ms
        val latencyScore = ScoreCalculator.calculateLatencyScore(rtt)
        val lossScore = if (isConnected) 90 else 0
        val stabilityScore = stabilityTracker.calculateStabilityScore()
        val bandwidthScore = 65 // RFCOMM has limited bandwidth (~1-2 Mbps max)
        val batteryScore = 95 // Ultra-low battery consumption!

        val overallQuality = if (!isConnected) 25 else ScoreCalculator.calculateOverallScore(
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
        val bwMetric = MetricValue.Estimated(1_500, "Classic Bluetooth 2.1+ EDR", System.currentTimeMillis())

        val confidenceScore = ConfidenceCalculator.calculateConfidence(
            metrics = listOf(latencyMetric, bwMetric),
            sampleCount = sampleCount,
            lastObservationTimeMs = if (lastObservationTimestamp > 0) lastObservationTimestamp else System.currentTimeMillis()
        )

        _assessment.value = CommunicationPathAssessment(
            transportType = transportType,
            isAvailable = isBluetoothEnabled,
            isConnected = isConnected,
            overallQualityScore = overallQuality,
            latencyScore = latencyScore,
            lossScore = lossScore,
            stabilityScore = stabilityScore,
            batteryScore = batteryScore,
            bandwidthScore = bandwidthScore,
            latencyMs = latencyMetric,
            latencyVarianceMs = MetricValue.Estimated(trendAnalyzer.getSampleVariance(), "SlidingWindowVariance"),
            packetLossPercent = MetricValue.Inferred(1.0f, "RFCOMM Stream Framing"),
            bandwidthKbps = bwMetric,
            signalDbm = MetricValue.Unknown, // Continuous RFCOMM RSSI is unavailable without continuous scanning
            confidence = confidenceScore,
            confidenceLevel = ConfidenceLevel.fromScore(confidenceScore),
            trend = trend,
            roleSuitability = roleSuitability,
            batteryImpact = BatteryImpact.VERY_LOW,
            consecutiveSuccessCount = sampleCount,
            recentFailureCount = 0,
            disconnectCountLastMinute = stabilityTracker.getDisconnectCountLastMinute(),
            connectionDurationMs = stabilityTracker.getConnectionDurationMs(),
            lastStateChangeTimestamp = System.currentTimeMillis(),
            lastUpdatedTimestamp = System.currentTimeMillis()
        )
    }
}
