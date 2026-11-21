package id.kodenusa.build.tier0

import id.kodenusa.core.build.BuildStep
import id.kodenusa.core.build.Tier0Config
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class Tier0PlannerTest {

    @TempDir
    lateinit var root: File

    private fun tools(): Pair<Toolchain, File> {
        val toolsDir = File(root, "tools").apply { mkdirs() }
        listOf("aapt2", "ecj.jar", "dx.jar", "apksigner.jar", "android.jar", "debug.keystore")
            .forEach { File(toolsDir, it).writeText("x") }
        val toolchain = Toolchain.fromRoot(toolsDir, HostJavaLauncher(File(toolsDir, "java")))
        return toolchain to toolsDir
    }

    private fun project(withRes: Boolean = true): Tier0Config {
        val dir = File(root, "proj").apply { mkdirs() }
        val manifest = File(dir, "src/main/AndroidManifest.xml").apply {
            parentFile.mkdirs()
            writeText("<manifest package=\"id.kodenusa.demo\"/>")
        }
        val source = File(dir, "src/main/java/id/kodenusa/demo/MainActivity.java").apply {
            parentFile.mkdirs()
            writeText("package id.kodenusa.demo; public class MainActivity {}")
        }
        val res = File(dir, "src/main/res").apply { mkdirs() }
        return Tier0Config(
            projectId = "id.kodenusa.demo",
            projectDir = dir,
            appName = "Demo",
            applicationId = "id.kodenusa.demo",
            versionName = "1.0.0",
            versionCode = 3,
            minSdk = 24,
            targetSdk = 35,
            javaVersion = 11,
            javaSources = listOf(source),
            resourceDirs = if (withRes) listOf(res) else emptyList(),
            assetDirs = emptyList(),
            manifest = manifest,
        )
    }

    @Test
    fun `rencana berisi tujuh langkah dengan urutan tetap`() {
        val (toolchain, _) = tools()
        val steps = Tier0Planner(toolchain).plan(project()).map { it.step }

        assertEquals(
            listOf(
                BuildStep.AAPT_COMPILE,
                BuildStep.AAPT_LINK,
                BuildStep.JAVAC,
                BuildStep.D8,
                BuildStep.PACKAGE,
                BuildStep.ALIGN,
                BuildStep.SIGN,
            ),
            steps,
        )
    }

    @Test
    fun `langkah internal tidak memanggil binary`() {
        val (toolchain, _) = tools()
        val plan = Tier0Planner(toolchain).plan(project())

        // PACKAGE dan ALIGN dikerjakan di dalam proses: zip dan alignment murni Kotlin.
        val internal = setOf(BuildStep.PACKAGE, BuildStep.ALIGN).map { step -> plan.single { it.step == step } }
        internal.forEach { assertTrue(it.commands.isEmpty(), "${it.step} tidak boleh memanggil binary") }

        plan.filterNot { it.step in setOf(BuildStep.PACKAGE, BuildStep.ALIGN) }.forEach {
            assertTrue(it.commands.isNotEmpty(), "${it.step} harus punya perintah")
        }
    }

    @Test
    fun `aapt2 link membawa min sdk target sdk dan versi dari konfigurasi`() {
        val (toolchain, _) = tools()
        val link = Tier0Planner(toolchain).plan(project()).single { it.step == BuildStep.AAPT_LINK }
        val argv = link.commands.single().argv

        assertTrue(argv.containsAll(listOf("--min-sdk-version", "24", "--target-sdk-version", "35")))
        assertTrue(argv.containsAll(listOf("--version-code", "3", "--version-name", "1.0.0")))
    }

    @Test
    fun `perintah tidak pernah memakai shell atau metakarakter`() {
        val (toolchain, _) = tools()
        val plan = Tier0Planner(toolchain).plan(project())

        plan.flatMap { it.commands }.forEach { command ->
            assertFalse(command.argv.any { it == "sh" || it == "-c" }, "Tidak boleh memakai shell")
            command.argv.forEach { argument ->
                assertFalse(argument.contains("&&"), "Argumen mengandung &&: $argument")
                assertFalse(argument.contains(";"), "Argumen mengandung ;: $argument")
            }
        }
    }

    @Test
    fun `javac menyertakan R java hasil hitungan dan sourcepath gen`() {
        val (toolchain, _) = tools()
        val config = project()
        val javac = Tier0Planner(toolchain).plan(config).single { it.step == BuildStep.JAVAC }
        val argv = javac.commands.single().argv

        val expectedR = File(config.projectDir, "build/tier0/gen/id/kodenusa/demo/R.java").path
        assertTrue(argv.contains(expectedR), "R.java harus ikut dikompilasi: $argv")
        assertTrue(argv.contains("-sourcepath"))
        assertTrue(argv.contains(config.javaSources.single().path))
    }

    @Test
    fun `Android jar dipakai sebagai bootclasspath pada level bahasa 8`() {
        val (toolchain, toolsDir) = tools()
        val javac = Tier0Planner(toolchain).plan(project()).single { it.step == BuildStep.JAVAC }
        val argv = javac.commands.single().argv

        assertTrue(argv.contains("-bootclasspath"), "ECJ hanya menerima bootclasspath pada level < 9")
        assertEquals(
            File(toolsDir, "android.jar").path,
            argv[argv.indexOf("-bootclasspath") + 1],
        )
        assertEquals(listOf("-source", "8", "-target", "8"), argv.subList(argv.indexOf("-source"), argv.indexOf("-source") + 4))
    }

    @Test
    fun `dexer menerima arsip jar bukan folder`() {
        val (toolchain, _) = tools()
        val dex = Tier0Planner(toolchain).plan(project()).single { it.step == BuildStep.D8 }
        val argv = dex.commands.single().argv

        assertTrue(argv.last().endsWith("classes.jar"), "dx tidak menerima direktori: ${argv.last()}")
        assertTrue(argv.containsAll(listOf("--dex", "--min-sdk-version", "24")))
    }

    @Test
    fun `projek tanpa folder res melewati langkah kompilasi resource`() {
        val (toolchain, _) = tools()
        val plan = Tier0Planner(toolchain).plan(project(withRes = false))
        val compile = plan.single { it.step == BuildStep.AAPT_COMPILE }

        assertNotNull(compile.skipReason)
        assertTrue(compile.commands.isEmpty())
    }

    @Test
    fun `argumen signing tidak membocorkan password ke log dalam bentuk path`() {
        val (toolchain, _) = tools()
        val sign = Tier0Planner(toolchain).plan(project()).single { it.step == BuildStep.SIGN }
        val argv = sign.commands.single().argv

        assertTrue(argv.containsAll(listOf("--ks-pass", "pass:android")), "Password debug memang publik")
        assertTrue(argv.contains("--out"))
    }
}
