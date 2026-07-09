package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = ClinicalBlue,
    onPrimary = Color.White,
    secondary = DeepNavy,
    onSecondary = Color.White,
    tertiary = MintGreen,
    onTertiary = Color.White,
    background = OffWhite,
    onBackground = SlateDark,
    surface = PureWhite,
    onSurface = SlateDark,
    error = Color(0xFFEF4444),
    onError = Color.White,
    outline = BorderGray
)

private val DarkColorScheme = darkColorScheme(
    primary = ClinicalBlue,
    onPrimary = Color.White,
    secondary = DeepNavy,
    onSecondary = Color.White,
    tertiary = MintGreen,
    onTertiary = Color.White,
    background = OffWhite, // Enforce clean bright theme as requested
    onBackground = SlateDark,
    surface = PureWhite,
    onSurface = SlateDark,
    error = Color(0xFFEF4444),
    onError = Color.White,
    outline = BorderGray
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Disable dynamic colors to keep clinical brand exact
    content: @Composable () -> Unit
) {
    // We strictly use our custom pathology clinical color palette to match the user's design constraints
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
