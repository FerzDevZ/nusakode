package id.kodenusa.core.ai

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test

/**
 * Uji sambungan ke endpoint sungguhan milik pengguna.
 *
 * Dilewati (skip) kalau variabel lingkungan tidak ada, supaya CI tanpa kunci tetap hijau. Untuk
 * menjalankannya:
 *
 * ```
 * AI_ENDPOINT=https://api.crown.ai.id/v1 AI_KEY=... AI_MODEL=... \
 *   ./gradlew :core:ai:test --tests '*ChatNyataTest*'
 * ```
 */
class ChatNyataTest {

    private val endpoint: String? = System.getenv("AI_ENDPOINT")
    private val kunci: String? = System.getenv("AI_KEY")
    private val model: String? = System.getenv("AI_MODEL")

    private fun siap(): AiConfig {
        assumeTrue(!endpoint.isNullOrBlank() && !kunci.isNullOrBlank(), "AI_ENDPOINT/AI_KEY tidak diisi")
        val dasar = AiConfig(endpoint.orEmpty(), kunci.orEmpty())
        if (!model.isNullOrBlank()) return dasar.denganModel(model.orEmpty())
        val daftar = runBlocking { OpenAiCompatClient().daftarModel(dasar) }
        return dasar.denganModel(daftar.first().id)
    }

    @Test
    fun daftar_model_terbaca_dari_endpoint_sungguhan() = runBlocking {
        assumeTrue(!endpoint.isNullOrBlank() && !kunci.isNullOrBlank(), "AI_ENDPOINT/AI_KEY tidak diisi")
        val daftar = OpenAiCompatClient().daftarModel(AiConfig(endpoint.orEmpty(), kunci.orEmpty()))

        assertTrue(daftar.isNotEmpty())
        println("MODEL: " + daftar.joinToString { "${it.id} (${it.pemilik})" })
    }

    @Test
    fun bicara_ke_model_sungguhan_dan_terima_jawaban() = runBlocking {
        val config = siap()
        val potongan = StringBuilder()

        val hasil = OpenAiCompatClient().bicara(
            config = config,
            pesan = PromptBuilder.untukTanya("Jawab singkat: satu perintah untuk mencetak teks di Java."),
            onDelta = { potongan.append(it) },
        )

        assertTrue(hasil.isNotBlank(), "jawaban kosong")
        println("MODEL DIPAKAI: ${config.model}")
        println("JAWABAN: " + hasil.take(400))
        assertTrue(potongan.isNotEmpty(), "tidak ada potongan yang dialirkan")
    }
}
