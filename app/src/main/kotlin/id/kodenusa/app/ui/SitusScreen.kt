package id.kodenusa.app.ui

import android.content.Intent
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import id.kodenusa.app.GitSettingsStore
import id.kodenusa.core.build.HasilSitus
import id.kodenusa.core.build.SitusGagalException
import id.kodenusa.core.build.SitusStatis
import id.kodenusa.core.git.Hasil
import id.kodenusa.core.git.RepoGit
import id.kodenusa.ui.CodeTextStyle
import id.kodenusa.ui.GhostButton
import id.kodenusa.ui.KodenusaPalette
import id.kodenusa.ui.Panel
import id.kodenusa.ui.Pill
import id.kodenusa.ui.PrimaryButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Layar "Situs" — jalur untuk orang yang ingin **website**, bukan APK.
 *
 * Tidak ada kompilasi di sini: situs hanyalah HTML/CSS/JS yang disalin rapi. Karena itu layar ini
 * bisa bekerja walau toolchain Android belum siap, dan hasilnya bisa langsung dibuka di browser
 * mana pun atau dibagikan sebagai zip.
 */
@Composable
fun SitusScreen(
    projectDir: File,
    onBack: () -> Unit,
) {
    val konteks = LocalContext.current
    val papanKlip = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val setelanGit = remember(konteks) { GitSettingsStore(konteks) }
    var hasil by remember(projectDir) { mutableStateOf<HasilSitus?>(null) }
    var galat by remember(projectDir) { mutableStateOf<String?>(null) }
    var pesan by remember(projectDir) { mutableStateOf<String?>(null) }
    var sibuk by remember(projectDir) { mutableStateOf(false) }
    var alamat by remember(projectDir) { mutableStateOf(setelanGit.alamatRepo()) }
    var token by remember(projectDir) { mutableStateOf(setelanGit.token()) }
    var terbitSibuk by remember(projectDir) { mutableStateOf(false) }
    var tautan by remember(projectDir) { mutableStateOf<String?>(null) }
    var terbitGalat by remember(projectDir) { mutableStateOf<String?>(null) }
    val alasanTerbit = when {
        alamat.isBlank() -> "Isi alamat repo GitHub dulu, misalnya https://github.com/nama/repo."
        token.isBlank() -> "Isi token GitHub dulu, lalu simpan."
        !setelanGit.bolehDiterbitkan(alamat) -> "Alamat harus diawali https:// atau file:// (SSH belum didukung)."
        hasil == null -> "Susun situs dulu supaya ada yang diterbitkan."
        else -> null
    }

    fun susun() {
        sibuk = true
        galat = null
        pesan = null
        scope.launch {
            val keluaran = withContext(Dispatchers.IO) {
                runCatching { SitusStatis.susun(projectDir) }
            }
            sibuk = false
            keluaran.fold(
                onSuccess = { hasil = it },
                onFailure = { salah ->
                    hasil = null
                    galat = if (salah is SitusGagalException) {
                        salah.message
                    } else {
                        "Situs gagal disusun: ${salah.message ?: "penyebab tidak diketahui"}"
                    }
                },
            )
        }
    }

    Scaffold(containerColor = Color(KodenusaPalette.BACKGROUND)) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                GhostButton("Kembali", onBack)
                Spacer(Modifier.weight(1f))
                when {
                    sibuk -> Pill("menyusun", KodenusaPalette.INFO)
                    hasil != null -> Pill("situs siap", KodenusaPalette.OK)
                    galat != null -> Pill("perlu diperbaiki", KodenusaPalette.ERROR)
                    else -> Pill("belum disusun", KodenusaPalette.TEXT_DIM)
                }
            }

            Panel(Modifier.fillMaxWidth()) {
                Column {
                    Text(
                        "Situs murni",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(KodenusaPalette.TEXT),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "HTML, CSS, dan JavaScript saja. Hasilnya bisa dibuka di browser mana pun, " +
                            "tanpa memasang aplikasi dan tanpa Play Store.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(KodenusaPalette.TEXT_DIM),
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Sumber: ${SitusStatis.sumber(projectDir)?.let { "ditemukan" } ?: "belum ada"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(KodenusaPalette.TEXT_DIM),
                    )
                    if (SitusStatis.sumber(projectDir) == null) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Tambahkan berkas web/index.html — atau pakai template \"Situs murni\" saat membuat projek.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(KodenusaPalette.WARN),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    PrimaryButton(
                        text = if (hasil == null) "Susun situs" else "Susun ulang",
                        onClick = ::susun,
                        modifier = Modifier.fillMaxWidth().testTag("susun-situs"),
                        loading = sibuk,
                        enabled = !sibuk,
                    )
                }
            }

            galat?.let { salah ->
                Panel(Modifier.fillMaxWidth().testTag("galat-situs")) {
                    Column {
                        Text("Belum bisa disusun", style = MaterialTheme.typography.titleMedium, color = Color(KodenusaPalette.ERROR))
                        Spacer(Modifier.height(6.dp))
                        Text(salah, style = MaterialTheme.typography.bodyMedium, color = Color(KodenusaPalette.TEXT))
                    }
                }
            }

            hasil?.let { situs ->
                Panel(Modifier.fillMaxWidth().testTag("ringkasan-situs")) {
                    Column {
                        Text("Isi situs", style = MaterialTheme.typography.titleMedium, color = Color(KodenusaPalette.TEXT))
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "${situs.berkas.size} berkas · ${ukuranManusia(situs.totalByte)} · ${situs.jumlahHtml} halaman",
                            style = CodeTextStyle,
                            color = Color(KodenusaPalette.ACCENT),
                        )
                        Spacer(Modifier.height(8.dp))
                        situs.berkas.take(8).forEach { berkas ->
                            Text(
                                "${berkas.jalur}  (${ukuranManusia(berkas.ukuran)})",
                                style = CodeTextStyle.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize),
                                color = Color(KodenusaPalette.TEXT_DIM),
                            )
                        }
                        if (situs.berkas.size > 8) {
                            Text(
                                "+${situs.berkas.size - 8} berkas lain",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(KodenusaPalette.TEXT_DIM),
                            )
                        }
                        if (situs.diabaikan.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Dilewati: " + situs.diabaikan.take(4).joinToString(", ") +
                                    if (situs.diabaikan.size > 4) " dan ${situs.diabaikan.size - 4} lainnya" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(KodenusaPalette.TEXT_DIM),
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GhostButton(
                                "Buka di browser",
                                {
                                    bukaBerkas(konteks, File(situs.folder, "index.html"), "text/html")
                                },
                                modifier = Modifier.weight(1f).testTag("buka-situs"),
                            )
                            GhostButton(
                                "Bagikan zip",
                                {
                                    scope.launch {
                                        val berkas = withContext(Dispatchers.IO) {
                                            runCatching { zipSitus(situs.folder, konteks.cacheDir, projectDir.name) }
                                        }
                                        berkas.fold(
                                            onSuccess = { bagikanBerkas(konteks, it, "application/zip") },
                                            onFailure = { pesan = "Zip gagal dibuat: ${it.message}" },
                                        )
                                    }
                                },
                                modifier = Modifier.weight(1f).testTag("bagikan-situs"),
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Terbit gratis ke GitHub Pages: siapkan token GitHub dulu di layar GitHub (menyusul).",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(KodenusaPalette.TEXT_DIM),
                        )
                    }
                }
            }

            Panel(Modifier.fillMaxWidth().testTag("panel-terbit")) {
                Column {
                    Text(
                        "Terbitkan ke GitHub Pages",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(KodenusaPalette.TEXT),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Gratis, tanpa server KodeNusa. Situs dikirim ke cabang ${GitSettingsStore.CABANG_BAWAAN} " +
                            "dari repo sementara, jadi kode Java projek tidak ikut tampil.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(KodenusaPalette.TEXT_DIM),
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = alamat,
                        onValueChange = { alamat = it; terbitGalat = null },
                        label = { Text("Alamat repo GitHub") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("alamat-repo"),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = token,
                        onValueChange = { token = it; terbitGalat = null },
                        label = { Text("Token GitHub") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth().testTag("token-github"),
                    )
                    Spacer(Modifier.height(12.dp))
                    PrimaryButton(
                        text = "Terbitkan sekarang",
                        onClick = {
                            setelanGit.simpan(alamat, token)
                            terbitSibuk = true
                            terbitGalat = null
                            tautan = null
                            val folder = hasil?.folder
                            scope.launch {
                                val keluaran = withContext(Dispatchers.IO) {
                                    if (folder == null) {
                                        Hasil.Gagal("Susun situs dulu sebelum menerbitkan.")
                                    } else {
                                        RepoGit.terbitkanSitus(
                                            folderSitus = folder,
                                            urlRepo = alamat,
                                            token = token,
                                            pesan = "Terbitkan situs dari KodeNusa",
                                        )
                                    }
                                }
                                terbitSibuk = false
                                when (keluaran) {
                                    is Hasil.Sukses -> {
                                        tautan = setelanGit.tautanSitus()
                                        pesan = if (tautan == null) {
                                            "Situs terkirim. Tautan publik hanya dihitung untuk repo github.com."
                                        } else {
                                            "Situs terbit. Tautan bisa dibuka siapa pun."
                                        }
                                    }
                                    is Hasil.Gagal -> terbitGalat = keluaran.pesan
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("terbitkan-situs"),
                        enabled = alasanTerbit == null && !terbitSibuk,
                        loading = terbitSibuk,
                    )
                    if (alasanTerbit != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(alasanTerbit, style = MaterialTheme.typography.bodySmall, color = Color(KodenusaPalette.TEXT_DIM))
                    }
                    terbitGalat?.let { salah ->
                        Spacer(Modifier.height(8.dp))
                        Text(salah, style = MaterialTheme.typography.bodyMedium, color = Color(KodenusaPalette.ERROR), modifier = Modifier.testTag("galat-terbit"))
                    }
                    tautan?.let { alamatSitus ->
                        Spacer(Modifier.height(10.dp))
                        Text(alamatSitus, style = CodeTextStyle, color = Color(KodenusaPalette.ACCENT), modifier = Modifier.testTag("tautan-situs"))
                        Spacer(Modifier.height(8.dp))
                        GhostButton(
                            "Salin tautan",
                            { papanKlip.setText(AnnotatedString(alamatSitus)) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            pesan?.let { info ->
                Panel(Modifier.fillMaxWidth()) {
                    Text(info, style = MaterialTheme.typography.bodyMedium, color = Color(KodenusaPalette.TEXT))
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Membuka berkas hasil dengan aplikasi lain (browser untuk HTML, pemilih zip untuk arsip). */
private fun bukaBerkas(konteks: android.content.Context, berkas: File, mime: String) {
    if (!berkas.isFile) return
    val uri = FileProvider.getUriForFile(konteks, "${konteks.packageName}.berkas", berkas)
    val niat = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mime)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { konteks.startActivity(niat) }
}

private fun bagikanBerkas(konteks: android.content.Context, berkas: File, mime: String) {
    val uri = FileProvider.getUriForFile(konteks, "${konteks.packageName}.berkas", berkas)
    val niat = Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val pilih = Intent.createChooser(niat, "Bagikan situs").apply {
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { konteks.startActivity(pilih) }
}

/** Membungkus folder situs jadi satu zip di folder cache supaya bisa dibagikan. */
internal fun zipSitus(folder: File, cacheDir: File, nama: String): File {
    val aman = nama.replace(Regex("[^A-Za-z0-9._-]+"), "-").trim('-').ifBlank { "situs" }
    val target = File(cacheDir, "situs-$aman.zip")
    ZipOutputStream(target.outputStream().buffered()).use { zip ->
        folder.walkTopDown()
            .filter { it.isFile }
            .sortedBy { it.absolutePath }
            .forEach { berkas ->
                val jalur = berkas.absolutePath.removePrefix(folder.absolutePath + File.separator)
                    .replace(File.separatorChar, '/')
                zip.putNextEntry(ZipEntry(jalur))
                berkas.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
    }
    return target
}

internal fun ukuranManusia(byte: Long): String = when {
    byte < 1024 -> "$byte B"
    byte < 1024 * 1024 -> "${byte / 1024} KB"
    else -> String.format("%.1f MB", byte / (1024.0 * 1024.0))
}
