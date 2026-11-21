package id.kodenusa.build.tier0

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class ApkPackagerTest {

    @TempDir
    lateinit var root: File

    private fun zip(file: File, entries: Map<String, String>) {
        ZipOutputStream(file.outputStream()).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
    }

    @Test
    fun `dex dan assets digabung ke base apk`() {
        val base = File(root, "base.apk").also {
            zip(it, mapOf("AndroidManifest.xml" to "manifest", "resources.arsc" to "arsc"))
        }
        val dexDir = File(root, "dex").apply {
            mkdirs()
            File(this, "classes.dex").writeText("dex-bytes")
        }
        val assetsDir = File(root, "src/main/assets").apply {
            mkdirs()
            File(this, "data.json").writeText("{}")
        }

        val output = File(root, "out.apk")
        ApkPackager().assemble(base, dexDir, listOf(assetsDir), output)

        ZipFile(output).use { apk ->
            val names = apk.entries().toList().map { it.name }
            assertTrue(names.containsAll(listOf("AndroidManifest.xml", "resources.arsc", "classes.dex", "assets/data.json")))
            assertEquals("dex-bytes", apk.getInputStream(apk.getEntry("classes.dex")).bufferedReader().readText())
        }
    }

    @Test
    fun `classes dex lama di base apk tidak terduplikasi`() {
        val base = File(root, "base.apk").also {
            zip(it, mapOf("AndroidManifest.xml" to "m", "classes.dex" to "lama"))
        }
        val dexDir = File(root, "dex").apply {
            mkdirs()
            File(this, "classes.dex").writeText("baru")
        }
        val output = File(root, "out.apk")

        ApkPackager().assemble(base, dexDir, emptyList(), output)

        ZipFile(output).use { apk ->
            assertEquals(1, apk.entries().toList().count { it.name == "classes.dex" })
            assertEquals("baru", apk.getInputStream(apk.getEntry("classes.dex")).bufferedReader().readText())
        }
    }

    @Test
    fun `jar packer menyimpan struktur folder class`() {
        val classes = File(root, "classes/id/kodenusa").apply { mkdirs() }
        File(classes, "R.class").writeText("r")
        File(root, "classes/MainActivity.class").writeText("m")
        val jar = File(root, "classes.jar")

        JarPacker().pack(File(root, "classes"), jar)

        ZipFile(jar).use { zip ->
            val names = zip.entries().toList().map { it.name }
            assertTrue(names.containsAll(listOf("id/kodenusa/R.class", "MainActivity.class")))
        }
    }
}
