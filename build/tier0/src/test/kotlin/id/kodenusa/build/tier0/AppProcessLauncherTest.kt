package id.kodenusa.build.tier0

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Bentuk argumen `app_process` berbeda antara Android 7 dan Android 9+. Salah bentuk tidak
 * menampilkan galat yang jelas: proses mencetak bantuan dalvikvm lalu keluar kode 1.
 * Uji ini mengunci kedua bentuk tanpa perlu perangkat.
 */
class AppProcessLauncherTest {

    private val jar = File("/data/user/0/id.kodenusa.app/files/toolchain/x86_64/dx.jar")
    private val biner = File("/system/bin/app_process")

    @Test
    fun android_7_memakai_bentuk_lama() {
        val argv = AppProcessLauncher(biner, sdkInt = 24).command("Main", listOf(jar), listOf("--version")).argv

        assertEquals(listOf(biner.path, "/system/bin", "Main", "--version"), argv)
    }

    @Test
    fun android_9_ke_atas_memakai_classpath_dan_nice_name() {
        val argv = AppProcessLauncher(biner, sdkInt = 34).command("Main", listOf(jar), listOf("--version")).argv

        assertEquals(biner.path, argv[0])
        assertEquals("-Djava.class.path=${jar.path}", argv[1])
        assertEquals("/system/bin", argv[2])
        assertTrue(argv[3].startsWith("--nice-name="), "harus ada --nice-name agar argv tidak salah tafsir")
        assertEquals(listOf("Main", "--version"), argv.drop(4))
    }

    @Test
    fun beberapa_jar_dipisah_titik_dua() {
        val lain = File("/data/user/0/id.kodenusa.app/files/toolchain/x86_64/ecj.jar")
        val argv = AppProcessLauncher(biner, sdkInt = 34).command("Main", listOf(jar, lain), emptyList()).argv

        assertEquals("-Djava.class.path=${jar.path}${File.pathSeparator}${lain.path}", argv[1])
    }

    @Test
    fun classpath_tetap_dikirim_lewat_environment() {
        val perintah = AppProcessLauncher(biner, sdkInt = 24).command("Main", listOf(jar), emptyList())

        assertEquals(jar.path, perintah.env["CLASSPATH"])
    }
}
