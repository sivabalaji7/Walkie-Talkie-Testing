package com.example.walkietalkieapp.dna.adapter

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import com.example.walkietalkieapp.dna.engine.ConfidenceCalculator
import com.example.walkietalkieapp.dna.engine.ScoreCalculator
import com.example.walkietalkieapp.dna.engine.StabilityTracker
import com.example.walkietalkieapp.dna.engine.TrendAnalyzer
import com.example.walkietalkieapp.dna.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.math.min

/**
 * DNA probe adapter for Internet communications (Cellular & Wi-Fi Internet).
 * Observes Android ConnectivityManager, validates reachability via lightweight socket pings,
 * and tracks stability, loss, and latency variance.
 */
class InternetDnaAdapter(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) : TransportProbeAdapter {

    override val transportType: TransportType = TransportType.Internet

    private val _assessment = MutableStateFlow(CommunicationPathAssessment.unavailable(transportType))
    override val assessment: StateFlow<CommunicationPathAssessment> = _assessment.asStateFlow()

    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    private val stabilityTracker = StabilityTracker()
    private val trendAnalyzer = TrendAnalyzer()

    private var probeJob: Job? = null
    private var isRegistered = false
    private var isVoiceActive = false

    private var activeNetwork: Network? = null
    private var isNetworkValidated = false
    private var isCellular = false
    private var isWifi = false
    private var upstreamBandwidthKbps: Int? = null
    private var signalDbm: Int? = null

