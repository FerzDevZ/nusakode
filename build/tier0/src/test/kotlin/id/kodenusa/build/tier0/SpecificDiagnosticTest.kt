package id.kodenusa.build.tier0

import id.kodenusa.core.build.BuildTool
import id.kodenusa.core.diagnose.ErrorCatalog
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Diagnostik tetap untuk kegagalan alat yang pesan aslinya berbahasa Inggris dan teknis. */
class SpecificDiagnosticTest {

    @Test
    fun `invokedynamic dari dx dipetakan ke dex lambda`() {
        val diagnostic = specificDiagnostic(BuildTool.D8, sawLambda = true)

        assertNotNull(diagnostic, "lambda seharusnya menghasilkan diagnostik")
        assertEquals("dex.lambda", diagnostic?.messageId)
        assertEquals(BuildTool.D8, diagnostic?.tool)
    }

    @Test
    fun `kegagalan lain tidak dipetakan paksa`() {
        assertNull(specificDiagnostic(BuildTool.D8, sawLambda = false))
    }

    @Test
    fun `pesan dex lambda ada di katalog dan berbahasa Indonesia`() {
        val entry = ErrorCatalog.get("dex.lambda")

        assertTrue(entry.title.contains("Lambda"), "judul: ${entry.title}")
        assertTrue(entry.fix.contains("anonymous class"), "saran: ${entry.fix}")
    }
}
