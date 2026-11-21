package id.kodenusa.core.ai

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PemilihKonteksTest {

    private fun k(jalur: String, isi: String, skor: Int = 0) =
        PemilihKonteks.Kandidat(jalur, isi, skor)

    @Test
    fun token_membuang_kata_henti_dan_token_pendek() {
        val hasil = PemilihKonteks.token("Bagaimana cara menambah produk di Kasir?")
        assertTrue(hasil.contains("produk"))
        assertTrue(hasil.contains("kasir"))
        assertTrue(hasil.none { it in setOf("yang", "di", "apa", "bagaimana", "cara") })
    }

    @Test
    fun skor_cocok_dengan_camelcase_dan_snake() {
        val skorJalur = PemilihKonteks.skor("perbaiki kasir", "src/main/java/app/DatabaseKasir.java", "class DatabaseKasir {}")
        assertTrue(skorJalur > 0, "DatabaseKasir harus cocok dengan 'kasir'")
    }

    @Test
    fun pilih_mengisi_anggaran_dari_skor_tertinggi_dan_deterministik() {
        val kandidat = listOf(
            k("a/kecil.java", "isi", 5),
            k("b/besar.java", "x".repeat(200), 10),
            k("c/sama.java", "y".repeat(50), 5),
        )
        val hasil = PemilihKonteks.pilih(kandidat, 400)
        assertEquals(listOf("b/besar.java", "a/kecil.java", "c/sama.java"), hasil.map { it.jalur })
        // Skor sama diurutkan leksikal — panggilan kedua memberi hasil identik.
        val ulang = PemilihKonteks.pilih(kandidat.reversed(), 400)
        assertEquals(hasil.map { it.jalur }, ulang.map { it.jalur })
    }

    @Test
    fun pilih_berhenti_bila_anggaran_habis() {
        val kandidat = (1..10).map { k("f$it.java", "x".repeat(100), 10 - it) }
        val hasil = PemilihKonteks.pilih(kandidat, 250)
        assertTrue(hasil.sumOf { it.isi.length } <= 250)
        assertTrue(hasil.isNotEmpty())
    }

    @Test
    fun wajib_selalu_ikut_walau_skor_nol() {
        val kandidat = listOf(k("lain.java", "abc", 99))
        val hasil = PemilihKonteks.pilih(kandidat, 10_000, wajib = listOf(k("kodenusa.json", "{}", 0)))
        assertTrue(hasil.any { it.jalur == "kodenusa.json" })
    }

    @Test
    fun berkas_skor_nol_tidak_ikut_bila_tak_diperlukan() {
        val kandidat = listOf(k("a.java", "isi tak terkait", 0))
        val hasil = PemilihKonteks.pilih(kandidat, 10_000)
        assertTrue(hasil.isEmpty())
    }
}
