package id.kodenusa.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import id.kodenusa.app.GitSettingsStore
import id.kodenusa.core.git.GitHubGagal
import id.kodenusa.core.git.GitHubRilis
import id.kodenusa.core.git.RepoGit
import id.kodenusa.ui.GhostButton
import id.kodenusa.ui.KodenusaPalette
import id.kodenusa.ui.Panel
import id.kodenusa.ui.PrimaryButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Panel "Buat repo baru di GitHub".
 *
 * Fungsi REST-nya (`GitHubRilis.buatRepo`) sudah ada sejak 0.9.5 dan teruji dengan token sungguhan
 * (`GitHubRilisTest`) — panel ini hanya menyambungkannya ke UI: repo dibuat, remote projek diarahkan
 * ke sana, dan alamatnya disimpan supaya tombol "Kirim ke GitHub" langsung siap.
 */
@Composable
fun BuatRepoCard(projectDir: File, setelan: GitSettingsStore, onSelesai: () -> Unit) {
    var buka by remember { mutableStateOf(false) }

    Panel(Modifier.fillMaxWidth()) {
        Column {
            Text(
                "Belum punya repo untuk projek ini?",
                style = MaterialTheme.typography.titleMedium,
                color = Color(KodenusaPalette.TEXT),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Buat repo baru di akun GitHub Anda, lalu alamatnya dipasang otomatis ke projek ini.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(KodenusaPalette.TEXT_DIM),
            )
            Spacer(Modifier.height(8.dp))
            GhostButton(
                "Buat repo baru",
                { buka = true },
                Modifier.testTag("buka-buat-repo"),
            )
        }
    }

    if (buka) {
        BuatRepoDialog(projectDir, setelan, onTutup = { buka = false }, onSelesai = {
            buka = false
            onSelesai()
        })
    }
}

@Composable
private fun BuatRepoDialog(
    projectDir: File,
    setelan: GitSettingsStore,
    onTutup: () -> Unit,
    onSelesai: () -> Unit,
) {
    var nama by remember { mutableStateOf(namaRepoAwal(projectDir.name)) }
    var privat by remember { mutableStateOf(true) }
    var sibuk by remember { mutableStateOf(false) }
    var galat by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val token = setelan.token()
    val tanpaToken = token.isBlank()

    AlertDialog(
        onDismissRequest = { if (!sibuk) onTutup() },
        title = { Text("Buat repo baru") },
        text = {
            Column {
                Text(
                    if (tanpaToken) "Token GitHub belum diisi. Isi dulu di layar Situs, lalu kembali ke sini."
                    else "Repo dibuat di akun GitHub Anda dengan token yang tersimpan.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(KodenusaPalette.TEXT_DIM),
                    modifier = Modifier.testTag("catatan-buat-repo"),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = nama,
                    onValueChange = { nama = it },
                    label = { Text("Nama repo") },
                    singleLine = true,
                    enabled = !sibuk && !tanpaToken,
                    modifier = Modifier.fillMaxWidth().testTag("nama-repo"),
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = privat,
                        onCheckedChange = { privat = it },
                        enabled = !sibuk && !tanpaToken,
                    )
                    Text(
                        "Privat (hanya Anda yang bisa melihat)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(KodenusaPalette.TEXT_DIM),
                    )
                }
                galat?.let { pesan ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        pesan,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(KodenusaPalette.ERROR),
                        modifier = Modifier.testTag("galat-buat-repo"),
                    )
                }
            }
        },
        confirmButton = {
            PrimaryButton(
                text = "Buat",
                onClick = {
                    sibuk = true
                    galat = null
                    scope.launch {
                        val hasil = withContext(Dispatchers.IO) {
                            runCatching { GitHubRilis.buatRepo(token, nama.trim(), privat) }
                                .fold(
                                    onSuccess = { repo ->
                                        val alamat = "https://github.com/${repo.pemilik}/${repo.nama}.git"
                                        RepoGit.aturRemote(projectDir, alamat)
                                        setelan.simpan(alamat, token)
                                        "https://github.com/${repo.pemilik}/${repo.nama}"
                                    },
                                    onFailure = { gagal ->
                                        (gagal as? GitHubGagal)?.let { g ->
                                            listOfNotNull(g.message, g.saran.takeIf { it.isNotBlank() })
                                                .joinToString(" ")
                                        } ?: (gagal.message ?: "Gagal membuat repo.")
                                    },
                                )
                        }
                        sibuk = false
                        when {
                            hasil.startsWith("https://") -> onSelesai()
                            else -> galat = hasil
                        }
                    }
                },
                modifier = Modifier.testTag("jalankan-buat-repo"),
                enabled = !sibuk && !tanpaToken,
                loading = sibuk,
            )
        },
        dismissButton = { TextButton(onClick = { if (!sibuk) onTutup() }) { Text("Batal") } },
        containerColor = Color(KodenusaPalette.SURFACE),
    )
}

/** Nama repo awal dari nama folder projek; karakter terlarang diganti strip, bukan ditolak, karena ini saran awal yang masih bisa diedit. */
internal fun namaRepoAwal(namaProjek: String): String =
    namaProjek.trim().replace(Regex("[^A-Za-z0-9._-]+"), "-").trim('-')
        .take(60).ifBlank { "kodenusa-projek" }
