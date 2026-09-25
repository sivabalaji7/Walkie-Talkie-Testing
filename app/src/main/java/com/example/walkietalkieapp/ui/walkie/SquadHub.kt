package com.example.walkietalkieapp.ui.walkie

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.walkietalkieapp.bluetooth.DiscoveredSquad
import com.example.walkietalkieapp.ui.theme.*
import com.example.walkietalkieapp.wifidirect.DiscoveredWifiSquad
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

data class SquadMember(
    val name: String,
    val avatar: String,
    val online: Boolean,
    val isSpeaking: Boolean = false,
    val isOwner: Boolean = false
)

data class Squad(
    val id: String,
    val name: String,
    val lastActive: String,
    val secure: Boolean,
    val members: List<SquadMember>
)

@Composable
fun SquadHub(
    mode: ConnectivityMode,
    squads: List<Squad>,
    activeIndex: Int,
    onStep: (direction: Int) -> Unit,
    onJoin: () -> Unit,
    onCreate: () -> Unit,
    onJoinByCode: () -> Unit,
    onRefresh: () -> Unit = {},
    discoveredWifiSquads: List<DiscoveredWifiSquad> = emptyList(),
    onJoinWifiSquad: (DiscoveredWifiSquad) -> Unit = {},
    isWifiConnecting: Boolean = false,
    isWifiScanning: Boolean = false,
    discoveredBtSquads: List<DiscoveredSquad> = emptyList(),
    onJoinBtSquad: (DiscoveredSquad) -> Unit = {},
    isBtConnecting: Boolean = false,
    isBtScanning: Boolean = false,
    isExternalWifiConnected: Boolean = false,
    connectedWifiSsid: String? = null,
    onRequestDisconnectWifi: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isOnline = mode == ConnectivityMode.INTERNET
    val activeSquad = squads.getOrNull(activeIndex) ?: squads.firstOrNull()
    val currentTheme = ModeThemes.get(mode)
    val haptic = LocalHapticFeedback.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
    ) {
        // Main Chassis Container
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(elevation = 4.dp, shape = RoundedCornerShape(32.dp))
                .clip(RoundedCornerShape(32.dp))
                .background(WalkieDeviceBody)
                .border(1.dp, WalkieCardBorder, RoundedCornerShape(32.dp))
                .heightIn(min = 490.dp)
        ) {
            // Dynamic Mode-Themed Highlighter Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(currentTheme.gradient)
                    .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                AnimatedContent(
                    targetState = Pair(isOnline, activeSquad),
                    transitionSpec = {
                        (slideInVertically { it / 2 } + fadeIn())
                            .togetherWith(slideOutVertically { -it / 2 } + fadeOut())
                    },
                    label = "highlighterBar"
                ) { (online, squad) ->
                    if (online && squad != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.Black.copy(alpha = 0.25f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Radio,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = squad.name,
                                    fontFamily = SpaceGrotesk,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White,
                                    maxLines = 1
                                )
                                val activeMembersText = if (squad.members.isNotEmpty()) {
                                    "${squad.members.count { it.online }} online · ${squad.members.size} members"
                                } else {
                                    squad.lastActive
                                }
                                Text(
                                    text = activeMembersText,
                                    fontFamily = PlusJakartaSans,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White.copy(alpha = 0.8f)
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (squad.secure) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = "Secure",
                                        tint = Color.White.copy(alpha = 0.9f),
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                                Text(
                                    text = "READY",
                                    fontFamily = SpaceGrotesk,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White.copy(alpha = 0.9f),
                                    letterSpacing = 1.sp
                                )
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color.Black.copy(alpha = 0.25f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (mode == ConnectivityMode.WIFI_DIRECT) Icons.Default.Wifi else Icons.Default.WifiOff,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = if (online) "No squads connected"
                                        else if (mode == ConnectivityMode.WIFI_DIRECT && isExternalWifiConnected) "External Wi-Fi Active"
                                        else "No saved squads offline",
                                        fontFamily = SpaceGrotesk,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (mode == ConnectivityMode.WIFI_DIRECT && isExternalWifiConnected) Color(0xFFF59E0B) else Color.White
                                    )
                                    Text(
                                        text = if (online) "Create or join a squad below"
                                        else if (mode == ConnectivityMode.WIFI_DIRECT && isExternalWifiConnected) "Disconnect Wi-Fi to enable direct squad radio"
                                        else if (mode == ConnectivityMode.WIFI_DIRECT) "Radar scanning on-air squads"
                                        else "Start one on the spot",
                                        fontFamily = PlusJakartaSans,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White.copy(alpha = 0.8f)
                                    )
                                }
                            }

                            // Refresh button at top-right corner of the radio container
                            val refreshSpinTransition = rememberInfiniteTransition(label = "refreshSpin")
                            val spinAngle by refreshSpinTransition.animateFloat(
                                initialValue = 0f,
                                targetValue = 360f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(durationMillis = 900, easing = LinearEasing),
                                    repeatMode = RepeatMode.Restart
                                ),
                                label = "refreshRotation"
                            )
                            val isRefreshing = (mode == ConnectivityMode.WIFI_DIRECT && isWifiScanning) ||
                                    (mode == ConnectivityMode.BLUETOOTH && isBtScanning)
                            val refreshTint = if (!isRefreshing) Color.White else if (mode == ConnectivityMode.BLUETOOTH) Color(0xFF0284C7) else WalkieGreen

                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.Black.copy(alpha = 0.25f))
                                    .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onRefresh()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Refresh",
                                    tint = refreshTint,
                                    modifier = Modifier
                                        .size(19.dp)
                                        .graphicsLayer {
                                            rotationZ = if (isRefreshing) spinAngle else 0f
                                        }
                                )
                            }
                        }
                    }
                }
            }

            if (mode == ConnectivityMode.WIFI_DIRECT) {
                WifiDirectRadarSection(
                    discoveredSquads = discoveredWifiSquads,
                    isConnecting = isWifiConnecting,
                    isScanning = isWifiScanning,
                    onJoinSquad = onJoinWifiSquad,
                    onCreateSquad = onCreate,
                    onRefresh = onRefresh,
                    currentTheme = currentTheme,
                    isExternalWifiConnected = isExternalWifiConnected,
                    connectedWifiSsid = connectedWifiSsid,
                    onRequestDisconnectWifi = onRequestDisconnectWifi,
                    modifier = Modifier.weight(1f)
                )
            } else if (mode == ConnectivityMode.BLUETOOTH) {
                BluetoothRadarSection(
                    discoveredSquads = discoveredBtSquads,
                    isConnecting = isBtConnecting,
                    isScanning = isBtScanning,
                    onJoinSquad = onJoinBtSquad,
                    onCreateSquad = onCreate,
                    onRefresh = onRefresh,
                    currentTheme = currentTheme,
                    modifier = Modifier.weight(1f)
                )
            } else {
                // Squad List or Offline Direct Action Pills
                Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(
                        start = 14.dp,
                        end = if (squads.isNotEmpty()) 40.dp else 14.dp,
                        top = 8.dp,
                        bottom = 8.dp
                    )
            ) {
                if (squads.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(vertical = 14.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = if (mode == ConnectivityMode.BLUETOOTH) Icons.Default.Bluetooth
                                else if (mode == ConnectivityMode.WIFI_DIRECT) Icons.Default.Wifi
                                else Icons.Default.GroupAdd,
                            contentDescription = null,
                            tint = currentTheme.primaryColor,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = if (mode == ConnectivityMode.INTERNET) "No Squads Found"
                                else if (mode == ConnectivityMode.BLUETOOTH) "Searching Bluetooth Signals..."
                                else "Searching Wi-Fi Direct Mesh...",
                            fontFamily = SpaceGrotesk,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = WalkieTextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (mode == ConnectivityMode.INTERNET) "Create or join a squad below to connect"
                                else "Create a local squad or tap Rescan",
                            fontFamily = PlusJakartaSans,
                            fontSize = 11.sp,
                            color = WalkieTextMuted
                        )
                        if (!isOnline) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(currentTheme.primaryColor)
                                        .clickable { onCreate() }
                                        .padding(horizontal = 14.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
                                    Text("Create", color = Color.Black, fontFamily = SpaceGrotesk, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(WalkieButton)
                                        .border(1.dp, currentTheme.primaryColor.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                        .clickable { onJoinByCode() }
                                        .padding(horizontal = 14.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, tint = currentTheme.primaryColor, modifier = Modifier.size(14.dp))
                                    Text("Rescan", color = currentTheme.primaryColor, fontFamily = SpaceGrotesk, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        squads.forEachIndexed { index, squad ->
                            val isSelected = index == activeIndex.mod(squads.size)
                            val offset = index - activeIndex.mod(squads.size)
                            val itemAlpha = if (isSelected) 1f else (0.8f - (abs(offset) * 0.15f)).coerceAtLeast(0.35f)

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (isSelected) WalkieButton.copy(alpha = 0.8f) else Color.Transparent)
                                    .border(
                                        1.dp,
                                        if (isSelected) currentTheme.primaryColor.copy(alpha = 0.35f) else Color.Transparent,
                                        RoundedCornerShape(14.dp)
                                    )
                                    .clickable {
                                        if (isSelected) {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            onJoin()
                                        } else {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            onStep(index - activeIndex.mod(squads.size))
                                        }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(WalkieButton),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (mode == ConnectivityMode.BLUETOOTH) Icons.Default.Bluetooth
                                            else if (mode == ConnectivityMode.WIFI_DIRECT) Icons.Default.Wifi
                                            else Icons.Default.Group,
                                        contentDescription = null,
                                        tint = if (isSelected) currentTheme.primaryColor else WalkieTextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = squad.name,
                                        fontFamily = SpaceGrotesk,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) WalkieTextPrimary else WalkieTextSecondary.copy(alpha = itemAlpha),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val subText = if (squad.members.isNotEmpty() && isOnline) {
                                        "${squad.members.size} members · ${squad.members.count { it.online }} online"
                                    } else {
                                        squad.lastActive
                                    }
                                    Text(
                                        text = subText,
                                        fontFamily = PlusJakartaSans,
                                        fontSize = 11.sp,
                                        color = WalkieTextMuted.copy(alpha = itemAlpha),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                if (squad.members.isNotEmpty() && isOnline) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy((-6).dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        squad.members.take(3).forEach { m ->
                                            Box(
                                                modifier = Modifier
                                                    .size(22.dp)
                                                    .clip(CircleShape)
                                                    .background(WalkieButton)
                                                    .border(1.dp, if (m.online) WalkieAmber else WalkieCardBorder, CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = m.avatar.ifBlank { m.name.take(1).uppercase() },
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (m.online) WalkieAmber else WalkieTextSecondary
                                                )
                                            }
                                        }
                                        if (squad.members.size > 3) {
                                            Box(
                                                modifier = Modifier
                                                    .size(22.dp)
                                                    .clip(CircleShape)
                                                    .background(WalkieButton)
                                                    .border(1.dp, WalkieCardBorder, CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "+${squad.members.size - 3}",
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = WalkieTextMuted
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Bottom Footer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(width = 1.dp, color = WalkieCardBorder, shape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(currentTheme.primaryColor)
                    )
                    Text(
                        text = if (squads.isNotEmpty()) "Pull wheel or tap squad to join" else if (isOnline) "No squads available" else "Offline mesh active",
                        fontFamily = PlusJakartaSans,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = WalkieTextSecondary
                    )
                }
                Text(
                    text = if (squads.isNotEmpty()) "${activeIndex.mod(squads.size) + 1}/${squads.size}" else "—",
                    fontFamily = SpaceGrotesk,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = WalkieTextMuted,
                    letterSpacing = 1.sp
                )
                }
            }
        }

        // SideScroller positioned on right edge (disabled for WIFI_DIRECT)
        if (mode != ConnectivityMode.WIFI_DIRECT) {
            SideScroller(
                onStep = onStep,
                onPull = onJoin,
                disabled = !isOnline,
                label = if (isOnline) "PULL TO JOIN" else "OFFLINE",
                currentIndex = activeIndex,
                itemCount = squads.size.coerceAtLeast(1),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .offset(x = 12.dp)
            )
        }
    }
}

@Composable
fun ActionPill(
    icon: ImageVector,
    label: String,
    sub: String,
    isPrimary: Boolean,
    gradient: Brush,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val haptic = LocalHapticFeedback.current
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 550f),
        label = "actionPillScale"
    )

    Row(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .then(
                if (isPrimary) Modifier.background(gradient) else Modifier.background(WalkieButton)
            )
            .border(
                1.dp,
                if (isPrimary) Color.White.copy(alpha = 0.25f) else WalkieCardBorder,
                RoundedCornerShape(16.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                }
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isPrimary) Color.White else WalkieTextPrimary,
                modifier = Modifier.size(18.dp)
            )
        }
        Column {
            Text(
                text = label,
                fontFamily = SpaceGrotesk,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = if (isPrimary) Color.White else WalkieTextPrimary
            )
            Text(
                text = sub,
                fontFamily = PlusJakartaSans,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = if (isPrimary) Color.White.copy(alpha = 0.8f) else WalkieTextSecondary
            )
        }
    }
}

@Composable
fun WifiDirectRadarSection(
    discoveredSquads: List<DiscoveredWifiSquad>,
    isConnecting: Boolean,
    isScanning: Boolean = false,
    onJoinSquad: (DiscoveredWifiSquad) -> Unit,
    onCreateSquad: () -> Unit,
    onRefresh: () -> Unit = {},
    currentTheme: ModeThemeConfig,
    isExternalWifiConnected: Boolean = false,
    connectedWifiSsid: String? = null,
    onRequestDisconnectWifi: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    Column(modifier = modifier.fillMaxWidth()) {
        // Curved rectangle tactical radar container fitting inside chassis
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 14.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0xFF06140C))
                .border(1.dp, WalkieGreen.copy(alpha = 0.35f), RoundedCornerShape(22.dp))
        ) {
            val infiniteTransition = rememberInfiniteTransition(label = "wifiRadarSweep")
            val sweepAngle by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(3600, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "sweepAngle"
            )

            // Mildly visible rotating tactical radar canvas
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val radius = (minOf(size.width, size.height) / 2f) * 0.95f

                // Concentric Range Rings (Subtle green tint)
                drawCircle(
                    color = WalkieGreen.copy(alpha = 0.08f),
                    radius = radius * 0.33f,
                    center = center,
                    style = Stroke(width = 1f)
                )
                drawCircle(
                    color = WalkieGreen.copy(alpha = 0.12f),
                    radius = radius * 0.66f,
                    center = center,
                    style = Stroke(width = 1f)
                )
                drawCircle(
                    color = WalkieGreen.copy(alpha = 0.18f),
                    radius = radius * 0.98f,
                    center = center,
                    style = Stroke(width = 1.2f)
                )

                // Crosshairs
                drawLine(
                    color = WalkieGreen.copy(alpha = 0.08f),
                    start = Offset(center.x, 0f),
                    end = Offset(center.x, size.height),
                    strokeWidth = 1f
                )
                drawLine(
                    color = WalkieGreen.copy(alpha = 0.08f),
                    start = Offset(0f, center.y),
                    end = Offset(size.width, center.y),
                    strokeWidth = 1f
                )

                // Rotating Sweep Line with Phosphor Glow Persistence
                val sweepRad = Math.toRadians(sweepAngle.toDouble())
                val sweepEnd = Offset(
                    (center.x + radius * cos(sweepRad)).toFloat(),
                    (center.y + radius * sin(sweepRad)).toFloat()
                )
                drawLine(
                    brush = Brush.radialGradient(
                        colors = listOf(WalkieGreen.copy(alpha = 0.22f), WalkieGreen.copy(alpha = 0.02f)),
                        center = center,
                        radius = radius
                    ),
                    start = center,
                    end = sweepEnd,
                    strokeWidth = 2.2f
                )
            }

            // Tactical Radar Top Badge
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 10.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.65f))
                    .border(0.5.dp, WalkieGreen.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (isScanning) WalkieGreen else WalkieGreen.copy(alpha = 0.4f))
                    )
                    Text(
                        text = if (isScanning) "P2P MESH RADAR • REALTIME SCANNING" else "P2P MESH RADAR • 5.0 GHz READY",
                        color = WalkieGreen,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = SpaceGrotesk,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            // Nearby Detected Squads Floating Inside Radar Viewport
            if (discoveredSquads.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Wifi,
                        contentDescription = null,
                        tint = WalkieGreen.copy(alpha = 0.6f),
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = if (isScanning) "SCANNING REALTIME SIGNALS..." else "SCAN IDLE — TAP TO SCAN",
                        fontFamily = SpaceGrotesk,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = WalkieTextPrimary,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Create a squad below or await nearby rooms",
                        fontFamily = PlusJakartaSans,
                        fontSize = 10.5.sp,
                        color = WalkieTextMuted
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(WalkieGreen.copy(alpha = 0.15f))
                            .border(1.dp, WalkieGreen.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onRefresh()
                            }
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                tint = WalkieGreen,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = if (isScanning) "SCANNING NOW..." else "RESCAN AIRWAVES",
                                fontFamily = SpaceGrotesk,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = WalkieGreen
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp, vertical = 44.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(discoveredSquads, key = { it.deviceAddress }) { squad ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .shadow(elevation = 6.dp, shape = RoundedCornerShape(16.dp))
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFF0F261B).copy(alpha = 0.92f))
                                .border(1.dp, WalkieGreen.copy(alpha = 0.55f), RoundedCornerShape(16.dp))
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onJoinSquad(squad)
                                }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(WalkieGreen.copy(alpha = 0.2f))
                                    .border(1.dp, WalkieGreen.copy(alpha = 0.45f), RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Wifi,
                                    contentDescription = null,
                                    tint = WalkieGreen,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = squad.squadName,
                                    fontFamily = SpaceGrotesk,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Owner: ${squad.hostUsername}",
                                    fontFamily = PlusJakartaSans,
                                    fontSize = 11.sp,
                                    color = WalkieGreen.copy(alpha = 0.85f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(WalkieGreen.copy(alpha = 0.2f))
                                            .border(1.dp, WalkieGreen.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                                            .clickable {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                if (isExternalWifiConnected) {
                                                    onRequestDisconnectWifi()
                                                } else {
                                                    onJoinSquad(squad)
                                                }
                                            }
                                            .padding(horizontal = 9.dp, vertical = 5.dp)
                                    ) {
                                        Text(
                                            text = if (isConnecting) "REQUESTING ⏳" else "REQUEST JOIN ↗",
                                            fontFamily = SpaceGrotesk,
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = WalkieGreen,
                                            letterSpacing = 0.5.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

        // Warning banner when connected to external Wi-Fi
        if (isExternalWifiConnected) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF2B180A))
                    .border(1.dp, Color(0xFFF59E0B).copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onRequestDisconnectWifi()
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color(0xFFF59E0B),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "External Wi-Fi Connected",
                        fontFamily = SpaceGrotesk,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFF59E0B)
                    )
                    Text(
                        text = if (!connectedWifiSsid.isNullOrBlank() && connectedWifiSsid != "Wi-Fi Network") {
                            "Disconnect from '$connectedWifiSsid' to use Wi-Fi Direct"
                        } else {
                            "Please disconnect from current Wi-Fi network"
                        },
                        fontFamily = PlusJakartaSans,
                        fontSize = 10.sp,
                        color = Color.White.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFFF59E0B).copy(alpha = 0.25f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "FIX",
                        fontFamily = SpaceGrotesk,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFF59E0B)
                    )
                }
            }
        }

        // Long Curved Rectangular Create Squad Button at the bottom
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 14.dp)
                .shadow(elevation = 6.dp, shape = RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
                .background(currentTheme.gradient)
                .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                .clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    if (isExternalWifiConnected) {
                        onRequestDisconnectWifi()
                    } else {
                        onCreateSquad()
                    }
                }
                .padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                tint = Color.Black,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "CREATE SQUAD",
                fontFamily = SpaceGrotesk,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.Black,
                letterSpacing = 0.5.sp
            )
        }
    }
}

