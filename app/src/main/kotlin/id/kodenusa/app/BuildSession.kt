package id.kodenusa.app

import id.kodenusa.core.build.BuildEvent
import id.kodenusa.core.build.BuildRequest
import id.kodenusa.core.build.BuildResult
import id.kodenusa.core.build.BuildStep
import id.kodenusa.core.build.Diagnostic
import id.kodenusa.core.build.LogStream
import id.kodenusa.core.build.Severity
import id.kodenusa.core.diagnose.ErrorCatalog
import id.kodenusa.core.project.LoadedProject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.io.File

data class BuildUiState(
    val running: Boolean = false,
    val currentStep: BuildStep? = null,
    val completedSteps: List<BuildStep> = emptyList(),
    val log: List<String> = emptyList(),
    val diagnostics: List<Diagnostic> = emptyList(),
    val result: BuildResult? = null,
    val fatal: String? = null,
) {
    val apk: File? get() = result?.apk
    val hasErrors: Boolean get() = result?.let { it.errorCount > 0 } == true || fatal != null
}

/**
 * Menjalankan build dan menyimpan hasilnya untuk UI.
 *
 * Catatan perilaku: log dibatasi [MAX_LOG_LINES] baris dan dipotong dari atas. Tanpa batas ini,
 * satu build yang gagal berulang bisa menghabiskan puluhan MB RAM hanya untuk teks.
 */
class BuildSession(
    private val scope: CoroutineScope,
    /** Batas baris log; diturunkan di mode hemat supaya RAM tidak habis untuk teks. */
    private val batasLog: Int = MAX_LOG_LINES,
) {

    var state: BuildUiState = BuildUiState()
        private set(value) {
            field = value
            onChange?.invoke(value)
        }

    var onChange: ((BuildUiState) -> Unit)? = null

    private var job: Job? = null

    fun start(engine: () -> Result<id.kodenusa.core.build.BuildEngine>, loaded: LoadedProject) {
        val config = loaded.config
        if (config == null) {
            state = state.copy(fatal = "Projek belum bisa dibangun: ${loaded.issues.joinToString("; ") { it.fallbackMessage }}")
            return
        }
        job?.cancel()
        state = BuildUiState(running = true)
        job = scope.launch {
            val built = engine()
            val buildEngine = built.getOrElse { error ->
                state = state.copy(running = false, fatal = error.message)
                return@launch
            }
            buildEngine.build(BuildRequest.Local(config))
                .catch { error -> state = state.copy(running = false, fatal = error.message) }
                .collect { event -> apply(event) }
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
        state = state.copy(running = false, currentStep = null)
    }

    private fun apply(event: BuildEvent) {
        when (event) {
            is BuildEvent.StepStarted -> state = state.copy(currentStep = event.step)

            is BuildEvent.StepSkipped -> state = state.copy(
                log = (state.log + "${event.step.title}: dilewati (${event.reason})").takeLast(batasLog),
            )

            is BuildEvent.Progress -> state = state.copy(currentStep = event.step)

            is BuildEvent.Log -> {
                if (event.stream == LogStream.SYSTEM) {
                    state = state.copy(log = (state.log + event.line).takeLast(batasLog))
                }
            }

            is BuildEvent.Problem -> state = state.copy(
                diagnostics = (state.diagnostics + event.diagnostic)
                    .distinctBy { "${it.messageId}:${it.file}:${it.line}:${it.rawMessage}" },
            )

            is BuildEvent.Artifact -> Unit

            is BuildEvent.Finished -> state = state.copy(
                running = false,
                currentStep = null,
                completedSteps = state.completedSteps + event.result.stepsExecuted,
                result = event.result,
                log = (state.log + ringkasan(event.result)).takeLast(batasLog),
            )
        }
    }

    private fun ringkasan(result: BuildResult): String {
        val detik = "%.1f".format(result.durationMs / 1000.0)
        return if (result.success) {
            "Selesai dalam $detik detik. APK: ${result.apk?.name ?: "-"}"
        } else {
            "Gagal setelah $detik detik. ${result.errorCount} masalah perlu diperbaiki."
        }
    }

    companion object {
        const val MAX_LOG_LINES = 2_000
    }
}

/** Titik masuk tunggal untuk menjelaskan diagnostik ke user. */
fun explain(diagnostic: Diagnostic) = ErrorCatalog.get(diagnostic.messageId).let { info ->
    ExplainedError(
        title = info.title,
        explanation = info.explanation,
        fix = info.fix,
        location = listOfNotNull(diagnostic.file?.substringAfterLast('/'), diagnostic.line?.let { "baris $it" })
            .joinToString(" · "),
        raw = diagnostic.rawMessage,
        severity = diagnostic.severity,
    )
}

data class ExplainedError(
    val title: String,
    val explanation: String,
    val fix: String,
    val location: String,
    val raw: String,
    val severity: Severity,
)
