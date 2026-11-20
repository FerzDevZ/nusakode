package id.kodenusa.app

import androidx.compose.ui.test.assertIsEnabled
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
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
        // Hanya isi folder kerja yang dihapus. Menghapus filesDir/projek itu sendiri membuat
        // AppContainer yang sudah dibuat kehilangan foldernya, lalu pembuatan projek jadi aneh.
        val kerja = java.io.File(
            InstrumentationRegistry.getInstrumentation().targetContext.filesDir,
            "projek",
        )
        kerja.listFiles().orEmpty().filter { it.isDirectory }.forEach { it.deleteRecursively() }
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
        layar.waitForIdle()
        val akarFolder = java.io.File(
            InstrumentationRegistry.getInstrumentation().targetContext.filesDir,
            "projek",
        ).listFiles().orEmpty().first { it.isDirectory }
        assertTrue("folder komponen tidak ada di disk", java.io.File(akarFolder, "komponen").isDirectory)
        layar.onNodeWithTag("daftar-berkas").performScrollToNode(hasTestTag("baris-komponen"))


        layar.waitUntil(timeoutMillis = 20_000) {
            layar.onAllNodesWithTag("baris-komponen").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun berkas_bisa_dipindah_ke_folder_lain() {
        buatProjekKosong("Projek Uji Pindah")
        bukaPanelBerkas()
        buatBerkas("Catatan.md")
        layar.onNodeWithTag("folder-baru").performClick()
        layar.onNodeWithTag("nama-berkas").performTextReplacement("komponen")
        layar.onNodeWithTag("aksi-berkas").assertIsEnabled().performClick()
        layar.waitForIdle()

        val akar = java.io.File(
            InstrumentationRegistry.getInstrumentation().targetContext.filesDir,
            "projek",
        ).listFiles().orEmpty().first { it.isDirectory }
        // Folder baru harus benar-benar ada di akar projek, bukan di dalam src/main/java/...
        assertTrue("folder komponen tidak ada di disk", java.io.File(akar, "komponen").isDirectory)
        layar.onNodeWithTag("daftar-berkas").performScrollToNode(hasTestTag("menu-Catatan.md"))
        layar.onNodeWithTag("menu-Catatan.md").performClick()
        layar.onNodeWithText("Pindahkan…").performClick()
        layar.onNodeWithText("Pilih folder tujuan di dalam projek ini.").assertExists()
        layar.onNodeWithTag("tujuan-komponen").performClick()

        // Berkas harus tetap ada di projek, sekarang di dalam folder komponen.
        layar.waitUntil(timeoutMillis = 20_000) {
            layar.onAllNodes(hasText("Catatan.md")).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun berkas_bisa_diduplikasi_dari_menu() {
        buatProjekKosong("Projek Uji Duplikat")
        bukaPanelBerkas()
        buatBerkas("asli.md")

        layar.onNodeWithTag("daftar-berkas").performScrollToNode(hasTestTag("menu-asli.md"))
        layar.onNodeWithTag("menu-asli.md").performClick()
        layar.onNodeWithText("Duplikat").performClick()

        layar.waitUntil(timeoutMillis = 20_000) {
            layar.onAllNodes(hasText("asli-salinan.md")).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun buatBerkas(nama: String) {
        layar.onNodeWithTag("berkas-baru").performClick()
        layar.onNodeWithTag("nama-berkas").performTextReplacement(nama)
        layar.onNodeWithTag("aksi-berkas").assertIsEnabled().performClick()
        layar.waitForIdle()
        layar.onNodeWithTag("daftar-berkas").performScrollToNode(hasTestTag("baris-$nama"))
        // Dialog wajib tertutup: kalau tetap terbuka, pembuatan berkas diam-diam gagal.
        assertFalse(
            "dialog tidak tertutup, pembuatan berkas gagal: $nama",
            layar.onAllNodesWithTag("nama-berkas").fetchSemanticsNodes().isNotEmpty(),
        )
        layar.waitUntil(timeoutMillis = 20_000) {
            layar.onAllNodesWithTag("baris-$nama").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun buatProjekKosong(nama: String) {
        if (layar.onAllNodes(hasText("Buat projek pertama")).fetchSemanticsNodes().isNotEmpty()) {
            layar.onNodeWithText("Buat projek pertama").performClick()
        } else {
            layar.onNodeWithText("Projek baru").performClick()
        }
        layar.onNodeWithTag("daftar-wizard").performScrollToNode(hasTestTag("jenis-kosong"))
        layar.onNodeWithTag("jenis-kosong").performClick()
        layar.onNodeWithTag("daftar-wizard").performScrollToNode(hasText("Projek kosong"))
        layar.onNodeWithText("Projek kosong").performClick()
        // Nama projek kini selalu terlihat di bilah bawah wizard — tidak perlu digulir.
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
        layar.onNodeWithTag("buka-menu").performClick()
        layar.waitUntil(timeoutMillis = 10_000) {
            layar.onAllNodesWithTag("berkas-baru").fetchSemanticsNodes().isNotEmpty()
        }
    }
}
