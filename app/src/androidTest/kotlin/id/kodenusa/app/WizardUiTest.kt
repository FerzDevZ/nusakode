package id.kodenusa.app

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Uji UI untuk jalur yang dulu rusak: user mengisi form "Projek baru", tetapi tombol
 * "Buat projek" tidak pernah bisa ditekan karena daftar template kosong tanpa pesan.
 *
 * Uji ini menekan tombol sungguhan di layar, jadi ia menangkap kelas bug yang tidak terlihat
 * oleh uji unit: kesalahan baca aset, kartu template tidak dirender, dan tombol terkunci.
 */
@RunWith(AndroidJUnit4::class)
class WizardUiTest {

    @get:Rule
    val layar = createAndroidComposeRule<MainActivity>()

    /** Setiap uji mulai dari daftar projek kosong supaya layar awal selalu "Buat projek pertama". */
    @Before
    fun bersihkanProjek() {
        val folder = InstrumentationRegistry.getInstrumentation().targetContext.filesDir
        folder.listFiles().orEmpty()
            .filter { it.isDirectory && java.io.File(it, "kodenusa.json").isFile }
            .forEach { it.deleteRecursively() }
    }

    /**
     * Membuka form projek baru dari layar mana pun: empty state memakai "Buat projek pertama",
     * sedangkan daftar berisi memakai tombol "Projek baru".
     */
    private fun bukaFormProjekBaru() {
        if (layar.onAllNodes(hasText("Buat projek pertama")).fetchSemanticsNodes().isNotEmpty()) {
            layar.onNodeWithText("Buat projek pertama").performClick()
        } else {
            layar.onNodeWithText("Projek baru").performClick()
        }
        layar.waitForIdle()
    }

    @Test
    fun penjelasan_tanpa_izin_tampil_di_layar_awal() {
        // Laporan pengguna (Samsung A17 5G, Android 15): "kok tidak ada dialog izin penyimpanan,
        // aneh". Itu memang disengaja (ADR-022) — tapi harus dijelaskan di dalam aplikasi, bukan
        // cuma di README, supaya pengguna Android 10+ tidak mengira aplikasinya rusak.
        layar.onNodeWithTag("info-tanpa-izin").assertExists()
        layar.onNodeWithText("Tidak ada dialog izin penyimpanan? Itu memang disengaja.").assertExists()
    }

