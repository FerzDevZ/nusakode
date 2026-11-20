package id.kodenusa.core.git

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream

/**
 * Penulisan badan unggahan bertahap (dipakai unggah APK 32 MB).
 *
 * Kenapa diuji terpisah: tanpa kabar progres, unggahan 32 MB terlihat seperti aplikasi menggantung.
 * Yang harus benar: **semua** bita terkirim, laporan naik monoton, dan laporan terakhir = ukuran.
 */
class AliranUnggahTest {

    @Test
    fun semua_bita_terkirim_dan_laporan_berakhir_di_ukuran_penuh() {
        val isi = ByteArray(200_000) { (it % 251).toByte() }
        val keluar = ByteArrayOutputStream()
        val laporan = mutableListOf<Long>()

        AliranUnggah.tulis(keluar, isi) { laporan += it }

        assertTrue(keluar.toByteArray().contentEquals(isi), "isi yang ditulis harus identik")
        assertEquals(isi.size.toLong(), laporan.last())
        assertEquals(laporan.sorted(), laporan, "laporan harus naik monoton")
        assertTrue(laporan.size >= 4, "200 KB harus terbagi menjadi beberapa potongan")
    }

    @Test
    fun badan_kosong_tidak_melempar_dan_tidak_melapor() {
        val laporan = mutableListOf<Long>()
        AliranUnggah.tulis(ByteArrayOutputStream(), ByteArray(0)) { laporan += it }
        assertTrue(laporan.isEmpty())
    }

    @Test
    fun potongan_lebih_kecil_dari_satu_bita_tetap_terkirim_utuh() {
        val isi = ByteArray(3) { 7 }
        val keluar = ByteArrayOutputStream()
        AliranUnggah.tulis(keluar, isi) { }
        assertEquals(3, keluar.toByteArray().size)
    }
}
