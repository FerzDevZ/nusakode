package id.kodenusa.build.tier0

import id.kodenusa.core.build.LogStream
import java.io.File
import java.nio.file.Files
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test

/**
 * Membuktikan janji di [ProcessRunner]: tombol Batal benar-benar mematikan proses OS.
 *
 * Tanpa tes ini, `cancel()` bisa terlihat benar di UI (job coroutine berhenti) sementara proses anak
 * tetap hidup dan memakan RAM HP 2 GB.
 */
class ProcessRunnerTest {

    @Test
    fun `cancel mematikan proses yang sedang berjalan`() {
        assumeTrue(File("/bin/sh").isFile, "butuh /bin/sh; tes dilewati di luar Linux/macOS")

        val folder = Files.createTempDirectory("kodenusa-runner").toFile()
        val pidFile = File(folder, "pid")
        // `exec` penting: shell digantikan oleh sleep, sehingga PID yang dicatat adalah proses yang
        // benar-benar berjalan dan bisa diperiksa setelah cancel.
        val argv = listOf(
            "/bin/sh",
            "-c",
            "echo \$\$ > '${pidFile.path}'; exec sleep 30",
        )

        val runner = RealProcessRunner()
        val pekerja = Thread {
            runner.run(argv, folder) { _: LogStream, _: String -> }
        }
        pekerja.isDaemon = true
        pekerja.start()

        val pid = tungguPid(pidFile)
        assumeTrue(pid > 0, "proses uji tidak sempat menulis PID")

        val mulai = System.currentTimeMillis()
        runner.cancel()
        pekerja.join(5_000)

        assertFalse(pekerja.isAlive, "run() tidak berhenti setelah cancel")
        assertTrue(System.currentTimeMillis() - mulai < 5_000, "cancel butuh lebih dari 5 detik")
        assertFalse(ProcessHandle.of(pid).map { it.isAlive }.orElse(false), "proses $pid masih hidup")

        folder.deleteRecursively()
    }

    @Test
    fun `cancel aman dipanggil saat tidak ada proses`() {
        val runner = RealProcessRunner()
        repeat(3) { runner.cancel() }
    }

    private fun tungguPid(pidFile: File): Long {
        val batas = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < batas) {
            bacaPid(pidFile)?.let { return it }
            Thread.sleep(20)
        }
        return 0
    }

    private fun bacaPid(pidFile: File): Long? {
        if (!pidFile.isFile) return null
        val nilai = pidFile.readText().trim().toLongOrNull() ?: return null
        return if (nilai > 0) nilai else null
    }
}
