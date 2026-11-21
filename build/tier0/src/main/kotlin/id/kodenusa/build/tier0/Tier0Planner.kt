package id.kodenusa.build.tier0

import id.kodenusa.core.build.BuildStep
import id.kodenusa.core.build.BuildTool
import id.kodenusa.core.build.Tier0Config
import java.io.File

/**
 * Kelas utama dexer dx yang paketnya sudah direlokasi dari `com.android.dx`/`com.android.dex`.
 * Relokasi wajib karena ART menaruh `com.android.dex` di bootclasspath sehingga kelas bundel tidak
 * pernah terpakai (berujung `NoSuchFieldError: callSiteIds`).
 */
const val DX_MAIN = "id.kodenusa.dxtool.command.Main"

data class ToolCommand(
    val tool: BuildTool,
    val argv: List<String>,
    /** Variabel lingkungan tambahan, misalnya CLASSPATH untuk `app_process` di Android. */
    val env: Map<String, String> = emptyMap(),
)

data class PlannedStep(
    val step: BuildStep,
    val commands: List<ToolCommand>,
    val cacheInputs: List<File> = emptyList(),
    val cacheSalt: String = "",
    val output: File? = null,
    val skipReason: String? = null,
)

/**
 * Menerjemahkan projek menjadi urutan perintah eksternal. Fungsi ini murni terhadap disk
 * (hanya membaca daftar file yang sudah diberikan pemanggil) sehingga bisa diuji tanpa menjalankan tool.
 *
 * Tidak ada `sh -c` dan tidak ada interpolasi string: setiap argumen adalah elemen list terpisah.
 */
class Tier0Planner(private val toolchain: Toolchain) {

    fun plan(config: Tier0Config): List<PlannedStep> {
        val paths = WorkPaths.of(config.projectDir)
        return listOf(
            compileResources(config, paths),
            linkResources(config, paths),
            compileJava(config, paths),
            dexClasses(config, paths),
            packageApk(paths),
            alignApk(paths),
            signApk(config, paths),
        )
    }

    private fun compileResources(config: Tier0Config, paths: WorkPaths): PlannedStep {
        if (config.resourceDirs.isEmpty()) {
            return PlannedStep(
                step = BuildStep.AAPT_COMPILE,
                commands = emptyList(),
                skipReason = "Projek ini belum punya folder res, jadi tidak ada resource yang perlu dikompilasi.",
            )
        }
        // aapt2 hanya menerima satu --dir (yang terakhir menang), jadi bila ada lebih dari satu
        // folder resource — misalnya res projek + folder ikon hasil build (docs/14 2.2) — isinya
        // digabung dulu ke satu folder kerja. Resource di HP kecil, menyalinnya murah.
        val folderKompilasi = if (config.resourceDirs.size == 1) {
            config.resourceDirs.single()
        } else {
            val gabungan = paths.mergedRes.apply { deleteRecursively(); mkdirs() }
            config.resourceDirs.forEach { folder ->
                folder.walkTopDown().filter { it.isFile }.forEach { berkas ->
                    val tujuan = File(gabungan, berkas.relativeTo(folder).path)
                    tujuan.parentFile?.mkdirs()
                    berkas.copyTo(tujuan, overwrite = true)
                }
            }
            gabungan
        }
        return PlannedStep(
            step = BuildStep.AAPT_COMPILE,
            commands = listOf(
                ToolCommand(
                    tool = BuildTool.AAPT2,
                    argv = listOf(
                        toolchain.aapt2.path,
                        "compile",
                        "--dir",
                        folderKompilasi.path,
                        "-o",
                        paths.compiledRes.path,
                    ),
                ),
            ),
            cacheInputs = config.resourceDirs,
            output = paths.compiledRes,
        )
    }

