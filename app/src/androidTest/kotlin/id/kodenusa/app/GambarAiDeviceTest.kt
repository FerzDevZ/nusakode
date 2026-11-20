package id.kodenusa.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Bukti bahwa gambar untuk AI benar-benar dikecilkan di HP, bukan dikirim mentah:
 * foto besar harus berakhir ≤ 1024 px dan ≤ 700 KB setelah jadi data URL.
 */
@RunWith(AndroidJUnit4::class)
class GambarAiDeviceTest {

    private val konteks = InstrumentationRegistry.getInstrumentation().targetContext

    private fun fotoBesar(): Uri {
        val bitmap = Bitmap.createBitmap(2400, 1600, Bitmap.Config.ARGB_8888)
        val kanvas = android.graphics.Canvas(bitmap)
        kanvas.drawColor(android.graphics.Color.rgb(30, 90, 160))
        kanvas.drawCircle(600f, 700f, 400f, android.graphics.Paint().apply { color = android.graphics.Color.RED })
        val berkas = File(konteks.cacheDir, "foto-uji.png")
        berkas.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return Uri.fromFile(berkas)
    }

    @Test
    fun foto_besar_dikecilkan_dan_dipadatkan() {
        val dataUrl = GambarUntukAi.dataUrl(konteks, fotoBesar())

        assertNotNull("gambar harus terbaca", dataUrl)
        assertTrue("harus JPEG base64", dataUrl!!.startsWith("data:image/jpeg;base64,"))

        val isi = android.util.Base64.decode(dataUrl.substringAfter("base64,"), android.util.Base64.NO_WRAP)
        assertTrue("ukuruan ${isi.size} B melewati batas", isi.size <= GambarUntukAi.MAKS_BYTE)

        val hasil = BitmapFactory.decodeByteArray(isi, 0, isi.size)
        assertNotNull(hasil)
        assertTrue("sisi terpanjang ${maxOf(hasil.width, hasil.height)} harus ≤ 1024", maxOf(hasil.width, hasil.height) <= GambarUntukAi.MAKS_SISI)
        hasil.recycle()
    }

    @Test
    fun sampel_dihitung_untuk_foto_kamera() {
        assertEquals(1, GambarUntukAi.sampelUntuk(800, 600))
        assertEquals(2, GambarUntukAi.sampelUntuk(2048, 1536))
        assertEquals(4, GambarUntukAi.sampelUntuk(4096, 3072))
    }
}
