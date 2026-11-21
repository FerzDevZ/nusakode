package id.kodenusa.core.project

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/**
 * Galeri template komunitas (docs/14 4.4): daftar template diambil dari satu berkas JSON di
 * GitHub raw, tiap template diunduh sebagai `.zip` lalu diverifikasi ukuran + SHA-256 SEBELUM
 * dibuka. Tanpa jaringan = daftar kosong dan pemanggil harus mengatakannya jujur di layar.
 *
 * Tidak ada server KodeNusa (ADR-001): hanya URL GitHub raw yang dipilih pengguna di setelan.
 *
 * Keamanan: isi zip dibaca ke memori lewat pembaca yang menolak jalur absolut dan `..`
 * (zip-slip), lalu disimpan lewat `simpan` milik pemanggil — modul ini tidak menyentuh disk
 * selain berkas zip sementara.
 */
object GaleriTemplate {

    /** Satu entri di `galeri.json` milik repo komunitas. */
    @Serializable
    data class GaleriItem(
        val id: String,
        val nama: String,
        val deskripsi: String = "",
        /** SHA-256 heksadesimal huruf kecil dari berkas zip. Wajib. */
        val sha256: String,
        /** Ukuran zip dalam byte. Wajib, dicek sebelum SHA supaya unduhan palsu cepat ditolak. */
        val ukuran: Long,
        /** Tautan raw zip, mis. https://raw.githubusercontent.com/.../template/kasir-mini.zip */
        val url: String,
    )

    private val json = Json { ignoreUnknownKeys = true }

    /** Unduh dan baca daftar galeri. Gagal jaringan/JSON = Result.gagal dengan pesan jelas. */
    fun unduhIndex(url: String): Result<List<GaleriItem>> = runCatching {
        val isi = unduh(url, ukuranMaks = 64_000L)
        val daftar = json.decodeFromString<List<GaleriItem>>(isi.decodeToString())
        require(daftar.isNotEmpty()) { "Daftar galeri kosong" }
        daftar
    }

    /**
     * Unduh zip template, verifikasi ukuran lalu SHA-256, dan baca isinya menjadi peta
     * jalur → isi teks. Kembalikan peta itu untuk disimpan lewat gerbang pratinjau (ADR-038).
     */
    fun unduhTemplate(item: GaleriItem): Result<Map<String, String>> = runCatching {
        val isi = unduh(item.url, ukuranMaks = maks(item.ukuran))
        require(isi.size.toLong() == item.ukuran) {
            "Ukuran unduhan (${isi.size} B) tidak cocok dengan daftar (${item.ukuran} B)"
        }
        val sha = sha256(isi)
        require(sha == item.sha256.lowercase()) {
            "SHA-256 tidak cocok: daftar $item.sha256, unduhan $sha"
        }
        bacaZip(isi)
    }

    /** Baca isi zip ke memori dengan proteksi zip-slip dan batas jumlah berkas. */
    fun bacaZip(isi: ByteArray): Map<String, String> {
        val hasil = LinkedHashMap<String, String>()
        ZipInputStream(isi.inputStream()).use { zip ->
            while (true) {
                val entri = zip.nextEntry ?: break
                val jalur = entri.name
                require(jalur.isNotEmpty() && !jalur.startsWith("/") && !jalur.contains("..") && !jalur.contains(':')) {
                    "Jalur tidak aman di dalam zip: ${entri.name}"
                }
                if (!entri.isDirectory) {
                    require(hasil.size < 40) { "Zip memuat terlalu banyak berkas" }
                    val teks = zip.readBytes().decodeToString()
                    require(teks.length <= 512_000) { "Berkas terlalu besar: $jalur" }
                    hasil[jalur] = teks
                }
            }
        }
        require(hasil.isNotEmpty()) { "Zip kosong" }
        return hasil
    }

    /** SHA-256 heksadesimal huruf kecil. */
    fun sha256(isi: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(isi).joinToString("") { "%02x".format(it) }

    /** Batas unduhan: ukuran tertulis + sedikit kelonggaran, hard cap 4 MB. */
    private fun maks(diminta: Long): Long = if (diminta in 1..4_000_000L) diminta else 4_000_000L

    private fun unduh(url: String, ukuranMaks: Long): ByteArray {
        val koneksi = URL(url).openConnection() as HttpURLConnection
        koneksi.connectTimeout = 15_000
        koneksi.readTimeout = 30_000
        koneksi.setRequestProperty("User-Agent", "KodeNusa")
        try {
            require(koneksi.responseCode == 200) { "Unduhan gagal: HTTP ${koneksi.responseCode}" }
            val isi = koneksi.inputStream.use { aliran -> aliran.readBytes() }
            require(isi.size <= ukuranMaks) { "Unduhan terlalu besar (${isi.size} B)" }
            return isi
        } finally {
            koneksi.disconnect()
        }
    }
}
