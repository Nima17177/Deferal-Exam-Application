package com.example.duckduckdefer.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * CompositionLocal that exposes whether the current theme is dark.
 * Any composable can read this via `LocalIsDarkTheme.current`.
 */
val LocalIsDarkTheme = staticCompositionLocalOf { false }

/**
 * CompositionLocal that exposes the current theme ID.
 * 0=System, 1=Light, 2=Dark, 3=NeonNight, 4=OceanBreeze, 5=ForestGuardian, 6=CosmicVoyage,
 * 7=SunsetBoulevard, 8=MidnightCherry, 9=ArcticFrost, 10=Retrowave, 11=LavenderDreams, 12=DuckMode
 */
val LocalThemeId = staticCompositionLocalOf { 0 }

private val DuckDarkColorScheme = darkColorScheme(
    primary = DuckOrange,
    onPrimary = Color(0xFF2B1600),

    primaryContainer = Color(0xFF5A2E00),
    onPrimaryContainer = Color(0xFFFFDCC2),

    secondary = DuckYellowAccent,
    onSecondary = Color(0xFF3A2C00),

    secondaryContainer = Color(0xFF5A4700),
    onSecondaryContainer = Color(0xFFFFE9A8),

    tertiary = DuckOrangeLight,
    onTertiary = Color(0xFF3A1B00),

    tertiaryContainer = Color(0xFF6B3600),
    onTertiaryContainer = Color(0xFFFFE2CC),

    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),

    background = BlackPrimary,
    onBackground = TextLight,

    surface = BlackSurface,
    onSurface = TextLight,

    surfaceVariant = BlackSurfaceVariant,
    onSurfaceVariant = TextMutedLight,

    outline = BlackOutline,
    outlineVariant = Color(0xFF303030),

    scrim = Color(0xFF000000),

    inverseSurface = Color(0xFFE8E2DA),
    inverseOnSurface = Color(0xFF2B2B2B),
    inversePrimary = Color(0xFF9C4F00)
)

private val DuckLightColorScheme = lightColorScheme(
    primary = Color(0xFF9C4F00),
    onPrimary = Color(0xFFFFFFFF),

    primaryContainer = Color(0xFFFFDCC2),
    onPrimaryContainer = Color(0xFF341100),

    secondary = Color(0xFF7A5E00),
    onSecondary = Color(0xFFFFFFFF),

    secondaryContainer = Color(0xFFFFE9A8),
    onSecondaryContainer = Color(0xFF241A00),

    tertiary = Color(0xFF8D4A00),
    onTertiary = Color(0xFFFFFFFF),

    tertiaryContainer = Color(0xFFFFDCC8),
    onTertiaryContainer = Color(0xFF2E1500),

    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),

    background = WhitePrimary,
    onBackground = TextDark,

    surface = WhiteSurface,
    onSurface = TextDark,

    surfaceVariant = WhiteSurfaceVariant,
    onSurfaceVariant = TextMutedDark,

    outline = GrayOutline,
    outlineVariant = Color(0xFFD0D0D0),

    scrim = Color(0xFF000000),

    inverseSurface = Color(0xFF2E2E2E),
    inverseOnSurface = Color(0xFFF5F5F5),
    inversePrimary = DuckOrangeLight
)

// ── Premium Theme: Neon Night (ID 3) ──
private val NeonNightColorScheme = darkColorScheme(
    primary = Color(0xFFBB86FC),
    onPrimary = Color(0xFF1A0033),
    primaryContainer = Color(0xFF3700B3),
    onPrimaryContainer = Color(0xFFE8DAFF),
    secondary = Color(0xFF03DAC5),
    onSecondary = Color(0xFF003330),
    secondaryContainer = Color(0xFF005049),
    onSecondaryContainer = Color(0xFFA0F5EA),
    tertiary = Color(0xFFCF6679),
    onTertiary = Color(0xFF3B0012),
    tertiaryContainer = Color(0xFF5C0023),
    onTertiaryContainer = Color(0xFFFFD9E0),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0D0D14),
    onBackground = Color(0xFFE6E0FF),
    surface = Color(0xFF141420),
    onSurface = Color(0xFFE6E0FF),
    surfaceVariant = Color(0xFF1E1E30),
    onSurfaceVariant = Color(0xFFBDB4D4),
    outline = Color(0xFF504878),
    outlineVariant = Color(0xFF2E2852)
)

