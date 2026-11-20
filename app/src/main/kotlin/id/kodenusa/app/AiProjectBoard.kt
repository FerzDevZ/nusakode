package id.kodenusa.app

import id.kodenusa.core.ai.BerkasEdit
import id.kodenusa.core.ai.PapanTulisProjek
import id.kodenusa.core.project.ProjectFiles
import java.io.File

/**
 * Jembatan antara agen AI dan projek sungguhan.
 *
 * Dua aturan yang tidak boleh dilanggar:
 * - **Jalur dari model tidak dipercaya.** `ProjectFiles.safeRelative` menolak `../`, jalur absolut,
 *   dan `~`; penolakan itu diteruskan sebagai pesan galat, bukan dibersihkan diam-diam.
 * - **Ringkasan projek dibatasi ukuran.** Tanpa batas, projek 200 berkas akan mengirim seluruh isi
 *   projek ke model: mahal, lambat, dan bisa melewati jendela konteks.
 */
class AiProjectBoard(
    private val akar: File,
    private val maksKarakter: Int = 2400,
) : PapanTulisProjek {

    override fun konteks(): String = buildString {
        appendLine("AKAR PROJEK: ${akar.name}")
        val manifest = File(akar, "kodenusa.json")
        if (manifest.isFile) {
            appendLine()
            appendLine("kodenusa.json:")
            appendLine(manifest.readText().take(600))
        }
        appendLine()
        appendLine("DAFTAR BERKAS:")
        val berkas = akar.walkTopDown()
            .onEnter { it.name !in DILEWATI }
            .filter { it.isFile }
            .sortedBy { it.relativeTo(akar).invariantSeparatorsPath }
            .toList()
        berkas.take(MAKS_BARIS).forEach { satu ->
            val relatif = satu.relativeTo(akar).invariantSeparatorsPath
            appendLine("- $relatif (${satu.length()} B)")
        }
        if (berkas.size > MAKS_BARIS) appendLine("- … ${berkas.size - MAKS_BARIS} berkas lain")

        appendLine()
        appendLine("ISI BERKAS PENTING:")
        val penting = berkas
            .filter { it.extension.lowercase() in TEKS_PENTING }
            .filter { it.name != "kodenusa.json" }
            .sortedBy { it.length() }
        for (satu in penting) {
            if (length > maksKarakter) break
            val relatif = satu.relativeTo(akar).invariantSeparatorsPath
            val sisa = maksKarakter - length
            if (sisa < 80) break
            appendLine()
            appendLine("--- $relatif ---")
            appendLine(satu.readText().take(minOf(sisa, MAX_ISI_BERKAS)))
        }
        if (length > maksKarakter) deleteRange(maksKarakter, length)
    }

    override fun tulis(edit: BerkasEdit): String? {
        val relatif = edit.jalur.trim()
        if (relatif.isBlank()) return "Model tidak menuliskan nama berkas."
        if (relatif.contains('~')) return "Jalur berkas tidak boleh memakai '~' (folder rumah)."
        val aman = ProjectFiles.safeRelative(akar, relatif)
            ?: return "Jalur '$relatif' keluar dari folder projek, jadi ditolak."
        if (File(aman).extension.lowercase() in DILARANG) {
            return "Berkas '$aman' tidak boleh ditulis agen karena bukan berkas teks projek."
        }
        val target = File(akar, aman)
        return runCatching {
            target.parentFile?.mkdirs()
            target.writeText(edit.isi)
            null
        }.getOrElse { salah -> "Berkas '$aman' gagal ditulis: ${salah.message ?: "penyebab tidak diketahui"}" }
    }

    private companion object {
        val DILEWATI = setOf("build", ".git", ".gradle", ".idea", "dex", "gen", "classes", ".tools", "node_modules")
        val TEKS_PENTING = setOf("java", "xml", "html", "css", "js", "json", "properties")
        val DILARANG = setOf("apk", "jar", "dex", "so", "png", "jpg", "jpeg", "webp", "zip", "keystore", "bin")
        const val MAKS_BARIS = 40
        const val MAX_ISI_BERKAS = 700
    }
}
