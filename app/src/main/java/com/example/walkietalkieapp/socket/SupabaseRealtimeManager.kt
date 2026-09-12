package com.example.walkietalkieapp.socket

import android.util.Log
import com.example.walkietalkieapp.floor.FloorManager
import com.example.walkietalkieapp.floor.FloorState
import com.example.walkietalkieapp.supabase.SupabaseClientManager
import io.github.jan.supabase.realtime.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.Serializable
import java.util.concurrent.ConcurrentHashMap

data class RoomMember(
    val id: String,
    val username: String,
    val isSpeaking: Boolean
)

data class SocketUiState(
    val isConnected: Boolean = false,
    val status: String = "Disconnected",
    val detail: String = "Ready to connect.",
    val eventLog: List<String> = listOf("App Started"),
    val roomId: String = "",
    val roomName: String = "",
    val username: String = "",
    val roomMembers: List<RoomMember> = emptyList(),
    val voiceLinkState: String = "IDLE",
    val lastActivityTimestamp: Long = System.currentTimeMillis(),
    val lastSpeakerName: String? = null,
    val lastSpeakerTimestamp: Long = 0
)

@Serializable
data class SignalMessage(
    val type: String = "", // "offer", "answer", "ice-candidate", "floor-grant", "floor-release", "peer-join", "peer-ack", "peer-ping", "peer-leave"
    val sender: String = "",
    val to: String? = null,
    val sdp: String? = null,
    val candidate: String? = null,
    val isPriority: Boolean? = null,
    val timestamp: Long = 0L
)

@Serializable
data class PresenceState(
    val userId: String = "",
    val username: String = "",
    val isSpeaking: Boolean = false
)

object SupabaseRealtimeManager {
    private const val TAG = "SupabaseRealtime"
    private var channel: RealtimeChannel? = null
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var heartbeatJob: Job? = null

    private var signalingListener: SignalingListener? = null
    fun setSignalingListener(listener: SignalingListener?) {
        this.signalingListener = listener
        if (listener != null) {
            checkAndTriggerWebRtcOffers()
        }
    }

    // Active squad peers map: username (lowercase) -> originalUsername
    private val activePeers = ConcurrentHashMap<String, String>()
    // Peer heartbeat timestamp map: username (lowercase) -> lastSeenMs
    private val peerLastSeen = ConcurrentHashMap<String, Long>()

