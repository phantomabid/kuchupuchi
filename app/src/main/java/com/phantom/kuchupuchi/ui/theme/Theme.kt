package com.phantom.kuchupuchi.ui.theme

import android.os.Build
import androidx.annotation.Keep
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.phantom.kuchupuchi.config.PreferenceManager

@Keep
enum class AppTheme(val id: String, val displayName: String) {
    DEFAULT("DEFAULT", "System Default"),
    MIDNIGHT("MIDNIGHT", "Midnight Dark"),
    ROMANTIC_ROSE("ROMANTIC_ROSE", "Romantic Rose"),
    OCEAN_BREEZE("OCEAN_BREEZE", "Ocean Breeze"),
    SUNSET_GOLD("SUNSET_GOLD", "Sunset Gold");

    companion object {
        fun fromId(id: String?): AppTheme {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: DEFAULT
        }
    }
}

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80,
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40,
)

private val MidnightColorScheme = darkColorScheme(
    primary = Color(0xFF82B1FF),
    onPrimary = Color(0xFF00296B),
    primaryContainer = Color(0xFF003F88),
    onPrimaryContainer = Color(0xFFD6E4FF),
    secondary = Color(0xFF80D8FF),
    onSecondary = Color(0xFF00363A),
    secondaryContainer = Color(0xFF004D40),
    onSecondaryContainer = Color(0xFFB2EBF2),
    tertiary = Color(0xFFB388FF),
    onTertiary = Color(0xFF311B92),
    background = Color(0xFF0B0E14),
    onBackground = Color(0xFFE2E8F0),
    surface = Color(0xFF131722),
    onSurface = Color(0xFFE2E8F0),
    surfaceVariant = Color(0xFF1E2433),
    onSurfaceVariant = Color(0xFFCBD5E1),
    outline = Color(0xFF475569),
)

private val RomanticRoseColorScheme = darkColorScheme(
    primary = Color(0xFFFF80AB),
    onPrimary = Color(0xFF5C0029),
    primaryContainer = Color(0xFF880E4F),
    onPrimaryContainer = Color(0xFFFFD8E4),
    secondary = Color(0xFFFF4081),
    onSecondary = Color(0xFF4A001E),
    secondaryContainer = Color(0xFF4A148C),
    onSecondaryContainer = Color(0xFFF3E5F5),
    tertiary = Color(0xFFFFB2DD),
    onTertiary = Color(0xFF4E0038),
    background = Color(0xFF1A0C13),
    onBackground = Color(0xFFFCE4EC),
    surface = Color(0xFF26121D),
    onSurface = Color(0xFFFCE4EC),
    surfaceVariant = Color(0xFF3B1E2E),
    onSurfaceVariant = Color(0xFFF8BBD0),
    outline = Color(0xFF883A64),
)

private val OceanBreezeColorScheme = darkColorScheme(
    primary = Color(0xFF18FFFF),
    onPrimary = Color(0xFF004D40),
    primaryContainer = Color(0xFF006064),
    onPrimaryContainer = Color(0xFFE0F7FA),
    secondary = Color(0xFF64FFDA),
    onSecondary = Color(0xFF00372B),
    secondaryContainer = Color(0xFF004D40),
    onSecondaryContainer = Color(0xFFA7FFEB),
    tertiary = Color(0xFF40C4FF),
    onTertiary = Color(0xFF00325A),
    background = Color(0xFF08171E),
    onBackground = Color(0xFFE0F7FA),
    surface = Color(0xFF0F232D),
    onSurface = Color(0xFFE0F7FA),
    surfaceVariant = Color(0xFF173340),
    onSurfaceVariant = Color(0xFFB2EBF2),
    outline = Color(0xFF26A69A),
)

private val SunsetGoldColorScheme = darkColorScheme(
    primary = Color(0xFFFFD700),
    onPrimary = Color(0xFF422000),
    primaryContainer = Color(0xFFE65100),
    onPrimaryContainer = Color(0xFFFFF3E0),
    secondary = Color(0xFFFFAB40),
    onSecondary = Color(0xFF4E1D00),
    secondaryContainer = Color(0xFFBF360C),
    onSecondaryContainer = Color(0xFFFFD180),
    tertiary = Color(0xFFFF6D00),
    onTertiary = Color(0xFF4E1500),
    background = Color(0xFF1A120B),
    onBackground = Color(0xFFFFF8E1),
    surface = Color(0xFF261B10),
    onSurface = Color(0xFFFFF8E1),
    surfaceVariant = Color(0xFF3B2A1A),
    onSurfaceVariant = Color(0xFFFFE0B2),
    outline = Color(0xFFFB8C00),
)

@Composable
fun KuchuPuchiTheme(
    appTheme: String = PreferenceManager.DEFAULT_THEME,
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val themeEnum = AppTheme.fromId(appTheme)
    val colorScheme = when (themeEnum) {
        AppTheme.DEFAULT -> {
            if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val context = LocalContext.current
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } else if (darkTheme) {
                DarkColorScheme
            } else {
                LightColorScheme
            }
        }
        AppTheme.MIDNIGHT -> MidnightColorScheme
        AppTheme.ROMANTIC_ROSE -> RomanticRoseColorScheme
        AppTheme.OCEAN_BREEZE -> OceanBreezeColorScheme
        AppTheme.SUNSET_GOLD -> SunsetGoldColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
