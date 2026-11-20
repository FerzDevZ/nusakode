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

    /**
     * Menyimpan apa yang sedang diketik pengguna, bukan hanya setelah "Muat model" ditekan.
     * Tanpa ini, menutup layar AI lalu membukanya lagi terasa seperti "reset" — keluhan nyata pengguna.
     */
    fun simpanDraf(endpoint: String, kunci: String) {
        prefs.edit()
            .putString(KUNCI_ENDPOINT, endpoint.trim())
            .putString(KUNCI_RAHASIA, kunci.trim())
            .apply()
    }

    /** Menyimpan obrolan per projek supaya jawaban panjang tidak hilang saat layar ditutup. */
    fun simpanPercakapan(kunciProjek: String, pesan: List<Pair<String, String>>) {
        val isi = buildString {
            append('[')
            pesan.forEachIndexed { urutan, (peran, teks) ->
                if (urutan > 0) append(',')
                append("{\"p\":\"").append(peran).append("\",\"t\":\"")
                append(teks.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n"))
                append("\"}")
            }
            append(']')
        }
        prefs.edit().putString(KUNCI_PERCAKAPAN + kunciProjek, isi).apply()
    }

    fun muatPercakapan(kunciProjek: String): List<Pair<String, String>> {
        val mentah = prefs.getString(KUNCI_PERCAKAPAN + kunciProjek, null) ?: return emptyList()
        return runCatching {
            val akar = kotlinx.serialization.json.Json.parseToJsonElement(mentah)
                .let { it as kotlinx.serialization.json.JsonArray }
            akar.mapNotNull { elemen ->
                val objek = elemen as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
                val peran = objek["p"]?.let { (it as kotlinx.serialization.json.JsonPrimitive).content }
                val teks = objek["t"]?.let { (it as kotlinx.serialization.json.JsonPrimitive).content }
                if (peran == null || teks == null) null else peran to teks
            }
        }.getOrDefault(emptyList())
    }

    fun bersihkanPercakapan(kunciProjek: String) {
        prefs.edit().remove(KUNCI_PERCAKAPAN + kunciProjek).apply()
    }

    private companion object {
        const val KUNCI_ENDPOINT = "endpoint"
        const val KUNCI_RAHASIA = "kunci"
        const val KUNCI_MODEL = "model"
        const val KUNCI_DAFTAR = "daftar_model"
        const val KUNCI_PERCAKAPAN = "percakapan_"
    }
}
