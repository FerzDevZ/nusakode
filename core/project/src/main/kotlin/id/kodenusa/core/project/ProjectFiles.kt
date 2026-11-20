package id.kodenusa.core.project

import java.io.File

/**
 * Operasi berkas di dalam folder projek: tambah, ganti nama, hapus.
 *
 * Aturan yang tidak boleh dilanggar:
 *
 * - **Tidak ada jalan keluar dari folder projek.** Setiap path dihitung ulang lewat `canonicalPath`
 *   lalu diperiksa masih di bawah folder projek. Tanpa ini, nama seperti `../../databases/x`
 *   membuat pengguna (atau berkas projek yang diimpor) bisa menghapus data aplikasi lain.
 * - **Tidak menimpa berkas yang sudah ada.** Ganti nama ke nama yang sudah dipakai adalah kesalahan
 *   yang dilaporkan, bukan penggantian diam-diam yang menghilangkan kode orang.
 * - **Nama divalidasi, bukan dibersihkan diam-diam.** Karakter terlarang ditolak dengan alasan yang
 *   bisa dibaca pengguna. Menyulap "aplikasi saya.java" menjadi "aplikasi_saya.java" membuat orang
 *   bingung mencari berkasnya.
 * - Semua kegagalan dikembalikan sebagai [Result.failure] berisi pesan bahasa Indonesia; tidak ada
 *   `runCatching` yang menelan sebabnya.
 */
object ProjectFiles {

    /** Panjang maksimum satu nama berkas; jauh di bawah batas filesystem (255 byte). */
    const val MAX_NAME = 120

    private val TERLARANG = setOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')

    /** Nama yang dipakai sistem berkas dan akan membingungkan bila dibuat pengguna. */
    private val DICADANG = setOf("con", "prn", "aux", "nul", ".", "..")

    sealed interface Hasil {
        data class Sukses(val file: File) : Hasil
        data class Gagal(val pesan: String) : Hasil
    }

    /** Membuat berkas kosong (atau dengan isi awal) di dalam folder [parentRelative]. */
    fun createFile(
        root: File,
        parentRelative: String,
        name: String,
        content: String = "",
    ): Hasil {
        val valid = validasiNama(name)
        if (valid != null) return Hasil.Gagal(valid)
        val parent = resolve(root, parentRelative) ?: return Hasil.Gagal("Folder tujuan tidak ada di dalam projek.")
        if (!parent.isDirectory) return Hasil.Gagal("Tujuan bukan folder.")
        val target = File(parent, name.trim())
        if (target.exists()) return Hasil.Gagal("Berkas \"${target.name}\" sudah ada. Pakai nama lain.")
        return runCatching {
            target.parentFile?.mkdirs()
            target.writeText(content)
            Hasil.Sukses(target)
        }.getOrElse { Hasil.Gagal("Berkas tidak bisa dibuat: ${it.message ?: "penyebab tidak diketahui"}") }
    }

    /** Membuat folder di dalam [parentRelative]. Tidak membuat folder bertingkat dari nama. */
    fun createFolder(root: File, parentRelative: String, name: String): Hasil {
        val valid = validasiNama(name)
        if (valid != null) return Hasil.Gagal(valid)
        val parent = resolve(root, parentRelative) ?: return Hasil.Gagal("Folder tujuan tidak ada di dalam projek.")
        if (!parent.isDirectory) return Hasil.Gagal("Tujuan bukan folder.")
        val target = File(parent, name.trim())
        if (target.exists()) return Hasil.Gagal("Folder \"${target.name}\" sudah ada. Pakai nama lain.")
        return if (target.mkdirs()) {
            Hasil.Sukses(target)
        } else {
            Hasil.Gagal("Folder tidak bisa dibuat. Periksa ruang penyimpanan.")
        }
    }

