package id.kodenusa.editor

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HighlighterTest {

    private fun types(text: String, language: Language = Language.JAVA): List<Pair<Highlighter.TokenType, String>> =
        Highlighter.tokenize(text, language).map { it.type to text.substring(it.start, it.end) }

    @Test
    fun `kata kunci java dikenali`() {
        val hasil = types("public class Main {")
        assertTrue(hasil.contains(Highlighter.TokenType.KEYWORD to "public"))
        assertTrue(hasil.contains(Highlighter.TokenType.KEYWORD to "class"))
    }

    @Test
    fun `kata kunci di dalam string tidak diwarnai`() {
        val hasil = types("""String s = "public class";""")
        assertTrue(hasil.contains(Highlighter.TokenType.STRING to "\"public class\""))
        assertEquals(0, hasil.count { it.first == Highlighter.TokenType.KEYWORD })
    }

    @Test
    fun `komentar mengalahkan kata kunci`() {
        val hasil = types("// public class\nint a;")
        assertTrue(hasil.contains(Highlighter.TokenType.COMMENT to "// public class"))
        assertEquals(0, hasil.count { it.second == "public" || it.second == "class" })
        assertTrue(hasil.contains(Highlighter.TokenType.KEYWORD to "int"))
    }

    @Test
    fun `string dengan escape tetap satu token`() {
        val hasil = types("""String s = "a\"b";""")
        assertEquals(1, hasil.count { it.first == Highlighter.TokenType.STRING })
    }

    @Test
    fun `angka hex dan desimal dikenali`() {
        val hasil = types("int a = 0xFF; float b = 3.14f;")
        assertTrue(hasil.contains(Highlighter.TokenType.NUMBER to "0xFF"))
        assertTrue(hasil.contains(Highlighter.TokenType.NUMBER to "3.14f"))
    }

    @Test
    fun `anotasi dikenali`() {
        val hasil = types("@Override public void onCreate() {}")
        assertTrue(hasil.contains(Highlighter.TokenType.ANNOTATION to "@Override"))
    }

    @Test
    fun `xml tag dikenali`() {
        val hasil = types("<LinearLayout android:layout_width=\"match_parent\" />", Language.XML)
        assertTrue(hasil.contains(Highlighter.TokenType.TAG to "<LinearLayout"))
        assertTrue(hasil.any { it.first == Highlighter.TokenType.KEYWORD && it.second == "android" })
    }

    @Test
    fun `teks kosong menghasilkan daftar kosong`() {
        assertTrue(Highlighter.tokenize("", Language.JAVA).isEmpty())
    }

    @Test
    fun `token selalu naik dan tidak tumpang tindih`() {
        val teks = """
            package id.kodenusa;
            /** Komentar dengan "string" di dalamnya */
            public class Main {
                private int nilai = 0x10; // sisa
                String pesan = "halo \"dunia\"";
            }
        """.trimIndent()

        val token = Highlighter.tokenize(teks, Language.JAVA)
        var sebelumnya = -1
        token.forEach {
            assertTrue(it.start >= sebelumnya, "Token tumpang tindih di ${it.start}")
            sebelumnya = it.end
        }
    }
}
