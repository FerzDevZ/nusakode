package id.kodenusa.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import id.kodenusa.app.KunciAman
import id.kodenusa.app.KunciPengguna
import id.kodenusa.ui.Banner
import id.kodenusa.ui.GhostButton
import id.kodenusa.ui.JenisBanner
import id.kodenusa.ui.Kode
import id.kodenusa.ui.Panel
import id.kodenusa.ui.PrimaryButton
import java.io.File

/**
 * Layar "Kunci tanda tangan" (docs/14 2.1, ADR-041): buat, lihat status, ekspor, dan hapus kunci
 * milik pengguna. Tanpa kunci pengguna, build tetap jalan dengan keystore debug per pemasangan —
 * itu perilaku bawaan dan tidak dianggap galat.
 *
 * Catatan jujur yang selalu tampil: kunci disimpan di HP ini saja; pindah HP berarti membawa
 * berkas `.p12` + sandinya, kalau tidak APK baru tidak bisa memperbarui aplikasi lama.
 */
@Composable
fun KunciScreen(
    kunciPengguna: KunciPengguna,
    onBack: () -> Unit,
) {
    val konteks = LocalContext.current
    var status by remember { mutableStateOf(kunciPengguna.status()) }
    var alias by remember { mutableStateOf(status.alias ?: "kodenusa") }
    var sandi by remember { mutableStateOf("") }
    var sandiUlang by remember { mutableStateOf("") }
    var pesan by remember { mutableStateOf<Pair<JenisBanner, String>?>(null) }
    var mintaEkspor by remember { mutableStateOf(false) }
    var konfirmasiHapus by remember { mutableStateOf(false) }

    val mintaBuat = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/x-pkcs12")) { uri ->
        mintaEkspor = false
        if (uri == null) return@rememberLauncherForActivityResult
        val berhasil = runCatching {
            konteks.contentResolver.openOutputStream(uri)?.use { out ->
                File(konteks.filesDir, KunciPengguna.FOLDER).inputStream().use { it.copyTo(out) }
            } ?: error("Tujuan ekspor tidak bisa ditulis")
        }.isSuccess
        pesan = if (berhasil) {
            JenisBanner.OK to "Kunci diekspor. Simpan berkasnya dan sandinya di tempat aman."
        } else {
            JenisBanner.GALAT to "Ekspor gagal. Coba lagi atau pilih lokasi lain."
        }
    }

    Scaffold(containerColor = Kode.background) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                GhostButton("Kembali", onBack, Modifier.testTag("kunci-kembali"))
                Spacer(Modifier.weight(1f))
                Text(
                    "Kunci tanda tangan",
                    style = MaterialTheme.typography.titleLarge,
                    color = Kode.text,
                )
            }

            if (status.ada) {
                Panel(Modifier.fillMaxWidth().testTag("status-kunci")) {
                    Column {
                        Text(
                            "Kunci sendiri aktif — alias: ${status.alias}.",
                            color = Kode.text,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            "APK hasil build ditandatangani dengan kunci ini dan bisa saling memperbarui.",
                            color = Kode.textDim,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            } else {
                Panel(Modifier.fillMaxWidth().testTag("status-kunci")) {
                    Column {
                        Text(
                            "Belum ada kunci sendiri — build memakai kunci debug bawaan.",
                            color = Kode.text,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            "Kunci debug hilang saat aplikasi dihapus atau HP diganti, sehingga APK baru " +
                                "tidak bisa memperbarui APK lama. Buat kunci sendiri bila kamu membagikan " +
                                "aplikasi ke orang lain.",
                            color = Kode.textDim,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            Panel(Modifier.fillMaxWidth()) {
                Text(
                    "Catatan jujur: kunci disimpan di HP ini saja. Pindahkan berkas .p12 lewat " +
                        "tombol Ekspor (plus sandinya) kalau kamu pindah HP. Membuat kunci baru " +
                        "menimpa kunci lama — aplikasi yang sudah terpasang tidak bisa diperbarui " +
                        "oleh APK bertanda tangan baru.",
                    color = Kode.warn,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            if (status.ada) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GhostButton("Ekspor .p12", { mintaBuat.launch("kunci-ttd.p12") }, Modifier.weight(1f).testTag("ekspor-kunci"))
                    GhostButton(
                        if (konfirmasiHapus) "Yakin? Tekan lagi" else "Hapus kunci",
                        {
                            if (konfirmasiHapus) {
                                kunciPengguna.hapus()
                                status = kunciPengguna.status()
                                konfirmasiHapus = false
                                pesan = JenisBanner.OK to "Kunci dihapus. Build kembali memakai kunci debug."
                            } else {
                                konfirmasiHapus = true
                            }
                        },
                        Modifier.weight(1f).testTag("hapus-kunci"),
                    )
                }
            } else {
                OutlinedTextField(
                    value = alias,
                    onValueChange = { alias = it.take(40) },
                    label = { Text("Alias kunci") },
                    modifier = Modifier.fillMaxWidth().testTag("alias-kunci"),
                )
                OutlinedTextField(
                    value = sandi,
                    onValueChange = { sandi = it },
                    label = { Text("Sandi kunci (minimal 6 karakter)") },
                    modifier = Modifier.fillMaxWidth().testTag("sandi-kunci"),
                )
                OutlinedTextField(
                    value = sandiUlang,
                    onValueChange = { sandiUlang = it },
                    label = { Text("Ulangi sandi") },
                    modifier = Modifier.fillMaxWidth().testTag("sandi-ulang"),
                )
                PrimaryButton(
                    text = "Buat kunci",
                    onClick = {
                        if (sandi != sandiUlang) {
                            pesan = JenisBanner.GALAT to "Sandi dan ulangannya tidak sama."
                            return@PrimaryButton
                        }
                        val hasil = kunciPengguna.buat(alias, sandi)
                        hasil.onSuccess {
                            status = kunciPengguna.status()
                            sandi = ""
                            sandiUlang = ""
                            pesan = JenisBanner.OK to "Kunci dibuat. APK berikutnya ditandatangani kunci ini."
                        }.onFailure {
                            pesan = JenisBanner.GALAT to (it.message ?: "Kunci tidak bisa dibuat.")
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("buat-kunci"),
                )
            }

            pesan?.let { (jenis, teks) ->
                Banner(jenis = jenis, teks = teks, modifier = Modifier.fillMaxWidth().testTag("pesan-kunci"))
            }

            Text(
                "Sandi kunci disimpan terenskripsi (AES-256-GCM, kunci di Android Keystore) — " +
                    "tidak pernah ditulis polos di penyimpanan.",
                color = Kode.textDim,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}
