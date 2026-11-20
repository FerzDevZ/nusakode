package id.kodenusa.core.diagnose

import id.kodenusa.core.build.BuildTool
import id.kodenusa.core.build.Severity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DiagnosticParserTest {

    private fun parse(vararg lines: String, tool: BuildTool = BuildTool.ECJ) =
        DiagnosticParser().parseAll(tool, lines.toList())

    @Test
    fun `ECJ cannot find symbol menghasilkan file baris kolom dan pesan Indonesia`() {
        val result = parse(
            "----------",
            "1. ERROR in /proj/src/main/java/MainActivity.java (at line 12)",
            "\t\ttextView.setText(missing);",
            "\t\t                ^^^^^^^",
            "missing cannot be resolved",
            "----------",
            "1 problem (1 error)",
        )

        assertEquals(1, result.size)
        val diagnostic = result.single()
        assertEquals(Severity.ERROR, diagnostic.severity)
        assertEquals("/proj/src/main/java/MainActivity.java", diagnostic.file)
        assertEquals(12, diagnostic.line)
        assertEquals(19, diagnostic.column)
        assertEquals("sym.not_found", diagnostic.messageId)
        assertTrue(ErrorCatalog.contains(diagnostic.messageId))
    }

    @Test
    fun `ECJ warning tetap dibaca sebagai warning bukan error`() {
        val result = parse(
            "1. WARNING in /proj/A.java (at line 3)",
            "\t\toldApi();",
            "\t\t^^^^^^",
            "The method oldApi() from the type A is deprecated",
        )

        assertEquals(1, result.size)
        assertEquals(Severity.WARNING, result.single().severity)
        assertEquals("warn.deprecated", result.single().messageId)
    }

    @Test
    fun `ECJ titik koma hilang dipetakan ke syntax expected`() {
        val result = parse(
            "1. ERROR in /proj/A.java (at line 7)",
            "\t\tint a = 1",
            "\t\t         ^",
            "Syntax error, insert \";\" to complete Statement",
        )

        assertEquals("syntax.expected", result.single().messageId)
        assertEquals(7, result.single().line)
    }

    @Test
    fun `ECJ package tidak ada dipetakan ke library belum ditambahkan`() {
        val result = parse(
            "1. ERROR in /proj/A.java (at line 1)",
            "\t\timport androidx.recyclerview.widget.RecyclerView;",
            "\t\t       ^^^^^^^^^^^^^^^^^^^^^^^^",
            "The import androidx.recyclerview cannot be resolved",
        )

        assertEquals("sym.package_missing", result.single().messageId)
    }

    @Test
    fun `ECJ tipe tidak cocok`() {
        val result = parse(
            "1. ERROR in /proj/A.java (at line 9)",
            "\t\tint angka = \"teks\";",
            "\t\t            ^^^^^^",
            "Type mismatch: cannot convert from String to int",
        )

        assertEquals("type.incompatible", result.single().messageId)
    }

    @Test
    fun `ECJ method abstrak belum diisi`() {
        val result = parse(
            "1. ERROR in /proj/A.java (at line 5)",
            "\t\tpublic class A extends Activity {",
            "\t\t       ^^^^^^",
            "The type A must implement the inherited abstract method Runnable.run()",
        )

        assertEquals("type.abstract_missing", result.single().messageId)
    }

    @Test
    fun `aapt2 atribut tidak ada menyimpan file dan baris`() {
        val result = parse(
            "res/layout/activity_main.xml:12: error: attribute 'textt' not found.",
            tool = BuildTool.AAPT2,
        )

        val diagnostic = result.single()
        assertEquals("res.attr_missing", diagnostic.messageId)
        assertEquals("res/layout/activity_main.xml", diagnostic.file)
        assertEquals(12, diagnostic.line)
        assertEquals(Severity.ERROR, diagnostic.severity)
    }

    @Test
    fun `aapt2 manifest rusak`() {
        val result = parse(
            "AndroidManifest.xml:5: error: unexpected element <activity> found in <manifest>.",
            tool = BuildTool.AAPT2,
        )

        assertEquals("res.manifest_invalid", result.single().messageId)
        assertEquals("AndroidManifest.xml", result.single().file)
    }

    @Test
    fun `aapt2 resource gagal dikompilasi`() {
        val result = parse(
            "res/layout/broken.xml:3: error: file failed to compile.",
            tool = BuildTool.AAPT2,
        )

        assertEquals("res.compile_failed", result.single().messageId)
    }

    @Test
    fun `d8 class ganda`() {
        val result = parse(
            "Error: Type id.kodenusa.demo.MainActivity is defined multiple times: a.jar:1, b.jar:1",
            tool = BuildTool.D8,
        )

        assertEquals("dex.duplicate_class", result.single().messageId)
        assertEquals(Severity.ERROR, result.single().severity)
    }

    @Test
    fun `d8 warning tidak dianggap error`() {
        val result = parse(
            "Warning: Missing class android.support.v4.app.Fragment (referenced from MainActivity)",
            tool = BuildTool.D8,
        )

        assertEquals(Severity.WARNING, result.single().severity)
    }

    @Test
    fun `apksigner gagal verifikasi`() {
        val result = parse("DOES NOT VERIFY", tool = BuildTool.APKSIGNER)

        assertEquals("sign.failed", result.single().messageId)
    }

    @Test
    fun `pesan minSdk dipetakan ke sdk terlalu rendah`() {
        val result = parse("error: call requires API level 26 (current min is 24)", tool = BuildTool.D8)

        assertEquals("sdk.too_low", result.single().messageId)
    }

    @Test
    fun `pesan tak dikenal tetap menghasilkan satu diagnostik yang bisa dilaporkan`() {
        val result = parse("error: sesuatu yang belum pernah kami lihat")

        val diagnostic = result.single()
        assertEquals(ErrorCatalog.UNKNOWN, diagnostic.messageId)
        assertNotNull(ErrorCatalog.get(diagnostic.messageId))
    }

    @Test
    fun `baris kosong dan pemisah tidak menghasilkan diagnostik`() {
        val result = parse("", "-----", "3 problems (3 errors)", "   ")

        assertTrue(result.isEmpty())
    }

    @Test
    fun `setiap messageId yang mungkin keluar punya entri katalog`() {
        val ids = DiagnosticParser.knownMessageIds() + ErrorCatalog.UNKNOWN
        ids.forEach { id ->
            assertTrue(ErrorCatalog.contains(id), "Katalog tidak punya entri untuk $id")
        }
    }

    @Test
    fun `flush tidak membocorkan diagnostik ganda`() {
        val parser = DiagnosticParser()
        assertNull(parser.feed(BuildTool.ECJ, "1. ERROR in /proj/A.java (at line 2)"))
        assertNull(parser.flush())
    }
}
