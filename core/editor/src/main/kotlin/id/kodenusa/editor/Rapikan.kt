package id.kodenusa.editor

/**
 * "Rapikan" — merapikan indentasi seluruh berkas dengan aturan konservatif.
 *
 * Batasan yang disengaja (jujur soal apa yang dilakukan):
 * - HANYA spasi di awal baris yang ditulis ulang. Isi baris tidak disentuh, jadi kode tetap
 *   sah untuk dikompilasi meski heuristik kedalaman meleset.
 * - Kedalaman dihitung dari kurung `{ [ (` untuk bahasa kurung, dan dari pasangan tag
 *   `<x>` / `</x>` untuk XML. Ini bukan formatter penuh (tidak menyusun argumen, tidak
 *   memecah baris panjang) — cukup supaya berkas hasil AI atau impor zips yang amburadul
 *   jadi terbaca.
 * - Tab di awal baris diganti 4 spasi agar lebar konsisten dengan template bawaan.
 */
object Rapikan {

    private const val LEBAH = 4

    fun rapikan(isi: String, bahasa: Language): String {
        if (isi.isBlank()) return isi
        return when (bahasa) {
            Language.XML -> rapikanTag(isi)
            Language.JAVA, Language.KOTLIN, Language.JAVASCRIPT, Language.JSON ->
                rapikanKurung(isi)
            Language.PYTHON, Language.MARKDOWN, Language.PLAIN -> isi
        }
    }

    private fun rapikanKurung(isi: String): String {
        var kedalaman = 0
        return isi.lineSequence().joinToString("\n") { baris ->
            val polos = baris.trimStart()
            // Baris yang MEMBUKA kurung menjorok pada kedalaman sekarang; baris yang diawali
            // kurung penutup menjorok pada kedalaman SETELAH dikurangi (bug v0.16: memakai
            // kedalaman sebelum dikurangi membuat "}" menjorok terlalu dalam).
            if (polos.startsWith('}') || polos.startsWith(']') || polos.startsWith(')')) {
                kedalaman = (kedalaman - 1).coerceAtLeast(0)
            }
            val hasil = " ".repeat(kedalaman * LEBAH) + polos
            if (polos.endsWith('{') || polos.endsWith('[') || polos.endsWith('(')) {
                kedalaman++
            }
            hasil
        }
    }

    private fun rapikanTag(isi: String): String {
        var kedalaman = 0
        return isi.lineSequence().joinToString("\n") { baris ->
            val polos = baris.trimStart()
            // Hanya baris yang MULAI dan SELESAI satu elemen tag yang mengubah kedalaman. Baris
            // lanjutan tag multi-baris (mis. atribut di baris kedua) menjorok pada kedalaman
            // sekarang tanpa mengubah hitungan — lebih aman daripada menebak kapan tag selesai.
            val pembukaSelesai = polos.startsWith('<') && !polos.startsWith("</") &&
                !polos.startsWith("<?") && !polos.startsWith("<!--") && !polos.startsWith("<!") &&
                polos.endsWith('>') && !polos.endsWith("/>") && !polos.contains("</")
            val penutup = polos.startsWith("</")
            if (penutup) {
                kedalaman = (kedalaman - 1).coerceAtLeast(0)
            }
            val hasil = " ".repeat(kedalaman * LEBAH) + polos
            if (pembukaSelesai) {
                kedalaman++
            }
            hasil
        }
    }
}
