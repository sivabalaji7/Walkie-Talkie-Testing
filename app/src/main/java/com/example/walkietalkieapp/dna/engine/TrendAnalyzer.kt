package com.example.walkietalkieapp.dna.engine

import com.example.walkietalkieapp.dna.model.TrendDirection
import java.util.concurrent.ConcurrentLinkedDeque
import kotlin.math.sqrt

/**
 * Time-aware trend analyzer using bounded sliding window regression and variance.
 * Identifies directional drift (IMPROVING / DEGRADING) and high-frequency instability (VOLATILE).
 */
class TrendAnalyzer(
    private val maxWindowSize: Int = 10,
    private val maxWindowDurationMs: Long = 60_000L
) {
    data class Sample(val score: Int, val timestamp: Long)

    private val samples = ConcurrentLinkedDeque<Sample>()

    fun recordSample(score: Int, timestamp: Long = System.currentTimeMillis()): TrendDirection {
        samples.addLast(Sample(score, timestamp))
        prune(timestamp)
        return evaluateTrend()
    }

    fun clear() {
        samples.clear()
    }

    private fun prune(now: Long) {
        while (samples.size > maxWindowSize || (samples.peekFirst()?.let { now - it.timestamp > maxWindowDurationMs } == true)) {
            samples.pollFirst()
        }
    }

    fun evaluateTrend(): TrendDirection {
        val list = samples.toList()
        if (list.size < 3) return TrendDirection.UNKNOWN

        // 1. Calculate Standard Deviation to detect Volatility
        val scores = list.map { it.score.toDouble() }
        val mean = scores.average()
        val variance = scores.map { (it - mean) * (it - mean) }.average()
        val stdDev = sqrt(variance)

        if (stdDev > 18.0) {
            return TrendDirection.VOLATILE
        }

        // 2. Linear slope estimation (Points per Minute)
        val first = list.first()
        val last = list.last()
        val timeSpanSeconds = (last.timestamp - first.timestamp) / 1000.0

        if (timeSpanSeconds < 3.0) return TrendDirection.STABLE

        val scoreDelta = last.score - first.score
        val ratePerMinute = (scoreDelta / timeSpanSeconds) * 60.0

        return when {
            ratePerMinute >= 10.0 -> TrendDirection.IMPROVING
            ratePerMinute <= -10.0 -> TrendDirection.DEGRADING
            else -> TrendDirection.STABLE
        }
    }

    fun getSampleVariance(): Double {
        val list = samples.toList()
        if (list.size < 2) return 0.0
        val scores = list.map { it.score.toDouble() }
        val mean = scores.average()
        return scores.map { (it - mean) * (it - mean) }.average()
    }
}
