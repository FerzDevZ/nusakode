package id.kodenusa.core.build

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class SitusPwaTest {

    private fun situs(akar: File) {
        File(akar, "index.html").writeText("<!doctype html><html><body><h1>Warung</h1></body></html>")
        File(akar, "gaya.css").writeText("body{background:#14110f}")
        File(akar, "aplikasi.js").writeText("console.log('halo')")
        File(akar, "catatan.txt").writeText("bukan aset web")
    }

    @Test
    fun menulis_manifest_dan_service_worker_di_akar(@TempDir akar: File) {
        situs(akar)
        val hasil = SitusPwa.siapkan(akar, nama = "Warung Bu Sri")

        assertTrue(File(akar, "manifest.json").exists())
        assertTrue(File(akar, "service-worker.js").exists())
        assertEquals(listOf("manifest.json", "service-worker.js"), hasil.dibuat.sorted())
        val manifest = File(akar, "manifest.json").readText()
        assertTrue(manifest.contains("\"name\": \"Warung Bu Sri\""))
        assertTrue(manifest.contains("\"display\": \"standalone\""))
        assertTrue(manifest.contains("\"start_url\": \"./\""))
    }

    @Test
    fun service_worker_hanya_memuat_aset_web_dan_melewati_txt(@TempDir akar: File) {
        situs(akar)
        SitusPwa.siapkan(akar, nama = "Warung")
        val pekerja = File(akar, "service-worker.js").readText()

        assertTrue(pekerja.contains("\"./index.html\""))
        assertTrue(pekerja.contains("\"./gaya.css\""))
        assertTrue(pekerja.contains("\"./aplikasi.js\""))
        assertFalse(pekerja.contains("catatan.txt"), "catatan.txt bukan aset situs")
        assertFalse(pekerja.contains("\"./service-worker.js\""), "service worker tidak mendaftarkan dirinya sendiri")
    }

    @Test
    fun berkas_yang_sudah_ada_tidak_ditimpa(@TempDir akar: File) {
        situs(akar)
        File(akar, "manifest.json").writeText("{\"name\":\"punya pengguna\"}")
        File(akar, "service-worker.js").writeText("// milik pengguna")

        val hasil = SitusPwa.siapkan(akar, nama = "Warung")

        assertEquals("{\"name\":\"punya pengguna\"}", File(akar, "manifest.json").readText())
        assertEquals("// milik pengguna", File(akar, "service-worker.js").readText())
        assertTrue(hasil.dibuat.isEmpty())
        assertEquals(listOf("manifest.json", "service-worker.js"), hasil.dilewati.sorted())
    }

    @Test
    fun pendaftar_service_worker_hanya_disispkan_sekali(@TempDir akar: File) {
        situs(akar)
        val pertama = SitusPwa.siapkan(akar)
        assertTrue(pertama.indexDiubah)
        val isiSetelah = File(akar, "index.html").readText()
        assertEquals(1, Regex("serviceWorker.register").findAll(isiSetelah).count())
        assertTrue(isiSetelah.indexOf("serviceWorker.register") < isiSetelah.indexOf("</body>"))

        File(akar, "manifest.json").delete()
        File(akar, "service-worker.js").delete()
        val kedua = SitusPwa.siapkan(akar)
        assertFalse(kedua.indexDiubah, "index.html tidak boleh disisipi dua kali")
        assertEquals(1, Regex("serviceWorker.register").findAll(File(akar, "index.html").readText()).count())
    }

    @Test
    fun ikon_didaftarkan_hanya_bila_berkasnya_ada(@TempDir akar: File) {
        situs(akar)
        File(akar, "ikon-192.png").writeBytes(ByteArray(16))
        val hasil = SitusPwa.siapkan(akar)
        val manifest = File(akar, "manifest.json").readText()

        assertTrue(manifest.contains("\"src\": \"ikon-192.png\""))
        assertTrue(manifest.contains("\"sizes\": \"192\""))
        assertTrue(manifest.contains("image/png"))
        assertEquals(null, hasil.catatan)
    }

    @Test
    fun tanpa_ikon_diberi_catatan_bukan_dibuatkan_berkas_palsu(@TempDir akar: File) {
        situs(akar)
        val hasil = SitusPwa.siapkan(akar)
        assertFalse(File(akar, "manifest.json").readText().contains("\"icons\""))
        assertTrue(hasil.catatan.orEmpty().contains("ikon"))
    }

    @Test
    fun html_tanpa_body_tetap_mendapat_pendaftar(@TempDir akar: File) {
        File(akar, "index.html").writeText("<h1>tanpa body</h1>")
        val hasil = SitusPwa.siapkan(akar)
        assertTrue(hasil.indexDiubah)
        assertTrue(File(akar, "index.html").readText().contains("serviceWorker.register"))
    }

    @Test
    fun situs_tanpa_index_tetap_menulis_manifest(@TempDir akar: File) {
        File(akar, "gaya.css").writeText("body{}")
        val hasil = SitusPwa.siapkan(akar)
        assertTrue(File(akar, "manifest.json").exists())
        assertFalse(hasil.indexDiubah)
        assertTrue(hasil.catatan.orEmpty().contains("index.html"))
    }

    @Test
    fun folder_build_dan_git_tidak_ikut_dicache(@TempDir akar: File) {
        situs(akar)
        File(akar, "build").mkdirs()
        File(akar, "build/salinan.html").writeText("x")
        File(akar, ".git").mkdirs()
        File(akar, ".git/HEAD").writeText("ref")
        val pekerja = run {
            SitusPwa.siapkan(akar)
            File(akar, "service-worker.js").readText()
        }
        assertFalse(pekerja.contains("build/salinan.html"))
        assertFalse(pekerja.contains(".git/HEAD"))
    }

    @Test
    fun aset_raksasa_tidak_diprecache(@TempDir akar: File) {
        situs(akar)
        File(akar, "video.mp4").writeBytes(ByteArray(3_000_000))
        val pekerja = run {
            SitusPwa.siapkan(akar)
            File(akar, "service-worker.js").readText()
        }
        assertFalse(pekerja.contains("video.mp4"), "berkas > 2 MB tidak diprecache")
    }
}
