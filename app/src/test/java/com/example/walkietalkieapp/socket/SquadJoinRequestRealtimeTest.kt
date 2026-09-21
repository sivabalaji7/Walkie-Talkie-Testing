package com.example.walkietalkieapp.socket

import org.junit.Assert.*
import org.junit.Test

class SquadJoinRequestRealtimeTest {

    @Test
    fun testSignalMessageJoinRequestProperties() {
        val msg = SignalMessage(
            type = "squad_join_request",
            sender = "GhostOperator",
            targetRoomCode = "ALPHA-7",
            targetUserId = "usr-uuid-1234",
            timestamp = 1710000000000L
        )

        assertEquals("squad_join_request", msg.type)
        assertEquals("GhostOperator", msg.sender)
        assertEquals("ALPHA-7", msg.targetRoomCode)
        assertEquals("usr-uuid-1234", msg.targetUserId)
        assertEquals(1710000000000L, msg.timestamp)
    }

    @Test
    fun testSignalMessageRequestApprovedProperties() {
        val msg = SignalMessage(
            type = "squad_request_approved",
            sender = "CommanderRoomOwner",
            targetRoomCode = "BRAVO-9",
            targetUserId = "usr-uuid-5678",
            timestamp = 1710000005000L
        )

        assertEquals("squad_request_approved", msg.type)
        assertEquals("CommanderRoomOwner", msg.sender)
        assertEquals("BRAVO-9", msg.targetRoomCode)
        assertEquals("usr-uuid-5678", msg.targetUserId)
        assertEquals(1710000005000L, msg.timestamp)
    }

    @Test
    fun testJoinRequestCallbackRegistration() {
        var receivedUser: String? = null
        var receivedRoom: String? = null

        SupabaseRealtimeManager.onJoinRequestReceived = { user, room ->
            receivedUser = user
            receivedRoom = room
        }

        SupabaseRealtimeManager.onJoinRequestReceived?.invoke("OperatorViper", "SQUAD-42")

        assertEquals("OperatorViper", receivedUser)
        assertEquals("SQUAD-42", receivedRoom)

        // Clear callback
        SupabaseRealtimeManager.onJoinRequestReceived = null
        assertNull(SupabaseRealtimeManager.onJoinRequestReceived)
    }

    @Test
    fun testRequestApprovedCallbackRegistration() {
        var approvedRoom: String? = null

        SupabaseRealtimeManager.onRequestApproved = { room ->
            approvedRoom = room
        }

        SupabaseRealtimeManager.onRequestApproved?.invoke("ECHO-101")

        assertEquals("ECHO-101", approvedRoom)

        // Clear callback
        SupabaseRealtimeManager.onRequestApproved = null
        assertNull(SupabaseRealtimeManager.onRequestApproved)
    }
}
