package id.kodenusa.app

/**
 * Cara aplikasi menyandikan nilai rahasia (kunci API AI, token GitHub).
 *
 * Dipisah sebagai antarmuka supaya penyimpanan bisa diuji tanpa perangkat: uji JVM memakai
 * [PenyandiPolos], sedangkan di HP dipakai [KunciAman] (Android Keystore).
 */
interface Penyandi {

    /** Mengembalikan teks tersandi. Hasil **tidak boleh** memuat teks asli. */
    fun enkripsi(teks: String): String

    /** Mengembalikan teks asli. Nilai lama yang belum tersandi dikembalikan apa adanya. */
    fun dekripsi(disandi: String): String
}

/** Penyandi identitas: hanya untuk uji. Tidak dipakai di aplikasi yang terpasang. */
object PenyandiPolos : Penyandi {
    override fun enkripsi(teks: String): String = teks
    override fun dekripsi(disandi: String): String = disandi
}
