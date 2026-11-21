package id.kodenusa.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Uji mode belajar (docs/14 3.3) terhadap aset sungguhan di APK — bukan berkas tiruan.
 * Pelajaran pentingnya: aset baru yang tidak ikut ke APK (pelajaran aapt) hanya ketahuan
 * kalau uji membaca aset dari APK yang benar-benar terpasang.
 */
@RunWith(AndroidJUnit4::class)
class PelajaranDeviceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val store = PelajaranStore.bawaan(context)

    @Test
    fun pelajaran_halo_dunia_membawa_5_langkah_dari_aset_apk() {
        val pelajaran = store.muat("halo-dunia")
        assertNotNull("pelajaran.md halo-dunia hilang dari APK", pelajaran)
        assertEquals("halo-dunia", pelajaran!!.templateId)
        assertEquals(5, pelajaran.langkah.size)
        assertEquals("Kenali layar editor", pelajaran.langkah[0].judul)
        assertEquals("Pasang dan lihat hasilnya", pelajaran.langkah[4].judul)
        // Setiap langkah punya isi, tidak ada langkah kosong.
        assertEquals(5, pelajaran.langkah.count { it.isi.isNotBlank() })
    }

    @Test
    fun template_tanpa_pelajaran_menghasilkan_null() {
        assertNull(store.muat("kasir-warung"))
        assertNull(store.muat(null))
    }

    @Test
    fun progres_tersimpan_di_filesdir_dan_bertahan() {
        val id = "uji-pelajaran-${System.nanoTime()}"
        assertEquals(1, store.bacaProgres(id))
        store.simpanProgres(id, 4)
        assertEquals(4, store.bacaProgres(id))
        // Bersihkan supaya uji berikutnya mulai bersih.
        java.io.File(java.io.File(context.filesDir, "pelajaran-progres"), "$id.txt").delete()
    }
}
