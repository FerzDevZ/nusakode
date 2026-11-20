package id.kodenusa.core.diagnose

import id.kodenusa.core.build.BuildTool
import id.kodenusa.core.build.Diagnostic
import id.kodenusa.core.build.Severity

/**
 * Mengubah output mentah ECJ/d8/aapt2/zipalign/apksigner menjadi [Diagnostic] yang bisa diklik.
 *
 * ECJ mencetak empat baris per masalah (header, kode sumber, penanda `^^^`, pesan), jadi parser ini
 * stateful: satu instance untuk satu proses build, jangan dipakai ulang lintas tool.
 */
class DiagnosticParser {

    private class Pending(
        val severity: Severity,
        val tool: BuildTool,
        val file: String?,
        val line: Int?,
        var column: Int?,
        var awaitingMessage: Boolean,
    )

    private var pending: Pending? = null

    private val ecjHeader = Regex("""^\d+\.\s+(ERROR|WARNING)\s+in\s+(.+?)(?:\s+\(at line (\d+)\))?$""")
    private val fileLineMessage =
        Regex("""^(.+?):(\d+):\s*(error|warning|warn):\s*(.+)$""", RegexOption.IGNORE_CASE)
    private val marker = Regex("""^\s*\^+\s*$""")
    private val separator = Regex("""^-{3,}$""")
    private val summary = Regex("""^\d+\s+problems?\s""")

    /** Memberi satu baris output; mengembalikan [Diagnostic] bila baris itu menutup sebuah masalah. */
    fun feed(tool: BuildTool, line: String): Diagnostic? {
        val trimmed = line.trimEnd()
        if (trimmed.isBlank()) return null

        pending?.let { current ->
            when {
                marker.matches(trimmed) -> {
                    current.column = trimmed.indexOf('^').takeIf { it >= 0 }?.plus(1)
                    current.awaitingMessage = true
                    return null
                }
                separator.matches(trimmed) || summary.containsMatchIn(trimmed) -> {
                    pending = null
                    return null
                }
                current.awaitingMessage -> {
                    pending = null
                    return build(current, trimmed)
                }
            }
        }

        if (separator.matches(trimmed) || summary.containsMatchIn(trimmed)) return null

        ecjHeader.find(trimmed)?.let { match ->
            pending = Pending(
                severity = if (match.groupValues[1] == "ERROR") Severity.ERROR else Severity.WARNING,
                tool = tool,
                file = match.groupValues[2].ifBlank { null },
                line = match.groupValues[3].toIntOrNull(),
                column = null,
                awaitingMessage = false,
            )
            return null
        }

        fileLineMessage.find(trimmed)?.let { match ->
            val severity = if (match.groupValues[3].equals("error", true)) Severity.ERROR else Severity.WARNING
            val message = match.groupValues[4]
            return Diagnostic(
                severity = severity,
                tool = tool,
                messageId = classify(tool, match.groupValues[1], message),
                rawMessage = message,
                file = match.groupValues[1],
                line = match.groupValues[2].toIntOrNull(),
            )
        }

        val lower = trimmed.lowercase()
        if (tool == BuildTool.APKSIGNER && SIGNAL_WORDS.any { lower.contains(it) }) {
            return Diagnostic(Severity.ERROR, tool, "sign.failed", trimmed)
        }
        if (tool == BuildTool.ZIPALIGN && SIGNAL_WORDS.any { lower.contains(it) }) {
            return Diagnostic(Severity.ERROR, tool, "align.failed", trimmed)
        }

        toolLevel(trimmed)?.let { return it }
        return null
    }

    /** Dipanggil di akhir proses agar masalah ECJ yang pesannya sudah terbaca tidak hilang. */
    fun flush(): Diagnostic? {
        val current = pending ?: return null
        pending = null
        return if (current.awaitingMessage) build(current, "") else null
    }

    fun parseAll(tool: BuildTool, lines: List<String>): List<Diagnostic> {
        val out = mutableListOf<Diagnostic>()
        for (line in lines) feed(tool, line)?.let(out::add)
        flush()?.let(out::add)
        return out
    }

