package id.kodenusa.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import id.kodenusa.app.AppContainer
import id.kodenusa.core.git.Hasil
import id.kodenusa.core.git.RepoGit
import id.kodenusa.ui.GhostButton
import id.kodenusa.ui.KodenusaPalette
import id.kodenusa.ui.Panel
import id.kodenusa.ui.PrimaryButton
import id.kodenusa.ui.Kode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Dialog "Ambil projek dari GitHub".
 *
 * Kode inti klon sudah ada di `RepoGit.klon` (JGit) dan sudah teruji offline lewat remote
 * `file://`. Dialog ini hanya menyambungkannya ke UI: alamat, token opsional, nama folder
 * yang unik, dan pesan galat yang bisa dibaca.
 */
@Composable
fun KlonProjekDialog(
    tujuanInduk: File,
    alamatAwal: String,
    tokenAwal: String,
    onTutup: () -> Unit,
    onSelesai: (File) -> Unit,
) {
    var alamat by remember { mutableStateOf(alamatAwal) }
    var token by remember { mutableStateOf(tokenAwal) }
    var sibuk by remember { mutableStateOf(false) }
    var galat by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { if (!sibuk) onTutup() },
        title = { Text("Ambil projek dari GitHub") },
        text = {
            Column {
                Text(
                    "Kode diunduh sebagai folder projek biasa, lalu bisa langsung dibangun di sini.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Kode.textDim,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = alamat,
                    onValueChange = { alamat = it },
                    label = { Text("Alamat repo (https://…)") },
                    singleLine = true,
                    enabled = !sibuk,
                    modifier = Modifier.fillMaxWidth().testTag("alamat-klon"),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text("Token GitHub (opsional untuk repo publik)") },
                    singleLine = true,
                    enabled = !sibuk,
                    modifier = Modifier.fillMaxWidth().testTag("token-klon"),
                )
                galat?.let { pesan ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        pesan,
                        style = MaterialTheme.typography.bodySmall,
                        color = Kode.error,
                        modifier = Modifier.testTag("galat-klon"),
                    )
                }
            }
        },
        confirmButton = {
            PrimaryButton(
                text = "Unduh",
                onClick = {
                    val nama = namaFolder(alamat)
                    val alasan = alasanDitolak(alamat, nama, tujuanInduk)
                    if (alasan != null) {
                        galat = alasan
                    } else {
                        sibuk = true
                        galat = null
                        val tujuan = File(tujuanInduk, nama!!)
                        scope.launch {
                            val hasil = withContext(Dispatchers.IO) {
                                RepoGit.klon(alamat.trim(), token.trim(), tujuan)
                            }
                            sibuk = false
                            when (hasil) {
                                is Hasil.Sukses -> onSelesai(tujuan)
                                is Hasil.Gagal -> galat = hasil.pesan
                            }
                        }
                    }
                },
                modifier = Modifier.testTag("mulai-klon"),
                enabled = !sibuk,
                loading = sibuk,
            )
        },
        dismissButton = {
            TextButton(onClick = { if (!sibuk) onTutup() }) { Text("Batal") }
        },
        containerColor = Kode.surface,
    )
}

/** Kartu kecil di layar projek yang membuka dialog klon. */
@Composable
fun KlonProjekCard(onBuka: () -> Unit) {
    Panel(Modifier.fillMaxWidth()) {
        Column {
            Text(
                "Punya projek di GitHub?",
                style = MaterialTheme.typography.titleMedium,
                color = Kode.text,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Unduh repo Anda sendiri ke HP ini, lalu lanjutkan mengerjakannya di sini.",
                style = MaterialTheme.typography.bodySmall,
                color = Kode.textDim,
            )
            Spacer(Modifier.height(8.dp))
            GhostButton("Ambil dari GitHub", onBuka, Modifier.testTag("buka-klon"))
        }
    }
}

/** Nama folder diambil dari segmen terakhir alamat; alamat aneh tetap ditolak, bukan disanitasi. */
internal fun namaFolder(alamat: String): String? {
    val segmen = alamat.trim().trimEnd('/').substringAfterLast('/')
    val nama = segmen.removeSuffix(".git")
    return if (nama.matches(Regex("[A-Za-z0-9._-]{1,60}"))) nama else null
}

/** Validasi terang-terangan: https:// atau file:// (remote lokal dipakai uji), bukan disanitasi diam-diam. */
internal fun alasanDitolak(alamat: String, nama: String?, tujuanInduk: File): String? = when {
    !alamat.trim().startsWith("https://") && !alamat.trim().startsWith("file://") ->
        "Alamat harus diawali https:// (atau file:// untuk repo di HP ini)."
    nama == null ->
        "Bagian terakhir alamat tidak bisa dipakai sebagai nama folder. Gunakan alamat repo yang biasa, misalnya https://github.com/pemilik/nama-repo.git"
    File(tujuanInduk, nama).exists() ->
        "Nama folder \"$nama\" sudah dipakai projek lain. Hapus atau ganti namanya dulu."
    else -> null
}
