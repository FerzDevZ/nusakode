package id.kodenusa.app

import android.content.Context
import id.kodenusa.core.ai.AiConfig
import id.kodenusa.core.ai.AiModel

/**
 * Menyimpan setelan AI milik pengguna.
 *
 * Kunci API **tidak pernah** ditanam di aplikasi dan tidak pernah dikirim ke server pembuat aplikasi.
 * Ia hanya hidup di penyimpanan privat aplikasi ini (`SharedPreferences`), yang tidak bisa dibaca
 * aplikasi lain pada HP tanpa root.
 */
class AiSettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences("ai", Context.MODE_PRIVATE)

    fun muat(): AiConfig = AiConfig(
        baseUrl = prefs.getString(KUNCI_ENDPOINT, "").orEmpty(),
        apiKey = prefs.getString(KUNCI_RAHASIA, "").orEmpty(),
        model = prefs.getString(KUNCI_MODEL, "").orEmpty(),
    )

    fun simpan(config: AiConfig) {
        prefs.edit()
            .putString(KUNCI_ENDPOINT, config.baseUrl.trim())
            .putString(KUNCI_RAHASIA, config.apiKey.trim())
            .putString(KUNCI_MODEL, config.model)
            .apply()
    }

    fun pilihModel(id: String) {
        prefs.edit().putString(KUNCI_MODEL, id).apply()
    }

    /** Daftar model terakhir, disimpan supaya layar AI tidak kosong saat dibuka tanpa internet. */
    fun simpanModel(model: List<AiModel>) {
        prefs.edit()
            .putString(KUNCI_DAFTAR, model.joinToString("\n") { "${it.id}\t${it.pemilik}" })
            .apply()
    }

    fun modelTerakhir(): List<AiModel> = prefs.getString(KUNCI_DAFTAR, "").orEmpty()
        .lineSequence()
        .filter { it.isNotBlank() }
        .map { baris ->
            val bagian = baris.split('\t')
            AiModel(bagian[0], bagian.getOrElse(1) { "" })
        }
        .toList()

    fun hapus() = prefs.edit().clear().apply()

    private companion object {
        const val KUNCI_ENDPOINT = "endpoint"
        const val KUNCI_RAHASIA = "kunci"
        const val KUNCI_MODEL = "model"
        const val KUNCI_DAFTAR = "daftar_model"
    }
}
