package id.kodenusa.app

import id.kodenusa.core.ai.GerbangBuild
import id.kodenusa.core.ai.HasilBuild
import id.kodenusa.core.build.BuildEngine
import id.kodenusa.core.build.BuildEvent
import id.kodenusa.core.build.BuildRequest
import id.kodenusa.core.build.Severity
import id.kodenusa.core.diagnose.ErrorCatalog
import id.kodenusa.core.project.ProjectLoader
import java.io.File

/**
 * Gerbang build untuk agen AI: menjalankan pipeline Tier 0 yang sama dengan tombol Build APK.
 *
 * Galat yang dikembalikan sudah diterjemahkan lewat [ErrorCatalog] — model menerima kalimat yang
 * bisa ditindaklanjuti ("R.java tidak ditemukan, tambahkan …"), bukan tumpukan teks alat.
 */
class AiBuildGate(
    private val akar: File,
    private val engine: BuildEngine,
) : GerbangBuild {

    override suspend fun build(): HasilBuild {
        val dimuat = ProjectLoader.load(akar)
        val config = dimuat.config
            ?: return HasilBuild(false, "Projek belum bisa dibangun: " + dimuat.issues.joinToString("; ") { it.fallbackMessage })

        val masalah = ArrayList<String>()
        var sukses = false
        engine.build(BuildRequest.Local(config)).collect { event ->
            when (event) {
                is BuildEvent.Problem -> if (event.diagnostic.severity == Severity.ERROR) {
                    val info = ErrorCatalog.get(event.diagnostic.messageId)
                    val lokasi = event.diagnostic.file?.substringAfterLast('/') ?: ""
                    masalah += listOfNotNull(
                        info.title,
                        lokasi.ifBlank { null },
                        event.diagnostic.line?.let { "baris $it" },
                        info.fix,
                    ).joinToString(" · ")
                }

                is BuildEvent.Finished -> sukses = event.result.success

                else -> Unit
            }
        }
        val ringkas = masalah.distinct().take(5).joinToString("\n")
        return if (sukses) {
            HasilBuild(true)
        } else {
            HasilBuild(false, ringkas.ifBlank { "Build gagal tanpa pesan yang bisa dibaca. Periksa berkas projek." })
        }
    }
}
