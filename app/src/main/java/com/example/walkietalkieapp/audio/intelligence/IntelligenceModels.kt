package com.example.walkietalkieapp.audio.intelligence

/**
 * Represents the inferred acoustic state of the local microphone environment.
 */
enum class AcousticEnvironment {
    QUIET,       // Low noise floor, clean speech
    NORMAL,      // Moderate ambient noise (e.g., standard room)
    NOISY,       // High ambient noise (e.g., street, crowd, fan)
    VERY_NOISY,  // Extreme noise, clipping risk
    UNKNOWN      // Insufficient data to classify
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