// ── Premium Theme: Ocean Breeze (ID 4) ──
private val OceanBreezeColorScheme = darkColorScheme(
    primary = Color(0xFF4FC3F7),
    onPrimary = Color(0xFF003545),
    primaryContainer = Color(0xFF004D64),
    onPrimaryContainer = Color(0xFFBDE9FF),
    secondary = Color(0xFF80CBC4),
    onSecondary = Color(0xFF003733),
    secondaryContainer = Color(0xFF005049),
    onSecondaryContainer = Color(0xFFA0F5EA),
    tertiary = Color(0xFF81D4FA),
    onTertiary = Color(0xFF003546),
    tertiaryContainer = Color(0xFF004D65),
    onTertiaryContainer = Color(0xFFBDE9FF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0A1A24),
    onBackground = Color(0xFFDEF0F7),
    surface = Color(0xFF0F2330),
    onSurface = Color(0xFFDEF0F7),
    surfaceVariant = Color(0xFF172D3D),
    onSurfaceVariant = Color(0xFFABC4D4),
    outline = Color(0xFF3E5A6C),
    outlineVariant = Color(0xFF253D4D)
)

// ── Premium Theme: Forest Guardian (ID 5) ──
private val ForestGuardianColorScheme = darkColorScheme(
    primary = Color(0xFF81C784),
    onPrimary = Color(0xFF0B3A0E),
    primaryContainer = Color(0xFF1B5E20),
    onPrimaryContainer = Color(0xFFC8E6C9),
    secondary = Color(0xFFA1887F),
    onSecondary = Color(0xFF2E1A12),
    secondaryContainer = Color(0xFF4E342E),
    onSecondaryContainer = Color(0xFFD7CCC8),
    tertiary = Color(0xFFAED581),
    onTertiary = Color(0xFF1B3C07),
    tertiaryContainer = Color(0xFF33691E),
    onTertiaryContainer = Color(0xFFDCEDC8),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0D1A0D),
    onBackground = Color(0xFFE0EDE0),
    surface = Color(0xFF12231A),
    onSurface = Color(0xFFE0EDE0),
    surfaceVariant = Color(0xFF1C3026),
    onSurfaceVariant = Color(0xFFB0C4B1),
    outline = Color(0xFF4A6A50),
    outlineVariant = Color(0xFF2D4832)
)

// ── Premium Theme: Cosmic Voyage / Space (ID 6) ──
private val SpaceColorScheme = darkColorScheme(
    primary = SpacePurple,
    onPrimary = Color(0xFF1A0040),
    primaryContainer = Color(0xFF2D1B69),
    onPrimaryContainer = Color(0xFFE0D0FF),
    secondary = SpaceCyan,
    onSecondary = Color(0xFF003640),
    secondaryContainer = Color(0xFF00495A),
    onSecondaryContainer = Color(0xFFA8F0FF),
    tertiary = SpaceNebulaPink,
    onTertiary = Color(0xFF3E0020),
    tertiaryContainer = Color(0xFF5C0035),
    onTertiaryContainer = Color(0xFFFFD9E6),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = SpaceDeepVoid,
    onBackground = SpaceStarlight,
    surface = SpaceDarkNebula,
    onSurface = SpaceStarlight,
    surfaceVariant = SpaceMidNebula,
    onSurfaceVariant = SpaceStarlightDim,
    outline = SpaceOutline,
    outlineVariant = Color(0xFF252847)
)

// ── Premium Theme: Sunset Boulevard (ID 7) ──
private val SunsetBoulevardColorScheme = darkColorScheme(
    primary = Color(0xFFFF7043),
    onPrimary = Color(0xFF3E0B00),
    primaryContainer = Color(0xFFBF360C),
    onPrimaryContainer = Color(0xFFFFDBCF),
    secondary = Color(0xFFFFCC80),
    onSecondary = Color(0xFF3A2800),
    secondaryContainer = Color(0xFF5C4000),
    onSecondaryContainer = Color(0xFFFFE9C4),
    tertiary = Color(0xFFFF8A65),
    onTertiary = Color(0xFF3E1500),
    tertiaryContainer = Color(0xFF6A2C00),
    onTertiaryContainer = Color(0xFFFFDBC8),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF1A0E08),
    onBackground = Color(0xFFFFF0E6),
    surface = Color(0xFF241610),
    onSurface = Color(0xFFFFF0E6),
    surfaceVariant = Color(0xFF302018),
    onSurfaceVariant = Color(0xFFD4B8A4),
    outline = Color(0xFF7A5A42),
    outlineVariant = Color(0xFF4A352A)
)

