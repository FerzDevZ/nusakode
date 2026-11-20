package id.kodenusa.core.project

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.io.File

/** Uji penyalinan berkas dari luar HP: nama asing, bentrok nama, folder terlarang, batas ukuran. */
class BerkasMasukTest {

    @TempDir
    lateinit var akar: File

    private fun masuk(teks: String) = ByteArrayInputStream(teks.toByteArray())

    @Test
    fun berkas_baru_masuk_ke_folder_tujuan() {
        File(akar, "web").mkdirs()
        val hasil = BerkasMasuk.simpan(akar, "web", "logo.svg", masuk("<svg/>"))

        assertTrue(hasil is BerkasMasuk.Hasil.Sukses, "harus sukses, hasil=$hasil")
        val berkas = (hasil as BerkasMasuk.Hasil.Sukses).file
        assertEquals(File(akar, "web/logo.svg").canonicalFile, berkas.canonicalFile)
        assertEquals("<svg/>", berkas.readText())
    }

    @Test
    fun nama_dengan_jalur_dipotong_jadi_nama_berkas() {
        val hasil = BerkasMasuk.simpan(akar, "", "Download/laporan.css", masuk("body{}"))
        assertTrue(hasil is BerkasMasuk.Hasil.Sukses, "hasil=$hasil")
        assertEquals("laporan.css", (hasil as BerkasMasuk.Hasil.Sukses).file.name)
    }

    @Test
    fun nama_bentrok_diberi_akhiran_bukan_menimpa() {
        File(akar, "catatan.txt").writeText("lama")
        val hasil = BerkasMasuk.simpan(akar, "", "catatan.txt", masuk("baru"))

        assertTrue(hasil is BerkasMasuk.Hasil.Sukses, "hasil=$hasil")
        assertEquals("catatan (2).txt", (hasil as BerkasMasuk.Hasil.Sukses).file.name)
        assertEquals("lama", File(akar, "catatan.txt").readText())
        assertEquals("baru", File(akar, "catatan (2).txt").readText())
    }

    @Test
    fun folder_tujuan_di_luar_projek_ditolak() {
        val hasil = BerkasMasuk.simpan(akar, "../keluar", "jahat.txt", masuk("x"))
        assertTrue(hasil is BerkasMasuk.Hasil.Gagal, "hasil=$hasil")
    }

    @Test
    fun nama_terlarang_ditolak_dengan_alasan() {
        val hasil = BerkasMasuk.simpan(akar, "", "..", masuk("x"))
        assertTrue(hasil is BerkasMasuk.Hasil.Gagal, "hasil=$hasil")
        assertTrue((hasil as BerkasMasuk.Hasil.Gagal).pesan.contains("tidak bisa dipakai"))
        assertEquals(null, BerkasMasuk.namaBersih(""))
    }

    @Test
    fun berkas_kebesaran_ditolak_dan_tidak_meninggalkan_sampah() {
        val besar = ByteArray(BerkasMasuk.MAKS_BYTE.toInt() + 1024) { 7 }
        val hasil = BerkasMasuk.simpan(akar, "", "video.mp4", ByteArrayInputStream(besar))

        assertTrue(hasil is BerkasMasuk.Hasil.Gagal, "hasil=$hasil")
        assertTrue((hasil as BerkasMasuk.Hasil.Gagal).pesan.contains("MB"))
        assertTrue(akar.listFiles().orEmpty().isEmpty(), "tidak boleh ada berkas separuh jadi")
    }

    @Test
    fun folder_tujuan_mengikuti_berkas_yang_sedang_dibuka() {
        // Berkas di web/index.html -> berkas baru ikut ke web, bukan ke akar dan bukan ke jalur
        // absolut (yang dulu membuat impor selalu ditolak).
        val aktif = File(akar, "web/index.html")
        assertEquals("web", BerkasMasuk.folderDariAktif(akar, aktif))
        assertEquals("", BerkasMasuk.folderDariAktif(akar, File(akar, "Toko.java")))
        assertEquals("", BerkasMasuk.folderDariAktif(akar, null))
        assertEquals(
            "src/main/java/app",
            BerkasMasuk.folderDariAktif(akar, File(akar, "src/main/java/app/MainActivity.java")),
        )
        // Berkas di luar projek tidak boleh menghasilkan folder tujuan.
        assertEquals("", BerkasMasuk.folderDariAktif(akar, File("/tmp/lain/Berkas.java")))
    }
}
