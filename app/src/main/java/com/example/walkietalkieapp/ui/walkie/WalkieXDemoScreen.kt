package com.example.walkietalkieapp.ui.walkie

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.walkietalkieapp.ui.auth.LoginForm
import com.example.walkietalkieapp.ui.auth.SignupForm
import com.example.walkietalkieapp.ui.theme.WalkieBackground
import com.example.walkietalkieapp.ui.theme.WalkieTalkieAppTheme

@Composable
fun WalkieXDemoScreen() {
    WalkieTalkieAppTheme(darkTheme = true) {
        WalkieTalkieApp()
    }
}

@Preview(name = "WalkieX App - Main Screen", showBackground = true, backgroundColor = 0xFF0A0A0B)
@Composable
fun PreviewWalkieTalkieApp() {
    WalkieTalkieAppTheme(darkTheme = true) {
        WalkieTalkieApp()
    }
}

@Preview(name = "Component - TopBar", showBackground = true, backgroundColor = 0xFF0A0A0B)
@Composable
fun PreviewTopBar() {
    WalkieTalkieAppTheme(darkTheme = true) {
        TopBar(
            onMenuOpen = {},
            onProfileClick = {},
            isPowered = true,
            userAvatar = "R",
            username = "Rahul"
        )
    }
}

@Preview(name = "Component - Antenna Mode Dial", showBackground = true, backgroundColor = 0xFF0A0A0B)
@Composable
fun PreviewAntennaModeDial() {
    var mode by remember { mutableStateOf(ConnectivityMode.INTERNET) }
    WalkieTalkieAppTheme(darkTheme = true) {
        AntennaModeDial(
            mode = mode,
            onChange = { mode = it }
        )
    }
}

@Preview(name = "Component - Display Panel", showBackground = true, backgroundColor = 0xFF0A0A0B)
@Composable
fun PreviewDisplayPanel() {
    WalkieTalkieAppTheme(darkTheme = true) {
        DisplayPanel(
            status = ConnectionStatus.CONNECTED,
            talkingState = TalkingState.YOU_TALKING,
            channelName = "The Inner Circle",
            connectivityMode = ConnectivityMode.INTERNET,
            pairedDevice = "4 online"
        )
    }
}

@Preview(name = "Component - Push To Talk", showBackground = true, backgroundColor = 0xFF0A0A0B)
@Composable
fun PreviewPushToTalk() {
    var talking by remember { mutableStateOf(false) }
    WalkieTalkieAppTheme(darkTheme = true) {
        PushToTalk(
            isTalking = talking,
            onPressStart = { talking = true },
            onPressEnd = { talking = false },
            mode = ConnectivityMode.INTERNET
        )
    }
}

@Preview(name = "Component - Action Buttons", showBackground = true, backgroundColor = 0xFF0A0A0B)
@Composable
fun PreviewActionButtons() {
    WalkieTalkieAppTheme(darkTheme = true) {
        ActionButtons(
            isPowered = true,
            onPowerToggle = {},
            onCreateChannel = {},
            onPairedDevices = {},
            onSpeaker = {},
            onQuickActions = {},
            inSquad = true,
            speakerOn = true
        )
    }
}

@Preview(name = "Component - Side Scroller", showBackground = true, backgroundColor = 0xFF0A0A0B)
@Composable
fun PreviewSideScroller() {
    WalkieTalkieAppTheme(darkTheme = true) {
        Box(modifier = Modifier.padding(20.dp)) {
            SideScroller(
                onStep = {},
                onPull = {}
            )
        }
    }
}

@Preview(name = "Component - Squad Room", showBackground = true, backgroundColor = 0xFF0A0A0B)
@Composable
fun PreviewSquadRoom() {
    WalkieTalkieAppTheme(darkTheme = true) {
        SquadRoom(
            squad = Squad(
                id = "mock_alpha",
                name = "Alpha Squad",
                lastActive = "Active now",
                secure = true,
                members = listOf(
                    SquadMember("Ghost", "", true),
                    SquadMember("Viper", "", false)
                )
            ),
            mode = ConnectivityMode.INTERNET,
            onExit = {}
        )
    }
}

@Preview(name = "Auth - Login Form", showBackground = true, backgroundColor = 0xFF0A0A0B)
@Composable
fun PreviewLoginForm() {
    WalkieTalkieAppTheme(darkTheme = true) {
        LoginForm(
            onLoginSubmit = { _, _ -> },
            onSwitchToSignup = {},
            onBackToRadio = {}
        )
    }
}

@Preview(name = "Auth - Signup Form", showBackground = true, backgroundColor = 0xFF0A0A0B)
@Composable
fun PreviewSignupForm() {
    WalkieTalkieAppTheme(darkTheme = true) {
        SignupForm(
            onSignupSubmit = { _, _, _ -> },
            onSwitchToLogin = {},
            onBackToRadio = {}
        )
    }
}
