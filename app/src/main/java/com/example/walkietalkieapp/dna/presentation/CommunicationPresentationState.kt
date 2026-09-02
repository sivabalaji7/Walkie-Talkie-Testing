package com.example.walkietalkieapp.dna.presentation

import androidx.compose.ui.graphics.Color
import com.example.walkietalkieapp.dna.model.BatteryImpact
import com.example.walkietalkieapp.dna.model.CommunicationPathAssessment
import com.example.walkietalkieapp.dna.model.ConfidenceLevel
import com.example.walkietalkieapp.dna.model.TransportType

/**
 * Semantic human-facing quality levels.
 */
enum class QualityLevel(val label: String) {
    EXCELLENT("Excellent"),
    GOOD("Good"),
    FAIR("Fair"),
    WEAK("Weak"),
    CRITICAL("Unstable"),
    OFFLINE("Offline")
}

/**
 * Visual strength level for custom segmented indicator (0 to 4 bars).
 */
enum class StrengthLevel(val activeSegments: Int) {
    ZERO(0),
    ONE(1),
    TWO(2),
    THREE(3),
    FOUR(4)
}

/**
 * Pre-computed semantic presentation state.
 * Contains all formatting, visual tokens, and human explanations so Composables do zero math.
 */
data class CommunicationPresentationState(
    val transportType: TransportType,
    val transportName: String,
    val isConnected: Boolean,
    val isAvailable: Boolean,
    
    // Semantic Levels
    val qualityLevel: QualityLevel,
    val strengthLevel: StrengthLevel,
    val confidenceLevel: ConfidenceLevel,
    
    // Human Readable Text
    val headlineStatus: String, // e.g. "Ready for voice", "Strong connection", "Offline"
    val supportingMessage: String, // e.g. "Voice is smooth", "Slight delay possible"
    val stabilitySummary: String, // e.g. "Connection is stable", "Weakening"
    val pillBadgeText: String, // e.g. "Wi-Fi Direct · Strong"
    
    // Visual Tokens
    val primaryAccentColor: Color,
    val accessibilityDescription: String,
    
    // Underlying raw assessment preserved for Level 3 advanced diagnostics
    val rawAssessment: CommunicationPathAssessment
)
