package id.kodenusa.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import id.kodenusa.core.build.SitusGagalException
import id.kodenusa.core.build.SitusStatis
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Membuktikan jalur "situs murni" bekerja di HP: template tanpa AndroidManifest bisa disusun
 * menjadi folder situs yang siap diunggah, tanpa pernah menyentuh pipeline APK.
 */
@RunWith(AndroidJUnit4::class)
class SitusDeviceTest {

    private val konteks = InstrumentationRegistry.getInstrumentation().targetContext
    private val templates = Templates(konteks)

    private fun buatProjek(nama: String): File =
        File(konteks.cacheDir, "situs-uji-$nama-${System.nanoTime()}")

    @Test
    fun template_situs_murni_disusun_jadi_folder_siap_terbit() {
        val target = buatProjek("murni")

        val projek = templates.create("situs-murni", target, "Warung Bu Sri", "id.kodenusa.warungbusri")
            .getOrThrow()

        assertFalse("template situs murni tidak boleh membawa AndroidManifest",
            File(projek, "src/main/AndroidManifest.xml").exists())

        val hasil = SitusStatis.susun(projek)

        assertTrue(hasil.adaSitus)
        assertTrue(File(hasil.folder, "index.html").isFile)
        assertTrue(File(hasil.folder, "gaya.css").isFile)
        assertTrue(File(hasil.folder, "aplikasi.js").isFile)
        assertTrue(hasil.totalByte > 0)
    }

    @Test
    fun template_web_katalog_juga_bisa_terbit_tanpa_apk() {
        val projek = templates.create("web-katalog", buatProjek("katalog"), "Katalog Warung", "id.kodenusa.katalog")
            .getOrThrow()

        val hasil = SitusStatis.susun(projek)

        assertTrue(hasil.adaSitus)
        assertTrue(hasil.berkas.any { it.jalur.endsWith(".css") })
    }

    @Test
    fun projek_aplikasi_biasa_ditolak_dengan_jelas() {
        val projek = templates.create("halo-dunia", buatProjek("aplikasi"), "Halo", "id.kodenusa.halo")
            .getOrThrow()

        val galat = runCatching { SitusStatis.susun(projek) }.exceptionOrNull()

        assertTrue(galat is SitusGagalException)
        assertTrue(galat!!.message!!.contains("index.html"))
    }
}
