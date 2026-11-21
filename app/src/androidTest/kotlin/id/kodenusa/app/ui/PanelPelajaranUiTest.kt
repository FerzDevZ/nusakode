package id.kodenusa.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import id.kodenusa.app.LangkahPelajaran
import id.kodenusa.app.Pelajaran

/**
 * Uji interaksi panel pelajaran sebagai composable mandiri (createComposeRule — bukan
 * createAndroidComposeRule, lihat invariant: MainActivity sudah setContent di onCreate).
 */
@RunWith(AndroidJUnit4::class)
class PanelPelajaranUiTest {

    @get:Rule
    val layar = createComposeRule()

    private val pelajaran = Pelajaran(
        "uji",
        listOf(
            LangkahPelajaran(1, "Kenali layar", "Buka berkasnya."),
            LangkahPelajaran(2, "Ubah teks", "Ubah strings.xml."),
            LangkahPelajaran(3, "Bangun APK", "Tekan tombol Bangun."),
        ),
    )

    @Test
    fun panel_menampilkan_langkah_pertama_dan_maju_mundur() {
        // State Compose, bukan var biasa: mengubah var biasa tidak memicu rekompisisi,
        // jadi panel tidak akan berubah dan uji gagal mencari tombol langkah 2.
        val langkah = androidx.compose.runtime.mutableIntStateOf(1)
        layar.setContent {
            PanelPelajaran(
                pelajaran = pelajaran,
                langkahSekarang = langkah.intValue,
                onLangkah = { langkah.intValue = it },
                onTutup = {},
                onSelesai = {},
            )
        }
        layar.onNodeWithTag("pelajaran-panel").assertIsDisplayed()
        layar.onNodeWithText("Pelajaran 1/3").assertIsDisplayed()
        layar.onNodeWithText("1. Kenali layar").assertIsDisplayed()

        layar.onNodeWithTag("pelajaran-berikut").performClick()
        layar.waitForIdle()
        assertEquals(2, langkah.intValue)

        // Langkah 1 tidak punya tombol Sebelumnya; langkah 2 punya.
        layar.onNodeWithTag("pelajaran-sebelum").performClick()
        layar.waitForIdle()
        assertEquals(1, langkah.intValue)
    }

    @Test
    fun langkah_terakhir_menawarkan_selesai() {
        var selesai = false
        layar.setContent {
            PanelPelajaran(
                pelajaran = pelajaran,
                langkahSekarang = 3,
                onLangkah = {},
                onTutup = {},
                onSelesai = { selesai = true },
            )
        }
        layar.onNodeWithText("Pelajaran 3/3").assertIsDisplayed()
        layar.onNodeWithText("3. Bangun APK").assertIsDisplayed()
        layar.onNodeWithTag("pelajaran-selesai").performClick()
        layar.waitForIdle()
        assertEquals(true, selesai)
    }

    @Test
    fun tombol_tutup_mengembalikan_kendali() {
        var tertutup = false
        layar.setContent {
            PanelPelajaran(
                pelajaran = pelajaran,
                langkahSekarang = 1,
                onLangkah = {},
                onTutup = { tertutup = true },
                onSelesai = {},
            )
        }
        layar.onNodeWithTag("pelajaran-tutup").performClick()
        layar.waitForIdle()
        assertEquals(true, tertutup)
    }
}
