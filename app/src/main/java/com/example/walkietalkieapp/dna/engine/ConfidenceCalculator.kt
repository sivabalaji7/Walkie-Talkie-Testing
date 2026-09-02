package com.example.walkietalkieapp.dna.engine

import com.example.walkietalkieapp.dna.model.MetricValue
import kotlin.math.max
import kotlin.math.min

/**
 * Evaluates the mathematical confidence score [0.0 .. 1.0] of a transport assessment.
 * Prevents false precision by penalizing sparse data, stale observations, and inferred approximations.
 */
object ConfidenceCalculator {

    private const val MAX_STALENESS_MS = 45_000L
    private const val TARGET_SAMPLE_COUNT = 6

    fun calculateConfidence(
        metrics: List<MetricValue<*>>,
        sampleCount: Int,
        lastObservationTimeMs: Long,
        currentTimeMs: Long = System.currentTimeMillis()
    ): Float {
        if (metrics.isEmpty()) return 0.0f

        // 1. Freshness Factor (Decays to 0 after 45 seconds of no new data)
        val ageMs = max(0L, currentTimeMs - lastObservationTimeMs)
        val freshnessFactor = max(0.0f, 1.0f - (ageMs.toFloat() / MAX_STALENESS_MS))

        // 2. Sample Count Factor (Ramps up to 1.0 at TARGET_SAMPLE_COUNT)
        val sampleCountFactor = min(1.0f, sampleCount.toFloat() / TARGET_SAMPLE_COUNT)

        // 3. Directness Factor (Average directness across provided metrics)
        val directnessSum = metrics.sumOf { metric ->
            when (metric) {
                is MetricValue.Measured -> 1.0
                is MetricValue.Estimated -> 0.75
                is MetricValue.Inferred -> 0.45
                is MetricValue.Unknown -> 0.0
            }
        }
        val directnessFactor = (directnessSum / metrics.size).toFloat()

        // Weighted confidence formulation
        val confidence = 0.40f * freshnessFactor +
                         0.35f * directnessFactor +
                         0.25f * sampleCountFactor

        return confidence.coerceIn(0.0f, 1.0f)
    }
}
