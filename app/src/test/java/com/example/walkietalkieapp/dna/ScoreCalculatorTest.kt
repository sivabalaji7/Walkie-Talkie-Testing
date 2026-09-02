package com.example.walkietalkieapp.dna

import com.example.walkietalkieapp.dna.engine.ScoreCalculator
import com.example.walkietalkieapp.dna.model.CommunicationMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoreCalculatorTest {

    @Test
    fun testLatencyScoreBoundaries() {
        assertEquals(100, ScoreCalculator.calculateLatencyScore(20L))
        assertEquals(100, ScoreCalculator.calculateLatencyScore(30L))
        
        // 165ms should be roughly halfway between 100 and 30 (around 65)
        val midScore = ScoreCalculator.calculateLatencyScore(165L)
        assertTrue(midScore in 60..70)

        assertEquals(30, ScoreCalculator.calculateLatencyScore(300L))
        
        // 650ms should be roughly halfway between 30 and 0 (around 15)
        val highLatencyScore = ScoreCalculator.calculateLatencyScore(650L)
        assertTrue(highLatencyScore in 10..20)

        assertEquals(0, ScoreCalculator.calculateLatencyScore(1000L))
        assertEquals(0, ScoreCalculator.calculateLatencyScore(2500L))
    }

    @Test
    fun testPacketLossScoreBoundaries() {
        assertEquals(100, ScoreCalculator.calculateLossScore(0.0f))
        assertEquals(100, ScoreCalculator.calculateLossScore(0.5f))
        
        val midLoss = ScoreCalculator.calculateLossScore(2.75f)
        assertTrue(midLoss in 75..85)

        assertEquals(60, ScoreCalculator.calculateLossScore(5.0f))
        
        val highLoss = ScoreCalculator.calculateLossScore(12.5f)
        assertTrue(highLoss in 25..35)

        assertEquals(0, ScoreCalculator.calculateLossScore(20.0f))
        assertEquals(0, ScoreCalculator.calculateLossScore(50.0f))
    }

    @Test
    fun testModeSpecificWeights() {
        // High latency (Score 30) but zero loss (Score 100) and good stability (Score 90)
        val voiceScore = ScoreCalculator.calculateOverallScore(
            latencyScore = 30,
            lossScore = 100,
            stabilityScore = 90,
            batteryScore = 80,
            bandwidthScore = 80,
            mode = CommunicationMode.VOICE_PTT
        )

        val textScore = ScoreCalculator.calculateOverallScore(
            latencyScore = 30,
            lossScore = 100,
            stabilityScore = 90,
            batteryScore = 80,
            bandwidthScore = 80,
            mode = CommunicationMode.TEXT_MESSAGING
        )

        // Text messaging should score higher than voice because text is less sensitive to latency
        assertTrue(textScore > voiceScore)
    }
}
