package id.kodenusa.app

import java.io.File

/**
 * Counter versionCode per projek (docs/14 2.3).
 *
 * Masalah yang dipecahkan: versionCode di kodenusa.json beku di 1, jadi APK baru dengan isi baru
 * tetap "versi 1" dan membingungkan saat dibagikan berulang. Counter ini disimpan di penyimpanan
 * privat aplikasi (`filesDir/version-code/<projectId>`), bukan di kodenusa.json — manifest tetap
 * milik pengguna, angka build adalah urusan build.
 *
 * Aturan: build pertama memakai versionCode dari kodenusa.json; tiap build sukses menyimpan
 * angkanya; build berikutnya memakai angka tersimpan + 1, dan TIDAK PERNAH turun di bawah nilai
 * kodenusa.json (kalau pengguna menaikkan versi di manifest sendiri, itu dihormati).
 */
class PenghitungVersi(private val folder: File) {

    /** versionCode untuk build berikutnya. */
    fun berikutnya(projectId: String, dariManifest: Int): Int {
        val tersimpan = baca(projectId) ?: return dariManifest.coerceAtLeast(1)
        return maxOf(tersimpan + 1, dariManifest.coerceAtLeast(1))
    }

    /** Dicatat HANYA saat build sukses, supaya build gagal tidak membakar angka versi. */
    fun catat(projectId: String, code: Int) {
        folder.mkdirs()
        berkas(projectId).writeText(code.toString())
    }

    private fun baca(projectId: String): Int? = runCatching {
        berkas(projectId).readText().trim().toInt()
    }.getOrNull()

    private fun berkas(projectId: String): File =
        File(folder, projectId.filter { it.isLetterOrDigit() || it == '-' || it == '_' }.ifBlank { "projek" })
}
