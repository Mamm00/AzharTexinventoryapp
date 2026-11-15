package com.alazhartex.stock.ui.bento

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alazhartex.stock.data.repo.ShedCardDto
import com.alazhartex.stock.data.repo.TopColourDto
import com.alazhartex.stock.domain.gauge.GaugeBucket
import androidx.compose.ui.text.font.FontFeatureSetting
import com.alazhartex.stock.ui.components.EmphasizedStat
import com.alazhartex.stock.ui.components.EmphasizedStyle
import com.alazhartex.stock.ui.theme.headlineLargeEmphasized
import com.alazhartex.stock.ui.components.ShedUtilisationRail
import com.alazhartex.stock.ui.components.SwatchRing
import com.alazhartex.stock.ui.components.bucketLabelFor
import com.alazhartex.stock.ui.components.gaugeColor
import com.alazhartex.stock.ui.integerString

/** One KPI tile on the bento — asymmetric silhouette (32/8 corners), tabular figures. */
@Composable
private fun KpiTile(
    label: String,
    value: String,
    contentDescription: String,
    flipRadius: Boolean,
    modifier: Modifier = Modifier,
    testTag: String? = null,
) {
    val shape = if (flipRadius) {
        RoundedCornerShape(topStart = 32.dp, topEnd = 8.dp, bottomStart = 8.dp, bottomEnd = 32.dp)
    } else {
        RoundedCornerShape(topStart = 8.dp, topEnd = 32.dp, bottomStart = 32.dp, bottomEnd = 8.dp)
    }
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
            .semantics { this.contentDescription = contentDescription }
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
    ) {
        Column(
            modifier = Modifier.padding(vertical = 14.dp, horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                value,
                style = headlineLargeEmphasized.copy(fontFeatureSettings = FontFeatureSetting("tnum")),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
    }
}

/** Full-width drift plane — NOT a card; error container paints only when drift exists. */
@Composable
private fun DriftStatusPlane(
    issueCount: Int,
    okText: String,
    driftText: (count: Int) -> String,
    modifier: Modifier = Modifier,
    testTag: String = "override_banner",
) {
    val hasIssue = issueCount > 0
    val scheme = MaterialTheme.colorScheme
    val background = if (hasIssue) scheme.errorContainer else androidx.compose.ui.graphics.Color.Transparent
    val onBackground = if (hasIssue) scheme.onErrorContainer else scheme.onSurfaceVariant
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(background)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .semantics { contentDescription = if (hasIssue) driftText(issueCount) else okText }
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(if (hasIssue) scheme.error else scheme.primary),
        )
        Text(
            if (hasIssue) driftText(issueCount) else okText,
            style = MaterialTheme.typography.bodyMedium,
            color = onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * v1.8.0 dashboard bento. Hero utilisation compartment (largest, emphasized type, animated
 * arc), four KPI compartments, a full-width drift status plane (not a card), Top Sheds rail
 * bars and the Top Colours orbit. Every tile carries a merged content description ending in
 * the primary value. Reflows cleanly at 200% font scale (labels elide, never clip).
 */
@Composable
fun DashboardBento(
    totalRolls: Long,
    activeRolls: Long,
    inactiveRolls: Long,
    capacityRolls: Long,
    percentDisplay: String?,
    percentFraction: Float,
    bucket: GaugeBucket?,
    topSheds: List<ShedCardDto>,
    topColours: List<TopColourDto>,
    driftViolations: Int,
    overridesEnabled: Boolean,
    overrideDiffs: Int,
    arabicIndic: Boolean,
    strings: BentoStrings,
    onColourClick: (TopColourDto) -> Unit,
    modifier: Modifier = Modifier,
) {
    val heroAnimated by animateFloatAsState(
        targetValue = (percentFraction / 1.5f).coerceIn(0f, 1f),
        label = "hero-arc",
    )
    val bucketLabel = bucketLabelFor(
        bucket,
        strings.bucketGreen,
        strings.bucketAmber,
        strings.bucketRed,
        strings.bucketOver,
        strings.bucketEmpty,
    )
    val heroDesc = "${strings.heroCaption}: ${percentDisplay?.plus("%") ?: "—"} $bucketLabel"
    BentoGrid(modifier = modifier.fillMaxWidth()) {
        // ---- Hero utilisation compartment -----------------------------------
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier
                .bentoSpan(12, 2)
                .semantics { contentDescription = heroDesc }
                .testTag("dash_hero"),
        ) {
            Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(128.dp)) {
                    AnimatedArc(fraction = heroAnimated, color = gaugeColor(bucket), strokeWidth = 14f)
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                    EmphasizedStat(
                        value = percentDisplay?.plus("%") ?: "—",
                        caption = strings.heroCaption,
                        style = EmphasizedStyle.Display,
                        contentDescription = heroDesc,
                    )
                    Text(bucketLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }
        // ---- KPI compartments (asymmetric 32/8 radius) ----------------------
        KpiTile(
            label = strings.totalLabel,
            value = integerString(totalRolls, arabicIndic),
            contentDescription = "${strings.totalLabel}: ${integerString(totalRolls, arabicIndic)}",
            flipRadius = false,
            testTag = "dash_tile_total",
            modifier = Modifier.bentoSpan(3, 1),
        )
        KpiTile(
            label = strings.activeLabel,
            value = integerString(activeRolls, arabicIndic),
            contentDescription = "${strings.activeLabel}: ${integerString(activeRolls, arabicIndic)}",
            flipRadius = true,
            testTag = "dash_tile_active",
            modifier = Modifier.bentoSpan(3, 1),
        )
        KpiTile(
            label = strings.inactiveLabel,
            value = integerString(inactiveRolls, arabicIndic),
            contentDescription = "${strings.inactiveLabel}: ${integerString(inactiveRolls, arabicIndic)}",
            flipRadius = true,
            testTag = "dash_tile_inactive",
            modifier = Modifier.bentoSpan(3, 1),
        )
        KpiTile(
            label = strings.capacityLabel,
            value = integerString(capacityRolls, arabicIndic),
            contentDescription = "${strings.capacityLabel}: ${integerString(capacityRolls, arabicIndic)}",
            flipRadius = false,
            testTag = "dash_tile_capacity",
            modifier = Modifier.bentoSpan(3, 1),
        )
        // ---- Full-width drift status plane (not a card) ----------------------
        val issueCount = if (overridesEnabled) overrideDiffs else driftViolations
        DriftStatusPlane(
            issueCount = issueCount,
            okText = if (overridesEnabled) strings.overridesOk else strings.driftOk,
            driftText = { count -> if (overridesEnabled) strings.overridesMismatch(count) else strings.driftDetected(count) },
            modifier = Modifier.bentoSpan(12, 1),
        )
        // ---- Top Sheds rail bars ---------------------------------------------
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.bentoSpan(7, 2).semantics { contentDescription = strings.topShedsCaption },
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(strings.topShedsCaption, style = MaterialTheme.typography.titleSmall)
                if (topSheds.isEmpty()) {
                    Text(strings.emptyLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    topSheds.take(5).forEach { shed ->
                        val fraction = ((shed.percentDisplay?.toFloatOrNull() ?: 0f) / 100f).coerceIn(0f, 1.5f)
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(shed.nameEn, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    integerString(shed.activeRolls, arabicIndic) + " · " + ("${shed.percentDisplay ?: "—"}%"),
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                )
                            }
                            ShedUtilisationRail(
                                percentFraction = fraction,
                                bucket = bucketFor(fraction),
                                contentDescriptionPrefix = shed.nameEn,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
        // ---- Top Colours orbit -------------------------------------------------
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.bentoSpan(5, 2).semantics { contentDescription = strings.topColoursCaption },
        ) {
            Column(
                modifier = Modifier.padding(16.dp).fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(strings.topColoursCaption, style = MaterialTheme.typography.titleSmall, modifier = Modifier.fillMaxWidth())
                if (topColours.isEmpty()) {
                    Text(strings.emptyLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    SwatchRing(
                        colours = topColours.take(10),
                        arabicDigits = arabicIndic,
                        onColourClick = onColourClick,
                    )
                }
            }
        }
    }
}

@Composable
private fun AnimatedArc(fraction: Float, color: androidx.compose.ui.graphics.Color, strokeWidth: Float = 12f) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val stroke = strokeWidth
        val radius = (size.minDimension - stroke) / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        drawCircle(
            color = color.copy(alpha = 0.18f),
            radius = radius,
            center = center,
            style = Stroke(width = stroke),
        )
        if (fraction > 0f) {
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 360f * fraction.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                style = Stroke(width = stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round),
            )
        }
    }
}

private fun bucketFor(fraction: Float): GaugeBucket = when {
    fraction * 100f < 50f -> GaugeBucket.GREEN
    fraction * 100f <= 85f -> GaugeBucket.AMBER
    else -> GaugeBucket.RED
}

/** Pre-resolved, locale-safe strings — all copy lives in strings.xml; this struct just ferries it. */
data class BentoStrings(
    val heroCaption: String,
    val totalLabel: String,
    val activeLabel: String,
    val inactiveLabel: String,
    val capacityLabel: String,
    val bucketGreen: String,
    val bucketAmber: String,
    val bucketRed: String,
    val bucketOver: String,
    val bucketEmpty: String,
    val driftOk: String,
    val driftDetected: (Int) -> String,
    val overridesOk: String,
    val overridesMismatch: (Int) -> String,
    val topShedsCaption: String,
    val topColoursCaption: String,
    val emptyLabel: String,
)
