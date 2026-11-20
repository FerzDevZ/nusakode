package id.kodenusa.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import id.kodenusa.app.MAKS_BYTE_BERKAS_AI
import id.kodenusa.app.AiProjectBoard
import id.kodenusa.app.AiSettingsStore
import id.kodenusa.core.ai.AiGagal
import id.kodenusa.core.ai.AiModel
import androidx.core.content.FileProvider
import id.kodenusa.app.GambarUntukAi
import id.kodenusa.app.UndoTulis
import id.kodenusa.core.ai.ChatMessage
import id.kodenusa.core.ai.CodeBlocks
import id.kodenusa.core.ai.OpenAiCompatClient
import id.kodenusa.core.ai.PromptBuilder
import id.kodenusa.core.ai.UbahanBerkas
import id.kodenusa.core.project.ProjectTree
import id.kodenusa.ui.GhostButton
import id.kodenusa.ui.KodenusaPalette
import id.kodenusa.ui.Panel
import id.kodenusa.ui.Pill
import id.kodenusa.ui.PrimaryButton
import id.kodenusa.ui.Kode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Konteks yang dibawa layar AI: berkas yang sedang dibuka, galat build, atau kosong. */
data class AiKonteks(
    val jalur: String? = null,
    val isi: String? = null,
    val galat: String? = null,
    val judul: String = "Tanya AI",
)

/**
 * Asisten AI dengan endpoint + kunci milik pengguna sendiri.
 *
 * Tiga hal yang harus benar dan dulu salah:
 * 1. **Tidak reset.** Endpoint/kunci disimpan setiap kali diketik, dan obrolan disimpan per projek.
 * 2. **Kode bisa dimasukkan ke berkas.** Tombol sisip mengembalikan kode ke pemanggil lewat [onSisip];
 *    pemanggil itulah yang menulis ke editor (bukan layar ini) supaya teks di layar ikut berubah.
 * 3. **Tidak ada panggilan jaringan diam-diam.** Tanpa endpoint + kunci, layar ini hanya formulir.
 */
