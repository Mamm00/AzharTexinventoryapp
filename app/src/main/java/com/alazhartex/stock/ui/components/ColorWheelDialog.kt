package com.alazhartex.stock.ui.components

import android.content.Context
import android.graphics.Bitmap as AndroidBitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.alazhartex.stock.R
import com.alazhartex.stock.domain.palette.CustomSwatch
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import org.json.JSONArray

/**
 * Full-screen HSV colour picker (v1.3 "color selection"), modelled on the warehouse's reference
 * picker: hue ring + saturation/value triangle, a curated preset grid, and user favourites that
 * persist on-device (max 12) in SharedPreferences (`alazhartex.ui` / `colour_favourites`) — a pure
 * UI preference, no database involvement, same ownership as the locale preference.
 *
 * Confirm returns a normalized `#RRGGBB` string. Self-contained dark panel like the reference.
 */
@Composable
fun ColorWheelDialog(
    initialHex: String?,
    onDismiss: () -> Unit,
    onConfirm: (hex: String) -> Unit,
) {
    val context = LocalContext.current
    var hsv by rememberSaveable(stateSaver = HsvSaver) {
        mutableStateOf(hsvFromHex(initialHex) ?: floatArrayOf(348f, 0.72f, 0.90f))
    }
    var hexField by rememberSaveable { mutableStateOf(hexString(hsv)) }
    var hexError by rememberSaveable { mutableStateOf(false) }
    var favourites by remember { mutableStateOf(loadFavourites(context)) }

    val hue = hsv[0]
    val saturation = hsv[1]
    val value = hsv[2]
    val currentHsvInt = AndroidColor.HSVToColor(hsv)
    val currentColor = Color(currentHsvInt)
    val currentHex = CustomSwatch.normalizeHex(hexField) ?: hexString(hsv)

    fun pushHsv(newHsv: FloatArray) {
        hsv = newHsv
        hexField = hexString(newHsv)
        hexError = false
    }

    fun pushHex(text: String) {
        hexField = text.uppercase(Locale.US).take(9)
        val parsed = CustomSwatch.normalizeHex(hexField)
        if (parsed != null) {
            hsv = hsvFromHex(parsed) ?: hsv
            hexError = false
        } else {
            hexError = hexField.isNotBlank() && hexField.trimStart('#').length >= 6
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF242424)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Top bar: cancel (X) — live preview chip — confirm (✓), like the reference panel.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_cancel), tint = Color.White)
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF333333))
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(currentColor)
                                .border(1.dp, Color(0x55FFFFFF), RoundedCornerShape(8.dp)),
                        )
                        Text(text = currentHex, color = Color.White, style = MaterialTheme.typography.titleMedium)
                    }
                    IconButton(onClick = { CustomSwatch.normalizeHex(currentHex)?.let(onConfirm) }) {
                        Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.action_confirm), tint = Color.White)
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Hue ring + SV triangle in ONE gesture surface.
                HsvWheel(
                    hue = hue,
                    saturation = saturation,
                    value = value,
                    onChange = { newHue, newSat, newValue -> pushHsv(floatArrayOf(newHue, newSat, newValue)) },
                )

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = hexField,
                    onValueChange = ::pushHex,
                    label = { Text(stringResource(R.string.color_hex_label)) },
                    singleLine = true,
                    isError = hexError,
                    prefix = { Text("#", color = Color(0xFFBBBBBB)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF8AB4FF),
                        unfocusedBorderColor = Color(0xFF555555),
                        cursorColor = Color.White,
                        focusedLabelColor = Color(0xFFBBBBBB),
                        unfocusedLabelColor = Color(0xFFBBBBBB),
                    ),
                    modifier = Modifier.fillMaxWidth(0.82f),
                )

                Spacer(Modifier.height(16.dp))

                Text(
                    stringResource(R.string.color_presets),
                    color = Color(0xFFCCCCCC),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.fillMaxWidth(),
                )
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    PRESET_ROWS.forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { preset ->
                                PresetCell(hex = preset, size = 46.dp) {
                                    hsvFromHex(preset)?.let(::pushHsv)
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        stringResource(R.string.color_favourites),
                        color = Color(0xFFCCCCCC),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    TextButton(onClick = { favourites = saveFavourite(context, currentHex, favourites) }) {
                        Text(stringResource(R.string.color_save_favourite), color = Color(0xFF8AB4FF))
                    }
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(6),
                    modifier = Modifier.fillMaxWidth().height(108.dp).padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    userScrollEnabled = false,
                ) {
                    itemsIndexed((0 until 12).toList(), key = { _, slot -> slot }) { _, slot ->
                        val favourite = favourites.getOrNull(slot)
                        if (favourite == null) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(1.5.dp, Color(0xFF555555), RoundedCornerShape(8.dp))
                                    .clickable { favourites = saveFavourite(context, currentHex, favourites) },
                                contentAlignment = Alignment.Center,
                            ) { Text("+", color = Color(0xFF777777), style = MaterialTheme.typography.titleLarge) }
                        } else {
                            Box {
                                PresetCell(hex = favourite, size = 46.dp) {
                                    hsvFromHex(favourite)?.let(::pushHsv)
                                }
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(18.dp)
                                        .clip(RoundedCornerShape(9.dp))
                                        .background(Color(0xCC000000))
                                        .clickable { favourites = removeFavourite(context, favourite, favourites) },
                                    contentAlignment = Alignment.Center,
                                ) { Text("×", color = Color.White, style = MaterialTheme.typography.labelSmall) }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun PresetCell(hex: String, size: Dp, onClick: () -> Unit) {
    val color = remember(hex) { CustomSwatch.parse(hex)?.let { Color(it.argb) } ?: Color.Transparent }
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(8.dp))
            .background(color)
            .border(1.dp, Color(0x44FFFFFF), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
    )
}

/** Hue ring + SV triangle with a single gesture surface; handles drawn on the canvas overlay. */
@Composable
private fun HsvWheel(
    hue: Float,
    saturation: Float,
    value: Float,
    onChange: (Float, Float, Float) -> Unit,
) {
    val ring = remember { renderHueRing(280) }
    val hueInt = ((hue % 360f) + 360f) % 360f
    val triangle = remember(hueInt.roundToInt()) { renderSvTriangle(hueInt.roundToInt().toFloat(), 200) }
    // Single Canvas draws both the hue ring and the SV triangle — one combined description.
    val wheelDesc = "${stringResource(R.string.color_hue_ring)} — ${stringResource(R.string.color_sv_triangle)}"

    // Fresh copies for the gesture lambdas — pointerInput(Unit) never relaunches mid-drag.
    val liveHue by androidx.compose.runtime.rememberUpdatedState(hue)
    val liveSat by androidx.compose.runtime.rememberUpdatedState(saturation)
    val liveValue by androidx.compose.runtime.rememberUpdatedState(value)

    androidx.compose.foundation.Canvas(
        modifier = Modifier
            .size(280.dp)
            .semantics { contentDescription = wheelDesc }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitPointerEvent().changes.firstOrNull() ?: return@awaitEachGesture
                    if (!down.pressed) return@awaitEachGesture
                    val w = size.width.toFloat()
                    val center = Offset(w / 2f, size.height / 2f)
                    val bandInner = w * 0.36f
                    val bandOuter = w * 0.5f
                    val triHalf = w * 0.36f // triangle occupies the inner disc
                    var mode = 0 // 0 = undecided, 1 = hue, 2 = sv

                    fun tryHue(pos: Offset): Boolean {
                        val d = pos - center
                        val r = hypot(d.x.toDouble(), d.y.toDouble()).toFloat()
                        if (r < bandInner || r > bandOuter) return false
                        var deg = Math.toDegrees(atan2(d.y.toDouble(), d.x.toDouble())).toFloat() + 90f
                        if (deg < 0f) deg += 360f
                        onChange(deg, liveSat, liveValue)
                        return true
                    }

                    fun trySv(pos: Offset): Boolean {
                        val triW = triHalf * 2f
                        val triH = triHalf * 1.74f
                        val originX = center.x - triHalf
                        val originY = center.y - triH / 2f
                        val (s, v) = svFromPoint(pos.x - originX, pos.y - originY, triW, triH) ?: return false
                        onChange(liveHue, s, v)
                        return true
                    }

                    if (tryHue(down.position)) {
                        mode = 1
                    } else if (trySv(down.position)) {
                        mode = 2
                    }
                    if (mode == 0) return@awaitEachGesture
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.pressed } ?: break
                        when (mode) {
                            1 -> tryHue(change.position)
                            2 -> trySv(change.position)
                        }
                        if (change.isConsumed) break
                    }
                }
            },
    ) {
        // Ring bitmap.
        drawImage(ring, dstOffset = androidx.compose.ui.unit.IntOffset(0, 0), dstSize = androidx.compose.ui.unit.IntSize(size.width.toInt(), size.height.toInt()))
        // Triangle bitmap centered.
        val triHalf = size.width * 0.36f
        val triW = triHalf * 2f
        val triH = triHalf * 1.74f
        val originX = (size.width - triW) / 2f
        val originY = (size.height - triH) / 2f
        drawImage(
            triangle,
            dstOffset = androidx.compose.ui.unit.IntOffset(originX.toInt(), originY.toInt()),
            dstSize = androidx.compose.ui.unit.IntSize(triW.toInt(), triH.toInt()),
        )
        // Hue handle (white-ringed circle on the band).
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        val bandRadius = size.width * 0.43f
        val angle = Math.toRadians((hue - 90.0))
        val hx = centerX + bandRadius * cos(angle).toFloat()
        val hy = centerY + bandRadius * sin(angle).toFloat()
        drawCircle(color = Color.White, radius = 9.dp.toPx(), center = Offset(hx, hy), style = Stroke(width = 3.dp.toPx()))
        drawCircle(color = Color.Black.copy(alpha = 0.30f), radius = 9.dp.toPx(), center = Offset(hx, hy), style = Stroke(width = 1.dp.toPx()))
        // SV handle (affine mix of the three vertices).
        val wA = value * (1f - saturation)
        val wC = saturation * value
        val wB = (1f - wA - wC).coerceIn(0f, 1f)
        val px = originX + wA * (triW / 2f) + wB * 0f + wC * triW
        val py = originY + wA * 0f + wB * triH + wC * triH
        drawCircle(color = Color.White, radius = 9.dp.toPx(), center = Offset(px, py), style = Stroke(width = 3.dp.toPx()))
        drawCircle(color = Color.Black.copy(alpha = 0.30f), radius = 9.dp.toPx(), center = Offset(px, py), style = Stroke(width = 1.dp.toPx()))
    }
}

