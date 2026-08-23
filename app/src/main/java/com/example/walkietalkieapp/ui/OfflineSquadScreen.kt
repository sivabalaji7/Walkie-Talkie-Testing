package com.example.walkietalkieapp.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.walkietalkieapp.bluetooth.SquadMember
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class TransportMode {
    INTERNET,
    BLUETOOTH,
    WIFI_DIRECT
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineSquadScreen(
    mode: TransportMode,
    isHost: Boolean,
    squadName: String,
    members: List<SquadMember>,
    connectionState: String,
    isChannelBusy: Boolean,
    currentSpeakerId: String?,
    currentSpeakerName: String?,
    lastSpeakerName: String?,
    lastSpeakerTimestamp: Long,
    isBeaconActive: Boolean,
    beaconCountdownSeconds: Int,
    myId: String,
    onGoVisible: () -> Unit,
    onStartTalk: () -> Unit,
    onStopTalk: () -> Unit,
    onLeave: () -> Unit,
    vibrate: () -> Unit,
    onReplay: () -> Unit,
    onWhisper: () -> Unit,
    isBatterySaverEnabled: Boolean,
    onBatterySaverToggle: (Boolean) -> Unit,
    notificationMessage: String? = null,
    onRetry: (() -> Unit)? = null
) {
    var isUserSpeaking by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    val isOthersSpeaking = isChannelBusy && currentSpeakerId != myId

    // 10-second timer: show retry button if stuck on CONNECTING
    var showRetry by remember { mutableStateOf(false) }
    LaunchedEffect(connectionState) {
        showRetry = false
        if (connectionState == "CONNECTING") {
            delay(10_000)
            showRetry = true
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Header: Settings, Squad Title, Replay
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { showSettings = true },
                    modifier = Modifier.background(Color.White.copy(alpha = 0.05f), CircleShape)
                ) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .background(if (mode == TransportMode.BLUETOOTH) Color(0xFF00E5FF).copy(alpha = 0.2f) else Color(0xFF4CAF50).copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (mode == TransportMode.BLUETOOTH) "BLUETOOTH" else "WI-FI DIRECT",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                color = if (mode == TransportMode.BLUETOOTH) Color(0xFF00E5FF) else Color(0xFF4CAF50)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isHost) "HOST: $squadName" else "SQUAD: $squadName",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            letterSpacing = 1.sp
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        val (statusText, statusColor) = when {
                            isBeaconActive -> "📡 Broadcasting Beacon (${beaconCountdownSeconds}s)" to Color(0xFF00E5FF)
                            connectionState == "HOSTING" -> "🟢 Active Host (${members.size} in squad)" to Color(0xFF4CAF50)
                            connectionState == "CONNECTED" -> "🟢 Connected (${members.size} in squad)" to Color(0xFF4CAF50)
                            else -> "🟡 Connecting..." to Color(0xFFFFA000)
                        }
                        Text(text = statusText, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = statusColor)
                    }
                }

                IconButton(
                    onClick = onReplay,
                    modifier = Modifier.background(Color.White.copy(alpha = 0.05f), CircleShape)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Replay", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
                }
            }

            TimerView()

            // RETRY BUTTON — shown after 10s of CONNECTING
            if (connectionState == "CONNECTING" && showRetry && onRetry != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = {
                        showRetry = false
                        onRetry()
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFA000)),
                    modifier = Modifier.fillMaxWidth().height(42.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("RETRY CONNECTION", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }

            // "GO VISIBLE" BUTTON (HOST ONLY - BLUETOOTH MODE)
            if (isHost && mode == TransportMode.BLUETOOTH) {
                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = onGoVisible,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isBeaconActive) Color(0xFF00E5FF) else Color(0xFF00E5FF).copy(alpha = 0.15f)
                    ),
                    modifier = Modifier.fillMaxWidth().height(42.dp)
                ) {
                    Icon(
                        Icons.Default.Podcasts,
                        contentDescription = null,
                        tint = if (isBeaconActive) Color.Black else Color(0xFF00E5FF),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isBeaconActive) "BROADCASTING SIGNAL (${beaconCountdownSeconds}s)..." else "GO VISIBLE (5s BEACON)",
                        color = if (isBeaconActive) Color.Black else Color(0xFF00E5FF),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Squad Member Avatars (Live Waveforms)
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (members.isEmpty()) {
                    item {
                        Text(
                            if (mode == TransportMode.BLUETOOTH) "Tap 'GO VISIBLE' to let nearby friends discover and join..."
                            else "Wi-Fi Direct Group active. Waiting for squad members...",
                            color = Color.White.copy(alpha = 0.3f),
                            fontSize = 12.sp,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        )
                    }
                } else {
                    items(members) { member ->
                        OfflineMemberItem(member = member, onReplay = onReplay)
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Center Speaker Indicator
            OfflineSpeakingIndicator(
                isUserSpeaking = isUserSpeaking,
                isOthersSpeaking = isOthersSpeaking,
                currentSpeakerName = currentSpeakerName,
                lastSpeakerName = lastSpeakerName,
                lastSpeakerTimestamp = lastSpeakerTimestamp
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Push-To-Talk Button (Half-Duplex Channel Lock)
            OfflinePushToTalkButton(
                isSpeaking = isUserSpeaking,
                isChannelBusy = isOthersSpeaking,
                isConnected = connectionState == "HOSTING" || connectionState == "CONNECTED",
                onPress = {
                    if (!isOthersSpeaking) {
                        vibrate()
                        isUserSpeaking = true
                        onStartTalk()
                    }
                },
                onRelease = {
                    isUserSpeaking = false
                    onStopTalk()
                },
                onWhisper = {
                    if (!isOthersSpeaking) {
                        isUserSpeaking = true
                        onWhisper()
                        CoroutineScope(Dispatchers.Main).launch {
                            delay(2000)
                            isUserSpeaking = false
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.weight(1.2f))

            // Bottom Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onLeave,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252).copy(alpha = 0.1f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(48.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, tint = Color(0xFFFF5252), modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("LEAVE SQUAD", color = Color(0xFFFF5252), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }

        // Settings Bottom Sheet
        if (showSettings) {
            ModalBottomSheet(
                onDismissRequest = { showSettings = false },
                containerColor = Color(0xFF1E2124),
                dragHandle = { BottomSheetDefaults.DragHandle(color = Color.Gray) }
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp).padding(bottom = 32.dp)
                ) {
                    Text("Squad Room Settings", style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(24.dp))

                    SettingsItem(
                        icon = Icons.Default.Bolt,
                        title = "Whisper Burst",
                        subtitle = "Send a quick 2-second voice broadcast",
                        color = Color(0xFF9C27B0),
                        onClick = {
                            onWhisper()
                            showSettings = false
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    SettingsItem(
                        icon = Icons.Default.Refresh,
                        title = "Replay Last Transmission",
                        subtitle = "Play back the most recent 10s of squad audio",
                        color = Color(0xFFFFA000),
                        onClick = {
                            onReplay()
                            showSettings = false
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    SettingsItem(
                        icon = Icons.Default.BatterySaver,
                        title = "Battery Saver",
                        subtitle = if (isBatterySaverEnabled) "Auto-idle disconnect enabled" else "Always stay connected",
                        color = if (isBatterySaverEnabled) Color(0xFF4CAF50) else Color.Gray,
                        onClick = { onBatterySaverToggle(!isBatterySaverEnabled) }
                    )
                }
            }
        }

        // Animated Notification Banner
        AnimatedVisibility(
            visible = notificationMessage != null,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2124)),
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(8.dp).background(Color(0xFF4CAF50), CircleShape))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = notificationMessage ?: "",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun OfflineMemberItem(member: SquadMember, onReplay: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")

    val speakingScale by animateFloatAsState(
        targetValue = if (member.isSpeaking) 1.15f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "speakingScale"
    )

    val borderAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "alpha"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .padding(horizontal = 10.dp)
            .scale(speakingScale)
            .clickable { onReplay() }
    ) {
        Box(contentAlignment = Alignment.BottomEnd) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .border(
                        width = if (member.isSpeaking) 3.dp else 1.dp,
                        color = if (member.isSpeaking) Color(0xFF4CAF50).copy(alpha = borderAlpha) else Color.White.copy(alpha = 0.1f),
                        shape = CircleShape
                    )
                    .padding(4.dp)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color(0xFF2C2F33), Color(0xFF1E2124))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = member.username.take(1).uppercase(),
                    color = if (member.isSpeaking) Color.White else Color.White.copy(alpha = 0.5f),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 22.sp
                )

                if (member.isSpeaking) {
                    WaveformSmall()
                }
            }

            // Status indicator dot
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .background(Color(0xFF0F1115), CircleShape)
                    .padding(2.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize().background(Color(0xFF4CAF50), CircleShape))
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = member.username,
            color = if (member.isSpeaking) Color.White else Color.White.copy(alpha = 0.5f),
            fontSize = 12.sp,
            fontWeight = if (member.isSpeaking) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
fun OfflinePushToTalkButton(
    isSpeaking: Boolean,
    isChannelBusy: Boolean,
    isConnected: Boolean,
    onPress: () -> Unit,
    onRelease: () -> Unit,
    onWhisper: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isSpeaking) 0.9f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "scale"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")

    val pulse1Scale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 2.2f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearOutSlowInEasing), RepeatMode.Restart),
        label = "pulse1Scale"
    )
    val pulse1Alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearOutSlowInEasing), RepeatMode.Restart),
        label = "pulse1Alpha"
    )

    val breathingTransition = rememberInfiniteTransition(label = "breathing")
    val breathingAlpha by breathingTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathingAlpha"
    )

    Box(contentAlignment = Alignment.Center) {
        if (isSpeaking) {
            Box(modifier = Modifier.size(170.dp).scale(pulse1Scale).background(Color(0xFF4CAF50).copy(alpha = pulse1Alpha), CircleShape))
        }

        Box(
            modifier = Modifier
                .size(170.dp)
                .scale(scale)
                .clip(CircleShape)
                .background(
                    brush = Brush.verticalGradient(
                        colors = when {
                            isSpeaking -> listOf(Color(0xFF66BB6A), Color(0xFF43A047))
                            isChannelBusy -> listOf(Color(0xFFE53935), Color(0xFFC62828))
                            else -> listOf(Color(0xFF2C2F33), Color(0xFF1E2126))
                        }
                    )
                )
                .then(
                    if (!isSpeaking && !isChannelBusy) {
                        Modifier.graphicsLayer(alpha = breathingAlpha)
                    } else Modifier
                )
                .border(
                    width = if (isSpeaking) 4.dp else 2.dp,
                    color = when {
                        isSpeaking -> Color(0xFF81C784)
                        isChannelBusy -> Color(0xFFFF8A80)
                        else -> Color.White.copy(alpha = 0.1f)
                    },
                    shape = CircleShape
                )
                .pointerInput(isConnected, isChannelBusy) {
                    if (isConnected && !isChannelBusy) {
                        detectTapGestures(
                            onPress = {
                                onPress()
                                try { awaitRelease() } finally { onRelease() }
                            },
                            onDoubleTap = {
                                onWhisper()
                            }
                        )
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.Mic,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(54.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = when {
                        isSpeaking -> "TRANSMITTING"
                        isChannelBusy -> "CHANNEL BUSY"
                        else -> "HOLD TO TALK"
                    },
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.5.sp
                )
            }
        }
    }
}