    private var lastMeasuredRttMs: Long? = null
    private var lastMeasuredLossPercent: Float? = null
    private var sampleCount = 0
    private var lastObservationTimestamp: Long = 0L

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            activeNetwork = network
            stabilityTracker.onConnected()
            recalculateAssessment()
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            activeNetwork = network
            isNetworkValidated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            isCellular = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
            isWifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            upstreamBandwidthKbps = capabilities.linkUpstreamBandwidthKbps.takeIf { it > 0 }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                signalDbm = capabilities.signalStrength.takeIf { it != NetworkCapabilities.SIGNAL_STRENGTH_UNSPECIFIED }
            }
            recalculateAssessment()
        }

        override fun onLost(network: Network) {
            if (activeNetwork == network) {
                activeNetwork = null
                isNetworkValidated = false
                stabilityTracker.onDisconnected()
                recalculateAssessment()
            }
        }

        override fun onUnavailable() {
            activeNetwork = null
            stabilityTracker.onDisconnected()
            recalculateAssessment()
        }
    }

    override fun startProbing() {
        if (isRegistered) return
        isRegistered = true

        try {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager?.registerNetworkCallback(request, networkCallback)
        } catch (e: Exception) {
            // Defensive: Handle platform security or configuration exceptions
        }

        startSamplingLoop()
    }

    override fun stopProbing() {
        if (!isRegistered) return
        isRegistered = false
        probeJob?.cancel()
        try {
            connectivityManager?.unregisterNetworkCallback(networkCallback)
        } catch (e: Exception) {
            // Ignore unregister exceptions on shutdown
        }
        stabilityTracker.reset()
        trendAnalyzer.clear()
        _assessment.value = CommunicationPathAssessment.unavailable(transportType)
    }

    override fun onAudioSessionStateChanged(isTransmittingOrReceiving: Boolean) {
        this.isVoiceActive = isTransmittingOrReceiving
        // Restart sampling with adjusted frequency
        startSamplingLoop()
    }

    /**
     * Directly report RTT ping from SupabaseRealtimeManager or WebRTC stats.
     */
    fun reportSocketPing(rttMs: Long) {
        lastMeasuredRttMs = rttMs
        sampleCount++
        lastObservationTimestamp = System.currentTimeMillis()
        stabilityTracker.recordSuccess()
        recalculateAssessment()
    }

    /**
     * Directly report Packet Loss from WebRTC RTCInboundRtpStreamStats.
     */
    fun reportRtcStats(lossPercent: Float, jitterMs: Double?) {
        lastMeasuredLossPercent = lossPercent
        sampleCount++
        lastObservationTimestamp = System.currentTimeMillis()
        recalculateAssessment()
    }

    private fun startSamplingLoop() {
        probeJob?.cancel()
        probeJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                if (activeNetwork != null) {
                    measureReachabilityAndLatency()
                }
                // Adaptive interval: 4s when voice is active, 20s when idle
                val intervalMs = if (isVoiceActive) 4_000L else 20_000L
                delay(intervalMs)
            }
        }
    }

    private suspend fun measureReachabilityAndLatency() {
        withContext(Dispatchers.IO) {
            val start = System.currentTimeMillis()
            var success = false
            try {
                // Lightweight TCP connect probe to Google DNS (8.8.8.8:53) with 1500ms timeout
                Socket().use { socket ->
                    socket.connect(InetSocketAddress("8.8.8.8", 53), 1500)
                    val rtt = System.currentTimeMillis() - start
                    lastMeasuredRttMs = rtt
                    lastMeasuredLossPercent = 0.0f
                    sampleCount++
                    lastObservationTimestamp = System.currentTimeMillis()
                    stabilityTracker.recordSuccess()
                    success = true
                }
            } catch (e: Exception) {
                stabilityTracker.recordFailure()
            }
            recalculateAssessment()
        }
    }

    @Synchronized
    private fun recalculateAssessment() {
        val hasNet = activeNetwork != null
        if (!hasNet) {
            _assessment.value = CommunicationPathAssessment.unavailable(transportType)
            return
        }

        val rtt = lastMeasuredRttMs
        val loss = lastMeasuredLossPercent
        val bw = upstreamBandwidthKbps

        val latencyScore = ScoreCalculator.calculateLatencyScore(rtt)
        val lossScore = ScoreCalculator.calculateLossScore(loss)
        val stabilityScore = stabilityTracker.calculateStabilityScore()
        val bandwidthScore = ScoreCalculator.calculateBandwidthScore(bw)
        val batteryScore = if (isCellular) 70 else 85

        val overallQuality = ScoreCalculator.calculateOverallScore(
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

        val metricsList = mutableListOf<MetricValue<*>>()
        val latencyMetric = rtt?.let { MetricValue.Measured(it, lastObservationTimestamp) } ?: MetricValue.Unknown
        val lossMetric = loss?.let { MetricValue.Measured(it, lastObservationTimestamp) } ?: MetricValue.Unknown
        val bwMetric = bw?.let { MetricValue.Estimated(it, "Android LinkProperties", System.currentTimeMillis()) } ?: MetricValue.Unknown
        val sigMetric = signalDbm?.let { MetricValue.Measured(it, System.currentTimeMillis()) } ?: MetricValue.Unknown

        metricsList.add(latencyMetric)
        metricsList.add(lossMetric)
        if (bwMetric is MetricValue.Estimated) metricsList.add(bwMetric)

        val confidenceScore = ConfidenceCalculator.calculateConfidence(
            metrics = metricsList,
            sampleCount = sampleCount,
            lastObservationTimeMs = if (lastObservationTimestamp > 0) lastObservationTimestamp else System.currentTimeMillis()
        )

        val batteryImpact = if (isCellular) BatteryImpact.HIGH else BatteryImpact.MODERATE

        _assessment.value = CommunicationPathAssessment(
            transportType = transportType,
            isAvailable = true,
            isConnected = isNetworkValidated || rtt != null,
            overallQualityScore = overallQuality,
            latencyScore = latencyScore,
            lossScore = lossScore,
            stabilityScore = stabilityScore,
            batteryScore = batteryScore,
            bandwidthScore = bandwidthScore,
            latencyMs = latencyMetric,
            latencyVarianceMs = MetricValue.Estimated(trendAnalyzer.getSampleVariance(), "SlidingWindowVariance"),
            packetLossPercent = lossMetric,
            bandwidthKbps = bwMetric,
            signalDbm = sigMetric,
            confidence = confidenceScore,
            confidenceLevel = ConfidenceLevel.fromScore(confidenceScore),
            trend = trend,
            roleSuitability = roleSuitability,
            batteryImpact = batteryImpact,
            consecutiveSuccessCount = sampleCount,
            recentFailureCount = 0,
            disconnectCountLastMinute = stabilityTracker.getDisconnectCountLastMinute(),
            connectionDurationMs = stabilityTracker.getConnectionDurationMs(),
            lastStateChangeTimestamp = System.currentTimeMillis(),
            lastUpdatedTimestamp = System.currentTimeMillis()
        )
    }
}
