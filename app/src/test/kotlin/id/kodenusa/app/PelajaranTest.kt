package id.kodenusa.app

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Uji mode belajar (docs/14 3.3): parser `pelajaran.md` tahan terhadap berkas rusak/kosong,
 * dan progres per projek tersimpan serta terbaca kembali.
 */
class PelajaranTest {

    @TempDir
    lateinit var akar: File

    @Test
    fun parser_mengambil_langkah_judul_dan_isi() {
        val isi = """
            # Pelajaran uji

            ## 1. Kenali layar
            Buka berkasnya. Lalu lihat isinya.

            ## 2. Ubah teks
            Ubah strings.xml
        """.trimIndent()
        val pelajaran = PelajaranParser.parse("uji", isi)
        assertNotNull(pelajaran)
        assertEquals(2, pelajaran!!.langkah.size)
        assertEquals("Kenali layar", pelajaran.langkah[0].judul)
        // Baris isi digabung menjadi satu paragraf, baris kosong dibuang.
        assertEquals("Buka berkasnya. Lalu lihat isinya.", pelajaran.langkah[0].isi)
        assertEquals(2, pelajaran.langkah[1].nomor)
    }

    @Test
    fun parser_menolak_berkas_tanpa_langkah() {
        assertNull(PelajaranParser.parse("uji", "# Hanya judul\n\ntidak ada langkah"))
        assertNull(PelajaranParser.parse("uji", ""))
    }

    @Test
    fun parser_tahan_baris_tak_beraturan() {
        // Baris "##" tanpa nomor diabaikan; langkah tetap terbaca dari yang sah.
        val pelajaran = PelajaranParser.parse("uji", "## pengantar\n## 1. Satu\nisi satu")
        assertNotNull(pelajaran)
        assertEquals(1, pelajaran!!.langkah.size)
        assertEquals("Satu", pelajaran.langkah[0].judul)
    }

    @Test
    fun muat_menolak_tanpa_template_dan_tanpa_aset() {
        val store = PelajaranStore(aset = { null }, akarProgres = akar)
        assertNull(store.muat(null))
        assertNull(store.muat(""))
        // Template tanpa pelajaran.md (aset null) → null, bukan pelajaran kosong.
        assertNull(store.muat("kasir-warung"))
    }

    @Test
    fun muat_aset_dengan_konten_pelajaran_menghasilkan_langkah() {
        val store = PelajaranStore(aset = { "## 1. Satu\nisi" }, akarProgres = akar)
        val pelajaran = store.muat("halo-dunia")
        assertNotNull(pelajaran)
        assertEquals(1, pelajaran!!.langkah.size)
        assertEquals("halo-dunia", pelajaran.templateId)
    }

    @Test
    fun progres_tersimpan_dan_terbaca_kembali() {
        val store = PelajaranStore(aset = { null }, akarProgres = akar)
        assertEquals(1, store.bacaProgres("projek-a"))
        store.simpanProgres("projek-a", 3)
        assertEquals(3, store.bacaProgres("projek-a"))
        // Progres projek lain tidak tercampur.
        assertEquals(1, store.bacaProgres("projek-b"))
    }

    @Test
    fun progres_rusak_kembali_ke_langkah_1() {
        val store = PelajaranStore(aset = { null }, akarProgres = akar)
        File(akar, "projek-c.txt").writeText("bukan angka")
        assertEquals(1, store.bacaProgres("projek-c"))
    }

    @Test
    fun progres_dipagasi_ke_rentang_wajar() {
        val store = PelajaranStore(aset = { null }, akarProgres = akar)
        store.simpanProgres("projek-d", 500)
        assertTrue(store.bacaProgres("projek-d") <= 99)
    }

    /** Semua uji di berkas ini JVM murni: aset dipalsukan lewat lambda, bukan Context Android. */
}
