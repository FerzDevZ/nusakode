package id.kodenusa.app

import android.content.Intent
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Keluhan "perizinan penyimpanan saja tidak bisa, dimana ambil file" dijawab dengan dua hal:
 * aplikasi **tidak meminta izin** apa pun (SAF), dan ada tombol "Ambil dari HP" di panel berkas.
 *
 * Uji ini menjaga keduanya: pemilih berkas memang ada di perangkat ini (bisa di-resolve), dan
 * tombolnya benar-benar tampil di layar editor.
 */
@RunWith(AndroidJUnit4::class)
class BerkasHpUiTest {

    @get:Rule
    val layar = createAndroidComposeRule<MainActivity>()

    private val konteks = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun bersihkan() {
        konteks.filesDir.listFiles().orEmpty()
            .filter { it.isDirectory && java.io.File(it, "kodenusa.json").isFile }
            .forEach { it.deleteRecursively() }
    }

    @Test
    fun pemilih_berkas_android_tersedia_untuk_semua_jenis_berkas() {
        // Membuka semua jenis berkas: pengguna boleh mengambil gambar, css, java, atau zip.
        val ambil = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        assertNotNull(
            "perangkat ini tidak punya pemilih berkas; SAF tidak akan bisa dipakai",
            ambil.resolveActivity(konteks.packageManager),
        )
        // Menyimpan hasil juga harus punya tujuan.
        val simpan = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/zip"
        }
        assertNotNull(simpan.resolveActivity(konteks.packageManager))
    }

    @Test
    fun panel_berkas_punya_tombol_ambil_dari_hp() {
        bukaProjek()

        layar.onNodeWithTag("buka-menu").performClick()
        layar.waitForIdle()

        layar.onNodeWithTag("daftar-berkas").assertExists()
        layar.onNodeWithTag("ambil-dari-hp").assertExists()
        // Tombol harus bisa ditekan: kalau terkunci, pengguna kembali ke masalah awal.
        layar.onNodeWithTag("ambil-dari-hp").performClick()
        layar.waitForIdle()
    }

    @Test
    fun layar_projek_menjelaskan_bahwa_tidak_perlu_izin() {
        layar.onNodeWithTag("catatan-izin").assertExists()
        layar.onNodeWithText("Tidak perlu izin penyimpanan", substring = true).assertExists()
    }

    @Test
    fun berkas_dari_aplikasi_lain_tersalin_ke_folder_projek() {
        // Meniru persis yang dilakukan tombol "Ambil dari HP": buka aliran dari URI penyedia lain,
        // lalu salin ke projek. URI di sini datang dari FileProvider aplikasi sendiri supaya uji
        // tidak bergantung pada aplikasi pihak ketiga yang mungkin tidak ada di emulator.
        val sumber = java.io.File(konteks.cacheDir, "dari-hp-${System.nanoTime()}.css").apply {
            writeText("body { color: #f5b301; }")
        }
        val uri = androidx.core.content.FileProvider.getUriForFile(
            konteks,
            "${konteks.packageName}.berkas",
            sumber,
        )
        val nama = KonteksBerkas.namaTampilan(konteks, uri)
        val projek = java.io.File(konteks.cacheDir, "impor-${System.nanoTime()}").apply { mkdirs() }

        val hasil = konteks.contentResolver.openInputStream(uri)!!.use { aliran ->
            id.kodenusa.core.project.BerkasMasuk.simpan(projek, "", nama, aliran)
        }

        assertTrue("hasil=$hasil", hasil is id.kodenusa.core.project.BerkasMasuk.Hasil.Sukses)
        val berkas = (hasil as id.kodenusa.core.project.BerkasMasuk.Hasil.Sukses).file
        assertEquals(sumber.name, berkas.name)
        assertEquals("body { color: #f5b301; }", berkas.readText())
        // Nama dari penyedia tidak boleh membawa jalur.
        assertTrue(!berkas.name.contains("/"))
    }

    private fun bukaProjek() {
        if (layar.onAllNodesWithText("Buat projek pertama").fetchSemanticsNodes().isNotEmpty()) {
            layar.onNodeWithText("Buat projek pertama").performClick()
        } else {
            layar.onNodeWithText("Projek baru").performClick()
        }
        layar.onNodeWithTag("daftar-wizard").performScrollToNode(hasTestTag("jenis-kosong"))
        layar.onNodeWithTag("jenis-kosong").performClick()
        layar.onNodeWithTag("daftar-wizard").performScrollToNode(hasText("Projek kosong"))
        layar.onNodeWithText("Projek kosong").performClick()
        // Nama projek kini selalu terlihat di bilah bawah wizard — tidak perlu digulir.
        layar.onAllNodes(hasSetTextAction())[0].performTextReplacement("Uji Berkas HP")
        layar.waitForIdle()
        layar.onNodeWithTag("tombol-buat").performClick()
        layar.waitUntil(timeoutMillis = 20_000) {
            layar.onAllNodes(hasText("Uji Berkas HP", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        layar.onAllNodes(hasText("Uji Berkas HP", substring = true))[0].performClick()
        layar.waitUntil(timeoutMillis = 20_000) {
            layar.onAllNodesWithText("AI").fetchSemanticsNodes().isNotEmpty()
        }
    }
}
