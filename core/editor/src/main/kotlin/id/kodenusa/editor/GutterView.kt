package id.kodenusa.editor

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.util.TypedValue
import android.view.View
import id.kodenusa.ui.PaletGelapBengkel
import id.kodenusa.ui.PaletInt
import kotlin.math.max

/**
 * Gutter nomor baris yang digambar sendiri (perombakan docs/14 — bug "kotak hitam" dan
 * "nomor baris cuma 1 / kosong / bergeser").
 *
 * Mengapa bukan TextView berisi "1\n2\n3\n..." lagi:
 * - TextView lama membaca `lineCount` tepat setelah setText, saat layout editor belum dibangun,
 *   lalu MENG-CACHE hasilnya — gutter bisa terjebak "1" sampai pengguna mengetik.
 * - Geser-tekstur dengan translationY hanya akurat bila tinggi baris gutter == tinggi baris
 *   editor piksel-per-piksel; beda font/spacing/wrap sekecil apa pun membuat nomor melenceng.
 * - Saat wrap aktif, nomor harus milik BARIS LOGIKA (baris sambungan dibiarkan kosong) —
 *   tidak mungkin dengan satu string statis.
 *
 * View ini melukis ulang dari [Layout] editor yang hidup setiap kali diminta (`segarkan()`):
 * scroll, perubahan teks, perubahan layout (termasuk saat layout pertama kali jadi), ganti
 * ukuran font, ganti wrap, dan ganti palet.
 */
class GutterView(context: Context) : View(context) {

    /** Sumber keadaan editor; null/false berarti belum siap dan gutter kosong sementara. */
    class Sumber(
        val layout: Layout?,
        val teks: CharSequence,
        val scrollY: Int,
        val tinggiEditor: Int,
        val paddingAtasEditor: Int,
        val barisKursor: Int,
    )

    var sumber: (() -> Sumber?)? = null

    private val paintAngka = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        textSize = sp(13f)
    }
    private val paintLatar = Paint().apply { color = PaletGelapBengkel.surface }
    private val paintGaris = Paint().apply { color = PaletGelapBengkel.border }

    private var palet: PaletInt = PaletGelapBengkel
    private var digitTerakhir = 2

    init {
        terapkanPalet(PaletGelapBengkel)
    }

    fun terapkanPalet(baru: PaletInt) {
        palet = baru
        paintLatar.color = baru.surface
        paintGaris.color = baru.border
        paintAngka.color = baru.textDim
        invalidate()
    }

    fun aturUkuranFont(sp: Float) {
        paintAngka.textSize = sp(sp)
        invalidate()
    }

    /** Minta gutter dilukis ulang dari keadaan editor sekarang. Murah: hanya invalidate. */
    fun segarkan() {
        // Lebar gutter mengikuti jumlah digit; bila berubah, ukur ulang.
        val s = sumber?.invoke()
        val digit = max(2, (s?.layout?.lineCount ?: 1).toString().length)
        if (digit != digitTerakhir) {
            digitTerakhir = digit
            requestLayout()
        }
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val charLebar = paintAngka.measureText("0")
        val lebar = GutterModel.lebar(
            charLebar,
            digitTerakhir,
            dp(8).toFloat(),
            dp(8).toFloat(),
        ).toInt().coerceAtLeast(dp(28))
        setMeasuredDimension(lebar, getDefaultSize(suggestedMinimumHeight, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paintLatar)
        val s = sumber?.invoke() ?: return
        val layout = s.layout ?: return
        if (layout.lineCount <= 0) return

        // Garis pemisah tipis di tepi kanan gutter.
        canvas.drawRect(
            width - dp(1).toFloat(),
            0f,
            width.toFloat(),
            height.toFloat(),
            paintGaris,
        )

        val baselineTambahan = -paintAngka.ascent()
        val xKanan = width - dp(8).toFloat()
        for (item in hitungItem(s)) {
            // Lukis dari baseline baris: getLineTop + tinggi ascent = dasar angka.
            paintAngka.color = if (item.sorot) palet.accent else palet.textDim
            canvas.drawText(item.teks, xKanan - paintAngka.measureText(item.teks), item.y + baselineTambahan, paintAngka)
        }
    }

    private fun hitungItem(s: Sumber): List<GutterModel.Item> {
        val layout = s.layout ?: return emptyList()
        if (layout.lineCount <= 0) return emptyList()
        return GutterModel.hitung(
            lineCount = layout.lineCount,
            yAtas = { i -> s.paddingAtasEditor.toFloat() + layout.getLineTop(i) - s.scrollY },
            awalBaris = { i -> layout.getLineStart(i) },
            teks = s.teks,
            tinggiView = height.toFloat(),
            barisKursor = s.barisKursor,
        )
    }

    /** Untuk uji instrumentasi: nomor yang AKAN digambar pada ukuran saat ini, urut dari atas. */
    fun angkaTergambar(): List<String> = sumber?.invoke()?.let { hitungItem(it) }?.map { it.teks } ?: emptyList()

    private fun sp(value: Float): Float = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_SP,
        value,
        resources.displayMetrics,
    )

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
