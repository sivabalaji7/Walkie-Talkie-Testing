package com.example.walkietalkieapp.audio.intelligence

/**
 * Internal diagnostic snapshot for the Intelligent Voice Enhancement system.
 * Used for engineering validation only — NOT exposed in user-facing UI.
 */
data class EnhancementDiagnostics(
    val environment: AcousticEnvironment = AcousticEnvironment.UNKNOWN,
    val activeProfile: EnhancementProfile = EnhancementProfile.BALANCED,
    val noiseFloorEstimate: Float = 0f,
    val isKrispActive: Boolean = false,
    val transportType: String = "UNKNOWN"
)
