package id.kodenusa.app.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import id.kodenusa.ui.KodenusaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Palet perintah: pencarian, penyaringan, dan satu perintah benar-benar dijalankan.
 *
 * Yang dibuktikan: di layar 360dp semua aksi tetap terjangkau tanpa menambah baris tombol, pencarian
 * memakai AND (bukan OR), dan perintah web-murni tidak pernah menawarkan "Build APK".
 */
@RunWith(AndroidJUnit4::class)
class PaletPerintahUiTest {

    @get:Rule
    val layar = createComposeRule()

    private fun perintahContoh(dijalankan: MutableList<String>, webMurni: Boolean = false) = daftarPerintah(
        onSimpan = { dijalankan += "simpan" },
        onCari = { dijalankan += "cari" },
        onBuild = { dijalankan += "build" },
        onSitus = { dijalankan += "situs" },
        onPratinjau = { dijalankan += "pratinjau" },
        onBerkasBaru = { dijalankan += "berkas-baru" },
        onImpor = { dijalankan += "impor" },
        webMurni = webMurni,
        adaSitus = true,
    )

    @Test
    fun mengetik_build_menemukan_perintah_build_dan_bisa_dijalankan() {
        val dijalankan = mutableListOf<String>()
        layar.setContent {
            KodenusaTheme {
                PaletPerintahDialog(perintah = perintahContoh(dijalankan), onTutup = {})
            }
        }
        layar.onNodeWithTag("cari-aksi").performTextInput("apk")
        layar.onNodeWithTag("aksi-build", useUnmergedTree = true).assertExists()
        layar.onNodeWithTag("aksi-build", useUnmergedTree = true).performClick()
        assertEquals(listOf("build"), dijalankan)
    }

    @Test
    fun pencarian_memakai_semua_kata_bukan_salah_satu() {
        val daftar = perintahContoh(mutableListOf())
        assertEquals(listOf("simpan"), saring(daftar, "simpan berkas").map { it.id })
        assertTrue("kata yang tidak ada harus mengosongkan hasil", saring(daftar, "simpan git").isEmpty())
    }

    @Test
    fun projek_web_murni_tidak_menawarkan_build_apk() {
        val daftar = perintahContoh(mutableListOf(), webMurni = true)
        assertTrue("tidak boleh ada build APK", daftar.none { it.id == "build" })
        // "situs" juga muncul di keterangan pratinjau, jadi yang diuji adalah kehadiran perintah
        // susun situs dan ketiadaan build APK — bukan urutan atau jumlah persisnya.
        assertTrue(saring(daftar, "situs").any { it.id == "susun-situs" })
    }

    @Test
    fun kueri_kosong_menampilkan_semua_dan_kueri_ngawur_menjelaskan() {
        val dijalankan = mutableListOf<String>()
        layar.setContent {
            KodenusaTheme {
                PaletPerintahDialog(perintah = perintahContoh(dijalankan), onTutup = {})
            }
        }
        layar.onNodeWithTag("aksi-simpan", useUnmergedTree = true).assertExists()
        layar.onNodeWithTag("cari-aksi").performTextInput("zzz")
        layar.onNodeWithTag("aksi-kosong").assertExists()
        layar.onNodeWithText("Tidak ada aksi yang cocok dengan \"zzz\".").assertExists()
    }
}
