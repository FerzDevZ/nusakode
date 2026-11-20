package id.kodenusa.core.ai

/**
 * Setelan sambungan AI milik pengguna sendiri (BYOK — bring your own key).
 *
 * Aplikasi tidak menyimpan kunci apa pun dari pembuatnya, tidak punya server perantara, dan tidak
 * mengirim data ke mana pun sebelum pengguna memasang endpoint + kunci. Ini yang membuat janji
 * "offline" tetap jujur: tanpa setelan, tidak ada satu byte pun yang keluar dari HP.
 */
data class AiConfig(
    /** Alamat dasar, mis. `https://api.crown.ai.id/v1` (boleh dengan atau tanpa garis miring akhir). */
    val baseUrl: String,
    val apiKey: String,
    /** Model yang dipilih pengguna; boleh kosong sebelum daftar model dimuat. */
    val model: String = "",
    val temperature: Double = 0.2,
) {
    val terpasang: Boolean get() = baseUrl.isNotBlank() && apiKey.isNotBlank()

    /** Alamat dasar tanpa garis miring di ujung, supaya penggabungan path tidak menghasilkan `//`. */
    fun dasar(): String = baseUrl.trim().trimEnd('/')

    /** Beberapa penyedia menulis `/v1` di akhir; keduanya harus bekerja. */
    fun url(path: String): String = dasar() + "/" + path.trimStart('/')

    /** Model siap pakai: kalau pengguna belum memilih, pakai yang pertama dari daftar. */
    fun denganModel(id: String): AiConfig = copy(model = id)
}

/** Satu model yang ditawarkan endpoint. */
data class AiModel(val id: String, val pemilik: String = "") {
    /** Nama pendek untuk ditampilkan di layar HP sempit. */
    fun label(): String = id.substringAfterLast('/').ifBlank { id }
}

/** Satu pesan dalam percakapan. */
data class ChatMessage(
    val peran: Peran,
    val isi: String,
    /**
     * Gambar untuk model yang bisa melihat (vision), berupa data URL `data:image/...;base64,...`.
     *
     * Kenapa data URL dan bukan unggahan terpisah: permintaan tetap satu berkas JSON, dan gambar
     * pengguna tidak pernah singgah di server pihak ketiga mana pun selain endpoint pilihannya.
     */
    val gambar: List<String> = emptyList(),
) {
    enum class Peran(val kode: String) { SISTEM("system"), PENGGUNA("user"), ASISTEN("assistant") }
}

/**
 * Semua kegagalan AI diwakili satu tipe ini, dengan pesan bahasa Indonesia yang menyebut
 * langkah perbaikannya. Tidak ada exception mentah yang bocor ke layar pengguna.
 */
class AiGagal(
    val jenis: Jenis,
    val pesan: String,
    val saran: String = "",
    val penyebab: Throwable? = null,
) : Exception(pesan, penyebab) {

    enum class Jenis {
        /** Endpoint/kunci belum diisi. */
        BELUM_DIATUR,
        /** 401/403 — kunci salah atau kedaluwarsa. */
        KUNCI_SALAH,
        /** 404 — alamat dasar tidak mengarah ke API yang benar. */
        ALAMAT_SALAH,
        /** 429 — kuota/permintaan terlalu sering. */
        KUOTA,
        /** 5xx — penyedia sedang bermasalah. */
        PENYEDIA,
        /** Tidak ada internet / DNS gagal. */
        JARINGAN,
        /** Balasan penyedia tidak bisa dibaca (bukan JSON yang diharapkan). */
        BALASAN_ANEH,
        /** Model yang dipilih menolak lampiran gambar. */
        GAMBAR_TIDAK_DIDUKUNG,
        /** Pengguna membatalkan. */
        DIBATALKAN,
    }

    companion object {
        fun kunciSalah(): AiGagal = AiGagal(
            Jenis.KUNCI_SALAH,
            "Kunci API ditolak penyedia (401/403).",
            "Periksa lagi kuncinya di menu AI — pastikan tidak ada spasi atau huruf yang terpotong.",
        )

        fun alamatSalah(url: String): AiGagal = AiGagal(
            Jenis.ALAMAT_SALAH,
            "Alamat endpoint tidak ditemukan (404): $url",
            "Alamat biasanya berakhiran /v1, mis. https://api.crown.ai.id/v1",
        )

        fun kuota(): AiGagal = AiGagal(
            Jenis.KUOTA,
            "Kuota atau batas permintaan tercapai (429).",
            "Tunggu sebentar, atau ganti model lain yang kuotanya masih ada.",
        )

        fun penyedia(kode: Int, isi: String): AiGagal = AiGagal(
            Jenis.PENYEDIA,
            "Penyedia AI sedang bermasalah ($kode).",
            if (isi.isBlank()) "Coba lagi beberapa saat." else "Jawaban penyedia: ${isi.take(200)}",
        )

        fun jaringan(error: Throwable): AiGagal = AiGagal(
            Jenis.JARINGAN,
            "Tidak bisa menghubungi penyedia AI. Periksa sambungan internet.",
            "AI memang butuh internet; semua fitur lain KodeNusa tetap jalan tanpa jaringan.",
            error,
        )

        fun balasanAneh(detail: String): AiGagal = AiGagal(
            Jenis.BALASAN_ANEH,
            "Balasan penyedia tidak bisa dibaca.",
            "Pastikan endpoint memakai format OpenAI (/chat/completions). Detail: ${detail.take(200)}",
        )

        /**
         * 400 yang menyebut gambar: modelnya tidak menerima `image_url`. Model murah (teks saja)
         * memang menolak permintaan bergambar, dan tanpa pesan khusus pengguna akan mengira
         * aplikasinya rusak.
         */
        fun gambarTidakDidukung(isi: String): AiGagal = AiGagal(
            Jenis.GAMBAR_TIDAK_DIDUKUNG,
            "Model yang dipilih tidak bisa membaca gambar.",
            "Kirim pertanyaannya tanpa gambar, atau pilih model yang mendukung gambar " +
                "(mis. gpt-4o, gemini, qwen-vl). Jawaban penyedia: ${isi.take(160)}",
        )

        fun belumDiatur(): AiGagal = AiGagal(
            Jenis.BELUM_DIATUR,
            "Endpoint dan kunci API belum diisi.",
            "Buka menu AI, isi alamat endpoint (mis. https://api.crown.ai.id/v1) dan kuncimu sendiri.",
        )
    }
}
