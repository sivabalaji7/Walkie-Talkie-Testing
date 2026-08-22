package com.example.walkietalkieapp.webrtc

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.walkietalkieapp.MainActivity
import com.example.walkietalkieapp.R
import com.example.walkietalkieapp.socket.SignalingListener
import com.example.walkietalkieapp.socket.SocketManager

private const val TAG = "WalkieTalkieService"

class WalkieTalkieService : Service(), SignalingListener {

    private val binder = LocalBinder()
    var webRTCManager: WebRTCManager? = null
        private set

    var onOthersSpeakingStateChange: ((Boolean) -> Unit)? = null

    companion object {
        private const val CHANNEL_ID = "WalkieTalkieServiceChannel"
        private const val NOTIFICATION_ID = 1
    }

    inner class LocalBinder : Binder() {
        fun getService(): WalkieTalkieService = this@WalkieTalkieService
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        SocketManager.setSignalingListener(this)
        webRTCManager = WebRTCManager(this).apply {
            init()
            initialize()
            onCallConnected = {
                val roomId = SocketManager.socketUiState.value.roomId
                val text = if (roomId.isNotEmpty()) "In Squad: $roomId" else "Voice Link Ready"
                updateNotification(text)
            }
            onCallDisconnected = {
                val roomId = SocketManager.socketUiState.value.roomId
                val text = if (roomId.isNotEmpty()) "In Squad: $roomId" else "Ready to talk"
                updateNotification(text)
                onOthersSpeakingStateChange?.invoke(false)
            }
        }
    }

    override fun onOfferReceived(fromPeerId: String, sdp: String) {
        if (SocketManager.socketUiState.value.roomId.isEmpty()) return
        Log.d(TAG, "onOfferReceived from $fromPeerId")
        webRTCManager?.handleOffer(fromPeerId, sdp)
        val speaker = SocketManager.socketUiState.value.lastSpeakerName
        val text = if (!speaker.isNullOrBlank() && speaker != SocketManager.socketUiState.value.username) {
            "$speaker is transmitting..."
        } else {
            "Incoming transmission..."
        }
        updateNotification(text)
    }

    override fun onAnswerReceived(fromPeerId: String, sdp: String) {
        if (SocketManager.socketUiState.value.roomId.isEmpty()) return
        Log.d(TAG, "onAnswerReceived from $fromPeerId")
        webRTCManager?.handleAnswer(fromPeerId, sdp)
    }

    override fun onIceCandidateReceived(fromPeerId: String, candidate: String) {
        if (SocketManager.socketUiState.value.roomId.isEmpty()) return
        webRTCManager?.handleIceCandidate(fromPeerId, candidate)
    }

    override fun onPeersReceived(peers: List<String>) {
        if (SocketManager.socketUiState.value.roomId.isEmpty()) return
        Log.d(TAG, "onPeersReceived: connecting to ${peers.size} peer(s)")
        webRTCManager?.connectToPeers(peers)
    }

    override fun onPeerLeft(peerId: String) {
        Log.d(TAG, "onPeerLeft: removing peer $peerId")
        webRTCManager?.removePeer(peerId)
    }

    override fun onCallStarted() {
        val speaker = SocketManager.socketUiState.value.lastSpeakerName
        val text = if (!speaker.isNullOrBlank() && speaker != SocketManager.socketUiState.value.username) {
            "$speaker is speaking..."
        } else {
            "Squad member speaking..."
        }
        updateNotification(text)
        onOthersSpeakingStateChange?.invoke(true)
    }

    override fun onCallEnded() {
        val roomId = SocketManager.socketUiState.value.roomId
        val text = if (roomId.isNotEmpty()) "In Squad: $roomId" else "Ready to talk"
        updateNotification(text)
        onOthersSpeakingStateChange?.invoke(false)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification("Ready to talk")
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.d("WalkieTalkieService", "onTaskRemoved: App closed, stopping audio session and leaving squad")
        SocketManager.leaveRoom()
        SocketManager.disconnect()
        webRTCManager?.cleanup()
        webRTCManager = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    fun startVoiceSession(roomId: String = "") {
        if (webRTCManager == null) {
            webRTCManager = WebRTCManager(this).apply {
                init()
                initialize()
            }
        }
        webRTCManager?.prepareConnection()
        val text = if (roomId.isNotEmpty()) "In Squad: $roomId" else "Ready to talk"
        updateNotification(text)
    }

    fun stopVoiceSession() {
        Log.d("WalkieTalkieService", "stopVoiceSession called")
        webRTCManager?.cleanup()
        webRTCManager = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private fun createNotification(contentText: String): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Squad Talk Active")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Walkie Talkie Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    fun updateNotification(text: String) {
        val notification = createNotification(text)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    fun replayLastTransmissions() {
        webRTCManager?.replayLastTransmissions()
    }

    fun restartIce() {
        webRTCManager?.restartIce()
    }

    override fun onDestroy() {
        Log.d("WalkieTalkieService", "onDestroy: Cleaning up WebRTC manager")
        SocketManager.setSignalingListener(null)
        webRTCManager?.cleanup()
        webRTCManager = null
        super.onDestroy()
    }
}
