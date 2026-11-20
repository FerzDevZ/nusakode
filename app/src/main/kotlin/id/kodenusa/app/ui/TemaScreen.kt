package id.kodenusa.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import id.kodenusa.app.Kntema
import id.kodenusa.app.ModeTema
import id.kodenusa.app.TemaStore
import id.kodenusa.ui.Banner
import id.kodenusa.ui.GhostButton
import id.kodenusa.ui.JenisBanner
import id.kodenusa.ui.Kode
import id.kodenusa.ui.KodenusaPalette
import id.kodenusa.ui.Panel
import id.kodenusa.ui.PaletInt
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Layar Tema (ADR-035/036): pilih tema bawaan, ikut sistem, atau tema kustom `.kntema`.
 * Setiap baris punya pratinjau warna asli tema — pengguna melihat sebelum memilih.
 * Tema kustom harus lolos gerbang kontras sebelum boleh dipakai; tema buruk ditolak dengan
 * pesan yang menyebut pasangan warna yang gagal.
 */
@Composable
fun TemaScreen(
    temaStore: TemaStore,
    onDipilih: () -> Unit,
    onBack: () -> Unit,
) {
    val konteks = LocalContext.current
    val lingkup = rememberCoroutineScope()
    var mode by remember { mutableStateOf(temaStore.mode) }
    var temaKustom by remember { mutableStateOf(Kntema.daftar(Kntema.folder(konteks))) }
    var pesan by remember { mutableStateOf<Pair<JenisBanner, String>?>(null) }
    var mintaImpor by remember { mutableStateOf(false) }
    var mintaEkspor by remember { mutableStateOf(false) }
    var jsonEkspor by remember { mutableStateOf<Pair<String, String>?>(null) }
    var unduhTerbuka by remember { mutableStateOf(false) }

    fun pilih(modeBaru: ModeTema) {
        temaStore.mode = modeBaru
        mode = modeBaru
        pesan = null
        onDipilih()
    }

    fun pasangKustom(tema: Kntema.Tema, json: String) {
        val gagal = Kntema.pasanganGagal(tema.palet)
        if (gagal.isNotEmpty()) {
            pesan = JenisBanner.GALAT to "Tema ditolak: ${gagal.take(2).joinToString("; ")}."
            return
        }
        val tersimpan = Kntema.simpan(Kntema.folder(konteks), tema).getOrNull()
        if (tersimpan == null) {
            pesan = JenisBanner.GALAT to "Tema tidak bisa disimpan di penyimpanan internal."
            return
        }
        temaKustom = Kntema.daftar(Kntema.folder(konteks))
        temaStore.jsonKustom = json
        pilih(ModeTema.KUSTOM)
        pesan = JenisBanner.OK to "Tema \"${tema.nama}\" dipasang."
    }

    // Konvensi proyek: peluncur SAF dipicu dari LaunchedEffect pada bendera state.
    val pemilihImpor = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            lingkup.launch {
                val hasil = withContext(Dispatchers.IO) {
                    runCatching {
                        val byte = konteks.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                            ?: throw IllegalStateException("Berkas tidak bisa dibaca.")
                        Kntema.parse(String(byte)).getOrThrow()
                    }
                }
                hasil.onSuccess { pasangKustom(it, Kntema.serialisasi(it)) }
                    .onFailure { pesan = JenisBanner.GALAT to "Impor gagal: ${it.message}" }
            }
        }
    }

    val pengekspor = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val (nama, json) = jsonEkspor ?: return@rememberLauncherForActivityResult
        if (uri != null) {
            lingkup.launch {
                val hasil = withContext(Dispatchers.IO) {
                    runCatching {
                        konteks.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                            ?: throw IllegalStateException("Tujuan tidak bisa ditulis.")
                    }
                }
                hasil.onSuccess { pesan = JenisBanner.OK to "Tema \"$nama\" diekspor." }
                    .onFailure { pesan = JenisBanner.GALAT to "Ekspor gagal: ${it.message}" }
            }
        }
    }

    LaunchedEffect(mintaImpor) {
        if (mintaImpor) {
            mintaImpor = false
            pemilihImpor.launch(arrayOf("application/json", "application/octet-stream"))
        }
    }
    LaunchedEffect(mintaEkspor) {
        if (mintaEkspor) {
            mintaEkspor = false
            val (nama, json) = jsonEkspor ?: return@LaunchedEffect
            pengekspor.launch(nama.replace(Regex("[^A-Za-z0-9_-]"), "_") + Kntema.EKSTENSI)
        }
    }

    Scaffold(containerColor = Kode.background) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                GhostButton("Kembali", onBack, Modifier.testTag("kembali-tema"))
                Spacer(Modifier.width(12.dp))
                Text("Tema", style = MaterialTheme.typography.headlineMedium, color = Kode.text)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Warna seluruh aplikasi mengikuti pilihan di sini. Tema kustom dibagikan sebagai berkas .kntema.",
                style = MaterialTheme.typography.bodySmall,
                color = Kode.textDim,
            )
            Spacer(Modifier.height(16.dp))

            pesan?.let { (jenis, teks) ->
                Banner(jenis, teks, Modifier.testTag("pesan-tema"))
                Spacer(Modifier.height(12.dp))
            }

            BarisTema(
                nama = "Ikuti sistem",
                keterangan = "Gelap saat malam Android menyala, terang saat siang.",
                palet = if (mode == ModeTema.SISTEM && gelapSistem(konteks.resources.configuration.uiMode)) {
                    id.kodenusa.ui.PaletGelapBengkel
                } else {
                    id.kodenusa.ui.PaletTerangKertas
                },
                aktif = mode == ModeTema.SISTEM,
                testTag = "tema-sistem",
                onClick = { pilih(ModeTema.SISTEM) },
            )
            BarisTema("Gelap bengkel", "Palet asli KodeNusa. Tenang untuk kerja lama.", id.kodenusa.ui.PaletGelapBengkel, mode == ModeTema.GELAP, "tema-gelap", onClick = { pilih(ModeTema.GELAP) })
            BarisTema("Gelap AMOLED", "Hitam murni. Paling hemat baterai layar AMOLED.", id.kodenusa.ui.PaletGelapAmoled, mode == ModeTema.AMOLED, "tema-amoled", onClick = { pilih(ModeTema.AMOLED) })
            BarisTema("Terang kertas", "Untuk siang terang. Kontrasnya diuji, bukan inversi otomatis.", id.kodenusa.ui.PaletTerangKertas, mode == ModeTema.TERANG, "tema-terang", onClick = { pilih(ModeTema.TERANG) })
            BarisTema("Terang kontras tinggi", "Paling tegas, untuk mata di luar ruangan.", id.kodenusa.ui.PaletTerangTinggi, mode == ModeTema.TERANG_TINGGI, "tema-terang-tinggi", onClick = { pilih(ModeTema.TERANG_TINGGI) })

            Spacer(Modifier.height(24.dp))
            Text("Tema kustom", style = MaterialTheme.typography.titleMedium, color = Kode.text)
            Spacer(Modifier.height(8.dp))

            for (tema in temaKustom) {
                val aktif = mode == ModeTema.KUSTOM && temaStore.jsonKustom == Kntema.serialisasi(tema)
                BarisTema(
                    nama = tema.nama,
                    keterangan = if (tema.gelap) "Tema gelap dari berkas" else "Tema terang dari berkas",
                    palet = tema.palet,
                    aktif = aktif,
                    testTag = "tema-kustom-${tema.nama}",
                    onClick = {
                        temaStore.jsonKustom = Kntema.serialisasi(tema)
                        pilih(ModeTema.KUSTOM)
                    },
                    onEkspor = {
                        jsonEkspor = tema.nama to Kntema.serialisasi(tema)
                        mintaEkspor = true
                    },
                    onHapus = {
                        Kntema.hapus(tema)
                        temaKustom = Kntema.daftar(Kntema.folder(konteks))
                        if (mode == ModeTema.KUSTOM) pilih(ModeTema.GELAP)
                        pesan = JenisBanner.INFO to "Tema \"${tema.nama}\" dihapus."
                    },
                )
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostButton("Impor .kntema", { mintaImpor = true }, Modifier.testTag("impor-tema"))
                GhostButton("Unduh dari tautan", { unduhTerbuka = true }, Modifier.testTag("unduh-tema"))
            }
            Spacer(Modifier.height(12.dp))
            Banner(
                JenisBanner.INFO,
                "Tema yang kontrasnya di bawah standar baca (4,5:1) akan ditolak saat dipasang — " +
                    "supaya teks tetap terbaca apa pun tema yang Anda pilih.",
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    if (unduhTerbuka) {
        DialogUnduhTema(
            onTutup = { unduhTerbuka = false },
            onUnduh = { tautan ->
                unduhTerbuka = false
                lingkup.launch {
                    pesan = JenisBanner.INFO to "Mengunduh tema…"
                    val hasil = withContext(Dispatchers.IO) {
                        runCatching {
                            val sambungan = URL(tautan).openConnection() as HttpURLConnection
                            sambungan.connectTimeout = 10_000
                            sambungan.readTimeout = 10_000
                            sambungan.setRequestProperty("User-Agent", "KodeNusa")
                            if (sambungan.responseCode !in 200..299) {
                                throw IllegalStateException("Server menjawab ${sambungan.responseCode}.")
                            }
                            val byte = sambungan.inputStream.use { input ->
                                val penampung = java.io.ByteArrayOutputStream()
                                val penyangga = ByteArray(4_096)
                                var terbaca: Int
                                var total = 0
                                while (input.read(penyangga).also { terbaca = it } != -1) {
                                    total += terbaca
                                    if (total > Kntema.MAKS_BYTE) throw IllegalStateException("Berkas tema lebih dari 4 KB.")
                                    penampung.write(penyangga, 0, terbaca)
                                }
                                penampung.toByteArray()
                            }
                            Kntema.parse(String(byte)).getOrThrow()
                        }
                    }
                    hasil.onSuccess { pasangKustom(it, Kntema.serialisasi(it)) }
                        .onFailure { pesan = JenisBanner.GALAT to "Unduh gagal: ${it.message}" }
                }
            },
        )
    }
}

