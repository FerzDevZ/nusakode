package id.kodenusa.core.project

import java.io.File

/** Satu baris yang cocok saat mencari di seluruh projek. */
data class HasilCari(val jalur: String, val baris: Int, val teks: String)

/** Berkas yang isinya benar-benar berubah karena penggantian. */
data class HasilGanti(val jalur: String, val jumlah: Int)

/** Ringkasan penggantian: berkas mana saja yang berubah, dan berapa potongan diganti. */
data class RingkasanGanti(val berkas: List<HasilGanti>) {
    val total: Int get() = berkas.sumOf { it.jumlah }
    val file: Int get() = berkas.size
}

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
        "build", ".git", ".gradle", ".idea", "dex", "gen", "classes", "node_modules", ".tools", "dist", ".riwayat",
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

    /**
     * Ganti semua kemunculan [kata] dengan [pengganti] di seluruh berkas projek.
     *
     * Aturan yang disengaja:
     * - **Pencocokan peka huruf besar/kecil.** Mengganti tanpa peduli huruf akan mengubah nama
     *   kelas (`Produk` → `barang`) dan nama tipe di tempat yang tidak dimaksud pengguna.
     * - Berkas yang tidak memuat [kata] **tidak disentuh** — waktu ubah dan risiko rusak nol.
     * - Ditulis lewat berkas sementara lalu diganti nama, supaya listrik mati di tengah jalan
     *   tidak meninggalkan berkas setengah isi.
     * - Berkas di luar akar projek (symlink) ditolak, sama seperti [ProjectFiles].
     * - Kata kosong ditolak; tanpa itu "ganti semua" akan menyisipkan teks di antara setiap huruf.
     */
    fun ganti(akar: File, kata: String, pengganti: String, maksBerkas: Int = 400): RingkasanGanti {
        if (kata.isEmpty()) return RingkasanGanti(emptyList())
        if (kata == pengganti) return RingkasanGanti(emptyList())
        val akarBersih = runCatching { akar.canonicalFile }.getOrDefault(akar)
        val berubah = ArrayList<HasilGanti>()
        gantiDi(akarBersih, akarBersih, kata, pengganti, maksBerkas, berubah)
        return RingkasanGanti(berubah)
    }

    private fun gantiDi(
        akar: File,
        folder: File,
        kata: String,
        pengganti: String,
        maks: Int,
        hasil: MutableList<HasilGanti>,
    ) {
        if (hasil.size >= maks) return
        val anak = folder.listFiles()?.sortedBy { it.name.lowercase() } ?: return
        for (berkas in anak) {
            if (hasil.size >= maks) return
            if (berkas.isDirectory) {
                if (berkas.name !in FOLDER_DILEWATI) gantiDi(akar, berkas, kata, pengganti, maks, hasil)
                continue
            }
            if (!layak(berkas)) continue
            val bersih = runCatching { berkas.canonicalFile }.getOrNull() ?: continue
            if (!bersih.path.startsWith(akar.path + File.separator)) continue
            val lama = runCatching { berkas.readText() }.getOrNull() ?: continue
            val jumlah = hitung(lama, kata)
            if (jumlah == 0) continue
            val baru = lama.replace(kata, pengganti)
            if (!tulisAman(berkas, baru)) continue
            val jalur = runCatching { berkas.relativeTo(akar).invariantSeparatorsPath }.getOrDefault(berkas.name)
            hasil += HasilGanti(jalur, jumlah)
        }
    }

    private fun hitung(isi: String, kata: String): Int {
        var posisi = isi.indexOf(kata)
        var jumlah = 0
        while (posisi >= 0) {
            jumlah++
            posisi = isi.indexOf(kata, posisi + kata.length)
        }
        return jumlah
    }

    private fun tulisAman(berkas: File, isi: String): Boolean = runCatching {
        val sementara = File(berkas.parentFile, ".${berkas.name}.ganti")
        sementara.writeText(isi)
        if (!sementara.renameTo(berkas)) {
            berkas.writeText(isi)
            sementara.delete()
        }
        true
    }.getOrDefault(false)
}
