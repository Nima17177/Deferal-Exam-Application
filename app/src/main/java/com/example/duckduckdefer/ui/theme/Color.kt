package com.example.duckduckdefer.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset

// Original colors (keeping for compatibility)
val Purple80 = Color(0xFF6658F5)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

val DuckYellow = Color(0xFFFFC107)
val DuckBlack = Color(0xFF1A1A1A)

// Brand oranges
val DuckOrange = Color(0xFFFF8C2A)
val DuckOrangeLight = Color(0xFFFFA94D)
val DuckOrangeDark = Color(0xFFE6761A)

// Accent
val DuckYellowAccent = Color(0xFFFFD166)

// Dark theme neutrals
val BlackPrimary = Color(0xFF121212)
val BlackSurface = Color(0xFF1E1E1E)
val BlackSurfaceVariant = Color(0xFF2A2A2A)
val BlackOutline = Color(0xFF444444)

// Light theme neutrals
val WhitePrimary = Color(0xFFF7F7F7)
val WhiteSurface = Color(0xFFFFFFFF)
val WhiteSurfaceVariant = Color(0xFFF2F2F2)
val GrayOutline = Color(0xFF7A7A7A)

// Premium warm light theme
val CreamYellow = Color(0xFFFFFDD3)

// Text colors
val TextLight = Color(0xFFF5F5F5)
val TextMutedLight = Color(0xFFBDBDBD)

val TextDark = Color(0xFF1A1A1A)
val TextMutedDark = Color(0xFF5F5F5F)

// Optional extra support colors
val DuckPrimaryContainerDark = Color(0xFF5A2E00)
val DuckOnPrimaryContainerDark = Color(0xFFFFDCC2)

val DuckPrimaryContainerLight = Color(0xFFFFDCC2)
val DuckOnPrimaryContainerLight = Color(0xFF341100)

// ── Space / Cosmic Voyage theme ──
val SpaceDeepVoid      = Color(0xFF0B0D1A)   // deepest background
val SpaceDarkNebula    = Color(0xFF111428)    // surface
val SpaceMidNebula     = Color(0xFF1A1E38)    // surfaceVariant
val SpaceStarlight     = Color(0xFFE8EAFF)    // onSurface / text
val SpaceStarlightDim  = Color(0xFF9A9DBF)    // muted text
val SpacePurple        = Color(0xFF9D6FFF)    // primary
val SpacePurpleDeep    = Color(0xFF7B4FD4)    // primary dark
val SpaceCyan          = Color(0xFF56E8FF)    // secondary accent
val SpaceNebulaPink    = Color(0xFFFF6BAE)    // tertiary accent
val SpaceOutline       = Color(0xFF3A3E5C)    // outline

object DuckGradients {

    val PrimaryGradient = Brush.linearGradient(
        colors = listOf(
            DuckOrangeLight,
            DuckOrange,
            DuckOrangeDark
        )
    )

    private val SpacePrimaryGradient = Brush.linearGradient(
        colors = listOf(
            SpacePurple,
            SpacePurpleDeep,
            SpaceNebulaPink
        )
    )

    private val NeonNightPrimaryGradient = Brush.linearGradient(
        colors = listOf(Color(0xFFBB86FC), Color(0xFF9B59FC), Color(0xFF7C3AFF))
    )

    private val OceanBreezePrimaryGradient = Brush.linearGradient(
        colors = listOf(Color(0xFF4FC3F7), Color(0xFF039BE5), Color(0xFF0277BD))
    )

    private val ForestGuardianPrimaryGradient = Brush.linearGradient(
        colors = listOf(Color(0xFF81C784), Color(0xFF4CAF50), Color(0xFF388E3C))
    )

    private val SunsetBoulevardPrimaryGradient = Brush.linearGradient(
        colors = listOf(Color(0xFFFF7043), Color(0xFFFF5722), Color(0xFFBF360C))
    )

    private val MidnightCherryPrimaryGradient = Brush.linearGradient(
        colors = listOf(Color(0xFFEF5350), Color(0xFFD32F2F), Color(0xFFB71C1C))
    )

    private val ArcticFrostPrimaryGradient = Brush.linearGradient(
        colors = listOf(Color(0xFF81D4FA), Color(0xFF4FC3F7), Color(0xFF0288D1))
    )

    private val RetrowavePrimaryGradient = Brush.linearGradient(
        colors = listOf(Color(0xFFFF006E), Color(0xFF3A86FF), Color(0xFFFFBE0B))
    )

