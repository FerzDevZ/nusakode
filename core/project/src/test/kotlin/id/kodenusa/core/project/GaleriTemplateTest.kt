package id.kodenusa.core.project

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Uji galeri template komunitas (docs/14 4.4): verifikasi SHA/ukuran + proteksi zip-slip. */
class GaleriTemplateTest {

    private fun zipIsi(berkas: Map<String, String>): ByteArray {
        val keluar = ByteArrayOutputStream()
        ZipOutputStream(keluar).use { zip ->
            berkas.forEach { (jalur, isi) ->
                zip.putNextEntry(ZipEntry(jalur))
                zip.write(isi.toByteArray())
                zip.closeEntry()
            }
        }
        return keluar.toByteArray()
    }

    @Test
    fun baca_zip_mengembalikan_isi_sesuai_jalur() {
        val isi = zipIsi(mapOf("kodenusa.json" to "{}", "src/app/MainActivity.java" to "class X {}"))
        val hasil = GaleriTemplate.bacaZip(isi)
        assertEquals(2, hasil.size)
        assertEquals("class X {}", hasil["src/app/MainActivity.java"])
    }

    @Test
    fun baca_zip_menolak_zip_slip() {
        val isi = zipIsi(mapOf("../keluar.txt" to "x"))
        val galat = assertThrows<IllegalArgumentException> { GaleriTemplate.bacaZip(isi) }
        assertTrue(galat.message!!.contains("tidak aman"))
    }

    @Test
    fun baca_zip_menolak_jalur_absolut() {
        val isi = zipIsi(mapOf("/etc/penampungan" to "x"))
        assertThrows<IllegalArgumentException> { GaleriTemplate.bacaZip(isi) }
    }

    @Test
    fun baca_zip_kosong_ditolak() {
        assertThrows<IllegalArgumentException> { GaleriTemplate.bacaZip(zipIsi(emptyMap())) }
    }

    @Test
    fun sha256_konsisten_dan_huruf_kecil() {
        val isi = "konten tetap".toByteArray()
        val satu = GaleriTemplate.sha256(isi)
        val dua = GaleriTemplate.sha256(isi)
        assertEquals(satu, dua)
        assertEquals(satu, satu.lowercase())
        assertEquals(64, satu.length)
    }

    @Test
    fun index_json_dengan_kunci_tambahan_tetap_terbaca() {
        val teks = """
            [
              {"id":"kasir-mini","nama":"Kasir Mini","deskripsi":"Kasir sederhana",
               "sha256":"abc","ukuran":123,"url":"https://raw.example/kasir.zip",
               "penulis":"Komunitas","versi":2}
            ]
        """.trimIndent()
        val daftar = Json { ignoreUnknownKeys = true }.decodeFromString<List<GaleriTemplate.GaleriItem>>(teks)
        assertEquals(1, daftar.size)
        assertEquals("Kasir Mini", daftar[0].nama)
    }
}
