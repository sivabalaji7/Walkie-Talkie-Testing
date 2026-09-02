package com.example.walkietalkieapp.dna.model

/**
 * Explicit metric classification wrapper.
 * Completely eliminates fabricated precision by distinguishing between directly measured,
 * derived/estimated, heuristically inferred, and unknown values.
 */
sealed interface MetricValue<out T> {
    val timestamp: Long

    /** Directly observed from active hardware/socket telemetry (e.g. WebRTC RTCStats, Socket ping RTT). */
    data class Measured<T>(
        val value: T,
        override val timestamp: Long = System.currentTimeMillis()
    ) : MetricValue<T>

    /** Derived from observational statistics (e.g. bandwidth derived from packet transfer durations). */
    data class Estimated<T>(
        val value: T,
        val method: String,
        override val timestamp: Long = System.currentTimeMillis()
    ) : MetricValue<T>

    /** Inferred via heuristic or historical context (e.g. radio cost models). */
    data class Inferred<T>(
        val value: T,
        val heuristic: String,
        override val timestamp: Long = System.currentTimeMillis()
    ) : MetricValue<T>

    /** Value is unavailable or unsupported on the current device/transport. */
    data object Unknown : MetricValue<Nothing> {
        override val timestamp: Long = 0L
    }

    fun valueOrNull(): T? = when (this) {
        is Measured -> value
        is Estimated -> value
        is Inferred -> value
        is Unknown -> null
    }

    fun valueOrDefault(default: @UnsafeVariance T): T = valueOrNull() ?: default

    fun isKnown(): Boolean = this !is Unknown

    fun isFresh(maxAgeMs: Long = 30_000L, currentTimeMs: Long = System.currentTimeMillis()): Boolean {
        if (this is Unknown) return false
        return (currentTimeMs - timestamp) <= maxAgeMs
    }
}
