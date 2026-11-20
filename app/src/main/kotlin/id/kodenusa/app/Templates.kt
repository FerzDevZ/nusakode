package id.kodenusa.app

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class TemplateInfo(
    val id: String,
    val title: String,
    val tagline: String,
    val audience: String,
    val appIdSuffix: String = "",
    val files: List<String> = emptyList(),
)

/**
 * Template disimpan sebagai projek utuh di `assets/templates/<id>`, bukan sebagai potongan kode
 * yang dirakit di runtime. Konsekuensinya template bisa diuji dengan pipeline build yang sama
 * persis seperti projek buatan user — tidak ada jalur khusus yang bisa diam-diam rusak.
 */
class Templates(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    fun list(): List<TemplateInfo> = runCatching {
        context.assets.open("templates/index.json").use { input ->
            json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(TemplateInfo.serializer()), input.readBytes().decodeToString())
        }
    }.getOrDefault(emptyList())

    /**
     * Menyalin template ke [target] dan mengganti penanda `__APP_NAME__` serta `__APP_ID__`.
     * Gagal dengan pesan jelas kalau template tidak dikenal, bukan membuat projek setengah jadi.
     */
    fun create(templateId: String, target: File, appName: String, appId: String): Result<File> = runCatching {
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
        target
    }
}
