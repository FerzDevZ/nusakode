package id.kodenusa.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream
import kotlin.math.max

/**
 * Menyiapkan gambar untuk model yang bisa melihat.
 *
 * Aturan yang mahal kalau dilanggar di HP 4/64:
 * 1. **Selalu dikecilkan** ke sisi terpanjang [MAKS_SISI]. Foto kamera 4000×3000 (≈ 4 MB) akan
 *    membengkak jadi > 5 MB sebagai base64 dan memakan kuota pengguna tanpa menambah informasi.
 * 2. **Batas ukuran keras** [MAKS_BYTE]; mutu JPEG diturunkan bertahap sampai lolos, bukan
 *    dikirim apa adanya.
 * 3. Dibaca lewat `contentResolver` (SAF/Photo Picker), jadi tidak butuh izin penyimpanan.
 */
object GambarUntukAi {

    const val MAKS_SISI = 1024
    const val MUTU_AWAL = 80
    const val MUTU_MINIMAL = 40
    const val MAKS_BYTE = 700_000

    /** Mengembalikan data URL `data:image/jpeg;base64,…`, atau `null` bila gambar tak terbaca. */
    fun dataUrl(konteks: Context, uri: Uri): String? {
        val asli = bukaBitmap(konteks, uri) ?: return null
        val kecil = kecilkan(asli)
        if (kecil !== asli) asli.recycle()
        return try {
            val jpeg = padatkan(kecil)
            if (jpeg == null) null
            else "data:image/jpeg;base64," + Base64.encodeToString(jpeg, Base64.NO_WRAP)
        } finally {
            kecil.recycle()
        }
    }

    private fun bukaBitmap(konteks: Context, uri: Uri): Bitmap? = runCatching {
        konteks.contentResolver.openInputStream(uri)?.use { aliran ->
            // Dua tahap: baca ukuran dulu supaya tidak mengalokasikan bitmap penuh untuk gambar raksasa.
            val opsiUkur = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(aliran, null, opsiUkur)
            val sampel = sampelUntuk(opsiUkur.outWidth, opsiUkur.outHeight)
            konteks.contentResolver.openInputStream(uri)?.use { kedua ->
                BitmapFactory.decodeStream(kedua, null, BitmapFactory.Options().apply { inSampleSize = sampel })
            }
        }
    }.getOrNull()

    /** Pangkat dua terdekat supaya pemuatan awal hemat memori, bukan hasil akhirnya. */
    fun sampelUntuk(lebar: Int, tinggi: Int): Int {
        if (lebar <= 0 || tinggi <= 0) return 1
        var sampel = 1
        while (max(lebar, tinggi) / (sampel * 2) >= MAKS_SISI) sampel *= 2
        return sampel
    }

    private fun kecilkan(bitmap: Bitmap): Bitmap {
        val sisi = max(bitmap.width, bitmap.height)
        if (sisi <= MAKS_SISI) return bitmap
        val skala = MAKS_SISI.toFloat() / sisi
        return Bitmap.createScaledBitmap(
            bitmap,
            max(1, (bitmap.width * skala).toInt()),
            max(1, (bitmap.height * skala).toInt()),
            true,
        )
    }

    private fun padatkan(bitmap: Bitmap): ByteArray? {
        var mutu = MUTU_AWAL
        while (mutu >= MUTU_MINIMAL) {
            val keluaran = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, mutu, keluaran)
            val isi = keluaran.toByteArray()
            if (isi.size <= MAKS_BYTE) return isi
            mutu -= 10
        }
        return null
    }
}
