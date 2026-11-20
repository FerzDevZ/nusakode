package id.kodenusa.core.project

import id.kodenusa.core.build.Severity
import id.kodenusa.core.project.ProjectValidator.validate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ProjectValidatorTest {

    private fun valid() = KodenusaProject(
        name = "Kasir Warung",
        applicationId = "id.kodenusa.kasir",
        minSdk = 24,
        targetSdk = 35,
    )

    @Test
    fun `projek valid tidak menghasilkan masalah`() {
        assertTrue(validate(valid()).isEmpty())
    }

    @Test
    fun `application id harus berpola package android`() {
        listOf("Kasir", "id.kodenusa.", "com.1abc", "a").forEach { invalid ->
            val issues = validate(valid().copy(applicationId = invalid))
            assertTrue(
                issues.any { it.messageId == ProjectIssueIds.APP_ID_FORMAT },
                "applicationId '$invalid' seharusnya ditolak",
            )
        }
    }

    @Test
    fun `nama kosong dan terlalu panjang ditolak`() {
        assertTrue(validate(valid().copy(name = "  ")).any { it.messageId == ProjectIssueIds.NAME_EMPTY })
        assertTrue(validate(valid().copy(name = "x".repeat(51))).any { it.messageId == ProjectIssueIds.NAME_TOO_LONG })
    }

    @Test
    fun `target sdk tidak boleh lebih kecil dari min sdk`() {
        val issues = validate(valid().copy(minSdk = 26, targetSdk = 24))
        assertTrue(issues.any { it.messageId == ProjectIssueIds.TARGET_BELOW_MIN })
    }

    @Test
    fun `versi java di luar dukungan ditolak`() {
        assertTrue(validate(valid().copy(javaVersion = 21)).any { it.messageId == ProjectIssueIds.JAVA_VERSION_SUPPORTED })
    }

    @Test
    fun `format baru yang tidak dikenal ditolak`() {
        assertTrue(validate(valid().copy(format = 99)).any { it.messageId == ProjectIssueIds.UNSUPPORTED_FORMAT })
    }
}

class ProjectLoaderTest {

    @TempDir
    lateinit var root: File

    private fun scaffold(json: String): File {
        val project = File(root, "proj").apply { mkdirs() }
        File(project, KodenusaProject.FILE_NAME).writeText(json)
        File(project, "src/main")
            .apply { mkdirs() }
            .also { File(it, "AndroidManifest.xml").writeText("<manifest package=\"id.kodenusa.demo\"/>") }
        File(project, "src/main/java/id/kodenusa/demo").apply { mkdirs() }
            .also { File(it, "MainActivity.java").writeText("package id.kodenusa.demo; class MainActivity {}") }
        File(project, "src/main/res/values").apply { mkdirs() }
            .also { File(it, "strings.xml").writeText("<resources/>") }
        return project
    }

    @Test
    fun `projek lengkap menghasilkan konfigurasi build`() {
        val project = scaffold(
            """
            {"name":"Demo","applicationId":"id.kodenusa.demo","versionCode":2}
            """.trimIndent(),
        )

        val loaded = ProjectLoader.load(project)

        assertTrue(loaded.isBuildable)
        val config = requireNotNull(loaded.config)
        assertEquals(1, config.javaSources.size)
        assertEquals(1, config.resourceDirs.size)
        assertEquals(2, config.versionCode)
    }

    @Test
    fun `tanpa kodenusa json projek ditolak dengan pesan yang jelas`() {
        val loaded = ProjectLoader.load(root)
        assertFalse(loaded.isBuildable)
        assertTrue(loaded.issues.any { it.messageId == ProjectIssueIds.MISSING_JSON })
    }

    @Test
    fun `tanpa sumber java projek ditolak`() {
        val project = scaffold("""{"name":"Demo","applicationId":"id.kodenusa.demo"}""")
        File(project, "src/main/java").deleteRecursively()

        val loaded = ProjectLoader.load(project)

        assertFalse(loaded.isBuildable)
        assertTrue(loaded.issues.any { it.messageId == ProjectIssueIds.NO_SOURCES })
    }

    @Test
    fun `library yang tidak ada dilaporkan sebagai error`() {
        val project = scaffold(
            """
            {"name":"Demo","applicationId":"id.kodenusa.demo","dependencies":{"files":["libs/hilang.jar"]}}
            """.trimIndent(),
        )

        val loaded = ProjectLoader.load(project)

        assertFalse(loaded.isBuildable)
        assertTrue(loaded.issues.any { it.messageId == ProjectIssueIds.MISSING_DEPENDENCY })
    }

    @Test
    fun `projek dengan build gradle ditandai butuh cloud`() {
        val project = scaffold("""{"name":"Demo","applicationId":"id.kodenusa.demo"}""")
        File(project, "build.gradle.kts").writeText("plugins {}")

        val loaded = ProjectLoader.load(project)

        assertEquals(Severity.INFO, loaded.issues.single { it.messageId == ProjectIssueIds.GRADLE_DETECTED }.severity)
        assertTrue(requireNotNull(loaded.validation).requiresCloudEngine)
    }
}
