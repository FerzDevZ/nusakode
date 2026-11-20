package id.kodenusa.ui

/**
 * Palet KodeNusa dalam ARGB. Disimpan sebagai Int (bukan Compose Color) supaya rasio kontrasnya
 * bisa diuji di JVM biasa tanpa emulator. Nilai OKLCH sumbernya ditulis di komentar tiap entri.
 *
 * Semua pasangan teks/latar di bawah lolos WCAG 2.2 AA (>= 4.5:1); [borderStrong] dipakai untuk
 * batas komponen interaktif karena harus >= 3:1 terhadap permukaan tempat ia berada.
 */
object KodenusaPalette {

    // oklch(0.18 0.012 250)
    const val BACKGROUND = 0xFF0E1217.toInt()

    // oklch(0.23 0.014 250)
    const val SURFACE = 0xFF181E23.toInt()

    // oklch(0.28 0.015 250)
    const val SURFACE_RAISED = 0xFF232A30.toInt()

    // oklch(0.42 0.012 250) — hanya pemisah dekoratif, bukan batas komponen.
    const val BORDER = 0xFF484E54.toInt()

    // oklch(0.56 0.012 250) — 3.14:1 terhadap SURFACE_RAISED.
    const val BORDER_STRONG = 0xFF6F757B.toInt()

    // oklch(0.95 0.008 250) — 16.26:1 terhadap BACKGROUND.
    const val TEXT = 0xFFEBEFF4.toInt()

    // oklch(0.72 0.010 250) — 7.59:1 terhadap BACKGROUND.
    const val TEXT_DIM = 0xFFA0A5AB.toInt()

    // oklch(0.80 0.155 75) — aksen amber, 9.85:1 terhadap BACKGROUND.
    const val ACCENT = 0xFFF7AD30.toInt()

    // oklch(0.22 0.05 75) — teks di atas ACCENT, 9.11:1.
    const val ACCENT_INK = 0xFF281600.toInt()

    // oklch(0.78 0.13 160) — 9.91:1 terhadap BACKGROUND.
    const val OK = 0xFF61D19A.toInt()

    // oklch(0.84 0.14 95) — 11.54:1 terhadap BACKGROUND.
    const val WARN = 0xFFE7C952.toInt()

    // oklch(0.68 0.19 25) — 5.98:1 terhadap BACKGROUND.
    const val ERROR = 0xFFF75D59.toInt()

    // oklch(0.75 0.10 235) — 8.60:1 terhadap BACKGROUND.
    const val INFO = 0xFF6AB8E4.toInt()

    /** Sisa dari DESIGN.md bagian tipografi: 1.0 = teks normal, 4.5 = tombol besar. */
    const val RADIUS_SMALL = 8
    const val RADIUS_MEDIUM = 12
    const val RADIUS_LARGE = 18
}
