package id.kodenusa.app

import android.content.Context
import id.kodenusa.ui.KodenusaTokens
import id.kodenusa.ui.PaletGelapAmoled
import id.kodenusa.ui.PaletGelapBengkel
import id.kodenusa.ui.PaletTerangKertas
import id.kodenusa.ui.PaletTerangTinggi

/** Pilihan tema pengguna. SISTEM mengikuti pengaturan malam Android; KUSTOM memakai .kntema. */
enum class ModeTema { GELAP, AMOLED, TERANG, TERANG_TINGGI, SISTEM, KUSTOM }

/**
 * Penyimpanan pilihan tema. Tema bukan rahasia, jadi cukup SharedPreferences — bukan Keystore
 * (ADR-026 tetap untuk token GitHub dan kunci AI).
 */
class TemaStore(private val konteks: Context) {

    private val prefs = konteks.getSharedPreferences("tema", Context.MODE_PRIVATE)

    var mode: ModeTema
        get() = runCatching { ModeTema.valueOf(prefs.getString(KUNCI_MODE, null) ?: ModeTema.GELAP.name) }
            .getOrDefault(ModeTema.GELAP)
        set(value) = prefs.edit().putString(KUNCI_MODE, value.name).apply()

    /** JSON tema kustom terakhir yang dipakai. Null = belum pernah memakai tema kustom. */
    var jsonKustom: String?
        get() = prefs.getString(KUNCI_KUSTOM, null)
        set(value) = prefs.edit().putString(KUNCI_KUSTOM, value).apply()

    /** Token warna aktif — dipanggil sebelum setContent supaya tidak ada frame warna salah. */
    fun tokens(): KodenusaTokens = when (mode) {
        ModeTema.GELAP -> KodenusaTokens.dari(PaletGelapBengkel, gelap = true)
        ModeTema.AMOLED -> KodenusaTokens.dari(PaletGelapAmoled, gelap = true)
        ModeTema.TERANG -> KodenusaTokens.dari(PaletTerangKertas, gelap = false)
        ModeTema.TERANG_TINGGI -> KodenusaTokens.dari(PaletTerangTinggi, gelap = false)
        ModeTema.SISTEM -> if (ikutSistemMalam()) KodenusaTokens.dari(PaletGelapBengkel, gelap = true)
        else KodenusaTokens.dari(PaletTerangKertas, gelap = false)
        ModeTema.KUSTOM -> tokensKustom()
    }

    private fun tokensKustom(): KodenusaTokens {
        val json = jsonKustom ?: return KodenusaTokens.dari(PaletGelapBengkel, gelap = true)
        val tema = Kntema.parse(json).getOrNull() ?: return KodenusaTokens.dari(PaletGelapBengkel, gelap = true)
        return KodenusaTokens.dari(tema.palet, gelap = tema.gelap)
    }

    private fun ikutSistemMalam(): Boolean =
        (konteks.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES

    private companion object {
        const val KUNCI_MODE = "mode"
        const val KUNCI_KUSTOM = "kustom-json"
    }
}
