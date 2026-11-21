package id.kodenusa.app

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Satu catatan hasil build: kapan, sukses/gagal, ukuran APK (byte) bila ada, dan durasi.
 * Disimpan supaya pengguna bisa melihat "berapa kali saya build, hasilnya berapa MB" —
 * angka yang sebelumnya hilang begitu layar build ditutup (docs/13 C3).
 */
@Serializable
data class CatatanBuild(
    val waktu: Long,
    val sukses: Boolean,
    val ukuranApk: Long? = null,
    val durasiMs: Long,
    /** versionCode yang dipakai build ini (docs/14 2.3); null = catatan lama sebelum fitur ada. */
    val versionCode: Int? = null,
)

/**
 * Riwayat build per projek, satu berkas JSON di `filesDir/riwayat-build/`. Dibatasi 10 catatan
 * terbaru (±2 KB per projek) supaya penyimpanan HP 2–4 GB tidak tumbuh tanpa batas.
 */
class RiwayatBuild(private val folder: File) {

    fun catat(projek: String, sukses: Boolean, ukuranApk: Long?, durasiMs: Long, versionCode: Int? = null) {
        folder.mkdirs()
        val semua = muat(projek).toMutableList()
        semua.add(
            0,
            CatatanBuild(
                waktu = System.currentTimeMillis(),
                sukses = sukses,
                ukuranApk = ukuranApk,
                durasiMs = durasiMs,
                versionCode = versionCode,
            ),
        )
        File(folder, namaBerkas(projek)).writeText(Json.encodeToString(semua.take(10)))
    }

    fun muat(projek: String): List<CatatanBuild> = runCatching {
        val berkas = File(folder, namaBerkas(projek))
        if (!berkas.isFile) emptyList() else Json.decodeFromString<List<CatatanBuild>>(berkas.readText())
    }.getOrDefault(emptyList())

    private fun namaBerkas(projek: String): String =
        projek.filter { it.isLetterOrDigit() || it == '-' || it == '_' }.ifBlank { "projek" } + ".json"
}
