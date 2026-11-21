package id.kodenusa.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import id.kodenusa.build.tier0.DebugKeystore
import id.kodenusa.build.tier0.Tier0BuildEngine
import id.kodenusa.build.tier0.Tier0Planner
import id.kodenusa.core.build.BuildEvent
import id.kodenusa.core.build.BuildRequest
import id.kodenusa.core.project.ProjectLoader
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Bukti kunci tanda tangan pengguna (docs/14 2.1, ADR-041) di perangkat:
 * 1. Kunci pengguna dibuat di `filesDir/kunci-ttd/`.
 * 2. APK dibangun dengan kunci itu — sertifikat penanda tangan APK SAMA persis dengan sertifikat
 *    di keystore pengguna, dan BERBEDA dari keystore debug per pemasangan.
 * 3. `apksigner verify` (tool yang sama dengan pipeline) menyatakan APK sah.
 * 4. Build kedua dengan kunci yang sama menghasilkan sertifikat yang sama — kontinuitas tanda
 *    tangan lintas build.
 *
 * Pembaruan `pm install -r` APK lama oleh APK baru dibuktikan di skrip host
 * `scripts/prove/kunci-ttd.sh` (shell UiAutomator tidak menafsirkan redireksi `>`).
 */
@RunWith(AndroidJUnit4::class)
class KunciPenggunaDeviceTest {

    private val konteks = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun dua_apk_dengan_kunci_pengguna_sertifikatnya_konsisten_dan_sah() {
        val kunci = KunciPengguna(konteks)
        // Kunci dibuat ulang setiap uji: buat() menimpa keystore lama secara sadar.
        kunci.buat("ujikunci", "sandi-uji-123").getOrThrow()
        val status = kunci.status()
        assertTrue("kunci pengguna tidak tercatat", status.ada)
        assertTrue("alias salah: ${status.alias}", status.alias == "ujikunci")

        // Build pertama (versionCode dari kodenusa.json).
        val apk1 = bangunApk()
        val sertifikatApk1 = sertifikatApk(apk1)
        val sertifikatKunci = sertifikatKeystorePengguna()
        val sertifikatDebug = sertifikatDebugKeystore()
        assertTrue(
            "sertifikat APK tidak sama dengan sertifikat keystore pengguna",
            sertifikatApk1.contentEquals(sertifikatKunci),
        )
        assertTrue(
            "APK masih bertanda tangan kunci debug — kunci pengguna tidak diterapkan",
            !sertifikatApk1.contentEquals(sertifikatDebug),
        )

        // apksigner verify — tool yang sama persis dengan langkah SIGN pipeline; sah = exit 0.
        val statusTool = ToolchainInstaller(konteks).install()
        check(statusTool is ToolchainInstaller.Status.Ready) { "Toolchain tidak siap: $statusTool" }
        val verifikasi = jalankanApksignerVerify(statusTool.toolchain, apk1)
        assertTrue("apksigner verify gagal: $verifikasi", verifikasi.first == 0)

        // Build kedua dengan kunci yang sama: sertifikat harus identik (kontinuitas).
        // Salinan APK v1 disimpan DI LUAR folder projek (yang dihapus ulang tiap build)
        // untuk bukti `pm install -r` di scripts/prove/kunci-ttd.sh.
        apk1.copyTo(File(konteks.filesDir, "uji-kunci-v1.apk"), overwrite = true)
        val apk2 = bangunApk(versionCode = 2)
        val sertifikatApk2 = sertifikatApk(apk2)
        assertTrue(
            "build kedua menghasilkan sertifikat berbeda — tanda tangan tidak stabil",
            sertifikatApk2.contentEquals(sertifikatApk1),
        )
    }

