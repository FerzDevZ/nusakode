package id.kodenusa.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import id.kodenusa.core.ai.BerkasEdit
import id.kodenusa.core.project.ProjectLoader
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Uji gerbang build yang dipakai agen: hasilnya harus sama dengan tombol Build APK, dan galat yang
 * diterima model harus sudah diterjemahkan — bukan tumpukan teks alat.
 */
@RunWith(AndroidJUnit4::class)
class AiBuildGateTest {

    private val konteks = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var akar: File

    @Before
    fun siapkan() {
        akar = File(konteks.cacheDir, "agen-${System.nanoTime()}")
        Templates(konteks).create("halo-dunia", akar, "Halo Agen", "id.kodenusa.haloagen").getOrThrow()
    }

    @Test
    fun build_lewat_gerbang_agen_menghasilkan_apk() = runBlocking {
        val container = AppContainer(konteks)
        val status = container.toolchainInstaller.install()
        assertTrue("toolchain harus siap di HP", status is ToolchainInstaller.Status.Ready)
        val toolchain = (status as ToolchainInstaller.Status.Ready).toolchain

        val hasil = AiBuildGate(akar, container.newEngine(toolchain)).build()

        assertTrue("build lewat agen harus sukses: ${hasil.galat}", hasil.sukses)
        assertEquals("", hasil.galat)
    }

    @Test
    fun galat_build_diterjemahkan_untuk_model() = runBlocking {
        val container = AppContainer(konteks)
        val status = container.toolchainInstaller.install()
        val toolchain = (status as ToolchainInstaller.Status.Ready).toolchain
        val papan = AiProjectBoard(akar)
        // Java yang tidak bisa dikompilasi: agen harus menerima kalimat yang bisa ditindaklanjuti.
        val tertulis = papan.tulis(BerkasEdit("src/main/java/app/Rusak.java", "package app;\npublic class Rusak { ini salah }\n"))
        assertEquals(null, tertulis)
        assertTrue("projek harus tetap bisa dimuat", ProjectLoader.load(akar).config != null)

        val hasil = AiBuildGate(akar, container.newEngine(toolchain)).build()

        assertFalse(hasil.sukses)
        assertTrue("galat harus berisi penjelasan, bukan kosong", hasil.galat.isNotBlank())
    }
}
