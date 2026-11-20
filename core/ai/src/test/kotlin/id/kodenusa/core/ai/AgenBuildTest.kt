package id.kodenusa.core.ai

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AgenBuildTest {

    /** Model palsu: mengembalikan balasan berurutan, dan mencatat pesan yang diterimanya. */
    private class ModelPalsu(private val balasan: List<String>) : GerbangModel {
        var dipanggil = 0
        val pesan = ArrayList<List<ChatMessage>>()

        override suspend fun balas(pesan: List<ChatMessage>): String {
            this.pesan += pesan
            val isi = balasan.getOrElse(dipanggil) { balasan.last() }
            dipanggil++
            return isi
        }
    }

    private class PapanPalsu(private val konteks: String = "src/main/java/app/MainActivity.java") :
        PapanTulisProjek {
        val tertulis = ArrayList<BerkasEdit>()

        override fun konteks(): String = konteks

        override fun tulis(edit: BerkasEdit): String? {
            tertulis += edit
            return null
        }
    }

    private class BuildPalsu(private val hasil: List<HasilBuild>) : GerbangBuild {
        var dipanggil = 0

        override suspend fun build(): HasilBuild {
            val sekarang = hasil.getOrElse(dipanggil) { hasil.last() }
            dipanggil++
            return sekarang
        }
    }

    private fun balasan(nama: String) = """
        <<<BERKAS: $nama>>>
        ```java
        class Isi {}
        ```
    """.trimIndent()

    @Test
    fun berhasil_bila_build_pertama_langsung_jadi() = runTest {
        val agen = AgenBuild(
            model = ModelPalsu(listOf(balasan("src/main/java/app/MainActivity.java"))),
            papan = PapanPalsu(),
            build = BuildPalsu(listOf(HasilBuild(sukses = true))),
        )

        val hasil = agen.jalankan("buat tombol simpan")

        assertTrue(hasil.sukses)
        assertEquals(1, hasil.putaran)
        assertEquals(1, hasil.berkas.size)
    }

    @Test
    fun galat_build_dikirim_balik_ke_model_pada_putaran_berikutnya() = runTest {
        val model = ModelPalsu(listOf(balasan("src/main/java/app/A.java"), balasan("src/main/java/app/B.java")))
        val build = BuildPalsu(
            listOf(
                HasilBuild(false, "Galat di baris 4: simbol 'tombol' tidak ditemukan"),
                HasilBuild(true),
            ),
        )
        val agen = AgenBuild(model = model, papan = PapanPalsu(), build = build)

        val hasil = agen.jalankan("perbaiki tombol")

        assertTrue(hasil.sukses)
        assertEquals(2, hasil.putaran)
        assertEquals(2, hasil.berkas.size)
        val pesanKedua = model.pesan[1].last().isi
        assertTrue(pesanKedua.contains("simbol 'tombol' tidak ditemukan"), "galat build harus ikut dikirim")
    }

    @Test
    fun berhenti_setelah_batas_putaran_dan_melaporkan_galat_terakhir() = runTest {
        val agen = AgenBuild(
            model = ModelPalsu(listOf(balasan("src/A.java"))),
            papan = PapanPalsu(),
            build = BuildPalsu(listOf(HasilBuild(false, "masih gagal"))),
            maksPutaran = 2,
        )

        val hasil = agen.jalankan("coba terus")

        assertFalse(hasil.sukses)
        assertEquals(2, hasil.putaran)
        assertEquals("masih gagal", hasil.galat)
    }

    @Test
    fun balasan_tanpa_berkas_dilaporkan_jelas_bukan_dianggap_sukses() = runTest {
        val agen = AgenBuild(
            model = ModelPalsu(listOf("Saya sarankan pakai Kotlin saja.")),
            papan = PapanPalsu(),
            build = BuildPalsu(listOf(HasilBuild(sukses = true))),
        )

        val hasil = agen.jalankan("pakai kotlin")

        assertFalse(hasil.sukses)
        assertEquals(0, hasil.berkas.size)
        assertTrue(
            hasil.catatan.any { it.contains("tidak mengirim berkas") },
            "pengguna harus diberi tahu bahwa model tidak mengirim berkas",
        )
    }

    @Test
    fun build_tidak_dijalankan_bila_semua_berkas_ditolak_papan_tulis() = runTest {
        val papan = object : PapanTulisProjek {
            override fun konteks(): String = ""
            override fun tulis(edit: BerkasEdit): String? = "tidak punya izin menulis"
        }
        val build = BuildPalsu(listOf(HasilBuild(sukses = true)))
        val agen = AgenBuild(
            model = ModelPalsu(listOf(balasan("src/A.java"))),
            papan = papan,
            build = build,
        )

        val hasil = agen.jalankan("tulis berkas")

        assertFalse(hasil.sukses)
        assertEquals(0, build.dipanggil, "build tidak berguna kalau tidak ada berkas yang tertulis")
        assertTrue(hasil.galat.contains("tidak punya izin menulis"))
    }
}
