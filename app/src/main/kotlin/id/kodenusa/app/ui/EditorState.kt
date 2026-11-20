package id.kodenusa.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import id.kodenusa.editor.CodeEditorView
import java.io.File

/**
 * Status kecil di sekitar editor yang tidak perlu disimpan ke disk: folder yang ditutup, posisi
 * kursor, dan status pencarian. Dipisah dari [id.kodenusa.app.EditorSession] supaya sesi dokumen
 * bisa diuji tanpa Android.
 */
class EditorState {
    var collapsed by mutableStateOf(emptySet<String>())
    var editor by mutableStateOf<CodeEditorView?>(null)
    var loadedPath by mutableStateOf<String?>(null)
    var line by mutableStateOf(1)
    var column by mutableStateOf(1)
    var query by mutableStateOf("")
    var matches by mutableStateOf(0)
    var matchIndex by mutableStateOf(0)
    var findOpen by mutableStateOf(false)
    /** false = cari di berkas aktif, true = cari di seluruh berkas projek. */
    var findProjek by mutableStateOf(false)
    var hasilProjek by mutableStateOf(emptyList<id.kodenusa.core.project.HasilCari>())
    var wrap by mutableStateOf(false)
    var toast by mutableStateOf<String?>(null)

    fun toggle(path: String) {
        collapsed = if (path in collapsed) collapsed - path else collapsed + path
    }

    fun load(file: File) {
        loadedPath = file.path
    }
}

@Composable
fun rememberEditorState(): EditorState = remember { EditorState() }
