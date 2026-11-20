package id.kodenusa.app

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import id.kodenusa.core.project.ProjectFiles
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Uji UI: berkas `.py` di editor punya tombol "Jalankan" yang membuka konsol Python dan
 * menampilkan keluaran skrip sungguhan (MicroPython di perangkat).
 */
@RunWith(AndroidJUnit4::class)
class PythonJalankanUiTest {

    @get:Rule
    val layar = createAndroidComposeRule<MainActivity>()

    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val toko get() = ProjectStore(File(ctx.filesDir, "projek"))

    @Before
    fun bersihkan() {
        toko.list().forEach { it.dir.deleteRecursively() }
    }

    @Test
    fun tombol_jalankan_menampilkan_keluaran_python() {
        val folder = Templates(ctx).create(
            "halo-dunia",
            toko.uniqueFolder("Py Uji"),
            "Py Uji",
            "id.kodenusa.pyuji",
        ).getOrThrow()
        val dibuat = ProjectFiles.createFile(folder, "", "alat.py", "print('halo dari python')\n")
        check(dibuat is ProjectFiles.Hasil.Sukses)

        layar.activityRule.scenario.recreate()
        layar.waitUntil(timeoutMillis = 20_000) {
            layar.onAllNodes(hasText("Py Uji", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        layar.onNode(hasText("Py Uji", substring = true)).performClick()
        layar.waitUntil(timeoutMillis = 20_000) {
            layar.onAllNodesWithTag("buka-palet").fetchSemanticsNodes().isNotEmpty()
        }

        // Buka berkas .py dari panel berkas.
        layar.onAllNodes(hasText("☰"))[0].performClick()
        layar.waitUntil(timeoutMillis = 10_000) {
            layar.onAllNodesWithTag("daftar-berkas").fetchSemanticsNodes().isNotEmpty()
        }
        layar.onNodeWithTag("daftar-berkas").performScrollToNode(hasTestTag("baris-alat.py"))
        layar.onNodeWithTag("baris-alat.py").performClick()
        layar.waitUntil(timeoutMillis = 10_000) {
            layar.onAllNodesWithTag("jalankan-python").fetchSemanticsNodes().isNotEmpty()
        }
        layar.onNodeWithTag("jalankan-python").performClick()

        // Konsol terbuka dan skrip benar-benar dijalankan di perangkat.
        layar.waitUntil(timeoutMillis = 30_000) {
            layar.onAllNodes(hasText("halo dari python")).fetchSemanticsNodes().isNotEmpty()
        }
        layar.onNodeWithTag("status-python").assertExists()
        layar.onAllNodesWithText("Selesai (kode 0)").fetchSemanticsNodes().isNotEmpty()
    }
}
