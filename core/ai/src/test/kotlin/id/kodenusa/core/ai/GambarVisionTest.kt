package id.kodenusa.core.ai

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress

/**
 * Gambar harus ikut sebagai bagian multimodal, bukan dibuang diam-diam.
 * Uji ini memakai server HTTP lokal, jadi tidak ada jaringan luar yang tersentuh.
 */
class GambarVisionTest {

    private lateinit var server: HttpServer
    private var badanTerakhir: String = ""

    @BeforeEach
    fun nyalakan() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/v1/chat/completions") { exchange ->
            badanTerakhir = exchange.requestBody.readBytes().toString(Charsets.UTF_8)
            val balasan = """data: {"choices":[{"delta":{"content":"terlihat"}}]}""" + "\n\ndata: [DONE]\n\n"
            exchange.responseHeaders.add("Content-Type", "text/event-stream")
            exchange.sendResponseHeaders(200, balasan.toByteArray().size.toLong())
            exchange.responseBody.use { it.write(balasan.toByteArray()) }
        }
        server.start()
    }

    @AfterEach
    fun matikan() {
        server.stop(0)
    }

    private fun config() = AiConfig(
        baseUrl = "http://127.0.0.1:${server.address.port}/v1",
        apiKey = "kunci-uji",
        model = "model-uji",
    )

    @Test
    fun gambar_dikirim_sebagai_bagian_image_url() = runBlocking {
        val pesan = listOf(
            ChatMessage(
                peran = ChatMessage.Peran.PENGGUNA,
                isi = "Apa yang salah di layar ini?",
                gambar = listOf("data:image/png;base64,AAAA"),
            ),
        )
        OpenAiCompatClient().bicara(config(), pesan)

        assertTrue(badanTerakhir.contains("\"image_url\""), badanTerakhir)
        assertTrue(badanTerakhir.contains("data:image/png;base64,AAAA"), badanTerakhir)
        assertTrue(badanTerakhir.contains("\"type\":\"text\""), badanTerakhir)
    }

    @Test
    fun tanpa_gambar_content_tetap_teks_biasa() = runBlocking {
        OpenAiCompatClient().bicara(config(), listOf(ChatMessage(ChatMessage.Peran.PENGGUNA, "halo")))

        assertTrue(badanTerakhir.contains("\"content\":\"halo\""), badanTerakhir)
        assertFalse(badanTerakhir.contains("image_url"), badanTerakhir)
    }
}
