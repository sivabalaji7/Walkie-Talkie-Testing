package com.example.walkietalkieapp.ui.walkie

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.walkietalkieapp.chat.TacticalChatManager
import com.example.walkietalkieapp.chat.TacticalMessage
import com.example.walkietalkieapp.chat.TacticalQuickStatus
import com.example.walkietalkieapp.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Tactical Data Link Modal Dialog providing silent encrypted micro-chat,
 * 1-tap quick action chips, and GPS coordinate beacon telemetry with external map deep-linking.
 */
@Composable
fun TacticalChatDialog(
    messages: List<TacticalMessage>,
    senderName: String,
    mode: ConnectivityMode,
    onSendMessage: (String) -> Unit,
    onSendGpsBeacon: () -> Unit,
    onDismiss: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val currentTheme = ModeThemes.get(mode)
    val listState = rememberLazyListState()

    var inputText by remember { mutableStateOf("") }
    var isDroppingBeacon by remember { mutableStateOf(false) }

    // Auto-scroll to latest message whenever message list updates
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.82f),
            shape = RoundedCornerShape(26.dp),
            color = WalkieCard,
            border = BorderStroke(1.2.dp, WalkieCardBorder),
            shadowElevation = 28.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // ==================== TOP HEADER ====================
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                                    imageVector = Icons.Default.Forum,
                                    contentDescription = "Tactical Data Link",
                                    tint = currentTheme.primaryColor,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "TACTICAL DATA LINK",
                                    fontFamily = SpaceGrotesk,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = WalkieTextPrimary,
                                    letterSpacing = 0.8.sp
                                )
                                Text(
                                    text = "ENCRYPTED MICRO-CHAT • GPS BEACON",
                                    fontFamily = SpaceGrotesk,
                                    fontSize = 9.sp,
                                    color = WalkieTextSecondary,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Active Count Chip
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (messages.isNotEmpty()) StatusReady.copy(alpha = 0.15f) else WalkieButton,
                                border = BorderStroke(1.dp, if (messages.isNotEmpty()) StatusReady.copy(alpha = 0.45f) else WalkieCardBorder)
                            ) {
                                Text(
                                    text = if (messages.isNotEmpty()) "${messages.size} LOGS" else "IDLE",
                                    fontFamily = SpaceGrotesk,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (messages.isNotEmpty()) StatusReady else WalkieTextMuted,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                )
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
                    }

                    // ==================== QUICK STATUS CHIPS ====================
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(TacticalQuickStatus.values()) { status ->
                            TacticalQuickChip(
                                label = status.chipLabel,
                                isBeacon = status == TacticalQuickStatus.DROP_BEACON,
                                primaryColor = currentTheme.primaryColor,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    if (status == TacticalQuickStatus.DROP_BEACON) {
                                        isDroppingBeacon = true
                                        onSendGpsBeacon()
                                        isDroppingBeacon = false
                                    } else {
                                        onSendMessage(status.transmissionText)
                                    }
                                }
                            )
                        }
                    }
                }

                // ==================== MESSAGES LIST ====================
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(vertical = 10.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(WalkieDeviceBodyLight.copy(alpha = 0.3f))
                        .border(1.dp, WalkieCardBorder.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    if (messages.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(WalkieButton)
                                    .border(1.dp, WalkieCardBorder, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Forum,
                                    contentDescription = null,
                                    tint = WalkieTextMuted,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "SILENT DATA LINK ACTIVE",
                                fontFamily = SpaceGrotesk,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = WalkieTextPrimary,
                                letterSpacing = 0.6.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Tap any Quick Status chip or drop GPS coordinates to broadcast silent telemetry to the squad.",
                                fontFamily = SpaceGrotesk,
                                fontSize = 10.sp,
                                color = WalkieTextSecondary,
                                textAlign = TextAlign.Center,
                                lineHeight = 14.sp
                            )
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(vertical = 4.dp)
                        ) {
                            items(messages, key = { it.id }) { msg ->
                                if (msg.isBeacon) {
                                    GpsBeaconCard(
                                        message = msg,
                                        primaryColor = currentTheme.primaryColor,
                                        onOpenMaps = {
                                            if (msg.latitude != null && msg.longitude != null) {
                                                val uri = Uri.parse("geo:${msg.latitude},${msg.longitude}?q=${msg.latitude},${msg.longitude}(${Uri.encode(msg.locationLabel ?: "Squad Beacon")})")
                                                val intent = Intent(Intent.ACTION_VIEW, uri)
                                                try {
                                                    context.startActivity(intent)
                                                } catch (e: Exception) {
                                                    val webUri = Uri.parse("https://www.google.com/maps/search/?api=1&query=${msg.latitude},${msg.longitude}")
                                                    context.startActivity(Intent(Intent.ACTION_VIEW, webUri))
                                                }
                                            }
                                        }
                                    )
                                } else {
                                    TacticalTextMessageBubble(
                                        message = msg,
                                        primaryColor = currentTheme.primaryColor
                                    )
                                }
                            }
                        }
                    }
                }

                // ==================== BOTTOM INPUT & GPS BAR ====================
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(WalkieButton)
                        .border(1.dp, WalkieCardBorder, RoundedCornerShape(16.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // GPS Beacon Quick Drop Button
                    val gpsInteractionSource = remember { MutableInteractionSource() }
                    val gpsPressed by gpsInteractionSource.collectIsPressedAsState()
                    val gpsScale by animateFloatAsState(
                        targetValue = if (gpsPressed) 0.92f else 1f,
                        animationSpec = spring(dampingRatio = 0.52f, stiffness = 550f),
                        label = "gpsScale"
                    )

                    Surface(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onSendGpsBeacon()
                            Toast.makeText(context, "Acquiring GPS fix & broadcasting beacon...", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = WalkieAmber.copy(alpha = 0.18f),
                        border = BorderStroke(1.dp, WalkieAmber.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .graphicsLayer {
                                scaleX = gpsScale
                                scaleY = gpsScale
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MyLocation,
                                contentDescription = "Drop Beacon",
                                tint = WalkieAmber,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "GPS",
                                fontFamily = SpaceGrotesk,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = WalkieAmber
                            )
                        }
                    }

                    // Text Input Field
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (inputText.isEmpty()) {
                            Text(
                                text = "Transmit silent message...",
                                fontFamily = SpaceGrotesk,
                                fontSize = 12.sp,
                                color = WalkieTextMuted
                            )
                        }
                        BasicTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            textStyle = TextStyle(
                                fontFamily = SpaceGrotesk,
                                fontSize = 12.sp,
                                color = WalkieTextPrimary,
                                fontWeight = FontWeight.Normal
                            ),
                            cursorBrush = SolidColor(currentTheme.primaryColor),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(
                                onSend = {
                                    if (inputText.isNotBlank()) {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onSendMessage(inputText.trim())
                                        inputText = ""
                                    }
                                }
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Send Button
                    val sendInteractionSource = remember { MutableInteractionSource() }
                    val sendPressed by sendInteractionSource.collectIsPressedAsState()
                    val sendScale by animateFloatAsState(
                        targetValue = if (sendPressed) 0.90f else 1f,
                        animationSpec = spring(dampingRatio = 0.52f, stiffness = 550f),
                        label = "sendScale"
                    )

                    val canSend = inputText.isNotBlank()
                    Surface(
                        onClick = {
                            if (canSend) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onSendMessage(inputText.trim())
                                inputText = ""
                            }
                        },
                        shape = CircleShape,
                        color = if (canSend) currentTheme.primaryColor else WalkieDeviceBodyLight,
                        border = BorderStroke(1.dp, if (canSend) currentTheme.primaryColor else WalkieCardBorder),
                        modifier = Modifier
                            .size(34.dp)
                            .graphicsLayer {
                                scaleX = sendScale
                                scaleY = sendScale
                            }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send",
                                tint = if (canSend) Color.Black else WalkieTextMuted,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 1-Tap Quick Action Chip for tactical radio silence communication.
 */
@Composable
private fun TacticalQuickChip(
    label: String,
    isBeacon: Boolean,
    primaryColor: Color,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = 0.52f, stiffness = 550f),
        label = "chipScale"
    )

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (isBeacon) WalkieAmber.copy(alpha = 0.14f) else WalkieButton,
        border = BorderStroke(1.dp, if (isBeacon) WalkieAmber.copy(alpha = 0.45f) else WalkieCardBorder),
        modifier = Modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
    ) {
        Text(
            text = label,
            fontFamily = SpaceGrotesk,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = if (isBeacon) WalkieAmber else WalkieTextPrimary,
            letterSpacing = 0.4.sp,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
        )
    }
}

/**
 * High-tech GPS Beacon telemetry card with coordinate readout and map opening intent.
 */
@Composable
private fun GpsBeaconCard(
    message: TacticalMessage,
    primaryColor: Color,
    onOpenMaps: () -> Unit
) {
    val timeStr = remember(message.timestamp) {
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        sdf.format(Date(message.timestamp))
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = WalkieAmber.copy(alpha = 0.10f),
        border = BorderStroke(1.2.dp, WalkieAmber.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = "Beacon",
                        tint = WalkieAmber,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = if (message.isSelf) "MY GPS BEACON" else "${message.senderName.uppercase()} BEACON",
                        fontFamily = SpaceGrotesk,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = WalkieAmber,
                        letterSpacing = 0.6.sp
                    )
                }
                Text(
                    text = timeStr,
                    fontFamily = SpaceGrotesk,
                    fontSize = 9.sp,
                    color = WalkieTextMuted
                )
            }

            // Coordinates Row
            if (message.latitude != null && message.longitude != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(WalkieDeviceBody)
                        .border(0.8.dp, WalkieCardBorder, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = String.format(Locale.US, "LAT: %.5f°   LON: %.5f°", message.latitude, message.longitude),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = WalkieTextPrimary
                    )

                    Surface(
                        onClick = onOpenMaps,
                        shape = RoundedCornerShape(6.dp),
                        color = WalkieAmber.copy(alpha = 0.22f),
                        border = BorderStroke(1.dp, WalkieAmber)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Map,
                                contentDescription = "Maps",
                                tint = WalkieAmber,
                                modifier = Modifier.size(11.dp)
                            )
                            Text(
                                text = "MAPS",
                                fontFamily = SpaceGrotesk,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = WalkieAmber
                            )
                        }
                    }
                }
            } else {
                Text(
                    text = message.content,
                    fontFamily = SpaceGrotesk,
                    fontSize = 11.sp,
                    color = WalkieTextPrimary
                )
            }
        }
    }
}

