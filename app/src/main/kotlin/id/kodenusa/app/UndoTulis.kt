package id.kodenusa.app

import java.io.File
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Cadangan otomatis sebelum AI menimpa berkas.
 *
 * Kenapa ada: satu jawaban model yang salah bisa menghapus kerja pengguna selamanya — di HP
 * tanpa PC, tidak ada "git revert" yang mudah. Sebelum berkas yang sudah ada ditimpa, isinya
 * disalin ke folder privat aplikasi; pengguna bisa menekan "Batalkan tulisan AI" untuk
 * mengembalikannya. Hanya beberapa batch terakhir yang dipertahankan supaya penyimpanan tidak
 * menumpuk di HP 2 GB.
 *
 * Batch = satu aksi tulis (satu tekan "Simpan berkas ke projek" atau satu sisip). Berkas baru
 * (yang belum ada sebelumnya) dicatat lewat penanda kosong: membatalkan berarti menghapusnya.
 */
class UndoTulis(private val folder: File) {

    /** Menyiapkan batch cadangan baru dan mengembalikan id-nya. */
    fun siapkanBatch(): String {
        folder.mkdirs()
        // ID unik bahkan saat beberapa batch dibuat dalam milidetik yang sama.
        val id = "$currentTimeMillis-$urutan"
        File(folder, id).mkdirs()
        bersihkan()
        return id
    }

    /** Menyalin isi berkas lama ke batch. Mengembalikan false bila berkas tidak ada / gagal. */
    fun simpanLama(batch: String, jalurRelatif: String, berkasLama: File): Boolean = runCatching {
        if (!berkasLama.isFile) return false
        File(File(folder, batch), encode(jalurRelatif, AKHIRAN_LAMA)).let { tujuan ->
            tujuan.parentFile?.mkdirs()
            berkasLama.copyTo(tujuan, overwrite = true)
        }
        true
    }.getOrDefault(false)

    /** Menandai berkas yang BARU dibuat AI: membatalkan berarti menghapusnya kembali. */
    fun tandaiBaru(batch: String, jalurRelatif: String): Boolean = runCatching {
        File(File(folder, batch), encode(jalurRelatif, AKHIRAN_BARU)).writeText("")
        true
    }.getOrDefault(false)

    /**
     * Mengembalikan semua berkas dalam batch: berkas lama dikembalikan ke isinya, berkas baru
     * dihapus. Mengembalikan daftar jalur relatif yang berubah.
     */
    fun batalkan(batch: String, akar: File): List<String> {
        val dir = File(folder, batch)
        if (!dir.isDirectory) return emptyList()
        val berubah = ArrayList<String>()
        dir.listFiles()?.forEach { cadangan ->
            val jalur = decode(cadangan.name) ?: return@forEach
            val target = File(akar, jalur)
            runCatching {
                if (cadangan.name.endsWith(AKHIRAN_BARU)) {
                    target.delete()
                } else {
                    target.parentFile?.mkdirs()
                    cadangan.copyTo(target, overwrite = true)
                }
                berubah += jalur
            }
        }
        dir.deleteRecursively()
        return berubah
    }

    /** Batch terakhir yang masih tersimpan, atau null bila tidak ada. */
    fun batchTerakhir(): String? =
        folder.listFiles()?.filter { it.isDirectory }?.maxByOrNull { it.name }?.name

    /** Tanda khusus untuk berkas baru; kosong pun tetap dibedakan dari cadangan berkas lama. */
    private fun encode(jalur: String, akhiran: String): String =
        URLEncoder.encode(jalur, "UTF-8") + akhiran

    private fun decode(nama: String): String? = runCatching {
        val akhiran = when {
            nama.endsWith(AKHIRAN_LAMA) -> AKHIRAN_LAMA
            nama.endsWith(AKHIRAN_BARU) -> AKHIRAN_BARU
            else -> return null
        }
        URLDecoder.decode(nama.removeSuffix(akhiran), "UTF-8")
    }.getOrNull()

    private fun bersihkan(maks: Int = 5) {
        val semua = folder.listFiles()?.filter { it.isDirectory }?.sortedBy { it.name } ?: return
        if (semua.size <= maks) return
        semua.take(semua.size - maks).forEach { it.deleteRecursively() }
    }

    private var urutan = 0L

    private val currentTimeMillis: Long
        get() = System.currentTimeMillis().also { urutan = (urutan + 1) % 1000 }

    private companion object {
        const val AKHIRAN_LAMA = ".knl"
        const val AKHIRAN_BARU = ".knb"
    }
}
