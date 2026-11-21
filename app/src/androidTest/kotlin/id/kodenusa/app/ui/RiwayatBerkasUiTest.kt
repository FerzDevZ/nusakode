package id.kodenusa.app.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import id.kodenusa.app.RiwayatBerkas
import id.kodenusa.ui.KodenusaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Dialog riwayat berkas (docs/14 2.8): versi lama tampil dengan nomor, dan menekannya
 * memulihkan isi lama sebagai perubahan yang belum disimpan — bukan langsung menimpa.
 */
@RunWith(AndroidJUnit4::class)
class RiwayatBerkasUiTest {

    @get:Rule
    val layar = createComposeRule()

    private val akar: File
        get() = File(
            InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
            "riwayat-uji",
        )

    @Test
    fun versi_tampil_dan_dipulihkan_saat_ditekan() {
        akar.deleteRecursively()
        val berkas = File(akar, "src/main/java/app/MainActivity.java").apply { parentFile.mkdirs() }
        berkas.writeText("versi sekarang")
        val riwayat = RiwayatBerkas(akar)
        riwayat.catat(berkas, "versi lama")

        var dipulihkan: String? = null
        layar.setContent {
            KodenusaTheme {
                DialogRiwayatBerkas(
                    riwayat = riwayat,
                    berkas = berkas,
                    onPulihkan = { dipulihkan = it },
                    onTutup = {},
                )
            }
        }

        layar.onNodeWithText("Riwayat berkas").assertExists()
        layar.onNodeWithTag("riwayat-1").performClick()
        layar.runOnIdle { assertEquals("versi lama", dipulihkan) }
    }

    @Test
    fun tanpa_versi_dijelaskan_bukan_diam_saja() {
        akar.deleteRecursively()
        val berkas = File(akar, "catatan.txt")
        layar.setContent {
            KodenusaTheme {
                DialogRiwayatBerkas(
                    riwayat = RiwayatBerkas(akar),
                    berkas = berkas,
                    onPulihkan = {},
                    onTutup = {},
                )
            }
        }
        layar.onNodeWithTag("riwayat-kosong").assertExists()
    }
}
