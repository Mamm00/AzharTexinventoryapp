package com.alazhartex.stock.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Tiny palette primitive (v1.1, delta §10.2).
 *
 * [contentDescription] is mandatory (interpolated by the caller). The swatch is never the sole
 * meaning carrier — every list/chip/picker/report also shows a text label next to it.
 * When [interactive] is true the touch target is padded to >= 48dp even at [SwatchSize.Tiny].
 */
enum class SwatchSize(val dp: Dp, val corner: Dp) {
    Tiny(12.dp, 4.dp),
    Small(20.dp, 4.dp),
    Medium(28.dp, 6.dp),
    Large(40.dp, 6.dp),
}

private val GRADE_BADGE_COLORS = mapOf(
    "A" to androidx.compose.ui.graphics.Color(0xFF1E7B3C),
    "B" to androidx.compose.ui.graphics.Color(0xFF2563EB),
    "C" to androidx.compose.ui.graphics.Color(0xFFD97706),
    "D" to androidx.compose.ui.graphics.Color(0xFF6B7280),
)

@Composable
fun ColourSwatch(
    value: Int,
    onColour: Int,
    contentDescription: String,
    modifier: Modifier = Modifier,
    size: SwatchSize = SwatchSize.Tiny,
    grade: String? = null,
    interactive: Boolean = false,
) {
    val inner: @Composable () -> Unit = {
        Box(
            modifier = Modifier
                .width(size.dp)
                .height(size.dp)
                .clip(RoundedCornerShape(size.corner))
                .background(androidx.compose.ui.graphics.Color(value)),
        ) {
            if (grade != null) {
                GradeBadge(grade = grade, modifier = Modifier.align(Alignment.TopEnd))
            }
        }
    }
    val outerSize = if (interactive) 48.dp else size.dp
    Box(
        modifier = modifier
            .semantics { this.contentDescription = contentDescription }
            .width(outerSize)
            .height(outerSize),
        contentAlignment = Alignment.Center,
    ) {
        inner()
    }
}

@Composable
fun GradeBadge(grade: String, modifier: Modifier = Modifier) {
    val badge = GRADE_BADGE_COLORS[grade] ?: MaterialTheme.colorScheme.outline
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(badge)
            .width(14.dp)
            .height(14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = grade.take(1),
            color = androidx.compose.ui.graphics.Color.White,
            fontSize = 9.sp,
            lineHeight = 9.sp,
        )
    }
}

/** Row of up to [cap] swatches + "+N" overflow label (Fabrics tab / family strip). */
@Composable
fun ColourSwatchStrip(
    // Triple per entry: value, onColour, contentDescription
    entries: List<Triple<Int, Int, String>>,
    moreLabel: String,
    modifier: Modifier = Modifier,
    cap: Int = 12,
    size: SwatchSize = SwatchSize.Tiny,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        entries.take(cap).forEach { (v, on, cd) ->
            ColourSwatch(value = v, onColour = on, contentDescription = cd, size = size, modifier = Modifier.padding(end = 2.dp))
        }
        if (entries.size > cap) {
            Text(text = moreLabel, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 2.dp))
        }
    }
}

private val PINNED_FAMILY_KEYS = setOf(
    "cotton", "linen", "wool", "silk", "polyester", "viscose", "denim", "velvet", "lycra", "oxford", "poplin",
)

/** Resolves the swatch for (family, grade, code), memoized per code for list render paths. */
@androidx.compose.runtime.Composable
fun rememberSwatch(family: String, grade: String, code: String): com.alazhartex.stock.domain.palette.PaletteResolver.Swatch =
    androidx.compose.runtime.remember(family, grade, code) {
        com.alazhartex.stock.domain.palette.PaletteResolver.resolve(family, grade, code)
    }

/**
 * Pinned families go through [com.alazhartex.stock.domain.palette.PaletteResolver]; anything else
 * (v1.1 custom families) goes through the deterministic [com.alazhartex.stock.domain.palette.PaletteFallback].
 */
@androidx.compose.runtime.Composable
fun rememberAnySwatch(family: String, grade: String, code: String): com.alazhartex.stock.domain.palette.PaletteResolver.Swatch =
    androidx.compose.runtime.remember(family, grade, code) {
        if (family in PINNED_FAMILY_KEYS) {
            com.alazhartex.stock.domain.palette.PaletteResolver.resolve(family, grade, code)
        } else {
            com.alazhartex.stock.domain.palette.PaletteFallback.resolve(family, grade, code)
        }
    }

/** "Colour {code} — {name} — grade {grade}" with the AR name under RTL and the EN name otherwise. */
@androidx.compose.runtime.Composable
fun paletteContentDescription(code: String, nameEn: String, nameAr: String, grade: String): String {
    val rtl = androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl
    return androidx.compose.ui.res.stringResource(
        com.alazhartex.stock.R.string.palette_swatch_cd,
        code,
        if (rtl) nameAr else nameEn,
        grade,
    )
}
