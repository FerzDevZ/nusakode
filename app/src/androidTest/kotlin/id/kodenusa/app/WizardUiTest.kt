package id.kodenusa.app

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
        layar.onNodeWithTag("daftar-wizard").performScrollToNode(hasSetTextAction())
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

        layar.onNodeWithTag("daftar-wizard").performScrollToNode(hasSetTextAction())
        layar.onAllNodes(hasSetTextAction())[0].performTextInput("Projek Dari Nol")
        layar.waitForIdle()

        layar.onNodeWithTag("tombol-buat").assertIsEnabled().performClick()
        layar.waitUntil(timeoutMillis = 20_000) {
            layar.onAllNodes(hasText("Projek Dari Nol", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
