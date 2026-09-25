package com.example.walkietalkieapp.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun BackgroundComposable(
    isActive: Boolean,
    gyroOffset: Offset = Offset.Zero
) {
    val infiniteTransition = rememberInfiniteTransition(label = "tactileBackground")

    val move1 by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(22000, easing = LinearEasing), RepeatMode.Reverse),
        label = "move1"
    )
    val move2 by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(28000, easing = LinearEasing), RepeatMode.Reverse),
        label = "move2"
    )

    // Intensity rises dynamically when transmitting or receiving
    val intensity by animateFloatAsState(
        targetValue = if (isActive) 0.55f else 0.22f,
        animationSpec = tween(700),
        label = "intensity"
    )

    val pulseScale by animateFloatAsState(
        targetValue = if (isActive) 1.22f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "pulseScale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TactileColors.surfaceContainerLowest)
    ) {
        // Blob 1: Amber Ignition Glow (Signature Vintage Radio warmth)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = (-0.35f + 0.6f * move1) * size.width + gyroOffset.x * 1.5f
                    translationY = (-0.25f + 0.5f * move2) * size.height + gyroOffset.y * 1.5f
                    scaleX = 2.0f * pulseScale
                    scaleY = 2.0f * pulseScale
                    alpha = intensity
                }
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            TactileColors.primaryContainer.copy(alpha = 0.85f),
                            TactileColors.primary.copy(alpha = 0.35f),
                            Color.Transparent
                        ),
                    )
                )
        )

        // Blob 2: Deep Technical Charcoal/Slate Bloom
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = (0.35f - 0.6f * move2) * size.width - gyroOffset.x * 1.5f
                    translationY = (0.25f + 0.5f * move1) * size.height - gyroOffset.y * 1.5f
                    scaleX = 2.2f * pulseScale
                    scaleY = 2.2f * pulseScale
                    alpha = intensity * 0.7f
                }
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF35343D).copy(alpha = 0.75f),
                            Color(0xFF1E1D22).copy(alpha = 0.3f),
                            Color.Transparent
                        ),
                    )
                )
        )

        // Subtle Machined Radio Mesh Grid Texture
        Canvas(modifier = Modifier.fillMaxSize()) {
            val step = 32.dp.toPx()
            val dotRadius = 1.dp.toPx()
            val gridColor = Color(0xFF564334).copy(alpha = 0.04f)

            var x = 0f
            while (x < size.width) {
                var y = 0f
                while (y < size.height) {
                    drawCircle(
                        color = gridColor,
                        radius = dotRadius,
                        center = Offset(x, y)
                    )
                    y += step
                }
                x += step
            }
        }
    }
}

@Composable
fun TimerView() {
    var seconds by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            seconds++
        }
    }

    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60

    val timeText = if (hours > 0) {
        String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, secs)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", minutes, secs)
    }

    Surface(
        color = TactileColors.surfaceContainerHigh,
        shape = TactileShapes.pill,
        modifier = Modifier
            .padding(top = 8.dp)
            .border(1.dp, TactileColors.ghostBorder, TactileShapes.pill)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .background(TactileColors.primaryContainer, CircleShape)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "LIVE $timeText",
                color = TactileColors.onSurface,
                fontSize = 12.sp,
                fontFamily = SpaceGrotesk,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.75.sp
            )
        }
    }
}

@Composable
fun WaveformSmall() {
    val infiniteTransition = rememberInfiniteTransition(label = "tactileWaveform")
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom
    ) {
        repeat(3) { index ->
            val height by infiniteTransition.animateFloat(
                initialValue = 0.2f,
                targetValue = 0.85f,
                animationSpec = infiniteRepeatable(
                    tween(300 + index * 120, easing = FastOutSlowInEasing),
                    RepeatMode.Reverse
                ),
                label = "bar"
            )
            Box(
                modifier = Modifier
                    .fillMaxHeight(height)
                    .width(3.dp)
                    .background(
                        TactileColors.primaryContainer.copy(alpha = 0.85f),
                        RoundedCornerShape(2.dp)
                    )
            )
        }
    }
}

