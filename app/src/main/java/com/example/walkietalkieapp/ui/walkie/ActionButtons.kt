package com.example.walkietalkieapp.ui.walkie

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.walkietalkieapp.ui.theme.*

@Composable
fun ActionButtons(
    isPowered: Boolean,
    onPowerToggle: () -> Unit,
    onCreateChannel: () -> Unit,
    onPairedDevices: () -> Unit,
    onSpeaker: () -> Unit,
    onQuickActions: () -> Unit,
    onRadarClick: (() -> Unit)? = null,
    speakerOn: Boolean = true,
    inSquad: Boolean = false,
    mode: ConnectivityMode = ConnectivityMode.INTERNET,
    replayCount: Int = 0,
    radarTargetCount: Int = 0,
    isGpsActive: Boolean = false,
    activeChannelCode: String = "CH-01",
    modifier: Modifier = Modifier
) {
    val currentTheme = ModeThemes.get(mode)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Top Row: 3 buttons (Power/Exit, Channel, Devices/Members)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TacticalButton(
                icon = if (inSquad) Icons.AutoMirrored.Filled.ExitToApp else Icons.Default.PowerSettingsNew,
                label = if (inSquad) "Exit" else "Power",
                isActive = !inSquad && isPowered,
                accent = true,
                activeBrush = currentTheme.gradient,
                indicatorColor = currentTheme.primaryColor,
                onClick = onPowerToggle,
                modifier = Modifier.weight(1f)
            )
            TacticalButton(
                icon = Icons.Default.Radio,
                label = "Channel",
                isActive = inSquad,
                accent = false,
                activeBrush = currentTheme.gradient,
                indicatorColor = currentTheme.primaryColor,
                badgeText = if (inSquad) activeChannelCode else "",
                onClick = onCreateChannel,
                modifier = Modifier.weight(1f)
            )
            TacticalButton(
                icon = if (inSquad) Icons.Default.Group else Icons.Default.Smartphone,
                label = if (inSquad) "Members" else "Devices",
                isActive = false,
                accent = false,
                activeBrush = currentTheme.gradient,
                indicatorColor = currentTheme.primaryColor,
                onClick = onPairedDevices,
                modifier = Modifier.weight(1f)
            )
        }

        // Bottom Row: 3 balanced tactical buttons (Speaker, Radar, Replay Reel)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TacticalButton(
                icon = if (speakerOn) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                label = if (speakerOn) "Speaker" else "Muted",
                isActive = inSquad && speakerOn,
                accent = true,
                activeBrush = currentTheme.gradient,
                indicatorColor = currentTheme.primaryColor,
                onClick = onSpeaker,
                modifier = Modifier.weight(1f)
            )
            TacticalButton(
                icon = Icons.Default.Explore,
                label = "Radar",
                isActive = inSquad && isGpsActive,
                accent = false,
                activeBrush = currentTheme.gradient,
                indicatorColor = if (isGpsActive) StatusReady else currentTheme.primaryColor,
                badgeCount = if (inSquad) radarTargetCount else 0,
                onClick = { onRadarClick?.invoke() },
                modifier = Modifier.weight(1f)
            )
            TacticalButton(
                icon = Icons.Default.Replay,
                label = "Replay",
                isActive = inSquad && replayCount > 0,
                accent = false,
                activeBrush = currentTheme.gradient,
                indicatorColor = if (replayCount > 0) StatusReady else currentTheme.primaryColor,
                badgeCount = if (inSquad) replayCount else 0,
                onClick = onQuickActions,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun TacticalButton(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    accent: Boolean,
    activeBrush: Brush,
    indicatorColor: Color,
    onClick: () -> Unit,
    badgeCount: Int = 0,
    badgeText: String = "",
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val haptic = LocalHapticFeedback.current
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = 0.52f, stiffness = 550f),
        label = "tacticalBtnScale"
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .height(50.dp)
            .clip(RoundedCornerShape(14.dp))
            .then(
                if (isActive && accent) {
                    Modifier.background(activeBrush)
                } else {
                    Modifier.background(WalkieButton)
                }
            )
            .border(
                1.dp,
                if (isActive && accent) Color.White.copy(alpha = 0.3f) else WalkieCardBorder,
                RoundedCornerShape(14.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        // Top LED indicator pip if active
        if (isActive) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = 2.dp)
                    .width(12.dp)
                    .height(2.5.dp)
                    .clip(CircleShape)
                    .background(indicatorColor)
            )
        }

        // Tactical Counter or Text Badge
        if (badgeCount > 0 || badgeText.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-4).dp, y = 4.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (badgeText.isNotEmpty()) WalkieAmber.copy(alpha = 0.85f) else Color(0xFFEF4444))
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            ) {
                Text(
                    text = if (badgeText.isNotEmpty()) badgeText else if (badgeCount > 99) "99+" else "$badgeCount",
                    color = Color.White,
                    fontSize = 7.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.sp
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isActive && accent) Color.White else WalkieTextSecondary,
                modifier = Modifier.size(17.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                fontSize = 9.5.sp,
                fontWeight = if (isActive && accent) FontWeight.ExtraBold else FontWeight.Medium,
                color = if (isActive && accent) Color.White else WalkieTextMuted,
                letterSpacing = 0.5.sp
            )
        }
    }
}
