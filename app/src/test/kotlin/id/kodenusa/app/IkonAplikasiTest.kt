package id.kodenusa.app

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Logika murni ikon aplikasi (docs/14 2.2) tanpa android.graphics. */
class IkonAplikasiTest {

    @Test
    fun warna_latar_menerima_enam_dan_delapan_digit() {
        assertEquals(0xFF0E7490.toInt(), IkonAplikasi.warnaLatar("#0E7490"))
        assertEquals(0x80FF0000.toInt(), IkonAplikasi.warnaLatar("#80FF0000"))
    }

    @Test
    fun warna_salah_kembali_ke_bawaan_bukan_crash() {
        assertEquals(IkonAplikasi.warnaLatar(null), IkonAplikasi.warnaLatar("merah"))
        assertEquals(IkonAplikasi.warnaLatar(""), IkonAplikasi.warnaLatar("#12345"))
    }

    @Test
    fun huruf_awal_ambil_huruf_pertama_dan_kapital() {
        assertEquals("K", IkonAplikasi.hurufAwal("kasir warung"))
        assertEquals("3", IkonAplikasi.hurufAwal("3in1 tools"))
        assertEquals("A", IkonAplikasi.hurufAwal("  "))
        assertEquals("A", IkonAplikasi.hurufAwal("!@#"))
    }

    @Test
    fun warna_teks_ikut_kontras_latar() {
        assertTrue(IkonAplikasi.warnaTeks(0xFF111111.toInt()) == 0xFFFFFFFF.toInt(), "latar gelap → teks putih")
        assertTrue(IkonAplikasi.warnaTeks(0xFFFFF7ED.toInt()) != 0xFFFFFFFF.toInt(), "latar terang → teks gelap")
    }

    /** docs/14 2.2: semua 8 pilihan warna wizard harus kontras ≥ 4,5:1 terhadap teksnya. */
    @Test
    fun semua_pilihan_warna_wizard_kontras_4_5() {
        for (pilihan in IkonAplikasi.PILIHAN_WARNA) {
            val latar = IkonAplikasi.warnaLatar(pilihan.hex)
            val teks = IkonAplikasi.warnaTeks(latar)
            val rasio = IkonAplikasi.kontras(latar, teks)
            assertTrue(
                rasio >= 4.5,
                "${pilihan.label} (${pilihan.hex}) kontras $rasio < 4,5",
            )
        }
    }

    @Test
    fun manifest_tanpa_ikon_dipatch_dengan_android_icon() {
        val sumber = "<manifest><application android:label=\"Halo\">\n</application></manifest>"
        assertTrue(IkonAplikasi.butuhPatch(sumber))
        val hasil = IkonAplikasi.patch(sumber)
        assertTrue(hasil.contains("android:icon=\"@mipmap/ic_launcher\""))
        assertTrue(hasil.contains("android:label=\"Halo\""))
    }

    @Test
    fun manifest_yang_sudah_punya_ikon_tidak_disentuh() {
        val sumber = "<application android:icon=\"@mipmap/punya_sendiri\">"
        assertFalse(IkonAplikasi.butuhPatch(sumber))
        assertEquals(sumber, IkonAplikasi.patch(sumber))
    }
}
