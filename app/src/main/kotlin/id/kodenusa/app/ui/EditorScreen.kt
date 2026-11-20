package id.kodenusa.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import id.kodenusa.app.AppContainer
import id.kodenusa.app.EditorSession
import id.kodenusa.core.project.ProjectFiles
import id.kodenusa.core.project.ProjectNode
import id.kodenusa.core.project.ProjectTree
import id.kodenusa.editor.CodeEditorView
import id.kodenusa.editor.Language
import id.kodenusa.ui.GhostButton
import id.kodenusa.ui.KodenusaPalette
import id.kodenusa.ui.Pill
import id.kodenusa.ui.PrimaryButton
import kotlinx.coroutines.launch
import java.io.File

/**
 * Ruang kerja kode: panel berkas, tab, editor, dan bar tombol simbol.
 *
 * Susunannya sengaja dibuat seperti editor desktop (Acode/VS Code) tetapi dengan dua hal yang tidak
 * dimiliki editor lain: tombol Build APK selalu terlihat, dan setiap pesan galat berbahasa Indonesia.
 */
@Composable
fun EditorScreen(
    container: AppContainer,
    projectDir: File,
    onBack: () -> Unit,
    onBuild: () -> Unit,
) {
    val loaded = remember(projectDir) { container.projectStore.load(projectDir) }
    // Daftar berkas adalah state, bukan nilai sekali baca: setelah menambah/menghapus berkas,
    // panel harus langsung berubah tanpa keluar-masuk projek.
    var nodes by remember(projectDir) { mutableStateOf(ProjectTree.list(projectDir)) }
    val session = remember(projectDir) { EditorSession(projectDir, initialFile(projectDir, nodes)) }
    val state = rememberEditorState()
    var aksi by remember { mutableStateOf<FileAction?>(null) }
    var aiTerbuka by remember { mutableStateOf(false) }
    fun segarkan() {
        nodes = ProjectTree.list(projectDir)
    }

    val scope = rememberCoroutineScope()
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val canBuild = loaded.config != null

    ModalNavigationDrawer(
        drawerState = drawer,
        gesturesEnabled = true,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = Color(KodenusaPalette.SURFACE),
                modifier = Modifier.width(272.dp),
            ) {
                FilePanel(
                    nodes = nodes,
                    collapsed = state.collapsed,
                    activePath = session.active?.path,
                    onToggle = { node -> state.toggle(node.relativePath) },
                    onOpen = { file ->
                        session.open(file)
                        state.load(file)
                        scope.launch { drawer.close() }
                    },
                    onBuild = {
                        session.saveAll()
                        onBuild()
                    },
                    onBack = onBack,
                    onNewFile = { aksi = FileAction.NewFile(folderAktif(session.active, projectDir)) },
                    onNewFolder = { aksi = FileAction.NewFolder(folderAktif(session.active, projectDir)) },
                    onRename = { node -> aksi = FileAction.Rename(node) },
                    onDelete = { node -> aksi = FileAction.Delete(node) },
                    buildable = canBuild,
                )
            }
        },
    ) {
        Scaffold(containerColor = Color(KodenusaPalette.BACKGROUND)) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                EditorTopBar(
                    title = loaded.project?.name ?: projectDir.name,
                    dirty = session.hasUnsaved,
                    onMenu = { scope.launch { drawer.open() } },
                    onSave = { session.saveAll(); state.toast = "Tersimpan" },
                    onFind = { state.findOpen = !state.findOpen },
                    onBuild = {
                        session.saveAll()
                        onBuild()
                    },
                    onAi = { aiTerbuka = true },
                    buildable = canBuild,
                )

                TabStrip(session, onSelect = { file -> session.open(file); state.load(file) })

                if (state.findOpen) {
                    FindBar(
                        query = state.query,
                        onQuery = { value ->
                            state.query = value
                            state.matches = state.editor?.countMatches(value) ?: 0
                            state.matchIndex = 0
                        },
                        matches = state.matches,
                        index = state.matchIndex,
                        onNext = {
                            if (state.matches > 0) {
                                state.matchIndex = (state.matchIndex + 1) % state.matches
                                state.editor?.selectMatch(state.query, state.matchIndex)
                            }
                        },
                        onPrev = {
                            if (state.matches > 0) {
                                state.matchIndex = (state.matchIndex - 1 + state.matches) % state.matches
                                state.editor?.selectMatch(state.query, state.matchIndex)
                            }
                        },
                        wrap = state.wrap,
                        onWrap = {
                            state.wrap = !state.wrap
                            state.editor?.setWordWrap(state.wrap)
                        },
                    )
                }

                Box(Modifier.weight(1f).fillMaxWidth().background(Color(KodenusaPalette.SURFACE))) {
                    val file = session.active
                    if (file == null) {
                        EmptyEditor()
                    } else {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { ctx ->
                                CodeEditorView(ctx).also { view ->
                                    state.editor = view
                                    view.setWordWrap(state.wrap)
                                    view.onTextChanged = { text -> session.update(file, text) }
                                    view.onCursorMoved = { line, column ->
                                        state.line = line
                                        state.column = column
                                    }
                                }
                            },
                            update = { view ->
                                if (state.loadedPath != file.path) {
                                    view.setContent(session.textOf(file), Language.of(file.name))
                                    state.loadedPath = file.path
                                }
                            },
                        )
                    }
                }

                KeyBar(
                    position = "${state.line}:${state.column}",
                    onInsert = { snippet -> state.editor?.insertSnippet(snippet) },
                    onLeft = { state.editor?.moveCursor(-1) },
                    onRight = { state.editor?.moveCursor(1) },
                    onUndo = { state.editor?.undo() },
                    onRedo = { state.editor?.redo() },
                    onSave = { session.saveAll(); state.toast = "Tersimpan" },
                    onBuild = { session.saveAll(); onBuild() },
                )
            }
        }
    }

    Autosave(session)
    if (aiTerbuka) {
        val aktif = session.active
        AiScreen(
            store = container.aiStore,
            konteks = AiKonteks(
                jalur = aktif?.let { runCatching { it.relativeTo(projectDir).invariantSeparatorsPath }.getOrNull() },
                isi = aktif?.let { session.textOf(it) },
                judul = if (aktif == null) "Tanya AI" else "AI · ${aktif.name}",
            ),
            onDismiss = { aiTerbuka = false },
            onSisip = { kode ->
                ketikaSisip(aktif, session, projectDir, kode)?.let { state.toast = it }
                aiTerbuka = false
            },
        )
    }
    FileActionHost(
        aksi = aksi,
        projectDir = projectDir,
        onDismiss = { aksi = null },
        onSelesai = { segarkan() },
        onBuka = { file -> session.open(file); state.load(file) },
        onTutupTab = { file -> session.close(file) },
        session = session,
    )
}

