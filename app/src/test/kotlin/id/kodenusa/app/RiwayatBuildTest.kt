package id.kodenusa.app

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class RiwayatBuildTest {

    @TempDir
    lateinit var folder: File

    @Test
    fun catat_dan_muat_catatan_terbaru_di_depan() {
        val riwayat = RiwayatBuild(folder)
        riwayat.catat("Projek A", sukses = true, ukuranApk = 3_200_000L, durasiMs = 42_000L)
        riwayat.catat("Projek A", sukses = false, ukuranApk = null, durasiMs = 8_000L)

        val semua = riwayat.muat("Projek A")
        assertEquals(2, semua.size)
        assertEquals(false, semua[0].sukses)
        assertEquals(null, semua[0].ukuranApk)
        assertEquals(true, semua[1].sukses)
        assertEquals(3_200_000L, semua[1].ukuranApk)
    }

    @Test
    fun dibatasi_sepuluh_catatan_terbaru() {
        val riwayat = RiwayatBuild(folder)
        repeat(14) { indeks ->
            riwayat.catat("p", sukses = true, ukuranApk = indeks.toLong(), durasiMs = 1_000L)
        }
        val semua = riwayat.muat("p")
        assertEquals(10, semua.size)
        // Catatan terbaru (indeks 13) harus di depan.
        assertEquals(13L, semua.first().ukuranApk)
    }

    @Test
    fun projek_dengan_karakter_aneh_tetap_tersimpan() {
        val riwayat = RiwayatBuild(folder)
        riwayat.catat("Kasir Warung / Uji 2", sukses = true, ukuranApk = 1L, durasiMs = 1L)
        assertEquals(1, riwayat.muat("Kasir Warung / Uji 2").size)
    }

    @Test
    fun projek_belum_pernah_build_menghasilkan_daftar_kosong() {
        val riwayat = RiwayatBuild(folder)
        assertTrue(riwayat.muat("tidak-ada").isEmpty())
        assertTrue(riwayat.muat("tidak-ada").isEmpty())
    }
}
