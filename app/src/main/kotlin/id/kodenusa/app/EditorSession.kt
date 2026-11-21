package id.kodenusa.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File

/**
 * Status editor: tab yang terbuka, teks di memori, dan penanda belum disimpan.
 *
 * Kenapa teks disimpan di memori, bukan dibaca ulang dari disk setiap pindah tab: pindah tab harus
 * terasa instan di HP 2 GB, dan perubahan yang belum disimpan tidak boleh hilang saat pengguna
 * menekan tombol kembali. Isi memori dibatasi oleh jumlah tab yang dibuka (maksimal [MAX_TABS]),
 * jadi bukan cache tanpa batas.
 */
class EditorSession(
    private val projectDir: File,
    initial: File?,
    /** Riwayat berkas (docs/14 2.8); null = tanpa riwayat (uji unit lama tetap ringan). */
    private val riwayat: RiwayatBerkas? = null,
) {

    val tabs = mutableStateListOf<File>()
    var active by mutableStateOf<File?>(null)
        private set

    private val texts = mutableStateMapOf<String, String>()
    private val dirtyFlags = mutableStateMapOf<String, Boolean>()

    init {
        initial?.let { open(it) }
    }

    val activeText: String
        get() = active?.let { texts[it.path] }.orEmpty()

    val hasUnsaved: Boolean
        get() = dirtyFlags.values.any { it }

    fun isDirty(file: File): Boolean = dirtyFlags[file.path] == true

    fun isOpen(file: File): Boolean = tabs.any { it.path == file.path }

    fun textOf(file: File): String = texts.getOrPut(file.path) { file.readText() }

    fun open(file: File) {
        if (tabs.none { it.path == file.path }) {
            if (tabs.size >= MAX_TABS) saveAndClose(tabs.first())
            tabs += file
            texts[file.path] = file.readText()
            dirtyFlags[file.path] = false
        }
        active = file
    }

    fun update(file: File, text: String) {
        texts[file.path] = text
        dirtyFlags[file.path] = true
    }

    fun save(file: File): Boolean {
        val text = texts[file.path] ?: return false
        return runCatching {
            // Riwayat berkas (docs/14 2.8): isi lama disalin dulu, tapi hanya kalau benar-benar
            // berubah — simpan-ulang tanpa perubahan tidak boleh membakar slot versi.
            if (riwayat != null && file.isFile) {
                val lama = runCatching { file.readText() }.getOrNull()
                if (lama != null && lama != text) riwayat.catat(file, lama)
            }
            file.writeText(text)
            dirtyFlags[file.path] = false
            true
        }.getOrDefault(false)
    }

    /** Menyimpan semua tab. Mengembalikan jumlah berkas yang berhasil ditulis. */
    fun saveAll(): Int = tabs.filter { isDirty(it) }.count { save(it) }

    fun close(file: File) {
        if (isDirty(file)) save(file)
        tabs.removeAll { it.path == file.path }
        texts.remove(file.path)
        dirtyFlags.remove(file.path)
        if (active?.path == file.path) active = tabs.lastOrNull()
    }

    fun saveAndClose(file: File) = close(file)

    fun relative(file: File): String = file.relativeTo(projectDir).invariantSeparatorsPath

    private companion object {
        /** Batas tab menjaga pemakaian RAM: tiap tab menyimpan satu salinan teks. */
        const val MAX_TABS = 8
    }
}
