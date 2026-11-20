package id.kodenusa.app

import id.kodenusa.core.project.ProjectFiles
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Membungkus projek jadi satu berkas `.zip` dan membukanya kembali.
 *
 * Kenapa lewat zip, bukan folder langsung: Android 11+ melarang aplikasi menulis ke folder sembarang
 * tanpa izin pemilih berkas, sedangkan build Tier 0 butuh biner yang hanya boleh dieksekusi dari
 * folder aplikasi. Jadi alamat "pilih lokasi" yang jujur adalah: berkas projek bisa dipindah ke
 * mana saja (Unduhan, Google Drive, kartu SD) sebagai zip, dan dibuka kembali kapan saja.
 *
 * Aturan yang tidak boleh dilanggar:
 * - **Entri zip yang keluar folder ditolak** (`../`, path absolut). Zip buatan orang lain adalah
 *   masukan yang tidak dipercaya, dan satu entri `../../databases/x` cukup untuk menimpa data
 *   aplikasi lain.
 * - **Folder build tidak ikut diekspor.** Isinya hasil generate dan bisa puluhan MB; membawanya
 *   membuat zip lambat dan penuh sampah.
 * - Semua kegagalan dikembalikan sebagai [Result.failure] dengan pesan bahasa Indonesia.
 */
object ProjectArchive {

    /** Folder yang tidak perlu ikut: hasil build dan cache. */
    private val DILEWATI = setOf("build", ".gradle", ".git", "dex", "gen", "classes", ".tools")

    /** Mengekspor isi [projectDir] ke [target] (berkas zip yang sudah dipilih pengguna). */
    fun export(projectDir: File, target: File): Result<Int> = runCatching {
        var jumlah = 0
        ZipOutputStream(target.outputStream().buffered()).use { zip ->
            projectDir.walkTopDown()
                .onEnter { dir -> dir.name !in DILEWATI }
                .filter { it.isFile }
                .forEach { file ->
                    val rel = file.relativeTo(projectDir).invariantSeparatorsPath
                    zip.putNextEntry(ZipEntry(rel))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                    jumlah += 1
                }
        }
        jumlah
    }.recoverCatching { error -> throw IllegalStateException("Projek gagal diekspor: ${error.message}", error) }

    /**
     * Membuka zip ke folder baru di bawah [projectsRoot].
     * Mengembalikan folder projek hasil ekstrak.
     */
    fun import(zipFile: File, projectsRoot: File): Result<File> = runCatching {
        val nama = zipFile.nameWithoutExtension
            .replace(Regex("[^A-Za-z0-9._-]+"), "-")
            .trim('-')
            .ifBlank { "projek-impor" }
        val tujuan = unik(projectsRoot, nama)
        if (!tujuan.mkdirs()) throw IllegalStateException("Folder projek tidak bisa dibuat. Periksa ruang penyimpanan.")

        var entri = 0
        ZipInputStream(zipFile.inputStream().buffered()).use { zip ->
            while (true) {
                val berkas = zip.nextEntry ?: break
                if (berkas.isDirectory) {
                    zip.closeEntry()
                    continue
                }
                val relatif = ProjectFiles.safeRelative(tujuan, berkas.name)
                    ?: throw IllegalStateException("Zip memuat alamat tidak aman: ${berkas.name}")
                val keluar = File(tujuan, relatif)
                keluar.parentFile?.mkdirs()
                keluar.outputStream().use { zip.copyTo(it) }
                zip.closeEntry()
                entri += 1
            }
        }

        if (entri == 0) {
            tujuan.deleteRecursively()
            throw IllegalStateException("Zip kosong. Pilih berkas projek KodeNusa (.zip).")
        }
        val akar = if (File(tujuan, "kodenusa.json").isFile) tujuan else satuFolderDiDalam(tujuan)
        if (akar == null || !File(akar, "kodenusa.json").isFile) {
            tujuan.deleteRecursively()
            throw IllegalStateException("Zip ini tidak berisi projek KodeNusa (kodenusa.json tidak ditemukan).")
        }
        if (akar != tujuan) {
            // Zip yang diekspor dari folder lain sering membungkus semuanya dalam satu folder.
            val sementara = unik(projectsRoot, akar.name)
            if (!akar.renameTo(sementara)) return@runCatching akar
            tujuan.deleteRecursively()
            return@runCatching sementara
        }
        tujuan
    }

    private fun satuFolderDiDalam(folder: File): File? {
        val anak = folder.listFiles().orEmpty().filter { it.name != "__MACOSX" }
        return anak.singleOrNull()?.takeIf { it.isDirectory }
    }

    private fun unik(root: File, base: String): File {
        var calon = File(root, base)
        var urutan = 2
        while (calon.exists()) {
            calon = File(root, "$base-$urutan")
            urutan += 1
        }
        return calon
    }
}
