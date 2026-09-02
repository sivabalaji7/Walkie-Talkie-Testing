package com.example.walkietalkieapp.dna

import com.example.walkietalkieapp.dna.engine.ConfidenceCalculator
import com.example.walkietalkieapp.dna.engine.StabilityTracker
import com.example.walkietalkieapp.dna.model.MetricValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfidenceAndStabilityTest {

    @Test
    fun testConfidenceCalculation() {
        val now = 100_000L
        val freshMetrics = listOf(
            MetricValue.Measured(25L, now),
            MetricValue.Measured(0.1f, now),
            MetricValue.Estimated(1000, "Method", now)
        )

        // Fresh data + target samples -> High confidence
        val highConf = ConfidenceCalculator.calculateConfidence(
            metrics = freshMetrics,
            sampleCount = 6,
            lastObservationTimeMs = now,
            currentTimeMs = now
        )
        assertTrue("Expected high confidence but was $highConf", highConf > 0.80f)

        // Old data (35 seconds later) -> Confidence significantly degrades
        val decayedConf = ConfidenceCalculator.calculateConfidence(
            metrics = freshMetrics,
            sampleCount = 6,
            lastObservationTimeMs = now,
            currentTimeMs = now + 35_000L
        )
        assertTrue("Expected decayed confidence but was $decayedConf", decayedConf < highConf)
    }

    @Test
    fun testStabilityTrackerFlappingPenalty() {
        val tracker = StabilityTracker()
        val now = 100_000L

        tracker.onConnected(now)
        assertEquals(100, tracker.calculateStabilityScore(now))

        // Multiple rapid disconnects within 60s
        tracker.onDisconnected(now + 5000L)
        tracker.onConnected(now + 10000L)
        tracker.onDisconnected(now + 15000L)
        tracker.onConnected(now + 20000L)
        tracker.onDisconnected(now + 25000L)

        val flappingScore = tracker.calculateStabilityScore(now + 30000L)
        assertTrue("Expected low score due to 3 disconnects but was $flappingScore", flappingScore <= 35)
    }
}
