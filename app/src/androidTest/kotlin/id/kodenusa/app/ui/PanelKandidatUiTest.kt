package id.kodenusa.app.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import id.kodenusa.ui.KodenusaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Panel kandidat lengkapi kata (docs/14 2.5): chip bisa ditekan dan mengirim kandidat yang
 * benar; memilih kandidat mengganti kata ketikan, buka menambah di belakangnya (dibuktikan di
 * KataAktifDeviceTest lewat CodeEditorView langsung).
 */
@RunWith(AndroidJUnit4::class)
class PanelKandidatUiTest {

    @get:Rule
    val layar = createComposeRule()

    @Test
    fun menekan_kandidat_mengirim_kata_yang_benar() {
        val dipilih = mutableListOf<String>()
        layar.setContent {
            KodenusaTheme {
                PanelKandidat(listOf("System", "Systemout")) { dipilih += it }
            }
        }
        layar.onNodeWithTag("kandidat-System", useUnmergedTree = true).performClick()
        assertEquals(listOf("System"), dipilih)
    }

    @Test
    fun setiap_kandidat_punya_chip_sendiri() {
        layar.setContent {
            KodenusaTheme {
                PanelKandidat(listOf("System", "sysout"), onPilih = {})
            }
        }
        layar.onNodeWithTag("kandidat-System", useUnmergedTree = true).assertExists()
        layar.onNodeWithTag("kandidat-sysout", useUnmergedTree = true).assertExists()
    }
}
