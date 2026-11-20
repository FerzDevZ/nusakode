package id.kodenusa.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.content.FileProvider
import id.kodenusa.app.ui.KodeNusaApp
import id.kodenusa.ui.KodenusaTheme
import java.io.File

class MainActivity : ComponentActivity() {

    private lateinit var container: AppContainer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pasangPenangkapGalat()
        container = AppContainer(applicationContext)
        setContent {
            KodenusaTheme {
                KodeNusaApp(container)
            }
        }
    }
    /**
     * Laporan galat ditulis ke penyimpanan internal, tidak dikirim ke mana pun (ADR-032).
     * Penangan lama tetap dipanggil supaya proses mati seperti biasa dan Android tetap tahu.
     */
    private fun pasangPenangkapGalat() {
        val lama = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { benang, galat ->
            runCatching {
                LaporanGalat.tulis(
                    applicationContext,
                    galat,
                    LaporanGalat.info(applicationContext, BuildConfig.VERSION_NAME),
                )
            }
            lama?.uncaughtException(benang, galat)
        }
    }
}

/**
 * Membuka pemasang APK Android. Kalau izin "instal aplikasi tidak dikenal" belum diberikan,
 * pemasang sendiri yang memintanya — aplikasi tidak boleh menebak dan menampilkan pesan gagal
 * sebelum user melihat dialog sistem.
 */
object ApkInstaller {

    fun install(context: Context, apk: File): Result<Unit> = runCatching {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.berkas", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }.onFailure {
        Toast.makeText(context, "APK tidak bisa dibuka: ${it.message}", Toast.LENGTH_LONG).show()
    }

    fun share(context: Context, apk: File): Result<Unit> = runCatching {
        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.berkas", apk)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.android.package-archive"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Bagikan APK"))
    }
}
