package com.example.walkietalkieapp.ui.walkie

import androidx.compose.animation.*
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
import kotlin.math.abs

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
                                    imageVector = Icons.Default.WifiOff,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = if (online) "No squads connected" else "No saved squads offline",
                                    fontFamily = SpaceGrotesk,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = if (online) "Create or join a squad below" else "Start one on the spot",
                                    fontFamily = PlusJakartaSans,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
            }

            // Squad List or Offline Direct Action Pills
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                if (isOnline) {
                    if (squads.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(vertical = 14.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.GroupAdd,
                                contentDescription = null,
                                tint = WalkieAmber,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "No Squads Found",
                                fontFamily = SpaceGrotesk,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = WalkieTextPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Create or join a squad below to connect",
                                fontFamily = PlusJakartaSans,
                                fontSize = 11.sp,
                                color = WalkieTextMuted
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                        squads.forEachIndexed { index, squad ->
                            val isSelected = index == activeIndex
                            val offset = index - activeIndex
                            val itemAlpha = if (isSelected) 1f else (0.8f - (abs(offset) * 0.15f)).coerceAtLeast(0.35f)

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (isSelected) WalkieButton.copy(alpha = 0.8f) else Color.Transparent)
                                    .border(
                                        1.dp,
                                        if (isSelected) WalkieAmber.copy(alpha = 0.35f) else Color.Transparent,
                                        RoundedCornerShape(14.dp)
                                    )
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onStep(index - activeIndex)
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
                                        imageVector = Icons.Default.Group,
                                        contentDescription = null,
                                        tint = if (isSelected) WalkieAmber else WalkieTextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = squad.name,
                                        fontFamily = SpaceGrotesk,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) WalkieTextPrimary else WalkieTextSecondary.copy(alpha = itemAlpha)
                                    )
                                    val subText = if (squad.members.isNotEmpty()) {
                                        "${squad.members.size} members · ${squad.members.count { it.online }} online"
                                    } else {
                                        squad.lastActive
                                    }
                                    Text(
                                        text = subText,
                                        fontFamily = PlusJakartaSans,
                                        fontSize = 11.sp,
                                        color = WalkieTextMuted.copy(alpha = itemAlpha)
                                    )
                                }

                                if (squad.members.isNotEmpty()) {
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
            } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(vertical = 14.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (mode == ConnectivityMode.BLUETOOTH) "BLUETOOTH · LOCAL BEACON" else "WI-FI DIRECT · DIRECT P2P",
                            fontFamily = SpaceGrotesk,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = WalkieTextMuted,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        ActionPill(
                            icon = Icons.Default.Add,
                            label = "Create Squad",
                            sub = "New local channel",
                            isPrimary = true,
                            gradient = currentTheme.gradient,
                            onClick = onCreate
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        ActionPill(
                            icon = Icons.AutoMirrored.Filled.Login,
                            label = "Join Squad",
                            sub = "Scan nearby devices",
                            isPrimary = false,
                            gradient = currentTheme.gradient,
                            onClick = onJoinByCode
                        )
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
                        text = if (isOnline) "Pull the wheel to join" else "Offline mode active",
                        fontFamily = PlusJakartaSans,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = WalkieTextSecondary
                    )
                }
                Text(
                    text = if (isOnline) "${activeIndex + 1}/${squads.size}" else "—",
                    fontFamily = SpaceGrotesk,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = WalkieTextMuted,
                    letterSpacing = 1.sp
                )
            }
        }

        // SideScroller positioned on right edge
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

