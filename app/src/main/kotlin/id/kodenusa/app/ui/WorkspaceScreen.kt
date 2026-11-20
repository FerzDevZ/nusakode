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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import id.kodenusa.app.AppContainer
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
    val projects = remember(refresh) { container.projectStore.list() }

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
private fun ProjectRow(project: ProjectSummary, onClick: () -> Unit, onDelete: () -> Unit) {
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
            GhostButton("Hapus projek", onDelete)
        }
    }
}

private fun tanggal(millis: Long): String =
    SimpleDateFormat("d MMM yyyy, HH:mm", Locale("in", "ID")).format(Date(millis))
