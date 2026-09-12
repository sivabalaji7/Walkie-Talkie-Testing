package com.example.walkietalkieapp.webrtc

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.walkietalkieapp.MainActivity
import com.example.walkietalkieapp.R
import com.example.walkietalkieapp.audio.AudioBurstManager
import com.example.walkietalkieapp.socket.AudioBurstListener
import com.example.walkietalkieapp.socket.SignalingListener
import com.example.walkietalkieapp.socket.SupabaseRealtimeManager

private const val TAG = "WalkieTalkieService"

class WalkieTalkieService : Service(), SignalingListener, AudioBurstListener {

    private val binder = LocalBinder()
    var audioBurstManager: AudioBurstManager? = null
        private set

    private var wakeLock: PowerManager.WakeLock? = null
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

        // Initialize AudioBurstManager for universal, CGNAT-immune tactical voice transmission
        audioBurstManager = AudioBurstManager(this).apply {
            onPlaybackStateChange = { isPlaying, speakerName ->
                if (isPlaying) {
                    val speaker = speakerName ?: "Squad Member"
                    updateNotification("🔊 $speaker is transmitting...")
                    onOthersSpeakingStateChange?.invoke(true)
                } else {
                    val roomId = SupabaseRealtimeManager.socketUiState.value.roomId
                    updateNotification(if (roomId.isNotEmpty()) "In Squad: $roomId" else "Ready to talk")
                    onOthersSpeakingStateChange?.invoke(false)
                }
            }
        }

        SupabaseRealtimeManager.setSignalingListener(this)
        SupabaseRealtimeManager.setAudioBurstListener(this)
        
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WalkieTalkieApp::VoiceServiceWakeLock")
        acquireWakeLock(15 * 60 * 1000L) // Timed 15-min safety timeout to avoid Android battery drain warnings
    }

    private fun acquireWakeLock(timeoutMs: Long = 10 * 60 * 1000L) {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
            wakeLock?.acquire(timeoutMs)
            Log.d(TAG, "WakeLock acquired (timeout: $timeoutMs ms)")
        } catch (e: Exception) {
            Log.e(TAG, "Error acquiring wake lock", e)
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
                Log.d(TAG, "WakeLock released")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing wake lock", e)
        }
    }

    override fun onPeerLeft(peerId: String) {}

    override fun onCallStarted() {
        acquireWakeLock(10 * 60 * 1000L) // Extend WakeLock during transmission
        val username = SupabaseRealtimeManager.socketUiState.value.username
        val speaker = SupabaseRealtimeManager.socketUiState.value.lastSpeakerName
        
        if (!speaker.isNullOrBlank() && !speaker.equals(username, ignoreCase = true)) {
            updateNotification("$speaker is speaking...")
            onOthersSpeakingStateChange?.invoke(true)
        } else {
            updateNotification("🔴 Transmitting...")
            onOthersSpeakingStateChange?.invoke(false)
        }
    }

    override fun onCallEnded() {
        val roomId = SupabaseRealtimeManager.socketUiState.value.roomId
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
        Log.d(TAG, "onTaskRemoved: App closed, stopping audio session and leaving squad")
        SupabaseRealtimeManager.leaveRoom()
        SupabaseRealtimeManager.disconnect()
        releaseWakeLock()
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
        acquireWakeLock(15 * 60 * 1000L)
        val text = if (roomId.isNotEmpty()) "In Squad: $roomId" else "Ready to talk"
        updateNotification(text)
    }

    fun stopVoiceSession() {
        Log.d(TAG, "stopVoiceSession called")
        releaseWakeLock()
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

    override fun onVoiceBurstReceived(sender: String, audioBase64: String, durationMs: Long) {
        val myName = SupabaseRealtimeManager.socketUiState.value.username
        if (sender.equals(myName, ignoreCase = true)) return
        acquireWakeLock(10 * 60 * 1000L)
        Log.d(TAG, "onVoiceBurstReceived from $sender (${durationMs}ms)")
        audioBurstManager?.playIncomingBurst(sender, audioBase64, durationMs)
    }

    fun startRecordingBurst(): Boolean {
        acquireWakeLock(10 * 60 * 1000L)
        updateNotification("🔴 Transmitting...")
        return audioBurstManager?.startRecording() ?: false
    }

    fun stopRecordingAndBroadcastBurst() {
        audioBurstManager?.stopRecordingAndBroadcast { audioBase64, durationMs ->
            SupabaseRealtimeManager.sendVoiceBurst(audioBase64, durationMs)
            SupabaseRealtimeManager.sendStopVoice()
        }
        val roomId = SupabaseRealtimeManager.socketUiState.value.roomId
        updateNotification(if (roomId.isNotEmpty()) "In Squad: $roomId" else "Ready to talk")
    }

    fun replayLastBurst(): Boolean {
        return audioBurstManager?.replayLastBurst() ?: false
    }

    fun replayLastTransmissions() {
        replayLastBurst()
    }

    fun restartIce() {}

    override fun onDestroy() {
        Log.d(TAG, "onDestroy: Cleaning up AudioBurst manager")
        SupabaseRealtimeManager.setSignalingListener(null)
        SupabaseRealtimeManager.setAudioBurstListener(null)
        audioBurstManager?.release()
        audioBurstManager = null
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        super.onDestroy()
    }
}
