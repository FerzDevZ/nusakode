package id.kodenusa.app

import id.kodenusa.core.build.BuildEngine
import id.kodenusa.core.build.BuildEvent
import id.kodenusa.core.build.BuildRequest
import id.kodenusa.core.build.BuildResult
import id.kodenusa.core.build.BuildStep
import id.kodenusa.core.build.BuildTool
import id.kodenusa.core.build.Diagnostic
import id.kodenusa.core.build.Severity
import id.kodenusa.core.build.Tier0Config
import id.kodenusa.core.project.LoadedProject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Sambungan versionCode otomatis (docs/14 2.3) di BuildSession: config yang dikirim ke engine
 * memakai angka counter, dan angkanya hanya dibakar saat build sukses.
 */
class BuildSessionVersiTest {

    @TempDir
    lateinit var folder: File

    private fun loaded(versionCode: Int): LoadedProject {
        val dir = folder.resolve("demo").apply { mkdirs() }
        val config = Tier0Config(
            projectId = "demo",
            projectDir = dir,
            appName = "Demo",
            applicationId = "id.kodenusa.demo",
            versionName = "1.0.0",
            versionCode = versionCode,
            minSdk = 24,
            targetSdk = 34,
            javaVersion = 8,
            javaSources = emptyList(),
            resourceDirs = emptyList(),
            assetDirs = emptyList(),
            manifest = dir.resolve("AndroidManifest.xml"),
        )
        return LoadedProject(project = null, validation = null, issues = emptyList(), config = config)
    }

    /** Engine palsu yang mencatat request yang diterima dan mengeluarkan hasil yang diminta. */
    private class EnginePalsu(
        private val sukses: Boolean,
        private val ditangkap: MutableList<BuildRequest>,
    ) : BuildEngine {
        override fun build(request: BuildRequest): kotlinx.coroutines.flow.Flow<BuildEvent> {
            ditangkap += request
            val config = (request as BuildRequest.Local).config
            val hasil = BuildResult(
                success = sukses,
                apk = File(config.projectDir, "base.apk").apply { writeText("apk") },
                durationMs = 1L,
                diagnostics = if (sukses) emptyList() else listOf(
                    Diagnostic(
                        severity = Severity.ERROR,
                        tool = BuildTool.ECJ,
                        messageId = "E",
                        rawMessage = "gagal",
                    ),
                ),
                stepsExecuted = listOf(BuildStep.SIGN),
                stepsSkipped = emptyList(),
            )
            return flowOf(BuildEvent.Finished(hasil))
        }
    }

    @Test
    fun build_pertama_memakai_versionCode_manifest_dan_mencatatnya_setelah_sukses() = runTest {
        val counter = PenghitungVersi(folder.resolve("version-code"))
        val ditangkap = mutableListOf<BuildRequest>()
        val session = BuildSession(CoroutineScope(UnconfinedTestDispatcher()), penghitungVersi = counter)

        session.start({ Result.success(EnginePalsu(true, ditangkap)) }, loaded(1))
        // Tunggu sampai build tuntas: state berhenti berjalan.
        waitUntil { !session.state.running }

        val config = (ditangkap.single() as BuildRequest.Local).config
        assertEquals(1, config.versionCode)
        // Build sukses sudah mencatat angkanya: build berikutnya harus 2.
        assertEquals(2, counter.berikutnya("demo", 1))
    }

    @Test
    fun build_sukses_kedua_memakai_versionCode_naik_satu() = runTest {
        val counter = PenghitungVersi(folder.resolve("version-code"))
        val ditangkap = mutableListOf<BuildRequest>()
        val session = BuildSession(CoroutineScope(UnconfinedTestDispatcher()), penghitungVersi = counter)

        session.start({ Result.success(EnginePalsu(true, ditangkap)) }, loaded(1))
        waitUntil { !session.state.running }
        session.start({ Result.success(EnginePalsu(true, ditangkap)) }, loaded(1))
        waitUntil { !session.state.running }

        val kode = ditangkap.map { (it as BuildRequest.Local).config.versionCode }
        assertEquals(listOf(1, 2), kode)
    }

    @Test
    fun build_gagal_tidak_membakar_versionCode() = runTest {
        val counter = PenghitungVersi(folder.resolve("version-code"))
        val ditangkap = mutableListOf<BuildRequest>()
        val session = BuildSession(CoroutineScope(UnconfinedTestDispatcher()), penghitungVersi = counter)

        session.start({ Result.success(EnginePalsu(false, ditangkap)) }, loaded(1))
        waitUntil { !session.state.running }
        session.start({ Result.success(EnginePalsu(true, ditangkap)) }, loaded(1))
        waitUntil { !session.state.running }

        // Build gagal tetap pakai 1, build sukses berikutnya juga 1 — angka hanya naik setelah sukses.
        val kode = ditangkap.map { (it as BuildRequest.Local).config.versionCode }
        assertEquals(listOf(1, 1), kode)
        assertEquals(2, counter.berikutnya("demo", 1))
    }

    @Test
    fun tanpa_counter_versionCode_manifest_dipakai_apa_adanya() = runTest {
        val ditangkap = mutableListOf<BuildRequest>()
        val session = BuildSession(CoroutineScope(UnconfinedTestDispatcher()))

        session.start({ Result.success(EnginePalsu(true, ditangkap)) }, loaded(4))
        waitUntil { !session.state.running }

        assertEquals(4, (ditangkap.single() as BuildRequest.Local).config.versionCode)
        assertNull(session.state.fatal)
    }

    private fun waitUntil(kondisi: () -> Boolean) {
        val batas = System.currentTimeMillis() + 5_000
        while (!kondisi()) {
            if (System.currentTimeMillis() > batas) error("kondisi tidak terpenuhi dalam 5 detik")
            Thread.sleep(10)
        }
    }
}
