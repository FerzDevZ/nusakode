package id.kodenusa.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import id.kodenusa.app.LangkahPelajaran
import id.kodenusa.app.Pelajaran
import id.kodenusa.ui.GhostButton
import id.kodenusa.ui.Kode
import id.kodenusa.ui.Panel
import id.kodenusa.ui.PrimaryButton

/**
 * Panel kecil pelajaran di bawah editor (docs/14 3.3): langkah sekarang, tombol maju/mundur,
 * dan tombol tutup. Panel bisa ditutup; tombol kecil "Pelajaran" mengembalikannya supaya
 * pemula tidak kehilangan jalannya.
 */
@Composable
fun PanelPelajaran(
    pelajaran: Pelajaran,
    langkahSekarang: Int,
    onLangkah: (Int) -> Unit,
    onTutup: () -> Unit,
    onSelesai: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val langkah = pelajaran.langkah.getOrNull(langkahSekarang - 1) ?: return
    Panel(modifier.fillMaxWidth().testTag("pelajaran-panel")) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Pelajaran ${langkahSekarang}/${pelajaran.langkah.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = Kode.accent,
                    modifier = Modifier.testTag("pelajaran-nomor"),
                )
                Spacer(Modifier.weight(1f))
                GhostButton("Tutup", onTutup, Modifier.testTag("pelajaran-tutup"))
            }
            Spacer(Modifier.height(2.dp))
            Text(
                "${langkah.nomor}. ${langkah.judul}",
                style = MaterialTheme.typography.titleSmall,
                color = Kode.text,
                modifier = Modifier.testTag("pelajaran-judul"),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                langkah.isi,
                style = MaterialTheme.typography.bodySmall,
                color = Kode.textDim,
                modifier = Modifier.testTag("pelajaran-isi"),
            )
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (langkahSekarang > 1) {
                    GhostButton(
                        "Sebelumnya",
                        { onLangkah(langkahSekarang - 1) },
                        Modifier.weight(1f).testTag("pelajaran-sebelum"),
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                if (langkahSekarang < pelajaran.langkah.size) {
                    PrimaryButton(
                        text = "Berikutnya",
                        onClick = { onLangkah(langkahSekarang + 1) },
                        modifier = Modifier.weight(1.4f).testTag("pelajaran-berikut"),
                    )
                } else {
                    PrimaryButton(
                        text = "Selesai",
                        onClick = onSelesai,
                        modifier = Modifier.weight(1.4f).testTag("pelajaran-selesai"),
                    )
                }
            }
        }
    }
}

/** Pil kecil untuk membuka kembali pelajaran yang ditutup, tanpa memenuhi layar. */
@Composable
fun TombolBukaPelajaran(onBuka: () -> Unit, modifier: Modifier = Modifier) {
    GhostButton(
        "Pelajaran",
        onBuka,
        modifier.padding(vertical = 2.dp).testTag("pelajaran-buka"),
    )
}