/** Menaruh kode jawaban AI ke berkas aktif; mengembalikan pesan untuk toast. */
private fun ketikaSisip(aktif: File?, session: EditorSession, projectDir: File, kode: String): String? {
    if (aktif == null) return "Buka dulu satu berkas, lalu masukkan kodenya."
    if (!aktif.name.endsWith(".java") && !aktif.name.endsWith(".xml") &&
        !aktif.name.endsWith(".html") && !aktif.name.endsWith(".css") &&
        !aktif.name.endsWith(".js") && !aktif.name.endsWith(".md")
    ) {
        return "Kode tidak dimasukkan: ${aktif.name} bukan berkas teks kode."
    }
    session.update(aktif, kode)
    session.save(aktif)
    return "Kode AI dimasukkan ke ${aktif.name}. Cek lalu Build APK."
}

/** Folder tujuan untuk berkas/folder baru: folder dari berkas aktif, atau akar projek. */
private fun folderAktif(active: File?, projectDir: File): String {
    val parent = active?.parentFile ?: return ""
    return runCatching {
        val rel = parent.relativeTo(projectDir).invariantSeparatorsPath
        if (rel.startsWith("..")) "" else rel
    }.getOrDefault("")
}

/** Isi awal untuk berkas baru: berkas Java langsung mendapat paket dan kelas yang benar. */
private fun starterUntuk(projectDir: File, parent: String, nama: String): String {
    if (!nama.endsWith(".java", ignoreCase = true)) return ""
    val kelas = nama.removeSuffix(".java").removeSuffix(".JAVA")
    if (!kelas.matches(Regex("[A-Za-z_][A-Za-z0-9_]*"))) return ""
    val paket = parent.removePrefix("src/main/java").trim('/').replace('/', '.')
    return id.kodenusa.core.project.ProjectFiles.javaStarter(paket, kelas)
}

