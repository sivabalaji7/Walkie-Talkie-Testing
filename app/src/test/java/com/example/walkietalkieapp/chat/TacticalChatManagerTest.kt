package com.example.walkietalkieapp.chat

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class TacticalChatManagerTest {

    @Before
    fun setUp() {
        TacticalChatManager.clearMessages()
        TacticalChatManager.isChatDialogVisible = false
    }

    @Test
    fun testReceiveMessageUpdatesListAndUnreadCount() {
        assertEquals(0, TacticalChatManager.messages.value.size)
        assertEquals(0, TacticalChatManager.unreadCount.value)

        TacticalChatManager.receiveMessage(
            sender = "GHOST-1",
            content = "Heading north-east to coordinate bravo",
            timestamp = 100000L
        )

        val messages = TacticalChatManager.messages.value
        assertEquals(1, messages.size)
        val msg = messages.first()
        assertEquals("GHOST-1", msg.senderName)
        assertEquals("Heading north-east to coordinate bravo", msg.content)
        assertEquals(100000L, msg.timestamp)
        assertFalse(msg.isSelf)
        assertFalse(msg.isBeacon)
        assertEquals(1, TacticalChatManager.unreadCount.value)
    }

    @Test
    fun testUnreadCountNotIncrementedWhenDialogVisible() {
        TacticalChatManager.isChatDialogVisible = true

        TacticalChatManager.receiveMessage(
            sender = "COMMAND",
            content = "Radio silence in effect"
        )

        assertEquals(1, TacticalChatManager.messages.value.size)
        assertEquals("Unread count should not increment if dialog is active", 0, TacticalChatManager.unreadCount.value)
    }

    @Test
    fun testMarkAllReadResetsUnreadCount() {
        TacticalChatManager.receiveMessage("Viper-1", "Status check")
        TacticalChatManager.receiveMessage("Viper-2", "Clear")
        assertEquals(2, TacticalChatManager.unreadCount.value)

        TacticalChatManager.markAllRead()
        assertEquals(0, TacticalChatManager.unreadCount.value)
    }

    @Test
    fun testRollingBufferCappedAt50() {
        for (i in 1..60) {
            TacticalChatManager.receiveMessage(
                sender = "Unit-$i",
                content = "Ping $i"
            )
        }

        val messages = TacticalChatManager.messages.value
        assertEquals("Tactical chat buffer must be capped at 50 messages", 50, messages.size)
        assertEquals("Oldest preserved message should be Unit-11", "Unit-11", messages.first().senderName)
        assertEquals("Latest message should be Unit-60", "Unit-60", messages.last().senderName)
    }

    @Test
    fun testGpsBeaconTelemetryPreserved() {
        val lat = 37.774929
        val lon = -122.419416
        TacticalChatManager.receiveMessage(
            sender = "PATHFINDER",
            content = "BEACON DROPPED: 37.77493°, -122.41942°",
            latitude = lat,
            longitude = lon,
            locationLabel = "Rendezvous Point Alpha",
            isBeacon = true
        )

        val messages = TacticalChatManager.messages.value
        assertEquals(1, messages.size)
        val beacon = messages.first()
        assertTrue(beacon.isBeacon)
        assertEquals(lat, beacon.latitude ?: 0.0, 0.000001)
        assertEquals(lon, beacon.longitude ?: 0.0, 0.000001)
        assertEquals("Rendezvous Point Alpha", beacon.locationLabel)
    }

    @Test
    fun testQuickStatusTemplates() {
        val statuses = TacticalQuickStatus.values()
        assertEquals(6, statuses.size)

        val onMyWay = TacticalQuickStatus.ON_MY_WAY
        assertEquals("⚡ ON MY WAY", onMyWay.chipLabel)
        assertTrue(onMyWay.transmissionText.contains("ON MY WAY"))

        val beacon = TacticalQuickStatus.DROP_BEACON
        assertEquals("📍 GPS BEACON", beacon.chipLabel)
        assertTrue(beacon.transmissionText.contains("GPS BEACON"))
    }

    @Test
    fun testClearMessages() {
        TacticalChatManager.receiveMessage("Alpha", "Msg 1")
        TacticalChatManager.receiveMessage("Bravo", "Msg 2")
        assertEquals(2, TacticalChatManager.messages.value.size)

        TacticalChatManager.clearMessages()
        assertEquals(0, TacticalChatManager.messages.value.size)
        assertEquals(0, TacticalChatManager.unreadCount.value)
    }
}
