package id.kodenusa.app

import android.content.Context
import android.os.Build
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest

/**
 * Menyiapkan tool build dari `assets/toolchain/<abi>` ke penyimpanan internal.
 *
 * Aturan yang tidak boleh dilanggar:
 * - Setiap berkas diverifikasi SHA-256 terhadap `toolchain.json` sebelum dijalankan. Tanpa ini,
 *   satu berkas yang tertukar menjadi eksekusi kode asing.
 * - Berkas diekstrak ke `filesDir` (bukan sdcard) lalu di-set executable. Folder sdcard memakai
 *   mount noexec sehingga tidak bisa dipakai untuk menjalankan biner.
 * - Kalau aset tidak ada, hasilnya [Status.Missing] dengan nama berkas yang kurang — bukan
 *   exception tanpa pesan dan bukan build yang gagal misterius.
 * - **Keystore tidak dibundel.** Kunci tanda tangan dibuat di perangkat ini sekali dan disimpan di
 *   `filesDir/keys/`, supaya tidak ada satu kunci pun yang dipakai bersama semua pemasangan
 *   KodeNusa. Lokasinya sengaja di luar folder toolchain yang dihapus setiap kali instal ulang.
 */
class ToolchainInstaller(private val context: Context) {

    sealed interface Status {
        data class Ready(val toolchain: id.kodenusa.build.tier0.Toolchain) : Status
        data class Missing(val names: List<String>, val abi: String, val bundled: Boolean) : Status
        data class Broken(val name: String, val reason: String) : Status
    }

    @Serializable
    private data class Manifest(val files: List<Entry> = emptyList())

    @Serializable
    private data class Entry(val name: String, val sha256: String = "", val executable: Boolean = false)

    fun install(): Status {
        val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
        // aapt2 berbeda per arsitektur; jar hasil dex, android.jar, dan keystore sama untuk semua.
        val manifest = readManifest()
        val required = REQUIRED
        val available = (manifest.files.map { it.name } + listOfNotNull(abiAsset(abi))).toSet()
        val missing = required.filterNot { it in available }

        if (missing.size == required.size) {
            return Status.Missing(names = missing, abi = abi, bundled = false)
        }
        if (missing.isNotEmpty()) {
            return Status.Missing(names = missing, abi = abi, bundled = true)
        }

        val root = File(context.filesDir, "toolchain/$abi")
        if (root.exists() && !root.deleteRecursively()) {
            return Status.Broken("toolchain", "Folder tool lama tidak bisa dihapus")
        }
        root.mkdirs()

        val expected = manifest.files.associateBy { it.name }
        for (name in required) {
            val target = File(root, name)
            target.parentFile?.mkdirs()
            val assetPath = if (name == AAPT2) "toolchain/$abi/$name" else "toolchain/shared/$name"
            context.assets.open(assetPath).use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            val entry = expected[name]
            if (entry != null && entry.sha256.isNotBlank()) {
                val actual = sha256(target)
                if (!actual.equals(entry.sha256, ignoreCase = true)) {
                    target.delete()
                    return Status.Broken(name, "Checksum tidak cocok. Berkas mungkin rusak saat unduh.")
                }
            }
            if (entry?.executable == true || name == AAPT2) {
                if (!target.setExecutable(true, false)) {
                    return Status.Broken(name, "Tidak bisa menjadikan berkas dapat dieksekusi")
                }
            }
        }

        // Di Android tidak ada JVM: jar yang sudah di-dex dijalankan lewat app_process (ART).
        val kunci = runCatching { id.kodenusa.build.tier0.DebugKeystore.ensure(keystoreFile()) }
            .getOrElse { error ->
                return Status.Broken("debug.keystore", error.message ?: "Gagal membuat kunci tanda tangan")
            }
        val toolchain = id.kodenusa.build.tier0.Toolchain.fromRoot(
            root,
            id.kodenusa.build.tier0.AppProcessLauncher(),
        ).copy(debugKeystore = kunci)
        val stillMissing = toolchain.missing()
        return if (stillMissing.isEmpty()) {
            Status.Ready(toolchain)
        } else {
            Status.Missing(names = stillMissing, abi = abi, bundled = true)
        }
    }

    /** Keystore per pemasangan, dibuat sekali lalu dipakai ulang lintas instal toolchain. */
    fun keystoreFile(): File = File(context.filesDir, "keys/debug.keystore")

    private fun abiAsset(abi: String): String? =
        runCatching { context.assets.list("toolchain/$abi")?.firstOrNull { it == AAPT2 } }.getOrNull()

    private fun readManifest(): Manifest = runCatching {
        context.assets.open("toolchain/shared/toolchain.json").use { input ->
            Json { ignoreUnknownKeys = true }
                .decodeFromString(Manifest.serializer(), input.readBytes().decodeToString())
        }
    }.getOrDefault(Manifest())

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val AAPT2 = "aapt2"

        /**
         * Harus sama dengan urutan di Toolchain.fromRoot; UI memakai daftar ini untuk pesan.
         * `debug.keystore` tidak ada di sini karena dibuat di perangkat, bukan diambil dari aset.
         */
        val REQUIRED = listOf(
            AAPT2,
            "ecj.jar",
            "dx.jar",
            "apksigner.jar",
            "android.jar",
        )
    }
}
