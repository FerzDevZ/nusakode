package id.kodenusa.app

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import id.kodenusa.core.project.ProjectFiles
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Janji jujur soal Python.
 *
 * Sejak Tier 2 (ADR-033) Python **bisa dijalankan** lewat MicroPython, tetapi masih **tidak bisa
 * dibuild** jadi APK. Uji ini memastikan berkas `.py` yang dibuka menyatakan keduanya di layar —
 * apa yang jalan dan apa yang belum — bukan membiarkan pengguna menebak sendiri.
 */
@RunWith(AndroidJUnit4::class)
class CatatanPythonUiTest {

    @get:Rule
    val layar = createAndroidComposeRule<MainActivity>()

    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val toko get() = ProjectStore(File(ctx.filesDir, "projek"))

    @Before
    fun bersihkan() {
        toko.list().forEach { it.dir.deleteRecursively() }
    }

    @Test
    fun membuka_berkas_py_menjelaskan_bahwa_python_belum_didukung() {
        val folder = Templates(ctx).create(
            "halo-dunia",
            toko.uniqueFolder("Python Uji"),
            "Python Uji",
            "id.kodenusa.pythonuji",
        ).getOrThrow()
        val dibuat = ProjectFiles.createFile(folder, "", "alat.py", "print('halo')\n")
        val py = (dibuat as ProjectFiles.Hasil.Sukses).file
        assertTrue(py.isFile)

        // Daftar projek dibaca saat layar disusun, jadi layar dibuat ulang setelah projek dibuat.
        layar.activityRule.scenario.recreate()
        layar.waitUntil(timeoutMillis = 20_000) {
            layar.onAllNodes(hasText("Python Uji", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        layar.onNode(hasText("Python Uji", substring = true)).performClick()
        layar.waitUntil(timeoutMillis = 20_000) {
            layar.onAllNodesWithTag("buka-palet").fetchSemanticsNodes().isNotEmpty()
        }

        // Berkas dibuka dari panel: ☰ lalu baris berkasnya.
        layar.onNode(hasTestTag("buka-palet")).assertExists()
        layar.onNodeWithTag("buka-menu").performClick()
        layar.waitUntil(timeoutMillis = 10_000) {
            layar.onAllNodesWithTag("daftar-berkas").fetchSemanticsNodes().isNotEmpty()
        }
        // Baris .py bisa berada di luar layar: gulir dulu, baru tekan.
        layar.onNodeWithTag("daftar-berkas").performScrollToNode(hasTestTag("baris-alat.py"))
        layar.onNodeWithTag("baris-alat.py").performClick()
        layar.waitUntil(timeoutMillis = 10_000) {
            layar.onAllNodesWithTag("catatan-python").fetchSemanticsNodes().isNotEmpty()
        }
        layar.onNodeWithTag("catatan-python").assertExists()
    }
}
