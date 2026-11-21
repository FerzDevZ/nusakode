package id.kodenusa.build.tier0

import java.io.File
import java.net.URLClassLoader
import javax.tools.JavaCompiler
import javax.tools.ToolProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Csv.java adalah SATU sumber di `app/src/main/assets/templates/bersama/` dan disalin ke
 * template yang butuh ekspor CSV ("butuhCsv": true). Uji ini mengompilasi berkas aset asli
 * (bukan salinan) dengan javac JDK lalu menguji aturan escape RFC 4180 — logika template dan
 * uji tidak bisa saling lepas karena membaca berkas yang sama.
 */
class CsvTemplateTest {

    private class CsvJembatan private constructor(private val kelas: Class<*>) {
        fun amankan(nilai: String?): String =
            kelas.getMethod("amankan", String::class.java).invoke(null, nilai) as String

        fun baris(vararg bidang: String): String {
            val metode = kelas.getMethod("baris", Array<String>::class.java)
            return metode.invoke(null, bidang) as String
        }

        fun dokumen(daftar: List<String>): String =
            kelas.getMethod("dokumen", List::class.java).invoke(null, daftar) as String

        companion object {
            fun muat(): CsvJembatan {
                val sumber = File("../../app/src/main/assets/templates/bersama/Csv.java")
                check(sumber.isFile) { "Csv.java tidak ditemukan di ${sumber.absolutePath}" }
                val out = File("build/uji-csv-classes").apply { mkdirs() }
                val compiler: JavaCompiler = ToolProvider.getSystemJavaCompiler()
                    ?: error("javac tidak tersedia di JDK uji")
                val kode = compiler.run(null, null, null, "-d", out.absolutePath, sumber.absolutePath)
                assertEquals(0, kode, "Csv.java gagal dikompilasi")
                // Paket hasil kompilasi: __APP_ID__.app.Csv — muat lewat URLClassLoader.
                val loader = URLClassLoader(arrayOf(out.toURI().toURL()))
                val kelas = loader.loadClass("__APP_ID__.app.Csv")
                return CsvJembatan(kelas)
            }
        }
    }

    private val csv by lazy { CsvJembatan.muat() }

    @Test
    fun bidang_biasa_tanpa_kutip() {
        assertEquals("beras 5 kg", csv.amankan("beras 5 kg"))
    }

    @Test
    fun bidang_berkoma_dibungkus_kutip() {
        assertEquals("\"gula, garam, minyak\"", csv.amankan("gula, garam, minyak"))
    }

    @Test
    fun kutip_di_dalam_bidang_digandakan() {
        assertEquals("\"kata \"\"diskon\"\" hari ini\"", csv.amankan("kata \"diskon\" hari ini"))
    }

    @Test
    fun baris_baru_dalam_bidang_dibungkus_kutip() {
        val hasil = csv.amankan("dua\nbaris")
        assertTrue(hasil.startsWith("\"") && hasil.endsWith("\""), "harus dibungkus kutip: $hasil")
    }

    @Test
    fun null_dan_kosong_jadi_bidang_kosong() {
        assertEquals("", csv.amankan(null))
        assertEquals("", csv.amankan(""))
    }

    @Test
    fun baris_menggabungkan_bidang_dengan_koma() {
        assertEquals(
            "2026-11-21,Beli beras,15000,MASUK",
            csv.baris("2026-11-21", "Beli beras", "15000", "MASUK"),
        )
    }

    @Test
    fun baris_dengan_koma_tetap_sah_dibaca_ulang() {
        // Simulasi pembaca CSV sederhana: pemisah di luar kutip saja.
        val baris = csv.baris("catatan, penting", "12000")
        val bidang = pisahCsv(baris)
        assertEquals(listOf("catatan, penting", "12000"), bidang)
    }

    @Test
    fun dokumen_diakhiri_ganti_baris_per_baris() {
        val hasil = csv.dokumen(listOf("a,b", "c,d"))
        assertEquals("a,b\nc,d\n", hasil)
    }

    /** Pembaca CSV minimal untuk memverifikasi keluaran bisa diurai kembali. */
    private fun pisahCsv(baris: String): List<String> {
        val hasil = mutableListOf<String>()
        val sekarang = StringBuilder()
        var dalamKutip = false
        var i = 0
        while (i < baris.length) {
            val c = baris[i]
            when {
                dalamKutip && c == '"' && i + 1 < baris.length && baris[i + 1] == '"' -> {
                    sekarang.append('"'); i++
                }
                c == '"' -> dalamKutip = !dalamKutip
                c == ',' && !dalamKutip -> {
                    hasil.add(sekarang.toString()); sekarang.clear()
                }
                else -> sekarang.append(c)
            }
            i++
        }
        hasil.add(sekarang.toString())
        return hasil
    }
}
