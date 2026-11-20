package id.kodenusa.app

import id.kodenusa.core.build.Severity
import id.kodenusa.core.project.KodenusaProject
import id.kodenusa.core.project.LoadedProject
import id.kodenusa.core.project.ProjectLoader
import java.io.File

data class ProjectSummary(
    val dir: File,
    val name: String,
    val applicationId: String,
    val lastOpened: Long,
    val buildable: Boolean,
)

/**
 * Projek adalah folder biasa di penyimpanan internal. Tidak ada database: daftar projek dibaca
 * ulang dari `kodenusa.json` setiap kali, sehingga folder bisa disalin keluar-masuk tanpa migrasi.
 */
class ProjectStore(private val root: File) {

    fun list(): List<ProjectSummary> = root.listFiles()
        .orEmpty()
        .filter { it.isDirectory && File(it, KodenusaProject.FILE_NAME).isFile }
        .mapNotNull { dir ->
            val loaded = ProjectLoader.load(dir)
            val project = loaded.project ?: return@mapNotNull null
            ProjectSummary(
                dir = dir,
                name = project.name,
                applicationId = project.applicationId,
                lastOpened = dir.lastModified(),
                buildable = loaded.isBuildable,
            )
        }
        .sortedByDescending { it.lastOpened }

    fun load(dir: File): LoadedProject = ProjectLoader.load(dir)

    fun uniqueFolder(name: String): File {
        val base = name.lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .ifBlank { "projek" }
        var candidate = File(root, base)
        var index = 2
        while (candidate.exists()) {
            candidate = File(root, "$base-$index")
            index += 1
        }
        return candidate
    }

    fun delete(dir: File): Boolean {
        val target = dir.canonicalFile
        return if (target.path.startsWith(root.canonicalFile.path + File.separator)) {
            target.deleteRecursively()
        } else {
            false
        }
    }

    /** Berkas yang boleh dibuka editor: sumber Java, resource XML, dan manifest. */
    fun editableFiles(dir: File): List<File> = buildList {
        val manifest = File(dir, "src/main/AndroidManifest.xml")
        if (manifest.isFile) add(manifest)
        addAll(walk(File(dir, "src/main/java"), ".java"))
        addAll(walk(File(dir, "src/main/res"), ".xml"))
    }.sortedBy { it.relativeTo(dir).invariantSeparatorsPath }

    private fun walk(start: File, extension: String): List<File> {
        if (!start.isDirectory) return emptyList()
        return start.walkTopDown()
            .filter { it.isFile && it.name.endsWith(extension) }
            .toList()
    }

    fun issuesText(loaded: LoadedProject): String = loaded.issues
        .filter { it.severity == Severity.ERROR }
        .joinToString("\n") { it.fallbackMessage }
}
