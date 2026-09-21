package com.example.walkietalkieapp.ui.auth

import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.walkietalkieapp.auth.AuthResult
import com.example.walkietalkieapp.auth.SupabaseAuthManager
import androidx.compose.material3.MaterialTheme
import kotlinx.coroutines.launch

enum class AuthMode {
    LOGIN,
    SIGNUP
}

@Composable
fun WalkieAuthScreen(
    initialMode: AuthMode = AuthMode.LOGIN,
    onAuthSuccess: (userId: String, username: String) -> Unit,
    onBackToRadio: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var currentMode by remember { mutableStateOf(initialMode) }
    val coroutineScope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = currentMode,
            transitionSpec = {
                if (targetState == AuthMode.SIGNUP) {
                    (slideInHorizontally(spring(dampingRatio = 0.75f, stiffness = 300f)) { it } + fadeIn())
                        .togetherWith(slideOutHorizontally(spring(dampingRatio = 0.75f, stiffness = 300f)) { -it } + fadeOut())
                } else {
                    (slideInHorizontally(spring(dampingRatio = 0.75f, stiffness = 300f)) { -it } + fadeIn())
                        .togetherWith(slideOutHorizontally(spring(dampingRatio = 0.75f, stiffness = 300f)) { it } + fadeOut())
                }
            },
            label = "authScreenTransition"
        ) { mode ->
            when (mode) {
                AuthMode.LOGIN -> {
                    LoginForm(
                        onLoginSubmit = { identifier, password ->
                            authError = null
                            isLoading = true
                            coroutineScope.launch {
                                val result = SupabaseAuthManager.signIn(identifier, password)
                                isLoading = false
                                when (result) {
                                    is AuthResult.Success -> {
                                        onAuthSuccess(result.userId, result.username)
                                    }
                                    is AuthResult.Error -> {
                                        authError = result.message
                                    }
                                }
                            }
                        },
                        onSwitchToSignup = {
                            authError = null
                            currentMode = AuthMode.SIGNUP
                        },
                        onBackToRadio = onBackToRadio,
                        isLoading = isLoading,
                        externalError = authError
                    )
                }
                AuthMode.SIGNUP -> {
                    SignupForm(
                        onSignupSubmit = { username, _, password ->
                            authError = null
                            isLoading = true
                            coroutineScope.launch {
                                val result = SupabaseAuthManager.signUp(username, password)
                                isLoading = false
                                when (result) {
                                    is AuthResult.Success -> {
                                        onAuthSuccess(result.userId, result.username)
                                    }
                                    is AuthResult.Error -> {
                                        authError = result.message
                                    }
                                }
                            }
                        },
                        onSwitchToLogin = {
                            authError = null
                            currentMode = AuthMode.LOGIN
                        },
                        onBackToRadio = onBackToRadio,
                        isLoading = isLoading,
                        externalError = authError
                    )
                }
            }
        }
    }
}
