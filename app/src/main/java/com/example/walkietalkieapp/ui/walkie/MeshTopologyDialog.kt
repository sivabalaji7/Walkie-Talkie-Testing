package com.example.walkietalkieapp.ui.walkie

import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.walkietalkieapp.mesh.AdaptiveMeshManager
import com.example.walkietalkieapp.mesh.LinkQualityGrade
import com.example.walkietalkieapp.mesh.MeshTransportType
import com.example.walkietalkieapp.ui.theme.*

@Composable
fun MeshTopologyDialog(
    onDismiss: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val activeRoute by AdaptiveMeshManager.activeRoute.collectAsState()
    val isFailoverActive by AdaptiveMeshManager.isFailoverActive.collectAsState()
    val isAutoFailoverEnabled by AdaptiveMeshManager.isAutoFailoverEnabled.collectAsState()
    val isMeshRelayEnabled by AdaptiveMeshManager.isMeshRelayEnabled.collectAsState()
    val primaryLinkQuality by AdaptiveMeshManager.primaryLinkQuality.collectAsState()
    val transportMetrics by AdaptiveMeshManager.transportMetrics.collectAsState()
    val topologyNodes by AdaptiveMeshManager.topologyNodes.collectAsState()
    val lastNotice by AdaptiveMeshManager.lastFailoverNotice.collectAsState()
    val recentEvents by AdaptiveMeshManager.recentEvents.collectAsState()

    val infiniteTransition = rememberInfiniteTransition(label = "meshPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.82f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.94f)
                    .fillMaxHeight(0.88f)
                    .clickable(enabled = false) {}
                    .shadow(elevation = 28.dp, shape = RoundedCornerShape(26.dp)),
                shape = RoundedCornerShape(26.dp),
                color = WalkieDeviceBody,
                border = androidx.compose.foundation.BorderStroke(1.dp, WalkieCardBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (isFailoverActive) WalkieAmber.copy(alpha = 0.22f)
                                        else StatusReady.copy(alpha = 0.20f)
                                    )
                                    .border(
                                        1.dp,
                                        if (isFailoverActive) WalkieAmber.copy(alpha = 0.6f)
                                        else StatusReady.copy(alpha = 0.6f),
                                        RoundedCornerShape(10.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Hub,
                                    contentDescription = "Mesh Hub",
                                    tint = if (isFailoverActive) WalkieAmber else StatusReady,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Column {
                                Text(
                                    text = "HYBRID MESH ROUTER",
                                    fontFamily = SpaceGrotesk,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = WalkieTextPrimary,
                                    letterSpacing = 0.8.sp
                                )
                                Text(
                                    text = "AUTONOMOUS TRIPLE-TRANSPORT FAILOVER",
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isFailoverActive) WalkieAmber else Color(0xFF38BDF8),
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }

                        // Close Button
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onDismiss()
                            },
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(WalkieButton)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = WalkieTextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Scrollable Telemetry Deck
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // 1. Active Primary Transport Card
                        item {
                            val activeBorder = if (isFailoverActive) WalkieAmber else StatusReady
                            val activeBg = if (isFailoverActive) Color(0xFF78350F).copy(alpha = 0.25f) else Color(0xFF064E3B).copy(alpha = 0.25f)

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(activeBg)
                                    .border(1.dp, activeBorder.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                                    .padding(14.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "ACTIVE AUDIO PIPELINE",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = WalkieTextSecondary,
                                            letterSpacing = 1.sp
                                        )

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(7.dp)
                                                    .clip(CircleShape)
                                                    .background(activeBorder.copy(alpha = pulseAlpha))
                                            )
                                            Text(
                                                text = if (isFailoverActive) "FAILOVER ROUTE ENGAGED" else "OPTIMAL ROUTE",
                                                fontSize = 9.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = activeBorder,
                                                letterSpacing = 0.5.sp
                                            )
                                        }
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        val icon = when (activeRoute) {
                                            MeshTransportType.INTERNET -> Icons.Default.Language
                                            MeshTransportType.WIFI_DIRECT -> Icons.Default.Wifi
                                            MeshTransportType.BLUETOOTH -> Icons.Default.Bluetooth
                                            MeshTransportType.HYBRID_AUTO -> Icons.Default.Hub
                                        }
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = null,
                                            tint = activeBorder,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Column {
                                            Text(
                                                text = activeRoute.label,
                                                fontFamily = SpaceGrotesk,
                                                fontSize = 17.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = Color.White
                                            )
                                            val qualityText = when (primaryLinkQuality.quality) {
                                                LinkQualityGrade.EXCELLENT -> "Excellent Signal • ${primaryLinkQuality.rttMs}ms RTT • ${primaryLinkQuality.packetLossPercent}% Loss"
                                                LinkQualityGrade.GOOD -> "Good Signal • ${primaryLinkQuality.rttMs}ms RTT • ${primaryLinkQuality.packetLossPercent}% Loss"
                                                LinkQualityGrade.FAIR -> "Degraded Link • ${primaryLinkQuality.rttMs}ms RTT • ${primaryLinkQuality.packetLossPercent}% Loss"
                                                LinkQualityGrade.POOR -> "Severe Loss • ${primaryLinkQuality.packetLossPercent}% Loss"
                                                LinkQualityGrade.DISCONNECTED -> "Offline / Disconnected"
                                            }
                                            Text(
                                                text = qualityText,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = WalkieTextSecondary
                                            )
                                        }
                                    }

                                    // Failover Notice Banner if active
                                    if (isFailoverActive) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(WalkieAmber.copy(alpha = 0.25f))
                                                .border(0.6.dp, WalkieAmber.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                                .padding(horizontal = 10.dp, vertical = 6.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = lastNotice ?: "Failed over from Internet",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFFEF08A)
                                            )
                                            Text(
                                                text = "RESTORE",
                                                fontSize = 9.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = WalkieAmber,
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(Color.Black.copy(alpha = 0.35f))
                                                    .clickable {
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        AdaptiveMeshManager.restoreInternetPrimary()
                                                    }
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 2. Triple-Transport Standby Matrix
                        item {
                            Text(
                                text = "TRIPLE-TRANSPORT STANDBY MATRIX",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = WalkieTextSecondary,
                                letterSpacing = 0.8.sp
                            )
                        }

                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(WalkieDeviceBodyLight.copy(alpha = 0.4f))
                                    .border(1.dp, WalkieCardBorder, RoundedCornerShape(14.dp))
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                TransportMatrixRow(
                                    name = "Internet (WebRTC Global)",
                                    icon = Icons.Default.Language,
                                    iconColor = Color(0xFF38BDF8),
                                    status = if (primaryLinkQuality.isAvailable) "${primaryLinkQuality.rttMs}ms • Ready" else "Disconnected",
                                    isPrimary = activeRoute == MeshTransportType.INTERNET
                                )
                                HorizontalDivider(color = WalkieCardBorder.copy(alpha = 0.5f), thickness = 0.5.dp)
                                TransportMatrixRow(
                                    name = "Wi-Fi Direct P2P (High Speed)",
                                    icon = Icons.Default.Wifi,
                                    iconColor = StatusReady,
                                    status = "18ms • Group Hot Standby",
                                    isPrimary = activeRoute == MeshTransportType.WIFI_DIRECT
                                )
                                HorizontalDivider(color = WalkieCardBorder.copy(alpha = 0.5f), thickness = 0.5.dp)
                                TransportMatrixRow(
                                    name = "Bluetooth LE Mesh (Proximity)",
                                    icon = Icons.Default.Bluetooth,
                                    iconColor = Color(0xFFA78BFA),
                                    status = "Low Power • Beacon Standby",
                                    isPrimary = activeRoute == MeshTransportType.BLUETOOTH
                                )
                            }
                        }

                        // 3. Squad Network Topology Nodes
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "SQUAD NETWORK TOPOLOGY RADAR",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = WalkieTextSecondary,
                                    letterSpacing = 0.8.sp
                                )
                                Text(
                                    text = "${topologyNodes.size} NODES IN RANGE",
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = StatusReady,
                                    letterSpacing = 0.4.sp
                                )
                            }
                        }

                        if (topologyNodes.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(WalkieDeviceBodyLight.copy(alpha = 0.25f))
                                        .padding(14.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Single operator mode. Invite squad peers to visualize multi-hop mesh routes.",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = WalkieTextSecondary
                                    )
                                }
                            }
                        } else {
                            items(topologyNodes) { node ->
                                MeshNodeRow(node = node)
                            }
                        }

                        // 4. Autonomous Failover & Relay Toggles
                        item {
                            Text(
                                text = "AUTONOMOUS ROUTING PROTOCOLS",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = WalkieTextSecondary,
                                letterSpacing = 0.8.sp
                            )
                        }

                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(WalkieDeviceBodyLight.copy(alpha = 0.4f))
                                    .border(1.dp, WalkieCardBorder, RoundedCornerShape(14.dp))
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // Toggle 1: Auto Failover
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Auto-Failover on Network Drop",
                                            fontFamily = SpaceGrotesk,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = WalkieTextPrimary
                                        )
                                        Text(
                                            text = "Instantly bridges voice to local P2P when internet loss > 25%",
                                            fontSize = 10.sp,
                                            color = WalkieTextSecondary
                                        )
                                    }
                                    Switch(
                                        checked = isAutoFailoverEnabled,
                                        onCheckedChange = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            AdaptiveMeshManager.setAutoFailoverEnabled(it)
                                        },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color.White,
                                            checkedTrackColor = StatusReady,
                                            uncheckedThumbColor = WalkieTextSecondary,
                                            uncheckedTrackColor = WalkieButton
                                        )
                                    )
                                }

                                HorizontalDivider(color = WalkieCardBorder.copy(alpha = 0.5f), thickness = 0.5.dp)

                                // Toggle 2: Mesh Voice Relay
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Mesh Voice Relay Repeater",
                                            fontFamily = SpaceGrotesk,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = WalkieTextPrimary
                                        )
                                        Text(
                                            text = "Relays transmissions for out-of-range squad peers (Multi-Hop)",
                                            fontSize = 10.sp,
                                            color = WalkieTextSecondary
                                        )
                                    }
                                    Switch(
                                        checked = isMeshRelayEnabled,
                                        onCheckedChange = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            AdaptiveMeshManager.setMeshRelayEnabled(it)
                                        },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color.White,
                                            checkedTrackColor = Color(0xFF38BDF8),
                                            uncheckedThumbColor = WalkieTextSecondary,
                                            uncheckedTrackColor = WalkieButton
                                        )
                                    )
                                }
                            }
                        }

                        // 5. Manual Transport Override Switches
                        item {
                            Text(
                                text = "MANUAL TRANSPORT OVERRIDE",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = WalkieTextSecondary,
                                letterSpacing = 0.8.sp
                            )
                        }

                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ManualOverrideChip(
                                    label = "🌐 WEBRTC",
                                    isSelected = activeRoute == MeshTransportType.INTERNET,
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        AdaptiveMeshManager.restoreInternetPrimary()
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                ManualOverrideChip(
                                    label = "⚡ WI-FI P2P",
                                    isSelected = activeRoute == MeshTransportType.WIFI_DIRECT,
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        AdaptiveMeshManager.triggerFailover(MeshTransportType.WIFI_DIRECT, "Manual operator override")
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                ManualOverrideChip(
                                    label = "📶 BT MESH",
                                    isSelected = activeRoute == MeshTransportType.BLUETOOTH,
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        AdaptiveMeshManager.triggerFailover(MeshTransportType.BLUETOOTH, "Manual operator override")
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Close Button
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onDismiss()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = WalkieButton)
                    ) {
                        Text(
                            text = "CLOSE RADAR",
                            fontFamily = SpaceGrotesk,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = WalkieTextPrimary,
                            letterSpacing = 0.6.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TransportMatrixRow(
    name: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    status: String,
    isPrimary: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = name,
                fontSize = 11.sp,
                fontWeight = if (isPrimary) FontWeight.Bold else FontWeight.Medium,
                color = if (isPrimary) Color.White else WalkieTextPrimary
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (isPrimary) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(StatusReady.copy(alpha = 0.25f))
                        .border(0.5.dp, StatusReady.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "PRIMARY",
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = StatusReady
                    )
                }
            }
            Text(
                text = status,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = WalkieTextSecondary
            )
        }
    }
}

