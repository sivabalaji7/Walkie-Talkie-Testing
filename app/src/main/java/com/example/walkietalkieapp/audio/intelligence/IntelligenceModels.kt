package com.example.walkietalkieapp.audio.intelligence

/**
 * Represents the inferred acoustic state of the local microphone environment.
 */
enum class AcousticEnvironment(
    val displayName: String,
    val colorHex: String
) {
    QUIET("QUIET (STUDIO)", "#22C55E"),
    NORMAL("NORMAL (ROOM)", "#38BDF8"),
    NOISY("NOISY (TRAFFIC/OFFICE)", "#F59E0B"),
    VERY_NOISY("VERY NOISY (HIGH SPL)", "#EF4444"),
    UNKNOWN("CALIBRATING...", "#9CA3AF")
}

/**
 * Represents the internal processing strategy applied by the Voice Quality Engine.
 */
enum class EnhancementProfile {
    /**
     * Minimal processing. Preserves perfectly natural voice tone.
     * Low AGC, minimum noise subtraction, EQ bypassed.
     */
    MINIMAL,
    
    /**
     * Standard Walkie-Talkie processing.
     * Moderate noise subtraction, normal AGC.
     */
    BALANCED,
    
    /**
     * Prioritizes absolute intelligibility over natural tone.
     * Aggressive noise subtraction, strong vocal Formant EQ, aggressive AGC.
     */
    VOICE_FOCUS
}