    @Test
    fun template_tampil_dan_tombol_buat_projek_bisa_ditekan() {
        bukaFormProjekBaru()

        // Kartu template wajib ada — inilah yang hilang saat bug.
        layar.onNodeWithText("Halo Dunia").assertExists()
        // Template web muncul setelah jenis "Website" dipilih, bukan tercampur di daftar aplikasi.
        layar.onNodeWithTag("daftar-wizard").performScrollToNode(hasTestTag("jenis-web"))
        layar.onNodeWithTag("jenis-web").performClick()
        layar.onNodeWithTag("daftar-wizard").performScrollToNode(hasText("Web jadi APK"))
        layar.onNodeWithText("Web jadi APK").assertExists()

        // Isi nama aplikasi (kolom teks pertama di form), lalu tekan tombol yang dulu mati.
        // Nama projek kini selalu terlihat di bilah bawah wizard — tidak perlu digulir.
        layar.onAllNodes(hasSetTextAction())[0].performTextInput("Warung Bu Sri")
        layar.waitForIdle()

        layar.onNodeWithTag("tombol-buat").assertIsEnabled().performClick()
        layar.waitUntil(timeoutMillis = 20_000) {
            layar.onAllNodes(hasText("Warung Bu Sri", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun projek_kosong_bisa_dipilih_dan_dibuat() {
        bukaFormProjekBaru()
        layar.onNodeWithTag("daftar-wizard").performScrollToNode(hasTestTag("jenis-kosong"))
        layar.onNodeWithTag("jenis-kosong").performClick()
        layar.onNodeWithTag("daftar-wizard").performScrollToNode(hasText("Projek kosong"))
        layar.onNodeWithText("Projek kosong").performClick()

        layar.onAllNodes(hasSetTextAction())[0].performTextInput("Projek Dari Nol")
        layar.waitForIdle()

        layar.onNodeWithTag("tombol-buat").assertIsEnabled().performClick()
        layar.waitUntil(timeoutMillis = 20_000) {
            layar.onAllNodes(hasText("Projek Dari Nol", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun website_saja_tidak_membuat_folder_aplikasi_android() {
        bukaFormProjekBaru()
        layar.onNodeWithTag("daftar-wizard").performScrollToNode(hasTestTag("jenis-web-murni"))
        layar.onNodeWithTag("jenis-web-murni").performClick()
        // Catatan khusus situs murni harus terlihat: ini yang membedakannya dari "Website jadi APK".
        layar.onNodeWithTag("daftar-wizard").performScrollToNode(hasTestTag("catatan-web-murni"))
        layar.onNodeWithTag("catatan-web-murni").assertExists()

        layar.onAllNodes(hasSetTextAction())[0].performTextReplacement("Toko Bu Sri")
        layar.waitForIdle()

        layar.onNodeWithTag("tombol-buat").assertIsEnabled().performClick()
        layar.waitUntil(timeoutMillis = 20_000) {
            layar.onAllNodes(hasTestTag("daftar-berkas")).fetchSemanticsNodes().isNotEmpty() ||
                layar.onAllNodes(hasText("Toko Bu Sri", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }

        val projek = projekTerbaru()
        // Inti keluhan: projek web tidak boleh diberi kerangka aplikasi Android.
        assertTrue(
            "folder src tidak boleh ada di projek web murni",
            !java.io.File(projek, "src").exists(),
        )
        assertTrue(
            "web/index.html harus ada",
            java.io.File(projek, "web/index.html").isFile,
        )
    }

    /** Projek yang baru dibuat: folder dengan `kodenusa.json` paling baru diubah. */
    /**
     * docs/14 2.2: memilih warna ikon di wizard harus benar-benar tersimpan di kodenusa.json,
     * bukan sekadar swatch yang berubah warna tanpa efek.
     */
    @Test
    fun warna_ikon_terpilih_tersimpan_di_kodenusa_json() {
        bukaFormProjekBaru()
        layar.onNodeWithTag("daftar-wizard").performScrollToNode(hasTestTag("warna-ikon-1F2937"))
        layar.onNodeWithTag("warna-ikon-1F2937").performClick()

        layar.onAllNodes(hasSetTextAction())[0].performTextInput("Ikon Arang")
        layar.waitForIdle()

        layar.onNodeWithTag("tombol-buat").assertIsEnabled().performClick()
        layar.waitUntil(timeoutMillis = 20_000) {
            layar.onAllNodes(hasText("Ikon Arang", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }

        val isi = java.io.File(projekTerbaru(), "kodenusa.json").readText()
        assertTrue("warnaIkon arang harus tersimpan", isi.contains("\"warnaIkon\": \"#1F2937\""))
    }

    private fun projekTerbaru(): java.io.File {
        val akar = InstrumentationRegistry.getInstrumentation().targetContext.filesDir
        val kandidat = akar.walkTopDown()
            .filter { it.isDirectory && java.io.File(it, "kodenusa.json").isFile }
            .maxByOrNull { java.io.File(it, "kodenusa.json").lastModified() }
        requireNotNull(kandidat) { "tidak ada projek yang dibuat" }
        return kandidat
    }

    @Test
    fun tombol_mati_menjelaskan_alasannya() {
        bukaFormProjekBaru()

        // Tombol "Buat projek" mati selama nama kosong. Sebelum ini, menekannya tidak memberi
        // reaksi apa pun sehingga terlihat seperti tombol rusak (keluhan "tidak bisa ditekan").
        // Tombol ada di bilah bawah, bukan di dalam daftar, jadi tidak perlu digulir.
        layar.onNodeWithTag("tombol-buat").assertIsNotEnabled()
        layar.onNodeWithTag("alasan-buat").assertExists()
        layar.onNodeWithText("Isi nama projek dulu untuk mengaktifkan tombol ini.").assertExists()

        layar.onAllNodes(hasSetTextAction())[0].performTextReplacement("Projek Alasan")
        layar.waitForIdle()

        layar.onNodeWithTag("alasan-buat").assertDoesNotExist()
        layar.onNodeWithTag("tombol-buat").assertIsEnabled()
    }

    @Test
    fun tombol_template_ai_dan_galeri_tampil_dan_dialog_galeri_terbuka() {
        bukaFormProjekBaru()
        // Kedua tombol baru v0.19 ada di daftar wizard.
        layar.onNodeWithTag("daftar-wizard").performScrollToNode(hasTestTag("buka-scaffold"))
        layar.onNodeWithTag("buka-scaffold").performClick()
        layar.waitForIdle()
        // Dialog scaffold terbuka; tanpa kunci AI, alasannya jujur dan tombol Rancang terkunci.
        layar.onNodeWithTag("alasan-scaffold").assertExists()
        layar.onNodeWithTag("batal-scaffold").performClick()
        layar.waitForIdle()

        layar.onNodeWithTag("daftar-wizard").performScrollToNode(hasTestTag("buka-galeri"))
        layar.onNodeWithTag("buka-galeri").performClick()
        layar.waitForIdle()
        // Dialog galeri memuat kolom alamat dengan nilai bawaan yang bisa diedit.
        layar.onNodeWithTag("url-galeri").assertExists()
        layar.onNodeWithTag("batal-galeri").performClick()
        layar.waitForIdle()
    }
}
