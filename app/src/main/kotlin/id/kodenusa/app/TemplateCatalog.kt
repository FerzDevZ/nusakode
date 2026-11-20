package id.kodenusa.app

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Satu template yang bisa dipilih di layar "Projek baru". */
@Serializable
data class TemplateInfo(
    val id: String,
    val title: String,
    val tagline: String,
    val audience: String,
    val appIdSuffix: String = "",
    /** "aplikasi" (Android/Java) atau "web" (HTML/CSS/JS di dalam WebView). */
    val jenis: String = "aplikasi",
    val files: List<String> = emptyList(),
)

/** Bentuk resmi `assets/templates/index.json`: `{"templates": [...]}`. */
@Serializable
data class TemplateIndex(val templates: List<TemplateInfo> = emptyList())

/**
 * Pembaca daftar template. Dipisah dari [Templates] supaya bisa diuji tanpa `Context`, dan
 * supaya kegagalan baca menjadi pesan yang terlihat di layar — bukan daftar kosong yang membuat
 * tombol "Buat projek" mati tanpa penjelasan.
 *
 * Menerima dua bentuk: objek `{"templates": [...]}` (format sekarang) dan array telanjang
 * `[...]` (format lama) agar berkas lama tidak langsung mematikan aplikasi.
 */
object TemplateCatalog {

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(text: String): List<TemplateInfo> {
        val isi = text.trim()
        require(isi.isNotEmpty()) { "Daftar template kosong." }
        val daftar = if (isi.startsWith("[")) {
            json.decodeFromString(ListSerializer(TemplateInfo.serializer()), isi)
        } else {
            json.decodeFromString(TemplateIndex.serializer(), isi).templates
        }
        require(daftar.isNotEmpty()) { "Daftar template tidak berisi satu pun template." }
        return daftar.filter { it.id.isNotBlank() && it.title.isNotBlank() }.distinctBy { it.id }
    }
}
