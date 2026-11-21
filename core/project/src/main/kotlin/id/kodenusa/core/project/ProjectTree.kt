package id.kodenusa.core.project

import java.io.File

/**
 * Satu baris di panel berkas. [depth] dipakai UI untuk indentasi, [relativePath] dipakai sebagai
 * kunci stabil saat daftar digulir ulang.
 */
data class ProjectNode(
    val file: File,
    val relativePath: String,
    val name: String,
    val depth: Int,
    val directory: Boolean,
)

/**
 * Daftar berkas projek yang bisa dibuka editor.
 *
 * Urutan: folder dulu, lalu berkas, masing-masing menurut abjad — sama seperti panel berkas Acode
 * dan VS Code. Hanya ekstensi teks yang muncul; `.class`, `build/`, dan `dex/` disembunyikan supaya
 * panel tetap ringkas di layar 5 inci.
 */
object ProjectTree {

    val TEXT_EXTENSIONS = setOf(
        "java", "kt", "kts", "xml", "json", "md", "txt", "gradle", "properties", "pro", "gitignore",
        // Berkas web dan skrip wajib muncul: template "Web jadi APK" isinya HTML/CSS/JS, dan tanpa
        // ini pengguna tidak bisa membuka atau mengedit halaman yang baru dibuatnya.
        "html", "htm", "css", "js", "mjs", "ts", "tsx", "jsx", "svg", "yml", "yaml", "toml", "sh",
        "py", "sql", "csv", "ini", "cfg", "env", "gitattributes", "editorconfig",
    )

    /** Folder yang tidak pernah ditampilkan karena isinya hasil build. */
    val IGNORED_DIRS = setOf("build", ".gradle", ".git", "dex", "gen", "classes", ".tools", ".riwayat")

    private const val MAX_ENTRIES = 500

    fun list(root: File, maxEntries: Int = MAX_ENTRIES): List<ProjectNode> {
        if (!root.isDirectory) return emptyList()
        val hasil = ArrayList<ProjectNode>()
        walk(root, root, 0, hasil, maxEntries)
        return hasil
    }

    /** Menyembunyikan isi folder yang sedang ditutup, tanpa membaca ulang disk. */
    fun visible(nodes: List<ProjectNode>, collapsed: Set<String>): List<ProjectNode> = buildList {
        var skipDeeperThan: Int? = null
        for (node in nodes) {
            val batas = skipDeeperThan
            if (batas != null) {
                if (node.depth > batas) continue
                skipDeeperThan = null
            }
            add(node)
            if (node.directory && node.relativePath in collapsed) skipDeeperThan = node.depth
        }
    }

    private fun walk(root: File, dir: File, depth: Int, out: MutableList<ProjectNode>, limit: Int) {
        if (out.size >= limit) return
        val children = dir.listFiles().orEmpty()
        val folders = children.filter { it.isDirectory && it.name !in IGNORED_DIRS }.sortedBy { it.name.lowercase() }
        val files = children
            .filter { it.isFile && it.extension.lowercase() in TEXT_EXTENSIONS }
            .sortedBy { it.name.lowercase() }

        for (folder in folders) {
            if (out.size >= limit) return
            out += node(root, folder, depth, directory = true)
            walk(root, folder, depth + 1, out, limit)
        }
        for (file in files) {
            if (out.size >= limit) return
            out += node(root, file, depth, directory = false)
        }
    }

    private fun node(root: File, file: File, depth: Int, directory: Boolean): ProjectNode = ProjectNode(
        file = file,
        relativePath = file.relativeTo(root).invariantSeparatorsPath,
        name = file.name,
        depth = depth,
        directory = directory,
    )
}
