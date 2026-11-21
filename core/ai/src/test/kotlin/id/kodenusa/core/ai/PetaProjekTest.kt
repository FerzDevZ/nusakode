package id.kodenusa.core.ai

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class PetaProjekTest {

    @TempDir
    lateinit var akar: File

    private fun tulis(jalur: String, isi: String) {
        val berkas = File(akar, jalur)
        berkas.parentFile?.mkdirs()
        berkas.writeText(isi)
    }

    @Test
    fun peta_menyertakan_identitas_dan_daftar_berkas_berperan() {
        tulis("kodenusa.json", """{"name":"Kasir","applicationId":"app.kasir"}""")
        tulis("src/main/java/app/MainActivity.java", "public class MainActivity extends Activity {\n}")
        tulis("src/main/res/layout/activity_main.xml", "<LinearLayout></LinearLayout>")
        val peta = PetaProjek.petakan(akar)
        assertTrue(peta.contains("PETA PROJEK"))
        assertTrue(peta.contains("[manifest-projek] kodenusa.json"))
        assertTrue(peta.contains("[kode-java] src/main/java/app/MainActivity.java"))
        assertTrue(peta.contains("[layout] src/main/res/layout/activity_main.xml"))
    }

    @Test
    fun kerangka_java_mengambil_kelas_metode_dan_rujukan_resource() {
        val isi = """
            package app;
            public class MainActivity extends Activity {
                private DatabaseKasir db;
                public void onCreate(Bundle b) {
                    setContentView(R.layout.activity_main);
                    String judul = getString(R.string.nama_app);
                }
            }
        """.trimIndent()
        val kerangka = PetaProjek.kerangka("src/main/java/app/MainActivity.java", isi)
        assertTrue(kerangka.any { it.contains("class MainActivity") })
        assertTrue(kerangka.any { it.contains("onCreate") })
        assertTrue(kerangka.any { it.contains("R.layout.activity_main") })
        assertTrue(kerangka.any { it.contains("R.string.nama_app") })
    }

    @Test
    fun folder_build_dan_git_dilewati() {
        tulis("build/hasil.apk", "x")
        tulis(".git/config", "x")
        tulis("src/main/java/app/A.java", "class A {}")
        val peta = PetaProjek.petakan(akar)
        assertFalse(peta.contains("build/"))
        assertFalse(peta.contains(".git/"))
        assertTrue(peta.contains("A.java"))
    }

    @Test
    fun peta_dipotong_pada_anggaran_karakter() {
        for (i in 1..30) tulis("src/main/java/app/Kelas$i.java", "class Kelas$i { /* ${"x".repeat(500)} */ }")
        val peta = PetaProjek.petakan(akar, 3_000)
        assertTrue(peta.length <= 3_000, "peta harus dipotong pada anggaran")
    }

    @Test
    fun kutipan_isi_mengutamakan_manifest_dan_mainactivity_bukan_berkas_terkecil() {
        tulis("kodenusa.json", """{"name":"Kasir"}""")
        tulis("src/main/java/app/ZzzKecil.java", "class Z {}")
        tulis("src/main/java/app/MainActivity.java", "public class MainActivity { /* ${"inti".repeat(50)} */ }")
        val peta = PetaProjek.petakan(akar, 12_000)
        val posisiMain = peta.indexOf("MainActivity")
        val posisiZzz = peta.indexOf("ZzzKecil")
        assertTrue(posisiMain >= 0, "MainActivity harus muncul")
        assertTrue(
            posisiMain < posisiZzz || posisiZzz < 0,
            "MainActivity harus dikutip lebih dulu daripada berkas kecil tak penting",
        )
    }
}
