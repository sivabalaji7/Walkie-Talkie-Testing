package com.example.walkietalkieapp.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
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
import com.example.walkietalkieapp.RoomsDashboardScreen
import com.example.walkietalkieapp.bluetooth.BluetoothSquadUiState
import com.example.walkietalkieapp.bluetooth.DiscoveredSquad
import com.example.walkietalkieapp.wifidirect.DiscoveredWifiSquad
import com.example.walkietalkieapp.wifidirect.WifiSquadUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MasterHomeScreen(
    currentUserId: String,
    currentUsername: String,
    callSign: String,
    selectedMode: TransportMode,
    onSelectMode: (TransportMode) -> Unit,
    onEditCallSign: () -> Unit,
    onLogout: () -> Unit,
    isBatterySaverEnabled: Boolean,
    onBatterySaverToggle: (Boolean) -> Unit,
    onJoinInternetRoom: (String, String) -> Unit,
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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        // Master Header Bar (User Badge, Battery Saver, Logout)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Operator Call Sign Badge (Clickable to Edit)
            Surface(
                onClick = onEditCallSign,
                color = Color(0xFF14161B),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF262B35))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        Icons.Default.AccountCircle,
                        contentDescription = null,
                        tint = when (selectedMode) {
                            TransportMode.INTERNET -> Color(0xFF00FF66)
                            TransportMode.BLUETOOTH -> Color(0xFF00E5FF)
                            TransportMode.WIFI_DIRECT -> Color(0xFF4CAF50)
                        },
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = callSign.ifBlank { currentUsername },
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Right Action Controls (Battery Saver Toggle + Logout)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = { onBatterySaverToggle(!isBatterySaverEnabled) },
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1E2126))
                        .border(1.dp, Color(0xFF2E333D), CircleShape)
                ) {
                    Icon(
                        Icons.Default.BatterySaver,
                        contentDescription = "Battery Saver",
                        tint = if (isBatterySaverEnabled) Color(0xFF4CAF50) else Color.Gray,
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = onLogout,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1E2126))
                        .border(1.dp, Color(0xFF2E333D), CircleShape)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Logout,
                        contentDescription = "Log Out",
                        tint = Color(0xFFFF5252),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Segmented Transport Switcher Bar (Option 1: 3-Way Toggle)
        Surface(
            color = Color(0xFF121418),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF222630)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // 1. Internet Mode Tab
                TransportTabButton(
                    title = "Internet",
                    icon = Icons.Default.Cloud,
                    isSelected = selectedMode == TransportMode.INTERNET,
                    selectedColor = Color(0xFF00FF66),
                    onClick = { onSelectMode(TransportMode.INTERNET) },
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(4.dp))

                // 2. Bluetooth Mode Tab
                TransportTabButton(
                    title = "Bluetooth",
                    icon = Icons.Default.Bluetooth,
                    isSelected = selectedMode == TransportMode.BLUETOOTH,
                    selectedColor = Color(0xFF00E5FF),
                    onClick = { onSelectMode(TransportMode.BLUETOOTH) },
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(4.dp))

                // 3. Wi-Fi Direct Mode Tab
                TransportTabButton(
                    title = "Wi-Fi Direct",
                    icon = Icons.Default.Wifi,
                    isSelected = selectedMode == TransportMode.WIFI_DIRECT,
                    selectedColor = Color(0xFF4CAF50),
                    onClick = { onSelectMode(TransportMode.WIFI_DIRECT) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Mode Content Container
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            when (selectedMode) {
                TransportMode.INTERNET -> {
                    RoomsDashboardScreen(
                        currentUserId = currentUserId,
                        currentUsername = currentUsername,
                        onLogout = onLogout,
                        onJoinRoom = onJoinInternetRoom,
                        showTopBar = false
                    )
                }
                TransportMode.BLUETOOTH -> {
                    OfflineDiscoveryScreen(
                        callSign = callSign,
                        onEditCallSign = onEditCallSign,
                        selectedMode = TransportMode.BLUETOOTH,
                        onSelectMode = onSelectMode,
                        btUiState = btUiState,
                        wifiUiState = wifiUiState,
                        hasPermissions = hasPermissions,
                        onRequestPermissions = onRequestPermissions,
                        onHostBtSquad = onHostBtSquad,
                        onJoinBtSquad = onJoinBtSquad,
                        onStartBtScan = onStartBtScan,
                        onHostWifiSquad = onHostWifiSquad,
                        onJoinWifiSquad = onJoinWifiSquad,
                        onStartWifiScan = onStartWifiScan
                    )
                }
                TransportMode.WIFI_DIRECT -> {
                    OfflineDiscoveryScreen(
                        callSign = callSign,
                        onEditCallSign = onEditCallSign,
                        selectedMode = TransportMode.WIFI_DIRECT,
                        onSelectMode = onSelectMode,
                        btUiState = btUiState,
                        wifiUiState = wifiUiState,
                        hasPermissions = hasPermissions,
                        onRequestPermissions = onRequestPermissions,
                        onHostBtSquad = onHostBtSquad,
                        onJoinBtSquad = onJoinBtSquad,
                        onStartBtScan = onStartBtScan,
                        onHostWifiSquad = onHostWifiSquad,
                        onJoinWifiSquad = onJoinWifiSquad,
                        onStartWifiScan = onStartWifiScan
                    )
                }
            }
        }
    }
}

@Composable
fun TransportTabButton(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    selectedColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) selectedColor.copy(alpha = 0.15f) else Color.Transparent)
            .border(
                width = if (isSelected) 1.dp else 0.dp,
                color = if (isSelected) selectedColor.copy(alpha = 0.6f) else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onClick() }
            .padding(vertical = 8.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) selectedColor else Color.Gray,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = title,
                color = if (isSelected) Color.White else Color.Gray,
                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                fontSize = 11.sp
            )
        }
    }
}
