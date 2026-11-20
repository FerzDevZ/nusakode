package id.kodenusa.core.git

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Penguraian alamat repo (selalu jalan, tanpa jaringan) dan satu uji **nyata** ke GitHub yang hanya
 * jalan bila `GH_TOKEN` tersedia.
 *
 * Kenapa uji nyata dipisah: kode unggah APK tidak bisa dibuktikan dengan model tiruan. Tanpa satu
 * kali jalan ke GitHub sungguhan, kita hanya menebak bentuk jawabannya. Uji ini membuat repo
 * sementara, mengunggah berkas, memeriksa tautannya bisa diunduh, lalu **menghapus repo itu lagi**.
 */
class GitHubRilisTest {

    @Test
    fun alamat_repo_github_diurai_jadi_pemilik_dan_nama() {
        assertEquals(RepoGitHub("FerzDevZ", "nusakode"), GitHubRilis.repoDariAlamat("https://github.com/FerzDevZ/nusakode"))
        assertEquals(RepoGitHub("FerzDevZ", "nusakode"), GitHubRilis.repoDariAlamat("https://github.com/FerzDevZ/nusakode.git"))
        assertEquals(RepoGitHub("FerzDevZ", "nusakode"), GitHubRilis.repoDariAlamat("https://github.com/FerzDevZ/nusakode/"))
    }

    @Test
    fun alamat_bukan_github_atau_kurang_lengkap_ditolak() {
        assertNull(GitHubRilis.repoDariAlamat(""))
        assertNull(GitHubRilis.repoDariAlamat("file:///tmp/repo"))
        assertNull(GitHubRilis.repoDariAlamat("https://github.com/FerzDevZ"))
        assertNull(GitHubRilis.repoDariAlamat("https://gitlab.com/FerzDevZ/nusakode"))
    }

    @Test
    fun nama_repo_tidak_sah_dijelaskan_sebelum_dikirim() {
        val gagal = runCatching { GitHubRilis.buatRepo("token-palsu", "repo dengan spasi") }.exceptionOrNull()
        assertTrue(gagal is GitHubGagal, "harus melempar GitHubGagal")
        assertTrue((gagal as GitHubGagal).pesan.contains("huruf, angka"))
    }

    @Test
    fun unggah_ke_rilis_bila_token_tersedia() {
        val token = System.getenv("GH_TOKEN").orEmpty()
        assumeTrue(token.isNotBlank(), "GH_TOKEN tidak diisi — uji jaringan dilewati")

        val namaRepo = "kodenusa-uji-" + System.currentTimeMillis()
        val repo = GitHubRilis.buatRepo(token, namaRepo, privat = true)
        try {
            val berkas = File.createTempFile("kodenusa-uji", ".txt")
            berkas.writeText("uji unggah dari Kotlin")
            val hasil = GitHubRilis.unggahKeRilis(
                token = token,
                repo = repo,
                tag = "build-uji",
                judul = "Build uji",
                catatan = "Dibuat oleh GitHubRilisTest",
                berkas = berkas,
            )
            assertEquals(berkas.length(), hasil.ukuranByte)
            assertTrue(hasil.tautanUnduh.contains("/releases/download/"), "tautan harus menunjuk aset rilis")
            berkas.delete()
        } finally {
            GitHubRilis.hapusRepo(token, repo)
        }
    }
}
