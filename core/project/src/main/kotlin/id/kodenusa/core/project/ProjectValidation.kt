package id.kodenusa.core.project

import id.kodenusa.core.build.Severity

/**
 * Satu masalah projek. [messageId] dipetakan ke kalimat bahasa Indonesia di `:core:diagnose`,
 * [fallbackMessage] dipakai kalau katalog belum punya entri (tidak boleh kosong).
 */
data class ProjectIssue(
    val messageId: String,
    val field: String,
    val severity: Severity,
    val fallbackMessage: String,
)

data class ProjectValidation(
    val project: KodenusaProject,
    val issues: List<ProjectIssue>,
) {
    val errors: List<ProjectIssue> get() = issues.filter { it.severity == Severity.ERROR }
    val warnings: List<ProjectIssue> get() = issues.filter { it.severity == Severity.WARNING }
    val isValid: Boolean get() = errors.isEmpty()

    /** Proyek yang punya `build.gradle` bukan proyek Tier 0; UI wajib menawarkan cloud, bukan gagal senyap. */
    val requiresCloudEngine: Boolean
        get() = issues.any { it.messageId == ProjectIssueIds.GRADLE_DETECTED && it.severity == Severity.INFO }
}

object ProjectIssueIds {
    const val MISSING_JSON = "project.missing_json"
    const val INVALID_JSON = "project.invalid_json"
    const val UNSUPPORTED_FORMAT = "project.unsupported_format"
    const val NAME_EMPTY = "project.name_empty"
    const val NAME_TOO_LONG = "project.name_too_long"
    const val APP_ID_FORMAT = "project.app_id_format"
    const val VERSION_CODE_POSITIVE = "project.version_code_positive"
    const val MIN_SDK_RANGE = "project.min_sdk_range"
    const val TARGET_BELOW_MIN = "project.target_below_min"
    const val JAVA_VERSION_SUPPORTED = "project.java_version_supported"
    const val MISSING_MANIFEST = "project.missing_manifest"
    const val NO_SOURCES = "project.no_sources"
    const val MISSING_DEPENDENCY = "project.missing_dependency"
    const val PATH_ESCAPE = "project.path_escape"
    const val GRADLE_DETECTED = "project.gradle_detected"
    const val MIN_SDK_TOO_LOW = "project.min_sdk_too_low"
}

/**
 * Validasi murni — tidak menyentuh filesystem. Pemeriksaan file dilakukan di [ProjectLoader]
 * supaya aturan ini bisa diuji tanpa disk.
 */
object ProjectValidator {

    private val APP_ID = Regex("^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+$")

    fun validate(project: KodenusaProject): List<ProjectIssue> = buildList {
        if (project.format != KodenusaProject.CURRENT_FORMAT) {
            add(
                ProjectIssue(
                    messageId = ProjectIssueIds.UNSUPPORTED_FORMAT,
                    field = "format",
                    severity = Severity.ERROR,
                    fallbackMessage = "Format ${project.format} tidak dikenali. " +
                        "Versi yang didukung: ${KodenusaProject.CURRENT_FORMAT}.",
                ),
            )
        }
        if (project.name.isBlank()) {
            add(ProjectIssue(ProjectIssueIds.NAME_EMPTY, "name", Severity.ERROR, "Nama aplikasi belum diisi."))
        }
        if (project.name.length > 50) {
            add(
                ProjectIssue(
                    ProjectIssueIds.NAME_TOO_LONG,
                    "name",
                    Severity.ERROR,
                    "Nama aplikasi maksimal 50 karakter agar tidak terpotong di layar utama.",
                ),
            )
        }
        if (!APP_ID.matches(project.applicationId)) {
            add(
                ProjectIssue(
                    ProjectIssueIds.APP_ID_FORMAT,
                    "applicationId",
                    Severity.ERROR,
                    "Format applicationId harus seperti id.kodenusa.namamu (huruf kecil, angka, titik).",
                ),
            )
        }
        if (project.versionCode <= 0) {
            add(
                ProjectIssue(
                    ProjectIssueIds.VERSION_CODE_POSITIVE,
                    "versionCode",
                    Severity.ERROR,
                    "versionCode harus lebih besar dari 0.",
                ),
            )
        }
        if (project.minSdk !in 21..35) {
            add(
                ProjectIssue(
                    ProjectIssueIds.MIN_SDK_RANGE,
                    "minSdk",
                    Severity.ERROR,
                    "minSdk harus antara 21 dan 35. Default KodeNusa adalah 24 (Android 7).",
                ),
            )
        }
        if (project.targetSdk < project.minSdk) {
            add(
                ProjectIssue(
                    ProjectIssueIds.TARGET_BELOW_MIN,
                    "targetSdk",
                    Severity.ERROR,
                    "targetSdk (${project.targetSdk}) tidak boleh lebih kecil dari minSdk (${project.minSdk}).",
                ),
            )
        }
        if (project.javaVersion !in listOf(8, 11)) {
            add(
                ProjectIssue(
                    ProjectIssueIds.JAVA_VERSION_SUPPORTED,
                    "javaVersion",
                    Severity.ERROR,
                    "Build di HP mendukung level bahasa Java 8 atau 11.",
                ),
            )
        }
    }
}
