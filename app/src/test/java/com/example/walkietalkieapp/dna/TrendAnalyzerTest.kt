package com.example.walkietalkieapp.dna

import com.example.walkietalkieapp.dna.engine.TrendAnalyzer
import com.example.walkietalkieapp.dna.model.TrendDirection
import org.junit.Assert.assertEquals
import org.junit.Test

class TrendAnalyzerTest {

    @Test
    fun testInsufficientDataGivesUnknown() {
        val analyzer = TrendAnalyzer()
        assertEquals(TrendDirection.UNKNOWN, analyzer.evaluateTrend())
        analyzer.recordSample(80, 1000L)
        analyzer.recordSample(82, 2000L)
        assertEquals(TrendDirection.UNKNOWN, analyzer.evaluateTrend())
    }

    @Test
    fun testStableSequence() {
        val analyzer = TrendAnalyzer()
        val now = 100_000L
        analyzer.recordSample(85, now)
        analyzer.recordSample(86, now + 2000L)
        analyzer.recordSample(85, now + 4000L)
        analyzer.recordSample(85, now + 6000L)

        assertEquals(TrendDirection.STABLE, analyzer.evaluateTrend())
    }

    @Test
    fun testImprovingSequence() {
        val analyzer = TrendAnalyzer()
        val now = 100_000L
        analyzer.recordSample(50, now)
        analyzer.recordSample(65, now + 2000L)
        analyzer.recordSample(78, now + 4000L)
        analyzer.recordSample(90, now + 6000L)

        assertEquals(TrendDirection.IMPROVING, analyzer.evaluateTrend())
    }

    @Test
    fun testDegradingSequence() {
        val analyzer = TrendAnalyzer()
        val now = 100_000L
        analyzer.recordSample(90, now)
        analyzer.recordSample(75, now + 2000L)
        analyzer.recordSample(60, now + 4000L)
        analyzer.recordSample(45, now + 6000L)

        assertEquals(TrendDirection.DEGRADING, analyzer.evaluateTrend())
    }

    @Test
    fun testVolatileSequence() {
        val analyzer = TrendAnalyzer()
        val now = 100_000L
        analyzer.recordSample(95, now)
        analyzer.recordSample(25, now + 2000L)
        analyzer.recordSample(90, now + 4000L)
        analyzer.recordSample(20, now + 6000L)
        analyzer.recordSample(95, now + 8000L)

        assertEquals(TrendDirection.VOLATILE, analyzer.evaluateTrend())
    }
}
