package com.example.walkietalkieapp.dna.engine

import java.util.concurrent.ConcurrentLinkedDeque
import kotlin.math.max
import kotlin.math.min

/**
 * Tracks link lifecycle, disconnect frequency, state flapping, and connection duration.
 * Heavy disconnect flap penalties protect future routing systems from oscillating connections.
 */
class StabilityTracker(
    private val flapWindowMs: Long = 60_000L
) {
    private var connectionStartTime: Long? = null
    private val disconnectTimestamps = ConcurrentLinkedDeque<Long>()
    private var consecutiveSuccesses: Int = 0
    private var recentFailures: Int = 0

    fun onConnected(timestamp: Long = System.currentTimeMillis()) {
        connectionStartTime = timestamp
        consecutiveSuccesses++
        prune(timestamp)
    }

    fun onDisconnected(timestamp: Long = System.currentTimeMillis()) {
        connectionStartTime = null
        disconnectTimestamps.addLast(timestamp)
        recentFailures++
        consecutiveSuccesses = 0
        prune(timestamp)
    }

    fun recordSuccess() {
        consecutiveSuccesses++
    }

    fun recordFailure(timestamp: Long = System.currentTimeMillis()) {
        recentFailures++
        consecutiveSuccesses = 0
    }

    private fun prune(now: Long) {
        while (disconnectTimestamps.peekFirst()?.let { now - it > flapWindowMs } == true) {
            disconnectTimestamps.pollFirst()
        }
    }

    fun getDisconnectCountLastMinute(now: Long = System.currentTimeMillis()): Int {
        prune(now)
        return disconnectTimestamps.size
    }

    fun getConnectionDurationMs(now: Long = System.currentTimeMillis()): Long {
        val start = connectionStartTime ?: return 0L
        return max(0L, now - start)
    }

    fun calculateStabilityScore(now: Long = System.currentTimeMillis()): Int {
        prune(now)
        val disconnects = disconnectTimestamps.size
        
        // Base score starts at 100
        var score = 100

        // Flapping Penalty: -25 points per disconnect in the last 60s
        score -= (disconnects * 25)

        // Duration Bonus: +1 point per 10s of unbroken connection (capped at +15)
        val durationMs = getConnectionDurationMs(now)
        val durationBonus = min(15, (durationMs / 10_000L).toInt())
        score += durationBonus

        // Recent Failure Penalty: -5 per consecutive failure
        if (consecutiveSuccesses == 0 && recentFailures > 0) {
            score -= min(30, recentFailures * 5)
        }

        return score.coerceIn(0, 100)
    }

    fun reset() {
        connectionStartTime = null
        disconnectTimestamps.clear()
        consecutiveSuccesses = 0
        recentFailures = 0
    }
}
