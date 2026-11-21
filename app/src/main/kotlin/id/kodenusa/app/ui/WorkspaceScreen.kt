package id.kodenusa.app.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.testTag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import id.kodenusa.app.AppContainer
import id.kodenusa.app.GitSettingsStore
import id.kodenusa.app.ProjectArchive
import id.kodenusa.app.ProjectSummary
import id.kodenusa.ui.EmptyState
import id.kodenusa.ui.GhostButton
import id.kodenusa.ui.angkaTabular
import id.kodenusa.ui.KodenusaPalette
import id.kodenusa.ui.Panel
import id.kodenusa.ui.Pill
import id.kodenusa.ui.PrimaryButton
import id.kodenusa.ui.Kode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun WorkspaceScreen(
    container: AppContainer,
    onOpenProject: (java.io.File) -> Unit,
    onNewProject: () -> Unit,
    onBukaTema: () -> Unit = {},
) {
    val konteks = androidx.compose.ui.platform.LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    var deleting by remember { mutableStateOf<ProjectSummary?>(null) }
    var pesan by remember { mutableStateOf<String?>(null) }
    var ekspor by remember { mutableStateOf<ProjectSummary?>(null) }
    var klon by remember { mutableStateOf(false) }
    val setelanGit = remember(konteks) { GitSettingsStore(konteks) }
    val projects = remember(refresh) { container.projectStore.list() }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    // Menyimpan zip ke lokasi yang dipilih pengguna (Unduhan, Drive, kartu SD).
    val pemilihEkspor = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        val projek = ekspor
        ekspor = null
        if (uri == null || projek == null) return@rememberLauncherForActivityResult
        scope.launch {
            val hasil = withContext(Dispatchers.IO) {
                runCatching {
                    val sementara = java.io.File(container.workspaceRoot, ".ekspor.zip")
                    val berkas = ProjectArchive.export(projek.dir, sementara).getOrThrow()
                    konteks.contentResolver.openOutputStream(uri)?.use { keluar ->
                        sementara.inputStream().use { it.copyTo(keluar) }
                    } ?: throw IllegalStateException("Lokasi tujuan tidak bisa ditulis.")
                    sementara.delete()
                    berkas
                }
            }
            pesan = hasil.fold(
                onSuccess = { "Projek ${projek.name} tersimpan ($it berkas)." },
                onFailure = { "Ekspor gagal: ${it.message}" },
            )
        }
    }

    // Membuka zip projek yang dipilih dari penyimpanan mana pun.
    var mintaImpor by remember { mutableStateOf(false) }
    val pemilihImpor = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val hasil = withContext(Dispatchers.IO) {
                runCatching {
                    val sementara = java.io.File(container.workspaceRoot, ".impor.zip")
                    konteks.contentResolver.openInputStream(uri)?.use { masuk ->
                        sementara.outputStream().use { masuk.copyTo(it) }
                    } ?: throw IllegalStateException("Berkas tidak bisa dibaca.")
                    val folder = ProjectArchive.import(sementara, container.workspaceRoot).getOrThrow()
                    sementara.delete()
                    folder
                }
            }
            hasil.onSuccess { folder ->
                refresh += 1
                pesan = "Projek dibuka dari zip."
                onOpenProject(folder)
            }.onFailure { pesan = "Impor gagal: ${it.message}" }
        }
    }

    LaunchedEffect(mintaImpor) {
        if (mintaImpor) {
            mintaImpor = false
            pemilihImpor.launch(arrayOf("application/zip", "application/octet-stream"))
        }
    }

    // Cadangkan semua projek jadi satu zip ke lokasi pilihan pengguna.
    var mintaCadangkan by remember { mutableStateOf(false) }
    val pemilihCadangkan = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        mintaCadangkan = false
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val hasil = withContext(Dispatchers.IO) {
                runCatching {
                    val sementara = java.io.File(container.workspaceRoot, ".cadangan-semua.zip")
                    val jumlah = ProjectArchive.exportSemua(
                        container.projectStore.list().map { it.dir }, sementara,
                    ).getOrThrow()
                    konteks.contentResolver.openOutputStream(uri)?.use { keluar ->
                        sementara.inputStream().use { it.copyTo(keluar) }
                    } ?: throw IllegalStateException("Lokasi tujuan tidak bisa ditulis.")
                    sementara.delete()
                    jumlah
                }
            }
            pesan = hasil.fold(
                onSuccess = { "Cadangan $it projek tersimpan." },
                onFailure = { "Cadangan gagal: ${it.message}" },
            )
        }
    }

    Scaffold(containerColor = Kode.background) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(20.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Projek saya", style = MaterialTheme.typography.headlineMedium, color = Kode.text)
                GhostButton("Tema", onBukaTema, Modifier.testTag("buka-tema"))
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "${projects.size} projek di perangkat ini. Semua tersimpan sebagai folder biasa.",
                style = MaterialTheme.typography.bodySmall,
                color = Kode.textDim,
            )
            Spacer(Modifier.height(16.dp))

            Spacer(Modifier.height(16.dp))

            // Penjelasan jujur kenapa TIDAK ada dialog izin penyimpanan saat aplikasi dibuka.
            // Pengguna Android biasa mengharapkan dialog "izinkan akses penyimpanan" dan menganggap
            // aplikasinya rusak bila tidak muncul (laporan dari Samsung A17 5G, Android 15).
            // Ini keputusan desain ADR-022: SAF butuh nol izin, dan itu justru lebih aman.
            // DULU ada dua tempat yang menjelaskan hal yang sama (kartu ini + teks redup di bawah
            // tombol impor) — kini satu sumber saja supaya layar tidak boros ruang.
            Panel(Modifier.fillMaxWidth().testTag("info-tanpa-izin")) {
                // Panel adalah Box: tanpa Column, judul dan isi menumpuk di titik yang sama
                // (bug yang sama dengan ProjectRow — ditemukan lewat screenshot + bounds v0.16).
                Column {
                    Text(
                        "Tidak ada dialog izin penyimpanan? Itu memang disengaja.",
                        style = MaterialTheme.typography.titleSmall,
                        color = Kode.text,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Projek disimpan di folder privat aplikasi, jadi tidak butuh izin apa pun. " +
                            "Berkas masuk dan keluar lewat pemilih berkas Android (Impor .zip, Ekspor, " +
                            "Foto) — Anda yang memilih berkas mana yang dibuka, bukan aplikasi. " +
                            "Lebih aman dan tetap bekerja di semua Android.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Kode.textDim,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Laporan galat dari proses yang mati sebelumnya. Ditempatkan paling atas supaya
            // terlihat sebelum pengguna mulai bekerja, dan hilang begitu dihapus.
            LaporanGalatCard(onHapus = { refresh += 1 })

            if (projects.isEmpty()) {
                // Keadaan kosong mendapat panggung utama — dan TIDAK meninggalkan paruh bawah layar
                // menggantung: aksi sekunder (impor, GitHub) ikut dikelompokkan di dalam panel kosong
                // supaya semua jalan masuk terlihat dalam satu pandangan (temuan audit v0.16).
                Panel(Modifier.fillMaxWidth()) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        EmptyState(
                            title = "Belum ada projek",
                            body = "Mulai dari template kasir, katalog, atau aplikasi halo dunia. Tidak perlu koneksi internet.",
                            action = { PrimaryButton("Buat projek pertama", onNewProject) },
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Peluncur pemilih berkas TIDAK dipanggil langsung di dalam onClick: di
                            // Android 11+, peluncuran intent di tengah pembongkaran koneksi aksesibilitas
                            // Compose membuat main thread menunggu Binder dan berakhir ANR. Bendera
                            // state dipakai supaya peluncuran terjadi setelah bingkai ini selesai.
                            GhostButton(
                                "Impor .zip",
                                { mintaImpor = true },
                                Modifier.testTag("impor-zip"),
                            )
                            GhostButton("Dari GitHub", { klon = true }, Modifier.testTag("buka-klon"))
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            if (projects.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GhostButton(
                        "Impor .zip",
                        { mintaImpor = true },
                        Modifier.testTag("impor-zip"),
                    )
                    GhostButton("Dari GitHub", { klon = true }, Modifier.testTag("buka-klon"))
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Ekspor projek ada di menu ⋯ pada tiap baris.",
                style = MaterialTheme.typography.labelSmall,
                color = Kode.textDim,
            )
            pesan?.let { teks ->
                Spacer(Modifier.height(6.dp))
                Text(teks, style = MaterialTheme.typography.bodySmall, color = Kode.accent)
            }
            Spacer(Modifier.height(16.dp))

            if (projects.isNotEmpty()) {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp),
                ) {
                    items(projects, key = { it.dir.path }) { project ->
                        ProjectRow(
                            project = project,
                            onClick = {
                                project.dir.setLastModified(System.currentTimeMillis())
                                onOpenProject(project.dir)
                            },
                            onDelete = { deleting = project },
                            onExport = {
                                ekspor = project
                                pemilihEkspor.launch("${project.name.replace(Regex("[^A-Za-z0-9._-]+"), "-")}.zip")
                            },
                        )
                    }
                }
            }
        }

        if (projects.isNotEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(20.dp),
                verticalArrangement = Arrangement.Bottom,
                horizontalAlignment = Alignment.End,
            ) {
                GhostButton(
                    "Cadangkan semua",
                    { pemilihCadangkan.launch("kodenusa-cadangan.zip") },
                    Modifier.testTag("cadangkan-semua"),
                )
                Spacer(Modifier.height(8.dp))
                PrimaryButton("Projek baru", onNewProject)
            }
        }
    }

    deleting?.let { project ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Hapus ${project.name}?") },
            text = {
                Text("Folder projek beserta seluruh kodenya dihapus dari perangkat. Tindakan ini tidak bisa dibatalkan.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        container.projectStore.delete(project.dir)
                        deleting = null
                        refresh += 1
                    },
                ) { Text("Hapus", color = Kode.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text("Batal") }
            },
            containerColor = Kode.surface,
        )
    }

    if (klon) {
        KlonProjekDialog(
            tujuanInduk = container.workspaceRoot,
            alamatAwal = setelanGit.alamatRepo(),
            tokenAwal = setelanGit.token(),
            onTutup = { klon = false },
            onSelesai = { folder ->
                klon = false
                refresh += 1
                pesan = "Projek diunduh dari GitHub."
                onOpenProject(folder)
            },
        )
    }
}

