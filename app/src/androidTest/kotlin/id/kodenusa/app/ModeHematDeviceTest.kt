package id.kodenusa.app

import android.app.ActivityManager
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Mode hemat menentukan batas RAM untuk log dan sorotan. Salah hitung di sini berarti HP kecil
 * kehabisan memori hanya karena teks, jadi ambangnya diuji di perangkat.
 */
@RunWith(AndroidJUnit4::class)
class ModeHematDeviceTest {

    private val konteks = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun ram_kecil_mengaktifkan_mode_hemat() {
        assertTrue(ModeHemat.aktif(null, 3_000_000_000L))
        assertTrue(ModeHemat.aktif(null, 3_600_000_000L))
        assertFalse(ModeHemat.aktif(null, 6_000_000_000L))
    }

    @Test
    fun batas_log_dan_sorotan_turun_saat_hemat() {
        val hemat = ModeHemat.batasUntuk(true)
        val normal = ModeHemat.batasUntuk(false)

        assertTrue(hemat.batasLog < normal.batasLog)
        assertTrue(hemat.sorotanBerkasBesarByte < normal.sorotanBerkasBesarByte)
        assertEquals(ModeHemat.LOG_HEMAT, hemat.batasLog)
        assertEquals(ModeHemat.LOG_NORMAL, normal.batasLog)
    }

    @Test
    fun deteksi_memakai_activity_manager_perangkat() {
        val aktivitas = konteks.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        aktivitas.getMemoryInfo(info)

        val batas = ModeHemat.untukKonteks(konteks)

        // Emulator uji berjalan dengan RAM 3 GB, jadi mode hemat wajib aktif di sini.
        assertTrue("RAM ${info.totalMem} byte seharusnya masuk mode hemat", batas.hemat)
        assertEquals(ModeHemat.LOG_HEMAT, batas.batasLog)
        assertTrue(ModeHemat.label(batas).contains("mode hemat"))
    }
}
