package com.example.walkietalkieapp.channel

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Tactical channel definition modeling authentic UHF/VHF radio frequencies and CTCSS sub-tones.
 */
data class TacticalChannel(
    val id: String,
    val callsign: String,
    val frequencyMhz: Double,
    val ctcssHz: Double,
    val description: String,
    val isEmergencyPriority: Boolean = false
)

/**
 * High-performance frequency synthesizer and multi-channel sub-band tuning manager for Squad operations.
 * Manages tactical channels, frequency synthesis, CTCSS squelch tone codes,
 * rotary dial stepping, live channel occupancy, and priority emergency channel preemption.
 */
object SquadChannelManager {

    val DEFAULT_CHANNELS = listOf(
        TacticalChannel(
            id = "CH-01",
            callsign = "ALPHA COMMAND",
            frequencyMhz = 462.5625,
            ctcssHz = 67.0,
            description = "General Squad Operations & Command Link"
        ),
        TacticalChannel(
            id = "CH-02",
            callsign = "BRAVO TACTICAL",
            frequencyMhz = 462.5875,
            ctcssHz = 71.9,
            description = "Close-Quarters Team Operations & Tactical Comms"
        ),
        TacticalChannel(
            id = "CH-03",
            callsign = "CHARLIE RECON",
            frequencyMhz = 462.6125,
            ctcssHz = 77.0,
            description = "Scout Surveillance & Perimeter Reconnaissance"
        ),
        TacticalChannel(
            id = "CH-04",
            callsign = "DELTA LOGISTICS",
            frequencyMhz = 462.6375,
            ctcssHz = 82.5,
            description = "Supply Chain, Equipment & Transport Dispatch"
        ),
        TacticalChannel(
            id = "CH-05",
            callsign = "ECHO MESH RELAY",
            frequencyMhz = 462.6625,
            ctcssHz = 88.5,
            description = "Extended Range Bridge & Multi-Hop Relay"
        ),
        TacticalChannel(
            id = "CH-06",
            callsign = "EMERGENCY DISPATCH",
            frequencyMhz = 462.6875,
            ctcssHz = 100.0,
            description = "High-Priority Distress & Immediate Preemption",
            isEmergencyPriority = true
        )
    )

    // Current tuned tactical channel
    private val _activeChannel = MutableStateFlow(DEFAULT_CHANNELS[0])
    val activeChannel: StateFlow<TacticalChannel> = _activeChannel.asStateFlow()

    // Complete list of tactical channels available to the squad
    private val _channelList = MutableStateFlow(DEFAULT_CHANNELS)
    val channelList: StateFlow<List<TacticalChannel>> = _channelList.asStateFlow()

    // Emergency priority preemption state (true when someone transmits on CH-06)
    private val _isEmergencyOverrideActive = MutableStateFlow(false)
    val isEmergencyOverrideActive: StateFlow<Boolean> = _isEmergencyOverrideActive.asStateFlow()

    // Number of active squad operators tuned to each channel
    private val _channelOccupancy = MutableStateFlow<Map<String, Int>>(
        DEFAULT_CHANNELS.associate { it.id to 0 }.toMutableMap().apply { put("CH-01", 1) }
    )
    val channelOccupancy: StateFlow<Map<String, Int>> = _channelOccupancy.asStateFlow()

    // -------------------------------------------------------------------------
    // Channel Tuning Operations
    // -------------------------------------------------------------------------

    /**
     * Tunes directly to a specific channel ID (e.g. "CH-02").
     */
    fun tuneToChannel(channelId: String): Boolean {
        val target = _channelList.value.find { it.id.equals(channelId, ignoreCase = true) } ?: return false
        _activeChannel.value = target

        // If tuning to emergency priority channel, auto-activate emergency override indicator
        if (target.isEmergencyPriority) {
            _isEmergencyOverrideActive.value = true
        } else if (_isEmergencyOverrideActive.value && target.id != "CH-06") {
            // Keep override active only if an external emergency broadcast is occurring
        }

        updateSelfOccupancy(target.id)
        return true
    }

    /**
     * Steps channels sequentially forward (+1) or backward (-1).
     * Designed for smooth pairing with the physical rotary DeviceWheel and dial gestures.
     */
    fun tuneStep(direction: Int): TacticalChannel {
        val channels = _channelList.value
        if (channels.isEmpty()) return _activeChannel.value

        val currentIndex = channels.indexOfFirst { it.id == _activeChannel.value.id }
        val nextIndex = if (currentIndex == -1) {
            0
        } else {
            val step = if (direction > 0) 1 else -1
            (currentIndex + step + channels.size) % channels.size
        }

        val target = channels[nextIndex]
        _activeChannel.value = target
        updateSelfOccupancy(target.id)
        return target
    }

    /**
     * Activates or clears emergency priority channel preemption.
     */
    fun setEmergencyOverride(active: Boolean) {
        _isEmergencyOverrideActive.value = active
    }

    /**
     * Updates the number of squad members active on a specific channel.
     */
    fun updateOccupancy(channelId: String, count: Int) {
        _channelOccupancy.update { current ->
            current.toMutableMap().apply { put(channelId, count.coerceAtLeast(0)) }
        }
    }

    /**
     * Resets channels back to default state upon leaving a squad room.
     */
    fun resetToDefault() {
        _activeChannel.value = DEFAULT_CHANNELS[0]
        _isEmergencyOverrideActive.value = false
        _channelOccupancy.value = DEFAULT_CHANNELS.associate { it.id to 0 }.toMutableMap().apply { put("CH-01", 1) }
    }

    private fun updateSelfOccupancy(activeId: String) {
        _channelOccupancy.update { current ->
            val updated = current.toMutableMap()
            // Increment active channel count and decrement previous if > 0
            updated[activeId] = (updated[activeId] ?: 0).coerceAtLeast(1)
            updated
        }
    }
}
