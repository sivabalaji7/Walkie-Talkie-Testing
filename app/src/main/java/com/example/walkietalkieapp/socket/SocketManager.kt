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
                    val data = args.firstOrNull() as? org.json.JSONArray ?: return@on
                    val currentSpeakingUser = _socketUiState.value.roomMembers.find { it.isSpeaking }?.username
                    val members = mutableListOf<RoomMember>()
                    for (i in 0 until data.length()) {
                        val obj = data.getJSONObject(i)
                        val uname = obj.optString("username", "")
                        if (uname.isNotBlank()) {
                            val isSpk = if (currentSpeakingUser != null) uname.equals(currentSpeakingUser, ignoreCase = true) else obj.optBoolean("isSpeaking", false)
                            members.add(RoomMember(obj.optString("id", uname), uname, isSpk))
                        }
                    }
                    
                    // Deduplicate members strictly by username to avoid duplicate avatar glitches
                    val deduped = members.distinctBy { it.username.trim().lowercase() }
                    
                    val currentlySpeaking = deduped.find { it.isSpeaking }
                    _socketUiState.update { state ->
                        var newState = state.copy(roomMembers = deduped)
                        if (currentlySpeaking != null) {
                            newState = newState.copy(
                                lastSpeakerName = currentlySpeaking.username,
                                lastSpeakerTimestamp = System.currentTimeMillis()
                            )
                        }
                        newState
                    }
                }

                on("user-joined") { args ->
                    if (_socketUiState.value.roomId.isEmpty()) return@on
                    val data = args.firstOrNull()
                    val username = when (data) {
                        is JSONObject -> data.optString("username", "Someone")
                        is String -> data
                        else -> "Someone"
                    }
                    val msg = "$username joined the squad"
                    addLog(msg)
                    _events.tryEmit(msg)
                    updateActivity()
                }

                on("user-left") { args ->
                    if (_socketUiState.value.roomId.isEmpty()) return@on
                    val data = args.firstOrNull()
                    val username = when (data) {
                        is JSONObject -> data.optString("username", "Someone")
                        is String -> data
                        else -> "Someone"
                    }
                    val msg = "$username left the squad"
                    addLog(msg)
                    _events.tryEmit(msg)
                    _socketUiState.update { state ->
                        state.copy(
                            roomMembers = state.roomMembers.filter { !it.username.equals(username, ignoreCase = true) }
                        )
                    }
                    updateActivity()
                }

                on("offer") { args ->
                    if (_socketUiState.value.roomId.isEmpty()) return@on
                    val data = args.firstOrNull() as? JSONObject ?: return@on
                    signalingListener?.onOfferReceived(data.optString("sdp"))
                }

                on("answer") { args ->
                    if (_socketUiState.value.roomId.isEmpty()) return@on
                    val data = args.firstOrNull() as? JSONObject ?: return@on
                    signalingListener?.onAnswerReceived(data.optString("sdp"))
                }

                on("ice-candidate") { args ->
                    if (_socketUiState.value.roomId.isEmpty()) return@on
                    signalingListener?.onIceCandidateReceived(args.firstOrNull()?.toString() ?: "")
                }

                on("start-voice") { args ->
                    if (_socketUiState.value.roomId.isEmpty()) return@on
                    val data = args.firstOrNull()
                    val sender = when (data) {
                        is JSONObject -> data.optString("username", "")
                        is String -> data
                        else -> ""
                    }
                    if (sender.isNotEmpty()) {
                        _socketUiState.update { state ->
                            state.copy(
                                lastSpeakerName = sender,
                                lastSpeakerTimestamp = System.currentTimeMillis(),
                                roomMembers = state.roomMembers.map { m ->
                                    if (m.username.equals(sender, ignoreCase = true)) m.copy(isSpeaking = true) else m.copy(isSpeaking = false)
                                }
                            )
                        }
                    }
                    signalingListener?.onCallStarted()
                }

                on("stop-voice") { 
                    if (_socketUiState.value.roomId.isEmpty()) return@on
                    _socketUiState.update { state ->
                        state.copy(
                            roomMembers = state.roomMembers.map { it.copy(isSpeaking = false) }
                        )
                    }
                    signalingListener?.onCallEnded() 
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
        if (oldRoomId.isNotEmpty()) {
            try {
                socket?.emit("leave-room", JSONObject().apply { put("roomId", oldRoomId) })
            } catch (e: Exception) {
                Log.e(TAG, "Error emitting leave-room: ${e.message}")
            }
        }
        _socketUiState.update { 
            it.copy(
                roomId = "", 
                roomMembers = emptyList(), 
                voiceLinkState = "IDLE", 
                lastSpeakerName = null
            ) 
        }
        addLog("Left Squad")
    }

    fun disconnect() {
        leaveRoom()
        try {
            socket?.disconnect()
        } catch (e: Exception) {
            Log.e(TAG, "Error disconnecting socket: ${e.message}")
        }
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

    fun sendOffer(sdp: String) { 
        val roomId = _socketUiState.value.roomId
        val username = _socketUiState.value.username
        socket?.emit("offer", JSONObject().apply { 
            put("sdp", sdp) 
            put("roomId", roomId)
            put("username", username)
        }) 
    }
    fun sendAnswer(sdp: String) { 
        val roomId = _socketUiState.value.roomId
        socket?.emit("answer", JSONObject().apply { 
            put("sdp", sdp) 
            put("roomId", roomId)
        }) 
    }
    fun sendIceCandidate(c: String) { 
        val roomId = _socketUiState.value.roomId
        try { 
            val json = JSONObject(c)
            json.put("roomId", roomId)
            socket?.emit("ice-candidate", json) 
        } catch(e:Exception) { 
            socket?.emit("ice-candidate", c) 
        } 
    }
    fun sendStartVoice() { 
        val roomId = _socketUiState.value.roomId
        val username = _socketUiState.value.username
        if (roomId.isNotEmpty()) {
            socket?.emit("start-voice", JSONObject().apply { 
                put("roomId", roomId) 
                put("username", username)
            }) 
            _socketUiState.update { state ->
                state.copy(
                    roomMembers = state.roomMembers.map { m ->
                        if (m.username == username) m.copy(isSpeaking = true) else m
                    }
                )
            }
        }
    }
    fun sendStopVoice() { 
        val roomId = _socketUiState.value.roomId
        val username = _socketUiState.value.username
        if (roomId.isNotEmpty()) {
            socket?.emit("stop-voice", JSONObject().apply { 
                put("roomId", roomId) 
                put("username", username)
            }) 
            _socketUiState.update { state ->
                state.copy(
                    roomMembers = state.roomMembers.map { m ->
                        if (m.username == username) m.copy(isSpeaking = false) else m
                    }
                )
            }
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
