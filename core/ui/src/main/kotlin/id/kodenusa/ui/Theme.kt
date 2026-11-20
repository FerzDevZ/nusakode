package id.kodenusa.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * KodeNusa selalu gelap. Alasannya bukan gaya: layar AMOLED di HP murah menghemat baterai, dan
 * kontras teks gelap-terang lebih mudah dijaga pada panel TN yang ada di perangkat 1,5 juta.
 */
val KodenusaColors = darkColorScheme(
    primary = Color(KodenusaPalette.ACCENT),
    onPrimary = Color(KodenusaPalette.ACCENT_INK),
    secondary = Color(KodenusaPalette.INFO),
    onSecondary = Color(KodenusaPalette.BACKGROUND),
    background = Color(KodenusaPalette.BACKGROUND),
    onBackground = Color(KodenusaPalette.TEXT),
    surface = Color(KodenusaPalette.SURFACE),
    onSurface = Color(KodenusaPalette.TEXT),
    surfaceVariant = Color(KodenusaPalette.SURFACE_RAISED),
    onSurfaceVariant = Color(KodenusaPalette.TEXT_DIM),
    outline = Color(KodenusaPalette.BORDER_STRONG),
    outlineVariant = Color(KodenusaPalette.BORDER),
    error = Color(KodenusaPalette.ERROR),
    onError = Color(KodenusaPalette.BACKGROUND),
)

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
    MaterialTheme(
        colorScheme = KodenusaColors,
        typography = KodenusaTypography,
        content = content,
    )
}
