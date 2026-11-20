package id.kodenusa.app

import android.content.Context
import id.kodenusa.core.git.RepoGit

/**
 * Menyimpan alamat repo GitHub dan token akses — **terpisah dari folder projek**.
 *
 * Kenapa terpisah: token tidak boleh ikut ter-export saat projek di-zip atau di-push ke git.
 * Kalau setelan ini disimpan di dalam projek, satu kali "Bagikan zip" cukup untuk membocorkan akun.
 *
 * Token tidak pernah ditulis ke `.git/config` (lihat ADR-024), tidak pernah masuk log, dan
 * **disandikan** di penyimpanan lewat [KunciAman] (Android Keystore). Alamat repo tidak disandikan
 * karena bukan rahasia dan perlu ditampilkan ulang kepada pengguna.
 */
class GitSettingsStore(
    context: Context,
    private val sandi: Penyandi = KunciAman,
) {

    private val prefs = context.getSharedPreferences("git", Context.MODE_PRIVATE)

    /** Alamat repo yang sudah dirapikan, atau kosong bila belum diisi. */
    fun alamatRepo(): String = prefs.getString(KEY_ALAMAT, "").orEmpty()

    /**
     * Token tersandi, dengan migrasi otomatis dari nilai teks biasa versi lama: sekali dibaca, nilai
     * lama langsung ditulis ulang dalam bentuk tersandi.
     */
    fun token(): String {
        val mentah = prefs.getString(KEY_TOKEN, "").orEmpty()
        if (mentah.isEmpty()) return ""
        if (mentah.startsWith(KunciAman.AWALAN)) return sandi.dekripsi(mentah)
        val asli = sandi.dekripsi(mentah)
        if (asli.isNotEmpty()) prefs.edit().putString(KEY_TOKEN, sandi.enkripsi(asli)).apply()
        return asli
    }

    fun nama(): String = prefs.getString(KEY_NAMA, "").orEmpty().ifBlank { "Pengguna KodeNusa" }

    fun email(): String = prefs.getString(KEY_EMAIL, "").orEmpty().ifBlank { "kodenusa@localhost" }

    fun cabang(): String = prefs.getString(KEY_CABANG, CABANG_BAWAAN).orEmpty().ifBlank { CABANG_BAWAAN }

    fun simpan(alamat: String, token: String, cabang: String = CABANG_BAWAAN) {
        val tokenBersih = token.trim()
        prefs.edit()
            .putString(KEY_ALAMAT, alamat.trim())
            .putString(KEY_TOKEN, if (tokenBersih.isEmpty()) "" else sandi.enkripsi(tokenBersih))
            .putString(KEY_CABANG, cabang.trim().ifBlank { CABANG_BAWAAN })
            .apply()
    }

    fun simpanIdentitas(nama: String, email: String) {
        prefs.edit().putString(KEY_NAMA, nama.trim()).putString(KEY_EMAIL, email.trim()).apply()
    }

    fun hapus() {
        prefs.edit().clear().apply()
    }

    /**
     * Alasan tombol terbit masih terkunci — dikembalikan sebagai kalimat supaya UI tidak perlu
     * menebak, dan supaya tidak ada tombol mati tanpa penjelasan.
     */
    fun alasanBelumSiap(): String? = when {
        alamatRepo().isBlank() -> "Isi alamat repo GitHub dulu, misalnya https://github.com/nama/repo."
        token().isBlank() -> "Isi token GitHub dulu (Settings → Developer settings → Personal access tokens)."
        !bolehDiterbitkan(alamatRepo()) -> "Alamat repo harus diawali https:// atau berupa folder lokal (file://)."
        else -> null
    }

    /** Situs hanya bisa terbit ke repo publik; alamat SSH ditolak karena JGit 3.7 tidak memakainya. */
    fun bolehDiterbitkan(alamat: String): Boolean {
        val bersih = alamat.trim()
        return bersih.startsWith("https://") || bersih.startsWith("file://")
    }

    /** Tautan GitHub Pages bila repo-nya di github.com, atau null bila hosting lain/folder lokal. */
    fun tautanSitus(): String? = RepoGit.halamanGitHub(alamatRepo())

    companion object {
        const val CABANG_BAWAAN = "gh-pages"
        private const val KEY_ALAMAT = "alamat"
        private const val KEY_TOKEN = "token"
        private const val KEY_CABANG = "cabang"
        private const val KEY_NAMA = "nama"
        private const val KEY_EMAIL = "email"
    }
}
