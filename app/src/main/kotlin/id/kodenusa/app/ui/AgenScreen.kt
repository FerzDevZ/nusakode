package id.kodenusa.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import id.kodenusa.app.AiBuildGate
import id.kodenusa.app.AiProjectBoard
import id.kodenusa.app.AiSettingsStore
import id.kodenusa.app.AppContainer
import id.kodenusa.core.ai.AgenBuild
import id.kodenusa.core.ai.ChatMessage
import id.kodenusa.core.ai.GerbangModel
import id.kodenusa.core.ai.HasilAgen
import id.kodenusa.core.ai.LangkahAgen
import id.kodenusa.core.ai.OpenAiCompatClient
import id.kodenusa.ui.CodeTextStyle
import id.kodenusa.ui.GhostButton
import id.kodenusa.ui.KodenusaPalette
import id.kodenusa.ui.Panel
import id.kodenusa.ui.Pill
import id.kodenusa.ui.PrimaryButton
import id.kodenusa.ui.Kode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Layar Agen: pengguna menulis keinginan, agen mengubah berkas, lalu build dijalankan sampai APK jadi.
 *
 * Batasnya jelas dan terlihat: maksimal 3 putaran per tugas, tombol Hentikan membatalkan coroutine,
 * dan tanpa kunci AI tombol Mulai terkunci dengan alasan tertulis (tidak ada panggilan jaringan diam-diam).
 */
