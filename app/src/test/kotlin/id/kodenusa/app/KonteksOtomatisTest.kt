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
}
