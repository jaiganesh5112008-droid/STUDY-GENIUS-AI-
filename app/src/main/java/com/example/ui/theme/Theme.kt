package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Immutable
data class StudyExtendedColors(
    val emeraldGlow: Color,
    val cyanGlow: Color,
    val electricBlue: Color,
    val violetGlow: Color,
    val amberWarning: Color,
    val glassSurface: Color,
    val glassBorder: Color,
    val cardElevated: Color,
    val isDark: Boolean
)

val LocalStudyColors = staticCompositionLocalOf {
    StudyExtendedColors(
        emeraldGlow = EmeraldGlow,
        cyanGlow = LuminousCyan,
        electricBlue = ElectricBlue,
        violetGlow = CosmicViolet,
        amberWarning = SolarAmber,
        glassSurface = GlassNavySurface,
        glassBorder = SubtleNavyOutline,
        cardElevated = ElevatedNavyCard,
        isDark = true
    )
}

private val DarkColorScheme = darkColorScheme(
    primary = EmeraldGlow,
    onPrimary = DeepSpaceNavy,
    primaryContainer = Color(0xFF0A3333),
    onPrimaryContainer = Color(0xFFA7F3D0),
    secondary = LuminousCyan,
    onSecondary = DeepSpaceNavy,
    secondaryContainer = Color(0xFF0F2B47),
    onSecondaryContainer = Color(0xFFBAE6FD),
    tertiary = ElectricBlue,
    onTertiary = SnowWhite,
    tertiaryContainer = Color(0xFF1A2A56),
    onTertiaryContainer = Color(0xFFDBEAFE),
    background = DeepSpaceNavy,
    onBackground = SnowWhite,
    surface = MidnightNavy,
    onSurface = SnowWhite,
    surfaceVariant = GlassNavySurface,
    onSurfaceVariant = CoolSlate,
    surfaceContainerLowest = DeepSpaceNavy,
    surfaceContainerLow = MidnightNavy,
    surfaceContainer = GlassNavySurface,
    surfaceContainerHigh = ElevatedNavyCard,
    surfaceContainerHighest = Color(0xFF1E2F54),
    outline = SubtleNavyOutline,
    outlineVariant = Color(0xFF1B2B4B),
    error = CrimsonRose,
    onError = SnowWhite,
    errorContainer = Color(0xFF4C1123),
    onErrorContainer = Color(0xFFFFD6DF)
)

private val LightColorScheme = lightColorScheme(
    primary = LightElectricBlue,
    onPrimary = PureWhiteSurface,
    primaryContainer = Color(0xFFDBEAFE),
    onPrimaryContainer = Color(0xFF1E3A8A),
    secondary = LightCyan,
    onSecondary = PureWhiteSurface,
    secondaryContainer = Color(0xFFCFFAFE),
    onSecondaryContainer = Color(0xFF164E63),
    tertiary = LightEmerald,
    onTertiary = PureWhiteSurface,
    tertiaryContainer = Color(0xFFD1FAE5),
    onTertiaryContainer = Color(0xFF064E3B),
    background = IceCanvas,
    onBackground = DeepNavyInk,
    surface = PureWhiteSurface,
    onSurface = DeepNavyInk,
    surfaceVariant = SoftIceContainer,
    onSurfaceVariant = SlateInk,
    surfaceContainerLowest = PureWhiteSurface,
    surfaceContainerLow = Color(0xFFF7FAFF),
    surfaceContainer = PureWhiteSurface,
    surfaceContainerHigh = SoftIceContainer,
    surfaceContainerHighest = Color(0xFFD9E6F7),
    outline = LightBorder,
    outlineVariant = Color(0xFFE2EBF7),
    error = Color(0xFFE11D48),
    onError = PureWhiteSurface,
    errorContainer = Color(0xFFFFE4E6),
    onErrorContainer = Color(0xFF881337)
)

private val StudyShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun StudyGeniusTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val extendedColors = if (darkTheme) {
        StudyExtendedColors(
            emeraldGlow = EmeraldGlow,
            cyanGlow = LuminousCyan,
            electricBlue = ElectricBlue,
            violetGlow = CosmicViolet,
            amberWarning = SolarAmber,
            glassSurface = GlassNavySurface,
            glassBorder = SubtleNavyOutline,
            cardElevated = ElevatedNavyCard,
            isDark = true
        )
    } else {
        StudyExtendedColors(
            emeraldGlow = LightEmerald,
            cyanGlow = LightCyan,
            electricBlue = LightElectricBlue,
            violetGlow = Color(0xFF7C3AED),
            amberWarning = Color(0xFFD97706),
            glassSurface = PureWhiteSurface,
            glassBorder = LightBorder,
            cardElevated = SoftIceContainer,
            isDark = false
        )
    }

    CompositionLocalProvider(LocalStudyColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = StudyShapes,
            content = content
        )
    }
}
