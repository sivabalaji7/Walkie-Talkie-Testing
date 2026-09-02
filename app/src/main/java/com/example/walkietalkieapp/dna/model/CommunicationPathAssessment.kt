package com.example.walkietalkieapp.dna.model

/**
 * Normalized, comprehensive state profile of a single communication path/transport.
 * Completely decoupled from UI rendering and hardware specifics.
 */
data class CommunicationPathAssessment(
    val transportType: TransportType,
    val isAvailable: Boolean,
    val isConnected: Boolean,
    
    // Normalized Quality & Sub-Scores (0 – 100)
    val overallQualityScore: Int,
    val latencyScore: Int,
    val lossScore: Int,
    val stabilityScore: Int,
    val batteryScore: Int,
    val bandwidthScore: Int,

    // Real Metrics (with explicit classification)
    val latencyMs: MetricValue<Long>,
    val latencyVarianceMs: MetricValue<Double>,
    val packetLossPercent: MetricValue<Float>,
    val bandwidthKbps: MetricValue<Int>,
    val signalDbm: MetricValue<Int>,

    // Intelligence Metrics
    val confidence: Float, // 0.0f .. 1.0f
    val confidenceLevel: ConfidenceLevel,
    val trend: TrendDirection,
    val roleSuitability: RoleSuitability,
    val batteryImpact: BatteryImpact,
    
    // Stability & Lifecycle Meta
    val consecutiveSuccessCount: Int,
    val recentFailureCount: Int,
    val disconnectCountLastMinute: Int,
    val connectionDurationMs: Long,
    val lastStateChangeTimestamp: Long,
    val lastUpdatedTimestamp: Long = System.currentTimeMillis()
) {
    val isStale: Boolean
        get() = (System.currentTimeMillis() - lastUpdatedTimestamp) > 30_000L

    companion object {
        fun unavailable(transportType: TransportType): CommunicationPathAssessment {
            return CommunicationPathAssessment(
                transportType = transportType,
                isAvailable = false,
                isConnected = false,
                overallQualityScore = 0,
                latencyScore = 0,
                lossScore = 0,
                stabilityScore = 0,
                batteryScore = 0,
                bandwidthScore = 0,
                latencyMs = MetricValue.Unknown,
                latencyVarianceMs = MetricValue.Unknown,
                packetLossPercent = MetricValue.Unknown,
                bandwidthKbps = MetricValue.Unknown,
                signalDbm = MetricValue.Unknown,
                confidence = 1.0f,
                confidenceLevel = ConfidenceLevel.HIGH,
                trend = TrendDirection.UNKNOWN,
                roleSuitability = RoleSuitability(0, 0, 0),
                batteryImpact = BatteryImpact.NONE,
                consecutiveSuccessCount = 0,
                recentFailureCount = 0,
                disconnectCountLastMinute = 0,
                connectionDurationMs = 0L,
                lastStateChangeTimestamp = System.currentTimeMillis()
            )
        }
    }
}

enum class BatteryImpact(val label: String) {
    NONE("No Power Cost"),
    VERY_LOW("Minimal Power (BLE/BT)"),
    MODERATE("Moderate Power (Wi-Fi P2P / LAN)"),
    HIGH("High Power (Cellular 5G/Radio TX)"),
    CRITICAL("Severe Battery Drain")
}
