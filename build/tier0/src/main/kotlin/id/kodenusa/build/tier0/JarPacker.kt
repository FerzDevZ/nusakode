package id.kodenusa.build.tier0

import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * d8 tidak menerima folder sebagai input, hanya arsip atau file .class. Jadi hasil ECJ dibungkus
 * dulu menjadi classes.jar — dilakukan di dalam proses supaya tidak butuh binary `jar` tambahan.
 */
class JarPacker {

    fun pack(classesDir: File, output: File) {
        check(classesDir.isDirectory) { "Folder classes tidak ada: ${classesDir.path}" }
        val files = flatten(classesDir).filter { it.isFile }
        check(files.isNotEmpty()) { "Tidak ada file .class untuk dijadikan dex" }

        output.parentFile?.mkdirs()
        val temp = File(output.parentFile, "${output.name}.tmp")
        ZipOutputStream(FileOutputStream(temp).buffered()).use { zip ->
            for (file in files) {
                val path = file.relativeTo(classesDir).invariantSeparatorsPath
                zip.putNextEntry(ZipEntry(path).apply { time = 0 })
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        if (!temp.renameTo(output)) {
            output.delete()
            check(temp.renameTo(output)) { "Tidak bisa menulis ${output.path}" }
        }
    }

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
