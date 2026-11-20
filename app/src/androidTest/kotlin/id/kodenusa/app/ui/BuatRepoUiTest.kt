package id.kodenusa.app.ui

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import id.kodenusa.app.GitSettingsStore
import id.kodenusa.app.Templates
import id.kodenusa.ui.KodenusaTheme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Uji panel "Buat repo baru" di layar Git.
 *
 * Jalur jaringannya (`GitHubRilis.buatRepo`) sudah diuji dengan token sungguhan di `:core:git`.
 * Di sini yang diuji adalah **gerbangnya**: tanpa token, tombol mati dan alasannya tertulis —
 * bukan percobaan jaringan yang gagal diam-diam.
 */
@RunWith(AndroidJUnit4::class)
class BuatRepoUiTest {

    @get:Rule
    val layar = createComposeRule()

    private val konteks = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun bersihkan() {
        GitSettingsStore(konteks).hapus()
    }

    private fun projek(): File {
        val folder = File(konteks.cacheDir, "buat-repo-${System.nanoTime()}")
        return Templates(konteks).create("halo-dunia", folder, "Repo Uji", "id.kodenusa.repouji").getOrThrow()
    }

    @Test
    fun tanpa_token_tombol_buat_mati_dan_alasannya_tertulis() {
        val projek = projek()
        layar.setContent {
            KodenusaTheme {
                GitScreen(projectDir = projek, onBack = {})
            }
        }
        layar.onNodeWithTag("buka-buat-repo").performClick()
        layar.waitUntil(timeoutMillis = 10_000) {
            layar.onAllNodesWithTag("catatan-buat-repo").fetchSemanticsNodes().isNotEmpty()
        }
        layar.onNodeWithText("Token GitHub belum diisi. Isi dulu di layar Situs, lalu kembali ke sini.")
            .assertExists()
        layar.onNodeWithTag("jalankan-buat-repo").assertIsNotEnabled()
    }

    @Test
    fun nama_repo_awal_diambil_dari_nama_folder_projek() {
        assertEquals("Kasir-Warung", namaRepoAwal("Kasir Warung"))
        assertEquals("kodenusa-projek", namaRepoAwal("   "))
        assertEquals("repo-1", namaRepoAwal("repo/1"))
    }
}
