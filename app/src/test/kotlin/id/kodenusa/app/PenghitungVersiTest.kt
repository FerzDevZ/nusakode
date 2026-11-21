package id.kodenusa.app

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * versionCode otomatis (docs/14 2.3): build pertama memakai nilai kodenusa.json, build sukses
 * berikutnya naik satu, dan tidak pernah turun di bawah manifest.
 */
class PenghitungVersiTest {

    @TempDir
    lateinit var folder: File

    @Test
    fun build_pertama_memakai_versionCode_dari_manifest() {
        val counter = PenghitungVersi(folder.resolve("version-code"))
        assertEquals(1, counter.berikutnya("demo", 1))
        assertEquals(5, counter.berikutnya("demo", 5))
    }

    @Test
    fun setelah_sukses_build_berikutnya_naik_satu() {
        val counter = PenghitungVersi(folder.resolve("version-code"))
        assertEquals(1, counter.berikutnya("demo", 1))
        counter.catat("demo", 1)
        assertEquals(2, counter.berikutnya("demo", 1))
        counter.catat("demo", 2)
        assertEquals(3, counter.berikutnya("demo", 1))
    }

    @Test
    fun manifest_yang_dinaikkan_pengguna_tetap_dihormati() {
        val counter = PenghitungVersi(folder.resolve("version-code"))
        counter.catat("demo", 3)
        // Pengguna menaikkan versionCode di kodenusa.json ke 10: counter tidak boleh menurunkannya.
        assertEquals(10, counter.berikutnya("demo", 10))
    }

    @Test
    fun counter_tahan_terhadap_proyek_yang_berbeda_dan_nama_aneh() {
        val counter = PenghitungVersi(folder.resolve("version-code"))
        counter.catat("demo", 4)
        counter.catat("projek/aneh", 9)
        assertEquals(5, counter.berikutnya("demo", 1))
        assertEquals(10, counter.berikutnya("projek/aneh", 1))
        assertEquals(1, counter.berikutnya("lain", 1))
    }

    @Test
    fun berkas_rusak_tidak_menghentikan_build() {
        val akar = folder.resolve("version-code").apply { mkdirs() }
        val counter = PenghitungVersi(akar)
        akar.resolve("demo").writeText("bukan angka")
        assertEquals(1, counter.berikutnya("demo", 1))
    }

    @Test
    fun counter_bertahan_setelah_instance_baru_dibuat() {
        val akar = folder.resolve("version-code").apply { mkdirs() }
        PenghitungVersi(akar).catat("demo", 7)
        assertEquals(8, PenghitungVersi(akar).berikutnya("demo", 1))
    }
}
