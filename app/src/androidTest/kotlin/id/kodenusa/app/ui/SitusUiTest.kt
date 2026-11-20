package id.kodenusa.app.ui

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import id.kodenusa.app.Templates
import id.kodenusa.ui.KodenusaTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Uji layar Situs: jalur "pure web" tidak boleh memaksa pengguna membuat APK.
 * Yang dibuktikan di sini adalah janji produk, bukan detail tampilan.
 */
@RunWith(AndroidJUnit4::class)
class SitusUiTest {

    @get:Rule
    val layar = createComposeRule()

    private val konteks = InstrumentationRegistry.getInstrumentation().targetContext

    private fun projekSitus(): File {
        val folder = File(konteks.cacheDir, "situs-ui-${System.nanoTime()}")
        return Templates(konteks).create("situs-murni", folder, "Warung Bu Sri", "id.kodenusa.warungbusri")
            .getOrThrow()
    }

    @Test
    fun menekan_susun_menampilkan_isi_situs() {
        val projek = projekSitus()
        layar.setContent { KodenusaTheme { SitusScreen(projectDir = projek, onBack = {}) } }

        layar.onNodeWithTag("susun-situs").performClick()

        layar.waitUntil(timeoutMillis = 10_000) {
            layar.onAllNodesWithTag("ringkasan-situs").fetchSemanticsNodes().isNotEmpty()
        }
        layar.onNodeWithText("Isi situs").assertExists()
        layar.onNodeWithText("index.html", substring = true).assertExists()
        assertTrue("index.html harus benar-benar tersusun di disk", File(projek, "build/situs/index.html").isFile)
    }

    @Test
    fun projek_tanpa_situs_menampilkan_alasan_bukan_tombol_mati() {
        val folder = File(konteks.cacheDir, "situs-ui-aplikasi-${System.nanoTime()}")
        val projek = Templates(konteks).create("halo-dunia", folder, "Halo", "id.kodenusa.halo").getOrThrow()
        layar.setContent { KodenusaTheme { SitusScreen(projectDir = projek, onBack = {}) } }

        layar.onNodeWithTag("susun-situs").performScrollTo().performClick()

        layar.waitUntil(timeoutMillis = 10_000) {
            layar.onAllNodesWithTag("galat-situs").fetchSemanticsNodes().isNotEmpty()
        }
        layar.onNodeWithText("Belum bisa disusun").assertExists()
        layar.onAllNodesWithText("web/index.html", substring = true)[0].assertExists()
    }
}
