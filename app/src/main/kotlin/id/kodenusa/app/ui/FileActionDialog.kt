package id.kodenusa.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import id.kodenusa.core.project.ProjectNode
import id.kodenusa.ui.KodenusaPalette
import id.kodenusa.ui.PrimaryButton

/** Aksi berkas yang menunggu jawaban pengguna di dialog. */
sealed interface FileAction {
    data class NewFile(val parent: String) : FileAction
    data class NewFolder(val parent: String) : FileAction
    data class Rename(val node: ProjectNode) : FileAction
    data class Delete(val node: ProjectNode) : FileAction
}

/**
 * Satu dialog untuk semua aksi berkas: tambah berkas, tambah folder, ganti nama, hapus.
 *
 * Semua operasi mengembalikan pesan galat (atau `null` bila berhasil), lalu galat itu ditampilkan
 * di dalam dialog yang sama. Ini disengaja: pengguna tidak boleh kehilangan apa yang sudah
 * diketiknya karena nama ditolak.
 */
@Composable
fun FileActionDialog(
    action: FileAction,
    onDismiss: () -> Unit,
    onNewFile: (parent: String, name: String) -> String?,
    onNewFolder: (parent: String, name: String) -> String?,
    onRename: (node: ProjectNode, name: String) -> String?,
    onDelete: (node: ProjectNode) -> String?,
) {
    var teks by remember(action) {
        mutableStateOf(if (action is FileAction.Rename) action.node.name else "")
    }
    var galat by remember(action) { mutableStateOf<String?>(null) }

    val judul = when (action) {
        is FileAction.NewFile -> "Berkas baru"
        is FileAction.NewFolder -> "Folder baru"
        is FileAction.Rename -> "Ganti nama"
        is FileAction.Delete -> "Hapus ${action.node.name}?"
    }

    val konfirmasi = when (action) {
        is FileAction.NewFile -> "Buat"
        is FileAction.NewFolder -> "Buat"
        is FileAction.Rename -> "Simpan"
        is FileAction.Delete -> "Hapus"
    }

    fun jalankan() {
        val pesan = when (action) {
            is FileAction.NewFile -> onNewFile(action.parent, teks)
            is FileAction.NewFolder -> onNewFolder(action.parent, teks)
            is FileAction.Rename -> onRename(action.node, teks)
            is FileAction.Delete -> onDelete(action.node)
        }
        if (pesan == null) onDismiss() else galat = pesan
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(KodenusaPalette.SURFACE),
        title = { Text(judul, color = Color(KodenusaPalette.TEXT)) },
        text = {
            Column {
                if (action is FileAction.Delete) {
                    Text(
                        "Berkas atau folder beserta isinya dihapus dari perangkat. Tindakan ini tidak bisa dibatalkan.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(KodenusaPalette.TEXT_DIM),
                    )
                } else {
                    Text(
                        petunjuk(action),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(KodenusaPalette.TEXT_DIM),
                    )
                    TextField(
                        value = teks,
                        onValueChange = { teks = it; galat = null },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag("nama-berkas"),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color(KodenusaPalette.SURFACE_RAISED),
                            unfocusedContainerColor = Color(KodenusaPalette.SURFACE_RAISED),
                            focusedTextColor = Color(KodenusaPalette.TEXT),
                            unfocusedTextColor = Color(KodenusaPalette.TEXT),
                        ),
                    )
                }
                galat?.let { pesan ->
                    Text(
                        pesan,
                        color = Color(KodenusaPalette.ERROR),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onDismiss) { Text("Batal", color = Color(KodenusaPalette.TEXT_DIM)) }
                PrimaryButton(konfirmasi, ::jalankan, modifier = Modifier.testTag("aksi-berkas"))
            }
        },
    )
}

private fun petunjuk(action: FileAction): String = when (action) {
    is FileAction.NewFile -> "Contoh: LayarKedua.java, gaya.css, catatan.md"
    is FileAction.NewFolder -> "Folder dibuat di dalam folder yang sedang dipilih."
    is FileAction.Rename -> "Hanya nama yang berubah; isi berkas tidak disentuh."
    is FileAction.Delete -> ""
}
