package id.kodenusa.build.tier0

import java.io.File

/**
 * Lokasi tool yang dipakai pipeline Tier 0. Di Android, isinya hasil ekstraksi `assets/toolchain`;
 * di host, isinya build-tools dari Android SDK. Pipeline tidak peduli bedanya.
 */
data class Toolchain(
    val aapt2: File,
    /** Opsional: hanya dipakai untuk verifikasi silang di host, bukan oleh pipeline. */
    val zipalign: File? = null,
    /** Bagaimana jar tool dijalankan: JVM host atau ART lewat `app_process`. */
    val launcher: JavaLauncher,
    val ecjJar: File,
    val dexJar: File,
    /**
     * Kelas utama dexer di dalam [dexJar]. Build-tools resmi memakai `com.android.dx.command.Main`,
     * sedangkan aset KodeNusa memakai dx yang paketnya sudah direlokasi (lihat ADR-017).
     */
    val dexMain: String = DX_MAIN,
    val apksignerJar: File,
    val androidJar: File,
    val debugKeystore: File,
    val keystoreAlias: String = "androiddebugkey",
    val keystorePassword: String = "android",
) {
    /** Nama field yang hilang — dipakai UI untuk memberi pesan spesifik, bukan "build gagal". */
    fun missing(): List<String> = buildList {
        if (!aapt2.isFile) add("aapt2")
        if (!ecjJar.isFile) add("ecj.jar")
        if (!dexJar.isFile) add("dx.jar")
        if (!apksignerJar.isFile) add("apksigner.jar")
        if (!androidJar.isFile) add("android.jar")
        if (!debugKeystore.isFile) add("debug.keystore")
    }

    companion object {
        /** Susunan folder saat tool dibundel di dalam APK KodeNusa. */
        fun fromRoot(root: File, launcher: JavaLauncher): Toolchain = Toolchain(
            aapt2 = File(root, "aapt2"),
            zipalign = File(root, "zipalign").takeIf { it.isFile },
            launcher = launcher,
            ecjJar = File(root, "ecj.jar"),
            dexJar = File(root, "dx.jar"),
            apksignerJar = File(root, "apksigner.jar"),
            androidJar = File(root, "android.jar"),
            debugKeystore = File(root, "debug.keystore"),
        )
    }
}
