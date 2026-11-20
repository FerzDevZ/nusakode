package id.kodenusa.core.git

import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Repo GitHub yang dipakai untuk berbagi hasil build. */
data class RepoGitHub(val pemilik: String, val nama: String) {
    val alamat: String get() = "https://github.com/$pemilik/$nama"
}

/** Hasil unggah APK ke GitHub Release. */
data class HasilUnggah(val tautanUnduh: String, val ukuranByte: Long, val tag: String)

/** Galat yang sudah berbentuk kalimat Indonesia, supaya bisa langsung ditampilkan. */
class GitHubGagal(val pesan: String, val saran: String = "", sebab: Throwable? = null) :
    Exception(pesan, sebab)

/**
 * Membuat repo dan mengunggah APK ke GitHub Release **tanpa PC**.
 *
 * Kenapa di `core/git` dan bukan di aplikasi: ini satu-satunya jalur "bagikan hasil build ke orang
 * lain" yang tidak butuh server KodeNusa, dan ia harus bisa diuji sebagai kode biasa.
 *
 * Aturan yang disengaja:
 * - **Tidak memakai library HTTP pihak ketiga.** Hanya `HttpURLConnection`; APK sudah 32 MB, menambah
 *   OkHttp berarti menambah 1 MB lagi untuk sesuatu yang bisa ditulis 100 baris.
 * - Token tidak pernah ditulis ke berkas atau log; ia hanya ada di header satu permintaan.
 * - Rilis dibuat dengan **tag yang sama** dipakai ulang bila sudah ada, jadi mengirim ulang tidak
 *   membuat tumpukan rilis kosong.
 * - Nama aset di-encode: nama berkas Indonesia sering mengandung spasi.
 */
/** Menulis badan permintaan sedikit demi sedikit sambil melaporkan berapa bita yang sudah terkirim. */
object AliranUnggah {

    /** Ukuran potongan: besar cukup untuk jaringan cepat, kecil cukup untuk memberi kabar tiap detik. */
    const val POTONGAN = 64 * 1024

    /**
     * Menulis [badan] ke [keluar] per potongan, memanggil [lapor] dengan jumlah bita kumulatif.
     *
     * Kenapa tidak `write(badan)` sekali: APK 32 MB di jaringan seluler butuh puluhan detik tanpa
     * kabar apa pun — pengguna akan mengira aplikasi menggantung lalu menutupnya di tengah unggahan.
     */
    fun tulis(keluar: java.io.OutputStream, badan: ByteArray, lapor: ((Long) -> Unit)? = null) {
        var posisi = 0
        while (posisi < badan.size) {
            val panjang = minOf(POTONGAN, badan.size - posisi)
            keluar.write(badan, posisi, panjang)
            posisi += panjang
            lapor?.invoke(posisi.toLong())
        }
        keluar.flush()
    }
}

@Suppress("TooManyFunctions") // Satu berkas = satu topik: pembuatan repo, rilis, dan aset.
object GitHubRilis {

    private const val API = "https://api.github.com"
    private const val UNGGAH = "https://uploads.github.com"

    /** Mengurai `https://github.com/pemilik/repo(.git)` menjadi pasangan pemilik/repo. */
    fun repoDariAlamat(alamat: String): RepoGitHub? {
        val bersih = alamat.trim().removeSuffix("/").removeSuffix(".git")
        val bagian = bersih.removePrefix("https://").removePrefix("http://").split('/')
        if (bagian.size < 3 || !bagian[0].equals("github.com", ignoreCase = true)) return null
        val pemilik = bagian[1].trim()
        val repo = bagian[2].trim()
        if (pemilik.isBlank() || repo.isBlank()) return null
        return RepoGitHub(pemilik, repo)
    }

    /** Membuat repo baru (default privat) di akun pemilik token. */
    fun buatRepo(token: String, nama: String, privat: Boolean = true): RepoGitHub {
        if (!nama.matches(Regex("[A-Za-z0-9._-]{1,100}"))) {
            throw GitHubGagal(
                "Nama repo hanya boleh huruf, angka, titik, garis bawah, dan strip.",
                "Contoh: kodenusa-warung.",
            )
        }
        val badan = """{"name":"$nama","private":$privat,"auto_init":true}"""
        val jawab = kirim(token, "POST", "$API/user/repos", badan.toByteArray(), "application/json")
        val pemilik = ambilString(jawab, "owner", "login")
            ?: throw GitHubGagal("GitHub tidak mengembalikan pemilik repo.", "Coba lagi.")
        return RepoGitHub(pemilik, nama)
    }

    /** Menghapus repo — dipakai uji dan tombol "batalkan". */
    fun hapusRepo(token: String, repo: RepoGitHub) {
        kirim(token, "DELETE", "$API/repos/${repo.pemilik}/${repo.nama}", null, null, bolehGagal = true)
    }

    /**
     * Mengunggah [berkas] ke rilis bertag [tag]. Rilis dibuat bila belum ada.
     * Bila aset dengan nama sama sudah ada, nama diberi akhiran waktu supaya unggahan tidak ditolak.
     */
    fun unggahKeRilis(
        token: String,
        repo: RepoGitHub,
        tag: String,
        judul: String,
        catatan: String,
        berkas: File,
        onProgres: ((Long) -> Unit)? = null,
    ): HasilUnggah {
        if (!berkas.isFile) throw GitHubGagal("Berkas yang mau diunggah tidak ada: ${berkas.name}.")
        val rilisId = idRilis(token, repo, tag, judul, catatan)
        val namaAset = namaAsetAman(berkas.name)
        val url = "$UNGGAH/repos/${repo.pemilik}/${repo.nama}/releases/$rilisId/assets" +
            "?name=${java.net.URLEncoder.encode(namaAset, "UTF-8")}"
        val jawab = kirim(
            token,
            "POST",
            url,
            berkas.readBytes(),
            "application/octet-stream",
            bacaPanjang = 600_000,
            onProgres = onProgres,
        )
        val tautan = ambilString(jawab, "browser_download_url")
            ?: "https://github.com/${repo.pemilik}/${repo.nama}/releases/tag/$tag"
        return HasilUnggah(tautan, berkas.length(), tag)
    }

