package id.kodenusa.app

import id.kodenusa.core.project.ProjectFiles
import java.io.File

/**
 * Riwayat berkas (docs/14 2.8): sebelum berkas ditimpa, isi lamanya disalin ke
 * `<projek>/.riwayat/<jalur>/<n>.bak` — maksimal [MAKS] versi, yang paling tua dibuang (LRU).
 *
 * Kenapa ini ada: undo hanya hidup selama editor terbuka. Pengguna yang menimpa berkas lalu
 * menutup projek kehilangan versi lamanya selamanya — dan bagi pemula, kehilangan kerja berarti
 * berhenti total. Folder `.riwayat` dikecualikan dari build, pohon berkas, dan pencarian
 * (sama seperti `build/`).
 */
class RiwayatBerkas(private val akarProjek: File) {

    data class Versi(val nomor: Int, val waktu: Long, val ukuran: Long)

    /** Mencatat isi lama [berkas] sebelum ditimpa. Gagal diam-diam: riwayat tidak boleh memblokir simpan. */
    fun catat(berkas: File, isiLama: String) {
        runCatching {
            val jalur = jalurAman(berkas) ?: return
            val folder = folderVersi(jalur)
            folder.mkdirs()
            val nomor = nomorBerikutnya(folder)
            File(folder, "$nomor.bak").writeText(isiLama)
            buangTerlama(folder)
        }
    }

    /** Daftar versi tersimpan untuk [berkas], terbaru dulu. */
    fun daftar(berkas: File): List<Versi> {
        val jalur = jalurAman(berkas) ?: return emptyList()
        return folderVersi(jalur).listFiles { f -> f.name.endsWith(".bak") }
            .orEmpty()
            .mapNotNull { f ->
                val nomor = f.name.removeSuffix(".bak").toIntOrNull() ?: return@mapNotNull null
                Versi(nomor, f.lastModified(), f.length())
            }
            .sortedByDescending { it.nomor }
    }

    /** Membaca isi versi ke-[nomor]; null bila tidak ada. */
    fun isi(berkas: File, nomor: Int): String? {
        val jalur = jalurAman(berkas) ?: return null
        val f = File(folderVersi(jalur), "$nomor.bak")
        return if (f.isFile) runCatching { f.readText() }.getOrNull() else null
    }

    /** Jalur relatif yang aman; null untuk berkas di luar projek atau jalur berbahaya (`../`). */
    private fun jalurAman(berkas: File): String? =
        runCatching { ProjectFiles.safeRelative(akarProjek, berkas.relativeTo(akarProjek).invariantSeparatorsPath) }
            .getOrNull()

    private fun folderVersi(jalur: String): File = File(File(akarProjek, FOLDER), jalur)

    private fun nomorBerikutnya(folder: File): Int =
        (folder.listFiles { f -> f.name.endsWith(".bak") }.orEmpty()
            .mapNotNull { it.name.removeSuffix(".bak").toIntOrNull() }
            .maxOrNull() ?: 0) + 1

    private fun buangTerlama(folder: File) {
        // LRU berdasar nomor (urutan penulisan), bukan mtime: granularitas waktu berkas bisa
        // kasar di beberapa sistem berkas sehingga urutan waktu tidak andal.
        val urutNaik = folder.listFiles { f -> f.name.endsWith(".bak") }.orEmpty()
            .sortedBy { it.name.removeSuffix(".bak").toIntOrNull() ?: 0 }
        // Simpan MAKS terbaru (nomor terbesar); sisanya dibuang.
        urutNaik.take(maxOf(0, urutNaik.size - MAKS)).forEach { it.delete() }
    }

    companion object {
        const val FOLDER = ".riwayat"
        const val MAKS = 10
    }
}
