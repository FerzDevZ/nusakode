package id.kodenusa.app

import android.content.Context
import id.kodenusa.core.ai.AiConfig
import id.kodenusa.core.ai.AiModel

/**
 * Menyimpan setelan AI milik pengguna.
 *
 * Kunci API **tidak pernah** ditanam di aplikasi dan tidak pernah dikirim ke server pembuat aplikasi.
 * Ia hidup di penyimpanan privat aplikasi ini dan **disandikan** lewat [KunciAman] (Android
 * Keystore), sehingga menyalin berkas preferensi tidak cukup untuk mengambil kunci.
 */
class AiSettingsStore(
    context: Context,
    private val sandi: Penyandi = KunciAman,
) {

    private val prefs = context.getSharedPreferences("ai", Context.MODE_PRIVATE)

    /**
     * Membaca nilai rahasia. Nilai lama yang masih teks biasa (dari versi sebelum 0.9.3) langsung
     * ditulis ulang dalam bentuk tersandi, jadi tidak ada pengguna yang harus mengisi ulang kunci.
     */
    private fun bacaRahasia(kunci: String): String {
        val mentah = prefs.getString(kunci, "").orEmpty()
        if (mentah.isEmpty()) return ""
        if (mentah.startsWith(KunciAman.AWALAN)) return sandi.dekripsi(mentah)
        val asli = sandi.dekripsi(mentah)
        if (asli.isNotEmpty()) prefs.edit().putString(kunci, sandi.enkripsi(asli)).apply()
        return asli
    }

    private fun tulisRahasia(kunci: String, nilai: String) {
        val bersih = nilai.trim()
        prefs.edit()
            .putString(kunci, if (bersih.isEmpty()) "" else sandi.enkripsi(bersih))
            .apply()
    }

    fun muat(): AiConfig = AiConfig(
        baseUrl = prefs.getString(KUNCI_ENDPOINT, "").orEmpty(),
        apiKey = bacaRahasia(KUNCI_RAHASIA),
        model = prefs.getString(KUNCI_MODEL, "").orEmpty(),
    )

    fun simpan(config: AiConfig) {
        prefs.edit()
            .putString(KUNCI_ENDPOINT, config.baseUrl.trim())
            .putString(KUNCI_MODEL, config.model)
            .apply()
        tulisRahasia(KUNCI_RAHASIA, config.apiKey)
    }

    fun pilihModel(id: String) {
        prefs.edit().putString(KUNCI_MODEL, id).apply()
    }

    /** Alamat galeri komunitas terakhir (docs/14 4.4) — bukan rahasia, cukup prefs biasa. */
    fun muatGaleriUrl(): String =
        prefs.getString(KUNCI_GALERI, GALERI_BAWAAN).orEmpty().ifBlank { GALERI_BAWAAN }

    fun simpanGaleriUrl(url: String) {
        prefs.edit().putString(KUNCI_GALERI, url.trim()).apply()
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
        prefs.edit().putString(KUNCI_ENDPOINT, endpoint.trim()).apply()
        tulisRahasia(KUNCI_RAHASIA, kunci)
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

    /** Jumlah karakter yang sudah dikirim ke AI untuk projek ini (indikator biaya jujur). */
    fun karakterTerkirim(kunciProjek: String): Int =
        prefs.getInt(KUNCI_KARAKTER + kunciProjek, 0)

    fun tambahKarakter(kunciProjek: String, jumlah: Int) {
        prefs.edit().putInt(KUNCI_KARAKTER + kunciProjek, karakterTerkirim(kunciProjek) + jumlah).apply()
    }

    fun nolkanKarakter(kunciProjek: String) {
        prefs.edit().remove(KUNCI_KARAKTER + kunciProjek).apply()
    }

    /**
     * Mode hemat kuota (docs/13 C5): konteks yang dikirim ke AI dipangkas — berkas dipotong
     * lebih pendek, sematan dibatasi 2, dan konteks agen lebih kecil. Default mati supaya
     * jawaban AI memakai konteks penuh; pengguna kuota terbatas yang menyalakannya sendiri.
     */
    fun hematKuota(): Boolean = prefs.getBoolean(KUNCI_HEMAT_KUOTA, false)

    fun simpanHematKuota(aktif: Boolean) {
        prefs.edit().putBoolean(KUNCI_HEMAT_KUOTA, aktif).apply()
    }

    private companion object {
        const val KUNCI_ENDPOINT = "endpoint"
        const val KUNCI_RAHASIA = "kunci"
        const val KUNCI_MODEL = "model"
        const val KUNCI_DAFTAR = "daftar_model"
        const val KUNCI_PERCAKAPAN = "percakapan_"
        const val KUNCI_KARAKTER = "karakter_"
        const val KUNCI_GALERI = "galeri_url"

        /** Alamat daftar galeri bawaan: berkas di repo KodeNusa sendiri (ADR-001, tanpa server). */
        const val GALERI_BAWAAN =
            "https://raw.githubusercontent.com/FerzDevZ/nusakode/main/galeri/galeri.json"
        const val KUNCI_HEMAT_KUOTA = "hemat_kuota"
    }
}
