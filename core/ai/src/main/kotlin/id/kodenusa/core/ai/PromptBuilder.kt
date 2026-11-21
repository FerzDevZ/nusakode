package id.kodenusa.core.ai

/**
 * Penyusun pesan untuk asisten kode KodeNusa.
 *
 * Semua instruksi ditulis bahasa Indonesia dan menyebut batas teknis yang nyata: projek Tier 0 hanya
 * Java 8, `minSdk 24`, tanpa Gradle dan tanpa library AndroidX. Tanpa batas ini, model akan dengan
 * senang hati menulis Kotlin atau `androidx.*` yang pasti gagal di-build di HP pengguna.
 */
object PromptBuilder {

    /** Batas aman isi berkas yang dikirim; sisanya dipotong dengan penanda yang jelas. */
    const val BATAS_KONTEKS = 5_000

    /** Batas saat mode hemat kuota (docs/13 C5): setengah ukuran normal. */
    const val BATAS_KONTEKS_HEMAT = 2_500

    const val SISTEM: String = "Kamu asisten koding di dalam KodeNusa, IDE Android untuk HP kelas " +
        "bawah di Indonesia. Pengguna memegang HP, bukan komputer, jadi jawaban harus bisa langsung " +
        "dipakai.\n\n" +
        "Aturan wajib:\n" +
        "1. Jawab dalam bahasa Indonesia yang sederhana. Jangan bertele-tele.\n" +
        "2. Kode selalu di dalam blok berpagar (```java ... ```). Sebut nama berkasnya di atas blok.\n" +
        "3. Projek ini di-build tanpa Gradle: hanya Java 8, minSdk 24, tanpa AndroidX, tanpa Kotlin, " +
        "tanpa library pihak ketiga. Jangan pernah menyarankan Kotlin, Jetpack Compose, Gradle, atau " +
        "dependensi baru. Gunakan API framework Android yang ada sejak API 24.\n" +
        "4. Kalau memperbaiki galat, jelaskan penyebabnya dalam satu kalimat, lalu beri kode lengkap " +
        "berkas yang sudah diperbaiki (bukan potongan yang membingungkan).\n" +
        "5. Kalau permintaan tidak mungkin di Tier 0 (mis. butuh Kotlin), katakan terus terang dan " +
        "tawarkan jalan Java yang setara.\n" +
        "6. Jangan mengarang nama berkas projek. Kalau tidak tahu, tanya."

    fun pesanSistem(): ChatMessage = ChatMessage(ChatMessage.Peran.SISTEM, SISTEM)

    /** Menjelaskan galat build yang sudah diterjemahkan KodeNusa. */
    fun untukGalat(galat: String, berkas: String?, isiBerkas: String?): List<ChatMessage> = buildList {
        add(pesanSistem())
        val konteks = StringBuilder("Build APK gagal. Ini pesan galatnya:\n\n")
        konteks.append(potong(galat, 3_000))
        if (berkas != null && isiBerkas != null) {
            konteks.append("\n\nBerkas yang paling mungkin salah: ").append(berkas).append("\n```java\n")
            konteks.append(potong(isiBerkas, 3_000)).append("\n```")
        }
        konteks.append("\n\nJelaskan penyebabnya dalam bahasa Indonesia sederhana, lalu beri isi berkas ")
        konteks.append("yang sudah diperbaiki secara lengkap.")
        add(ChatMessage(ChatMessage.Peran.PENGGUNA, konteks.toString()))
    }

    /** Menjelaskan atau mengubah satu berkas. */
    fun untukBerkas(
        permintaan: String,
        jalur: String,
        isiBerkas: String,
        hemat: Boolean = false,
    ): List<ChatMessage> = buildList {
        add(pesanSistem())
        val konteks = StringBuilder("Berkas: ").append(jalur).append("\n```\n")
        konteks.append(potong(isiBerkas, if (hemat) BATAS_KONTEKS_HEMAT else BATAS_KONTEKS)).append("\n```\n\n")
        konteks.append("Permintaan pengguna: ").append(permintaan.trim())
        add(ChatMessage(ChatMessage.Peran.PENGGUNA, konteks.toString()))
    }

