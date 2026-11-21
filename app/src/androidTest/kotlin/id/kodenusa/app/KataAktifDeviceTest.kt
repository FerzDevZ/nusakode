package id.kodenusa.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import id.kodenusa.editor.CodeEditorView
import id.kodenusa.editor.Language
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Lengkapi kata di sisi editor (docs/14 2.5): kata sebelum kursor terbaca benar, dan memilih
 * kandidat MENGGANTI kata yang sedang diketik — buka menambah di belakangnya.
 */
@RunWith(AndroidJUnit4::class)
class KataAktifDeviceTest {

    private val konteks = InstrumentationRegistry.getInstrumentation().targetContext

    private fun editor(isi: String): CodeEditorView {
        val view = CodeEditorView(konteks)
        view.setContent(isi, Language.JAVA)
        return view
    }

    @Test
    fun kata_sebelum_kursor_terbaca_dari_tengah_kata() {
        val view = editor("System.out.println();")
        val editText = editTextDari(view)
        runCatching { editText.setSelection(4) }.onFailure { throw it }
        assertEquals("Syst", view.kataSebelumKursor())
    }

    @Test
    fun kursor_setelah_spasi_menghasilkan_kata_kosong() {
        val view = editor("System ")
        editTextDari(view).setSelection(7)
        assertEquals("", view.kataSebelumKursor())
    }

    @Test
    fun memilih_kandidat_mengganti_kata_ketikan() {
        val view = editor("Sys")
        editTextDari(view).setSelection(3)
        view.gantiKataAktif("System")
        assertEquals("System", view.text)
    }

    @Test
    fun penggantian_tidak_menabrak_kata_sebelumnya() {
        val view = editor("int tot; tot")
        editTextDari(view).setSelection(12)
        view.gantiKataAktif("total")
        assertEquals("int tot; total", view.text)
    }

    private fun editTextDari(view: CodeEditorView): android.widget.EditText {
        val field = CodeEditorView::class.java.getDeclaredField("editor")
        field.isAccessible = true
        return field.get(view) as android.widget.EditText
    }
}
