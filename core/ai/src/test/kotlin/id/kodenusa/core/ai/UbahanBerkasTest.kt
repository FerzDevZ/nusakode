package id.kodenusa.core.ai

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UbahanBerkasTest {

    @Test
    fun mengambil_satu_berkas_dari_penanda() {
        val balasan = """
            Saya tambahkan tombol simpan.

            <<<BERKAS: src/main/java/app/MainActivity.java>>>
            ```java
            package app;

            public class MainActivity {}
            ```
        """.trimIndent()

        val hasil = UbahanBerkas.ambil(balasan)

        assertEquals(1, hasil.size)
        assertEquals("src/main/java/app/MainActivity.java", hasil[0].jalur)
        assertTrue(hasil[0].isi.startsWith("package app;"))
        assertTrue(hasil[0].isi.endsWith("\n"), "isi berkas harus diakhiri satu baris baru")
    }

    @Test
    fun mengambil_beberapa_berkas_sekaligus() {
        val balasan = """
            <<<BERKAS: src/main/res/values/strings.xml>>>
            ```xml
            <resources><string name="app">Warung</string></resources>
            ```
            <<<BERKAS: src/main/AndroidManifest.xml>>>
            ```xml
            <manifest package="app" />
            ```
        """.trimIndent()

        val hasil = UbahanBerkas.ambil(balasan)

        assertEquals(2, hasil.size)
        assertEquals("src/main/res/values/strings.xml", hasil[0].jalur)
        assertEquals("src/main/AndroidManifest.xml", hasil[1].jalur)
        // Berkas kedua tidak boleh ikut menelan isi berkas pertama.
        assertTrue(hasil[1].isi.contains("manifest"))
        assertTrue(!hasil[1].isi.contains("resources"))
    }

    @Test
    fun balasan_tanpa_penanda_dianggap_kosong() {
        val balasan = "Maaf, saya tidak bisa mengerjakan itu di Tier 0."

        assertTrue(UbahanBerkas.ambil(balasan).isEmpty())
    }

    @Test
    fun jalur_yang_keluar_projek_ditolak() {
        assertNull(UbahanBerkas.rapikanJalur("../../shared_prefs/kunci.xml"))
        assertNull(UbahanBerkas.rapikanJalur("/data/data/id.kodenusa.app/files/x"))
        assertNull(UbahanBerkas.rapikanJalur("src/../../rahasia.txt"))
        assertNull(UbahanBerkas.rapikanJalur("   "))
    }

    @Test
    fun jalur_normal_dirapikan() {
        val jalurWindows = " src\\main\\java\\app\\MainActivity.java "
        assertEquals("src/main/java/app/MainActivity.java", UbahanBerkas.rapikanJalur(jalurWindows))
        assertNull(UbahanBerkas.rapikanJalur("/app/MainActivity.java"), "jalur absolut harus ditolak")
    }

    @Test
    fun berkas_dengan_jalur_buruk_dilewati_sedangkan_yang_baik_dipakai() {
        val balasan = """
            <<<BERKAS: ../../rahasia.txt>>>
            ```text
            jangan ditulis
            ```
            <<<BERKAS: src/Aman.java>>>
            ```java
            class Aman {}
            ```
        """.trimIndent()

        val hasil = UbahanBerkas.ambil(balasan)

        assertEquals(1, hasil.size)
        assertEquals("src/Aman.java", hasil[0].jalur)
    }
}