@Composable
private fun FileActionHost(
    aksi: FileAction?,
    projectDir: File,
    session: EditorSession,
    onDismiss: () -> Unit,
    onSelesai: () -> Unit,
    onBuka: (File) -> Unit,
    onTutupTab: (File) -> Unit,
) {
    val action = aksi ?: return
    FileActionDialog(
        action = action,
        onDismiss = onDismiss,
        onNewFile = { parent, nama ->
            val isi = starterUntuk(projectDir, parent, nama)
            when (val hasil = ProjectFiles.createFile(projectDir, parent, nama, isi)) {
                is ProjectFiles.Hasil.Gagal -> hasil.pesan
                is ProjectFiles.Hasil.Sukses -> {
                    onSelesai()
                    onBuka(hasil.file)
                    null
                }
            }
        },
        onNewFolder = { parent, nama ->
            when (val hasil = ProjectFiles.createFolder(projectDir, parent, nama)) {
                is ProjectFiles.Hasil.Gagal -> hasil.pesan
                is ProjectFiles.Hasil.Sukses -> {
                    onSelesai()
                    null
                }
            }
        },
        onRename = { node, nama ->
            val terbuka = session.tabs.firstOrNull { it.path == node.file.path }
            when (val hasil = ProjectFiles.rename(projectDir, node.relativePath, nama)) {
                is ProjectFiles.Hasil.Gagal -> hasil.pesan
                is ProjectFiles.Hasil.Sukses -> {
                    // Tab lama harus ikut pindah, kalau tidak isi yang belum disimpan tertulis ke
                    // path yang sudah tidak ada.
                    if (terbuka != null) {
                        session.save(terbuka)
                        onTutupTab(terbuka)
                        if (!hasil.file.isDirectory) onBuka(hasil.file)
                    }
                    onSelesai()
                    null
                }
            }
        },
        onDelete = { node ->
            when (val hasil = ProjectFiles.delete(projectDir, node.relativePath)) {
                is ProjectFiles.Hasil.Gagal -> hasil.pesan
                is ProjectFiles.Hasil.Sukses -> {
                    session.tabs.filter { it.path.startsWith(node.file.path) }.forEach(onTutupTab)
                    onSelesai()
                    null
                }
            }
        },
    )
}

@Composable
private fun EditorTopBar(
    title: String,
    dirty: Boolean,
    onMenu: () -> Unit,
    onSave: () -> Unit,
    onFind: () -> Unit,
    onBuild: () -> Unit,
    onAi: () -> Unit,
    buildable: Boolean,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            GhostButton("☰", onMenu)
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color(KodenusaPalette.TEXT),
                    maxLines = 1,
                )
                if (dirty) {
                    Text(
                        "belum disimpan · tersimpan otomatis",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(KodenusaPalette.WARN),
                    )
                }
            }
            PrimaryButton("Build APK", onBuild, enabled = buildable)
        }
        // Baris kedua: aksi yang sering dipakai tapi tidak boleh berebut tempat dengan tombol build
        // di layar 360dp (HP kelas bawah yang jadi sasaran aplikasi ini).
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            GhostButton("AI", onAi, Modifier.testTag("buka-ai"))
            GhostButton("Cari", onFind)
            GhostButton("Simpan", onSave)
        }
    }
}