/**
 * Standard Tactical Micro-Chat Message Bubble with callsign, timestamp, and self/peer styling.
 */
@Composable
private fun TacticalTextMessageBubble(
    message: TacticalMessage,
    primaryColor: Color
) {
    val timeStr = remember(message.timestamp) {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        sdf.format(Date(message.timestamp))
    }

    val isSelf = message.isSelf

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isSelf) Alignment.End else Alignment.Start
    ) {
        // Callsign and Timestamp Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
        ) {
            Text(
                text = if (isSelf) "YOU" else message.senderName.uppercase(),
                fontFamily = SpaceGrotesk,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSelf) primaryColor else WalkieTextSecondary,
                letterSpacing = 0.4.sp
            )
            Text(
                text = "•",
                fontSize = 8.sp,
                color = WalkieTextMuted
            )
            Text(
                text = timeStr,
                fontFamily = SpaceGrotesk,
                fontSize = 8.sp,
                color = WalkieTextMuted
            )
        }

        // Message Content Body
        Surface(
            shape = RoundedCornerShape(
                topStart = 12.dp,
                topEnd = 12.dp,
                bottomStart = if (isSelf) 12.dp else 2.dp,
                bottomEnd = if (isSelf) 2.dp else 12.dp
            ),
            color = if (isSelf) primaryColor.copy(alpha = 0.16f) else WalkieButton,
            border = BorderStroke(1.dp, if (isSelf) primaryColor.copy(alpha = 0.45f) else WalkieCardBorder)
        ) {
            Text(
                text = message.content,
                fontFamily = SpaceGrotesk,
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
                color = WalkieTextPrimary,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
            )
        }
    }
}
