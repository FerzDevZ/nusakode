package id.kodenusa.editor

/**
 * Logika murni gutter (nomor baris) — dipisah dari penggambaran supaya bisa diuji di JVM
 * tanpa Android. Inilah perombakan akar bug lama: gutter lama menyimpan SEMUA nomor dalam
 * satu TextView raksasa lalu menggesernya dengan translationY. Beda tinggi baris sesecit
 * apa pun membuat nomor bergeser, dan bila layout editor belum dibangun saat `lineCount`
 * dibaca (tepat setelah setText), gutter terjebak "1" karena di-cache.
 *
 * Model baru: setiap kali gutter digambar, ia MENGHITUNG ulang dari keadaan layout editor
 * yang hidup — tidak ada cache, tidak ada teks raksasa, tidak ada translationY.
 */
object GutterModel {

    /** Satu nomor baris yang akan dilukis: posisi-y layar, teksnya, dan apakah disorot. */
    data class Item(val y: Float, val teks: String, val sorot: Boolean)

    /**
     * Hitung nomor-nomor baris yang terlihat.
     *
     * @param lineCount      jumlah baris VISUAL layout editor (baris yang di-wrap dihitung dua).
     * @param yAtas          fungsi y-atas layar untuk baris visual ke-i (sudah termasuk padding
     *                       dan sudah digeser scrollY).
     * @param awalBaris      offset teks awal baris visual ke-i.
     * @param teks           isi editor (untuk mendeteksi baris sambungan hasil wrap).
     * @param tinggiView     tinggi area gutter dalam piksel.
     * @param barisKursor    nomor baris LOGIKA kursor, untuk disorot; 0 = tidak disorot.
     */
    fun hitung(
        lineCount: Int,
        yAtas: (Int) -> Float,
        awalBaris: (Int) -> Int,
        teks: CharSequence,
        tinggiView: Float,
        barisKursor: Int,
    ): List<Item> {
        if (lineCount <= 0 || tinggiView <= 0f) return emptyList()

        // Baris visual pertama & terakhir yang menembus area terlihat.
        var pertama = 0
        while (pertama < lineCount - 1 && yAtas(pertama + 1) < 0f) pertama += 1
        var terakhir = pertama
        while (terakhir < lineCount - 1 && yAtas(terakhir + 1) <= tinggiView) terakhir += 1

        val hasil = ArrayList<Item>(terakhir - pertama + 1)
        // Nomor logika baris pertama yang terlihat: hitung '\n' sampai awal baris itu.
        var nomorLogika = hitungBarisLogika(teks, awalBaris(pertama))
        for (i in pertama..terakhir) {
            val sambungan = i > 0 && awalBaris(i) > 0 && teks[awalBaris(i) - 1] != '\n'
            if (!sambungan) {
                hasil += Item(yAtas(i), nomorLogika.toString(), nomorLogika == barisKursor)
                nomorLogika += 1
            }
        }
        return hasil
    }

    /** Nomor baris logika (1-based) dari offset teks: jumlah '\n' sebelum offset + 1. */
    fun hitungBarisLogika(teks: CharSequence, offset: Int): Int {
        var nomor = 1
        for (i in 0 until offset.coerceAtMost(teks.length)) {
            if (teks[i] == '\n') nomor += 1
        }
        return nomor
    }

    /** Lebar gutter (piksel) untuk [digit] angka monospace + ruang kanan. */
    fun lebar(charLebar: Float, digit: Int, paddingKiri: Float, paddingKanan: Float): Float =
        paddingKiri + paddingKanan + charLebar * maxOf(digit, 2)
}