@Composable
fun PulsingDot(color: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "dot")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f, targetValue = 1.35f,
        animationSpec = infiniteRepeatable(tween(650, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "scale"
    )
    Box(
        modifier = Modifier
            .size(8.dp)
            .scale(scale)
            .background(color, CircleShape)
    )
}

@Composable
fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    color: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = TactileColors.surfaceContainerLow,
        shape = TactileShapes.tile,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, TactileColors.ghostBorder, TactileShapes.tile)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(TactileColors.surfaceContainerHighest, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = title,
                    color = TactileColors.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontFamily = SpaceGrotesk,
                    fontSize = 15.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = TactileColors.onSurfaceVariant,
                    fontFamily = Manrope,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
fun NotificationBanner(
    message: String,
    isError: Boolean = false,
    onDismiss: (() -> Unit)? = null
) {
    Surface(
        color = TactileColors.surfaceContainerHigh,
        shape = TactileShapes.pill,
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .border(1.dp, TactileColors.ghostBorder, TactileShapes.pill)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        if (isError) TactileColors.error else TactileColors.primaryContainer,
                        CircleShape
                    )
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = message,
                color = TactileColors.onSurface,
                fontSize = 13.sp,
                fontFamily = Manrope,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (onDismiss != null) {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss",
                    tint = TactileColors.onSecondaryContainer,
                    modifier = Modifier
                        .size(16.dp)
                        .clickable { onDismiss() }
                )
            }
        }
    }
}

