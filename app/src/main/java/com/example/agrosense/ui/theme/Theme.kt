package com.example.agrosense.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary              = Green800,
    onPrimary            = Color.White,
    primaryContainer     = Green200,
    onPrimaryContainer   = Green900,

    secondary            = Green700,
    onSecondary          = Color.White,
    secondaryContainer   = Green100,
    onSecondaryContainer = Green900,

    tertiary             = Teal400,
    onTertiary           = Color.White,
    tertiaryContainer    = Teal200,
    onTertiaryContainer  = Color(0xFF00251A),

    background           = Green50,
    onBackground         = Color(0xFF1A1C19),

    surface              = SurfaceLight,
    onSurface            = Color(0xFF1A1C19),
    surfaceVariant       = Green100,
    onSurfaceVariant     = Color(0xFF414941),

    error                = Red700,
    onError              = Color.White,
    errorContainer       = Red100,
    onErrorContainer     = Color(0xFF410002),

    outline              = Color(0xFF717971),
)

private val DarkColorScheme = darkColorScheme(
    primary              = Green200,
    onPrimary            = Green900,
    primaryContainer     = Green800,
    onPrimaryContainer   = Green100,

    secondary            = Green200,
    onSecondary          = Green700,
    secondaryContainer   = Green800,
    onSecondaryContainer = Green100,

    tertiary             = Teal200,
    onTertiary           = Color(0xFF00251A),
    tertiaryContainer    = Color(0xFF00504A),
    onTertiaryContainer  = Teal200,

    background           = BackgroundDark,
    onBackground         = Color(0xFFE2E3DC),

    surface              = SurfaceDark,
    onSurface            = Color(0xFFE2E3DC),
    surfaceVariant       = Color(0xFF414941),
    onSurfaceVariant     = Color(0xFFC1C9BF),

    error                = Color(0xFFFFB4AB),
    onError              = Color(0xFF690005),
    errorContainer       = Color(0xFF93000A),
    onErrorContainer     = Color(0xFFFFDAD6),

    outline              = Color(0xFF8B9389),
)

@Composable
fun AgroSenseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography  = Typography,
        content     = content
    )
}