@Composable
private fun MeshNodeRow(node: com.example.walkietalkieapp.mesh.MeshPeerNode) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(WalkieDeviceBodyLight.copy(alpha = 0.35f))
            .border(0.6.dp, WalkieCardBorder.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(WalkieButton),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = node.callsign.take(1).uppercase(),
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    color = Color.White
                )
            }
            Column {
                Text(
                    text = node.callsign,
                    fontFamily = SpaceGrotesk,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "${node.latencyMs}ms • ${node.signalDbm} dBm",
                    fontSize = 9.sp,
                    color = WalkieTextSecondary
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (node.isRelayActive) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(Color(0xFF38BDF8).copy(alpha = 0.22f))
                        .border(0.5.dp, Color(0xFF38BDF8).copy(alpha = 0.5f), RoundedCornerShape(5.dp))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "RELAY",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF38BDF8)
                    )
                }
            }

            val routeTag = when (node.primaryTransport) {
                MeshTransportType.INTERNET -> "🌐 NET"
                MeshTransportType.WIFI_DIRECT -> "⚡ Wi-Fi"
                MeshTransportType.BLUETOOTH -> "📶 BT"
                MeshTransportType.HYBRID_AUTO -> "⚡ MESH"
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(5.dp))
                    .background(Color.Black.copy(alpha = 0.35f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = routeTag,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun ManualOverrideChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val isPressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = 0.52f, stiffness = 550f),
        label = "overrideScale"
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) WalkieAmber.copy(alpha = 0.25f) else WalkieButton)
            .border(
                0.8.dp,
                if (isSelected) WalkieAmber else WalkieCardBorder,
                RoundedCornerShape(8.dp)
            )
            .clickable(
                interactionSource = interaction,
                indication = null
            ) { onClick() }
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontFamily = SpaceGrotesk,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = if (isSelected) WalkieAmber else WalkieTextSecondary,
            letterSpacing = 0.4.sp
        )
    }
}
