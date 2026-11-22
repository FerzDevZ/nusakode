package id.kodenusa.app

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Uji cadangan otomatis harian (docs/09 P2 #10): satu zip per hari, maksimal dua salinan,
 * tidak pernah menulis ke dalam folder projek pengguna.
 */
class CadanganOtomatisTest {

    @TempDir
    lateinit var sementara: File

    private fun projek(nama: String): File {
        val dir = File(sementara, "workspace/$nama").apply { mkdirs() }
        File(dir, "kodenusa.json").writeText("{\"name\":\"$nama\"}")
        File(dir, "src/main/java/app").mkdirs()
        File(dir, "src/main/java/app/MainActivity.java").writeText("class MainActivity {}")
        return dir
    }

    private fun cadangan(projek: List<File>): CadanganOtomatis =
        CadanganOtomatis(File(sementara, "cadangan-harian")) { projek }

    private fun hari(ms: Long): Long = ms * 24L * 60 * 60 * 1000

    @Test
    fun tanpa_projek_tidak_membuat_zip_dan_mencatat_alasannya() {
        val cadangan = cadangan(emptyList())

        val hasil = cadangan.jalankan(hari(10))

        assertNull(hasil)
        assertTrue(cadangan.salinan().isEmpty(), "tidak boleh ada zip kosong")
        val status = File(File(sementara, "cadangan-harian"), "status.txt").readText()
        assertTrue(status.contains("belum ada projek"))
    }

    @Test
    fun membuat_zip_harian_dengan_isi_projek_dan_menghentikan_jeda_24_jam() {
        val projek = listOf(projek("tokoku"))
        val cadangan = cadangan(projek)
        val sekarang = hari(10)

        val jumlah = cadangan.jalankan(sekarang)

        assertEquals(1, jumlah)
        val salinan = cadangan.salinan()
        assertEquals(1, salinan.size)
        assertTrue(salinan[0].name.contains("kodenusa-cadangan-"), salinan[0].name)
        val entri = cadangan.isiZip(salinan[0])
        assertTrue(entri.any { it.startsWith("projek/tokoku/") }, entri.joinToString())
        assertTrue(entri.none { it.contains("/build/") }, "folder build tidak ikut")
        assertFalse(cadangan.perluCadangkan(sekarang + 1000), "belum 24 jam")
        assertTrue(cadangan.perluCadangkan(sekarang + hari(1) + 1), "lewat 24 jam harus dicadangkan lagi")
    }

    @Test
    fun menyimpan_maksimal_dua_salinan_dan_menghapus_yang_lebih_tua() {
        val projek = listOf(projek("tokoku"))
        val cadangan = cadangan(projek)
        cadangan.jalankan(hari(1))
        // Zip lama buatan tangan: lebih tua daripada dua salinan terakhir, nama hari yang lain.
        val lama = File(File(sementara, "cadangan-harian"), "kodenusa-cadangan-1970-01-01.zip")
        lama.writeBytes(ByteArray(16))
        lama.setLastModified(hari(1) - 1000)

        cadangan.jalankan(hari(2))

        val salinan = cadangan.salinan()
        assertEquals(2, salinan.size, "maksimal dua salinan")
        assertFalse(lama.exists(), "salinan ketiga dihapus")
        val status = File(File(sementara, "cadangan-harian"), "status.txt").readText()
        assertTrue(status.contains("Cadangan harian dibuat"))
    }

    @Test
    fun projek_tanpa_kodenusa_json_dilewati() {
        val sah = projek("tokoku")
        val takSah = File(sementara, "workspace/lainnya").apply { mkdirs() }
        File(takSah, "catatan.txt").writeText("bukan projek")
        val cadangan = cadangan(listOf(sah, takSah))

        val jumlah = cadangan.jalankan(hari(10))

        assertEquals(1, jumlah, "hanya projek sah yang ikut")
    }
}
