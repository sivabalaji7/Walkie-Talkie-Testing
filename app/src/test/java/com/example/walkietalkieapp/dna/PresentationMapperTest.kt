package com.example.walkietalkieapp.dna

import com.example.walkietalkieapp.dna.model.*
import com.example.walkietalkieapp.dna.presentation.PresentationMapper
import com.example.walkietalkieapp.dna.presentation.QualityLevel
import com.example.walkietalkieapp.dna.presentation.StrengthLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PresentationMapperTest {

    private fun createMockAssessment(
        score: Int,
        isConnected: Boolean = true,
        isAvailable: Boolean = true,
        rttMs: Long? = 25L,
        lossPercent: Float? = 0.0f,
        trend: TrendDirection = TrendDirection.STABLE
    ): CommunicationPathAssessment {
        return CommunicationPathAssessment(
            transportType = TransportType.WifiDirect,
            isAvailable = isAvailable,
            isConnected = isConnected,
            overallQualityScore = score,
            latencyScore = 95,
            lossScore = 100,
            stabilityScore = 100,
            batteryScore = 80,
            bandwidthScore = 90,
            latencyMs = rttMs?.let { MetricValue.Measured(it) } ?: MetricValue.Unknown,
            latencyVarianceMs = MetricValue.Unknown,
            packetLossPercent = lossPercent?.let { MetricValue.Measured(it) } ?: MetricValue.Unknown,
            bandwidthKbps = MetricValue.Unknown,
            signalDbm = MetricValue.Unknown,
            confidence = 0.90f,
            confidenceLevel = ConfidenceLevel.HIGH,
            trend = trend,
            roleSuitability = RoleSuitability(score, score, score),
            batteryImpact = BatteryImpact.MODERATE,
            consecutiveSuccessCount = 10,
            recentFailureCount = 0,
            disconnectCountLastMinute = 0,
            connectionDurationMs = 60_000L,
            lastStateChangeTimestamp = System.currentTimeMillis()
        )
    }

    @Test
    fun testExcellentMapping() {
        val assessment = createMockAssessment(score = 92)
        val presentation = PresentationMapper.mapToPresentation(assessment)

        assertEquals(QualityLevel.EXCELLENT, presentation.qualityLevel)
        assertEquals(StrengthLevel.FOUR, presentation.strengthLevel)
        assertEquals("Ready for voice", presentation.headlineStatus)
        assertTrue(presentation.pillBadgeText.contains("Wi-Fi Direct · Excellent"))
    }

    @Test
    fun testGoodMapping() {
        val assessment = createMockAssessment(score = 75)
        val presentation = PresentationMapper.mapToPresentation(assessment)

        assertEquals(QualityLevel.GOOD, presentation.qualityLevel)
        assertEquals(StrengthLevel.THREE, presentation.strengthLevel)
        assertEquals("Good connection", presentation.headlineStatus)
    }

    @Test
    fun testWeakMapping() {
        val assessment = createMockAssessment(score = 35)
        val presentation = PresentationMapper.mapToPresentation(assessment)

        assertEquals(QualityLevel.WEAK, presentation.qualityLevel)
        assertEquals(StrengthLevel.ONE, presentation.strengthLevel)
        assertEquals("Weak signal", presentation.headlineStatus)
    }

    @Test
    fun testOfflineMapping() {
        val assessment = createMockAssessment(score = 0, isConnected = false, isAvailable = false)
        val presentation = PresentationMapper.mapToPresentation(assessment)

        assertEquals(QualityLevel.OFFLINE, presentation.qualityLevel)
        assertEquals(StrengthLevel.ZERO, presentation.strengthLevel)
        assertEquals("Offline", presentation.headlineStatus)
        assertTrue(presentation.pillBadgeText.contains("Offline"))
    }

    @Test
    fun testHumanExplanationForHighLatency() {
        val assessment = createMockAssessment(score = 55, rttMs = 450L)
        val presentation = PresentationMapper.mapToPresentation(assessment)

        assertEquals("Slight voice delay possible", presentation.supportingMessage)
    }

    @Test
    fun testHumanExplanationForDegradingTrend() {
        val assessment = createMockAssessment(score = 65, trend = TrendDirection.DEGRADING)
        val presentation = PresentationMapper.mapToPresentation(assessment)

        assertEquals("Connection is weakening", presentation.stabilitySummary)
    }
}
