package id.kodenusa.core.project

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ProjectTreeTest {

    @TempDir
    lateinit var root: File

    private fun tulis(path: String): File = File(root, path).apply {
        parentFile?.mkdirs()
        writeText("// isi")
    }

    @Test
    fun `folder tampil sebelum berkas dan keduanya berurutan`() {
        tulis("kodenusa.json")
        tulis("src/main/java/app/MainActivity.java")
        tulis("src/main/res/values/strings.xml")

        val nodes = ProjectTree.list(root)
        assertEquals("src", nodes.first().name)
        assertTrue(nodes.first().directory)

        val dalamSrc = nodes.filter { it.relativePath.startsWith("src/") }
        val pertamaBerkas = dalamSrc.indexOfFirst { !it.directory }
        assertTrue(pertamaBerkas > dalamSrc.indexOfFirst { it.directory })
    }

    @Test
    fun `folder hasil build dan ekstensi biner disembunyikan`() {
        tulis("src/main/java/app/MainActivity.java")
        tulis("build/tier0/app-debug.apk")
        tulis("src/main/java/app/MainActivity.class")
        tulis(".gradle/cache.bin")

        val jalur = ProjectTree.list(root).map { it.relativePath }
        assertFalse(jalur.any { it.startsWith("build/") })
        assertFalse(jalur.any { it.startsWith(".gradle/") })
        assertFalse(jalur.any { it.endsWith(".class") })
        assertTrue(jalur.any { it.endsWith("MainActivity.java") })
    }

    @Test
    fun `kedalaman bertambah satu per folder`() {
        tulis("src/main/java/app/MainActivity.java")
        val nodes = ProjectTree.list(root)
        assertEquals(0, nodes.single { it.name == "src" }.depth)
        assertEquals(1, nodes.single { it.name == "main" }.depth)
        assertEquals(2, nodes.single { it.name == "java" }.depth)
        assertEquals(3, nodes.single { it.name == "app" }.depth)
        assertEquals(4, nodes.single { it.name == "MainActivity.java" }.depth)
    }

    @Test
    fun `folder yang ditutup menyembunyikan seluruh isinya`() {
        tulis("src/main/java/app/MainActivity.java")
        tulis("src/main/res/values/strings.xml")
        tulis("kodenusa.json")

        val semua = ProjectTree.list(root)
        val terlihat = ProjectTree.visible(semua, setOf("src"))

        assertTrue(terlihat.any { it.relativePath == "src" })
        assertTrue(terlihat.any { it.name == "kodenusa.json" })
        assertFalse(terlihat.any { it.relativePath == "src/main" })
        assertFalse(terlihat.any { it.name == "MainActivity.java" })
    }

    @Test
    fun `membuka kembali folder memunculkan isinya`() {
        tulis("src/main/java/app/MainActivity.java")
        val semua = ProjectTree.list(root)
        val terlihat = ProjectTree.visible(semua, emptySet())
        assertTrue(terlihat.any { it.name == "MainActivity.java" })
    }
}
