package com.example.walkietalkieapp.dna.model

/**
 * High-level discrete events emitted by the Communication DNA Intelligence Engine.
 * Enables reactive hand-off, warnings, and diagnostic telemetry without polling.
 */
sealed interface CommunicationDnaEvent {
    val timestamp: Long
    val transportType: TransportType

    data class TransportBecameAvailable(
        override val transportType: TransportType,
        override val timestamp: Long = System.currentTimeMillis()
    ) : CommunicationDnaEvent

    data class TransportBecameUnavailable(
        override val transportType: TransportType,
        val reason: String,
        override val timestamp: Long = System.currentTimeMillis()
    ) : CommunicationDnaEvent

    data class QualityCrossedThreshold(
        override val transportType: TransportType,
        val oldScore: Int,
        val newScore: Int,
        val isSignificant: Boolean,
        override val timestamp: Long = System.currentTimeMillis()
    ) : CommunicationDnaEvent

    data class TrendChanged(
        override val transportType: TransportType,
        val oldTrend: TrendDirection,
        val newTrend: TrendDirection,
        override val timestamp: Long = System.currentTimeMillis()
    ) : CommunicationDnaEvent

    data class TransportBecameUnstable(
        override val transportType: TransportType,
        val flapCount: Int,
        val message: String,
        override val timestamp: Long = System.currentTimeMillis()
    ) : CommunicationDnaEvent

    data class TransportRecovered(
        override val transportType: TransportType,
        val currentScore: Int,
        override val timestamp: Long = System.currentTimeMillis()
    ) : CommunicationDnaEvent
}
