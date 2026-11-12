package com.alazhartex.stock.domain.palette

/**
 * User-picked swatch override (v1.3 "color selection" feature).
 *
 * Every seeded/pinned colour resolves through [PaletteResolver] (spec-pinned, deterministic).
 * A colour the user created with an explicitly picked hex carries it in `colours.custom_hex`
 * and renders EXACTLY that colour in every screen. `custom_hex` is NULL for everything seeded,
 * so the pinned palette contract for the seed is untouched.
 *
 * PURE + DETERMINISTIC: same inputs -> same packed ARGB / on-colour on every platform.
 * WCAG relative-luminance math mirrors [PaletteResolver].
 */
object CustomSwatch {

    /** #RGB is deliberately rejected — the picker always emits 6 or 8 hex digits. */
    private val HEX_REGEX = Regex("^#([0-9a-fA-F]{6}|[0-9a-fA-F]{8})$")

    data class Swatch(val argb: Int, val onColour: Int) {
        val rgb: Int get() = argb and 0x00FFFFFF
        fun hex(): String = "#%06X".format(rgb)
        fun onHex(): String = "#%06X".format(onColour and 0x00FFFFFF)
    }

    fun isValidHex(input: String?): Boolean = input != null && HEX_REGEX.matches(input.trim())

    /**
     * Normalize user input to canonical `#RRGGBB` (uppercase). Accepts `#RRGGBB` and `#AARRGGBB`
     * (alpha is dropped — swatches are opaque). Returns null when malformed.
     */
    fun normalizeHex(input: String?): String? {
        if (input == null) return null
        val match = HEX_REGEX.matchEntire(input.trim()) ?: return null
        val body = match.groupValues[1]
        val rgb = body.takeLast(6).uppercase()
        return "#$rgb"
    }

    /** Parse a stored `custom_hex`. Returns null on malformed data (callers fall back to the pinned resolver). */
    fun parse(input: String?): Swatch? {
        val normalized = normalizeHex(input) ?: return null
        val r = normalized.substring(1, 3).toInt(16)
        val g = normalized.substring(3, 5).toInt(16)
        val b = normalized.substring(5, 7).toInt(16)
        val argb = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        return Swatch(argb = argb, onColour = onColourFor(r, g, b))
    }

    /** BLACK when it yields the higher WCAG contrast ratio against [argb], else WHITE. */
    fun onColourFor(argb: Int): Int {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return onColourFor(r, g, b)
    }

    private fun onColourFor(r: Int, g: Int, b: Int): Int {
        val lum = relativeLuminance(r, g, b)
        val onBlack = contrastRatio(lum, 0.0)
        val onWhite = contrastRatio(lum, 1.0)
        return if (onBlack >= onWhite) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
    }

    private fun channel(c: Int): Double {
        val s = c / 255.0
        return if (s <= 0.04045) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4)
    }

    private fun relativeLuminance(r: Int, g: Int, b: Int): Double =
        0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b)

    private fun contrastRatio(l1: Double, l2: Double): Double {
        val lighter = maxOf(l1, l2)
        val darker = minOf(l1, l2)
        return (lighter + 0.05) / (darker + 0.05)
    }
}
