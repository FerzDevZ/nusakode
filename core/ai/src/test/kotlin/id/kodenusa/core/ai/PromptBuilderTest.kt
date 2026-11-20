package id.kodenusa.core.ai

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PromptBuilderTest {

    @Test
    fun pesan_sistem_melarang_kotlin_dan_gradle() {
        val sistem = PromptBuilder.SISTEM

        assertTrue(sistem.contains("Java 8"))
        assertTrue(sistem.contains("minSdk 24"))
        assertTrue(sistem.contains("tanpa Gradle"))
        assertTrue(sistem.contains("bahasa Indonesia"))
        assertTrue(sistem.contains("Jangan pernah menyarankan Kotlin"))
    }

    @Test
    fun galat_build_dikirim_bersama_berkas_tersangka() {
        val pesan = PromptBuilder.untukGalat(
            galat = "error: cannot find symbol\n  variabel tombol",
            berkas = "src/main/java/app/MainActivity.java",
            isiBerkas = "class MainActivity { void a() { tombol.setText(\"x\"); } }",
        )

        assertEquals(2, pesan.size)
        assertEquals(ChatMessage.Peran.SISTEM, pesan[0].peran)
        val isi = pesan[1].isi
        assertTrue(isi.contains("cannot find symbol"))
        assertTrue(isi.contains("src/main/java/app/MainActivity.java"))
        assertTrue(isi.contains("tombol.setText"))
        assertTrue(isi.contains("sudah diperbaiki"))
    }

    @Test
    fun berkas_panjang_dipotong_dengan_penanda_yang_jujur() {
        val besar = "a".repeat(20_000)

        val hasil = PromptBuilder.potong(besar, 1_000)

        assertTrue(hasil.length < 1_200)
        assertTrue(hasil.contains("dipotong agar hemat kuota"))
        assertTrue(hasil.startsWith("a"))
        assertTrue(hasil.endsWith("a"))
    }

    @Test
    fun teks_pendek_tidak_disentuh() {
        assertEquals("pendek", PromptBuilder.potong("pendek", 100))
    }

    @Test
    fun pertanyaan_bebas_hanya_membawa_sistem_dan_pertanyaan() {
        val pesan = PromptBuilder.untukTanya("  apa itu Activity?  ")

        assertEquals(2, pesan.size)
        assertEquals("apa itu Activity?", pesan[1].isi)
    }
}

class CodeBlocksTest {

    @Test
    fun blok_kode_diambil_beserta_bahasanya() {
        val jawaban = """
            Berikut perbaikannya:
            ```java
            class A { }
            ```
            Semoga membantu.
        """.trimIndent()

        val blok = CodeBlocks.ambil(jawaban)

        assertEquals(1, blok.size)
        assertEquals("java", blok[0].bahasa)
        assertEquals("class A { }", blok[0].isi)
    }

    @Test
    fun blok_terpanjang_dipilih_sebagai_berkas_utama() {
        val jawaban = """
            ```java
            class Kecil { }
            ```
            lalu berkas lengkapnya:
            ```java
            class Besar {
                void satu() { }
                void dua() { }
            }
            ```
        """.trimIndent()

        val utama = CodeBlocks.terbesar(jawaban)

        assertTrue(utama?.isi?.contains("void dua()") == true)
    }

    @Test
    fun jawaban_tanpa_kode_tidak_menghasilkan_blok() {
        assertTrue(CodeBlocks.ambil("Tidak ada kode di sini.").isEmpty())
        assertNull(CodeBlocks.terbesar("Tidak ada kode di sini."))
    }

    @Test
    fun penjelasan_bisa_dibaca_tanpa_pagar_kode() {
        val bersih = CodeBlocks.tanpaPagar("Penyebabnya lupa import.\n```java\nclass A{}\n```\nSelesai.")

        assertFalse(bersih.contains("```"))
        assertTrue(bersih.contains("Penyebabnya lupa import."))
    }
}
