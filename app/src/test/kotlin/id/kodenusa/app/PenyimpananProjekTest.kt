package id.kodenusa.app

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class PenyimpananProjekTest {

    private fun projekDenganBuild(akar: File): File {
        File(akar, "src/main/java/app").mkdirs()
        File(akar, "src/main/java/app/MainActivity.java").writeText("class MainActivity {}")
        File(akar, "build/apk").mkdirs()
        File(akar, "build/apk/app-debug.apk").writeText("x".repeat(2048))
        File(akar, "build/classes.dex").writeText("y".repeat(1024))
        return akar
    }

    @Test
    fun ukuran_build_menghitung_semua_berkas(@TempDir akar: File) {
        projekDenganBuild(akar)
        assertEquals(3072L, PenyimpananProjek.ukuranBuild(akar))
    }

    @Test
    fun ukuran_sumber_tidak_termasuk_build(@TempDir akar: File) {
        projekDenganBuild(akar)
        assertEquals("class MainActivity {}".length.toLong(), PenyimpananProjek.ukuranSumber(akar))
    }

    @Test
    fun bersihkan_hanya_menghapus_build(@TempDir akar: File) {
        projekDenganBuild(akar)

        val bebas = PenyimpananProjek.bersihkanBuild(akar)

        assertEquals(3072L, bebas)
        assertFalse(File(akar, "build").exists(), "folder build harus hilang")
        assertTrue(File(akar, "src/main/java/app/MainActivity.java").exists(), "sumber tidak boleh tersentuh")
    }

    @Test
    fun projek_tanpa_build_tidak_melempar(@TempDir akar: File) {
        assertEquals(0L, PenyimpananProjek.ukuranBuild(akar))
        assertEquals(0L, PenyimpananProjek.bersihkanBuild(akar))
    }

    @Test
    fun ringkas_byte_memakai_satuan_manusiawi() {
        assertEquals("0 B", PenyimpananProjek.ringkasByte(0))
        assertEquals("512 B", PenyimpananProjek.ringkasByte(512))
        assertEquals("2 KB", PenyimpananProjek.ringkasByte(2048))
        assertTrue(PenyimpananProjek.ringkasByte(5 * 1024 * 1024).endsWith("MB"))
    }
}
