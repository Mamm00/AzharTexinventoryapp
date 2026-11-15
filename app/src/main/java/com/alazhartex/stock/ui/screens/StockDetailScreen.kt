package com.alazhartex.stock.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alazhartex.stock.AlAzharApp
import com.alazhartex.stock.R
import com.alazhartex.stock.ui.LocalArabicIndicDigits
import com.alazhartex.stock.ui.components.LoadingBox
import com.alazhartex.stock.ui.components.ShedUtilisationRail
import com.alazhartex.stock.ui.components.UtilisationGauge
import com.alazhartex.stock.ui.components.bucketLabelFor
import com.alazhartex.stock.ui.integerString
import com.alazhartex.stock.ui.navigation.LocalAnimatedVisibilityScope
import com.alazhartex.stock.ui.navigation.LocalSharedTransitionScope
import com.alazhartex.stock.ui.percentDisplay
import com.alazhartex.stock.ui.shared.DetailCardScaffold

private data class StockDetailState(val loaded: Boolean, val card: com.alazhartex.stock.data.repo.ShedCardDto?)

/**
 * v1.8.0 shared-bounds stock detail surface (receives the NavHost's SharedTransitionScope
 * and this destination's AnimatedVisibilityScope — see AlAzharRoot). Read-only; roll
 * mutations stay on the stock cards' audited dialogs.
 */
@Composable
fun StockDetailScreen(app: AlAzharApp, shedId: String, onBack: () -> Unit) {
    val arabicIndic = LocalArabicIndicDigits.current
    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalAnimatedVisibilityScope.current
    val state by produceState(initialValue = StockDetailState(false, null), rememberDbVersion()) {
        value = runCatching { app.container.readRepository.shedCards().firstOrNull { it.shedId == shedId } }
            .fold(
                onSuccess = { StockDetailState(true, it) },
                onFailure = { StockDetailState(true, null) },
            )
    }
    val body: @Composable () -> Unit = {
        val current = state
        when {
            !current.loaded -> LoadingBox(stringResource(R.string.loading))
            current.card == null -> Text(stringResource(R.string.error_generic), style = MaterialTheme.typography.bodyLarge)
            else -> {
                val loaded = current.card
                val bucketLabel = bucketLabelFor(
                    loaded.bucket,
                    stringResource(R.string.bucket_green),
                    stringResource(R.string.bucket_amber),
                    stringResource(R.string.bucket_red),
                    stringResource(R.string.bucket_over_capacity),
                    stringResource(R.string.label_empty_no_rows),
                )
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(loaded.nameEn, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(loaded.nameAr, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    UtilisationGauge(
                        label = stringResource(R.string.label_utilisation),
                        percentDisplay = percentDisplay(loaded.percentDisplay, arabicIndic),
                        bucket = loaded.bucket,
                        bucketLabel = bucketLabel,
                        percentFraction = loaded.percentTwoDp.toFloat() / 100f,
                        contentDescriptionPrefix = stringResource(R.string.label_utilisation_for, loaded.shedId),
                    )
                    ShedUtilisationRail(
                        percentFraction = loaded.percentTwoDp.toFloat() / 100f,
                        bucket = loaded.bucket,
                        contentDescriptionPrefix = loaded.nameEn,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    DetailFact(stringResource(R.string.label_active_rolls), "${integerString(loaded.activeRolls, arabicIndic)} / ${integerString(loaded.capacityRolls, arabicIndic)}")
                    DetailFact(stringResource(R.string.label_utilisation), loaded.percentDisplay?.let { "$it%" } ?: "—")
                }
            }
        }
    }
    if (sharedTransitionScope != null && animatedVisibilityScope != null) {
        DetailCardScaffold(
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = animatedVisibilityScope,
            sharedKey = "stock-$shedId",
            title = stringResource(R.string.stock_detail_title),
            backContentDescription = stringResource(R.string.action_back),
            onBack = onBack,
        ) { body() }
    } else {
        Scaffold { padding ->
            Column(Modifier.padding(padding).padding(16.dp)) { body() }
        }
    }
}

@Composable
private fun DetailFact(label: String, value: String) {
    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(value, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End)
        }
    }
}
