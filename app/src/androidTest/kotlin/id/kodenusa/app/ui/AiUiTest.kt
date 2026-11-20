package id.kodenusa.app.ui

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import id.kodenusa.app.MainActivity
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Uji layar AI tanpa jaringan: yang dibuktikan adalah janji privasi — sebelum pengguna mengisi
 * endpoint dan kunci, layar hanya menampilkan formulir dan tombol "Muat model" terkunci.
 */
@RunWith(AndroidJUnit4::class)
class AiUiTest {

    @get:Rule
    val layar = createAndroidComposeRule<MainActivity>()

    @Before
    fun bersihkan() {
        val konteks = InstrumentationRegistry.getInstrumentation().targetContext
        konteks.filesDir.listFiles().orEmpty()
            .filter { it.isDirectory && java.io.File(it, "kodenusa.json").isFile }
            .forEach { it.deleteRecursively() }
        // Jangan biarkan sisa kunci dari sesi lain membuat layar langsung masuk mode obrolan.
        konteks.getSharedPreferences("ai", android.content.Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun layar_ai_menampilkan_formulir_endpoint_dan_kunci() {
        bukaEditor()
        layar.onNodeWithTag("buka-ai").performClick()

        layar.onNodeWithText("Alamat endpoint").assertExists()
        layar.onNodeWithText("Kunci API").assertExists()
        layar.onNodeWithTag("muat-model").assertExists()
        // Tombol harus terkunci selama endpoint/kunci belum diisi: tidak ada panggilan jaringan diam-diam.
        layar.onNodeWithTag("muat-model").assertIsNotEnabled()
    }

    @Test
    fun mengisi_endpoint_dan_kunci_membuka_tombol_muat_model() {
        bukaEditor()
        layar.onNodeWithTag("buka-ai").performClick()

        layar.onNodeWithTag("Alamat endpoint").performTextReplacement("https://api.crown.ai.id/v1")
        layar.onNodeWithTag("Kunci API").performTextReplacement("kunci-uji-panjang")

        layar.onNodeWithTag("muat-model").assertIsEnabled()
    }

    private fun bukaEditor() {
        if (layar.onAllNodesWithText("Buat projek pertama").fetchSemanticsNodes().isNotEmpty()) {
            layar.onNodeWithText("Buat projek pertama").performClick()
        } else {
            layar.onNodeWithText("Projek baru").performClick()
        }
        layar.onNodeWithTag("daftar-wizard").performScrollToNode(hasText("Projek kosong"))
        layar.onNodeWithText("Projek kosong").performClick()
        layar.onNodeWithTag("daftar-wizard").performScrollToNode(hasSetTextAction())
        layar.onAllNodes(hasSetTextAction())[0].performTextReplacement("Projek AI")
        layar.waitForIdle()
        layar.onNodeWithTag("tombol-buat").assertIsEnabled().performClick()
        layar.waitUntil(timeoutMillis = 20_000) {
            layar.onAllNodes(hasText("Projek AI", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        layar.onNodeWithText("Projek AI", substring = true).performClick()
        layar.waitUntil(timeoutMillis = 20_000) {
            layar.onAllNodesWithText("AI").fetchSemanticsNodes().isNotEmpty()
        }
    }
}
