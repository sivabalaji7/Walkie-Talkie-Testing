package com.example.walkietalkieapp.ui

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.walkietalkieapp.R

/**
 * Tactile Digitalism Design System for WalkieX
 * 
 * Blending vintage radio hardware aesthetics with contemporary editorial design.
 * Tonal shifts define structure (no harsh 1px borders), warm amber ignition
 * accents against deep obsidian/charcoal surfaces, and Space Grotesk + Manrope typography.
 */
object TactileColors {
    // Canvas & Surfaces (Tonal Hierarchy)
    val background = Color(0xFF131315)
    val lightBackground = Color(0xFFF8F9FA)
    val surface = Color(0xFF131315)
    val surfaceDim = Color(0xFF131315)
    val surfaceBright = Color(0xFF39393B)
    val surfaceContainerLowest = Color(0xFF0E0E10)
    val surfaceContainerLow = Color(0xFF1B1B1D)
    val surfaceContainer = Color(0xFF201F21)
    val surfaceContainerHigh = Color(0xFF2A2A2C)
    val surfaceContainerHighest = Color(0xFF353437)
    val surfaceVariant = Color(0xFF353437)
    val surfaceTint = Color(0xFFFFB77F)

    // Primary Accents (Vintage Radio Amber/Orange)
    val primary = Color(0xFFFFB77F)
    val primaryContainer = Color(0xFFFF8A00)
    val primaryGradientEnd = Color(0xFFFF6A00)
    val onPrimary = Color(0xFF4E2600)
    val onPrimaryContainer = Color(0xFF613100)
    val primaryFixed = Color(0xFFFFDCC4)
    val primaryFixedDim = Color(0xFFFFB77F)
    val onPrimaryFixed = Color(0xFF2F1500)
    val onPrimaryFixedVariant = Color(0xFF6F3900)

    // Secondary Accents (Machined Slate)
    val secondary = Color(0xFFC8C6C9)
    val secondaryContainer = Color(0xFF47464A)
    val onSecondary = Color(0xFF303033)
    val onSecondaryContainer = Color(0xFFB6B4B8)
    val secondaryFixed = Color(0xFFE4E2E5)
    val secondaryFixedDim = Color(0xFFC8C6C9)
    val onSecondaryFixed = Color(0xFF1B1B1E)
    val onSecondaryFixedVariant = Color(0xFF47464A)

    // Tertiary Accents
    val tertiary = Color(0xFFC8C6C6)
    val tertiaryContainer = Color(0xFFAAA8A8)
    val onTertiary = Color(0xFF303030)
    val onTertiaryContainer = Color(0xFF3D3D3D)
    val tertiaryFixed = Color(0xFFE4E2E1)
    val tertiaryFixedDim = Color(0xFFC8C6C6)

    // Typography & Content
    val onBackground = Color(0xFFE5E1E4)
    val onSurface = Color(0xFFE5E1E4)
    val onSurfaceVariant = Color(0xFFDDC1AE)
    val outline = Color(0xFFA58C7B)
    val outlineVariant = Color(0xFF564334)
    val ghostBorder = Color(0xFF564334).copy(alpha = 0.25f)

    // Feedback & Status
    val error = Color(0xFFFFB4AB)
    val errorContainer = Color(0xFF93000A)
    val onError = Color(0xFF690005)
    val onErrorContainer = Color(0xFFFFDAD6)
    val statusActive = Color(0xFF4CAF50)
    val statusBusy = Color(0xFFFF8A00)
    val statusConnecting = Color(0xFF00E5FF)

    // Inverse
    val inverseSurface = Color(0xFFE5E1E4)
    val inverseOnSurface = Color(0xFF303032)
    val inversePrimary = Color(0xFF914C00)

    // Gradients
    val buttonGradient = Brush.horizontalGradient(listOf(primaryContainer, primaryGradientEnd))
    val pttGradient = Brush.verticalGradient(listOf(primary, primaryContainer))
    val pttPressedGradient = Brush.verticalGradient(listOf(primaryContainer, Color(0xFFE65100)))
    val cardGradient = Brush.verticalGradient(listOf(surfaceContainer, surfaceContainerLow))
    val headerGradient = Brush.verticalGradient(listOf(surfaceContainerHigh, surfaceContainerLow))
}

// Google Fonts Provider Setup
private val googleFontProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)

// Space Grotesk (Display, Headlines, Frequency Numbers, Timers, Instrument Readouts)
val SpaceGrotesk = FontFamily(
    Font(googleFont = GoogleFont("Space Grotesk"), fontProvider = googleFontProvider)
)

// Manrope (Body, Metadata, Status, Labels, Functional Inputs)
val Manrope = FontFamily(
    Font(googleFont = GoogleFont("Manrope"), fontProvider = googleFontProvider)
)

object TactileTypography {
    val displayLarge = TextStyle(
        fontFamily = SpaceGrotesk,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        letterSpacing = (-0.5).sp,
        color = TactileColors.onSurface
    )

    val headlineMedium = TextStyle(
        fontFamily = SpaceGrotesk,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        letterSpacing = 0.sp,
        color = TactileColors.onSurface
    )

    val headlineSmall = TextStyle(
        fontFamily = SpaceGrotesk,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        letterSpacing = 0.5.sp,
        color = TactileColors.onSurface
    )

    val readoutNumber = TextStyle(
        fontFamily = SpaceGrotesk,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        letterSpacing = 1.sp,
        color = TactileColors.primary
    )

    val titleMedium = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        letterSpacing = 0.15.sp,
        color = TactileColors.onSurface
    )

    val bodyMedium = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        letterSpacing = 0.25.sp,
        color = TactileColors.onSurface
    )

    val bodySmall = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        letterSpacing = 0.4.sp,
        color = TactileColors.onSurfaceVariant
    )

    val labelMedium = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        letterSpacing = 0.5.sp,
        color = TactileColors.onSecondaryContainer
    )

    val labelSmall = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        letterSpacing = 0.5.sp,
        color = TactileColors.onSecondaryContainer
    )
}

object TactileShapes {
    val card = RoundedCornerShape(24.dp)
    val input = RoundedCornerShape(16.dp)
    val tile = RoundedCornerShape(14.dp)
    val chip = CircleShape
    val pill = CircleShape
    val button = CircleShape
    val small = RoundedCornerShape(8.dp)
}