@Composable
fun AgenScreen(
    container: AppContainer,
    projectDir: File,
    onBack: () -> Unit,
) {
    val konteks = LocalContext.current
    val scope = rememberCoroutineScope()
    val setelan = remember(konteks) { AiSettingsStore(konteks) }
    var tugas by remember { mutableStateOf("") }
    var langkah by remember(projectDir) { mutableStateOf<List<LangkahAgen>>(emptyList()) }
    var hasil by remember(projectDir) { mutableStateOf<HasilAgen?>(null) }
    var galat by remember(projectDir) { mutableStateOf<String?>(null) }
    var berjalan by remember(projectDir) { mutableStateOf(false) }
    var pekerjaan by remember(projectDir) { mutableStateOf<Job?>(null) }

    val config = setelan.muat()
    val alasanMulai = when {
        !config.terpasang -> "Isi endpoint dan kunci AI dulu di layar AI."
        config.model.isBlank() -> "Pilih model dulu di layar AI (tombol Muat model)."
        tugas.isBlank() -> "Tulis dulu apa yang ingin dibuat."
        berjalan -> "Agen sedang bekerja."
        else -> null
    }

    Scaffold(containerColor = Kode.background) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                GhostButton("Kembali", onBack)
                Spacer(Modifier.weight(1f))
                when {
                    berjalan -> Pill("bekerja", Kode.info)
                    hasil?.sukses == true -> Pill("APK jadi", Kode.ok)
                    hasil != null || galat != null -> Pill("berhenti", Kode.error)
                    else -> Pill("siap", Kode.textDim)
                }
            }
            Spacer(Modifier.height(12.dp))

            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    Panel(Modifier.fillMaxWidth()) {
                        Column {
                            Text("Minta AI buatkan", style = MaterialTheme.typography.titleMedium, color = Kode.text)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Tulis keinginanmu dalam bahasa Indonesia. Agen mengubah berkas projek, " +
                                    "lalu build dijalankan sendiri. Maksimal 3 putaran per tugas.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Kode.textDim,
                            )
                            Spacer(Modifier.height(10.dp))
                            OutlinedTextField(
                                value = tugas,
                                onValueChange = { tugas = it },
                                label = { Text("Contoh: tambah tombol Bagikan dan layar kedua") },
                                modifier = Modifier.fillMaxWidth().height(110.dp).testTag("tugas-agen"),
                            )
                            Spacer(Modifier.height(12.dp))
                            PrimaryButton(
                                text = "Mulai",
                                onClick = {
                                    galat = null
                                    hasil = null
                                    langkah = emptyList()
                                    berjalan = true
                                    pekerjaan = scope.launch {
                                        val siap = withContext(Dispatchers.IO) { container.toolchainInstaller.install() }
                                        if (siap !is id.kodenusa.app.ToolchainInstaller.Status.Ready) {
                                            berjalan = false
                                            galat = when (siap) {
                                                is id.kodenusa.app.ToolchainInstaller.Status.Missing ->
                                                    "Alat build belum lengkap: ${siap.names.joinToString(", ")}."
                                                is id.kodenusa.app.ToolchainInstaller.Status.Broken -> siap.reason
                                                else -> "Alat build belum siap."
                                            }
                                            return@launch
                                        }
                                        val model = object : GerbangModel {
                                            override suspend fun balas(pesan: List<ChatMessage>): String =
                                                OpenAiCompatClient().bicara(config, pesan)
                                        }
                                        val agen = AgenBuild(
                                            model = model,
                                            papan = AiProjectBoard(projectDir),
                                            build = AiBuildGate(projectDir, container.newEngine(siap.toolchain)),
                                        )
                                        val keluaran = runCatching {
                                            agen.jalankan(tugas) { satu -> langkah = langkah + satu }
                                        }
                                        berjalan = false
                                        keluaran.fold(
                                            onSuccess = { hasil = it; if (!it.sukses) galat = it.galat },
                                            onFailure = { salah ->
                                                galat = if (salah is kotlinx.coroutines.CancellationException) {
                                                    "Dihentikan. Berkas yang sudah ditulis tetap ada di projek."
                                                } else {
                                                    "Agen berhenti: ${salah.message ?: "penyebab tidak diketahui"}"
                                                }
                                            },
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().testTag("mulai-agen"),
                                enabled = alasanMulai == null,
                                loading = berjalan,
                            )
                            alasanMulai?.let { alasan ->
                                Spacer(Modifier.height(6.dp))
                                Text(alasan, style = MaterialTheme.typography.bodySmall, color = Kode.textDim, modifier = Modifier.testTag("alasan-agen"))
                            }
                            if (berjalan) {
                                Spacer(Modifier.height(8.dp))
                                GhostButton(
                                    "Hentikan",
                                    {
                                        pekerjaan?.cancel()
                                        berjalan = false
                                        galat = "Dihentikan. Berkas yang sudah ditulis tetap ada di projek."
                                    },
                                    modifier = Modifier.fillMaxWidth().testTag("hentikan-agen"),
                                )
                            }
                        }
                    }
                }

                if (langkah.isNotEmpty()) {
                    item {
                        Text("Proses", style = MaterialTheme.typography.titleMedium, color = Kode.text)
                    }
                    items(langkah) { satu ->
                        Panel(Modifier.fillMaxWidth()) {
                            Column {
                                Text(
                                    "Putaran ${satu.putaran}/${satu.maksPutaran} · ${satu.jumlahBerkas} berkas",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Kode.text,
                                )
                                if (satu.catatan.isNotBlank()) {
                                    Text(satu.catatan, style = MaterialTheme.typography.bodySmall, color = Kode.textDim)
                                }
                                satu.galat?.let { salah ->
                                    Spacer(Modifier.height(4.dp))
                                    Text(salah, style = MaterialTheme.typography.bodySmall, color = Kode.error)
                                }
                            }
                        }
                    }
                }

                hasil?.let { akhir ->
                    item {
                        Panel(Modifier.fillMaxWidth().testTag("hasil-agen")) {
                            Column {
                                Text(
                                    if (akhir.sukses) "APK berhasil dibuat" else "Belum berhasil",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (akhir.sukses) Kode.ok else Kode.error,
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "${akhir.putaran} putaran · ${akhir.berkas.size} berkas diubah",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Kode.textDim,
                                )
                                akhir.berkas.take(8).forEach { satu ->
                                    Text(satu.jalur, style = CodeTextStyle.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize), color = Kode.textDim)
                                }
                                if (!akhir.sukses) {
                                    Spacer(Modifier.height(6.dp))
                                    Text(akhir.galat, style = MaterialTheme.typography.bodyMedium, color = Kode.error)
                                }
                            }
                        }
                    }
                }

                galat?.let { salah ->
                    item {
                        Panel(Modifier.fillMaxWidth().testTag("galat-agen")) {
                            Text(salah, style = MaterialTheme.typography.bodyMedium, color = Kode.error)
                        }
                    }
                }

                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}