@Composable
private fun ProjectRow(project: ProjectSummary, onClick: () -> Unit, onDelete: () -> Unit, onExport: () -> Unit) {
    Panel(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        // Panel adalah Box: TANPA Column di sini, ketiga baris di bawah saling menumpuk di titik
        // yang sama (bug nyata yang ditemukan uji perangkat v0.16 — nama projek jadi kacau dan
        // klik mendarat di tombol Ekspor).
        Column {
            // Baris asimetris ala DESIGN.md §10: identitas di kiri, status + metadata nyata di kanan.
            // Dulu semua metadata menumpuk di kiri sehingga kanan kartu kosong; kini kanan membawa
            // angka yang dipakai untuk memutuskan projek mana yang dibuka.
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        project.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = Kode.text,
                    )
                    Text(
                        project.applicationId,
                        style = MaterialTheme.typography.bodySmall,
                        color = Kode.textDim,
                    )
                }
                if (project.buildable) {
                    Pill("siap build", Kode.ok)
                } else {
                    Pill("perlu diperbaiki", Kode.warn)
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "${project.jumlahBerkas} berkas · ${ringkasByte(project.ukuranBytes)}",
                    style = MaterialTheme.typography.bodySmall.angkaTabular(),
                    color = Kode.textDim,
                )
                Text(
                    "Dibuka ${tanggal(project.lastOpened)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Kode.textDim,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostButton("Ekspor .zip", onExport, Modifier.testTag("ekspor-${project.name}"))
                GhostButton("Hapus projek", onDelete)
            }
        }
    }
}

/** Ringkasan ukuran untuk metadata baris: byte → KB/MB dengan satu desimal. */
private fun ringkasByte(byte: Long): String = when {
    byte >= 1_048_576 -> String.format(Locale("in", "ID"), "%.1f MB", byte / 1_048_576.0)
    byte >= 1_024 -> String.format(Locale("in", "ID"), "%.0f KB", byte / 1_024.0)
    else -> "$byte B"
}

private fun tanggal(millis: Long): String =
    SimpleDateFormat("d MMM yyyy, HH:mm", Locale("in", "ID")).format(Date(millis))