@Composable
fun AiScreen(
    store: AiSettingsStore,
    konteks: AiKonteks,
    kunciProjek: String,
    akarProjek: File,
    onDismiss: () -> Unit,
    onSisip: (kode: String, bahasa: String) -> String?,
    onBukaBerkasBaru: (java.io.File) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val klien = remember { OpenAiCompatClient() }

    var config by remember { mutableStateOf(store.muat()) }
    var endpoint by remember { mutableStateOf(config.baseUrl) }
    var kunci by remember { mutableStateOf(config.apiKey) }
    var modelList by remember { mutableStateOf(store.modelTerakhir()) }
    // Formulir tetap dibuka selama model belum dipilih: mendarat di layar obrolan tanpa model
    // membuat orang bingung dan mengira setelannya hilang.
    var sedangAtur by remember { mutableStateOf(!config.terpasang || config.model.isBlank()) }
    var memuatModel by remember { mutableStateOf(false) }

    var riwayat by remember { mutableStateOf(store.muatPercakapan(kunciProjek)) }
    var pertanyaan by remember { mutableStateOf("") }
    var jawaban by remember { mutableStateOf("") }
    var berpikir by remember { mutableStateOf(false) }
    var sibuk by remember { mutableStateOf(false) }
    var galat by remember { mutableStateOf<String?>(null) }
    var pekerjaan by remember { mutableStateOf<Job?>(null) }
    var gambar by remember { mutableStateOf<String?>(null) }
    var memuatGambar by remember { mutableStateOf(false) }

    // Akses tingkat PROJEK, bukan hanya berkas yang sedang terbuka. Tanpa ini, AI tidak bisa
    // membaca berkas lain (mis. nama kolom di DatabaseKasir.java) dan tidak bisa membuat berkas
    // baru — dan itu yang membuat "AI-nya belum bisa buka folder source".
    val papan = remember(akarProjek) { AiProjectBoard(akarProjek) }
    var pakaiKonteksProjek by remember { mutableStateOf(false) }
    var buatBerkasTerbuka by remember { mutableStateOf(false) }
    var cariBerkas by remember { mutableStateOf("") }
    var berkasSematan by remember { mutableStateOf(listOf<String>()) }
    var pilihBerkas by remember { mutableStateOf(false) }
    var hasilTulis by remember { mutableStateOf<String?>(null) }
    var rencanaTulis by remember { mutableStateOf<List<RencanaTulis>?>(null) }
    var adaBatchUndo by remember { mutableStateOf(false) }
    var karakter by remember { mutableStateOf(store.karakterTerkirim(kunciProjek)) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val undo = remember(akarProjek) { UndoTulis(File(ctx.filesDir, "undo-tulis")) }
    // Dibuat sebagai lambda (bukan fungsi lokal) supaya bisa dipakai launcher di bawah dan tetap
    // ringkas: satu tempat untuk "ubah uri jadi data URL", dipakai galeri maupun kamera.
    val muatGambar: (Uri) -> Unit = { uri ->
        scope.launch {
            memuatGambar = true
            gambar = withContext(Dispatchers.IO) { GambarUntukAi.dataUrl(ctx, uri) }
            memuatGambar = false
            if (gambar == null) {
                galat = "Gambar tidak bisa dibaca. Coba pilih berkas JPG atau PNG lain."
            }
        }
    }
    val pemilihGambar = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        muatGambar(uri)
    }
    // Kamera: banyak pengguna lebih cepat memotret layar galat daripada mencari berkasnya di galeri.
    // Foto ditulis dulu ke cache lewat FileProvider supaya aplikasi kamera punya tujuan tulis.
    var tujuanKamera by remember { mutableStateOf<Uri?>(null) }
    val kamera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { jadi ->
        val uri = tujuanKamera
        if (!jadi || uri == null) return@rememberLauncherForActivityResult
        muatGambar(uri)
    }

    fun simpanDraf() = store.simpanDraf(endpoint, kunci)

    /** Isi berkas projek, dipakai untuk melampirkan berkas mana pun sebagai konteks. */
    fun isiBerkas(jalur: String): String? = runCatching {
        val berkas = File(akarProjek, jalur)
        if (berkas.isFile && berkas.length() <= MAKS_BYTE_BERKAS_AI) berkas.readText() else null
    }.getOrNull()

    fun pesanUntuk(teks: String): List<ChatMessage> {
        val sematan = berkasSematan.mapNotNull { jalur -> isiBerkas(jalur)?.let { jalur to it } }
        val dasar = when {
            // Konteks projek dipakai lebih dulu: model perlu tahu struktur folder sebelum
            // menulis berkas baru, dan hanya prompt agen yang mengajarkan format <<<BERKAS: …>>>.
            pakaiKonteksProjek -> PromptBuilder.untukAgen(teks, papan.konteks(), "")
            sematan.isNotEmpty() -> PromptBuilder.untukBerkasSematan(teks, sematan)
            konteks.galat != null ->
                PromptBuilder.untukGalat(konteks.galat.orEmpty(), konteks.jalur, konteks.isi)
            konteks.jalur != null && konteks.isi != null ->
                PromptBuilder.untukBerkas(teks, konteks.jalur, konteks.isi)
            else -> PromptBuilder.untukTanya(teks)
        }
        // Riwayat obrolan ikut dikirim supaya model ingat konteks percakapan sebelumnya.
        if (riwayat.isEmpty()) return dasar
        val lanjutan = riwayat.takeLast(6).map { (peran, isi) ->
            ChatMessage(
                if (peran == "user") ChatMessage.Peran.PENGGUNA else ChatMessage.Peran.ASISTEN,
                isi,
            )
        }
        return listOf(dasar.first()) + lanjutan + dasar.drop(1)
    }

    fun kirim(teks: String) {
        if (teks.isBlank() || sibuk) return
        galat = null
        jawaban = ""
        berpikir = false
        sibuk = true
        riwayat = riwayat + ("user" to teks)
        store.simpanPercakapan(kunciProjek, riwayat)
        pertanyaan = ""
        pekerjaan = scope.launch {
            try {
                val hasil = withContext(Dispatchers.IO) {
                    val lampiran = gambar
                    val pesan = pesanUntuk(teks).let { daftar ->
                        if (lampiran == null) daftar else daftar.dropLast(1) +
                            daftar.last().copy(gambar = listOf(lampiran))
                    }
                    gambar = null
                    // Indikator biaya jujur: hitung karakter yang benar-benar dikirim ke endpoint.
                    val terkirim = pesan.sumOf { it.isi.length }
                    karakter += terkirim
                    store.tambahKarakter(kunciProjek, terkirim)
                    klien.bicara(
                        config = config,
                        pesan = pesan,
                        onReasoning = { berpikir = true },
                        onDelta = { potongan -> jawaban += potongan },
                    )
                }
                val akhir = jawaban.ifEmpty { hasil }
                riwayat = riwayat + ("assistant" to akhir)
                store.simpanPercakapan(kunciProjek, riwayat)
                jawaban = ""
            } catch (gagal: AiGagal) {
                galat = listOf(gagal.pesan, gagal.saran).filter { it.isNotBlank() }.joinToString("\n")
                riwayat = riwayat.dropLast(1)
                store.simpanPercakapan(kunciProjek, riwayat)
            } catch (error: Exception) {
                galat = "Gagal memanggil AI: ${error.message ?: error.javaClass.simpleName}"
                riwayat = riwayat.dropLast(1)
            } finally {
                sibuk = false
                pekerjaan = null
            }
        }
    }

    fun pilihModel(id: String) {
        config = config.denganModel(id)
        store.simpan(config)
        sedangAtur = false
    }

    /**
     * Menyiapkan rencana tulis dari jawaban model: berkas mana yang BARU dan mana yang akan
     * DITIMPA (dengan ukuran lamanya). Tulisan tidak langsung jalan — pengguna melihat dulu,
     * lalu menyetujui. Berkas yang ditimpa dicadangkan dulu supaya bisa dibatalkan.
     */
    fun siapkanTulis(jawabanModel: String) {
        val edits = UbahanBerkas.ambil(jawabanModel)
        if (edits.isEmpty()) {
            galat = "Jawaban ini tidak berisi penanda <<<BERKAS: jalur>>>. Pakai tombol Konteks projek " +
                "supaya AI menulis berkas lengkap, atau tekan Sisip untuk berkas yang sedang terbuka."
            return
        }
        rencanaTulis = edits.map { edit ->
            val aman = id.kodenusa.core.project.ProjectFiles.safeRelative(akarProjek, edit.jalur.trim())
            val lama = aman?.let { File(akarProjek, it) }
            RencanaTulis(edit, lama?.isFile == true, lama?.length() ?: 0L)
        }
    }

    fun jalankanTulis(rencana: List<RencanaTulis>) {
        val batch = undo.siapkanBatch()
        var ditulis = 0
        val ditolak = ArrayList<String>()
        rencana.forEach { satu ->
            val aman = id.kodenusa.core.project.ProjectFiles.safeRelative(akarProjek, satu.edit.jalur.trim())
            if (aman == null) {
                ditolak += satu.edit.jalur
                return@forEach
            }
            val target = File(akarProjek, aman)
            if (target.isFile) {
                undo.simpanLama(batch, aman, target)
            } else {
                undo.tandaiBaru(batch, aman)
            }
            if (papan.tulis(satu.edit) == null) ditulis++ else ditolak += satu.edit.jalur
        }
        galat = null
        rencanaTulis = null
        hasilTulis = buildString {
            append("$ditulis berkas disimpan di projek")
            if (ditolak.isNotEmpty()) append(". Ditolak: ").append(ditolak.joinToString(", "))
            append(". Tekan Batalkan untuk mengembalikan isi lama.")
        }
        adaBatchUndo = undo.batchTerakhir() != null
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(color = Kode.background, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().imePadding()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GhostButton("Tutup", onDismiss)
                    Text(
                        konteks.judul,
                        style = MaterialTheme.typography.titleSmall,
                        color = Kode.text,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                        maxLines = 1,
                    )
                    if (sedangAtur) {
                        if (config.terpasang) GhostButton("Kembali", { sedangAtur = false })
                    } else {
                        GhostButton("Model", { sedangAtur = true }, Modifier.testTag("ganti-model"))
                    }
                }

                if (sedangAtur) {
                    AturAiPanel(
                        endpoint = endpoint,
                        kunci = kunci,
                        modelList = modelList,
                        modelTerpilih = config.model,
                        memuat = memuatModel,
                        galat = galat,
                        onEndpoint = { endpoint = it; simpanDraf() },
                        onKunci = { kunci = it; simpanDraf() },
                        onPilihModel = ::pilihModel,
                        onMuatModel = {
                            galat = null
                            memuatModel = true
                            scope.launch {
                                try {
                                    val calon = config.copy(baseUrl = endpoint, apiKey = kunci)
                                    val daftar = withContext(Dispatchers.IO) { klien.daftarModel(calon) }
                                    modelList = daftar
                                    store.simpan(calon)
                                    store.simpanModel(daftar)
                                    config = calon.denganModel(calon.model.ifBlank { daftar.first().id })
                                    store.simpan(config)
                                    sedangAtur = false
                                } catch (gagal: AiGagal) {
                                    galat = listOf(gagal.pesan, gagal.saran)
                                        .filter { it.isNotBlank() }.joinToString("\n")
                                } catch (error: Exception) {
                                    galat = "Gagal memuat model: ${error.message}"
                                } finally {
                                    memuatModel = false
                                }
                            }
                        },
                    )
                } else {
                    ChatAiPanel(
                        model = config.model,
                        riwayat = riwayat,
                        jawaban = jawaban,
                        berpikir = berpikir,
                        sibuk = sibuk,
                        galat = galat,
                        adaKonteksBerkas = konteks.isi != null,
                        adaGalat = konteks.galat != null,
                        pertanyaan = pertanyaan,
                        onPertanyaan = { pertanyaan = it },
                        onKirim = { kirim(pertanyaan.ifBlank { "Lanjutkan." }) },
                        onCepat = { teks -> kirim(teks) },
                        onHentikan = { pekerjaan?.cancel(); sibuk = false },
                        onBersihkan = {
                            store.bersihkanPercakapan(kunciProjek)
                            store.nolkanKarakter(kunciProjek)
                            karakter = 0
                            riwayat = emptyList()
                            galat = null
                        },
                        onSisip = { kode, bahasa ->
                            val pesan = onSisip(kode, bahasa)
                            if (pesan != null) galat = pesan
                            // Sisip yang berhasil juga membuat batch cadangan di EditorScreen —
                            // tombol Batalkan mengembalikan batch terakhir, apa pun jalurnya.
                            adaBatchUndo = undo.batchTerakhir() != null
                            null
                        },
                        adaGambar = gambar != null,
                        memuatGambar = memuatGambar,
                        onLampirkan = {
                            pemilihGambar.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        },
                        onFoto = {
                            // HP murah kadang tidak punya aplikasi kamera sama sekali. Tanpa
                            // pemeriksaan ini, ActivityNotFoundException akan mematikan aplikasi
                            // hanya karena satu tombol ditekan.
                            val niat = Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)
                            if (niat.resolveActivity(ctx.packageManager) == null) {
                                galat = "Tidak ada aplikasi kamera di HP ini. Pakai \"Lampirkan gambar\" saja."
                            } else {
                                val berkas = File(ctx.cacheDir, "kamera-ai.jpg")
                                if (berkas.exists()) berkas.delete()
                                val tujuan = FileProvider.getUriForFile(
                                    ctx,
                                    "${ctx.packageName}.berkas",
                                    berkas,
                                )
                                tujuanKamera = tujuan
                                kamera.launch(tujuan)
                            }
                        },
                        onBuangGambar = { gambar = null },
                        adaKonteksProjek = pakaiKonteksProjek,
                        berkasSematan = berkasSematan,
                        hasilTulis = hasilTulis,
                        karakter = karakter,
                        onKonteksProjek = {
                            pakaiKonteksProjek = !pakaiKonteksProjek
                            if (pakaiKonteksProjek) berkasSematan = emptyList()
                            hasilTulis = null
                        },
                        onPilihBerkas = { pilihBerkas = true },
                        onBuangSematan = { jalur -> berkasSematan = berkasSematan - jalur },
                        onTulisProjek = { siapkanTulis(jawaban.ifEmpty { riwayat.lastOrNull()?.second.orEmpty() }) },
                        onBatalkanTulis = {
                            val batch = undo.batchTerakhir()
                            if (batch == null) {
                                hasilTulis = "Tidak ada tulisan AI yang bisa dibatalkan."
                            } else {
                                val berubah = undo.batalkan(batch, akarProjek)
                                hasilTulis = if (berubah.isEmpty()) {
                                    "Tidak ada yang bisa dikembalikan."
                                } else {
                                    "${berubah.size} berkas dikembalikan ke isi sebelumnya."
                                }
                            }
                            adaBatchUndo = undo.batchTerakhir() != null
                        },
                        adaTulisanBisaDibatalkan = adaBatchUndo,
                        onBuatBerkasBaru = { buatBerkasTerbuka = true },
                    )
                }
            }
        }

        if (buatBerkasTerbuka) {
            val kodeTerakhir = CodeBlocks.terbesar(jawaban.ifEmpty { riwayat.lastOrNull()?.second.orEmpty() })
            DialogBuatBerkasDariKode(
                kode = kodeTerakhir?.isi.orEmpty(),
                akar = akarProjek,
                onTutup = { buatBerkasTerbuka = false },
                onJadi = { berkas ->
                    buatBerkasTerbuka = false
                    onBukaBerkasBaru(berkas)
                },
            )
        }

        if (rencanaTulis != null) {
            DialogKonfirmasiTulis(
                rencana = rencanaTulis.orEmpty(),
                onBatal = { rencanaTulis = null },
                onSetuju = { jalankanTulis(rencanaTulis.orEmpty()) },
            )
        }

        if (pilihBerkas) {
            Dialog(onDismissRequest = { pilihBerkas = false }) {
                Surface(
                    color = Kode.surface,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "Pilih berkas projek",
                            style = MaterialTheme.typography.titleMedium,
                            color = Kode.text,
                        )
                        Text(
                            "Isi berkas yang dipilih dikirim utuh sebagai konteks (maksimal 3), " +
                                "hanya ke endpoint AI milikmu.",
                            style = MaterialTheme.typography.labelSmall,
                            color = Kode.textDim,
                        )
                        Spacer(Modifier.height(8.dp))
                        // Pohon folder: folder tampil sebagai judul, berkas menjorok sesuai
                        // kedalamannya — pengguna bisa melihat sub-folder, bukan daftar datar.
                        TextField(
                            value = cariBerkas,
                            onValueChange = { cariBerkas = it },
                            singleLine = true,
                            placeholder = { Text("Cari nama berkas…", color = Kode.textDim) },
                            modifier = Modifier.fillMaxWidth().testTag("cari-berkas-ai"),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Kode.surfaceRaised,
                                unfocusedContainerColor = Kode.surfaceRaised,
                                focusedTextColor = Kode.text,
                                unfocusedTextColor = Kode.text,
                            ),
                        )
                        Spacer(Modifier.height(6.dp))
                        val nodeProyek = remember(akarProjek) { ProjectTree.list(akarProjek) }
                        val tampil = if (cariBerkas.isBlank()) nodeProyek else nodeProyek.filter {
                            it.relativePath.contains(cariBerkas, ignoreCase = true)
                        }
                        if (tampil.isEmpty()) {
                            Text(
                                if (cariBerkas.isBlank()) "Belum ada berkas di projek ini."
                                else "Tidak ada berkas yang cocok dengan \"$cariBerkas\".",
                                style = MaterialTheme.typography.bodySmall,
                                color = Kode.textDim,
                            )
                        }
                        LazyColumn(Modifier.weight(1f, fill = false).testTag("daftar-berkas-ai")) {
                            items(tampil) { node ->
                                val menjorok = Modifier.padding(
                                    start = (8 + node.depth * 14).dp,
                                    top = 6.dp,
                                    bottom = 6.dp,
                                )
                                if (node.directory) {
                                    Text(
                                        "▸ ${node.name}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Kode.textDim,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .then(menjorok)
                                            .testTag("folder-ai-${node.relativePath}"),
                                    )
                                } else {
                                    Text(
                                        node.name,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Kode.text,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .then(menjorok)
                                            .clickable {
                                                val sudahAda = node.relativePath in berkasSematan
                                                if (!sudahAda && berkasSematan.size >= 3) {
                                                    galat = "Maksimal 3 berkas sematan. Buang salah satu dulu dengan tombol Buang."
                                                } else {
                                                    berkasSematan =
                                                        (berkasSematan + node.relativePath).distinct().take(3)
                                                    galat = null
                                                }
                                                pakaiKonteksProjek = false
                                                pilihBerkas = false
                                                hasilTulis = null
                                            }
                                            .testTag("berkas-ai-${node.relativePath}"),
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        GhostButton("Tutup", { pilihBerkas = false }, Modifier.testTag("tutup-pilih-berkas"))
                    }
                }
            }
        }
    }
}

