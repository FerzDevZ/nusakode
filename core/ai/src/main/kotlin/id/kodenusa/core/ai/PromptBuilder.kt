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
    fun untukBerkas(permintaan: String, jalur: String, isiBerkas: String): List<ChatMessage> = buildList {
        add(pesanSistem())
        val konteks = StringBuilder("Berkas: ").append(jalur).append("\n```\n")
        konteks.append(potong(isiBerkas, BATAS_KONTEKS)).append("\n```\n\n")
        konteks.append("Permintaan pengguna: ").append(permintaan.trim())
        add(ChatMessage(ChatMessage.Peran.PENGGUNA, konteks.toString()))
    }

    /**
     * Perintah untuk agen yang boleh mengubah beberapa berkas lalu di-build.
     *
     * Format penanda ditulis eksplisit karena model yang lebih kecil sering lupa memberi nama
     * berkas kalau hanya diminta "kirim kodenya". Tanpa nama berkas, perubahan tidak bisa
     * diterapkan otomatis dan pengguna harus menyalin manual — persis yang ingin dihapus fitur ini.
     */
    fun untukAgen(tugas: String, konteksProjek: String, galatSebelumnya: String): List<ChatMessage> =
        buildList {
            add(pesanSistem())
            add(ChatMessage(ChatMessage.Peran.SISTEM, ATURAN_AGEN))
            val isi = StringBuilder("Tugas dari pengguna:\n")
            isi.append(tugas.trim()).append("\n\nKeadaan projek sekarang:\n")
            isi.append(potong(konteksProjek, BATAS_KONTEKS))
            if (galatSebelumnya.isNotBlank()) {
                isi.append("\n\nBuild sebelumnya gagal dengan galat ini:\n")
                isi.append(potong(galatSebelumnya, 2_000))
                isi.append("\nPerbaiki penyebabnya sekarang.")
            }
            add(ChatMessage(ChatMessage.Peran.PENGGUNA, isi.toString()))
        }

    /** Aturan format yang dipakai [UbahanBerkas] untuk menerapkan perubahan otomatis. */
    const val ATURAN_AGEN: String = "Kamu mengubah projek secara langsung. Untuk SETIAP berkas yang " +
        "berubah, tulis persis seperti ini:\n" +
        "<<<BERKAS: jalur/relatif/NamaBerkas.java>>>\n" +
        "```java\n" +
        "isi berkas lengkap dari awal sampai akhir\n" +
        "```\n" +
        "Syarat: jalur relatif tanpa garis miring di depan dan tanpa '..'; isi harus lengkap, bukan " +
        "potongan; jangan pernah menulis penanda untuk berkas yang tidak berubah; jangan memakai " +
        "Kotlin, Gradle, AndroidX, atau library pihak ketiga. Kalau tugas tidak mungkin di Tier 0, " +
        "tulis penjelasannya tanpa penanda berkas sama sekali."

    /** Pertanyaan bebas tanpa berkas terbuka. */
    fun untukTanya(pertanyaan: String): List<ChatMessage> = listOf(
        pesanSistem(),
        ChatMessage(ChatMessage.Peran.PENGGUNA, pertanyaan.trim()),
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
