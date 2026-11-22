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

class PromptBuilderAturanTest {
        @Test
        fun aturan_agen_mengizinkan_berkas_baru_secara_eksplisit() {
            // Bug nyata: aturan lama hanya menyebut "berkas yang berubah", sehingga model menolak
            // membuat berkas baru meski tugasnya jelas membutuhkan.
            assertTrue(
                PromptBuilder.ATURAN_AGEN.contains("BARU"),
                "aturan agen harus menyebut berkas BARU",
            )
            assertTrue(
                PromptBuilder.ATURAN_AGEN.contains("Membuat berkas BARU diperbolehkan"),
                "aturan agen harus mengizinkan pembuatan berkas baru",
            )
            assertTrue(
                PromptBuilder.ATURAN_AGEN.contains("folder"),
                "aturan agen harus menjelaskan folder dibuat otomatis",
            )
        }

        @Test
        fun berkas_sematan_dikirim_utuh_bukan_ringkasan() {
            // Saran pengguna: berkas yang disematkan harus dikirim utuh, bukan ringkasan 2.400 karakter
            // yang memotong bagian penting berkas panjang.
            val isiPanjang = buildString { repeat(300) { appendLine("baris ke-$it") } }
            val pesan = PromptBuilder.untukBerkasSematan(
                "jelaskan kelas ini",
                listOf("src/main/java/app/DatabaseKasir.java" to isiPanjang),
            )

            val isi = pesan.last().isi
            assertTrue(isi.contains("src/main/java/app/DatabaseKasir.java"))
            assertTrue(isi.contains("baris ke-299"), "isi berkas harus sampai baris terakhir")
            assertTrue(isi.contains("jelaskan kelas ini"))
        }

        @Test
        fun berkas_sematan_lebih_dari_satu_semua_tercantum() {
            val pesan = PromptBuilder.untukBerkasSematan(
                "bandingkan",
                listOf(
                    "a.java" to "isi A",
                    "b.java" to "isi B",
                    "c.java" to "isi C",
                ),
            )

            val isi = pesan.last().isi
            assertTrue(isi.contains("--- a.java ---"))
            assertTrue(isi.contains("--- b.java ---"))
            assertTrue(isi.contains("--- c.java ---"))
            assertTrue(isi.contains("isi A") && isi.contains("isi B") && isi.contains("isi C"))
        }

        @Test
        fun templat_kasir_warung_mengajarkan_database_kasir() {
            // Saran #7 docs/12: model harus tahu struktur tipikal template, bukan menebak.
            val templat = PromptBuilder.templatProjek("kasir-warung")

            assertTrue(templat != null)
            assertTrue(templat!!.contains("DatabaseKasir"), "harus menyebut kelas basis data yang ada")
            assertTrue(templat.contains("Produk.java"))
        }

        @Test
        fun templat_tak_dikenal_menghasilkan_null_tidak_mengarang() {
            assertNull(PromptBuilder.templatProjek("template-aneh"))
            assertNull(PromptBuilder.templatProjek(null))
        }

        @Test
        fun pesan_agen_menyertakan_templat_bila_ada() {
            val pesan = PromptBuilder.untukAgen(
                "tambah layar riwayat",
                "src/main/java/app/MainActivity.java",
                "",
                PromptBuilder.templatProjek("kasir-warung"),
            )

            assertTrue(pesan.last().isi.contains("DatabaseKasir"))
            assertTrue(pesan.last().isi.contains("tambah layar riwayat"))
        }

    @Test
    fun hemat_kuota_memangkas_konteks_berkas() {
        val isi = "x".repeat(4_000)
        val normal = PromptBuilder.untukBerkas("perbaiki", "a.java", isi)
        val hemat = PromptBuilder.untukBerkas("perbaiki", "a.java", isi, hemat = true)
        // Mode hemat mengirim sekitar setengah konteks: pastikan jelas lebih pendek dan penanda
        // pemotongan tetap jujur muncul.
        assertTrue(hemat.last().isi.length < normal.last().isi.length)
        assertTrue(hemat.last().isi.contains("hemat kuota"))
    }

    @Test
    fun hemat_kuota_membatasi_sematan_maksimal_dua_berkas() {
        val berkas = (1..3).map { "b$it.java" to "isi$it" }
        val normal = PromptBuilder.untukBerkasSematan("ubah", berkas)
        val hemat = PromptBuilder.untukBerkasSematan("ubah", berkas, hemat = true)
        assertTrue(normal.last().isi.contains("b3.java"))
        // docs/13 C5: maksimal 2 berkas saat hemat — berkas ketiga tidak dikirim.
        assertTrue(!hemat.last().isi.contains("b3.java"))
        assertTrue(hemat.last().isi.contains("b2.java"))
    }

    @Test
    fun hemat_kuota_memangkas_konteks_agen() {
        val konteks = "y".repeat(4_000)
        val normal = PromptBuilder.untukAgen("tugas", konteks, "")
        val hemat = PromptBuilder.untukAgen("tugas", konteks, "", hemat = true)
        assertTrue(hemat.last().isi.length < normal.last().isi.length)
    }

