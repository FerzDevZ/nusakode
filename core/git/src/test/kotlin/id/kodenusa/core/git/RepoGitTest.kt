package id.kodenusa.core.git

import org.eclipse.jgit.api.Git
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class RepoGitTest {

    private fun projek(akar: File): File =
        File(akar, "projek").apply {
            mkdirs()
            File(this, "src").mkdirs()
            File(this, "src/MainActivity.java").writeText("class MainActivity {}\n")
        }

    /** Remote lokal (bukan jaringan) supaya uji push/klon berjalan di mana saja, termasuk CI. */
    private fun remoteKosong(akar: File): File =
        File(akar, "remote.git").also { Git.init().setBare(true).setDirectory(it).call().close() }

    /** Remote bare buatan sendiri belum punya HEAD yang menunjuk `main`; GitHub selalu punya. */
    private fun arahkanHeadKeMain(remote: File) {
        Git.open(remote).also { it.repository.updateRef("HEAD").link("refs/heads/main") }.close()
    }

    @Test
    fun siapkan_membuat_repo_gitignore_dan_cabang_main(@TempDir sementara: File) {
        val akar = projek(sementara)

        val hasil = RepoGit.siapkan(akar, "Ferdinand", "ferz@example.com")

        assertTrue(hasil is Hasil.Sukses)
        assertTrue(RepoGit.adalahRepo(akar))
        val gitignore = File(akar, ".gitignore").readText()
        assertTrue(gitignore.contains("build/"), "hasil build tidak boleh masuk repo")
        // Nama cabang baru bisa diganti ke `main` setelah commit pertama (batasan JGit 4.x).
        RepoGit.simpanSemua(akar, "pertama", "Ferdinand", "ferz@example.com")
        val git = Git.open(akar)
        try {
            assertEquals("main", git.repository.branch)
        } finally {
            git.close()
        }
    }

    @Test
    fun simpan_semua_membuat_commit_dan_mengosongkan_status(@TempDir sementara: File) {
        val akar = projek(sementara)
        RepoGit.siapkan(akar, "Ferdinand", "ferz@example.com")
        assertFalse(RepoGit.keadaan(akar).kosong)

        val hasil = RepoGit.simpanSemua(akar, "commit pertama", "Ferdinand", "ferz@example.com")

        assertTrue(hasil is Hasil.Sukses, "commit harus berhasil: $hasil")
        assertTrue(RepoGit.keadaan(akar).kosong, "setelah commit tidak boleh ada perubahan tertinggal")
        assertEquals(1, RepoGit.daftarCommit(akar).size)
        assertEquals("commit pertama", RepoGit.daftarCommit(akar)[0].pesan)
    }

    @Test
    fun simpan_tanpa_perubahan_ditolak_dengan_pesan_jelas(@TempDir sementara: File) {
        val akar = projek(sementara)
        RepoGit.siapkan(akar, "Ferdinand", "ferz@example.com")
        RepoGit.simpanSemua(akar, "pertama", "Ferdinand", "ferz@example.com")

        val hasil = RepoGit.simpanSemua(akar, "kosong", "Ferdinand", "ferz@example.com")

        assertTrue(hasil is Hasil.Gagal)
        assertTrue((hasil as Hasil.Gagal).pesan.contains("Tidak ada perubahan"))
    }

    @Test
    fun hasil_build_tidak_masuk_commit(@TempDir sementara: File) {
        val akar = projek(sementara)
        RepoGit.siapkan(akar, "Ferdinand", "ferz@example.com")
        File(akar, "build").mkdirs()
        File(akar, "build/app-debug.apk").writeText("biner")

        val keadaan = RepoGit.keadaan(akar)

        assertFalse(keadaan.ditambah.any { it.startsWith("build/") }, "build/ harus diabaikan: $keadaan")
    }

    @Test
    fun push_ke_remote_lokal_lalu_klon_menghasilkan_isi_sama(@TempDir sementara: File) {
        val akar = projek(sementara)
        val remote = remoteKosong(sementara)
        RepoGit.siapkan(akar, "Ferdinand", "ferz@example.com")
        RepoGit.simpanSemua(akar, "commit pertama", "Ferdinand", "ferz@example.com")
        assertTrue(RepoGit.aturRemote(akar, "file://${remote.absolutePath}") is Hasil.Sukses)

        val dorong = RepoGit.dorong(akar, token = "")

        assertTrue(dorong is Hasil.Sukses, "push harus berhasil: $dorong")
        arahkanHeadKeMain(remote)
        val tujuan = File(sementara, "salinan")
        val klon = RepoGit.klon("file://${remote.absolutePath}", token = "", tujuan = tujuan)
        assertTrue(klon is Hasil.Sukses, "klon harus berhasil: $klon")
        assertNotNull(File(tujuan, "src/MainActivity.java").takeIf { it.isFile })
        assertEquals("class MainActivity {}\n", File(tujuan, "src/MainActivity.java").readText())
    }

    @Test
    fun push_tanpa_remote_ditolak_dengan_pesan_jelas(@TempDir sementara: File) {
        val akar = projek(sementara)
        RepoGit.siapkan(akar, "Ferdinand", "ferz@example.com")

        val hasil = RepoGit.dorong(akar, token = "apa-saja")

        assertTrue(hasil is Hasil.Gagal)
        assertTrue((hasil as Hasil.Gagal).pesan.contains("Remote belum diatur"))
    }

    @Test
    fun alamat_remote_yang_bukan_https_ditolak(@TempDir sementara: File) {
        val akar = projek(sementara)
        RepoGit.siapkan(akar, "Ferdinand", "ferz@example.com")

        val hasil = RepoGit.aturRemote(akar, "git@github.com:nama/repo.git")

        assertTrue(hasil is Hasil.Gagal, "alamat SSH belum didukung, harus ditolak terang-terangan")
    }

    @Test
    fun token_tidak_pernah_ditulis_ke_konfigurasi_git(@TempDir sementara: File) {
        val akar = projek(sementara)
        RepoGit.siapkan(akar, "Ferdinand", "ferz@example.com")
        RepoGit.aturRemote(akar, "file:///tmp/repo.git")

        val config = File(akar, ".git/config").readText()

        assertFalse(config.contains("ghp_"), "token tidak boleh tersimpan di .git/config")
        assertFalse(config.contains("x-access-token"))
    }
}