    private fun namaAsetAman(nama: String): String {
        val bersih = nama.replace(Regex("[^A-Za-z0-9._-]+"), "-").trim('-')
        return if (bersih.length >= 3) bersih else "KodeNusa.apk"
    }

    private fun idRilis(
        token: String,
        repo: RepoGitHub,
        tag: String,
        judul: String,
        catatan: String,
    ): Long {
        val cari = kirim(
            token,
            "GET",
            "$API/repos/${repo.pemilik}/${repo.nama}/releases/tags/$tag",
            null,
            null,
            bolehGagal = true,
        )
        cari?.let { teks -> ambilAngka(teks, "id")?.let { return it } }

        val badan = buildString {
            append("""{"tag_name":""").append(kutip(tag))
            append(""","name":""").append(kutip(judul))
            append(""","body":""").append(kutip(catatan))
            append(""","draft":false,"prerelease":false}""")
        }
        val jawab = kirim(
            token,
            "POST",
            "$API/repos/${repo.pemilik}/${repo.nama}/releases",
            badan.toByteArray(),
            "application/json",
        )
        return ambilAngka(jawab, "id")
            ?: throw GitHubGagal("Rilis tidak bisa dibuat di GitHub.", "Periksa apakah token punya izin \"repo\".")
    }

    private fun kutip(teks: String): String = buildString {
        append('"')
        for (huruf in teks) {
            when (huruf) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                else -> if (huruf >= ' ') append(huruf)
            }
        }
        append('"')
    }

    private fun kirim(
        token: String,
        metode: String,
        alamat: String,
        badan: ByteArray?,
        tipe: String?,
        bolehGagal: Boolean = false,
        bacaPanjang: Int = 120_000,
        onProgres: ((Long) -> Unit)? = null,
    ): String? {
        val koneksi = try {
            (URL(alamat).openConnection() as HttpURLConnection).apply {
                requestMethod = metode
                connectTimeout = 30_000
                readTimeout = if (bacaPanjang > 200_000) 600_000 else 60_000
                setRequestProperty("Authorization", "token $token")
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "KodeNusa")
                if (tipe != null) setRequestProperty("Content-Type", tipe)
                if (badan != null) {
                    doOutput = true
                    setFixedLengthStreamingMode(badan.size)
                }
            }
        } catch (gagal: java.io.IOException) {
            throw GitHubGagal(
                "Tidak bisa menghubungi GitHub (${gagal.javaClass.simpleName}). Periksa sambungan internet.",
                "Semua fitur lain tetap jalan tanpa jaringan; unggah APK memang butuh internet.",
                gagal,
            )
        }

        try {
            badan?.let { isi ->
                koneksi.outputStream.use { aliran -> AliranUnggah.tulis(aliran, isi, onProgres) }
            }
            val kode = koneksi.responseCode
            val aliran = if (kode in 200..299) koneksi.inputStream else koneksi.errorStream
            val teks = aliran?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()
            if (kode in 200..299) return teks
            if (bolehGagal && (kode == 404 || kode == 422)) return null
            throw when (kode) {
                401, 403 -> GitHubGagal(
                    "Token GitHub ditolak atau tidak punya izin tulis.",
                    "Buat token baru di GitHub → Settings → Developer settings → Tokens dengan centang \"repo\".",
                )
                404 -> GitHubGagal(
                    "Repo atau rilis tidak ditemukan di akun ini.",
                    "Pastikan alamat repo di menu GitHub sudah benar, mis. FerzDevZ/kodenusa-warung.",
                )
                422 -> GitHubGagal(
                    "GitHub menolak permintaan ini.",
                    "Biasanya nama repo sudah dipakai atau nama aset bentrok. Coba nama lain. " +
                        "Jawaban GitHub: ${teks.take(160)}",
                )
                else -> GitHubGagal(
                    "GitHub mengembalikan galat $kode.",
                    teks.take(160),
                )
            }
        } catch (gagal: GitHubGagal) {
            throw gagal
        } catch (gagal: java.io.IOException) {
            throw GitHubGagal(
                "Unggahan ke GitHub gagal di tengah jalan: ${gagal.message ?: gagal.javaClass.simpleName}",
                "Coba lagi; bila tetap gagal periksa sisa kuota internet Anda.",
                gagal,
            )
        } finally {
            koneksi.disconnect()
        }
    }

    /** Pembaca JSON sederhana: cukup untuk mengambil satu nilai bersarang, tanpa menambah library. */
    private fun ambilString(json: String?, kunci: String): String? {
        val teks = json ?: return null
        val pola = Regex("\"$kunci\"\\s*:\\s*\"([^\"]*)\"")
        return pola.find(teks)?.groupValues?.getOrNull(1)
    }

    private fun ambilString(json: String?, induk: String, kunci: String): String? {
        val teks = json ?: return null
        val blok = Regex("\"$induk\"\\s*:\\s*\\{([^}]*)}").find(teks)?.groupValues?.getOrNull(1) ?: return null
        return ambilString(blok, kunci)
    }

    private fun ambilAngka(json: String?, kunci: String): Long? {
        val teks = json ?: return null
        val pola = Regex("\"$kunci\"\\s*:\\s*(\\d+)")
        return pola.find(teks)?.groupValues?.getOrNull(1)?.toLongOrNull()
    }
}
