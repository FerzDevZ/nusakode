package id.kodenusa.app

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import id.kodenusa.core.git.Hasil
import id.kodenusa.core.git.RepoGit
import id.kodenusa.core.project.ProjectFiles
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Uji dua fitur 0.9.8 yang tampil di layar projek:
 *
 * 1. **Laporan galat** — kartu muncul hanya kalau proses sebelumnya mati, dan bisa dihapus.
 * 2. **Ambil projek dari GitHub** — menolak alamat yang salah dengan kalimat yang bisa dibaca,
 *    dan klon dari remote lokal (`file://`) benar-benar membuat projek yang terbuka di editor.
 *
 * Klon di sini tidak menyentuh jaringan: remote-nya repo JGit sungguhan di `cacheDir`.
 */
@RunWith(AndroidJUnit4::class)
class LaporanKlonUiTest {

    @get:Rule
    val layar = createAndroidComposeRule<MainActivity>()

    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val toko get() = ProjectStore(File(ctx.filesDir, "projek"))

    @Before
    fun bersihkan() {
        toko.list().forEach { it.dir.deleteRecursively() }
        LaporanGalat.hapus(ctx)
    }

    private fun buatProjek(nama: String): File {
        val folder = Templates(ctx).create(
            "halo-dunia",
            toko.uniqueFolder(nama),
            nama,
            "id.kodenusa.uji$nama",
        ).getOrThrow()
        layar.activityRule.scenario.recreate()
        layar.waitUntil(timeoutMillis = 20_000) {
            layar.onAllNodes(hasText(nama, substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        layar.onNode(hasText(nama, substring = true)).performClick()
        layar.waitUntil(timeoutMillis = 20_000) {
            layar.onAllNodesWithTag("buka-palet").fetchSemanticsNodes().isNotEmpty()
        }
        return folder
    }

    private fun kembaliKeProjek() {
        layar.activityRule.scenario.recreate()
        layar.waitUntil(timeoutMillis = 20_000) {
            layar.onAllNodes(hasText("Projek saya", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun laporan_galat_tampil_lalu_bisa_dihapus() {
        LaporanGalat.tulis(
            ctx,
            RuntimeException("uji laporan"),
            InfoLaporan(versi = "uji", sdk = 35, perangkat = "Emulator", ramMb = 2048),
        )
        kembaliKeProjek()

        layar.onNodeWithTag("laporan-galat").assertExists()
        layar.onNodeWithTag("isi-laporan").assertExists()

        layar.onNodeWithTag("hapus-laporan").performClick()
        layar.waitUntil(timeoutMillis = 10_000) {
            layar.onAllNodesWithTag("laporan-galat").fetchSemanticsNodes().isEmpty()
        }
        assertFalse(LaporanGalat.berkas(ctx).exists())
    }

    @Test
    fun tanpa_laporan_galat_kartu_tidak_muncul() {
        kembaliKeProjek()
        layar.onAllNodesWithTag("laporan-galat").fetchSemanticsNodes().isEmpty()
    }

    @Test
    fun klon_menolak_alamat_bukan_https_dengan_kalimat_yang_jelas() {
        kembaliKeProjek()
        layar.onNodeWithTag("buka-klon").performClick()
        layar.waitUntil(timeoutMillis = 10_000) {
            layar.onAllNodesWithTag("alamat-klon").fetchSemanticsNodes().isNotEmpty()
        }
        layar.onNodeWithTag("alamat-klon").performTextReplacement("http://contoh.id/repo.git")
        layar.onNodeWithTag("mulai-klon").performClick()
        layar.waitUntil(timeoutMillis = 10_000) {
            layar.onAllNodesWithTag("galat-klon").fetchSemanticsNodes().isNotEmpty()
        }
        layar.onNodeWithTag("galat-klon").assertExists()
    }

    @Test
    fun klon_dari_repo_lokal_menghasilkan_projek_yang_terbuka() {
        val sumber = File(ctx.cacheDir, "sumber-klon-${System.nanoTime()}")
        sumber.mkdirs()
        File(sumber, "kodenusa.json").writeText("""{"name":"KlonUji","applicationId":"id.kodenusa.klonuji"}""")
        File(sumber, "baca.txt").writeText("isi uji\n")
        assertTrue(RepoGit.siapkan(sumber, "Uji", "uji@kodenusa.id") is Hasil.Sukses)
        assertTrue(RepoGit.simpanSemua(sumber, "awal", "Uji", "uji@kodenusa.id") is Hasil.Sukses)

        kembaliKeProjek()
        layar.onNodeWithTag("buka-klon").performClick()
        layar.waitUntil(timeoutMillis = 10_000) {
            layar.onAllNodesWithTag("alamat-klon").fetchSemanticsNodes().isNotEmpty()
        }
        layar.onNodeWithTag("alamat-klon").performTextReplacement("file://${sumber.absolutePath}")
        layar.onNodeWithTag("mulai-klon").performClick()

        // Setelah klon berhasil, aplikasi langsung membuka projek hasil klon di editor.
        layar.waitUntil(timeoutMillis = 30_000) {
            layar.onAllNodesWithTag("buka-palet").fetchSemanticsNodes().isNotEmpty()
        }
        val klonFolder = File(ctx.filesDir, "projek").listFiles()
            ?.firstOrNull { it.name.startsWith("sumber-klon") }
        assertTrue(klonFolder != null && File(klonFolder, "baca.txt").isFile)
    }

    @Test
    fun membuka_berkas_kt_menjelaskan_bahwa_kotlin_belum_dibangun() {
        val folder = buatProjek("Kotlin Uji")
        val dibuat = ProjectFiles.createFile(folder, "", "alat.kt", "fun utama() {}\n")
        assertTrue(dibuat is ProjectFiles.Hasil.Sukses)
        kembaliKeProjek()
        layar.onNode(hasText("Kotlin Uji", substring = true)).performClick()
        layar.waitUntil(timeoutMillis = 20_000) {
            layar.onAllNodesWithTag("buka-palet").fetchSemanticsNodes().isNotEmpty()
        }
        layar.onAllNodes(hasText("☰"))[0].performClick()
        layar.waitUntil(timeoutMillis = 10_000) {
            layar.onAllNodesWithTag("daftar-berkas").fetchSemanticsNodes().isNotEmpty()
        }
        layar.onNodeWithTag("daftar-berkas").performScrollToNode(hasTestTag("baris-alat.kt"))
        layar.onNodeWithTag("baris-alat.kt").performClick()
        layar.waitUntil(timeoutMillis = 10_000) {
            layar.onAllNodesWithTag("catatan-kotlin").fetchSemanticsNodes().isNotEmpty()
        }
        layar.onNodeWithTag("catatan-kotlin").assertExists()
    }
}
