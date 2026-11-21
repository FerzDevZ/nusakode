package id.kodenusa.core.build

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class SitusStatisTest {

    private fun tulis(akar: File, jalur: String, isi: String = "x"): File =
        File(akar, jalur).apply {
            parentFile?.mkdirs()
            writeText(isi)
        }

    @Test
    fun menyusun_situs_dari_folder_web(@TempDir sementara: File) {
        val projek = File(sementara, "projek").apply { mkdirs() }
        tulis(projek, "web/index.html", "<h1>Halo</h1>")
        tulis(projek, "web/gaya.css", "body{}")
        tulis(projek, "web/js/aplikasi.js", "console.log(1)")
        tulis(projek, "web/gambar/logo.svg", "<svg/>")

        val hasil = SitusStatis.susun(projek)

        assertEquals(4, hasil.berkas.size)
        assertTrue(hasil.adaSitus)
        assertEquals(1, hasil.jumlahHtml)
        assertTrue(File(hasil.folder, "js/aplikasi.js").isFile)
        assertTrue(File(hasil.folder, "gambar/logo.svg").isFile)
        assertTrue(hasil.totalByte > 0)
    }

    @Test
    fun memakai_assets_bila_tidak_ada_folder_web(@TempDir sementara: File) {
        val projek = File(sementara, "projek").apply { mkdirs() }
        tulis(projek, "src/main/assets/index.html", "<h1>Aset</h1>")

        val hasil = SitusStatis.susun(projek)

        assertTrue(hasil.adaSitus)
        assertEquals("index.html", hasil.berkas.single().jalur)
    }

    @Test
    fun projek_tanpa_index_ditolak_dengan_pesan_jelas(@TempDir sementara: File) {
        val projek = File(sementara, "projek").apply { mkdirs() }
        tulis(projek, "web/halaman.html", "<h1>Bukan index</h1>")
        tulis(projek, "src/main/assets/logo.png", "biner")

        val galat = assertThrows(SitusGagalException::class.java) { SitusStatis.susun(projek) }

        assertTrue(galat.message!!.contains("web/index.html"))
    }

    @Test
    fun index_di_subfolder_ditolak(@TempDir sementara: File) {
        val projek = File(sementara, "projek").apply { mkdirs() }
        tulis(projek, "web/pages/index.html", "<h1>Salah tempat</h1>")

        val galat = assertThrows(SitusGagalException::class.java) { SitusStatis.susun(projek) }

        assertTrue(galat.message!!.contains("akar situs"))
    }

    @Test
    fun node_modules_build_dan_berkas_aneh_tidak_ikut_terbit(@TempDir sementara: File) {
        val projek = File(sementara, "projek").apply { mkdirs() }
        tulis(projek, "web/index.html", "<h1>x</h1>")
        tulis(projek, "web/node_modules/lib/index.js", "x")
        tulis(projek, "web/build/sisa.html", "x")
        tulis(projek, "web/berkas.exe", "x")
        tulis(projek, "web/catatan.psd", "x")

        val hasil = SitusStatis.susun(projek)

        assertEquals(listOf("index.html"), hasil.berkas.map { it.jalur })
        assertTrue(hasil.diabaikan.any { it.contains("node_modules") })
        assertTrue(hasil.diabaikan.contains("berkas.exe"))
        assertFalse(File(hasil.folder, "node_modules").exists())
    }

    @Test
    fun hasil_build_lama_dibersihkan(@TempDir sementara: File) {
        val projek = File(sementara, "projek").apply { mkdirs() }
        tulis(projek, "web/index.html", "<h1>baru</h1>")
        val tujuan = File(projek, "build/situs")
        tulis(tujuan, "usang.html", "lama")

        val hasil = SitusStatis.susun(projek)

        assertFalse(File(hasil.folder, "usang.html").exists(), "sisa build lama tidak boleh terbit")
    }

    @Test
    fun folder_hasil_di_dalam_sumber_ditolak(@TempDir sementara: File) {
        val projek = File(sementara, "projek").apply { mkdirs() }
        tulis(projek, "web/index.html", "<h1>x</h1>")

        val galat = assertThrows(SitusGagalException::class.java) {
            SitusStatis.susun(projek, tujuan = File(projek, "web/hasil"))
        }

        assertTrue(galat.message!!.contains("tidak boleh berada di dalam"))
    }

    @Test
    fun jenis_berkas_dikenali_dari_ekstensi() {
        assertEquals("html", SitusStatis.jenisDari("index.html"))
        assertEquals("gambar", SitusStatis.jenisDari("Logo.PNG"))
        assertEquals("font", SitusStatis.jenisDari("inter.woff2"))
        assertNull(SitusStatis.jenisDari("berkas.tidak-dikenal"))
    }
}
