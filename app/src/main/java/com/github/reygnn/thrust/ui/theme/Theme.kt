package com.github.reygnn.thrust.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary            = ThrustBlue,
    onPrimary          = ThrustWhite,
    primaryContainer   = ThrustNavy,
    onPrimaryContainer = ThrustCyan,
    secondary          = ThrustGold,
    onSecondary        = ThrustDark,
    secondaryContainer = ThrustGray,
    onSecondaryContainer = ThrustWhite,
    tertiary           = ThrustGreen,
    onTertiary         = ThrustDark,
    background         = ThrustDark,
    onBackground       = ThrustWhite,
    surface            = ThrustNavy,
    onSurface          = ThrustWhite,
    surfaceVariant     = ThrustGray,
    onSurfaceVariant   = ThrustWhite,
    error              = ThrustRed,
    onError            = ThrustWhite,
)

@Composable
fun ThrustTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography  = ThrustTypography,
        content     = content,
    )
}
