package com.example.walkietalkieapp.dna.adapter

import com.example.walkietalkieapp.dna.model.CommunicationPathAssessment
import com.example.walkietalkieapp.dna.model.TransportType
import kotlinx.coroutines.flow.StateFlow

/**
 * Common contract for all transport-specific network intelligence adapters.
 * Guarantees that any new transport (LAN, Relay, UWB) can be plugged in without engine changes.
 */
interface TransportProbeAdapter {
    val transportType: TransportType
    val assessment: StateFlow<CommunicationPathAssessment>

    fun startProbing()
    fun stopProbing()

    /**
     * Optional notification from active voice sessions to adapt probe frequencies.
     */
    fun onAudioSessionStateChanged(isTransmittingOrReceiving: Boolean) {}
}