    private val _socketUiState = MutableStateFlow(SocketUiState())
    val socketUiState: StateFlow<SocketUiState> = _socketUiState.asStateFlow()

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 10)
    val events = _events.asSharedFlow()

    init {
        scope.launch {
            SupabaseClientManager.client.realtime.status.collect { status ->
                Log.d(TAG, "Realtime status changed: $status")
                when (status) {
                    Realtime.Status.CONNECTED -> {
                        _socketUiState.update { it.copy(isConnected = true, status = "ONLINE", detail = "Connected") }
                        addLog("Connected to Supabase Realtime")
                        val currentRoom = _socketUiState.value.roomId
                        val currentUser = _socketUiState.value.username
                        if (currentRoom.isNotEmpty() && currentUser.isNotEmpty() && (channel == null || channel?.status?.value != io.github.jan.supabase.realtime.RealtimeChannel.Status.SUBSCRIBED)) {
                            Log.d(TAG, "Reconnected to Realtime — re-subscribing to room $currentRoom")
                            joinRoom(currentRoom, currentUser, _socketUiState.value.roomName)
                        }
                    }
                    Realtime.Status.CONNECTING -> {
                        _socketUiState.update { it.copy(isConnected = false, status = "CONNECTING", detail = "Connecting...") }
                    }
                    Realtime.Status.DISCONNECTED -> {
                        _socketUiState.update { it.copy(isConnected = false, status = "OFFLINE", detail = "Disconnected") }
                        val currentRoom = _socketUiState.value.roomId
                        if (currentRoom.isNotEmpty()) {
                            launch {
                                delay(2500L)
                                if (_socketUiState.value.roomId.isNotEmpty() && SupabaseClientManager.client.realtime.status.value != Realtime.Status.CONNECTED) {
                                    Log.d(TAG, "Auto-reconnecting to Realtime...")
                                    try { SupabaseClientManager.client.realtime.connect() } catch (e: Exception) { Log.w(TAG, "Reconnect error: ${e.message}") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    fun initialize() {
        scope.launch {
            try {
                if (SupabaseClientManager.client.realtime.status.value != Realtime.Status.CONNECTED) {
                    SupabaseClientManager.client.realtime.connect()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Connection Error", e)
                _socketUiState.update { it.copy(status = "OFFLINE", detail = "Connection Error: ${e.message}") }
            }
        }
    }

    fun connect() = initialize()

    fun disconnect() {
        leaveRoom()
        scope.launch {
            try { SupabaseClientManager.client.realtime.disconnect() } catch (e: Exception) {
                Log.e(TAG, "Error disconnecting", e)
            }
            _socketUiState.update { it.copy(isConnected = false, status = "OFFLINE", detail = "Disconnected") }
        }
    }

    fun joinRoom(roomId: String, username: String, roomName: String = "") {
        if (roomId.isEmpty()) return
        val cleanId = roomId.trim().uppercase()
        val cleanUsername = username.trim()
        leaveRoom()

        _socketUiState.update { 
            it.copy(
                roomId = cleanId, 
                roomName = if (roomName.isNotEmpty()) roomName else cleanId, 
                username = cleanUsername,
                detail = "Joining $cleanId..."
            ) 
        }
        
        scope.launch {
            try {
                // Ensure realtime is connected before subscribing to channel
                if (SupabaseClientManager.client.realtime.status.value != Realtime.Status.CONNECTED) {
                    Log.d(TAG, "Realtime client not yet connected, connecting now...")
                    try {
                        SupabaseClientManager.client.realtime.connect()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error triggering realtime.connect()", e)
                    }
                    withTimeoutOrNull(5000) {
                        SupabaseClientManager.client.realtime.status.first { it == Realtime.Status.CONNECTED }
                    }
                }

                val ch = SupabaseClientManager.client.channel("squad:$cleanId") {
                    presence {
                        key = cleanUsername.lowercase()
                    }
                    broadcast {
                        receiveOwnBroadcasts = false
                        acknowledgeBroadcasts = true
                    }
                }
                channel = ch

                // 1. Listen to Broadcast Messages (Signaling, Floor Control, Custom Presence Engine)
                launch {
                    ch.broadcastFlow<SignalMessage>("webrtc").collect { msg ->
                        handleSignalMessage(msg)
                    }
                }

                // 2. Block subscribe until joined
                Log.d(TAG, "Subscribing to channel squad:$cleanId...")
                ch.subscribe(blockUntilSubscribed = true)
                Log.d(TAG, "Subscribed to squad:$cleanId successfully. Status: ${ch.status.value}")

                _socketUiState.update { it.copy(isConnected = true, status = "ONLINE", detail = "Connected to $cleanId") }
                addLog("Joined Squad: $cleanId")

                // Add self to active peers immediately
                activePeers[cleanUsername.lowercase()] = cleanUsername
                peerLastSeen[cleanUsername.lowercase()] = System.currentTimeMillis()
                updateMemberList()

                // Announce presence immediately via Broadcast peer-join
                broadcastSignal(SignalMessage(type = "peer-join", sender = cleanUsername))

                // Start active presence heartbeat loop (every 8 seconds)
                heartbeatJob?.cancel()
                heartbeatJob = launch {
                    while (isActive) {
                        delay(8000)
                        val currentRoom = _socketUiState.value.roomId
                        val myUser = _socketUiState.value.username
                        if (currentRoom.isNotEmpty() && myUser.isNotEmpty()) {
                            // Ping presence
                            broadcastSignal(SignalMessage(type = "peer-ping", sender = myUser))
                            
                            // Prune dead peers (>35 seconds without any ping or signal)
                            val now = System.currentTimeMillis()
                            val currentMyName = myUser.lowercase()
                            val iterator = peerLastSeen.entries.iterator()
                            var changed = false
                            while (iterator.hasNext()) {
                                val entry = iterator.next()
                                if (entry.key != currentMyName && (now - entry.value > 35000L)) {
                                    val leftKey = entry.key
                                    iterator.remove()
                                    val leftName = activePeers.remove(leftKey)
                                    if (leftName != null) {
                                        Log.d(TAG, "Pruned timed-out peer: $leftName")
                                        addLog("$leftName left (timeout)")
                                        signalingListener?.onPeerLeft(leftName)
                                        changed = true
                                    }
                                }
                            }
                            if (changed) {
                                updateMemberList()
                            }
                            
                            // Ensure offer is triggered for all known peers
                            checkAndTriggerWebRtcOffers()
                        }
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Join Error for squad:$cleanId", e)
                addLog("Join Error: ${e.localizedMessage}")
                _socketUiState.update { it.copy(status = "OFFLINE", detail = "Failed to join: ${e.message}") }
            }
        }
    }

    private fun handlePeerSeen(peerName: String) {
        val clean = peerName.trim()
        val myName = _socketUiState.value.username.trim()
        if (clean.isBlank() || clean.equals(myName, ignoreCase = true)) return
        val key = clean.lowercase()
        peerLastSeen[key] = System.currentTimeMillis()
        val isNew = !activePeers.containsKey(key)
        activePeers[key] = clean
        if (isNew) {
            Log.d(TAG, "Discovered squad peer: $clean")
            addLog("Online: $clean")
            updateMemberList()
            checkAndTriggerWebRtcOffers()
        }
    }

    private fun handlePeerLeft(peerName: String) {
        val clean = peerName.trim()
        if (clean.isBlank()) return
        val key = clean.lowercase()
        peerLastSeen.remove(key)
        if (activePeers.remove(key) != null) {
            Log.d(TAG, "Squad peer left: $clean")
            addLog("$clean left squad")
            updateMemberList()
            signalingListener?.onPeerLeft(clean)
        }
    }

    private fun updateMemberList(newSpeaker: String? = _socketUiState.value.lastSpeakerName) {
        val myName = _socketUiState.value.username.trim()

        // Always keep self in activePeers if joined
        if (myName.isNotBlank()) {
            activePeers[myName.lowercase()] = myName
            peerLastSeen[myName.lowercase()] = System.currentTimeMillis()
        }

        val speaker = newSpeaker
        val memberList = activePeers.values.map { info ->
            RoomMember(
                id = info,
                username = info,
                isSpeaking = speaker != null && info.equals(speaker, ignoreCase = true)
            )
        }.sortedBy { it.username.lowercase() }

        _socketUiState.update { state ->
            state.copy(
                roomMembers = memberList,
                lastSpeakerName = speaker
            )
        }
    }

    private fun checkAndTriggerWebRtcOffers() {
        val myName = _socketUiState.value.username.trim()
        if (myName.isBlank()) return
        val peersToOffer = activePeers.values
            .filter { !it.equals(myName, ignoreCase = true) }
            .filter { myName.compareTo(it, ignoreCase = true) > 0 }
        if (peersToOffer.isNotEmpty()) {
            Log.d(TAG, "Offer initiator ($myName) connecting to peers: $peersToOffer")
            signalingListener?.onPeersReceived(peersToOffer)
        }
    }

    private fun handleSignalMessage(msg: SignalMessage) {
        val myName = _socketUiState.value.username.trim()
        if (msg.sender.isBlank() || msg.sender.equals(myName, ignoreCase = true)) return // ignore self
        if (msg.to != null && !msg.to.equals(myName, ignoreCase = true)) return // not addressed to me

        // Any message from a sender acts as an implicit presence heartbeat
        handlePeerSeen(msg.sender)

        when (msg.type) {
            "peer-join" -> {
                Log.d(TAG, "Received peer-join from ${msg.sender}")
                handlePeerSeen(msg.sender)
                // Acknowledge presence so the newly joined peer discovers us immediately
                broadcastSignal(SignalMessage(type = "peer-ack", sender = myName, to = msg.sender))
                checkAndTriggerWebRtcOffers()
            }
            "peer-ack" -> {
                Log.d(TAG, "Received peer-ack from ${msg.sender}")
                handlePeerSeen(msg.sender)
                checkAndTriggerWebRtcOffers()
            }
            "peer-ping" -> {
                handlePeerSeen(msg.sender)
            }
            "peer-leave" -> {
                Log.d(TAG, "Received peer-leave from ${msg.sender}")
                handlePeerLeft(msg.sender)
            }
            "offer" -> {
                Log.d(TAG, "Received SDP offer from ${msg.sender}")
                msg.sdp?.let { signalingListener?.onOfferReceived(msg.sender, it) }
            }
            "answer" -> {
                Log.d(TAG, "Received SDP answer from ${msg.sender}")
                msg.sdp?.let { signalingListener?.onAnswerReceived(msg.sender, it) }
            }
            "ice-candidate" -> {
                msg.candidate?.let { signalingListener?.onIceCandidateReceived(msg.sender, it) }
            }
            "floor-grant" -> {
                val speaker = msg.sender
                Log.d(TAG, "Floor locked by $speaker (isPriority=${msg.isPriority})")
                if (msg.isPriority == true && FloorManager.floorStatus.value.state == FloorState.TRANSMITTING) {
                    FloorManager.handleFloorRevoked()
                }
                FloorManager.handleFloorLocked(speaker, speaker, System.currentTimeMillis() + 60000)
                updateMemberList(newSpeaker = speaker)
                signalingListener?.onCallStarted()
            }
            "floor-release" -> {
                Log.d(TAG, "Floor released by ${msg.sender}")
                FloorManager.handleFloorIdle()
                updateMemberList(newSpeaker = null)
                signalingListener?.onCallEnded()
            }
        }
    }

    fun leaveRoom() {
        heartbeatJob?.cancel()
        heartbeatJob = null

        val myName = _socketUiState.value.username.trim()
        if (myName.isNotEmpty() && channel != null) {
            broadcastSignal(SignalMessage(type = "peer-leave", sender = myName))
        }

        val currentChannel = channel
        channel = null

        scope.launch {
            try {
                currentChannel?.unsubscribe()
            } catch (e: Exception) {
                Log.e(TAG, "Error unsubscribing channel", e)
            }
        }

        activePeers.clear()
        peerLastSeen.clear()
        _socketUiState.update { 
            it.copy(
                roomId = "", 
                roomName = "", 
                roomMembers = emptyList(), 
                lastSpeakerName = null,
                voiceLinkState = "IDLE",
                detail = "Ready to connect."
            ) 
        }
        FloorManager.reset()
        addLog("Left Squad")
    }

    fun sendOffer(targetPeerId: String, sdp: String) {
        val myName = _socketUiState.value.username.trim()
        broadcastSignal(SignalMessage(type = "offer", sender = myName, to = targetPeerId, sdp = sdp))
    }

    fun sendAnswer(targetPeerId: String, sdp: String) {
        val myName = _socketUiState.value.username.trim()
        broadcastSignal(SignalMessage(type = "answer", sender = myName, to = targetPeerId, sdp = sdp))
    }

    fun sendIceCandidate(targetPeerId: String, candidate: String) {
        val myName = _socketUiState.value.username.trim()
        broadcastSignal(SignalMessage(type = "ice-candidate", sender = myName, to = targetPeerId, candidate = candidate))
    }

    private fun broadcastSignal(msg: SignalMessage) {
        scope.launch {
            try {
                channel?.broadcast("webrtc", msg)
            } catch (e: Exception) { 
                Log.e(TAG, "Broadcast error for message type ${msg.type}", e) 
            }
        }
    }

    fun sendStartVoice(isPriority: Boolean = false) {
        val myName = _socketUiState.value.username.trim()
        val roomId = _socketUiState.value.roomId
        if (myName.isNotEmpty() && roomId.isNotEmpty()) {
            broadcastSignal(SignalMessage(
                type = "floor-grant",
                sender = myName,
                isPriority = isPriority,
                timestamp = System.currentTimeMillis()
            ))
            updateMemberList(newSpeaker = myName)
            signalingListener?.onCallStarted()
        }
    }

    fun sendStopVoice() {
        val myName = _socketUiState.value.username.trim()
        val roomId = _socketUiState.value.roomId
        if (myName.isNotEmpty() && roomId.isNotEmpty()) {
            broadcastSignal(SignalMessage(
                type = "floor-release",
                sender = myName,
                timestamp = System.currentTimeMillis()
            ))
            updateMemberList(newSpeaker = null)
            signalingListener?.onCallEnded()
        }
    }

    fun emitRequestFloor(isPriority: Boolean = false) {
        FloorManager.handleFloorGranted(System.currentTimeMillis() + 60000)
    }

    fun emitReleaseFloor() {
        sendStopVoice()
    }

    fun updateVoiceLinkState(state: String) {
        _socketUiState.update { it.copy(voiceLinkState = state) }
    }

    fun updateActivity() {
        _socketUiState.update { it.copy(lastActivityTimestamp = System.currentTimeMillis()) }
    }

    fun addLog(msg: String) {
        Log.d(TAG, "LOG: $msg")
        _socketUiState.update {
            val logs = it.eventLog.takeLast(19).toMutableList()
            logs.add(msg)
            it.copy(eventLog = logs)
        }
    }
}
