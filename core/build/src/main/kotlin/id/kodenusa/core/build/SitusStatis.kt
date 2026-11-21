package id.kodenusa.core.build

import java.io.File

/** Satu berkas yang siap dipublikasikan, dengan jalur relatif terhadap akar situs. */
data class BerkasSitus(val jalur: String, val ukuran: Long, val jenis: String)

data class HasilSitus(
    val folder: File,
    val berkas: List<BerkasSitus>,
    val diabaikan: List<String>,
) {
    val totalByte: Long get() = berkas.sumOf { it.ukuran }
    val jumlahHtml: Int get() = berkas.count { it.jenis == "html" }
    val adaSitus: Boolean get() = berkas.any { it.jalur == "index.html" }
}

/** Kegagalan membuat situs, selalu dengan kalimat yang bisa langsung ditampilkan ke pengguna. */
class SitusGagalException(pesan: String) : Exception(pesan)

/**
 * Mengubah projek menjadi **situs web murni** — tanpa APK, tanpa WebView.
 *
 * Hasilnya folder statis biasa (`index.html` + css/js/gambar) yang bisa:
 * - dibuka langsung di browser mana pun lewat `file://` atau server lokal,
 * - diunggah ke GitHub Pages / Netlify / hosting apa pun,
 * - dibagikan sebagai zip.
 *
 * Sumber diambil dari `web/` (bila ada, untuk projek web murni) atau dari `src/main/assets/`
 * (untuk projek yang selama ini dibungkus WebView). Dengan begitu satu projek bisa punya dua
 * keluaran: situs dan APK, tanpa menduplikasi berkas.
 */
object SitusStatis {

    const val ENTRY = "index.html"
    const val HASIL = "build/situs"

    /** Folder yang tidak pernah ikut dipublikasikan. */
    private val ABAIKAN = setOf(
        ".git", ".gradle", ".idea", "build", "dist", "gen", "node_modules", ".tools", "dex",
    )

    private val JENIS = mapOf(
        "html" to "html", "htm" to "html",
        "css" to "css", "js" to "js", "mjs" to "js", "json" to "data",
        "svg" to "gambar", "png" to "gambar", "jpg" to "gambar", "jpeg" to "gambar",
        "webp" to "gambar", "gif" to "gambar", "ico" to "gambar",
        "woff" to "font", "woff2" to "font", "ttf" to "font", "otf" to "font",
        "txt" to "teks", "md" to "teks", "xml" to "data", "webmanifest" to "data",
    )

    /** Sumber situs yang ditemukan di projek, urut sesuai prioritas. */
    fun sumber(akar: File): File? {
        val web = File(akar, "web")
        if (File(web, ENTRY).isFile) return web
        val aset = File(akar, "src/main/assets")
        if (File(aset, ENTRY).isFile) return aset
        return null
    }

    /**
     * Menyusun situs ke [tujuan] (bawaan `build/situs`).
     *
     * [dibersihkan] menghapus isi folder tujuan lebih dulu supaya sisa build lama tidak ikut terbit
     * — inilah kesalahan yang paling sering terjadi pada hosting statis.
     */
    fun susun(akar: File, tujuan: File = File(akar, HASIL), dibersihkan: Boolean = true): HasilSitus {
        val sumber = tentukanSumber(akar)
        validasiTujuan(sumber, tujuan)

        if (dibersihkan && tujuan.exists()) tujuan.deleteRecursively()
        tujuan.mkdirs()

        val dipakai = ArrayList<BerkasSitus>()
        val diabaikan = ArrayList<String>()
        sumber.walkTopDown()
            .onEnter { folder ->
                val lewati = folder != sumber && folder.name in ABAIKAN
                if (lewati) diabaikan += "folder ${relatif(sumber, folder)}/"
                !lewati
            }
            .filter { it.isFile }
            .sortedBy { relatif(sumber, it) }
            .forEach { berkas ->
                val nama = relatif(sumber, berkas)
                val jenis = jenisDari(nama)
                if (jenis == null) {
                    diabaikan += nama
                    return@forEach
                }
                val salinan = File(tujuan, nama)
                salinan.parentFile?.mkdirs()
                berkas.copyTo(salinan, overwrite = true)
                dipakai += BerkasSitus(jalur = nama, ukuran = salinan.length(), jenis = jenis)
            }

        val hasil = HasilSitus(folder = tujuan, berkas = dipakai, diabaikan = diabaikan)
        if (!hasil.adaSitus) {
            throw SitusGagalException(
                "Berkas $ENTRY harus ada di akar situs (web/index.html atau " +
                    "src/main/assets/index.html), bukan di subfolder.",
            )
        }
        return hasil
    }

    fun jenisDari(nama: String): String? = JENIS[nama.substringAfterLast('.', "").lowercase()]

    /** Menentukan folder sumber situs, atau menjelaskan dengan tepat apa yang kurang. */
    private fun tentukanSumber(akar: File): File {
        if (!akar.isDirectory) throw SitusGagalException("Folder projek tidak ditemukan.")
        sumber(akar)?.let { return it }
        val kandidat = listOf(File(akar, "web"), File(akar, "src/main/assets"))
        val adaHtml = kandidat.firstOrNull { folder ->
            folder.isDirectory && folder.walkTopDown().any { it.isFile && jenisDari(it.name) == "html" }
        }
        if (adaHtml != null) {
            throw SitusGagalException(
                "Berkas $ENTRY harus ada di akar situs (web/index.html atau " +
                    "src/main/assets/index.html). Sekarang ia berada di subfolder, " +
                    "sehingga alamat situs akan rusak.",
            )
        }
        throw SitusGagalException(
            "Projek ini belum punya situs. Tambahkan berkas web/index.html " +
                "(atau src/main/assets/index.html).",
        )
    }

    /** Folder hasil tidak boleh berada di dalam sumber, kalau tidak berkas akan menyalin dirinya sendiri. */
    private fun validasiTujuan(sumber: File, tujuan: File) {
        if (tujuan.canonicalPath.startsWith(sumber.canonicalPath + File.separator)) {
            throw SitusGagalException("Folder hasil tidak boleh berada di dalam folder sumber situs.")
        }
    }

    private fun relatif(akar: File, berkas: File): String =
        berkas.absolutePath.removePrefix(akar.absolutePath + File.separator).replace(File.separatorChar, '/')
}
