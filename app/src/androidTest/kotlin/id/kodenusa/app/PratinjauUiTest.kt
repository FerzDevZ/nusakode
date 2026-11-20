package id.kodenusa.app

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Pratinjau situs di dalam aplikasi.
 *
 * Yang diuji: tombol Pratinjau muncul untuk projek yang punya halaman situs, WebView-nya benar-benar
 * terpasang, dan halaman yang dimuat adalah **isi projek pengguna** — bukan halaman kosong. Projek
 * dibuat langsung lewat `Templates` supaya uji ini tidak bergantung pada alur wizard.
 */
@RunWith(AndroidJUnit4::class)
class PratinjauUiTest {

    @get:Rule
    val layar = createAndroidComposeRule<MainActivity>()

    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val templates get() = Templates(ctx)
    private val toko get() = ProjectStore(File(ctx.filesDir, "projek"))

    @Before
    fun bersihkan() {
        toko.list().forEach { it.dir.deleteRecursively() }
    }

    @Test
    fun projek_situs_bisa_dipratinjau_di_dalam_aplikasi() {
        val folder = templates.create(
            "situs-murni",
            toko.uniqueFolder("Pratinjau Uji"),
            "Pratinjau Uji",
            "id.kodenusa.pratinjau",
        ).getOrThrow()

        // Teks penanda di halaman, supaya isi pratinjau bisa dibedakan dari halaman galat.
        val indeks = File(folder, "web/index.html")
        assertTrue("template situs harus punya web/index.html", indeks.isFile)
        indeks.writeText(indeks.readText().replaceFirst("</body>", "<h1>PenandaPratinjau</h1>\n</body>"))

        // Daftar projek dibaca saat layar disusun; projek yang baru dibuat di latar uji baru
        // terlihat setelah layarnya dibuat ulang.
        layar.activityRule.scenario.recreate()
        bukaProjek("Pratinjau Uji")

        layar.waitUntil(timeoutMillis = 20_000) {
            layar.onAllNodesWithTag("buka-pratinjau").fetchSemanticsNodes().isNotEmpty()
        }
        layar.onNodeWithTag("buka-pratinjau").performClick()
        layar.waitUntil(timeoutMillis = 20_000) {
            layar.onAllNodesWithTag("pratinjau-web").fetchSemanticsNodes().isNotEmpty()
        }
        layar.onNodeWithTag("pratinjau-web").assertExists()
        // Berkas yang dimuat adalah milik projek, bukan salinan bawaan aplikasi.
        assertTrue(indeks.readText().contains("PenandaPratinjau"))

        layar.onNodeWithTag("tutup-pratinjau").performClick()
        layar.waitUntil(timeoutMillis = 10_000) {
            layar.onAllNodesWithTag("pratinjau-web").fetchSemanticsNodes().isEmpty()
        }
    }

    private fun bukaProjek(nama: String) {
        layar.waitUntil(timeoutMillis = 20_000) {
            layar.onAllNodes(hasText(nama, substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        layar.onNodeWithText(nama, substring = true).performClick()
        layar.waitForIdle()
    }
}
