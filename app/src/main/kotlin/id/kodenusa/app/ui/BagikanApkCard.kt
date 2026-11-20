package id.kodenusa.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import id.kodenusa.app.PenyimpananProjek
import id.kodenusa.core.git.GitHubGagal
import id.kodenusa.core.git.GitHubRilis
import id.kodenusa.core.git.HasilUnggah
import id.kodenusa.ui.GhostButton
import id.kodenusa.ui.KodenusaPalette
import id.kodenusa.ui.Panel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Kartu "Bagikan APK" di layar Build.
 *
 * Kenapa ada: selama ini hasil build berhenti di folder projek. Untuk mengirim APK ke orang lain
 * pengguna harus menyalin berkas ke PC — persis hal yang tidak mungkin bagi sasaran aplikasi ini.
 * Dengan kartu ini APK naik langsung ke GitHub Release repo pengguna, dan tautannya bisa dibagikan
 * lewat WhatsApp.
 *
 * Yang sengaja ditampilkan sebelum menekan tombol: **ukuran** dan **tujuan** unggahan. Unggahan 32 MB
 * di jaringan Indonesia bisa memakan puluhan MB kuota; menyembunyikan angka itu tidak jujur.
 */
@Composable
fun BagikanApkCard(
    apk: File,
    store: GitSettingsStore,
    versi: String,
) {
    val scope = rememberCoroutineScope()
    var sedang by remember(apk) { mutableStateOf(false) }
    var terkirim by remember(apk) { mutableStateOf(0L) }
    var hasil by remember(apk) { mutableStateOf<HasilUnggah?>(null) }
    var galat by remember(apk) { mutableStateOf<String?>(null) }

    Panel(Modifier.fillMaxWidth()) {
        Column {
            Text(
                "Bagikan APK ke GitHub",
                style = MaterialTheme.typography.titleMedium,
                color = Color(KodenusaPalette.TEXT),
            )
            Spacer(Modifier.height(4.dp))
            val repo = GitHubRilis.repoDariAlamat(store.alamatRepo())
            Text(
                when {
                    store.token().isBlank() -> "Token GitHub belum diisi. Isi dulu di menu Git."
                    repo == null -> "Alamat repo GitHub belum diisi. Isi dulu di menu Git."
                    else -> "Naik ke ${repo.pemilik}/${repo.nama} sebagai rilis v$versi."
                },
                style = MaterialTheme.typography.bodySmall,
                color = Color(KodenusaPalette.TEXT_DIM),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                PenyimpananProjek.ringkasByte(apk.length()) + " akan diunggah · butuh kuota internet",
                style = MaterialTheme.typography.labelSmall,
                color = Color(KodenusaPalette.WARN),
                modifier = Modifier.testTag("info-unggah"),
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                GhostButton(
                    if (sedang) "Mengunggah…" else "Unggah APK",
                    {
                        val token = store.token()
                        val tujuan = repo
                        if (token.isBlank() || tujuan == null) {
                            galat = "Lengkapi alamat repo dan token GitHub dulu di menu Git."
                            return@GhostButton
                        }
                        sedang = true
                        galat = null
                        hasil = null
                        terkirim = 0L
                        val judul = "KodeNusa build " + apk.name
                        val catatan = "Dibangun di HP dengan KodeNusa $versi pada " +
                            SimpleDateFormat("d MMM yyyy HH:mm", Locale("id", "ID")).format(Date())
                        scope.launch {
                            val jawab = withContext(Dispatchers.IO) {
                                runCatching {
                                    GitHubRilis.unggahKeRilis(
                                        token = token,
                                        repo = tujuan,
                                        tag = "build-$versi",
                                        judul = judul,
                                        catatan = catatan,
                                        berkas = apk,
                                        onProgres = { bita ->
                                            // Pembaruan state Compose harus di thread utama; callback
                                            // ini datang dari thread IO unggahan.
                                            scope.launch { terkirim = bita }
                                        },
                                    )
                                }
                            }
                            sedang = false
                            jawab.fold(
                                onSuccess = { hasil = it },
                                onFailure = { gagal ->
                                    galat = if (gagal is GitHubGagal) {
                                        gagal.pesan + " " + gagal.saran
                                    } else {
                                        "Unggahan gagal: ${gagal.message ?: gagal.javaClass.simpleName}"
                                    }
                                },
                            )
                        }
                    },
                    Modifier.testTag("unggah-apk"),
                )
            }
            if (sedang) {
                Spacer(Modifier.height(6.dp))
                val persen = if (apk.length() > 0) (terkirim * 100 / apk.length()).toInt() else 0
                Text(
                    "Mengunggah $persen% (${PenyimpananProjek.ringkasByte(terkirim)} dari " +
                        PenyimpananProjek.ringkasByte(apk.length()) + ")",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(KodenusaPalette.INFO),
                    modifier = Modifier.testTag("progres-unggah"),
                )
            }
            hasil?.let { sukses ->
                Spacer(Modifier.height(8.dp))
                Text(
                    "Terunggah. Tautan: ${sukses.tautanUnduh}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(KodenusaPalette.OK),
                    modifier = Modifier.testTag("tautan-apk"),
                )
            }
            galat?.let { pesan ->
                Spacer(Modifier.height(8.dp))
                Text(
                    pesan,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(KodenusaPalette.ERROR),
                    modifier = Modifier.testTag("galat-unggah"),
                )
            }
        }
    }
}
