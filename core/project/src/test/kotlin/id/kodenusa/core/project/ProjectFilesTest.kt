package id.kodenusa.core.project

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Uji operasi berkas projek. Yang paling penting di sini bukan jalur bahagia, melainkan
 * penolakan: nama aneh, nama kembar, dan upaya keluar dari folder projek.
 */
class ProjectFilesTest {

    @TempDir
    lateinit var root: File

    private fun gagal(hasil: ProjectFiles.Hasil): String =
        (hasil as? ProjectFiles.Hasil.Gagal)?.pesan ?: error("seharusnya gagal, tetapi: $hasil")

    private fun sukses(hasil: ProjectFiles.Hasil): File =
        (hasil as? ProjectFiles.Hasil.Sukses)?.file ?: error("seharusnya berhasil, tetapi: $hasil")

    @Test
    fun berkas_baru_dibuat_di_folder_yang_dipilih() {
        val src = File(root, "src/main/java/app").apply { mkdirs() }

        val hasil = ProjectFiles.createFile(root, "src/main/java/app", "Halo.java", "public class Halo {}")

        assertEquals(File(src, "Halo.java").canonicalPath, sukses(hasil).canonicalPath)
        assertEquals("public class Halo {}", File(src, "Halo.java").readText())
    }

    @Test
    fun nama_kembar_ditolak_tanpa_menimpa() {
        File(root, "Main.java").writeText("kode lama")

        val hasil = ProjectFiles.createFile(root, "", "Main.java", "kode baru")

        assertTrue(gagal(hasil).contains("sudah ada"))
        assertEquals("kode lama", File(root, "Main.java").readText())
    }

    @Test
    fun nama_dengan_slash_ditolak() {
        val hasil = ProjectFiles.createFile(root, "", "app/Main.java")

        assertTrue(gagal(hasil).contains("tidak boleh memuat"))
        assertTrue(File(root, "app").let { !it.exists() })
    }

    @Test
    fun nama_kosong_dan_titik_di_awal_ditolak() {
        assertTrue(gagal(ProjectFiles.createFile(root, "", "   ")).contains("kosong"))
        assertTrue(gagal(ProjectFiles.createFile(root, "", ".env")).contains("titik"))
    }

    @Test
    fun folder_baru_dibuat_dan_bisa_menampung_berkas() {
        val folder = sukses(ProjectFiles.createFolder(root, "", "komponen"))

        assertTrue(folder.isDirectory)
        assertNotNull(sukses(ProjectFiles.createFile(root, "komponen", "Tombol.java")))
    }

    @Test
    fun ganti_nama_berhasil_dan_nama_kembar_ditolak() {
        File(root, "lama.txt").writeText("isi")
        File(root, "ada.txt").writeText("lain")

        val hasil = ProjectFiles.rename(root, "lama.txt", "baru.txt")

        assertEquals(File(root, "baru.txt").canonicalPath, sukses(hasil).canonicalPath)
        assertTrue(gagal(ProjectFiles.rename(root, "baru.txt", "ada.txt")).contains("sudah dipakai"))
    }

    @Test
    fun hapus_berkas_dan_folder_beserta_isinya() {
        File(root, "catatan.md").writeText("x")
        File(root, "modul/deep").mkdirs()
        File(root, "modul/deep/A.java").writeText("y")

        assertTrue(ProjectFiles.delete(root, "catatan.md") is ProjectFiles.Hasil.Sukses)
        assertTrue(ProjectFiles.delete(root, "modul") is ProjectFiles.Hasil.Sukses)
        assertTrue(!File(root, "catatan.md").exists())
        assertTrue(!File(root, "modul").exists())
    }

    @Test
    fun folder_projek_tidak_bisa_dihapus() {
        val hasil = ProjectFiles.delete(root, "")

        assertTrue(gagal(hasil).contains("Folder projek"))
        assertTrue(root.isDirectory)
    }

