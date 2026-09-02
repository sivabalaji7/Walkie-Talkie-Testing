package com.example.walkietalkieapp.audio.intelligence

/**
 * Intelligent decision engine that selects the optimal Voice Quality Processing profile
 * based on the acoustic environment.
 * Applies temporal hysteresis to prevent rapid profile switching (flapping) and audible artifacts.
 */
class EnhancementPolicyEngine {

    var currentProfile = EnhancementProfile.BALANCED
        private set

    private var targetProfile = EnhancementProfile.BALANCED
    private var framesInTargetState = 0
    
    // Hysteresis: require ~1 second of sustained environmental change before switching profiles (assuming 40ms frames -> 25 frames)
    private val HYSTERESIS_FRAMES = 25

    /**
     * Determines the optimal enhancement profile for the current environment.
     * Must be called periodically (e.g., once per audio frame).
     */
    fun evaluate(environment: AcousticEnvironment): EnhancementProfile {
        val recommendedProfile = when (environment) {
            AcousticEnvironment.QUIET -> EnhancementProfile.MINIMAL
            AcousticEnvironment.NORMAL -> EnhancementProfile.BALANCED
            AcousticEnvironment.NOISY, AcousticEnvironment.VERY_NOISY -> EnhancementProfile.VOICE_FOCUS
            AcousticEnvironment.UNKNOWN -> EnhancementProfile.BALANCED
        }

        // Apply Hysteresis
        if (recommendedProfile == targetProfile) {
            framesInTargetState++
            if (framesInTargetState >= HYSTERESIS_FRAMES && currentProfile != targetProfile) {
                currentProfile = targetProfile // Commit to the new profile
            }
        } else {
            // Environment changed, reset the hysteresis counter
            targetProfile = recommendedProfile
            framesInTargetState = 0
        }

        return currentProfile
    }

    fun reset() {
        currentProfile = EnhancementProfile.BALANCED
        targetProfile = EnhancementProfile.BALANCED
        framesInTargetState = 0
    }
}
