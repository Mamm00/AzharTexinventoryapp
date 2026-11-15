package com.alazhartex.stock.ui.components

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.alazhartex.stock.BuildConfig
import org.json.JSONObject

/** One whatsnew.json entry, locale-resolved (ar if the device locale is Arabic, else en). */
data class WhatsNew(val version: String, val bullets: List<String>)

object WhatsNewLoader {
    /** Reads the bundled asset; null when the file or the key for this build's version is missing. */
    fun load(context: Context, arabic: Boolean): WhatsNew? = runCatching {
        val text = context.assets.open("whatsnew.json").bufferedReader().use { it.readText() }
        val root = JSONObject(text)
        val forVersion = root.optJSONObject(BuildConfig.VERSION_NAME) ?: return@runCatching null
        val bulletsJson = forVersion.optJSONArray(if (arabic) "ar" else "en") ?: forVersion.optJSONArray("en")
            ?: return@runCatching null
        val bullets = (0 until bulletsJson.length()).map { bulletsJson.getString(it) }.filter { it.isNotBlank() }
        if (bullets.isEmpty()) null else WhatsNew(BuildConfig.VERSION_NAME, bullets)
    }.getOrNull()
}

/**
 * Guide add-on "What's new" card — bullets come from the bundled whatsnew.json (pinned in
 * CONTENT.md) for the running versionName; dismissal is persisted per version in DataStore
 * so it never nags the same user twice for the same release.
 */
@Composable
fun WhatsNewCard(
    whatsNew: WhatsNew,
    dismissLabel: String,
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        modifier = modifier.fillMaxWidth().testTag("whatsnew_card"),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Filled.Whatshot, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
            }
            whatsNew.bullets.forEach { bullet ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                    Text("•", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
                    Text(bullet, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss, modifier = Modifier.testTag("whatsnew_dismiss")) {
                    Text(dismissLabel)
                }
            }
        }
    }
}
