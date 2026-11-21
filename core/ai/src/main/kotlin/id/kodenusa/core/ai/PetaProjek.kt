package id.kodenusa.core.ai

import java.io.File

/**
 * Pemetaan cerdas seluruh projek — pengganti ringkasan 2.400 karakter "berkas terkecil dulu".
 *
 * Tujuan (keluhan pengguna): "saya buka AI, ia harus pelajari seluruh source code, paham" —
 * tanpa memilih berkas satu per satu. Peta ini memberi model:
 *  1. Identitas projek (kodenusa.json + manifest).
 *  2. Daftar berkas berperan (manifest, kode, layout, resource, aset) — bukan daftar datar.
 *  3. KERANGKA tiap berkas kode: nama kelas, signature metode, layout/resource yang
 *     dirujuk — diekstrak dengan aturan murah (regex), tanpa parser penuh, tetap cepat
 *     di HP 2 GB dan tanpa internet.
 *  4. Kutipan isi untuk berkas paling penting saja, diisi sampai anggaran karakter habis.
 *
 * Semua angka batas ditulis eksplisit: tanpa batas, projek 200 berkas mengirim seluruh isi
 * ke model — mahal, lambat, dan melewati jendela konteks.
 */
object PetaProjek {

    /** Folder yang tidak pernah dikirim ke model (hasil build, metadata, dependensi). */
    val DILEWATI_PROJEK = setOf(
        "build", ".git", ".gradle", ".idea", "dex", "gen", "classes", ".tools",
        "node_modules", "keystore",
    )

    data class Entri(val jalur: String, val peran: String, val byte: Long, val kerangka: List<String>)

    /**
     * Susun peta projek dalam batas [anggaranKarakter]. Urutan sengaja stabil (identitas →
     * daftar → kerangka → kutipan) supaya awal prompt tidak berubah-ubah antar pertanyaan —
     * itu yang membuat kuota boros (prompt caching menyukai awalan stabil).
     */
    fun petakan(akar: File, anggaran: Int = ANGGARAN_BAWAAN): String {
        val entri = daftar(akar)
        return susun(akar, entri, anggaran)
    }

    /** Daftar berkas projek beserta peran dan kerangkanya, terurut jalur. */
    fun daftar(akar: File): List<Entri> =
        akar.walkTopDown()
            .onEnter { it.name !in DILEWATI_PROJEK }
            .filter { it.isFile }
            .map { satu ->
                val jalur = satu.relativeTo(akar).invariantSeparatorsPath
                Entri(jalur, peran(jalur), satu.length(), kerangka(jalur, bacaAman(satu, 24_000)))
            }
            .sortedBy { it.jalur }
            .toList()

    /** Peran berkas dari jalurnya — dipakai untuk bobot kepentingan dan label peta. */
    fun peran(jalur: String): String = when {
        jalur == "kodenusa.json" -> "manifest-projek"
        jalur.endsWith("AndroidManifest.xml") -> "manifest-android"
        jalur.contains("res/layout") -> "layout"
        jalur.contains("res/values") -> "resource-teks"
        jalur.contains("res/") -> "resource"
        jalur.contains("assets/") || jalur.startsWith("web/") -> "aset-web"
        jalur.endsWith(".java") -> "kode-java"
        jalur.endsWith(".js") || jalur.endsWith(".html") || jalur.endsWith(".css") -> "kode-web"
        jalur.endsWith(".json") -> "konfigurasi"
        else -> "lain"
    }

    /**
     * Kerangka berkas: baris-baris yang menjelaskan STRUKTUR, bukan isi penuh.
     * Untuk Java: deklarasi kelas/metode + rujukan resource. Untuk layout XML: id & view.
     * Untuk strings.xml: nama teks. Maksimal [maksBaris] baris per berkas.
     */
    fun kerangka(jalur: String, isi: String, maksBaris: Int = 12): List<String> {
        if (isi.isBlank()) return emptyList()
        val hasil = ArrayList<String>(maksBaris)
        val pola = when {
            jalur.endsWith(".java") -> POLA_JAVA
            jalur.contains("res/layout") -> POLA_LAYOUT
            jalur.contains("res/values") -> POLA_VALUES
            else -> return emptyList()
        }
        for (barisMentah in isi.lines()) {
            if (hasil.size >= maksBaris) break
            val baris = barisMentah.trim()
            if (baris.isEmpty()) continue
            val cocok = pola.find(baris) ?: continue
            hasil += cocok.value.trim()
        }
        return hasil
    }

    private val POLA_JAVA = Regex(
        "(public|private|protected)?\\s*(final\\s+)?(class|interface|enum)\\s+\\w+|" +
            "(public|private|protected)\\s+[\\w<>\\[\\],\\s]+\\s+\\w+\\s*\\(|" +
            "R\\.(layout|string|id|drawable|color)\\.\\w+|setContentView\\(\\w+\\)",
    )
    private val POLA_LAYOUT = Regex("android:id=\"@\\+id/\\w+\"|<[a-zA-Z][\\w.]*[ >]")
    private val POLA_VALUES = Regex("<string name=\"\\w+\"|<color name=\"\\w+\"")

    private fun bacaAman(berkas: File, maks: Int): String =
        runCatching { berkas.readText().take(maks) }.getOrDefault("")

    private fun susun(akar: File, entri: List<Entri>, anggaran: Int): String = buildString {
        appendLine("PETA PROJEK (disusun otomatis dari seluruh berkas):")
        appendLine("AKAR: ${akar.name}")

        val manifest = entri.filter { it.peran.startsWith("manifest") }
        for (m in manifest) {
            appendLine()
            appendLine("--- ${m.jalur} ---")
            appendLine(bacaAman(File(akar, m.jalur), 800))
        }

        appendLine()
        appendLine("DAFTAR BERKAS (${entri.size} berkas):")
        for (e in entri) {
            appendLine("- [${e.peran}] ${e.jalur} (${e.byte} B)")
            if (length > anggaran) break
        }

        appendLine()
        appendLine("KERANGKA KODE:")
        val berkerangka = entri.filter { it.kerangka.isNotEmpty() }
        for (e in berkerangka) {
            if (length > anggaran * 3 / 4) break
            appendLine("${e.jalur}:")
            for (k in e.kerangka) appendLine("  $k")
        }

        // Kutipan isi: berkas terpenting dulu (bukan terkecil dulu seperti versi lama).
        appendLine()
        appendLine("ISI BERKAS TERPENTING:")
        for (e in entri.kunci(anggaran - length)) {
            if (length > anggaran) break
            appendLine()
            appendLine("--- ${e.jalur} ---")
            appendLine(bacaAman(File(akar, e.jalur), 4_000).take(maxOf(0, anggaran - length)))
        }
        if (length > anggaran) deleteRange(anggaran, length)
    }

    /** Urutan kepentingan: manifest > kode utama > layout > resource > lainnya. */
    internal fun bobot(peran: String, jalur: String): Int = when {
        peran == "manifest-projek" -> 100
        peran == "manifest-android" -> 90
        jalur.endsWith("MainActivity.java") -> 80
        peran == "kode-java" -> 60
        peran == "layout" -> 50
        peran == "kode-web" -> 50
        peran == "resource-teks" -> 40
        peran == "konfigurasi" -> 30
        else -> 10
    }

    internal fun List<Entri>.kunci(sisaAnggaran: Int): List<Entri> {
        if (sisaAnggaran <= 200) return emptyList()
        return sortedWith(
            compareByDescending<Entri> { bobot(it.peran, it.jalur) }.thenBy { it.byte },
        ).take(6)
    }

    const val ANGGARAN_BAWAAN = 12_000
}