    private val LavenderDreamsPrimaryGradient = Brush.linearGradient(
        colors = listOf(Color(0xFFCE93D8), Color(0xFFBA68C8), Color(0xFF7B1FA2))
    )

    private val DuckModePrimaryGradient = Brush.linearGradient(
        colors = listOf(Color(0xFFFFD600), Color(0xFFFF9100), Color(0xFFE65100))
    )

    /**
     * Theme-aware primary gradient. Use this instead of the static PrimaryGradient.
     */
    @Composable
    fun primaryGradient(): Brush {
        return when (LocalThemeId.current) {
            3 -> NeonNightPrimaryGradient
            4 -> OceanBreezePrimaryGradient
            5 -> ForestGuardianPrimaryGradient
            6 -> SpacePrimaryGradient
            7 -> SunsetBoulevardPrimaryGradient
            8 -> MidnightCherryPrimaryGradient
            9 -> ArcticFrostPrimaryGradient
            10 -> RetrowavePrimaryGradient
            11 -> LavenderDreamsPrimaryGradient
            12 -> DuckModePrimaryGradient
            else -> PrimaryGradient
        }
    }

    // ── Default hero / card gradients ──

    val DarkHeroGradient = Brush.linearGradient(
        colors = listOf(
            BlackSurface,
            BlackPrimary,
            DuckOrange
        )
    )

    val LightHeroGradient = Brush.linearGradient(
        colors = listOf(
            CreamYellow,
            Color(0xFFFFE8B0),
            DuckOrange
        )
    )

    val LightCardGradient = Brush.linearGradient(
        colors = listOf(
            CreamYellow,
            Color(0xFFFFF3D6),
            Color(0xFFFFE5B4)
        )
    )

    // ── Premium theme hero gradients ──

    private val NeonNightHeroGradient = Brush.linearGradient(
        colors = listOf(
            Color(0xFF0D0D14),
            Color(0xFF1A0033),
            Color(0xFFBB86FC)
        )
    )

    private val OceanBreezeHeroGradient = Brush.linearGradient(
        colors = listOf(
            Color(0xFF0A1A24),
            Color(0xFF0F2330),
            Color(0xFF4FC3F7)
        )
    )

    private val ForestGuardianHeroGradient = Brush.linearGradient(
        colors = listOf(
            Color(0xFF0D1A0D),
            Color(0xFF12231A),
            Color(0xFF81C784)
        )
    )

    private val SpaceHeroGradient = Brush.linearGradient(
        colors = listOf(
            SpaceDeepVoid,
            SpaceMidNebula,
            SpacePurple,
            SpaceNebulaPink
        )
    )

    private val SunsetBoulevardHeroGradient = Brush.linearGradient(
        colors = listOf(
            Color(0xFF1A0E08),
            Color(0xFF3E1500),
            Color(0xFFFF7043)
        )
    )

    private val MidnightCherryHeroGradient = Brush.linearGradient(
        colors = listOf(
            Color(0xFF1A0A0A),
            Color(0xFF3E0000),
            Color(0xFFEF5350)
        )
    )

    private val ArcticFrostHeroGradient = Brush.linearGradient(
        colors = listOf(
            Color(0xFFE1F5FE),
            Color(0xFFBBDEFB),
            Color(0xFF0288D1)
        )
    )

    private val RetrowaveHeroGradient = Brush.linearGradient(
        colors = listOf(
            Color(0xFF0D0D0D),
            Color(0xFF1A1A2E),
            Color(0xFFFF006E),
            Color(0xFF3A86FF)
        )
    )

    private val LavenderDreamsHeroGradient = Brush.linearGradient(
        colors = listOf(
            Color(0xFFFFF8E1),
            Color(0xFFF3E5F5),
            Color(0xFFCE93D8)
        )
    )

    private val DuckModeHeroGradient = Brush.linearGradient(
        colors = listOf(
            Color(0xFF1A1500),
            Color(0xFF3A2E00),
            Color(0xFFFFD600),
            Color(0xFFFF9100)
        )
    )

    // ── Premium theme card gradients ──

    private val NeonNightCardGradient = Brush.linearGradient(
        colors = listOf(
            Color(0xFF0D0D14),
            Color(0xFF170025),
            Color(0xFFBB86FC).copy(alpha = 0.35f)
        )
    )

