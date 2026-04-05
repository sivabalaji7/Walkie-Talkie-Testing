package com.example.walkietalkieapp

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.walkietalkieapp.socket.SignalingListener
import com.example.walkietalkieapp.socket.SocketManager
import com.example.walkietalkieapp.webrtc.WebRTCManager
import com.example.walkietalkieapp.ui.theme.WalkieTalkieAppTheme

private const val TAG = "WalkieTalkieApp"

class MainActivity : ComponentActivity(), SignalingListener {
    private var hasAudioPermission by mutableStateOf(false)
    private var hasRequestedAudioPermission by mutableStateOf(false)
    private var shouldShowAudioPermissionRationale by mutableStateOf(false)
    
    private var webRTCManager: WebRTCManager? = null
    private var isOthersSpeaking by mutableStateOf(false)

    private val audioPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            hasRequestedAudioPermission = true
            hasAudioPermission = isGranted
            shouldShowAudioPermissionRationale =
                !isGranted && shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)

            if (isGranted) {
                initializeWebRTC()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        refreshAudioPermissionState()
        
        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            audioManager?.let {
                it.mode = AudioManager.MODE_IN_COMMUNICATION
                it.isSpeakerphoneOn = true
                Log.d(TAG, "AudioManager configured")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to configure AudioManager: ${e.message}")
        }

        if (hasAudioPermission) {
            initializeWebRTC()
        }
        
        SocketManager.setSignalingListener(this)
        SocketManager.initialize()
        SocketManager.connect()

        setContent {
            val socketUiState by SocketManager.socketUiState.collectAsState()

            WalkieTalkieAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    WalkieTalkieScreen(
                        hasAudioPermission = hasAudioPermission,
                        socketUiState = socketUiState,
                        isOthersSpeaking = isOthersSpeaking,
                        onRequestAudioPermission = ::requestAudioPermission,
                        onConnectSocket = { SocketManager.connect() },
                        onDisconnectSocket = { SocketManager.disconnect() },
                        onStartPushToTalk = ::startPushToTalk,
                        onStopPushToTalk = ::stopPushToTalk,
                        vibrate = ::vibrate
                    )
                }
            }
        }
    }

    private fun vibrate() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                val vibrator = vibratorManager?.defaultVibrator
                vibrator?.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                @Suppress("DEPRECATION")
                vibrator?.vibrate(50)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Vibration failed: ${e.message}")
        }
    }

    private fun initializeWebRTC() {
        if (webRTCManager != null) return
        
        try {
            webRTCManager = WebRTCManager(this).apply {
                init()
                initialize()
                createPeerConnection()
            }
            Log.d(TAG, "WebRTC initialization completed")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize WebRTC: ${e.message}", e)
        }
    }

    override fun onResume() {
        super.onResume()
        refreshAudioPermissionState()
    }

    override fun onDestroy() {
        SocketManager.setSignalingListener(null)
        SocketManager.disconnect()
        webRTCManager?.cleanup()
        super.onDestroy()
    }

    private fun requestAudioPermission() {
        if (hasAudioPermission) return
        hasRequestedAudioPermission = true
        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun refreshAudioPermissionState() {
        hasAudioPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        shouldShowAudioPermissionRationale =
            !hasAudioPermission && shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)
    }

    override fun onOfferReceived(sdp: String) {
        runOnUiThread {
            Log.d(TAG, "Offer received, handling on UI thread")
            isOthersSpeaking = true
            SocketManager.addLog("Voice incoming...")
            webRTCManager?.handleOffer(sdp)
        }
    }

    override fun onAnswerReceived(sdp: String) {
        runOnUiThread {
            Log.d(TAG, "Answer received, handling on UI thread")
            isOthersSpeaking = true
            SocketManager.addLog("Voice connected")
            webRTCManager?.handleAnswer(sdp)
        }
    }

    override fun onIceCandidateReceived(candidate: String) {
        runOnUiThread {
            Log.d(TAG, "ICE Candidate received")
            webRTCManager?.handleIceCandidate(candidate)
        }
    }

    override fun onCallStarted() {
        runOnUiThread {
            isOthersSpeaking = true
        }
    }

    override fun onCallEnded() {
        runOnUiThread {
            Log.d(TAG, "Remote call ended, resetting speaking indicator")
            isOthersSpeaking = false
            SocketManager.addLog("Remote user stopped talking")
        }
    }
    
    private fun startPushToTalk() {
        Log.d(TAG, "PTT Pressed")
        SocketManager.addLog("PTT Pressed")
        if (!hasAudioPermission) {
            requestAudioPermission()
            return
        }
        
        if (webRTCManager == null) {
            initializeWebRTC()
        }
        
        webRTCManager?.startTalking()
        
        if (webRTCManager?.isConnected() != true) {
            webRTCManager?.createOffer()
        }
    }
    
    private fun stopPushToTalk() {
        Log.d(TAG, "PTT Released")
        SocketManager.addLog("PTT Released")
        webRTCManager?.stopTalking()
        SocketManager.sendStopVoice()
    }
}

