package id.kodenusa.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import id.kodenusa.core.build.Tier0Config
import java.io.File

/**
 * Membuat ikon aplikasi hasil build (docs/14 2.2) saat build berjalan di perangkat.
 *
 * Kenapa digambar sebagai PNG dan bukan vector font: huruf awal nama aplikasi berubah-ubah, dan
 * membundle font untuk semua kemungkinan huruf boros RAM/ukuran. Android framework di sekitar
 * build sudah punya font sistem — dipakai saja.
 *
 * Hasil di folder kerja (cache aplikasi, bukan folder projek pengguna):
 * - `res/mipmap/ic_launcher.png` — ikon lama (API 24–25)
 * - `res/mipmap-anydpi-v26/ic_launcher.xml` — adaptive icon (API 26+)
 * - `res/drawable/ic_launcher_fg.png` — latar depan adaptive (huruf di zona aman)
 * - `res/values/ikon.xml` — warna latar
 *
 * Manifest pengguna TIDAK pernah ditimpa: bila belum menunjuk ikon, salinan manifest yang sudah
 * dipatch dipakai untuk build ini (lihat [IkonAplikasi.patch]).
 */
class PembuatIkon(private val folderKerja: File) {

    fun siapkan(config: Tier0Config): Tier0Config {
        val folderRes = File(folderKerja, "res").apply { deleteRecursively(); mkdirs() }
        val latar = IkonAplikasi.warnaLatar(config.warnaIkon)
        val huruf = IkonAplikasi.hurufAwal(config.appName)
        val teks = IkonAplikasi.warnaTeks(latar = latar)

        tulisPng(File(folderRes, "mipmap/ic_launcher.png"), 192) { kanvas, ukuran ->
            gambarLatarBulat(kanvas, ukuran, latar)
            gambarHuruf(kanvas, ukuran, huruf, teks, porsi = 0.52f)
        }
        tulisPng(File(folderRes, "drawable/ic_launcher_fg.png"), 432) { kanvas, ukuran ->
            // Zona aman adaptive icon: lingkaran dalam ±33dp dari 108dp. Huruf digambar di dalamnya.
            gambarHuruf(kanvas, ukuran, huruf, teks, porsi = 0.30f)
        }
        File(folderRes, "mipmap-anydpi-v26").mkdirs()
        File(folderRes, "mipmap-anydpi-v26/ic_launcher.xml").writeText(
            """
            <?xml version="1.0" encoding="utf-8"?>
            <adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
                <background android:drawable="@color/ikon_latar"/>
                <foreground android:drawable="@drawable/ic_launcher_fg"/>
            </adaptive-icon>
            """.trimIndent(),
        )
        File(folderRes, "values").mkdirs()
        File(folderRes, "values/ikon.xml").writeText(
            """
            <?xml version="1.0" encoding="utf-8"?>
            <resources>
                <color name="ikon_latar">#%06X</color>
            </resources>
            """.trimIndent().format(latar and 0xFFFFFF),
        )

        val manifestIkon = File(folderKerja, "AndroidManifest.xml")
        val teksManifest = config.manifest.readText()
        manifestIkon.writeText(IkonAplikasi.patch(teksManifest))
        return IkonAplikasi.configDenganIkon(config, folderRes, manifestIkon)
    }

    private fun tulisPng(tujuan: File, ukuran: Int, gambar: (Canvas, Int) -> Unit) {
        tujuan.parentFile.mkdirs()
        val bitmap = Bitmap.createBitmap(ukuran, ukuran, Bitmap.Config.ARGB_8888)
        gambar(Canvas(bitmap), ukuran)
        tujuan.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    private fun gambarLatarBulat(kanvas: Canvas, ukuran: Int, warna: Int) {
        val kuas = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = warna }
        val radius = ukuran * 0.22f
        kanvas.drawRoundRect(RectF(0f, 0f, ukuran.toFloat(), ukuran.toFloat()), radius, radius, kuas)
    }

    private fun gambarHuruf(kanvas: Canvas, ukuran: Int, huruf: String, warna: Int, porsi: Float) {
        val kuas = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = warna
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = ukuran * porsi
            textAlign = Paint.Align.CENTER
        }
        val metrik = kuas.fontMetrics
        val garisDasar = ukuran / 2f - (metrik.ascent + metrik.descent) / 2f
        kanvas.drawText(huruf, ukuran / 2f, garisDasar, kuas)
    }
}
