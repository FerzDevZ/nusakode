package id.kodenusa.build.tier0

import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Langkah PACKAGE dikerjakan di dalam proses (bukan memanggil `zip`), supaya pipeline yang sama
 * jalan di host dan di Android tanpa binary tambahan.
 *
 * Hasil: base.apk (manifest biner + resources) + classes*.dex + assets.
 */
class ApkPackager {

    fun assemble(baseApk: File, dexDir: File, assetDirs: List<File>, output: File) {
        val dexFiles = dexDir.listFiles { file -> file.isFile && file.name.endsWith(".dex") }
            ?.sortedBy { it.name }
            .orEmpty()

        output.parentFile?.mkdirs()
        val temp = File(output.parentFile, "${output.name}.tmp")
        ZipOutputStream(FileOutputStream(temp).buffered()).use { zip ->
            val written = HashSet<String>()
            copyBaseApk(baseApk, zip, written)
            copyDex(dexFiles, zip, written)
            copyAssets(assetDirs, zip, written)
        }

        if (!temp.renameTo(output)) {
            output.delete()
            check(temp.renameTo(output)) { "Tidak bisa menulis ${output.path}" }
        }
    }

    /** Menyalin isi base.apk kecuali dex lama; dex baru ditambahkan setelahnya. */
    private fun copyBaseApk(baseApk: File, zip: ZipOutputStream, written: MutableSet<String>) {
        ZipInputStream(baseApk.inputStream().buffered()).use { input ->
            while (true) {
                val entry = input.nextEntry ?: break
                val name = entry.name
                if (isDexFile(name) || !written.add(name)) continue
                writeEntry(zip, name, if (entry.isDirectory) null else input)
            }
        }
    }

    private fun copyDex(dexFiles: List<File>, zip: ZipOutputStream, written: MutableSet<String>) {
        for (dex in dexFiles) {
            if (!written.add(dex.name)) continue
            dex.inputStream().buffered().use { writeEntry(zip, dex.name, it) }
        }
    }

    private fun copyAssets(assetDirs: List<File>, zip: ZipOutputStream, written: MutableSet<String>) {
        for ((path, file) in assetEntries(assetDirs)) {
            if (!written.add(path)) continue
            file.inputStream().buffered().use { writeEntry(zip, path, it) }
        }
    }

    private fun writeEntry(zip: ZipOutputStream, name: String, source: java.io.InputStream?) {
        // Waktu dipatok 0 supaya APK yang sama menghasilkan byte yang sama (build reproducible).
        zip.putNextEntry(ZipEntry(name).apply { time = 0 })
        source?.copyTo(zip)
        zip.closeEntry()
    }

    private fun assetEntries(assetDirs: List<File>): List<Pair<String, File>> =
        assetDirs.filter { it.isDirectory }.flatMap { dir ->
            val root = dir.parentFile ?: dir
            flatten(dir).map { file -> file.relativeTo(root).invariantSeparatorsPath to file }
        }.filter { (path, _) -> path.startsWith("assets/") }

    private fun isDexFile(name: String): Boolean = name.endsWith(".dex") && name.startsWith("classes")

    private fun flatten(dir: File): List<File> {
        val out = mutableListOf<File>()
        val queue = ArrayDeque(listOf(dir))
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            current.listFiles()?.forEach { child ->
                if (child.isDirectory) queue.add(child) else out.add(child)
            }
        }
        return out.sortedBy { it.path }
    }
}
