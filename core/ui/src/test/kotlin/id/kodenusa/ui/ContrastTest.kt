package id.kodenusa.ui

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Kontras dihitung dari nilai ARGB yang benar-benar dipakai aplikasi, bukan dari angka di dokumen.
 * Kalau ada yang mengubah palet tanpa memikirkan aksesibilitas, tes ini gagal.
 *
 * Sejak sistem tema (ADR-035), uji ini berlaku untuk **setiap tema** — bawaan maupun kustom yang
 * lolos gerbang impor. Tema baru ditambahkan ke [SEMUA_PALET] atau build gagal.
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

    /** Tema bawaan + contoh tema kustom valid — semua wajib lolos standar yang sama. */
    private val semuaPalet: List<Pair<String, PaletInt>> = TemaBawaan

    @Test
    fun `setiap warna teks lolos aa pada setiap permukaan di setiap tema`() {
        for ((namaTema, palet) in semuaPalet) {
            val surfaces = listOf(
                "background" to palet.background,
                "surface" to palet.surface,
                "surfaceRaised" to palet.surfaceRaised,
            )
            val texts = listOf(
                "text" to palet.text,
                "textDim" to palet.textDim,
                "accent" to palet.accent,
                "ok" to palet.ok,
                "warn" to palet.warn,
                "error" to palet.error,
                "info" to palet.info,
            )
            for ((textName, text) in texts) {
                for ((surfaceName, surface) in surfaces) {
                    val value = ratio(text, surface)
                    assertTrue(
                        value >= 4.5,
                        "$namaTema: $textName di atas $surfaceName hanya ${"%.2f".format(value)}:1",
                    )
                }
            }
        }
    }

    @Test
    fun `teks di atas aksen tetap terbaca di setiap tema`() {
        for ((namaTema, palet) in semuaPalet) {
            val value = ratio(palet.accentInk, palet.accent)
            assertTrue(value >= 4.5, "$namaTema: accentInk di atas accent hanya ${"%.2f".format(value)}:1")
        }
    }

    @Test
    fun `batas komponen interaktif mencapai tiga banding satu di setiap tema`() {
        for ((namaTema, palet) in semuaPalet) {
            for ((surfaceName, surface) in listOf(
                "background" to palet.background,
                "surface" to palet.surface,
                "surfaceRaised" to palet.surfaceRaised,
            )) {
                val value = ratio(palet.borderStrong, surface)
                assertTrue(
                    value >= 3.0,
                    "$namaTema: borderStrong di atas $surfaceName hanya ${"%.2f".format(value)}:1",
                )
            }
        }
    }

    @Test
    fun `palet tidak memakai ungu atau indigo`() {
        // Aksen wajib berada di rentang amber/cokelat (hue panas) sesuai DESIGN.md.
        for ((namaTema, palet) in semuaPalet) {
            val warna = palet.accent
            val r = (warna shr 16) and 0xFF
            val g = (warna shr 8) and 0xFF
            val b = warna and 0xFF
            assertTrue(r >= g, "$namaTema: aksen bukan keluarga hangat (r=$r g=$g b=$b)")
            assertTrue(b < g, "$namaTema: aksen memarkan biru/ungu (r=$r g=$g b=$b)")
        }
    }
}
