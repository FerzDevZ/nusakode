package id.kodenusa.app

import id.kodenusa.ui.Kontras
import id.kodenusa.ui.PaletInt
import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Format tema kustom KodeNusa (`.kntema`) — JSON ≤ 4 KB berisi 13 warna.
 *
 * Gerbang kejujuran (ADR-036): tema yang kontrasnya di bawah WCAG AA **ditolak saat impor**
 * dengan pesan yang menyebut pasangan mana yang gagal. Tema buruk tidak boleh masuk hanya karena
 * pengguna mengimpornya. Parsing memakai kotlinx.serialization supaya bisa diuji di JVM biasa.
 */
object Kntema {

    const val EKSTENSI = ".kntema"
    const val MAKS_BYTE = 4_096
    const val VERSI = 1

    private const val BATAS_TEKS = 4.5
    private const val BATAS_BATAS = 3.0

    // encodeDefaults wajib true: tanpa ini "gelap":true (sama dengan default) tidak ditulis ke
    // berkas, dan tema terang kustom salah terbaca sebagai gelap saat berkasnya dibaca ulang.
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Serializable
    private data class WarnaJson(
        val background: String? = null,
        val surface: String? = null,
        val surfaceRaised: String? = null,
        val border: String? = null,
        val borderStrong: String? = null,
        val text: String? = null,
        val textDim: String? = null,
        val accent: String? = null,
        val accentInk: String? = null,
        val ok: String? = null,
        val warn: String? = null,
        val error: String? = null,
        val info: String? = null,
    )

    @Serializable
    private data class TemaJson(val versi: Int = 0, val nama: String = "", val gelap: Boolean = true, val warna: WarnaJson? = null)

    data class Tema(val nama: String, val gelap: Boolean, val palet: PaletInt, val berkas: File?)

    private class PaletKustom(private val nilai: Map<String, Int>) : PaletInt {
        override val background get() = nilai.getValue("background")
        override val surface get() = nilai.getValue("surface")
        override val surfaceRaised get() = nilai.getValue("surfaceRaised")
        override val border get() = nilai.getValue("border")
        override val borderStrong get() = nilai.getValue("borderStrong")
        override val text get() = nilai.getValue("text")
        override val textDim get() = nilai.getValue("textDim")
        override val accent get() = nilai.getValue("accent")
        override val accentInk get() = nilai.getValue("accentInk")
        override val ok get() = nilai.getValue("ok")
        override val warn get() = nilai.getValue("warn")
        override val error get() = nilai.getValue("error")
        override val info get() = nilai.getValue("info")
    }

    /** Mengurai teks JSON menjadi tema. Gagal = Result.failure dengan pesan bahasa Indonesia. */
    fun parse(teksJson: String): Result<Tema> = runCatching {
        if (teksJson.toByteArray().size > MAKS_BYTE) {
            throw IllegalArgumentException("Berkas tema lebih dari 4 KB.")
        }
        val objek = json.decodeFromString<TemaJson>(teksJson)
        if (objek.versi != VERSI) throw IllegalArgumentException("versi harus $VERSI.")
        if (objek.nama.isBlank() || objek.nama.length > 40) {
            throw IllegalArgumentException("Nama tema harus 1–40 karakter.")
        }
        val warna = objek.warna ?: throw IllegalArgumentException("Bagian \"warna\" tidak ada.")
        val nilai = mutableMapOf<String, Int>()
        for ((kunci, hex) in mapOf(
            "background" to warna.background, "surface" to warna.surface, "surfaceRaised" to warna.surfaceRaised,
            "border" to warna.border, "borderStrong" to warna.borderStrong, "text" to warna.text,
            "textDim" to warna.textDim, "accent" to warna.accent, "accentInk" to warna.accentInk,
            "ok" to warna.ok, "warn" to warna.warn, "error" to warna.error, "info" to warna.info,
        )) {
            if (hex == null || !hex.matches(Regex("^#[0-9a-fA-F]{6}$"))) {
                throw IllegalArgumentException("Warna \"$kunci\" bukan hex 6 digit (contoh #181E23).")
            }
            nilai[kunci] = (0xFF000000.toInt()) or hex.substring(1).toInt(16)
        }
        Tema(objek.nama, objek.gelap, PaletKustom(nilai), null)
    }