@Composable
fun BluetoothRadarSection(
    discoveredSquads: List<DiscoveredSquad>,
    isConnecting: Boolean,
    isScanning: Boolean = false,
    onJoinSquad: (DiscoveredSquad) -> Unit,
    onCreateSquad: () -> Unit,
    onRefresh: () -> Unit = {},
    currentTheme: ModeThemeConfig,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val btPrimary = Color(0xFF0284C7)

    Column(modifier = modifier.fillMaxWidth()) {
        // Curved rectangle tactical radar container fitting inside chassis
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 14.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0xFF041226))
                .border(1.dp, btPrimary.copy(alpha = 0.35f), RoundedCornerShape(22.dp))
        ) {
            val infiniteTransition = rememberInfiniteTransition(label = "btRadarSweep")
            val sweepAngle by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(3600, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "sweepAngle"
            )

            // Mildly visible rotating tactical radar canvas
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val radius = (minOf(size.width, size.height) / 2f) * 0.95f

                // Concentric Range Rings (Subtle blue tint)
                drawCircle(
                    color = btPrimary.copy(alpha = 0.08f),
                    radius = radius * 0.33f,
                    center = center,
                    style = Stroke(width = 1f)
                )
                drawCircle(
                    color = btPrimary.copy(alpha = 0.12f),
                    radius = radius * 0.66f,
                    center = center,
                    style = Stroke(width = 1f)
                )
                drawCircle(
                    color = btPrimary.copy(alpha = 0.18f),
                    radius = radius * 0.98f,
                    center = center,
                    style = Stroke(width = 1.2f)
                )

                // Crosshairs
                drawLine(
                    color = btPrimary.copy(alpha = 0.08f),
                    start = Offset(center.x, 0f),
                    end = Offset(center.x, size.height),
                    strokeWidth = 1f
                )
                drawLine(
                    color = btPrimary.copy(alpha = 0.08f),
                    start = Offset(0f, center.y),
                    end = Offset(size.width, center.y),
                    strokeWidth = 1f
                )

                // Rotating Sweep Line with Blue Phosphor Glow
                val sweepRad = Math.toRadians(sweepAngle.toDouble())
                val sweepEnd = Offset(
                    (center.x + radius * cos(sweepRad)).toFloat(),
                    (center.y + radius * sin(sweepRad)).toFloat()
                )
                drawLine(
                    brush = Brush.radialGradient(
                        colors = listOf(btPrimary.copy(alpha = 0.22f), btPrimary.copy(alpha = 0.02f)),
                        center = center,
                        radius = radius
                    ),
                    start = center,
                    end = sweepEnd,
                    strokeWidth = 2.2f
                )
            }

            // Tactical Radar Top Badge
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 10.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.65f))
                    .border(0.5.dp, btPrimary.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (isScanning) btPrimary else btPrimary.copy(alpha = 0.4f))
                    )
                    Text(
                        text = if (isScanning) "BLE MESH RADAR • REALTIME SCANNING" else "BLE MESH RADAR • 2.4 GHz READY",
                        color = btPrimary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = SpaceGrotesk,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            // Nearby Detected Squads Floating Inside Radar Viewport
            if (discoveredSquads.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Bluetooth,
                        contentDescription = null,
                        tint = btPrimary.copy(alpha = 0.6f),
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = if (isScanning) "SCANNING REALTIME SIGNALS..." else "SCAN IDLE — TAP TO SCAN",
                        fontFamily = SpaceGrotesk,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = WalkieTextPrimary,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Create a squad below or await nearby rooms",
                        fontFamily = PlusJakartaSans,
                        fontSize = 10.5.sp,
                        color = WalkieTextMuted
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(btPrimary.copy(alpha = 0.15f))
                            .border(1.dp, btPrimary.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onRefresh()
                            }
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                tint = btPrimary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = if (isScanning) "SCANNING NOW..." else "RESCAN AIRWAVES",
                                fontFamily = SpaceGrotesk,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = btPrimary
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp, vertical = 44.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(discoveredSquads, key = { it.hostAddress }) { squad ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .shadow(elevation = 6.dp, shape = RoundedCornerShape(16.dp))
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFF08203E).copy(alpha = 0.92f))
                                .border(1.dp, btPrimary.copy(alpha = 0.55f), RoundedCornerShape(16.dp))
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onJoinSquad(squad)
                                }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(btPrimary.copy(alpha = 0.2f))
                                    .border(1.dp, btPrimary.copy(alpha = 0.45f), RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bluetooth,
                                    contentDescription = null,
                                    tint = btPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = squad.squadName,
                                    fontFamily = SpaceGrotesk,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Owner: ${squad.hostUsername} • RSSI: ${squad.rssi} dBm",
                                    fontFamily = PlusJakartaSans,
                                    fontSize = 11.sp,
                                    color = btPrimary.copy(alpha = 0.85f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(btPrimary.copy(alpha = 0.2f))
                                    .border(1.dp, btPrimary.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 9.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = if (isConnecting) "CONNECTING ⏳" else "CONNECT ↗",
                                    fontFamily = SpaceGrotesk,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = btPrimary,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // Long Curved Rectangular Create Squad Button at the bottom
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 14.dp)
                .shadow(elevation = 6.dp, shape = RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
                .background(currentTheme.gradient)
                .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                .clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onCreateSquad()
                }
                .padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                tint = Color.Black,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "CREATE SQUAD",
                fontFamily = SpaceGrotesk,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.Black,
                letterSpacing = 0.5.sp
            )
        }
    }
}

