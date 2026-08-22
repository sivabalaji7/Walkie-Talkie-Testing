package com.example.walkietalkieapp.floor

enum class FloorState {
    IDLE,           // Channel open, PTT available
    REQUESTING,     // PTT pressed, waiting for server grant
    TRANSMITTING,   // Local user holds the mic
    RECEIVING,      // Remote user is speaking
    BUSY_BLOCKED    // Channel occupied, PTT locked out
}

data class FloorStatus(
    val state: FloorState = FloorState.IDLE,
    val currentSpeakerId: String? = null,
    val currentSpeakerName: String? = null,
    val expiresAt: Long = 0,
    val isLocalUserSpeaking: Boolean = false
)
