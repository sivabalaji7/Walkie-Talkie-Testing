package com.example.walkietalkieapp.audio.engine

import android.util.Log
import java.util.concurrent.ConcurrentSkipListMap

/**
 * Adaptive Jitter Buffer for Real-Time Voice.
 *
 * Collects incoming VoicePackets, orders them by Sequence Number, and absorbs network jitter.
 * Drops late packets and yields silence for missing packets to keep playback smooth.
 */
class JitterBuffer(
    private val frameDurationMs: Long = 40,
    private val minBufferFrames: Int = 3, // ~120ms initial buffering
    private val maxBufferFrames: Int = 10 // ~400ms max buffering before dropping
) {
    private val TAG = "JitterBuffer"

    // Ordered map of SequenceNumber -> VoicePacket
    private val buffer = ConcurrentSkipListMap<Long, VoicePacket>()
    
    @Volatile
    private var isBuffering = true
    
    @Volatile
    private var expectedNextSequence: Long = -1

    /**
     * Pushes an incoming packet into the buffer.
     */
    fun push(packet: VoicePacket) {
        if (!isBuffering && packet.sequenceNumber < expectedNextSequence) {
            // Late packet - drop it to maintain real-time continuity
            Log.v(TAG, "Dropped late packet: ${packet.sequenceNumber} (Expected: $expectedNextSequence)")
            return
        }

        buffer[packet.sequenceNumber] = packet

        // If buffer gets too large, drop the oldest packets
        while (buffer.size > maxBufferFrames) {
            val oldest = buffer.pollFirstEntry()
            Log.v(TAG, "Buffer overflow, dropping oldest: ${oldest?.key}")
        }
    }

    /**
     * Polls the next chronological packet.
     * Returns null if buffering, or if there is no packet available (indicating silence should be played).
     */
    fun poll(): VoicePacket? {
        if (isBuffering) {
            if (buffer.size >= minBufferFrames) {
                isBuffering = false
                expectedNextSequence = buffer.firstKey()
                Log.d(TAG, "Buffering complete. Starting playback from seq: $expectedNextSequence")
            } else {
                return null // Still buffering
            }
        }

        if (buffer.isEmpty()) {
            Log.v(TAG, "Buffer empty, possible underflow or end of transmission")
            isBuffering = true
            return null
        }

        val packet = buffer.remove(expectedNextSequence)
        if (packet != null) {
            expectedNextSequence++
            if (packet.isFinalFrame) {
                reset() // Auto-reset for the next transmission
            }
            return packet
        } else {
            // Packet loss detected! The packet we expected isn't here.
            // We must advance the sequence and yield null (silence) to keep time moving.
            Log.w(TAG, "Packet loss detected at seq: $expectedNextSequence")
            expectedNextSequence++
            return null
        }
    }

    fun reset() {
        buffer.clear()
        isBuffering = true
        expectedNextSequence = -1
        Log.d(TAG, "JitterBuffer reset")
    }
}
