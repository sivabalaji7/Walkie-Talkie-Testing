package com.example.walkietalkieapp.ui

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun BackgroundComposable(
    isActive: Boolean,
    gyroOffset: Offset = Offset.Zero
) {
    val infiniteTransition = rememberInfiniteTransition(label = "background")

    val move1 by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(20000, easing = LinearEasing), RepeatMode.Reverse),
        label = "move1"
    )
    val move2 by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(28000, easing = LinearEasing), RepeatMode.Reverse),
        label = "move2"
    )

    val intensity by animateFloatAsState(
        targetValue = if (isActive) 0.55f else 0.35f,
        animationSpec = tween(1000),
        label = "intensity"
    )

    val pulseScale by animateFloatAsState(
        targetValue = if (isActive) 1.25f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "pulseScale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF020203))
    ) {
        // Blob 1: Indigo
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = (-0.3f + 0.6f * move1) * size.width + gyroOffset.x
                    translationY = (-0.2f + 0.5f * move2) * size.height + gyroOffset.y
                    scaleX = 2.0f * pulseScale
                    scaleY = 2.0f * pulseScale
                    alpha = intensity
                }
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFF6366F1), Color.Transparent),
                    )
                )
        )

        // Blob 2: Cyan
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = (0.3f - 0.6f * move2) * size.width - gyroOffset.x
                    translationY = (0.2f + 0.5f * move1) * size.height - gyroOffset.y
                    scaleX = 2.2f * pulseScale
                    scaleY = 2.2f * pulseScale
                    alpha = intensity * 0.8f
                }
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFF06B6D4), Color.Transparent),
                    )
                )
        )
    }
}

@Composable
fun TimerView() {
    var seconds by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) { delay(1000); seconds++ }
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
        color = Color.White.copy(alpha = 0.05f),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.padding(top = 8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(6.dp).background(Color(0xFF00E5FF), CircleShape))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "LIVE: $timeText",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        }
    }
}

@Composable
fun WaveformSmall() {
    val infiniteTransition = rememberInfiniteTransition(label = "waveform")
    Row(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom
    ) {
        repeat(3) { index ->
            val height by infiniteTransition.animateFloat(
                initialValue = 0.2f,
                targetValue = 0.8f,
                animationSpec = infiniteRepeatable(
                    tween(300 + index * 100),
                    RepeatMode.Reverse
                ),
                label = "bar"
            )
            Box(
                modifier = Modifier
                    .fillMaxHeight(height)
                    .width(3.dp)
                    .background(Color(0xFF4CAF50).copy(alpha = 0.7f), RoundedCornerShape(2.dp))
            )
        }
    }
}

@Composable
fun PulsingDot(color: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "dot")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f, targetValue = 1.3f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "scale"
    )
    Box(modifier = Modifier.size(8.dp).scale(scale).background(color, CircleShape))
}

@OptIn(ExperimentalMaterial3Api::class)
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
        color = Color(0xFF2C2F33),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(40.dp).background(color.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(subtitle, color = Color.Gray, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun BluetoothEnableDialog(
    onDismiss: () -> Unit,
    onLaunchEnable: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Bluetooth, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(36.dp)) },
        title = { Text("Turn On Bluetooth", fontWeight = FontWeight.Bold, color = Color.White) },
        text = { Text("Bluetooth is required to discover and connect with nearby walkie-talkie radios.", color = Color.White.copy(alpha = 0.8f)) },
        confirmButton = {
            Button(
                onClick = onLaunchEnable,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF))
            ) {
                Text("TURN ON", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCEL", color = Color.Gray)
            }
        },
        containerColor = Color(0xFF1E2124),
        shape = RoundedCornerShape(20.dp)
    )
}

@Composable
fun WifiEnableDialog(
    onDismiss: () -> Unit,
    onLaunchEnable: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Wifi, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(36.dp)) },
        title = { Text("Turn On Wi-Fi", fontWeight = FontWeight.Bold, color = Color.White) },
        text = { Text("Wi-Fi is required for Wi-Fi Direct to create and discover high-range squad radios.", color = Color.White.copy(alpha = 0.8f)) },
        confirmButton = {
            Button(
                onClick = onLaunchEnable,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
            ) {
                Text("TURN ON", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCEL", color = Color.Gray)
            }
        },
        containerColor = Color(0xFF1E2124),
        shape = RoundedCornerShape(20.dp)
    )
}

@Composable
fun LocationEnableDialog(
    onDismiss: () -> Unit,
    onLaunchSettings: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color(0xFFFFA000), modifier = Modifier.size(36.dp)) },
        title = { Text("Enable Location Services", fontWeight = FontWeight.Bold, color = Color.White) },
        text = { Text("Android requires Location (GPS) to be turned ON to scan for nearby Bluetooth and Wi-Fi Direct devices.", color = Color.White.copy(alpha = 0.8f)) },
        confirmButton = {
            Button(
                onClick = onLaunchSettings,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFA000))
            ) {
                Text("SETTINGS", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCEL", color = Color.Gray)
            }
        },
        containerColor = Color(0xFF1E2124),
        shape = RoundedCornerShape(20.dp)
    )
}

@Composable
fun CallSignDialog(
    currentCallSign: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var tempName by remember { mutableStateOf(currentCallSign) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Badge, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(36.dp)) },
        title = { Text("Set Your Call Sign", fontWeight = FontWeight.Bold, color = Color.White) },
        text = {
            Column {
                Text(
                    "This name will be saved locally on your device and shown during offline Bluetooth and Wi-Fi Direct transmissions.",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = tempName,
                    onValueChange = { tempName = it },
                    label = { Text("Call Sign (e.g. ALPHA-1, GHOST)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (tempName.isNotBlank()) {
                        onSave(tempName)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
                enabled = tempName.isNotBlank()
            ) {
                Text("SAVE CALL SIGN", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            if (currentCallSign.isNotBlank()) {
                TextButton(onClick = onDismiss) {
                    Text("CANCEL", color = Color.Gray)
                }
            }
        },
        containerColor = Color(0xFF1E2124),
        shape = RoundedCornerShape(20.dp)
    )
}
