package com.example.walkietalkieapp.ui.walkie

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.example.walkietalkieapp.ui.theme.*

enum class ConnectivityMode {
    INTERNET,
    BLUETOOTH,
    WIFI_DIRECT
}

data class ModeThemeConfig(
    val mode: ConnectivityMode,
    val label: String,
    val tag: String,
    val detail: String,
    val primaryColor: Color,
    val secondaryColor: Color,
    val glowColor: Color,
    val ringColor: Color,
    val gradient: Brush
)

object ModeThemes {
    val Internet = ModeThemeConfig(
        mode = ConnectivityMode.INTERNET,
        label = "INTERNET",
        tag = "GLOBAL PTT",
        detail = "Cloud relays & private squad channels",
        primaryColor = ModeInternetPrimary,
        secondaryColor = ModeInternetSecondary,
        glowColor = ModeInternetGlow,
        ringColor = ModeInternetPrimary,
        gradient = Brush.verticalGradient(
            listOf(Color(0xFFF59E0B), Color(0xFFD97706), Color(0xFFEA580C))
        )
    )

    val Bluetooth = ModeThemeConfig(
        mode = ConnectivityMode.BLUETOOTH,
        label = "BLUETOOTH",
        tag = "NEARBY BEACON",
        detail = "Short range direct peer-to-peer audio",
        primaryColor = ModeBluetoothPrimary,
        secondaryColor = ModeBluetoothSecondary,
        glowColor = ModeBluetoothGlow,
        ringColor = ModeBluetoothPrimary,
        gradient = Brush.verticalGradient(
            listOf(Color(0xFF0284C7), Color(0xFF2563EB), Color(0xFF4F46E5))
        )
    )

    val WifiDirect = ModeThemeConfig(
        mode = ConnectivityMode.WIFI_DIRECT,
        label = "WI-FI DIRECT",
        tag = "OFFLINE MESH",
        detail = "High bandwidth local wireless squad",
        primaryColor = ModeWifiDirectPrimary,
        secondaryColor = ModeWifiDirectSecondary,
        glowColor = ModeWifiDirectGlow,
        ringColor = ModeWifiDirectPrimary,
        gradient = Brush.verticalGradient(
            listOf(Color(0xFF059669), Color(0xFF0D9488), Color(0xFF047857))
        )
    )

    fun get(mode: ConnectivityMode): ModeThemeConfig = when (mode) {
        ConnectivityMode.INTERNET -> Internet
        ConnectivityMode.BLUETOOTH -> Bluetooth
        ConnectivityMode.WIFI_DIRECT -> WifiDirect
    }
}