    private val OceanBreezeCardGradient = Brush.linearGradient(
        colors = listOf(
            Color(0xFF0A1A24),
            Color(0xFF0E2636),
            Color(0xFF4FC3F7).copy(alpha = 0.30f)
        )
    )

    private val ForestGuardianCardGradient = Brush.linearGradient(
        colors = listOf(
            Color(0xFF0D1A0D),
            Color(0xFF142A18),
            Color(0xFF81C784).copy(alpha = 0.30f)
        )
    )

    private val SpaceCardGradient = Brush.linearGradient(
        colors = listOf(
            SpaceDarkNebula,
            SpaceMidNebula,
            SpacePurpleDeep.copy(alpha = 0.4f)
        )
    )

    private val SunsetBoulevardCardGradient = Brush.linearGradient(
        colors = listOf(
            Color(0xFF241610),
            Color(0xFF302018),
            Color(0xFFFF7043).copy(alpha = 0.30f)
        )
    )

    private val MidnightCherryCardGradient = Brush.linearGradient(
        colors = listOf(
            Color(0xFF241212),
            Color(0xFF301A1A),
            Color(0xFFEF5350).copy(alpha = 0.30f)
        )
    )

    private val ArcticFrostCardGradient = Brush.linearGradient(
        colors = listOf(
            Color(0xFFFFFFFF),
            Color(0xFFE8F4F8),
            Color(0xFF81D4FA).copy(alpha = 0.30f)
        )
    )

    private val RetrowaveCardGradient = Brush.linearGradient(
        colors = listOf(
            Color(0xFF1A1A2E),
            Color(0xFF252540),
            Color(0xFFFF006E).copy(alpha = 0.30f)
        )
    )

    private val LavenderDreamsCardGradient = Brush.linearGradient(
        colors = listOf(
            Color(0xFFFFFBF5),
            Color(0xFFF5EEF8),
            Color(0xFFCE93D8).copy(alpha = 0.30f)
        )
    )

    private val DuckModeCardGradient = Brush.linearGradient(
        colors = listOf(
            Color(0xFF241E00),
            Color(0xFF302800),
            Color(0xFFFFD600).copy(alpha = 0.35f)
        )
    )

    // ── Surface glow gradients ──

    private val DefaultSurfaceGlow = Brush.linearGradient(
        colors = listOf(
            Color(0x33FF8C2A),
            Color(0x221E1E1E),
            Color(0x112A2A2A)
        )
    )

    private val NeonNightSurfaceGlow = Brush.linearGradient(
        colors = listOf(
            Color(0xFFBB86FC).copy(alpha = 0.15f),
            Color(0xFF03DAC5).copy(alpha = 0.06f),
            Color(0xFF0D0D14).copy(alpha = 0.10f)
        )
    )

    private val OceanBreezeSurfaceGlow = Brush.linearGradient(
        colors = listOf(
            Color(0xFF4FC3F7).copy(alpha = 0.15f),
            Color(0xFF80CBC4).copy(alpha = 0.06f),
            Color(0xFF0A1A24).copy(alpha = 0.10f)
        )
    )

    private val ForestGuardianSurfaceGlow = Brush.linearGradient(
        colors = listOf(
            Color(0xFF81C784).copy(alpha = 0.15f),
            Color(0xFFA1887F).copy(alpha = 0.06f),
            Color(0xFF0D1A0D).copy(alpha = 0.10f)
        )
    )

    private val SpaceSurfaceGlow = Brush.linearGradient(
        colors = listOf(
            SpacePurple.copy(alpha = 0.15f),
            SpaceCyan.copy(alpha = 0.06f),
            SpaceDeepVoid.copy(alpha = 0.10f)
        )
    )

    private val SunsetBoulevardSurfaceGlow = Brush.linearGradient(
        colors = listOf(
            Color(0xFFFF7043).copy(alpha = 0.15f),
            Color(0xFFFFCC80).copy(alpha = 0.06f),
            Color(0xFF1A0E08).copy(alpha = 0.10f)
        )
    )

    private val MidnightCherrySurfaceGlow = Brush.linearGradient(
        colors = listOf(
            Color(0xFFEF5350).copy(alpha = 0.15f),
            Color(0xFFF48FB1).copy(alpha = 0.06f),
            Color(0xFF1A0A0A).copy(alpha = 0.10f)
        )
    )

    private val ArcticFrostSurfaceGlow = Brush.linearGradient(
        colors = listOf(
            Color(0xFF0288D1).copy(alpha = 0.10f),
            Color(0xFF81D4FA).copy(alpha = 0.06f),
            Color(0xFFE1F5FE).copy(alpha = 0.10f)
        )
    )

