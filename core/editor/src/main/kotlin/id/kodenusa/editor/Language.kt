package id.kodenusa.editor

/**
 * Bahasa yang dikenali editor. Daftar ini sengaja kecil: setiap bahasa menambah tabel kata kunci
 * yang harus dimuat ke RAM, dan target kita HP 2 GB.
 *
 * [PLAIN] adalah jaring pengaman: berkas yang tidak dikenal tetap bisa dibuka dan diedit, hanya
 * tanpa warna. Tidak ada berkas yang ditolak editor.
 */
enum class Language(
    val id: String,
    val extensions: Set<String>,
) {
    JAVA("java", setOf("java")),
    KOTLIN("kotlin", setOf("kt", "kts")),
    XML("xml", setOf("xml")),
    JSON("json", setOf("json")),
    JAVASCRIPT("javascript", setOf("js", "jsx", "ts", "tsx")),
    PYTHON("python", setOf("py")),
    MARKDOWN("markdown", setOf("md")),
    PLAIN("plain", emptySet()),
    ;

    companion object {
        fun of(fileName: String): Language {
            val extension = fileName.substringAfterLast('.', "").lowercase()
            return entries.firstOrNull { extension in it.extensions } ?: PLAIN
        }
    }
}
