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

    /** Mode belajar (docs/14 3.3): pelajaran per template + progres per projek. */
    val pelajaranStore = PelajaranStore.bawaan(context)

    val workspaceRoot: File = File(context.filesDir, WORKSPACE).apply { mkdirs() }

    val toolchainInstaller = ToolchainInstaller(context)

    val projectStore = ProjectStore(workspaceRoot)

    val templates = Templates(context)

    /** "Template saya": template buatan pengguna lewat AI (docs/14 4.3). */
    val templateSaya = TemplateSaya(java.io.File(context.filesDir, "template-saya").apply { mkdirs() })

    /** Setelan endpoint + kunci AI milik pengguna (kosong = AI belum aktif). */
    val aiStore = AiSettingsStore(context)

    /** Pilihan tema pengguna (bawaan, ikut sistem, atau .kntema kustom). */
    val temaStore = TemaStore(context)

    /** Cadangan sebelum AI menimpa berkas; dipakai layar AI dan editor (jalur sisip). */
    val undoTulis = UndoTulis(File(context.filesDir, "undo-tulis"))

    /** Riwayat tulisan AI per projek, bisa dibuka ulang dari layar AI. */
    val riwayatAgen = RiwayatAgen(File(context.filesDir, "riwayat-agen"))

    /** Riwayat build per projek (docs/13 C3): waktu, sukses, ukuran APK, durasi. */
    val riwayatBuild = RiwayatBuild(File(context.filesDir, "riwayat-build"))

    /** Counter versionCode otomatis per projek (docs/14 2.3). */
    val penghitungVersi = PenghitungVersi(File(context.filesDir, "version-code"))

    /** Setelan editor kecil: ukuran font (docs/14 2.6). */
    val setelanEditor = SetelanEditor(context)

    /** Pembuat ikon aplikasi hasil build (docs/14 2.2); hasilnya di cache, bukan folder projek. */
    val pembuatIkon = PembuatIkon(File(context.cacheDir, "ikon-build"))

    /** Kunci tanda tangan milik pengguna (docs/14 2.1, ADR-041). */
    val kunciPengguna = KunciPengguna(context)

    /** Cadangan otomatis harian (docs/09 P2 #10): zip cadangan-semua per hari, maks 2 salinan. */
    val cadanganOtomatis = CadanganOtomatis.bawaan(context, workspaceRoot)

    fun newEngine(toolchain: Toolchain): BuildEngine = Tier0BuildEngine(toolchain)

    private companion object {
        const val WORKSPACE = "projek"
    }
}
