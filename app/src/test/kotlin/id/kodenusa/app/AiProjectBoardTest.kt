package id.kodenusa.app

import id.kodenusa.core.ai.BerkasEdit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class AiProjectBoardTest {

    private fun projek(akar: File): File {
        File(akar, "kodenusa.json").writeText("""{"name":"Uji","applicationId":"id.uji.app"}""")
        File(akar, "src/main/java/app").mkdirs()
        File(akar, "src/main/java/app/MainActivity.java").writeText("package app;\npublic class MainActivity {}\n")
        File(akar, "src/main/res/values/strings.xml").apply { parentFile.mkdirs() }
            .writeText("<resources><string name=\"app_name\">Uji</string></resources>")
        return akar
    }

    @Test
    fun konteks_memuat_manifes_dan_berkas_java(@TempDir sementara: File) {
        val papan = AiProjectBoard(projek(sementara))
        val konteks = papan.konteks()

        assertTrue(konteks.contains("kodenusa.json"), "manifes harus ikut")
        assertTrue(konteks.contains("MainActivity.java"), "daftar berkas harus ikut")
        assertTrue(konteks.contains("public class MainActivity"), "isi berkas java harus ikut")
    }

    @Test
    fun konteks_tidak_membengkak_melebihi_batas(@TempDir sementara: File) {
        val akar = projek(sementara)
        val besar = File(akar, "src/main/assets").apply { mkdirs() }
        repeat(20) { nomor -> File(besar, "berkas$nomor.js").writeText("x".repeat(500)) }

        val konteks = AiProjectBoard(akar, maksKarakter = 2400).konteks()

        assertTrue(konteks.length <= 2400, "konteks ${konteks.length} karakter melewati batas 2400")
    }

    @Test
    fun folder_build_dan_git_tidak_ikut_dikirim(@TempDir sementara: File) {
        val akar = projek(sementara)
        File(akar, "build/situs").mkdirs()
        File(akar, "build/situs/index.html").writeText("<html></html>")
        File(akar, ".git").mkdirs()

        val konteks = AiProjectBoard(akar).konteks()

        assertTrue(!konteks.contains("build/situs/index.html"), "hasil build tidak boleh dikirim ke model")
        assertTrue(!konteks.contains(".git/"), "isi .git tidak boleh dikirim ke model")
    }

    @Test
    fun tulis_menyimpan_berkas_baru(@TempDir sementara: File) {
        val akar = projek(sementara)
        val papan = AiProjectBoard(akar)

        val galat = papan.tulis(BerkasEdit("src/main/java/app/LayarKedua.java", "package app;\npublic class LayarKedua {}\n"))

        assertNull(galat, "menulis di dalam projek harus berhasil")
        assertEquals("package app;\npublic class LayarKedua {}\n", File(akar, "src/main/java/app/LayarKedua.java").readText())
    }

    @Test
    fun tulis_menolak_jalur_yang_keluar_projek(@TempDir sementara: File) {
        val akar = projek(sementara)
        val papan = AiProjectBoard(akar)

        listOf("/etc/passwd", "../luar.java", "src/../../luar.java", "~/rahasia.txt").forEach { jalur ->
            val galat = papan.tulis(BerkasEdit(jalur, "isi"))
            assertTrue(galat != null, "jalur '$jalur' seharusnya ditolak")
        }
        assertTrue(!File(sementara.parentFile, "luar.java").exists())
    }

    @Test
    fun tulis_menolak_berkas_biner(@TempDir sementara: File) {
        val akar = projek(sementara)
        val galat = AiProjectBoard(akar).tulis(BerkasEdit("app-release.apk", "bukan apk asli"))
        assertTrue(galat != null, "agen tidak boleh menulis berkas biner")
    }
}
