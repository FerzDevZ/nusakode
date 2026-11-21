package id.kodenusa.build.tier0

import id.kodenusa.core.build.BuildStep
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class BuildCacheTest {

    @TempDir
    lateinit var root: File

    @Test
    fun `fingerprint berubah ketika isi file berubah`() {
        val file = File(root, "A.java").apply { writeText("class A {}") }
        val first = Fingerprint.of(listOf(file))
        file.writeText("class A { int x; }")
        val second = Fingerprint.of(listOf(file))

        assertTrue(first != second)
    }

    @Test
    fun `fingerprint sama meski lokasi berbeda dengan isi sama`() {
        val a = File(root, "a/A.java").apply { parentFile.mkdirs(); writeText("class A {}") }
        val b = File(root, "b/B.java").apply { parentFile.mkdirs(); writeText("class B {}") }
        assertTrue(Fingerprint.of(listOf(a)) != Fingerprint.of(listOf(b)))
    }

    @Test
    fun `salt mengubah hasil`() {
        val file = File(root, "A.java").apply { writeText("class A {}") }
        assertTrue(Fingerprint.of(listOf(file), "11") != Fingerprint.of(listOf(file), "17"))
    }

    @Test
    fun `langkah dianggap basi bila output hilang`() {
        val state = File(root, "cache")
        val cache = BuildCache(state)
        val output = File(root, "classes").apply { mkdirs() }
        val fingerprint = Fingerprint.of(emptyList(), "x")

        cache.record(BuildStep.JAVAC, fingerprint)
        assertTrue(cache.isFresh(BuildStep.JAVAC, fingerprint, output))

        output.deleteRecursively()
        assertFalse(cache.isFresh(BuildStep.JAVAC, fingerprint, output))
    }

    @Test
    fun `cache bertahan setelah disimpan dan dibuka ulang`() {
        val state = File(root, "cache")
        BuildCache(state).apply {
            record(BuildStep.D8, "abc")
            save()
        }

        val loaded = BuildCache(state)
        assertEquals("abc", loaded.snapshot()["D8"])
    }

    @Test
    fun `cache kosong tidak membuat langkah dianggap segar`() {
        val cache = BuildCache(File(root, "cache-baru"))
        assertFalse(cache.isFresh(BuildStep.D8, "abc", File(root, "dex")))
    }
}
