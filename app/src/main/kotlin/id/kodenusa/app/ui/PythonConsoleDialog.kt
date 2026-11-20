package id.kodenusa.app.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import id.kodenusa.app.PythonRunner
import id.kodenusa.ui.GhostButton
import id.kodenusa.ui.KodenusaPalette
import id.kodenusa.ui.PrimaryButton
import id.kodenusa.ui.Kode
import java.io.File
import kotlinx.coroutines.launch

/**
 * Konsol Python di dalam editor: tombol "Jalankan" pada berkas `.py` membuka dialog ini, yang
 * menjalankan skrip dengan [PythonRunner] dan menampilkan keluarannya baris demi baris, seperti
 * terminal sederhana. Status selesai (kode keluar) dan batas waktu ditampilkan jujur.
 */
@Composable
fun PythonConsoleDialog(
    berkas: File,
    folderKerja: File,
    runner: PythonRunner,
    onTutup: () -> Unit,
) {
    val baris = remember { mutableStateListOf<String>() }
    var selesai by remember { mutableStateOf(false) }
    var kode by remember { mutableStateOf<Int?>(null) }
    val daftar = rememberLazyListState()
    val scope = rememberCoroutineScope()

    fun jalankan() {
        baris.clear()
        selesai = false
        kode = null
        scope.launch {
            val hasil = runner.jalankan(berkas, folderKerja) { teks ->
                baris.add(teks)
                scope.launch { daftar.animateScrollToItem((baris.size - 1).coerceAtLeast(0)) }
            }
            when (hasil) {
                is PythonRunner.Hasil.Selesai -> {
                    kode = hasil.kode
                    if (hasil.kode == PythonRunner.KODE_TIMEOUT) {
                        baris.add("— dihentikan karena melebihi batas waktu —")
                    }
                }
                is PythonRunner.Hasil.Gagal -> baris.add("Galat: ${hasil.pesan}")
            }
            selesai = true
        }
    }

    LaunchedEffect(berkas.path) { jalankan() }

    Dialog(
        onDismissRequest = onTutup,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            color = Kode.background,
            modifier = Modifier.fillMaxSize().padding(12.dp),
            shape = MaterialTheme.shapes.large,
        ) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Python — ${berkas.name}",
                            style = MaterialTheme.typography.titleMedium,
                            color = Kode.text,
                        )
                        Text(
                            "MicroPython · tanpa internet",
                            style = MaterialTheme.typography.labelSmall,
                            color = Kode.textDim,
                        )
                    }
                    GhostButton("Tutup", onTutup, Modifier.testTag("tutup-python"))
                }
                Spacer(Modifier.height(8.dp))
                LazyColumn(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Kode.surface)
                        .padding(10.dp)
                        .testTag("keluaran-python"),
                    state = daftar,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    if (baris.isEmpty() && !selesai) {
                        item {
                            Text(
                                "menjalankan…",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = Kode.textDim,
                            )
                        }
                    }
                    items(baris) { teks ->
                        Text(
                            teks,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = Kode.text,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryButton(
                        text = if (selesai) "Jalankan lagi" else "Jalankan ulang",
                        onClick = { jalankan() },
                        modifier = Modifier.weight(1f).testTag("jalankan-lagi-python"),
                    )
                    GhostButton(
                        "Hentikan",
                        { runner.hentikan() },
                        Modifier.weight(1f).testTag("hentikan-python"),
                    )
                }
                kode?.let { k ->
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (k == 0) "Selesai (kode 0)" else "Selesai (kode $k)",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (k == 0) Kode.ok else Kode.warn,
                        modifier = Modifier.testTag("status-python"),
                    )
                }
            }
        }
    }
}
