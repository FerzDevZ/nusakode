package id.kodenusa.app.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import id.kodenusa.core.project.ProjectNode
import id.kodenusa.ui.KodenusaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Buka-berkas cepat di palet perintah (docs/14 2.7): mengetik sebagian nama menemukan berkasnya,
 * dan satu ketukan membukanya.
 */
@RunWith(AndroidJUnit4::class)
class BukaBerkasPaletUiTest {

    @get:Rule
    val layar = createComposeRule()

    private fun nodes(): List<ProjectNode> = listOf(
        ProjectNode(File("/tmp/p/src"), "src", "src", 0, true),
        ProjectNode(
            File("/tmp/p/src/main/java/app/MainActivity.java"),
            "src/main/java/app/MainActivity.java",
            "MainActivity.java",
            3,
            false,
        ),
        ProjectNode(File("/tmp/p/web/gaya.css"), "web/gaya.css", "gaya.css", 1, false),
    )

    @Test
    fun mengetik_sebagian_nama_menemukan_berkas_dan_membukanya() {
        val dibuka = mutableListOf<File>()
        layar.setContent {
            KodenusaTheme {
                PaletPerintahDialog(
                    perintah = perintahBukaBerkas(nodes()) { dibuka += it },
                    onTutup = {},
                )
            }
        }
        layar.onNodeWithTag("cari-aksi").performTextInput("mainact")
        layar.onNodeWithTag("aksi-buka-src-main-java-app-MainActivity-java", useUnmergedTree = true)
            .performClick()
        assertEquals(1, dibuka.size)
        assertEquals("MainActivity.java", dibuka.single().name)
    }

    @Test
    fun folder_tidak_ditawarkan_dan_kueri_kosong_mendaftar_semua_berkas() {
        val dibuka = mutableListOf<File>()
        layar.setContent {
            KodenusaTheme {
                PaletPerintahDialog(
                    perintah = perintahBukaBerkas(nodes()) { dibuka += it },
                    onTutup = {},
                )
            }
        }
        layar.onNodeWithTag("aksi-buka-web-gaya-css", useUnmergedTree = true).assertExists()
        layar.onNodeWithTag("aksi-buka-src", useUnmergedTree = true).assertDoesNotExist()
        assertEquals(0, dibuka.size)
    }
}
