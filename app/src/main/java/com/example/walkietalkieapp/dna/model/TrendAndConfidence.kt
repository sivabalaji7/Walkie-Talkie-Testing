package com.example.walkietalkieapp.dna.model

/**
 * Normalized trend direction classified over rolling time windows.
 */
enum class TrendDirection(val description: String) {
    IMPROVING("Improving"),
    STABLE("Stable"),
    DEGRADING("Degrading"),
    VOLATILE("Volatile / Flapping"),
    UNKNOWN("Insufficient Data")
}

/**
 * Categorical confidence level derived from continuous confidence score [0.0, 1.0].
 */
enum class ConfidenceLevel(val threshold: Float, val label: String) {
    HIGH(0.75f, "High Confidence"),
    MEDIUM(0.45f, "Medium Confidence"),
    LOW(0.20f, "Low Confidence"),
    NONE(0.0f, "No Confidence");

    companion object {
        fun fromScore(score: Float): ConfidenceLevel {
            return when {
                score >= HIGH.threshold -> HIGH
                score >= MEDIUM.threshold -> MEDIUM
                score >= LOW.threshold -> LOW
                else -> NONE
            }
        }
    }
}

/**
 * Communication mode profile used to calculate mode-specific suitabilities.
 */
enum class CommunicationMode {
    VOICE_PTT,
    TEXT_MESSAGING,
    BULK_DATA_TRANSFER
}

/**
 * Suitability ratings (0–100) per communication modality.
 */
data class RoleSuitability(
    val voiceScore: Int,
    val textScore: Int,
    val bulkDataScore: Int
) {
    fun scoreFor(mode: CommunicationMode): Int = when (mode) {
        CommunicationMode.VOICE_PTT -> voiceScore
        CommunicationMode.TEXT_MESSAGING -> textScore
        CommunicationMode.BULK_DATA_TRANSFER -> bulkDataScore
    }
}