    private fun build(pending: Pending, message: String): Diagnostic = Diagnostic(
        severity = pending.severity,
        tool = pending.tool,
        messageId = classify(pending.tool, pending.file.orEmpty(), message),
        rawMessage = message,
        file = pending.file,
        line = pending.line,
        column = pending.column,
    )

    private fun toolLevel(line: String): Diagnostic? {
        val lower = line.lowercase()
        val severity = when {
            lower.startsWith("error") || lower.contains(" error:") -> Severity.ERROR
            lower.startsWith("warning") || lower.contains(" warning:") -> Severity.WARNING
            else -> null
        } ?: return null
        return Diagnostic(
            severity = severity,
            tool = BuildTool.INTERNAL,
            messageId = classify(BuildTool.INTERNAL, null, line),
            rawMessage = line,
        )
    }

    companion object {
        private val SIGNAL_WORDS = listOf("does not verify", "failed", "error", "exception", "invalid", "unable")

        /*
         * Urutan penting. Contoh: "unexpected element" mengandung kata "expected", dan
         * "The import ... cannot be resolved" mengandung "cannot be resolved". Aturan yang lebih
         * spesifik wajib lebih dulu, kalau tidak user mendapat penjelasan yang salah.
         */
        private val RULES: List<Pair<Regex, String>> = listOf(
            Regex(
                """unexpected element|failed processing manifest|has no default constructor|no <manifest>""",
            ) to "res.manifest_invalid",
            Regex(
                """the import .* cannot be resolved|package .* does not exist|cannot be resolved to a type""",
            ) to "sym.package_missing",
            Regex(
                """cannot find symbol|cannot be resolved|is not defined|undefined reference""",
            ) to "sym.not_found",
            Regex(
                """type mismatch|incompatible types|inconvertible types|cannot convert from""",
            ) to "type.incompatible",
            Regex(
                """not abstract and does not override|must implement the inherited|must be implemented""",
            ) to "type.abstract_missing",
            Regex(
                """missing return statement|return statement is missing""",
            ) to "type.return_missing",
            Regex(
                """attribute .* not found|no resource identifier found for attribute""",
            ) to "res.attr_missing",
            Regex(
                """duplicate resource|already defined|duplicate value""",
            ) to "res.duplicate",
            Regex(
                """failed to compile|is not a valid|aapt2""",
            ) to "res.compile_failed",
            Regex(
                """resource .* not found|no resource found|resource linking failed|resource entry""",
            ) to "res.not_found",
            Regex(
                """unclosed|unterminated""",
            ) to "syntax.unclosed",
            Regex(
                """illegal start of|illegal character""",
            ) to "syntax.illegal_start",
            Regex(
                """expected|syntax error""",
            ) to "syntax.expected",
            Regex(
                """defined multiple times|duplicate class|already exists""",
            ) to "dex.duplicate_class",
            Regex(
                """compilation failed|compilation error|cannot fit|method too large""",
            ) to "dex.failed",
            Regex(
                """requires api level|min sdk|call requires version""",
            ) to "sdk.too_low",
            Regex(
                """deprecat""",
            ) to "warn.deprecated",
            Regex(
                """unchecked|unsafe operations""",
            ) to "warn.unchecked",
            Regex(
                """unreachable""",
            ) to "warn.unreachable",
            Regex(
                """keystore|does not verify|failed to load|signing""",
            ) to "sign.failed",
            Regex(
                """zip align|zipalign|unable to open""",
            ) to "align.failed",
            Regex(
                """no such file|permission denied|cannot read""",
            ) to "io.failed",        )

        /** Dipakai juga oleh test katalog: setiap id yang mungkin keluar harus ada penjelasannya. */
        fun classify(tool: BuildTool, @Suppress("UNUSED_PARAMETER") file: String?, message: String): String {
            val lower = message.lowercase()
            if (tool == BuildTool.D8 && lower.contains("defined multiple times")) return "dex.duplicate_class"
            if (tool == BuildTool.APKSIGNER) return "sign.failed"
            if (tool == BuildTool.ZIPALIGN) return "align.failed"
            return RULES.firstOrNull { (pattern, _) -> pattern.containsMatchIn(lower) }?.second ?: ErrorCatalog.UNKNOWN
        }

        fun knownMessageIds(): Set<String> = RULES.map { it.second }.toSet()
    }
}