    @Test
    fun path_yang_keluar_dari_projek_ditolak() {
        val luar = File(root.parentFile, "rahasia.txt").apply { writeText("jangan disentuh") }

        assertNull(ProjectFiles.safeRelative(root, "../rahasia.txt"))
        assertNull(ProjectFiles.safeRelative(root, "src/../../rahasia.txt"))
        assertTrue(ProjectFiles.delete(root, "../rahasia.txt") is ProjectFiles.Hasil.Gagal)
        assertTrue(luar.exists())
        luar.delete()
    }

    @Test
    fun path_absolut_tidak_dianggap_relatif() {
        val luar = File(root.parentFile, "lain.txt").apply { writeText("x") }

        assertNull(ProjectFiles.safeRelative(root, luar.absolutePath))
        luar.delete()
    }

    @Test
    fun nama_dengan_spasi_di_tepi_dipangkas() {
        val file = sukses(ProjectFiles.createFile(root, "", "  Toko.java  "))

        assertEquals("Toko.java", file.name)
    }

    @Test
    fun kerangka_java_memuat_paket_dan_nama_kelas() {
        val kode = ProjectFiles.javaStarter("app.kedua", "LayarKedua")

        assertTrue(kode.contains("package app.kedua;"))
        assertTrue(kode.contains("public class LayarKedua"))
    }

    @Test
    fun berkas_bisa_dipindah_ke_folder_lain() {
        val folder = sukses(ProjectFiles.createFolder(root, "", "komponen"))
        val berkas = sukses(ProjectFiles.createFile(root, "", "Toko.java", "class Toko {}"))

        val hasil = ProjectFiles.move(root, "Toko.java", "komponen")

        assertTrue(hasil is ProjectFiles.Hasil.Sukses)
        assertTrue(File(folder, "Toko.java").exists())
        assertFalse(berkas.exists())
    }

    @Test
    fun folder_tidak_bisa_dipindah_ke_dalam_dirinya_sendiri() {
        sukses(ProjectFiles.createFolder(root, "", "induk"))
        sukses(ProjectFiles.createFolder(root, "induk", "anak"))

        val hasil = ProjectFiles.move(root, "induk", "induk/anak")

        assertTrue(hasil is ProjectFiles.Hasil.Gagal)
    }

    @Test
    fun pindah_menolak_menimpa_berkas_yang_sudah_ada() {
        sukses(ProjectFiles.createFolder(root, "", "tujuan"))
        sukses(ProjectFiles.createFile(root, "tujuan", "Toko.java", "lama"))
        sukses(ProjectFiles.createFile(root, "", "Toko.java", "baru"))

        val hasil = ProjectFiles.move(root, "Toko.java", "tujuan")

        assertTrue(hasil is ProjectFiles.Hasil.Gagal)
        assertEquals("baru", File(root, "Toko.java").readText())
    }

    @Test
    fun duplikat_berkas_memakai_nama_bersufiks_salinan() {
        sukses(ProjectFiles.createFile(root, "", "gaya.css", "body{}"))

        val pertama = sukses(ProjectFiles.duplicate(root, "gaya.css"))
        val kedua = sukses(ProjectFiles.duplicate(root, "gaya.css"))

        assertEquals("gaya-salinan.css", pertama.name)
        assertEquals("gaya-salinan-2.css", kedua.name)
        assertEquals("body{}", pertama.readText())
    }

    @Test
    fun duplikat_folder_menyalin_seluruh_isinya() {
        sukses(ProjectFiles.createFolder(root, "", "paket"))
        sukses(ProjectFiles.createFile(root, "paket", "A.java", "class A {}"))

        val salinan = sukses(ProjectFiles.duplicate(root, "paket"))

        assertEquals("paket-salinan", salinan.name)
        assertEquals("class A {}", File(salinan, "A.java").readText())
    }

    @Test
    fun pindah_menolak_folder_tujuan_di_luar_projek() {
        sukses(ProjectFiles.createFile(root, "", "Toko.java", ""))
        val luar = File(root.parentFile, "keluar").apply { mkdirs() }

        val hasil = ProjectFiles.move(root, "Toko.java", "../keluar")

        assertTrue(hasil is ProjectFiles.Hasil.Gagal)
        luar.delete()
    }
}
