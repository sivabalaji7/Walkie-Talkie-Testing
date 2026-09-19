package com.example.walkietalkieapp.ui.walkie

import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.walkietalkieapp.ui.theme.*

data class DrawerMenuItem(
    val icon: ImageVector,
    val label: String,
    val group: String,
    val isDestructive: Boolean = false,
    val isAccent: Boolean = false
)

@Composable
fun SideDrawer(
    isOpen: Boolean,
    onClose: () -> Unit,
    onOpenAuth: (mode: String) -> Unit,
    username: String = "Guest Callsign",
    userAvatar: String = "G",
    isLoggedIn: Boolean = false,
    onLogout: () -> Unit = {},
    isDarkMode: Boolean = false,
    onToggleDarkMode: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (isOpen) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopStart
        ) {
            // Fullscreen backdrop overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(DrawerOverlay)
                    .clickable { onClose() }
            )

            // Animated Drawer Panel anchored to TopStart
            androidx.compose.animation.AnimatedVisibility(
                visible = isOpen,
                enter = slideInHorizontally(
                    initialOffsetX = { -it },
                    animationSpec = spring(dampingRatio = 0.75f, stiffness = 300f)
                ),
                exit = slideOutHorizontally(
                    targetOffsetX = { -it },
                    animationSpec = spring(dampingRatio = 0.75f, stiffness = 300f)
                ),
                modifier = Modifier.align(Alignment.TopStart)
            ) {
                Box(
                    modifier = modifier
                        .fillMaxHeight()
                        .width(300.dp)
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .clip(WalkieDrawerShape)
                        .background(DrawerBackground)
                        .border(1.dp, WalkieCardBorder, WalkieDrawerShape)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(20.dp)
                    ) {
                        // Close button
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(WalkieButton)
                                .clickable { onClose() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close drawer",
                                tint = WalkieTextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        // User Profile Header
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(
                                        Brush.linearGradient(
                                            listOf(WalkieAmber, WalkieAmberDark)
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = userAvatar,
                                    fontFamily = SpaceGrotesk,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.Black
                                )
                            }

                            Column {
                                Text(
                                    text = username,
                                    fontFamily = SpaceGrotesk,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = WalkieTextPrimary
                                )
                                Text(
                                    text = if (isLoggedIn) "Online • Ready" else "Guest • Tap to login",
                                    fontFamily = PlusJakartaSans,
                                    fontSize = 12.sp,
                                    color = WalkieTextMuted
                                )
                            }
                        }

                        // Quick Login Button for guests
                        if (!isLoggedIn) {
                            Spacer(modifier = Modifier.height(14.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(WalkieAmber)
                                    .clickable {
                                        onClose()
                                        onOpenAuth("login")
                                    }
                                    .padding(vertical = 10.dp, horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Login,
                                    contentDescription = null,
                                    tint = Color.Black,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "SIGN IN / REGISTER",
                                    fontFamily = SpaceGrotesk,
                                    color = Color.Black,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 11.sp,
                                    letterSpacing = 1.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Tactical Menu Sections
                        val groups = listOf(
                            "Account" to listOf(
                                DrawerMenuItem(Icons.Default.Person, "Profile", "Account"),
                                DrawerMenuItem(Icons.Default.Settings, "Settings", "Account")
                            ),
                            "Communication" to listOf(
                                DrawerMenuItem(Icons.Default.Bookmark, "Saved Channels", "Communication"),
                                DrawerMenuItem(Icons.Default.Smartphone, "Paired Devices", "Communication")
                            ),
                            "Preferences" to listOf(
                                DrawerMenuItem(Icons.AutoMirrored.Filled.VolumeUp, "Audio Preferences", "Preferences"),
                                DrawerMenuItem(Icons.Default.Wifi, "Connectivity", "Preferences")
                            ),
                            "Support" to listOf(
                                DrawerMenuItem(Icons.AutoMirrored.Filled.HelpOutline, "Help & Support", "Support"),
                                DrawerMenuItem(Icons.Default.Info, "About", "Support"),
                                if (isLoggedIn) {
                                    DrawerMenuItem(Icons.AutoMirrored.Filled.Logout, "Logout", "Support", isDestructive = true)
                                } else {
                                    DrawerMenuItem(Icons.AutoMirrored.Filled.Login, "Login / Sign Up", "Support", isAccent = true)
                                }
                            )
                        )

                        groups.forEach { (groupTitle, items) ->
                            Text(
                                text = groupTitle.uppercase(),
                                fontFamily = SpaceGrotesk,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = WalkieTextMuted,
                                letterSpacing = 1.sp,
                                modifier = Modifier.padding(vertical = 6.dp)
                            )

                            // Inject Dark Mode toggle inside Preferences group
                            if (groupTitle == "Preferences") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { onToggleDarkMode(!isDarkMode) }
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(WalkieButton),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = if (isDarkMode) Icons.Default.DarkMode else Icons.Default.LightMode,
                                                contentDescription = "Dark Mode",
                                                tint = WalkieAmber,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        Column {
                                            Text(
                                                text = "Dark Mode",
                                                fontFamily = PlusJakartaSans,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = WalkieTextPrimary
                                            )
                                            Text(
                                                text = if (isDarkMode) "Tactical Black" else "Retro Warm Cream",
                                                fontFamily = PlusJakartaSans,
                                                fontSize = 10.sp,
                                                color = WalkieTextMuted
                                            )
                                        }
                                    }
                                    Switch(
                                        checked = isDarkMode,
                                        onCheckedChange = { onToggleDarkMode(it) },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color.Black,
                                            checkedTrackColor = WalkieAmber,
                                            uncheckedThumbColor = WalkieTextSecondary,
                                            uncheckedTrackColor = WalkieButton
                                        )
                                    )
                                }
                            }

                            items.forEach { item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            when (item.label) {
                                                "Logout" -> {
                                                    onLogout()
                                                    onClose()
                                                }
                                                "Login / Sign Up" -> {
                                                    onClose()
                                                    onOpenAuth("login")
                                                }
                                                else -> {
                                                    onClose()
                                                }
                                            }
                                        }
                                        .padding(horizontal = 10.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(WalkieButton),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = item.icon,
                                            contentDescription = item.label,
                                            tint = when {
                                                item.isDestructive -> Color(0xFFEF4444)
                                                item.isAccent -> WalkieAmber
                                                else -> WalkieTextSecondary
                                            },
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Text(
                                        text = item.label,
                                        fontFamily = PlusJakartaSans,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = when {
                                            item.isDestructive -> Color(0xFFEF4444)
                                            item.isAccent -> WalkieAmber
                                            else -> WalkieTextPrimary
                                        }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                        }
                    }
                }
            }
        }
    }
}
