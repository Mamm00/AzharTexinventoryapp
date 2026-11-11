package com.alazhartex.stock.domain.palette

import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin

/**
 * Tiny-palette algorithm, pinned in CONTENT.md (v1.1, delta contract §10.1).
 *
 * PURE + DETERMINISTIC: no Android Context, no Locale, no TimeZone, no clock, no Random.
 * Same inputs -> same packed ARGB on every platform. FNV-1a 32-bit is the pinned stable hash
 * (offset basis 2166136261, prime 16777619) so Kotlin and Python re-derive identical bytes.
 *
 * Family -> hue anchor (pinned, immutable): cotton 0, linen 20, wool 45, silk 90, polyester 150,
 * viscose 190, denim 220, velvet 270, lycra 300, oxford 330, poplin 15.
 * Grade -> CIELAB L* target: A 72, B 62, C 52, D 42.
 * Within family+grade: hue jitter = (hash % 17) - 8 (range -8..+8);
 * chroma = 0.42 + ((hash ushr 8) % 11) / 100 (range 0.42..0.52).
 * Final colour = LCH(L*, chroma*100, (anchor + jitter + 360) mod 360) -> Lab -> XYZ (D65) -> sRGB.
 * On-colour: WCAG relative luminance; BLACK if it yields the higher contrast ratio, else WHITE.
 *
 * Note: @Immutable is a Compose annotation and this is a pure-JVM module, so the annotation lives
 * on the Compose-facing call sites instead (see ADR.md). This object holds no state.
 */
object PaletteResolver {

    /** Packed ARGB with opaque alpha. */
    data class Swatch(val argb: Int, val onColour: Int, val contrastRatio: Double) {
        val rgb: Int get() = argb and 0x00FFFFFF
        fun hex(): String = "#%06X".format(rgb)
        fun onHex(): String = "#%06X".format(onColour and 0x00FFFFFF)
    }

    enum class Grade(val lStar: Double) { A(72.0), B(62.0), C(52.0), D(42.0) }

    /** Family key (as stored in colours.family) -> hue anchor degrees. Pinned. */
    private val FAMILY_HUE_ANCHORS: Map<String, Int> = linkedMapOf(
        "cotton" to 0,
        "linen" to 20,
        "wool" to 45,
        "silk" to 90,
        "polyester" to 150,
        "viscose" to 190,
        "denim" to 220,
        "velvet" to 270,
        "lycra" to 300,
        "oxford" to 330,
        "poplin" to 15,
    )

    private const val FNV_OFFSET_BASIS: UInt = 2166136261u
    private const val FNV_PRIME: UInt = 16777619u

    /** FNV-1a 32-bit over UTF-8 bytes (pinned, bit-identical to the Python twin). */
    fun stableHash32(input: String): UInt {
        var hash = FNV_OFFSET_BASIS
        for (byte in input.encodeToByteArray()) {
            hash = hash xor byte.toUByte().toUInt()
            hash *= FNV_PRIME
        }
        return hash
    }

    fun hueAnchorForFamily(family: String): Int =
        FAMILY_HUE_ANCHORS[family] ?: throw IllegalArgumentException("unknown family: $family")

    /** Computes the deterministic swatch for (family, grade, code). */
    fun resolve(family: String, grade: String, code: String): Swatch {
        val anchor = hueAnchorForFamily(family)
        val gradeL = Grade.entries.firstOrNull { it.name == grade }
            ?: throw IllegalArgumentException("unknown grade: $grade")
        val hash = stableHash32(code)
        val hueJitter = (hash % 17u).toInt() - 8
        val chroma = 0.42 + ((hash shr 8) % 11u).toInt() / 100.0
        val hue = (anchor + hueJitter + 360) % 360
        val (r, g, b) = lchToSrgb(gradeL.lStar, chroma * 100.0, hue.toDouble())
        val argb = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        val lum = relativeLuminance(r, g, b)
        val onBlack = contrastRatio(lum, 0.0)
        val onWhite = contrastRatio(lum, 1.0)
        return if (onBlack >= onWhite) {
            Swatch(argb = argb, onColour = 0xFF000000.toInt(), contrastRatio = onBlack)
        } else {
            Swatch(argb = argb, onColour = 0xFFFFFFFF.toInt(), contrastRatio = onWhite)
        }
    }

    /** 5-stop gradient strip hues (anchor ± 20°) for the Types tab. */
    fun gradientStopHues(anchorDegrees: Int): List<Int> =
        listOf(anchorDegrees - 20, anchorDegrees - 10, anchorDegrees, anchorDegrees + 10, anchorDegrees + 20)
            .map { ((it % 360) + 360) % 360 }

    // ---- CIE pipeline (D65) ----

    private const val XN = 0.95047
    private const val ZN = 1.08883
    private const val EPSILON = 216.0 / 24389.0
    private const val KAPPA = 24389.0 / 27.0

    private fun lchToSrgb(lStar: Double, chroma: Double, hueDegrees: Double): Triple<Int, Int, Int> {
        val hRad = Math.toRadians(hueDegrees)
        val a = chroma * cos(hRad)
        val b = chroma * sin(hRad)
        val fy = (lStar + 16.0) / 116.0
        val fx = fy + a / 500.0
        val fz = fy - b / 200.0
        val x = XN * invF(fx)
        val y = if (lStar > KAPPA * EPSILON) ((lStar + 16.0) / 116.0).pow(3.0) else lStar / KAPPA
        val z = ZN * invF(fz)
        val r = gammaEncode(3.2404542 * x - 1.5371385 * y - 0.4985314 * z)
        val g = gammaEncode(-0.9692660 * x + 1.8760108 * y + 0.0415560 * z)
        val bb = gammaEncode(0.0556434 * x - 0.2040259 * y + 1.0572252 * z)
        return Triple(toByte(r), toByte(g), toByte(bb))
    }

    private fun invF(t: Double): Double {
        val t3 = t * t * t
        return if (t3 > EPSILON) t3 else (116.0 * t - 16.0) / KAPPA
    }

    private fun gammaEncode(c: Double): Double {
        val clamped = c.coerceIn(0.0, 1.0)
        return if (clamped <= 0.0031308) 12.92 * clamped else 1.055 * clamped.pow(1.0 / 2.4) - 0.055
    }

    /** floor(v * 255 + 0.5) — pinned half-up, bit-identical across JVMs and the Python twin. */
    private fun toByte(c: Double): Int = floor(c * 255.0 + 0.5).toInt().coerceIn(0, 255)

    // ---- WCAG relative luminance + contrast ----

    private fun channelLuminance(c: Int): Double {
        val c8 = c / 255.0
        return if (c8 <= 0.04045) c8 / 12.92 else ((c8 + 0.055) / 1.055).pow(2.4)
    }

    fun relativeLuminance(r: Int, g: Int, b: Int): Double =
        0.2126 * channelLuminance(r) + 0.7152 * channelLuminance(g) + 0.0722 * channelLuminance(b)

    fun contrastRatio(l1: Double, l2: Double): Double {
        val hi = maxOf(l1, l2)
        val lo = minOf(l1, l2)
        return (hi + 0.05) / (lo + 0.05)
    }
}
