package id.kodenusa.editor

/**
 * Lengkapi kata untuk editor (docs/14 2.5).
 *
 * Sengaja TANPA index projek, TANPA LSP, TANPA pohon sintaks: satu [HashSet] dari isi berkas
 * aktif + kata kunci bahasa. Alasannya bukan kemalasan — HP 2 GB tidak boleh membayar indeks
 * yang tumbuh setiap ketikan. Murni fungsi murni: teks masuk, daftar kandidat keluar, sehingga
 * seluruh perilakunya bisa diuji di JVM tanpa Android.
 */
object PenyediaKandidat {

    /** Batas kandidat yang ditampilkan; panel di atas keyboard tidak boleh makan layar. */
    const val MAKS = 6

    /** Kandidat baru muncul setelah pengguna mengetik minimal huruf sebanyak ini. */
    const val MIN_HURUF = 2

    private val KATA = Regex("[A-Za-z_][A-Za-z0-9_]*")

    /**
     * Kandidat lengkapi untuk [kataDiketik] di dalam [teks].
     *
     * Urutan deterministik: kata yang sudah ada di berkas pengguna lebih dulu (urutan kemunculan),
     * lalu kata kunci bahasa. Kata yang identik dengan yang diketik tidak diulang; duplikat
     * dibuang; hasil dipotong di [MAKS].
     */
    fun kandidat(teks: String, kataDiketik: String, bahasa: Language): List<String> {
        val awalan = kataDiketik.trim()
        if (awalan.length < MIN_HURUF) return emptyList()
        if (!awalan.all { it.isLetterOrDigit() || it == '_' }) return emptyList()

        val sudah = HashSet<String>()
        val hasil = ArrayList<String>()

        fun tawarkan(kata: String) {
            if (kata.length <= awalan.length) return
            if (!kata.startsWith(awalan, ignoreCase = true)) return
            val kunci = kata.lowercase()
            if (!sudah.add(kunci)) return
            hasil.add(kata)
        }

        KATA.findAll(teks).forEach { tawarkan(it.value) }
        Highlighter.keywordsOf(bahasa).forEach { tawarkan(it) }
        return hasil.take(MAKS)
    }

    /**
     * Sisa kata yang harus disisipkan bila pengguna memilih [kandidat] setelah mengetik
     * [kataDiketik]. Pencocokan peka huruf besar-kecil dulu; kalau hanya beda kapital, sisipkan
     * bagian belakangnya saja supaya tulisan pengguna tidak dikoreksi paksa.
     */
    fun sisa(kandidat: String, kataDiketik: String): String =
        if (kandidat.startsWith(kataDiketik)) kandidat.substring(kataDiketik.length) else ""
}
