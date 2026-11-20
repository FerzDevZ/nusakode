package id.kodenusa.core.ai

/** Hasil satu kali build, setelah galatnya sudah diterjemahkan KodeNusa. */
data class HasilBuild(val sukses: Boolean, val galat: String = "")

/** Sumber jawaban model. Dipisah menjadi antarmuka supaya loop bisa diuji tanpa jaringan. */
interface GerbangModel {
    suspend fun balas(pesan: List<ChatMessage>): String
}

/**
 * Akses projek yang dipakai agen: membaca ringkasan projek dan menulis berkas.
 *
 * [tulis] mengembalikan pesan galat (atau `null` bila berhasil), sehingga loop bisa membedakan
 * "model salah menulis berkas" dari "build gagal" — dua masalah yang perbaikannya berbeda.
 */
interface PapanTulisProjek {
    fun konteks(): String
    fun tulis(edit: BerkasEdit): String?
}

/** Menjalankan build Tier 0 dan mengembalikan galat yang sudah ramah pengguna. */
interface GerbangBuild {
    suspend fun build(): HasilBuild
}

/** Satu putaran agen, dilaporkan ke UI supaya pengguna melihat prosesnya, bukan layar diam. */
data class LangkahAgen(
    val putaran: Int,
    val maksPutaran: Int,
    val jumlahBerkas: Int,
    val galat: String? = null,
    val catatan: String = "",
)

/** Ringkasan akhir: apa yang berubah dan kenapa berhenti. */
data class HasilAgen(
    val sukses: Boolean,
    val putaran: Int,
    val berkas: List<BerkasEdit>,
    val galat: String,
    val catatan: List<String>,
)

/**
 * Loop "tugas → ubah berkas → build → perbaiki" sampai APK jadi.
 *
 * Inti janji produk: pengguna hanya menulis keinginan dalam bahasa Indonesia, agen yang mengurus
 * berkas mana yang harus diubah. Batas [maksPutaran] dijaga keras; tanpa itu, model yang salah
 * terus bisa menghabiskan kuota pengguna dan membuat HP panas tanpa hasil.
 */
class AgenBuild(
    private val model: GerbangModel,
    private val papan: PapanTulisProjek,
    private val build: GerbangBuild,
    private val maksPutaran: Int = MAKS_PUTARAN_BAWAAN,
) {

    suspend fun jalankan(tugas: String, onLangkah: (LangkahAgen) -> Unit = {}): HasilAgen {
        val catatan = ArrayList<String>()
        val semua = ArrayList<BerkasEdit>()
        var galat = ""
        var putaran = 0
        while (putaran < maksPutaran) {
            putaran++
            val balasan = model.balas(PromptBuilder.untukAgen(tugas, papan.konteks(), galat))
            val edit = UbahanBerkas.ambil(balasan)
            if (edit.isEmpty()) {
                catatan += "Model tidak mengirim berkas. Ia hanya menjawab dengan penjelasan."
                onLangkah(LangkahAgen(putaran, maksPutaran, 0, galat.ifBlank { null }, catatan.last()))
                return HasilAgen(false, putaran, semua, galat, catatan)
            }
            semua += edit
            val ditolak = edit.mapNotNull { satu -> papan.tulis(satu)?.let { "${satu.jalur}: $it" } }
            if (ditolak.size == edit.size) {
                catatan += "Semua berkas ditolak saat ditulis."
                onLangkah(LangkahAgen(putaran, maksPutaran, edit.size, galat.ifBlank { null }, catatan.last()))
                return HasilAgen(false, putaran, semua, ditolak.joinToString("\n"), catatan)
            }
            val hasil = build.build()
            if (hasil.sukses) {
                catatan += "APK jadi pada putaran $putaran (${edit.size} berkas diubah)."
                onLangkah(LangkahAgen(putaran, maksPutaran, edit.size, null, catatan.last()))
                return HasilAgen(true, putaran, semua, "", catatan)
            }
            galat = hasil.galat
            catatan += "Putaran $putaran gagal build, galat dikirim balik ke model."
            onLangkah(LangkahAgen(putaran, maksPutaran, edit.size, galat, catatan.last()))
        }
        catatan += "Batas $maksPutaran putaran tercapai tanpa APK jadi."
        return HasilAgen(false, putaran, semua, galat.ifBlank { "Build tetap gagal setelah $maksPutaran putaran." }, catatan)
    }

    companion object {
        /** Cukup untuk satu perbaikan galat, tanpa membakar kuota pengguna. */
        const val MAKS_PUTARAN_BAWAAN = 3
    }
}