    @Test
    fun templat_baru_v0_16_dikenali() {
        // docs/13 C4: dua template baru. Prompt agen harus menyebut kelas datanya supaya model
        // mengikuti pola yang sudah ada, bukan membuat basis data sendiri.
        assertTrue(PromptBuilder.templatProjek("absensi-kelas")!!.contains("DatabaseAbsensi"))
        assertTrue(PromptBuilder.templatProjek("laporan-warung")!!.contains("DatabaseLaporan"))
    }

    @Test
    fun scaffold_menyuruh_format_berkas_dan_penanda_wajib() {
        val pesan = PromptBuilder.untukScaffold("aplikasi pencatat utang")
        // Pesan pengguna harus menyebut kelima berkas wajib + penanda pengganti nama.
        val isi = pesan.last().isi
        assertTrue(isi.contains("kodenusa.json"))
        assertTrue(isi.contains("AndroidManifest.xml"))
        assertTrue(isi.contains("MainActivity.java"))
        assertTrue(isi.contains("__APP_NAME__"))
        assertTrue(isi.contains("__APP_ID__"))
        assertTrue(isi.contains("<<<BERKAS"))
        // Deskripsi pengguna diteruskan apa adanya.
        assertTrue(isi.contains("pencatat utang"))
        // Aturan Tier 0 tetap dikirim lewat ATURAN_AGEN.
        assertTrue(pesan.any { it.isi.contains("AndroidX") })
    }
    @Test
    fun `chat_biasa_juga_mengajarkan_penanda_berkas`() {
        // Keluhan pengguna v0.21: minta "buat web login register" di chat biasa, model balas blok
        // kode polos tanpa nama berkas — satu-satunya jalan adalah tombol sisip yang menyuntik SATU
        // blok ke berkas AKTIF, jadi seluruh HTML/CSS nyangkut di aplikasi.js. Format <<<BERKAS:>>>
        // kini wajib diajarkan di jalur chat otomatis juga, bukan hanya jalur agen.
        val pesan = PromptBuilder.untukTanyaOtomatis("buat web login register", "PETA", emptyList())

        assertTrue(
            pesan.any { it.isi.contains("<<<BERKAS:") },
            "aturan penanda berkas harus ikut di chat biasa",
        )
        // Aturan satu-berkas-satu-jenis ikut, supaya HTML tidak berakhir di dalam string JS.
        assertTrue(pesan.any { it.isi.contains("DILARANG menaruh HTML") })
    }

    @Test
    fun `pelajari_projek_tidak_membawa_aturan_tulis_berkas`() {
        // "Pelajari projek" hanya minta ringkasan — aturan tulis berkas hanya memboroskan kuota.
        val pesan = PromptBuilder.untukPelajari("PETA", emptyList())

        assertTrue(pesan.none { it.isi.contains("<<<BERKAS:") })
    }

    @Test
    fun `aturan_agen_melarang_html_di_dalam_javascript`() {
        assertTrue(PromptBuilder.ATURAN_AGEN.contains("DILARANG menaruh HTML"))
        assertTrue(PromptBuilder.ATURAN_AGEN.contains("setiap halaman web berkas sendiri"))
    }

    @Test
    fun `templat_web_mengajarkan_berkas_per_jenis`() {
        val situs = PromptBuilder.templatProjek("situs-murni")!!
        assertTrue(situs.contains("berkas .html"))
        assertTrue(situs.contains("login.html"))
        assertTrue(situs.contains("localStorage"))

        val katalog = PromptBuilder.templatProjek("web-katalog")!!
        assertTrue(katalog.contains("ATURAN WEB"))
    }

    @Test
    fun `chat_otomatis_membawa_struktur_template_web`() {
        val pesan = PromptBuilder.untukTanyaOtomatis(
            "buat halaman login",
            "PETA",
            emptyList(),
            templat = PromptBuilder.templatProjek("situs-murni"),
        )

        assertTrue(pesan.last().isi.contains("web/index.html"))
    }

    @Test
    fun `saran_jalur_dari_penanda_dan_dari_nama_berkas_di_teks`() {
        // Penanda <<<BERKAS:>>> menang — jalur paling pasti.
        assertEquals(
            "web/login.html",
            CodeBlocks.saranJalur("ini halamannya\n<<<BERKAS: web/login.html>>>\n```html\n<x>\n```", "html"),
        )
        // Tanpa penanda: nama berkas .html di teks cocok dengan blok html.
        assertEquals(
            "login.html",
            CodeBlocks.saranJalur("Simpan ini sebagai login.html ya.\n```html\n<x>\n```", "html"),
        )
        // Jenis tidak cocok (blok css tapi yang disebut .js) → null, jangan menebak salah.
        assertNull(CodeBlocks.saranJalur("lihat aplikasi.js\n```css\nbody{}\n```", "css"))
        // Tidak ada petunjuk sama sekali.
        assertNull(CodeBlocks.saranJalur("tanpa nama berkas", "java"))
    }
}
