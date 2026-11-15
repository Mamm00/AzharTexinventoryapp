package com.alazhartex.stock.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme

/**
 * v1.8.0 liquid-glass surface. Whitelisted call sites ONLY (enforced by the
 * GlassSurfaceWhitelist detekt rule): top app bar on scroll, modal sheet scrims,
 * Cross-View sticky headers. Never on data-dense rows, never on buttons meant to
 * be hit precisely — glass is chrome, not content.
 *
 * API 31+: the glass layer itself is blurred (soft frost) over a translucent tint with a
 * hairline border. Below API 31: opaque surfaceContainer — fully legible, no translucency
 * guesswork on unpredictable wallpapers. blurRadius/tint/borderAlpha are call-site tunable.
 */
@Composable
fun Modifier.azharGlass(
    blurRadius: Dp = 12.dp,
    tint: Color = Color.Unspecified,
    borderAlpha: Float = 0.08f,
): Modifier {
    val scheme = MaterialTheme.colorScheme
    val resolvedTint = if (tint == Color.Unspecified) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            scheme.surfaceContainer.copy(alpha = 0.62f)
        } else {
            scheme.surfaceContainer
        }
    } else {
        tint
    }
    val shape = RoundedCornerShape(24.dp)
    val hairline = Modifier.border(1.dp, scheme.onSurface.copy(alpha = borderAlpha.coerceIn(0f, 1f)), shape)
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        this
            .then(Modifier.graphicsLayer {
                renderEffect = android.graphics.RenderEffect.createBlurEffect(blurRadius.toPx(), blurRadius.toPx(), android.graphics.Shader.TileMode.DECAL)
                    .asComposeRenderEffect()
            })
            .then(Modifier.clip(shape).background(resolvedTint, shape))
            .then(hairline)
    } else {
        this
            .then(Modifier.clip(shape).background(scheme.surfaceContainer, shape))
            .then(hairline)
    }
}
