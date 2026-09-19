package com.example.walkietalkieapp.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.walkietalkieapp.bluetooth.BluetoothSquadUiState
import com.example.walkietalkieapp.bluetooth.DiscoveredSquad
import com.example.walkietalkieapp.wifidirect.DiscoveredWifiSquad
import com.example.walkietalkieapp.wifidirect.WifiSquadUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineDiscoveryScreen(
    callSign: String,
    onEditCallSign: () -> Unit,
    selectedMode: TransportMode,
    onSelectMode: (TransportMode) -> Unit,
    btUiState: BluetoothSquadUiState,
    wifiUiState: WifiSquadUiState,
    hasPermissions: Boolean,
    onRequestPermissions: () -> Unit,
    onHostBtSquad: (String, String) -> Unit,
    onJoinBtSquad: (DiscoveredSquad, String) -> Unit,
    onStartBtScan: () -> Unit,
    onHostWifiSquad: (String, String) -> Unit,
    onJoinWifiSquad: (DiscoveredWifiSquad, String) -> Unit,
    onStartWifiScan: () -> Unit
) {
    var squadName by remember { mutableStateOf("") }
    var selectedTab by remember { mutableIntStateOf(0) }

    val modeAccent = if (selectedMode == TransportMode.BLUETOOTH) TactileColors.statusConnecting else TactileColors.statusActive

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Mode Header & Technical Metadata
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .background(TactileColors.surfaceContainerLow, CircleShape)
                    .border(1.dp, modeAccent.copy(alpha = 0.3f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (selectedMode == TransportMode.BLUETOOTH) Icons.Default.Bluetooth else Icons.Default.Wifi,
                    contentDescription = null,
                    tint = modeAccent,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = if (selectedMode == TransportMode.BLUETOOTH) "BLUETOOTH TACTICAL MESH" else "WI-FI DIRECT P2P GROUP",
                    fontFamily = SpaceGrotesk,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = TactileColors.onSurface,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = if (selectedMode == TransportMode.BLUETOOTH) "Off-grid local mesh • Low power consumption"
                    else "Off-grid high-speed • Direct device link (100m+)",
                    fontFamily = Manrope,
                    color = TactileColors.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }
        }

        // Active Operator Call Sign Bar
        Surface(
            onClick = onEditCallSign,
            color = TactileColors.surfaceContainerLow,
            shape = TactileShapes.tile,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, modeAccent.copy(alpha = 0.25f), TactileShapes.tile)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(TactileColors.surfaceContainerHighest, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Badge,
                            contentDescription = null,
                            tint = modeAccent,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "ACTIVE OPERATOR CALL SIGN",
                            fontFamily = SpaceGrotesk,
                            color = TactileColors.onSecondaryContainer,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )
                        Text(
                            text = callSign.ifBlank { "Tap to set Call Sign" },
                            fontFamily = SpaceGrotesk,
                            color = TactileColors.onSurface,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit Call Sign",
                    tint = TactileColors.onSecondaryContainer,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Tab Selector (JOIN SQUAD vs HOST SQUAD)
        Surface(
            color = TactileColors.surfaceContainerLowest,
            shape = TactileShapes.tile,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, TactileColors.ghostBorder, TactileShapes.tile)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Join Tab
                val joinBg by animateColorAsState(
                    targetValue = if (selectedTab == 0) TactileColors.surfaceContainerHighest else Color.Transparent,
                    animationSpec = tween(200),
                    label = "joinTabBg"
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(TactileShapes.tile)
                        .background(joinBg)
                        .border(
                            1.dp,
                            if (selectedTab == 0) modeAccent.copy(alpha = 0.4f) else Color.Transparent,
                            TactileShapes.tile
                        )
                        .clickable { selectedTab = 0 }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "JOIN SQUAD",
                        fontFamily = SpaceGrotesk,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = if (selectedTab == 0) modeAccent else TactileColors.onSecondaryContainer,
                        letterSpacing = 0.5.sp
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Host Tab
                val hostBg by animateColorAsState(
                    targetValue = if (selectedTab == 1) TactileColors.surfaceContainerHighest else Color.Transparent,
                    animationSpec = tween(200),
                    label = "hostTabBg"
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(TactileShapes.tile)
                        .background(hostBg)
                        .border(
                            1.dp,
                            if (selectedTab == 1) modeAccent.copy(alpha = 0.4f) else Color.Transparent,
                            TactileShapes.tile
                        )
                        .clickable { selectedTab = 1 }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "HOST SQUAD",
                        fontFamily = SpaceGrotesk,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = if (selectedTab == 1) modeAccent else TactileColors.onSecondaryContainer,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (selectedTab == 0) {
            // JOIN TAB (DISCOVERED SIGNALS LIST)
            val isScanning = if (selectedMode == TransportMode.BLUETOOTH) btUiState.isScanning else wifiUiState.isScanning

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (selectedMode == TransportMode.BLUETOOTH) "NEARBY BLUETOOTH SIGNALS" else "NEARBY WI-FI DIRECT SQUADS",
                    fontFamily = SpaceGrotesk,
                    color = TactileColors.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    letterSpacing = 0.5.sp
                )
                Surface(
                    onClick = {
                        if (!hasPermissions) onRequestPermissions()
                        else if (selectedMode == TransportMode.BLUETOOTH) onStartBtScan()
                        else onStartWifiScan()
                    },
                    shape = TactileShapes.pill,
                    color = modeAccent.copy(alpha = 0.15f),
                    modifier = Modifier.border(1.dp, modeAccent.copy(alpha = 0.35f), TactileShapes.pill)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isScanning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                color = modeAccent,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Scanning...",
                                color = modeAccent,
                                fontFamily = SpaceGrotesk,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                                tint = modeAccent
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "Scan On-Air",
                                color = modeAccent,
                                fontFamily = SpaceGrotesk,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                if (selectedMode == TransportMode.BLUETOOTH) {
                    // Bluetooth Discovered Squads
                    if (btUiState.discoveredSquads.isEmpty()) {
                        item {
                            EmptySignalsPlaceholder(
                                hint = "Ask the squad host to tap 'GO VISIBLE' in their squad room, then tap 'Scan On-Air'."
                            )
                        }
                    } else {
                        items(btUiState.discoveredSquads) { squad ->
                            SquadCardItem(
                                title = squad.squadName,
                                subtitle = "Host: ${squad.hostUsername} • Signal: ${squad.rssi} dBm",
                                accentColor = modeAccent,
                                canJoin = callSign.isNotBlank(),
                                onJoin = { onJoinBtSquad(squad, callSign) }
                            )
                        }
                    }
                } else {
                    // Wi-Fi Direct Discovered Squads
                    if (wifiUiState.discoveredSquads.isEmpty()) {
                        item {
                            EmptySignalsPlaceholder(
                                hint = "Make sure the host created a Wi-Fi Direct Squad room, then tap 'Scan On-Air'."
                            )
                        }
                    } else {
                        items(wifiUiState.discoveredSquads) { squad ->
                            SquadCardItem(
                                title = squad.squadName,
                                subtitle = "Host: ${squad.hostUsername} • Wi-Fi Direct P2P",
                                accentColor = modeAccent,
                                canJoin = callSign.isNotBlank(),
                                onJoin = { onJoinWifiSquad(squad, callSign) }
                            )
                        }
                    }
                }
            }
        } else {
            // HOST TAB
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(
                    color = TactileColors.surfaceContainerLow,
                    shape = TactileShapes.card,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, TactileColors.ghostBorder, TactileShapes.card)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(TactileColors.surfaceContainerHighest, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (selectedMode == TransportMode.BLUETOOTH) Icons.Default.Podcasts else Icons.Default.Wifi,
                                contentDescription = null,
                                tint = modeAccent,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = if (selectedMode == TransportMode.BLUETOOTH) "Host Bluetooth Mesh Squad" else "Host Wi-Fi Direct Squad",
                            fontFamily = SpaceGrotesk,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = TactileColors.onSurface
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedTextField(
                            value = squadName,
                            onValueChange = { squadName = it },
                            label = { Text("Squad Name (e.g. ALPHA-1)", fontFamily = Manrope) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TactileColors.onSurface,
                                unfocusedTextColor = TactileColors.onSurface,
                                focusedBorderColor = modeAccent,
                                unfocusedBorderColor = TactileColors.outlineVariant,
                                focusedLabelColor = modeAccent,
                                unfocusedLabelColor = TactileColors.onSecondaryContainer,
                                focusedContainerColor = TactileColors.surfaceContainerLowest,
                                unfocusedContainerColor = TactileColors.surfaceContainerLowest
                            ),
                            shape = TactileShapes.tile,
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Button(
                            onClick = {
                                if (callSign.isNotBlank()) {
                                    if (selectedMode == TransportMode.BLUETOOTH) {
                                        onHostBtSquad(callSign, squadName.ifBlank { "TACTICAL-1" })
                                    } else {
                                        onHostWifiSquad(callSign, squadName.ifBlank { "DIRECT-1" })
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = TactileShapes.pill,
                            colors = ButtonDefaults.buttonColors(containerColor = modeAccent),
                            enabled = callSign.isNotBlank()
                        ) {
                            Icon(
                                if (selectedMode == TransportMode.BLUETOOTH) Icons.Default.Podcasts else Icons.Default.Wifi,
                                contentDescription = null,
                                tint = Color(0xFF131315),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (selectedMode == TransportMode.BLUETOOTH) "CREATE BT SQUAD ROOM" else "CREATE WI-FI SQUAD ROOM",
                                color = Color(0xFF131315),
                                fontFamily = SpaceGrotesk,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = if (selectedMode == TransportMode.BLUETOOTH)
                        "Bluetooth Mesh: Zero internet, minimal battery draw. Tap 'GO VISIBLE' inside the room anytime nearby comrades want to discover and join."
                    else
                        "Wi-Fi Direct: Ultra-low latency voice link up to 100m+ range. Automatically builds a local direct peer connection.",
                    fontFamily = Manrope,
                    color = TactileColors.onSurfaceVariant,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
    }
}

@Composable
fun EmptySignalsPlaceholder(hint: String) {
    Surface(
        color = TactileColors.surfaceContainerLow,
        shape = TactileShapes.tile,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
            .border(1.dp, TactileColors.ghostBorder, TactileShapes.tile)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(TactileColors.surfaceContainerHighest, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Podcasts,
                    contentDescription = null,
                    tint = TactileColors.onSecondaryContainer,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "No on-air squad signals detected",
                fontFamily = SpaceGrotesk,
                color = TactileColors.onSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = hint,
                fontFamily = Manrope,
                color = TactileColors.onSurfaceVariant,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
fun SquadCardItem(
    title: String,
    subtitle: String,
    accentColor: Color,
    canJoin: Boolean,
    onJoin: () -> Unit
) {
    Surface(
        onClick = onJoin,
        color = TactileColors.surfaceContainerLow,
        shape = TactileShapes.tile,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, TactileColors.ghostBorder, TactileShapes.tile),
        enabled = canJoin
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(TactileColors.surfaceContainerHighest, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Podcasts,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = TactileColors.onSurface,
                    fontFamily = SpaceGrotesk,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Text(
                    text = subtitle,
                    fontFamily = Manrope,
                    color = accentColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Button(
                onClick = onJoin,
                shape = TactileShapes.pill,
                colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                enabled = canJoin,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "JOIN",
                    color = Color(0xFF131315),
                    fontFamily = SpaceGrotesk,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}
