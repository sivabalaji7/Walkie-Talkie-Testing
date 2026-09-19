package com.example.walkietalkieapp.ui.auth

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.walkietalkieapp.ui.theme.*

@Composable
fun LoginForm(
    onLoginSubmit: (username: String, password: String) -> Unit,
    onSwitchToSignup: () -> Unit,
    onBackToRadio: (() -> Unit)? = null,
    isLoading: Boolean = false,
    externalError: String? = null,
    modifier: Modifier = Modifier
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val displayError = externalError ?: errorMessage
    val focusManager = LocalFocusManager.current

    var btnPressed by remember { mutableStateOf(false) }
    val btnScale by animateFloatAsState(
        targetValue = if (btnPressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "loginBtnScale"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Tactical Card Chassis
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(elevation = 28.dp, shape = WalkieChassisShape)
                .clip(WalkieChassisShape)
                .background(Color(0xFF1B1B1D))
                .border(1.dp, Color.White.copy(alpha = 0.08f), WalkieChassisShape)
                .padding(28.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // Back to Radio
                if (onBackToRadio != null) {
                    Row(
                        modifier = Modifier
                            .clickable { onBackToRadio() }
                            .padding(bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Radio",
                            tint = WalkieTextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Back to Radio",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = WalkieTextSecondary
                        )
                    }
                }

                // Header Branding
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(WalkieAmber.copy(alpha = 0.12f))
                            .border(1.dp, WalkieAmber.copy(alpha = 0.25f), CircleShape)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Radio,
                            contentDescription = null,
                            tint = WalkieAmber,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "WALKIEX RADIO",
                            color = WalkieAmber,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Welcome back!",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = WalkieTextPrimary,
                        letterSpacing = (-0.5).sp
                    )

                    Text(
                        text = "Please sign in to your squad account",
                        fontSize = 13.sp,
                        color = WalkieTextSecondary,
                        modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
                    )
                }

                // Error alert
                if (displayError != null) {
                    Text(
                        text = displayError,
                        color = Color(0xFFEF4444),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                // Callsign / Username Input
                Text(
                    text = "CALLSIGN / USERNAME",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = WalkieAmberLight,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
                )

                OutlinedTextField(
                    value = username,
                    onValueChange = {
                        username = it
                        errorMessage = null
                    },
                    placeholder = {
                        Text("Enter your callsign", color = WalkieTextMuted, fontSize = 13.sp)
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Person, contentDescription = null, tint = WalkieTextMuted, modifier = Modifier.size(18.dp))
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF2A2A2C),
                        unfocusedContainerColor = Color(0xFF2A2A2C),
                        focusedBorderColor = WalkieAmber.copy(alpha = 0.5f),
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = WalkieTextPrimary,
                        unfocusedTextColor = WalkieTextPrimary
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Password Input
                Text(
                    text = "PASSWORD",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = WalkieAmberLight,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        errorMessage = null
                    },
                    placeholder = {
                        Text("••••••••••••", color = WalkieTextMuted, fontSize = 13.sp)
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = WalkieTextMuted, modifier = Modifier.size(18.dp))
                    },
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (showPassword) "Hide password" else "Show password",
                                tint = WalkieTextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    },
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF2A2A2C),
                        unfocusedContainerColor = Color(0xFF2A2A2C),
                        focusedBorderColor = WalkieAmber.copy(alpha = 0.5f),
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = WalkieTextPrimary,
                        unfocusedTextColor = WalkieTextPrimary
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        focusManager.clearFocus()
                    }),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(22.dp))

                // Primary Action Button (LOGIN)
                Box(
                    modifier = Modifier
                        .scale(btnScale)
                        .fillMaxWidth()
                        .height(52.dp)
                        .shadow(elevation = 12.dp, shape = CircleShape, ambientColor = WalkieAmber, spotColor = WalkieAmber)
                        .clip(CircleShape)
                        .background(
                            Brush.horizontalGradient(
                                listOf(WalkieAmber, WalkieAmberDark)
                            )
                        )
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    if (isLoading) return@detectTapGestures
                                    btnPressed = true
                                    tryAwaitRelease()
                                    btnPressed = false
                                    if (username.isBlank()) {
                                        errorMessage = "Please enter your callsign."
                                    } else if (password.isBlank()) {
                                        errorMessage = "Please enter your password."
                                    } else {
                                        errorMessage = null
                                        onLoginSubmit(username.trim(), password.trim())
                                    }
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            color = Color.Black,
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(20.dp)
                        )
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "LOGIN",
                                color = Color.Black,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.sp,
                                letterSpacing = 1.5.sp
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Switch to Signup Link
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "New to WalkieX? ",
                        color = WalkieTextSecondary,
                        fontSize = 12.sp
                    )
                    Text(
                        text = "Create an account",
                        color = WalkieAmber,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier.clickable { onSwitchToSignup() }
                    )
                }
            }
        }
    }
}
