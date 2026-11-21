package id.kodenusa.app

import id.kodenusa.core.build.Tier0Config
import java.io.File

/**
 * Logika murni ikon aplikasi hasil build (docs/14 2.2) — tanpa android.* supaya bisa diuji di JVM.
 * Menggambar PNG-nya ada di [PembuatIkon].
 */
object IkonAplikasi {

    /** Warna bawaan bila kodenusa.json tidak menentukan warnaIkon: teal bengkel KodeNusa. */
    const val WARNA_BAWAAN = "#0E7490"

    /** Huruf latar ikon; kontras warna teks terhadap latar dijamin >= 4.5:1 (lihat uji). */
    data class WarnaIkon(val hex: String, val label: String)

    /**
     * 8 pilihan warna ikon untuk wizard (docs/14 2.2). Diambil dari palet aplikasi dan diperluas;
     * semua diuji kontrasnya terhadap warna teks hasil [warnaTeks] di [IkonAplikasiTest].
     */
    val PILIHAN_WARNA = listOf(
        WarnaIkon("#0E7490", "Teal"),
        WarnaIkon("#1F2937", "Arang"),
        WarnaIkon("#F7AD30", "Amber"),
        WarnaIkon("#61D19A", "Hijau"),
        WarnaIkon("#6AB8E4", "Biru"),
        WarnaIkon("#E7C952", "Kuning"),
        WarnaIkon("#F75D59", "Merah"),
        WarnaIkon("#EBEFF4", "Kertas"),
    )

    /** Mem-parse warna hex `#RRGGBB`/`#AARRGGBB`; salah format = warna bawaan, bukan crash. */
    fun warnaLatar(hex: String?): Int {
        val teks = hex?.trim().orEmpty()
        val sah = teks.matches(Regex("#[0-9A-Fa-f]{6}")) || teks.matches(Regex("#[0-9A-Fa-f]{8}"))
        return parse(if (sah) teks else WARNA_BAWAAN)
    }

    private fun parse(hex: String): Int {
        val angka = hex.removePrefix("#").toLong(16)
        return if (hex.length == 7) (0xFF000000L or angka).toInt() else angka.toInt()
    }

    /** Huruf awal nama aplikasi; nama kosong atau simbol = "A" (tidak pernah kosong). */
    fun hurufAwal(nama: String): String =
        nama.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "A"

    /** Luminansi relatif WCAG (kompresi gamma sRGB diperhitungkan). */
    fun luminansi(warna: Int): Double {
        fun lin(c: Double): Double =
            if (c <= 0.04045) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
        val r = lin(((warna shr 16) and 0xFF) / 255.0)
        val g = lin(((warna shr 8) and 0xFF) / 255.0)
        val b = lin((warna and 0xFF) / 255.0)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    /** Rasio kontras WCAG dua warna ARGB. */
    fun kontras(a: Int, b: Int): Double {
        val terang = maxOf(luminansi(a), luminansi(b))
        val gelap = minOf(luminansi(a), luminansi(b))
        return (terang + 0.05) / (gelap + 0.05)
    }

    private val TEKS_PUTIH = 0xFFFFFFFF.toInt()
    private val TEKS_GELAP = 0xFF1F2937.toInt()

    /** Warna teks huruf ikon: dipilih otomatis warna dengan kontras tertinggi terhadap latar. */
    fun warnaTeks(latar: Int): Int {
        val putih = kontras(latar, TEKS_PUTIH)
        val gelap = kontras(latar, TEKS_GELAP)
        return if (putih >= gelap) TEKS_PUTIH else TEKS_GELAP
    }

    /** true bila manifest belum menunjuk ikon dan perlu ditambah `android:icon`. */
    fun butuhPatch(manifestText: String): Boolean =
        Regex("<application\\b[^>]*>").find(manifestText)?.value?.contains("android:icon") != true

    /**
     * Menambah `android:icon="@mipmap/ic_launcher"` ke tag `<application>`. Idempoten: manifest
     * yang sudah punya ikon dikembalikan apa adanya (Chesterton's fence — pengguna bisa menunjuk
     * ikonnya sendiri, dan itu dihormati).
     */
    fun patch(manifestText: String): String {
        if (!butuhPatch(manifestText)) return manifestText
        return manifestText.replaceFirst("<application", "<application\n        android:icon=\"@mipmap/ic_launcher\"")
    }

    /** Nama berkas resource ikon yang dihasilkan; dipakai uji untuk memeriksa isi APK. */
    const val RES_LEGACY = "res/mipmap/ic_launcher.png"
    const val RES_ADAPTIF = "res/mipmap-anydpi-v26/ic_launcher.xml"
    const val RES_FOREGROUND = "res/drawable/ic_launcher_fg.png"

    /** Memperbarui config: resourceDirs + folder ikon, manifest menunjuk ikon bila belum. */
    fun configDenganIkon(config: Tier0Config, folderRes: File, manifestIkon: File): Tier0Config =
        config.copy(
            resourceDirs = config.resourceDirs + folderRes,
            manifest = manifestIkon,
        )
}
