package id.kodenusa.app

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import id.kodenusa.build.tier0.Tier0BuildEngine
import id.kodenusa.core.build.Tier0Config
import id.kodenusa.core.project.ProjectLoader
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.ZipFile

/**
 * Ikon aplikasi hasil build (docs/14 2.2) — bukti ujung ke ujung di perangkat:
 * build nyata menghasilkan APK yang ikonnya bisa dibaca PackageManager, dengan PNG lama 192px
 * dan adaptive icon untuk Android 8+.
 */
@RunWith(AndroidJUnit4::class)
class IkonAplikasiDeviceTest {

    private val konteks = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun build_dengan_ikon_menghasilkan_apk_berikon() = runBlocking {
        val status = ToolchainInstaller(konteks).install()
        check(status is ToolchainInstaller.Status.Ready) { "Toolchain tidak siap: $status" }

        val projek = File(konteks.filesDir, "projek/uji-ikon").apply { deleteRecursively(); mkdirs() }
        salinTemplate("halo-dunia", projek)
        // Placeholder template harus diganti dulu, kalau tidak kodenusa.json tidak valid.
        projek.walkTopDown().filter { it.isFile }.forEach { f ->
            val teks = f.readText()
            if (teks.contains("__APP_")) {
                f.writeText(teks.replace("__APP_NAME__", "Halo Dunia").replace("__APP_ID__", "id.kodenusa.uji.ikon"))
            }
        }

        val loaded = ProjectLoader.load(projek)
        val config = loaded.config ?: error("Konfigurasi kosong")
        val configIkon = config.copy(warnaIkon = "#0E7490")
        val folderKerja = File(konteks.cacheDir, "ikon-uji").apply { deleteRecursively(); mkdirs() }
        val configFinal = PembuatIkon(folderKerja).siapkan(configIkon)

        val engine = Tier0BuildEngine(status.toolchain)
        var hasil: id.kodenusa.core.build.BuildResult? = null
        engine.build(id.kodenusa.core.build.BuildRequest.Local(configFinal)).collect {
            if (it is id.kodenusa.core.build.BuildEvent.Finished) hasil = it.result
        }
        val selesai = hasil ?: error("Build tidak selesai")
        assertTrue(
            "Build gagal: " + selesai.diagnostics.joinToString("; ") { "${it.messageId} ${it.rawMessage}" },
            selesai.success,
        )
        val apk = selesai.apk ?: error("APK tidak dihasilkan")

        ZipFile(apk).use { zip ->
            assertTrue("ikon lama tidak ada", zip.getEntry(IkonAplikasi.RES_LEGACY) != null)
            assertTrue("adaptive icon tidak ada", zip.getEntry(IkonAplikasi.RES_ADAPTIF) != null)
            assertTrue("latar depan tidak ada", zip.getEntry(IkonAplikasi.RES_FOREGROUND) != null)
        }

        val info = konteks.packageManager.getPackageArchiveInfo(apk.path, 0)
            ?: error("manifest APK tidak terbaca")
        val infoApl = info.applicationInfo
        assertTrue("PackageManager tidak menemukan ikon", infoApl != null && infoApl.icon != 0)

        // Ukuran ikon lama wajib 192×192 supaya launcher API 24–25 tidak menolaknya.
        val opsi = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(File(folderKerja, "res/mipmap/ic_launcher.png").path, opsi)
        assertEquals(192, opsi.outWidth)
        assertEquals(192, opsi.outHeight)
    }

    private fun salinTemplate(nama: String, tujuan: File) {
        salinFolder("templates/$nama", tujuan)
    }

    /** Menyalin folder aset (atau berkas, bila list kosong) ke folder projek secara rekursif. */
    private fun salinFolder(jalurAset: String, tujuan: File) {
        val anak = konteks.assets.list(jalurAset).orEmpty()
        if (anak.isEmpty()) {
            konteks.assets.open(jalurAset).use { masuk ->
                tujuan.parentFile.mkdirs()
                tujuan.outputStream().use { masuk.copyTo(it) }
            }
        } else {
            tujuan.mkdirs()
            anak.forEach { salinFolder("$jalurAset/$it", File(tujuan, it)) }
        }
    }
}
