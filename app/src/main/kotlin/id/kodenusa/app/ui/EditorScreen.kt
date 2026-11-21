package id.kodenusa.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.compose.ui.viewinterop.AndroidView
import id.kodenusa.app.AppContainer
import id.kodenusa.app.EditorSession
import id.kodenusa.app.KonteksBerkas
import id.kodenusa.app.ModeTema
import id.kodenusa.core.project.BerkasMasuk
import id.kodenusa.core.project.HasilCari
import id.kodenusa.core.project.KodenusaProject
import id.kodenusa.core.project.ProjectFiles
import id.kodenusa.core.project.ProjectNode
import id.kodenusa.core.project.ProjectSearch
import id.kodenusa.core.project.ProjectTree
import id.kodenusa.editor.CodeEditorView
import id.kodenusa.editor.Language
import id.kodenusa.editor.PenyediaKandidat
import id.kodenusa.ui.Banner
import id.kodenusa.ui.GhostButton
import id.kodenusa.ui.JenisBanner
import id.kodenusa.ui.GhostIconButton
import id.kodenusa.ui.KodenusaPalette
import id.kodenusa.ui.Pill
import id.kodenusa.ui.PrimaryButton
import id.kodenusa.ui.Kode
import id.kodenusa.ui.LocalKodenusaTokens
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
    onSitus: (() -> Unit)? = null,
    onGantiTema: (ModeTema) -> Unit = {},
) {
    val loaded = remember(projectDir) { container.projectStore.load(projectDir) }
    // Situs murni tidak punya APK. Menampilkan tombol "Build APK" di projek seperti itu hanya
    // membuat pengguna menekan tombol yang pasti gagal — jadi tombolnya diganti "Susun situs".
    val webMurni = loaded.project?.jenis == KodenusaProject.JENIS_WEB_MURNI
    // Pratinjau tersedia untuk projek apa pun yang punya halaman situs (web-murni atau
    // web-katalog), bukan hanya web-murni: aset web di dalam APK juga ingin dilihat.
    var pratinjau by remember { mutableStateOf(false) }
    var palet by remember { mutableStateOf(false) }
    // Pemilih berkas tidak boleh diluncurkan dari dalam onClick: di Android 11+ itu membuat main
    // thread menunggu Binder tepat saat Compose membongkar koneksi aksesibilitas (ANR).
    var mintaImpor by remember { mutableStateOf(false) }
    val adaSitus = remember(projectDir) {
        id.kodenusa.core.build.SitusStatis.sumber(projectDir)?.let {
            File(it, "index.html").isFile
        } == true
    }
    // Daftar berkas adalah state, bukan nilai sekali baca: setelah menambah/menghapus berkas,
    // panel harus langsung berubah tanpa keluar-masuk projek.
    var nodes by remember(projectDir) { mutableStateOf(ProjectTree.list(projectDir)) }
    val daftarBerkas = androidx.compose.foundation.lazy.rememberLazyListState()
    val session = remember(projectDir) { EditorSession(projectDir, initialFile(projectDir, nodes)) }
    val state = rememberEditorState()
    var aksi by remember { mutableStateOf<FileAction?>(null) }
    var aiTerbuka by remember { mutableStateOf(false) }
    var pesanBerkas by remember { mutableStateOf<String?>(null) }
    var pythonTerbuka by remember { mutableStateOf(false) }
    fun segarkan() {
        nodes = ProjectTree.list(projectDir)
    }

    // Mengambil berkas yang sudah ada di HP (foto, css, logo, .java). Pemilih berkas Android (SAF)
    // dipakai supaya aplikasi tidak perlu izin penyimpanan sama sekali: izin tidak bisa "gagal"
    // kalau memang tidak pernah diminta.
    val konteksHp = androidx.compose.ui.platform.LocalContext.current
    val runnerPython = remember {
        id.kodenusa.app.PythonRunner.dariKonteks(konteksHp.applicationInfo.nativeLibraryDir)
    }
    val pemilihBerkas = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) {
            state.toast = "Tidak ada berkas dipilih."
            return@rememberLauncherForActivityResult
        }
        val tujuan = BerkasMasuk.folderDariAktif(projectDir, session.active)
        val nama = KonteksBerkas.namaTampilan(konteksHp, uri)
        val hasil = runCatching {
            konteksHp.contentResolver.openInputStream(uri)?.use { aliran ->
                BerkasMasuk.simpan(projectDir, tujuan, nama, aliran)
            } ?: BerkasMasuk.Hasil.Gagal("Berkas tidak bisa dibaca dari lokasi itu.")
        }.getOrElse { BerkasMasuk.Hasil.Gagal(it.message ?: "penyebab tidak diketahui") }
        segarkan()
        val kabar = when (hasil) {
            is BerkasMasuk.Hasil.Sukses ->
                "${hasil.file.name} masuk ke ${tujuan.ifBlank { "akar projek" }} (${hasil.byte} bita)"
            is BerkasMasuk.Hasil.Gagal -> "$nama ditolak — ${hasil.pesan}"
        }
        pesanBerkas = kabar
        state.toast = kabar
    }

    val scope = rememberCoroutineScope()
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val canBuild = loaded.config != null
    // Ukuran font editor (docs/14 2.6): dibaca dari setelan sekali, diubah lewat A−/A+ di bar.
    val setelanEditor = container.setelanEditor
    var ukuranFont by remember { mutableStateOf(setelanEditor.ukuranFont) }

    ModalNavigationDrawer(
        drawerState = drawer,
        gesturesEnabled = true,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = Kode.surface,
                modifier = Modifier.width(272.dp),
            ) {
                FilePanel(
                    nodes = nodes,
                    listState = daftarBerkas,
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
                    onNewFile = { aksi = FileAction.NewFile },
                    onNewFolder = { aksi = FileAction.NewFolder },
                    onRename = { node -> aksi = FileAction.Rename(node) },
                    onDelete = { node -> aksi = FileAction.Delete(node) },
                    onMove = { node -> aksi = FileAction.Move(node) },
                    onBagikan = { node -> bagikan(konteksHp, node.file) },
                    onDuplicate = { node ->
                        // Duplikat tidak perlu pertanyaan, jadi langsung dikerjakan.
                        when (val hasil = ProjectFiles.duplicate(projectDir, node.relativePath)) {
                            is ProjectFiles.Hasil.Gagal -> state.toast = hasil.pesan
                            is ProjectFiles.Hasil.Sukses -> {
                                segarkan()
                                state.toast = "Salinan dibuat: ${hasil.file.name}"
                            }
                        }
                    },
                    onAmbil = {
                        pesanBerkas = null
                        mintaImpor = true
                    },
                    pesan = pesanBerkas,
                    buildable = canBuild,
                    webMurni = webMurni,
                    onSitus = { session.saveAll(); onSitus?.invoke() },
                    adaSitus = adaSitus,
                    onPratinjau = {
                        session.saveAll()
                        pratinjau = true
                    },
                )
            }
        },
    ) {
        val hostPesan = remember { SnackbarHostState() }
        LaunchedEffect(mintaImpor) {
            if (mintaImpor) {
                mintaImpor = false
                pemilihBerkas.launch(arrayOf("*/*"))
            }
        }
        // Semua hasil aksi (berkas masuk, salinan, galat) sebelumnya ditulis ke `state.toast`
        // tetapi tidak pernah ditampilkan: pengguna tidak tahu apakah aksinya berhasil atau gagal.
        LaunchedEffect(state.toast) {
            val pesan = state.toast ?: return@LaunchedEffect
            state.toast = null
            hostPesan.showSnackbar(pesan)
        }
        Scaffold(
            containerColor = Kode.background,
            snackbarHost = { SnackbarHost(hostPesan) { data -> Pesan(data.visuals.message) } },
        ) { padding ->
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
                    webMurni = webMurni,
                    onSitus = { session.saveAll(); onSitus?.invoke() },
                    adaSitus = adaSitus,
                    onPratinjau = {
                        session.saveAll()
                        pratinjau = true
                    },
                    onPalet = { palet = true },
                    onJalankanPython = if (session.active?.name?.endsWith(".py", ignoreCase = true) == true) {
                        {
                            session.saveAll()
                            pythonTerbuka = true
                        }
                    } else {
                        null
                    },
                )

                TabStrip(session, onSelect = { file -> session.open(file); state.load(file) })

                if (state.findOpen) {
                    FindBar(
                        query = state.query,
                        onQuery = { value ->
                            state.query = value
                            if (state.findProjek) {
                                state.hasilProjek = ProjectSearch.cari(projectDir, value)
                            } else {
                                state.matches = state.editor?.countMatches(value) ?: 0
                                state.matchIndex = 0
                            }
                        },
                        matches = if (state.findProjek) state.hasilProjek.size else state.matches,
                        index = state.matchIndex,
                        modeProjek = state.findProjek,
                        onMode = {
                            state.findProjek = !state.findProjek
                            state.hasilProjek = if (state.findProjek) {
                                ProjectSearch.cari(projectDir, state.query)
                            } else {
                                emptyList()
                            }
                        },
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
                        pengganti = state.pengganti,
                        onPengganti = { state.pengganti = it; state.pesanGanti = null },
                        pesanGanti = state.pesanGanti,
                        onGantiSemua = {
                            val ringkas = ProjectSearch.ganti(projectDir, state.query, state.pengganti)
                            state.pesanGanti = if (ringkas.total == 0) {
                                "Tidak ada yang diganti."
                            } else {
                                "${ringkas.total} potongan di ${ringkas.file} berkas diganti."
                            }
                            // Berkas aktif bisa jadi salah satu yang berubah: muat ulang supaya
                            // yang terlihat di editor sama dengan yang ada di disk.
                            state.hasilProjek = ProjectSearch.cari(projectDir, state.query)
                            val aktif = state.loadedPath?.let { File(it) }
                            if (aktif != null && aktif.isFile) {
                                val jalurAktif = runCatching { aktif.relativeTo(projectDir).invariantSeparatorsPath }
                                    .getOrNull()
                                if (ringkas.berkas.any { it.jalur == jalurAktif }) {
                                    session.open(aktif)
                                    state.load(aktif)
                                }
                            }
                        },
                    )
                }

                if (state.findOpen && state.findProjek) {
                    HasilCariPanel(
                        hasil = state.hasilProjek,
                        onBuka = { hit ->
                            val berkas = File(projectDir, hit.jalur)
                            if (berkas.isFile) {
                                session.open(berkas)
                                state.load(berkas)
                                state.query = state.query
                                state.findProjek = false
                                state.findOpen = false
                            }
                        },
                    )
                }

                // Python dan Kotlin: jujur di layar, bukan janji kosong. KodeNusa bisa menyunting
                // dan membagikan kedua jenis berkas itu, tetapi Tier 0 hanya mengenal Java — jadi
                // build akan gagal dan pengguna berhak tahu sebelum menekan Build APK.
                val berkasAktif = session.active
                if (berkasAktif != null && berkasAktif.name.endsWith(".py", ignoreCase = true)) {
                    Banner(
                        JenisBanner.PERINGATAN,
                        "Python dijalankan dengan MicroPython di dalam HP (tanpa internet). " +
                            "Kode dasar jalan; modul seperti requests, numpy, dan pip belum tersedia. " +
                            "Build APK dari Python belum bisa.",
                        Modifier.testTag("catatan-python"),
                    )
                }
                if (berkasAktif != null && berkasAktif.name.endsWith(".kt", ignoreCase = true)) {
                    Banner(
                        JenisBanner.PERINGATAN,
                        "Kotlin belum bisa dibuild di KodeNusa (lihat catatan di wizard). " +
                            "Berkas tetap bisa disunting dan dikirim ke GitHub.",
                        Modifier.testTag("catatan-kotlin"),
                    )
                }

                Box(Modifier.weight(1f).fillMaxWidth().background(Kode.surface)) {
                    val file = session.active
                    val paletEditor = LocalKodenusaTokens.current.paletInt
                    if (file == null) {
                        EmptyEditor()
                    } else {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { ctx ->
                                CodeEditorView(ctx).also { view ->
                                    state.editor = view
                                    view.setWordWrap(state.wrap)
                                    view.setTextSizeSp(ukuranFont)
                                    view.onTextChanged = { text ->
                                        session.update(file, text)
                                        // Lengkapi kata (docs/14 2.5): dihitung dari isi berkas
                                        // aktif saja, tanpa index projek (HP 2 GB).
                                        val kata = view.kataSebelumKursor()
                                        state.kataDiketik = kata
                                        state.kandidat = PenyediaKandidat.kandidat(text, kata, Language.of(file.name))
                                    }
                                    view.onCursorMoved = { line, column ->
                                        state.line = line
                                        state.column = column
                                    }
                                }
                            },
                            update = { view ->
                                view.palet = paletEditor
                                view.setTextSizeSp(ukuranFont)
                                if (state.loadedPath != file.path) {
                                    view.setContent(session.textOf(file), Language.of(file.name))
                                    state.loadedPath = file.path
                                }
                            },
                        )
                    }
                }

                // Panel kandidat lengkapi kata (docs/14 2.5): muncul hanya saat ada kandidat,
                // di atas bar aksesori supaya tetap dekat keyboard.
                if (state.kandidat.isNotEmpty()) {
                    PanelKandidat(
                        kandidat = state.kandidat,
                        onPilih = { kata ->
                            state.editor?.gantiKataAktif(kata)
                            state.kandidat = emptyList()
                            state.kataDiketik = ""
                        },
                    )
                }

                KeyBar(
                    position = "${state.line}:${state.column}",
                    ukuranFont = ukuranFont,
                    onFontKecil = { ukuranFont = setelanEditor.perkecil() },
                    onFontBesar = { ukuranFont = setelanEditor.perbesar() },
                    onInsert = { snippet -> state.editor?.insertSnippet(snippet) },
                    onLeft = { state.editor?.moveCursor(-1) },
                    onRight = { state.editor?.moveCursor(1) },
                    onUndo = { state.editor?.undo() },
                    onRedo = { state.editor?.redo() },
                    onRapikan = {
                        val berubah = state.editor?.rapikan() ?: false
                        if (berubah) state.toast = "Indentasi dirapikan"
                        berubah
                    },
                    onSave = { session.saveAll(); state.toast = "Tersimpan" },
                    onBuild = { session.saveAll(); onBuild() },
                )
            }
        }
    }

    Autosave(session)
    if (pratinjau) {
        PratinjauSitusDialog(akarProjek = projectDir, onTutup = { pratinjau = false })
    }
    if (palet) {
        PaletPerintahDialog(
            perintah = perintahBukaBerkas(nodes) { berkas ->
                session.open(berkas)
                state.load(berkas)
            } + daftarPerintah(
                onSimpan = { session.saveAll(); state.toast = "Tersimpan" },
                onCari = { state.findOpen = true },
                onBuild = { session.saveAll(); onBuild() },
                onSitus = { session.saveAll(); onSitus?.invoke() },
                onPratinjau = { session.saveAll(); pratinjau = true },
                onBerkasBaru = { aksi = FileAction.NewFile },
                onImpor = { mintaImpor = true },
                onTemaGelap = { onGantiTema(ModeTema.GELAP) },
                onTemaTerang = { onGantiTema(ModeTema.TERANG) },
                onTemaSistem = { onGantiTema(ModeTema.SISTEM) },
                webMurni = webMurni,
                adaSitus = adaSitus,
            ),
            onTutup = { palet = false },
        )
    }
    if (aiTerbuka) {
        val aktif = session.active
        AiScreen(
            store = container.aiStore,
            konteks = AiKonteks(
                jalur = aktif?.let { runCatching { it.relativeTo(projectDir).invariantSeparatorsPath }.getOrNull() },
                isi = aktif?.let { session.textOf(it) },
                judul = if (aktif == null) "Tanya AI" else "AI · ${aktif.name}",
            ),
            kunciProjek = projectDir.name,
            akarProjek = projectDir,
            onDismiss = { aiTerbuka = false },
            onSisip = { kode, bahasa ->
                val pesan = ketikaSisip(
                    aktif, session, state.editor, kode, bahasa,
                    projectDir, container.undoTulis, container.riwayatAgen,
                )
                state.toast = pesan
                aiTerbuka = false
                null
            },
            onBukaBerkasBaru = { berkas ->
                // Berkas yang baru dibuat AI langsung dibuka: pengguna melihat hasilnya, bukan
                // mencarinya di panel. Pohon berkas juga disegarkan supaya berkas baru terlihat.
                session.open(berkas)
                state.load(berkas)
                nodes = ProjectTree.list(projectDir)
                aiTerbuka = false
                state.toast = "Berkas ${berkas.name} dibuat dan dibuka."
            },
        )
    }
    if (pythonTerbuka) {
        val aktif = session.active
        if (aktif != null) {
            PythonConsoleDialog(
                berkas = aktif,
                folderKerja = projectDir,
                runner = runnerPython,
                onTutup = { pythonTerbuka = false },
            )
        }
    }
    FileActionHost(
        aksi = aksi,
        projectDir = projectDir,
        onDismiss = { aksi = null },
        onSelesai = { segarkan() },
        onBuka = { file -> session.open(file); state.load(file) },
        onTutupTab = { file -> session.close(file) },
        session = session,
        semuaNode = nodes,
    )
}

