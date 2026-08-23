package com.example.walkietalkieapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // App / Mode Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            Icon(
                if (selectedMode == TransportMode.BLUETOOTH) Icons.Default.Bluetooth else Icons.Default.Wifi,
                contentDescription = null,
                tint = if (selectedMode == TransportMode.BLUETOOTH) Color(0xFF00E5FF) else Color(0xFF4CAF50),
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = if (selectedMode == TransportMode.BLUETOOTH) "Bluetooth Tactical Mesh" else "Wi-Fi Direct P2P Group",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
                Text(
                    text = if (selectedMode == TransportMode.BLUETOOTH) "Off-grid short range • Low battery consumption" else "Off-grid high-speed • Up to 100m+ range",
                    color = Color.Gray,
                    fontSize = 11.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Call Sign Bar (Display & Edit)
        Surface(
            onClick = onEditCallSign,
            color = Color(0xFF1E2126),
            shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (selectedMode == TransportMode.BLUETOOTH) Color(0xFF00E5FF).copy(alpha = 0.3f) else Color(0xFF4CAF50).copy(alpha = 0.3f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Badge,
                        contentDescription = null,
                        tint = if (selectedMode == TransportMode.BLUETOOTH) Color(0xFF00E5FF) else Color(0xFF4CAF50),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text("ACTIVE OPERATOR CALL SIGN", color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Text(callSign.ifBlank { "Tap to set Call Sign" }, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                    }
                }
                Icon(Icons.Default.Edit, contentDescription = "Edit Call Sign", tint = Color.Gray, modifier = Modifier.size(18.dp))
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Tab Selector (Join vs Host)
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color(0xFF1E2126),
            contentColor = Color.White,
            indicator = {},
            divider = {},
            modifier = Modifier.clip(RoundedCornerShape(12.dp))
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = {
                    Text(
                        "JOIN SQUAD",
                        fontWeight = FontWeight.Bold,
                        color = if (selectedTab == 0) (if (selectedMode == TransportMode.BLUETOOTH) Color(0xFF00E5FF) else Color(0xFF4CAF50)) else Color.Gray
                    )
                }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = {
                    Text(
                        "HOST SQUAD",
                        fontWeight = FontWeight.Bold,
                        color = if (selectedTab == 1) (if (selectedMode == TransportMode.BLUETOOTH) Color(0xFF00E5FF) else Color(0xFF4CAF50)) else Color.Gray
                    )
                }
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (selectedTab == 0) {
            // JOIN TAB
            val isScanning = if (selectedMode == TransportMode.BLUETOOTH) btUiState.isScanning else wifiUiState.isScanning
            val accentColor = if (selectedMode == TransportMode.BLUETOOTH) Color(0xFF00E5FF) else Color(0xFF4CAF50)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (selectedMode == TransportMode.BLUETOOTH) "Nearby Bluetooth Signals" else "Nearby Wi-Fi Direct Squads",
                    color = Color.White.copy(alpha = 0.8f),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Button(
                    onClick = {
                        if (!hasPermissions) onRequestPermissions()
                        else if (selectedMode == TransportMode.BLUETOOTH) onStartBtScan()
                        else onStartWifiScan()
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor.copy(alpha = 0.15f)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    if (isScanning) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), color = accentColor, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Listening...", color = accentColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp), tint = accentColor)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Scan On-Air", color = accentColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (selectedMode == TransportMode.BLUETOOTH) {
                    // BLUETOOTH DISCOVERED SQUADS
                    if (btUiState.discoveredSquads.isEmpty()) {
                        item {
                            EmptySignalsPlaceholder(
                                hint = "Ask the host to tap 'GO VISIBLE (5s)' in their squad room, then tap 'Scan On-Air'."
                            )
                        }
                    } else {
                        items(btUiState.discoveredSquads) { squad ->
                            SquadCardItem(
                                title = squad.squadName,
                                subtitle = "Host: ${squad.hostUsername} • Signal: ${squad.rssi} dBm",
                                accentColor = Color(0xFF00E5FF),
                                canJoin = callSign.isNotBlank(),
                                onJoin = { onJoinBtSquad(squad, callSign) }
                            )
                        }
                    }
                } else {
                    // WI-FI DIRECT DISCOVERED SQUADS
                    if (wifiUiState.discoveredSquads.isEmpty()) {
                        item {
                            EmptySignalsPlaceholder(
                                hint = "Make sure Host created a Wi-Fi Direct Squad room, then tap 'Scan On-Air'."
                            )
                        }
                    } else {
                        items(wifiUiState.discoveredSquads) { squad ->
                            SquadCardItem(
                                title = squad.squadName,
                                subtitle = "Host: ${squad.hostUsername} • Wi-Fi Direct",
                                accentColor = Color(0xFF4CAF50),
                                canJoin = callSign.isNotBlank(),
                                onJoin = { onJoinWifiSquad(squad, callSign) }
                            )
                        }
                    }
                }
            }
        } else {
            // HOST TAB
            val accentColor = if (selectedMode == TransportMode.BLUETOOTH) Color(0xFF00E5FF) else Color(0xFF4CAF50)

            Column(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                OutlinedTextField(
                    value = squadName,
                    onValueChange = { squadName = it },
                    label = { Text("Squad Name (e.g. ALPHA-1)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = {
                        if (callSign.isNotBlank()) {
                            if (selectedMode == TransportMode.BLUETOOTH) {
                                onHostBtSquad(callSign, squadName)
                            } else {
                                onHostWifiSquad(callSign, squadName)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                    enabled = callSign.isNotBlank()
                ) {
                    Icon(
                        if (selectedMode == TransportMode.BLUETOOTH) Icons.Default.Podcasts else Icons.Default.Wifi,
                        contentDescription = null,
                        tint = Color.Black
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (selectedMode == TransportMode.BLUETOOTH) "CREATE BT SQUAD ROOM" else "CREATE WI-FI SQUAD ROOM",
                        color = Color.Black,
                        fontWeight = FontWeight.Black
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = if (selectedMode == TransportMode.BLUETOOTH)
                        "Bluetooth Mesh: Ultra-low battery, zero setup. Tap 'GO VISIBLE' inside the room anytime friends want to discover and join."
                    else
                        "Wi-Fi Direct: High-bandwidth, long-range (~100m+). Creates a local P2P group without needing any router or internet.",
                    color = Color.Gray,
                    fontSize = 12.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun EmptySignalsPlaceholder(hint: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.Podcasts, contentDescription = null, tint = Color.Gray.copy(alpha = 0.4f), modifier = Modifier.size(44.dp))
        Spacer(modifier = Modifier.height(10.dp))
        Text("No on-air Squad signals detected.", color = Color.Gray, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = hint,
            color = Color.Gray.copy(alpha = 0.6f),
            fontSize = 11.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
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
        color = Color(0xFF1E2126),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth(),
        enabled = canJoin
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(42.dp).background(accentColor.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Podcasts, contentDescription = null, tint = accentColor, modifier = Modifier.size(22.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(
                    text = subtitle,
                    color = accentColor.copy(alpha = 0.8f),
                    fontSize = 11.sp
                )
            }
            Button(
                onClick = onJoin,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                enabled = canJoin,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text("JOIN", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    }
}
