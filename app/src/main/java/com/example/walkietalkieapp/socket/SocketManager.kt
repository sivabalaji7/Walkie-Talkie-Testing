package com.example.walkietalkieapp.socket

import android.util.Log
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject
import java.net.URISyntaxException
import com.example.walkietalkieapp.floor.FloorManager

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

object SocketManager {
    private const val TAG = "SocketManager"
    private const val SERVER_URL = "https://walkie-talkie-app-server.onrender.com"

    private var socket: Socket? = null
    private var signalingListener: SignalingListener? = null

    private val _socketUiState = MutableStateFlow(SocketUiState())
    val socketUiState: StateFlow<SocketUiState> = _socketUiState.asStateFlow()

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 10)
    val events = _events.asSharedFlow()

    fun setSignalingListener(listener: SignalingListener?) {
        this.signalingListener = listener
    }

    fun initialize() {
        if (socket != null) return
        try {
            val opts = IO.Options().apply {
                // Allow both for better compatibility
                transports = arrayOf("polling", "websocket") 
                forceNew = true
                reconnection = true
                reconnectionDelay = 1000
                timeout = 10000
            }

            socket = IO.socket(SERVER_URL, opts).apply {
                on(Socket.EVENT_CONNECT) {
                    _socketUiState.update { it.copy(isConnected = true, status = "ONLINE", detail = "Connected") }
                    addLog("Connected to Server")
                    
                    val state = _socketUiState.value
                    if (state.roomId.isNotEmpty()) {
                        emitJoin(state.roomId, state.username)
                    }
                }

                on(Socket.EVENT_DISCONNECT) {
                    _socketUiState.update { it.copy(isConnected = false, status = "OFFLINE", detail = "Disconnected") }
                    addLog("Connection Lost")
                }

                on(Socket.EVENT_CONNECT_ERROR) { args ->
                    val err = args.firstOrNull()?.toString() ?: "Connection Error"
                    Log.e(TAG, "Connect Error: $err")
                    // Adding "Retry" to trigger the Yellow UI state in MainActivity
                    _socketUiState.update { it.copy(status = "OFFLINE", detail = "Retrying: $err") }
                }

                on("room-update") { args ->
                    if (_socketUiState.value.roomId.isEmpty()) return@on
                    val raw = args.firstOrNull() ?: return@on
                    val jsonArray: org.json.JSONArray? = when (raw) {
                        is org.json.JSONArray -> raw
                        is JSONObject -> raw.optJSONArray("members") ?: raw.optJSONArray("users") ?: raw.optJSONArray("roomMembers")
                        is String -> {
                            try {
                                if (raw.trim().startsWith("[")) org.json.JSONArray(raw)
                                else {
                                    val obj = JSONObject(raw)
                                    obj.optJSONArray("members") ?: obj.optJSONArray("users") ?: obj.optJSONArray("roomMembers")
                                }
                            } catch (e: Exception) {
                                null
                            }
                        }
                        else -> null
                    }
                    if (jsonArray == null) return@on

                    val currentSpeakingUser = _socketUiState.value.roomMembers.find { it.isSpeaking }?.username
                    val members = mutableListOf<RoomMember>()
                    for (i in 0 until jsonArray.length()) {
                        val item = jsonArray.opt(i)
                        val uname = when (item) {
                            is JSONObject -> item.optString("username", item.optString("name", item.optString("user", "")))
                            is String -> item
                            else -> ""
                        }
                        val id = if (item is JSONObject) item.optString("id", uname) else uname
                        val isSpk = if (item is JSONObject) item.optBoolean("isSpeaking", false) else false
                        if (uname.isNotBlank()) {
                            val activeSpk = if (currentSpeakingUser != null) uname.equals(currentSpeakingUser, ignoreCase = true) else isSpk
                            members.add(RoomMember(id, uname, activeSpk))
                        }
                    }
                    
                    val deduped = members.distinctBy { it.username.trim().lowercase() }
                    _socketUiState.update { state ->
                        state.copy(roomMembers = deduped)
                    }
                }

                on("user-joined") { args ->
                    if (_socketUiState.value.roomId.isEmpty()) return@on
                    val data = args.firstOrNull()
                    val peerId = when (data) {
                        is JSONObject -> data.optString("id", "")
                        else -> ""
                    }
                    val username = when (data) {
                        is JSONObject -> data.optString("username", data.optString("name", data.optString("user", "")))
                        is String -> {
                            try {
                                val json = JSONObject(data)
                                json.optString("username", json.optString("name", json.optString("user", data)))
                            } catch (e: Exception) {
                                data
                            }
                        }
                        else -> ""
                    }
                    if (peerId.isNotEmpty()) {
                        signalingListener?.onPeersReceived(listOf(peerId))
                    }
                    if (username.isNotBlank()) {
                        val msg = "$username joined the squad"
                        addLog(msg)
                        _events.tryEmit(msg)
                        _socketUiState.update { state ->
                            val exists = state.roomMembers.any { it.username.equals(username, ignoreCase = true) }
                            if (!exists) {
                                state.copy(roomMembers = state.roomMembers + RoomMember(username, username, false))
                            } else {
                                state
                            }
                        }
                    }
                    updateActivity()
                }

                val onUserLeftHandler: (Array<Any>) -> Unit = { args ->
                    if (_socketUiState.value.roomId.isNotEmpty()) {
                        val data = args.firstOrNull()
                        val peerId = when (data) {
                            is JSONObject -> data.optString("id", "")
                            else -> ""
                        }
                        if (peerId.isNotEmpty()) {
                            signalingListener?.onPeerLeft(peerId)
                        }

                        val username = when (data) {
                            is JSONObject -> {
                                data.optString("username", data.optString("name", data.optString("user", data.optString("sender", data.optString("userId", data.optString("id", ""))))))
                            }
                            is String -> {
                                try {
                                    val json = JSONObject(data)
                                    json.optString("username", json.optString("name", json.optString("user", json.optString("sender", json.optString("userId", json.optString("id", data))))))
                                } catch (e: Exception) {
                                    data
                                }
                            }
                            else -> ""
                        }
                        if (username.isNotBlank()) {
                            val msg = "$username left the squad"
                            addLog(msg)
                            _events.tryEmit(msg)
                            _socketUiState.update { state ->
                                state.copy(
                                    roomMembers = state.roomMembers.filter { 
                                        !it.username.equals(username, ignoreCase = true) && 
                                        !it.id.equals(username, ignoreCase = true) 
                                    }
                                )
                            }
                        }
                        updateActivity()
                    }
                }

                on("user-left", onUserLeftHandler)
                on("userLeft", onUserLeftHandler)
                on("user_left", onUserLeftHandler)
                on("member-left", onUserLeftHandler)
                on("peer-disconnected", onUserLeftHandler)
                on("member-left", onUserLeftHandler)
                on("peer-disconnected", onUserLeftHandler)

                // ── MULTI-PEER MESH SIGNALING ──────────────────────
                on("room-peers") { args ->
                    if (_socketUiState.value.roomId.isEmpty()) return@on
                    val data = args.firstOrNull()
                    if (data is JSONObject) {
                        val peersArray = data.optJSONArray("peers")
                        val peerIds = mutableListOf<String>()
                        if (peersArray != null) {
                            for (i in 0 until peersArray.length()) {
                                val p = peersArray.optJSONObject(i)
                                val pId = p?.optString("id", "") ?: ""
                                if (pId.isNotEmpty()) peerIds.add(pId)
                            }
                        }
                        Log.d(TAG, "Received room-peers: $peerIds")
                        signalingListener?.onPeersReceived(peerIds)
                    }
                }

                on("offer") { args ->
                    if (_socketUiState.value.roomId.isEmpty()) return@on
                    val data = args.firstOrNull()
                    val fromPeerId = when (data) {
                        is JSONObject -> data.optString("from", "")
                        else -> ""
                    }
                    val sdp = when (data) {
                        is JSONObject -> data.optString("sdp", "")
                        is String -> {
                            try {
                                JSONObject(data).optString("sdp", data)
                            } catch (e: Exception) {
                                data
                            }
                        }
                        else -> ""
                    }
                    if (sdp.isNotBlank()) {
                        Log.d(TAG, "Received offer from $fromPeerId")
                        signalingListener?.onOfferReceived(fromPeerId, sdp)
                    }
                }

                on("answer") { args ->
                    if (_socketUiState.value.roomId.isEmpty()) return@on
                    val data = args.firstOrNull()
                    val fromPeerId = when (data) {
                        is JSONObject -> data.optString("from", "")
                        else -> ""
                    }
                    val sdp = when (data) {
                        is JSONObject -> data.optString("sdp", "")
                        is String -> {
                            try {
                                JSONObject(data).optString("sdp", data)
                            } catch (e: Exception) {
                                data
                            }
                        }
                        else -> ""
                    }
                    if (sdp.isNotBlank()) {
                        Log.d(TAG, "Received answer from $fromPeerId")
                        signalingListener?.onAnswerReceived(fromPeerId, sdp)
                    }
                }

                on("ice-candidate") { args ->
                    if (_socketUiState.value.roomId.isEmpty()) return@on
                    val data = args.firstOrNull()
                    val fromPeerId = when (data) {
                        is JSONObject -> data.optString("from", "")
                        else -> ""
                    }
                    val candidateStr = when (data) {
                        is JSONObject -> {
                            val cand = data.opt("candidate")
                            if (cand is JSONObject) cand.toString()
                            else if (cand is String) cand
                            else data.toString()
                        }
                        is String -> data
                        else -> ""
                    }
                    if (candidateStr.isNotBlank()) {
                        signalingListener?.onIceCandidateReceived(fromPeerId, candidateStr)
                    }
                }

                val onStartVoiceHandler: (Array<Any>) -> Unit = { args ->
                    if (_socketUiState.value.roomId.isNotEmpty()) {
                        val data = args.firstOrNull()
                        val sender = when (data) {
                            is JSONObject -> {
                                data.optString("username", data.optString("sender", data.optString("name", data.optString("user", ""))))
                            }
                            is String -> {
                                try {
                                    val json = JSONObject(data)
                                    json.optString("username", json.optString("sender", json.optString("name", json.optString("user", data))))
                                } catch (e: Exception) {
                                    data
                                }
                            }
                            else -> ""
                        }
                        _socketUiState.update { state ->
                            val resolvedSender = if (sender.isNotBlank() && !sender.startsWith("{")) sender 
                                else state.roomMembers.find { !it.username.equals(state.username, ignoreCase = true) }?.username ?: ""
                            
                            val updatedMembers = if (resolvedSender.isNotBlank()) {
                                val exists = state.roomMembers.any { it.username.equals(resolvedSender, ignoreCase = true) }
                                if (exists) {
                                    state.roomMembers.map { m ->
                                        if (m.username.equals(resolvedSender, ignoreCase = true)) m.copy(isSpeaking = true) else m.copy(isSpeaking = false)
                                    }
                                } else {
                                    state.roomMembers.map { it.copy(isSpeaking = false) } + RoomMember(resolvedSender, resolvedSender, isSpeaking = true)
                                }
                            } else {
                                state.roomMembers
                            }
                            state.copy(
                                lastSpeakerName = if (resolvedSender.isNotBlank()) resolvedSender else state.lastSpeakerName,
                                lastSpeakerTimestamp = System.currentTimeMillis(),
                                roomMembers = updatedMembers
                            )
                        }
                        signalingListener?.onCallStarted()
                    }
                }

                on("start-voice", onStartVoiceHandler)
                on("startVoice", onStartVoiceHandler)
                on("start_voice", onStartVoiceHandler)

                val onStopVoiceHandler: (Array<Any>) -> Unit = {
                    if (_socketUiState.value.roomId.isNotEmpty()) {
                        _socketUiState.update { state ->
                            state.copy(
                                roomMembers = state.roomMembers.map { it.copy(isSpeaking = false) }
                            )
                        }
                        signalingListener?.onCallEnded()
                    }
                }

                on("stop-voice", onStopVoiceHandler)
                on("stopVoice", onStopVoiceHandler)
                on("stop_voice", onStopVoiceHandler)

                // ── FLOOR ARBITRATION PROTOCOL ──────────────────────
                on("floor-granted") { args ->
                    val data = args.firstOrNull()
                    val expiresAt = when (data) {
                        is JSONObject -> data.optLong("expiresAt", System.currentTimeMillis() + 20000)
                        else -> System.currentTimeMillis() + 20000
                    }
                    FloorManager.handleFloorGranted(expiresAt)
                }

                on("floor-denied") { args ->
                    val data = args.firstOrNull()
                    val reason = if (data is JSONObject) data.optString("reason", "CHANNEL_BUSY") else "CHANNEL_BUSY"
                    val speaker = if (data is JSONObject) data.optString("currentSpeakerName", "Someone") else "Someone"
                    FloorManager.handleFloorDenied(reason, speaker)
                }

                on("floor-status") { args ->
                    val data = args.firstOrNull()
                    if (data is JSONObject) {
                        val state = data.optString("state", "IDLE")
                        if (state.equals("LOCKED", ignoreCase = true)) {
                            val speakerId = data.optString("speakerId", "")
                            val speakerName = data.optString("speakerName", "")
                            val expiresAt = data.optLong("expiresAt", 0)
                            FloorManager.handleFloorLocked(speakerId, speakerName, expiresAt)
                        } else if (state.equals("IDLE", ignoreCase = true)) {
                            FloorManager.handleFloorIdle()
                        }
                    }
                }

                on("floor-revoked") {
                    FloorManager.handleFloorRevoked()
                }

                on("floor-warning") {
                    FloorManager.handleFloorWarning()
                }

                on("floor-timeout") {
                    FloorManager.handleFloorTimedOut()
                }
            }
        } catch (e: URISyntaxException) { Log.e(TAG, "Socket init failed", e) }
    }

    fun connect() {
        if (socket?.connected() == false) {
            addLog("Attempting connection...")
            socket?.connect()
        }
    }

    fun leaveRoom() {
        val oldRoomId = _socketUiState.value.roomId
        val username = _socketUiState.value.username
        if (oldRoomId.isNotEmpty()) {
            try {
                val payload = JSONObject().apply { 
                    put("roomId", oldRoomId) 
                    put("username", username)
                    put("userId", username)
                    put("name", username)
                    put("sender", username)
                }
                socket?.emit("leave-room", payload)
                socket?.emit("leaveRoom", payload)
                socket?.emit("leave_room", payload)
                socket?.emit("leave", payload)
                socket?.emit("leave-room", oldRoomId)
                socket?.emit("user-left", payload)
                socket?.emit("user_left", payload)
                socket?.emit("member-left", payload)
            } catch (e: Exception) {
                Log.e(TAG, "Error emitting leave-room: ${e.message}")
            }
        }
        _socketUiState.update { 
            it.copy(
                roomId = "", 
                roomName = "",
                roomMembers = emptyList(), 
                voiceLinkState = "IDLE", 
                lastSpeakerName = null
            ) 
        }
        FloorManager.reset()
        addLog("Left Squad")
    }

    fun disconnect() {
        leaveRoom()
        try {
            socket?.disconnect()
        } catch (e: Exception) {
            Log.e(TAG, "Error disconnecting socket: ${e.message}")
        }
        FloorManager.reset()
        _socketUiState.update { 
            it.copy(
                isConnected = false, 
                status = "OFFLINE", 
                detail = "Disconnected", 
                roomId = "", 
                roomMembers = emptyList(),
                voiceLinkState = "IDLE",
                lastSpeakerName = null
            ) 
        }
    }

    fun createRoom(username: String) {
        val code = (1..6).map { (('A'..'Z') + ('0'..'9')).random() }.joinToString("")
        joinRoom(code, username)
    }

    fun joinRoom(roomId: String, username: String, roomName: String = "") {
        if (roomId.isEmpty()) {
            leaveRoom()
            return
        }
        
        val cleanId = roomId.trim().uppercase()
        _socketUiState.update { it.copy(roomId = cleanId, roomName = if(roomName.isNotEmpty()) roomName else cleanId, username = username) }
        
        if (socket?.connected() == true) {
            emitJoin(cleanId, username)
        } else {
            addLog("Queued Join: $cleanId")
            connect()
        }
    }

    private fun emitJoin(roomId: String, username: String) {
        val data = JSONObject().apply {
            put("roomId", roomId)
            put("username", username)
        }
        socket?.emit("join-room", data)
        addLog("Joining Squad: $roomId")
    }

    fun sendOffer(targetPeerId: String, sdp: String) { 
        val roomId = _socketUiState.value.roomId
        val username = _socketUiState.value.username
        socket?.emit("offer", JSONObject().apply { 
            put("to", targetPeerId)
            put("sdp", sdp) 
            put("roomId", roomId) 
            put("username", username)
        }) 
    }
    fun sendAnswer(targetPeerId: String, sdp: String) { 
        val roomId = _socketUiState.value.roomId
        socket?.emit("answer", JSONObject().apply { 
            put("to", targetPeerId)
            put("sdp", sdp) 
            put("roomId", roomId) 
        }) 
    }
    fun sendIceCandidate(targetPeerId: String, c: String) { 
        val roomId = _socketUiState.value.roomId
        try { 
            val json = JSONObject(c)
            json.put("to", targetPeerId)
            json.put("roomId", roomId)
            socket?.emit("ice-candidate", JSONObject().apply {
                put("to", targetPeerId)
                put("candidate", json)
                put("roomId", roomId)
            }) 
        } catch(e: Exception) { 
            socket?.emit("ice-candidate", JSONObject().apply {
                put("to", targetPeerId)
                put("candidate", c)
                put("roomId", roomId)
            }) 
        } 
    }
    fun sendStartVoice() { 
        val roomId = _socketUiState.value.roomId
        val username = _socketUiState.value.username
        if (roomId.isNotEmpty()) {
            val payload = JSONObject().apply { 
                put("roomId", roomId) 
                put("username", username)
                put("sender", username)
                put("name", username)
            }
            socket?.emit("start-voice", payload)
            socket?.emit("startVoice", payload)
            socket?.emit("start_voice", payload)
            _socketUiState.update { state ->
                state.copy(
                    lastSpeakerName = username,
                    lastSpeakerTimestamp = System.currentTimeMillis(),
                    roomMembers = state.roomMembers.map { m ->
                        if (m.username.equals(username, ignoreCase = true)) m.copy(isSpeaking = true) else m.copy(isSpeaking = false)
                    }
                )
            }
        }
    }
    fun sendStopVoice() { 
        val roomId = _socketUiState.value.roomId
        val username = _socketUiState.value.username
        if (roomId.isNotEmpty()) {
            val payload = JSONObject().apply { 
                put("roomId", roomId) 
                put("username", username)
                put("sender", username)
                put("name", username)
            }
            socket?.emit("stop-voice", payload)
            socket?.emit("stopVoice", payload)
            socket?.emit("stop_voice", payload)
            _socketUiState.update { state ->
                state.copy(
                    roomMembers = state.roomMembers.map { it.copy(isSpeaking = false) }
                )
            }
        }
    }

    fun emitRequestFloor(isPriority: Boolean = false) {
        val roomId = _socketUiState.value.roomId
        val username = _socketUiState.value.username
        if (roomId.isNotEmpty()) {
            val payload = JSONObject().apply {
                put("roomId", roomId)
                put("username", username)
                put("userId", username)
                put("isPriority", isPriority)
            }
            socket?.emit("request-floor", payload)
        }
    }

    fun emitReleaseFloor() {
        val roomId = _socketUiState.value.roomId
        val username = _socketUiState.value.username
        if (roomId.isNotEmpty()) {
            val payload = JSONObject().apply {
                put("roomId", roomId)
                put("username", username)
            }
            socket?.emit("release-floor", payload)
        }
    }

    fun updateVoiceLinkState(state: String) {
        _socketUiState.update { it.copy(voiceLinkState = state) }
    }

    fun updateActivity() {
        _socketUiState.update { it.copy(lastActivityTimestamp = System.currentTimeMillis()) }
    }

    fun addLog(msg: String) {
        val time = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
        val newLogs = listOf("[$time] $msg") + _socketUiState.value.eventLog
        _socketUiState.update { it.copy(eventLog = newLogs.take(20)) }
    }
}
