package com.example.walkietalkieapp.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
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
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Section: Header Controls & Channel Info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Settings Pod
                IconButton(
                    onClick = { showSettings = true },
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(TactileColors.surfaceContainerLow)
                        .border(1.dp, TactileColors.ghostBorder, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Squad Settings",
                        tint = TactileColors.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Center Title & Mode
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val modeColor = if (mode == TransportMode.BLUETOOTH) TactileColors.statusConnecting else TactileColors.statusActive
                        Surface(
                            color = modeColor.copy(alpha = 0.15f),
                            shape = TactileShapes.small
                        ) {
                            Text(
                                text = if (mode == TransportMode.BLUETOOTH) "BLUETOOTH" else "WI-FI DIRECT",
                                fontFamily = SpaceGrotesk,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = modeColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isHost) "HOST: $squadName" else "SQUAD: $squadName",
                            fontFamily = SpaceGrotesk,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = TactileColors.onSurface,
                            letterSpacing = 0.5.sp
                        )
                    }

                    // Valour Connection Status Pill
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 6.dp)
                    ) {
                        val transportType = if (mode == TransportMode.BLUETOOTH) com.example.walkietalkieapp.dna.model.TransportType.Bluetooth else com.example.walkietalkieapp.dna.model.TransportType.WifiDirect
                        val allLinks by com.example.walkietalkieapp.dna.valour.ValourLinkIntelligence.allLinksState.collectAsState()
                        val offlineLinkState = allLinks[transportType]
                            ?: com.example.walkietalkieapp.dna.valour.ValourLinkIntelligence.mapToValourLinkState(
                                com.example.walkietalkieapp.dna.model.CommunicationPathAssessment.unavailable(transportType)
                            )

                        var showDetailsSheet by remember { mutableStateOf(false) }

                        com.example.walkietalkieapp.dna.valour.ValourLinkPill(
                            linkState = offlineLinkState,
                            onClick = { showDetailsSheet = true }
                        )

                        if (showDetailsSheet) {
                            com.example.walkietalkieapp.dna.valour.ValourConnectionDetailsSheet(
                                linkState = offlineLinkState,
                                onDismissRequest = { showDetailsSheet = false }
                            )
                        }
                    }
                }

                // Replay Pod
                IconButton(
                    onClick = onReplay,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(TactileColors.surfaceContainerLow)
                        .border(1.dp, TactileColors.ghostBorder, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Replay",
                        tint = TactileColors.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
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
                    shape = TactileShapes.pill,
                    colors = ButtonDefaults.buttonColors(containerColor = TactileColors.primaryContainer),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = Color(0xFF131315), modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "RETRY CONNECTION",
                        color = Color(0xFF131315),
                        fontFamily = SpaceGrotesk,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            // "GO VISIBLE" BUTTON (HOST ONLY - BLUETOOTH MODE)
            if (isHost && mode == TransportMode.BLUETOOTH) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    onClick = onGoVisible,
                    shape = TactileShapes.pill,
                    color = if (isBeaconActive) TactileColors.statusConnecting else TactileColors.surfaceContainerLow,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .border(
                            1.dp,
                            if (isBeaconActive) TactileColors.statusConnecting else TactileColors.statusConnecting.copy(alpha = 0.4f),
                            TactileShapes.pill
                        )
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Podcasts,
                            contentDescription = null,
                            tint = if (isBeaconActive) Color(0xFF131315) else TactileColors.statusConnecting,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isBeaconActive) "BROADCASTING BEACON (${beaconCountdownSeconds}s)..." else "GO VISIBLE (5s BEACON)",
                            color = if (isBeaconActive) Color(0xFF131315) else TactileColors.statusConnecting,
                            fontFamily = SpaceGrotesk,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Squad Member Avatars (Live Audio Halos)
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (members.isEmpty()) {
                    item {
                        Text(
                            text = if (mode == TransportMode.BLUETOOTH) "Tap 'GO VISIBLE' to let nearby friends discover and join..."
                            else "Wi-Fi Direct Group active. Waiting for squad members...",
                            fontFamily = Manrope,
                            color = TactileColors.onSecondaryContainer.copy(alpha = 0.5f),
                            fontSize = 12.sp
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

            Spacer(modifier = Modifier.height(16.dp))

            // Push-To-Talk Button (Half-Duplex Hardware Button)
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

            Spacer(modifier = Modifier.weight(1f))

            // Leave Squad Action Button
            Surface(
                onClick = onLeave,
                color = TactileColors.surfaceContainerLow,
                shape = TactileShapes.pill,
                modifier = Modifier
                    .border(1.dp, TactileColors.error.copy(alpha = 0.35f), TactileShapes.pill)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                        contentDescription = null,
                        tint = TactileColors.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "LEAVE SQUAD",
                        color = TactileColors.error,
                        fontFamily = SpaceGrotesk,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }

        // Settings Bottom Sheet
        if (showSettings) {
            ModalBottomSheet(
                onDismissRequest = { showSettings = false },
                containerColor = TactileColors.surfaceContainerLow,
                dragHandle = { BottomSheetDefaults.DragHandle(color = TactileColors.outlineVariant) }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 36.dp)
                ) {
                    Text(
                        text = "Squad Room Settings",
                        fontFamily = SpaceGrotesk,
                        fontSize = 20.sp,
                        color = TactileColors.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(20.dp))

                    SettingsItem(
                        icon = Icons.Default.Bolt,
                        title = "Whisper Burst",
                        subtitle = "Send a quick 2-second voice broadcast",
                        color = TactileColors.primaryContainer,
                        onClick = {
                            onWhisper()
                            showSettings = false
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    SettingsItem(
                        icon = Icons.Default.Refresh,
                        title = "Replay Last Transmission",
                        subtitle = "Play back the most recent 10s of squad audio",
                        color = TactileColors.primary,
                        onClick = {
                            onReplay()
                            showSettings = false
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    SettingsItem(
                        icon = Icons.Default.BatterySaver,
                        title = "Battery Saver",
                        subtitle = if (isBatterySaverEnabled) "Auto-idle disconnect enabled" else "Always stay connected in foreground",
                        color = if (isBatterySaverEnabled) TactileColors.statusActive else TactileColors.onSecondaryContainer,
                        onClick = { onBatterySaverToggle(!isBatterySaverEnabled) }
                    )
                }
            }
        }

        // Notification Banner Overlay
        if (notificationMessage != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 80.dp)
            ) {
                NotificationBanner(message = notificationMessage)
            }
        }
    }
}

@Composable
fun OfflineMemberItem(member: SquadMember, onReplay: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")

    val speakingScale by animateFloatAsState(
        targetValue = if (member.isSpeaking) 1.12f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "speakingScale"
    )

    val borderAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "alpha"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .padding(horizontal = 8.dp)
            .scale(speakingScale)
            .clickable { onReplay() }
    ) {
        Box(contentAlignment = Alignment.BottomEnd) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .border(
                        width = if (member.isSpeaking) 2.5.dp else 1.dp,
                        color = if (member.isSpeaking) TactileColors.primaryContainer.copy(alpha = borderAlpha) else TactileColors.ghostBorder,
                        shape = CircleShape
                    )
                    .padding(3.dp)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(TactileColors.surfaceContainerHighest, TactileColors.surfaceContainerLow)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = member.username.take(1).uppercase(),
                    color = if (member.isSpeaking) TactileColors.primaryContainer else TactileColors.onSurface,
                    fontFamily = SpaceGrotesk,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )

                if (member.isSpeaking) {
                    WaveformSmall()
                }
            }

            // Status indicator dot
            Box(
                modifier = Modifier
                    .size(13.dp)
                    .background(TactileColors.surfaceContainerLowest, CircleShape)
                    .padding(2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(TactileColors.statusActive, CircleShape)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = member.username,
            color = if (member.isSpeaking) TactileColors.primary else TactileColors.onSurface,
            fontFamily = Manrope,
            fontSize = 11.sp,
            fontWeight = if (member.isSpeaking) FontWeight.Bold else FontWeight.Normal
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
        targetValue = if (isSpeaking) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "scale"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")

    val pulse1Scale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 2.1f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearOutSlowInEasing), RepeatMode.Restart),
        label = "pulse1Scale"
    )
    val pulse1Alpha by infiniteTransition.animateFloat(
        initialValue = 0.35f, targetValue = 0f,
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

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(250.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isSpeaking) {
            Box(
                modifier = Modifier
                    .size(175.dp)
                    .scale(pulse1Scale)
                    .background(TactileColors.primaryContainer.copy(alpha = pulse1Alpha), CircleShape)
            )
        }

        // Machined outer ring chassis (Vintage radio ridges)
        Canvas(modifier = Modifier.size(200.dp)) {
            drawCircle(
                color = Color(0xFF1E1D21),
                radius = size.minDimension / 2f
            )
            drawCircle(
                color = Color(0xFF353437).copy(alpha = 0.4f),
                radius = size.minDimension / 2f,
                style = Stroke(width = 1.dp.toPx())
            )
            drawCircle(
                color = Color(0xFF2A2A2D).copy(alpha = 0.3f),
                radius = (size.minDimension / 2f) - 10.dp.toPx(),
                style = Stroke(width = 1.dp.toPx())
            )
        }

        // Core PTT Button
        Box(
            modifier = Modifier
                .size(165.dp)
                .scale(scale)
                .clip(CircleShape)
                .background(
                    brush = Brush.verticalGradient(
                        colors = when {
                            isSpeaking -> listOf(TactileColors.primary, TactileColors.primaryContainer)
                            isChannelBusy -> listOf(TactileColors.surfaceContainerHigh, TactileColors.surfaceContainerLow)
                            else -> listOf(TactileColors.surfaceContainerHighest, TactileColors.surfaceContainerLow)
                        }
                    )
                )
                .then(
                    if (!isSpeaking && !isChannelBusy) {
                        Modifier.graphicsLayer(alpha = breathingAlpha)
                    } else Modifier
                )
                .border(
                    width = if (isSpeaking) 3.dp else 1.5.dp,
                    color = when {
                        isSpeaking -> TactileColors.primaryContainer
                        isChannelBusy -> TactileColors.error
                        else -> TactileColors.ghostBorder
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
                    imageVector = when {
                        isChannelBusy -> Icons.Default.Close
                        else -> Icons.Default.Mic
                    },
                    contentDescription = null,
                    tint = when {
                        isSpeaking -> Color(0xFF131315)
                        isChannelBusy -> TactileColors.onSecondaryContainer
                        else -> TactileColors.onSurface
                    },
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = when {
                        isSpeaking -> "TRANSMITTING"
                        isChannelBusy -> "CHANNEL BUSY"
                        else -> "HOLD TO TALK"
                    },
                    color = when {
                        isSpeaking -> Color(0xFF131315)
                        isChannelBusy -> TactileColors.onSecondaryContainer
                        else -> TactileColors.onSurface
                    },
                    fontFamily = SpaceGrotesk,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
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
        modifier = Modifier
            .height(80.dp)
            .fillMaxWidth(),
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
                        PulsingDot(TactileColors.primaryContainer)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "YOU ARE BROADCASTING",
                            color = TactileColors.primary,
                            fontFamily = SpaceGrotesk,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            letterSpacing = 1.sp
                        )
                    }
                }
                "OTHERS" -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        PulsingDot(TactileColors.statusConnecting)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "${currentSpeakerName?.uppercase() ?: "MEMBER"} IS TRANSMITTING",
                            color = TactileColors.statusConnecting,
                            fontFamily = SpaceGrotesk,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            letterSpacing = 1.sp
                        )
                    }
                }
                else -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Channel is Idle",
                            color = TactileColors.onSurfaceVariant.copy(alpha = 0.6f),
                            fontFamily = Manrope,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        if (lastSpeakerName != null) {
                            Text(
                                text = "Last: $lastSpeakerName",
                                color = TactileColors.onSecondaryContainer,
                                fontFamily = SpaceGrotesk,
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
