package id.kodenusa.app

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Riwayat berkas (docs/14 2.8): isi lama tersimpan sebelum ditimpa, maksimal 10 versi (LRU),
 * dan jalur berbahaya ditolak tanpa crash.
 */
class RiwayatBerkasTest {

    @TempDir
    lateinit var akar: File

    @Test
    fun catat_dan_baca_kembali_isi_lama() {
        val berkas = File(akar, "src/main/java/app/MainActivity.java").apply { parentFile.mkdirs() }
        val riwayat = RiwayatBerkas(akar)
        riwayat.catat(berkas, "versi 1")

        assertEquals(listOf(1), riwayat.daftar(berkas).map { it.nomor })
        assertEquals("versi 1", riwayat.isi(berkas, 1))
    }

    @Test
    fun maksimal_sepuluh_versi_yang_tertua_dibuang() {
        val berkas = File(akar, "catatan.txt")
        val riwayat = RiwayatBerkas(akar)

        for (i in 1..12) {
            riwayat.catat(berkas, "isi-$i")
            // jeda 2 ms supaya lastModified benar-benar naik (LRU memakai waktu berkas)
            Thread.sleep(2)
        }

        assertEquals(RiwayatBerkas.MAKS, riwayat.daftar(berkas).size)
        val nomorTersisa = riwayat.daftar(berkas).map { it.nomor }
        assertTrue(nomorTersisa.none { it <= 2 }, "v2 dan di bawahnya harus sudah dibuang (10 terbaru = v3..v12)")
        assertEquals("isi-12", riwayat.isi(berkas, 12))
        assertNull(riwayat.isi(berkas, 1))
    }

    @Test
    fun berkas_di_luar_projek_ditolak_tanpa_crash() {
        val luar = File(akar.parentFile, "di-luar.txt").apply { writeText("isi") }
        val riwayat = RiwayatBerkas(akar)

        riwayat.catat(luar, "isi") // tidak boleh melempar, tidak boleh menulis apa pun
        assertTrue(riwayat.daftar(luar).isEmpty())
        assertNull(riwayat.isi(luar, 1))
        assertTrue(File(akar, RiwayatBerkas.FOLDER).listFiles().isNullOrEmpty())
    }

    @Test
    fun isi_versi_yang_tidak_ada_mengembalikan_null() {
        val berkas = File(akar, "a.txt")
        val riwayat = RiwayatBerkas(akar)
        assertNull(riwayat.isi(berkas, 7))
    }
}
