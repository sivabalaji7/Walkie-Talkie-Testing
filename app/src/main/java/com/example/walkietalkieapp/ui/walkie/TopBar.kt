package com.example.walkietalkieapp.ui.walkie

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.SignalCellularAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.walkietalkieapp.ui.theme.*

@Composable
fun TopBar(
    onMenuOpen: () -> Unit,
    onProfileClick: () -> Unit,
    isPowered: Boolean = true,
    userAvatar: String = "G",
    username: String = "Guest",
    isLoggedIn: Boolean = false,
    modifier: Modifier = Modifier
) {
    val menuInteractionSource = remember { MutableInteractionSource() }
    val menuPressed by menuInteractionSource.collectIsPressedAsState()

    val avatarInteractionSource = remember { MutableInteractionSource() }
    val avatarPressed by avatarInteractionSource.collectIsPressedAsState()

    val menuScale by animateFloatAsState(
        targetValue = if (menuPressed) 0.90f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "menuScale"
    )

    val avatarScale by animateFloatAsState(
        targetValue = if (avatarPressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "avatarScale"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        // Menu Button
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .graphicsLayer {
                    scaleX = menuScale
                    scaleY = menuScale
                }
                .size(40.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(WalkieCard)
                .border(1.dp, WalkieCardBorder, RoundedCornerShape(16.dp))
                .clickable(
                    interactionSource = menuInteractionSource,
                    indication = null,
                    onClick = onMenuOpen
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Menu,
                contentDescription = "Open navigation menu",
                tint = WalkieTextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }

        // Title
        Text(
            text = "WalkieX",
            style = Typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp),
            color = Color.Black,
            modifier = Modifier.align(Alignment.Center)
        )

        // Status Indicators & User Avatar
        Row(
            modifier = Modifier.align(Alignment.CenterEnd),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Signal & Battery Chip
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(WalkieCard)
                    .border(1.dp, WalkieCardBorder, RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.SignalCellularAlt,
                    contentDescription = "Signal",
                    tint = if (isPowered) StatusReady else WalkieTextMuted,
                    modifier = Modifier.size(14.dp)
                )
                Icon(
                    imageVector = Icons.Outlined.BatteryFull,
                    contentDescription = "Battery",
                    tint = WalkieTextSecondary.copy(alpha = 0.6f),
                    modifier = Modifier.size(14.dp)
                )
            }

            // Avatar Chip
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = avatarScale
                        scaleY = avatarScale
                    }
                    .size(34.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(WalkieAmber, WalkieAmberDark)
                        )
                    )
                    .border(1.dp, WalkieAmber.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                    .clickable(
                        interactionSource = avatarInteractionSource,
                        indication = null,
                        onClick = onProfileClick
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = userAvatar,
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }
    }
}