class PagesTest {

    private fun remoteBare(akar: File): File =
        File(akar, "remote.git").also { Git.init().setBare(true).setDirectory(it).call().close() }

    @Test
    fun situs_terbit_ke_cabang_gh_pages(@TempDir sementara: File) {
        val situs = File(sementara, "situs").apply { mkdirs() }
        File(situs, "index.html").writeText("<h1>Toko</h1>")
        File(situs, "css").mkdirs()
        File(situs, "css/gaya.css").writeText("body{}")
        val remote = remoteBare(sementara)

        val hasil = RepoGit.terbitkanSitus(situs, "file://${remote.absolutePath}", token = "", pesan = "terbit")

        assertTrue(hasil is Hasil.Sukses, "terbit harus berhasil: $hasil")
        val cek = File(sementara, "cek")
        Git.cloneRepository().setURI("file://${remote.absolutePath}").setDirectory(cek)
            .setBranch("gh-pages").call().close()
        assertTrue(File(cek, "index.html").isFile)
        assertEquals("<h1>Toko</h1>", File(cek, "index.html").readText())
        assertTrue(File(cek, "css/gaya.css").isFile)
    }

    @Test
    fun situs_tanpa_index_ditolak(@TempDir sementara: File) {
        val situs = File(sementara, "situs").apply { mkdirs() }

        val hasil = RepoGit.terbitkanSitus(situs, "https://github.com/a/b.git", "", "x")

        assertTrue(hasil is Hasil.Gagal)
        assertTrue((hasil as Hasil.Gagal).pesan.contains("index.html"))
    }

    @Test
    fun tautan_github_pages_dihitung_dari_alamat_repo() {
        assertEquals("https://ferdi.github.io/warung/", RepoGit.halamanGitHub("https://github.com/ferdi/warung.git"))
        assertEquals("https://ferdi.github.io/warung/", RepoGit.halamanGitHub("https://github.com/ferdi/warung/"))
        assertEquals("https://ferdi.github.io/", RepoGit.halamanGitHub("https://github.com/ferdi/ferdi.github.io"))
        assertEquals(null, RepoGit.halamanGitHub("https://gitlab.com/ferdi/warung"))
    }
}