/** Menaruh kode jawaban AI ke berkas aktif; mengembalikan pesan untuk toast. */
private fun ketikaSisip(
    aktif: File?,
    session: EditorSession,
    editor: id.kodenusa.editor.CodeEditorView?,
    kode: String,
    bahasa: String,
    akarProjek: File,
    undo: id.kodenusa.app.UndoTulis,
    riwayat: id.kodenusa.app.RiwayatAgen,
): String {
    if (aktif == null) return "Belum ada berkas yang dibuka. Pilih satu berkas dulu di panel, lalu tekan sisip lagi."
    val teks = aktif.name.substringAfterLast('.', "").lowercase()
    if (teks !in setOf("java", "xml", "html", "htm", "css", "js", "json", "md", "txt", "py", "sql")) {
        return "Kode tidak dimasukkan: ${aktif.name} bukan berkas teks."
    }
    // Cadangkan isi lama dulu: satu jawaban model yang salah tidak boleh menghapus kerja
    // pengguna selamanya. Tombol "Batalkan tulisan AI" di layar AI mengembalikannya.
    val batch = undo.siapkanBatch()
    val jalurRelatif = runCatching { aktif.relativeTo(akarProjek).invariantSeparatorsPath }
        .getOrDefault(aktif.name)
    undo.simpanLama(batch, jalurRelatif, aktif)
    // Tiga hal harus terjadi sekaligus, dulu hanya dua: teks di memori, berkas di disk, DAN isi
    // editor yang terlihat. Tanpa yang ketiga, pengguna menekan sisip tetapi layar tidak berubah.
    session.update(aktif, kode)
    val tersimpan = session.save(aktif)
    editor?.setContent(kode, editor.languageOf(aktif.name))
    val bahasaKode = if (bahasa.isBlank()) "" else " ($bahasa)"
    if (tersimpan) {
        // Sisip juga tercatat di riwayat agen supaya bisa dibuka ulang dari layar AI.
        riwayat.catat(
            projek = akarProjek.name,
            tugas = "Sisip kode AI ke $jalurRelatif",
            berkas = listOf(jalurRelatif),
            batch = batch,
        )
    }
    return if (tersimpan) {
        "Kode AI$bahasaKode masuk ke ${aktif.name} dan sudah disimpan."
    } else {
        "Kode masuk ke ${aktif.name}, tetapi gagal menulis ke penyimpanan."
    }
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
    semuaNode: List<ProjectNode>,
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
        onPindah = { node, tujuan ->
            val terbuka = session.tabs.firstOrNull { it.path == node.file.path }
            when (val hasil = ProjectFiles.move(projectDir, node.relativePath, tujuan)) {
                is ProjectFiles.Hasil.Gagal -> hasil.pesan
                is ProjectFiles.Hasil.Sukses -> {
                    // Tab yang terbuka harus dibuka ulang di jalur baru, kalau tidak penyimpanan
                    // otomatis menulis ke lokasi yang sudah tidak ada.
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
        semuaNode = semuaNode,
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
    webMurni: Boolean = false,
    onSitus: () -> Unit = {},
    adaSitus: Boolean = false,
    onPratinjau: () -> Unit = {},
    onPalet: () -> Unit = {},
    onJalankanPython: (() -> Unit)? = null,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            GhostIconButton(Icons.Filled.Menu, "Buka panel berkas", onMenu, Modifier.testTag("buka-menu"))
            GhostButton("Aksi", onPalet, Modifier.testTag("buka-palet"))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    color = Kode.text,
                    maxLines = 1,
                )
                if (dirty) {
                    Text(
                        "belum disimpan · tersimpan otomatis",
                        style = MaterialTheme.typography.labelSmall,
                        color = Kode.warn,
                    )
                }
            }
            if (webMurni) {
                PrimaryButton("Susun situs", onSitus, modifier = Modifier.testTag("susun-dari-editor"))
            } else {
                PrimaryButton("Build APK", onBuild, enabled = buildable)
            }
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
            if (onJalankanPython != null) {
                GhostButton("Jalankan", onJalankanPython, Modifier.testTag("jalankan-python"))
            }
            if (adaSitus) {
                GhostButton("Pratinjau", onPratinjau, Modifier.testTag("buka-pratinjau"))
            }
        }
    }
}