// ── Premium Theme: Midnight Cherry (ID 8) ──
private val MidnightCherryColorScheme = darkColorScheme(
    primary = Color(0xFFEF5350),
    onPrimary = Color(0xFF3E0000),
    primaryContainer = Color(0xFFB71C1C),
    onPrimaryContainer = Color(0xFFFFDAD6),
    secondary = Color(0xFFF48FB1),
    onSecondary = Color(0xFF3E0018),
    secondaryContainer = Color(0xFF880E4F),
    onSecondaryContainer = Color(0xFFFFD9E5),
    tertiary = Color(0xFFCE93D8),
    onTertiary = Color(0xFF2E003A),
    tertiaryContainer = Color(0xFF4A0072),
    onTertiaryContainer = Color(0xFFF3DAFF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF1A0A0A),
    onBackground = Color(0xFFFFE8E5),
    surface = Color(0xFF241212),
    onSurface = Color(0xFFFFE8E5),
    surfaceVariant = Color(0xFF301A1A),
    onSurfaceVariant = Color(0xFFD4AAAA),
    outline = Color(0xFF7A4A4A),
    outlineVariant = Color(0xFF4A2D2D)
)

// ── Premium Theme: Arctic Frost (ID 9) ──
private val ArcticFrostColorScheme = lightColorScheme(
    primary = Color(0xFF0288D1),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFBBDEFB),
    onPrimaryContainer = Color(0xFF001C36),
    secondary = Color(0xFF78909C),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCFD8DC),
    onSecondaryContainer = Color(0xFF1C2A30),
    tertiary = Color(0xFF4FC3F7),
    onTertiary = Color(0xFF003545),
    tertiaryContainer = Color(0xFFB3E5FC),
    onTertiaryContainer = Color(0xFF001E2D),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF0F8FF),
    onBackground = Color(0xFF1A2A36),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A2A36),
    surfaceVariant = Color(0xFFE8F4F8),
    onSurfaceVariant = Color(0xFF4A6572),
    outline = Color(0xFF90A4AE),
    outlineVariant = Color(0xFFCFD8DC)
)

// ── Premium Theme: Retrowave (ID 10) ──
private val RetrowaveColorScheme = darkColorScheme(
    primary = Color(0xFFFF006E),
    onPrimary = Color(0xFF3E0018),
    primaryContainer = Color(0xFF8B0035),
    onPrimaryContainer = Color(0xFFFFD9E2),
    secondary = Color(0xFF3A86FF),
    onSecondary = Color(0xFF00204A),
    secondaryContainer = Color(0xFF003D8F),
    onSecondaryContainer = Color(0xFFD4E3FF),
    tertiary = Color(0xFFFFBE0B),
    onTertiary = Color(0xFF3A2C00),
    tertiaryContainer = Color(0xFF5C4500),
    onTertiaryContainer = Color(0xFFFFE9A8),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0D0D0D),
    onBackground = Color(0xFFF0E6FF),
    surface = Color(0xFF1A1A2E),
    onSurface = Color(0xFFF0E6FF),
    surfaceVariant = Color(0xFF252540),
    onSurfaceVariant = Color(0xFFBDB4D4),
    outline = Color(0xFF504878),
    outlineVariant = Color(0xFF2E2852)
)

// ── Premium Theme: Lavender Dreams (ID 11) ──
private val LavenderDreamsColorScheme = lightColorScheme(
    primary = Color(0xFF7B1FA2),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE1BEE7),
    onPrimaryContainer = Color(0xFF2E003A),
    secondary = Color(0xFFCE93D8),
    onSecondary = Color(0xFF3E0048),
    secondaryContainer = Color(0xFFF3E5F5),
    onSecondaryContainer = Color(0xFF2E003A),
    tertiary = Color(0xFFBA68C8),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF3E5F5),
    onTertiaryContainer = Color(0xFF2E003A),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFFF8E1),
    onBackground = Color(0xFF2E1A3A),
    surface = Color(0xFFFFFBF5),
    onSurface = Color(0xFF2E1A3A),
    surfaceVariant = Color(0xFFF5EEF8),
    onSurfaceVariant = Color(0xFF5C4668),
    outline = Color(0xFF9C8AA8),
    outlineVariant = Color(0xFFD7C9E0)
)

