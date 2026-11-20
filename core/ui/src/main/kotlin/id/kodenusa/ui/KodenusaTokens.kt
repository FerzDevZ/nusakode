package id.kodenusa.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Palet per tema dalam ARGB Int — bentuk ini yang diuji kontrasnya di JVM biasa (ContrastTest)
 * tanpa emulator. Setiap tema **wajib** lolos uji yang sama (teks/latar >= 4,5:1, batas komponen
 * >= 3:1) sebelum boleh dipakai.
 */
interface PaletInt {
    val background: Int
    val surface: Int
    val surfaceRaised: Int
    val border: Int
    val borderStrong: Int
    val text: Int
    val textDim: Int
    val accent: Int
    val accentInk: Int
    val ok: Int
    val warn: Int
    val error: Int
    val info: Int
}

/** Palet lama (0.9.x) — kini menjadi tema "Gelap bengkel", nilai byte-per-byte tidak berubah. */
object PaletGelapBengkel : PaletInt {
    override val background = 0xFF0E1217.toInt()
    override val surface = 0xFF181E23.toInt()
    override val surfaceRaised = 0xFF232A30.toInt()
    override val border = 0xFF484E54.toInt()
    override val borderStrong = 0xFF6F757B.toInt()
    override val text = 0xFFEBEFF4.toInt()
    override val textDim = 0xFFA0A5AB.toInt()
    override val accent = 0xFFF7AD30.toInt()
    override val accentInk = 0xFF281600.toInt()
    override val ok = 0xFF61D19A.toInt()
    override val warn = 0xFFE7C952.toInt()
    override val error = 0xFFF75D59.toInt()
    override val info = 0xFF6AB8E4.toInt()
}

/** Hitam murni untuk layar AMOLED; border dinaikkan agar garis tetap terbaca di hitam. */
object PaletGelapAmoled : PaletInt {
    override val background = 0xFF000000.toInt()
    override val surface = 0xFF0B0F13.toInt()
    override val surfaceRaised = 0xFF161C22.toInt()
    override val border = 0xFF4A5158.toInt()
    override val borderStrong = 0xFF82898F.toInt()
    override val text = 0xFFEBEFF4.toInt()
    override val textDim = 0xFFA0A5AB.toInt()
    override val accent = 0xFFF7AD30.toInt()
    override val accentInk = 0xFF281600.toInt()
    override val ok = 0xFF61D19A.toInt()
    override val warn = 0xFFE7C952.toInt()
    override val error = 0xFFF75D59.toInt()
    override val info = 0xFF6AB8E4.toInt()
}

/**
 * Terang "kertas". Bukan inversi otomatis: aksen amber digelapkan (oklch ~0.60) dan teks
 * digelapkan agar setiap pasangan tetap >= 4,5:1 di latar terang — diukur oleh ContrastTest.
 */
object PaletTerangKertas : PaletInt {
    override val background = 0xFFF7F8FA.toInt()
    override val surface = 0xFFEFF1F4.toInt()
    override val surfaceRaised = 0xFFE3E6EA.toInt()
    override val border = 0xFFC2C7CE.toInt()
    override val borderStrong = 0xFF7C838B.toInt()
    override val text = 0xFF1B2127.toInt()
    override val textDim = 0xFF565E67.toInt()
    override val accent = 0xFF8F5400.toInt()
    override val accentInk = 0xFFFFF4E0.toInt()
    override val ok = 0xFF176E45.toInt()
    override val warn = 0xFF6F5A0C.toInt()
    override val error = 0xFFA8302A.toInt()
    override val info = 0xFF28648F.toInt()
}

