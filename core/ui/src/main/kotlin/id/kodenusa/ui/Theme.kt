package id.kodenusa.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val JakartaSans = FontFamily(
    Font(R.font.plus_jakarta_sans, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans_semibold, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans_bold, FontWeight.Bold),
)

val JetBrainsMono = FontFamily(
    Font(R.font.jetbrains_mono, FontWeight.Normal),
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
)

private val KodenusaTypography = Typography().run {
    copy(
        displaySmall = displaySmall.copy(fontFamily = JakartaSans, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineMedium = headlineMedium.copy(fontFamily = JakartaSans, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp),
        headlineSmall = headlineSmall.copy(fontFamily = JakartaSans, fontWeight = FontWeight.SemiBold),
        titleLarge = titleLarge.copy(fontFamily = JakartaSans, fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontFamily = JakartaSans, fontWeight = FontWeight.SemiBold),
        bodyLarge = bodyLarge.copy(fontFamily = JakartaSans, lineHeight = 24.sp),
        bodyMedium = bodyMedium.copy(fontFamily = JakartaSans, lineHeight = 21.sp),
        bodySmall = bodySmall.copy(fontFamily = JakartaSans, lineHeight = 18.sp),
        labelLarge = labelLarge.copy(fontFamily = JakartaSans, fontWeight = FontWeight.SemiBold),
        labelMedium = labelMedium.copy(fontFamily = JakartaSans),
    )
}

/** Gaya untuk kode dan log. Ligatur dimatikan supaya `!=` dan `=>` tidak berubah bentuk. */
val CodeTextStyle = TextStyle(
    fontFamily = JetBrainsMono,
    fontSize = 13.sp,
    lineHeight = 20.sp,
    letterSpacing = 0.sp,
)

@Composable
fun KodenusaTheme(content: @Composable () -> Unit) {
    KodenusaTheme(GelapBengkel, content)
}

/**
 * Tema KodeNusa dari token semantik; tanpa argumen = tema gelap bengkel (perilaku lama).
 */
@Composable
fun KodenusaTheme(tokens: KodenusaTokens, content: @Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(LocalKodenusaTokens provides tokens) {
        MaterialTheme(
            colorScheme = skemaDari(tokens),
            typography = KodenusaTypography,
            content = content,
        )
    }
}

private fun skemaDari(t: KodenusaTokens) = androidx.compose.material3.ColorScheme(
    primary = t.accent,
    onPrimary = t.accentInk,
    primaryContainer = t.accent,
    onPrimaryContainer = t.accentInk,
    secondary = t.info,
    onSecondary = t.background,
    secondaryContainer = t.surfaceRaised,
    onSecondaryContainer = t.text,
    tertiary = t.ok,
    onTertiary = t.background,
    tertiaryContainer = t.surfaceRaised,
    onTertiaryContainer = t.text,
    background = t.background,
    onBackground = t.text,
    surface = t.surface,
    onSurface = t.text,
    surfaceVariant = t.surfaceRaised,
    onSurfaceVariant = t.textDim,
    surfaceTint = t.surfaceRaised,
    inversePrimary = t.accent,
    inverseSurface = t.text,
    inverseOnSurface = t.background,
    error = t.error,
    onError = t.background,
    errorContainer = t.surfaceRaised,
    onErrorContainer = t.error,
    outline = t.borderStrong,
    outlineVariant = t.border,
    scrim = t.background,
    surfaceBright = t.surfaceRaised,
    surfaceDim = t.background,
    surfaceContainer = t.surface,
    surfaceContainerHigh = t.surfaceRaised,
    surfaceContainerHighest = t.surfaceRaised,
    surfaceContainerLow = t.surface,
    surfaceContainerLowest = t.background,
)
