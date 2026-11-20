package id.kodenusa.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Python nyata di perangkat: bukan janji. Uji ini menjalankan skrip sungguhan dengan biner
 * `libmicropython.so` yang diekstrak Android ke `nativeLibraryDir`, jadi lulus hanya jika
 * interpreter benar-benar bekerja di HP (ADR-033: Tier 2).
 */
@RunWith(AndroidJUnit4::class)
class PythonRunnerDeviceTest {

    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var akar: File
    private lateinit var runner: PythonRunner

    @Before
    fun siap() {
        akar = File(ctx.filesDir, "uji-python").also { it.deleteRecursively(); it.mkdirs() }
        runner = PythonRunner.dariKonteks(ctx.applicationInfo.nativeLibraryDir)
        assertTrue("libmicropython.so harus dibawa APK", runner != null)
    }

    private fun tulis(nama: String, isi: String): File = File(akar, nama).apply { writeText(isi) }

    @Test
    fun halo_dunia_tercetak_dan_kode_nol() = runBlocking<Unit> {
        val berkas = tulis("halo.py", "print('halo dari python')\n")
        val hasil = runner.jalankan(berkas, akar) {}
        hasil as PythonRunner.Hasil.Selesai
        assertEquals(0, hasil.kode)
        assertTrue(hasil.baris.any { it.contains("halo dari python") })
    }

    @Test
    fun galat_python_ditangkap_sebagai_stderr_dengan_kode_bukan_nol() = runBlocking<Unit> {
        val berkas = tulis("galat.py", "x = 1 / 0\n")
        val hasil = runner.jalankan(berkas, akar) {}
        hasil as PythonRunner.Hasil.Selesai
        assertTrue("kode keluar harus bukan nol: ${hasil.kode}", hasil.kode != 0)
        assertTrue(hasil.baris.any { it.contains("ZeroDivisionError") })
    }

    @Test
    fun skrip_tanpa_akhir_dihentikan_batas_waktu() = runBlocking<Unit> {
        val berkas = tulis("putar.py", "while True:\n    pass\n")
        val hasil = runner.jalankan(berkas, akar) {}
        hasil as PythonRunner.Hasil.Selesai
        assertEquals(PythonRunner.KODE_TIMEOUT, hasil.kode)
    }

    @Test
    fun berkas_di_luar_folder_projek_ditolak() = runBlocking<Unit> {
        val luar = File(ctx.cacheDir, "luar.py").apply { writeText("print('x')\n") }
        val hasil = runner.jalankan(luar, akar) {}
        assertTrue(hasil is PythonRunner.Hasil.Gagal)
        luar.delete()
    }

    @Test
    fun berkas_bukan_py_ditolak() = runBlocking<Unit> {
        val berkas = tulis("bukan.txt", "print('x')\n")
        val hasil = runner.jalankan(berkas, akar) {}
        assertTrue(hasil is PythonRunner.Hasil.Gagal)
    }
}
