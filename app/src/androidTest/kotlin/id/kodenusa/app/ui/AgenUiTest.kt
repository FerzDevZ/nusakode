package id.kodenusa.app.ui

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import id.kodenusa.app.AiSettingsStore
import id.kodenusa.app.AppContainer
import id.kodenusa.app.Templates
import id.kodenusa.core.ai.AiConfig
import id.kodenusa.ui.KodenusaTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Uji layar Agen tanpa jaringan: yang dibuktikan adalah janji privasi dan batas biaya.
 * Tanpa kunci AI tidak ada tombol aktif yang bisa memanggil internet.
 */
@RunWith(AndroidJUnit4::class)
class AgenUiTest {

    @get:Rule
    val layar = createComposeRule()

    private val konteks = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun bersihkan() {
        // Setelan AI kosong: layar tidak boleh memanggil jaringan tanpa kunci milik pengguna.
        konteks.getSharedPreferences("ai", android.content.Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun projek(): File {
        val folder = File(konteks.cacheDir, "agen-ui-${System.nanoTime()}")
        return Templates(konteks).create("halo-dunia", folder, "Halo Agen", "id.kodenusa.haloagen").getOrThrow()
    }

    private fun konten(projek: File) {
        layar.setContent {
            KodenusaTheme {
                AgenScreen(container = AppContainer(konteks), projectDir = projek, onBack = {})
            }
        }
    }

    @Test
    fun tanpa_kunci_ai_tombol_mulai_terkunci_dengan_alasan() {
        val projek = projek()
        konten(projek)

        layar.onNodeWithTag("mulai-agen").performScrollTo().assertIsNotEnabled()
        layar.onNodeWithText("Isi endpoint dan kunci AI dulu", substring = true).assertExists()
    }

    @Test
    fun setelah_kunci_dan_tugas_diisi_tombol_mulai_aktif() {
        val projek = projek()
        AiSettingsStore(konteks).simpan(AiConfig(baseUrl = "https://contoh.invalid/v1", apiKey = "kunci-uji", model = "uji"))
        konten(projek)

        layar.onNodeWithTag("tugas-agen").performScrollTo().performTextReplacement("Tambah tombol Bagikan")

        layar.waitUntil(timeoutMillis = 5_000) {
            layar.onAllNodesWithTag("alasan-agen").fetchSemanticsNodes().isEmpty()
        }
        layar.onNodeWithTag("mulai-agen").performScrollTo().assertIsEnabled()
    }
}
