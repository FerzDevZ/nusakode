package id.kodenusa.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import id.kodenusa.editor.CodeEditorView
import id.kodenusa.editor.Language
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Ukuran font editor (docs/14 2.6): A−/A+ mengubah ukuran teks, dan gutter WAJIB ikut — kalau
 * tidak, nomor baris bergeser dari kodenya (kontrak penyelarasan gutter).
 */
@RunWith(AndroidJUnit4::class)
class UkuranFontEditorDeviceTest {

    private val konteks = InstrumentationRegistry.getInstrumentation().targetContext
    private val density = konteks.resources.displayMetrics.density

    @Test
    fun mengubah_ukuran_mengubah_teks_editor_dan_gutter_sekaligus() {
        val view = CodeEditorView(konteks)
        view.setContent("int a = 1;", Language.JAVA)

        view.setTextSizeSp(20f)

        val (editor, gutter) = editTextDanGutter(view)
        assertEquals(20f * density, editor.textSize, 0.5f)
        assertEquals("gutter harus sama dengan editor", editor.textSize, gutter.textSize, 0.5f)
    }

    @Test
    fun ukuran_awal_mengikuti_setelan_sebelum_konten_dimuat() {
        val view = CodeEditorView(konteks)
        view.setTextSizeSp(16f)
        view.setContent("int a = 1;", Language.JAVA)
        val (editor, gutter) = editTextDanGutter(view)
        assertEquals(16f * density, editor.textSize, 0.5f)
        assertEquals(16f * density, gutter.textSize, 0.5f)
    }

    private fun editTextDanGutter(view: CodeEditorView): Pair<android.widget.EditText, android.widget.TextView> {
        val editorField = CodeEditorView::class.java.getDeclaredField("editor")
        editorField.isAccessible = true
        val gutterField = CodeEditorView::class.java.getDeclaredField("gutter")
        gutterField.isAccessible = true
        return (editorField.get(view) as android.widget.EditText) to
            (gutterField.get(view) as android.widget.TextView)
    }
}
