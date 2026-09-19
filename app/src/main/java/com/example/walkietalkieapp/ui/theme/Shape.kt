package com.example.walkietalkieapp.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val WalkieShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

val WalkieChassisShape = RoundedCornerShape(40.dp)
val WalkieCardShape = RoundedCornerShape(32.dp)
val WalkiePillShape = CircleShape
val WalkieButtonShape = RoundedCornerShape(16.dp)
val WalkieScreenShape = RoundedCornerShape(20.dp)
val WalkieDrawerShape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp)
