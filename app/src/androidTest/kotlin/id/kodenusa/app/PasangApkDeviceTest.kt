package id.kodenusa.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import id.kodenusa.build.tier0.Tier0BuildEngine
import id.kodenusa.core.build.BuildEvent
import id.kodenusa.core.build.BuildRequest
import id.kodenusa.core.project.ProjectLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tombol "Pasang" (docs/14 2.4) — bukti ujung ke ujung: APK hasil build di perangkat dipasang
 * lewat jalur yang SAMA dengan tombol (ApkInstaller → ACTION_VIEW → pemasang Android), dengan
 * konfirmasi sistem diklik UiAutomator. Setelah selesai, paket benar-benar terdaftar.
 */
@RunWith(AndroidJUnit4::class)
class PasangApkDeviceTest {

    private val konteks = InstrumentationRegistry.getInstrumentation().targetContext
    private val perangkat = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private val paketUji = "id.kodenusa.uji.pasang"

    @Test
    fun apk_hasil_build_terpasang_lewat_pemasang_android() {
        // 0. Layar "App installed." dari alur sebelumnya MEMBLOKIR pemasang baru — tutup dulu.
        // Layar setelan "Install unknown apps" yang lama juga menyimpan uid lama aplikasi
        // (setelah reinstall uid berubah) sehingga tombol izinnya gagal — matikan paksa.
        perangkat.executeShellCommand("am force-stop com.google.android.packageinstaller")
        perangkat.executeShellCommand("am force-stop com.android.packageinstaller")
        perangkat.executeShellCommand("am force-stop com.android.settings")
        perangkat.pressHome()
        Thread.sleep(1_000)
        // 1. Build APK nyata di perangkat.
        val apk = bangunApk()
        // 2. Bersihkan sisa pemasangan uji sebelumnya supaya asersi bermakna.
        perangkat.executeShellCommand("pm uninstall $paketUji")
        Thread.sleep(500)

        // 3. Jalur yang sama dengan tombol "Pasang" di BuildScreen.
        val hasil = ApkInstaller.install(konteks, apk)
        assertTrue("ApkInstaller gagal membuka pemasang: ${hasil.exceptionOrNull()}", hasil.isSuccess)

        // 4. Ikuti alur pemasang sistem sampai paket benar-benar terdaftar. Pemindaian Play
        // Protect bisa memakan waktu, jadi jendelanya 3 menit — bukan 30 detik.
        if (!ikutiAlurPemasang()) {
            // Bukti, bukan tebakan: saat gagal, rekam aplikasi yang fokus + hierarki UI
            // supaya penyebabnya terbaca dari laporan uji (mis. dialog tak terduga).
            // Catatan: executeShellCommand tidak memproses pipa — pakai perintah polos.
            val fokus = perangkat.currentPackageName
            perangkat.executeShellCommand("uiautomator dump /sdcard/pasang-gagal.xml")
            val tampilan = perangkat.executeShellCommand("cat /sdcard/pasang-gagal.xml").take(1_500)
            fail(
                "Paket $paketUji tidak terdaftar setelah pemasangan.\n" +
                    "Aplikasi fokus: $fokus\nTampilan: $tampilan",
            )
        }

        // 5. Beres-beres supaya uji berikutnya mulai dari nol.
        perangkat.executeShellCommand("pm uninstall $paketUji")
        perangkat.pressBack()
    }