    /** Projek halo-dunia dibangun di perangkat dengan toolchain + kunci pengguna (jalur BuildScreen). */
    private fun bangunApk(versionCode: Int = 1): File {
        val status = ToolchainInstaller(konteks).install()
        check(status is ToolchainInstaller.Status.Ready) { "Toolchain tidak siap: $status" }
        val projek = File(konteks.filesDir, "projek/uji-kunci").apply {
            deleteRecursively(); mkdirs()
        }
        salinFolder("templates/halo-dunia", projek)
        projek.walkTopDown().filter { it.isFile }.forEach { f ->
            val teks = f.readText()
            if (teks.contains("__APP_")) {
                f.writeText(teks.replace("__APP_NAME__", "Uji Kunci").replace("__APP_ID__", paketUji()))
            }
        }
        // versionCode ditulis setelah salin ulang template (folder projek dihapus tiap build).
        val manifestJson = File(projek, "kodenusa.json")
        manifestJson.writeText(
            manifestJson.readText().replace("\"versionCode\": 1", "\"versionCode\": $versionCode"),
        )
        val loaded = ProjectLoader.load(projek)
        val config = loaded.config ?: error("Konfigurasi kosong: ${loaded.issues}")
        val toolchain = KunciPengguna(konteks).terapkan(status.toolchain)
        assertTrue(
            "toolchain masih menunjuk keystore debug",
            toolchain.debugKeystore.absolutePath.contains("kunci-ttd"),
        )
        var hasil: id.kodenusa.core.build.BuildResult? = null
        kotlinx.coroutines.runBlocking {
            Tier0BuildEngine(toolchain).build(BuildRequest.Local(config)).collect {
                if (it is BuildEvent.Finished) hasil = it.result
            }
        }
        val selesai = hasil ?: error("Build tidak selesai")
        assertTrue("Build gagal", selesai.success)
        return selesai.apk ?: error("APK tidak dihasilkan")
    }

    /** apksigner verify dijalankan lewat launcher toolchain yang sama dengan pipeline. */
    private fun jalankanApksignerVerify(toolchain: id.kodenusa.build.tier0.Toolchain, apk: File): Pair<Int, String> {
        val perintah = toolchain.launcher.command(
            // Sama dengan Tier0Planner.APKSIGNER_MAIN (companion-nya private).
            "com.android.apksigner.ApkSignerTool",
            listOf(toolchain.apksignerJar),
            listOf("verify", "--print-certs", apk.absolutePath),
        )
        val proses = ProcessBuilder(perintah.argv).redirectErrorStream(true).start()
        val keluaran = proses.inputStream.bufferedReader().readText()
        val kode = proses.waitFor()
        return kode to keluaran
    }

    /** Menulis versionCode ke kodenusa.json setelah salin ulang template. */
    private fun paketUji() = "id.kodenusa.uji.kunci"

    private fun sertifikatApk(apk: File): ByteArray {
        val info = konteks.packageManager.getPackageArchiveInfo(
            apk.absolutePath,
            android.content.pm.PackageManager.GET_SIGNATURES,
        ) ?: error("APK tidak bisa dibaca: ${apk.name}")
        val tandaTangan = info.signatures ?: error("APK tanpa tanda tangan")
        return MessageDigest.getInstance("SHA-256").digest(tandaTangan.first().toByteArray())
    }

    private fun sertifikatKeystorePengguna(): ByteArray {
        val berkas = File(konteks.filesDir, "kunci-ttd/kunci.p12")
        val store = KeyStore.getInstance("PKCS12").apply {
            berkas.inputStream().use { load(it, "sandi-uji-123".toCharArray()) }
        }
        val cert = store.getCertificate("ujikunci")
        return MessageDigest.getInstance("SHA-256").digest(cert.encoded)
    }

    private fun sertifikatDebugKeystore(): ByteArray {
        val berkas = File(konteks.filesDir, "keys/debug.keystore")
        DebugKeystore.ensure(berkas)
        val store = KeyStore.getInstance("PKCS12").apply {
            berkas.inputStream().use { load(it, DebugKeystore.PASSWORD.toCharArray()) }
        }
        val cert = store.getCertificate(DebugKeystore.ALIAS)
        return MessageDigest.getInstance("SHA-256").digest(cert.encoded)
    }

    private fun salinFolder(jalur: String, tujuan: File) {
        val anak = konteks.assets.list(jalur).orEmpty()
        if (anak.isEmpty()) {
            konteks.assets.open(jalur).use { masuk ->
                tujuan.parentFile.mkdirs()
                tujuan.outputStream().use { masuk.copyTo(it) }
            }
        } else {
            tujuan.mkdirs()
            anak.forEach { salinFolder("$jalur/$it", File(tujuan, it)) }
        }
    }
}
