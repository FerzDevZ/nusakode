package id.kodenusa.core.ai

/**
 * Satu perubahan berkas yang diminta model: jalur relatif terhadap akar projek, lalu isi lengkapnya.
 *
 * Isi selalu **lengkap**, bukan potongan `diff`. Alasannya sederhana: HP kelas bawah sering
 * kehilangan sambungan di tengah jalan, dan menambal potongan yang setengah diterima jauh lebih
 * berbahaya daripada menulis ulang berkas utuh.
 */
data class BerkasEdit(val jalur: String, val isi: String)

/**
 * Pembaca balasan model menjadi daftar perubahan berkas.
 *
 * Model diminta menulis penanda `<<<BERKAS: jalur>>>` sebelum setiap blok berpagar, sehingga
 * pemisahan berkas tidak bergantung pada tebakan nama dari kalimat penjelasan.
 */
object UbahanBerkas {

    /** Batas keras supaya satu balasan tidak bisa menulis ratusan berkas di HP 2 GB. */
    const val MAKS_BERKAS = 12

    private val POLA_PENANDA = Regex("<<<\\s*BERKAS\\s*:\\s*([^>\\n]+?)\\s*>>>", RegexOption.IGNORE_CASE)
    private val POLA_PAGAR = Regex("```[^\\n]*\\n([\\s\\S]*?)```")

    /**
     * Mengambil semua perubahan dari [balasan]. Hasil kosong berarti model tidak mengirim berkas,
     * dan pemanggil wajib memperlakukannya sebagai kegagalan yang jelas — bukan sebagai "sukses
     * tanpa perubahan", yang akan membuat pengguna menunggu build yang tidak mengubah apa pun.
     */
    fun ambil(balasan: String): List<BerkasEdit> {
        val penanda = POLA_PENANDA.findAll(balasan).toList()
        if (penanda.isEmpty()) return emptyList()
        val hasil = ArrayList<BerkasEdit>()
        penanda.forEachIndexed { urutan, cocok ->
            if (hasil.size >= MAKS_BERKAS) return hasil
            val jalur = rapikanJalur(cocok.groupValues[1]) ?: return@forEachIndexed
            val mulai = cocok.range.last + 1
            val akhir = penanda.getOrNull(urutan + 1)?.range?.first ?: balasan.length
            val potongan = balasan.substring(mulai, akhir)
            val isi = POLA_PAGAR.find(potongan)?.groupValues?.get(1) ?: return@forEachIndexed
            hasil += BerkasEdit(jalur, isi.trimEnd('\n') + "\n")
        }
        return hasil
    }

    /**
     * Jalur harus relatif dan tetap di dalam projek. Balasan model adalah masukan yang tidak
     * dipercaya: `../../shared_prefs/…` atau `/data/data/…` akan menulis di luar projek, jadi
     * jalur seperti itu dibuang, bukan "dirapikan" diam-diam.
     */
    fun rapikanJalur(mentah: String): String? {
        val bersih = mentah.trim().trim('`', '"', '\'').replace('\\', '/')
        if (bersih.startsWith("/")) return null
        if (bersih.startsWith("~")) return null
        val jalur = bersih.trimStart('/')
        if (jalur.isBlank()) return null
        if (jalur.contains("..")) return null
        if (jalur.startsWith(".")) return null
        if (jalur.length > 200) return null
        if (!jalur.matches(Regex("[A-Za-z0-9_./()@+ -]+"))) return null
        return jalur
    }
}