    /**
     * Berkas yang disematkan pengguna (maksimal 3) dikirim utuh — bukan ringkasan 2.400 karakter.
     * Ringkasan memotong bagian penting berkas panjang; sematan memberi model isi aslinya.
     */
    fun untukBerkasSematan(
        permintaan: String,
        berkas: List<Pair<String, String>>,
        hemat: Boolean = false,
    ): List<ChatMessage> = buildList {
        add(pesanSistem())
        // Mode hemat kuota: maksimal 2 berkas disematkan (docs/13 C5) — kuota terbatas lebih
        // penting daripada konteks berkas ketiga yang mungkin tidak dipakai model.
        val batasJumlah = if (hemat) 2 else 3
        val konteks = StringBuilder("Berkas yang disematkan pengguna (isi utuh):\n")
        berkas.take(batasJumlah).forEach { (jalur, isi) ->
            konteks.append("\n--- ").append(jalur).append(" ---\n")
            konteks.append(potong(isi, if (hemat) BATAS_KONTEKS_HEMAT else BATAS_KONTEKS)).append('\n')
        }
        konteks.append("\nPermintaan pengguna: ").append(permintaan.trim())
        add(ChatMessage(ChatMessage.Peran.PENGGUNA, konteks.toString()))
    }

    /**
     * Perintah untuk agen yang boleh mengubah beberapa berkas lalu di-build.
     *
     * Format penanda ditulis eksplisit karena model yang lebih kecil sering lupa memberi nama
     * berkas kalau hanya diminta "kirim kodenya". Tanpa nama berkas, perubahan tidak bisa
     * diterapkan otomatis dan pengguna harus menyalin manual — persis yang ingin dihapus fitur ini.
     */
    fun untukAgen(
        tugas: String,
        konteksProjek: String,
        galatSebelumnya: String,
        templat: String? = null,
        hemat: Boolean = false,
    ): List<ChatMessage> =
        buildList {
            add(pesanSistem())
            add(ChatMessage(ChatMessage.Peran.SISTEM, ATURAN_AGEN))
            val isi = StringBuilder("Tugas dari pengguna:\n")
            isi.append(tugas.trim()).append("\n\nKeadaan projek sekarang:\n")
            isi.append(potong(konteksProjek, if (hemat) BATAS_KONTEKS_HEMAT else BATAS_KONTEKS))
            // Struktur tipikal template membantu model mengikuti pola kelas yang sudah ada
            // (saran #7 di docs/12) — tanpa ini model menebak struktur dari nama berkas saja.
            if (templat != null) isi.append("\n\n").append(templat)
            if (galatSebelumnya.isNotBlank()) {
                isi.append("\n\nBuild sebelumnya gagal dengan galat ini:\n")
                isi.append(potong(galatSebelumnya, 2_000))
                isi.append("\nPerbaiki penyebabnya sekarang.")
            }
            add(ChatMessage(ChatMessage.Peran.PENGGUNA, isi.toString()))
        }

