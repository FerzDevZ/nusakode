package id.kodenusa.app

import id.kodenusa.core.project.ProjectJson
import java.io.File

/**
 * "Template saya": template buatan pengguna lewat AI (docs/14 4.3), tersimpan di penyimpanan
 * privat aplikasi (`filesDir/template-saya/<id>/`) — bukan di assets bawaan yang tidak bisa
 * ditulis. Setiap template adalah folder projek mini: `kodenusa.json` + berkas sumber dengan
 * penanda `__APP_NAME__`/`__APP_ID__` yang diganti saat projek dibuat, sama seperti aset.
 *
 * Aturan yang tidak boleh dilanggar:
 * - Template setengah jadi DITOLAK saat disimpan: wajib punya `kodenusa.json` yang sah,
 *   `AndroidManifest.xml`, dan satu berkas `MainActivity.java`. Lebih baik gagal jelas
 *   sekarang daripada projek rusak saat pengguna menekan "Buat projek".
 * - Jalur berkas dari AI diperiksa: `..` dan jalur absolut ditolak (masukan tidak dipercaya).
 */
class TemplateSaya(private val akar: File) {

    /** Daftar template milik pengguna, urut nama. Gagal dibaca = daftar kosong, bukan crash. */
    fun list(): List<TemplateInfo> = runCatching {
        akar.listFiles().orEmpty()
            .filter { it.isDirectory && File(it, "kodenusa.json").isFile }
            .mapNotNull { folder ->
                runCatching {
                    val manifest = ProjectJson.decode(File(folder, "kodenusa.json").readText())
                    TemplateInfo(
                        id = PREFIKS + folder.name,
                        title = manifest.name,
                        tagline = "Template buatanmu (AI)",
                        audience = "template saya",
                        appIdSuffix = "",
                        jenis = manifest.jenis.ifBlank { "aplikasi" },
                        files = folder.walkTopDown().filter { it.isFile }
                            .map { it.relativeTo(folder).invariantSeparatorsPath }
                            .sorted()
                            .toList(),
                    )
                }.getOrNull()
            }.sortedBy { it.title.lowercase() }
    }.getOrDefault(emptyList())

    /**
     * Simpan hasil rancangan AI sebagai template baru. [berkas] memetakan jalur relatif → isi.
     * Kembali pesan galat berbahasa Indonesia, atau null bila berhasil.
     */
    fun simpan(id: String, berkas: Map<String, String>): String? {
        val slug = id.trim().lowercase().replace(Regex("[^a-z0-9-]+"), "-").trim('-')
        if (slug.isEmpty()) return "Nama template tidak sah."
        val manifestIsi = berkas["kodenusa.json"]
            ?: return "Template wajib memuat kodenusa.json."
        val manifest = runCatching { ProjectJson.decode(manifestIsi) }.getOrNull()
            ?: return "kodenusa.json tidak bisa dibaca sebagai projek KodeNusa."
        if (berkas.keys.none { it.endsWith("AndroidManifest.xml") }) {
            return "Template wajib memuat AndroidManifest.xml."
        }
        if (berkas.keys.none { it.endsWith("MainActivity.java") }) {
            return "Template wajib memuat MainActivity.java."
        }
        for (jalur in berkas.keys) {
            if (jalur.startsWith("/") || jalur.contains("..")) {
                return "Jalur tidak aman dari jawaban AI: $jalur"
            }
        }

        val folder = File(akar, slug)
        if (folder.exists()) return "Template \"$slug\" sudah ada. Hapus dulu kalau ingin mengganti."
        folder.mkdirs()
        for ((jalur, isi) in berkas) {
            val tujuan = File(folder, jalur)
            tujuan.parentFile?.mkdirs()
            tujuan.writeText(isi)
        }
        // Nama tampilan diambil dari kodenusa.json agar konsisten dengan kartu di wizard.
        if (manifest.name.isBlank()) {
            folder.deleteRecursively()
            return "kodenusa.json tidak memuat nama aplikasi."
        }
        return null
    }

    /** Salin template milik pengguna ke folder projek baru, mengganti penanda seperti aset. */
    fun create(
        templateId: String,
        target: File,
        appName: String,
        appId: String,
        warnaIkon: String? = null,
    ): Result<File> = runCatching {
        val slug = templateId.removePrefix(PREFIKS)
        val folder = File(akar, slug)
        require(folder.isDirectory && File(folder, "kodenusa.json").isFile) {
            "Template '$slug' tidak ada di template saya"
        }
        require(!target.exists()) { "Folder ${target.name} sudah dipakai" }
        target.mkdirs()
        folder.walkTopDown().filter { it.isFile }.forEach { sumber ->
            val relatif = sumber.relativeTo(folder).invariantSeparatorsPath
            val out = File(target, relatif)
            out.parentFile?.mkdirs()
            out.writeText(
                sumber.readText()
                    .replace("__APP_NAME__", appName)
                    .replace("__APP_ID__", appId),
            )
        }
        if (warnaIkon != null) {
            val manifest = File(target, "kodenusa.json")
            val proyek = ProjectJson.decode(manifest.readText())
            manifest.writeText(ProjectJson.encode(proyek.copy(warnaIkon = warnaIkon)))
        }
        target
    }

    fun hapus(id: String): Boolean = File(akar, id.removePrefix(PREFIKS)).deleteRecursively()

    companion object {
        /** Awalan id supaya template pengguna tidak bertabrakan dengan id aset. */
        const val PREFIKS = "saya:"
    }
}
