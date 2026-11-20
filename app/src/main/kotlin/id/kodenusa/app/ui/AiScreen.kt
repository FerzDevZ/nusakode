package id.kodenusa.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import id.kodenusa.app.AiSettingsStore
import id.kodenusa.core.ai.AiConfig
import id.kodenusa.core.ai.AiGagal
import id.kodenusa.core.ai.AiModel
import id.kodenusa.core.ai.ChatMessage
import id.kodenusa.core.ai.CodeBlocks
import id.kodenusa.core.ai.OpenAiCompatClient
import id.kodenusa.core.ai.PromptBuilder
import id.kodenusa.ui.GhostButton
import id.kodenusa.ui.KodenusaPalette
import id.kodenusa.ui.Panel
import id.kodenusa.ui.PrimaryButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Konteks yang dibawa layar AI: berkas yang sedang dibuka, galat build, atau kosong. */
data class AiKonteks(
    val jalur: String? = null,
    val isi: String? = null,
    val galat: String? = null,
    val judul: String = "Tanya AI",
)

/**
 * Asisten AI dengan endpoint + kunci milik pengguna sendiri.
 *
 * Tidak ada kunci bawaan dan tidak ada server perantara: kalau pengguna belum mengisi apa pun, layar
 * ini hanya menampilkan formulir dan aplikasi tetap 100% offline.
 */