    /** APK contoh halo-dunia dibangun di perangkat, sama seperti template asli. */
    private fun bangunApk(): java.io.File {
        val status = ToolchainInstaller(konteks).install()
        check(status is ToolchainInstaller.Status.Ready) { "Toolchain tidak siap: $status" }
        val projek = java.io.File(konteks.filesDir, "projek/uji-pasang").apply {
            deleteRecursively(); mkdirs()
        }
        salinAset("halo-dunia", projek)
        projek.walkTopDown().filter { it.isFile }.forEach { f ->
            val teks = f.readText()
            if (teks.contains("__APP_")) {
                f.writeText(teks.replace("__APP_NAME__", "Uji Pasang").replace("__APP_ID__", paketUji))
            }
        }
        val loaded = ProjectLoader.load(projek)
        val config = loaded.config ?: error("Konfigurasi kosong: ${loaded.issues}")
        var hasil: id.kodenusa.core.build.BuildResult? = null
        kotlinx.coroutines.runBlocking {
            Tier0BuildEngine(status.toolchain).build(BuildRequest.Local(config)).collect {
                if (it is BuildEvent.Finished) hasil = it.result
            }
        }
        val selesai = hasil ?: error("Build tidak selesai")
        assertTrue("Build gagal", selesai.success)
        return selesai.apk ?: error("APK tidak dihasilkan")
    }

    private fun salinAset(nama: String, tujuan: java.io.File) {
        salinFolder("templates/$nama", tujuan)
    }

    private fun salinFolder(jalur: String, tujuan: java.io.File) {
        val anak = konteks.assets.list(jalur).orEmpty()
        if (anak.isEmpty()) {
            konteks.assets.open(jalur).use { masuk ->
                tujuan.parentFile.mkdirs()
                tujuan.outputStream().use { masuk.copyTo(it) }
            }
        } else {
            tujuan.mkdirs()
            anak.forEach { salinFolder("$jalur/$it", java.io.File(tujuan, it)) }
        }
    }

    /**
     * Mengikuti dialog pemasang sampai paket terdaftar (maks 3 menit): peringatan Play Protect,
     * izin "instal aplikasi tidak dikenal", lalu tombol konfirmasi pasang.
     */
    private fun ikutiAlurPemasang(): Boolean {
        val batas = System.currentTimeMillis() + 180_000
        var sudahIzin = false
        while (System.currentTimeMillis() < batas) {
            if (paketTerdaftar()) return true
            val teksTombol = tombolTerlihat()
            when {
                teksTombol == "Install" || teksTombol == "Pasang" || teksTombol == "INSTALL" -> {
                    klikTeks(teksTombol)
                }
                teksTombol == "Continue" || teksTombol == "Lanjut" -> {
                    klikTeks(teksTombol)
                }
                teksTombol == "Settings" || teksTombol == "Setelan" -> {
                    klikTeks(teksTombol)
                    sudahIzin = true
                }
                teksTombol == "Allow from this source" || teksTombol == "Izinkan dari sumber ini" -> {
                    klikTeks(teksTombol)
                    sudahIzin = true
                    Thread.sleep(1_500)
                }
                // HANYA kembali dari Setelan ke pemasang — bukan back buta. Bukti uji: back
                // otomatis saat transisi pemasang (tombol belum tampak) MEMBATALKAN pemasangan
                // dan loop berakhir di launcher tanpa paket. Back aman hanya bila fokusnya
                // memang aplikasi Setelan.
                sudahIzin && teksTombol == null && perangkat.currentPackageName == "com.android.settings" -> {
                    perangkat.pressBack()
                    Thread.sleep(1_500)
                }
            }
            Thread.sleep(1_000)
        }
        return paketTerdaftar()
    }

    private fun tombolTerlihat(): String? {
        val kandidat = listOf(
            "Install", "Pasang", "INSTALL", "Continue", "Lanjut", "Settings", "Setelan",
            "Allow from this source", "Izinkan dari sumber ini",
        )
        return kandidat.firstOrNull { teks ->
            perangkat.hasObject(By.text(teks))
        }
    }

    private fun klikTeks(teks: String?) {
        if (teks == null) return
        val node = perangkat.findObject(By.text(teks))
        if (node != null) node.click()
    }

    private fun paketTerdaftar(): Boolean {
        // getPackageInfo lintas paket terkena package visibility filtering (Android 11+),
        // jadi verifikasi lewat shell yang punya visibilitas penuh.
        val keluaran = perangkat.executeShellCommand("pm list packages $paketUji")
        return keluaran.contains("package:$paketUji")
    }
}
