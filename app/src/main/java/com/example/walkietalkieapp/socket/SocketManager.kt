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
    val username: String = "",
    val roomMembers: List<RoomMember> = emptyList(),
    val voiceLinkState: String = "IDLE",
    val lastActivityTimestamp: Long = System.currentTimeMillis(),
    val lastSpeakerName: String? = null,
    val lastSpeakerTimestamp: Long = 0
)

object SocketManager {
    private const val TAG = "SocketManager"
    private const val SERVER_URL = "https://walkie-talkie-app-server-production.up.railway.app"

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
                    val data = args.firstOrNull() as? org.json.JSONArray ?: return@on
                    val members = mutableListOf<RoomMember>()
                    for (i in 0 until data.length()) {
                        val obj = data.getJSONObject(i)
                        members.add(RoomMember(obj.getString("id"), obj.getString("username"), obj.getBoolean("isSpeaking")))
                    }
                    
                    val currentlySpeaking = members.find { it.isSpeaking }
                    _socketUiState.update { state ->
                        var newState = state.copy(roomMembers = members)
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
                    val data = args.firstOrNull()
                    val username = when (data) {
                        is JSONObject -> data.optString("username", "Someone")
                        is String -> data
                        else -> "Someone"
                    }
                    val msg = "$username left the squad"
                    addLog(msg)
                    _events.tryEmit(msg)
                    updateActivity()
                }

                on("offer") { args ->
                    val data = args.firstOrNull() as? JSONObject ?: return@on
                    signalingListener?.onOfferReceived(data.optString("sdp"))
                    signalingListener?.onCallStarted()
                }

                on("answer") { args ->
                    val data = args.firstOrNull() as? JSONObject ?: return@on
                    signalingListener?.onAnswerReceived(data.optString("sdp"))
                    signalingListener?.onCallStarted()
                }

                on("ice-candidate") { args ->
                    signalingListener?.onIceCandidateReceived(args.firstOrNull()?.toString() ?: "")
                }

                on("stop-voice") { signalingListener?.onCallEnded() }
            }
        } catch (e: URISyntaxException) { Log.e(TAG, "Socket init failed", e) }
    }

    fun connect() {
        if (socket?.connected() == false) {
            addLog("Attempting connection...")
            socket?.connect()
        }
    }

    fun disconnect() { socket?.disconnect() }

    fun createRoom(username: String) {
        val code = (1..6).map { (('A'..'Z') + ('0'..'9')).random() }.joinToString("")
        joinRoom(code, username)
    }

    fun joinRoom(roomId: String, username: String) {
        if (roomId.isEmpty()) {
            val oldRoomId = _socketUiState.value.roomId
            if (oldRoomId.isNotEmpty()) {
                socket?.emit("leave-room", JSONObject().apply { put("roomId", oldRoomId) })
            }
            _socketUiState.update { it.copy(roomId = "", roomMembers = emptyList()) }
            return
        }
        
        val cleanId = roomId.trim().uppercase()
        _socketUiState.update { it.copy(roomId = cleanId, username = username) }
        
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
        socket?.emit("offer", JSONObject().apply { 
            put("sdp", sdp) 
            put("roomId", roomId)
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
    fun sendStopVoice() { 
        val roomId = _socketUiState.value.roomId
        socket?.emit("stop-voice", JSONObject().apply { put("roomId", roomId) }) 
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
