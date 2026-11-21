package id.kodenusa.build.hostcli

import id.kodenusa.build.tier0.DX_MAIN
import id.kodenusa.build.tier0.DebugKeystore
import id.kodenusa.build.tier0.HostJavaLauncher
import id.kodenusa.build.tier0.Tier0BuildEngine
import id.kodenusa.build.tier0.Tier0Paths
import id.kodenusa.build.tier0.Toolchain
import id.kodenusa.core.build.BuildEvent
import id.kodenusa.core.build.BuildRequest
import id.kodenusa.core.build.Severity
import id.kodenusa.core.diagnose.ErrorCatalog
import id.kodenusa.core.project.ProjectLoader
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.collect
import java.io.File
import kotlin.system.exitProcess

/**
 * CLI host untuk membuktikan pipeline Tier 0 tanpa perangkat Android.
 * Perintah yang dihasilkan identik dengan yang dijalankan di HP; hanya prefix JVM yang berbeda.
 */
object Tier0Cli {

    @JvmStatic
    fun main(args: Array<String>) {
        val projectDir = args.firstOrNull()?.let(::File)
        if (projectDir == null || !projectDir.isDirectory) {
            System.err.println("Pemakaian: tier0 <folder-projek> [--sdk <path-sdk>] [--tools <folder-tool>]")
            exitProcess(2)
        }

        val sdkRoot = argValue(args, "--sdk")?.let(::File)
            ?: System.getenv("ANDROID_HOME")?.let(::File)
            ?: File(System.getProperty("user.home"), "android-sdk")
        val toolsRoot = argValue(args, "--tools")?.let(::File) ?: File(projectDir, ".tools")

        val buildTools = latestBuildTools(sdkRoot)
        val platform = latestPlatform(sdkRoot)
        if (buildTools == null || platform == null) {
            System.err.println("Android SDK tidak lengkap di ${sdkRoot.path}. Butuh build-tools dan platforms.")
            exitProcess(3)
        }

        val javaBinary = System.getenv("JAVA_HOME")?.let { File(it, "bin/java") }
            ?.takeIf { it.isFile }
            ?: File("/usr/bin/java")

        // dx resmi dari build-tools memakai paket asli; jar hasil relokasi (dibuat scripts/toolchain)
        // dipakai kalau ada, supaya host dan perangkat memakai dexer yang sama persis.
        val dxHost = File(toolsRoot, "android-toolchain/dxr.jar")
        val dxStock = File(buildTools, "lib/dx.jar")
        val dexJar = listOf(dxHost, dxStock).firstOrNull { it.isFile } ?: dxStock
        val dexMain = if (dexJar == dxHost) DX_MAIN else "com.android.dx.command.Main"

        // Keystore dibuat di mesin ini saat pertama dipakai — sama seperti di perangkat, tidak ada
        // kunci yang ikut didistribusikan di dalam repositori atau APK.
        val keystore = runCatching { DebugKeystore.ensure(File(toolsRoot, "debug.keystore")) }
            .getOrElse { error ->
                System.err.println("Gagal membuat keystore debug: ${error.message}")
                exitProcess(4)
            }

        val toolchain = Toolchain(
            aapt2 = File(buildTools, "aapt2"),
            zipalign = File(buildTools, "zipalign"),
            launcher = HostJavaLauncher(javaBinary),
            ecjJar = File(toolsRoot, "ecj.jar"),
            dexJar = dexJar,
            dexMain = dexMain,
            apksignerJar = File(buildTools, "lib/apksigner.jar"),
            androidJar = File(platform, "android.jar"),
            debugKeystore = keystore,
        )

        val loaded = ProjectLoader.load(projectDir)
        loaded.issues.forEach { issue ->
            val text = ErrorCatalog.get(issue.messageId).title
            println("[${issue.severity}] ${issue.field}: $text — ${issue.fallbackMessage}")
        }
        val config = loaded.config
        if (config == null) {
            val errors = loaded.issues.count { it.severity == Severity.ERROR }
            System.err.println("Projek tidak bisa dibangun: $errors masalah.")
            exitProcess(1)
        }

        val engine = Tier0BuildEngine(toolchain)
        var success = false
        runBlocking {
            engine.build(BuildRequest.Local(config)).collect { event ->
                when (event) {
                    is BuildEvent.StepStarted -> println("==> [${event.index}/${event.total}] ${event.step.title}")
                    is BuildEvent.StepSkipped -> println("--- lewati ${event.step.title}: ${event.reason}")
                    is BuildEvent.Log -> println("    ${event.line}")
                    is BuildEvent.Problem -> println(
                        "!!! ${ErrorCatalog.get(event.diagnostic.messageId).title}: ${event.diagnostic.rawMessage}",
                    )
                    is BuildEvent.Progress -> Unit
                    is BuildEvent.Artifact -> println("=== APK: ${event.apk.path} (${event.sizeBytes} byte)")
                    is BuildEvent.Finished -> success = event.result.success
                }
            }
        }

        val apk = Tier0Paths.apkFile(projectDir)
        println(if (success) "HASIL: sukses -> ${apk.path}" else "HASIL: gagal")
        exitProcess(if (success) 0 else 1)
    }

    private fun argValue(args: Array<String>, key: String): String? {
        val index = args.indexOf(key)
        return if (index >= 0 && index + 1 < args.size) args[index + 1] else null
    }

    private fun latestBuildTools(sdk: File): File? = dirs(File(sdk, "build-tools")).maxByOrNull { it.name }

    private fun latestPlatform(sdk: File): File? = dirs(File(sdk, "platforms")).maxByOrNull { it.name }

    private fun dirs(root: File): List<File> = root.listFiles { file -> file.isDirectory }?.toList().orEmpty()
}