@Composable
fun OfflineSpeakingIndicator(
    isUserSpeaking: Boolean,
    isOthersSpeaking: Boolean,
    currentSpeakerName: String?,
    lastSpeakerName: String?,
    lastSpeakerTimestamp: Long
) {
    Box(
        modifier = Modifier.height(80.dp).fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = when {
                isUserSpeaking -> "YOU"
                isOthersSpeaking -> "OTHERS"
                else -> "IDLE"
            },
            transitionSpec = {
                (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut())
            },
            label = "indicator"
        ) { state ->
            when (state) {
                "YOU" -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        PulsingDot(Color(0xFF4CAF50))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "YOU ARE BROADCASTING",
                            color = Color(0xFF4CAF50),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp,
                            letterSpacing = 1.sp
                        )
                    }
                }
                "OTHERS" -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        PulsingDot(Color(0xFFF44336))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "${currentSpeakerName?.uppercase() ?: "MEMBER"} IS TRANSMITTING",
                            color = Color(0xFFF44336),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp,
                            letterSpacing = 1.sp
                        )
                    }
                }
                else -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "Channel is Idle",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        if (lastSpeakerName != null) {
                            Text(
                                "Last: $lastSpeakerName",
                                color = Color.White.copy(alpha = 0.3f),
                                fontSize = 11.sp,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
