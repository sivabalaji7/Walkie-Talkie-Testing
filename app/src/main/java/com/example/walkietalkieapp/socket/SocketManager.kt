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
// IMPORTANT: Update this URL if your ngrok restarted!
private const val SERVER_URL = "https://unforgetting-melodie-overfiercely.ngrok-free.dev" 
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
                        updateState(false, "OFFLINE", "Check ngrok URL", "Connect Error")
                    }

                    createdSocket.on(Socket.EVENT_DISCONNECT) {
                        updateState(false, "OFFLINE", "Disconnected", "Disconnected")
                    }

                    createdSocket.on("message") { args ->
                        val msg = args.firstOrNull()?.toString().orEmpty()
                        updateState(true, "ONLINE", "Message Received", "Msg: $msg")
                    }

                    createdSocket.on("offer") { args ->
                        val sdp = args.firstOrNull()?.toString().orEmpty()
                        updateState(true, "ONLINE", "Voice Incoming...", "Call Started")
                        signalingListener?.onOfferReceived(sdp)
                    }

                    createdSocket.on("answer") { args ->
                        val sdp = args.firstOrNull()?.toString().orEmpty()
                        updateState(true, "ONLINE", "Voice Connected", "Handshake Done")
                        signalingListener?.onAnswerReceived(sdp)
                    }

                    createdSocket.on("ice-candidate") { args ->
                        val candidate = args.firstOrNull()?.toString().orEmpty()
                        signalingListener?.onIceCandidateReceived(candidate)
                    }
                }
        }
    }

    fun connect() {
        initialize()
        socket?.connect()
    }

    fun disconnect() {
        socket?.disconnect()
    }

    fun sendMessage(msg: String) {
        if (socket?.connected() == true) {
            socket?.emit("message", msg)
            updateState(true, "ONLINE", "Test Sent", "Sent: $msg")
        }
    }

    fun sendOffer(sdp: String) {
        socket?.emit("offer", sdp)
    }

    fun sendAnswer(sdp: String) {
        socket?.emit("answer", sdp)
    }

    fun sendIceCandidate(candidate: String) {
        socket?.emit("ice-candidate", candidate)
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
