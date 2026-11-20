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
 * - **aapt2 diambil dari `nativeLibraryDir`, bukan dari `assets`.** Sejak Android 10 ada kebijakan
 *   W^X: berkas yang bisa ditulis aplikasi tidak boleh dieksekusi, jadi `aapt2` dari `filesDir`
 *   ditolak `error=13 Permission denied` (dibuktikan di API 34, lihat ADR-021). Biner native yang
 *   dikemas sebagai `libaapt2.so` diekstrak Android ke `nativeLibraryDir` yang boleh dieksekusi di
 *   semua versi Android. Jar tetap lewat `app_process` karena `/system/bin/app_process` adalah
 *   biner sistem yang selalu boleh dijalankan.
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
        val available = manifest.files.map { it.name }.toSet()
        val missing = required.filterNot { it in available }

        // Biner native tidak datang dari aset; Android mengekstraknya sendiri saat pemasangan.
        val binerAapt2 = File(context.applicationInfo.nativeLibraryDir, NATIVE_AAPT2)
        if (!binerAapt2.isFile) {
            return Status.Missing(names = listOf(NATIVE_AAPT2), abi = abi, bundled = false)
        }
        if (missing.isNotEmpty()) {
            return Status.Missing(names = missing, abi = abi, bundled = true)
        }

        val root = File(context.filesDir, "toolchain/$abi")
        if (root.exists() && !root.deleteRecursively()) {
            return Status.Broken("toolchain", "Folder tool lama tidak bisa dihapus")
        }
        root.mkdirs()

        // ART 10+ menolak memuat dex dari berkas yang bisa ditulis (ADR-021), jadi jar ditandai
        // read-only setelah diverifikasi. Tanpa ini apksigner mati dengan SecurityException
        // "Writable dex file is not allowed" di HP Android 10 ke atas.
        val expected = manifest.files.associateBy { it.name }
        for (name in required) {
            val target = File(root, name)
            target.parentFile?.mkdirs()
            context.assets.open("toolchain/shared/$name").use { input ->
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
            if (!target.setReadOnly()) {
                return Status.Broken(name, "Tidak bisa menjadikan berkas hanya-baca")
            }
        }

        // Di Android tidak ada JVM: jar yang sudah di-dex dijalankan lewat app_process (ART).
        val kunci = runCatching { id.kodenusa.build.tier0.DebugKeystore.ensure(keystoreFile()) }
            .getOrElse { error ->
                return Status.Broken("debug.keystore", error.message ?: "Gagal membuat kunci tanda tangan")
            }
        val toolchain = id.kodenusa.build.tier0.Toolchain.fromRoot(
            root,
            id.kodenusa.build.tier0.AppProcessLauncher(sdkInt = Build.VERSION.SDK_INT),
        ).copy(aapt2 = binerAapt2, debugKeystore = kunci)
        val stillMissing = toolchain.missing()
        return if (stillMissing.isEmpty()) {
            Status.Ready(toolchain)
        } else {
            Status.Missing(names = stillMissing, abi = abi, bundled = true)
        }
    }

    /** Keystore per pemasangan, dibuat sekali lalu dipakai ulang lintas instal toolchain. */
    fun keystoreFile(): File = File(context.filesDir, "keys/debug.keystore")

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
        /** Nama berkas biner di `nativeLibraryDir`; aset aslinya `aapt2` dari build-tools. */
        const val NATIVE_AAPT2 = "libaapt2.so"

        /**
         * Berkas yang masih datang dari `assets/toolchain/shared`. `aapt2` tidak ada di sini karena
         * diambil dari `nativeLibraryDir`, dan `debug.keystore` dibuat di perangkat (ADR-019).
         */
        val REQUIRED = listOf(
            "ecj.jar",
            "dx.jar",
            "apksigner.jar",
            "android.jar",
        )
    }
}
