package id.kodenusa.editor

/**
 * Penanda warna sederhana berbasis regex, bukan parser penuh. Tujuannya membuat struktur kode
 * terbaca di layar 5 inci tanpa memakan RAM: tidak ada AST, tidak ada salinan teks tambahan.
 *
 * Hasilnya daftar rentang karakter tanpa ketergantungan Android, sehingga bisa diuji di JVM.
 */
object Highlighter {

    enum class TokenType { KEYWORD, STRING, COMMENT, NUMBER, ANNOTATION, TAG }

    data class Token(val start: Int, val end: Int, val type: TokenType)

    private val JAVA_KEYWORDS = setOf(
        "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const",
        "continue", "default", "do", "double", "else", "enum", "extends", "final", "finally", "float",
        "for", "if", "implements", "import", "instanceof", "int", "interface", "long", "native",
        "new", "package", "private", "protected", "public", "return", "short", "static",
        "super", "switch", "synchronized", "this", "throw", "throws", "transient", "try", "void",
        "volatile", "while", "true", "false", "null",
    )

    private val KOTLIN_KEYWORDS = JAVA_KEYWORDS + setOf(
        "fun", "val", "when", "object", "data", "sealed", "override", "suspend", "companion",
        "inline", "lateinit", "by", "init", "is", "as", "typealias", "internal", "open", "operator",
    )

    private val JAVASCRIPT_KEYWORDS = setOf(
        "const", "let", "var", "function", "return", "if", "else", "for", "while", "class",
        "extends", "new", "this", "import", "export", "from", "async", "await", "try", "catch",
        "finally", "throw", "typeof", "instanceof", "null", "undefined", "true", "false", "switch",
        "case", "break", "continue", "interface", "type", "enum", "implements", "public", "private",
    )

    private val PYTHON_KEYWORDS = setOf(
        "def", "class", "return", "if", "elif", "else", "for", "while", "import", "from", "as",
        "try", "except", "finally", "raise", "with", "lambda", "None", "True", "False", "and", "or",
        "not", "in", "is", "pass", "break", "continue", "global", "nonlocal", "yield", "assert",
    )

    private val JSON_KEYWORDS = setOf("true", "false", "null")

    private val XML_KEYWORDS = setOf("xml", "version", "encoding", "xmlns", "android", "app", "tools")

    private val MARKDOWN_KEYWORDS = setOf("true", "false", "null")

    private val STRING_PATTERN = Regex("\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'")
    private val JAVA_COMMENT = Regex("//[^\\n]*|/\\*[\\s\\S]*?\\*/")
    private val XML_COMMENT = Regex("<!--[\\s\\S]*?-->")
    private val PYTHON_COMMENT = Regex("#[^\\n]*")
    private val NUMBER_PATTERN = Regex("\\b(?:0[xX][0-9a-fA-F]+|\\d+(?:\\.\\d+)?[fFdDlL]?)\\b")
    private val ANNOTATION_PATTERN = Regex("@[A-Za-z_][A-Za-z0-9_.]*")
    private val XML_TAG_PATTERN = Regex("</?[A-Za-z_][A-Za-z0-9_:.-]*")
    private val MARKDOWN_HEADING = Regex("(?m)^#{1,6} [^\\n]*")
    private val WORD_PATTERN = Regex("[A-Za-z_][A-Za-z0-9_]*")

    fun tokenize(text: CharSequence, language: Language): List<Token> {
        if (text.isEmpty()) return emptyList()
        val claimed = BooleanArray(text.length)
        val tokens = mutableListOf<Token>()

        fun claim(range: IntRange, type: TokenType) {
            for (index in range) {
                if (claimed[index]) return
            }
            for (index in range) claimed[index] = true
            tokens += Token(range.first, range.last + 1, type)
        }

        // Komentar dan string lebih dulu: kata kunci di dalamnya tidak boleh diwarnai.
        commentPattern(language).findAll(text).forEach { claim(it.range, TokenType.COMMENT) }
        when (language) {
            Language.PYTHON -> TRIPLE_QUOTE.findAll(text).forEach { claim(it.range, TokenType.STRING) }
            Language.MARKDOWN -> MARKDOWN_HEADING.findAll(text).forEach { claim(it.range, TokenType.KEYWORD) }
            else -> Unit
        }
        STRING_PATTERN.findAll(text).forEach { claim(it.range, TokenType.STRING) }
        NUMBER_PATTERN.findAll(text).forEach { claim(it.range, TokenType.NUMBER) }
        ANNOTATION_PATTERN.findAll(text)
            .filter { language == Language.JAVA || language == Language.KOTLIN }
            .forEach { claim(it.range, TokenType.ANNOTATION) }
        if (language == Language.XML) XML_TAG_PATTERN.findAll(text).forEach { claim(it.range, TokenType.TAG) }

        val keywords = keywordsOf(language)
        WORD_PATTERN.findAll(text)
            .filter { it.value in keywords }
            .forEach { claim(it.range, TokenType.KEYWORD) }

        return tokens.sortedBy { it.start }
    }

    private val TRIPLE_QUOTE = Regex("\"\"\"[\\s\\S]*?\"\"\"")

    private fun commentPattern(language: Language): Regex = when (language) {
        Language.XML, Language.MARKDOWN -> XML_COMMENT
        Language.PYTHON -> PYTHON_COMMENT
        Language.JSON, Language.PLAIN -> NO_MATCH
        else -> JAVA_COMMENT
    }

    private val NO_MATCH = Regex("(?!)")

    /** Kata kunci per bahasa; dipakai ulang oleh PenyediaKandidat (lengkapi kata). */
    fun keywordsOf(language: Language): Set<String> = when (language) {
        Language.JAVA -> JAVA_KEYWORDS
        Language.KOTLIN -> KOTLIN_KEYWORDS
        Language.XML -> XML_KEYWORDS
        Language.JSON -> JSON_KEYWORDS
        Language.JAVASCRIPT -> JAVASCRIPT_KEYWORDS
        Language.PYTHON -> PYTHON_KEYWORDS
        Language.MARKDOWN -> MARKDOWN_KEYWORDS
        Language.PLAIN -> emptySet()
    }
}
