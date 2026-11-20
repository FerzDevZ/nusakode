package id.kodenusa.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import id.kodenusa.app.GitSettingsStore
import id.kodenusa.core.git.CommitRingkas
import id.kodenusa.core.git.Hasil
import id.kodenusa.core.git.Perubahan
import id.kodenusa.core.git.RepoGit
import id.kodenusa.ui.CodeTextStyle
import id.kodenusa.ui.GhostButton
import id.kodenusa.ui.KodenusaPalette
import id.kodenusa.ui.Panel
import id.kodenusa.ui.Pill
import id.kodenusa.ui.PrimaryButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Layar Git: simpan perubahan di dalam HP, kirim ke GitHub saat ada sinyal, dan lihat riwayat.
 *
 * Ini yang membedakan KodeNusa dari editor biasa: commit tetap bisa dilakukan **tanpa internet**.
 * Tombol kirim baru aktif kalau repo dan token sudah diisi, dan alasannya selalu ditulis.
 */
@Composable
fun GitScreen(
    projectDir: File,
    onBack: () -> Unit,
) {
    val konteks = LocalContext.current
    val scope = rememberCoroutineScope()
    val setelan = remember(konteks) { GitSettingsStore(konteks) }

    var sibuk by remember { mutableStateOf(false) }
    var adalahRepo by remember(projectDir) { mutableStateOf(RepoGit.adalahRepo(projectDir)) }
    var perubahan by remember(projectDir) { mutableStateOf(Perubahan()) }
    var riwayat by remember(projectDir) { mutableStateOf<List<CommitRingkas>>(emptyList()) }
    var pesan by remember { mutableStateOf("Perubahan dari KodeNusa") }
    var kabar by remember(projectDir) { mutableStateOf<String?>(null) }
    var galat by remember(projectDir) { mutableStateOf<String?>(null) }
    var remote by remember(projectDir) { mutableStateOf(RepoGit.urlRemote(projectDir).orEmpty()) }

    suspend fun segarkan() {
        val keadaan = withContext(Dispatchers.IO) {
            RepoGit.adalahRepo(projectDir) to RepoGit.keadaan(projectDir)
        }
        adalahRepo = keadaan.first
        perubahan = keadaan.second
        riwayat = withContext(Dispatchers.IO) { RepoGit.daftarCommit(projectDir, batas = 20) }
        remote = withContext(Dispatchers.IO) { RepoGit.urlRemote(projectDir).orEmpty() }
    }

    LaunchedEffect(projectDir) { segarkan() }

    fun jalankan(pesanSukses: String, blok: suspend () -> Hasil<*>) {
        sibuk = true
        galat = null
        kabar = null
        scope.launch {
            val hasil = withContext(Dispatchers.IO) { blok() }
            sibuk = false
            when (hasil) {
                is Hasil.Sukses -> {
                    kabar = pesanSukses
                    segarkan()
                }
                is Hasil.Gagal -> galat = hasil.pesan
            }
        }
    }

    val alasanKirim = when {
        !adalahRepo -> "Siapkan repo dulu."
        setelan.alamatRepo().isBlank() -> "Isi alamat repo GitHub di layar Situs, lalu kembali ke sini."
        setelan.token().isBlank() -> "Token GitHub belum diisi."
        else -> null
    }

    Scaffold(containerColor = Color(KodenusaPalette.BACKGROUND)) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                GhostButton("Kembali", onBack)
                Spacer(Modifier.weight(1f))
                when {
                    sibuk -> Pill("bekerja", KodenusaPalette.INFO)
                    adalahRepo -> Pill("repo siap", KodenusaPalette.OK)
                    else -> Pill("belum ada repo", KodenusaPalette.TEXT_DIM)
                }
            }
            Spacer(Modifier.height(12.dp))

            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    Panel(Modifier.fillMaxWidth()) {
                        Column {
                            Text("Simpan perubahan", style = MaterialTheme.typography.titleMedium, color = Color(KodenusaPalette.TEXT))
                            Spacer(Modifier.height(6.dp))
                            Text(
                                if (perubahan.kosong) "Tidak ada berkas yang berubah." else
                                    "${perubahan.jumlah} berkas berubah · +${perubahan.ditambah.size} " +
                                        "~${perubahan.diubah.size} -${perubahan.dihapus.size}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(KodenusaPalette.TEXT_DIM),
                                modifier = Modifier.testTag("ringkasan-git"),
                            )
                            if (!perubahan.kosong) {
                                Spacer(Modifier.height(6.dp))
                                (perubahan.ditambah + perubahan.diubah + perubahan.dihapus).take(6).forEach { jalur ->
                                    Text(jalur, style = CodeTextStyle.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize), color = Color(KodenusaPalette.TEXT_DIM))
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            OutlinedTextField(
                                value = pesan,
                                onValueChange = { pesan = it },
                                label = { Text("Pesan commit") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("pesan-commit"),
                            )
                            Spacer(Modifier.height(10.dp))
                            if (!adalahRepo) {
                                PrimaryButton(
                                    text = "Siapkan repo",
                                    onClick = {
                                        jalankan("Repo siap dipakai.") {
                                            RepoGit.siapkan(projectDir, setelan.nama(), setelan.email()).also { hasil ->
                                                if (hasil is Hasil.Sukses && setelan.alamatRepo().isNotBlank()) {
                                                    RepoGit.aturRemote(projectDir, setelan.alamatRepo())
                                                }
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().testTag("siapkan-repo"),
                                    loading = sibuk,
                                )
                            } else {
                                PrimaryButton(
                                    text = "Simpan perubahan",
                                    onClick = {
                                        jalankan("Perubahan tersimpan.") {
                                            RepoGit.simpanSemua(projectDir, pesan, setelan.nama(), setelan.email())
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().testTag("simpan-perubahan"),
                                    enabled = !sibuk && !perubahan.kosong,
                                    loading = sibuk,
                                )
                                Spacer(Modifier.height(8.dp))
                                GhostButton(
                                    "Kirim ke GitHub",
                                    {
                                        jalankan("Terkirim ke GitHub.") {
                                            RepoGit.aturRemote(projectDir, setelan.alamatRepo()).let { atur ->
                                                if (atur is Hasil.Gagal) {
                                                    atur
                                                } else {
                                                    RepoGit.dorong(projectDir, setelan.token())
                                                }
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().testTag("kirim-github"),
                                    enabled = alasanKirim == null && !sibuk,
                                )
                                alasanKirim?.let { alasan ->
                                    Spacer(Modifier.height(6.dp))
                                    Text(alasan, style = MaterialTheme.typography.bodySmall, color = Color(KodenusaPalette.TEXT_DIM))
                                }
                            }
                            kabar?.let { info ->
                                Spacer(Modifier.height(8.dp))
                                Text(info, style = MaterialTheme.typography.bodyMedium, color = Color(KodenusaPalette.OK))
                            }
                            galat?.let { salah ->
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    salah,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(KodenusaPalette.ERROR),
                                    modifier = Modifier.testTag("galat-git"),
                                )
                            }
                        }
                    }
                }

                item {
                    Panel(Modifier.fillMaxWidth()) {
                        Column {
                            Text("Alamat repo projek", style = MaterialTheme.typography.titleMedium, color = Color(KodenusaPalette.TEXT))
                            Spacer(Modifier.height(6.dp))
                            Text(
                                remote.ifBlank { "Belum diatur. Isi alamat repo di layar Situs." },
                                style = CodeTextStyle.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize),
                                color = Color(KodenusaPalette.TEXT_DIM),
                                modifier = Modifier.testTag("remote-git"),
                            )
                        }
                    }
                }

                item {
                    Text("Riwayat", style = MaterialTheme.typography.titleMedium, color = Color(KodenusaPalette.TEXT), modifier = Modifier.testTag("judul-riwayat"))
                }

                if (riwayat.isEmpty()) {
                    item {
                        Text(
                            if (adalahRepo) "Belum ada commit." else "Riwayat muncul setelah repo disiapkan dan perubahan disimpan.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(KodenusaPalette.TEXT_DIM),
                        )
                    }
                } else {
                    items(riwayat, key = { it.sha }) { commit ->
                        Panel(Modifier.fillMaxWidth()) {
                            Column {
                                Text(commit.pesan, style = MaterialTheme.typography.bodyMedium, color = Color(KodenusaPalette.TEXT))
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "${commit.sha7()} · ${commit.penulis} · ${tanggalRingkas(commit.waktu)}",
                                    style = CodeTextStyle.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize),
                                    color = Color(KodenusaPalette.TEXT_DIM),
                                )
                            }
                        }
                    }
                }

                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

private fun tanggalRingkas(waktu: Long): String =
    SimpleDateFormat("dd MMM yyyy HH:mm", Locale("id", "ID")).format(Date(waktu))