package id.kodenusa.app

import java.io.File

/**
 * Hitung dan bersihkan hasil build yang menumpuk.
 *
 * Yang dihapus **hanya** folder `build/` tepat di bawah akar projek. Berkas sumber tidak pernah
 * disentuh, dan jalur di luar projek ditolak — kesalahan di sini akan menghapus pekerjaan orang.
 */
object PenyimpananProjek {

    const val FOLDER_HASIL = "build"

    fun folderBuild(akar: File): File = File(akar, FOLDER_HASIL)

    /** Ukuran folder hasil build dalam byte (0 kalau belum pernah build). */
    fun ukuranBuild(akar: File): Long {
        val folder = folderBuild(akar)
        if (!folder.isDirectory) return 0L
        return folder.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    /** Ukuran seluruh projek dalam byte, tanpa folder hasil build. */
    fun ukuranSumber(akar: File): Long {
        if (!akar.isDirectory) return 0L
        val hasil = folderBuild(akar).absolutePath
        return akar.walkTopDown()
            .filter { it.isFile && !it.absolutePath.startsWith(hasil) }
            .sumOf { it.length() }
    }

    /**
     * Menghapus folder hasil build dan mengembalikan jumlah byte yang dibebaskan.
     * Mengembalikan 0 bila tidak ada yang perlu dihapus.
     */
    fun bersihkanBuild(akar: File): Long {
        val folder = folderBuild(akar)
        if (!folder.isDirectory) return 0L
        val sebelum = ukuranBuild(akar)
        // Ulangi sampai kosong: sebagian ROM mengunci berkas sebentar setelah build, dan
        // kegagalan menghapus tidak boleh membuat aplikasi berhenti.
        folder.deleteRecursively()
        return sebelum - ukuranBuild(akar)
    }

    fun ringkasByte(byte: Long): String = when {
        byte <= 0L -> "0 B"
        byte < 1024L -> "$byte B"
        byte < 1024L * 1024L -> "${byte / 1024} KB"
        byte < 1024L * 1024L * 1024L -> String.format(java.util.Locale("id", "ID"), "%.1f MB", byte / 1024.0 / 1024.0)
        else -> String.format(java.util.Locale("id", "ID"), "%.1f GB", byte / 1024.0 / 1024.0 / 1024.0)
    }
}
