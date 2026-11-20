package id.kodenusa.core.project

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Manifest build Tier 0. Ini menggantikan `build.gradle` — sengaja kecil dan datar supaya
 * pipeline bisa di-cache per file dan selesai di bawah satu menit pada HP 2 GB.
 */
@Serializable
data class KodenusaProject(
    val format: Int = CURRENT_FORMAT,
    val name: String,
    val applicationId: String,
    val versionName: String = "1.0.0",
    val versionCode: Int = 1,
    val minSdk: Int = DEFAULT_MIN_SDK,
    val targetSdk: Int = DEFAULT_TARGET_SDK,
    val javaVersion: Int = DEFAULT_JAVA_VERSION,
    val entry: String? = null,
    val buildEngine: String = ENGINE_TIER0,
    /** `aplikasi`, `web` (WebView jadi APK), atau `web-murni` (situs tanpa APK). */
    val jenis: String = "aplikasi",
    @SerialName("templateId") val templateId: String? = null,
    val dependencies: ProjectDependencies = ProjectDependencies(),
) {
    companion object {
        const val CURRENT_FORMAT = 1
        const val DEFAULT_MIN_SDK = 24
        const val DEFAULT_TARGET_SDK = 34
        const val DEFAULT_JAVA_VERSION = 8
        const val ENGINE_TIER0 = "tier0"
        const val JENIS_WEB_MURNI = "web-murni"
        const val FILE_NAME = "kodenusa.json"
    }
}

@Serializable
data class ProjectDependencies(
    val files: List<String> = emptyList(),
)

object ProjectJson {
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    private val json = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun decode(text: String): KodenusaProject = json.decodeFromString(KodenusaProject.serializer(), text)

    fun encode(project: KodenusaProject): String = json.encodeToString(KodenusaProject.serializer(), project)
}
