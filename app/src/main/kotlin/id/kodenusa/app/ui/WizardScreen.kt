package id.kodenusa.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import id.kodenusa.app.AppContainer
import id.kodenusa.app.TemplateInfo
import id.kodenusa.app.Templates
import id.kodenusa.core.build.Severity
import id.kodenusa.core.project.KodenusaProject
import id.kodenusa.core.project.ProjectValidator
import id.kodenusa.ui.GhostButton
import id.kodenusa.ui.KodeField
import id.kodenusa.ui.KodenusaPalette
import id.kodenusa.ui.Panel
import id.kodenusa.ui.PrimaryButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Tiga hal yang mau dibuat orang, ditanyakan lebih dulu daripada template.
 *
 * Dulu layar ini menanyakan "Nama aplikasi" dan "ID aplikasi" kepada semua orang — termasuk yang
 * cuma mau menulis halaman web. Sekarang yang pertama ditanya adalah jenis projek, dan isian yang
 * tidak relevan disembunyikan.
 */
private enum class Jenis(val label: String, val keyword: String, val contohNama: String) {
    APLIKASI("Aplikasi Android", "aplikasi", "Nama aplikasi"),
    WEB("Website saja (tanpa APK)", "web-murni", "Nama website"),
    WEBVIEW("Website jadi APK", "web", "Nama aplikasi"),
    KOSONG("Mulai kosong", "kosong", "Nama projek"),
}

