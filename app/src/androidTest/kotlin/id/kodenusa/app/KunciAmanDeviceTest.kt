package id.kodenusa.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import id.kodenusa.core.ai.AiConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Membuktikan janji keamanan: kunci AI dan token GitHub **tidak** bisa dibaca dari berkas preferensi
 * walaupun berkasnya disalin utuh. Uji ini menulis lewat penyimpanan asli (Android Keystore), lalu
 * memeriksa isi berkas di `shared_prefs` apa adanya.
 */
@RunWith(AndroidJUnit4::class)
class KunciAmanDeviceTest {

    private val konteks = InstrumentationRegistry.getInstrumentation().targetContext
    private val contohKunci = "sk-uji-jangan-pernah-tersimpan-polos-1234567890"
    private val contohToken = "ghp_uji_token_tidak_boleh_polos_0987654321"

    private fun berkasPrefs(nama: String): File =
        File(konteks.applicationInfo.dataDir, "shared_prefs/$nama.xml")

    /**
     * `apply()` menulis ke disk secara asinkron, sedangkan uji ini memeriksa isi berkas. Satu
     * `commit()` kecil memaksa semua perubahan yang tertunda ikut tertulis, tanpa mengubah nilai
     * yang sedang diuji.
     */
    private fun segarkanKeDisk(nama: String) {
        konteks.getSharedPreferences(nama, 0).edit().putString("uji_paksa_tulis", "1").commit()
    }

    private fun nilaiMentah(nama: String, kunci: String): String =
        konteks.getSharedPreferences(nama, 0).getString(kunci, "").orEmpty()

    @Before
    fun bersihkan() {
        konteks.getSharedPreferences("ai", 0).edit().clear().commit()
        konteks.getSharedPreferences("git", 0).edit().clear().commit()
        KunciAman.lupakan()
    }

    @Test
    fun sandi_bolak_balik_mengembalikan_teks_asli() {
        val hasil = KunciAman.enkripsi(contohKunci)
        assertTrue("harus berawalan versi format", hasil.startsWith(KunciAman.AWALAN))
        assertFalse("teks asli tidak boleh terlihat", hasil.contains(contohKunci))
        assertEquals(contohKunci, KunciAman.dekripsi(hasil))
    }

    @Test
    fun dua_penyandian_menghasilkan_iv_berbeda() {
        val a = KunciAman.enkripsi(contohKunci)
        val b = KunciAman.enkripsi(contohKunci)
        assertTrue("IV acak: hasil harus berbeda", a != b)
        assertEquals(contohKunci, KunciAman.dekripsi(a))
        assertEquals(contohKunci, KunciAman.dekripsi(b))
    }

    @Test
    fun kunci_ai_tidak_tertulis_polos_di_berkas_preferensi() {
        AiSettingsStore(konteks).simpan(AiConfig("https://api.contoh.id/v1", contohKunci, "model-uji"))
        val mentah = nilaiMentah("ai", "kunci")
        assertTrue("nilai tersimpan harus berawalan enc1:", mentah.startsWith(KunciAman.AWALAN))
        assertFalse("nilai tersimpan tidak boleh memuat kunci asli", mentah.contains(contohKunci))
        segarkanKeDisk("ai")
        val isi = berkasPrefs("ai").readText()
        assertFalse("kunci AI bocor di shared_prefs", isi.contains(contohKunci))
        assertEquals(contohKunci, AiSettingsStore(konteks).muat().apiKey)
    }

    @Test
    fun token_github_tidak_tertulis_polos_di_berkas_preferensi() {
        GitSettingsStore(konteks).simpan("https://github.com/contoh/repo", contohToken)
        val mentah = nilaiMentah("git", "token")
        assertTrue("token harus tersandi", mentah.startsWith(KunciAman.AWALAN))
        assertEquals(contohToken, GitSettingsStore(konteks).token())
        segarkanKeDisk("git")
        val isi = berkasPrefs("git").readText()
        assertFalse("token GitHub bocor di shared_prefs", isi.contains(contohToken))
        // Alamat repo bukan rahasia dan tetap boleh terbaca polos untuk ditampilkan di layar.
        assertTrue(isi.contains("github.com/contoh/repo"))
    }

    @Test
    fun kunci_lama_yang_masih_polos_dimigrasikan_otomatis() {
        konteks.getSharedPreferences("ai", 0).edit()
            .putString("endpoint", "https://api.lama.id/v1")
            .putString("kunci", "kunci-lama-polos-abcdef")
            .commit()
        assertTrue(
            "prasyarat: nilai lama memang polos",
            berkasPrefs("ai").readText().contains("kunci-lama-polos-abcdef"),
        )

        val terbaca = AiSettingsStore(konteks).muat().apiKey
        assertEquals("kunci-lama-polos-abcdef", terbaca)
        assertTrue(
            "setelah dibaca sekali, nilai harus disandikan",
            nilaiMentah("ai", "kunci").startsWith(KunciAman.AWALAN),
        )
        segarkanKeDisk("ai")
        assertFalse(
            "nilai polos harus hilang dari berkas",
            berkasPrefs("ai").readText().contains("kunci-lama-polos-abcdef"),
        )
    }

    @Test
    fun nilai_tersandi_yang_rusak_tidak_membuat_aplikasi_berhenti() {
        assertEquals("", KunciAman.dekripsi("enc1:bukan-base64"))
        assertEquals("", KunciAman.dekripsi("enc1:a:b:c"))
    }

    @Test
    fun setelah_kunci_dihapus_nilai_tersandi_menjadi_kosong_bukan_galat() {
        val disandi = KunciAman.enkripsi(contohKunci)
        KunciAman.lupakan()
        assertEquals("", KunciAman.dekripsi(disandi))
    }
}
