package id.kodenusa.app

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File

/**
 * Jembatan antara pemilih berkas Android (SAF) dan folder projek.
 *
 * Semua akses berkas di aplikasi ini lewat pemilih bawaan Android, bukan izin penyimpanan. Itu
 * keputusan sadar (ADR-022): izin `READ_EXTERNAL_STORAGE` di Android 13+ tidak berlaku untuk
 * dokumen umum, `MANAGE_EXTERNAL_STORAGE` membuat aplikasi diblokir Play Protect, sedangkan SAF
 * bekerja tanpa izin sama sekali dan pengguna tetap memilih sendiri berkas mana yang dibuka.
 *
 * Karena tidak ada izin, satu-satunya hal yang bisa gagal adalah **nama berkas** dari aplikasi
 * lain: nama itu bisa kosong atau berisi jalur. [namaTampilan] menanganinya tanpa menebak-nebak.
 */
object KonteksBerkas {

    /** Nama berkas yang ditampilkan pemilih; jatuh ke potongan terakhir URI bila kosong. */
    fun namaTampilan(konteks: Context, uri: Uri): String {
        val dariPenyedia = runCatching {
            konteks.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { kursor ->
                    if (kursor.moveToFirst()) kursor.getString(0) else null
                }
        }.getOrNull()
        val kandidat = dariPenyedia?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment?.substringAfterLast('/')
            ?: "berkas"
        return kandidat
    }

    /** URI yang bisa dibagikan aplikasi lain untuk satu berkas di dalam folder projek. */
    fun uriBagikan(konteks: Context, berkas: File): Uri =
        androidx.core.content.FileProvider.getUriForFile(
            konteks,
            "${konteks.packageName}.berkas",
            berkas,
        )

    /** MIME sederhana dari ekstensi; cukup untuk WhatsApp/Drive memilih aplikasi yang tepat. */
    fun mime(berkas: File): String = when (berkas.extension.lowercase()) {
        "zip" -> "application/zip"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "webp" -> "image/webp"
        "svg" -> "image/svg+xml"
        "gif" -> "image/gif"
        "pdf" -> "application/pdf"
        "html", "htm" -> "text/html"
        "css" -> "text/css"
        "js" -> "text/javascript"
        "json" -> "application/json"
        "xml" -> "text/xml"
        "md", "txt", "java", "kt", "properties" -> "text/plain"
        "apk" -> "application/vnd.android.package-archive"
        else -> "*/*"
    }
}
