package id.kodenusa.app

import android.content.Context
import id.kodenusa.build.tier0.DebugKeystore
import id.kodenusa.build.tier0.Toolchain
import java.io.File

/**
 * Kunci tanda tangan milik pengguna (docs/14 2.1, ADR-041).
 *
 * Sebelum fitur ini, semua APK ditandatangani dengan debug keystore per pemasangan
 * (`filesDir/keys/debug.keystore`). Hapus aplikasi atau ganti HP berarti kunci hilang dan APK baru
 * tidak bisa memperbarui APK lama (`INSTALL_UPDATE_INCOMPATIBLE`). Kunci pengguna menyimpan
 * keystore di `filesDir/kunci-ttd/` yang bisa diekspor lewat SAF, jadi pembaruan lintas HP bisa
 * dilakukan selama berkas `.p12` + sandinya dibawa pindah.
 *
 * Sandi keystore TIDAK PERNAH ditulis polos: ia dienskripsi lewat [KunciAman] (AES-256-GCM dengan
 * kunci di Android Keystore), sama seperti token GitHub.
 *
 * Fallback tetap dijaga (Chesterton's Fence): kalau kunci pengguna belum dibuat, atau sandinya
 * tidak bisa dibaca (misal HP baru tanpa berkas kunci), build tetap jalan dengan keystore debug —
 * perilaku lama tidak berubah.
 */
class KunciPengguna(private val context: Context) {

    /** Berkas keystore pengguna; `.p12` (PKCS12) supaya bisa dibaca di semua versi Android. */
    val berkas: File get() = File(context.filesDir, FOLDER)

    private val setelan get() = context.getSharedPreferences(NAMA_SET, Context.MODE_PRIVATE)

    data class Status(val ada: Boolean, val alias: String? = null)

    /** Keadaan sekarang: kunci pengguna ada (dengan aliasnya) atau belum. */
    fun status(): Status {
        val alias = setelan.getString(KUNCI_ALIAS, null)
        return Status(ada = berkas.isFile && alias != null, alias = alias)
    }

    /**
     * Membuat keystore pengguna baru dan menimpa yang lama bila ada. Menimpa berarti aplikasi lama
     * tidak bisa diperbarui dengan APK baru lagi — itu konsekuensi kriptografi, bukan bug, dan
     * pesannya harus jujur di UI.
     */
    fun buat(alias: String, sandi: String): Result<Unit> = runCatching {
        val aliasBersih = alias.trim()
        require(aliasBersih.matches(POLA_ALIAS)) {
            "Alias hanya boleh huruf, angka, titik, garis bawah, dan tanda minus (1–40 karakter)."
        }
        require(sandi.length >= PANJANG_SANDI_MIN) {
            "Sandi minimal $PANJANG_SANDI_MIN karakter."
        }
        DebugKeystore.generate(
            target = berkas,
            commonName = "KodeNusa $aliasBersih",
            alias = aliasBersih,
            password = sandi,
            // docs/14 2.1: validitas 10.000 hari (~27 tahun) supaya kunci tidak kedaluwarsa
            // selama aplikasi pengguna masih dipakai.
            masaBerlakuHari = 10_000,
        )
        setelan.edit()
            .putString(KUNCI_ALIAS, aliasBersih)
            .putString(KUNCI_SANDI, KunciAman.enkripsi(sandi))
            .apply()
    }

    /** Menghapus kunci pengguna; build kembali memakai keystore debug seperti dulu. */
    fun hapus() {
        berkas.delete()
        setelan.edit().clear().apply()
    }

    /** Mengembalikan keystore pengguna ke toolchain bila ada; kalau tidak, toolchain apa adanya. */
    fun terapkan(toolchain: Toolchain): Toolchain {
        val status = status()
        if (!status.ada) return toolchain
        val sandi = runCatching {
            KunciAman.dekripsi(setelan.getString(KUNCI_SANDI, "").orEmpty())
        }.getOrNull()
        // Sandi tidak bisa dibaca (kunci enkripsi HP berubah, dsb.) → jangan bikin build gagal;
        // fallback ke keystore debug dan biarkan UI menampilkan status kunci pengguna bermasalah.
        if (sandi.isNullOrBlank()) return toolchain
        return toolchain.copy(
            debugKeystore = berkas,
            keystoreAlias = status.alias!!,
            keystorePassword = sandi,
        )
    }

    companion object {
        const val FOLDER = "kunci-ttd/kunci.p12"
        const val PANJANG_SANDI_MIN = 6
        const val POLA_ALIAS_TEXT = "[a-zA-Z0-9._-]{1,40}"
        private const val NAMA_SET = "kunci-ttd"
        private const val KUNCI_ALIAS = "alias"
        private const val KUNCI_SANDI = "sandi"
        private val POLA_ALIAS = Regex(POLA_ALIAS_TEXT)
    }
}
