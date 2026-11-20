package id.kodenusa.app.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import id.kodenusa.app.AppContainer
import id.kodenusa.app.TemplateInfo
import id.kodenusa.core.build.Severity
import id.kodenusa.core.project.KodenusaProject
import id.kodenusa.core.project.ProjectValidator
import id.kodenusa.ui.GhostButton
import id.kodenusa.ui.KodeField
import id.kodenusa.ui.KodenusaPalette
import id.kodenusa.ui.Panel
import id.kodenusa.ui.Pill
import id.kodenusa.ui.PrimaryButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun WizardScreen(
    container: AppContainer,
    onCancel: () -> Unit,
    onCreated: (File) -> Unit,
) {
    val templates = remember { container.templates.list() }
    var selected by remember { mutableStateOf(templates.firstOrNull()) }
    var name by remember { mutableStateOf("") }
    var appId by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val candidate = KodenusaProject(
        name = name,
        applicationId = appId.ifBlank { "id.kodenusa.projek" },
    )
    val issues = if (name.isBlank()) emptyList() else ProjectValidator.validate(candidate)
    val nameError = issues.firstOrNull { it.field == "name" }
    val idError = issues.firstOrNull { it.field == "applicationId" }
    val canCreate = selected != null && name.isNotBlank() && issues.none { it.severity == Severity.ERROR } && !busy

    Scaffold(containerColor = Color(KodenusaPalette.BACKGROUND)) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 20.dp),
        ) {
            item {
                Text("Projek baru", style = MaterialTheme.typography.headlineMedium, color = Color(KodenusaPalette.TEXT))
                Spacer(Modifier.height(4.dp))
                Text(
                    "Pilih template, beri nama, lalu build. Template sudah berisi kode yang bisa langsung dijalankan.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(KodenusaPalette.TEXT_DIM),
                )
            }

            items(templates, key = { it.id }) { template ->
                TemplateCard(template, selected?.id == template.id) { selected = template }
            }

            item {
                KodeField(
                    label = "Nama aplikasi",
                    value = name,
                    onValueChange = { name = it.take(50) },
                    helper = nameError?.fallbackMessage ?: "Muncul di layar utama HP. Contoh: Kasir Warung Bu Sri",
                    isError = nameError != null,
                )
            }

            item {
                KodeField(
                    label = "ID aplikasi",
                    value = appId,
                    onValueChange = { appId = it.take(60) },
                    helper = idError?.fallbackMessage
                        ?: "Unik dan tidak bisa diubah setelah dipasang. Contoh: id.kodenusa.kasirbusri",
                    isError = idError != null,
                )
            }

            if (failure != null) {
                item {
                    Panel(Modifier.fillMaxWidth()) {
                        Text(failure.orEmpty(), color = Color(KodenusaPalette.ERROR), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                        target = container.projectStore.uniqueFolder(name),
                                        appName = name.trim(),
                                        appId = appId.trim().ifBlank { defaultId(name) },
                                    )
                                }
                                busy = false
                                result.onSuccess(onCreated).onFailure { failure = it.message }
                            }
                        },
                        modifier = Modifier.weight(1.4f),
                        enabled = canCreate,
                        loading = busy,
                    )
                }
            }
        }
    }
}

@Composable
private fun TemplateCard(template: TemplateInfo, selected: Boolean, onClick: () -> Unit) {
    Panel(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    template.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(if (selected) KodenusaPalette.ACCENT else KodenusaPalette.TEXT),
                )
                if (selected) Pill("dipilih", KodenusaPalette.ACCENT)
            }
            Spacer(Modifier.height(6.dp))
            Text(template.tagline, style = MaterialTheme.typography.bodyMedium, color = Color(KodenusaPalette.TEXT_DIM))
            Spacer(Modifier.height(6.dp))
            Text(
                "Untuk ${template.audience}",
                style = MaterialTheme.typography.bodySmall,
                color = Color(KodenusaPalette.TEXT_DIM),
            )
        }
    }
}

private fun defaultId(name: String): String {
    val slug = name.lowercase().replace(Regex("[^a-z0-9]+"), "")
    return "id.kodenusa.${slug.ifBlank { "projek" }}"
}
