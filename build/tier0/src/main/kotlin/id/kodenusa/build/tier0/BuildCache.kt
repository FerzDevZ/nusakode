package id.kodenusa.build.tier0

import id.kodenusa.core.build.BuildStep
import java.io.File
import java.security.MessageDigest

/**
 * Cache build berbasis isi file (bukan timestamp) supaya hasilnya sama di perangkat mana pun
 * dan aman ketika user memindahkan folder projek.
 */
class BuildCache(private val stateFile: File) {

    private val entries = LinkedHashMap<String, String>()

    init {
        stateFile.takeIf { it.isFile }?.let { file ->
            file.readLines()
                .mapNotNull { line -> line.split('=', limit = 2).takeIf { it.size == 2 } }
                .forEach { (step, value) -> entries[step] = value }
        }
    }

    fun isFresh(step: BuildStep, fingerprint: String, output: File?): Boolean {
        if (entries[step.name] != fingerprint) return false
        if (output == null) return true
        return output.exists()
    }

    fun record(step: BuildStep, fingerprint: String) {
        entries[step.name] = fingerprint
    }

    fun save() {
        stateFile.parentFile?.mkdirs()
        val tmp = File(stateFile.parentFile, "${stateFile.name}.tmp")
        tmp.writeText(entries.entries.joinToString("\n") { "${it.key}=${it.value}" })
        if (!tmp.renameTo(stateFile)) {
            stateFile.delete()
            tmp.renameTo(stateFile)
        }
    }

    fun clear() {
        entries.clear()
        stateFile.delete()
    }

    fun snapshot(): Map<String, String> = entries.toMap()
}

object Fingerprint {

    fun of(inputs: List<File>, salt: String = ""): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt.toByteArray())
        val files = inputs.filter { it.exists() }.flatMap { flatten(it) }.sortedBy { it.path }
        for (file in files) {
            digest.update(file.path.toByteArray())
            digest.update(file.length().toString().toByteArray())
            file.inputStream().use { stream -> updateFromStream(digest, stream) }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun updateFromStream(digest: MessageDigest, stream: java.io.InputStream) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = stream.read(buffer)
            if (read <= 0) return
            digest.update(buffer, 0, read)
        }
    }

    private fun flatten(file: File): List<File> {
        if (!file.isDirectory) return listOf(file)
        val out = mutableListOf<File>()
        val queue = ArrayDeque(listOf(file))
        while (queue.isNotEmpty()) {
            val dir = queue.removeFirst()
            dir.listFiles()?.forEach { child ->
                if (child.isDirectory) queue.add(child) else out.add(child)
            }
        }
        return out
    }
}
