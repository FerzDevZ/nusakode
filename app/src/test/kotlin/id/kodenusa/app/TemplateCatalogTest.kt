package id.kodenusa.app

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.File
import java.nio.file.Files

/**
 * Regresi untuk bug "tombol Buat projek tidak pernah aktif": `index.json` berbentuk
 * `{"templates": [...]}` sementara pembaca lama mengharapkan array telanjang, gagal tanpa suara,
 * lalu daftar template kosong dan tombol terkunci selamanya.
 */
class TemplateCatalogTest {

    private val satuTemplate = """
        {
          "templates": [
            {
              "id": "kasir-warung",
              "title": "Kasir Warung",
              "tagline": "Catat barang",
              "audience": "pemilik warung",
              "files": ["kodenusa.json"]
            }
          ]
        }
    """.trimIndent()

    @Test
    fun `format objek templates dibaca`() {
        val hasil = TemplateCatalog.parse(satuTemplate)
        assertEquals(1, hasil.size)
        assertEquals("kasir-warung", hasil.single().id)
        assertEquals("Kasir Warung", hasil.single().title)
    }

    @Test
    fun `format array lama masih diterima`() {
        val lama = """[{"id":"halo-dunia","title":"Halo Dunia","tagline":"t","audience":"pemula"}]"""
        assertEquals("halo-dunia", TemplateCatalog.parse(lama).single().id)
    }

    @Test
    fun `daftar kosong ditolak dengan pesan jelas`() {
        val galat = assertThrows<IllegalArgumentException> { TemplateCatalog.parse("""{"templates":[]}""") }
        assertTrue(galat.message.orEmpty().contains("tidak berisi"), galat.message.orEmpty())
    }

    @Test
    fun `isi rusak tidak lolos diam-diam`() {
        assertThrows<Exception> { TemplateCatalog.parse("{ bukan json") }
    }

    @Test
    fun `template ganda diambil sekali`() {
        val ganda = """{"templates":[
            {"id":"a","title":"A","tagline":"t","audience":"p"},
            {"id":"a","title":"A lagi","tagline":"t","audience":"p"}
        ]}"""
        assertEquals(1, TemplateCatalog.parse(ganda).size)
    }

    @Test
    fun `projek kosong menghasilkan lima berkas yang siap dibangun`() {
        val folder = Files.createTempDirectory("kodenusa-kosong").toFile()
        val target = File(folder, "projek-saya")
        val hasil = BlankProject.create(target, "Warung Bu Sri", "id.kodenusa.warungbusri")

        val manifest = File(hasil, "kodenusa.json")
        assertTrue(manifest.exists(), "kodenusa.json harus ada")
        assertTrue(manifest.readText().contains("\"applicationId\": \"id.kodenusa.warungbusri\""))
        val androidManifest = File(hasil, "src/main/AndroidManifest.xml").readText()
        assertTrue(androidManifest.contains("android:name=\".MainActivity\""), androidManifest)
        assertTrue(File(hasil, "src/main/java/id/kodenusa/warungbusri/MainActivity.java").exists())
        assertTrue(File(hasil, "src/main/res/layout/activity_main.xml").exists())
        assertTrue(File(hasil, "src/main/res/values/strings.xml").readText().contains("Warung Bu Sri"))
        folder.deleteRecursively()
    }

    @Test
    fun `nama dengan karakter xml diloloskan`() {
        val folder = Files.createTempDirectory("kodenusa-xml").toFile()
        val hasil = BlankProject.create(File(folder, "p"), "Toko A & B <jaya>", "id.kodenusa.toko")
        val strings = File(hasil, "src/main/res/values/strings.xml").readText()
        assertTrue(strings.contains("Toko A &amp; B &lt;jaya&gt;"), strings)
        folder.deleteRecursively()
    }
}
