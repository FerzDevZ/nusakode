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
import id.kodenusa.app.ProjectArchive
import id.kodenusa.app.ProjectSummary
import id.kodenusa.ui.EmptyState
import id.kodenusa.ui.GhostButton
import id.kodenusa.ui.KodenusaPalette
import id.kodenusa.ui.Panel
import id.kodenusa.ui.Pill
import id.kodenusa.ui.PrimaryButton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun WorkspaceScreen(
    container: AppContainer,
    onOpenProject: (java.io.File) -> Unit,
    onNewProject: () -> Unit,
) {
    var refresh by remember { mutableIntStateOf(0) }
    var deleting by remember { mutableStateOf<ProjectSummary?>(null) }
    var pesan by remember { mutableStateOf<String?>(null) }
    var ekspor by remember { mutableStateOf<ProjectSummary?>(null) }
    val projects = remember(refresh) { container.projectStore.list() }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val konteks = androidx.compose.ui.platform.LocalContext.current

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

    Scaffold(containerColor = Color(KodenusaPalette.BACKGROUND)) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(20.dp))
            Text("Projek saya", style = MaterialTheme.typography.headlineMedium, color = Color(KodenusaPalette.TEXT))
            Spacer(Modifier.height(4.dp))
            Text(
                "${projects.size} projek di perangkat ini. Semua tersimpan sebagai folder biasa.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(KodenusaPalette.TEXT_DIM),
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostButton("Impor .zip", { pemilihImpor.launch(arrayOf("application/zip", "application/octet-stream")) })
                GhostButton("Ekspor? pilih projek", { pesan = "Tekan \"Ekspor .zip\" pada salah satu projek di bawah." })
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Tidak perlu izin penyimpanan: setiap ambil/simpan berkas lewat pemilih bawaan Android, " +
                    "jadi yang bisa diakses hanya berkas yang Anda pilih sendiri.",
                style = MaterialTheme.typography.labelSmall,
                color = Color(KodenusaPalette.TEXT_DIM),
                modifier = Modifier.testTag("catatan-izin"),
            )
            pesan?.let { teks ->
                Spacer(Modifier.height(6.dp))
                Text(teks, style = MaterialTheme.typography.bodySmall, color = Color(KodenusaPalette.ACCENT))
            }
            Spacer(Modifier.height(16.dp))

            if (projects.isEmpty()) {
                Panel(Modifier.fillMaxWidth()) {
                    EmptyState(
                        title = "Belum ada projek",
                        body = "Mulai dari template kasir, katalog, atau aplikasi halo dunia. Tidak perlu koneksi internet.",
                        action = { PrimaryButton("Buat projek pertama", onNewProject) },
                    )
                }
            } else {
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
                ) { Text("Hapus", color = Color(KodenusaPalette.ERROR)) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text("Batal") }
            },
            containerColor = Color(KodenusaPalette.SURFACE),
        )
    }
}

@Composable
private fun ProjectRow(project: ProjectSummary, onClick: () -> Unit, onDelete: () -> Unit, onExport: () -> Unit) {
    Panel(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    project.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(KodenusaPalette.TEXT),
                    modifier = Modifier.weight(1f),
                )
                if (project.buildable) {
                    Pill("siap build", KodenusaPalette.OK)
                } else {
                    Pill("perlu diperbaiki", KodenusaPalette.WARN)
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                project.applicationId,
                style = MaterialTheme.typography.bodySmall,
                color = Color(KodenusaPalette.TEXT_DIM),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "Terakhir dibuka ${tanggal(project.lastOpened)}",
                style = MaterialTheme.typography.bodySmall,
                color = Color(KodenusaPalette.TEXT_DIM),
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostButton("Ekspor .zip", onExport, Modifier.testTag("ekspor-${project.name}"))
                GhostButton("Hapus projek", onDelete)
            }
        }
    }
}

private fun tanggal(millis: Long): String =
    SimpleDateFormat("d MMM yyyy, HH:mm", Locale("in", "ID")).format(Date(millis))