/**
 * Dialog "Buat berkas baru dari kode": pengguna menulis jalur lengkap berkas baru (boleh
 * sub-folder, mis. `src/main/java/app/LayarKedua.java`), berkas dibuat dengan isi kode dari
 * jawaban AI, lalu langsung dibuka di editor. Inilah jalur resmi membuat berkas baru dari AI —
 * sebelumnya tombol sisip hanya bisa menimpa berkas yang sedang terbuka.
 */
// internal (bukan private) supaya bisa diuji langsung dari androidTest tanpa lewat seluruh layar.
@Composable
internal fun DialogBuatBerkasDariKode(
    kode: String,
    akar: java.io.File,
    onTutup: () -> Unit,
    onJadi: (java.io.File) -> Unit,
) {
    var jalur by remember { mutableStateOf("") }
    var galat by remember { mutableStateOf<String?>(null) }
    Dialog(onDismissRequest = onTutup) {
        Surface(
            color = Kode.surface,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "Buat berkas baru dari kode",
                    style = MaterialTheme.typography.titleMedium,
                    color = Kode.text,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Tulis jalur lengkapnya; folder yang belum ada ikut dibuat.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Kode.textDim,
                )
                Spacer(Modifier.height(10.dp))
                TextField(
                    value = jalur,
                    onValueChange = { jalur = it },
                    singleLine = true,
                    placeholder = { Text("src/main/java/app/LayarKedua.java", color = Kode.textDim) },
                    modifier = Modifier.fillMaxWidth().testTag("jalur-berkas-baru"),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Kode.surfaceRaised,
                        unfocusedContainerColor = Kode.surfaceRaised,
                        focusedTextColor = Kode.text,
                        unfocusedTextColor = Kode.text,
                    ),
                )
                galat?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = Kode.error)
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GhostButton("Batal", onTutup, Modifier.testTag("batal-berkas-baru"))
                    PrimaryButton(
                        "Buat berkas",
                        {
                            val bersih = jalur.trim().trim('/')
                            if (bersih.isBlank()) {
                                galat = "Jalur berkas masih kosong."
                                return@PrimaryButton
                            }
                            if (bersih.contains("..") || bersih.startsWith("/")) {
                                galat = "Jalur harus di dalam folder projek."
                                return@PrimaryButton
                            }
                            val nama = bersih.substringAfterLast('/')
                            val induk = bersih.removeSuffix("/$nama")
                            val hasil = id.kodenusa.core.project.ProjectFiles.createFile(
                                akar, induk, nama, kode,
                            )
                            when (hasil) {
                                is id.kodenusa.core.project.ProjectFiles.Hasil.Gagal -> galat = hasil.pesan
                                is id.kodenusa.core.project.ProjectFiles.Hasil.Sukses -> onJadi(hasil.file)
                            }
                        },
                        modifier = Modifier.testTag("buat-berkas-ok"),
                    )
                }
            }
        }
    }
}

