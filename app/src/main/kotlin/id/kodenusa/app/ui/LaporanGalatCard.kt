package id.kodenusa.app.ui

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import id.kodenusa.app.LaporanGalat
import id.kodenusa.ui.CodeTextStyle
import id.kodenusa.ui.GhostButton
import id.kodenusa.ui.KodenusaPalette
import id.kodenusa.ui.Panel
import id.kodenusa.ui.Kode
import java.io.File

/**
 * Kartu "aplikasi berhenti mendadak" di layar projek.
 *
 * Muncul hanya kalau ada berkas laporan dari proses yang mati sebelumnya. Pengguna yang memutuskan
 * apakah laporannya dibagikan — aplikasi tidak pernah mengirimnya sendiri.
 */
@Composable
fun LaporanGalatCard(onHapus: () -> Unit) {
    val konteks = LocalContext.current
    var ada by remember { mutableStateOf(LaporanGalat.baca(konteks) != null) }
    if (!ada) return
    val isi = remember { LaporanGalat.baca(konteks).orEmpty() }
    var galat by remember { mutableStateOf<String?>(null) }

    Panel(Modifier.fillMaxWidth().testTag("laporan-galat")) {
        Column {
            Text(
                "Aplikasi berhenti mendadak",
                style = MaterialTheme.typography.titleMedium,
                color = Kode.warn,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Laporan ini tersimpan di HP Anda dan tidak dikirim ke mana pun. Bagikan hanya kalau " +
                    "Anda mau melaporkannya.",
                style = MaterialTheme.typography.bodySmall,
                color = Kode.textDim,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                isi.lineSequence().take(4).joinToString("\n"),
                style = CodeTextStyle.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize),
                color = Kode.textDim,
                modifier = Modifier.testTag("isi-laporan"),
            )
            galat?.let { pesan ->
                Spacer(Modifier.height(6.dp))
                Text(pesan, style = MaterialTheme.typography.bodySmall, color = Kode.error)
            }
            Spacer(Modifier.height(10.dp))
            Row {
                GhostButton(
                    "Bagikan laporan",
                    { galat = bagikan(konteks) },
                    Modifier.testTag("bagikan-laporan"),
                )
                Spacer(Modifier.padding(horizontal = 4.dp))
                GhostButton(
                    "Hapus laporan",
                    {
                        LaporanGalat.hapus(konteks)
                        ada = false
                        onHapus()
                    },
                    Modifier.testTag("hapus-laporan"),
                )
            }
        }
    }
}

/** Menyalin laporan ke `cacheDir` supaya bisa dibagikan lewat FileProvider yang sudah ada. */
private fun bagikan(konteks: android.content.Context): String? = runCatching {
    val sumber = LaporanGalat.berkas(konteks)
    val tujuan = File(konteks.cacheDir, LaporanGalat.NAMA_BERKAS)
    sumber.copyTo(tujuan, overwrite = true)
    val uri = FileProvider.getUriForFile(konteks, "${konteks.packageName}.berkas", tujuan)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, "Laporan galat KodeNusa")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    konteks.startActivity(Intent.createChooser(intent, "Bagikan laporan galat"))
    null
}.getOrElse { "Laporan tidak bisa dibagikan: ${it.message}" }
