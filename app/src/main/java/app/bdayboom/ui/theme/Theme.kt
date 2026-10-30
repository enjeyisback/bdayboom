package app.bdayboom.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import app.bdayboom.R

// Variable fonts (SIL OFL 1.1, licences in assets/licenses). One file each, weights picked by axis.
@OptIn(ExperimentalTextApi::class)
private fun variableFamily(res: Int, vararg weights: Int) = FontFamily(
    weights.map { w ->
        Font(res, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
    },
)

val Manrope = variableFamily(R.font.manrope, 400, 500, 600, 700, 800)
val PlusJakartaSans = variableFamily(R.font.plus_jakarta_sans, 400, 500, 600, 700, 800)

val LocalAppColors = staticCompositionLocalOf { LightColors }
val LocalAppType = staticCompositionLocalOf { AppTypography(PlusJakartaSans, Manrope) }
val LocalIsDark = staticCompositionLocalOf { false }

object AppTheme {
    val colors: AppColors
        @Composable @ReadOnlyComposable get() = LocalAppColors.current
    val type: AppTypography
        @Composable @ReadOnlyComposable get() = LocalAppType.current
    val isDark: Boolean
        @Composable @ReadOnlyComposable get() = LocalIsDark.current
}

/**
 * Light = "Cake & Confetti" (cream, coral). Dark = "Midnight Gala" (obsidian, amber).
 * Same fonts in both: Plus Jakarta Sans headings, Manrope body. Tokens map onto Material 3 so stock components pick them up too.
 */
@Composable
fun AppTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val c = if (dark) DarkColors else LightColors
    val t = remember { AppTypography(PlusJakartaSans, Manrope) }
    val base = if (dark) darkColorScheme() else lightColorScheme()
    val scheme = base.copy(
        primary = c.accent,
        onPrimary = c.onAccent,
        primaryContainer = c.accentSoft,
        onPrimaryContainer = c.onAccentSoft,
        secondary = c.celebrate,
        onSecondary = c.onCelebrate,
        secondaryContainer = c.celebrateSoft,
        onSecondaryContainer = c.onCelebrateSoft,
        tertiary = c.highlight,
        onTertiary = c.onHighlight,
        tertiaryContainer = c.highlightSoft,
        onTertiaryContainer = c.onHighlightSoft,
        background = c.bg,
        onBackground = c.text,
        surface = c.surface,
        onSurface = c.text,
        surfaceVariant = c.surface2,
        onSurfaceVariant = c.textMuted,
        surfaceTint = c.accent,
        surfaceContainerLowest = c.surface,
        surfaceContainerLow = c.surface,
        surfaceContainer = c.surface,
        surfaceContainerHigh = c.surface,
        surfaceContainerHighest = c.surface2,
        surfaceBright = c.surface,
        surfaceDim = c.bg,
        outline = c.borderInput,
        outlineVariant = c.border,
        error = c.danger,
        onError = c.onDanger,
        inverseSurface = c.text,
        inverseOnSurface = c.bg,
        inversePrimary = if (dark) LightColors.accent else DarkColors.accent,
        scrim = c.scrim,
    )
    val typography = Typography(
        displaySmall = t.display,
        headlineSmall = t.xl,
        titleLarge = t.lg,
        titleMedium = t.title,
        titleSmall = t.sm.copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = t.base,
        bodyMedium = t.sm,
        bodySmall = t.xs,
        labelLarge = t.sm.copy(fontWeight = FontWeight.SemiBold),
        labelMedium = t.label,
        labelSmall = t.label,
    )
    val shapes = Shapes(
        extraSmall = RoundedCornerShape(Radius.sm),
        small = RoundedCornerShape(Radius.sm),
        medium = RoundedCornerShape(Radius.md),
        large = RoundedCornerShape(Radius.lg),
        extraLarge = RoundedCornerShape(Radius.xl),
    )
    CompositionLocalProvider(LocalAppColors provides c, LocalAppType provides t, LocalIsDark provides dark) {
        MaterialTheme(colorScheme = scheme, typography = typography, shapes = shapes, content = content)
    }
}
