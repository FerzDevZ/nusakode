package id.kodenusa.app

import android.app.ActivityManager
import android.content.Context
import android.os.Build

/**
 * Batas kerja yang diturunkan saat HP termasuk kecil (RAM ≤ 3,5 GB atau ditandai low-RAM oleh sistem).
 *
 * Kenapa perlu: di HP 4 GB, teks log build dan sorotan sintaks adalah dua hal yang paling cepat
 * memakan memori tanpa menambah kemampuan. Keduanya bisa dikurangi tanpa menghilangkan fitur.
 */
data class BatasHemat(
    val hemat: Boolean,
    val batasLog: Int,
    val sorotanBerkasBesarByte: Long,
)

object ModeHemat {

    const val LOG_NORMAL = 2_000
    const val LOG_HEMAT = 600

    /** Berkas di atas ukuran ini tidak disorot saat mode hemat (pola regex pada teks besar mahal). */
    const val SOROTAN_NORMAL_BYTE = 400_000L
    const val SOROTAN_HEMAT_BYTE = 120_000L

    /** Ambang RAM total: HP "4 GB" melaporkan 3,6–3,9 GB, jadi batasnya 4,2 GB. */
    const val AMBANG_RAM_BYTE = 4_200_000_000L

    fun aktif(aktivitas: ActivityManager?, ramTotalByte: Long): Boolean {
        val bertandaLowRam = aktivitas?.isLowRamDevice == true
        return bertandaLowRam || ramTotalByte in 1..AMBANG_RAM_BYTE
    }

    fun batasUntuk(hemat: Boolean): BatasHemat = if (hemat) {
        BatasHemat(true, LOG_HEMAT, SOROTAN_HEMAT_BYTE)
    } else {
        BatasHemat(false, LOG_NORMAL, SOROTAN_NORMAL_BYTE)
    }

    /** Pintasan untuk pemakaian di layar: deteksi sendiri dari konteks aplikasi. */
    fun untukKonteks(konteks: Context): BatasHemat {
        val aktivitas = konteks.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val info = ActivityManager.MemoryInfo()
        aktivitas?.getMemoryInfo(info)
        return batasUntuk(aktif(aktivitas, info.totalMem))
    }

    /** Nama pendek untuk ditampilkan di layar (kosong kalau mode hemat tidak aktif). */
    fun label(batas: BatasHemat): String = if (batas.hemat) "mode hemat · RAM ${ramRingkas()}" else ""

    private fun ramRingkas(): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return "kecil"
        return "≤ 4 GB"
    }
}