@Composable
private fun TabStrip(session: EditorSession, onSelect: (File) -> Unit) {
    if (session.tabs.isEmpty()) return
    Row(
        Modifier
            .fillMaxWidth()
            .background(Kode.surfaceRaised)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        session.tabs.forEach { file ->
            val active = file.path == session.active?.path
            Row(
                Modifier
                    .background(
                        if (active) Kode.background else Kode.surface,
                        RoundedCornerShape(6.dp),
                    )
                    .border(
                        1.dp,
                        if (active) Kode.accent else Kode.border,
                        RoundedCornerShape(6.dp),
                    )
                    .clickable { onSelect(file) }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    file.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (active) Kode.text else Kode.textDim,
                )
                if (session.isDirty(file)) {
                    Spacer(Modifier.width(4.dp))
                    Text("●", style = MaterialTheme.typography.labelSmall, color = Kode.warn)
                }
            }
        }
    }
}

@Composable
private fun FilePanel(
    nodes: List<ProjectNode>,
    listState: androidx.compose.foundation.lazy.LazyListState,
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
    onMove: (ProjectNode) -> Unit,
    onDuplicate: (ProjectNode) -> Unit,
    onBagikan: (ProjectNode) -> Unit,
    onAmbil: () -> Unit,
    pesan: String?,
    buildable: Boolean,
    webMurni: Boolean = false,
    onSitus: () -> Unit = {},
    adaSitus: Boolean = false,
    onPratinjau: () -> Unit = {},
) {
    val visible = ProjectTree.visible(nodes, collapsed)
    Column(Modifier.fillMaxHeight()) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            GhostIconButton(Icons.Filled.ArrowBack, "Kembali", onBack)
            Text(
                "Berkas projek",
                style = MaterialTheme.typography.titleSmall,
                color = Kode.text,
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
        // Mengambil berkas yang sudah ada di HP (foto, css, logo, .java) — dulu tidak ada jalan
        // masuk sama sekali selain mengetik ulang, dan itu yang dikeluhkan "dimana ambil file".
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
            GhostButton(
                "Ambil dari HP",
                onAmbil,
                Modifier.fillMaxWidth().testTag("ambil-dari-hp"),
            )
        }
        // Hasil aksi berkas ditampilkan di panel ini juga, bukan hanya snackbar di bawah: saat
        // panel terbuka, snackbar tertutup panel dan pengguna tidak tahu berkasnya masuk atau tidak.
        if (pesan != null) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .background(Kode.surfaceRaised, RoundedCornerShape(10.dp))
                    .border(1.dp, Kode.border, RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp)
                    .testTag("pesan-berkas"),
            ) {
                Text(
                    pesan,
                    style = MaterialTheme.typography.labelSmall,
                    color = Kode.textDim,
                )
            }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).padding(top = 6.dp).testTag("daftar-berkas"),
        ) {
            items(visible, key = { it.relativePath }) { node ->
                FileRow(
                    node = node,
                    active = activePath == node.relativePath,
                    onClick = { if (node.directory) onToggle(node) else onOpen(node.file) },
                    onRename = { onRename(node) },
                    onDelete = { onDelete(node) },
                    onMove = { onMove(node) },
                    onDuplicate = { onDuplicate(node) },
                    onBagikan = onBagikan,
                )
            }
        }
        Box(Modifier.fillMaxWidth().padding(12.dp)) {
            if (webMurni) {
                PrimaryButton("Susun situs", onSitus, modifier = Modifier.testTag("susun-dari-panel"))
            } else {
                PrimaryButton("Build APK", onBuild, enabled = buildable)
            }
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
    onMove: () -> Unit,
    onDuplicate: () -> Unit,
    onBagikan: (ProjectNode) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (active) Kode.surfaceRaised else Color.Transparent)
            .clickable(onClick = onClick)
            .testTag("baris-${node.relativePath}")
            .padding(start = (8 + node.depth * 12).dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (node.directory) ("▸ " + node.name) else node.name,
            style = MaterialTheme.typography.bodySmall,
            color = if (node.directory) Kode.text else Kode.textDim,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        Box {
            // Tombol dengan ukuran sentuh 48dp, bukan teks kecil: target 12dp tidak bisa ditekan
            // dengan jempol dan juga gagal di uji instrumentasi ("Failed to inject touch input").
            GhostIconButton(
                Icons.Filled.MoreVert,
                "Menu berkas ${node.name}",
                { menu = true },
                Modifier.width(48.dp).testTag("menu-${node.name}"),
            )
            DropdownMenu(
                expanded = menu,
                onDismissRequest = { menu = false },
                containerColor = Kode.surfaceRaised,
            ) {
                DropdownMenuItem(
                    text = { Text("Bagikan berkas…", color = Kode.text) },
                    onClick = { menu = false; onBagikan(node) },
                    enabled = !node.directory,
                )
                DropdownMenuItem(
                    text = { Text("Pindahkan…", color = Kode.text) },
                    onClick = { menu = false; onMove() },
                )
                DropdownMenuItem(
                    text = { Text("Duplikat", color = Kode.text) },
                    onClick = { menu = false; onDuplicate() },
                )
                DropdownMenuItem(
                    text = { Text("Ganti nama", color = Kode.text) },
                    onClick = { menu = false; onRename() },
                )
                DropdownMenuItem(
                    text = { Text("Hapus", color = Kode.error) },
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
    pengganti: String = "",
    onPengganti: (String) -> Unit = {},
    pesanGanti: String? = null,
    onGantiSemua: () -> Unit = {},
    modeProjek: Boolean,
    onMode: () -> Unit,
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
            placeholder = {
                Text(
                    if (modeProjek) "cari di seluruh projek" else "cari di berkas",
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Kode.surfaceRaised,
                unfocusedContainerColor = Kode.surfaceRaised,
                focusedTextColor = Kode.text,
                unfocusedTextColor = Kode.text,
            ),
            modifier = Modifier.weight(1f),
        )
        Text(
            if (matches == 0) "0" else if (modeProjek) "$matches" else "${index + 1}/$matches",
            style = MaterialTheme.typography.labelSmall,
            color = Kode.textDim,
        )
        GhostButton(
            if (modeProjek) "projek ✓" else "projek",
            onMode,
            Modifier.testTag("cari-projek"),
        )
        GhostButton("↑", onPrev)
        GhostButton("↓", onNext)
        GhostButton(if (wrap) "wrap on" else "wrap off", onWrap)
    }
    if (modeProjek) {
        // Baris kedua hanya muncul untuk pencarian se-projek: mengganti di berkas aktif sudah
        // bisa dilakukan langsung di editor, jadi tombolnya tidak perlu ditumpuk di sana.
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            TextField(
                value = pengganti,
                onValueChange = onPengganti,
                singleLine = true,
                placeholder = { Text("ganti dengan…", style = MaterialTheme.typography.bodySmall) },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Kode.surfaceRaised,
                    unfocusedContainerColor = Kode.surfaceRaised,
                    focusedTextColor = Kode.text,
                    unfocusedTextColor = Kode.text,
                ),
                modifier = Modifier.weight(1f).testTag("isi-pengganti"),
            )
            GhostButton(
                "Ganti semua",
                onGantiSemua,
                Modifier.testTag("ganti-semua"),
                enabled = query.isNotBlank() && matches > 0,
            )
        }
        if (pesanGanti != null) {
            Text(
                pesanGanti,
                style = MaterialTheme.typography.labelSmall,
                color = Kode.ok,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp)
                    .testTag("pesan-ganti"),
            )
        }
    }
}

