package id.kodenusa.core.project

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ProjectSearchTest {

    private fun projek(akar: File) {
        File(akar, "src/main/java/app").mkdirs()
        File(akar, "web").mkdirs()
        File(akar, "src/main/java/app/MainActivity.java")
            .writeText("class MainActivity {\n    int totalHarga = 0;\n}\n")
        File(akar, "web/index.html").writeText("<h1>Toko Warung</h1>\n<p>totalHarga</p>\n")
        File(akar, "build/junk.txt").apply { parentFile.mkdirs() }.writeText("totalHarga di folder build\n")
        File(akar, "gambar.png").writeText("totalHarga biner")
    }

    @Test
    fun mencari_di_semua_berkas_teks(@TempDir akar: File) {
        projek(akar)

        val hasil = ProjectSearch.cari(akar, "totalHarga")

        assertTrue(hasil.any { it.jalur == "src/main/java/app/MainActivity.java" && it.baris == 2 })
        assertTrue(hasil.any { it.jalur == "web/index.html" && it.baris == 2 })
    }

    @Test
    fun melewati_folder_build_dan_berkas_biner(@TempDir akar: File) {
        projek(akar)

        val hasil = ProjectSearch.cari(akar, "totalHarga")

        assertTrue(hasil.none { it.jalur.startsWith("build/") })
        assertTrue(hasil.none { it.jalur.endsWith(".png") })
    }

    @Test
    fun tanpa_membedakan_huruf_besar_kecil(@TempDir akar: File) {
        projek(akar)
        assertEquals(2, ProjectSearch.cari(akar, "TOTALHARGA").size)
    }

    @Test
    fun kueri_kosong_mengembalikan_kosong(@TempDir akar: File) {
        projek(akar)
        assertTrue(ProjectSearch.cari(akar, "   ").isEmpty())
    }

    @Test
    fun hasil_dibatasi(@TempDir akar: File) {
        File(akar, "a.txt").writeText((1..50).joinToString("\n") { "kata $it" })
        assertEquals(10, ProjectSearch.cari(akar, "kata", maksHasil = 10).size)
    }
}