@Composable
fun TactileStatusChip(
    text: String,
    isActive: Boolean = true,
    icon: ImageVector? = null
) {
    Surface(
        color = TactileColors.secondaryContainer,
        shape = TactileShapes.pill,
        modifier = Modifier.border(1.dp, TactileColors.ghostBorder, TactileShapes.pill)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isActive) TactileColors.primary else TactileColors.onSecondaryContainer,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
            } else {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(
                            if (isActive) TactileColors.statusActive else TactileColors.onSecondaryContainer,
                            CircleShape
                        )
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = text,
                color = TactileColors.onSurface,
                fontSize = 11.sp,
                fontFamily = Manrope,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun BluetoothEnableDialog(
    onDismiss: () -> Unit,
    onLaunchEnable: () -> Unit
) {
    TactileSystemDialog(
        icon = Icons.Default.Bluetooth,
        iconTint = TactileColors.statusConnecting,
        title = "Turn On Bluetooth",
        message = "Bluetooth is required to discover and connect with nearby walkie-talkie radios.",
        confirmText = "TURN ON",
        onConfirm = onLaunchEnable,
        onDismiss = onDismiss
    )
}

@Composable
fun WifiEnableDialog(
    onDismiss: () -> Unit,
    onLaunchEnable: () -> Unit
) {
    TactileSystemDialog(
        icon = Icons.Default.Wifi,
        iconTint = TactileColors.statusActive,
        title = "Turn On Wi-Fi",
        message = "Wi-Fi is required for Wi-Fi Direct to create and discover high-range squad radios.",
        confirmText = "TURN ON",
        onConfirm = onLaunchEnable,
        onDismiss = onDismiss
    )
}

@Composable
fun LocationEnableDialog(
    onDismiss: () -> Unit,
    onLaunchSettings: () -> Unit
) {
    TactileSystemDialog(
        icon = Icons.Default.LocationOn,
        iconTint = TactileColors.primaryContainer,
        title = "Enable Location Services",
        message = "Android requires Location (GPS) to be turned ON to scan for nearby Bluetooth and Wi-Fi Direct devices.",
        confirmText = "SETTINGS",
        onConfirm = onLaunchSettings,
        onDismiss = onDismiss
    )
}

@Composable
fun WifiDisconnectWarningDialog(
    connectedSsid: String?,
    onDismiss: () -> Unit,
    onLaunchWifiSettings: () -> Unit
) {
    val ssidNotice = if (!connectedSsid.isNullOrBlank() && connectedSsid != "Wi-Fi Network") {
        " ('$connectedSsid')"
    } else {
        ""
    }
    TactileSystemDialog(
        icon = Icons.Default.WifiOff,
        iconTint = TactileColors.primaryContainer,
        title = "Disconnect Active Wi-Fi",
        message = "Your device is connected to an external Wi-Fi network$ssidNotice.\n\nWi-Fi Direct requires your Wi-Fi radio to be free from active network connections to create or join a squad.\n\nPlease disconnect from your current Wi-Fi network (keep Wi-Fi switched ON) before creating or joining a squad.",
        confirmText = "SETTINGS",
        onConfirm = onLaunchWifiSettings,
        onDismiss = onDismiss
    )
}

@Composable
fun CallSignDialog(
    currentCallSign: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var tempName by remember { mutableStateOf(currentCallSign) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = TactileColors.surfaceContainerLow,
            shape = TactileShapes.card,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, TactileColors.ghostBorder, TactileShapes.card)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Machined icon pod
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .background(TactileColors.surfaceContainerHighest, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Badge,
                        contentDescription = null,
                        tint = TactileColors.primary,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Set Your Call Sign",
                    fontFamily = SpaceGrotesk,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = TactileColors.onSurface
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "This name will be saved locally on your device and shown during offline Bluetooth and Wi-Fi Direct transmissions.",
                    fontFamily = Manrope,
                    fontSize = 13.sp,
                    color = TactileColors.onSurfaceVariant,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Carved-out input field
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TactileColors.surfaceContainerLowest, TactileShapes.tile)
                        .border(1.dp, TactileColors.ghostBorder, TactileShapes.tile)
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    if (tempName.isEmpty()) {
                        Text(
                            text = "Call Sign (e.g. ALPHA-1, GHOST)",
                            fontFamily = Manrope,
                            fontSize = 14.sp,
                            color = TactileColors.onSecondaryContainer
                        )
                    }
                    BasicTextField(
                        value = tempName,
                        onValueChange = { tempName = it },
                        singleLine = true,
                        textStyle = TextStyle(
                            fontFamily = SpaceGrotesk,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            color = TactileColors.onSurface
                        ),
                        cursorBrush = SolidColor(TactileColors.primaryContainer),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (currentCallSign.isNotBlank()) {
                        TextButton(
                            onClick = onDismiss,
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = TactileColors.onSecondaryContainer
                            )
                        ) {
                            Text(
                                text = "CANCEL",
                                fontFamily = Manrope,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    Box(
                        modifier = Modifier
                            .clip(TactileShapes.pill)
                            .background(
                                if (tempName.isNotBlank()) TactileColors.buttonGradient
                                else Brush.horizontalGradient(
                                    listOf(
                                        TactileColors.surfaceContainerHighest,
                                        TactileColors.surfaceContainerHighest
                                    )
                                )
                            )
                            .clickable(enabled = tempName.isNotBlank()) {
                                onSave(tempName.trim())
                            }
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "SAVE CALL SIGN",
                            color = if (tempName.isNotBlank()) Color(0xFF131315) else TactileColors.onSecondaryContainer,
                            fontFamily = SpaceGrotesk,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TactileSystemDialog(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = TactileColors.surfaceContainerLow,
            shape = TactileShapes.card,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, TactileColors.ghostBorder, TactileShapes.card)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .background(TactileColors.surfaceContainerHighest, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = title,
                    fontFamily = SpaceGrotesk,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = TactileColors.onSurface
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = message,
                    fontFamily = Manrope,
                    fontSize = 13.sp,
                    color = TactileColors.onSurfaceVariant,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = TactileColors.onSecondaryContainer
                        )
                    ) {
                        Text(
                            text = "CANCEL",
                            fontFamily = Manrope,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Box(
                        modifier = Modifier
                            .clip(TactileShapes.pill)
                            .background(TactileColors.buttonGradient)
                            .clickable { onConfirm() }
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = confirmText,
                            color = Color(0xFF131315),
                            fontFamily = SpaceGrotesk,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }
        }
    }
}
