package id.kodenusa.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import id.kodenusa.app.TemplateSaya
import id.kodenusa.core.project.GaleriTemplate
import id.kodenusa.ui.GhostButton
import id.kodenusa.ui.Kode
import id.kodenusa.ui.Panel
import id.kodenusa.ui.PrimaryButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Dialog galeri template komunitas (docs/14 4.4): daftar diambil dari GitHub raw pilihan
 * pengguna, tiap template diverifikasi ukuran + SHA-256 sebelum pratinjau, dan disimpan lewat
 * gerbang yang sama dengan template AI (ADR-038). Tanpa jaringan = pesan jujur, daftar bawaan
 * tetap bisa dipakai.
 */
@Composable
fun GaleriDialog(
    templateSaya: TemplateSaya,
    urlTersimpan: String,
    onSimpanUrl: (String) -> Unit,
    scope: kotlinx.coroutines.CoroutineScope,
    onTutup: () -> Unit,
    onTersimpan: (pesan: String) -> Unit,
) {
    var url by remember { mutableStateOf(urlTersimpan) }
    var daftar by remember { mutableStateOf<List<GaleriTemplate.GaleriItem>>(emptyList()) }
    var rincian by remember { mutableStateOf<Map<String, String>?>(null) }
    var galat by remember { mutableStateOf<String?>(null) }
    var sedangMuat by remember { mutableStateOf(false) }
    var sedangUnduh by remember { mutableStateOf<String?>(null) }

    Panel(Modifier.fillMaxWidth()) {
        Column {
            Text("Galeri komunitas", style = MaterialTheme.typography.titleMedium, color = Kode.text)
            Spacer(Modifier.height(6.dp))
            Text(
                "Template buatan komunitas, diunduh langsung dari GitHub. Setiap zip diperiksa " +
                    "ukuran dan SHA-256-nya sebelum dipakai. Tanpa internet, daftar bawaan tetap bisa dipakai.",
                style = MaterialTheme.typography.bodyMedium,
                color = Kode.textDim,
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("Alamat daftar (galeri.json di GitHub raw)") },
                modifier = Modifier.fillMaxWidth().testTag("url-galeri"),
            )
            Spacer(Modifier.height(8.dp))

            if (daftar.isNotEmpty() && rincian == null) {
                LazyColumn(Modifier.fillMaxWidth().height(180.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(daftar, key = { it.id }) { item ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(item.nama, style = MaterialTheme.typography.bodyMedium, color = Kode.text)
                                if (item.deskripsi.isNotBlank()) {
                                    Text(item.deskripsi, style = MaterialTheme.typography.bodySmall, color = Kode.textDim)
                                }
                            }
                            GhostButton(
                                if (sedangUnduh == item.id) "…" else "Ambil",
                                {
                                    sedangUnduh = item.id
                                    galat = null
                                    scope.launch {
                                        val hasil = withContext(Dispatchers.IO) {
                                            GaleriTemplate.unduhTemplate(item)
                                        }
                                        sedangUnduh = null
                                        hasil.fold(
                                            onSuccess = { berkas -> rincian = berkas },
                                            onFailure = { galat = it.message ?: "Unduhan gagal" },
                                        )
                                    }
                                },
                                Modifier.testTag("ambil-${item.id}"),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            rincian?.let { berkas ->
                Text(
                    "Pratinjau ${berkas.size} berkas (ADR-038 — belum ada yang ditulis):",
                    style = MaterialTheme.typography.labelMedium,
                    color = Kode.text,
                )
                Spacer(Modifier.height(4.dp))
                berkas.keys.sorted().take(12).forEach { jalur ->
                    Text("• $jalur", style = MaterialTheme.typography.bodySmall, color = Kode.textDim)
                }
                Spacer(Modifier.height(8.dp))
            }

            galat?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = Kode.error, modifier = Modifier.testTag("galat-galeri"))
                Spacer(Modifier.height(8.dp))
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostButton("Batal", onTutup, Modifier.weight(1f).testTag("batal-galeri"))
                PrimaryButton(
                    text = if (rincian == null) "Muat daftar" else "Simpan sebagai template",
                    onClick = {
                        galat = null
                        if (rincian == null) {
                            sedangMuat = true
                            scope.launch {
                                val hasil = withContext(Dispatchers.IO) {
                                    GaleriTemplate.unduhIndex(url.trim())
                                }
                                sedangMuat = false
                                hasil.fold(
                                    onSuccess = {
                                        daftar = it
                                        onSimpanUrl(url.trim())
                                    },
                                    onFailure = { galat = it.message ?: "Daftar gagal dimuat" },
                                )
                            }
                        } else {
                            val nama = rincian!!["kodenusa.json"]
                                ?.let { isi -> Regex("\"name\"\\s*:\\s*\"([^\"]+)\"").find(isi)?.groupValues?.get(1) }
                                ?.takeIf { it.isNotBlank() && it != "__APP_NAME__" }
                                ?: "galeri-${System.currentTimeMillis() / 1000}"
                            val galatSimpan = templateSaya.simpan(nama, rincian!!)
                            if (galatSimpan == null) {
                                onTersimpan("Template \"$nama\" tersimpan di daftar wizard.")
                            } else {
                                galat = galatSimpan
                            }
                        }
                    },
                    modifier = Modifier.weight(1.4f).testTag("tombol-galeri"),
                    loading = sedangMuat,
                )
            }
        }
    }
}
