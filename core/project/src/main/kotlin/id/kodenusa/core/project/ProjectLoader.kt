package id.kodenusa.core.project

import id.kodenusa.core.build.Severity
import id.kodenusa.core.build.Tier0Config
import kotlinx.serialization.SerializationException
import java.io.File

data class LoadedProject(
    val project: KodenusaProject?,
    val validation: ProjectValidation?,
    val issues: List<ProjectIssue>,
    val config: Tier0Config?,
) {
    val isBuildable: Boolean get() = config != null && issues.none { it.severity == Severity.ERROR }
}

/**
 * Membaca folder projek menjadi [Tier0Config]. Semua path diverifikasi berada di dalam folder
 * projek (canonicalPath) karena input ini bisa berasal dari zip atau git yang tidak dipercaya.
 */
object ProjectLoader {

    private const val MANIFEST_PATH = "src/main/AndroidManifest.xml"
    private const val SOURCE_ROOT = "src/main/java"
    private const val RES_ROOT = "src/main/res"
    private const val ASSETS_ROOT = "src/main/assets"

    fun load(projectDir: File): LoadedProject {
        val issues = mutableListOf<ProjectIssue>()
        val root = projectDir.canonicalFile

        val manifestFile = File(root, "build.gradle")
        if (manifestFile.exists() || File(root, "build.gradle.kts").exists()) {
            issues += ProjectIssue(
                messageId = ProjectIssueIds.GRADLE_DETECTED,
                field = "buildEngine",
                severity = Severity.INFO,
                fallbackMessage = "Proyek ini memakai Gradle. Tier 0 (di dalam HP) hanya " +
                    "membangun projek kodenusa.json; build di server belum tersedia, " +
                    "jadi projek ini belum bisa dibangun di sini.",
            )
        }

        val jsonFile = File(root, KodenusaProject.FILE_NAME)
        if (!jsonFile.isFile) {
            issues += ProjectIssue(
                messageId = ProjectIssueIds.MISSING_JSON,
                field = KodenusaProject.FILE_NAME,
                severity = Severity.ERROR,
                fallbackMessage = "File ${KodenusaProject.FILE_NAME} tidak ditemukan di folder projek.",
            )
            return LoadedProject(null, null, issues, null)
        }

        val project = try {
            ProjectJson.decode(jsonFile.readText())
        } catch (error: SerializationException) {
            issues += ProjectIssue(
                messageId = ProjectIssueIds.INVALID_JSON,
                field = KodenusaProject.FILE_NAME,
                severity = Severity.ERROR,
                fallbackMessage = "${KodenusaProject.FILE_NAME} tidak bisa dibaca: ${error.message?.take(160)}",
            )
            return LoadedProject(null, null, issues, null)
        }

        val staticIssues = ProjectValidator.validate(project)
        issues += staticIssues

        val manifest = File(root, MANIFEST_PATH)
        if (!manifest.isFile) {
            issues += ProjectIssue(
                messageId = ProjectIssueIds.MISSING_MANIFEST,
                field = MANIFEST_PATH,
                severity = Severity.ERROR,
                fallbackMessage = "AndroidManifest.xml tidak ditemukan. Tanpa manifest, APK tidak bisa dipasang.",
            )
        }

        val javaSources = walk(root, SOURCE_ROOT).filter { it.name.endsWith(".java") }
        if (javaSources.isEmpty()) {
            issues += ProjectIssue(
                messageId = ProjectIssueIds.NO_SOURCES,
                field = SOURCE_ROOT,
                severity = Severity.ERROR,
                fallbackMessage = "Belum ada file .java di $SOURCE_ROOT. Tambahkan minimal satu Activity.",
            )
        }

        val extraJars = project.dependencies.files.mapNotNull { relative ->
            val jar = File(root, relative)
            when {
                !jar.isFile -> {
                    issues += ProjectIssue(
                        messageId = ProjectIssueIds.MISSING_DEPENDENCY,
                        field = relative,
                        severity = Severity.ERROR,
                        fallbackMessage = "Library $relative tidak ditemukan di folder projek.",
                    )
                    null
                }
                !isInside(root, jar) -> {
                    issues += escapeIssue(relative)
                    null
                }
                else -> jar
            }
        }

        val config = if (issues.any { it.severity == Severity.ERROR }) {
            null
        } else {
            Tier0Config(
                projectId = project.applicationId,
                projectDir = root,
                appName = project.name,
                applicationId = project.applicationId,
                versionName = project.versionName,
                versionCode = project.versionCode,
                minSdk = project.minSdk,
                targetSdk = project.targetSdk,
                javaVersion = project.javaVersion,
                javaSources = javaSources,
                resourceDirs = existingDirs(root, RES_ROOT),
                assetDirs = existingDirs(root, ASSETS_ROOT),
                manifest = manifest,
                extraJars = extraJars,
                incremental = true,
            )
        }

        return LoadedProject(project, ProjectValidation(project, issues), issues, config)
    }

    private fun walk(root: File, relative: String): List<File> {
        val dir = File(root, relative)
        if (!dir.isDirectory) return emptyList()
        val out = mutableListOf<File>()
        val queue = ArrayDeque(listOf(dir))
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            val children = current.listFiles() ?: continue
            for (child in children.sortedBy { it.name }) {
                if (child.isDirectory) queue.add(child) else out.add(child)
            }
        }
        return out
    }

    private fun existingDirs(root: File, relative: String): List<File> =
        File(root, relative).takeIf { it.isDirectory }?.let { listOf(it) } ?: emptyList()

    private fun isInside(root: File, candidate: File): Boolean =
        candidate.canonicalPath.startsWith(root.canonicalPath + File.separator)

    private fun escapeIssue(relative: String) = ProjectIssue(
        messageId = ProjectIssueIds.PATH_ESCAPE,
        field = relative,
        severity = Severity.ERROR,
        fallbackMessage = "Path $relative keluar dari folder projek. Ini ditolak demi keamanan.",
    )
}
