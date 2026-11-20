package id.kodenusa.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.WindowCompat
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Memutar keempat tema bawaan + "Ikuti sistem" dengan membuat ulang aktivitas di antara gantian.
 * Menangkap kelas bug yang tidak terlihat uji unit: tema salah terbaca setelah proses dibuat ulang,
 * dan ikon bilah status tidak mengikuti kegelapan tema (bug nyata 0.11.0 yang ditemukan lewat
 * screenshot).
 */
@RunWith(AndroidJUnit4::class)
class TemaCycleDeviceTest {

    @get:Rule
    val layar = createAndroidComposeRule<MainActivity>()

    private val store get() = TemaStore(layar.activity.applicationContext)

    @Test
    fun `semua_tema_bertahan_dan_bilah_status_mengikuti_setelah_recreate`() {
        val semula = store.mode
        try {
            for (pasangan in listOf(
                ModeTema.GELAP to true,
                ModeTema.AMOLED to true,
                ModeTema.TERANG to false,
                ModeTema.TERANG_TINGGI to false,
            )) {
                val (mode, gelap) = pasangan
                store.mode = mode
                layar.activityRule.scenario.recreate()
                layar.waitForIdle()

                assertEquals("mode $mode harus bertahan setelah recreate", mode, store.mode)
                cekBilahStatus(gelap, mode.name)
            }

            // "Ikuti sistem": emulator uji dalam mode siang, jadi harus terang.
            layar.activityRule.scenario.recreate()
            layar.waitForIdle()
            store.mode = ModeTema.SISTEM
            layar.activityRule.scenario.recreate()
            layar.waitForIdle()
            cekBilahStatus(gelap = false, nama = "SISTEM (siang)")
        } finally {
            store.mode = semula
            store.jsonKustom = null
        }
    }

    /** Ikon bilah status gelap = tema terang; ikon terang = tema gelap. */
    private fun cekBilahStatus(gelap: Boolean, nama: String) {
        val jendela = layar.activity.window
        val pengatur = WindowCompat.getInsetsController(jendela, jendela.decorView)
        assertEquals(
            "ikon bilah status harus ${if (gelap) "terang" else "gelap"} pada tema $nama",
            !gelap,
            pengatur.isAppearanceLightStatusBars,
        )
    }
}
