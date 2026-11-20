package id.kodenusa.app

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Uji bungkus/buka projek. Prioritas uji ada pada penolakan: zip dari luar adalah masukan yang
 * tidak dipercaya, dan satu entri jahat bisa menulis ke luar folder projek.
 */
class ProjectArchiveTest {

    @TempDir
    lateinit var sementara: File

    private fun projek(nama: String = "tokoku"): File {
        val dir = File(sementara, nama).apply { mkdirs() }
        File(dir, "kodenusa.json").writeText("{\"name\":\"$nama\"}")
        File(dir, "src/main/java/app").mkdirs()
        File(dir, "src/main/java/app/MainActivity.java").writeText("class MainActivity {}")
        File(dir, "build/dex").mkdirs()
        File(dir, "build/dex/sampah.dex").writeText("tidak perlu dibawa")
        return dir
    }

    private fun zip(nama: String, entri: Map<String, String>): File {
        val file = File(sementara, nama)
        ZipOutputStream(file.outputStream()).use { zip ->
            entri.forEach { (path, isi) ->
                zip.putNextEntry(ZipEntry(path))
                zip.write(isi.toByteArray())
                zip.closeEntry()
            }
        }
        return file
    }

    @Test
    fun ekspor_membawa_sumber_dan_melewati_folder_build() {
        val dir = projek()
        val target = File(sementara, "keluar.zip")

        val hasil = ProjectArchive.export(dir, target)

        assertEquals(2, hasil.getOrThrow())
        val isi = bacaEntri(target)
        assertTrue(isi.contains("kodenusa.json"))
        assertTrue(isi.contains("src/main/java/app/MainActivity.java"))
        assertFalse(isi.any { it.startsWith("build/") })
    }

    @Test
    fun impor_zip_ekspor_menghasilkan_projek_yang_sama() {
        val dir = projek()
        val zip = File(sementara, "keluar.zip")
        ProjectArchive.export(dir, zip)
        val tujuan = File(sementara, "projek").apply { mkdirs() }

        val hasil = ProjectArchive.import(zip, tujuan).getOrThrow()

        assertTrue(File(hasil, "kodenusa.json").isFile)
        assertTrue(File(hasil, "src/main/java/app/MainActivity.java").isFile)
    }

    @Test
    fun zip_yang_membungkus_satu_folder_tetap_dikenali() {
        val zip = zip(
            "bungkus.zip",
            mapOf("tokoku/kodenusa.json" to "{}", "tokoku/src/main/java/app/A.java" to "class A {}"),
        )
        val tujuan = File(sementara, "projek").apply { mkdirs() }

        val hasil = ProjectArchive.import(zip, tujuan).getOrThrow()

        assertTrue(File(hasil, "kodenusa.json").isFile)
    }

    @Test
    fun zip_tanpa_kodenusa_json_ditolak_dan_tidak_meninggalkan_sampah() {
        val zip = zip("asing.zip", mapOf("readme.txt" to "bukan projek"))
        val tujuan = File(sementara, "projek").apply { mkdirs() }

        val hasil = ProjectArchive.import(zip, tujuan)

        assertTrue(hasil.isFailure)
        assertTrue(hasil.exceptionOrNull()?.message.orEmpty().contains("tidak berisi projek KodeNusa"))
        assertEquals(0, tujuan.listFiles().orEmpty().size)
    }

    @Test
    fun entri_yang_keluar_folder_ditolak() {
        val zip = zip("jahat.zip", mapOf("kodenusa.json" to "{}", "../../curian.txt" to "isi"))
        val tujuan = File(sementara, "projek").apply { mkdirs() }

        val hasil = ProjectArchive.import(zip, tujuan)

        assertTrue(hasil.isFailure)
        assertFalse(File(sementara.parentFile, "curian.txt").exists())
    }

    @Test
    fun nama_folder_bentrok_diberi_akhiran() {
        val zip = zip("tokoku.zip", mapOf("kodenusa.json" to "{}"))
        val tujuan = File(sementara, "projek").apply { mkdirs() }
        File(tujuan, "tokoku").mkdirs()

        val hasil = ProjectArchive.import(zip, tujuan).getOrThrow()

        assertEquals("tokoku-2", hasil.name)
    }

    private fun bacaEntri(zip: File): List<String> = java.util.zip.ZipFile(zip).use { berkas ->
        berkas.entries().toList().map { it.name }
    }
}
