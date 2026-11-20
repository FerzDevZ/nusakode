package id.kodenusa.core.ai

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Uji klien terhadap server HTTP sungguhan di localhost (bukan mock). Dengan begitu bentuk balasan,
 * header, dan kode status benar-benar diuji seperti di HP.
 */
class OpenAiCompatClientTest {

    private lateinit var server: HttpServer
    private var jalurTerakhir: String = ""
    private var metodeTerakhir: String = ""
    private var badanTerakhir: String = ""
    private var otorisasiTerakhir: String? = null
    private var balasan: (HttpExchange) -> Unit = {}

    private val klien = OpenAiCompatClient(waktuSambungMs = 3_000, waktuBacaMs = 5_000, jedaUlangMs = 10)

    @BeforeEach
    fun nyalakan() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            jalurTerakhir = exchange.requestURI.path
            metodeTerakhir = exchange.requestMethod
            otorisasiTerakhir = exchange.requestHeaders.getFirst("Authorization")
            badanTerakhir = exchange.requestBody.readBytes().toString(Charsets.UTF_8)
            balasan(exchange)
        }
        server.start()
    }

    @AfterEach
    fun matikan() = server.stop(0)

    private fun dasar(): String = "http://127.0.0.1:${server.address.port}/v1"

    private fun config(model: String = "zai-org/GLM-5.3-Flash"): AiConfig =
        AiConfig(baseUrl = dasar(), apiKey = "kunci-uji", model = model)

    private fun kirimJson(exchange: HttpExchange, kode: Int, isi: String, tipe: String = "application/json") {
        val byte = isi.toByteArray(Charsets.UTF_8)
        exchange.responseHeaders.add("Content-Type", tipe)
        exchange.sendResponseHeaders(kode, byte.size.toLong())
        exchange.responseBody.use { it.write(byte) }
    }

    @Test
    fun daftar_model_dibaca_dari_endpoint_pengguna() = runTest {
        balasan = { ex ->
            kirimJson(
                ex,
                200,
                """{"object":"list","data":[
                    {"id":"zai-org/GLM-5.3-Flash","object":"model","owned_by":"crown"},
                    {"id":"deepseek-ai/DeepSeek-V4-Flash-0731","object":"model","owned_by":"crown"}
                ]}""",
            )
        }

        val model = klien.daftarModel(config())

        assertEquals(listOf("zai-org/GLM-5.3-Flash", "deepseek-ai/DeepSeek-V4-Flash-0731"), model.map { it.id })
        assertEquals("GLM-5.3-Flash", model.first().label())
        assertEquals("/v1/models", jalurTerakhir)
        assertEquals("GET", metodeTerakhir)
        assertEquals("Bearer kunci-uji", otorisasiTerakhir)
    }

    @Test
    fun kunci_salah_menghasilkan_pesan_401_yang_jelas() = runTest {
        balasan = { ex -> kirimJson(ex, 401, """{"error":{"message":"invalid api key"}}""") }

        val gagal = runCatching { klien.daftarModel(config()) }.exceptionOrNull() as AiGagal

        assertEquals(AiGagal.Jenis.KUNCI_SALAH, gagal.jenis)
        assertTrue(gagal.saran.contains("kuncinya"))
    }

    @Test
    fun alamat_tanpa_v1_tetap_bisa_dipakai() = runTest {
        balasan = { ex -> kirimJson(ex, 200, """{"data":[{"id":"model-a"}]}""") }

        val model = klien.daftarModel(AiConfig("http://127.0.0.1:${server.address.port}/v1/", "k"))

        assertEquals("/v1/models", jalurTerakhir)
        assertEquals("model-a", model.single().id)
    }

    @Test
    fun aliran_sse_content_dan_reasoning_dirakit_benar() = runTest {
        balasan = { ex ->
            val potongan = listOf(
                """data: {"choices":[{"delta":{"content":"","role":"assistant"}}]}""",
                """data: {"choices":[{"delta":{"reasoning":"Berpikir"}}]}""",
                """data: {"choices":[{"delta":{"content":"Halo"}}]}""",
                """data: {"choices":[{"delta":{"content":", dunia"}}]}""",
                "data: [DONE]",
                "",
            )
            val isi = potongan.joinToString("\n")
            ex.responseHeaders.add("Content-Type", "text/event-stream")
            ex.sendResponseHeaders(200, 0)
            ex.responseBody.use { it.write(isi.toByteArray()) }
        }
        val kumpulan = StringBuilder()
        val pikiran = StringBuilder()

        val hasil = klien.bicara(
            config(),
            PromptBuilder.untukTanya("sapa saya"),
            onReasoning = { pikiran.append(it) },
            onDelta = { kumpulan.append(it) },
        )

        assertEquals("Halo, dunia", hasil)
        assertEquals("Halo, dunia", kumpulan.toString())
        assertEquals("Berpikir", pikiran.toString())
        assertEquals("/v1/chat/completions", jalurTerakhir)
        assertEquals("POST", metodeTerakhir)
        assertTrue(badanTerakhir.contains("\"stream\":true"))
        assertTrue(badanTerakhir.contains("zai-org/GLM-5.3-Flash"))
    }

    @Test
    fun penyedia_yang_mengabaikan_stream_tetap_terbaca() = runTest {
        balasan = { ex ->
            kirimJson(ex, 200, """{"choices":[{"message":{"role":"assistant","content":"Jawaban utuh"}}]}""")
        }

        val hasil = klien.bicara(config(), PromptBuilder.untukTanya("halo"))

        assertEquals("Jawaban utuh", hasil)
    }

    @Test
    fun galat_503_dicoba_ulang_sekali_lalu_berhasil() = runTest {
        var panggilan = 0
        balasan = { ex ->
            panggilan += 1
            if (panggilan == 1) {
                kirimJson(ex, 503, """{"error":{"message":"service_unavailable"}}""")
            } else {
                kirimJson(ex, 200, """{"choices":[{"message":{"content":"Pulih"}}]}""")
            }
        }

        val hasil = klien.bicara(config(), PromptBuilder.untukTanya("halo"))

        assertEquals("Pulih", hasil)
        assertEquals(2, panggilan)
    }

    @Test
    fun tanpa_kunci_langsung_ditolak_tanpa_jaringan() = runTest {
        val gagal = runCatching {
            klien.bicara(AiConfig("", ""), PromptBuilder.untukTanya("halo"))
        }.exceptionOrNull() as AiGagal

        assertEquals(AiGagal.Jenis.BELUM_DIATUR, gagal.jenis)
    }

    @Test
    fun pembatalan_menghentikan_aliran() = runTest {
        balasan = { ex ->
            ex.responseHeaders.add("Content-Type", "text/event-stream")
            ex.sendResponseHeaders(200, 0)
            ex.responseBody.use { keluar ->
                repeat(50) { ke ->
                    keluar.write("""data: {"choices":[{"delta":{"content":"kata$ke "}}]}""" .toByteArray())
                    keluar.write("\n".toByteArray())
                    keluar.flush()
                }
            }
        }
        var dihitung = 0

        val gagal = runCatching {
            klien.bicara(config(), PromptBuilder.untukTanya("panjang"), batalkan = { dihitung > 2 }) {
                dihitung += 1
            }
        }.exceptionOrNull() as AiGagal

        assertEquals(AiGagal.Jenis.DIBATALKAN, gagal.jenis)
        assertTrue(dihitung < 10)
    }

    @Test
    fun model_tanpa_dukungan_gambar_dijelaskan_bukan_disebut_galat_penyedia() = runTest {
        // Bentuk balasan nyata dari penyedia yang modelnya hanya teks.
        balasan = { ex ->
            kirimJson(
                ex,
                400,
                """{"error":{"message":"Invalid content type. image_url is only supported by certain models."}}""",
            )
        }

        val gagal = runCatching { klien.daftarModel(config()) }.exceptionOrNull() as AiGagal

        assertEquals(AiGagal.Jenis.GAMBAR_TIDAK_DIDUKUNG, gagal.jenis)
        assertTrue(gagal.pesan.contains("tidak bisa membaca gambar"))
        assertTrue(gagal.saran.contains("gpt-4o"))
    }

    @Test
    fun galat_400_lain_tetap_disebut_galat_penyedia() = runTest {
        balasan = { ex -> kirimJson(ex, 400, """{"error":{"message":"max tokens too large"}}""") }

        val gagal = runCatching { klien.daftarModel(config()) }.exceptionOrNull() as AiGagal

        assertEquals(AiGagal.Jenis.PENYEDIA, gagal.jenis)
        assertTrue(gagal.saran.contains("max tokens too large"))
    }
}
