package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = EmeraldDeep,
    onPrimary = Color.White,
    primaryContainer = EmeraldContainer,
    onPrimaryContainer = OnEmeraldContainer,
    secondary = SaffronGold,
    onSecondary = Color.White,
    secondaryContainer = SaffronContainer,
    onSecondaryContainer = OnSaffronContainer,
    tertiary = WarmTeal,
    onTertiary = Color.White,
    tertiaryContainer = WarmTealContainer,
    onTertiaryContainer = OnWarmTealContainer,
    background = ParchmentBg,
    onBackground = InkDark,
    surface = ParchmentSurface,
    onSurface = InkDark,
    surfaceVariant = ParchmentSurfaceVariant,
    onSurfaceVariant = InkMuted,
    outline = ParchmentOutline,
    error = AlertCoral,
    onError = Color.White
)

private val DarkColorScheme = darkColorScheme(
    primary = NightPrimary,
    onPrimary = NightOnPrimary,
    primaryContainer = NightPrimaryContainer,
    onPrimaryContainer = NightOnPrimaryContainer,
    secondary = SaffronBright,
    onSecondary = OnSaffronContainer,
    secondaryContainer = NightSaffronContainer,
    onSecondaryContainer = NightOnSaffronContainer,
    tertiary = NightPrimary,
    onTertiary = NightOnPrimary,
    tertiaryContainer = NightEmeraldSurfaceHigh,
    onTertiaryContainer = NightInk,
    background = NightEmeraldBg,
    onBackground = NightInk,
    surface = NightEmeraldSurface,
    onSurface = NightInk,
    surfaceVariant = NightEmeraldSurfaceHigh,
    onSurfaceVariant = NightInkMuted,
    outline = NightEmeraldOutline,
    error = NightAlertCoral,
    onError = Color(0xFF420D06)
)

val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun NamazTimeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
