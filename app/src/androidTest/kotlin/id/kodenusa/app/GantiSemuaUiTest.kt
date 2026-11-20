package id.kodenusa.app

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * "Ganti semua" di seluruh projek — fitur yang paling sering diminta setelah pencarian: mengganti
 * nama variabel atau harga di banyak berkas sekaligus, tanpa membuka satu per satu.
 *
 * Yang diperiksa bukan hanya tampilan, tetapi **isi berkas di disk** sesudah tombol ditekan. Tanpa
 * itu, tombol bisa terlihat berhasil sementara berkasnya tidak berubah.
 */
@RunWith(AndroidJUnit4::class)
class GantiSemuaUiTest {

    @get:Rule
    val layar = createAndroidComposeRule<MainActivity>()

    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun bersihkan() {
        File(ctx.filesDir, "projek").listFiles().orEmpty()
            .filter { it.isDirectory }
            .forEach { it.deleteRecursively() }
    }

    @Test
    fun ganti_semua_mengubah_berkas_di_disk_dan_melaporkan_jumlahnya() {
        val akar = buatProjekKosong("Projek Ganti")
        val satu = File(akar, "src/main/java/app/Satu.java").apply {
            parentFile?.mkdirs()
            writeText("class Satu {\n    int totalHarga = 1;\n    int lain = totalHarga + 2;\n}\n")
        }
        val dua = File(akar, "src/main/java/app/Dua.java").apply {
            parentFile?.mkdirs()
            writeText("class Dua {\n    int totalHarga = 3;\n}\n")
        }

        layar.onNodeWithText("Cari").performClick()
        layar.waitUntil(timeoutMillis = 10_000) {
            layar.onAllNodesWithTag("cari-projek").fetchSemanticsNodes().isNotEmpty()
        }
        layar.onNodeWithTag("cari-projek").performClick()
        // Urutan penting: mode se-projek dulu, baru kata kuncinya. Kalau dibalik, jumlah kecocokan
        // dihitung untuk berkas aktif saja sehingga tombolnya tidak pernah aktif.
        layar.onAllNodes(hasSetTextAction())[0].performTextReplacement("totalHarga")
        layar.waitUntil(timeoutMillis = 10_000) {
            layar.onAllNodes(hasText("ganti dengan…")).fetchSemanticsNodes().isNotEmpty()
        }
        layar.onNodeWithTag("isi-pengganti").performTextReplacement("hargaAkhir")
        layar.onNodeWithTag("ganti-semua").performClick()

        layar.waitUntil(timeoutMillis = 20_000) {
            layar.onAllNodesWithTag("pesan-ganti").fetchSemanticsNodes().isNotEmpty()
        }
        layar.onNodeWithTag("pesan-ganti").assertExists()
        layar.onNodeWithText("3 potongan di 2 berkas diganti.").assertExists()

        assertEquals(
            "class Satu {\n    int hargaAkhir = 1;\n    int lain = hargaAkhir + 2;\n}\n",
            satu.readText(),
        )
        assertEquals("class Dua {\n    int hargaAkhir = 3;\n}\n", dua.readText())
        assertTrue(!satu.readText().contains("totalHarga"))
    }

    private fun buatProjekKosong(nama: String): File {
        if (layar.onAllNodes(hasText("Buat projek pertama")).fetchSemanticsNodes().isNotEmpty()) {
            layar.onNodeWithText("Buat projek pertama").performClick()
        } else {
            layar.onNodeWithText("Projek baru").performClick()
        }
        layar.onNodeWithTag("daftar-wizard").performScrollToNode(hasTestTag("jenis-kosong"))
        layar.onNodeWithTag("jenis-kosong").performClick()
        layar.onNodeWithTag("daftar-wizard").performScrollToNode(hasText("Projek kosong"))
        layar.onNodeWithText("Projek kosong").performClick()
        layar.onNodeWithTag("daftar-wizard").performScrollToNode(hasSetTextAction())
        layar.onAllNodes(hasSetTextAction())[0].performTextReplacement(nama)
        layar.waitForIdle()
        layar.onNodeWithTag("tombol-buat").performClick()
        layar.waitUntil(timeoutMillis = 20_000) {
            layar.onAllNodes(hasText(nama, substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        layar.onNodeWithText(nama, substring = true).performClick()
        layar.waitForIdle()
        return File(ctx.filesDir, "projek").listFiles().orEmpty()
            .filter { it.isDirectory }
            .maxByOrNull { it.lastModified() }
            ?: error("folder projek tidak ditemukan")
    }
}
