package id.kodenusa.core.ai

/**
 * Pemilih konteks OTOMATIS: dari pertanyaan pengguna, tentukan berkas mana yang paling
 * perlu dikirim ke model — tanpa pengguna memilih satu per satu.
 *
 * Mengapa leksikal, bukan embedding: KodeNusa berjalan offline di HP 2 GB tanpa server.
 * Skor token + bobot kepentingan jalur memberi hasil yang bisa diprediksi dan diuji,
 * dan cukup untuk projek kecil (10–60 berkas) yang menjadi target aplikasi ini.
 * Kejujuran desain: ini BM25-sederhana, bukan pencarian semantik — diungkapkan jujur
 * di UI sebagai "otomatis", bukan "pintar semantik".
 */
object PemilihKonteks {

    /** Kata yang terlalu umum untuk menaikkan skor apa pun (campuran Indonesia/Inggris). */
    private val BERHENTI = setOf(
        "yang", "di", "ke", "dari", "ini", "itu", "dan", "atau", "untuk", "dengan", "pada",
        "ada", "apa", "bagaimana", "gimana", "kenapa", "mengapa", "tolong", "buat", "buatkan",
        "ubah", "ganti", "tambah", "hapus", "cara", "kok", "tidak", "bisa", "saya", "aku",
        "the", "is", "how", "to", "add", "change", "fix", "make", "in", "on", "and", "or",
    )

    /** Pecah teks jadi token kecil: huruf/angka saja, buang kata henti dan token pendek. */
    fun token(teks: String): List<String> =
        teks.lowercase()
            .split(Regex("[^a-z0-9]+"))
            .filter { it.length >= 3 && it !in BERHENTI }

    data class Kandidat(val jalur: String, val isi: String, val skor: Int)

    /**
     * Skor relevansi [jalur]+[isi] terhadap [pertanyaan].
     * - Kecocokan token di jalur dinilai 3× (nama berkas yang cocok sinyal kuat).
     * - Kecocokan token di isi dinilai 1× per token unik (bukan per kemunculan — berkas
     *   besar tidak boleh menang hanya karena mengulang kata yang sama).
     * - Token camelCase/snake_case dipecah: "DatabaseKasir" cocok dengan "kasir".
     */
    fun skor(pertanyaan: String, jalur: String, isi: String): Int {
        val tanya = token(pertanyaan).toSet()
        if (tanya.isEmpty()) return 0
        var skor = 0
        val tokenJalur = token(jalur.replace('/', ' ')).toMutableSet()
        // Pecah camelCase & snake: DatabaseKasir -> database, kasir
        tokenJalur += pecahIdentitas(jalur)
        for (t in tanya) if (t in tokenJalur) skor += 3

        val tokenIsi = token(isi.take(8_000)).toSet() + pecahIdentitas(isi.take(8_000))
        for (t in tanya) if (t in tokenIsi) skor += 1
        return skor
    }

    /** Pecah identitas campuran (camelCase/snake_case) jadi token kecil tambahan. */
    internal fun pecahIdentitas(teks: String): Set<String> =
        Regex("[A-Z]?[a-z0-9]+|[A-Z]+(?![a-z])")
            .findAll(teks)
            .map { it.value.lowercase() }
            .filter { it.length >= 3 }
            .toSet()

    /**
     * Pilih berkas untuk dikirim. [wajib] selalu ikut (di depan), sisanya diisi dari
     * kandidat berskor tertinggi sampai [anggaranKarakter] habis. Deterministik: skor sama
     * diurutkan leksikal supaya hasil tidak berubah-ubah antar panggilan.
     */
    fun pilih(
        kandidat: List<Kandidat>,
        anggaranKarakter: Int,
        wajib: List<Kandidat> = emptyList(),
    ): List<Kandidat> {
        val hasil = ArrayList<Kandidat>()
        var terpakai = 0
        for (w in wajib.sortedBy { it.jalur }) {
            hasil += w
            terpakai += w.isi.length
        }
        for (k in kandidat.sortedWith(
            compareByDescending<Kandidat> { it.skor }.thenBy { it.jalur },
        )) {
            if (k in hasil) continue
            if (k.skor <= 0) continue
            if (terpakai + k.isi.length > anggaranKarakter) continue
            hasil += k
            terpakai += k.isi.length
        }
        return hasil
    }
}
