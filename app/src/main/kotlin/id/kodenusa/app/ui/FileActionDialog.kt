package id.kodenusa.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import id.kodenusa.core.project.ProjectNode
import id.kodenusa.core.project.ProjectTree
import id.kodenusa.ui.KodenusaPalette
import id.kodenusa.ui.PrimaryButton

/** Aksi berkas yang menunggu jawaban pengguna di dialog. */
sealed interface FileAction {
    data object NewFile : FileAction
    data object NewFolder : FileAction
    data class Rename(val node: ProjectNode) : FileAction
    data class Delete(val node: ProjectNode) : FileAction
    data class Move(val node: ProjectNode) : FileAction
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
    onPindah: (node: ProjectNode, tujuan: String) -> String?,
    semuaNode: List<ProjectNode>,
) {
    var teks by remember(action) {
        mutableStateOf(if (action is FileAction.Rename) action.node.name else "")
    }
    var galat by remember(action) { mutableStateOf<String?>(null) }
    // Lokasi selalu mulai dari akar projek. Dulu berkas baru ikut folder berkas yang sedang
    // terbuka, sehingga hasilnya "hilang" jauh di dalam src/main/java/... tanpa penjelasan.
    var induk by remember(action) { mutableStateOf("") }

    val judul = when (action) {
        is FileAction.NewFile -> "Berkas baru"
        is FileAction.NewFolder -> "Folder baru"
        is FileAction.Rename -> "Ganti nama"
        is FileAction.Delete -> "Hapus ${action.node.name}?"
        is FileAction.Move -> "Pindahkan ${action.node.name}"
    }

    val konfirmasi = when (action) {
        is FileAction.NewFile -> "Buat"
        is FileAction.NewFolder -> "Buat"
        is FileAction.Rename -> "Simpan"
        is FileAction.Delete -> "Hapus"
        is FileAction.Move -> "Pindah"
    }

    fun jalankan() {
        val pesan = when (action) {
            is FileAction.NewFile -> onNewFile(induk, teks)
            is FileAction.NewFolder -> onNewFolder(induk, teks)
            is FileAction.Rename -> onRename(action.node, teks)
            is FileAction.Delete -> onDelete(action.node)
            is FileAction.Move -> null
        }
        if (pesan == null) onDismiss() else galat = pesan
    }

    if (action is FileAction.Move) {
        MoveDialog(action.node, semuaNode, onDismiss, onPindah)
        return
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
                    LokasiField(induk, semuaNode) { induk = it }
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

/** Pemilih folder induk: "akar projek" atau folder mana pun di dalam projek. */
@Composable
private fun LokasiField(
    induk: String,
    semuaNode: List<ProjectNode>,
    onInduk: (String) -> Unit,
) {
    var buka by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "Lokasi: ",
            style = MaterialTheme.typography.labelMedium,
            color = Color(KodenusaPalette.TEXT_DIM),
        )
        Box {
            TextButton(onClick = { buka = true }, modifier = Modifier.testTag("pilih-lokasi")) {
                Text(
                    induk.ifBlank { "akar projek" } + " ▾",
                    color = Color(KodenusaPalette.ACCENT),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            DropdownMenu(
                expanded = buka,
                onDismissRequest = { buka = false },
                containerColor = Color(KodenusaPalette.SURFACE_RAISED),
            ) {
                DropdownMenuItem(
                    text = { Text("akar projek", color = Color(KodenusaPalette.TEXT)) },
                    onClick = { buka = false; onInduk("") },
                )
                semuaNode.filter { it.directory }.forEach { folder ->
                    DropdownMenuItem(
                        text = { Text(folder.relativePath, color = Color(KodenusaPalette.TEXT)) },
                        onClick = { buka = false; onInduk(folder.relativePath) },
                    )
                }
            }
        }
    }
}

private fun petunjuk(action: FileAction): String = when (action) {
    is FileAction.NewFile -> "Contoh: LayarKedua.java, gaya.css, catatan.md"
    is FileAction.NewFolder -> "Folder dibuat di dalam folder yang sedang dipilih."
    is FileAction.Rename -> "Hanya nama yang berubah; isi berkas tidak disentuh."
    is FileAction.Delete -> ""
    is FileAction.Move -> "Pilih folder tujuan. Nama berkas tidak berubah."
}

/**
 * Pemilih folder tujuan pemindahan.
 *
 * Hanya folder yang ditampilkan, dan tujuan yang mustahil disaring lebih dulu: folder asal berkas itu
 * sendiri, serta (untuk folder) seluruh turunannya — memindahkan folder ke dalam dirinya sendiri
 * membuat isinya lenyap tanpa pesan apa pun.
 */
@Composable
private fun MoveDialog(
    node: ProjectNode,
    semuaNode: List<ProjectNode>,
    onDismiss: () -> Unit,
    onPindah: (ProjectNode, String) -> String?,
) {
    var galat by remember(node) { mutableStateOf<String?>(null) }
    val asal = node.relativePath.substringBeforeLast('/', "")
    val kandidat = daftarFolderTujuan(node, semuaNode)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(KodenusaPalette.SURFACE),
        title = { Text("Pindahkan ${node.name}", color = Color(KodenusaPalette.TEXT)) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    "Pilih folder tujuan di dalam projek ini.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(KodenusaPalette.TEXT_DIM),
                )
                kandidat.forEach { (relatif, label) ->
                    val sekarang = relatif == asal
                    TextButton(
                        onClick = {
                            if (sekarang) {
                                galat = "Berkas sudah ada di folder itu."
                            } else {
                                val pesan = onPindah(node, relatif)
                                if (pesan == null) onDismiss() else galat = pesan
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("tujuan-$label"),
                    ) {
                        Text(
                            if (sekarang) "• $label (sekarang)" else label,
                            color = Color(
                                if (sekarang) KodenusaPalette.TEXT_DIM else KodenusaPalette.TEXT,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                galat?.let {
                    Text(
                        it,
                        color = Color(KodenusaPalette.ERROR),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Batal", color = Color(KodenusaPalette.TEXT_DIM)) }
        },
    )
}

/** Folder yang sah menjadi tujuan: akar projek + semua folder, tanpa asal dan turunannya. */
private fun daftarFolderTujuan(
    node: ProjectNode,
    semuaNode: List<ProjectNode>,
): List<Pair<String, String>> {
    val asal = node.relativePath.substringBeforeLast('/', "")
    // Daftar folder diambil dari panel yang sedang terlihat, bukan dari disk lagi: kalau keduanya
    // berbeda sumber, dialog bisa menawarkan folder yang tidak ada di layar (atau sebaliknya).
    val hasil = mutableListOf("" to "akar projek")
    for (folder in semuaNode.filter { it.directory }) {
        val relatif = folder.relativePath
        val turunanSendiri = node.directory &&
            (relatif == node.relativePath || relatif.startsWith(node.relativePath + "/"))
        if (turunanSendiri || relatif == asal) continue
        hasil += relatif to relatif
    }
    return hasil
}

