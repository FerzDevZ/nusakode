plugins {
    alias(libs.plugins.detekt)
    application
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("id.kodenusa.build.hostcli.Tier0Cli")
    applicationName = "kodenusa-tier0"
}

dependencies {
    implementation(project(":build:tier0"))
    implementation(project(":core:project"))
    implementation(project(":core:diagnose"))
    implementation(libs.kotlinx.coroutines.core)
}

detekt {
    config.setFrom(rootProject.files("detekt.yml"))
    buildUponDefaultConfig = true
}