@Composable
private fun AturAiPanel(
    endpoint: String,
    kunci: String,
    modelList: List<AiModel>,
    modelTerpilih: String,
    memuat: Boolean,
    galat: String?,
    onEndpoint: (String) -> Unit,
    onKunci: (String) -> Unit,
    onPilihModel: (String) -> Unit,
    onMuatModel: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
    ) {
        Text(
            "Pakai endpoint dan kunci milikmu sendiri. KodeNusa tidak punya server AI dan tidak " +
                "mengirim apa pun sebelum kamu mengisi ini.",
            style = MaterialTheme.typography.bodySmall,
            color = Kode.textDim,
        )
        Spacer(Modifier.height(12.dp))
        IsianAi("Alamat endpoint", endpoint, onEndpoint, "https://api.crown.ai.id/v1", rahasia = false)
        Spacer(Modifier.height(8.dp))
        IsianAi("Kunci API", kunci, onKunci, "tempel kuncimu di sini", rahasia = true)
        Spacer(Modifier.height(6.dp))
        Text(
            "Yang diketik langsung tersimpan — boleh ditutup lalu dibuka lagi.",
            style = MaterialTheme.typography.labelSmall,
            color = Kode.textDim,
        )
        Spacer(Modifier.height(10.dp))
        PrimaryButton(
            if (memuat) "Memuat model…" else "Muat model dari endpoint",
            onMuatModel,
            enabled = !memuat && endpoint.isNotBlank() && kunci.isNotBlank(),
            modifier = Modifier.testTag("muat-model"),
        )
        if (modelList.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Text("Pilih model", style = MaterialTheme.typography.titleSmall, color = Kode.text)
            Spacer(Modifier.height(6.dp))
            modelList.forEach { model ->
                Panel(
                    Modifier.fillMaxWidth().padding(bottom = 6.dp).clickable { onPilihModel(model.id) },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (model.id == modelTerpilih) "● " else "○ ",
                            color = if (model.id == modelTerpilih) Kode.accent else Kode.textDim,
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                model.label(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Kode.text,
                            )
                            Text(
                                model.id,
                                style = MaterialTheme.typography.labelSmall,
                                color = Kode.textDim,
                            )
                        }
                    }
                }
            }
        }
        galat?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = Kode.error)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun IsianAi(
    label: String,
    nilai: String,
    onNilai: (String) -> Unit,
    contoh: String,
    rahasia: Boolean,
) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Kode.textDim)
        Spacer(Modifier.height(4.dp))
        TextField(
            value = nilai,
            onValueChange = onNilai,
            singleLine = true,
            placeholder = { Text(contoh, color = Kode.textDim) },
            visualTransformation = if (rahasia) {
                PasswordVisualTransformation()
            } else {
                VisualTransformation.None
            },
            modifier = Modifier.fillMaxWidth().testTag(label),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Kode.surfaceRaised,
                unfocusedContainerColor = Kode.surfaceRaised,
                focusedTextColor = Kode.text,
                unfocusedTextColor = Kode.text,
            ),
        )
    }
}

