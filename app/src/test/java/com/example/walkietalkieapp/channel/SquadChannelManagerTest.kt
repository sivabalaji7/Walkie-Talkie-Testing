package com.example.walkietalkieapp.channel

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SquadChannelManagerTest {

    @Before
    fun setUp() {
        SquadChannelManager.resetToDefault()
    }

    @Test
    fun testDefaultChannelIsAlphaCommand() {
        val initial = SquadChannelManager.activeChannel.value
        assertEquals("CH-01", initial.id)
        assertEquals("ALPHA COMMAND", initial.callsign)
        assertEquals(462.5625, initial.frequencyMhz, 0.0001)
        assertEquals(67.0, initial.ctcssHz, 0.01)
        assertFalse(initial.isEmergencyPriority)
    }

    @Test
    fun testTuneToValidChannel() {
        val success = SquadChannelManager.tuneToChannel("CH-02")
        assertTrue(success)

        val active = SquadChannelManager.activeChannel.value
        assertEquals("CH-02", active.id)
        assertEquals("BRAVO TACTICAL", active.callsign)
        assertEquals(462.5875, active.frequencyMhz, 0.0001)
        assertEquals(71.9, active.ctcssHz, 0.01)
    }

    @Test
    fun testTuneToInvalidChannelFails() {
        val success = SquadChannelManager.tuneToChannel("CH-99")
        assertFalse(success)
        assertEquals("CH-01", SquadChannelManager.activeChannel.value.id)
    }

    @Test
    fun testRotaryStepTuningForwardAndBackward() {
        // Step forward from CH-01 -> CH-02
        val ch2 = SquadChannelManager.tuneStep(1)
        assertEquals("CH-02", ch2.id)

        // Step forward from CH-02 -> CH-03
        val ch3 = SquadChannelManager.tuneStep(1)
        assertEquals("CH-03", ch3.id)

        // Step backward from CH-03 -> CH-02
        val chBack = SquadChannelManager.tuneStep(-1)
        assertEquals("CH-02", chBack.id)
    }

    @Test
    fun testRotaryStepTuningCircularWrapping() {
        // Step backward from CH-01 -> wraps to CH-06 (Emergency Dispatch)
        val wrappedEnd = SquadChannelManager.tuneStep(-1)
        assertEquals("CH-06", wrappedEnd.id)
        assertTrue(wrappedEnd.isEmergencyPriority)

        // Step forward from CH-06 -> wraps to CH-01
        val wrappedStart = SquadChannelManager.tuneStep(1)
        assertEquals("CH-01", wrappedStart.id)
    }

    @Test
    fun testEmergencyPriorityPreemption() {
        assertFalse(SquadChannelManager.isEmergencyOverrideActive.value)

        // Tuning to emergency channel triggers override
        SquadChannelManager.tuneToChannel("CH-06")
        assertTrue(SquadChannelManager.isEmergencyOverrideActive.value)

        // Manual override clear
        SquadChannelManager.setEmergencyOverride(false)
        assertFalse(SquadChannelManager.isEmergencyOverrideActive.value)

        // Remote emergency trigger
        SquadChannelManager.setEmergencyOverride(true)
        assertTrue(SquadChannelManager.isEmergencyOverrideActive.value)
    }

    @Test
    fun testChannelOccupancyTracking() {
        SquadChannelManager.updateOccupancy("CH-02", 4)
        val occupancy = SquadChannelManager.channelOccupancy.value
        assertEquals(4, occupancy["CH-02"])

        SquadChannelManager.updateOccupancy("CH-03", 2)
        assertEquals(2, SquadChannelManager.channelOccupancy.value["CH-03"])
    }
}
