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
@Suppress("TooManyFunctions")
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
     * Memindahkan berkas atau folder ke folder lain di dalam projek yang sama.
     *
     * Dua penolakan penting: folder tidak boleh dipindah ke dalam dirinya sendiri (hasilnya folder
     * yang hilang beserta isinya), dan pemindahan tidak boleh menimpa berkas yang sudah ada di tujuan.
     */
    fun move(root: File, relativePath: String, tujuanRelative: String): Hasil {
        val sumber = resolve(root, relativePath)
            ?: return Hasil.Gagal("Berkas yang ingin dipindahkan tidak ditemukan.")
        if (sumber.canonicalPath == root.canonicalPath) {
            return Hasil.Gagal("Folder projek tidak bisa dipindahkan.")
        }
        val tujuanAman = if (tujuanRelative.isBlank()) "" else safeRelative(root, tujuanRelative)
        if (tujuanAman == null) return Hasil.Gagal("Folder tujuan tidak sah.")
        val tujuan = if (tujuanAman.isEmpty()) root else File(root, tujuanAman)
        if (!tujuan.isDirectory) return Hasil.Gagal("Folder tujuan tidak ditemukan.")

        val sumberPath = sumber.canonicalPath
        val tujuanPath = tujuan.canonicalPath
        if (tujuanPath == sumberPath) return Hasil.Gagal("Berkas sudah ada di folder itu.")
        if (sumber.isDirectory && tujuanPath.startsWith(sumberPath + File.separator)) {
            return Hasil.Gagal("Folder tidak bisa dipindah ke dalam dirinya sendiri.")
        }
        if (sumber.parentFile?.canonicalPath == tujuanPath) {
            return Hasil.Gagal("Berkas sudah ada di folder itu.")
        }

        val target = File(tujuan, sumber.name)
        if (target.exists()) return Hasil.Gagal("Di folder tujuan sudah ada \"${sumber.name}\".")
        return if (sumber.renameTo(target)) {
            Hasil.Sukses(target)
        } else {
            Hasil.Gagal("Berkas tidak bisa dipindahkan. Tutup dulu tab yang memakainya.")
        }
    }

    /** Menyalin berkas atau folder ke folder yang sama dengan nama bersufiks "-salinan". */
    fun duplicate(root: File, relativePath: String): Hasil {
        val sumber = resolve(root, relativePath)
            ?: return Hasil.Gagal("Berkas yang ingin diduplikasi tidak ditemukan.")
        if (sumber.canonicalPath == root.canonicalPath) {
            return Hasil.Gagal("Folder projek tidak bisa diduplikasi.")
        }
        val induk = sumber.parentFile ?: return Hasil.Gagal("Folder induk tidak ditemukan.")
        val target = unik(induk, sumber.name)
        return runCatching {
            if (sumber.isDirectory) sumber.copyRecursively(target) else sumber.copyTo(target)
            Hasil.Sukses(target)
        }.getOrElse { Hasil.Gagal("Berkas tidak bisa diduplikasi: ${it.message ?: "sebab tidak diketahui"}") }
    }

    /** Nama yang belum dipakai di [folder]: "gaya.css" → "gaya-salinan.css" → "gaya-salinan-2.css". */
    private fun unik(folder: File, nama: String): File {
        val titik = nama.lastIndexOf('.')
        val pokok = if (titik > 0) nama.substring(0, titik) else nama
        val akhiran = if (titik > 0) nama.substring(titik) else ""
        var calon = File(folder, "$pokok-salinan$akhiran")
        var nomor = 2
        while (calon.exists()) {
            calon = File(folder, "$pokok-salinan-$nomor$akhiran")
            nomor++
        }
        return calon
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
        // Target dibangun dari akar yang sudah dikanonikkan. Kalau memakai `root` mentah, di
        // perangkat hasilnya berbeda: `/data/user/0/...` dan `/data/data/...` adalah alamat yang
        // sama untuk folder yang sama, sehingga perbandingan prefix gagal dan folder yang sah
        // (mis. `web`) ditolak sebagai "di luar projek".
        val rootCanon = runCatching { root.canonicalFile }.getOrDefault(root.absoluteFile)
        val rootPath = rootCanon.path
        val targetPath = runCatching { File(rootCanon, normalized).canonicalFile.path }
            .getOrElse { return null }
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
