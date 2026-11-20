package id.kodenusa.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import id.kodenusa.editor.CodeEditorView
import id.kodenusa.editor.Language
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regresi untuk keluhan "cuma bisa lihat 1 baris" + "ada kotak hitam menutupi baris lain".
 *
 * Penyebabnya `setWordWrap(false)` yang menyetel `maxLines = 1`. Editor tetap melaporkan jumlah
 * baris teks yang benar, tetapi hanya SATU baris yang dilukis — sisanya tampak seperti kotak gelap
 * kosong. Uji ini menahan agar kesalahan itu tidak kembali.
 */
@RunWith(AndroidJUnit4::class)
class EditorBarisDeviceTest {

    private val konteks = InstrumentationRegistry.getInstrumentation().targetContext

    private fun editor(isi: String): CodeEditorView {
        val view = CodeEditorView(konteks)
        view.setContent(isi, Language.JAVA)
        return view
    }

    @Test
    fun tanpa_wrap_semua_baris_tetap_terlihat() {
        val isi = (1..20).joinToString("\n") { "int baris$it = $it;" }
        val view = editor(isi)

        view.setWordWrap(false)

        // Teks harus utuh…
        assertEquals(20, view.text.lines().size)
        // …dan editor tidak boleh dikunci ke satu baris, karena itulah bug "cuma 1 baris".
        val editText = editTextDari(view)
        assertTrue("maxLines harus membiarkan banyak baris, bukan 1", editText.maxLines > 1)
    }

    @Test
    fun menyalakan_wrap_tidak_mengubah_isi_teks() {
        val isi = "a\nb\nc\n"
        val view = editor(isi)

        view.setWordWrap(true)
        assertEquals(4, view.text.lines().size)
        view.setWordWrap(false)
        assertEquals(4, view.text.lines().size)
        assertTrue(editTextDari(view).maxLines > 1)
    }

    private fun editTextDari(view: CodeEditorView): android.widget.EditText {
        val field = CodeEditorView::class.java.getDeclaredField("editor")
        field.isAccessible = true
        return field.get(view) as android.widget.EditText
    }
}