    /** Mengganti nama berkas atau folder tanpa memindahkannya ke folder lain. */
    fun rename(root: File, relativePath: String, newName: String): Hasil {
        val valid = validasiNama(newName)
        if (valid != null) return Hasil.Gagal(valid)
        val source = resolve(root, relativePath) ?: return Hasil.Gagal("Berkas yang ingin diganti namanya tidak ditemukan.")
        if (source.name == newName.trim()) return Hasil.Sukses(source)
        val target = File(source.parentFile ?: return Hasil.Gagal("Folder induk tidak ditemukan."), newName.trim())
        if (target.exists()) return Hasil.Gagal("Nama \"${target.name}\" sudah dipakai.")
        return if (source.renameTo(target)) {
            Hasil.Sukses(target)
        } else {
            Hasil.Gagal("Nama tidak bisa diubah. Berkas mungkin sedang dibuka.")
        }
    }

    /** Menghapus berkas atau folder beserta isinya. Folder projek itu sendiri tidak boleh dihapus. */
    fun delete(root: File, relativePath: String): Hasil {
        val target = resolve(root, relativePath) ?: return Hasil.Gagal("Berkas yang ingin dihapus tidak ditemukan.")
        if (target.canonicalPath == root.canonicalPath) {
            return Hasil.Gagal("Folder projek tidak bisa dihapus dari sini. Hapus lewat daftar projek.")
        }
        if (!target.exists()) return Hasil.Gagal("Berkas sudah tidak ada.")
        return if (target.deleteRecursively()) {
            Hasil.Sukses(target)
        } else {
            Hasil.Gagal("Berkas tidak bisa dihapus. Tutup dulu tab yang memakainya.")
        }
    }

    /**
     * Path relatif aman, atau `null` bila hasilnya keluar dari folder projek.
     * Dipakai UI sebelum memanggil operasi lain, dan oleh pengimpor zip.
     */
    fun safeRelative(root: File, relativePath: String): String? {
        val mentah = relativePath.replace('\\', '/')
        // Path absolut tidak pernah dianggap relatif: kalau dibiarkan, pemangkasan '/' membuat
        // "/data/data/lain" berubah menjadi folder di dalam projek — alamat yang salah tetapi
        // tampak sah.
        if (mentah.startsWith("/") || Regex("^[A-Za-z]:").containsMatchIn(mentah)) return null
        val normalized = mentah.trim().trim('/')
        if (normalized.isEmpty()) return null
        if (normalized.split('/').any { it == ".." }) return null
        val target = File(root, normalized)
        val rootPath = root.canonicalPath
        val targetPath = target.canonicalPath
        if (targetPath != rootPath && !targetPath.startsWith(rootPath + File.separator)) return null
        return normalized
    }

    /** Isi awal untuk berkas Java baru supaya hasilnya langsung bisa di-build. */
    fun javaStarter(packageName: String, className: String): String = buildString {
        if (packageName.isNotBlank()) {
            appendLine("package $packageName;")
            appendLine()
        }
        appendLine("public class $className {")
        appendLine("    // TODO: tulis kode di sini, lalu tekan Build APK.")
        appendLine("}")
    }

    /** Pesan kesalahan nama, atau `null` kalau nama boleh dipakai. */
    fun validasiNama(name: String): String? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return "Nama tidak boleh kosong."
        if (trimmed.length > MAX_NAME) return "Nama terlalu panjang (maksimal $MAX_NAME karakter)."
        if (trimmed.any { it in TERLARANG }) return "Nama tidak boleh memuat karakter / \\ : * ? \" < > |"
        if (trimmed.startsWith(".")) return "Nama tidak boleh dimulai dengan titik."
        if (trimmed.lowercase() in DICADANG) return "Nama \"$trimmed\" dipakai sistem. Pakai nama lain."
        return null
    }

    private fun resolve(root: File, relativePath: String): File? {
        val safe = if (relativePath.isBlank()) "" else safeRelative(root, relativePath) ?: return null
        val file = if (safe.isEmpty()) root else File(root, safe)
        return file.takeIf { it.exists() }
    }
}
