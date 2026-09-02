package com.example.walkietalkieapp.audio.engine

/**
 * Represents a compressed, sequenced frame of voice audio.
 * This is the central packet abstraction that moves between transports and the Voice Quality Engine.
 */
data class VoicePacket(
    val sequenceNumber: Long,
    val timestampMs: Long,
    val senderId: String,
    val payload: ByteArray,
    val isFinalFrame: Boolean = false // True if this is the last frame of a PTT session
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as VoicePacket

        if (sequenceNumber != other.sequenceNumber) return false
        if (timestampMs != other.timestampMs) return false
        if (senderId != other.senderId) return false
        if (!payload.contentEquals(other.payload)) return false
        if (isFinalFrame != other.isFinalFrame) return false

        return true
    }

    override fun hashCode(): Int {
        var result = sequenceNumber.hashCode()
        result = 31 * result + timestampMs.hashCode()
        result = 31 * result + senderId.hashCode()
        result = 31 * result + payload.contentHashCode()
        result = 31 * result + isFinalFrame.hashCode()
        return result
    }
}
