package id.kodenusa.app

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Menyandikan kunci AI dan token GitHub dengan kunci yang **tidak bisa keluar dari HP**.
 *
 * Alasannya konkret: sebelumnya kedua nilai itu tersimpan sebagai teks biasa di
 * `SharedPreferences`, sehingga satu perintah `cat shared_prefs/ai.xml` (atau aplikasi backup yang
 * membaca folder privat) sudah cukup untuk mengambil akun pengguna. Dengan Android Keystore, kunci
 * enkripsi hidup di TEE/StrongBox dan tidak pernah bisa diekspor — menyalin berkas preferensi tidak
 * lagi berguna.
 *
 * Format nilai tersandi: `enc1:<iv base64>:<ciphertext base64>`.
 *
 * - `enc1` = versi format. Nilai tanpa awalan ini dianggap **teks lama** dan dibaca apa adanya,
 *   supaya pengguna yang sudah mengisi kunci sebelum pembaruan ini tidak kehilangan setelannya;
 *   pemanggil lalu menyimpannya ulang dalam bentuk tersandi.
 * - IV selalu acak 12 bita per penyandian, karena memakai IV yang sama dua kali pada GCM
 *   membocorkan isi pesan.
 */
object KunciAman : Penyandi {

    const val AWALAN = "enc1:"
    private const val ALIAS = "kodenusa.rahasia.v1"
    private const val PROVIDER = "AndroidKeyStore"
    private const val TRANSFORMASI = "AES/GCM/NoPadding"
    private const val PANJANG_TAG_BIT = 128
    private const val PANJANG_IV = 12

    override fun enkripsi(teks: String): String {
        if (teks.isEmpty()) return ""
        val cipher = Cipher.getInstance(TRANSFORMASI)
        cipher.init(Cipher.ENCRYPT_MODE, kunci())
        val iv = cipher.iv
        val hasil = cipher.doFinal(teks.toByteArray(Charsets.UTF_8))
        return AWALAN + Base64.encodeToString(iv, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(hasil, Base64.NO_WRAP)
    }

    override fun dekripsi(disandi: String): String {
        if (disandi.isEmpty()) return ""
        if (!disandi.startsWith(AWALAN)) return disandi
        val bagian = disandi.removePrefix(AWALAN).split(':')
        // Nilai yang bentuknya rusak tidak boleh membuat aplikasi berhenti; setelan dianggap kosong
        // dan pengguna diminta mengisi ulang, bukan melihat layar galat.
        if (bagian.size != 2) return ""
        return runCatching {
            val iv = Base64.decode(bagian[0], Base64.NO_WRAP)
            val isi = Base64.decode(bagian[1], Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMASI)
            cipher.init(Cipher.DECRYPT_MODE, kunci(), GCMParameterSpec(PANJANG_TAG_BIT, iv))
            String(cipher.doFinal(isi), Charsets.UTF_8)
        }.getOrDefault("")
    }

    /** Menghapus kunci enkripsi; nilai tersandi lama menjadi tidak bisa dibaca (fitur "hapus semua"). */
    fun lupakan() {
        runCatching {
            val toko = KeyStore.getInstance(PROVIDER).apply { load(null) }
            if (toko.containsAlias(ALIAS)) toko.deleteEntry(ALIAS)
        }
    }

    private fun kunci(): SecretKey {
        val toko = KeyStore.getInstance(PROVIDER).apply { load(null) }
        (toko.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val pembuat = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        pembuat.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                // Tidak meminta biometrik: kunci dibaca saat aplikasi aktif, dan memaksa sidik jari
                // akan membuat build otomatis serta agen berhenti meminta kunci setiap kali.
                .setUserAuthenticationRequired(false)
                .build(),
        )
        return pembuat.generateKey()
    }
}
