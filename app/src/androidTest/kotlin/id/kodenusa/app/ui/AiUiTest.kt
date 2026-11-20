package id.kodenusa.app.ui

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
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

    @Test
    fun ai_bisa_memilih_berkas_dari_folder_projek() {
        // Setelan AI disemai lebih dulu supaya layar langsung masuk mode obrolan (bukan formulir).
        InstrumentationRegistry.getInstrumentation().targetContext
            .getSharedPreferences("ai", android.content.Context.MODE_PRIVATE)
            .edit()
            .putString("endpoint", "https://api.crown.ai.id/v1")
            .putString("kunci", "kunci-uji-panjang")
            .putString("model", "model-uji")
            .commit()

        bukaEditor()
        layar.onNodeWithTag("buka-ai").performClick()
        layar.waitForIdle()

        // Tombol konteks projek harus ada: AI bekerja di tingkat folder, bukan cuma satu berkas.
        layar.onNodeWithTag("konteks-projek").assertExists()
        layar.onNodeWithTag("pilih-berkas-ai").performClick()
        layar.waitForIdle()
        layar.onNodeWithText("Pilih berkas projek").assertExists()
        layar.onNodeWithTag("tutup-pilih-berkas").performClick()
        layar.waitForIdle()
    }

    @Test
    fun setelan_ai_tidak_hilang_setelah_layar_ditutup() {
        bukaEditor()
        layar.onNodeWithTag("buka-ai").performClick()

        layar.onNodeWithTag("Alamat endpoint").performTextReplacement("https://api.crown.ai.id/v1")
        layar.onNodeWithTag("Kunci API").performTextReplacement("kunci-uji-panjang")
        // Menutup layar tanpa menekan "Muat model" — inilah yang dulu terasa seperti reset.
        layar.onNodeWithText("Tutup").performClick()
        layar.waitForIdle()

        layar.onNodeWithTag("buka-ai").performClick()
        layar.onNodeWithText("https://api.crown.ai.id/v1").assertExists()
        layar.onNodeWithTag("muat-model").assertIsEnabled()
    }

    private fun bukaEditor() {
        if (layar.onAllNodesWithText("Buat projek pertama").fetchSemanticsNodes().isNotEmpty()) {
            layar.onNodeWithText("Buat projek pertama").performClick()
        } else {
            layar.onNodeWithText("Projek baru").performClick()
        }
        layar.onNodeWithTag("daftar-wizard").performScrollToNode(hasTestTag("jenis-kosong"))
        layar.onNodeWithTag("jenis-kosong").performClick()
        layar.onNodeWithTag("daftar-wizard").performScrollToNode(hasText("Projek kosong"))
        layar.onNodeWithText("Projek kosong").performClick()
        // Nama projek kini selalu terlihat di bilah bawah wizard — tidak perlu digulir.
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

    @Test
    fun pemilih_berkas_ai_menampilkan_pohon_dan_pencarian_sub_folder() {
        // Setelan AI disemai supaya layar langsung masuk mode obrolan.
        InstrumentationRegistry.getInstrumentation().targetContext
            .getSharedPreferences("ai", android.content.Context.MODE_PRIVATE)
            .edit()
            .putString("endpoint", "https://api.crown.ai.id/v1")
            .putString("kunci", "kunci-uji-panjang")
            .putString("model", "model-uji")
            .commit()

        bukaEditor()
        layar.onNodeWithTag("buka-ai").performClick()
        layar.waitForIdle()
        layar.onNodeWithTag("pilih-berkas-ai").performClick()
        layar.waitForIdle()

        // Kolom pencarian harus ada — cara mencari berkas di sub-folder tanpa menggulir panjang.
        layar.onNodeWithTag("cari-berkas-ai").assertExists()
        // Folder src tampil sebagai judul di pohon, bukan disembunyikan. Memakai testTag, bukan
        // teks, karena panel berkas editor di belakang dialog juga menampilkan "▸ src".
        layar.onNodeWithTag("folder-ai-src").assertExists()
        // Mencari berkas yang ada di sub-folder dalam harus menemukannya lewat jalurnya.
        layar.onNodeWithTag("cari-berkas-ai").performTextInput("MainActivity")
        layar.waitForIdle()
        layar.onAllNodes(hasText("MainActivity", substring = true)).fetchSemanticsNodes()
            .isNotEmpty().let { temuan ->
                org.junit.Assert.assertTrue("berkas di sub-folder harus ditemukan pencarian", temuan)
            }
        layar.onNodeWithTag("tutup-pilih-berkas").performClick()
    }

    @Test
    fun chip_saran_baru_tampil_di_layar_ai() {
        semaiSetelan()
        bukaEditor()
        layar.onNodeWithTag("buka-ai").performClick()
        layar.waitForIdle()

        // Saran baru dari saran UI/UX: pengguna HP sering bingung mau ngetik apa.
        layar.onNodeWithText("Buat layout XML").assertExists()
        layar.onNodeWithText("Tambah layar kedua").assertExists()
        layar.onNodeWithText("Jelaskan berkas ini").assertExists()
    }

    @Test
    fun berkas_sematan_maksimal_tiga_dengan_pill_dan_buang() {
        semaiSetelan()
        bukaEditor()
        layar.onNodeWithTag("buka-ai").performClick()
        layar.waitForIdle()

        pin("berkas-ai-src/main/AndroidManifest.xml")
        layar.onNodeWithTag("berkas-terpasang-AndroidManifest.xml").assertExists()
        pin("berkas-ai-src/main/res/layout/activity_main.xml")
        pin("berkas-ai-src/main/res/values/strings.xml")

        // Sematan keempat harus ditolak dengan pesan yang jujur, bukan dibisukan diam-diam.
        pin("berkas-ai-kodenusa.json")
        layar.waitUntil(timeoutMillis = 5_000) {
            layar.onAllNodesWithText("Maksimal 3 berkas sematan", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Buang satu sematan: pill hilang dan slot kembali tersedia. Baris chip bisa digulir
        // horizontal — tombol Buang paling kanan harus digulir dulu supaya sentuhannya mendarat.
        layar.onNodeWithTag("baris-chip-ai")
            .performScrollToNode(hasTestTag("buang-sematan-AndroidManifest.xml"))
        layar.waitForIdle()
        layar.onNodeWithTag("buang-sematan-AndroidManifest.xml").performClick()
        layar.waitForIdle()
        layar.onNodeWithTag("berkas-terpasang-AndroidManifest.xml").assertDoesNotExist()
    }

    private fun pin(testTag: String) {
        layar.onNodeWithTag("pilih-berkas-ai").performClick()
        layar.waitForIdle()
        // Pohon berkas memakai LazyColumn: berkas di bawah layar harus digulir dulu.
        layar.onNodeWithTag("daftar-berkas-ai").performScrollToNode(hasTestTag(testTag))
        layar.waitForIdle()
        layar.onNodeWithTag(testTag).performClick()
        layar.waitForIdle()
    }

    private fun semaiSetelan() {
        InstrumentationRegistry.getInstrumentation().targetContext
            .getSharedPreferences("ai", android.content.Context.MODE_PRIVATE)
            .edit()
            .putString("endpoint", "https://api.crown.ai.id/v1")
            .putString("kunci", "kunci-uji-panjang")
            .putString("model", "model-uji")
            .commit()
    }
}
