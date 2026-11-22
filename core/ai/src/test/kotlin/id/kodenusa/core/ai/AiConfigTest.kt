package id.kodenusa.core.ai

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Aturan endpoint cadangan (v0.21.0): setengah isi ditolak, model kosong diwarisi dari utama.
 */
class AiConfigTest {

    @Test
    fun cadangan_setengah_isi_dianggap_belum_terpasang() {
        val alamatSaja = AiConfig(
            baseUrl = "https://utama.example/v1",
            apiKey = "kunci",
            cadanganBaseUrl = "https://cadangan.example/v1",
        )
        assertFalse(alamatSaja.cadanganTerpasang, "alamat tanpa kunci tidak boleh dianggap terpasang")

        val kunciSaja = AiConfig(
            baseUrl = "https://utama.example/v1",
            apiKey = "kunci",
            cadanganApiKey = "kunci-cadangan",
        )
        assertFalse(kunciSaja.cadanganTerpasang, "kunci tanpa alamat tidak boleh dianggap terpasang")
    }

    @Test
    fun cadangan_lengkap_dan_model_kosong_mewarisi_model_utama() {
        val config = AiConfig(
            baseUrl = "https://utama.example/v1",
            apiKey = "kunci",
            model = "zai-org/GLM-5.3-Flash",
            cadanganBaseUrl = "https://cadangan.example/v1/",
            cadanganApiKey = "kunci-cadangan",
        )

        assertTrue(config.cadanganTerpasang)
        val cadangan = config.cadangan()
        assertEquals("https://cadangan.example/v1", cadangan.dasar(), "garis miring akhir dibuang")
        assertEquals("zai-org/GLM-5.3-Flash", cadangan.model, "model kosong diwarisi dari utama")
    }

    @Test
    fun cadangan_model_sendiri_dipakai_bila_diisi() {
        val config = AiConfig(
            baseUrl = "https://utama.example/v1",
            apiKey = "kunci",
            model = "model-utama",
            cadanganBaseUrl = "https://cadangan.example/v1",
            cadanganApiKey = "kunci-cadangan",
            cadanganModel = "model-cadangan",
        )

        assertEquals("model-cadangan", config.cadangan().model)
    }

    @Test
    fun tanpa_cadangan_terpasang_tetap_normal() {
        val config = AiConfig(baseUrl = "https://utama.example/v1", apiKey = "kunci")
        assertFalse(config.cadanganTerpasang)
        assertFalse(config.cadangan().terpasang, "cadangan() tanpa isi tidak boleh dianggap terpasang")
    }
}
