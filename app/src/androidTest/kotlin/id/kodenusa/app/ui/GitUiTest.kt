package id.kodenusa.app.ui

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import id.kodenusa.app.GitSettingsStore
import id.kodenusa.app.Templates
import id.kodenusa.core.git.RepoGit
import id.kodenusa.ui.KodenusaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Uji layar Git: commit harus bisa dilakukan tanpa internet, dan tombol kirim tidak boleh aktif
 * sebelum repo/token siap. Bukti terakhir memakai remote lokal (`file://`) supaya uji ini tidak
 * pernah menyentuh jaringan.
 */
@RunWith(AndroidJUnit4::class)
class GitUiTest {

    @get:Rule
    val layar = createComposeRule()

    private val konteks = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun bersihkan() {
        GitSettingsStore(konteks).hapus()
    }

    private fun projek(): File {
        val folder = File(konteks.cacheDir, "git-ui-${System.nanoTime()}")
        return Templates(konteks).create("halo-dunia", folder, "Halo Git", "id.kodenusa.halogit").getOrThrow()
    }

    @Test
    fun siapkan_repo_lalu_simpan_perubahan_muncul_di_riwayat() {
        val projek = projek()
        layar.setContent { KodenusaTheme { GitScreen(projectDir = projek, onBack = {}) } }

        layar.onNodeWithTag("siapkan-repo").performScrollTo().performClick()
        layar.waitUntil(timeoutMillis = 15_000) {
            layar.onAllNodesWithText("repo siap").fetchSemanticsNodes().isNotEmpty()
        }

        layar.onNodeWithTag("pesan-commit").performScrollTo().performTextReplacement("Commit pertama")
        layar.onNodeWithTag("simpan-perubahan").performScrollTo().performClick()
        layar.waitUntil(timeoutMillis = 15_000) {
            layar.onAllNodesWithText("Commit pertama").fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(RepoGit.adalahRepo(projek))
        assertEquals(1, RepoGit.daftarCommit(projek).size)
    }

    @Test
    fun kirim_terkunci_tanpa_token_dan_menuliskan_alasannya() {
        val projek = projek()
        RepoGit.siapkan(projek, "Uji", "uji@localhost")
        GitSettingsStore(konteks).simpan("https://github.com/uji/halo", "")
        layar.setContent { KodenusaTheme { GitScreen(projectDir = projek, onBack = {}) } }

        layar.waitUntil(timeoutMillis = 15_000) {
            layar.onAllNodesWithText("repo siap").fetchSemanticsNodes().isNotEmpty()
        }
        layar.onNodeWithTag("kirim-github").performScrollTo().assertIsNotEnabled()
        layar.onNode(hasText("Token GitHub belum diisi.", substring = true)).assertExists()
    }

    @Test
    fun commit_terkirim_ke_remote_lokal_tanpa_internet() {
        val projek = projek()
        val remote = File(konteks.cacheDir, "remote-${System.nanoTime()}.git")
        RepoGit.siapkan(projek, "Uji", "uji@localhost")
        buatRepoKosong(remote)
        GitSettingsStore(konteks).simpan("file://${remote.absolutePath}", "token-uji")

        layar.setContent { KodenusaTheme { GitScreen(projectDir = projek, onBack = {}) } }
        layar.waitUntil(timeoutMillis = 15_000) {
            layar.onAllNodesWithText("repo siap").fetchSemanticsNodes().isNotEmpty()
        }

        // Remote diatur dari layar lewat tombol kirim; commit dulu supaya ada yang dikirim.
        layar.onNodeWithTag("pesan-commit").performScrollTo().performTextReplacement("Kirim pertama")
        layar.onNodeWithTag("simpan-perubahan").performScrollTo().performClick()
        layar.waitUntil(timeoutMillis = 15_000) {
            layar.onAllNodesWithText("Kirim pertama").fetchSemanticsNodes().isNotEmpty()
        }
        layar.onNodeWithTag("kirim-github").performScrollTo().performClick()
        layar.waitUntil(timeoutMillis = 20_000) {
            layar.onAllNodesWithText("Terkirim ke GitHub.").fetchSemanticsNodes().isNotEmpty() ||
                layar.onAllNodesWithTag("galat-git").fetchSemanticsNodes().isNotEmpty()
        }
        val galat = layar.onAllNodesWithTag("galat-git").fetchSemanticsNodes()
            .map { node -> node.config[SemanticsProperties.Text].joinToString() }
        assertTrue("push gagal: ${galat.firstOrNull()}", galat.isEmpty())
        layar.onNodeWithTag("galat-git").assertDoesNotExist()
        val repoRemote = org.eclipse.jgit.api.Git.open(remote)
        val adaMain = repoRemote.repository.resolve("refs/heads/main") != null
        repoRemote.close()
        assertTrue("remote harus menerima commit di cabang main", adaMain)
    }

    private fun buatRepoKosong(folder: File) {
        org.eclipse.jgit.api.Git.init().setBare(true).setDirectory(folder).call().close()
    }
}
