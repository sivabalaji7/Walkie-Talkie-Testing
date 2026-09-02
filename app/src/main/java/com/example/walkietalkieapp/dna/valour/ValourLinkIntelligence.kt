package com.example.walkietalkieapp.dna.valour

import androidx.compose.ui.graphics.Color
import com.example.walkietalkieapp.dna.engine.CommunicationDnaEngine
import com.example.walkietalkieapp.dna.model.CommunicationPathAssessment
import com.example.walkietalkieapp.dna.model.TransportType
import com.example.walkietalkieapp.dna.model.TrendDirection
import com.example.walkietalkieapp.dna.presentation.StrengthLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*

/**
 * Valour Link Intelligence 2.0
 * Unified source of truth coordinating transport-agnostic link intelligence across the entire app.
 */
object ValourLinkIntelligence {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val _activeTransportType = MutableStateFlow<TransportType>(TransportType.Internet)
    val activeTransportType: StateFlow<TransportType> = _activeTransportType.asStateFlow()

    val allLinksState: StateFlow<Map<TransportType, ValourLinkState>> = CommunicationDnaEngine.assessments
        .map { assessmentsMap ->
            val result = mutableMapOf<TransportType, ValourLinkState>()
            
            // Map standard transports
            val standardTransports = listOf(TransportType.Internet, TransportType.Bluetooth, TransportType.WifiDirect)
            standardTransports.forEach { type ->
                val assessment = assessmentsMap[type] ?: CommunicationPathAssessment.unavailable(type)
                result[type] = mapToValourLinkState(assessment)
            }
            
            // Map any custom/relay transports
            assessmentsMap.forEach { (type, assessment) ->
                if (type !in standardTransports) {
                    result[type] = mapToValourLinkState(assessment)
                }
            }
            result
        }
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = emptyMap()
        )

    val activeLinkState: StateFlow<ValourLinkState> = combine(
        allLinksState,
        _activeTransportType
    ) { allLinks, activeType ->
        allLinks[activeType] ?: mapToValourLinkState(CommunicationPathAssessment.unavailable(activeType))
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = mapToValourLinkState(CommunicationPathAssessment.unavailable(TransportType.Internet))
    )

    fun setActiveTransport(transportType: TransportType) {
        _activeTransportType.value = transportType
    }

    /**
     * Centralized, deterministic translation of raw DNA assessment into Valour Link State.
     */
    fun mapToValourLinkState(assessment: CommunicationPathAssessment): ValourLinkState {
        val transportName = assessment.transportType.displayName
        val isConnected = assessment.isConnected && assessment.isAvailable
        val isAvailable = assessment.isAvailable
        val score = assessment.overallQualityScore
        val voiceScore = assessment.roleSuitability.voiceScore

        val (connectionState, qualityLevel, strengthLevel, accentColor) = when {
            !isAvailable -> {
                Quad(LinkConnectionState.OFFLINE, LinkQualityLevel.OFFLINE, StrengthLevel.ZERO, Color.Gray)
            }
            !isConnected -> {
                Quad(LinkConnectionState.CONNECTING, LinkQualityLevel.CONNECTING, StrengthLevel.ONE, Color(0xFFFFA000))
            }
            score >= 85 -> {
                Quad(LinkConnectionState.CONNECTED, LinkQualityLevel.EXCELLENT, StrengthLevel.FOUR, Color(0xFF00FF66))
            }
            score >= 65 -> {
                Quad(LinkConnectionState.CONNECTED, LinkQualityLevel.GOOD, StrengthLevel.THREE, Color(0xFF00E5FF))
            }
            score >= 45 -> {
                Quad(LinkConnectionState.CONNECTED, LinkQualityLevel.FAIR, StrengthLevel.TWO, Color(0xFFFFA000))
            }
            score >= 20 -> {
                Quad(LinkConnectionState.CONNECTED, LinkQualityLevel.WEAK, StrengthLevel.ONE, Color(0xFFFF8A80))
            }
            else -> {
                Quad(LinkConnectionState.CONNECTED, LinkQualityLevel.CRITICAL, StrengthLevel.ONE, Color(0xFFFF5252))
            }
        }

        val voiceReadiness = when {
            !isConnected -> VoiceReadiness.UNAVAILABLE
            voiceScore >= 85 -> VoiceReadiness.OPTIMAL
            voiceScore >= 65 -> VoiceReadiness.GOOD
            voiceScore >= 45 -> VoiceReadiness.FAIR
            else -> VoiceReadiness.DEGRADED
        }

        val stability = when (assessment.trend) {
            TrendDirection.IMPROVING -> LinkStability.STRENGTHENING
            TrendDirection.DEGRADING -> LinkStability.WEAKENING
            TrendDirection.VOLATILE -> LinkStability.FLUCTUATING
            TrendDirection.STABLE -> if (isConnected) LinkStability.STABLE else LinkStability.STANDBY
            TrendDirection.UNKNOWN -> if (isConnected) LinkStability.STABLE else LinkStability.STANDBY
        }

        val headlineText = when {
            !isAvailable -> "Offline"
            !isConnected -> "Connecting..."
            qualityLevel == LinkQualityLevel.EXCELLENT -> "Ready for voice"
            qualityLevel == LinkQualityLevel.GOOD -> "Good connection"
            qualityLevel == LinkQualityLevel.FAIR -> "Fair connection"
            qualityLevel == LinkQualityLevel.WEAK -> "Weak signal"
            else -> "Unstable connection"
        }

        val subheadText = when {
            !isConnected -> "No active communication path"
            (assessment.latencyMs.valueOrNull() ?: 0L) > 400L -> "Slight voice delay possible"
            (assessment.packetLossPercent.valueOrNull() ?: 0f) > 5.0f -> "Some voice glitches possible"
            qualityLevel == LinkQualityLevel.EXCELLENT -> "Voice communication is optimal"
            qualityLevel == LinkQualityLevel.GOOD -> "Voice communication should be smooth"
            qualityLevel == LinkQualityLevel.FAIR -> "Minor audio latency possible"
            else -> "Audio quality may be impaired"
        }

        val shortTransportLabel = when (assessment.transportType.id) {
            "INTERNET" -> "Internet"
            "WIFI_DIRECT" -> "Wi-Fi Direct"
            "BLUETOOTH" -> "Bluetooth"
            else -> assessment.transportType.displayName
        }

        val compactPillText = when {
            !isAvailable -> "$shortTransportLabel · Offline"
            !isConnected -> "$shortTransportLabel · Connecting"
            else -> "$shortTransportLabel · ${qualityLevel.label}"
        }

        val accessibilityDescription = "$shortTransportLabel connection, ${qualityLevel.label.lowercase()}, ${stability.message.lowercase()}."

        val diagnostics = LinkDiagnostics(
            dnaScore = assessment.overallQualityScore,
            rttMs = assessment.latencyMs.valueOrNull(),
            packetLossPercent = assessment.packetLossPercent.valueOrNull(),
            bandwidthKbps = assessment.bandwidthKbps.valueOrNull(),
            stabilityScore = assessment.stabilityScore,
            confidenceLevel = assessment.confidenceLevel.label,
            trendDescription = assessment.trend.description,
            batteryImpact = assessment.batteryImpact.label,
            disconnectCountLastMinute = assessment.disconnectCountLastMinute
        )

        return ValourLinkState(
            transportType = assessment.transportType,
            transportName = transportName,
            connectionState = connectionState,
            qualityLevel = qualityLevel,
            strengthLevel = strengthLevel,
            voiceReadiness = voiceReadiness,
            voiceReadinessScore = voiceScore,
            headlineText = headlineText,
            subheadText = subheadText,
            compactPillText = compactPillText,
            accessibilityDescription = accessibilityDescription,
            accentColor = accentColor,
            diagnostics = diagnostics,
            rawAssessment = assessment
        )
    }

    private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
}
