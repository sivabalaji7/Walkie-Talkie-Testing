package com.example.walkietalkieapp.dna.presentation

import androidx.compose.ui.graphics.Color
import com.example.walkietalkieapp.dna.model.CommunicationPathAssessment
import com.example.walkietalkieapp.dna.model.TrendDirection

/**
 * Centralized, deterministic mapper converting raw Communication DNA intelligence
 * into user-friendly semantic presentation states.
 */
object PresentationMapper {

    fun mapToPresentation(assessment: CommunicationPathAssessment): CommunicationPresentationState {
        val transportName = assessment.transportType.displayName
        val isConnected = assessment.isConnected && assessment.isAvailable
        val score = assessment.overallQualityScore

        val (qualityLevel, strengthLevel, accentColor) = when {
            !assessment.isAvailable || !assessment.isConnected -> {
                Triple(QualityLevel.OFFLINE, StrengthLevel.ZERO, Color.Gray)
            }
            score >= 85 -> {
                Triple(QualityLevel.EXCELLENT, StrengthLevel.FOUR, Color(0xFF00FF66))
            }
            score >= 65 -> {
                Triple(QualityLevel.GOOD, StrengthLevel.THREE, Color(0xFF00E5FF))
            }
            score >= 45 -> {
                Triple(QualityLevel.FAIR, StrengthLevel.TWO, Color(0xFFFFA000))
            }
            score >= 20 -> {
                Triple(QualityLevel.WEAK, StrengthLevel.ONE, Color(0xFFFF8A80))
            }
            else -> {
                Triple(QualityLevel.CRITICAL, StrengthLevel.ONE, Color(0xFFFF5252))
            }
        }

        val headlineStatus = when (qualityLevel) {
            QualityLevel.EXCELLENT -> "Ready for voice"
            QualityLevel.GOOD -> "Good connection"
            QualityLevel.FAIR -> "Fair connection"
            QualityLevel.WEAK -> "Weak signal"
            QualityLevel.CRITICAL -> "Unstable connection"
            QualityLevel.OFFLINE -> if (assessment.isAvailable) "Connecting..." else "Offline"
        }

        val stabilitySummary = when (assessment.trend) {
            TrendDirection.IMPROVING -> "Connection is strengthening"
            TrendDirection.DEGRADING -> "Connection is weakening"
            TrendDirection.VOLATILE -> "Connection is fluctuating"
            TrendDirection.STABLE -> if (isConnected) "Connection is stable" else "Channel standby"
            TrendDirection.UNKNOWN -> if (isConnected) "Connection active" else "No connection"
        }

        val supportingMessage = when {
            !isConnected -> "No active communication path"
            (assessment.latencyMs.valueOrNull() ?: 0L) > 400L -> "Slight voice delay possible"
            (assessment.packetLossPercent.valueOrNull() ?: 0f) > 5.0f -> "Some voice glitches possible"
            qualityLevel == QualityLevel.EXCELLENT -> "Voice communication is optimal"
            qualityLevel == QualityLevel.GOOD -> "Voice communication should be smooth"
            qualityLevel == QualityLevel.FAIR -> "Minor audio interruptions possible"
            qualityLevel == QualityLevel.WEAK -> "Audio quality may be affected"
            else -> "Trying to maintain connection"
        }

        val shortTransportLabel = when (assessment.transportType.id) {
            "INTERNET" -> "Internet"
            "WIFI_DIRECT" -> "Wi-Fi Direct"
            "BLUETOOTH" -> "Bluetooth"
            else -> assessment.transportType.displayName
        }

        val pillBadgeText = if (isConnected) {
            "$shortTransportLabel · ${qualityLevel.label}"
        } else if (assessment.isAvailable) {
            "$shortTransportLabel · Connecting"
        } else {
            "$shortTransportLabel · Offline"
        }

        val accessibilityDescription = "$shortTransportLabel. ${qualityLevel.label} connection. $stabilitySummary. $headlineStatus."

        return CommunicationPresentationState(
            transportType = assessment.transportType,
            transportName = transportName,
            isConnected = isConnected,
            isAvailable = assessment.isAvailable,
            qualityLevel = qualityLevel,
            strengthLevel = strengthLevel,
            confidenceLevel = assessment.confidenceLevel,
            headlineStatus = headlineStatus,
            supportingMessage = supportingMessage,
            stabilitySummary = stabilitySummary,
            pillBadgeText = pillBadgeText,
            primaryAccentColor = accentColor,
            accessibilityDescription = accessibilityDescription,
            rawAssessment = assessment
        )
    }
}