    /**
     * Mengembalikan daftar pasangan warna yang gagal kontras. Kosong = tema layak dipakai.
     * Semua tema — bawaan maupun kustom — diukur dengan standar yang sama.
     */
    fun pasanganGagal(palet: PaletInt): List<String> {
        val gagal = mutableListOf<String>()
        val permukaan = listOf(
            "background" to palet.background,
            "surface" to palet.surface,
            "surfaceRaised" to palet.surfaceRaised,
        )
        val teks = listOf(
            "text" to palet.text, "textDim" to palet.textDim, "accent" to palet.accent,
            "ok" to palet.ok, "warn" to palet.warn, "error" to palet.error, "info" to palet.info,
        )
        for ((namaTeks, warnaTeks) in teks) {
            for ((namaPermukaan, permukaanWarna) in permukaan) {
                val rasio = Kontras.rasio(warnaTeks, permukaanWarna)
                if (rasio < BATAS_TEKS) {
                    gagal += "Teks $namaTeks (#${hex(warnaTeks)}) terlalu redup di atas $namaPermukaan " +
                        "(#${hex(permukaanWarna)}) — ${"%.2f".format(rasio)}:1, butuh ≥ 4,5:1"
                }
            }
        }
        val rasioInk = Kontras.rasio(palet.accentInk, palet.accent)
        if (rasioInk < BATAS_TEKS) {
            gagal += "Teks accentInk (#${hex(palet.accentInk)}) di atas accent (#${hex(palet.accent)}) — " +
                "${"%.2f".format(rasioInk)}:1, butuh ≥ 4,5:1"
        }
        for ((namaPermukaan, permukaanWarna) in permukaan) {
            val rasio = Kontras.rasio(palet.borderStrong, permukaanWarna)
            if (rasio < BATAS_BATAS) {
                gagal += "Batas borderStrong (#${hex(palet.borderStrong)}) di atas $namaPermukaan " +
                    "(#${hex(permukaanWarna)}) — ${"%.2f".format(rasio)}:1, butuh ≥ 3:1"
            }
        }
        return gagal
    }

    private fun hex(argb: Int): String = "%06X".format(argb and 0xFFFFFF)

    fun serialisasi(tema: Tema): String {
        val p = tema.palet
        val warna = WarnaJson(
            background = "#${hex(p.background)}", surface = "#${hex(p.surface)}",
            surfaceRaised = "#${hex(p.surfaceRaised)}", border = "#${hex(p.border)}",
            borderStrong = "#${hex(p.borderStrong)}", text = "#${hex(p.text)}",
            textDim = "#${hex(p.textDim)}", accent = "#${hex(p.accent)}",
            accentInk = "#${hex(p.accentInk)}", ok = "#${hex(p.ok)}", warn = "#${hex(p.warn)}",
            error = "#${hex(p.error)}", info = "#${hex(p.info)}",
        )
        return json.encodeToString(
            TemaJson.serializer(),
            TemaJson(VERSI, tema.nama, tema.gelap, warna),
        )
    }

    /** Folder tema kustom di penyimpanan internal aplikasi. */
    fun folder(context: android.content.Context): File = File(context.filesDir, "tema").apply { mkdirs() }

    fun simpan(folder: File, tema: Tema): Result<File> = runCatching {
        val aman = tema.nama.replace(Regex("[^A-Za-z0-9_-]"), "_").ifBlank { "tema" }
        val berkas = File(folder, "$aman$EKSTENSI")
        berkas.writeText(serialisasi(tema))
        berkas
    }

    fun daftar(folder: File): List<Tema> =
        folder.listFiles { f -> f.name.endsWith(EKSTENSI) }
            ?.mapNotNull { f ->
                parse(runCatching { f.readText() }.getOrDefault("")).getOrNull()?.copy(berkas = f)
            }
            ?.sortedBy { it.nama.lowercase() }
            ?: emptyList()

    fun hapus(tema: Tema): Boolean = tema.berkas?.delete() ?: false

    /** Tema bawaan dibungkus supaya bisa diekspor dengan jalur yang sama seperti tema kustom. */
    fun dariBawaan(nama: String, palet: PaletInt): Tema = Tema(nama, nama.startsWith("Gelap"), palet, null)
}
