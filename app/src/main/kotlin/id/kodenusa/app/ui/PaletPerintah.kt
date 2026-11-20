package id.kodenusa.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import id.kodenusa.ui.KodenusaPalette
import id.kodenusa.ui.Pill
import id.kodenusa.ui.PrimaryButton
import id.kodenusa.ui.Kode

/** Satu perintah yang bisa dicari dan dijalankan dari palet. */
data class Perintah(
    val id: String,
    val judul: String,
    val keterangan: String = "",
    /** Kata lain yang ikut dicari, supaya "build", "apk", dan "bangun" menemukan perintah yang sama. */
    val alias: List<String> = emptyList(),
    val lain: Boolean = false,
    val jalankan: () -> Unit,
)

/**
 * Palet perintah: ketik apa yang ingin dilakukan, bukan mencari tombolnya.
 *
 * Kenapa ini penting di HP, bukan sekadar hiasan: layar 360dp hanya muat satu tombol utama dan tiga
 * tombol kecil. Di editor desktop semua aksi punya tempat; di sini tidak. Palet memberi akses ke
 * semua aksi tanpa menambah baris tombol yang saling berebut ruang.
 *
 * Pencocokan sengaja sederhana: setiap kata kunci harus muncul di judul/keterangan/alias
 * (AND, bukan OR) — supaya mengetik "simpan git" tidak memunculkan semua perintah yang memuat "git".
 */
@Composable
fun PaletPerintahDialog(perintah: List<Perintah>, onTutup: () -> Unit) {
    var kueri by remember { mutableStateOf("") }
    val hasil = remember(kueri, perintah) { saring(perintah, kueri) }

    Dialog(onDismissRequest = onTutup, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxWidth()
                .padding(12.dp)
                .background(Kode.surfaceRaised)
                .padding(12.dp),
        ) {
            Text(
                "Palet perintah",
                style = MaterialTheme.typography.titleMedium,
                color = Kode.text,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = kueri,
                onValueChange = { kueri = it },
                singleLine = true,
                placeholder = { Text("Cari aksi: build, git, cari, pratinjau…") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth().testTag("cari-aksi"),
            )
            Spacer(Modifier.height(8.dp))
            if (hasil.isEmpty()) {
                Text(
                    "Tidak ada aksi yang cocok dengan \"$kueri\".",
                    style = MaterialTheme.typography.bodySmall,
                    color = Kode.textDim,
                    modifier = Modifier.testTag("aksi-kosong"),
                )
            } else {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 340.dp)) {
                    items(hasil, key = { it.id }) { aksi ->
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable {
                                    onTutup()
                                    aksi.jalankan()
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    aksi.judul,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Kode.text,
                                    modifier = Modifier.testTag("aksi-${aksi.id}"),
                                )
                                if (aksi.keterangan.isNotBlank()) {
                                    Text(
                                        aksi.keterangan,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Kode.textDim,
                                    )
                                }
                            }
                            if (aksi.lain) Pill("lain", Kode.info)
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            PrimaryButton("Tutup", onTutup, Modifier.testTag("tutup-palet"))
        }
    }
}

/** Menyaring perintah: setiap kata pada [kueri] harus muncul di judul, keterangan, atau alias. */
fun saring(perintah: List<Perintah>, kueri: String): List<Perintah> {
    val kata = kueri.trim().lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
    if (kata.isEmpty()) return perintah
    return perintah.filter { aksi ->
        val teks = (aksi.judul + " " + aksi.keterangan + " " + aksi.alias.joinToString(" ")).lowercase()
        kata.all { teks.contains(it) }
    }
}
