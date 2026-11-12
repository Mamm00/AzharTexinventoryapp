package com.alazhartex.stock.domain.palette

/**
 * Custom family hue-anchor rule (delta contract §12, Types tab).
 * Anchor = ((max(existing) + 25) mod 360), snapped to the nearest 5 degrees.
 * PURE and deterministic — the custom family list lives in app_settings (not Room),
 * so the anchor is recomputed at insertion time from the pinned anchors + existing customs.
 */
object CustomHue {

    const val STEP_DEGREES: Int = 25
    const val SNAP_DEGREES: Int = 5

    fun nextAnchor(existingAnchors: List<Int>): Int {
        val base = if (existingAnchors.isEmpty()) 0 else existingAnchors.max() + STEP_DEGREES
        val wrapped = ((base % 360) + 360) % 360
        // nearest multiple of 5 (half-up on .5 boundaries); 360 snaps back to 0
        val snapped = ((wrapped.toDouble() / SNAP_DEGREES) + 0.5).toInt() * SNAP_DEGREES
        return snapped % 360
    }
}
