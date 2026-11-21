package id.kodenusa.app

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Satu catatan tulisan AI: tugas apa, berkas apa yang berubah, dan id batch undo-nya.
 * Disimpan supaya pengguna bisa membuka ulang hasil agen setelah layar ditutup — dulu
 * jejaknya hilang begitu layar AI ditutup (saran #6 di docs/12).
 */
@Serializable
data class CatatanAgen(
    val waktu: Long,
    val tugas: String,
    val berkas: List<String>,
    val batch: String? = null,
)

/**
 * Riwayat tulisan AI per projek, disimpan sebagai satu berkas JSON di `filesDir/riwayat-agen/`.
 * Offline, ikut terhapus saat aplikasi dihapus, dan dibatasi 20 catatan terbaru supaya
 * penyimpanan HP 2–4 GB tidak tumbuh tanpa batas.
 */
class RiwayatAgen(private val folder: File) {

    /** Mencatat satu tulisan AI. Catatan terbaru selalu di depan. */
    fun catat(projek: String, tugas: String, berkas: List<String>, batch: String?) {
        folder.mkdirs()
        val semua = muat(projek).toMutableList()
        semua.add(
            0,
            CatatanAgen(
                waktu = System.currentTimeMillis(),
                tugas = tugas.replace('\n', ' ').trim().take(160),
                berkas = berkas,
                batch = batch,
            ),
        )
        File(folder, namaBerkas(projek)).writeText(Json.encodeToString(semua.take(20)))
    }

    /** Memuat riwayat satu projek; projek yang belum pernah ditulis AI menghasilkan daftar kosong. */
    fun muat(projek: String): List<CatatanAgen> = runCatching {
        val berkas = File(folder, namaBerkas(projek))
        if (!berkas.isFile) emptyList() else Json.decodeFromString<List<CatatanAgen>>(berkas.readText())
    }.getOrDefault(emptyList())

    /**
     * Nama projek dipakai sebagai nama berkas — bersihkan karakter yang tidak aman untuk
     * sistem berkas supaya projek apa pun bisa dicatat tanpa galat.
     */
    private fun namaBerkas(projek: String): String =
        projek.filter { it.isLetterOrDigit() || it == '-' || it == '_' }.ifBlank { "projek" } + ".json"
}
