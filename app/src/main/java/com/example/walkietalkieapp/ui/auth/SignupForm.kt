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
fun SignupForm(
    onSignupSubmit: (username: String, email: String, password: String) -> Unit,
    onSwitchToLogin: () -> Unit,
    onBackToRadio: (() -> Unit)? = null,
    isLoading: Boolean = false,
    externalError: String? = null,
    modifier: Modifier = Modifier
) {
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var termsAccepted by remember { mutableStateOf(false) }
    var showPassword by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val displayError = externalError ?: errorMessage
    val focusManager = LocalFocusManager.current

    var btnPressed by remember { mutableStateOf(false) }
    val btnScale by animateFloatAsState(
        targetValue = if (btnPressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "signupBtnScale"
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
                .padding(26.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // Back to Radio
                if (onBackToRadio != null) {
                    Row(
                        modifier = Modifier
                            .clickable { onBackToRadio() }
                            .padding(bottom = 10.dp),
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

                // Header
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Join WalkieX",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = WalkieTextPrimary,
                        letterSpacing = (-0.5).sp
                    )

                    Text(
                        text = "Connect instantly with friends & squad nearby",
                        fontSize = 12.sp,
                        color = WalkieTextSecondary,
                        modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                    )
                }

                if (displayError != null) {
                    Text(
                        text = displayError,
                        color = Color(0xFFEF4444),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                // Field 1: Callsign
                Text("CALLSIGN / USERNAME", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = WalkieAmberLight, letterSpacing = 1.sp)
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it; errorMessage = null },
                    placeholder = { Text("Pick a unique callsign", color = WalkieTextMuted, fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = WalkieTextMuted, modifier = Modifier.size(16.dp)) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF2A2A2C),
                        unfocusedContainerColor = Color(0xFF2A2A2C),
                        focusedBorderColor = WalkieAmber.copy(alpha = 0.5f),
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = WalkieTextPrimary,
                        unfocusedTextColor = WalkieTextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 10.dp)
                )

                // Field 2: Email
                Text("EMAIL ADDRESS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = WalkieAmberLight, letterSpacing = 1.sp)
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it; errorMessage = null },
                    placeholder = { Text("callsign@walkiex.radio", color = WalkieTextMuted, fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = WalkieTextMuted, modifier = Modifier.size(16.dp)) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF2A2A2C),
                        unfocusedContainerColor = Color(0xFF2A2A2C),
                        focusedBorderColor = WalkieAmber.copy(alpha = 0.5f),
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = WalkieTextPrimary,
                        unfocusedTextColor = WalkieTextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 10.dp)
                )

                // Field 3: Password
                Text("CREATE PASSWORD", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = WalkieAmberLight, letterSpacing = 1.sp)
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; errorMessage = null },
                    placeholder = { Text("At least 6 characters", color = WalkieTextMuted, fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = WalkieTextMuted, modifier = Modifier.size(16.dp)) },
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null,
                                tint = WalkieTextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    },
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF2A2A2C),
                        unfocusedContainerColor = Color(0xFF2A2A2C),
                        focusedBorderColor = WalkieAmber.copy(alpha = 0.5f),
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = WalkieTextPrimary,
                        unfocusedTextColor = WalkieTextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 10.dp)
                )

                // Field 4: Confirm Password
                Text("CONFIRM PASSWORD", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = WalkieAmberLight, letterSpacing = 1.sp)
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it; errorMessage = null },
                    placeholder = { Text("Repeat your password", color = WalkieTextMuted, fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Default.Shield, contentDescription = null, tint = WalkieTextMuted, modifier = Modifier.size(16.dp)) },
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF2A2A2C),
                        unfocusedContainerColor = Color(0xFF2A2A2C),
                        focusedBorderColor = WalkieAmber.copy(alpha = 0.5f),
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = WalkieTextPrimary,
                        unfocusedTextColor = WalkieTextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 12.dp)
                )

                // Terms Checkbox
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    Checkbox(
                        checked = termsAccepted,
                        onCheckedChange = { termsAccepted = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = WalkieAmber,
                            checkmarkColor = Color.Black
                        )
                    )
                    Text(
                        text = "I agree to the Terms of Service and Privacy Policy.",
                        fontSize = 11.sp,
                        color = WalkieTextSecondary,
                        lineHeight = 14.sp
                    )
                }

                // CTA Button
                Box(
                    modifier = Modifier
                        .scale(btnScale)
                        .fillMaxWidth()
                        .height(50.dp)
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
                                        errorMessage = "Please enter a callsign."
                                    } else if (password.length < 6) {
                                        errorMessage = "Password must be at least 6 characters."
                                    } else if (password != confirmPassword) {
                                        errorMessage = "Passwords do not match."
                                    } else if (!termsAccepted) {
                                        errorMessage = "Please accept the terms."
                                    } else {
                                        errorMessage = null
                                        onSignupSubmit(username.trim(), email.trim(), password.trim())
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
                                text = "CREATE ACCOUNT",
                                color = Color.Black,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 12.sp,
                                letterSpacing = 1.25.sp
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

                Spacer(modifier = Modifier.height(16.dp))

                // Switch to Login Link
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Already have an account? ",
                        color = WalkieTextSecondary,
                        fontSize = 12.sp
                    )
                    Text(
                        text = "Log In",
                        color = WalkieAmber,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier.clickable { onSwitchToLogin() }
                    )
                }
            }
        }
    }
}