private fun gelapSistem(uiMode: Int): Boolean =
    (uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
        android.content.res.Configuration.UI_MODE_NIGHT_YES

@Composable
private fun BarisTema(
    nama: String,
    keterangan: String,
    palet: PaletInt,
    aktif: Boolean,
    testTag: String,
    onClick: () -> Unit,
    onEkspor: (() -> Unit)? = null,
    onHapus: (() -> Unit)? = null,
) {
    Panel(Modifier.padding(bottom = 8.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .testTag(testTag)
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PratinjauTema(palet)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(nama, style = MaterialTheme.typography.titleMedium, color = Kode.text)
                Text(keterangan, style = MaterialTheme.typography.bodySmall, color = Kode.textDim)
            }
            Box(
                Modifier
                    .size(20.dp)
                    .border(
                        androidx.compose.foundation.BorderStroke(2.dp, if (aktif) Kode.accent else Kode.borderStrong),
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (aktif) Box(Modifier.size(10.dp).background(Kode.accent, CircleShape))
            }
        }
        if (onEkspor != null || onHapus != null) {
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                onEkspor?.let { GhostButton("Ekspor", it) }
                onHapus?.let { GhostButton("Hapus", it) }
            }
        }
    }
}

/** Kartu mini warna tema: latar + permukaan + aksen + contoh teks — memakai warna tema itu sendiri. */
@Composable
private fun PratinjauTema(palet: PaletInt) {
    Box(
        Modifier
            .size(width = 72.dp, height = 48.dp)
            .background(Color(palet.background), RoundedCornerShape(KodenusaPalette.RADIUS_SMALL.dp))
            .border(
                androidx.compose.foundation.BorderStroke(1.dp, Color(palet.border)),
                RoundedCornerShape(KodenusaPalette.RADIUS_SMALL.dp),
            ),
    ) {
        Box(
            Modifier
                .padding(6.dp)
                .size(width = 60.dp, height = 14.dp)
                .background(Color(palet.surface), RoundedCornerShape(4.dp)),
        )
        Box(
            Modifier
                .padding(start = 6.dp, bottom = 6.dp)
                .size(10.dp)
                .background(Color(palet.accent), CircleShape)
                .align(Alignment.BottomStart),
        )
        Text(
            "Aa",
            style = MaterialTheme.typography.labelMedium,
            color = Color(palet.text),
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp),
        )
    }
}

@Composable
private fun DialogUnduhTema(onTutup: () -> Unit, onUnduh: (String) -> Unit) {
    var tautan by remember { mutableStateOf("") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onTutup,
        title = { Text("Unduh tema dari tautan", color = Kode.text) },
        text = {
            OutlinedTextField(
                value = tautan,
                onValueChange = { tautan = it },
                label = { Text("Tautan berkas .kntema") },
                singleLine = true,
                modifier = Modifier.testTag("tautan-tema"),
            )
        },
        confirmButton = {
            GhostButton("Unduh", { if (tautan.isNotBlank()) onUnduh(tautan) }, Modifier.testTag("tombol-unduh"))
        },
        dismissButton = { GhostButton("Batal", onTutup) },
        containerColor = Kode.surface,
    )
}
