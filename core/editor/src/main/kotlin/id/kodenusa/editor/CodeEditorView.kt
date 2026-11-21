package id.kodenusa.editor

import android.content.Context
import android.graphics.Typeface
import android.text.Editable
import android.text.Spannable
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import id.kodenusa.ui.PaletGelapBengkel
import id.kodenusa.ui.PaletInt
import kotlin.math.max

/**
 * Editor kode minimal untuk perangkat 2 GB.
 *
 * Keputusan penting:
 * - Memakai [EditText] asli, bukan Compose BasicTextField. Pada file 2.000 baris, EditText hanya
 *   melukis baris yang terlihat; BasicTextField merekomposisi seluruh teks setiap ketikan.
 * - Penyorotan sintaks hanya dilakukan pada baris yang terlihat, dan jumlah span lama dibersihkan
 *   supaya tidak menumpuk menjadi ribuan span.
 * - Teks tidak di-wrap secara default (setHorizontallyScrolling) karena nomor baris di gutter harus
 *   tetap sejajar. Pengguna bisa menyalakan wrap lewat [setWordWrap].
 * - Riwayat undo dibuat sendiri (bukan mengandalkan IME) supaya tombol undo di bar aksesori bekerja
 *   sama seperti Ctrl+Z di desktop.
 */
class CodeEditorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val gutter = GutterView(context)
    private val editor = EditText(context)

    private var language = Language.JAVA
    private var highlighting = false
    private var wrap = false

    /**
     * Palet warna editor dalam ARGB (dunia View, bukan Compose). Diisi dari tema aktif oleh
     * EditorScreen; menggantinya langsung mengecat ulang gutter, teks, dan penyorotan.
     */
    var palet: PaletInt = PaletGelapBengkel
        set(value) {
            field = value
            gutter.terapkanPalet(value)
            editor.setTextColor(value.text)
            applyHighlight()
        }

    private val undoStack = ArrayDeque<String>()
    private val redoStack = ArrayDeque<String>()
    private var lastSnapshotAt = 0L
    private var restoring = false

    var onTextChanged: ((String) -> Unit)? = null
    var onCursorMoved: ((line: Int, column: Int) -> Unit)? = null

    val text: String get() = editor.text?.toString().orEmpty()
    val lineCount: Int get() = editor.lineCount

    init {
        background = null
        gutter.apply {
            isFocusable = false
            isClickable = false
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }

        editor.apply {
            setBackgroundColor(0x00000000)
            setTextColor(palet.text)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.MONOSPACE
            gravity = Gravity.TOP or Gravity.START
            setPadding(dp(10), dp(10), dp(10), dp(48))
            includeFontPadding = false
            setLineSpacing(0f, LINE_SPACING_MULTIPLIER)
            isSingleLine = false
            setHorizontallyScrolling(true)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            imeOptions = android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI
            setTextIsSelectable(true)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(gutter)
            addView(editor)
        }
        addView(
            row,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
        )
        gutter.sumber = { sumberGutter() }

        editor.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                gutter.segarkan()
                applyHighlight()
                snapshotIfNeeded()
                onTextChanged?.invoke(s?.toString().orEmpty())
                reportCursor()
            }
        })

        editor.setOnScrollChangeListener { _: View, _: Int, _: Int, _: Int, _: Int ->
            gutter.segarkan()
            applyHighlight()
        }

        // Kunci perbaikan bug lama: begitu layout editor JADI (termasuk pertama kali setelah
        // setText), gutter dihitung ulang. Dulu lineCount dibaca sebelum layout jadi sehingga
        // gutter terjebak "1" karena di-cache.
        editor.viewTreeObserver.addOnGlobalLayoutListener { gutter.segarkan() }

        editor.setOnClickListener { reportCursor() }
    }

    fun setContent(content: String, languageName: Language) {
        language = languageName
        highlighting = true
        restoring = true
        editor.setText(content)
        restoring = false
        highlighting = false
        undoStack.clear()
        redoStack.clear()
        gutter.segarkan()
        applyHighlight()
        reportCursor()
    }

    fun languageOf(path: String): Language = Language.of(path)

    /** Menyisipkan teks di posisi kursor; dipakai bar aksesori (tab, kurung, titik dua). */
    fun insertSnippet(snippet: String) {
        val editable = editor.text ?: return
        val start = minOf(editor.selectionStart, editor.selectionEnd).coerceAtLeast(0)
        val end = maxOf(editor.selectionStart, editor.selectionEnd).coerceAtLeast(0)
        editable.replace(start, end, snippet)
        editor.setSelection((start + snippet.length).coerceAtMost(editable.length))
    }

    /** Menggeser kursor satu karakter (bukan satu baris) — tombol panah di bar aksesori. */
    fun moveCursor(delta: Int) {
        val editable = editor.text ?: return
        val target = (editor.selectionEnd + delta).coerceIn(0, editable.length)
        editor.setSelection(target)
        reportCursor()
    }

    fun undo() {
        val previous = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(text)
        restore(previous)
    }

    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(text)
        restore(next)
    }

    /**
     * Merapikan indentasi seluruh berkas (tombol "Rapikan", docs/13 C2). Mengembalikan true
     * bila isi berubah. Snapshot undo dipaksa satu kali supaya hasil rapikan bisa dibatalkan.
     */
    fun rapikan(): Boolean {
        val sebelum = text
        val sesudah = Rapikan.rapikan(sebelum, language)
        if (sesudah == sebelum) return false
        lastSnapshotAt = 0L
        snapshotIfNeeded()
        val posisi = editor.selectionStart.coerceAtLeast(0)
        restoring = true
        editor.setText(sesudah)
        editor.setSelection(posisi.coerceIn(0, sesudah.length))
        restoring = false
        gutter.segarkan()
        applyHighlight()
        onTextChanged?.invoke(sesudah)
        reportCursor()
        return true
    }

    fun setWordWrap(enabled: Boolean) {
        wrap = enabled
        editor.setHorizontallyScrolling(!enabled)
        // JANGAN menyetel maxLines = 1 di sini. Dulu baris di bawah ini ada, dan efeknya seluruh
        // berkas hanya menampilkan SATU baris — sisa teks seolah tertutup kotak gelap. maxLines
        // hanya membatasi jumlah baris yang dilukis, bukan cara mematikan wrap.
        editor.maxLines = Int.MAX_VALUE
        gutter.segarkan()
    }

    val wordWrap: Boolean get() = wrap

    /**
     * Ukuran font editor (docs/14 2.6). Gutter WAJIB ikut diganti: kalau ukurannya beda, tinggi
     * baris gutter dan editor tidak sama lagi dan nomor baris bergeser dari kodenya.
     */
    fun setTextSizeSp(sp: Float) {
        editor.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        gutter.aturUkuranFont(sp)
        gutter.segarkan()
    }

    /**
     * Kata yang sedang diketik: huruf beruntun tepat sebelum kursor. Dipakai lengkapi kata
     * (docs/14 2.5); kosong berarti kursor tidak sedang di tengah kata.
     */
    fun kataSebelumKursor(): String {
        val (awal, akhir) = batasKata()
        return (editor.text ?: return "").substring(awal, akhir)
    }

    /** Mengganti kata yang sedang diketik dengan [kata] — aksi memilih kandidat lengkapi kata. */
    fun gantiKataAktif(kata: String) {
        val editable = editor.text ?: return
        val (awal, akhir) = batasKata()
        if (awal == akhir) return
        editable.replace(awal, akhir, kata)
        editor.setSelection((awal + kata.length).coerceAtMost(editable.length))
    }

    /** Batas (awal, akhir) kata di sebelah kiri kursor; akhir = posisi kursor. */
    private fun batasKata(): Pair<Int, Int> {
        val editable = editor.text ?: return 0 to 0
        val akhir = editor.selectionStart.coerceIn(0, editable.length)
        var awal = akhir
        while (awal > 0) {
            val c = editable[awal - 1]
            if (!(c.isLetterOrDigit() || c == '_')) break
            awal -= 1
        }
        return awal to akhir
    }

    /** Jumlah kemunculan [query] (tanpa membedakan huruf besar/kecil). */
    fun countMatches(query: String): Int {
        if (query.isEmpty()) return 0
        val source = text.lowercase()
        val needle = query.lowercase()
        var count = 0
        var index = source.indexOf(needle)
        while (index >= 0) {
            count += 1
            index = source.indexOf(needle, index + needle.length)
        }
        return count
    }

    /**
     * Memilih kemunculan ke-[index] dari [query] dan menggulir ke sana. Mengembalikan offset awal
     * atau -1 bila tidak ada. Dipakai tombol "cari berikutnya".
     */
    fun selectMatch(query: String, index: Int): Int {
        if (query.isEmpty()) return -1
        val source = text.lowercase()
        val needle = query.lowercase()
        val offsets = ArrayList<Int>()
        var cursor = source.indexOf(needle)
        while (cursor >= 0 && offsets.size <= index) {
            offsets += cursor
            cursor = source.indexOf(needle, cursor + needle.length)
        }
        val offset = offsets.getOrNull(index) ?: return -1
        editor.setSelection(offset, (offset + query.length).coerceAtMost(text.length))
        editor.requestFocus()
        reportCursor()
        return offset
    }

    val cursorLine: Int get() = lineOf(editor.selectionStart)
    val cursorColumn: Int get() = columnOf(editor.selectionStart)

    private fun restore(content: String) {
        restoring = true
        editor.setText(content)
        editor.setSelection(editor.text.length)
        restoring = false
        gutter.segarkan()
        applyHighlight()
        onTextChanged?.invoke(content)
        reportCursor()
    }

    private fun snapshotIfNeeded() {
        if (restoring) return
        val now = System.currentTimeMillis()
        if (now - lastSnapshotAt < SNAPSHOT_INTERVAL_MS) return
        lastSnapshotAt = now
        undoStack.addLast(text)
        if (undoStack.size > MAX_HISTORY) undoStack.removeFirst()
        redoStack.clear()
    }

    private fun reportCursor() {
        onCursorMoved?.invoke(cursorLine, cursorColumn)
        gutter.segarkan()
    }

    private fun lineOf(offset: Int): Int {
        val layout = editor.layout ?: return 1
        return layout.getLineForOffset(offset.coerceIn(0, text.length)) + 1
    }

    private fun columnOf(offset: Int): Int {
        val layout = editor.layout ?: return 1
        val safe = offset.coerceIn(0, text.length)
        return safe - layout.getLineStart(layout.getLineForOffset(safe)) + 1
    }

    private fun refreshGutter() {
        gutter.segarkan()
    }

    /** Keadaan editor untuk gutter; dipanggil saat gutter dilukis. */
    private fun sumberGutter(): GutterView.Sumber = GutterView.Sumber(
        layout = editor.layout,
        teks = editor.text ?: "",
        scrollY = editor.scrollY,
        tinggiEditor = editor.height,
        paddingAtasEditor = editor.paddingTop,
        barisKursor = if (editor.layout == null) 0 else lineOf(editor.selectionStart),
    )

    private fun applyHighlight() {
        if (highlighting) return
        val editable = editor.text ?: return
        val layout = editor.layout ?: return

        highlighting = true
        try {
            editable.getSpans(0, editable.length, ForegroundColorSpan::class.java)
                .filter { it is KodeSpan }
                .forEach { editable.removeSpan(it) }

            val firstLine = max(0, layout.getLineForVertical(editor.scrollY) - 2)
            val lastLine = minOf(editor.lineCount - 1, layout.getLineForVertical(editor.scrollY + editor.height) + 2)
            if (lastLine < firstLine) return

            val start = layout.getLineStart(firstLine)
            val end = layout.getLineEnd(lastLine)
            if (end <= start) return

            val window = editable.subSequence(start, end).toString()
            Highlighter.tokenize(window, language).forEach { token ->
                val from = start + token.start
                val to = start + token.end
                if (from < 0 || to > editable.length || from >= to) return@forEach
                val span = KodeSpan(colorOf(token.type))
                editable.setSpan(span, from, to, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        } finally {
            highlighting = false
        }
    }

    private fun colorOf(type: Highlighter.TokenType): Int = when (type) {
        Highlighter.TokenType.KEYWORD -> palet.accent
        Highlighter.TokenType.STRING -> palet.ok
        Highlighter.TokenType.COMMENT -> palet.textDim
        Highlighter.TokenType.NUMBER -> palet.warn
        Highlighter.TokenType.ANNOTATION -> palet.info
        Highlighter.TokenType.TAG -> palet.info
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    /** Subclass supaya span milik penyorot bisa dibedakan dari span lain di teks. */
    private class KodeSpan(color: Int) : ForegroundColorSpan(color)

    private companion object {
        const val LINE_SPACING_MULTIPLIER = 1.15f
        const val MAX_HISTORY = 60
        const val SNAPSHOT_INTERVAL_MS = 400L
    }
}
