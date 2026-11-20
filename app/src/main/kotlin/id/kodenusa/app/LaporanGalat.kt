package id.kodenusa.app

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Data lingkungan yang ikut ditulis saat aplikasi berhenti mendadak. */
data class InfoLaporan(
    val versi: String,
    val sdk: Int = Build.VERSION.SDK_INT,
    val perangkat: String = Build.MODEL.orEmpty(),
    val ramMb: Long = 0L,
    val waktu: Long = System.currentTimeMillis(),
    /** Di JVM uji ini kosong: `Build.SUPPORTED_ABIS` hanya ada di perangkat. */
    val abi: String = runCatching { Build.SUPPORTED_ABIS.firstOrNull().orEmpty() }.getOrDefault(""),
)

/**
 * Laporan galat yang disimpan **di dalam HP**, bukan dikirim ke mana pun.
 *
 * Aplikasi ini sengaja tidak punya telemetri, dan tanpa laporan lokal kita tidak akan pernah tahu
 * apa yang rusak di HP orang lain. Karena itu laporan ditulis ke `filesDir/laporan/` saat proses
 * mati, lalu pengguna sendiri yang memutuskan apakah berkasnya dibagikan.
 *
 * Isi laporan **disaring dari rahasia** sebelum ditulis: token GitHub dan kunci AI pernah muncul di
 * jejak tumpukan atau pesan galat, dan laporan yang bocor lebih buruk daripada tidak ada laporan.
 */
object LaporanGalat {

    const val NAMA_BERKAS = "galat-terakhir.txt"
    private const val FOLDER = "laporan"
    private const val MAKS_BARIS_JEJAK = 60

    /** Pola yang isinya tidak boleh keluar dari perangkat. */
    private val POLA_RAHASIA = listOf(
        Regex("ghp_[A-Za-z0-9]{10,}"),
        Regex("github_pat_[A-Za-z0-9_]{10,}"),
        Regex("sk-[A-Za-z0-9_-]{10,}"),
        Regex("AIza[0-9A-Za-z_-]{10,}"),
        Regex("crown-[0-9a-fA-F]{6,}"),
        Regex("x-access-token:[^@\\s]+"),
        Regex("(?i)\\b(token|kunci|api[_-]?key|password|sandi)\\b\\s*[=:]\\s*\\S+"),
    )

    fun folder(context: Context): File = File(context.filesDir, FOLDER)

    fun berkas(context: Context): File = File(folder(context), NAMA_BERKAS)

    /** Mengganti apa pun yang menyerupai rahasia dengan penanda tetap. */
    fun saringRahasia(teks: String): String =
        POLA_RAHASIA.fold(teks) { hasil, pola -> pola.replace(hasil, "<disembunyikan>") }

    /** Teks laporan akhir. Fungsi murni supaya bisa diuji tanpa Android. */
    fun bangun(galat: Throwable, info: InfoLaporan): String {
        val jejak = galat.stackTraceToString()
            .lineSequence()
            .take(MAKS_BARIS_JEJAK)
            .joinToString("\n")
        val waktu = SimpleDateFormat("d MMM yyyy, HH:mm", Locale("in", "ID")).format(Date(info.waktu))
        val teks = buildString {
            appendLine("KodeNusa ${info.versi}")
            appendLine("Waktu: $waktu")
            appendLine("Android SDK ${info.sdk}" + (if (info.abi.isNotBlank()) " · ${info.abi}" else ""))
            appendLine("Perangkat: ${info.perangkat.ifBlank { "tidak diketahui" }}")
            if (info.ramMb > 0) appendLine("RAM terpasang: ${info.ramMb} MB")
            appendLine()
            appendLine("Galat: ${galat.javaClass.name}: ${galat.message.orEmpty()}")
            appendLine()
            appendLine("Jejak tumpukan:")
            append(jejak)
        }
        return saringRahasia(teks)
    }

    /** Menulis laporan. Tidak pernah melempar: kalau gagal, proses tetap harus mati seperti biasa. */
    fun tulis(context: Context, galat: Throwable, info: InfoLaporan): File? = runCatching {
        val tujuan = berkas(context)
        tujuan.parentFile?.mkdirs()
        tujuan.writeText(bangun(galat, info))
        tujuan
    }.getOrNull()

    fun baca(context: Context): String? = runCatching {
        berkas(context).takeIf { it.isFile }?.readText()?.takeIf { it.isNotBlank() }
    }.getOrNull()

    fun hapus(context: Context) {
        runCatching { berkas(context).delete() }
    }

    fun info(context: Context, versi: String): InfoLaporan {
        val ramMb = runCatching {
            val manajer = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memori = ActivityManager.MemoryInfo()
            manajer.getMemoryInfo(memori)
            memori.totalMem / (1024 * 1024)
        }.getOrDefault(0L)
        return InfoLaporan(versi = versi, ramMb = ramMb)
    }
}
