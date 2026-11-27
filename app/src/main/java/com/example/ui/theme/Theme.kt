package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

enum class AppThemeMode(val labelPtBr: String) {
    DARK("Escuro"),
    LIGHT("Claro"),
    SYSTEM("Sistema")
}

private val AtomicDarkColorScheme = darkColorScheme(
    primary = AtomicWhite,
    onPrimary = AtomicInk,
    primaryContainer = AtomicSurfaceHigh,
    onPrimaryContainer = AtomicTextPrimary,
    secondary = TagGreenFg,
    onSecondary = AtomicInk,
    secondaryContainer = TagGreenBg,
    onSecondaryContainer = TagGreenFg,
    tertiary = TagAmberFg,
    onTertiary = AtomicInk,
    tertiaryContainer = TagAmberBg,
    onTertiaryContainer = TagAmberFg,
    background = AtomicBackground,
    onBackground = AtomicTextPrimary,
    surface = AtomicSurface,
    onSurface = AtomicTextPrimary,
    surfaceVariant = AtomicSurfaceContainer,
    onSurfaceVariant = AtomicTextSecondary,
    surfaceContainerLowest = AtomicBackground,
    surfaceContainerLow = AtomicSurface,
    surfaceContainer = AtomicSurfaceContainer,
    surfaceContainerHigh = AtomicSurfaceHigh,
    surfaceContainerHighest = AtomicSurfaceHighest,
    outline = AtomicOutline,
    outlineVariant = AtomicOutlineVariant,
    error = TagRed,
    onError = Color.White,
    errorContainer = TagRedBg,
    onErrorContainer = Color(0xFFFCA5A5)
)

private val AtomicLightColorScheme = lightColorScheme(
    primary = LightTextPrimary,
    onPrimary = Color.White,
    primaryContainer = LightSurfaceContainer,
    onPrimaryContainer = LightTextPrimary,
    secondary = Color(0xFF15803D),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCFCE7),
    onSecondaryContainer = Color(0xFF14532D),
    tertiary = Color(0xFFC2410C),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFEDD5),
    onTertiaryContainer = Color(0xFF7C2D12),
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceContainer,
    onSurfaceVariant = LightTextSecondary,
    surfaceContainerLowest = LightSurface,
    surfaceContainerLow = LightBackground,
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerHigh = LightSurfaceHigh,
    surfaceContainerHighest = Color(0xFFD5D5DC),
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    error = Color(0xFFDC2626),
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D)
)

val NucleoShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun NucleoChatTheme(
    themeMode: AppThemeMode = AppThemeMode.DARK,
    content: @Composable () -> Unit
) {
    val useDarkTheme = when (themeMode) {
        AppThemeMode.DARK -> true
        AppThemeMode.LIGHT -> false
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = if (useDarkTheme) AtomicDarkColorScheme else AtomicLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = NucleoShapes,
        content = content
    )
}