/** Terang ekstrem untuk mata di luar ruangan; aksen cokelat tua, teks hampir hitam. */
object PaletTerangTinggi : PaletInt {
    override val background = 0xFFFFFFFF.toInt()
    override val surface = 0xFFF1F1F1.toInt()
    override val surfaceRaised = 0xFFE4E4E4.toInt()
    override val border = 0xFF9E9E9E.toInt()
    override val borderStrong = 0xFF4D4D4D.toInt()
    override val text = 0xFF111111.toInt()
    override val textDim = 0xFF3A3A3A.toInt()
    override val accent = 0xFF7A4300.toInt()
    override val accentInk = 0xFFFFFFFF.toInt()
    override val ok = 0xFF0A5C39.toInt()
    override val warn = 0xFF6B5300.toInt()
    override val error = 0xFFA32019.toInt()
    override val info = 0xFF0B4F86.toInt()
}

/** Daftar tema bawaan, urutannya juga urutan tampil di layar Tema. */
val TemaBawaan: List<Pair<String, PaletInt>> = listOf(
    "Gelap bengkel" to PaletGelapBengkel,
    "Gelap AMOLED" to PaletGelapAmoled,
    "Terang kertas" to PaletTerangKertas,
    "Terang kontras tinggi" to PaletTerangTinggi,
)

/** Token warna siap-Compose; satu-satunya cara komponen KodeNusa mengambil warna. */
data class KodenusaTokens(
    val background: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val border: Color,
    val borderStrong: Color,
    val text: Color,
    val textDim: Color,
    val accent: Color,
    val accentInk: Color,
    val ok: Color,
    val warn: Color,
    val error: Color,
    val info: Color,
    /** true = tema gelap (dipakai untuk status bar dan warna konsol editor). */
    val gelap: Boolean,
    /** Palet asal dalam ARGB — dibutuhkan dunia View (editor kode) yang tidak bisa baca Compose. */
    val paletInt: PaletInt,
) {
    companion object {
        fun dari(palet: PaletInt, gelap: Boolean): KodenusaTokens = KodenusaTokens(
            background = Color(palet.background),
            surface = Color(palet.surface),
            surfaceRaised = Color(palet.surfaceRaised),
            border = Color(palet.border),
            borderStrong = Color(palet.borderStrong),
            text = Color(palet.text),
            textDim = Color(palet.textDim),
            accent = Color(palet.accent),
            accentInk = Color(palet.accentInk),
            ok = Color(palet.ok),
            warn = Color(palet.warn),
            error = Color(palet.error),
            info = Color(palet.info),
            gelap = gelap,
            paletInt = palet,
        )
    }
}

val GelapBengkel = KodenusaTokens.dari(PaletGelapBengkel, gelap = true)

/** CompositionLocal sumber kebenaran warna. Default = tema gelap supaya pratinjau aman. */
val LocalKodenusaTokens = staticCompositionLocalOf { GelapBengkel }

/**
 * Akses warna tema dari komponen: `Kode.accent`, `Kode.surface`, dst. Ada supaya pemanggilan di
 * layar tetap satu kata, dan supaya migrasi dari `KodenusaPalette.X` bisa mekanis.
 */
object Kode {
    val background: Color @Composable get() = LocalKodenusaTokens.current.background
    val surface: Color @Composable get() = LocalKodenusaTokens.current.surface
    val surfaceRaised: Color @Composable get() = LocalKodenusaTokens.current.surfaceRaised
    val border: Color @Composable get() = LocalKodenusaTokens.current.border
    val borderStrong: Color @Composable get() = LocalKodenusaTokens.current.borderStrong
    val text: Color @Composable get() = LocalKodenusaTokens.current.text
    val textDim: Color @Composable get() = LocalKodenusaTokens.current.textDim
    val accent: Color @Composable get() = LocalKodenusaTokens.current.accent
    val accentInk: Color @Composable get() = LocalKodenusaTokens.current.accentInk
    val ok: Color @Composable get() = LocalKodenusaTokens.current.ok
    val warn: Color @Composable get() = LocalKodenusaTokens.current.warn
    val error: Color @Composable get() = LocalKodenusaTokens.current.error
    val info: Color @Composable get() = LocalKodenusaTokens.current.info
}
