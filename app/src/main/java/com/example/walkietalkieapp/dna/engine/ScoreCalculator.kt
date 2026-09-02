package com.example.walkietalkieapp.dna.engine

import com.example.walkietalkieapp.dna.model.CommunicationMode
import com.example.walkietalkieapp.dna.model.RoleSuitability
import kotlin.math.max
import kotlin.math.min

/**
 * Pure, deterministic mathematical calculator for normalizing network quality metrics to 0–100 scores.
 * All formulas are bounded, explainable, and testable without Android framework dependencies.
 */
object ScoreCalculator {

    /**
     * Calculates Latency Score (0–100) based on Round-Trip-Time (RTT).
     * <= 30ms -> 100 (Exceptional)
     * 30ms..300ms -> 100..30 (Acceptable for PTT voice)
     * 300ms..1000ms -> 30..0 (Degraded/High Delay)
     * > 1000ms -> 0 (Unusable for real-time voice)
     */
    fun calculateLatencyScore(rttMs: Long?): Int {
        if (rttMs == null || rttMs < 0) return 50 // Default neutral if unknown but connected
        return when {
            rttMs <= 30L -> 100
            rttMs <= 300L -> {
                val fraction = (rttMs - 30.0) / (300.0 - 30.0)
                (100.0 - fraction * 70.0).toInt().coerceIn(30, 100)
            }
            rttMs <= 1000L -> {
                val fraction = (rttMs - 300.0) / (1000.0 - 300.0)
                (30.0 - fraction * 30.0).toInt().coerceIn(0, 30)
            }
            else -> 0
        }
    }

    /**
     * Calculates Packet Loss Score (0–100).
     * <= 0.5% -> 100 (Near perfect)
     * 0.5%..5.0% -> 100..60 (Handled seamlessly by Opus in-band FEC)
     * 5.0%..20.0% -> 60..0 (Audible robotic packet drop / glitching)
     * > 20.0% -> 0 (Severely impaired voice channel)
     */
    fun calculateLossScore(packetLossPercent: Float?): Int {
        if (packetLossPercent == null || packetLossPercent < 0f) return 80 // Default reasonable if unknown
        return when {
            packetLossPercent <= 0.5f -> 100
            packetLossPercent <= 5.0f -> {
                val fraction = (packetLossPercent - 0.5f) / (5.0f - 0.5f)
                (100.0f - fraction * 40.0f).toInt().coerceIn(60, 100)
            }
            packetLossPercent <= 20.0f -> {
                val fraction = (packetLossPercent - 5.0f) / (20.0f - 5.0f)
                (60.0f - fraction * 60.0f).toInt().coerceIn(0, 60)
            }
            else -> 0
        }
    }

    /**
     * Calculates Bandwidth Score (0–100) relative to target voice bitrate (64 kbps).
     */
    fun calculateBandwidthScore(bandwidthKbps: Int?): Int {
        if (bandwidthKbps == null || bandwidthKbps <= 0) return 70
        return when {
            bandwidthKbps >= 1000 -> 100
            bandwidthKbps >= 256 -> 90
            bandwidthKbps >= 64 -> 75
            bandwidthKbps >= 32 -> 50
            else -> 20
        }
    }

    /**
     * Calculates weighted Overall Quality Score (0–100) for a given communication mode.
     */
    fun calculateOverallScore(
        latencyScore: Int,
        lossScore: Int,
        stabilityScore: Int,
        batteryScore: Int,
        bandwidthScore: Int,
        mode: CommunicationMode = CommunicationMode.VOICE_PTT
    ): Int {
        val score = when (mode) {
            CommunicationMode.VOICE_PTT -> {
                0.35f * latencyScore +
                0.30f * lossScore +
                0.25f * stabilityScore +
                0.10f * batteryScore
            }
            CommunicationMode.TEXT_MESSAGING -> {
                0.15f * latencyScore +
                0.45f * lossScore +
                0.25f * stabilityScore +
                0.15f * batteryScore
            }
            CommunicationMode.BULK_DATA_TRANSFER -> {
                0.10f * latencyScore +
                0.35f * lossScore +
                0.20f * stabilityScore +
                0.35f * bandwidthScore
            }
        }
        return score.toInt().coerceIn(0, 100)
    }

    /**
     * Evaluates role suitability ratings across all communication modes.
     */
    fun calculateRoleSuitability(
        latencyScore: Int,
        lossScore: Int,
        stabilityScore: Int,
        batteryScore: Int,
        bandwidthScore: Int
    ): RoleSuitability {
        return RoleSuitability(
            voiceScore = calculateOverallScore(latencyScore, lossScore, stabilityScore, batteryScore, bandwidthScore, CommunicationMode.VOICE_PTT),
            textScore = calculateOverallScore(latencyScore, lossScore, stabilityScore, batteryScore, bandwidthScore, CommunicationMode.TEXT_MESSAGING),
            bulkDataScore = calculateOverallScore(latencyScore, lossScore, stabilityScore, batteryScore, bandwidthScore, CommunicationMode.BULK_DATA_TRANSFER)
        )
    }
}