@Composable
fun WalkieTalkieScreen(
    hasAudioPermission: Boolean,
    socketUiState: com.example.walkietalkieapp.socket.SocketUiState,
    isOthersSpeaking: Boolean,
    onRequestAudioPermission: () -> Unit,
    onConnectSocket: () -> Unit,
    onDisconnectSocket: () -> Unit,
    onStartPushToTalk: () -> Unit,
    onStopPushToTalk: () -> Unit,
    vibrate: () -> Unit
) {
    var isUserSpeaking by remember { mutableStateOf(false) }
    val lazyListState = rememberLazyListState()

    LaunchedEffect(socketUiState.eventLog.size) {
        if (socketUiState.eventLog.isNotEmpty()) {
            lazyListState.animateScrollToItem(0)
        }
    }

    val buttonColor = when {
        isUserSpeaking -> Color.Red
        hasAudioPermission -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.secondaryContainer
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Walkie Talkie",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                val (statusColor, statusText) = when {
                    socketUiState.isConnected -> Color.Green to "Connected"
                    socketUiState.status == "OFFLINE" && socketUiState.detail.contains("Retry", true) -> Color.Yellow to "Connecting..."
                    else -> Color.Red to "Disconnected"
                }
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(statusColor, CircleShape)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = statusText, fontSize = 14.sp)
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Row(
                modifier = Modifier
                    .padding(12.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isUserSpeaking) {
                    Text("🟢 You are speaking", color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                } else if (isOthersSpeaking) {
                    Text("🔴 Other user speaking", color = Color.Red, fontWeight = FontWeight.Bold)
                } else {
                    Text("Idle", color = Color.Gray)
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (socketUiState.isConnected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Server Status: ${socketUiState.status}",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = socketUiState.detail,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Box(
            modifier = Modifier
                .size(210.dp)
                .background(buttonColor.copy(alpha = 0.1f), CircleShape)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            if (hasAudioPermission && socketUiState.isConnected) {
                                vibrate()
                                isUserSpeaking = true
                                onStartPushToTalk()
                                try {
                                    awaitRelease()
                                } finally {
                                    isUserSpeaking = false
                                    onStopPushToTalk()
                                }
                            } else if (!hasAudioPermission) {
                                onRequestAudioPermission()
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier.size(180.dp),
                shape = CircleShape,
                color = buttonColor,
                shadowElevation = 8.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = if (isUserSpeaking) "TALK" else "HOLD TO\nTALK",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onConnectSocket,
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.medium
            ) {
                Text(text = "Connect")
            }
            Button(
                onClick = onDisconnectSocket,
                modifier = Modifier.weight(1f),
                enabled = socketUiState.isConnected,
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text(text = "Disconnect")
            }
        }

        Text(
            text = "Activity Log",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Start
        )
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F4F8)),
            border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.3f))
        ) {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(8.dp)
            ) {
                items(socketUiState.eventLog) { logLine ->
                    Text(
                        text = logLine,
                        fontSize = 11.sp,
                        color = Color.Black,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                    HorizontalDivider(thickness = 0.5.dp, color = Color.Gray.copy(alpha = 0.1f))
                }
            }
        }
    }
}
