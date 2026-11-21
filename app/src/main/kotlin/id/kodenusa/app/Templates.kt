package id.kodenusa.app

import android.content.Context
import android.util.Log
import id.kodenusa.core.project.ProjectJson
import java.io.File

/**
 * Template disimpan sebagai projek utuh di `assets/templates/<id>`, bukan sebagai potongan kode
 * yang dirakit di runtime. Konsekuensinya template bisa diuji dengan pipeline build yang sama
 * persis seperti projek buatan user — tidak ada jalur khusus yang bisa diam-diam rusak.
 *
 * Template juga bisa kosong ([BLANK_ID]): user yang ingin menulis semuanya sendiri tidak boleh
 * mentok di layar "Projek baru" hanya karena tidak ada template yang cocok.
 */
class Templates(private val context: Context) {

    /** Memuat daftar template beserta sebab kegagalannya, bukan daftar kosong tanpa alasan. */
    fun load(): Result<List<TemplateInfo>> = runCatching {
        context.assets.open(INDEX).use { input ->
            TemplateCatalog.parse(input.readBytes().decodeToString())
        }
    }.onFailure { Log.e(TAG, "Daftar template gagal dibaca dari $INDEX", it) }

    fun list(): List<TemplateInfo> = load().getOrElse { emptyList() }

    /**
     * Menyalin template ke [target] dan mengganti penanda `__APP_NAME__` serta `__APP_ID__`.
     * Gagal dengan pesan jelas kalau template tidak dikenal, bukan membuat projek setengah jadi.
     */
    fun create(
        templateId: String,
        target: File,
        appName: String,
        appId: String,
        warnaIkon: String? = null,
    ): Result<File> = runCatching {
        if (templateId == BLANK_ID) return@runCatching BlankProject.create(target, appName, appId, warnaIkon)

        val info = list().firstOrNull { it.id == templateId }
            ?: error("Template '$templateId' tidak ada di paket ini")

        require(!target.exists()) { "Folder ${target.name} sudah dipakai" }
        target.mkdirs()

        for (relative in info.files) {
            val out = File(target, relative)
            out.parentFile?.mkdirs()
            val text = context.assets.open("templates/$templateId/$relative").use { input ->
                input.readBytes().decodeToString()
            }
            out.writeText(
                text.replace("__APP_NAME__", appName)
                    .replace("__APP_ID__", appId),
            )
        }
        if (warnaIkon != null) {
            val manifest = File(target, "kodenusa.json")
            val proyek = ProjectJson.decode(manifest.readText())
            manifest.writeText(ProjectJson.encode(proyek.copy(warnaIkon = warnaIkon)))
        }
        target
    }

    /** Projek kosong: manifest, satu activity, satu layout. Semua isi ditulis dari sini. */
    fun createBlank(target: File, appName: String, appId: String): Result<File> =
        runCatching { BlankProject.create(target, appName, appId) }

    companion object {
        const val BLANK_ID = BlankProject.ID
        private const val INDEX = "templates/index.json"
        private const val TAG = "KodeNusaTemplates"
    }
}