/** Daftar kecocokan lintas berkas; satu ketukan membuka berkasnya. */
@Composable
private fun HasilCariPanel(hasil: List<HasilCari>, onBuka: (HasilCari) -> Unit) {
    if (hasil.isEmpty()) {
        Text(
            "Tidak ada kecocokan di berkas projek.",
            style = MaterialTheme.typography.labelSmall,
            color = Kode.textDim,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        )
        return
    }
    LazyColumn(
        Modifier.fillMaxWidth().heightIn(max = 180.dp).testTag("hasil-cari-projek"),
    ) {
        items(hasil) { hit ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable { onBuka(hit) }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .testTag("cari-${hit.jalur}-${hit.baris}"),
            ) {
                Text(
                    "${hit.jalur}:${hit.baris}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Kode.info,
                )
                Text(
                    hit.teks,
                    style = MaterialTheme.typography.bodySmall,
                    color = Kode.textDim,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * Buka-berkas cepat di palet perintah (docs/14 2.7): satu perintah per berkas projek. Pencarian
 * memakai mesin `saring` yang sama, jadi mengetik sebagian nama sudah menemukan berkasnya.
 */
fun perintahBukaBerkas(nodes: List<ProjectNode>, onBuka: (File) -> Unit): List<Perintah> =
    nodes.filter { !it.directory }.map { node ->
        Perintah(
            id = "buka-" + node.relativePath.replace(Regex("[^A-Za-z0-9]+"), "-").trimEnd('-'),
            judul = node.name,
            keterangan = node.relativePath,
            alias = listOf(node.relativePath),
            jalankan = { onBuka(node.file) },
        )
    }

/**
 * Panel kandidat lengkapi kata (docs/14 2.5): maksimal 6 chip di atas bar aksesori. Memilih satu
 * kandidat mengganti kata yang sedang diketik, buka menambahkan di belakangnya.
 */
@Composable
internal fun PanelKandidat(kandidat: List<String>, onPilih: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Kode.surfaceRaised)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 6.dp, vertical = 4.dp)
            .testTag("panel-kandidat"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            "⇥",
            style = MaterialTheme.typography.labelMedium,
            color = Kode.textDim,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        kandidat.forEach { kata ->
            Box(
                Modifier
                    .background(Kode.surface, RoundedCornerShape(5.dp))
                    .border(1.dp, Kode.accent, RoundedCornerShape(5.dp))
                    .clickable { onPilih(kata) }
                    .padding(horizontal = 10.dp, vertical = 8.dp)
                    .testTag("kandidat-$kata"),
            ) {
                Text(kata, style = MaterialTheme.typography.labelMedium, color = Kode.text)
            }
        }
    }
}

@Composable
private fun KeyBar(
    position: String,
    ukuranFont: Float = 14f,
    onFontKecil: () -> Unit = {},
    onFontBesar: () -> Unit = {},
    onInsert: (String) -> Unit,
    onLeft: () -> Unit,
    onRight: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onRapikan: () -> Boolean,
    onSave: () -> Unit,
    onBuild: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Kode.surfaceRaised)
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
        // Ukuran font (docs/14 2.6): dua tombol di ujung bar yang sudah bisa digulir, jadi tidak
        // merebut tempat dengan tombol yang lebih sering dipakai.
        Key("A−", Modifier.testTag("font-kecil")) { onFontKecil() }
        Key("A+", Modifier.testTag("font-besar")) { onFontBesar() }
        Text(
            "${ukuranFont.toInt()}sp",
            style = MaterialTheme.typography.labelSmall,
            color = Kode.textDim,
            modifier = Modifier.padding(horizontal = 4.dp).testTag("ukuran-font"),
        )
        // docs/13 C2: merapikan indentasi seluruh berkas. Hanya spasi awal baris yang berubah,
        // jadi tidak pernah merusak kode; undo tetap bisa membatalkan.
        Key("Rapikan") {
            val berubah = onRapikan()
            if (berubah) onSave()
        }
        Key("Simpan") { onSave() }
        Key("Build") { onBuild() }
        Text(
            position,
            style = MaterialTheme.typography.labelSmall,
            color = Kode.textDim,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
    }
}

@Composable
private fun Key(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        Modifier
            .background(Kode.surface, RoundedCornerShape(5.dp))
            .border(1.dp, Kode.border, RoundedCornerShape(5.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .then(modifier),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Kode.text)
    }
}

@Composable
private fun EmptyEditor() {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Belum ada berkas dibuka", style = MaterialTheme.typography.titleSmall, color = Kode.text)
        Spacer(Modifier.height(6.dp))
        Text(
            "Buka panel ☰ lalu pilih berkas. Semua perubahan tersimpan otomatis.",
            style = MaterialTheme.typography.bodySmall,
            color = Kode.textDim,
        )
        Spacer(Modifier.height(12.dp))
        Pill("Tier 0 · tanpa Gradle", Kode.info)
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

private fun initialFile(projectDir: File, nodes: List<ProjectNode>): File? {    // Urutan ini membuat projek web langsung terbuka di halamannya, bukan di layar kosong.
    val kandidat = listOf(
        "src/main/java/app/MainActivity.java",
        "web/index.html",
        "src/main/assets/index.html",
        "index.html",
        "web/gaya.css",
    )
    kandidat.forEach { jalur ->
        val berkas = File(projectDir, jalur)
        if (berkas.isFile) return berkas
    }
    val ekstensiKode = setOf("java", "html", "htm", "css", "js", "json")
    return nodes.firstOrNull { !it.directory && it.file.extension.lowercase() in ekstensiKode }?.file
}

/**
 * Folder tujuan berkas yang diambil dari HP: folder berkas yang sedang dibuka, atau akar projek.
 *
 * Dulu berkas baru "hilang" karena selalu ditaruh di folder yang tidak terlihat; untuk berkas dari
 * HP aturannya sama — masuk ke folder yang sedang dilihat pengguna.
 */
/**
 * Membagikan satu berkas projek ke aplikasi lain (WhatsApp, Drive, Gmail) lewat `FileProvider`.
 *
 * Ini jalan keluar yang selama ini tidak ada: pengguna bisa mengeluarkan berkas tanpa kabel USB dan
 * tanpa mencari folder `Android/data` yang tertutup sejak Android 11.
 */
private fun bagikan(konteks: android.content.Context, berkas: java.io.File) {
    if (!berkas.isFile) return
    runCatching {
        val uri = KonteksBerkas.uriBagikan(konteks, berkas)
        val kirim = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = KonteksBerkas.mime(berkas)
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            putExtra(android.content.Intent.EXTRA_SUBJECT, berkas.name)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        konteks.startActivity(
            android.content.Intent.createChooser(kirim, "Bagikan ${berkas.name}").apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
    }
}

/** Balasan singkat untuk aksi berkas, memakai warna panel aplikasi (bukan snackbar bawaan). */
@Composable
private fun Pesan(teks: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(12.dp)
            .background(Kode.surfaceRaised, RoundedCornerShape(12.dp))
            .border(1.dp, Kode.border, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .testTag("pesan-aksi"),
    ) {
        Text(teks, style = MaterialTheme.typography.bodySmall, color = Kode.text)
    }
}
