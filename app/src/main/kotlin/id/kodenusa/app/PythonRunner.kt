package id.kodenusa.app

import java.io.File
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Menjalankan berkas `.py` dengan MicroPython yang dibawa di dalam APK.
 *
 * - Biner `libmicropython.so` dikemas di `jniLibs` sehingga Android mengekstraknya ke
 *   `nativeLibraryDir` — satu-satunya lokasi yang masih boleh `exec` di Android 10+ (ADR-021),
 *   pola yang sama dengan `libaapt2.so`.
 * - Tidak ada `sh -c` dan tidak ada interpolasi string perintah (ADR-006): argv dibangun sebagai
 *   daftar, dan jalur berkas divalidasi agar tetap di dalam folder projek.
 * - Batas waktu 20 detik: skrip yang berputar tanpa akhir dimatikan, bukan menggantungkan UI.
 * - MicroPython, bukan CPython: modul seperti `requests`, `numpy`, dan `pip` tidak tersedia —
 *   catatan di layar editor menyebut ini dengan jujur.
 */
class PythonRunner(
    private val interpreter: File,
    private val batasMs: Long = BATAS_MS,
) {

    sealed interface Hasil {
        /** Skrip selesai; [kode] 0 berarti sukses menurut MicroPython. */
        data class Selesai(val kode: Int, val baris: List<String>) : Hasil

        /** Tidak sampai menjalankan: berkas tidak sah atau interpreter tidak ada. */
        data class Gagal(val pesan: String) : Hasil
    }

    private val proses = AtomicReference<Process?>(null)

    /**
     * Menjalankan [berkas] dengan folder kerja [folderKerja]. [onBaris] dipanggil untuk setiap
     * baris keluaran (stdout dan stderr disatukan agar urutannya seperti di konsol).
     */
    suspend fun jalankan(
        berkas: File,
        folderKerja: File,
        onBaris: (String) -> Unit,
    ): Hasil = withContext(Dispatchers.IO) {
        if (!interpreter.isFile) {
            return@withContext Hasil.Gagal("Interpreter Python tidak ditemukan di perangkat ini.")
        }
        if (!berkas.isFile) {
            return@withContext Hasil.Gagal("Berkas tidak ada lagi. Simpan dulu, lalu jalankan.")
        }
        if (!berkas.name.endsWith(".py", ignoreCase = true)) {
            return@withContext Hasil.Gagal("Hanya berkas .py yang bisa dijalankan.")
        }
        val akar = folderKerja.canonicalFile
        val target = berkas.canonicalFile
        if (!target.path.startsWith(akar.path + File.separator)) {
            return@withContext Hasil.Gagal("Berkas berada di luar folder projek.")
        }

        val builder = ProcessBuilder(listOf(interpreter.path, target.path))
            .directory(akar)
            .redirectErrorStream(true)
        val p = runCatching { builder.start() }.getOrElse { e ->
            return@withContext Hasil.Gagal("Gagal memulai interpreter: ${e.message ?: "sebab tidak diketahui"}")
        }
        proses.set(p)
        try {
            val baris = mutableListOf<String>()
            val pembaca = Thread {
                // destroyForcibly() menutup stream saat thread ini masih membaca — Android melempar
                // InterruptedIOException yang kalau tidak ditangkap akan merusak proses uji/UI.
                // Kematian proses sudah terdeteksi lewat waitFor, jadi cukup berhenti diam-diam.
                runCatching {
                    p.inputStream.bufferedReader().forEachLine { teks ->
                        baris.add(teks)
                        onBaris(teks)
                    }
                }
            }
            pembaca.start()
            val selesai = p.waitFor(batasMs, java.util.concurrent.TimeUnit.MILLISECONDS)
            pembaca.join(2_000)
            if (!selesai) {
                p.destroyForcibly()
                onBaris("Skrip melebihi ${batasMs / 1000} detik dan dihentikan.")
                return@withContext Hasil.Selesai(kode = KODE_TIMEOUT, baris = baris)
            }
            Hasil.Selesai(kode = p.exitValue(), baris = baris)
        } finally {
            proses.set(null)
        }
    }

    /** Mematikan skrip yang sedang berjalan. Aman dipanggil kapan saja. */
    fun hentikan() {
        proses.get()?.destroyForcibly()
    }

    companion object {
        const val NAMA_BINER = "libmicropython.so"
        const val BATAS_MS = 20_000L
        const val KODE_TIMEOUT = 124

        /** Lokasi interpreter di perangkat, dari konteks aplikasi. */
        fun dariKonteks(nativeLibraryDir: String): PythonRunner =
            PythonRunner(File(nativeLibraryDir, NAMA_BINER))
    }
}