@Composable
fun WizardScreen(
    container: AppContainer,
    onCancel: () -> Unit,
    onCreated: (File) -> Unit,
) {
    // Template boleh kosong (user menulis sendiri). Kalau aset gagal dibaca, sebabnya ditampilkan
    // supaya tombol "Buat projek" tidak pernah mati tanpa penjelasan.
    val dimuat = remember { container.templates.load() }
    val templates = dimuat.getOrNull().orEmpty()

    var jenis by remember { mutableStateOf(Jenis.APLIKASI) }
    val pilihan = remember(templates, jenis) {
        when (jenis) {
            Jenis.KOSONG -> listOf(TemplateInfoKosong)
            else -> templates.filter { it.jenis == jenis.keyword }.ifEmpty {
                // Kalau aset tidak punya template bergaya itu, projek kosong tetap harus bisa dibuat.
                listOf(TemplateInfoKosong)
            }
        }
    }
    var selected by remember(pilihan) { mutableStateOf(pilihan.firstOrNull()) }
    var name by remember { mutableStateOf("") }
    var appId by remember { mutableStateOf("") }
    var lanjutan by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // ID kosong berarti "buatkan dari nama", jadi yang divalidasi adalah ID yang benar-benar dipakai.
    val efektifId = appId.trim().ifBlank { defaultId(name) }
    val candidate = KodenusaProject(name = name.trim(), applicationId = efektifId)
    // Situs murni tidak punya applicationId yang berarti, jadi jangan pernah menahan tombol Buat
    // hanya karena ID turunan dari nama website tidak lolos aturan paket Android.
    val webSaja = jenis == Jenis.WEB
    val masalah = if (name.isBlank() || webSaja) emptyList() else ProjectValidator.validate(candidate)
    val nameError = masalah.firstOrNull { it.field == "name" }
    val idError = masalah.firstOrNull { it.field == "applicationId" }
    val masalahLain = masalah.filter {
        it.severity == Severity.ERROR && it.field != "name" && it.field != "applicationId"
    }
    val canCreate = selected != null && name.isNotBlank() &&
        masalah.none { it.severity == Severity.ERROR } && !busy

    Scaffold(
        containerColor = Color(KodenusaPalette.BACKGROUND),
        // Tombol aksi tinggal di bilah bawah, tidak ikut tergulir: dulu tombol ini adalah item
        // terakhir LazyColumn sehingga tertutup keyboard dan user tidak bisa menekannya.
        bottomBar = {
            Surface(color = Color(KodenusaPalette.BACKGROUND)) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    GhostButton("Batal", onCancel, Modifier.weight(1f))
                    PrimaryButton(
                        text = "Buat projek",
                        onClick = {
                            val template = selected ?: return@PrimaryButton
                            busy = true
                            failure = null
                            scope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    container.templates.create(
                                        templateId = template.id,
                                        target = container.projectStore.uniqueFolder(name.trim()),
                                        appName = name.trim(),
                                        appId = efektifId,
                                    )
                                }
                                busy = false
                                result.onSuccess(onCreated).onFailure { failure = it.message }
                            }
                        },
                        modifier = Modifier.weight(1.4f).testTag("tombol-buat"),
                        enabled = canCreate,
                        loading = busy,
                    )
                }
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp)
                .testTag("daftar-wizard"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 20.dp),
        ) {
            item {
                Text(
                    "Mau bikin apa?",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color(KodenusaPalette.TEXT),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Pilih jenisnya dulu. Isian yang tidak perlu tidak akan ditanyakan.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(KodenusaPalette.TEXT_DIM),
                )
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Jenis.entries.forEach { opsi ->
                        val aktif = jenis == opsi
                        Surface(
                            color = Color(
                                if (aktif) KodenusaPalette.SURFACE_RAISED else KodenusaPalette.SURFACE,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (jenis != opsi) {
                                        jenis = opsi
                                        appId = ""
                                    }
                                }
                                .testTag("jenis-${opsi.keyword}"),
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    if (aktif) "● " else "○ ",
                                    color = Color(
                                        if (aktif) KodenusaPalette.ACCENT else KodenusaPalette.TEXT_DIM,
                                    ),
                                )
                                Text(
                                    opsi.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(KodenusaPalette.TEXT),
                                    fontWeight = if (aktif) FontWeight.SemiBold else FontWeight.Normal,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(
                                    onClick = {
                                        if (jenis != opsi) {
                                            jenis = opsi
                                            appId = ""
                                        }
                                    },
                                ) {
                                    Text(
                                        if (aktif) "dipilih" else "pilih",
                                        color = Color(KodenusaPalette.TEXT_DIM),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            val galatTemplate = dimuat.exceptionOrNull()
            if (galatTemplate != null) {
                item {
                    Panel(Modifier.fillMaxWidth()) {
                        Text(
                            "Template bawaan gagal dimuat: " +
                                "${galatTemplate.message ?: "sebab tidak diketahui"}. " +
                                "Kamu masih bisa mulai dari projek kosong.",
                            color = Color(KodenusaPalette.ERROR),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            items(pilihan, key = { it.id }) { template ->
                TemplateCard(template, selected?.id == template.id) {
                    selected = template
                    // Nama diisi otomatis dari template; pengguna boleh menimpanya kapan saja.
                    if (name.isBlank() && template.id != Templates.BLANK_ID) name = template.title
                }
            }

            item {
                KodeField(
                    label = jenis.contohNama,
                    value = name,
                    onValueChange = { name = it.take(50) },
                    helper = nameError?.fallbackMessage ?: when (jenis) {
                        Jenis.WEB -> "Judul halaman web-mu. Contoh: Katalog Toko Bu Sri"
                        Jenis.WEBVIEW -> "Muncul di layar utama HP. Contoh: Katalog Toko"
                        Jenis.KOSONG -> "Nama folder projek. Contoh: Percobaan Pertama"
                        Jenis.APLIKASI -> "Muncul di layar utama HP. Contoh: Kasir Warung Bu Sri"
                    },
                    isError = nameError != null,
                )
            }

            if (masalahLain.isNotEmpty()) {
                item {
                    Panel(Modifier.fillMaxWidth()) {
                        masalahLain.forEach { issue ->
                            Text(
                                issue.fallbackMessage,
                                color = Color(KodenusaPalette.ERROR),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }

            // ID aplikasi hanya relevan untuk APK, dan hampir tidak pernah perlu diubah pemula.
            item {
                if (jenis == Jenis.WEB) {
                    Text(
                        "Website saja: hanya HTML, CSS, dan JavaScript. Tidak ada folder aplikasi Android " +
                            "dan tidak ada APK — hasilnya dibuka lewat tautan.",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(KodenusaPalette.OK),
                        modifier = Modifier.testTag("catatan-web-murni"),
                    )
                } else if (jenis == Jenis.WEBVIEW || jenis == Jenis.KOSONG) {
                    Text(
                        "Website tetap dibungkus jadi APK, tetapi kamu tidak perlu memikirkan ID aplikasi.",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(KodenusaPalette.TEXT_DIM),
                    )
                } else {
                    TextButton(onClick = { lanjutan = !lanjutan }) {
                        Text(
                            if (lanjutan) "Sembunyikan pengaturan lanjutan" else "Pengaturan lanjutan",
                            color = Color(KodenusaPalette.TEXT_DIM),
                        )
                    }
                }
            }

            if (lanjutan && jenis == Jenis.APLIKASI) {
                item {
                    KodeField(
                        label = "ID aplikasi",
                        value = appId,
                        onValueChange = { appId = it.take(60) },
                        helper = idError?.fallbackMessage
                            ?: "Kosongkan untuk dibuat otomatis dari nama. Yang akan dipakai: $efektifId",
                        isError = idError != null,
                    )
                }
            }

            failure?.let { pesan ->
                item {
                    Panel(Modifier.fillMaxWidth()) {
                        Text(
                            pesan,
                            color = Color(KodenusaPalette.ERROR),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TemplateCard(template: TemplateInfo, selected: Boolean, onClick: () -> Unit) {
    Panel(
        Modifier.fillMaxWidth().testTag("template-${template.id}"),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (selected) "● " else "○ ",
                    color = Color(if (selected) KodenusaPalette.ACCENT else KodenusaPalette.TEXT_DIM),
                )
                Text(
                    template.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color(KodenusaPalette.TEXT),
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onClick) {
                    Text(if (selected) "dipilih" else "pilih", color = Color(KodenusaPalette.TEXT_DIM))
                }
            }
            Text(
                template.tagline,
                style = MaterialTheme.typography.bodySmall,
                color = Color(KodenusaPalette.TEXT_DIM),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Untuk ${template.audience}",
                style = MaterialTheme.typography.labelSmall,
                color = Color(KodenusaPalette.TEXT_DIM),
            )
        }
    }
}

private fun defaultId(name: String): String {
    val slug = name.lowercase().replace(Regex("[^a-z0-9]+"), "")
    return "id.kodenusa.${slug.ifBlank { "projek" }}"
}

/** Kartu terakhir di daftar: mulai dari nol tanpa template. */
private val TemplateInfoKosong = TemplateInfo(
    id = Templates.BLANK_ID,
    title = "Projek kosong",
    tagline = "Manifest, satu activity, satu layout. Semua isi ditulis sendiri dari awal.",
    audience = "yang sudah tahu struktur projek Android",
    jenis = "kosong",
)
