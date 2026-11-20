package id.kodenusa.app

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import id.kodenusa.build.tier0.AppProcessLauncher
import id.kodenusa.build.tier0.HostJavaLauncher
import id.kodenusa.build.tier0.Tier0BuildEngine
import id.kodenusa.build.tier0.Tier0Planner
import id.kodenusa.build.tier0.ZipAligner
import id.kodenusa.core.build.BuildEvent
import id.kodenusa.core.build.BuildRequest
import id.kodenusa.core.build.BuildResult
import id.kodenusa.core.project.ProjectLoader
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.ZipFile

/**
 * Bukti bahwa Tier 0 berjalan di perangkat Android, bukan hanya di host.
 *
 * Yang dibuktikan berurutan:
 * 1. `aapt2` biner bionic bisa dieksekusi dari `filesDir` tanpa root (aturan W^X Android 10+).
 * 2. Jar yang sudah di-dex bisa dijalankan lewat `app_process` (ECJ, d8, apksigner).
 * 3. Seluruh pipeline menghasilkan APK yang benar-benar bertanda tangan.
 */
@RunWith(AndroidJUnit4::class)
class OnDeviceBuildTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun toolchain_terpasang_lengkap() {
        val status = ToolchainInstaller(context).install()
        assertTrue("Toolchain tidak siap: $status", status is ToolchainInstaller.Status.Ready)
        val toolchain = (status as ToolchainInstaller.Status.Ready).toolchain
        assertTrue("aapt2 harus executable", toolchain.aapt2.canExecute())
    }

    @Test
    fun aapt2_biner_bionic_berjalan() {
        val status = ToolchainInstaller(context).install() as ToolchainInstaller.Status.Ready
        val proses = ProcessBuilder(status.toolchain.aapt2.path, "version")
            .redirectErrorStream(true)
            .start()
        val keluaran = proses.inputStream.bufferedReader().readText()
        assertEquals("aapt2 version gagal: $keluaran", 0, proses.waitFor())
        assertTrue("Keluaran aapt2 tidak dikenal: $keluaran", keluaran.contains("Android Asset Packaging Tool"))
    }

    @Test
    fun aapt2_bisa_membaca_android_jar() {
        val status = ToolchainInstaller(context).install() as ToolchainInstaller.Status.Ready
        val tool = status.toolchain
        val proses = ProcessBuilder(tool.aapt2.path, "dump", "resources", tool.androidJar.path)
            .redirectErrorStream(true)
            .start()
        val keluaran = proses.inputStream.bufferedReader().readText()
        proses.waitFor()
        Log.i(TAG, "dump resources exit=${proses.exitValue()} ${keluaran.take(300)}")
        assertEquals("aapt2 tidak bisa membaca android.jar: ${keluaran.take(300)}", 0, proses.exitValue())
    }

    @Test
    fun aapt2_link_dengan_android_jar() {
        val status = ToolchainInstaller(context).install() as ToolchainInstaller.Status.Ready
        val tool = status.toolchain
        val projek = File(context.cacheDir, "uji-link").apply {
            deleteRecursively(); mkdirs()
        }
        salinAset("templates/halo-dunia", projek)
        gantiPlaceholder(projek)
        val res = File(projek, "src/main/res")
        val dikompilasi = File(projek, "compiled.zip")
        jalankan(tool.aapt2, listOf("compile", "--dir", res.path, "-o", dikompilasi.path))
        val manifest = File(projek, "src/main/AndroidManifest.xml")
        val apk = File(projek, "base.apk")
        val args = listOf(
            "link", "-o", apk.path, "-I", tool.androidJar.path,
            "--manifest", manifest.path, "--min-sdk-version", "24",
            "--target-sdk-version", "35", "--auto-add-overlay", dikompilasi.path,
        )
        val keluaran = jalankan(tool.aapt2, args)
        Log.i(TAG, "link selesai ada=${apk.isFile} ${keluaran.take(300)}")
        assertTrue("link gagal: $keluaran", apk.isFile)
    }

    private fun jalankan(biner: File, args: List<String>): String {
        val proses = ProcessBuilder(listOf(biner.path) + args).redirectErrorStream(true).start()
        val keluaran = proses.inputStream.bufferedReader().readText()
        proses.waitFor()
        Log.i(TAG, "jalan ${biner.name} ${args.first()} exit=${proses.exitValue()} $keluaran")
        return keluaran
    }

    @Test
    fun berkas_toolchain_utuh() {
        val status = ToolchainInstaller(context).install() as ToolchainInstaller.Status.Ready
        val tool = status.toolchain
        val daftar = mapOf(
            "aapt2" to tool.aapt2, "ecj.jar" to tool.ecjJar, "dx.jar" to tool.dexJar,
            "apksigner.jar" to tool.apksignerJar, "android.jar" to tool.androidJar,
            "keystore" to tool.debugKeystore,
        )
        daftar.forEach { (nama, f) ->
            Log.i(TAG, "berkas $nama len=${f.length()} head=${f.inputStream().read().toChar()} ada=${f.isFile}")
        }
        assertTrue("android.jar terlalu kecil: ${tool.androidJar.length()}", tool.androidJar.length() > 20_000_000)
    }

    @Test
    fun keystore_bisa_dibuka_di_android() {
        val status = ToolchainInstaller(context).install() as ToolchainInstaller.Status.Ready
        val file = status.toolchain.debugKeystore
        val tipe = runCatching {
            val ks = java.security.KeyStore.getInstance("PKCS12")
            file.inputStream().use { ks.load(it, "android".toCharArray()) }
            assertTrue(
                "alias androiddebugkey tidak ada di keystore buatan perangkat",
                ks.containsAlias("androiddebugkey"),
            )
            "PKCS12"
        }
        Log.i(TAG, "keystore $tipe len=${file.length()} path=${file.path}")
        assertTrue("keystore buatan perangkat tidak bisa dibuka: $tipe", tipe.isSuccess)
    }

    @Test
    fun keystore_dibuat_perangkat_bukan_dari_aset() {
        val installer = ToolchainInstaller(context)
        installer.install()
        val kunci = installer.keystoreFile()
        assertTrue("keystore tidak berada di filesDir/keys", kunci.path.contains("/keys/"))
        assertTrue("keystore tidak dibuat saat install", kunci.isFile)
        val dibundel = runCatching {
            context.assets.open("toolchain/shared/debug.keystore").close()
            true
        }.getOrDefault(false)
        assertFalse("debug.keystore masih ikut dibundel di aset", dibundel)

        val pertama = kunci.readBytes()
        ToolchainInstaller(context).install()
        assertTrue(
            "instal ulang toolchain mengganti kunci tanda tangan pengguna",
            pertama.contentEquals(installer.keystoreFile().readBytes()),
        )
    }

    @Test
    fun apksigner_menandatangani_zip_kecil() {
        val status = ToolchainInstaller(context).install() as ToolchainInstaller.Status.Ready
        val tool = status.toolchain
        val dir = File(context.cacheDir, "uji-sign").apply { mkdirs() }
        val masuk = File(dir, "masuk.zip")
        java.util.zip.ZipOutputStream(masuk.outputStream()).use { zip ->
            zip.putNextEntry(java.util.zip.ZipEntry("AndroidManifest.xml"))
            zip.write(byteArrayOf(0x03, 0x00, 0x08, 0x00))
            zip.closeEntry()
        }
        val hasil = File(dir, "keluar.apk")
        val proses = ProcessBuilder(
            "/system/bin/app_process", "/system/bin", "com.android.apksigner.ApkSignerTool",
            "sign", "--ks", tool.debugKeystore.path, "--ks-type", "PKCS12", "--v1-signing-enabled", "false",
            "--v2-signing-enabled", "true", "--min-sdk-version", "24",
            "--ks-key-alias", tool.keystoreAlias,
            "--ks-pass", "pass:${tool.keystorePassword}",
            "--key-pass", "pass:${tool.keystorePassword}",
            "--out", hasil.path, masuk.path,
        ).apply { environment()["CLASSPATH"] = tool.apksignerJar.path }
            .redirectErrorStream(true)
            .start()
        val keluaran = proses.inputStream.bufferedReader().readText()
        val kode = proses.waitFor()
        Log.i(TAG, "sign exit=$kode output=$keluaran")
        assertEquals("sign gagal: $keluaran", 0, kode)
    }

    @Test
    fun apksigner_bisa_dijalankan_lewat_app_process() {
        val status = ToolchainInstaller(context).install() as ToolchainInstaller.Status.Ready
        val tool = status.toolchain.apksignerJar
        val proses = ProcessBuilder("/system/bin/app_process", "/system/bin", "com.android.apksigner.ApkSignerTool", "version")
            .apply { environment()["CLASSPATH"] = tool.path }
            .redirectErrorStream(true)
            .start()
        val keluaran = proses.inputStream.bufferedReader().readText()
        val kode = proses.waitFor()
        Log.i(TAG, "apksigner version exit=$kode: $keluaran")
        assertEquals("apksigner tidak jalan: $keluaran", 0, kode)
    }

    @Test
    fun template_halo_dunia_terbangun_di_perangkat() = bangunTemplate("halo-dunia")

    @Test
    fun template_kasir_warung_terbangun_di_perangkat() = bangunTemplate("kasir-warung")

    @Test
    fun template_katalog_whatsapp_terbangun_di_perangkat() = bangunTemplate("katalog-whatsapp")

    private fun bangunTemplate(nama: String) = runBlocking {
        val status = ToolchainInstaller(context).install()
        check(status is ToolchainInstaller.Status.Ready) { "Toolchain tidak siap: $status" }
        val toolchain = status.toolchain

        val projek = File(context.filesDir, "projek/uji-$nama").apply {
            deleteRecursively()
            mkdirs()
        }
        salinAset("templates/$nama", projek)
        gantiPlaceholder(projek)

        val loaded = ProjectLoader.load(projek)
        assertTrue("Projek $nama tidak valid: ${loaded.issues}", loaded.isBuildable)
        val config = loaded.config ?: error("Konfigurasi projek kosong")

        val mulai = System.currentTimeMillis()
        val hasil = jalankan(toolchain, config)
        val durasi = System.currentTimeMillis() - mulai
        Log.i(TAG, "Durasi build $nama di perangkat: ${durasi}ms, ukuran APK: ${hasil.apkSizeBytes}")

        assertEquals(
            "Build gagal: " + hasil.diagnostics.joinToString("; ") { "${it.messageId} ${it.rawMessage}" },
            true,
            hasil.success,
        )
        val apk = hasil.apk ?: error("APK tidak dihasilkan")
        assertTrue("APK kosong", apk.length() > 4_000)

        ZipFile(apk).use { zip ->
            assertTrue("classes.dex tidak ada", zip.getEntry("classes.dex") != null)
            assertTrue("AndroidManifest.xml tidak ada", zip.getEntry("AndroidManifest.xml") != null)
            assertTrue("resources.arsc tidak ada", zip.getEntry("resources.arsc") != null)
        }

        val verifikasi = ProcessBuilder(
            "/system/bin/app_process",
            "/system/bin",
            "com.android.apksigner.ApkSignerTool",
            "verify",
            "--verbose",
            apk.path,
        ).apply {
            environment()["CLASSPATH"] = File(toolchain.apksignerJar.parentFile, toolchain.apksignerJar.name).path
        }.redirectErrorStream(true).start()
        val teks = verifikasi.inputStream.bufferedReader().readText()
        assertEquals("apksigner verify gagal: $teks", 0, verifikasi.waitFor())
        assertTrue("APK tidak punya skema v2: $teks", teks.contains("v2 scheme (APK Signature Scheme v2): true"))
    }

    @Test
    fun planner_memakai_app_process_bukan_jvm() {
        val status = ToolchainInstaller(context).install() as ToolchainInstaller.Status.Ready
        val rencana = Tier0Planner(status.toolchain).plan(contohConfig())
        val javac = rencana.single { it.step == id.kodenusa.core.build.BuildStep.JAVAC }
        val perintah = javac.commands.single()
        assertEquals("/system/bin/app_process", perintah.argv.first())
        assertTrue("JAVAC tidak memakai CLASSPATH", perintah.env.containsKey("CLASSPATH"))
        assertTrue("Launcher host tidak boleh dipakai di perangkat", status.toolchain.launcher is AppProcessLauncher)
        assertTrue(status.toolchain.launcher !is HostJavaLauncher)
    }

    @Test
    fun zip_aligner_menjaga_isi_apk() {
        val sumber = File(context.cacheDir, "uji-masuk.zip")
        val keluar = File(context.cacheDir, "uji-keluar.apk")
        val isi = "kodenusa ".repeat(500).toByteArray()
        java.util.zip.ZipOutputStream(sumber.outputStream()).use { zip ->
            zip.putNextEntry(java.util.zip.ZipEntry("classes.dex")).apply { zip.write(isi) }
            zip.closeEntry()
            zip.putNextEntry(java.util.zip.ZipEntry("assets/teks.txt")).apply { zip.write(isi) }
            zip.closeEntry()
        }
        ZipAligner().align(sumber, keluar)
        ZipFile(keluar).use { zip ->
            val dibaca = zip.getInputStream(zip.getEntry("assets/teks.txt")).readBytes()
            assertTrue("Isi entri berubah setelah alignment", dibaca.contentEquals(isi))
        }
    }

    private suspend fun jalankan(
        toolchain: id.kodenusa.build.tier0.Toolchain,
        config: id.kodenusa.core.build.Tier0Config,
    ): BuildResult {
        val engine = Tier0BuildEngine(toolchain)
        var hasil: BuildResult? = null
        engine.build(BuildRequest.Local(config)).collect { event ->
            when (event) {
                is BuildEvent.Finished -> hasil = event.result
                is BuildEvent.Problem -> Log.w(TAG, "Masalah: ${event.diagnostic.rawMessage}")
                is BuildEvent.Log -> Log.v(TAG, "${event.tool}: ${event.line}")
                else -> Unit
            }
        }
        return hasil ?: error("Build selesai tanpa hasil")
    }

    private fun contohConfig(): id.kodenusa.core.build.Tier0Config {
        val projek = File(context.filesDir, "projek/rencana").apply { mkdirs() }
        val java = File(projek, "src/main/java/app/MainActivity.java").apply {
            parentFile?.mkdirs()
            writeText("package app; public class MainActivity {}")
        }
        val manifest = File(projek, "src/main/AndroidManifest.xml").apply {
            parentFile?.mkdirs()
            writeText("<manifest package=\"app\"/>")
        }
        return id.kodenusa.core.build.Tier0Config(
            projectId = "app",
            projectDir = projek,
            appName = "Uji",
            applicationId = "app",
            versionName = "1.0.0",
            versionCode = 1,
            minSdk = 24,
            targetSdk = 35,
            javaVersion = 8,
            javaSources = listOf(java),
            resourceDirs = emptyList(),
            assetDirs = emptyList(),
            manifest = manifest,
        )
    }

    private fun salinAset(path: String, tujuan: File, relatif: String = "") {
        val daftar = context.assets.list(path).orEmpty()
        if (daftar.isEmpty()) {
            val target = File(tujuan, relatif)
            target.parentFile?.mkdirs()
            context.assets.open(path).use { input -> target.outputStream().use { input.copyTo(it) } }
            return
        }
        for (nama in daftar) {
            val anak = if (relatif.isEmpty()) nama else "$relatif/$nama"
            salinAset("$path/$nama", tujuan, anak)
        }
    }

    /** Penanda template muncul di kodenusa.json, AndroidManifest.xml, dan strings.xml sekaligus. */
    private fun gantiPlaceholder(projek: File) {
        projek.walkTopDown().filter { it.isFile }.forEach { file ->
            val teks = file.readText()
            if (!teks.contains("__APP_")) return@forEach
            file.writeText(
                teks.replace("__APP_NAME__", "Halo Dunia")
                    .replace("__APP_ID__", "id.kodenusa.uji.halo"),
            )
        }
    }

    private companion object {
        const val TAG = "KodeNusaBuild"
    }
}
