package id.kodenusa.app

import android.content.Context
import id.kodenusa.build.tier0.Tier0BuildEngine
import id.kodenusa.build.tier0.Toolchain
import id.kodenusa.core.build.BuildEngine
import java.io.File

/**
 * Satu tempat yang tahu di mana projek, tool, dan template disimpan. Tidak ada dependency
 * injection framework: aplikasi ini harus tetap ringan di HP 2 GB.
 */
class AppContainer(context: Context) {

    val workspaceRoot: File = File(context.filesDir, WORKSPACE).apply { mkdirs() }

    val toolchainInstaller = ToolchainInstaller(context)

    val projectStore = ProjectStore(workspaceRoot)

    val templates = Templates(context)

    /** Setelan endpoint + kunci AI milik pengguna (kosong = AI belum aktif). */
    val aiStore = AiSettingsStore(context)

    /** Pilihan tema pengguna (bawaan, ikut sistem, atau .kntema kustom). */
    val temaStore = TemaStore(context)

    /** Cadangan sebelum AI menimpa berkas; dipakai layar AI dan editor (jalur sisip). */
    val undoTulis = UndoTulis(File(context.filesDir, "undo-tulis"))

    fun newEngine(toolchain: Toolchain): BuildEngine = Tier0BuildEngine(toolchain)

    private companion object {
        const val WORKSPACE = "projek"
    }
}
