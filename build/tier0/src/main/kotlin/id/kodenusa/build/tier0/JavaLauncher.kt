package id.kodenusa.build.tier0

import id.kodenusa.core.build.BuildTool
import java.io.File

/**
 * Cara menjalankan jar Java (ECJ, d8, apksigner) di dua lingkungan yang berbeda:
 *
 * - **Host**: ada JVM penuh, cukup `java -cp <jar> <kelas> <argumen>`.
 * - **Android**: tidak ada JVM. ART dijalankan lewat `/system/bin/app_process` dengan kelas utama
 *   dan `CLASSPATH` menunjuk ke jar yang sudah di-dex. Inilah sebabnya toolchain perangkat memakai
 *   `ecj-dex.jar` dan kawan-kawan, bukan jar asli.
 *
 * Pipeline hanya melihat [command], jadi tidak ada cabang `if (isAndroid)` di dalam langkah build.
 */
interface JavaLauncher {
    fun command(
        mainClass: String,
        jars: List<File>,
        args: List<String>,
        heapMb: Int = 512,
    ): ToolCommand
}

/** JVM biasa di host (JDK 17 saat pengembangan, atau `java` di folder toolchain). */
class HostJavaLauncher(private val javaBinary: File) : JavaLauncher {
    override fun command(mainClass: String, jars: List<File>, args: List<String>, heapMb: Int): ToolCommand =
        ToolCommand(
            tool = BuildTool.INTERNAL,
            argv = buildList {
                add(javaBinary.path)
                add("-Xmx${heapMb}m")
                add("-cp")
                add(jars.joinToString(File.pathSeparator) { it.path })
                add(mainClass)
                addAll(args)
            },
        )
}

/**
 * ART lewat `app_process`. Jar yang diberikan wajib sudah berisi `classes.dex`.
 *
 * Dua aturan platform yang mahal kalau dilanggar (dibuktikan di emulator API 34, ADR-021):
 *
 * 1. **Sejak API 28 bentuk argumennya berbeda.** Di Android 7 bentuknya
 *    `app_process <folder> <kelas> <argumen…>`; di Android 9+ `app_process` membaca token ketiga
 *    sebagai `--nice-name`, sehingga bentuk lama menghasilkan "dalvikvm: [options] class" lalu
 *    keluar dengan kode 1. Bentuk modern menaruh classpath di opsi VM:
 *    `app_process -Djava.class.path=<jar> /system/bin --nice-name=<nama> <kelas> <argumen…>`.
 * 2. **Jar wajib read-only.** ART 10+ menolak memuat dex dari berkas yang bisa ditulis
 *    (`SecurityException: Writable dex file … is not allowed`) karena itu jalur injeksi kode.
 *    Folder toolchain sudah ditandai read-only oleh [ToolchainInstaller].
 *
 * `-Xmx` tidak diberikan karena ART mengabaikan batas heap JVM dan memakai heap aplikasi; memaksa
 * nilai besar justru membuat proses gagal start di HP 2 GB.
 */
class AppProcessLauncher(
    private val appProcess: File = File("/system/bin/app_process"),
    private val sdkInt: Int,
) : JavaLauncher {
    override fun command(mainClass: String, jars: List<File>, args: List<String>, heapMb: Int): ToolCommand {
        val classpath = jars.joinToString(File.pathSeparator) { it.path }
        val modern = sdkInt >= 28
        return ToolCommand(
            tool = BuildTool.INTERNAL,
            argv = buildList {
                add(appProcess.path)
                if (modern) {
                    add("-Djava.class.path=$classpath")
                    add("/system/bin")
                    add("--nice-name=kodenusa-tool")
                } else {
                    add("/system/bin")
                }
                add(mainClass)
                addAll(args)
            },
            // CLASSPATH tetap dikirim: Android 7 membacanya dari lingkungan, bukan dari -D.
            env = mapOf("CLASSPATH" to classpath),
        )
    }
}
