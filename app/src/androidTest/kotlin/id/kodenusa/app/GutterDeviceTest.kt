package id.kodenusa.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import id.kodenusa.editor.CodeEditorView
import id.kodenusa.editor.GutterView
import id.kodenusa.editor.Language
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regresi perombakan gutter (keluhan pengguna: "nomor baris cuma 1 / kadang kosong /
 * 1 sampai 33 lalu hitam di bawahnya / pembaca linenya bug").
 *
 * Gutter lama membaca `lineCount` tepat setelah setText — saat layout EditText belum dibangun
 * hasilnya 1 — lalu MENG-CACHE-nya, jadi gutter terjebak "1" sampai pengguna mengetik. Gutter
 * baru menggambar ulang dari layout yang hidup; uji ini memaksa siklus layout sungguhan
 * (attach ke jendela lewat Compose host) sebelum membaca angka yang akan digambar.
 */
@RunWith(AndroidJUnit4::class)
class GutterDeviceTest {

    private val konteks = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun gutter_tidak_terjebak_angka_1_setelah_setContent() {
        // Host manual: ukur+layout pohon view secara manual agar layout editor benar-benar
        // dibangun — persis kondisi yang dulu membuat gutter terjebak "1".
        val host = android.widget.FrameLayout(konteks)
        val view = CodeEditorView(konteks)
        val isi = (1..120).joinToString("\n") { "int baris$it = $it;" }
        view.setContent(isi, Language.JAVA)
        host.addView(
            view,
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
        )

        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync {
            host.measure(
                android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(1600, android.view.View.MeasureSpec.EXACTLY),
            )
            host.layout(0, 0, 1080, 1600)
        }

        val angka = gutterDari(view).angkaTergambar()
        assertTrue("gutter tidak boleh kosong", angka.isNotEmpty())
        assertEquals("nomor pertama harus 1", "1", angka.first())
        assertTrue(
            "gutter harus menampilkan banyak nomor (dapat ${angka.size}) — dulu bug terjebak \"1\"",
            angka.size >= 10,
        )
        assertTrue("nomor harus berurutan", angka == angka.mapIndexed { i, s -> (i + 1).toString() })
    }

    @Test
    fun gutter_wrap_hanya_menomori_baris_logika() {
        val host = android.widget.FrameLayout(konteks)
        val view = CodeEditorView(konteks)
        val panjang = "kata ".repeat(60).trim() // satu baris logika yang pasti di-wrap
        view.setContent("$panjang\nbaris dua", Language.JAVA)
        view.setWordWrap(true)
        host.addView(
            view,
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
        )
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync {
            host.measure(
                android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(1600, android.view.View.MeasureSpec.EXACTLY),
            )
            host.layout(0, 0, 1080, 1600)
        }
        val angka = gutterDari(view).angkaTergambar()
        // Baris logika 1 di-wrap jadi banyak baris visual, tapi hanya dinomori sekali.
        assertEquals(1, angka.count { it == "1" })
        assertEquals("2", angka.last())
    }

    @Test
    fun gutter_ikut_ukuran_font_dan_tetap_akurat() {
        val host = android.widget.FrameLayout(konteks)
        val view = CodeEditorView(konteks)
        val isi = (1..80).joinToString("\n") { "x$it" }
        view.setContent(isi, Language.JAVA)
        host.addView(
            view,
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
        )
        val instr = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        instr.runOnMainSync {
            host.measure(
                android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(1600, android.view.View.MeasureSpec.EXACTLY),
            )
            host.layout(0, 0, 1080, 1600)
            view.setTextSizeSp(20f)
            // setTextSize membatalkan layout EditText; paksa siklus ukur+layout lagi supaya
            // Layout tersedia saat dibaca (tanpa ini angkaTergambar() kosong).
            host.measure(
                android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(1600, android.view.View.MeasureSpec.EXACTLY),
            )
            host.layout(0, 0, 1080, 1600)
        }
        var angka = emptyList<String>()
        val batasWaktu = android.os.SystemClock.elapsedRealtime() + 2_000
        while (angka.isEmpty() && android.os.SystemClock.elapsedRealtime() < batasWaktu) {
            instr.runOnMainSync { angka = gutterDari(view).angkaTergambar() }
        }
        assertEquals("1", angka.first())
        assertTrue("setelah ganti font nomor tetap berurutan dari 1", angka.first() == "1")
    }

    @Test
    fun gutter_font_terkecil_di_layar_kecil_tetap_berurutan() {
        // Verifikasi v0.21.0: ukuran font minimum (14sp) di viewport HP kecil (720x1280) —
        // kombinasi terkecil yang pengguna bisa pilih di setelan editor.
        val host = android.widget.FrameLayout(konteks)
        val view = CodeEditorView(konteks)
        val isi = (1..90).joinToString("\n") { "int x$it = $it;" }
        view.setContent(isi, Language.JAVA)
        host.addView(
            view,
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
        )
        val instr = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        instr.runOnMainSync {
            view.setTextSizeSp(14f)
            host.measure(
                android.view.View.MeasureSpec.makeMeasureSpec(720, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(1280, android.view.View.MeasureSpec.EXACTLY),
            )
            host.layout(0, 0, 720, 1280)
        }
        var angka = emptyList<String>()
        val batasWaktu = android.os.SystemClock.elapsedRealtime() + 2_000
        while (angka.isEmpty() && android.os.SystemClock.elapsedRealtime() < batasWaktu) {
            instr.runOnMainSync { angka = gutterDari(view).angkaTergambar() }
        }
        assertTrue("gutter tidak boleh kosong di 14sp/720x1280", angka.isNotEmpty())
        assertEquals("1", angka.first())
        assertTrue(
            "nomor harus berurutan di font terkecil",
            angka == angka.mapIndexed { i, s -> (i + 1).toString() },
        )
        assertTrue("layar kecil harus memuat banyak baris (dapat ${angka.size})", angka.size >= 20)
    }

    private fun gutterDari(view: CodeEditorView): GutterView {
        val field = CodeEditorView::class.java.getDeclaredField("gutter")
        field.isAccessible = true
        return field.get(view) as GutterView
    }
}
