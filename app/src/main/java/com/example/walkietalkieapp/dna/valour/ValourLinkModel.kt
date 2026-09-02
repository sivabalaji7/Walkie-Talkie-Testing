package com.example.walkietalkieapp.dna.valour

import androidx.compose.ui.graphics.Color
import com.example.walkietalkieapp.dna.model.CommunicationPathAssessment
import com.example.walkietalkieapp.dna.model.ConfidenceLevel
import com.example.walkietalkieapp.dna.model.TransportType
import com.example.walkietalkieapp.dna.presentation.StrengthLevel

/**
 * Valour Link Connection State
 */
enum class LinkConnectionState(val label: String) {
    CONNECTED("Connected"),
    CONNECTING("Connecting"),
    RECONNECTING("Reconnecting"),
    OPTIMIZING("Optimizing connection"),
    OFFLINE("Offline")
}

/**
 * Semantic Link Quality Classification
 */
enum class LinkQualityLevel(val label: String) {
    EXCELLENT("Excellent"),
    GOOD("Good"),
    FAIR("Fair"),
    WEAK("Weak"),
    CRITICAL("Unstable"),
    CONNECTING("Connecting"),
    OFFLINE("Offline"),
    UNKNOWN("Unknown")
}

/**
 * Qualitative Voice Readiness Classification
 */
enum class VoiceReadiness(val label: String, val description: String) {
    OPTIMAL("Optimal", "Excellent voice conditions • Ready for Push-to-Talk"),
    GOOD("Good", "Voice communication should be smooth"),
    FAIR("Fair", "Minor audio latency or delay possible"),
    DEGRADED("Degraded", "Audio quality may be impaired"),
    UNAVAILABLE("Unavailable", "Voice channel unavailable")
}

/**
 * Link Stability Classification
 */
enum class LinkStability(val label: String, val message: String) {
    STABLE("Stable", "Connection is stable"),
    STRENGTHENING("Improving", "Connection is strengthening"),
    WEAKENING("Degrading", "Connection is weakening"),
    FLUCTUATING("Fluctuating", "Connection is fluctuating"),
    STANDBY("Standby", "Channel standby")
}

/**
 * Master Valour Link State Profile
 * One unified model consumed across Dashboard, Rooms, and Connection Sheets.
 */
data class ValourLinkState(
    val transportType: TransportType,
    val transportName: String,
    val connectionState: LinkConnectionState,
    val qualityLevel: LinkQualityLevel,
    val strengthLevel: StrengthLevel,
    val voiceReadiness: VoiceReadiness,
    val voiceReadinessScore: Int,
    
    // Human-Centric Natural Language
    val headlineText: String,
    val subheadText: String,
    val compactPillText: String,
    val accessibilityDescription: String,
    
    // Visual Token
    val accentColor: Color,
    
    // Level 3 Deep Diagnostics
    val diagnostics: LinkDiagnostics,
    val rawAssessment: CommunicationPathAssessment
)

/**
 * Technical Diagnostics preserved for Level 3 Progressive Disclosure
 */
data class LinkDiagnostics(
    val dnaScore: Int,
    val rttMs: Long?,
    val packetLossPercent: Float?,
    val bandwidthKbps: Int?,
    val stabilityScore: Int,
    val confidenceLevel: String,
    val trendDescription: String,
    val batteryImpact: String,
    val disconnectCountLastMinute: Int
)
