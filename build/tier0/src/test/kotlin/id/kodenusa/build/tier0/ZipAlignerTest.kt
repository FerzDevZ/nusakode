package id.kodenusa.build.tier0

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ZipAlignerTest {

    @Test
    fun `isi entri tidak berubah setelah alignment`() {
        val dir = File(System.getProperty("java.io.tmpdir"), "kodenusa-align-${System.nanoTime()}")
        dir.mkdirs()
        val source = File(dir, "masuk.zip")
        val teks = "halo dunia ".repeat(200)
        val biner = ByteArray(500) { (it % 251).toByte() }

        ZipOutputStream(source.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("assets/teks.txt")).apply { zip.write(teks.toByteArray()) }
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("classes.dex")).apply { zip.write(biner) }
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("resources.arsc")).apply { zip.write(biner) }
            zip.closeEntry()
        }

        val target = File(dir, "keluar.apk")
        ZipAligner().align(source, target)

        ZipFile(target).use { zip ->
            assertArrayEquals(biner, zip.getInputStream(zip.getEntry("classes.dex")).readBytes())
            assertArrayEquals(biner, zip.getInputStream(zip.getEntry("resources.arsc")).readBytes())
            assertEquals(
                teks,
                zip.getInputStream(zip.getEntry("assets/teks.txt")).bufferedReader().readText(),
            )
            assertEquals(ZipEntry.STORED, zip.getEntry("classes.dex").method)
            assertEquals(ZipEntry.DEFLATED, zip.getEntry("assets/teks.txt").method)
        }
        dir.deleteRecursively()
    }

    @Test
    fun `data yang tidak dikompresi jatuh pada batas 4 byte`() {
        val dir = File(System.getProperty("java.io.tmpdir"), "kodenusa-align-${System.nanoTime()}")
        dir.mkdirs()
        val source = File(dir, "masuk.zip")
        ZipOutputStream(source.outputStream()).use { zip ->
            listOf("resources.arsc", "classes.dex", "assets/a.txt").forEach { name ->
                zip.putNextEntry(ZipEntry(name)).apply { zip.write(ByteArray(100)) }
                zip.closeEntry()
            }
        }
        val target = File(dir, "keluar.apk")
        ZipAligner().align(source, target)

        val offsets = dataOffsets(target)
        assertEquals(0, offsets.getValue("resources.arsc") % 4)
        assertEquals(0, offsets.getValue("classes.dex") % 4)

        assertTrue(target.length() > 0)
        dir.deleteRecursively()
    }

    @Test
    fun `pustaka native disejajarkan pada halaman 16 kb`() {
        val dir = File(System.getProperty("java.io.tmpdir"), "kodenusa-align-${System.nanoTime()}")
        dir.mkdirs()
        val source = File(dir, "masuk.zip")
        ZipOutputStream(source.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("lib/arm64-v8a/libcontoh.so")).apply { zip.write(ByteArray(64)) }
            zip.closeEntry()
        }
        val target = File(dir, "keluar.apk")
        ZipAligner().align(source, target)

        assertEquals(0, dataOffsets(target).getValue("lib/arm64-v8a/libcontoh.so") % 16384)
        dir.deleteRecursively()
    }

    /** Membaca local file header langsung untuk mengetahui offset awal data tiap entri. */
    private fun dataOffsets(apk: File): Map<String, Long> {
        val bytes = apk.readBytes()
        val result = HashMap<String, Long>()
        var index = 0
        while (index + 30 <= bytes.size) {
            val signature = readInt(bytes, index)
            if (signature != 0x04034b50) break
            val nameLength = readShort(bytes, index + 26)
            val extraLength = readShort(bytes, index + 28)
            val name = String(bytes, index + 30, nameLength, Charsets.UTF_8)
            val dataStart = index + 30 + nameLength + extraLength
            result[name] = dataStart.toLong()
            val compressedSize = readInt(bytes, index + 18)
            index = dataStart + compressedSize
        }
        return result
    }

    private fun readInt(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)

    private fun readShort(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)
}
