package com.example.walkietalkieapp.audio.engine

/**
 * Represents the current operational state of the Voice Quality Engine.
 */
enum class VoiceQualityState {
    IDLE,
    PREPARING,
    CAPTURING,
    PROCESSING,
    ENCODING,
    TRANSMITTING,
    RECEIVING,
    BUFFERING,
    DECODING,
    PLAYING,
    ERROR
}

/**
 * Represents the processing tier currently active.
 */
enum class VoiceProcessingTier {
    TIER_1_FULL_DSP,      // High-Pass, Noise Suppression, EQ, AGC
    TIER_2_REDUCED_DSP,   // Basic Noise Suppression, AGC
    TIER_3_MINIMAL        // Raw capture only (fallback)
}
