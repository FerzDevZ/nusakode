package id.kodenusa.ui

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Perhitungan rasio kontras WCAG 2.2 dari ARGB. Dipakai oleh gerbang impor tema (Kntema) dan
 * oleh ContrastTest — satu sumber kebenaran, bukan dua rumus yang bisa berbeda.
 */
object Kontras {

    private fun channel(argb: Int, shift: Int): Double {
        val raw = ((argb shr shift) and 0xFF) / 255.0
        return if (raw <= 0.04045) raw / 12.92 else ((raw + 0.055) / 1.055).pow(2.4)
    }

    fun luminance(argb: Int): Double =
        0.2126 * channel(argb, 16) + 0.7152 * channel(argb, 8) + 0.0722 * channel(argb, 0)

    fun rasio(a: Int, b: Int): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }
}
