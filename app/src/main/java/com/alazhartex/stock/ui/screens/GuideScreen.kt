package com.alazhartex.stock.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.alazhartex.stock.AlAzharApp
import com.alazhartex.stock.BuildConfig
import com.alazhartex.stock.R
import com.alazhartex.stock.ui.GuideContent
import com.alazhartex.stock.ui.LocalNavigate
import com.alazhartex.stock.ui.components.AppPermissionsCard
import com.alazhartex.stock.ui.components.WhatsNewCard
import com.alazhartex.stock.ui.components.WhatsNewLoader
import com.alazhartex.stock.ui.navigation.Routes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

@Composable
fun GuideScreen(app: AlAzharApp) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val arabic = configuration.locales[0].language == "ar"
    val navigate = LocalNavigate.current
    val scope = rememberCoroutineScope()
    val reduceMotion by app.container.displayPrefs.reduceMotion.collectAsStateWithLifecycle(initialValue = false)
    val dismissedVersion by app.container.displayPrefs.dismissedWhatsNewVersion.collectAsStateWithLifecycle(initialValue = BuildConfig.VERSION_NAME)
    val whatsNew = remember(arabic) { WhatsNewLoader.load(context, arabic) }
    val showWhatsNew = whatsNew != null && dismissedVersion != BuildConfig.VERSION_NAME
    Scaffold { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (showWhatsNew) {
                item(key = "whatsnew", contentType = "whatsnew") {
                    WhatsNewCard(
                        whatsNew = requireNotNull(whatsNew),
                        dismissLabel = stringResource(R.string.whatsnew_dismiss),
                        title = stringResource(R.string.whatsnew_title, BuildConfig.VERSION_NAME),
                        onDismiss = { scope.launch { app.container.displayPrefs.setWhatsNewDismissedVersion(BuildConfig.VERSION_NAME) } },
                    )
                }
            }
            item(key = "brand", contentType = "brand") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(R.drawable.alazhartex_logo),
                        // decorative brand banner — the guide cards carry the content
                        contentDescription = null,
                        modifier = Modifier.height(72.dp),
                        contentScale = ContentScale.Fit,
                    )
                }
            }
            items(items = GuideContent.sections, key = { it.bodyRes }, contentType = { "guide" }) { section ->
                Card(colors = CardDefaults.cardColors()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(text = stringResource(section.titleRes), style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                        Text(text = stringResource(section.bodyRes), style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            item(key = "app_permissions", contentType = "app_permissions") {
                AppPermissionsCard(
                    reduceMotion = reduceMotion,
                    reduceMotionLabel = stringResource(R.string.reduce_motion_label),
                    reduceMotionStatusText = stringResource(if (reduceMotion) R.string.reduce_motion_status_on else R.string.reduce_motion_status_off),
                    bodyText = stringResource(R.string.app_permissions_body),
                    openSettingsLabel = stringResource(R.string.reduce_motion_shortcut),
                    titleText = stringResource(R.string.app_permissions_title),
                    onOpenDisplaySettings = { navigate(Routes.SETTINGS) },
                )
            }
        }
    }
}
