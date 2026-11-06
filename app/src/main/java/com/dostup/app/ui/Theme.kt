package com.dostup.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme

/** Dark palette: AmneziaVPN-like near-black surfaces with ByeByeRKN's own cyan accent. */
object Palette {
    val bgBase = Color(0xFF101012)
    val bgSurface = Color(0xFF18181B)
    val bgSurfaceAlt = Color(0xFF1F1F23)
    val bgSheet = Color(0xFF313136)
    val bgPanel = Color(0xFF3F3F46)
    val textPrimary = Color(0xFFFAFAFA)
    val textSecondary = Color(0xFFE4E4E7)
    val textTertiary = Color(0xFFA1A1AA)
    val textQuaternary = Color(0xFF86868F)
    /** ByeByeRKN brand cyan: the connected ring, accent text and active controls. */
    val brand = Color(0xFF00D5FE)
    val brandDark = Color(0xFF009FC0)
    /** Brand washed onto the dark background, for selected chips and tabs. */
    val brandTint = Color(0xFF0A2A31)
    val danger = Color(0xFFF87171)
}

private val colors = darkColorScheme(
    primary = Palette.brand,
    onPrimary = Color(0xFF00191F),
    primaryVariant = Palette.brandDark,
    onPrimaryVariant = Palette.brand,
    disabledPrimary = Color(0xFF0C3640),
    disabledOnPrimary = Color(0xFF4F7C86),
    disabledPrimaryButton = Color(0xFF0C3640),
    disabledOnPrimaryButton = Color(0xFF4F7C86),
    disabledPrimarySlider = Color(0xFF12505E),
    primaryContainer = Palette.brandDark,
    onPrimaryContainer = Color.White,
    secondary = Palette.bgPanel,
    onSecondary = Palette.textPrimary,
    secondaryVariant = Palette.bgSheet,
    onSecondaryVariant = Palette.textSecondary,
    secondaryContainer = Palette.bgSurfaceAlt,
    onSecondaryContainer = Palette.textQuaternary,
    secondaryContainerVariant = Palette.bgSheet,
    onSecondaryContainerVariant = Palette.textTertiary,
    tertiaryContainer = Palette.brandTint,
    onTertiaryContainer = Palette.brand,
    tertiaryContainerVariant = Palette.bgSheet,
    background = Palette.bgBase,
    onBackground = Palette.textPrimary,
    onBackgroundVariant = Palette.textTertiary,
    surface = Palette.bgBase,
    onSurface = Palette.textPrimary,
    surfaceVariant = Palette.bgSurface,
    onSurfaceSecondary = Palette.textSecondary,
    onSurfaceVariantSummary = Palette.textTertiary,
    onSurfaceVariantActions = Palette.textQuaternary,
    disabledOnSurface = Color(0xFF52525B),
    surfaceContainer = Palette.bgSurface,
    onSurfaceContainer = Palette.textPrimary,
    onSurfaceContainerVariant = Palette.textQuaternary,
    surfaceContainerHigh = Palette.bgSurfaceAlt,
    onSurfaceContainerHigh = Palette.textTertiary,
    surfaceContainerHighest = Palette.bgSheet,
    onSurfaceContainerHighest = Palette.textPrimary,
    outline = Palette.bgPanel,
    dividerLine = Palette.bgSheet,
    sliderKeyPointForeground = Palette.brand,
)

@Composable
fun DostupTheme(content: @Composable () -> Unit) {
    MiuixTheme(colors = colors, content = content)
}
