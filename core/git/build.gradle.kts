plugins {
    alias(libs.plugins.detekt)
    `java-library`
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // JGit 4.11 = versi terakhir berbasis Java 7. JGit 5.x memakai java.nio.file.Paths yang baru
    // ada di API 26, sehingga gagal total (NoClassDefFoundError) di Android 7 — sudah dibuktikan
    // lewat GitDeviceTest di emulator API 24.
    api(libs.jgit) {
        // Apache HttpClient bawaan JGit 4.x bertabrakan dengan org.apache.http milik Android
        // (dan hilang total di API 28+). Tanpa itu, JGit memakai HttpURLConnection Android.
        exclude(group = "org.apache.httpcomponents")
    }
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
}

detekt {
    config.setFrom(rootProject.files("detekt.yml"))
    buildUponDefaultConfig = true
}
