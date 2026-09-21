package com.example.walkietalkieapp.socket

import android.util.Log
import com.example.walkietalkieapp.chat.TacticalChatManager
import com.example.walkietalkieapp.crypto.E2ECryptoManager
import com.example.walkietalkieapp.floor.FloorManager
import com.example.walkietalkieapp.floor.FloorState
import com.example.walkietalkieapp.supabase.SupabaseClientManager
import io.github.jan.supabase.realtime.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.Serializable
import org.json.JSONObject
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
    val lastSpeakerTimestamp: Long = 0,
    val isE2EActive: Boolean = false,
    val e2eFingerprint: String = ""
)

@Serializable
data class SignalMessage(
    val type: String = "", // "offer", "answer", "ice-candidate", "floor-grant", "floor-release", "peer-join", "peer-ack", "peer-ping", "peer-leave", "key-exchange", "tactical-msg"
    val sender: String = "",
    val to: String? = null,
    val sdp: String? = null,
    val candidate: String? = null,
    val isPriority: Boolean? = null,
    val timestamp: Long = 0L,
    val publicKey: String? = null,
    val encryptedPayload: String? = null,
    val textContent: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationLabel: String? = null,
    val isBeacon: Boolean? = null
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

    fun joinRoom(roomId: String, username: String, roomName: String = "", roomCode: String = "") {
        if (roomId.isEmpty()) return
        val cleanId = roomId.trim().uppercase()
        val cleanUsername = username.trim()
        FloorManager.myUsername = cleanUsername
        leaveRoom()

        val effectiveCode = if (roomCode.isNotBlank()) roomCode.trim().uppercase() else cleanId
        E2ECryptoManager.deriveSquadKey(cleanId, effectiveCode)

        val myPubKey = try {
            E2ECryptoManager.initSession()
            E2ECryptoManager.getMyPublicKeyBase64()
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing E2EE session: ${e.message}")
            null
        }

        _socketUiState.update { 
            it.copy(
                roomId = cleanId, 
                roomName = if (roomName.isNotEmpty()) roomName else cleanId, 
                username = cleanUsername,
                detail = "Joining $cleanId...",
                isE2EActive = false,
                e2eFingerprint = E2ECryptoManager.getMyFingerprint()
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

                // Announce presence immediately via Broadcast peer-join with public key
                broadcastSignal(SignalMessage(type = "peer-join", sender = cleanUsername, publicKey = myPubKey))

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
            if (clean.equals(_socketUiState.value.lastSpeakerName, ignoreCase = true)) {
                Log.d(TAG, "Active floor holder $clean left — resetting floor to IDLE")
                FloorManager.handleFloorIdle()
                updateMemberList(newSpeaker = null)
                signalingListener?.onCallEnded()
            } else {
                updateMemberList()
            }
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

        // Ingest peer public key if attached to any message
        msg.publicKey?.let { pubKey ->
            val registered = E2ECryptoManager.registerPeerPublicKey(msg.sender, pubKey)
            if (registered) {
                _socketUiState.update { it.copy(isE2EActive = E2ECryptoManager.getEncryptedPeersCount() > 0) }
                // If we don't have this peer recorded or if they joined, share our public key
                val myKey = E2ECryptoManager.getMyPublicKeyBase64()
                if (myKey != null && (msg.type == "peer-join" || msg.type == "key-exchange")) {
                    broadcastSignal(SignalMessage(type = "key-exchange", sender = myName, to = msg.sender, publicKey = myKey))
                }
            }
        }

        when (msg.type) {
            "key-exchange" -> {
                Log.d(TAG, "Received E2EE key-exchange from ${msg.sender}")
                msg.publicKey?.let { pubKey ->
                    if (E2ECryptoManager.registerPeerPublicKey(msg.sender, pubKey)) {
                        _socketUiState.update { it.copy(isE2EActive = E2ECryptoManager.getEncryptedPeersCount() > 0) }
                    }
                }
            }
            "peer-join" -> {
                Log.d(TAG, "Received peer-join from ${msg.sender}")
                handlePeerSeen(msg.sender)
                // Acknowledge presence so the newly joined peer discovers us immediately, including our public key
                val myKey = E2ECryptoManager.getMyPublicKeyBase64()
                broadcastSignal(SignalMessage(type = "peer-ack", sender = myName, to = msg.sender, publicKey = myKey))
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
                E2ECryptoManager.removePeer(msg.sender)
                _socketUiState.update { it.copy(isE2EActive = E2ECryptoManager.getEncryptedPeersCount() > 0) }
                handlePeerLeft(msg.sender)
            }
            "offer" -> {
                Log.d(TAG, "Received SDP offer from ${msg.sender} (encrypted=${msg.encryptedPayload != null})")
                val effectiveSdp = if (msg.encryptedPayload != null) {
                    val decrypted = E2ECryptoManager.decryptFromPeer(msg.sender, msg.encryptedPayload)
                        ?: E2ECryptoManager.decryptSquadPayload(msg.encryptedPayload)
                    if (decrypted != null) {
                        Log.d(TAG, "Successfully decrypted SDP offer from ${msg.sender} with AES-256-GCM")
                        decrypted
                    } else {
                        Log.e(TAG, "Failed to decrypt SDP offer from ${msg.sender}, checking plaintext")
                        msg.sdp
                    }
                } else {
                    msg.sdp
                }
                effectiveSdp?.let { signalingListener?.onOfferReceived(msg.sender, it) }
            }
            "answer" -> {
                Log.d(TAG, "Received SDP answer from ${msg.sender} (encrypted=${msg.encryptedPayload != null})")
                val effectiveSdp = if (msg.encryptedPayload != null) {
                    val decrypted = E2ECryptoManager.decryptFromPeer(msg.sender, msg.encryptedPayload)
                        ?: E2ECryptoManager.decryptSquadPayload(msg.encryptedPayload)
                    if (decrypted != null) {
                        Log.d(TAG, "Successfully decrypted SDP answer from ${msg.sender} with AES-256-GCM")
                        decrypted
                    } else {
                        Log.e(TAG, "Failed to decrypt SDP answer from ${msg.sender}, checking plaintext")
                        msg.sdp
                    }
                } else {
                    msg.sdp
                }
                effectiveSdp?.let { signalingListener?.onAnswerReceived(msg.sender, it) }
            }
            "ice-candidate" -> {
                val effectiveCandidate = if (msg.encryptedPayload != null) {
                    E2ECryptoManager.decryptFromPeer(msg.sender, msg.encryptedPayload)
                        ?: E2ECryptoManager.decryptSquadPayload(msg.encryptedPayload)
                        ?: msg.candidate
                } else {
                    msg.candidate
                }
                effectiveCandidate?.let { signalingListener?.onIceCandidateReceived(msg.sender, it) }
            }
            "floor-grant" -> {
                val speaker = msg.sender
                Log.d(TAG, "Floor claim received from $speaker (isPriority=${msg.isPriority}, timestamp=${msg.timestamp})")
                val expiresAt = if (msg.timestamp > 0) msg.timestamp + 20000L else System.currentTimeMillis() + 20000L
                FloorManager.handleFloorClaimReceived(
                    remoteSpeaker = speaker,
                    remoteTimestamp = msg.timestamp,
                    remoteIsPriority = msg.isPriority == true,
                    expiresAt = expiresAt
                )
                updateMemberList(newSpeaker = speaker)
                signalingListener?.onCallStarted()
            }
            "floor-release" -> {
                val currentSpeaker = _socketUiState.value.lastSpeakerName ?: FloorManager.floorStatus.value.currentSpeakerName
                Log.d(TAG, "Floor release received from ${msg.sender} (currentSpeaker=$currentSpeaker)")
                if (currentSpeaker == null || msg.sender.equals(currentSpeaker, ignoreCase = true) || msg.isPriority == true) {
                    FloorManager.handleFloorIdle()
                    updateMemberList(newSpeaker = null)
                    signalingListener?.onCallEnded()
                } else {
                    Log.d(TAG, "Ignoring floor-release from ${msg.sender} because active speaker is $currentSpeaker")
                }
            }
            "tactical-msg" -> {
                Log.d(TAG, "Received tactical-msg from ${msg.sender} (encrypted=${msg.encryptedPayload != null})")
                if (msg.encryptedPayload != null) {
                    val decrypted = E2ECryptoManager.decryptSquadPayload(msg.encryptedPayload)
                    if (decrypted != null) {
                        try {
                            val obj = JSONObject(decrypted)
                            val text = obj.optString("text", "")
                            val lat = if (obj.has("lat") && !obj.isNull("lat")) obj.optDouble("lat") else null
                            val lng = if (obj.has("lng") && !obj.isNull("lng")) obj.optDouble("lng") else null
                            val lbl = if (obj.has("label") && !obj.isNull("label")) obj.optString("label") else null
                            val beacon = obj.optBoolean("beacon", false)

                            TacticalChatManager.receiveMessage(
                                sender = msg.sender,
                                content = text,
                                timestamp = if (msg.timestamp > 0) msg.timestamp else System.currentTimeMillis(),
                                latitude = lat,
                                longitude = lng,
                                locationLabel = lbl,
                                isBeacon = beacon
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing decrypted tactical-msg JSON", e)
                        }
                    } else {
                        Log.e(TAG, "Failed to decrypt tactical-msg from ${msg.sender}")
                    }
                } else {
                    TacticalChatManager.receiveMessage(
                        sender = msg.sender,
                        content = msg.textContent ?: "",
                        timestamp = if (msg.timestamp > 0) msg.timestamp else System.currentTimeMillis(),
                        latitude = msg.latitude,
                        longitude = msg.longitude,
                        locationLabel = msg.locationLabel,
                        isBeacon = msg.isBeacon == true
                    )
                }
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
        E2ECryptoManager.resetSession()
        _socketUiState.update { 
            it.copy(
                roomId = "", 
                roomName = "", 
                roomMembers = emptyList(), 
                lastSpeakerName = null,
                voiceLinkState = "IDLE",
                detail = "Ready to connect.",
                isE2EActive = false,
                e2eFingerprint = ""
            ) 
        }
        FloorManager.reset()
        addLog("Left Squad")
    }

    fun sendOffer(targetPeerId: String, sdp: String) {
        val myName = _socketUiState.value.username.trim()
        val myPubKey = E2ECryptoManager.getMyPublicKeyBase64()
        val encrypted = E2ECryptoManager.encryptForPeer(targetPeerId, sdp)
            ?: E2ECryptoManager.encryptSquadPayload(sdp)

        if (encrypted != null) {
            Log.d(TAG, "Sending AES-256-GCM encrypted SDP offer to $targetPeerId")
            broadcastSignal(SignalMessage(type = "offer", sender = myName, to = targetPeerId, encryptedPayload = encrypted, publicKey = myPubKey))
        } else {
            Log.d(TAG, "Sending plaintext SDP offer to $targetPeerId (crypto fallback)")
            broadcastSignal(SignalMessage(type = "offer", sender = myName, to = targetPeerId, sdp = sdp, publicKey = myPubKey))
        }
    }

    fun sendAnswer(targetPeerId: String, sdp: String) {
        val myName = _socketUiState.value.username.trim()
        val myPubKey = E2ECryptoManager.getMyPublicKeyBase64()
        val encrypted = E2ECryptoManager.encryptForPeer(targetPeerId, sdp)
            ?: E2ECryptoManager.encryptSquadPayload(sdp)

        if (encrypted != null) {
            Log.d(TAG, "Sending AES-256-GCM encrypted SDP answer to $targetPeerId")
            broadcastSignal(SignalMessage(type = "answer", sender = myName, to = targetPeerId, encryptedPayload = encrypted, publicKey = myPubKey))
        } else {
            Log.d(TAG, "Sending plaintext SDP answer to $targetPeerId (crypto fallback)")
            broadcastSignal(SignalMessage(type = "answer", sender = myName, to = targetPeerId, sdp = sdp, publicKey = myPubKey))
        }
    }

    fun sendIceCandidate(targetPeerId: String, candidate: String) {
        val myName = _socketUiState.value.username.trim()
        val myPubKey = E2ECryptoManager.getMyPublicKeyBase64()
        val encrypted = E2ECryptoManager.encryptForPeer(targetPeerId, candidate)
            ?: E2ECryptoManager.encryptSquadPayload(candidate)

        if (encrypted != null) {
            broadcastSignal(SignalMessage(type = "ice-candidate", sender = myName, to = targetPeerId, encryptedPayload = encrypted, publicKey = myPubKey))
        } else {
            broadcastSignal(SignalMessage(type = "ice-candidate", sender = myName, to = targetPeerId, candidate = candidate, publicKey = myPubKey))
        }
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

    fun sendStartVoice(isPriority: Boolean = false, timestamp: Long = System.currentTimeMillis()) {
        val myName = _socketUiState.value.username.trim()
        val roomId = _socketUiState.value.roomId
        val myPubKey = E2ECryptoManager.getMyPublicKeyBase64()
        if (myName.isNotEmpty() && roomId.isNotEmpty()) {
            broadcastSignal(SignalMessage(
                type = "floor-grant",
                sender = myName,
                isPriority = isPriority,
                timestamp = timestamp,
                publicKey = myPubKey
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
        FloorManager.requestFloor(isPriority)
    }

    fun emitReleaseFloor() {
        sendStopVoice()
    }

    fun sendTacticalMessage(
        textContent: String,
        latitude: Double? = null,
        longitude: Double? = null,
        locationLabel: String? = null,
        isBeacon: Boolean = false
    ) {
        val myName = _socketUiState.value.username.trim()
        val roomId = _socketUiState.value.roomId
        if (myName.isNotEmpty() && roomId.isNotEmpty()) {
            val payloadObj = JSONObject().apply {
                put("text", textContent)
                if (latitude != null) put("lat", latitude)
                if (longitude != null) put("lng", longitude)
                if (locationLabel != null) put("label", locationLabel)
                put("beacon", isBeacon)
            }
            val encrypted = E2ECryptoManager.encryptSquadPayload(payloadObj.toString())
            if (encrypted != null) {
                broadcastSignal(
                    SignalMessage(
                        type = "tactical-msg",
                        sender = myName,
                        encryptedPayload = encrypted,
                        timestamp = System.currentTimeMillis()
                    )
                )
            } else {
                broadcastSignal(
                    SignalMessage(
                        type = "tactical-msg",
                        sender = myName,
                        textContent = textContent,
                        latitude = latitude,
                        longitude = longitude,
                        locationLabel = locationLabel,
                        isBeacon = isBeacon,
                        timestamp = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    fun rekeySession() {
        try {
            E2ECryptoManager.initSession()
            val myPubKey = E2ECryptoManager.getMyPublicKeyBase64()
            val myName = _socketUiState.value.username.trim()
            val newFingerprint = E2ECryptoManager.getMyFingerprint()
            _socketUiState.update { 
                it.copy(
                    e2eFingerprint = newFingerprint,
                    isE2EActive = false
                ) 
            }
            if (myPubKey != null && myName.isNotEmpty()) {
                broadcastSignal(SignalMessage(type = "key-exchange", sender = myName, publicKey = myPubKey))
                addLog("Re-keyed session (ECDH P-256). Fingerprint: $newFingerprint")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error re-keying session", e)
        }
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
