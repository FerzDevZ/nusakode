package id.kodenusa.editor

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Uji murni logika gutter baru — pengganti teknik TextView-raksasa + translationY yang
 * menyebabkan "nomor cuma 1 / kosong / bergeser / kotak hitam di bawahnya".
 */
class GutterModelTest {

    /** Editor tiruan: setiap baris visual setinggi 20 px, padding atas 10, tanpa scroll. */
    private fun baris20(i: Int) = 10f + i * 20f

    @Test
    fun menomori_semua_baris_dari_1() {
        val teks = "a\nb\nc"
        val item = GutterModel.hitung(
            lineCount = 3,
            yAtas = ::baris20,
            awalBaris = { i -> if (i == 0) 0 else teks.indexOf('\n', 0).let { hitungAwal(teks, i) } },
            teks = teks,
            tinggiView = 1000f,
            barisKursor = 0,
        )
        assertEquals(listOf("1", "2", "3"), item.map { it.teks })
    }

    @Test
    fun baris_di_luar_area_terlihat_tidak_dilukis() {
        val teks = (1..100).joinToString("\n") { "x" }
        val item = GutterModel.hitung(
            lineCount = 100,
            yAtas = ::baris20,
            awalBaris = { i -> awalBarisKe(teks, i) },
            teks = teks,
            tinggiView = 100f, // hanya ~5 baris muat
            barisKursor = 0,
        )
        assertTrue(item.size in 5..7, "harus sedikit, dapat ${item.size}")
        assertEquals("1", item.first().teks)
    }

    @Test
    fun scroll_menggeser_nomor_yang_terlihat() {
        val teks = (1..100).joinToString("\n") { "x" }
        var geser = 0f
        val item = GutterModel.hitung(
            lineCount = 100,
            yAtas = { i -> baris20(i) - geser },
            awalBaris = { i -> awalBarisKe(teks, i) },
            teks = teks,
            tinggiView = 100f,
            barisKursor = 0,
        )
        geser = 20f * 9 // gulir 9 baris
        val sesudahGulir = GutterModel.hitung(
            lineCount = 100,
            yAtas = { i -> baris20(i) - geser },
            awalBaris = { i -> awalBarisKe(teks, i) },
            teks = teks,
            tinggiView = 100f,
            barisKursor = 0,
        )
        // Baris pertama yang menembus area terlihat adalah baris ke-9 (nomor 9) — baris
        // yang terpotong di tepi atas tetap dinomori, dan nomor 1 sudah tidak ada.
        assertEquals("9", sesudahGulir.first().teks)
        assertTrue(sesudahGulir.none { it.teks == "1" })
    }

    @Test
    fun baris_sambungan_wrap_tidak_dinomori() {
        // Satu baris logika "halo dunia yang sangat panjang" di-wrap jadi 3 baris visual.
        val teks = "halo dunia yang sangat panjang\nbaris dua"
        // Layout tiruan: baris visual 0..2 adalah sambungan baris logika 1, baris 3 = logika 2.
        val awalVisual = intArrayOf(0, 7, 18, 31)
        val item = GutterModel.hitung(
            lineCount = 4,
            yAtas = ::baris20,
            awalBaris = { i -> awalVisual[i] },
            teks = teks,
            tinggiView = 1000f,
            barisKursor = 0,
        )
        // Hanya "1" dan "2" — baris sambungan kosong.
        assertEquals(listOf("1", "2"), item.map { it.teks })
    }

    @Test
    fun nomor_baris_kursor_disorot() {
        val teks = "a\nb\nc"
        val item = GutterModel.hitung(
            lineCount = 3,
            yAtas = ::baris20,
            awalBaris = { i -> awalBarisKe(teks, i) },
            teks = teks,
            tinggiView = 1000f,
            barisKursor = 2,
        )
        assertTrue(item.first { it.teks == "2" }.sorot)
        assertTrue(item.first { it.teks == "1" }.let { !it.sorot })
    }

    @Test
    fun lebar_gutter_mengikuti_jumlah_digit() {
        val duaDigit = GutterModel.lebar(10f, 2, 8f, 8f)
        val empatDigit = GutterModel.lebar(10f, 4, 8f, 8f)
        assertEquals(36f, duaDigit)
        assertEquals(56f, empatDigit)
        // Minimum dua digit supaya gutter tidak berkedip antara 1 digit dan 2 digit.
        assertEquals(36f, GutterModel.lebar(10f, 1, 8f, 8f))
    }

    @Test
    fun hitung_baris_logika_dari_offset() {
        assertEquals(1, GutterModel.hitungBarisLogika("a\nb\nc", 0))
        assertEquals(2, GutterModel.hitungBarisLogika("a\nb\nc", 3))
        assertEquals(3, GutterModel.hitungBarisLogika("a\nb\nc", 5))
    }

    // ---------- helper ----------

    private fun hitungAwal(teks: String, baris: Int): Int {
        var awal = 0
        repeat(baris) { awal = teks.indexOf('\n', awal) + 1 }
        return awal
    }

    private fun awalBarisKe(teks: String, baris: Int): Int = hitungAwal(teks, baris)
}
