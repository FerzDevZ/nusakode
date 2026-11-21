package id.kodenusa.app

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** Uji riwayat tulisan AI: tugas → berkas yang berubah → bisa dibuka ulang. */
class RiwayatAgenTest {

    @TempDir
    lateinit var root: File

    @Test
    fun catatan_tersimpan_dan_terbaca_ulang_dengan_batch_undo() {
        val riwayat = RiwayatAgen(File(root, "riwayat"))

        riwayat.catat("UjiAI", "buat LayarKedua.java", listOf("src/main/java/app/LayarKedua.java"), "1763-1")
        riwayat.catat("UjiAI", "perbaiki layout", listOf("src/main/res/layout/activity_main.xml"), null)

        val muatan = riwayat.muat("UjiAI")
        assertEquals(2, muatan.size)
        // Terbaru di depan supaya pengguna melihat yang terakhir dulu.
        assertEquals("perbaiki layout", muatan[0].tugas)
        assertEquals("1763-1", muatan[1].batch)
        assertEquals("src/main/java/app/LayarKedua.java", muatan[1].berkas.first())
    }

    @Test
    fun projek_berbeda_tidak_saling_mencampur() {
        val riwayat = RiwayatAgen(File(root, "riwayat2"))

        riwayat.catat("Kasir", "tambah tombol bayar", listOf("MainActivity.java"), null)
        riwayat.catat("Katalog", "ganti warna kartu", listOf("aplikasi.js"), null)

        assertEquals(1, riwayat.muat("Kasir").size)
        assertEquals("ganti warna kartu", riwayat.muat("Katalog")[0].tugas)
    }

    @Test
    fun maksimal_dua_puluh_catatan_terbaru() {
        val riwayat = RiwayatAgen(File(root, "riwayat3"))
        repeat(25) { riwayat.catat("Projek", "tugas ke-$it", listOf("a.java"), null) }

        val muatan = riwayat.muat("Projek")
        assertEquals(20, muatan.size)
        assertEquals("tugas ke-24", muatan[0].tugas)
    }

    @Test
    fun projek_belum_ada_tulisannya_menghasilkan_daftar_kosong() {
        val riwayat = RiwayatAgen(File(root, "riwayat4"))
        assertTrue(riwayat.muat("BelumAda").isEmpty())
    }

    @Test
    fun nama_projek_berbahaya_dibersihkan() {
        val riwayat = RiwayatAgen(File(root, "riwayat5"))
        riwayat.catat("../kabur", "tugas", listOf("a.java"), null)
        // Tidak melempar galat dan tetap bisa dibaca ulang lewat kunci yang sama.
        assertEquals(1, riwayat.muat("../kabur").size)
        assertNull(riwayat.muat("../kabur")[0].batch)
    }
}
