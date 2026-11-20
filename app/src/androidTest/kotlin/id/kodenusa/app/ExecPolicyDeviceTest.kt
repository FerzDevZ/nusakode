package id.kodenusa.app

import android.os.Build
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Menjawab satu pertanyaan yang menentukan hidup-mati toolchain di HP modern:
 * bolehkah mengeksekusi biner dari penyimpanan aplikasi (`filesDir`) di Android 10+?
 *
 * Sejak Android 10 ada kebijakan W^X: file yang bisa ditulis aplikasi tidak boleh juga
 * dieksekusi. Kalau `aapt2` tidak bisa dijalankan dari `filesDir`, seluruh Tier 0 gagal di
 * HP keluaran 2019 ke atas — jadi ini harus dibuktikan di perangkat, bukan dianggap aman
 * karena berhasil di API 24.
 */
@RunWith(AndroidJUnit4::class)
class ExecPolicyDeviceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val abi: String = Build.SUPPORTED_ABIS.first()

    @Test
    fun cara_eksekusi_yang_didukung_di_perangkat_ini() {
        val hasil = mutableListOf<String>()

        for (lokasi in listOf(context.filesDir, context.cacheDir)) {
            val biner = siapkanBiner(lokasi, "aapt2")
            val laporan = jalankan(biner)
            hasil += "${lokasi.name}: $laporan"
            Log.i(TAG, "lokasi=${lokasi.absolutePath} hasil=$laporan")
        }

        // Satu-satunya lokasi yang diizinkan di Android 10+ dan wajib berhasil di semua versi.
        val binerNative = File(context.applicationInfo.nativeLibraryDir, "libaapt2.so")
        val laporanNative = jalankan(binerNative)
        hasil += "nativeLibraryDir: $laporanNative"
        Log.i(TAG, "SDK=${Build.VERSION.SDK_INT} ABI=$abi\n" + hasil.joinToString("\n"))

        assertTrue(
            "aapt2 dari nativeLibraryDir gagal di API ${Build.VERSION.SDK_INT}:\n" + hasil.joinToString("\n"),
            laporanNative.contains("kode=0"),
        )
    }

    /** Menyalin biner native ke lokasi uji dan menandainya bisa dieksekusi. */
    private fun siapkanBiner(lokasi: File, nama: String): File {
        val folder = File(lokasi, "uji-exec").apply { mkdirs() }
        val target = File(folder, nama)
        if (!target.isFile) {
            val asal = File(context.applicationInfo.nativeLibraryDir, "libaapt2.so")
            asal.inputStream().use { input -> target.outputStream().use { output -> input.copyTo(output) } }
        }
        target.setExecutable(true, false)
        return target
    }

    /** Mengembalikan ringkasan hasil eksekusi: kode keluar dan beberapa kata pertama keluaran. */
    private fun jalankan(biner: File): String = runCatching {
        val proses = ProcessBuilder(listOf(biner.absolutePath, "version"))
            .redirectErrorStream(true)
            .start()
        val keluaran = proses.inputStream.bufferedReader().readText().trim().replace('\n', ' ')
        val kode = proses.waitFor()
        "kode=$kode keluaran=${keluaran.take(80)}"
    }.getOrElse { galat ->
        "gagal=${galat::class.java.simpleName}: ${galat.message}"
    }

    companion object {
        private const val TAG = "KodeNusaExec"
    }
}
