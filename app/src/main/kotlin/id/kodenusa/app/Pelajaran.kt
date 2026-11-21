package id.kodenusa.app

import android.content.Context
import java.io.File

/**
 * Mode belajar (docs/14 3.3): pelajaran per template, satu langkah = satu aksi kecil di editor.
 * Konten `pelajaran.md` ditulis manusia di folder template — template tanpa berkas itu tidak
 * punya pelajaran, dan itunya tidak dianggap galat (panel hanya tidak muncul).
 *
 * Format `pelajaran.md` sengaja sederhana dan bisa dibaca manusia tanpa alat:
 * setiap baris `## N. Judul` memulai langkah, baris setelahnya adalah isi langkah.
 * Parser menolak diam: berkas tanpa langkah sama sekali = null (pelajaran tidak ada),
 * bukan pelajaran kosong yang menampilkan panel kosong.
 */
data class Pelajaran(
    val templateId: String,
    val langkah: List<LangkahPelajaran>,
)

data class LangkahPelajaran(
    val nomor: Int,
    val judul: String,
    val isi: String,
)

object PelajaranParser {

    private val POLA_JUDUL = Regex("^##\\s*(\\d+)\\.\\s*(.+?)\\s*$")

    /** Parse isi `pelajaran.md`. Hasil null = tidak ada langkah sah sama sekali. */
    fun parse(templateId: String, isi: String): Pelajaran? {
        val langkah = ArrayList<LangkahPelajaran>()
        var nomor = 0
        var judul = ""
        val isiLangkah = StringBuilder()
        for (baris in isi.lines()) {
            val cocok = POLA_JUDUL.find(baris)
            if (cocok != null) {
                if (nomor > 0) simpan(langkah, nomor, judul, isiLangkah)
                nomor = cocok.groupValues[1].toInt()
                judul = cocok.groupValues[2]
                isiLangkah.clear()
            } else if (nomor > 0 && baris.isNotBlank()) {
                if (isiLangkah.isNotEmpty()) isiLangkah.append(' ')
                isiLangkah.append(baris.trim())
            }
        }
        if (nomor > 0) simpan(langkah, nomor, judul, isiLangkah)
        if (langkah.isEmpty()) return null
        return Pelajaran(templateId, langkah)
    }

    private fun simpan(langkah: MutableList<LangkahPelajaran>, nomor: Int, judul: String, isi: StringBuilder) {
        langkah += LangkahPelajaran(nomor, judul, isi.toString().trim())
    }
}

/** Membaca pelajaran dari aset template + menyimpan progres per projek. */
class PelajaranStore(private val aset: (String) -> String?, private val akarProgres: File) {

    /** Pelajaran untuk [templateId], atau null bila template itu tidak punya `pelajaran.md`. */
    fun muat(templateId: String?): Pelajaran? {
        if (templateId.isNullOrBlank()) return null
        val isi = runCatching { aset("templates/$templateId/pelajaran.md") }.getOrNull() ?: return null
        return PelajaranParser.parse(templateId, isi)
    }

    /** Langkah terakhir yang dicapai projek ini; 1 bila belum pernah belajar. */
    fun bacaProgres(idProjek: String): Int =
        runCatching { File(akarProgres, "$idProjek.txt").readText().trim().toIntOrNull()?.coerceIn(1, 99) }
            .getOrNull() ?: 1

    fun simpanProgres(idProjek: String, langkah: Int) {
        akarProgres.mkdirs()
        File(akarProgres, "$idProjek.txt").writeText(langkah.toString())
    }

    companion object {
        /** Progres disimpan di penyimpanan privat, bukan di dalam projek pengguna. */
        fun bawaan(context: Context) = PelajaranStore(
            aset = { jalur -> context.assets.open(jalur).bufferedReader().use { it.readText() } },
            akarProgres = File(context.filesDir, "pelajaran-progres"),
        )
    }
}
