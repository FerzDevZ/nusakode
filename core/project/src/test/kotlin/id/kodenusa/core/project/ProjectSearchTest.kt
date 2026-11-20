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

    @Test
    fun ganti_semua_mengubah_isi_dan_melaporkan_jumlahnya(@TempDir akar: File) {
        File(akar, "Main.java").writeText("int totalHarga = 1;\nint lain = totalHarga + 2;\n")
        File(akar, "Lain.java").writeText("int totalHarga = 3;\n")

        val ringkas = ProjectSearch.ganti(akar, "totalHarga", "hargaAkhir")

        assertEquals(2, ringkas.file)
        assertEquals(3, ringkas.total)
        assertTrue(File(akar, "Main.java").readText().contains("hargaAkhir"))
        assertTrue(File(akar, "Lain.java").readText().contains("hargaAkhir"))
    }

    @Test
    fun ganti_tidak_menyentuh_berkas_tanpa_kecocokan(@TempDir akar: File) {
        File(akar, "Main.java").writeText("tidak ada yang cocok\n")
        val sebelum = File(akar, "Main.java").lastModified()

        val ringkas = ProjectSearch.ganti(akar, "totalHarga", "hargaAkhir")

        assertEquals(0, ringkas.file)
        assertEquals(0, ringkas.total)
        assertEquals(sebelum, File(akar, "Main.java").lastModified())
    }

    @Test
    fun ganti_peka_huruf_supaya_nama_kelas_tidak_ikut_berubah(@TempDir akar: File) {
        File(akar, "Produk.java").writeText("class Produk { int produk; }\n")

        val ringkas = ProjectSearch.ganti(akar, "produk", "barang")

        assertEquals(1, ringkas.total)
        val isi = File(akar, "Produk.java").readText()
        assertTrue(isi.contains("class Produk"))
        assertTrue(isi.contains("int barang"))
    }

    @Test
    fun ganti_kata_kosong_ditolak(@TempDir akar: File) {
        File(akar, "a.txt").writeText("isi")
        val ringkas = ProjectSearch.ganti(akar, "", "x")
        assertEquals(0, ringkas.total)
        assertEquals("isi", File(akar, "a.txt").readText())
    }

    @Test
    fun ganti_kata_sama_dengan_pengganti_tidak_menulis_apa_pun(@TempDir akar: File) {
        File(akar, "a.txt").writeText("sama")
        assertEquals(0, ProjectSearch.ganti(akar, "sama", "sama").total)
    }

    @Test
    fun ganti_melewati_folder_build_dan_git(@TempDir akar: File) {
        File(akar, "build").mkdirs()
        File(akar, "build/hasil.txt").writeText("totalHarga")
        File(akar, ".git").mkdirs()
        File(akar, ".git/HEAD.txt").writeText("totalHarga")
        File(akar, "kode.txt").writeText("totalHarga")

        val ringkas = ProjectSearch.ganti(akar, "totalHarga", "hargaAkhir")

        assertEquals(listOf("kode.txt"), ringkas.berkas.map { it.jalur })
        assertEquals("totalHarga", File(akar, "build/hasil.txt").readText())
    }
}
