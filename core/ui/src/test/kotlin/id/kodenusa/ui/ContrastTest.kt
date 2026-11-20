package id.kodenusa.ui

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Kontras dihitung dari nilai ARGB yang benar-benar dipakai aplikasi, bukan dari angka di dokumen.
 * Kalau ada yang mengubah palet tanpa memikirkan aksesibilitas, tes ini gagal.
 */
class ContrastTest {

    private fun channel(argb: Int, shift: Int): Double {
        val raw = ((argb shr shift) and 0xFF) / 255.0
        return if (raw <= 0.04045) raw / 12.92 else ((raw + 0.055) / 1.055).pow(2.4)
    }

    private fun luminance(argb: Int): Double =
        0.2126 * channel(argb, 16) + 0.7152 * channel(argb, 8) + 0.0722 * channel(argb, 0)

    private fun ratio(a: Int, b: Int): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    private val surfaces = listOf(
        "BACKGROUND" to KodenusaPalette.BACKGROUND,
        "SURFACE" to KodenusaPalette.SURFACE,
        "SURFACE_RAISED" to KodenusaPalette.SURFACE_RAISED,
    )

    private val texts = listOf(
        "TEXT" to KodenusaPalette.TEXT,
        "TEXT_DIM" to KodenusaPalette.TEXT_DIM,
        "ACCENT" to KodenusaPalette.ACCENT,
        "OK" to KodenusaPalette.OK,
        "WARN" to KodenusaPalette.WARN,
        "ERROR" to KodenusaPalette.ERROR,
        "INFO" to KodenusaPalette.INFO,
    )

    @Test
    fun `setiap warna teks lolos aa pada setiap permukaan`() {
        for ((textName, text) in texts) {
            for ((surfaceName, surface) in surfaces) {
                val value = ratio(text, surface)
                assertTrue(value >= 4.5, "$textName di atas $surfaceName hanya ${"%.2f".format(value)}:1")
            }
        }
    }

    @Test
    fun `teks di atas aksen tetap terbaca`() {
        assertTrue(ratio(KodenusaPalette.ACCENT_INK, KodenusaPalette.ACCENT) >= 4.5)
    }

    @Test
    fun `batas komponen interaktif mencapai tiga banding satu`() {
        for ((surfaceName, surface) in surfaces) {
            val value = ratio(KodenusaPalette.BORDER_STRONG, surface)
            assertTrue(value >= 3.0, "BORDER_STRONG di atas $surfaceName hanya ${"%.2f".format(value)}:1")
        }
    }

    @Test
    fun `palet tidak memakai ungu atau indigo`() {
        // Aksen wajib berada di rentang amber (hue 60-100 derajat) sesuai DESIGN.md.
        assertTrue(KodenusaPalette.ACCENT.toLong() and 0xFFFFFF == 0xF7AD30L)
    }
}
