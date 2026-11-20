package id.kodenusa.app

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Uji jalur yang dikeluhkan pengguna: projek sudah dibuat, tetapi tidak bisa menambah atau mengubah
 * berkas. Uji ini menekan tombol sungguhan sampai berkas baru muncul di panel dan bisa dibuka.
 */
@RunWith(AndroidJUnit4::class)
class FileOpsUiTest {

    @get:Rule
    val layar = createAndroidComposeRule<MainActivity>()

    @Before
    fun bersihkan() {
        val folder = InstrumentationRegistry.getInstrumentation().targetContext.filesDir
        folder.listFiles().orEmpty()
            .filter { it.isDirectory && java.io.File(it, "kodenusa.json").isFile }
            .forEach { it.deleteRecursively() }
    }

    @Test
    fun berkas_baru_bisa_dibuat_dari_panel_projek() {
        buatProjekKosong("Projek Uji Berkas")
        bukaPanelBerkas()

        layar.onNodeWithTag("berkas-baru").performClick()
        layar.onNodeWithTag("nama-berkas").performTextReplacement("Catatan.md")
        layar.onNodeWithTag("aksi-berkas").assertIsEnabled().performClick()

        layar.waitUntil(timeoutMillis = 20_000) {
            layar.onAllNodesWithTag("daftar-berkas").fetchSemanticsNodes().isNotEmpty() &&
                layar.onAllNodes(hasText("Catatan.md")).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun folder_baru_bisa_dibuat() {
        buatProjekKosong("Projek Uji Folder")
        bukaPanelBerkas()

        layar.onNodeWithTag("folder-baru").performClick()
        layar.onNodeWithTag("nama-berkas").performTextReplacement("komponen")
        layar.onNodeWithTag("aksi-berkas").assertIsEnabled().performClick()

        layar.waitUntil(timeoutMillis = 20_000) {
            layar.onAllNodes(hasText("komponen", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun buatProjekKosong(nama: String) {
        if (layar.onAllNodes(hasText("Buat projek pertama")).fetchSemanticsNodes().isNotEmpty()) {
            layar.onNodeWithText("Buat projek pertama").performClick()
        } else {
            layar.onNodeWithText("Projek baru").performClick()
        }
        layar.onNodeWithTag("daftar-wizard").performScrollToNode(hasText("Projek kosong"))
        layar.onNodeWithText("Projek kosong").performClick()
        layar.onNodeWithTag("daftar-wizard").performScrollToNode(hasSetTextAction())
        layar.onAllNodes(hasSetTextAction())[0].performTextReplacement(nama)
        layar.waitForIdle()
        layar.onNodeWithTag("tombol-buat").assertIsEnabled().performClick()
        layar.waitUntil(timeoutMillis = 20_000) {
            layar.onAllNodes(hasText(nama, substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        layar.onNodeWithText(nama, substring = true).performClick()
        layar.waitForIdle()
    }

    private fun bukaPanelBerkas() {
        layar.onNodeWithText("☰").performClick()
        layar.waitUntil(timeoutMillis = 10_000) {
            layar.onAllNodesWithTag("berkas-baru").fetchSemanticsNodes().isNotEmpty()
        }
    }
}