    private fun linkResources(config: Tier0Config, paths: WorkPaths): PlannedStep {
        val argv = buildList {
            add(toolchain.aapt2.path)
            add("link")
            add("-I")
            add(toolchain.androidJar.path)
            add("--manifest")
            add(config.manifest.path)
            add("-o")
            add(paths.baseApk.path)
            add("--java")
            add(paths.gen.path)
            add("--auto-add-overlay")
            add("--min-sdk-version")
            add(config.minSdk.toString())
            add("--target-sdk-version")
            add(config.targetSdk.toString())
            add("--version-code")
            add(config.versionCode.toString())
            add("--version-name")
            add(config.versionName)
            // aapt2 butuh arsip hasil `compile`, bukan folder res mentah.
            if (config.resourceDirs.isNotEmpty()) add(paths.compiledRes.path)
        }
        return PlannedStep(
            step = BuildStep.AAPT_LINK,
            commands = listOf(ToolCommand(BuildTool.AAPT2, argv)),
            cacheInputs = listOf(config.manifest) + config.resourceDirs,
            cacheSalt = "${config.minSdk}/${config.targetSdk}/${config.versionCode}/${config.versionName}",
            output = paths.baseApk,
        )
    }

    private fun compileJava(config: Tier0Config, paths: WorkPaths): PlannedStep {
        val args = buildList {
            add("-source")
            add(TIER0_JAVA_LEVEL)
            add("-target")
            add(TIER0_JAVA_LEVEL)
            add("-proc:none")
            add("-nowarn")
            // Pada level bahasa 8, android.jar dipakai sebagai bootclasspath sehingga java.util dan
            // java.lang berasal dari android.jar saja. Kalau dipakai sebagai -classpath biasa pada
            // level 9+, ECJ menemukan java.util di dua modul dan menolak seluruh projek.
            add("-bootclasspath")
            add(toolchain.androidJar.path)
            if (config.extraJars.isNotEmpty()) {
                add("-classpath")
                add(config.extraJars.joinToString(File.pathSeparator) { it.path })
            }
            add("-d")
            add(paths.classes.path)
            add("-sourcepath")
            add(paths.gen.path)
            generatedRClass(config, paths.gen)?.let { add(it.path) }
            config.javaSources.forEach { add(it.path) }
        }
        val command = toolchain.launcher.command(ECJ_MAIN, listOf(toolchain.ecjJar), args)
        return PlannedStep(
            step = BuildStep.JAVAC,
            commands = listOf(command.copy(tool = BuildTool.ECJ)),
            cacheInputs = config.javaSources + listOfNotNull(paths.gen.takeIf { it.isDirectory }) + config.extraJars,
            cacheSalt = "$TIER0_JAVA_LEVEL/${config.javaVersion}",
            output = paths.classes,
        )
    }

    private fun dexClasses(config: Tier0Config, paths: WorkPaths): PlannedStep {
        // dx, bukan d8: d8 memanggil java.nio.file.Paths yang baru ada di API 26, sedangkan dx
        // hanya butuh java.io sehingga jalan sejak Android 7. Lambda (invokedynamic) belum didukung.
        val args = listOf(
            "--dex",
            "--min-sdk-version",
            config.minSdk.toString(),
            "--output",
            paths.dex.path,
            paths.classesJar.path,
        )
        return PlannedStep(
            step = BuildStep.D8,
            commands = listOf(
                toolchain.launcher.command(toolchain.dexMain, listOf(toolchain.dexJar), args)
                    .copy(tool = BuildTool.D8),
            ),
            cacheInputs = listOf(paths.classes),
            cacheSalt = config.minSdk.toString(),
            output = File(paths.dex, "classes.dex"),
        )
    }

    private fun packageApk(paths: WorkPaths): PlannedStep = PlannedStep(
        step = BuildStep.PACKAGE,
        commands = emptyList(),
        cacheInputs = listOf(paths.baseApk, paths.dex),
        output = paths.unsignedApk,
    )

    /**
     * Alignment dikerjakan `ZipAligner` di dalam proses, jadi langkah ini tidak memanggil binary
     * apa pun. Ini menghapus kebutuhan `zipalign` arm64 di HP.
     */
    private fun alignApk(paths: WorkPaths): PlannedStep = PlannedStep(
        step = BuildStep.ALIGN,
        commands = emptyList(),
        cacheInputs = listOf(paths.unsignedApk),
        output = paths.alignedApk,
    )

