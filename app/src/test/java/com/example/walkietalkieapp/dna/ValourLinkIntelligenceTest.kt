package com.example.walkietalkieapp.dna

import com.example.walkietalkieapp.dna.model.*
import com.example.walkietalkieapp.dna.presentation.StrengthLevel
import com.example.walkietalkieapp.dna.valour.LinkConnectionState
import com.example.walkietalkieapp.dna.valour.LinkQualityLevel
import com.example.walkietalkieapp.dna.valour.ValourLinkIntelligence
import com.example.walkietalkieapp.dna.valour.VoiceReadiness
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ValourLinkIntelligenceTest {

    private fun createAssessment(
        score: Int,
        voiceScore: Int = score,
        isConnected: Boolean = true,
        isAvailable: Boolean = true,
        rttMs: Long? = 28L,
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
            roleSuitability = RoleSuitability(voiceScore, score, score),
            batteryImpact = BatteryImpact.MODERATE,
            consecutiveSuccessCount = 10,
            recentFailureCount = 0,
            disconnectCountLastMinute = 0,
            connectionDurationMs = 60_000L,
            lastStateChangeTimestamp = System.currentTimeMillis()
        )
    }

    @Test
    fun testExcellentLinkStateMapping() {
        val assessment = createAssessment(score = 90, voiceScore = 95)
        val linkState = ValourLinkIntelligence.mapToValourLinkState(assessment)

        assertEquals(LinkQualityLevel.EXCELLENT, linkState.qualityLevel)
        assertEquals(StrengthLevel.FOUR, linkState.strengthLevel)
        assertEquals(LinkConnectionState.CONNECTED, linkState.connectionState)
        assertEquals(VoiceReadiness.OPTIMAL, linkState.voiceReadiness)
        assertEquals("Ready for voice", linkState.headlineText)
        assertTrue(linkState.compactPillText.contains("Wi-Fi Direct · Excellent"))
    }

    @Test
    fun testGoodLinkStateMapping() {
        val assessment = createAssessment(score = 75, voiceScore = 70)
        val linkState = ValourLinkIntelligence.mapToValourLinkState(assessment)

        assertEquals(LinkQualityLevel.GOOD, linkState.qualityLevel)
        assertEquals(StrengthLevel.THREE, linkState.strengthLevel)
        assertEquals(VoiceReadiness.GOOD, linkState.voiceReadiness)
        assertEquals("Good connection", linkState.headlineText)
    }

    @Test
    fun testConnectingLinkStateMapping() {
        val assessment = createAssessment(score = 0, isConnected = false, isAvailable = true)
        val linkState = ValourLinkIntelligence.mapToValourLinkState(assessment)

        assertEquals(LinkQualityLevel.CONNECTING, linkState.qualityLevel)
        assertEquals(LinkConnectionState.CONNECTING, linkState.connectionState)
        assertEquals(VoiceReadiness.UNAVAILABLE, linkState.voiceReadiness)
        assertEquals("Connecting...", linkState.headlineText)
        assertTrue(linkState.compactPillText.contains("Connecting"))
    }

    @Test
    fun testOfflineLinkStateMapping() {
        val assessment = createAssessment(score = 0, isConnected = false, isAvailable = false)
        val linkState = ValourLinkIntelligence.mapToValourLinkState(assessment)

        assertEquals(LinkQualityLevel.OFFLINE, linkState.qualityLevel)
        assertEquals(LinkConnectionState.OFFLINE, linkState.connectionState)
        assertEquals(StrengthLevel.ZERO, linkState.strengthLevel)
        assertEquals(VoiceReadiness.UNAVAILABLE, linkState.voiceReadiness)
        assertEquals("Offline", linkState.headlineText)
        assertTrue(linkState.compactPillText.contains("Offline"))
    }

    @Test
    fun testDiagnosticsPreservation() {
        val assessment = createAssessment(score = 88, rttMs = 42L, lossPercent = 1.2f)
        val linkState = ValourLinkIntelligence.mapToValourLinkState(assessment)

        assertEquals(88, linkState.diagnostics.dnaScore)
        assertEquals(42L, linkState.diagnostics.rttMs)
        assertEquals(1.2f, linkState.diagnostics.packetLossPercent)
    }
}
