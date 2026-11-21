package id.kodenusa.build.tier0

import id.kodenusa.core.build.LogStream
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

interface ProcessRunner {
    /** Menjalankan perintah, memanggil [onLine] untuk setiap baris, mengembalikan exit code. */
    fun run(
        argv: List<String>,
        workingDir: File,
        env: Map<String, String> = emptyMap(),
        onLine: (LogStream, String) -> Unit,
    ): Int

    /** Mematikan proses yang sedang berjalan. Wajib idempoten dan aman dipanggil saat tidak ada proses. */
    fun cancel()
}

class RealProcessRunner : ProcessRunner {

    @Volatile
    private var current: Process? = null

    override fun run(
        argv: List<String>,
        workingDir: File,
        env: Map<String, String>,
        onLine: (LogStream, String) -> Unit,
    ): Int {
        val builder = ProcessBuilder(argv)
            .directory(workingDir)
            .redirectErrorStream(false)
        if (env.isNotEmpty()) builder.environment().putAll(env)
        val process = builder.start()
        current = process
        try {
            val stdout = pump(process.inputStream, LogStream.STDOUT, onLine)
            val stderr = pump(process.errorStream, LogStream.STDERR, onLine)
            val finished = tungguSelesai(process, BATAS_MS)
            stdout.join(2_000)
            stderr.join(2_000)
            if (!finished) {
                process.destroyForcibly()
                onLine(LogStream.SYSTEM, "Proses melebihi 10 menit dan dihentikan.")
                return TIMEOUT_EXIT
            }
            return process.exitValue()
        } finally {
            current = null
        }
    }

    override fun cancel() {
        current?.let { process ->
            process.destroyForcibly()
            onLineNoop(process)
        }
    }

    private fun onLineNoop(process: Process) {
        // destroyForcibly sudah cukup; tidak ada callback yang aman dipanggil setelah cancel.
        tungguSelesai(process, 1_000)
    }

    /**
     * `Process.waitFor(timeout, unit)` baru ada di API 26, sedangkan KodeNusa mendukung API 24.
     * Polling `exitValue()` dengan jeda singkat memberi perilaku yang sama tanpa refleksi.
     */
    private fun tungguSelesai(process: Process, batasMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + batasMs
        while (System.currentTimeMillis() < deadline) {
            try {
                process.exitValue()
                return true
            } catch (_: IllegalThreadStateException) {
                Thread.sleep(POLL_MS)
            }
        }
        return try {
            process.exitValue()
            true
        } catch (_: IllegalThreadStateException) {
            false
        }
    }

    private fun pump(
        stream: java.io.InputStream,
        logStream: LogStream,
        onLine: (LogStream, String) -> Unit,
    ): Thread = Thread {
        BufferedReader(InputStreamReader(stream)).useLines { lines ->
            lines.forEach { onLine(logStream, it) }
        }
    }.apply {
        isDaemon = true
        start()
    }

    companion object {
        const val TIMEOUT_EXIT = -9
        private const val BATAS_MS = 10 * 60 * 1000L
        private const val POLL_MS = 25L
    }
}
