package id.kodenusa.core.project

import java.io.File
import java.io.InputStream

/**
 * Menyalin berkas dari luar HP (Unduhan, Drive, kartu SD) ke dalam folder projek.
 *
 * Kenapa ada di `:core:project` dan bukan di layar: aturan keamanannya harus bisa diuji tanpa
 * perangkat. Yang dijaga di sini:
 *
 * - **Nama dari luar tidak dipercaya.** Nama tampilan dari pemilih berkas bisa berisi `/`,
 *   `../`, atau karakter kontrol. Semua divalidasi lewat [ProjectFiles.validasiNama]; nama yang
 *   tidak lolos **ditolak** dengan alasan, bukan disulap diam-diam.
 * - **Tidak ada jalan keluar dari projek.** Folder tujuan dihitung lewat
 *   [ProjectFiles.safeRelative].
 * - **Ukuran dibatasi.** HP 4 GB tidak boleh kehabisan ruang karena satu berkas 500 MB yang
 *   terpilih tanpa sengaja; batasnya [MAKS_BYTE] dan pesannya menyebut angka.
 * - **Nama bentrok tidak menghilangkan berkas lama.** Berkas baru diberi akhiran ` (2)`, ` (3)`.
 */
object BerkasMasuk {

    /** Batas satu berkas yang diimpor dari HP: 24 MB. Lebih dari ini ditolak dengan alasan jelas. */
    const val MAKS_BYTE = 24_000_000L

    sealed interface Hasil {
        data class Sukses(val file: File, val byte: Long) : Hasil
        data class Gagal(val pesan: String) : Hasil
    }

    /**
     * Menyalin [masuk] menjadi berkas baru bernama [namaTampilan] di [folderRelative].
     *
     * @return [Hasil.Sukses] berisi berkas hasil, atau [Hasil.Gagal] berisi kalimat bahasa Indonesia.
     */
    fun simpan(
        root: File,
        folderRelative: String,
        namaTampilan: String,
        masuk: InputStream,
    ): Hasil {
        val nama = namaBersih(namaTampilan) ?: return Hasil.Gagal(
            "Nama berkas \"$namaTampilan\" tidak bisa dipakai di projek. Ganti nama berkasnya dulu.",
        )
        val folder = folderTujuan(root, folderRelative)
            ?: return Hasil.Gagal("Folder tujuan tidak ada di dalam projek.")
        val tujuan = File(folder, nama)
        if (tujuan.exists()) {
            val unik = unik(folder, nama)
            return tulis(unik, masuk)
        }
        return tulis(tujuan, masuk)
    }

    /** Nama berkas yang aman, atau `null` bila nama dari pemilih berkas tidak layak dipakai. */    fun namaBersih(namaTampilan: String): String? {
        // Pemilih berkas kadang memberi nama lengkap dengan jalur ("Download/laporan.zip").
        val potong = namaTampilan.substringAfterLast('/').substringAfterLast('\\').trim()
        if (potong.isEmpty()) return null
        if (ProjectFiles.validasiNama(potong) != null) return null
        return potong
    }

    private fun folderTujuan(root: File, folderRelative: String): File? {
        val folder = if (folderRelative.isBlank()) {
            root
        } else {
            val aman = ProjectFiles.safeRelative(root, folderRelative) ?: return null
            File(root, aman)
        }
        if (!folder.isDirectory) folder.mkdirs()
        return folder.takeIf { it.isDirectory }
    }

    /**
     * Folder (relatif terhadap [akar]) tempat berkas baru ditaruh, dilihat dari berkas yang sedang
     * terbuka: berkas di `web/index.html` masuk ke `web`, berkas di akar masuk ke akar.
     *
     * Kenapa tidak memotong `File.path` langsung: `File.path` **selalu absolut**, sehingga
     * pemotongan menghasilkan `/data/user/0/.../web` — jalur absolut yang kemudian ditolak sebagai
     * "folder di luar projek". Itu sebabnya berkas dari HP dulu tidak pernah masuk.
     */
    fun folderDariAktif(akar: File, aktif: File?): String {
        val akarPath = akar.absolutePath.trimEnd('/')
        // `removePrefix` saja tidak cukup: jalur di luar projek (`/tmp/lain`) tidak terpotong,
        // lalu `trimStart('/')` membuatnya tampak relatif ("tmp/lain"). Awalan harus benar-benar
        // cocok, termasuk pemisahnya.
        val jalurAktif = aktif?.absolutePath
            ?.takeIf { it.startsWith("$akarPath/") }
            ?.removePrefix("$akarPath/")
            ?.takeIf { it.isNotEmpty() && !it.contains("..") }
            ?: return ""
        return jalurAktif.substringBeforeLast('/', "").takeIf { it.isNotBlank() } ?: ""
    }

    private fun unik(folder: File, nama: String): File {        val dasar = nama.substringBeforeLast('.', nama)
        val ekstensi = nama.substringAfterLast('.', "")
        val akhiran = if (ekstensi.isEmpty()) "" else ".$ekstensi"
        var urutan = 2
        while (urutan < 100) {
            val calon = File(folder, "$dasar ($urutan)$akhiran")
            if (!calon.exists()) return calon
            urutan += 1
        }
        return File(folder, "$dasar-${System.currentTimeMillis()}$akhiran")
    }

    private fun tulis(tujuan: File, masuk: InputStream): Hasil {
        var total = 0L
        return runCatching {
            masuk.use { sumber ->
                tujuan.outputStream().use { keluar ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val baca = sumber.read(buffer)
                        if (baca < 0) break
                        total += baca
                        if (total > MAKS_BYTE) {
                            keluar.close()
                            tujuan.delete()
                            return Hasil.Gagal(
                                "Berkas lebih dari ${MAKS_BYTE / 1_000_000} MB. Pilih berkas yang lebih kecil.",
                            )
                        }
                        keluar.write(buffer, 0, baca)
                    }
                }
            }
            Hasil.Sukses(tujuan, total)
        }.getOrElse {
            tujuan.delete()
            Hasil.Gagal("Berkas tidak bisa disalin: ${it.message ?: "penyebab tidak diketahui"}")
        }
    }
}
