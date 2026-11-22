package id.kodenusa.app

import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.zip.ZipFile

/**
 * Cadangan otomatis harian (docs/09 P2 #10): satu zip cadangan-semua per hari, dibuat sendiri
 * saat aplikasi dibuka, maksimal dua salinan yang paling baru — salinan lebih tua dihapus
 * sendiri supaya penyimpanan HP tidak penuh diam-diam.
 *
 * Aturan yang tidak boleh dilanggar:
 * - **Tidak pernah menulis ke dalam folder projek pengguna.** Cadangan hidup di penyimpanan
 *   privat aplikasi (`filesDir/cadangan-harian`), bukan di cache yang Android boleh bersihkan
 *   kapan saja — sebuah cadangan yang bisa hilang bukan cadangan.
 * - **Tidak pernah mengganggu pengguna.** Pekerjaan berjalan di benang belakang; kegagalan
 *   ditulis ke `status.txt`, tidak pernah memunculkan dialog.
 * - **Tidak membuat zip bila tidak ada projek.** Zip kosong menyesatkan.
 * - Isi zip tetap hasil [ProjectArchive.exportSemua], jadi aturan entri yang keluar folder dan
 *   folder build yang dilewati berlaku sama seperti cadangan manual.
 */
class CadanganOtomatis(
    private val tujuan: File,
    private val projectDirs: () -> List<File>,
) {

    /** Zip yang ada sekarang, terbaru dulu. */
    fun salinan(): List<File> =
        tujuan.listFiles().orEmpty().filter { it.isFile && it.name.endsWith(".zip") }
            .sortedByDescending { it.lastModified() }

    /** Benar bila belum ada zip dalam 24 jam terakhir. */
    fun perluCadangkan(sekarang: Long = System.currentTimeMillis()): Boolean {
        val terbaru = salinan().firstOrNull()?.lastModified() ?: 0L
        return sekarang - terbaru >= JEDA_MS
    }

    /** Membuat zip cadangan hari ini dan memangkas salinan lama.
     * Mengembalikan jumlah projek yang ikut, atau null bila tidak ada projek.
     */
    fun jalankan(sekarang: Long = System.currentTimeMillis()): Int? {
        val projek = projectDirs().filter { it.isDirectory && File(it, "kodenusa.json").isFile }
        if (projek.isEmpty()) {
            tulisStatus(sekarang, "Dilewati: belum ada projek untuk dicadangkan.")
            return null
        }
        val tanggal = DateTimeFormatter.ISO_LOCAL_DATE
            .withZone(ZoneOffset.UTC)
            .format(Instant.ofEpochMilli(sekarang))
        tujuan.mkdirs()
        val target = File(tujuan, "kodenusa-cadangan-$tanggal.zip")
        val jumlah = ProjectArchive.exportSemua(projek, target)
            .getOrElse { gagal ->
                target.delete()
                tulisStatus(sekarang, "Gagal: ${gagal.message.orEmpty()}")
                return null
            }
        // Waktu berkas mengikuti waktu cadangan yang diminta, bukan waktu tulis, supaya jeda
        // 24 jam terukur dari hari cadangan dibuat.
        target.setLastModified(sekarang)
        // Pangkas: sisakan dua zip terbaru, hapus sisanya.
        salinan().drop(MAKS_SALINAN).forEach { it.delete() }
        tulisStatus(sekarang, "Cadangan harian dibuat: ${target.name} ($jumlah projek).")
        return jumlah
    }

    /** Mencatat kegagalan pekerjaan latar ke status.txt — dipanggil dari MainActivity. */
    fun catatGagal(gagal: Throwable) {
        tulisStatus(System.currentTimeMillis(), "Gagal: ${gagal.message.orEmpty()}")
    }

    /** Isi satu zip cadangan (nama entri), dipakai uji dan pemulihan manual. */
    fun isiZip(zip: File): List<String> =
        ZipFile(zip).use { z -> z.entries().asSequence().map { it.name }.filter { !it.endsWith("/") }.toList() }

    private fun tulisStatus(sekarang: Long, pesan: String) {
        runCatching {
            tujuan.mkdirs()
            File(tujuan, "status.txt").appendText(
                "${DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(sekarang))} $pesan\n",
            )
        }
    }

    companion object {
        const val JEDA_MS: Long = 24L * 60 * 60 * 1000
        const val MAKS_SALINAN = 2

        fun bawaan(context: android.content.Context, workspaceRoot: File) = CadanganOtomatis(
            tujuan = File(context.filesDir, "cadangan-harian"),
            projectDirs = { workspaceRoot.listFiles().orEmpty().filter { it.isDirectory } },
        )
    }
}
