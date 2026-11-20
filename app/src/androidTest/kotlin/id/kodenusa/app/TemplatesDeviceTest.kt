package id.kodenusa.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.ZipFile

/**
 * Regresi untuk bug "tombol Buat projek tidak pernah bisa ditekan": daftar template dulu dibaca
 * dengan bentuk JSON yang salah sehingga hasilnya kosong tanpa suara, dan karena template kosong
 * tombol dikunci selamanya. Uji ini memakai aset sungguhan dari APK, bukan berkas tiruan.
 */
@RunWith(AndroidJUnit4::class)
class TemplatesDeviceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val templates = Templates(context)

    @Test
    fun daftar_template_dari_aset_tidak_kosong() {
        val daftar = templates.load().getOrThrow()
        val ids = daftar.map { it.id }
        assertTrue("template bawaan hilang: $ids", ids.containsAll(listOf("halo-dunia", "kasir-warung", "katalog-whatsapp", "web-katalog")))
        assertTrue("setiap template harus punya berkas", daftar.all { it.files.isNotEmpty() })
        assertTrue("judul tidak boleh kosong", daftar.all { it.title.isNotBlank() })
    }

    @Test
    fun template_bisa_disalin_ke_folder_projek() {
        val target = File(context.cacheDir, "uji-template-${System.nanoTime()}/kasir-warung")
        val hasil = templates.create("kasir-warung", target, "Kasir Warung Bu Sri", "id.kodenusa.kasirbusri").getOrThrow()

        val manifest = File(hasil, "kodenusa.json").readText()
        assertTrue(manifest, manifest.contains("\"name\": \"Kasir Warung Bu Sri\""))
        assertTrue(manifest, manifest.contains("\"applicationId\": \"id.kodenusa.kasirbusri\""))
        assertTrue(File(hasil, "src/main/AndroidManifest.xml").readText().contains("id.kodenusa.kasirbusri"))
        target.parentFile?.deleteRecursively()
    }

    @Test
    fun template_web_membawa_aset_html() {
        val target = File(context.cacheDir, "uji-web-${System.nanoTime()}/web-katalog")
        val hasil = templates.create("web-katalog", target, "Katalog Warung", "id.kodenusa.katalog").getOrThrow()
        assertTrue(File(hasil, "src/main/assets/index.html").exists())
        assertTrue(File(hasil, "src/main/assets/gaya.css").exists())
        assertTrue(File(hasil, "src/main/assets/aplikasi.js").exists())
        target.parentFile?.deleteRecursively()
    }

    @Test
    fun projek_kosong_bisa_dibuat_dari_hp() {
        val target = File(context.cacheDir, "uji-kosong-${System.nanoTime()}/kosong")
        val hasil = templates.create(Templates.BLANK_ID, target, "Projek Saya", "id.kodenusa.projeksaya").getOrThrow()
        val paket = File(hasil, "src/main/java/id/kodenusa/projeksaya/MainActivity.java")
        assertTrue("activity harus ada di paket applicationId", paket.exists())
        target.parentFile?.deleteRecursively()
    }

    @Test
    fun aset_template_ikut_masuk_di_dalam_apk() {
        val jumlah = ZipFile(File(context.applicationInfo.sourceDir)).use { zip ->
            zip.entries().asSequence().count { it.name.startsWith("assets/templates/") }
        }
        assertTrue("aset template harus ikut masuk APK aplikasi, dapat $jumlah", jumlah >= 5)
    }
}