@Composable
private fun TabStrip(session: EditorSession, onSelect: (File) -> Unit) {
    if (session.tabs.isEmpty()) return
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color(KodenusaPalette.SURFACE_RAISED))
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        session.tabs.forEach { file ->
            val active = file.path == session.active?.path
            Row(
                Modifier
                    .background(
                        Color(if (active) KodenusaPalette.BACKGROUND else KodenusaPalette.SURFACE),
                        RoundedCornerShape(6.dp),
                    )
                    .border(
                        1.dp,
                        Color(if (active) KodenusaPalette.ACCENT else KodenusaPalette.BORDER),
                        RoundedCornerShape(6.dp),
                    )
                    .clickable { onSelect(file) }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    file.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(if (active) KodenusaPalette.TEXT else KodenusaPalette.TEXT_DIM),
                )
                if (session.isDirty(file)) {
                    Spacer(Modifier.width(4.dp))
                    Text("●", style = MaterialTheme.typography.labelSmall, color = Color(KodenusaPalette.WARN))
                }
            }
        }
    }
}

@Composable
private fun FilePanel(
    nodes: List<ProjectNode>,
    collapsed: Set<String>,
    activePath: String?,
    onToggle: (ProjectNode) -> Unit,
    onOpen: (File) -> Unit,
    onBuild: () -> Unit,
    onBack: () -> Unit,
    onNewFile: () -> Unit,
    onNewFolder: () -> Unit,
    onRename: (ProjectNode) -> Unit,
    onDelete: (ProjectNode) -> Unit,
    buildable: Boolean,
) {
    val visible = ProjectTree.visible(nodes, collapsed)
    Column(Modifier.fillMaxHeight()) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            GhostButton("‹", onBack)
            Text(
                "Berkas projek",
                style = MaterialTheme.typography.titleSmall,
                color = Color(KodenusaPalette.TEXT),
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
        }
        // Tombol tambah selalu terlihat: inilah yang dulu tidak ada sehingga projek baru terasa
        // "kosong dan tidak bisa diapa-apakan".
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            GhostButton("+ Berkas", onNewFile, Modifier.weight(1f).testTag("berkas-baru"))
            GhostButton("+ Folder", onNewFolder, Modifier.weight(1f).testTag("folder-baru"))
        }
        LazyColumn(Modifier.weight(1f).padding(top = 6.dp).testTag("daftar-berkas")) {
            items(visible, key = { it.relativePath }) { node ->
                FileRow(
                    node = node,
                    active = activePath == node.relativePath,
                    onClick = { if (node.directory) onToggle(node) else onOpen(node.file) },
                    onRename = { onRename(node) },
                    onDelete = { onDelete(node) },
                )
            }
        }
        Box(Modifier.fillMaxWidth().padding(12.dp)) {
            PrimaryButton("Build APK", onBuild, enabled = buildable)
        }
    }
}

