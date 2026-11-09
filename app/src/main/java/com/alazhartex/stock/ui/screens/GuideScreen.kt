package com.alazhartex.stock.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.alazhartex.stock.ui.GuideContent

@Composable
fun GuideScreen() {
    Scaffold { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(items = GuideContent.sections, key = { it.bodyRes }, contentType = { "guide" }) { section ->
                Card(colors = CardDefaults.cardColors()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(text = stringResource(section.titleRes), style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                        Text(text = stringResource(section.bodyRes), style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
