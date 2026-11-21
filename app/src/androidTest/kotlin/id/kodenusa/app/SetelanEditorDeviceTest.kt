package id.kodenusa.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Ukuran font editor (docs/14 2.6): batas 14–22sp, langkah 2sp, dan persisten antar instance.
 */
@RunWith(AndroidJUnit4::class)
class SetelanEditorDeviceTest {

    private val konteks = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun default_14sp_dan_naik_turun_dua_sp() {
        konteks.getSharedPreferences("editor", android.content.Context.MODE_PRIVATE).edit().clear().commit()
        val setelan = SetelanEditor(konteks)
        assertEquals(14f, setelan.ukuranFont)
        assertEquals(16f, setelan.perbesar())
        assertEquals(14f, setelan.perkecil())
    }

    @Test
    fun terkunci_di_batas_14_dan_22() {
        konteks.getSharedPreferences("editor", android.content.Context.MODE_PRIVATE).edit().clear().commit()
        val setelan = SetelanEditor(konteks)
        repeat(10) { setelan.perbesar() }
        assertEquals(22f, setelan.ukuranFont)
        repeat(10) { setelan.perkecil() }
        assertEquals(14f, setelan.ukuranFont)
    }

    @Test
    fun pilihan_bertahan_setelah_instance_baru() {
        konteks.getSharedPreferences("editor", android.content.Context.MODE_PRIVATE).edit().clear().commit()
        SetelanEditor(konteks).let { s ->
            repeat(3) { s.perbesar() }
        }
        assertEquals(20f, SetelanEditor(konteks).ukuranFont)
    }
}
