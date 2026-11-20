package id.kodenusa.core.project

import java.io.File

/** Satu baris yang cocok saat mencari di seluruh projek. */
data class HasilCari(val jalur: String, val baris: Int, val teks: String)

/**
 * Pencarian teks di seluruh berkas projek — "cari dan cari tahu selayaknya coding".
 *
 * Kenapa bukan sekadar `grep` berkas aktif: bug biasanya muncul di berkas yang tidak sedang dibuka
 * (nama kolom di satu berkas, pemakaiannya di berkas lain). Pencarian ini:
 * - melewati folder yang tidak berisi kode pengguna (`build`, `.git`, `dex`, `node_modules`, …);
 * - hanya membaca berkas teks (daftar ekstensi putih, bukan tebakan `MIME`);
 * - membatasi ukuran berkas dan jumlah hasil supaya RAM 2 GB tidak habis karena berkas 20 MB.
 */
object ProjectSearch {

    const val MAKS_HASIL = 80
    const val MAKS_BYTE_BERKAS = 400_000L

    private const val MAKS_PANJANG_BARIS = 160

    private val FOLDER_DILEWATI = setOf(
        "build", ".git", ".gradle", ".idea", "dex", "gen", "classes", "node_modules", ".tools", "dist",
    )

    private val EKSTENSI_TEKS = setOf(
        "java", "kt", "kts", "xml", "html", "htm", "css", "js", "json", "md", "txt", "py",
        "sh", "yml", "yaml", "jsx", "ts", "tsx", "sql", "gradle", "properties", "toml", "cfg", "ini",
    )

    fun cari(akar: File, kata: String, maksHasil: Int = MAKS_HASIL): List<HasilCari> {
        val kueri = kata.trim()
        if (kueri.isEmpty()) return emptyList()
        val hasil = ArrayList<HasilCari>()
        telusuri(akar, akar, kueri.lowercase(), maksHasil.coerceIn(1, 500), hasil)
        return hasil
    }

    private fun telusuri(akar: File, folder: File, kueri: String, maks: Int, hasil: MutableList<HasilCari>) {
        if (hasil.size >= maks) return
        val anak = folder.listFiles()?.sortedBy { it.name.lowercase() } ?: return
        for (berkas in anak) {
            if (hasil.size >= maks) return
            if (berkas.isDirectory) {
                if (berkas.name !in FOLDER_DILEWATI) telusuri(akar, berkas, kueri, maks, hasil)
                continue
            }
            if (!layak(berkas)) continue
            val jalur = runCatching { berkas.relativeTo(akar).invariantSeparatorsPath }.getOrNull() ?: continue
            val baris = runCatching { berkas.useLines { it.toList() } }.getOrNull() ?: continue
            baris.forEachIndexed { indeks, isi ->
                if (hasil.size >= maks) return
                if (isi.lowercase().contains(kueri)) {
                    hasil += HasilCari(jalur, indeks + 1, isi.trim().take(MAKS_PANJANG_BARIS))
                }
            }
        }
    }

    private fun layak(berkas: File): Boolean {
        if (!berkas.isFile) return false
        if (berkas.length() > MAKS_BYTE_BERKAS) return false
        val ekstensi = berkas.extension.lowercase()
        return ekstensi in EKSTENSI_TEKS
    }
}
