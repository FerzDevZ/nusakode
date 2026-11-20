package id.kodenusa.app

import id.kodenusa.app.ui.Perintah
import id.kodenusa.app.ui.daftarPerintah
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Uji JVM daftar perintah palet: pintasan tema harus bisa ditemukan lewat kata kunci singkat
 ("tema", "gelap", "terang", "sistem") tanpa membuka layar Tema.
 */
class DaftarPerintahTest {

    private fun semua(): List<Perintah> = daftarPerintah(
        onSimpan = {}, onCari = {}, onBuild = {}, onSitus = {}, onPratinjau = {},
        onBerkasBaru = {}, onImpor = {},
        onTemaGelap = {}, onTemaTerang = {}, onTemaSistem = {},
        webMurni = false, adaSitus = false,
    )

    private fun cari(kata: String): List<Perintah> {
        val kataLower = kata.lowercase()
        return semua().filter {
            it.judul.lowercase().contains(kataLower) || it.alias.any { a -> a.contains(kataLower) }
        }
    }

    @Test
    fun `kata_tema_menampilkan_tiga_pintasan_tema`() {
        val hasil = cari("tema")
        assertTrue(hasil.any { it.id == "tema-gelap" }, "pintasan tema gelap harus ada")
        assertTrue(hasil.any { it.id == "tema-terang" }, "pintasan tema terang harus ada")
        assertTrue(hasil.any { it.id == "tema-sistem" }, "pintasan ikut sistem harus ada")
    }

    @Test
    fun `kata_gelap_dan_terang_menemukan_perintahnya`() {
        assertTrue(cari("gelap").any { it.id == "tema-gelap" })
        assertTrue(cari("terang").any { it.id == "tema-terang" })
        assertTrue(cari("sistem").any { it.id == "tema-sistem" })
    }

    @Test
    fun `perintah_lama_tetap_ada_setelah_pintasan_tema_ditambah`() {
        val semua = semua()
        assertTrue(semua.any { it.id == "simpan" })
        assertTrue(semua.any { it.id == "build" })
        assertTrue(semua.any { it.id == "cari" })
    }
}