/** Barycentric → (saturation, value); null when the point sits outside the triangle. */
private fun svFromPoint(x: Float, y: Float, w: Float, h: Float): Pair<Float, Float>? {
    val ax = w / 2f
    val ay = 0f
    val bx = 0f
    val by = h
    val cx = w
    val cy = h
    val v0x = cx - ax
    val v0y = cy - ay
    val v1x = bx - ax
    val v1y = by - ay
    val v2x = x - ax
    val v2y = y - ay
    val dot00 = v0x * v0x + v0y * v0y
    val dot01 = v0x * v1x + v0y * v1y
    val dot02 = v0x * v2x + v0y * v2y
    val dot11 = v1x * v1x + v1y * v1y
    val dot12 = v1x * v2x + v1y * v2y
    val denom = dot00 * dot11 - dot01 * dot01
    if (denom == 0f) return null
    val inv = 1f / denom
    val u = (dot11 * dot02 - dot01 * dot12) * inv
    val v = (dot00 * dot12 - dot01 * dot02) * inv
    val rawA = 1f - u - v
    val rawB = v
    val rawC = u
    if (rawA < -0.05f || rawB < -0.05f || rawC < -0.05f) return null
    val wA = rawA.coerceIn(0f, 1f)
    val wB = rawB.coerceIn(0f, 1f)
    val wC = rawC.coerceIn(0f, 1f)
    val sum = wA + wB + wC
    if (sum <= 0f) return null
    val nA = wA / sum
    val nC = wC / sum
    val value = (nA + nC).coerceIn(0f, 1f)
    val sat = if (value > 0f) (nC / value).coerceIn(0f, 1f) else 0f
    return sat to value
}

