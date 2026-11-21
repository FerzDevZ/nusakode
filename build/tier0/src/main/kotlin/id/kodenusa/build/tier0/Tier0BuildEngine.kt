package id.kodenusa.build.tier0

import id.kodenusa.core.build.BuildEngine
import id.kodenusa.core.build.BuildEvent
import id.kodenusa.core.build.BuildRequest
import id.kodenusa.core.build.BuildResult
import id.kodenusa.core.build.BuildStep
import id.kodenusa.core.build.BuildTool
import id.kodenusa.core.build.Diagnostic
import id.kodenusa.core.build.LogStream
import id.kodenusa.core.build.Severity
import id.kodenusa.core.build.Tier0Config
import id.kodenusa.core.diagnose.DiagnosticParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Build Tier 0: nol Gradle, nol JVM penuh. Hanya memanggil tool yang sudah dibundel dan
 * membungkus hasilnya sendiri.
 *
 * Kontrak penting:
 * - satu build = satu alur [BuildEvent]; log mengalir langsung, tidak ditahan sampai akhir.
 * - cancel dari UI ikut mematikan proses OS (lihat `invokeOnCompletion`).
 * - hasil selalu menyertakan [BuildResult], baik sukses maupun gagal.
 */
class Tier0BuildEngine(
    private val toolchain: Toolchain,
    private val runner: ProcessRunner = RealProcessRunner(),
    private val packager: ApkPackager = ApkPackager(),
    private val jarPacker: JarPacker = JarPacker(),
    private val zipAligner: ZipAligner = ZipAligner(),
) : BuildEngine {

    private val planner = Tier0Planner(toolchain)

    override fun build(request: BuildRequest): Flow<BuildEvent> =
        runBuild((request as BuildRequest.Local).config).buffer(Channel.UNLIMITED)

    private fun runBuild(config: Tier0Config): Flow<BuildEvent> = channelFlow {
        // trySend aman dipanggil dari thread mana pun (termasuk callback proses), jadi semua
        // emisi memakai satu lambda ini.
        val emit: (BuildEvent) -> Unit = { trySend(it) }

        val startedAt = System.currentTimeMillis()
        val diagnostics = mutableListOf<Diagnostic>()
        val executed = mutableListOf<BuildStep>()
        val skipped = mutableListOf<BuildStep>()
        val work = Tier0Paths.workDir(config.projectDir)
        val cache = BuildCache(File(work, ".kodenusa-cache"))

        coroutineContext[Job]?.invokeOnCompletion { cause ->
            if (cause is CancellationException) runner.cancel()
        }

        try {
            val missing = toolchain.missing()
            if (missing.isNotEmpty()) {
                emitProblem(
                    emit,
                    Diagnostic(
                        severity = Severity.ERROR,
                        tool = BuildTool.INTERNAL,
                        messageId = "io.failed",
                        rawMessage = "Tool tidak ditemukan: ${missing.joinToString(", ")}",
                    ),
                    diagnostics,
                )
                emit(BuildEvent.Finished(failed(startedAt, diagnostics, executed, skipped)))
                return@channelFlow
            }

            work.mkdirs()
            File(work, "gen").mkdirs()
            File(work, "classes").mkdirs()
            File(work, "dex").mkdirs()
            val steps = planner.plan(config)
            var failure = false

            withContext(Dispatchers.IO) {
                for ((index, step) in steps.withIndex()) {
                    if (step.skipReason != null) {
                        skipped += step.step
                        emit(BuildEvent.StepSkipped(step.step, step.skipReason))
                        continue
                    }

                    val fingerprint = Fingerprint.of(step.cacheInputs, step.cacheSalt)
                    if (config.incremental && cache.isFresh(step.step, fingerprint, step.output)) {
                        skipped += step.step
                        emit(BuildEvent.StepSkipped(step.step, "Tidak ada perubahan sejak build terakhir"))
                        continue
                    }

                    emit(BuildEvent.StepStarted(step.step, index + 1, steps.size))
                    executed += step.step

                    val ok = when (step.step) {
                        BuildStep.PACKAGE -> packageApk(config, work, emit, diagnostics)
                        BuildStep.ALIGN -> alignApk(work, emit, diagnostics)
                        BuildStep.D8 -> packClasses(work, emit, diagnostics) &&
                            runCommands(step, work, emit, diagnostics)
                        else -> runCommands(step, work, emit, diagnostics)
                    }

                    if (!ok) {
                        failure = true
                        break
                    }

                    cache.record(step.step, fingerprint)
                    cache.save()
                }
            }

            cache.save()

            if (failure) {
                emit(BuildEvent.Finished(failed(startedAt, diagnostics, executed, skipped)))
                return@channelFlow
            }

            val apk = Tier0Paths.apkFile(config.projectDir)
            if (!apk.isFile) {
                emitProblem(
                    emit,
                    Diagnostic(
                        severity = Severity.ERROR,
                        tool = BuildTool.INTERNAL,
                        messageId = "io.failed",
                        rawMessage = "APK tidak ditemukan setelah proses selesai: ${apk.path}",
                    ),
                    diagnostics,
                )
                emit(BuildEvent.Finished(failed(startedAt, diagnostics, executed, skipped)))
                return@channelFlow
            }

            emit(BuildEvent.Artifact(apk, apk.length()))
            emit(
                BuildEvent.Finished(
                    BuildResult(
                        success = true,
                        apk = apk,
                        durationMs = System.currentTimeMillis() - startedAt,
                        diagnostics = diagnostics.toList(),
                        stepsExecuted = executed.toList(),
                        stepsSkipped = skipped.toList(),
                        apkSizeBytes = apk.length(),
                    ),
                ),
            )
        } catch (cancelled: CancellationException) {
            runner.cancel()
            throw cancelled
        }
    }

    private fun runCommands(
        step: PlannedStep,
        work: File,
        emit: (BuildEvent) -> Unit,
        diagnostics: MutableList<Diagnostic>,
    ): Boolean {
        for (command in step.commands) {
            if (!runOne(command, work, emit, diagnostics)) return false
        }
        return true
    }

    /** Menjalankan satu alat, meneruskan log, dan mengembalikan false kalau langkah harus berhenti. */
    private fun runOne(
        command: ToolCommand,
        work: File,
        emit: (BuildEvent) -> Unit,
        diagnostics: MutableList<Diagnostic>,
    ): Boolean {
        val parser = DiagnosticParser()
        var lambda = false
        val exitCode = runner.run(command.argv, work, command.env) { stream, line ->
            emit(BuildEvent.Log(stream, command.tool, line))
            if (line.contains("invokedynamic") || line.contains("LambdaMetafactory")) lambda = true
            reportProblem(parser.feed(command.tool, line), emit, diagnostics)
        }
        reportProblem(parser.flush(), emit, diagnostics)
        if (exitCode == 0) return true

        // dx menolak invokedynamic; pesan aslinya panjang dan berbahasa Inggris, jadi diganti
        // diagnostik yang bisa ditindaklanjuti pengguna.
        specificDiagnostic(command.tool, lambda)?.let { emitProblem(emit, it, diagnostics) }

        if (diagnostics.none { it.severity == Severity.ERROR }) {
            emitProblem(
                emit,
                Diagnostic(
                    severity = Severity.ERROR,
                    tool = command.tool,
                    messageId = "io.failed",
                    rawMessage = "Keluar dengan kode $exitCode tanpa pesan yang bisa dibaca.",
                ),
                diagnostics,
            )
        }
        return false
    }

    private fun reportProblem(
        diagnostic: Diagnostic?,
        emit: (BuildEvent) -> Unit,
        diagnostics: MutableList<Diagnostic>,
    ) {
        if (diagnostic != null && diagnostic.severity == Severity.ERROR) {
            emitProblem(emit, diagnostic, diagnostics)
        }
    }

    private fun alignApk(
        work: File,
        emit: (BuildEvent) -> Unit,
        diagnostics: MutableList<Diagnostic>,
    ): Boolean = try {
        zipAligner.align(File(work, "unsigned.apk"), File(work, "aligned.apk"))
        true
    } catch (error: Exception) {
        emitProblem(
            emit,
            Diagnostic(
                severity = Severity.ERROR,
                tool = BuildTool.INTERNAL,
                messageId = "io.failed",
                rawMessage = "Gagal merapikan APK: ${error.message}",
            ),
            diagnostics,
        )
        false
    }

    private fun packClasses(
        work: File,
        emit: (BuildEvent) -> Unit,
        diagnostics: MutableList<Diagnostic>,
    ): Boolean = try {
        jarPacker.pack(File(work, "classes"), File(work, "classes.jar"))
        true
    } catch (error: Exception) {
        emitProblem(
            emit,
            Diagnostic(
                severity = Severity.ERROR,
                tool = BuildTool.INTERNAL,
                messageId = "io.failed",
                rawMessage = "Gagal membungkus hasil kompilasi: ${error.message}",
            ),
            diagnostics,
        )
        false
    }

    private fun packageApk(
        config: Tier0Config,
        work: File,
        emit: (BuildEvent) -> Unit,
        diagnostics: MutableList<Diagnostic>,
    ): Boolean = try {
        emit(BuildEvent.Log(LogStream.SYSTEM, BuildTool.INTERNAL, "Membungkus APK…"))
        packager.assemble(
            baseApk = File(work, "base.apk"),
            dexDir = File(work, "dex"),
            assetDirs = config.assetDirs,
            output = File(work, "unsigned.apk"),
        )
        true
    } catch (error: Exception) {
        emitProblem(
            emit,
            Diagnostic(
                severity = Severity.ERROR,
                tool = BuildTool.INTERNAL,
                messageId = "align.failed",
                rawMessage = "Gagal membungkus APK: ${error.message}",
            ),
            diagnostics,
        )
        false
    }

    private fun emitProblem(
        emit: (BuildEvent) -> Unit,
        diagnostic: Diagnostic,
        diagnostics: MutableList<Diagnostic>,
    ) {
        diagnostics += diagnostic
        emit(BuildEvent.Problem(diagnostic))
    }

    private fun failed(
        startedAt: Long,
        diagnostics: List<Diagnostic>,
        executed: List<BuildStep>,
        skipped: List<BuildStep>,
    ) = BuildResult(
        success = false,
        apk = null,
        durationMs = System.currentTimeMillis() - startedAt,
        diagnostics = diagnostics.toList(),
        stepsExecuted = executed.toList(),
        stepsSkipped = skipped.toList(),
    )
}

/**
 * Menerjemahkan kegagalan alat yang pesannya tidak bisa dicerna pengguna menjadi diagnostik tetap.
 * Dipisah dari pipeline supaya bisa diuji tanpa menjalankan proses apa pun.
 */
internal fun specificDiagnostic(tool: BuildTool, sawLambda: Boolean): Diagnostic? = when {
    sawLambda -> Diagnostic(
        severity = Severity.ERROR,
        tool = tool,
        messageId = "dex.lambda",
        rawMessage = "dx menolak invokedynamic (lambda/method reference).",
    )
    else -> null
}
