package id.kodenusa.app

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Uji cadangan otomatis sebelum AI menimpa berkas. Yang diuji adalah janji utamanya: kerja
 * pengguna tidak hilang selamanya karena satu jawaban model yang salah.
 */
class UndoTulisTest {

    @TempDir
    lateinit var root: File

    @Test
    fun berkas_lama_dikembalikan_dan_berkas_baru_dihapus() {
        val folder = File(root, "undo")
        val projek = File(root, "projek").apply { mkdirs() }
        val undo = UndoTulis(folder)

        val lama = File(projek, "MainActivity.java").apply { writeText("versi lama") }
        val batch = undo.siapkanBatch()
        assertTrue(undo.simpanLama(batch, "MainActivity.java", lama))
        assertTrue(undo.tandaiBaru(batch, "src/main/java/app/LayarKedua.java"))

        // AI menulis: menimpa yang lama dan membuat yang baru.
        lama.writeText("versi rusak dari AI")
        val baru = File(projek, "src/main/java/app/LayarKedua.java")
        baru.parentFile.mkdirs()
        baru.writeText("kelas baru")

        val berubah = undo.batalkan(batch, projek)

        assertEquals(2, berubah.size)
        assertEquals("versi lama", lama.readText())
        assertFalse(baru.exists(), "berkas baru harus dihapus kembali")
    }

    @Test
    fun berkas_yang_belum_ada_tidak_dicadangkan() {
        val folder = File(root, "undo2")
        val projek = File(root, "projek2").apply { mkdirs() }
        val undo = UndoTulis(folder)

        val batch = undo.siapkanBatch()
        assertFalse(undo.simpanLama(batch, "TidakAda.java", File(projek, "TidakAda.java")))
        assertTrue(File(folder, batch).isDirectory, "batch tetap ada walau kosong")
        assertTrue(undo.batalkan(batch, projek).isEmpty())
    }

    @Test
    fun hanya_lima_batch_terakhir_dipertahankan() {
        val folder = File(root, "undo3")
        val undo = UndoTulis(folder)
        repeat(7) { undo.siapkanBatch() }

        val sisa = folder.listFiles()?.filter { it.isDirectory }.orEmpty()
        assertEquals(5, sisa.size)
    }

    @Test
    fun batalkan_batch_yang_tidak_ada_tidak_mengubah_apapun() {
        val projek = File(root, "projek4").apply { mkdirs() }
        val undo = UndoTulis(File(root, "undo4"))

        assertTrue(undo.batalkan("9999", projek).isEmpty())
    }
}
