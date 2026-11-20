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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
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
import id.kodenusa.app.ApkInstaller
import id.kodenusa.app.AppContainer
import id.kodenusa.app.BuildSession
import id.kodenusa.app.BuildUiState
import id.kodenusa.app.explain
import id.kodenusa.core.build.BuildStep
import id.kodenusa.core.build.Severity
import id.kodenusa.core.project.ProjectLoader
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

@Composable
fun BuildScreen(
    container: AppContainer,
    projectDir: File,
    onBack: () -> Unit,
    onSitus: () -> Unit,
    onGit: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val session = remember(projectDir) { BuildSession(scope) }
    var state by remember { mutableStateOf(BuildUiState()) }
    var setupMessage by remember { mutableStateOf<String?>(null) }
    var aiTerbuka by remember { mutableStateOf(false) }

    session.onChange = { state = it }

    LaunchedEffect(projectDir) {
        val loaded = ProjectLoader.load(projectDir)
        if (!loaded.isBuildable) {
            setupMessage = container.projectStore.issuesText(loaded)
            return@LaunchedEffect
        }
        setupMessage = null
        val status = withContext(Dispatchers.IO) { container.toolchainInstaller.install() }
        when (status) {
            is id.kodenusa.app.ToolchainInstaller.Status.Ready -> {
                val engine = container.newEngine(status.toolchain)
                session.start({ Result.success(engine) }, loaded)
            }

            is id.kodenusa.app.ToolchainInstaller.Status.Missing -> {
                setupMessage = if (status.bundled) {
                    "Paket tool build tidak lengkap. Kurang: ${status.names.joinToString(", ")}. " +
                        "Pasang ulang KodeNusa versi lengkap untuk ${status.abi}."
                } else {
                    "Tool build untuk ${status.abi} belum ada di dalam aplikasi ini. " +
                        "Tanpa aapt2 dan java, APK tidak bisa dirakit di HP. " +
                        "Versi ini hanya menguji alur editor, projek, dan diagnostik."
                }
            }

            is id.kodenusa.app.ToolchainInstaller.Status.Broken -> {
                setupMessage = "Tool ${status.name} tidak bisa dipakai: ${status.reason}"
            }
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
                GhostButton("Kembali", {
                    session.cancel()
                    onBack()
                })
                Spacer(Modifier.width(8.dp))
                GhostButton("Situs", onSitus, modifier = Modifier.testTag("buka-situs"))
                GhostButton("Git", onGit, modifier = Modifier.testTag("buka-git"))
                Spacer(Modifier.weight(1f))
                when {
                    state.running -> Pill("sedang build", KodenusaPalette.INFO)
                    state.apk != null -> Pill("APK siap", KodenusaPalette.OK)
                    state.result != null || setupMessage != null -> Pill("gagal", KodenusaPalette.ERROR)
                    else -> Pill("siap", KodenusaPalette.TEXT_DIM)
                }
            }

            setupMessage?.let { pesan ->
                Panel(Modifier.fillMaxWidth()) {
                    Column {
                        Text(
                            "Build belum bisa dijalankan",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color(KodenusaPalette.ERROR),
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(pesan, style = MaterialTheme.typography.bodyMedium, color = Color(KodenusaPalette.TEXT_DIM))
                    }
                }
            }

            StepsPanel(state)

            if (state.log.isNotEmpty()) {
                LogPanel(state.log)
            }

            if (state.diagnostics.isNotEmpty()) {
                Text("Perlu diperbaiki", style = MaterialTheme.typography.titleMedium, color = Color(KodenusaPalette.TEXT))
                state.diagnostics.forEach { diagnostic ->
                    val info = explain(diagnostic)
                    Panel(Modifier.fillMaxWidth()) {
                        Column {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(
                                    info.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color(
                                        if (info.severity == Severity.ERROR) KodenusaPalette.ERROR else KodenusaPalette.WARN,
                                    ),
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            if (info.location.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(info.location, style = CodeTextStyle, color = Color(KodenusaPalette.ACCENT))
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(info.explanation, style = MaterialTheme.typography.bodyMedium, color = Color(KodenusaPalette.TEXT))
                            Spacer(Modifier.height(8.dp))
                            Text("Cara memperbaiki", style = MaterialTheme.typography.labelMedium, color = Color(KodenusaPalette.TEXT_DIM))
                            Text(info.fix, style = MaterialTheme.typography.bodyMedium, color = Color(KodenusaPalette.OK))
                            if (info.raw.isNotBlank()) {
                                Spacer(Modifier.height(8.dp))
                                Text(info.raw, style = CodeTextStyle.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize), color = Color(KodenusaPalette.TEXT_DIM))
                            }
                        }
                    }
                }
            }

            state.apk?.let { apk ->
                Panel(Modifier.fillMaxWidth()) {
                    Column {
                        Text(
                            "APK siap dipasang",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color(KodenusaPalette.OK),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${apk.name} · ${ukuran(apk.length())}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(KodenusaPalette.TEXT_DIM),
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PrimaryButton("Pasang", {
                                ApkInstaller.install(context, apk)
                            })
                            GhostButton("Bagikan", { ApkInstaller.share(context, apk) })
                        }
                    }
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.running) {
                    GhostButton("Hentikan", { session.cancel() }, Modifier.weight(1f))
                } else {
                    if (state.hasErrors) {
                        GhostButton("Tanya AI", { aiTerbuka = true }, Modifier.testTag("tanya-ai-galat"))
                    }
                    PrimaryButton(
                        text = if (state.result == null) "Build ulang" else "Build lagi",
                        onClick = {
                            scope.launch {
                                setupMessage = null
                                val loaded = ProjectLoader.load(projectDir)
                                val status = withContext(Dispatchers.IO) { container.toolchainInstaller.install() }
                                if (status is id.kodenusa.app.ToolchainInstaller.Status.Ready) {
                                    val engine = container.newEngine(status.toolchain)
                                    session.start({ Result.success(engine) }, loaded)
                                } else {
                                    setupMessage = "Tool build belum lengkap. Cek pesan di atas."
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = setupMessage == null && !state.running,
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (aiTerbuka) {
        AiScreen(
            store = container.aiStore,
            konteks = AiKonteks(
                galat = galatUntukAi(state, setupMessage),
                judul = "AI · galat build",
            ),
            kunciProjek = "build",
            onDismiss = { aiTerbuka = false },
            onSisip = { _, _ ->
                "Buka berkas yang ingin diperbaiki dulu, lalu tekan AI dari layar editor supaya kodenya masuk ke berkas itu."
            },
        )
    }
}

/** Merangkum galat build menjadi teks yang bisa dipahami model. */
private fun galatUntukAi(state: BuildUiState, setup: String?): String = buildString {
    setup?.let { appendLine("Masalah persiapan build: $it") }
    state.fatal?.let { appendLine("Galat utama: $it") }
    state.diagnostics.forEach { d ->
        val info = explain(d)
        appendLine("${d.severity} · ${info.title} · ${d.rawMessage}")
        if (info.fix.isNotBlank()) appendLine("  saran: ${info.fix}")
        d.file?.let { berkas ->
            appendLine("  lokasi: $berkas${d.line?.let { baris -> ":$baris" }.orEmpty()}")
        }
    }
}

@Composable
private fun StepsPanel(state: BuildUiState) {
    Panel(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Tahapan build", style = MaterialTheme.typography.titleMedium, color = Color(KodenusaPalette.TEXT))
            BuildStep.entries.filter { it != BuildStep.PREPARE }.forEach { step ->
                val done = state.completedSteps.contains(step)
                val active = state.currentStep == step
                val color = when {
                    active -> KodenusaPalette.ACCENT
                    done -> KodenusaPalette.OK
                    else -> KodenusaPalette.TEXT_DIM
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        when {
                            active -> "▸"
                            done -> "✓"
                            else -> "·"
                        },
                        style = CodeTextStyle,
                        color = Color(color),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(step.title, style = MaterialTheme.typography.bodyMedium, color = Color(color))
                        Text(
                            step.detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(KodenusaPalette.TEXT_DIM),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LogPanel(log: List<String>) {
    val listState = rememberLazyListState()
    LaunchedEffect(log.size) {
        if (log.isNotEmpty()) listState.scrollToItem(log.size - 1)
    }
    Column {
        Text("Catatan build", style = MaterialTheme.typography.titleMedium, color = Color(KodenusaPalette.TEXT))
        Spacer(Modifier.height(6.dp))
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .background(Color(KodenusaPalette.SURFACE), RoundedCornerShape(KodenusaPalette.RADIUS_SMALL.dp))
                .padding(10.dp),
        ) {
            items(log.size) { index ->
                Text(log[index], style = CodeTextStyle, color = Color(KodenusaPalette.TEXT_DIM))
            }
        }
    }
}

private fun ukuran(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / 1024.0 / 1024.0)
    bytes >= 1024 -> "%.0f KB".format(bytes / 1024.0)
    else -> "$bytes byte"
}