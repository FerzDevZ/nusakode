package id.kodenusa.app.ui

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import id.kodenusa.app.GitSettingsStore
import id.kodenusa.ui.KodenusaTheme
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Kartu "Bagikan APK" tanpa jaringan.
 *
 * Yang dibuktikan: tanpa token/repo pengguna **tidak** dikirim ke unggahan yang pasti gagal, dan
 * alasannya disebut dalam Bahasa Indonesia. Ukuran berkas juga wajib tampil sebelum menekan, karena
 * unggahan APK 32 MB memakan kuota.
 */
@RunWith(AndroidJUnit4::class)
class BagikanApkUiTest {

    @get:Rule
    val layar = createComposeRule()

    private val konteks: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun kosongkanSetelan() {
        GitSettingsStore(konteks).hapus()
    }

    @Test
    fun tanpa_token_dijelaskan_dan_ukuran_tampil_sebelum_unggah() {
        val apk = File(konteks.cacheDir, "uji-bagikan.apk")
        apk.writeBytes(ByteArray(2 * 1024 * 1024))

        layar.setContent {
            KodenusaTheme {
                BagikanApkCard(apk, GitSettingsStore(konteks), "0.9.5")
            }
        }

        layar.onNodeWithText("Bagikan APK ke GitHub").assertExists()
        layar.onNodeWithText("Token GitHub belum diisi. Isi dulu di menu Git.").assertExists()
        layar.onNodeWithTag("info-unggah").assertExists()
        assertTrue(apk.isFile)

        layar.onNodeWithTag("unggah-apk").performClick()
        layar.onNodeWithTag("galat-unggah").assertExists()
        layar.onNodeWithText("Lengkapi alamat repo dan token GitHub dulu di menu Git.").assertExists()
        apk.delete()
    }

    @Test
    fun alamat_repo_github_ditampilkan_sebagai_tujuan() {
        GitSettingsStore(konteks).simpan("https://github.com/FerzDevZ/kodenusa-uji", "token-uji")
        val apk = File(konteks.cacheDir, "uji-tujuan.apk")
        apk.writeBytes(ByteArray(1024))

        layar.setContent {
            KodenusaTheme {
                BagikanApkCard(apk, GitSettingsStore(konteks), "0.9.5")
            }
        }

        layar.onNodeWithText("Naik ke FerzDevZ/kodenusa-uji sebagai rilis v0.9.5.").assertExists()
        apk.delete()
    }
}
