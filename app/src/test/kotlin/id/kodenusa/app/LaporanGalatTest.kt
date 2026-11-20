package id.kodenusa.app

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Uji murni (tanpa Android) untuk isi laporan galat: rahasia tidak boleh lolos, dan isi yang
 * berguna harus tetap ada. Fungsi Android (`tulis`, `baca`) diuji di perangkat.
 */
class LaporanGalatTest {

    private val info = InfoLaporan(
        versi = "0.9.8",
        sdk = 35,
        perangkat = "Redmi 9A",
        ramMb = 3845,
        waktu = 1_700_000_000_000L,
    )

    @Test
    fun `laporan memuat versi, perangkat, ram, dan galat`() {
        val galat = IllegalStateException("Gagal membuka projek")
        val teks = LaporanGalat.bangun(galat, info)

        assertTrue(teks.contains("KodeNusa 0.9.8"), teks)
        assertTrue(teks.contains("Redmi 9A"), teks)
        assertTrue(teks.contains("RAM terpasang: 3845 MB"), teks)
        assertTrue(teks.contains("Android SDK 35"), teks)
        assertTrue(teks.contains("IllegalStateException: Gagal membuka projek"), teks)
    }

    @Test
    fun `token github tidak pernah muncul di laporan`() {
        val galat = RuntimeException("push gagal: https://x-access-token:ghp_Abcdef12345678901234@github.com/x")
        val teks = LaporanGalat.bangun(galat, info)

        assertFalse(teks.contains("ghp_Abcdef12345678901234"), teks)
        assertTrue(teks.contains("<disembunyikan>"), teks)
    }

    @Test
    fun `kunci ai dan api key umum juga disaring`() {
        val teks = LaporanGalat.saringRahasia(
            "Authorization: Bearer crown-27f7af7aab27f07dfe005f97c6549138\n" +
                "api_key = sk-abc123def456ghi789\n" +
                "password: rahasia123",
        )
        assertFalse(teks.contains("crown-27f7"), teks)
        assertFalse(teks.contains("sk-abc123"), teks)
        assertFalse(teks.contains("rahasia123"), teks)
        assertEquals(3, Regex("<disembunyikan>").findAll(teks).count())
    }

    @Test
    fun `jejak tumpukan dipotong supaya laporan tetap kecil`() {
        val galat = RuntimeException("besar").apply {
            stackTrace = Array(500) { i -> StackTraceElement("Kelas$i", "metode$i", "Berkas$i.kt", i) }
        }
        val teks = LaporanGalat.bangun(galat, info)
        val barisJejak = teks.substringAfter("Jejak tumpukan:").trim().lines().count { it.trimStart().startsWith("at ") }
        assertTrue(barisJejak <= 60, "baris jejak = $barisJejak")
        assertTrue(barisJejak >= 50, "baris jejak = $barisJejak")
    }

    @Test
    fun `galat tanpa pesan tetap menghasilkan laporan`() {
        val teks = LaporanGalat.bangun(NullPointerException(), info)
        assertTrue(teks.contains("NullPointerException"), teks)
    }
}