// ── Premium Theme: Duck Mode 🦆 (ID 12) ──
private val DuckModeColorScheme = darkColorScheme(
    primary = Color(0xFFFFD600),
    onPrimary = Color(0xFF3A2E00),
    primaryContainer = Color(0xFF5C4A00),
    onPrimaryContainer = Color(0xFFFFEF80),
    secondary = Color(0xFFFF9100),
    onSecondary = Color(0xFF3A1D00),
    secondaryContainer = Color(0xFF5C3000),
    onSecondaryContainer = Color(0xFFFFDBA8),
    tertiary = Color(0xFFFFFFFF),
    onTertiary = Color(0xFF3A3A3A),
    tertiaryContainer = Color(0xFF5C5C5C),
    onTertiaryContainer = Color(0xFFF5F5F5),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF1A1500),
    onBackground = Color(0xFFFFF8E1),
    surface = Color(0xFF241E00),
    onSurface = Color(0xFFFFF8E1),
    surfaceVariant = Color(0xFF302800),
    onSurfaceVariant = Color(0xFFD4C890),
    outline = Color(0xFF7A6D3A),
    outlineVariant = Color(0xFF4A4020)
)

@Composable
fun DuckDuckDeferTheme(
    themePreference: Int = 0, // 0 = System, 1 = Light, 2 = Dark, 3+ = Premium
    fontSizePreference: Int = 1, // 0 = Small, 1 = Medium, 2 = Large
    darkTheme: Boolean = when (themePreference) {
        1 -> false
        2 -> true
        9, 11 -> false // Arctic Frost & Lavender Dreams are light-based
        else -> if (themePreference >= 3) true else isSystemInDarkTheme() // Premium themes are dark-based
    },
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    // Premium themes override the normal dark/light selection
    val isPremiumTheme = themePreference >= 3

    val colorScheme = when {
        isPremiumTheme -> when (themePreference) {
            3 -> NeonNightColorScheme
            4 -> OceanBreezeColorScheme
            5 -> ForestGuardianColorScheme
            6 -> SpaceColorScheme
            7 -> SunsetBoulevardColorScheme
            8 -> MidnightCherryColorScheme
            9 -> ArcticFrostColorScheme
            10 -> RetrowaveColorScheme
            11 -> LavenderDreamsColorScheme
            12 -> DuckModeColorScheme
            else -> DuckDarkColorScheme
        }
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DuckDarkColorScheme
        else -> DuckLightColorScheme
    }

    // Adjust font sizes based on preference
    val fontScale = when (fontSizePreference) {
        0 -> 0.85f
        2 -> 1.15f
        else -> 1.0f
    }

    val customTypography = Typography(
        displayLarge = Typography.displayLarge.copy(fontSize = Typography.displayLarge.fontSize * fontScale),
        displayMedium = Typography.displayMedium.copy(fontSize = Typography.displayMedium.fontSize * fontScale),
        displaySmall = Typography.displaySmall.copy(fontSize = Typography.displaySmall.fontSize * fontScale),
        headlineLarge = Typography.headlineLarge.copy(fontSize = Typography.headlineLarge.fontSize * fontScale),
        headlineMedium = Typography.headlineMedium.copy(fontSize = Typography.headlineMedium.fontSize * fontScale),
        headlineSmall = Typography.headlineSmall.copy(fontSize = Typography.headlineSmall.fontSize * fontScale),
        titleLarge = Typography.titleLarge.copy(fontSize = Typography.titleLarge.fontSize * fontScale),
        titleMedium = Typography.titleMedium.copy(fontSize = Typography.titleMedium.fontSize * fontScale),
        titleSmall = Typography.titleSmall.copy(fontSize = Typography.titleSmall.fontSize * fontScale),
        bodyLarge = Typography.bodyLarge.copy(fontSize = Typography.bodyLarge.fontSize * fontScale),
        bodyMedium = Typography.bodyMedium.copy(fontSize = Typography.bodyMedium.fontSize * fontScale),
        bodySmall = Typography.bodySmall.copy(fontSize = Typography.bodySmall.fontSize * fontScale),
        labelLarge = Typography.labelLarge.copy(fontSize = Typography.labelLarge.fontSize * fontScale),
        labelMedium = Typography.labelMedium.copy(fontSize = Typography.labelMedium.fontSize * fontScale),
        labelSmall = Typography.labelSmall.copy(fontSize = Typography.labelSmall.fontSize * fontScale)
    )

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    CompositionLocalProvider(
        LocalIsDarkTheme provides darkTheme,
        LocalThemeId provides themePreference
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = customTypography,
            content = content
        )
    }
}