    private val RetrowaveSurfaceGlow = Brush.linearGradient(
        colors = listOf(
            Color(0xFFFF006E).copy(alpha = 0.15f),
            Color(0xFF3A86FF).copy(alpha = 0.06f),
            Color(0xFF0D0D0D).copy(alpha = 0.10f)
        )
    )

    private val LavenderDreamsSurfaceGlow = Brush.linearGradient(
        colors = listOf(
            Color(0xFF7B1FA2).copy(alpha = 0.10f),
            Color(0xFFCE93D8).copy(alpha = 0.06f),
            Color(0xFFFFF8E1).copy(alpha = 0.10f)
        )
    )

    private val DuckModeSurfaceGlow = Brush.linearGradient(
        colors = listOf(
            Color(0xFFFFD600).copy(alpha = 0.15f),
            Color(0xFFFF9100).copy(alpha = 0.06f),
            Color(0xFF1A1500).copy(alpha = 0.10f)
        )
    )

    /** Kept for backward-compat — prefer surfaceGlowGradient() composable */
    val SurfaceGlowGradient = DefaultSurfaceGlow

    /**
     * Theme-aware surface glow.
     */
    @Composable
    fun surfaceGlowGradient(): Brush {
        return when (LocalThemeId.current) {
            3 -> NeonNightSurfaceGlow
            4 -> OceanBreezeSurfaceGlow
            5 -> ForestGuardianSurfaceGlow
            6 -> SpaceSurfaceGlow
            7 -> SunsetBoulevardSurfaceGlow
            8 -> MidnightCherrySurfaceGlow
            9 -> ArcticFrostSurfaceGlow
            10 -> RetrowaveSurfaceGlow
            11 -> LavenderDreamsSurfaceGlow
            12 -> DuckModeSurfaceGlow
            else -> DefaultSurfaceGlow
        }
    }

    /**
     * Returns the appropriate hero gradient based on the current theme.
     */
    @Composable
    fun heroGradient(): Brush {
        return when (LocalThemeId.current) {
            3 -> NeonNightHeroGradient
            4 -> OceanBreezeHeroGradient
            5 -> ForestGuardianHeroGradient
            6 -> SpaceHeroGradient
            7 -> SunsetBoulevardHeroGradient
            8 -> MidnightCherryHeroGradient
            9 -> ArcticFrostHeroGradient
            10 -> RetrowaveHeroGradient
            11 -> LavenderDreamsHeroGradient
            12 -> DuckModeHeroGradient
            else -> if (LocalIsDarkTheme.current) DarkHeroGradient else LightHeroGradient
        }
    }

    /**
     * Returns a premium card gradient.
     */
    @Composable
    fun cardGradient(): Brush {
        return when (LocalThemeId.current) {
            3 -> NeonNightCardGradient
            4 -> OceanBreezeCardGradient
            5 -> ForestGuardianCardGradient
            6 -> SpaceCardGradient
            7 -> SunsetBoulevardCardGradient
            8 -> MidnightCherryCardGradient
            9 -> ArcticFrostCardGradient
            10 -> RetrowaveCardGradient
            11 -> LavenderDreamsCardGradient
            12 -> DuckModeCardGradient
            else -> if (LocalIsDarkTheme.current) DarkHeroGradient else LightCardGradient
        }
    }

    /**
     * Returns the appropriate content color for elements on top of the hero gradient.
     */
    @Composable
    fun heroContentColor(): Color {
        return when (LocalThemeId.current) {
            6 -> SpaceStarlight
            9, 11 -> TextDark // Arctic Frost & Lavender Dreams are light themes
            3, 4, 5, 7, 8, 10, 12 -> Color.White
            else -> if (LocalIsDarkTheme.current) Color.White else TextDark
        }
    }

    /**
     * Returns a muted/secondary content color for the hero gradient.
     */
    @Composable
    fun heroContentColorSecondary(): Color {
        return when (LocalThemeId.current) {
            6 -> SpaceStarlightDim
            9, 11 -> TextMutedDark // Arctic Frost & Lavender Dreams are light themes
            3, 4, 5, 7, 8, 10, 12 -> Color.White.copy(alpha = 0.7f)
            else -> if (LocalIsDarkTheme.current) Color.White.copy(alpha = 0.7f) else TextMutedDark
        }
    }
}
