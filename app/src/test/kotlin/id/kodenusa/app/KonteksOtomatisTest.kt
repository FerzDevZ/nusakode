package id.kodenusa.app

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class KonteksOtomatisTest {

    @TempDir
    lateinit var akar: File

    private fun tulis(jalur: String, isi: String) {
        val berkas = File(akar, jalur)
        berkas.parentFile?.mkdirs()
        berkas.writeText(isi)
    }

    @Test
    fun pertanyaan_tentang_kasir_memilih_database_kasir_otomatis() {
        tulis("kodenusa.json", """{"name":"Kasir"}""")
        tulis("src/main/java/app/MainActivity.java", "public class MainActivity extends Activity {}")
        tulis("src/main/java/app/DatabaseKasir.java", "public class DatabaseKasir { void simpanProduk() {} }")
        tulis("src/main/java/app/TakTerkait.java", "class TakTerkait { void halLain() {} }")
        val hasil = KonteksOtomatis(akar).kumpulkan("Tambahkan produk baru di kasir", hemat = false)
        val jalur = hasil.berkas.map { it.first }
        assertTrue(jalur.any { it.endsWith("DatabaseKasir.java") }, "DatabaseKasir harus terpilih otomatis")
        assertTrue(jalur.none { it.endsWith("TakTerkait.java") }, "TakTerkait tidak relevan")
        assertTrue(hasil.jumlahBerkasProjek >= 4)
    }

    @Test
    fun manifest_dan_berkas_aktif_selalu_ikut() {
        tulis("kodenusa.json", """{"name":"X"}""")
        tulis("src/main/java/app/MainActivity.java", "class MainActivity {}")
        val hasil = KonteksOtomatis(akar).kumpulkan("hal umum", hemat = false, berkasAktif = "src/main/java/app/MainActivity.java")
        val jalur = hasil.berkas.map { it.first }
        assertTrue(jalur.contains("kodenusa.json"))
        assertTrue(jalur.contains("src/main/java/app/MainActivity.java"))
    }

    @Test
    fun hemat_kuota_memangkas_karakter_total() {
        tulis("kodenusa.json", """{"name":"X"}""")
        for (i in 1..6) tulis("src/main/java/app/K$i.java", "class K$i { /* ${"isi".repeat(400)} */ }")
        val normal = KonteksOtomatis(akar).kumpulkan("kelas K", hemat = false)
        val hemat = KonteksOtomatis(akar).kumpulkan("kelas K", hemat = true)
        assertTrue(hemat.jumlahKarakter < normal.jumlahKarakter, "hemat harus lebih kecil dari normal")
        assertTrue(hemat.jumlahKarakter <= 12_000)
    }

    @Test
    fun folder_build_tidak_dipindai() {
        tulis("kodenusa.json", """{"name":"X"}""")
        tulis("build/hasil/kelas.java", "class Hasil {}")
        val hasil = KonteksOtomatis(akar).kumpulkan("kelas", hemat = false)
        assertEquals(1, hasil.jumlahBerkasProjek)
    }

    // ---- Verifikasi kualitas di projek yang lebih besar (v0.21.0) ----

    /** Projek sintetis 40+ berkas: tiga layar, dua database, utilitas, layout, resource, aset. */
    private fun projekBesar() {
        tulis("kodenusa.json", """{"name":"Toko Nusantara","templateId":"kasir-warung"}""")
        tulis(
            "src/main/AndroidManifest.xml",
            """<manifest><application><activity android:name=".MainActivity"/>""" +
                """<activity android:name=".LaporanActivity"/><activity android:name=".StokActivity"/></application></manifest>""",
        )
        val kelas = mapOf(
            "MainActivity" to "kasir, transaksi, tombol tambah, total hari ini",
            "LaporanActivity" to "laporan penjualan, rekap bulanan, grafik",
            "StokActivity" to "stok barang, jumlah stok, restock",
            "DatabaseKasir" to "tabel transaksi, insert transaksi, total hari ini",
            "DatabaseStok" to "tabel stok, kurangi stok, ambil stok",
            "DatabaseLaporan" to "rekap laporan, agregasi penjualan",
            "AdapterTransaksi" to "adapter daftar transaksi, bind view",
            "FormatRupiah" to "format rupiah, ribuan, mata uang",
            "TanggalUtil" to "format tanggal, zona waktu",
            "PengaturanToko" to "nama toko, alamat toko, preferensi",
            "KalkulatorDiskon" to "diskon persen, potongan harga",
            "CetakStruk" to "struk penjualan, teks struk",
        )
        for ((nama, isi) in kelas) {
            tulis("src/main/java/app/$nama.java", "public class $nama { /* $isi */ void kerja() {} }")
        }
        for (i in 1..20) {
            tulis("src/main/java/app/Helper$i.java", "public class Helper$i { void bantu() {} }")
        }
        tulis(
            "src/main/res/layout/activity_main.xml",
            """<LinearLayout><TextView android:id="@+id/teks_total"/><EditText android:id="@+id/input_nama"/><Button android:id="@+id/tombol_tambah"/></LinearLayout>""",
        )
        tulis(
            "src/main/res/layout/activity_laporan.xml",
            """<LinearLayout><TextView android:id="@+id/teks_rekap"/><Button android:id="@+id/tombol_rekap"/></LinearLayout>""",
        )
        tulis(
            "src/main/res/values/strings.xml",
            """<resources><string name="judul_total">Total hari ini</string><string name="tombol_tambah">Tambah</string></resources>""",
        )
        tulis(
            "src/main/res/values/colors.xml",
            """<resources><color name="hijau_toko">#2E7D32</color></resources>""",
        )
        tulis("src/main/assets/soal.json", """[{"soal":"contoh"}]""")
        tulis("web/index.html", "<html><body>katalog</body></html>")
    }

    @Test
    fun projek_besar_pertanyaan_kasir_memilih_berkas_kasir_dan_menolak_yang_lain() {
        projekBesar()
        val hasil = KonteksOtomatis(akar)
            .kumpulkan("Kenapa total kasir tidak bertambah setelah saya tambah transaksi?", hemat = false)
        val jalur = hasil.berkas.map { it.first }

        // Berkas domain kasir terpilih: database transaksi, layar utama, layoutnya, teksnya.
        assertTrue(jalur.any { it.endsWith("DatabaseKasir.java") }, "jalur terpilih: $jalur")
        assertTrue(jalur.any { it.endsWith("MainActivity.java") }, "jalur terpilih: $jalur")
        assertTrue(jalur.any { it.contains("res/layout/activity_main") }, "jalur terpilih: $jalur")
        assertTrue(jalur.any { it.endsWith("strings.xml") }, "jalur terpilih: $jalur")

        // Berkas domain lain tidak menyusup ke pertanyaan kasir.
        assertTrue(jalur.none { it.endsWith("DatabaseStok.java") }, "jalur terpilih: $jalur")
        assertTrue(jalur.none { it.endsWith("DatabaseLaporan.java") }, "jalur terpilih: $jalur")
        assertTrue(jalur.none { it.endsWith("activity_laporan.xml") }, "jalur terpilih: $jalur")

        // Helper tanpa kaitan tidak memenuhi anggaran; peta tetap melaporkan seluruh projek.
        assertTrue(hasil.jumlahBerkasProjek >= 40, "jumlah berkas projek: ${hasil.jumlahBerkasProjek}")
        assertTrue(hasil.peta.contains("DAFTAR BERKAS"))
        assertTrue(hasil.jumlahKarakter <= 12_000 + hasil.peta.length + 5_000 * hasil.jumlahBerkasDipilih,
            "karakter total: ${hasil.jumlahKarakter}")
    }

    @Test
    fun projek_besar_pertanyaan_stok_memilih_sisi_stok() {
        projekBesar()
        val hasil = KonteksOtomatis(akar)
            .kumpulkan("Bagaimana cara mengurangi stok barang saat barang terjual?", hemat = false)
        val jalur = hasil.berkas.map { it.first }
        assertTrue(jalur.any { it.endsWith("DatabaseStok.java") }, "jalur terpilih: $jalur")
        assertTrue(jalur.any { it.endsWith("StokActivity.java") }, "jalur terpilih: $jalur")
        assertTrue(jalur.none { it.endsWith("CetakStruk.java") }, "jalur terpilih: $jalur")
    }

    @Test
    fun projek_besar_mode_hemat_tetap_membawa_berkas_inti() {
        projekBesar()
        val hasil = KonteksOtomatis(akar)
            .kumpulkan("Kenapa total kasir tidak bertambah?", hemat = true)
        val jalur = hasil.berkas.map { it.first }
        assertTrue(jalur.contains("kodenusa.json"))
        assertTrue(jalur.any { it.endsWith("DatabaseKasir.java") }, "jalur terpilih: $jalur")
        assertTrue(hasil.jumlahKarakter <= 6_000 + hasil.peta.length + 2_500 * hasil.jumlahBerkasDipilih,
            "karakter total hemat: ${hasil.jumlahKarakter}")
    }
}