private fun renderHueRing(size: Int): androidx.compose.ui.graphics.ImageBitmap {
    val bmp = AndroidBitmap.createBitmap(size, size, AndroidBitmap.Config.ARGB_8888)
    val c = size / 2f
    val outer = size / 2f
    val inner = outer * 0.72f
    val pixels = IntArray(size * size)
    val hsv = FloatArray(3)
    for (y in 0 until size) {
        for (x in 0 until size) {
            val dx = x + 0.5f - c
            val dy = y + 0.5f - c
            val r = hypot(dx.toDouble(), dy.toDouble())
            if (r < inner || r > outer) continue
            var deg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())) + 90.0
            if (deg < 0) deg += 360.0
            hsv[0] = deg.toFloat()
            hsv[1] = 1f
            hsv[2] = 1f
            pixels[y * size + x] = AndroidColor.HSVToColor(hsv)
        }
    }
    bmp.setPixels(pixels, 0, size, 0, 0, size, size)
    return bmp.asImageBitmap()
}

private fun renderSvTriangle(hueDeg: Float, size: Int): androidx.compose.ui.graphics.ImageBitmap {
    val bmp = AndroidBitmap.createBitmap(size, size, AndroidBitmap.Config.ARGB_8888)
    val pixels = IntArray(size * size)
    val ax = size / 2f
    val ay = 0f
    val bx = 0f
    val by = size.toFloat()
    val cx = size.toFloat()
    val cy = size.toFloat()
    val hueColor = AndroidColor.HSVToColor(floatArrayOf(hueDeg, 1f, 1f))
    val hR = (hueColor shr 16) and 0xFF
    val hG = (hueColor shr 8) and 0xFF
    val hB = hueColor and 0xFF
    val v0x = cx - ax
    val v0y = cy - ay
    val v1x = bx - ax
    val v1y = by - ay
    val dot00 = v0x * v0x + v0y * v0y
    val dot01 = v0x * v1x + v0y * v1y
    val dot11 = v1x * v1x + v1y * v1y
    val denom = dot00 * dot11 - dot01 * dot01
    for (y in 0 until size) {
        for (x in 0 until size) {
            val v2x = x + 0.5f - ax
            val v2y = y + 0.5f - ay
            val dot02 = v0x * v2x + v0y * v2y
            val dot12 = v1x * v2x + v1y * v2y
            val u = (dot11 * dot02 - dot01 * dot12) / denom
            val v = (dot00 * dot12 - dot01 * dot02) / denom
            var wA = 1f - u - v
            val wB = v
            val wC = u
            if (wA < -0.002f || wB < -0.002f || wC < -0.002f) continue
            wA = wA.coerceIn(0f, 1f)
            val cwB = wB.coerceIn(0f, 1f)
            val cwC = wC.coerceIn(0f, 1f)
            val sum = (wA + cwB + cwC).coerceAtLeast(1e-6f)
            val nA = wA / sum
            val nC = cwC / sum
            val r = (nA * 255f + nC * hR).roundToInt().coerceIn(0, 255)
            val g = (nA * 255f + nC * hG).roundToInt().coerceIn(0, 255)
            val b = (nA * 255f + nC * hB).roundToInt().coerceIn(0, 255)
            pixels[y * size + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
    }
    bmp.setPixels(pixels, 0, size, 0, 0, size, size)
    return bmp.asImageBitmap()
}

private fun hsvFromHex(hex: String?): FloatArray? {
    val swatch = CustomSwatch.parse(hex) ?: return null
    val r = (swatch.argb shr 16) and 0xFF
    val g = (swatch.argb shr 8) and 0xFF
    val b = swatch.argb and 0xFF
    val out = FloatArray(3)
    AndroidColor.RGBToHSV(r, g, b, out)
    return out
}

private fun hexString(hsv: FloatArray): String =
    String.format(Locale.US, "#%06X", 0xFFFFFF and AndroidColor.HSVToColor(hsv))

// ---------- favourites (SharedPreferences, UI-only) ----------

private const val UI_PREFS = "alazhartex.ui"
private const val FAVOURITES_KEY = "colour_favourites"
private const val MAX_FAVOURITES = 12

private fun loadFavourites(context: Context): List<String> {
    val raw = context.getSharedPreferences(UI_PREFS, Context.MODE_PRIVATE).getString(FAVOURITES_KEY, null)
        ?: return emptyList()
    return runCatching {
        val array = JSONArray(raw)
        (0 until array.length())
            .mapNotNull { CustomSwatch.normalizeHex(array.optString(it)) }
            .distinct()
            .take(MAX_FAVOURITES)
    }.getOrDefault(emptyList())
}

private fun persistFavourites(context: Context, favourites: List<String>) {
    val array = JSONArray()
    favourites.take(MAX_FAVOURITES).forEach { array.put(it) }
    context.getSharedPreferences(UI_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(FAVOURITES_KEY, array.toString())
        .apply()
}

private fun saveFavourite(context: Context, hex: String?, current: List<String>): List<String> {
    val normalized = CustomSwatch.normalizeHex(hex) ?: return current
    val updated = (listOf(normalized) + current.filterNot { it == normalized }).take(MAX_FAVOURITES)
    persistFavourites(context, updated)
    return updated
}

private fun removeFavourite(context: Context, hex: String, current: List<String>): List<String> {
    val updated = current.filterNot { it == hex }
    persistFavourites(context, updated)
    return updated
}

private val HsvSaver = listSaver<FloatArray, Float>(
    save = { listOf(it[0], it[1], it[2]) },
    restore = { floatArrayOf(it[0], it[1], it[2]) },
)

/** Two curated rows from the brand palette sheet (terracotta/gold/beige/olive families). */
private val PRESET_ROWS: List<List<String>> = listOf(
    listOf("#000000", "#FFFFFF", "#7E57C2", "#5C6BC0", "#42A5F5", "#26C6DA"),
    listOf("#00E676", "#D4E157", "#FFCA28", "#FFA726", "#FF7043", "#F06292"),
    listOf("#C62828", "#8D6E63", "#5D4037", "#37474F", "#F5E6C8", "#B7A26A"),
)