    /**
     * Struktur tipikal tiap template projek (saran #7 docs/12). Dikirim ke model supaya ia
     * mengikuti pola kelas yang sudah ada, bukan menebak. `null` berarti template tidak dikenal —
     * tidak ada blok tambahan, dan jangan mengarang struktur.
     */
    fun templatProjek(id: String?): String? = when (id) {
        "halo-dunia" ->
            "Struktur template halo-dunia: satu Activity di src/main/java/app/MainActivity.java, " +
                "layout res/layout/activity_main.xml, dan teks di res/values/strings.xml. " +
                "Ikuti pola yang sudah ada."
        "kasir-warung" ->
            "Struktur template kasir-warung: MainActivity.java (layar UI), DatabaseKasir.java " +
                "(SQLiteOpenHelper dengan tabel produk), Produk.java (model data), dan layout " +
                "res/layout/activity_main.xml. Ikuti pola kelas yang sudah ada — jangan membuat " +
                "basis data baru kalau bisa memakai DatabaseKasir."
        "katalog-whatsapp" ->
            "Struktur template katalog-whatsapp: MainActivity.java (layar daftar katalog) dan layout " +
                "res/layout/activity_main.xml. Ikuti pola yang sudah ada."
        "absensi-kelas" ->
            "Struktur template absensi-kelas: MainActivity.java (layar absensi), DatabaseAbsensi.java " +
                "(SQLiteOpenHelper dengan tabel absensi: nama, hadir, waktu), dan layout " +
                "res/layout/activity_main.xml. Ikuti pola kelas yang sudah ada — jangan membuat " +
                "basis data baru kalau bisa memakai DatabaseAbsensi."
        "laporan-warung" ->
            "Struktur template laporan-warung: MainActivity.java (layar catatan uang masuk/keluar), " +
                "DatabaseLaporan.java (SQLiteOpenHelper dengan tabel catatan: keterangan, jumlah, " +
                "masuk, waktu), Csv.java (pembuat CSV RFC 4180 untuk tombol Ekspor CSV), dan layout " +
                "res/layout/activity_main.xml. Ikuti pola kelas yang " +
                "sudah ada — jangan membuat basis data baru kalau bisa memakai DatabaseLaporan."
        "jurnal-harian" ->
            "Struktur template jurnal-harian: MainActivity.java (layar catatan + mood 1-5), " +
                "DatabaseJurnal.java (SQLiteOpenHelper dengan tabel catatan: tanggal, isi, mood), " +
                "dan layout res/layout/activity_main.xml. Ikuti pola kelas yang sudah ada — jangan " +
                "membuat basis data baru kalau bisa memakai DatabaseJurnal."
        "stok-barang" ->
            "Struktur template stok-barang: MainActivity.java (layar barang masuk/keluar), " +
                "DatabaseStok.java (SQLiteOpenHelper dengan tabel barang: nama, stok, minimum), " +
                "dan layout res/layout/activity_main.xml. Ikuti pola kelas yang sudah ada — jangan " +
                "membuat basis data baru kalau bisa memakai DatabaseStok."
        "kuis-pelajaran" ->
            "Struktur template kuis-pelajaran: MainActivity.java (layar kuis dari assets/soal.json), " +
                "DatabaseKuis.java (SQLiteOpenHelper dengan tabel riwayat: skor, total, waktu), " +
                "dan layout res/layout/activity_main.xml. Soal baru cukup ditambah ke soal.json, " +
                "bukan mengubah kode Java."
        "web-katalog" ->
            "Struktur template web-katalog: WebView di src/main/java/app/MainActivity.java memuat " +
                "halaman di src/main/assets/ (index.html, gaya.css, aplikasi.js). Ubah isi web di " +
                "folder assets, bukan di Java, kalau perubahan hanya tampilan."
        "situs-murni" ->
            "Projek web murni tanpa Activity: web/index.html, web/gaya.css, web/aplikasi.js. " +
                "Tidak ada kode Java dan tidak ada build APK di projek ini."
        "kosong" ->
            "Projek kosong: satu MainActivity.java di src/main/java/<paket>/, manifest Android, " +
                "layout res/layout/activity_main.xml, dan res/values/strings.xml."
        else -> null
    }

    /** Aturan format yang dipakai [UbahanBerkas] untuk menerapkan perubahan otomatis. */
    const val ATURAN_AGEN: String = "Kamu mengubah projek secara langsung. Untuk SETIAP berkas yang " +
        "berubah atau BARU, tulis persis seperti ini:\n" +
        "<<<BERKAS: jalur/relatif/NamaBerkas.java>>>\n" +
        "```java\n" +
        "isi berkas lengkap dari awal sampai akhir\n" +
        "```\n" +
        "Syarat: jalur relatif tanpa garis miring di depan dan tanpa '..'; isi harus lengkap, bukan " +
        "potongan; jangan pernah menulis penanda untuk berkas yang tidak berubah; jangan memakai " +
        "Kotlin, Gradle, AndroidX, atau library pihak ketiga. Membuat berkas BARU diperbolehkan bila " +
        "tugasnya membutuhkan: tulis jalur lengkap termasuk foldernya (mis. " +
        "src/main/java/app/LayarKedua.java) dan aplikasi akan membuat foldernya otomatis. Kalau " +
        "tugas tidak mungkin di Tier 0, tulis penjelasannya tanpa penanda berkas sama sekali."

    /** Pertanyaan bebas tanpa berkas terbuka. */
    fun untukTanya(pertanyaan: String): List<ChatMessage> = listOf(
        pesanSistem(),
        ChatMessage(ChatMessage.Peran.PENGGUNA, pertanyaan.trim()),
    )

