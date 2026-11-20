package id.kodenusa.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import id.kodenusa.core.git.Hasil
import id.kodenusa.core.git.RepoGit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Membuktikan JGit benar-benar berjalan di Android sungguhan, bukan hanya di JVM.
 *
 * Ini penting: di projek ini `java.nio.file` pernah membuat alat Java gagal total di API 24, dan
 * JGit adalah library Java yang bisa kena masalah yang sama. Uji ini menyiapkan repo, commit,
 * lalu push ke remote lokal — semuanya di dalam HP.
 */
@RunWith(AndroidJUnit4::class)
class GitDeviceTest {

    private val konteks = InstrumentationRegistry.getInstrumentation().targetContext

    private fun projekBaru(nama: String): File {
        val akar = File(konteks.cacheDir, "git-uji-$nama")
        akar.deleteRecursively()
        File(akar, "src/main/java/app").mkdirs()
        File(akar, "src/main/java/app/MainActivity.java").writeText("package app;\n\nclass MainActivity {}\n")
        return akar
    }

    @Test
    fun git_bisa_commit_dan_push_di_dalam_hp() {
        val akar = projekBaru("push")
        assertTrue(RepoGit.siapkan(akar, "Ferdinand", "ferz@example.com") is Hasil.Sukses)

        val simpan = RepoGit.simpanSemua(akar, "commit dari HP", "Ferdinand", "ferz@example.com")
        assertTrue("commit gagal: $simpan", simpan is Hasil.Sukses)
        assertTrue(RepoGit.keadaan(akar).kosong)

        // Remote lokal: membuktikan jalur push/pull bekerja tanpa internet.
        val remote = File(konteks.cacheDir, "git-uji-remote.git")
        remote.deleteRecursively()
        org.eclipse.jgit.api.Git.init().setBare(true).setDirectory(remote).call().close()
        assertTrue(RepoGit.aturRemote(akar, "file://${remote.absolutePath}") is Hasil.Sukses)

        val dorong = RepoGit.dorong(akar, token = "")
        assertTrue("push gagal: $dorong", dorong is Hasil.Sukses)

        // Remote bare buatan sendiri belum punya HEAD menunjuk `main` (GitHub selalu punya).
        val remoteGit = org.eclipse.jgit.api.Git.open(remote)
        try {
            remoteGit.repository.updateRef("HEAD").link("refs/heads/main")
        } finally {
            remoteGit.close()
        }

        val tujuan = File(konteks.cacheDir, "git-uji-klon")
        tujuan.deleteRecursively()
        assertTrue(RepoGit.klon("file://${remote.absolutePath}", "", tujuan) is Hasil.Sukses)
        assertTrue(File(tujuan, "src/main/java/app/MainActivity.java").isFile)
    }

    @Test
    fun hasil_build_diabaikan_dan_riwayat_terbaca() {
        val akar = projekBaru("riwayat")
        assertTrue(RepoGit.siapkan(akar, "Ferdinand", "ferz@example.com") is Hasil.Sukses)
        File(akar, "build/outputs").mkdirs()
        File(akar, "build/outputs/app-debug.apk").writeText("biner")

        assertTrue(RepoGit.simpanSemua(akar, "pertama", "Ferdinand", "ferz@example.com") is Hasil.Sukses)

        val riwayat = RepoGit.daftarCommit(akar)
        assertEquals(1, riwayat.size)
        assertEquals("pertama", riwayat[0].pesan)
        assertFalse(
            "build/ tidak boleh masuk commit",
            RepoGit.keadaan(akar).ditambah.any { it.contains("build/") },
        )
    }
}
