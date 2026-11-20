package id.kodenusa.core.ai

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Menjalankan **seluruh 10 tugas evaluasi** (`docs/07-eval-agen.md`) dengan model tiruan yang
 * sudah "tahu" jawaban benar.
 *
 * Kenapa perlu, padahal [EvalAgenTest] sudah ada: uji itu butuh kunci API asli, jadi tidak pernah
 * jalan di CI dan mesinnya sendiri tidak pernah terbukti. Uji ini membuktikan hal yang berbeda dan
 * bisa dipercaya tanpa jaringan:
 *
 * - 10 tugas benar-benar bisa diselesaikan lewat loop `AgenBuild` (bukan hanya lewat tulisan tangan);
 * - berkas yang ditulis model mendarat di jalur yang diminta, tanpa menyentuh berkas lain;
 * - gerbang build dipanggil, dan galat build sungguhan memicu putaran perbaikan.
 *
 * Yang **tidak** dibuktikan di sini: kecerdasan model sungguhan. Angka pass-rate tetap harus diisi
 * dengan `AI_EVAL=1` dan kunci milik pengguna.
 */
class EvalAgenJalurTest {

    /** Model tiruan: pada putaran pertama menjawab dengan berkas, setelah itu "sudah selesai". */
    private class ModelTiruan(private val jawaban: String) : GerbangModel {
        var dipanggil = 0
            private set

        override suspend fun balas(pesan: List<ChatMessage>): String {
            dipanggil++
            return jawaban
        }
    }

    private class Papan(val akar: File) : PapanTulisProjek {
        val tertulis = LinkedHashMap<String, String>()

        override fun konteks(): String = "projek uji"

        override fun tulis(edit: BerkasEdit): String? {
            val tujuan = File(akar, edit.jalur)
            if (!tujuan.canonicalPath.startsWith(akar.canonicalPath + File.separator)) {
                return "jalur di luar projek"
            }
            tujuan.parentFile?.mkdirs()
            tujuan.writeText(edit.isi)
            tertulis[edit.jalur] = edit.isi
            return null
        }
    }

    private class Gerbang(val sukses: Boolean = true) : GerbangBuild {
        var dipanggil = 0
            private set

        override suspend fun build(): HasilBuild {
            dipanggil++
            return if (sukses) HasilBuild(true) else HasilBuild(false, "galat contoh dari gerbang")
        }
    }

    private fun jawaban(jalur: String, isi: String): String =
        "<<<BERKAS: $jalur>>>\n```java\n$isi\n```\n"

    /** 10 tugas tetap; isinya sengaja sederhana karena yang diuji adalah jalurnya, bukan modelnya. */
    private val tugas = listOf(
        "judul-layout" to jawaban(
            "src/main/res/layout/activity_main.xml",
            "<TextView android:id=\"@+id/judul\" android:text=\"Warung Bu Sri\" />",
        ),
        "tombol-simpan" to jawaban(
            "src/main/res/layout/activity_main.xml",
            "<Button android:id=\"@+id/simpan\" android:text=\"Simpan\" />",
        ),
        "method-hitung" to jawaban(
            "src/main/java/app/Hitung.java",
            "class Hitung { int total(int a, int b) { return a + b; } }",
        ),
        "toast-klik" to jawaban(
            "src/main/java/app/MainActivity.java",
            "class MainActivity { void klik() { android.widget.Toast.makeText(null, \"Halo\", 0).show(); } }",
        ),
        "warna-aksen" to jawaban("src/main/res/values/colors.xml", "<color name=\"aksen\">#F2A03D</color>"),
        "string-app" to jawaban("src/main/res/values/strings.xml", "<string name=\"nama_app\">Kasir Warung</string>"),
        "kelas-produk" to jawaban(
            "src/main/java/app/Produk.java",
            "class Produk { String nama; int harga; }",
        ),
        "layout-linear" to jawaban(
            "src/main/res/layout/activity_main.xml",
            "<LinearLayout android:orientation=\"vertical\" />",
        ),
        "simpan-preferensi" to jawaban(
            "src/main/java/app/Simpan.java",
            "class Simpan { void tulis(android.content.Context c) { c.getSharedPreferences(\"app\", 0); } }",
        ),
        "perbaiki-galat" to jawaban("src/main/java/app/MainActivity.java", "class MainActivity { }"),
    )

    @Test
    fun sepuluh_tugas_evaluasi_selesai_lewat_loop_agen(@TempDir akar: File) = runTest {
        val lulus = ArrayList<String>()

        for ((nama, balasan) in tugas) {
            val folder = File(akar, nama).apply { mkdirs() }
            val papan = Papan(folder)
            val gerbang = Gerbang()
            val model = ModelTiruan(balasan)

            val hasil = AgenBuild(model, papan, gerbang).jalankan("kerjakan $nama")

            assertTrue(hasil.sukses, "tugas $nama seharusnya selesai: ${hasil.galat}")
            assertEquals(1, hasil.putaran, "tugas $nama cukup satu putaran")
            assertTrue(gerbang.dipanggil >= 1, "tugas $nama harus melewati gerbang build")
            assertTrue(papan.tertulis.isNotEmpty(), "tugas $nama harus menulis berkas")
            lulus += nama
        }

        assertEquals(10, lulus.size)
    }

    @Test
    fun galat_build_memicu_putaran_perbaikan_lalu_berhenti_dengan_jujur(@TempDir akar: File) = runTest {
        val papan = Papan(akar)
        val gerbang = Gerbang(sukses = false)
        val model = ModelTiruan(
            jawaban("src/main/java/app/MainActivity.java", "class MainActivity { }"),
        )

        val hasil = AgenBuild(model, papan, gerbang, maksPutaran = 2).jalankan("perbaiki galat")

        assertTrue(!hasil.sukses)
        assertEquals(2, hasil.putaran, "putaran dibatasi keras walau model terus menjawab")
        assertEquals(2, gerbang.dipanggil)
        assertTrue(hasil.galat.contains("galat contoh dari gerbang"))
    }

    @Test
    fun model_yang_menulis_di_luar_projek_ditolak_tanpa_menyentuh_disk(@TempDir akar: File) = runTest {
        val projek = File(akar, "projek").apply { mkdirs() }
        val papan = Papan(projek)
        val model = ModelTiruan("<<<BERKAS: ../keluar.java>>>\n```java\nclass X {}\n```\n")

        val hasil = AgenBuild(model, papan, Gerbang()).jalankan("tulis di luar")

        assertTrue(papan.tertulis.isEmpty())
        assertTrue(!File(akar, "keluar.java").exists())
        assertTrue(hasil.catatan.any { it.contains("ditolak") } || !hasil.sukses)
    }
}
