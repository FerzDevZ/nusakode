package id.kodenusa.editor

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Lengkapi kata (docs/14 2.5): sumber kandidat, urutan deterministik, batas 6, tanpa duplikat.
 * Semua di JVM karena PenyediaKandidat fungsi murni — tidak menyentuh Android sama sekali.
 */
class PenyediaKandidatTest {

    @Test
    fun kurang_dari_dua_huruf_tidak_mengusulkan_apapun() {
        assertTrue(PenyediaKandidat.kandidat("System out", "S", Language.JAVA).isEmpty())
        assertTrue(PenyediaKandidat.kandidat("System out", "", Language.JAVA).isEmpty())
    }

    @Test
    fun simbol_bukan_kata_tidak_mengusulkan_apapun() {
        // Pengguna baru mengetik tanda baca: panel tidak boleh muncul mengganggu.
        assertTrue(PenyediaKandidat.kandidat("System", ";", Language.JAVA).isEmpty())
        assertTrue(PenyediaKandidat.kandidat("System", "a.b", Language.JAVA).isEmpty())
    }

    @Test
    fun kata_dari_berkas_diusulkan_dengan_awalan_peka_kapital_tidak() {
        val teks = "System.out.println(status); int jumlah = 0;"
        val hasil = PenyediaKandidat.kandidat(teks, "sys", Language.JAVA)
        assertEquals(listOf("System"), hasil)
    }

    @Test
    fun kata_milik_pengguna_lebih_dulu_dari_kata_kunci() {
        val teks = "int syarat = 0;"
        val hasil = PenyediaKandidat.kandidat(teks, "sy", Language.JAVA)
        // Kata milik pengguna di depan; "synchronized" ikut karena kata kunci juga dicari.
        assertEquals(listOf("syarat", "synchronized"), hasil)
    }

    @Test
    fun kandidat_yang_sama_dengan_ketikan_tidak_diulang() {
        val hasil = PenyediaKandidat.kandidat("System", "System", Language.JAVA)
        assertTrue(hasil.none { it.equals("System", ignoreCase = true) })
    }

    @Test
    fun maksimal_enam_kandidat_dan_tanpa_duplikat() {
        val teks = (1..20).joinToString(" ") { "namaVariabel$it" }
        val hasil = PenyediaKandidat.kandidat(teks, "nama", Language.JAVA)
        assertEquals(6, hasil.size)
        assertEquals(hasil.size, hasil.toSet().size)
    }

    @Test
    fun kata_kunci_java_muncul_untuk_awalan_pr_dan_pu() {
        val hasilPr = PenyediaKandidat.kandidat("", "pr", Language.JAVA)
        assertTrue(hasilPr.contains("private"))
        assertTrue(hasilPr.contains("protected"))
        val hasilPu = PenyediaKandidat.kandidat("", "pu", Language.JAVA)
        assertTrue(hasilPu.contains("public"))
    }

    @Test
    fun bahasa_xml_memakai_kata_kuncinya_sendiri() {
        val hasil = PenyediaKandidat.kandidat("", "and", Language.XML)
        assertTrue(hasil.contains("android"))
    }

    @Test
    fun sisa_menghitung_bagian_belakang_dan_kosong_kalau_kapital_beda() {
        assertEquals("tem", PenyediaKandidat.sisa("System", "Sys"))
        assertEquals("", PenyediaKandidat.sisa("System", "sys"))
    }
}
