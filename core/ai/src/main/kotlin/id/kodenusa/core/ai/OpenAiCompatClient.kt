package id.kodenusa.core.ai

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Klien untuk endpoint apa pun yang berbicara format OpenAI (`/v1/models`, `/v1/chat/completions`).
 *
 * Sengaja tanpa library HTTP pihak ketiga: `HttpURLConnection` sudah ada di Android maupun JVM (untuk
 * uji), sehingga APK tidak bertambah berat dan pengujian jalan tanpa server tiruan tambahan.
 *
 * Kenyataan yang harus ditangani (semuanya ditemukan dari endpoint sungguhan, bukan dari dugaan):
 * - Sebagian penyedia mengirim `delta.reasoning` sebelum `delta.content`; kalau hanya `content` yang
 *   dibaca, jawaban terlihat kosong sampai selesai.
 * - Sebagian mengabaikan `stream: true` dan membalas JSON biasa → harus tetap bisa dibaca.
 * - 503 sesaat sering muncul → dicoba ulang sekali sebelum menyerah.
 */
class OpenAiCompatClient(
    private val waktuSambungMs: Int = 20_000,
    private val waktuBacaMs: Int = 180_000,
    private val percobaanUlang: Int = 1,
    private val jedaUlangMs: Long = 1_200,
) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Mengambil daftar model yang benar-benar ditawarkan endpoint milik pengguna. */
    suspend fun daftarModel(config: AiConfig): List<AiModel> = withContext(Dispatchers.IO) {
        if (!config.terpasang) throw AiGagal.belumDiatur()
        val url = config.url("models")
        val koneksi = buka(url, config.apiKey)
        try {
            koneksi.requestMethod = "GET"
            val kode = koneksi.responseCode
            val isi = bacaTeks(koneksi, kode)
            if (kode !in 200..299) throw dariStatus(kode, url, isi)
            uraiDaftarModel(isi)
        } catch (gagal: AiGagal) {
            throw gagal
        } catch (error: IOException) {
            throw AiGagal.jaringan(error)
        } finally {
            koneksi.disconnect()
        }
    }

    /**
     * Bertanya ke model dan mengalirkan jawaban potongan demi potongan lewat [onDelta].
     * Mengembalikan teks akhir yang utuh.
     */
    suspend fun bicara(
        config: AiConfig,
        pesan: List<ChatMessage>,
        batalkan: () -> Boolean = { false },
        onReasoning: (String) -> Unit = {},
        onDelta: (String) -> Unit = {},
    ): String = withContext(Dispatchers.IO) {
        if (!config.terpasang) throw AiGagal.belumDiatur()
        if (config.model.isBlank()) {
            throw AiGagal(
                AiGagal.Jenis.BELUM_DIATUR,
                "Model belum dipilih.",
                "Tekan \"Muat model\" lalu pilih salah satu model dari daftar endpoint-mu.",
            )
        }
        var sisaPercobaan = percobaanUlang
        while (true) {
            try {
                return@withContext sekaliBicara(config, pesan, batalkan, onReasoning, onDelta)
            } catch (gagal: AiGagal) {
                val layakUlang = gagal.jenis == AiGagal.Jenis.PENYEDIA && sisaPercobaan > 0
                if (!layakUlang) throw gagal
                sisaPercobaan -= 1
                delay(jedaUlangMs)
            }
        }
        @Suppress("UNREACHABLE_CODE")
        ""
    }

    private fun sekaliBicara(
        config: AiConfig,
        pesan: List<ChatMessage>,
        batalkan: () -> Boolean,
        onReasoning: (String) -> Unit,
        onDelta: (String) -> Unit,
    ): String {
        val url = config.url("chat/completions")
        val koneksi = buka(url, config.apiKey)
        try {
            koneksi.requestMethod = "POST"
            koneksi.doOutput = true
            koneksi.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            koneksi.setRequestProperty("Accept", "text/event-stream")
            val badan = badanPermintaan(config, pesan).toString()
            koneksi.outputStream.use { it.write(badan.toByteArray(Charsets.UTF_8)) }

            val kode = koneksi.responseCode
            if (kode !in 200..299) {
                throw dariStatus(kode, url, bacaTeks(koneksi, kode))
            }

            val tipe = koneksi.contentType.orEmpty()
            val pembaca = koneksi.inputStream.bufferedReader(Charsets.UTF_8)
            return if (tipe.contains("event-stream")) {
                bacaAliran(pembaca, batalkan, onReasoning, onDelta)
            } else {
                val mentah = pembaca.readText()
                if (mentah.trimStart().startsWith("data:")) {
                    bacaAliran(mentah.reader().buffered(), batalkan, onReasoning, onDelta)
                } else {
                    val utuh = uraiJawabanUtuh(mentah)
                    onDelta(utuh)
                    utuh
                }
            }
        } catch (gagal: AiGagal) {
            throw gagal
        } catch (error: IOException) {
            throw AiGagal.jaringan(error)
        } finally {
            koneksi.disconnect()
        }
    }

    private fun bacaAliran(
        pembaca: java.io.BufferedReader,
        batalkan: () -> Boolean,
        onReasoning: (String) -> Unit,
        onDelta: (String) -> Unit,
    ): String {
        val jawaban = StringBuilder()
        val pikiran = StringBuilder()
        while (true) {
            if (batalkan()) throw AiGagal(AiGagal.Jenis.DIBATALKAN, "Dihentikan oleh pengguna.")
            val baris = pembaca.readLine() ?: break
            val bersih = baris.trim()
            if (bersih.isEmpty() || bersih.startsWith(":")) continue
            if (!bersih.startsWith("data:")) continue
            val data = bersih.removePrefix("data:").trim()
            if (data == "[DONE]") break
            val potongan = potonganDelta(data) ?: continue
            if (potongan.isi.isNotEmpty()) {
                jawaban.append(potongan.isi)
                onDelta(potongan.isi)
            }
            if (potongan.pikir.isNotEmpty()) {
                pikiran.append(potongan.pikir)
                onReasoning(potongan.pikir)
            }
        }
        // Perbedaan penting: `reasoning` adalah proses berpikir model, bukan jawaban. Kalau ikut
        // digabung, kode yang dimasukkan ke editor akan diawali omonongan model. Ia hanya dipakai
        // sebagai cadangan kalau penyedia sama sekali tidak mengirim `content`.
        if (jawaban.isEmpty() && pikiran.isEmpty()) {
            throw AiGagal.balasanAneh("aliran selesai tanpa isi")
        }
        return jawaban.toString().ifEmpty { pikiran.toString() }
    }

    private class Potongan(val isi: String, val pikir: String)

    private fun potonganDelta(data: String): Potongan? {
        val akar = runCatching { json.parseToJsonElement(data).jsonObject }.getOrNull() ?: return null
        val pilihan = akar["choices"] as? JsonArray ?: return null
        val pertama = pilihan.firstOrNull()?.jsonObject ?: return null
        val delta = pertama["delta"]?.jsonObject
        if (delta != null) {
            val isi = delta["content"]?.stringAtauNull().orEmpty()
            val pikir = delta["reasoning_content"]?.stringAtauNull()
                ?: delta["reasoning"]?.stringAtauNull().orEmpty()
            return Potongan(isi, pikir)
        }
        val utuh = pertama["message"]?.jsonObject?.get("content")?.stringAtauNull().orEmpty()
        return Potongan(utuh, "")
    }

    private fun uraiJawabanUtuh(mentah: String): String {
        val akar = runCatching { json.parseToJsonElement(mentah).jsonObject }.getOrElse {
            throw AiGagal.balasanAneh(it.message.orEmpty())
        }
        (akar["error"] as? JsonObject)?.let { galat ->
            throw AiGagal.balasanAneh(galat["message"]?.stringAtauNull().orEmpty())
        }
        val pilihan = akar["choices"] as? JsonArray ?: throw AiGagal.balasanAneh("tanpa 'choices'")
        val pesan = pilihan.firstOrNull()?.jsonObject?.get("message")?.jsonObject
            ?: throw AiGagal.balasanAneh("tanpa 'message'")
        return pesan["content"]?.stringAtauNull().orEmpty()
    }

    private fun uraiDaftarModel(isi: String): List<AiModel> {
        val akar = runCatching { json.parseToJsonElement(isi) }.getOrElse {
            throw AiGagal.balasanAneh(it.message.orEmpty())
        }
        val daftar: JsonArray = when (akar) {
            is JsonArray -> akar
            is JsonObject -> akar["data"] as? JsonArray ?: akar["models"] as? JsonArray
                ?: throw AiGagal.balasanAneh("tanpa 'data'")
            else -> throw AiGagal.balasanAneh("bukan objek/array")
        }
        val model = daftar.mapNotNull { elemen ->
            when (elemen) {
                is JsonPrimitive -> elemen.content.takeIf { it.isNotBlank() }?.let { AiModel(it) }
                is JsonObject -> {
                    val id = elemen["id"]?.stringAtauNull()
                        ?: elemen["name"]?.stringAtauNull()
                        ?: elemen["model"]?.stringAtauNull()
                    id?.takeIf { it.isNotBlank() }?.let {
                        AiModel(it, elemen["owned_by"]?.stringAtauNull().orEmpty())
                    }
                }
                else -> null
            }
        }
        if (model.isEmpty()) {
            throw AiGagal.balasanAneh("daftar model kosong")
        }
        return model.distinctBy { it.id }
    }

    private fun badanPermintaan(config: AiConfig, pesan: List<ChatMessage>): JsonObject = buildJsonObject {
        put("model", config.model)
        put("stream", true)
        put("temperature", config.temperature)
        put("messages", buildJsonArray {
            pesan.forEach { m ->
                add(
                    buildJsonObject {
                        put("role", m.peran.kode)
                        if (m.gambar.isEmpty()) {
                            put("content", m.isi)
                        } else {
                            // Bentuk multimodal OpenAI-compatible: teks dan gambar dalam satu array.
                            put(
                                "content",
                                buildJsonArray {
                                    add(
                                        buildJsonObject {
                                            put("type", "text")
                                            put("text", m.isi)
                                        },
                                    )
                                    m.gambar.forEach { gambar ->
                                        add(
                                            buildJsonObject {
                                                put("type", "image_url")
                                                put("image_url", buildJsonObject { put("url", gambar) })
                                            },
                                        )
                                    }
                                },
                            )
                        }
                    },
                )
            }
        })
    }

    private fun buka(url: String, kunci: String): HttpURLConnection {
        val koneksi = (URL(url).openConnection() as? HttpURLConnection)
            ?: throw AiGagal.jaringan(IllegalStateException("bukan HTTP"))
        koneksi.connectTimeout = waktuSambungMs
        koneksi.readTimeout = waktuBacaMs
        koneksi.instanceFollowRedirects = true
        koneksi.setRequestProperty("Authorization", "Bearer $kunci")
        koneksi.setRequestProperty("User-Agent", "KodeNusa")
        return koneksi
    }

    private fun bacaTeks(koneksi: HttpURLConnection, kode: Int): String = runCatching {
        val aliran = if (kode in 200..299) koneksi.inputStream else koneksi.errorStream
        aliran?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
    }.getOrDefault("")

    private fun dariStatus(kode: Int, url: String, isi: String): AiGagal = when (kode) {
        401, 403 -> AiGagal.kunciSalah()
        404 -> AiGagal.alamatSalah(url)
        429 -> AiGagal.kuota()
        in 500..599 -> AiGagal.penyedia(kode, pesanGalat(isi))
        else -> AiGagal.penyedia(kode, pesanGalat(isi))
    }

    private fun pesanGalat(isi: String): String = runCatching {
        val akar = json.parseToJsonElement(isi).jsonObject
        (akar["error"] as? JsonObject)?.get("message")?.stringAtauNull()
            ?: akar["message"]?.stringAtauNull()
            ?: isi.take(160)
    }.getOrDefault(isi.take(160))

    private fun JsonElement.stringAtauNull(): String? = (this as? JsonPrimitive)?.content
}
