package id.kodenusa.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import id.kodenusa.app.AiSettingsStore
import id.kodenusa.app.TemplateSaya
import id.kodenusa.core.ai.AiConfig
import id.kodenusa.core.ai.OpenAiCompatClient
import id.kodenusa.core.ai.PromptBuilder
import id.kodenusa.core.ai.UbahanBerkas
import id.kodenusa.ui.GhostButton
import id.kodenusa.ui.Kode
import id.kodenusa.ui.Panel
import id.kodenusa.ui.PrimaryButton
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Dialog "Buat template dengan AI" (docs/14 4.3): pengguna menulis deskripsi, AI merancang berkas
 * template, pratinjau ditampilkan dulu (ADR-038), baru disimpan ke "template saya" setelah
 * disetujui. Tidak ada berkas yang ditulis sebelum persetujuan.
 */
@Composable
fun ScaffoldAiDialog(
    setelan: AiSettingsStore,
    templateSaya: TemplateSaya,
    scope: kotlinx.coroutines.CoroutineScope,
    onTutup: () -> Unit,
    onTersimpan: (pesan: String) -> Unit,
) {
    val config = setelan.muat()
    var deskripsi by remember { mutableStateOf("") }
    var rancangan by remember { mutableStateOf<Map<String, String>?>(null) }
    var galat by remember { mutableStateOf<String?>(null) }
    var sedangRancang by remember { mutableStateOf(false) }
    var sedangSimpan by remember { mutableStateOf(false) }

    val alasan = when {
        !config.terpasang -> "Isi endpoint dan kunci AI dulu di layar AI."
        config.model.isBlank() -> "Pilih model dulu di layar AI (tombol Muat model)."
        deskripsi.isBlank() -> "Tulis dulu deskripsi aplikasinya."
        else -> null
    }

    Panel(Modifier.fillMaxWidth()) {
        Column {
            Text("Buat template dengan AI", style = MaterialTheme.typography.titleMedium, color = Kode.text)
            Spacer(Modifier.height(6.dp))
            Text(
                "Tulis aplikasi yang kamu mau. AI merancang berkasnya, kamu lihat pratinjaunya, " +
                    "baru disimpan sebagai template di daftar \"Mulai dari nol\".",
                style = MaterialTheme.typography.bodyMedium,
                color = Kode.textDim,
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = deskripsi,
                onValueChange = { deskripsi = it },
                label = { Text("Contoh: aplikasi pencatat utang teman dengan tombol lunas") },
                modifier = Modifier.fillMaxWidth().testTag("deskripsi-scaffold"),
            )
            Spacer(Modifier.height(10.dp))

            val rancanganSiap = rancangan != null
            if (rancanganSiap) {
                Text(
                    "Pratinjau ${rancangan!!.size} berkas (ADR-038 — belum ada yang ditulis):",
                    style = MaterialTheme.typography.labelMedium,
                    color = Kode.text,
                )
                Spacer(Modifier.height(4.dp))
                rancangan!!.keys.sorted().forEach { jalur ->
                    Text("• $jalur", style = MaterialTheme.typography.bodySmall, color = Kode.textDim)
                }
                Spacer(Modifier.height(8.dp))
            }

            galat?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = Kode.error, modifier = Modifier.testTag("galat-scaffold"))
                Spacer(Modifier.height(8.dp))
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostButton("Batal", onTutup, Modifier.weight(1f).testTag("batal-scaffold"))
                if (!rancanganSiap) {
                    PrimaryButton(
                        text = "Rancang",
                        onClick = {
                            sedangRancang = true
                            galat = null
                            scope.launch {
                                val jawaban = withContext(Dispatchers.IO) {
                                    runCatching {
                                        OpenAiCompatClient().bicara(config, PromptBuilder.untukScaffold(deskripsi))
                                    }
                                }
                                sedangRancang = false
                                jawaban.fold(
                                    onSuccess = { teks ->
                                        val berkas = UbahanBerkas.ambil(teks).associate { it.jalur to it.isi }
                                        if (berkas.isEmpty()) {
                                            galat = "AI tidak mengirim berkas. Coba tulis deskripsi lebih terperinci."
                                        } else {
                                            rancangan = berkas
                                        }
                                    },
                                    onFailure = { galat = "AI gagal: ${it.message ?: "penyebab tidak diketahui"}" },
                                )
                            }
                        },
                        modifier = Modifier.weight(1.4f).testTag("rancang-scaffold"),
                        enabled = alasan == null,
                        loading = sedangRancang,
                    )
                } else {
                    PrimaryButton(
                        text = "Simpan sebagai template",
                        onClick = {
                            val berkas = rancangan ?: return@PrimaryButton
                            sedangSimpan = true
                            galat = null
                            val nama = berkas["kodenusa.json"]
                                ?.let { isi -> Regex("\"name\"\\s*:\\s*\"([^\"]+)\"").find(isi)?.groupValues?.get(1) }
                                ?.takeIf { it.isNotBlank() && it != "__APP_NAME__" }
                                ?: deskripsi.take(24).trim()
                            val galatSimpan = templateSaya.simpan(nama, berkas)
                            sedangSimpan = false
                            if (galatSimpan == null) {
                                onTersimpan("Template \"$nama\" tersimpan di daftar wizard.")
                            } else {
                                galat = galatSimpan
                            }
                        },
                        modifier = Modifier.weight(1.4f).testTag("simpan-scaffold"),
                        loading = sedangSimpan,
                    )
                }
            }
            alasan?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = Kode.textDim, modifier = Modifier.testTag("alasan-scaffold"))
            }
        }
    }
}