    /**
     * Prompt perancang template baru dari deskripsi pengguna (docs/14 4.3). Keluarannya dipakai
     * sebagai berkas template di "template saya" — bukan projek langsung — supaya hasilnya
     * melewati pratinjau tulis (ADR-038) dan diganti namanya saat dibuat.
     */
    fun untukScaffold(deskripsi: String): List<ChatMessage> = listOf(
        pesanSistem(),
        ChatMessage(ChatMessage.Peran.SISTEM, ATURAN_AGEN),
        ChatMessage(
            ChatMessage.Peran.PENGGUNA,
            "Rancangkan SATU template aplikasi Android lengkap dari deskripsi ini:\n" +
                deskripsi.trim() + "\n\n" +
                "Wajib mengirim semua berkas berikut dengan penanda <<<BERKAS: ...>>> (isi lengkap):\n" +
                "1. kodenusa.json — format persis seperti contoh ini, ganti nilai deskripsi saja:\n" +
                "   {\"format\":1,\"name\":\"__APP_NAME__\",\"applicationId\":\"__APP_ID__\"," +
                "\"versionName\":\"1.0.0\",\"versionCode\":1,\"minSdk\":24,\"targetSdk\":34," +
                "\"javaVersion\":8,\"buildEngine\":\"tier0\"}\n" +
                "2. src/main/AndroidManifest.xml — seperti template lain: package=\"app\", satu activity" +
                " dengan android:name=\".MainActivity\" android:exported=\"true\".\n" +
                "3. src/main/java/app/MainActivity.java — Activity biasa, setContentView(R.layout.activity_main).\n" +
                "4. src/main/res/layout/activity_main.xml — LinearLayout vertikal, id jelas untuk tiap" +
                " elemen yang dipakai kode.\n" +
                "5. src/main/res/values/strings.xml — minimal <string name=\"app_name\">__APP_NAME__</string>.\n" +
                "6. Boleh menambah berkas lain (mis. Database...java) bila deskripsinya membutuhkan.\n\n" +
                "Aturan keras:\n" +
                "- Ganti teks judul dan label dengan __APP_NAME__ bila itu nama aplikasi; jangan mengarang nama lain.\n" +
                "- Java 8 saja, tanpa AndroidX, tanpa Kotlin, tanpa library pihak ketiga (API framework sejak API 24).\n" +
                "- Import __APP_ID__.R di berkas java (id aplikasi diganti saat projek dibuat).\n" +
                "- Jangan tulis penjelasan di luar berkas; hanya penanda <<<BERKAS: ...>>> + blok berpagar.",
        ),
    )

    /**
     * Memotong teks panjang dengan penanda di tengah, bukan membuang ekornya diam-diam: model perlu
     * tahu bahwa ada bagian yang tidak dikirim supaya tidak "menebak" isi yang hilang.
     */
    fun potong(teks: String, batas: Int): String {
        if (teks.length <= batas) return teks
        val kepala = batas * 2 / 3
        val ekor = batas - kepala
        val dibuang = teks.length - kepala - ekor
        val catatan = "\n… [$dibuang karakter dipotong agar hemat kuota] …\n"
        return teks.take(kepala) + catatan + teks.takeLast(ekor)
    }
}

/** Satu blok kode berpagar dari jawaban model. */
data class BlokKode(val bahasa: String, val isi: String)

/** Utilitas mengambil kode dari jawaban model, supaya bisa dimasukkan ke editor sekali tekan. */
object CodeBlocks {

    private val PAGAR = Regex("```([A-Za-z0-9+#._-]*)\\n(.*?)```", RegexOption.DOT_MATCHES_ALL)

    fun ambil(jawaban: String): List<BlokKode> = PAGAR.findAll(jawaban).map { cocok ->
        BlokKode(
            bahasa = cocok.groupValues[1].lowercase(),
            isi = cocok.groupValues[2].trimEnd('\n'),
        )
    }.filter { it.isi.isNotBlank() }.toList()

    /** Blok terpanjang — biasanya itulah berkas utuh yang dimaksud, bukan potongan contoh. */
    fun terbesar(jawaban: String): BlokKode? = ambil(jawaban).maxByOrNull { it.isi.length }

    /** Membuang pagar kode supaya teks penjelasan bisa dibaca tanpa gangguan. */
    fun tanpaPagar(jawaban: String): String = PAGAR.replace(jawaban, "").trim()
}