@Composable
fun AiScreen(
    store: AiSettingsStore,
    konteks: AiKonteks,
    onDismiss: () -> Unit,
    onSisip: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val klien = remember { OpenAiCompatClient() }

    var config by remember { mutableStateOf(store.muat()) }
    var endpoint by remember { mutableStateOf(config.baseUrl) }
    var kunci by remember { mutableStateOf(config.apiKey) }
    var modelList by remember { mutableStateOf(store.modelTerakhir()) }
    var sedangAtur by remember { mutableStateOf(!config.terpasang) }
    var memuatModel by remember { mutableStateOf(false) }

    var pertanyaan by remember { mutableStateOf("") }
    var jawaban by remember { mutableStateOf("") }
    var berpikir by remember { mutableStateOf("") }
    var sibuk by remember { mutableStateOf(false) }
    var galat by remember { mutableStateOf<String?>(null) }
    var pekerjaan by remember { mutableStateOf<Job?>(null) }
    var sudahTanya by remember { mutableStateOf(false) }

    fun pesanUntuk(teks: String): List<ChatMessage> = when {
        konteks.galat != null ->
            PromptBuilder.untukGalat(konteks.galat.orEmpty() + "\n\nPermintaan tambahan: " + teks, konteks.jalur, konteks.isi)
        konteks.jalur != null && konteks.isi != null -> PromptBuilder.untukBerkas(teks, konteks.jalur, konteks.isi)
        else -> PromptBuilder.untukTanya(teks)
    }

    fun kirim(teks: String) {
        if (teks.isBlank() || sibuk) return
        galat = null
        jawaban = ""
        berpikir = ""
        sibuk = true
        sudahTanya = true
        pekerjaan = scope.launch {
            try {
                val hasil = withContext(Dispatchers.IO) {
                    klien.bicara(
                        config = config,
                        pesan = pesanUntuk(teks),
                        batalkan = { false },
                        onReasoning = { potongan -> berpikir += potongan },
                        onDelta = { potongan -> jawaban += potongan },
                    )
                }
                if (jawaban.isEmpty()) jawaban = hasil
            } catch (gagal: AiGagal) {
                galat = listOf(gagal.pesan, gagal.saran).filter { it.isNotBlank() }.joinToString("\n")
            } catch (error: Exception) {
                galat = "Gagal memanggil AI: ${error.message ?: error.javaClass.simpleName}"
            } finally {
                sibuk = false
                pekerjaan = null
            }
        }
    }

    fun sesuaikanModel(id: String) {
        config = config.denganModel(id)
        store.simpan(config)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(color = Color(KodenusaPalette.BACKGROUND), modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().imePadding()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GhostButton("Tutup", onDismiss)
                    Text(
                        konteks.judul,
                        style = MaterialTheme.typography.titleSmall,
                        color = Color(KodenusaPalette.TEXT),
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                        maxLines = 1,
                    )
                    if (!sedangAtur) {
                        GhostButton("Atur", { sedangAtur = true })
                    }
                }

                if (sedangAtur) {
                    AturAiPanel(
                        endpoint = endpoint,
                        kunci = kunci,
                        modelList = modelList,
                        modelTerpilih = config.model,
                        memuat = memuatModel,
                        galat = galat,
                        onEndpoint = { endpoint = it },
                        onKunci = { kunci = it },
                        onPilihModel = ::sesuaikanModel,
                        onMuatModel = {
                            galat = null
                            memuatModel = true
                            scope.launch {
                                try {
                                    val calon = config.copy(baseUrl = endpoint, apiKey = kunci)
                                    val daftar = withContext(Dispatchers.IO) { klien.daftarModel(calon) }
                                    modelList = daftar
                                    store.simpan(calon)
                                    store.simpanModel(daftar)
                                    config = calon.denganModel(calon.model.ifBlank { daftar.first().id })
                                    store.simpan(config)
                                    sedangAtur = false
                                } catch (gagal: AiGagal) {
                                    galat = listOf(gagal.pesan, gagal.saran).filter { it.isNotBlank() }.joinToString("\n")
                                } catch (error: Exception) {
                                    galat = "Gagal memuat model: ${error.message}"
                                } finally {
                                    memuatModel = false
                                }
                            }
                        },
                    )
                } else {
                    ChatAiPanel(
                        model = config.model,
                        jawaban = jawaban,
                        berpikir = berpikir,
                        sibuk = sibuk,
                        galat = galat,
                        sudahTanya = sudahTanya,
                        adaKonteksBerkas = konteks.isi != null,
                        adaGalat = konteks.galat != null,
                        pertanyaan = pertanyaan,
                        onPertanyaan = { pertanyaan = it },
                        onKirim = { kirim(pertanyaan.ifBlank { "Perbaiki kode ini." }) },
                        onCepat = { teks -> pertanyaan = teks; kirim(teks) },
                        onHentikan = { pekerjaan?.cancel(); sibuk = false },
                        adaKode = CodeBlocks.terbesar(jawaban) != null,
                        onSisip = {
                            CodeBlocks.terbesar(jawaban)?.let { blok -> onSisip(blok.isi) }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun AturAiPanel(
    endpoint: String,
    kunci: String,
    modelList: List<AiModel>,
    modelTerpilih: String,
    memuat: Boolean,
    galat: String?,
    onEndpoint: (String) -> Unit,
    onKunci: (String) -> Unit,
    onPilihModel: (String) -> Unit,
    onMuatModel: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
    ) {
        Text(
            "Pakai endpoint dan kunci milikmu sendiri. KodeNusa tidak punya server AI dan tidak " +
                "mengirim apa pun sebelum kamu mengisi ini.",
            style = MaterialTheme.typography.bodySmall,
            color = Color(KodenusaPalette.TEXT_DIM),
        )
        Spacer(Modifier.height(12.dp))
        IsianAi("Alamat endpoint", endpoint, onEndpoint, "https://api.crown.ai.id/v1", rahasia = false)
        Spacer(Modifier.height(8.dp))
        IsianAi("Kunci API", kunci, onKunci, "tempel kuncimu di sini", rahasia = true)
        Spacer(Modifier.height(12.dp))
        PrimaryButton(
            if (memuat) "Memuat model…" else "Muat model dari endpoint",
            onMuatModel,
            enabled = !memuat && endpoint.isNotBlank() && kunci.isNotBlank(),
            modifier = Modifier.testTag("muat-model"),
        )
        if (modelList.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Text("Pilih model", style = MaterialTheme.typography.titleSmall, color = Color(KodenusaPalette.TEXT))
            Spacer(Modifier.height(6.dp))
            modelList.forEach { model ->
                Panel(
                    Modifier.fillMaxWidth().padding(bottom = 6.dp).clickable { onPilihModel(model.id) },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (model.id == modelTerpilih) "● " else "○ ",
                            color = Color(if (model.id == modelTerpilih) KodenusaPalette.ACCENT else KodenusaPalette.TEXT_DIM),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(model.label(), style = MaterialTheme.typography.bodyMedium, color = Color(KodenusaPalette.TEXT))
                            Text(model.id, style = MaterialTheme.typography.labelSmall, color = Color(KodenusaPalette.TEXT_DIM))
                        }
                    }
                }
            }
        }
        galat?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = Color(KodenusaPalette.ERROR))
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun IsianAi(
    label: String,
    nilai: String,
    onNilai: (String) -> Unit,
    contoh: String,
    rahasia: Boolean,
) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Color(KodenusaPalette.TEXT_DIM))
        Spacer(Modifier.height(4.dp))
        TextField(
            value = nilai,
            onValueChange = onNilai,
            singleLine = true,
            placeholder = { Text(contoh, color = Color(KodenusaPalette.TEXT_DIM)) },
            visualTransformation = if (rahasia) {
                androidx.compose.ui.text.input.PasswordVisualTransformation()
            } else {
                androidx.compose.ui.text.input.VisualTransformation.None
            },
            modifier = Modifier.fillMaxWidth().testTag(label),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color(KodenusaPalette.SURFACE_RAISED),
                unfocusedContainerColor = Color(KodenusaPalette.SURFACE_RAISED),
                focusedTextColor = Color(KodenusaPalette.TEXT),
                unfocusedTextColor = Color(KodenusaPalette.TEXT),
            ),
        )
    }
}

