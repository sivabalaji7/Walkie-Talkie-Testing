package com.example.walkietalkieapp.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.walkietalkieapp.RoomsDashboardScreen
import com.example.walkietalkieapp.bluetooth.BluetoothSquadUiState
import com.example.walkietalkieapp.bluetooth.DiscoveredSquad
import com.example.walkietalkieapp.wifidirect.DiscoveredWifiSquad
import com.example.walkietalkieapp.wifidirect.WifiSquadUiState

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
        // Master Header Bar (WalkieX Branding, Operator Callsign, Quick Controls)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Brand & System Indicator
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "WALKIEX",
                        fontFamily = SpaceGrotesk,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        letterSpacing = 1.sp,
                        color = TactileColors.onSurface
                    )
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(TactileColors.primaryContainer, CircleShape)
                    )
                }
                Text(
                    text = "PRECISION TRANSCEIVER",
                    fontFamily = Manrope,
                    fontWeight = FontWeight.Medium,
                    fontSize = 9.sp,
                    letterSpacing = 1.2.sp,
                    color = TactileColors.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }

            // Right Actions (Operator Call Sign Badge + Battery Saver + Logout)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Operator Call Sign Badge (Clickable to Edit)
                Surface(
                    onClick = onEditCallSign,
                    color = TactileColors.surfaceContainerLow,
                    shape = TactileShapes.pill,
                    modifier = Modifier.border(1.dp, TactileColors.ghostBorder, TactileShapes.pill)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        val modeColor = when (selectedMode) {
                            TransportMode.INTERNET -> TactileColors.primary
                            TransportMode.BLUETOOTH -> TactileColors.statusConnecting
                            TransportMode.WIFI_DIRECT -> TactileColors.statusActive
                        }
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .background(modeColor, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = callSign.ifBlank { currentUsername },
                            color = TactileColors.onSurface,
                            fontFamily = SpaceGrotesk,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Call Sign",
                            tint = TactileColors.onSecondaryContainer,
                            modifier = Modifier.size(11.dp)
                        )
                    }
                }

                // Battery Saver Toggle Button
                IconButton(
                    onClick = { onBatterySaverToggle(!isBatterySaverEnabled) },
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(TactileColors.surfaceContainerLow)
                        .border(1.dp, TactileColors.ghostBorder, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.BatterySaver,
                        contentDescription = "Battery Saver",
                        tint = if (isBatterySaverEnabled) TactileColors.statusActive else TactileColors.onSecondaryContainer,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Logout Button
                IconButton(
                    onClick = onLogout,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(TactileColors.surfaceContainerLow)
                        .border(1.dp, TactileColors.ghostBorder, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Logout,
                        contentDescription = "Log Out",
                        tint = TactileColors.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Segmented Transport Switcher Bar (Tactile Digitalism Tonal Switcher)
        Surface(
            color = TactileColors.surfaceContainerLowest,
            shape = TactileShapes.tile,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp)
                .border(1.dp, TactileColors.ghostBorder, TactileShapes.tile)
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
                    selectedColor = TactileColors.primary,
                    onClick = { onSelectMode(TransportMode.INTERNET) },
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(4.dp))

                // 2. Bluetooth Mode Tab
                TransportTabButton(
                    title = "Bluetooth",
                    icon = Icons.Default.Bluetooth,
                    isSelected = selectedMode == TransportMode.BLUETOOTH,
                    selectedColor = TactileColors.statusConnecting,
                    onClick = { onSelectMode(TransportMode.BLUETOOTH) },
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(4.dp))

                // 3. Wi-Fi Direct Mode Tab
                TransportTabButton(
                    title = "Wi-Fi Direct",
                    icon = Icons.Default.Wifi,
                    isSelected = selectedMode == TransportMode.WIFI_DIRECT,
                    selectedColor = TactileColors.statusActive,
                    onClick = { onSelectMode(TransportMode.WIFI_DIRECT) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Unified Valour Link Intelligence 2.0 Card
        val currentTransportType = when (selectedMode) {
            TransportMode.INTERNET -> com.example.walkietalkieapp.dna.model.TransportType.Internet
            TransportMode.BLUETOOTH -> com.example.walkietalkieapp.dna.model.TransportType.Bluetooth
            TransportMode.WIFI_DIRECT -> com.example.walkietalkieapp.dna.model.TransportType.WifiDirect
        }

        LaunchedEffect(currentTransportType) {
            com.example.walkietalkieapp.dna.valour.ValourLinkIntelligence.setActiveTransport(currentTransportType)
        }

        val allLinks by com.example.walkietalkieapp.dna.valour.ValourLinkIntelligence.allLinksState.collectAsState()
        val activeLinkState by com.example.walkietalkieapp.dna.valour.ValourLinkIntelligence.activeLinkState.collectAsState()

        var selectedSheetLinkState by remember {
            mutableStateOf<com.example.walkietalkieapp.dna.valour.ValourLinkState?>(null)
        }

        Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
            com.example.walkietalkieapp.dna.valour.ValourLinkDashboardCard(
                activeLinkState = activeLinkState,
                allLinks = allLinks,
                onViewDetailsClick = { link -> selectedSheetLinkState = link }
            )
        }

        selectedSheetLinkState?.let { linkState ->
            com.example.walkietalkieapp.dna.valour.ValourConnectionDetailsSheet(
                linkState = linkState,
                onDismissRequest = { selectedSheetLinkState = null }
            )
        }

        Spacer(modifier = Modifier.height(2.dp))

        // Mode Content Container (Smooth cross-fade transitions)
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
    icon: ImageVector,
    isSelected: Boolean,
    selectedColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) TactileColors.surfaceContainerHighest else Color.Transparent,
        animationSpec = tween(250),
        label = "tabBg"
    )

    val iconColor by animateColorAsState(
        targetValue = if (isSelected) selectedColor else TactileColors.onSecondaryContainer,
        animationSpec = tween(250),
        label = "tabIcon"
    )

    val textColor by animateColorAsState(
        targetValue = if (isSelected) TactileColors.onSurface else TactileColors.onSecondaryContainer,
        animationSpec = tween(250),
        label = "tabText"
    )

    Box(
        modifier = modifier
            .clip(TactileShapes.tile)
            .background(backgroundColor)
            .border(
                width = if (isSelected) 1.dp else 0.dp,
                color = if (isSelected) TactileColors.outlineVariant.copy(alpha = 0.5f) else Color.Transparent,
                shape = TactileShapes.tile
            )
            .clickable { onClick() }
            .padding(vertical = 10.dp, horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = title,
                color = textColor,
                fontFamily = if (isSelected) SpaceGrotesk else Manrope,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 12.sp
            )
        }
    }
}
