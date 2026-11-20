package id.kodenusa.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import id.kodenusa.app.MainActivity
import id.kodenusa.app.ModeTema
import id.kodenusa.app.TemaStore
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

/**
 * Uji layar Tema lewat UI nyata: buka dari workspace, pilih tema terang, dan pilihan bertahan
 * setelah aktivitas dibuat ulang (anti-kilat membaca tema dari penyimpanan, bukan default).
 */
@RunWith(AndroidJUnit4::class)
class TemaUiTest {

    @get:Rule
    val layar = createAndroidComposeRule<MainActivity>()

    private fun bukaTema() {
        layar.onNodeWithTag("buka-tema").performClick()
        layar.waitForIdle()
    }

    @Test
    fun `layar_tema_terbuka_dan_menampilkan_empat_tema_bawaan`() {
        bukaTema()
        layar.onNodeWithTag("tema-gelap").assertIsDisplayed()
        layar.onNodeWithTag("tema-amoled").assertIsDisplayed()
        layar.onNodeWithTag("tema-terang").assertIsDisplayed()
        layar.onNodeWithTag("tema-terang-tinggi").assertIsDisplayed()
    }

    @Test
    fun `pilih_tema_terang_pilihan_bertahan_setelah_recreate`() {
        val store = TemaStore(layar.activity.applicationContext)
        val semula = store.mode
        try {
            bukaTema()
            layar.onNodeWithTag("tema-terang").performClick()
            layar.waitForIdle()

            layar.onNodeWithTag("kembali-tema").performClick()
            layar.waitForIdle()
            layar.activityRule.scenario.recreate()
            layar.waitForIdle()

            assertEquals("pilihan tema harus bertahan setelah recreate", ModeTema.TERANG, store.mode)
        } finally {
            store.mode = semula
            store.jsonKustom = null
        }
    }

    @Test
    fun `bagian_tema_kustom_tampil_meski_belum_ada_tema_kustom`() {
        bukaTema()
        layar.onNodeWithText("Tema kustom").assertIsDisplayed()
        layar.onNodeWithTag("impor-tema").performScrollTo()
        layar.onNodeWithTag("impor-tema").assertIsDisplayed()
        layar.onNodeWithTag("unduh-tema").assertIsDisplayed()
    }
}
