package id.kodenusa.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Uji nyata penyimpanan tema: pilihan bertahan, resolusi SISTEM mengikuti malam Android. */
@RunWith(AndroidJUnit4::class)
class TemaStoreDeviceTest {

    private val konteks get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun `mode_tersimpan_dan_terbaca_kembali`() {
        val store = TemaStore(konteks)
        val semula = store.mode
        try {
            store.mode = ModeTema.TERANG
            assertEquals(ModeTema.TERANG, TemaStore(konteks).mode)
            store.mode = ModeTema.AMOLED
            assertEquals(ModeTema.AMOLED, TemaStore(konteks).mode)
        } finally {
            store.mode = semula
        }
    }

    @Test
    fun `token_tema_terang_benar_benar_terang`() {
        val store = TemaStore(konteks)
        val semula = store.mode
        try {
            store.mode = ModeTema.TERANG
            val tokens = store.tokens()
            assertTrue(!tokens.gelap)
            // Latar terang harus punya luminance tinggi (nilai RGB besar), bukan gelap.
            val latar = tokens.paletInt.background
            assertTrue("latar terang harus terang: $latar", ((latar shr 16) and 0xFF) > 200)
        } finally {
            store.mode = semula
        }
    }

    @Test
    fun `tema_kustom_tanpa_json_jatuh_ke_gelap_bengkel`() {
        val store = TemaStore(konteks)
        val semula = store.mode
        try {
            store.mode = ModeTema.KUSTOM
            store.jsonKustom = null
            val tokens = store.tokens()
            assertTrue("kustom tanpa data harus aman memakai tema gelap", tokens.gelap)
        } finally {
            store.mode = semula
        }
    }

    @Test
    fun `tema_kustom_valid_dipakai_sebagai_warna_aktif`() {
        val store = TemaStore(konteks)
        val semula = store.mode
        try {
            val json = """
                {"versi":1,"nama":"UjiPerangkat","gelap":true,"warna":{
                "background":"#0E1217","surface":"#181E23","surfaceRaised":"#232A30",
                "border":"#484E54","borderStrong":"#6F757B","text":"#EBEFF4","textDim":"#A0A5AB",
                "accent":"#F7AD30","accentInk":"#281600","ok":"#61D19A","warn":"#E7C952",
                "error":"#F75D59","info":"#6AB8E4"}}
            """.trimIndent()
            store.jsonKustom = json
            store.mode = ModeTema.KUSTOM
            val tokens = store.tokens()
            assertEquals(0xFF0E1217.toInt(), tokens.paletInt.background)
            assertTrue(Kntema.pasanganGagal(tokens.paletInt).isEmpty())
        } finally {
            store.mode = semula
        }
    }
}
