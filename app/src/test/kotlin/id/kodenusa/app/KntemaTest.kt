package id.kodenusa.app

import id.kodenusa.ui.PaletGelapBengkel
import id.kodenusa.ui.PaletTerangKertas
import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Uji gerbang tema kustom: format .kntema, gerbang kontras, dan penyimpanan berkas.
 * Tema buruk harus ditolak dengan pesan yang jelas — bukan dipasang lalu merusak keterbacaan.
 */
class KntemaTest {

    private val jsonValid = """
        {"versi":1,"nama":"Uji Malam","gelap":true,"warna":{
        "background":"#0E1217","surface":"#181E23","surfaceRaised":"#232A30",
        "border":"#484E54","borderStrong":"#6F757B","text":"#EBEFF4","textDim":"#A0A5AB",
        "accent":"#F7AD30","accentInk":"#281600","ok":"#61D19A","warn":"#E7C952",
        "error":"#F75D59","info":"#6AB8E4"}}
    """.trimIndent()

    @Test
    fun `tema valid terurai dan kontrasnya lolos`() {
        val tema = Kntema.parse(jsonValid).getOrThrow()
        assertEquals("Uji Malam", tema.nama)
        assertTrue(tema.gelap)
        assertTrue(Kntema.pasanganGagal(tema.palet).isEmpty(), "tema bawaan tidak boleh gagal kontras")
    }

    @Test
    fun `warna hilang ditolak dengan nama kuncinya`() {
        val rusak = jsonValid.replace("\"warn\":\"#E7C952\",", "")
        val galat = Kntema.parse(rusak).exceptionOrNull()!!.message!!
        assertTrue(galat.contains("warn"), "pesan harus menyebut kunci yang hilang: $galat")
    }

    @Test
    fun `hex rusak ditolak`() {
        val rusak = jsonValid.replace("#EBEFF4", "#EBEF")
        assertTrue(Kntema.parse(rusak).isFailure)
    }

    @Test
    fun `ukuran lebih dari 4kb ditolak`() {
        val besar = jsonValid.replace("Uji Malam", "U".repeat(5_000))
        val galat = Kntema.parse(besar).exceptionOrNull()!!.message!!
        assertTrue(galat.contains("4 KB"))
    }

    @Test
    fun `tema kontras rendah ditolak gerbang`() {
        // Teks abu-abu di latar gelap: kontras ~2:1 — persis tema buruk yang harus dicegah.
        val buruk = jsonValid
            .replace("#EBEFF4", "#5A5A5A")
            .replace("Uji Malam", "Tema Buruk")
        val tema = Kntema.parse(buruk).getOrThrow()
        val gagal = Kntema.pasanganGagal(tema.palet)
        assertTrue(gagal.isNotEmpty(), "tema kontras rendah harus tertangkap")
        assertTrue(gagal.first().contains("text"), "pesan harus menyebut pasangan: ${gagal.first()}")
        assertTrue(gagal.first().contains("4,5"), "pesan harus menyebut standar: ${gagal.first()}")
    }

    @Test
    fun `tema bawaan lolos gerbang yang sama dengan tema kustom`() {
        assertTrue(Kntema.pasanganGagal(PaletGelapBengkel).isEmpty())
        assertTrue(Kntema.pasanganGagal(PaletTerangKertas).isEmpty())
    }

    @Test
    fun `serialisasi dan parse kembali menghasilkan warna sama`() {
        val tema = Kntema.parse(jsonValid).getOrThrow()
        val kembali = Kntema.parse(Kntema.serialisasi(tema)).getOrThrow()
        assertEquals(tema.palet.background, kembali.palet.background)
        assertEquals(tema.palet.accent, kembali.palet.accent)
        assertEquals(tema.nama, kembali.nama)
    }

    @Test
    fun `simpan daftar dan hapus tema kustom di folder`(@TempDir folder: File) {
        val tema = Kntema.parse(jsonValid).getOrThrow()
        val berkas = Kntema.simpan(folder, tema).getOrThrow()
        assertTrue(berkas.exists())
        assertEquals(1, Kntema.daftar(folder).size)
        assertTrue(Kntema.hapus(Kntema.daftar(folder).first()))
        assertEquals(0, Kntema.daftar(folder).size)
    }

    @Test
    fun `nama dengan karakter aneh tetap aman sebagai nama berkas`(@TempDir folder: File) {
        val tema = Kntema.parse(jsonValid.replace("Uji Malam", "Tema/../Buruk")).getOrThrow()
        val berkas = Kntema.simpan(folder, tema).getOrThrow()
        assertFalse(berkas.absolutePath.contains(".."), "nama berkas tidak boleh lolos dari folder tema")
    }
}

    @Test
    fun `serialisasi_tema_terang_menulis_gelap_false`() {
        val palet = PaletTerangKertas
        val tema = Kntema.Tema("Terang Uji", false, palet, null)
        val json = Kntema.serialisasi(tema)
        assertTrue(json.contains("\"gelap\":false"), "gelap=false harus tertulis di berkas")
        val balik = Kntema.parse(json).getOrThrow()
        assertFalse(balik.gelap, "tema terang harus terbaca kembali sebagai terang")
    }
