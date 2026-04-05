package com.example.walkietalkieapp.socket

import android.util.Log
import io.socket.client.IO
import io.socket.client.Socket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

private const val TAG = "SocketManager"
// Permanent Railway Server URL
private const val SERVER_URL = "https://walkie-talkie-app-server-production.up.railway.app"
private const val MAX_LOG_ENTRIES = 20

data class SocketUiState(
    val isConnected: Boolean = false,
    val status: String = "Disconnected",
    val detail: String = "Ready to connect.",
    val eventLog: List<String> = listOf("App Started")
)

object SocketManager {
    @Volatile
    private var socket: Socket? = null
    private var signalingListener: SignalingListener? = null

    private val _socketUiState = MutableStateFlow(SocketUiState())
    val socketUiState: StateFlow<SocketUiState> = _socketUiState.asStateFlow()

    fun setSignalingListener(listener: SignalingListener?) {
        signalingListener = listener
    }

    fun initialize() {
        if (socket != null) return

        synchronized(this) {
            if (socket != null) return@synchronized

            val opts = IO.Options().apply {
                forceNew = true
                reconnection = true
                reconnectionDelay = 1000
                timeout = 20000
                // Using websocket only for better stability on Railway
                transports = arrayOf("websocket")
            }

            runCatching { IO.socket(SERVER_URL, opts) }
                .onSuccess { createdSocket ->
                    socket = createdSocket
                    
                    createdSocket.on(Socket.EVENT_CONNECT) {
                        Log.d(TAG, "Socket connected")
                        updateState(true, "ONLINE", "Ready for Walkie-Talkie", "Connected to Server")
                    }

                    createdSocket.on(Socket.EVENT_CONNECT_ERROR) { args ->
                        val err = args.joinToString { it.toString() }
                        Log.e(TAG, "Socket Connect Error: $err")
                        updateState(false, "OFFLINE", "Retry: $err", "Connect Error")
                    }

                    createdSocket.on(Socket.EVENT_DISCONNECT) {
                        Log.d(TAG, "Socket disconnected")
                        updateState(false, "OFFLINE", "Disconnected", "Disconnected")
                    }

                    createdSocket.on("message") { args ->
                        val msg = args.firstOrNull()?.toString().orEmpty()
                        addLog("Msg: $msg")
                    }

                    createdSocket.on("offer") { args ->
                        val sdp = args.firstOrNull()?.toString().orEmpty()
                        Log.d(TAG, "Offer received")
                        updateState(true, "ONLINE", "Voice Incoming...", "Offer Received")
                        signalingListener?.onOfferReceived(sdp)
                    }

                    createdSocket.on("answer") { args ->
                        val sdp = args.firstOrNull()?.toString().orEmpty()
                        Log.d(TAG, "Answer received")
                        updateState(true, "ONLINE", "Voice Connected", "Answer Received")
                        signalingListener?.onAnswerReceived(sdp)
                    }

                    createdSocket.on("ice-candidate") { args ->
                        val candidate = args.firstOrNull()?.toString().orEmpty()
                        signalingListener?.onIceCandidateReceived(candidate)
                    }

                    createdSocket.on("stop-voice") {
                        Log.d(TAG, "Stop voice signal received")
                        addLog("Remote user stopped talking")
                        signalingListener?.onCallEnded()
                    }
                }
                .onFailure { e ->
                    Log.e(TAG, "Socket Creation Failed", e)
                    addLog("Init Error: ${e.message}")
                }
        }
    }

    fun connect() {
        initialize()
        Log.d(TAG, "Attempting to connect to: $SERVER_URL")
        addLog("Connecting to server...")
        socket?.connect()
    }

    fun disconnect() {
        Log.d(TAG, "Disconnecting socket")
        socket?.disconnect()
    }

    fun sendMessage(msg: String) {
        if (socket?.connected() == true) {
            socket?.emit("message", msg)
            addLog("Sent: $msg")
        }
    }

    fun sendOffer(sdp: String) {
        if (socket?.connected() == true) {
            socket?.emit("offer", sdp)
            addLog("Offer Sent")
        } else {
            addLog("Err: Not connected (Offer)")
        }
    }

    fun sendAnswer(sdp: String) {
        if (socket?.connected() == true) {
            socket?.emit("answer", sdp)
            addLog("Answer Sent")
        } else {
            addLog("Err: Not connected (Answer)")
        }
    }

    fun sendIceCandidate(candidate: String) {
        if (socket?.connected() == true) {
            socket?.emit("ice-candidate", candidate)
        }
    }

    fun sendStopVoice() {
        if (socket?.connected() == true) {
            socket?.emit("stop-voice")
            addLog("Stop Voice Sent")
        }
    }

    fun addLog(logEntry: String) {
        _socketUiState.update { currentState ->
            currentState.copy(
                eventLog = (listOf(withTimestamp(logEntry)) + currentState.eventLog).take(MAX_LOG_ENTRIES)
            )
        }
    }

    private fun updateState(isConnected: Boolean, status: String, detail: String, logEntry: String) {
        _socketUiState.update { currentState ->
            currentState.copy(
                isConnected = isConnected,
                status = status,
                detail = detail,
                eventLog = (listOf(withTimestamp(logEntry)) + currentState.eventLog).take(MAX_LOG_ENTRIES)
            )
        }
    }

    private fun withTimestamp(message: String): String {
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        return "[$timestamp] $message"
    }
}
