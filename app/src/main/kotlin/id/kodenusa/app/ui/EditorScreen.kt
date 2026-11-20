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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import id.kodenusa.app.AppContainer
import id.kodenusa.app.EditorSession
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
    val nodes = remember(projectDir) { ProjectTree.list(projectDir) }
    val session = remember(projectDir) { EditorSession(projectDir, initialFile(projectDir, nodes)) }
    val state = rememberEditorState()

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
}

@Composable
private fun EditorTopBar(
    title: String,
    dirty: Boolean,
    onMenu: () -> Unit,
    onSave: () -> Unit,
    onFind: () -> Unit,
    onBuild: () -> Unit,
    buildable: Boolean,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
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
                Text("belum disimpan · tersimpan otomatis", style = MaterialTheme.typography.labelSmall, color = Color(KodenusaPalette.WARN))
            }
        }
        GhostButton("Cari", onFind)
        GhostButton("Simpan", onSave)
        PrimaryButton("Build APK", onBuild, enabled = buildable)
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
        LazyColumn(Modifier.weight(1f)) {
            items(visible, key = { it.relativePath }) { node ->
                FileRow(node, activePath == node.relativePath, onClick = {
                    if (node.directory) onToggle(node) else onOpen(node.file)
                })
            }
        }
        Box(Modifier.fillMaxWidth().padding(12.dp)) {
            PrimaryButton("Build APK", onBuild, enabled = buildable)
        }
    }
}

@Composable
private fun FileRow(node: ProjectNode, active: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (active) Color(KodenusaPalette.SURFACE_RAISED) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(start = (8 + node.depth * 12).dp, top = 8.dp, bottom = 8.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (node.directory) (if (active) "▾" else "▸") + " " + node.name else node.name,
            style = MaterialTheme.typography.bodySmall,
            color = Color(if (node.directory) KodenusaPalette.TEXT else KodenusaPalette.TEXT_DIM),
            maxLines = 1,
        )
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