@Composable
private fun FileRow(
    node: ProjectNode,
    active: Boolean,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (active) Color(KodenusaPalette.SURFACE_RAISED) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(start = (8 + node.depth * 12).dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (node.directory) ("▸ " + node.name) else node.name,
            style = MaterialTheme.typography.bodySmall,
            color = Color(if (node.directory) KodenusaPalette.TEXT else KodenusaPalette.TEXT_DIM),
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        Box {
            Text(
                "⋯",
                style = MaterialTheme.typography.bodySmall,
                color = Color(KodenusaPalette.TEXT_DIM),
                modifier = Modifier
                    .clickable { menu = true }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
            DropdownMenu(
                expanded = menu,
                onDismissRequest = { menu = false },
                containerColor = Color(KodenusaPalette.SURFACE_RAISED),
            ) {
                DropdownMenuItem(
                    text = { Text("Ganti nama", color = Color(KodenusaPalette.TEXT)) },
                    onClick = { menu = false; onRename() },
                )
                DropdownMenuItem(
                    text = { Text("Hapus", color = Color(KodenusaPalette.ERROR)) },
                    onClick = { menu = false; onDelete() },
                )
            }
        }
    }
}

@Composable
private fun FindBar(
    query: String,
    onQuery: (String) -> Unit,
    matches: Int,
    index: Int,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    wrap: Boolean,
    onWrap: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        TextField(
            value = query,
            onValueChange = onQuery,
            singleLine = true,
            placeholder = { Text("cari di berkas", style = MaterialTheme.typography.bodySmall) },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color(KodenusaPalette.SURFACE_RAISED),
                unfocusedContainerColor = Color(KodenusaPalette.SURFACE_RAISED),
                focusedTextColor = Color(KodenusaPalette.TEXT),
                unfocusedTextColor = Color(KodenusaPalette.TEXT),
            ),
            modifier = Modifier.weight(1f),
        )
        Text(
            if (matches == 0) "0" else "${index + 1}/$matches",
            style = MaterialTheme.typography.labelSmall,
            color = Color(KodenusaPalette.TEXT_DIM),
        )
        GhostButton("↑", onPrev)
        GhostButton("↓", onNext)
        GhostButton(if (wrap) "wrap on" else "wrap off", onWrap)
    }
}

@Composable
private fun KeyBar(
    position: String,
    onInsert: (String) -> Unit,
    onLeft: () -> Unit,
    onRight: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onSave: () -> Unit,
    onBuild: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color(KodenusaPalette.SURFACE_RAISED))
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Key("Tab") { onInsert("    ") }
        Key("{") { onInsert("{") }
        Key("}") { onInsert("}") }
        Key("(") { onInsert("(") }
        Key(")") { onInsert(")") }
        Key("\"") { onInsert("\"") }
        Key(";") { onInsert(";") }
        Key("=") { onInsert(" = ") }
        Key("+") { onInsert(" + ") }
        Key("<") { onInsert("<") }
        Key(">") { onInsert(">") }
        Key("/*") { onInsert("/*  */") }
        Key("←") { onLeft() }
        Key("→") { onRight() }
        Key("Undo") { onUndo() }
        Key("Redo") { onRedo() }
        Key("Simpan") { onSave() }
        Key("Build") { onBuild() }
        Text(
            position,
            style = MaterialTheme.typography.labelSmall,
            color = Color(KodenusaPalette.TEXT_DIM),
            modifier = Modifier.padding(horizontal = 8.dp),
        )
    }
}

@Composable
private fun Key(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .background(Color(KodenusaPalette.SURFACE), RoundedCornerShape(5.dp))
            .border(1.dp, Color(KodenusaPalette.BORDER), RoundedCornerShape(5.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Color(KodenusaPalette.TEXT))
    }
}

@Composable
private fun EmptyEditor() {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Belum ada berkas dibuka", style = MaterialTheme.typography.titleSmall, color = Color(KodenusaPalette.TEXT))
        Spacer(Modifier.height(6.dp))
        Text(
            "Buka panel ☰ lalu pilih berkas. Semua perubahan tersimpan otomatis.",
            style = MaterialTheme.typography.bodySmall,
            color = Color(KodenusaPalette.TEXT_DIM),
        )
        Spacer(Modifier.height(12.dp))
        Pill("Tier 0 · tanpa Gradle", KodenusaPalette.INFO)
    }
}

@Composable
private fun Autosave(session: EditorSession) {
    val file = session.active ?: return
    val text = session.textOf(file)
    androidx.compose.runtime.LaunchedEffect(file.path, text) {
        if (!session.isDirty(file)) return@LaunchedEffect
        kotlinx.coroutines.delay(900)
        session.save(file)
    }
}

private fun initialFile(projectDir: File, nodes: List<ProjectNode>): File? {
    val main = File(projectDir, "src/main/java/app/MainActivity.java")
    if (main.isFile) return main
    return nodes.firstOrNull { !it.directory && it.file.extension == "java" }?.file
}
