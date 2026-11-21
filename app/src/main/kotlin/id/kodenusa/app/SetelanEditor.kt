package id.kodenusa.app

import android.content.Context

/**
 * Setelan editor yang kecil dan bukan rahasia (docs/14 2.6): ukuran font. SharedPreferences
 * cukup — pola yang sama dengan TemaStore; Keystore hanya untuk token dan kunci (ADR-026).
 */
class SetelanEditor(konteks: Context) {

    private val prefs = konteks.getSharedPreferences("editor", Context.MODE_PRIVATE)

    /** Ukuran font editor dalam sp; dibatasi [MIN]..[MAKS] saat dibaca maupun ditulis. */
    var ukuranFont: Float
        get() = prefs.getFloat(KUNCI, DEFAULT).coerceIn(MIN, MAKS)
        set(value) = prefs.edit().putFloat(KUNCI, value.coerceIn(MIN, MAKS)).apply()

    /** Naik satu langkah; mengembalikan ukuran baru supaya pemanggil bisa langsung memakainya. */
    fun perbesar(): Float = geser(+LANGKAH)

    /** Turun satu langkah; mengembalikan ukuran baru. */
    fun perkecil(): Float = geser(-LANGKAH)

    private fun geser(delta: Float): Float {
        val baru = (ukuranFont + delta).coerceIn(MIN, MAKS)
        ukuranFont = baru
        return baru
    }

    companion object {
        const val MIN = 14f
        const val MAKS = 22f
        const val DEFAULT = 14f
        const val LANGKAH = 2f
        private const val KUNCI = "ukuran-font"
    }
}
