package id.kodenusa.core.build

import kotlinx.coroutines.flow.Flow
import java.io.File

/**
 * Urutan langkah pipeline Tier 0. Urutan ini tidak boleh diubah tanpa ADR baru:
 * resource harus ter-link sebelum R.java dikompilasi, dan dex harus selesai sebelum APK dibungkus.
 */
enum class BuildStep(val title: String, val detail: String) {
    PREPARE("Menyiapkan projek", "Memeriksa manifest, resource, dan folder kerja"),
    AAPT_COMPILE("Menyusun resource", "Mengompilasi layout, drawable, dan values"),
    AAPT_LINK("Menyambungkan resource", "Membuat resource.arsc dan R.java"),
    JAVAC("Mengompilasi Java", "Mengubah .java menjadi .class"),
    D8("Mengubah ke dex", "Mengubah .class menjadi classes.dex"),
    PACKAGE("Membungkus APK", "Menggabungkan dex, resource, dan manifest"),
    ALIGN("Merapikan APK", "Menyelaraskan entri zip"),
    SIGN("Menandatangani APK", "Menandatangani dengan keystore debug"),
}

enum class BuildTool { AAPT2, ECJ, D8, ZIPALIGN, APKSIGNER, INTERNAL }

enum class LogStream { STDOUT, STDERR, SYSTEM }

enum class Severity { INFO, WARNING, ERROR }

/**
 * Satu masalah yang bisa diklik user. [messageId] adalah kunci katalog bahasa Indonesia di
 * `:core:diagnose`; pesan mentah tool tetap disimpan untuk panel detail.
 */
data class Diagnostic(
    val severity: Severity,
    val tool: BuildTool,
    val messageId: String,
    val rawMessage: String,
    val file: String? = null,
    val line: Int? = null,
    val column: Int? = null,
)

data class Tier0Config(
    val projectId: String,
    val projectDir: File,
    val appName: String,
    val applicationId: String,
    val versionName: String,
    val versionCode: Int,
    val minSdk: Int,
    val targetSdk: Int,
    val javaVersion: Int,
    val javaSources: List<File>,
    val resourceDirs: List<File>,
    val assetDirs: List<File>,
    val manifest: File,
    /** Warna latar ikon aplikasi (docs/14 2.2); null = warna bawaan KodeNusa. */
    val warnaIkon: String? = null,
    val extraJars: List<File> = emptyList(),
    val incremental: Boolean = true,
)

sealed interface BuildRequest {
    data class Local(val config: Tier0Config) : BuildRequest
}

sealed interface BuildEvent {
    data class StepStarted(val step: BuildStep, val index: Int, val total: Int) : BuildEvent
    data class StepSkipped(val step: BuildStep, val reason: String) : BuildEvent
    data class Progress(val step: BuildStep, val percent: Int) : BuildEvent
    data class Log(val stream: LogStream, val tool: BuildTool, val line: String) : BuildEvent
    data class Problem(val diagnostic: Diagnostic) : BuildEvent
    data class Artifact(val apk: File, val sizeBytes: Long) : BuildEvent
    data class Finished(val result: BuildResult) : BuildEvent
}

data class BuildResult(
    val success: Boolean,
    val apk: File?,
    val durationMs: Long,
    val diagnostics: List<Diagnostic>,
    val stepsExecuted: List<BuildStep>,
    val stepsSkipped: List<BuildStep>,
    val apkSizeBytes: Long? = null,
) {
    val errorCount: Int get() = diagnostics.count { it.severity == Severity.ERROR }
}

/**
 * Satu-satunya kontrak yang dilihat UI. Tier 0/1/2 wajib berperilaku sama untuk input yang sama;
 * `BuildEngineContractTest` dijalankan terhadap setiap implementasi.
 */
interface BuildEngine {
    fun build(request: BuildRequest): Flow<BuildEvent>
}

class BuildFailure(message: String, val diagnostics: List<Diagnostic> = emptyList()) : Exception(message)
