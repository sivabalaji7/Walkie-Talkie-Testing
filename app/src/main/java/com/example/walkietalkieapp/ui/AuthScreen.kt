package com.example.walkietalkieapp.ui

import androidx.compose.animation.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.text.googlefonts.Font
import com.example.walkietalkieapp.R

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.walkietalkieapp.auth.AuthResult
import com.example.walkietalkieapp.auth.SupabaseAuthManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    onAuthSuccess: (userId: String, username: String) -> Unit
) {
    var isSignUp by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    
    // Backend States
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    // Shared Tactile Digitalism design tokens
    val background = TactileColors.lightBackground
    val primaryContainer = TactileColors.primaryContainer
    val primary = TactileColors.primary
    val surfaceContainerLow = TactileColors.surfaceContainerLow
    val surfaceContainerHighest = TactileColors.surfaceContainerHighest
    val onSurface = TactileColors.onSurface
    val onSurfaceVariant = TactileColors.onSurfaceVariant
    val onSecondaryContainer = TactileColors.onSecondaryContainer
    val outlineVariant = TactileColors.outlineVariant
    val onBackground = TactileColors.background
    val buttonGradientEnd = TactileColors.primaryGradientEnd

    fun submit() {
        errorMessage = null
        focusManager.clearFocus()

        if (username.isBlank()) {
            errorMessage = "Please enter a username"
            return
        }
        if (password.isBlank()) {
            errorMessage = "Please enter a password"
            return
        }

        if (isSignUp) {
            if (password.length < 6) {
                errorMessage = "Password must be at least 6 characters"
                return
            }
            if (password != confirmPassword) {
                errorMessage = "Passwords do not match"
                return
            }

            isLoading = true
            coroutineScope.launch {
                val result = SupabaseAuthManager.signUp(username, password)
                isLoading = false
                when (result) {
                    is AuthResult.Success -> {
                        onAuthSuccess(result.userId, result.username)
                    }
                    is AuthResult.Error -> {
                        errorMessage = result.message
                    }
                }
            }
        } else {
            isLoading = true
            coroutineScope.launch {
                val result = SupabaseAuthManager.signIn(username, password)
                isLoading = false
                when (result) {
                    is AuthResult.Success -> {
                        onAuthSuccess(result.userId, result.username)
                    }
                    is AuthResult.Error -> {
                        errorMessage = result.message
                    }
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
    ) {
        // Decorative Glows
        Canvas(modifier = Modifier.fillMaxSize()) {
            val glowRadius = size.width * 0.8f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(primaryContainer.copy(alpha = 0.08f), Color.Transparent),
                    center = Offset(0f, 0f),
                    radius = glowRadius
                ),
                radius = glowRadius,
                center = Offset(0f, 0f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(primaryContainer.copy(alpha = 0.08f), Color.Transparent),
                    center = Offset(size.width, size.height),
                    radius = glowRadius
                ),
                radius = glowRadius,
                center = Offset(size.width, size.height)
            )
        }

        // Top Navigation
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.WifiTethering,
                contentDescription = "WalkieX Logo",
                tint = primaryContainer,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "WALKIEX",
                color = onBackground,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 2.sp,
                fontFamily = SpaceGrotesk
            )
        }

        // Main Content Container
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Form Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(48.dp))
                    .background(surfaceContainerLow)
                    .border(
                        width = 1.dp,
                        color = outlineVariant.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(48.dp)
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp, vertical = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (isSignUp) "Create Account" else "Welcome back!",
                        color = onSurface,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp,
                        fontFamily = SpaceGrotesk
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = if (isSignUp) "Join the communication network" else "Please sign in to your account",
                        color = onSecondaryContainer,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = Manrope
                    )

                    Spacer(modifier = Modifier.height(32.dp))
                    
                    // Error Alert Banner
                    AnimatedVisibility(
                        visible = errorMessage != null,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        errorMessage?.let { msg ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 16.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color(0xFF3A1A1A)) // Dark reddish context
                                    .border(1.dp, Color(0xFFFF5252).copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = Color(0xFFFF5252),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = msg,
                                    color = Color(0xFFFF8A80),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    fontFamily = Manrope
                                )
                            }
                        }
                    }

                    // Username Input
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "USERNAME",
                            color = primary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp,
                            fontFamily = Manrope,
                            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                        )
                        
                        var isUsernameFocused by remember { mutableStateOf(false) }
                        OutlinedTextField(
                            value = username,
                            onValueChange = { 
                                username = it
                                errorMessage = null 
                            },
                            textStyle = androidx.compose.ui.text.TextStyle(fontFamily = Manrope, color = onSurface),
                            placeholder = { Text("Enter your username", color = onSecondaryContainer.copy(alpha = 0.3f), fontFamily = Manrope) },
                            leadingIcon = {
                                Icon(Icons.Default.Person, contentDescription = null, tint = onSecondaryContainer)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { isUsernameFocused = it.isFocused },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = surfaceContainerHighest,
                                unfocusedContainerColor = surfaceContainerHighest,
                                focusedBorderColor = primary.copy(alpha = 0.2f),
                                unfocusedBorderColor = Color.Transparent,
                                focusedTextColor = onSurface,
                                unfocusedTextColor = onSurface,
                                cursorColor = primary
                            ),
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Text,
                                imeAction = ImeAction.Next
                            ),
                            keyboardActions = KeyboardActions(
                                onNext = { focusManager.moveFocus(FocusDirection.Down) }
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Password Input
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "PASSWORD",
                            color = primary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp,
                            fontFamily = Manrope,
                            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                        )
                        
                        var isPasswordFocused by remember { mutableStateOf(false) }
                        OutlinedTextField(
                            value = password,
                            onValueChange = { 
                                password = it
                                errorMessage = null 
                            },
                            textStyle = androidx.compose.ui.text.TextStyle(fontFamily = Manrope, color = onSurface),
                            placeholder = { Text("••••••••••••", color = onSecondaryContainer.copy(alpha = 0.3f), fontFamily = Manrope) },
                            leadingIcon = {
                                Icon(Icons.Default.Lock, contentDescription = null, tint = onSecondaryContainer)
                            },
                            trailingIcon = {
                                IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                    Icon(
                                        imageVector = if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = null,
                                        tint = onSecondaryContainer
                                    )
                                }
                            },
                            visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { isPasswordFocused = it.isFocused },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = surfaceContainerHighest,
                                unfocusedContainerColor = surfaceContainerHighest,
                                focusedBorderColor = primary.copy(alpha = 0.2f),
                                unfocusedBorderColor = Color.Transparent,
                                focusedTextColor = onSurface,
                                unfocusedTextColor = onSurface,
                                cursorColor = primary
                            ),
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = if (isSignUp) ImeAction.Next else ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onNext = { focusManager.moveFocus(FocusDirection.Down) },
                                onDone = { submit() }
                            )
                        )
                    }
                    
                    AnimatedVisibility(
                        visible = isSignUp,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column {
                            Spacer(modifier = Modifier.height(24.dp))
                            // Confirm Password Input
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "CONFIRM PASSWORD",
                                    color = primary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.5.sp,
                                    fontFamily = Manrope,
                                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                                )
                                var isConfirmPasswordFocused by remember { mutableStateOf(false) }
                                OutlinedTextField(
                                    value = confirmPassword,
                                    onValueChange = { 
                                        confirmPassword = it
                                        errorMessage = null 
                                    },
                                    textStyle = androidx.compose.ui.text.TextStyle(fontFamily = Manrope, color = onSurface),
                                    placeholder = { Text("••••••••••••", color = onSecondaryContainer.copy(alpha = 0.3f), fontFamily = Manrope) },
                                    leadingIcon = {
                                        Icon(Icons.Default.Lock, contentDescription = null, tint = onSecondaryContainer)
                                    },
                                    visualTransformation = PasswordVisualTransformation(),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .onFocusChanged { isConfirmPasswordFocused = it.isFocused },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = surfaceContainerHighest,
                                        unfocusedContainerColor = surfaceContainerHighest,
                                        focusedBorderColor = primary.copy(alpha = 0.2f),
                                        unfocusedBorderColor = Color.Transparent,
                                        focusedTextColor = onSurface,
                                        unfocusedTextColor = onSurface,
                                        cursorColor = primary
                                    ),
                                    singleLine = true,
                                    shape = RoundedCornerShape(16.dp),
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Password,
                                        imeAction = ImeAction.Done
                                    ),
                                    keyboardActions = KeyboardActions(
                                        onDone = { submit() }
                                    )
                                )
                            }
                        }
                    }

                    if (!isSignUp) {
                        Spacer(modifier = Modifier.height(8.dp))
                        // Forgot Password Link
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Text(
                                text = "Forgot password?",
                                color = onSecondaryContainer,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = Manrope,
                                modifier = Modifier
                                    .clickable { /* Handle forgot password */ }
                                    .padding(4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    val isFormValid = username.isNotBlank() && password.isNotBlank()

                    // Primary Button
                    Button(
                        onClick = { submit() },
                        enabled = !isLoading && isFormValid,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .clip(RoundedCornerShape(50)), // full rounded
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent
                        ),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.linearGradient(
                                        colors = if (!isLoading && isFormValid) {
                                            listOf(primaryContainer, buttonGradientEnd)
                                        } else {
                                            listOf(surfaceContainerHighest, surfaceContainerHighest)
                                        }
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = Color(0xFF4E2600),
                                    strokeWidth = 2.5.dp
                                )
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = if (isSignUp) "SIGN UP" else "LOGIN",
                                        color = if (!isLoading && isFormValid) Color(0xFF4E2600) else onSecondaryContainer,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        letterSpacing = 1.sp,
                                        fontFamily = SpaceGrotesk
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Icon(
                                        imageVector = Icons.Default.ArrowForward,
                                        contentDescription = null,
                                        tint = if (!isLoading && isFormValid) Color(0xFF4E2600) else onSecondaryContainer,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    // Alternate Option
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = if (isSignUp) "Already have an account? " else "New to WalkieX? ",
                            color = onSecondaryContainer,
                            fontSize = 14.sp,
                            fontFamily = Manrope
                        )
                        Text(
                            text = if (isSignUp) "Sign In" else "Create an account",
                            color = primary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            textDecoration = TextDecoration.Underline,
                            fontFamily = Manrope,
                            modifier = Modifier.clickable {
                                isSignUp = !isSignUp
                                errorMessage = null
                            }
                        )
                    }
                }
            }
            
            // Footer
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "© 2026 WALKIEX COMMUNICATION",
                color = onBackground.copy(alpha = 0.4f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                fontFamily = SpaceGrotesk
            )
        }
    }
}
