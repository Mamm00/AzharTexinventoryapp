package com.alazhartex.stock.domain.palette

/**
 * Deterministic preview palette for NON-pinned (custom) families (v1.1, delta contract §12).
 * The pinned 209-colour pipeline in [PaletteResolver] is untouched; this is a separate,
 * deterministic HSL preview used only when a family key has no pinned anchor. Hue is derived
 * from stableHash32(family), value scales with grade (A brightest -> D darkest), saturation 0.55.
 * Same inputs -> same bytes everywhere (pure Kotlin, no platform calls).
 */
object PaletteFallback {

    fun stableHue(family: String): Int = (PaletteResolver.stableHash32(family) % 360u).toInt()

    private fun gradeValue(grade: String): Float = when (grade) {
        "A" -> 0.80f
        "B" -> 0.68f
        "C" -> 0.56f
        else -> 0.44f
    }

    /** Standard HSL -> ARGB (opaque). */
    fun hslArgb(hueDegrees: Int, saturation: Float, lightness: Float): Int {
        val h = (((hueDegrees % 360) + 360) % 360) / 60.0
        val c = (1.0 - kotlin.math.abs(2.0 * lightness - 1.0)) * saturation
        val x = c * (1 - kotlin.math.abs(h % 2.0 - 1.0))
        val (r1, g1, b1) = when (h.toInt()) {
            0 -> Triple(c, x, 0.0)
            1 -> Triple(x, c, 0.0)
            2 -> Triple(0.0, c, x)
            3 -> Triple(0.0, x, c)
            4 -> Triple(x, 0.0, c)
            else -> Triple(c, 0.0, x)
        }
        val m = lightness - c / 2.0
        val r = ((r1 + m) * 255.0).toInt().coerceIn(0, 255)
        val g = ((g1 + m) * 255.0).toInt().coerceIn(0, 255)
        val b = ((b1 + m) * 255.0).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    /** Same preview, but anchored at a pinned/stored hue (custom family rows in the Types tab). */
    fun resolveAnchored(hueAnchor: Int, grade: String, code: String): PaletteResolver.Swatch {
        val jitter = (PaletteResolver.stableHash32(code) % 17u).toInt() - 8
        return anchored(hueAnchor, grade, jitter)
    }

    private fun anchored(hueAnchor: Int, grade: String, jitter: Int): PaletteResolver.Swatch {
        val hue = (((hueAnchor + jitter) % 360) + 360) % 360
        val argb = hslArgb(hue, 0.55f, gradeValue(grade))
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        val lum = PaletteResolver.relativeLuminance(r, g, b)
        val onBlack = PaletteResolver.contrastRatio(lum, 0.0)
        val onWhite = PaletteResolver.contrastRatio(lum, 1.0)
        return if (onBlack >= onWhite) {
            PaletteResolver.Swatch(argb = argb, onColour = 0xFF000000.toInt(), contrastRatio = onBlack)
        } else {
            PaletteResolver.Swatch(argb = argb, onColour = 0xFFFFFFFF.toInt(), contrastRatio = onWhite)
        }
    }

    fun resolve(family: String, grade: String, code: String): PaletteResolver.Swatch {
        val jitter = (PaletteResolver.stableHash32(code) % 17u).toInt() - 8
        val anchor = stableHue(family)
        return anchored(anchor, grade, jitter)
    }
}
