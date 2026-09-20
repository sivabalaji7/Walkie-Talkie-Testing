package com.example.walkietalkieapp.ui.walkie

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.walkietalkieapp.ptt.HardwarePttManager
import com.example.walkietalkieapp.ui.theme.*

/**
 * Tactical Hardware PTT Controller Modal Dialog providing physical volume button triggers,
 * headset/media button integration, live hardware key diagnostics, and background lock screen control.
 */
@Composable
fun HardwarePttDialog(
    mode: ConnectivityMode,
    onDismiss: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val currentTheme = ModeThemes.get(mode)

    val isVolKeyEnabled by HardwarePttManager.isVolumeKeyPttEnabled.collectAsState()
    val isHeadsetEnabled by HardwarePttManager.isHeadsetPttEnabled.collectAsState()
    val isKeyDown by HardwarePttManager.isHardwareKeyDown.collectAsState()
    val triggerSource by HardwarePttManager.activeTriggerSource.collectAsState()

    // Breathing radar pulse for hardware key diagnostic indicator
    val pulseTransition = rememberInfiniteTransition(label = "hwDiagPulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(650, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "diagAlpha"
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(26.dp),
            color = WalkieCard,
            border = BorderStroke(1.2.dp, WalkieCardBorder),
            shadowElevation = 28.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ==================== HEADER ====================
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
                                .size(34.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(currentTheme.primaryColor.copy(alpha = 0.18f))
                                .border(1.2.dp, currentTheme.primaryColor.copy(alpha = 0.5f), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Hardware PTT",
                                tint = currentTheme.primaryColor,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "HARDWARE PTT CONTROLLER",
                                fontFamily = SpaceGrotesk,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = WalkieTextPrimary,
                                letterSpacing = 0.8.sp
                            )
                            Text(
                                text = "PHYSICAL KEYS • HEADSET • QUICK PTT",
                                fontFamily = SpaceGrotesk,
                                fontSize = 9.sp,
                                color = WalkieTextSecondary,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }

                    // Close Button
                    Surface(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onDismiss()
                        },
                        shape = CircleShape,
                        color = WalkieButton,
                        border = BorderStroke(1.dp, WalkieCardBorder),
                        modifier = Modifier.size(30.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = WalkieTextSecondary,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }

                // ==================== LIVE HARDWARE KEY DIAGNOSTIC CARD ====================
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = if (isKeyDown) StatusReady.copy(alpha = 0.16f) else WalkieDeviceBody,
                    border = BorderStroke(
                        1.2.dp,
                        if (isKeyDown) StatusReady.copy(alpha = pulseAlpha) else WalkieCardBorder
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(if (isKeyDown) StatusReady else WalkieButton)
                                .border(1.dp, if (isKeyDown) StatusReady else WalkieCardBorder, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.RadioButtonChecked,
                                contentDescription = null,
                                tint = if (isKeyDown) Color.Black else WalkieTextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isKeyDown) "🟢 SIGNAL ACTIVE: ${triggerSource ?: "HARDWARE KEY"}" else "TEST HARDWARE BUTTON",
                                fontFamily = SpaceGrotesk,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isKeyDown) StatusReady else WalkieTextPrimary,
                                letterSpacing = 0.6.sp
                            )
                            Text(
                                text = if (isKeyDown) "Transmitting voice live via physical hardware trigger."
                                else "Press your phone's physical Volume Down button to test hardware response.",
                                fontFamily = SpaceGrotesk,
                                fontSize = 10.sp,
                                color = WalkieTextSecondary,
                                lineHeight = 13.sp
                            )
                        }
                    }
                }

                // ==================== TACTILE HARDWARE TOGGLES ====================
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HardwareToggleRow(
                        icon = Icons.AutoMirrored.Filled.VolumeDown,
                        title = "Volume Down Button PTT",
                        subtitle = "Hold physical volume down key to speak without touching the screen",
                        isChecked = isVolKeyEnabled,
                        primaryColor = currentTheme.primaryColor,
                        onCheckedChange = { checked ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            HardwarePttManager.setVolumeKeyPttEnabled(checked)
                        }
                    )

                    HardwareToggleRow(
                        icon = Icons.Default.Headset,
                        title = "Headset & Bluetooth Media Key",
                        subtitle = "Press inline mic or Bluetooth PTT button to key transmitter",
                        isChecked = isHeadsetEnabled,
                        primaryColor = currentTheme.primaryColor,
                        onCheckedChange = { checked ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            HardwarePttManager.setHeadsetPttEnabled(checked)
                        }
                    )

                    HardwareToggleRow(
                        icon = Icons.Default.NotificationsActive,
                        title = "Lock Screen & Notification Shade",
                        subtitle = "Active quick-talk button always available in system tray",
                        isChecked = true,
                        primaryColor = currentTheme.primaryColor,
                        enabled = false,
                        onCheckedChange = {}
                    )
                }

                // ==================== DISMISS BUTTON ====================
                Surface(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onDismiss()
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = WalkieButton,
                    border = BorderStroke(1.dp, WalkieCardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "SAVE & CLOSE",
                            fontFamily = SpaceGrotesk,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = WalkieTextPrimary,
                            letterSpacing = 0.8.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HardwareToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isChecked: Boolean,
    primaryColor: Color,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = WalkieButton.copy(alpha = 0.85f),
        border = BorderStroke(1.dp, if (isChecked) primaryColor.copy(alpha = 0.4f) else WalkieCardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(WalkieDeviceBody)
                        .border(1.dp, WalkieCardBorder, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isChecked) primaryColor else WalkieTextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Column {
                    Text(
                        text = title,
                        fontFamily = SpaceGrotesk,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = WalkieTextPrimary
                    )
                    Text(
                        text = subtitle,
                        fontFamily = SpaceGrotesk,
                        fontSize = 9.sp,
                        color = WalkieTextSecondary,
                        lineHeight = 12.sp
                    )
                }
            }

            Switch(
                checked = isChecked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.Black,
                    checkedTrackColor = primaryColor,
                    uncheckedThumbColor = WalkieTextMuted,
                    uncheckedTrackColor = WalkieDeviceBody
                )
            )
        }
    }
}
