package id.kodenusa.app.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import id.kodenusa.ui.KodenusaTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Membuktikan jalur baru "AI bisa membuat berkas baru di sub-folder" tanpa jaringan: dialog
 * membuat folder bertingkat (src/main/java/app) dan menulis isi kode dari jawaban AI.
 */
@RunWith(AndroidJUnit4::class)
class AiBuatBerkasTest {

    @get:Rule
    val layar = createComposeRule()

    @Test
    fun dialog_membuat_berkas_baru_beserta_sub_foldernya() {
        val akar = File(
            InstrumentationRegistry.getInstrumentation().targetContext.filesDir,
            "uji-buat-berkas",
        )
        akar.deleteRecursively()
        akar.mkdirs()
        try {
            layar.setContent {
                KodenusaTheme {
                    DialogBuatBerkasDariKode(
                        kode = "public class LayarUji {}\n",
                        akar = akar,
                        onTutup = {},
                        onJadi = {},
                    )
                }
            }
            layar.onNodeWithTag("jalur-berkas-baru")
                .performTextInput("src/main/java/app/LayarUji.java")
            layar.onNodeWithTag("buat-berkas-ok").performClick()
            layar.waitForIdle()

            val jadi = File(akar, "src/main/java/app/LayarUji.java")
            layar.waitUntil(timeoutMillis = 5_000) { jadi.isFile }
            assertTrue("berkas harus dibuat di sub-folder", jadi.isFile)
            assertEquals("public class LayarUji {}\n", jadi.readText())
        } finally {
            akar.deleteRecursively()
        }
    }

    @Test
    fun dialog_menolak_jalur_yang_keluar_dari_projek() {
        val akar = File(
            InstrumentationRegistry.getInstrumentation().targetContext.filesDir,
            "uji-buat-berkas-ditolak",
        )
        akar.deleteRecursively()
        akar.mkdirs()
        try {
            layar.setContent {
                KodenusaTheme {
                    DialogBuatBerkasDariKode(kode = "x", akar = akar, onTutup = {}, onJadi = {})
                }
            }
            layar.onNodeWithTag("jalur-berkas-baru").performTextInput("../kabur.java")
            layar.onNodeWithTag("buat-berkas-ok").performClick()
            layar.waitForIdle()
            // Dialog tetap terbuka (tidak ada onJadi) dan tidak ada berkas di luar folder uji.
            layar.onNodeWithTag("buat-berkas-ok").assertExists()
            assertTrue(
                "tidak boleh ada berkas yang tertulis di luar folder uji",
                !File(akar.parentFile, "kabur.java").exists(),
            )
        } finally {
            akar.deleteRecursively()
            File(akar.parentFile, "kabur.java").delete()
        }
    }
}
