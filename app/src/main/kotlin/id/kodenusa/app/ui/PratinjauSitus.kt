package id.kodenusa.app.ui

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import id.kodenusa.core.build.SitusStatis
import id.kodenusa.ui.GhostButton
import id.kodenusa.ui.KodenusaPalette
import id.kodenusa.ui.PrimaryButton
import id.kodenusa.ui.Kode
import java.io.File

/**
 * Pratinjau situs langsung di dalam aplikasi.
 *
 * Kenapa perlu: projek web sebelumnya hanya bisa dilihat setelah "Susun situs" lalu keluar ke
 * browser. Itu membuat sunting → lihat butuh empat langkah dan pengguna kehilangan alurnya. Di sini
 * halaman dimuat langsung dari folder projek, jadi menekan "Pratinjau" setelah mengetik CSS memberi
 * hasil seketika — tanpa internet, tanpa keluar aplikasi.
 *
 * Catatan jujur yang ditampilkan ke pengguna: service worker (mode offline PWA) **tidak** jalan di
 * `file://`; itu batasan browser, bukan bug aplikasi.
 */
@Composable
fun PratinjauSitusDialog(akarProjek: File, onTutup: () -> Unit) {
    val folder = remember(akarProjek) { SitusStatis.sumber(akarProjek) }
    val indeks = folder?.let { File(it, "index.html") }
    var galat by remember { mutableStateOf<String?>(null) }
    var muatUlang by remember { mutableStateOf(0) }

    Dialog(onDismissRequest = onTutup, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxSize().background(Kode.background).padding(8.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    "Pratinjau situs",
                    style = MaterialTheme.typography.titleSmall,
                    color = Kode.text,
                    modifier = Modifier.weight(1f),
                )
                if (indeks != null) {
                    GhostButton(
                        "Muat ulang",
                        { muatUlang += 1 },
                        Modifier.testTag("muat-ulang-pratinjau"),
                    )
                }
                PrimaryButton("Tutup", onTutup, Modifier.testTag("tutup-pratinjau"))
            }
            Spacer(Modifier.height(6.dp))
            if (indeks == null || !indeks.isFile) {
                // Pesan yang bisa ditindaklanjuti, bukan layar kosong: pengguna harus tahu berkas
                // mana yang harus dibuat, dan di folder mana.
                Text(
                    "index.html belum ada. Pratinjau membutuhkan index.html di akar folder situs " +
                        "(biasanya web/index.html).",
                    style = MaterialTheme.typography.bodySmall,
                    color = Kode.warn,
                    modifier = Modifier.testTag("galat-pratinjau"),
                )
                return@Column
            }
            Text(
                "Dibuka dari berkas lokal. Mode offline (PWA) hanya aktif setelah situs diterbitkan " +
                    "lewat https.",
                style = MaterialTheme.typography.labelSmall,
                color = Kode.textDim,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            if (galat != null) {
                Text(
                    galat.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = Kode.error,
                    modifier = Modifier.testTag("galat-pratinjau"),
                )
            }
            AndroidView(
                modifier = Modifier.fillMaxSize().testTag("pratinjau-web"),
                factory = { ctx ->
                    WebView(ctx).apply {
                        // Izin berkas lokal diperlukan supaya CSS dan JS di folder yang sama ikut
                        // terbaca; tanpa ini halaman tampil tanpa gaya dan terlihat seperti rusak.
                        settings.allowFileAccess = true
                        settings.allowContentAccess = true
                        @SuppressLint("SetJavaScriptEnabled")
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.setSupportZoom(true)
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                galat = null
                            }
                        }
                        loadUrl("file://${indeks.absolutePath}")
                        tag = "pratinjau-${muatUlang}"
                    }
                },
                update = { view ->
                    // Muat ulang dipakai setelah pengguna menyimpan perubahan: WebView tidak tahu
                    // berkasnya berubah, jadi harus diminta membaca ulang.
                    if (view.tag != "pratinjau-$muatUlang") {
                        view.tag = "pratinjau-$muatUlang"
                        view.loadUrl("file://${indeks.absolutePath}")
                    }
                },
            )
        }
    }
}