@Composable
private fun ChatAiPanel(
    model: String,
    jawaban: String,
    berpikir: String,
    sibuk: Boolean,
    galat: String?,
    sudahTanya: Boolean,
    adaKonteksBerkas: Boolean,
    adaGalat: Boolean,
    pertanyaan: String,
    onPertanyaan: (String) -> Unit,
    onKirim: () -> Unit,
    onCepat: (String) -> Unit,
    onHentikan: () -> Unit,
    adaKode: Boolean,
    onSisip: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Text(
            "model: $model",
            style = MaterialTheme.typography.labelSmall,
            color = Color(KodenusaPalette.TEXT_DIM),
            maxLines = 1,
        )
        if (!sudahTanya) {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (adaGalat) GhostButton("Perbaiki galat ini", { onCepat("Jelaskan penyebab galat ini dan beri berkas yang sudah diperbaiki.") })
                if (adaKonteksBerkas) {
                    GhostButton("Perbaiki berkas ini", { onCepat("Perbaiki berkas ini dan jelaskan dalam satu kalimat apa yang salah.") })
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                GhostButton("Buat Activity", { onCepat("Buat Activity Android baru lengkap dengan layout XML-nya untuk Java 8 tanpa AndroidX.") })
                GhostButton("Tanya bebas", { onCepat("Apa saja yang bisa kamu bantu di KodeNusa?") })
            }
        }

        LazyColumn(Modifier.weight(1f).padding(top = 10.dp)) {
            if (berpikir.isNotBlank() && jawaban.isBlank()) {
                item {
                    Text(
                        "model sedang berpikir…",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(KodenusaPalette.TEXT_DIM),
                    )
                }
            }
            item {
                if (jawaban.isNotBlank()) {
                    Text(
                        CodeBlocks.tanpaPagar(jawaban),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(KodenusaPalette.TEXT),
                    )
                }
            }
            CodeBlocks.ambil(jawaban).forEach { blok ->
                item {
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        color = Color(KodenusaPalette.SURFACE),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(10.dp)) {
                            Text(
                                "kode ${blok.bahasa.ifBlank { "tanpa bahasa" }} · ${blok.isi.lines().size} baris",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(KodenusaPalette.ACCENT),
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                blok.isi.take(4_000),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(KodenusaPalette.TEXT_DIM),
                            )
                        }
                    }
                }
            }
            galat?.let { pesan ->
                item {
                    Spacer(Modifier.height(10.dp))
                    Text(pesan, style = MaterialTheme.typography.bodySmall, color = Color(KodenusaPalette.ERROR))
                }
            }
        }

        if (adaKode) {
            PrimaryButton("Masukkan kode ke berkas", onSisip, modifier = Modifier.fillMaxWidth().testTag("sisip-kode"))
            Spacer(Modifier.height(6.dp))
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TextField(
                value = pertanyaan,
                onValueChange = onPertanyaan,
                singleLine = false,
                maxLines = 3,
                placeholder = { Text("Tulis permintaan…", color = Color(KodenusaPalette.TEXT_DIM)) },
                modifier = Modifier.weight(1f).testTag("tanya-ai"),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color(KodenusaPalette.SURFACE_RAISED),
                    unfocusedContainerColor = Color(KodenusaPalette.SURFACE_RAISED),
                    focusedTextColor = Color(KodenusaPalette.TEXT),
                    unfocusedTextColor = Color(KodenusaPalette.TEXT),
                ),
            )
            if (sibuk) {
                GhostButton("Hentikan", onHentikan)
            } else {
                PrimaryButton("Kirim", onKirim)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
