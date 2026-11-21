package id.kodenusa.build.tier0

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * Pengganti binary `zipalign`.
 *
 * Android mensyaratkan awal data setiap entri yang tidak dikompresi berada pada batas 4 byte, dan
 * pustaka native (`.so`) pada batas halaman 16 KB agar bisa dipetakan langsung oleh ART versi baru.
 * `zipalign` menambah panjang extra field pada local header; kelas ini melakukan hal yang sama murni
 * di Kotlin, sehingga tidak perlu binary tambahan di HP.
 *
 * Semua entri dibaca ulang (dekompresi lalu kompresi kembali bila perlu) karena ukuran payload dan
 * offset harus diketahui sebelum header ditulis.
 */
class ZipAligner {

    fun align(source: File, target: File) {
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, "${target.name}.tmp")

        ZipFile(source).use { zip ->
            val entries = zip.entries().toList().filter { !it.isDirectory }
            FileOutputStream(temp).use { output ->
                val written = writeEntries(zip, entries, output)
                val centralStart = output.channel.position()
                for (item in written) writeCentralEntry(output, item, ByteArray(0))
                writeEndOfCentral(output, written.size, centralStart, output.channel.position() - centralStart)
            }
        }

        if (!temp.renameTo(target)) {
            target.delete()
            check(temp.renameTo(target)) { "Tidak bisa menulis ${target.path}" }
        }
    }

    private fun writeEntries(
        zip: ZipFile,
        entries: List<ZipEntry>,
        output: FileOutputStream,
    ): List<WrittenEntry> {
        val written = ArrayList<WrittenEntry>(entries.size)
        for (entry in entries) {
            val data = zip.getInputStream(entry).use { it.readBytes() }
            val method = if (shouldStayStored(entry.name)) ZipEntry.STORED else ZipEntry.DEFLATED
            val payload = if (method == ZipEntry.STORED) data else deflate(data)
            val headerOffset = output.channel.position()
            val name = entry.name.toByteArray(Charsets.UTF_8)
            val crc = crcOf(data)
            val extra = paddingFor(
                start = headerOffset + LOCAL_HEADER_SIZE + name.size,
                alignment = alignmentFor(entry.name, method),
            )
            writeLocalHeader(output, name, extra, method, crc, payload.size, data.size)
            output.write(payload)
            written += WrittenEntry(name, method, crc, payload.size, data.size, headerOffset)
        }
        return written
    }

    private fun shouldStayStored(name: String): Boolean =
        name.endsWith(".so") || name.endsWith(".dex") || name == "resources.arsc"

    private fun alignmentFor(name: String, method: Int): Int = when {
        method != ZipEntry.STORED -> 1
        name.endsWith(".so") -> PAGE_ALIGNMENT
        else -> 4
    }

    /** Extra field berisi satu record padding supaya data mulai tepat pada batas alignment. */
    private fun paddingFor(start: Long, alignment: Int): ByteArray {
        if (alignment <= 1) return ByteArray(0)
        var padding = ((alignment - (start % alignment)) % alignment).toInt()
        if (padding == 0) return ByteArray(0)
        // Extra field harus kosong atau minimal 4 byte (id + panjang). Menambah 4 byte tidak
        // merusak alignment karena alignment selalu kelipatan 4.
        while (padding < 4) padding += 4
        val extra = ByteArray(padding)
        extra[0] = (PADDING_EXTRA_ID and 0xFF).toByte()
        extra[1] = ((PADDING_EXTRA_ID shr 8) and 0xFF).toByte()
        val length = padding - 4
        extra[2] = (length and 0xFF).toByte()
        extra[3] = ((length shr 8) and 0xFF).toByte()
        return extra
    }

    private fun writeLocalHeader(
        out: FileOutputStream,
        name: ByteArray,
        extra: ByteArray,
        method: Int,
        crc: Long,
        compressedSize: Int,
        size: Int,
    ) {
        val header = ByteArrayOutputStream()
        header.writeInt(0x04034b50)
        header.writeShort(LOCAL_VERSION)
        header.writeShort(0)
        header.writeShort(method)
        header.writeShort(DOS_TIME)
        header.writeShort(DOS_DATE)
        header.writeInt(crc.toInt())
        header.writeInt(compressedSize)
        header.writeInt(size)
        header.writeShort(name.size)
        header.writeShort(extra.size)
        out.write(header.toByteArray())
        out.write(name)
        if (extra.isNotEmpty()) out.write(extra)
    }

    private fun writeCentralEntry(out: FileOutputStream, item: WrittenEntry, extra: ByteArray) {
        val header = ByteArrayOutputStream()
        header.writeInt(0x02014b50)
        header.writeShort(CENTRAL_VERSION)
        header.writeShort(LOCAL_VERSION)
        header.writeShort(0)
        header.writeShort(item.method)
        header.writeShort(DOS_TIME)
        header.writeShort(DOS_DATE)
        header.writeInt(item.crc.toInt())
        header.writeInt(item.compressedSize)
        header.writeInt(item.size)
        header.writeShort(item.name.size)
        header.writeShort(extra.size)
        header.writeShort(0)
        header.writeShort(0)
        header.writeShort(0)
        header.writeInt(0)
        header.writeInt(item.headerOffset.toInt())
        out.write(header.toByteArray())
        out.write(item.name)
        if (extra.isNotEmpty()) out.write(extra)
    }

    private fun writeEndOfCentral(out: FileOutputStream, count: Int, centralStart: Long, centralSize: Long) {
        val tail = ByteArrayOutputStream()
        tail.writeInt(0x06054b50)
        tail.writeShort(0)
        tail.writeShort(0)
        tail.writeShort(count)
        tail.writeShort(count)
        tail.writeInt(centralSize.toInt())
        tail.writeInt(centralStart.toInt())
        tail.writeShort(0)
        out.write(tail.toByteArray())
    }

    private fun crcOf(data: ByteArray): Long = CRC32().apply { update(data) }.value

    private fun deflate(data: ByteArray): ByteArray {
        val buffer = ByteArrayOutputStream(data.size / 2 + 64)
        // ZIP menyimpan deflate mentah tanpa header zlib, jadi nowrap harus true.
        DeflaterOutputStream(buffer, Deflater(Deflater.BEST_SPEED, true)).use { it.write(data) }
        return buffer.toByteArray()
    }

    private fun ByteArrayOutputStream.writeInt(value: Int) {
        write(value and 0xFF)
        write((value shr 8) and 0xFF)
        write((value shr 16) and 0xFF)
        write((value shr 24) and 0xFF)
    }

    private fun ByteArrayOutputStream.writeShort(value: Int) {
        write(value and 0xFF)
        write((value shr 8) and 0xFF)
    }

    private data class WrittenEntry(
        val name: ByteArray,
        val method: Int,
        val crc: Long,
        val compressedSize: Int,
        val size: Int,
        val headerOffset: Long,
    )

    private companion object {
        private const val LOCAL_VERSION = 20
        private const val CENTRAL_VERSION = 20
        private const val LOCAL_HEADER_SIZE = 30
        private const val PADDING_EXTRA_ID = 0xD935
        private const val PAGE_ALIGNMENT = 16384
        // 1 Januari 1980 00:00:00, waktu tetap supaya hasil build dapat direproduksi.
        private const val DOS_TIME = 0
        private const val DOS_DATE = 0x0021
    }
}