@Composable
private fun ChatAiPanel(
    model: String,
    riwayat: List<Pair<String, String>>,
    jawaban: String,
    berpikir: Boolean,
    sibuk: Boolean,
    galat: String?,
    adaKonteksBerkas: Boolean,
    adaGalat: Boolean,
    pertanyaan: String,
    onPertanyaan: (String) -> Unit,
    onKirim: () -> Unit,
    onCepat: (String) -> Unit,
    onHentikan: () -> Unit,
    onBersihkan: () -> Unit,
    onSisip: (String, String) -> Unit,
    adaGambar: Boolean = false,
    memuatGambar: Boolean = false,
    onLampirkan: () -> Unit = {},
    onFoto: () -> Unit = {},
    onBuangGambar: () -> Unit = {},
    adaKonteksProjek: Boolean = false,
    berkasSematan: List<String> = emptyList(),
    hasilTulis: String? = null,
    karakter: Int = 0,
    onKonteksProjek: () -> Unit = {},
    onPilihBerkas: () -> Unit = {},
    onBuangSematan: (String) -> Unit = {},
    onTulisProjek: () -> Unit = {},
    onBatalkanTulis: () -> Unit = {},
    adaTulisanBisaDibatalkan: Boolean = false,
    onBuatBerkasBaru: () -> Unit = {},
) {
    val daftar = rememberLazyListState()
    val terakhir = jawaban.ifBlank { riwayat.lastOrNull { it.first == "assistant" }?.second.orEmpty() }
    val blok = CodeBlocks.terbesar(terakhir)

    // Gulir ke bawah setiap ada potongan baru, supaya tulisan yang sedang mengalir tetap terlihat.
    LaunchedEffect(jawaban.length, riwayat.size) {
        if (riwayat.isNotEmpty() || jawaban.isNotEmpty()) {
            daftar.animateScrollToItem((riwayat.size + 1).coerceAtLeast(0))
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "model: $model",
                    style = MaterialTheme.typography.labelSmall,
                    color = Kode.textDim,
                    maxLines = 1,
                )
                // Indikator biaya jujur: pengguna membayar kuncinya sendiri, jadi jumlah karakter
                // yang sudah dikirim ditampilkan — bukan token yang dikarang, tapi angka nyata.
                if (karakter > 0) {
                    Text(
                        "terkirim: $karakter karakter ke endpoint",
                        style = MaterialTheme.typography.labelSmall,
                        color = Kode.textDim,
                        modifier = Modifier.testTag("indikator-karakter"),
                    )
                }
            }
            if (riwayat.isNotEmpty()) GhostButton("Bersihkan", onBersihkan)
        }

        if (riwayat.isEmpty() && jawaban.isEmpty()) {
            Spacer(Modifier.height(10.dp))
            // Satu baris saran yang bisa digulir: pengguna HP sering bingung mau ngetik apa.
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (adaGalat) {
                    GhostButton("Perbaiki galat build", {
                        onCepat("Jelaskan penyebab galat build ini, lalu beri isi berkas yang sudah diperbaiki lengkap.")
                    })
                }
                if (adaKonteksBerkas) {
                    GhostButton("Perbaiki berkas ini", {
                        onCepat("Perbaiki berkas ini dan jelaskan dalam satu kalimat apa yang salah.")
                    })
                    GhostButton("Jelaskan berkas ini", {
                        onCepat("Jelaskan isi berkas ini baris demi baris dalam bahasa Indonesia sederhana.")
                    })
                }
                GhostButton("Buat Activity", {
                    onCepat("Buat Activity Android baru lengkap dengan layout XML untuk Java 8 tanpa AndroidX.")
                })
                GhostButton("Buat layout XML", {
                    onCepat("Buat berkas layout XML baru bernama activity_layar.xml: satu LinearLayout berisi judul dan tombol, untuk Java 8 tanpa AndroidX.")
                })
                GhostButton("Tambah layar kedua", {
                    onCepat("Tambahkan layar kedua: buat berkas LayarKedua.java beserta layout XML-nya, dan tunjukkan cara membukanya dari MainActivity.")
                })
                GhostButton("Bantu itu apa", { onCepat("Apa saja yang bisa kamu bantu di KodeNusa?") })
            }
        }

        LazyColumn(state = daftar, modifier = Modifier.weight(1f).padding(top = 8.dp)) {
            items(riwayat) { (peran, isi) -> PesanAi(peran, isi) }
            if (jawaban.isNotBlank() || sibuk) {
                item { PesanAi("assistant", jawaban.ifBlank { if (berpikir) "…" else "" }) }
            }
            galat?.let { pesan ->
                item {
                    Text(
                        pesan,
                        style = MaterialTheme.typography.bodySmall,
                        color = Kode.error,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            }
        }

        blok?.let { kode ->
            PrimaryButton(
                "Masukkan kode ke berkas (${kode.isi.lines().size} baris)",
                { onSisip(kode.isi, kode.bahasa) },
                modifier = Modifier.fillMaxWidth().testTag("sisip-kode"),
            )
            Spacer(Modifier.height(6.dp))
            GhostButton(
                "Buat berkas baru dari kode",
                onBuatBerkasBaru,
                modifier = Modifier.fillMaxWidth().testTag("buat-berkas-dari-kode"),
            )
            Spacer(Modifier.height(6.dp))
        }

        if (terakhir.contains("<<<BERKAS:")) {
            PrimaryButton(
                "Simpan berkas dari jawaban ini ke projek",
                onTulisProjek,
                modifier = Modifier.fillMaxWidth().testTag("tulis-ke-projek"),
            )
            Spacer(Modifier.height(6.dp))
        }

        if (adaTulisanBisaDibatalkan) {
            GhostButton(
                "Batalkan tulisan AI",
                onBatalkanTulis,
                modifier = Modifier.fillMaxWidth().testTag("batalkan-tulis"),
            )
            Spacer(Modifier.height(6.dp))
        }

        hasilTulis?.let { pesan ->
            Text(
                pesan,
                style = MaterialTheme.typography.bodySmall,
                color = Kode.ok,
                modifier = Modifier.testTag("hasil-tulis"),
            )
            Spacer(Modifier.height(6.dp))
        }

        // Satu baris chip yang bisa digulir: menghemat dua baris vertikal di layar 5 inci,
        // dan urutannya tetap konteks dulu, lalu gambar — sama seperti alur berpikir pengguna.
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).testTag("baris-chip-ai"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            GhostButton(
                if (adaKonteksProjek) "Konteks projek ✓" else "Konteks projek + bikin berkas",
                onKonteksProjek,
                modifier = Modifier.testTag("konteks-projek"),
            )
            GhostButton(
                "Pilih berkas…",
                onPilihBerkas,
                modifier = Modifier.testTag("pilih-berkas-ai"),
            )
            berkasSematan.forEach { jalur ->
                val nama = jalur.substringAfterLast('/')
                Pill(
                    nama,
                    Kode.info,
                    modifier = Modifier.testTag("berkas-terpasang-$nama"),
                )
                GhostButton("Buang", { onBuangSematan(jalur) }, Modifier.testTag("buang-sematan-$nama"))
            }
            GhostButton(
                if (memuatGambar) "Menyiapkan gambar…" else "Lampirkan gambar",
                onLampirkan,
                modifier = Modifier.testTag("lampirkan-gambar"),
                enabled = !memuatGambar,
            )
            GhostButton(
                "Foto",
                onFoto,
                modifier = Modifier.testTag("foto-kamera"),
                enabled = !memuatGambar,
            )
            if (adaGambar) {
                Pill(
                    "1 gambar siap",
                    Kode.ok,
                    modifier = Modifier.testTag("gambar-terpasang"),
                )
                GhostButton("Buang", onBuangGambar)
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            TextField(
                value = pertanyaan,
                onValueChange = onPertanyaan,
                maxLines = 3,
                placeholder = { Text("Tulis permintaan…", color = Kode.textDim) },
                modifier = Modifier.weight(1f).testTag("tanya-ai"),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Kode.surfaceRaised,
                    unfocusedContainerColor = Kode.surfaceRaised,
                    focusedTextColor = Kode.text,
                    unfocusedTextColor = Kode.text,
                ),
            )
            if (sibuk) GhostButton("Hentikan", onHentikan) else PrimaryButton("Kirim", onKirim)
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun PesanAi(peran: String, isi: String) {
    val pengguna = peran == "user"
    Surface(
        color = if (pengguna) Kode.surfaceRaised else Kode.surface,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
    ) {
        Column(Modifier.padding(10.dp)) {
            Text(
                if (pengguna) "kamu" else "AI",
                style = MaterialTheme.typography.labelSmall,
                color = if (pengguna) Kode.textDim else Kode.accent,
            )
            Spacer(Modifier.height(4.dp))
            // Penjelasan ditampilkan tanpa pagar kode; isi kodenya muncul di tombol sisip di bawah.
            Text(
                if (pengguna) isi else CodeBlocks.tanpaPagar(isi).take(3_000),
                style = MaterialTheme.typography.bodySmall,
                color = Kode.text,
            )
        }
    }
}

/** Satu berkas yang akan ditulis AI: apakah berkas baru, atau menimpa yang sudah ada. */
private data class RencanaTulis(
    val edit: id.kodenusa.core.ai.BerkasEdit,
    val timpa: Boolean,
    val ukuranLama: Long,
)

/**
 * Pratinjau sebelum AI menulis berkas: pengguna melihat berkas mana yang baru dan mana yang
 * akan ditimpa (dengan ukuran lamanya), lalu memutuskan. Berkas yang ditimpa tetap
 * dicadangkan otomatis dan bisa dibatalkan lewat tombol "Batalkan tulisan AI".
 */
@Composable
private fun DialogKonfirmasiTulis(
    rencana: List<RencanaTulis>,
    onBatal: () -> Unit,
    onSetuju: () -> Unit,
) {
    Dialog(onDismissRequest = onBatal) {
        Surface(
            color = Kode.surface,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "AI akan menulis ${rencana.size} berkas",
                    style = MaterialTheme.typography.titleMedium,
                    color = Kode.text,
                )
                Text(
                    "Berkas yang ditimpa dicadangkan dulu dan bisa dibatalkan.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Kode.textDim,
                )
                Spacer(Modifier.height(8.dp))
                rencana.forEach { satu ->
                    Text(
                        buildString {
                            append(if (satu.timpa) "timpa " else "baru  ")
                            append(satu.edit.jalur)
                            if (satu.timpa) {
                                append(" (")
                                append(satu.ukuranLama)
                                append(" B → ")
                                append(satu.edit.isi.toByteArray().size)
                                append(" B)")
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (satu.timpa) Kode.warn else Kode.ok,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .testTag("rencana-" + satu.edit.jalur),
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GhostButton("Batal", onBatal, Modifier.testTag("tulis-batal"))
                    PrimaryButton(
                        "Simpan",
                        onSetuju,
                        modifier = Modifier.weight(1f).testTag("tulis-setuju"),
                    )
                }
            }
        }
    }
}