    private fun signApk(config: Tier0Config, paths: WorkPaths): PlannedStep {
        val args = listOf(
            "sign",
            "--ks",
            toolchain.debugKeystore.path,
            // Android tidak punya penyedia JKS dan tipe default-nya BKS, sedangkan keystore
            // bawaan dibuat sebagai PKCS12 agar bisa dibaca di semua versi Android.
            "--ks-type",
            "PKCS12",
            "--ks-key-alias",
            toolchain.keystoreAlias,
            "--ks-pass",
            "pass:${toolchain.keystorePassword}",
            "--key-pass",
            "pass:${toolchain.keystorePassword}",
            // v1 (JAR signing) mematikan proses ART di Android 7 saat apksigner dijalankan lewat
            // app_process, sedangkan minSdk KodeNusa 24 sehingga v2 sudah cukup: Android 7.0 ke atas
            // memasang APK bertanda tangan v2 tanpa perlu v1.
            "--v1-signing-enabled",
            "false",
            "--v2-signing-enabled",
            "true",
            // apksigner 0.8 (build-tools 28) belum mengenal opsi v3, dan tanpa nilai minSdk eksplisit
            // ia menebak lewat manifest lalu gagal untuk APK tanpa manifest.
            "--min-sdk-version",
            config.minSdk.toString(),
            "--out",
            paths.signedApk.path,
            paths.alignedApk.path,
        )
        return PlannedStep(
            step = BuildStep.SIGN,
            commands = listOf(
                toolchain.launcher.command(APKSIGNER_MAIN, listOf(toolchain.apksignerJar), args)
                    .copy(tool = BuildTool.APKSIGNER),
            ),
            // Keystore ikut masuk fingerprint: ganti kunci dengan input yang sama tidak boleh
            // memakai ulang APK bertanda tangan lama (docs/14 2.1, ADR-041).
            cacheInputs = listOf(paths.alignedApk, toolchain.debugKeystore),
            cacheSalt = "v1/${config.versionCode}",
            output = paths.signedApk,
        )
    }

    private fun generatedRClass(config: Tier0Config, gen: File): File? {
        // aapt2 menulis R.java ke dalam folder gen/<package>/R.java. Path ini dihitung, bukan
        // dicek keberadaannya, karena perencanaan terjadi sebelum langkah link dijalankan.
        val packagePath = config.applicationId.replace('.', File.separatorChar)
        return File(gen, "$packagePath/R.java")
    }

    private data class WorkPaths(
        val gen: File,
        val classes: File,
        val classesJar: File,
        val dex: File,
        val compiledRes: File,
        val mergedRes: File,
        val baseApk: File,
        val unsignedApk: File,
        val alignedApk: File,
        val signedApk: File,
    ) {
        companion object {
            fun of(projectDir: File): WorkPaths {
                val work = Tier0Paths.workDir(projectDir)
                return WorkPaths(
                    gen = File(work, "gen"),
                    classes = File(work, "classes"),
                    classesJar = File(work, "classes.jar"),
                    dex = File(work, "dex"),
                    compiledRes = File(work, "res-compiled.zip"),
                    mergedRes = File(work, "res-merged"),
                    baseApk = File(work, "base.apk"),
                    unsignedApk = File(work, "unsigned.apk"),
                    alignedApk = File(work, "aligned.apk"),
                    signedApk = Tier0Paths.apkFile(projectDir),
                )
            }
        }
    }

    private companion object {
        /**
         * Level bahasa yang dipakai Tier 0. Dipatok ke 8 karena hanya pada level < 9 ECJ menerima
         * `-bootclasspath android.jar`, dan hanya dengan cara itu java.util/java.lang berasal dari
         * satu sumber. Java 9+ (var, switch expression) menjadi tugas Tier 1/2.
         */
        const val TIER0_JAVA_LEVEL = "8"

        const val ECJ_MAIN = "org.eclipse.jdt.internal.compiler.batch.Main"
        const val APKSIGNER_MAIN = "com.android.apksigner.ApkSignerTool"
    }
}

object Tier0Paths {
    fun workDir(projectDir: File): File = File(projectDir, "build/tier0")
    fun apkFile(projectDir: File): File = File(workDir(projectDir), "app-debug.apk")
}
